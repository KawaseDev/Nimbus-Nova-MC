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

test('server controls manage only their own process and stop it before restarting', async () => {
  const fs = require('node:fs');
  const os = require('node:os');
  const path = require('node:path');
  const { EventEmitter } = require('node:events');
  const { PassThrough } = require('node:stream');
  const { createServerControl } = require('./server-control');
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'nimbus-control-'));
  fs.mkdirSync(path.join(dir, 'run'));
  fs.writeFileSync(path.join(dir, 'run', 'eula.txt'), 'eula=true\n');
  let occupied = true, launches = 0, stops = 0;
  const controller = createServerControl({ root: dir, checkServer: async () => occupied, spawnServer: () => {
    launches++;
    const child = new EventEmitter();
    child.stdout = new PassThrough(); child.stderr = new PassThrough(); child.stdin = new PassThrough();
    child.stdin.on('data', data => {
      assert.equal(data.toString(), 'stop\n');
      stops++;
      setImmediate(() => child.emit('close', 0));
    });
    setImmediate(() => child.stdout.write('Done (1s)!'));
    return child;
  } });
  try {
    assert.equal((await controller.status()).state, 'external');
    await assert.rejects(controller.command('start'), /already using/);
    await assert.rejects(controller.command('stop'), /not started/);
    occupied = false;
    await controller.command('start');
    await new Promise(resolve => setImmediate(resolve));
    assert.equal((await controller.status()).state, 'running');
    await controller.command('restart');
    assert.equal(launches, 2);
    assert.equal(stops, 1);
    await controller.command('stop');
    assert.equal(stops, 2);
    assert.equal((await controller.status()).state, 'stopped');
  } finally { fs.rmSync(dir, { recursive: true, force: true }); }
});

test('server power API requires a session and same-origin request', async () => {
  const fs = require('node:fs');
  const os = require('node:os');
  const path = require('node:path');
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'nimbus-power-'));
  let action;
  const control = { status: async () => ({ state: 'stopped', reachable: false }), command: async value => { action = value; } };
  const server = createServer({ accountsFile: path.join(dir, 'users.json'), control });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  try {
    const post = (url, headers, body) => fetch(`${base}${url}`, { method: 'POST', headers: { 'Content-Type': 'application/json', ...headers }, body: JSON.stringify(body) });
    assert.equal((await post('/api/server', { Origin: base }, { action: 'stop' })).status, 401);
    const registration = await post('/api/account/register', { Origin: base }, { username: 'teacher', password: 'x' });
    const cookie = registration.headers.get('set-cookie').split(';')[0];
    assert.equal((await post('/api/server', { Origin: 'http://evil.example', Cookie: cookie }, { action: 'stop' })).status, 403);
    assert.equal((await post('/api/server', { Origin: base, Cookie: cookie }, { action: 'shell' })).status, 400);
    assert.equal((await post('/api/server', { Origin: base, Cookie: cookie }, { action: 'restart' })).status, 202);
    assert.equal(action, 'restart');
    assert.equal((await post('/api/server', { Origin: base, Cookie: cookie }, { action: 'update' })).status, 202);
    assert.equal(action, 'update');
  } finally { await new Promise(resolve => server.close(resolve)); fs.rmSync(dir, { recursive: true, force: true }); }
});

