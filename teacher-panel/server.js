const http = require('node:http');
const net = require('node:net');
const fs = require('node:fs');
const path = require('node:path');
const { createMissionApi } = require('./mission-api');
const { createAccounts } = require('./accounts');

const minecraftHost = process.env.MINECRAFT_HOST || '127.0.0.1';
const minecraftPort = Number(process.env.MINECRAFT_PORT || 25565);
const panelPort = Number(process.env.PANEL_PORT || 3000);

function checkServer(host, port) {
  return new Promise((resolve) => {
    const socket = net.createConnection({ host, port });
    socket.setTimeout(1500);
    socket.once('connect', () => { socket.destroy(); resolve(true); });
    socket.once('error', () => resolve(false));
    socket.once('timeout', () => { socket.destroy(); resolve(false); });
  });
}

function createServer(options = {}) {
  const missionApi = createMissionApi(options);
  const accounts = createAccounts(options.accountsFile || path.join(__dirname, 'data', 'teachers.json'));
  return http.createServer(async (request, response) => {
    if (!/^(?:localhost|127\.0\.0\.1)(?::[0-9]+)?$/i.test(request.headers.host || '')) { response.writeHead(400).end(); return; }
    if (await accounts.handle(request, response)) return;
    if (await missionApi(request, response)) return;
    if (request.method !== 'GET') {
      response.writeHead(405, { Allow: 'GET' }).end();
      return;
    }
    if (request.url === '/api/status' && !accounts.current(request)) {
      response.writeHead(401).end();
      return;
    }
    if (request.url === '/api/status') {
      const reachable = await checkServer(minecraftHost, minecraftPort);
      response.writeHead(200, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
      response.end(JSON.stringify({ reachable, host: minecraftHost, port: minecraftPort }));
      return;
    }
    if (request.url === '/') {
      response.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' });
      response.end(fs.readFileSync(path.join(__dirname, accounts.current(request) ? 'index.html' : 'login.html')));
      return;
    }
    response.writeHead(404).end();
  });
}

if (require.main === module) {
  createServer({ teacherToken: process.env.TEACHER_TOKEN, bridgeToken: process.env.BRIDGE_TOKEN, bridgeUrl: process.env.BRIDGE_URL }).listen(panelPort, '127.0.0.1', () => {
    console.log(`Teacher panel: http://127.0.0.1:${panelPort}`);
  });
}
module.exports = { checkServer, createServer };
