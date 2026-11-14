const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');

function createAccounts(file) {
  const sessions = new Map();
  const attempts = new Map();
  let users = [];
  try { users = JSON.parse(fs.readFileSync(file, 'utf8')); }
  catch (error) { if (error.code !== 'ENOENT') throw error; }
  if (!Array.isArray(users)) throw new Error('Invalid teacher account file');

  function current(request) {
    const token = /(?:^|;\s*)teacher_session=([a-f0-9]{64})(?:;|$)/.exec(request.headers.cookie || '')?.[1];
    const session = sessions.get(token);
    if (!session || session.expires <= Date.now()) { sessions.delete(token); return null; }
    return session.username;
  }
  function json(response, status, body, headers = {}) {
    response.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store', ...headers });
    response.end(JSON.stringify(body));
  }
  function sameOrigin(request) {
    return request.headers.origin === `http://${request.headers.host}`;
  }
  function save() {
    fs.mkdirSync(path.dirname(file), { recursive: true, mode: 0o700 });
    const temporary = `${file}.${crypto.randomBytes(8).toString('hex')}.tmp`;
    try {
      fs.writeFileSync(temporary, JSON.stringify(users), { mode: 0o600, flag: 'wx' });
      fs.renameSync(temporary, file);
    } finally { try { fs.unlinkSync(temporary); } catch (error) { if (error.code !== 'ENOENT') throw error; } }
  }
  async function handle(request, response) {
    if (!request.url.startsWith('/api/account/')) return false;
    if (request.url === '/api/account/me' && request.method === 'GET') {
      const username = current(request);
      json(response, username ? 200 : 401, username ? { username } : { error: 'Sign in required' });
      return true;
    }
    if (request.method !== 'POST' || !sameOrigin(request)) { json(response, 403, { error: 'Forbidden' }); return true; }
    if (request.url === '/api/account/logout') {
      const token = /(?:^|;\s*)teacher_session=([a-f0-9]{64})(?:;|$)/.exec(request.headers.cookie || '')?.[1];
      sessions.delete(token);
      json(response, 200, { ok: true }, { 'Set-Cookie': 'teacher_session=; HttpOnly; SameSite=Strict; Path=/; Max-Age=0' });
      return true;
    }
    if (!['/api/account/register', '/api/account/login'].includes(request.url)) { json(response, 404, { error: 'Not found' }); return true; }
    const key = request.socket.remoteAddress;
    const now = Date.now();
    const recent = (attempts.get(key) || []).filter((time) => now - time < 60000);
    if (recent.length >= 10) { json(response, 429, { error: 'Try again later' }); return true; }
    recent.push(now); attempts.set(key, recent);
    if (!/^application\/json(?:\s*;|$)/i.test(request.headers['content-type'] || '')) { json(response, 400, { error: 'Invalid request' }); return true; }
    let data;
    try {
      let body = '';
      for await (const chunk of request) { body += chunk; if (Buffer.byteLength(body) > 4096) throw new Error(); }
      data = JSON.parse(body);
    } catch { json(response, 400, { error: 'Invalid request' }); return true; }
    if (!data || typeof data.username !== 'string' || typeof data.password !== 'string' || !/^[a-zA-Z0-9_.-]{3,32}$/.test(data.username) || data.password.length < 12 || Buffer.byteLength(data.password) > 256) {
      json(response, 400, { error: 'Use a 3–32 character username and a password of at least 12 characters' }); return true;
    }
    const username = data.username.toLowerCase();
    const user = users.find((entry) => entry.username === username);
    if (request.url.endsWith('/register')) {
      if (user) { json(response, 409, { error: 'Username already exists' }); return true; }
      const salt = crypto.randomBytes(16).toString('hex');
      const hash = crypto.scryptSync(data.password, salt, 64).toString('hex');
      users.push({ username, salt, hash });
      try { save(); } catch { users.pop(); json(response, 500, { error: 'Could not save account' }); return true; }
    } else {
      const hash = crypto.scryptSync(data.password, user?.salt || '00000000000000000000000000000000', 64);
      const expected = Buffer.from(user?.hash || '0'.repeat(128), 'hex');
      if (!user || expected.length !== hash.length || !crypto.timingSafeEqual(hash, expected)) { json(response, 401, { error: 'Invalid username or password' }); return true; }
    }
    const token = crypto.randomBytes(32).toString('hex');
    for (const [id, session] of sessions) if (session.expires <= now) sessions.delete(id);
    sessions.set(token, { username, expires: now + 12 * 60 * 60 * 1000 });
    json(response, 200, { username }, { 'Set-Cookie': `teacher_session=${token}; HttpOnly; SameSite=Strict; Path=/; Max-Age=43200` });
    return true;
  }
  return { current, handle };
}
module.exports = { createAccounts };