test('server update saves and stops before building, preserves worlds, and leaves a failed build stopped', async () => {
  const fs = require('node:fs');
  const os = require('node:os');
  const path = require('node:path');
  const { EventEmitter } = require('node:events');
  const { PassThrough } = require('node:stream');
  const { createServerControl } = require('./server-control');
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'nimbus-update-'));
  fs.mkdirSync(path.join(dir, 'run', 'world'), { recursive: true });
  fs.writeFileSync(path.join(dir, 'run', 'eula.txt'), 'eula=true\n');
  fs.writeFileSync(path.join(dir, 'run', 'world', 'player-build'), 'keep');
  const events = [];
  let occupied = false, failBuild = false, failStop = false;
  const controller = createServerControl({ root: dir, checkServer: async () => occupied, spawnServer: (_command, args) => {
    const child = new EventEmitter();
    child.stdout = new PassThrough(); child.stderr = new PassThrough(); child.stdin = new PassThrough();
    if (args[0] === 'build') {
      events.push('build');
      setImmediate(() => child.emit('close', failBuild ? 1 : 0));
    } else {
      events.push('start');
      child.stdin.on('data', data => {
        assert.equal(data.toString(), 'stop\n');
        events.push('stop');
        setImmediate(() => { occupied = false; child.emit('close', failStop ? 1 : 0); });
      });
      setImmediate(() => { occupied = true; child.stdout.write('Done (1s)!'); });
    }
    return child;
  } });
  try {
    await controller.command('start');
    await new Promise(resolve => setImmediate(resolve));
    await controller.command('update');
    await new Promise(resolve => setImmediate(resolve));
    assert.deepEqual(events, ['start', 'stop', 'build', 'start']);
    assert.equal(fs.readFileSync(path.join(dir, 'run', 'world', 'player-build'), 'utf8'), 'keep');
    failBuild = true;
    await assert.rejects(controller.command('update'), /Build failed/);
    assert.equal((await controller.status()).state, 'stopped');
    assert.equal(fs.readFileSync(path.join(dir, 'run', 'world', 'player-build'), 'utf8'), 'keep');
    failBuild = false;
    await controller.command('start');
    await new Promise(resolve => setImmediate(resolve));
    failStop = true;
    const eventCount = events.length;
    await assert.rejects(controller.command('update'), /did not stop cleanly/);
    assert.deepEqual(events.slice(eventCount), ['stop']);
    occupied = true;
    await assert.rejects(controller.command('update'), /outside this panel/);
    occupied = false; failStop = false;
    const stoppedEvents = events.length;
    await controller.command('update');
    await new Promise(resolve => setImmediate(resolve));
    assert.deepEqual(events.slice(stoppedEvents), ['build', 'start']);
    assert.equal(fs.readFileSync(path.join(dir, 'run', 'world', 'player-build'), 'utf8'), 'keep');
  } finally { fs.rmSync(dir, { recursive: true, force: true }); }
});

