const http = require('node:http');
const net = require('node:net');
const fs = require('node:fs');
const path = require('node:path');
const { createMissionApi, bridgeAddress } = require('./mission-api');
const { createAccounts } = require('./accounts');
const { createServerControl } = require('./server-control');

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
  const control = options.control || createServerControl({ checkServer: () => checkServer(minecraftHost, minecraftPort) });
  return http.createServer(async (request, response) => {
    if (!/^(?:localhost|127\.0\.0\.1)(?::[0-9]+)?$/i.test(request.headers.host || '')) { response.writeHead(400).end(); return; }
    if (await accounts.handle(request, response)) return;
    if (await missionApi(request, response)) return;
    if (['/api/mission-state', '/api/mission-action', '/api/message'].includes(request.url)) {
      if (!accounts.current(request)) { response.writeHead(401).end(); return; }
      const readOnly = request.url === '/api/mission-state';
      if (readOnly ? request.method !== 'GET' : request.method !== 'POST' || request.headers.origin !== `http://${request.headers.host}` ||
          !/^application\/json(?:\s*;|$)/i.test(request.headers['content-type'] || '')) {
        response.writeHead(403).end(); return;
      }
      let body;
      if (!readOnly) {
        try {
          let raw = '';
          for await (const chunk of request) { raw += chunk; if (Buffer.byteLength(raw) > 2048) throw new Error(); }
          const data = JSON.parse(raw);
          if (request.url === '/api/mission-action') {
            if (!data || typeof data.id !== 'string' || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(data.id) ||
                !['start', 'pause', 'resume', 'abort', 'reset'].includes(data.action)) throw new Error();
            body = `${data.id} ${data.action}`;
          } else {
            if (!data || typeof data.message !== 'string' || !data.message.trim() || data.message.trim().length > 200 || /[\x00-\x1f\x7f-\x9f]/.test(data.message)) throw new Error();
            body = data.message.trim();
          }
        } catch { response.writeHead(400).end(); return; }
      }
      const bridge = bridgeAddress(options.bridgeUrl);
      if (!bridge || !options.bridgeToken || options.bridgeToken.length < 32) { response.writeHead(503).end(); return; }
      try {
        const route = readOnly ? '/mission' : request.url === '/api/message' ? '/message' : '/mission-action';
        const result = await fetch(new URL(route, bridge), {
          method: readOnly ? 'GET' : 'POST', headers: { Authorization: `Bearer ${options.bridgeToken}` }, body,
          signal: AbortSignal.timeout(4000), redirect: 'error'
        });
        if (!readOnly) { response.writeHead(result.status === 204 ? 204 : result.status === 409 ? 409 : 503).end(); return; }
        if (!result.ok) throw new Error();
        const data = await result.json();
        if (!['READY', 'ACTIVE', 'PAUSED', 'SUCCESS', 'PARTIAL', 'FAILED', 'ABORTED'].includes(data.phase) ||
            typeof data.elapsedSeconds !== 'number' || typeof data.battery !== 'number' || typeof data.oxygen !== 'number' ||
            typeof data.players !== 'number' || typeof data.lastEvent !== 'string') throw new Error();
        response.writeHead(200, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
        response.end(JSON.stringify(data));
      } catch { response.writeHead(503).end(); }
      return;
    }
    if (request.url === '/api/server') {
      if (!accounts.current(request)) { response.writeHead(401).end(); return; }
      if (request.method !== 'POST' || request.headers.origin !== `http://${request.headers.host}` ||
          !/^application\/json(?:\s*;|$)/i.test(request.headers['content-type'] || '')) {
        response.writeHead(403).end(); return;
      }
      let action;
      try {
        let body = '';
        for await (const chunk of request) { body += chunk; if (Buffer.byteLength(body) > 256) throw new Error(); }
        action = JSON.parse(body).action;
        if (!['start', 'stop', 'restart'].includes(action)) throw new Error();
      } catch { response.writeHead(400).end(); return; }
      try {
        await control.command(action);
        response.writeHead(202).end();
      } catch (error) {
        response.writeHead(409, { 'Content-Type': 'application/json' }).end(JSON.stringify({ error: error.message }));
      }
      return;
    }
    if (request.url === '/api/kick') {
      if (!accounts.current(request)) { response.writeHead(401).end(); return; }
      if (request.method !== 'POST' || request.headers.origin !== `http://${request.headers.host}` ||
          !/^application\/json(?:\s*;|$)/i.test(request.headers['content-type'] || '')) {
        response.writeHead(403).end(); return;
      }
      let data;
      try {
        let body = '';
        for await (const chunk of request) { body += chunk; if (Buffer.byteLength(body) > 256) throw new Error(); }
        data = JSON.parse(body);
      } catch { response.writeHead(400).end(); return; }
      if (!data || typeof data.id !== 'string' || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(data.id)) {
        response.writeHead(400).end(); return;
      }
      const bridge = bridgeAddress(options.bridgeUrl);
      if (!bridge || !options.bridgeToken || options.bridgeToken.length < 32) { response.writeHead(503).end(); return; }
      try {
        const result = await fetch(new URL('/kick', bridge), {
          method: 'POST', headers: { Authorization: `Bearer ${options.bridgeToken}` }, body: data.id,
          signal: AbortSignal.timeout(4000), redirect: 'error'
        });
        response.writeHead(result.status === 204 ? 204 : result.status === 404 ? 404 : 503).end();
      } catch { response.writeHead(503).end(); }
      return;
    }
    if (request.method !== 'GET') {
      response.writeHead(405, { Allow: 'GET' }).end();
      return;
    }
    if ((request.url === '/api/status' || request.url === '/api/roster') && !accounts.current(request)) {
      response.writeHead(401).end();
      return;
    }
    if (request.url === '/api/roster') {
      const bridge = bridgeAddress(options.bridgeUrl);
      if (!bridge || !options.bridgeToken || options.bridgeToken.length < 32) {
        response.writeHead(503).end();
        return;
      }
      try {
        const result = await fetch(new URL('/roster', bridge), {
          headers: { Authorization: `Bearer ${options.bridgeToken}` },
          signal: AbortSignal.timeout(4000), redirect: 'error'
        });
        if (!result.ok) throw new Error('Bridge unavailable');
        const data = await result.json();
        if (!Array.isArray(data.players) || data.players.some(player => !player || typeof player.name !== 'string' || typeof player.id !== 'string' || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(player.id))) throw new Error('Invalid roster');
        response.writeHead(200, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
        response.end(JSON.stringify({ players: data.players.map(player => ({ id: player.id, name: player.name })) }));
      } catch { response.writeHead(503).end(); }
      return;
    }
    if (request.url === '/api/status') {
      const status = await control.status();
      response.writeHead(200, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
      response.end(JSON.stringify({ ...status, host: minecraftHost, port: minecraftPort }));
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
