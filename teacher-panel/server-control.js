const { spawn } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');

function createServerControl({ checkServer, spawnServer = spawn, root = path.resolve(__dirname, '..'), onExit = () => {} }) {
  let child = null;
  let state = 'stopped';
  let error = '';
  let busy = false;
  let restarting = false;
  let updating = false;

  async function start() {
    if (child) throw new Error('Server is already managed by the panel');
    state = 'starting';
    if (await checkServer()) { state = 'stopped'; throw new Error('A server is already using the Minecraft port'); }
    let eula;
    try { eula = fs.readFileSync(path.join(root, 'run', 'eula.txt'), 'utf8'); } catch { eula = ''; }
    if (!/^eula=true\s*$/m.test(eula)) { state = 'stopped'; throw new Error('Accept the Minecraft EULA in run/eula.txt first'); }
    error = '';
    let process;
    try {
      process = spawnServer(path.join(root, processPlatformWrapper()), ['runServer', '--console=plain'], {
        cwd: root, env: { ...global.process.env }, stdio: ['pipe', 'pipe', 'pipe']
      });
    } catch (err) { state = 'stopped'; throw err; }
    child = process;
    process.stdin.on('error', () => {});
    let recentOutput = '';
    process.stdout.on('data', data => {
      const output = data.toString();
      recentOutput = (recentOutput + output).slice(-1024);
      if (recentOutput.includes('Done (') && state === 'starting') state = 'running';
      global.process.stdout.write(output);
    });
    process.stderr.on('data', data => global.process.stderr.write(data));
    process.on('error', err => { error = `Server failed to start: ${err.message}`; });
    process.on('close', code => {
      if (state !== 'stopping' && code !== 0 && !error) error = 'Server exited unexpectedly';
      child = null;
      state = 'stopped';
      if (!restarting) onExit();
    });
  }

  async function stop() {
    if (!child) throw new Error('Server was not started by this panel');
    if (!child.stdin.writable) throw new Error('Server input is unavailable');
    const stopping = child;
    state = 'stopping';
    await new Promise((resolve, reject) => {
      const timeout = setTimeout(() => { stopping.off('close', done); reject(new Error('Server did not stop; check its console')); }, 30000);
      function done(code) {
        clearTimeout(timeout);
        if (code !== 0) reject(new Error('Server did not stop cleanly. Check the server console before updating.'));
        else resolve();
      }
      stopping.once('close', done);
      stopping.stdin.write('stop\n', err => { if (err) { clearTimeout(timeout); stopping.off('close', done); reject(new Error('Could not send stop command')); } });
    });
  }

  async function build() {
    state = 'updating';
    await new Promise((resolve, reject) => {
      let buildProcess;
      try {
        buildProcess = spawnServer(path.join(root, processPlatformWrapper()), ['build', '--console=plain'], {
          cwd: root, env: { ...global.process.env }, stdio: ['ignore', 'pipe', 'pipe']
        });
      } catch (failure) { reject(failure); return; }
      buildProcess.stdout.on('data', data => global.process.stdout.write(data));
      buildProcess.stderr.on('data', data => global.process.stderr.write(data));
      buildProcess.once('error', reject);
      buildProcess.once('close', code => code === 0 ? resolve() : reject(new Error('Build failed. Check the server console.')));
    });
  }

  async function command(action) {
    if (busy) throw new Error('A server operation is already in progress');
    busy = true;
    try {
      if (action === 'start') await start();
      else if (action === 'stop') await stop();
      else if (action === 'restart') {
        restarting = true;
        try { await stop(); await start(); }
        catch (error) { if (!child) onExit(); throw error; }
        finally { restarting = false; }
      }
      else if (action === 'update') {
        if (!child && await checkServer()) throw new Error('A server is running outside this panel. Stop it before updating.');
        updating = true;
        restarting = true;
        try {
          if (child) await stop();
          await build();
          await start();
        } catch (failure) {
          if (!child) { state = 'stopped'; error = failure.message; onExit(); }
          throw failure;
        } finally { restarting = false; updating = false; }
      }
      else throw new Error('Invalid operation');
    } finally { busy = false; }
  }

  async function status() {
    const reachable = await checkServer();
    const starting = restarting || (busy && state === 'starting');
    return { reachable, managed: !!child || starting, state: updating ? 'updating' : starting ? 'starting' : child ? state : reachable ? 'external' : 'stopped', error };
  }
  return { command, status };
}

function processPlatformWrapper() { return global.process.platform === 'win32' ? 'gradlew.bat' : 'gradlew'; }
module.exports = { createServerControl };
