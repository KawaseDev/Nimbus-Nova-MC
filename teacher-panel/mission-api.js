const { timingSafeEqual } = require('node:crypto');

const actions = new Set(['start', 'pause', 'resume', 'end', 'assign_role']);
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function send(response, status, data) {
  response.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
  response.end(JSON.stringify(data));
}

function authorized(request, token) {
  const provided = Buffer.from(request.headers.authorization || '');
  const expected = Buffer.from(`Bearer ${token}`);
  return provided.length === expected.length && timingSafeEqual(provided, expected);
}

async function readCommand(request) {
  if (!/^application\/json(?:\s*;|$)/i.test(request.headers['content-type'] || '')) throw new Error('Expected JSON');
  let body = '';
  for await (const chunk of request) {
    body += chunk;
    if (Buffer.byteLength(body) > 4096) throw new Error('Command too large');
  }
  const command = JSON.parse(body);
  if (!command || typeof command !== 'object' || Array.isArray(command) ||
      typeof command.id !== 'string' || !uuid.test(command.id) ||
      typeof command.sessionId !== 'string' || !uuid.test(command.sessionId) || !actions.has(command.action) ||
      (command.parameters !== undefined && (!command.parameters || typeof command.parameters !== 'object' || Array.isArray(command.parameters)))) {
    throw new Error('Invalid command');
  }
  return { id: command.id, sessionId: command.sessionId, action: command.action, parameters: command.parameters || {} };
}

function createMissionApi({ teacherToken, bridgeToken, bridgeUrl } = {}) {
  let bridge;
  try {
    bridge = new URL(bridgeUrl);
    if (bridge.protocol !== 'http:' || !['127.0.0.1', 'localhost', '[::1]'].includes(bridge.hostname) || bridge.username || bridge.password || bridge.pathname !== '/' || bridge.search || bridge.hash) bridge = null;
  } catch { bridge = null; }

  return async function missionApi(request, response) {
    if (request.url !== '/api/mission' && request.url !== '/api/commands') return false;
    if (!teacherToken || teacherToken.length < 32) { send(response, 503, { error: 'Teacher authentication is not configured' }); return true; }
    if (!authorized(request, teacherToken)) { send(response, 401, { error: 'Unauthorized' }); return true; }
    const isSnapshot = request.url === '/api/mission';
    if (request.method !== (isSnapshot ? 'GET' : 'POST')) { send(response, 405, { error: 'Method not allowed' }); return true; }
    let command;
    if (!isSnapshot) {
      try { command = await readCommand(request); }
      catch { send(response, 400, { error: 'Invalid command' }); return true; }
    }
    if (!bridge || !bridgeToken || bridgeToken.length < 32) { send(response, 503, { error: 'Mission bridge is not configured' }); return true; }
    try {
      const result = await fetch(new URL(isSnapshot ? '/mission' : '/commands', bridge), {
        method: isSnapshot ? 'GET' : 'POST',
        headers: { Authorization: `Bearer ${bridgeToken}`, ...(isSnapshot ? {} : { 'Content-Type': 'application/json' }) },
        body: isSnapshot ? undefined : JSON.stringify(command),
        signal: AbortSignal.timeout(3000),
        redirect: 'error'
      });
      const payload = await result.json();
      send(response, result.status, payload);
    } catch { send(response, 502, { error: 'Mission bridge unavailable or returned invalid data' }); }
    return true;
  };
}

module.exports = { createMissionApi };
