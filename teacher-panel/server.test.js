const { test } = require('node:test');
const assert = require('node:assert/strict');
const net = require('node:net');
const { checkServer, createServer } = require('./server');

test('server reachability reports an open and then closed port', async () => {
  const socketServer = net.createServer((socket) => socket.end());
  await new Promise((resolve) => socketServer.listen(0, '127.0.0.1', resolve));
  const port = socketServer.address().port;
  assert.equal(await checkServer('127.0.0.1', port), true);
  await new Promise((resolve) => socketServer.close(resolve));
  assert.equal(await checkServer('127.0.0.1', port), false);
});

test('panel serves its page and rejects requests outside its API', async () => {
  const server = createServer();
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  try {
    const base = `http://127.0.0.1:${server.address().port}`;
    assert.match(await (await fetch(base)).text(), /Teacher sign in/);
    assert.equal((await fetch(`${base}/missing`)).status, 404);
    assert.equal((await fetch(`${base}/api/status`, { method: 'POST' })).status, 405);
    assert.equal((await fetch(`${base}/api/status`)).status, 401);
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
});

test('mission API authenticates teachers and relays validated commands to the bridge', async () => {
  const teacherToken = 't'.repeat(32);
  const bridgeToken = 'b'.repeat(32);
  const command = { id: '01234567-89ab-4cde-8f01-23456789abcd', sessionId: '11234567-89ab-4cde-8f01-23456789abcd', action: 'pause' };
  let received;
  const http = require('node:http');
  const bridge = http.createServer(async (request, response) => {
    assert.equal(request.headers.authorization, `Bearer ${bridgeToken}`);
    let body = '';
    for await (const chunk of request) body += chunk;
    received = { path: request.url, body: body && JSON.parse(body) };
    response.writeHead(200, { 'Content-Type': 'application/json' }).end(JSON.stringify({ ok: true }));
  });
  await new Promise((resolve) => bridge.listen(0, '127.0.0.1', resolve));
  const server = createServer({ teacherToken, bridgeToken, bridgeUrl: `http://127.0.0.1:${bridge.address().port}` });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  const headers = { Authorization: `Bearer ${teacherToken}`, 'Content-Type': 'application/json' };
  try {
    assert.equal((await fetch(`${base}/api/mission`)).status, 401);
    assert.equal((await fetch(`${base}/api/mission`, { headers })).status, 200);
    assert.equal(received.path, '/mission');
    assert.equal((await fetch(`${base}/api/commands`, { method: 'POST', headers, body: JSON.stringify({ ...command, action: 'shell' }) })).status, 400);
    assert.equal((await fetch(`${base}/api/commands`, { method: 'POST', headers, body: JSON.stringify(command) })).status, 200);
    assert.deepEqual(received, { path: '/commands', body: { ...command, parameters: {} } });
  } finally {
    await new Promise((resolve) => server.close(resolve));
    await new Promise((resolve) => bridge.close(resolve));
  }
});

test('mission API fails closed when authentication or bridge is not configured', async () => {
  for (const options of [{}, { teacherToken: 't'.repeat(32) }]) {
    const server = createServer(options);
    await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
    try {
      const response = await fetch(`http://127.0.0.1:${server.address().port}/api/mission`, { headers: { Authorization: `Bearer ${'t'.repeat(32)}` } });
      assert.equal(response.status, 503);
    } finally { await new Promise((resolve) => server.close(resolve)); }
  }
});


test('teacher accounts persist, protect the dashboard, and sign out', async () => {
  const fs = require('node:fs');
  const os = require('node:os');
  const path = require('node:path');
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'nimbus-accounts-'));
  const accountsFile = path.join(dir, 'teachers.json');
  async function start() {
    const server = createServer({ accountsFile });
    await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
    return [server, `http://127.0.0.1:${server.address().port}`];
  }
  let [server, base] = await start();
  const credentials = { username: 'Teacher', password: 'x' };
  async function post(endpoint, data, cookie, origin = base) {
    return fetch(`${base}/api/account/${endpoint}`, { method: 'POST', headers: { Origin: origin, 'Content-Type': 'application/json', ...(cookie ? { Cookie: cookie } : {}) }, body: JSON.stringify(data) });
  }
  try {
    assert.equal((await post('register', credentials, null, 'http://evil.example')).status, 403);
    assert.equal((await post('register', { ...credentials, password: '' })).status, 400);
    let response = await post('register', credentials);
    assert.equal(response.status, 200);
    let cookie = response.headers.get('set-cookie').split(';')[0];
    assert.match(await (await fetch(base, { headers: { Cookie: cookie } })).text(), /Mission Control/);
    assert.equal((await fetch(`${base}/api/status`, { headers: { Cookie: cookie } })).status, 200);
    assert.equal((await post('register', credentials)).status, 409);
    assert.equal(JSON.parse(fs.readFileSync(accountsFile, 'utf8'))[0].password, undefined);
    assert.equal((await post('logout', {}, cookie)).status, 200);
    assert.equal((await fetch(`${base}/api/account/me`, { headers: { Cookie: cookie } })).status, 401);
    await new Promise((resolve) => server.close(resolve));
    [server, base] = await start();
    assert.equal((await post('login', { ...credentials, password: 'wrong password' })).status, 401);
    response = await post('login', credentials);
    assert.equal(response.status, 200);
    cookie = response.headers.get('set-cookie').split(';')[0];
    assert.deepEqual(await (await fetch(`${base}/api/account/me`, { headers: { Cookie: cookie } })).json(), { username: 'teacher' });
  } finally {
    await new Promise((resolve) => server.close(resolve));
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test('roster requires a teacher session and uses the authenticated bridge', async () => {
  const fs = require('node:fs');
  const os = require('node:os');
  const path = require('node:path');
  const http = require('node:http');
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'nimbus-roster-'));
  const token = 'b'.repeat(32);
  const playerId = '01234567-89ab-4cde-8f01-23456789abcd';
  let kicked;
  const bridge = http.createServer(async (request, response) => {
    assert.equal(request.headers.authorization, `Bearer ${token}`);
    if (request.url === '/kick') {
      let body = '';
      for await (const chunk of request) body += chunk;
      kicked = body;
      response.writeHead(204).end();
      return;
    }
    assert.equal(request.url, '/roster');
    response.writeHead(200, { 'Content-Type': 'application/json' });
    response.end(JSON.stringify({ players: [{ id: playerId, name: 'Explorer' }] }));
  });
  await new Promise(resolve => bridge.listen(0, '127.0.0.1', resolve));
  const server = createServer({ accountsFile: path.join(dir, 'users.json'), bridgeToken: token, bridgeUrl: `http://127.0.0.1:${bridge.address().port}` });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  try {
    assert.equal((await fetch(`${base}/api/roster`)).status, 401);
    const registration = await fetch(`${base}/api/account/register`, { method: 'POST', headers: { Origin: base, 'Content-Type': 'application/json' }, body: JSON.stringify({ username: 'teacher', password: 'x' }) });
    const cookie = registration.headers.get('set-cookie').split(';')[0];
    const roster = await fetch(`${base}/api/roster`, { headers: { Cookie: cookie } });
    assert.deepEqual(await roster.json(), { players: [{ id: playerId, name: 'Explorer' }] });
    const kick = (headers, id) => fetch(`${base}/api/kick`, { method: 'POST', headers: { 'Content-Type': 'application/json', ...headers }, body: JSON.stringify({ id }) });
    assert.equal((await kick({ Origin: base }, playerId)).status, 401);
    assert.equal((await kick({ Origin: 'http://evil.example', Cookie: cookie }, playerId)).status, 403);
    assert.equal((await kick({ Origin: base, Cookie: cookie }, 'bad-id')).status, 400);
    assert.equal((await kick({ Origin: base, Cookie: cookie }, playerId)).status, 204);
    assert.equal(kicked, playerId);
    await new Promise(resolve => bridge.close(resolve));
    assert.equal((await fetch(`${base}/api/roster`, { headers: { Cookie: cookie } })).status, 503);
  } finally {
    await new Promise(resolve => server.close(resolve));
    if (bridge.listening) await new Promise(resolve => bridge.close(resolve));
    fs.rmSync(dir, { recursive: true, force: true });
  }
});
