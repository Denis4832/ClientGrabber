import assert from 'node:assert/strict';
import fs from 'node:fs';
import net from 'node:net';
import os from 'node:os';
import path from 'node:path';
import { spawn, spawnSync } from 'node:child_process';
import { once } from 'node:events';
import { fileURLToPath } from 'node:url';

const project = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const root = fs.mkdtempSync(path.join(os.tmpdir(), 'logger-launcher-'));
const sandbox = path.join(root, 'проверка кириллицы');
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
let child;
let occupied;
async function until(check, description) {
  const end = Date.now() + 30000;
  while (!check()) {
    if (Date.now() > end) throw new Error(description);
    await sleep(25);
  }
}
function launch() {
  const process = spawn('powershell.exe', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', path.join(sandbox, 'start-server.ps1')],
    { cwd: sandbox, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
  const state = { process, output: '', exited: false };
  process.stdout.on('data', chunk => { state.output += chunk.toString('utf8'); });
  process.stderr.on('data', chunk => { state.output += chunk.toString('utf8'); });
  process.on('exit', code => { state.exited = true; state.code = code; });
  return state;
}
function killTree(state) {
  if (state && !state.exited) spawnSync('taskkill.exe', ['/PID', String(state.process.pid), '/T', '/F'], { windowsHide: true, stdio: 'pipe' });
}
try {
  fs.mkdirSync(path.join(sandbox, 'server', 'proxy'), { recursive: true });
  for (const name of ['start-server.ps1', 'build-plugin.ps1', 'java-tools.ps1']) fs.copyFileSync(path.join(project, name), path.join(sandbox, name));
  const source = 'plugin-src/src/main/java/local/logger';
  fs.mkdirSync(path.join(sandbox, source), { recursive: true });
  for (const name of fs.readdirSync(path.join(project, source))) {
    if (name.endsWith('.java')) fs.copyFileSync(path.join(project, source, name), path.join(sandbox, source, name));
  }
  const portReservation = net.createServer();
  portReservation.listen(0, '127.0.0.1'); await once(portReservation, 'listening');
  const port = portReservation.address().port;
  await new Promise(resolve => portReservation.close(resolve));
  fs.writeFileSync(path.join(sandbox, 'server', 'proxy', 'localplayerlogger-proxy.properties'),
    `listen_host=127.0.0.1\nlisten_port=${port}\nbackend_host=127.0.0.1\nbackend_port=9\n`);
  child = launch();
  await until(() => child.exited || (child.output.includes(`ПРОКСИ ГОТОВ: 127.0.0.1:${port}`)
    && child.output.includes('проверка кириллицы')), 'Launcher did not become ready');
  assert.equal(child.exited, false, child.output);
  assert.ok(child.output.includes('НЕ ЗАКРЫВАЙТЕ КОНСОЛЬ ВО ВРЕМЯ ИГРЫ'), child.output);
  assert.ok(child.output.includes('проверка кириллицы'), child.output);
  assert.ok(!child.output.includes('\uFFFD'), child.output);
  killTree(child); await until(() => child.exited, 'Launcher process tree did not stop');
  occupied = net.createServer(); occupied.listen(port, '127.0.0.1'); await once(occupied, 'listening');
  child = launch();
  await until(() => child.exited, 'Occupied port did not cause startup to fail');
  assert.equal(child.code, 1, child.output);
  assert.ok(child.output.includes('Локальный порт занят'), child.output);
  assert.ok(child.output.includes('ОШИБКА'), child.output);
  console.log('Launcher smoke: Windows PowerShell 5.1, Cyrillic path, Russian warning, UTF-8 and occupied-port error PASS.');
} finally {
  killTree(child);
  if (occupied) await new Promise(resolve => occupied.close(resolve));
  const absolute = path.resolve(root);
  assert.ok(absolute.startsWith(path.resolve(os.tmpdir()) + path.sep) && path.basename(absolute).startsWith('logger-launcher-'));
  fs.rmSync(absolute, { recursive: true, force: true });
}
