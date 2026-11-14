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
    assert.match(await (await fetch(base)).text(), /Mission Control/);
    assert.equal((await fetch(`${base}/missing`)).status, 404);
    assert.equal((await fetch(`${base}/api/status`, { method: 'POST' })).status, 405);
    assert.equal(typeof (await (await fetch(`${base}/api/status`)).json()).reachable, 'boolean');
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
});