test('mission controls and crew messages require a teacher and forward only valid actions', async () => {
  const fs = require('node:fs');
  const os = require('node:os');
  const path = require('node:path');
  const http = require('node:http');
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'nimbus-mission-'));
  const token = 'b'.repeat(32);
  const id = '01234567-89ab-4cde-8f01-23456789abcd';
  let received;
  const bridge = http.createServer(async (request, response) => {
    assert.equal(request.headers.authorization, `Bearer ${token}`);
    if (request.url === '/mission') {
      response.writeHead(200, { 'Content-Type': 'application/json' }).end(JSON.stringify({ phase: 'READY', elapsedSeconds: 0, battery: 80, oxygen: 75, players: 1, lastEvent: 'Ready' }));
      return;
    }
    let body = '';
    for await (const chunk of request) body += chunk;
    received = { path: request.url, body };
    response.writeHead(204).end();
  });
  await new Promise(resolve => bridge.listen(0, '127.0.0.1', resolve));
  const server = createServer({ accountsFile: path.join(dir, 'users.json'), bridgeToken: token, bridgeUrl: `http://127.0.0.1:${bridge.address().port}` });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  const post = (url, headers, body) => fetch(`${base}${url}`, { method: 'POST', headers: { 'Content-Type': 'application/json', ...headers }, body: JSON.stringify(body) });
  try {
    assert.equal((await fetch(`${base}/api/mission-state`)).status, 401);
    const registration = await post('/api/account/register', { Origin: base }, { username: 'teacher', password: 'x' });
    const cookie = registration.headers.get('set-cookie').split(';')[0];
    const headers = { Origin: base, Cookie: cookie };
    assert.equal((await (await fetch(`${base}/api/mission-state`, { headers: { Cookie: cookie } })).json()).phase, 'READY');
    assert.equal((await post('/api/mission-action', { Origin: 'http://evil.example', Cookie: cookie }, { id, action: 'start' })).status, 403);
    assert.equal((await post('/api/mission-action', headers, { id, action: 'shell' })).status, 400);
    assert.equal((await post('/api/mission-action', headers, { id, action: 'pause' })).status, 204);
    assert.deepEqual(received, { path: '/mission-action', body: `${id} pause` });
    assert.equal((await post('/api/mission-action', headers, { id, action: 'acknowledge' })).status, 204);
    assert.deepEqual(received, { path: '/mission-action', body: `${id} acknowledge` });
    assert.equal((await post('/api/mission-action', headers, { id, action: 'moon_rescue' })).status, 204);
    assert.deepEqual(received, { path: '/mission-action', body: `${id} moon_rescue` });
    for (const action of ['ship_select', 'ship_explore', 'ship_start', 'ship_pause', 'ship_acknowledge', 'ship_resume', 'ship_abort', 'ship_reset', 'ship_end']) {
      assert.equal((await post('/api/mission-action', headers, { id, action })).status, 204);
      assert.deepEqual(received, { path: '/mission-action', body: `${id} ${action}` });
    }
    assert.equal((await post('/api/mission-action', headers, { id, action: 'ship_shell' })).status, 400);
    assert.equal((await post('/api/message', headers, { message: 'hello\nworld' })).status, 400);
    assert.equal((await post('/api/message', headers, { message: '  Return to base  ' })).status, 204);
    assert.deepEqual(received, { path: '/message', body: 'Return to base' });
  } finally {
    await new Promise(resolve => server.close(resolve));
    await new Promise(resolve => bridge.close(resolve));
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test('world creation requires a teacher and forwards only a fixed request', async () => {
  const fs = require('node:fs');
  const os = require('node:os');
  const path = require('node:path');
  const http = require('node:http');
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'nimbus-world-'));
  const token = 'b'.repeat(32);
  let creations = 0, upgrades = 0;
  const bridge = http.createServer((request, response) => {
    assert.equal(request.headers.authorization, `Bearer ${token}`);
    assert.ok(['/world', '/world/upgrade'].includes(request.url));
    if (request.url === '/world/upgrade') { upgrades++; response.writeHead(202).end(); }
    else if (request.method === 'POST') { creations++; response.writeHead(202).end(); }
    else response.writeHead(200, { 'Content-Type': 'application/json' }).end(JSON.stringify({ status: 'idle' }));
  });
  await new Promise(resolve => bridge.listen(0, '127.0.0.1', resolve));
  const server = createServer({ accountsFile: path.join(dir, 'users.json'), bridgeToken: token, bridgeUrl: `http://127.0.0.1:${bridge.address().port}` });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  try {
    assert.equal((await fetch(`${base}/api/world`)).status, 401);
    const registration = await fetch(`${base}/api/account/register`, { method: 'POST', headers: { Origin: base, 'Content-Type': 'application/json' }, body: JSON.stringify({ username: 'teacher', password: 'x' }) });
    const cookie = registration.headers.get('set-cookie').split(';')[0];
    assert.equal((await (await fetch(`${base}/api/world`, { headers: { Cookie: cookie } })).json()).status, 'idle');
    assert.equal((await fetch(`${base}/api/world`, { method: 'POST', headers: { Cookie: cookie, Origin: 'http://evil.example' } })).status, 403);
    assert.equal((await fetch(`${base}/api/world`, { method: 'POST', headers: { Cookie: cookie, Origin: base } })).status, 202);
    assert.equal(creations, 1);
    assert.equal((await fetch(`${base}/api/world/upgrade`, { method: 'POST', headers: { Cookie: cookie, Origin: 'http://evil.example' } })).status, 403);
    assert.equal((await fetch(`${base}/api/world/upgrade`, { method: 'POST', headers: { Cookie: cookie, Origin: base } })).status, 202);
    assert.equal(upgrades, 1);
  } finally {
    await new Promise(resolve => server.close(resolve));
    await new Promise(resolve => bridge.close(resolve));
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test('student view requires a teacher and only returns valid bounded images', async () => {
  const fs = require('node:fs');
  const os = require('node:os');
  const path = require('node:path');
  const http = require('node:http');
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'nimbus-view-'));
  const token = 'b'.repeat(32);
  const id = '01234567-89ab-4cde-8f01-23456789abcd';
  let invalid = false;
  const bridge = http.createServer((request, response) => {
    assert.equal(request.headers.authorization, `Bearer ${token}`);
    assert.equal(request.url, `/view?id=${id}`);
    response.writeHead(200, { 'Content-Type': 'image/jpeg' });
    response.end(invalid ? 'not an image' : Buffer.from([0xff, 0xd8, 0, 0xff, 0xd9]));
  });
  await new Promise(resolve => bridge.listen(0, '127.0.0.1', resolve));
  const server = createServer({ accountsFile: path.join(dir, 'users.json'), bridgeToken: token, bridgeUrl: `http://127.0.0.1:${bridge.address().port}` });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  try {
    assert.equal((await fetch(`${base}/api/view?id=${id}`)).status, 401);
    const registration = await fetch(`${base}/api/account/register`, { method: 'POST', headers: { Origin: base, 'Content-Type': 'application/json' }, body: JSON.stringify({ username: 'teacher', password: 'x' }) });
    const cookie = registration.headers.get('set-cookie').split(';')[0];
    assert.equal((await fetch(`${base}/api/view?id=bad`, { headers: { Cookie: cookie } })).status, 400);
    const response = await fetch(`${base}/api/view?id=${id}`, { headers: { Cookie: cookie } });
    assert.equal(response.status, 200);
    assert.equal(response.headers.get('content-type'), 'image/jpeg');
    assert.equal(response.headers.get('cache-control'), 'no-store');
    invalid = true;
    assert.equal((await fetch(`${base}/api/view?id=${id}`, { headers: { Cookie: cookie } })).status, 503);
  } finally {
    await new Promise(resolve => server.close(resolve));
    await new Promise(resolve => bridge.close(resolve));
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test('session codes resolve only while active and reserve one server per teacher', async () => {
  const { createSessions } = require('./sessions');
  let online = false;
  let offline = false;
  const sessions = createSessions({ gameAddress: '127.0.0.1:25565', discoveryAddress: '127.0.0.1:3002', checkServer: async () => online, isOffline: async () => offline });
  const listener = sessions.listener();
  await new Promise(resolve => listener.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${listener.address().port}`;
  const resolveCode = code => fetch(`${base}/resolve`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ code }) });
  try {
    await assert.rejects(sessions.create('teacher'), /Start the Minecraft server/);
    online = true;
    const session = await sessions.create('teacher');
    assert.match(session.code, /^[A-HJ-NP-Z2-9]{8}$/);
    assert.equal((await sessions.create('teacher')).code, session.code);
    await assert.rejects(sessions.create('other'), /reserved/);
    assert.equal(sessions.teacher('other').status, 'occupied');
    assert.equal((await resolveCode('AAAAAAAA')).status, 404);
    const result = await resolveCode(session.code.toLowerCase());
    assert.deepEqual(await result.json(), { address: '127.0.0.1:25565', offline: false });
    offline = true;
    assert.equal((await (await resolveCode(session.code)).json()).offline, true);
    online = false;
    assert.equal((await resolveCode(session.code)).status, 503);
    online = true;
    assert.throws(() => sessions.end('other'), /reserved/);
    session.expires = Date.now() - 1;
    assert.equal((await resolveCode(session.code)).status, 404);
    assert.equal(sessions.teacher('teacher').status, 'expired');
    await assert.rejects(sessions.create('other'), /reserved/);
    const renewed = await sessions.create('teacher');
    assert.equal((await resolveCode(renewed.code)).status, 200);
    sessions.end('teacher');
    assert.equal((await resolveCode(renewed.code)).status, 404);
    for (let i = 0; i < 6; i++) await resolveCode('AAAAAAAA');
    assert.equal((await resolveCode('AAAAAAAA')).status, 429);
  } finally { await new Promise(resolve => listener.close(resolve)); }
});

test('session follows server availability and is private to the owning teacher', async () => {
  const fs = require('node:fs');
  const os = require('node:os');
  const path = require('node:path');
  const { createSessions } = require('./sessions');
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'nimbus-session-'));
  let online = false;
  const sessions = createSessions({ checkServer: async () => online });
  const control = { status: async () => ({ reachable: online, managed: online, state: online ? 'running' : 'stopped' }) };
  const server = createServer({ accountsFile: path.join(dir, 'users.json'), sessions, control });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  try {
    assert.equal((await fetch(`${base}/api/session`)).status, 401);
    const registration = await fetch(`${base}/api/account/register`, { method: 'POST', headers: { Origin: base, 'Content-Type': 'application/json' }, body: JSON.stringify({ username: 'teacher', password: 'x' }) });
    const cookie = registration.headers.get('set-cookie').split(';')[0];
    assert.equal((await fetch(`${base}/api/session`, { method: 'POST', headers: { Cookie: cookie, Origin: base } })).status, 405);
    assert.equal((await (await fetch(`${base}/api/session`, { headers: { Cookie: cookie } })).json()).status, 'offline');
    online = true;
    const created = await fetch(`${base}/api/session`, { headers: { Cookie: cookie } });
    assert.equal((await created.json()).status, 'open');
    const otherRegistration = await fetch(`${base}/api/account/register`, { method: 'POST', headers: { Origin: base, 'Content-Type': 'application/json' }, body: JSON.stringify({ username: 'otherteacher', password: 'x' }) });
    const otherCookie = otherRegistration.headers.get('set-cookie').split(';')[0];
    assert.equal((await (await fetch(`${base}/api/session`, { headers: { Cookie: otherCookie } })).json()).status, 'occupied');
    assert.equal((await fetch(`${base}/api/status`, { headers: { Cookie: otherCookie } })).status, 403);
    assert.equal((await fetch(`${base}/api/session`, { method: 'DELETE', headers: { Cookie: otherCookie, Origin: base } })).status, 405);
    online = false;
    assert.equal((await (await fetch(`${base}/api/session`, { headers: { Cookie: cookie } })).json()).status, 'offline');
    assert.equal(sessions.teacher('teacher').status, 'available');
  } finally { await new Promise(resolve => server.close(resolve)); fs.rmSync(dir, { recursive: true, force: true }); }
});

test('server start, restart, stop and unexpected exit control the session code', async () => {
  const fs = require('node:fs');
  const os = require('node:os');
  const path = require('node:path');
  const { EventEmitter } = require('node:events');
  const { PassThrough } = require('node:stream');
  const { createSessions } = require('./sessions');
  const { createServerControl } = require('./server-control');
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'nimbus-lifecycle-'));
  fs.mkdirSync(path.join(dir, 'run'));
  fs.writeFileSync(path.join(dir, 'run', 'eula.txt'), 'eula=true\n');
  let online = false, current;
  const sessions = createSessions({ checkServer: async () => online });
  const control = createServerControl({ root: dir, checkServer: async () => online, onExit: () => sessions.clear(), spawnServer: (_command, args) => {
    const child = new EventEmitter(); current = child;
    child.stdout = new PassThrough(); child.stderr = new PassThrough(); child.stdin = new PassThrough();
    if (args[0] === 'build') { setImmediate(() => child.emit('close', 0)); return child; }
    child.stdin.on('data', () => setImmediate(() => { online = false; child.emit('close', 0); }));
    setImmediate(() => { online = true; child.stdout.write('Done (1s)!'); });
    return child;
  } });
  const server = createServer({ accountsFile: path.join(dir, 'users.json'), sessions, control });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  const post = (url, headers, body) => fetch(`${base}${url}`, { method: 'POST', headers: { 'Content-Type': 'application/json', ...headers }, body: JSON.stringify(body) });
  try {
    const registration = await post('/api/account/register', { Origin: base }, { username: 'teacher', password: 'x' });
    const cookie = registration.headers.get('set-cookie').split(';')[0];
    const headers = { Cookie: cookie, Origin: base };
    const session = async () => (await (await fetch(`${base}/api/session`, { headers: { Cookie: cookie } })).json());
    assert.equal((await session()).status, 'offline');
    assert.equal((await post('/api/server', headers, { action: 'start' })).status, 202);
    await new Promise(resolve => setImmediate(resolve));
    const first = await session(); assert.equal(first.status, 'open');
    assert.equal((await post('/api/server', headers, { action: 'restart' })).status, 202);
    await new Promise(resolve => setImmediate(resolve));
    const second = await session(); assert.equal(second.status, 'open'); assert.notEqual(second.code, first.code);
    assert.equal((await post('/api/server', headers, { action: 'update' })).status, 202);
    await new Promise(resolve => setImmediate(resolve));
    const updated = await session(); assert.equal(updated.status, 'open'); assert.notEqual(updated.code, second.code);
    assert.equal((await post('/api/server', headers, { action: 'stop' })).status, 202);
    assert.equal((await session()).status, 'offline');
    assert.equal((await post('/api/server', headers, { action: 'start' })).status, 202);
    await new Promise(resolve => setImmediate(resolve));
    assert.equal((await session()).status, 'open');
    online = false; current.emit('close', 1);
    assert.equal((await session()).status, 'offline');
  } finally { await new Promise(resolve => server.close(resolve)); fs.rmSync(dir, { recursive: true, force: true }); }
});
