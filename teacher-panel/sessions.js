const crypto = require('node:crypto');
const http = require('node:http');

function createSessions({ gameAddress = '127.0.0.1:25565', discoveryAddress = '127.0.0.1:3002', checkServer, isOffline = async () => false }) {
  function address(value, expectedPort) {
    let url;
    try { url = new URL(`http://${value}`); } catch { throw new Error('Invalid session address'); }
    if (!url.hostname || !url.port || Number(url.port) !== expectedPort || url.username || url.password || url.pathname !== '/' || url.search || url.hash)
      throw new Error('Invalid session address');
    return url.hostname.toLowerCase();
  }
  const discoveryHost = address(discoveryAddress, 3002);
  const gameHost = address(gameAddress, Number(gameAddress.slice(gameAddress.lastIndexOf(':') + 1)));
  if (discoveryHost !== gameHost) throw new Error('Session and Minecraft addresses must use the same host');
  let session;
  const attempts = new Map();
  function active() { return session; }
  function reserve(owner) {
    const existing = active();
    if (existing) {
      if (existing.owner !== owner) throw new Error('The server is reserved by another teacher');
      if (existing.expires > Date.now()) return existing;
    }
    const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    let code = '';
    for (let i = 0; i < 8; i++) code += alphabet[crypto.randomInt(alphabet.length)];
    session = { owner, code, expires: Date.now() + 60 * 60 * 1000 };
    return session;
  }
  async function create(owner) {
    if (!await checkServer()) throw new Error('Start the Minecraft server first');
    return reserve(owner);
  }
  function expire(owner) {
    const existing = active();
    if (existing && existing.owner !== owner) throw new Error('The server is reserved by another teacher');
    if (existing) existing.expires = 0;
  }
  function end(owner) {
    const existing = active();
    if (existing && existing.owner !== owner) throw new Error('The server is reserved by another teacher');
    session = undefined;
  }
  function clear() { session = undefined; }
  function teacher(owner) {
    const existing = active();
    if (!existing) return { status: 'available', discoveryAddress };
    if (existing.owner !== owner) return { status: 'occupied' };
    if (existing.expires <= Date.now()) return { status: 'expired', discoveryAddress };
    return { status: 'open', code: existing.code, expires: existing.expires, discoveryAddress };
  }
  function listener() {
    return http.createServer(async (request, response) => {
      if (request.method !== 'POST' || request.url !== '/resolve' || !/^application\/json(?:\s*;|$)/i.test(request.headers['content-type'] || '')) {
        response.writeHead(404).end(); return;
      }
      const ip = request.socket.remoteAddress;
      const now = Date.now();
      for (const [key, value] of attempts) if (now - value.start >= 60000) attempts.delete(key);
      const entry = attempts.get(ip) || { start: now, count: 0 };
      if (++entry.count > 12) { response.writeHead(429).end(); return; }
      if (attempts.size >= 1024 && !attempts.has(ip)) { response.writeHead(429).end(); return; }
      attempts.set(ip, entry);
      let code;
      try {
        let body = '';
        for await (const chunk of request) { body += chunk; if (Buffer.byteLength(body) > 256) throw new Error(); }
        code = JSON.parse(body).code;
        if (typeof code !== 'string' || !/^[A-HJ-NP-Z2-9]{8}$/i.test(code)) throw new Error();
      } catch { response.writeHead(400).end(); return; }
      const current = active();
      if (!current || current.expires <= Date.now() || code.toUpperCase() !== current.code) { response.writeHead(404).end(); return; }
      if (!await checkServer()) { response.writeHead(503).end(); return; }
      response.writeHead(200, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
      let offline = false;
      try { offline = await isOffline() === true; } catch { /* Keep authenticated login as the default. */ }
      response.end(JSON.stringify({ address: gameAddress, offline }));
    });
  }
  return { create, reserve, expire, end, clear, teacher, listener };
}
module.exports = { createSessions };
