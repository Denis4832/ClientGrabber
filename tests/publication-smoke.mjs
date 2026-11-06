import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const project = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const jar = path.join(project, 'server', 'proxy', 'LocalPlayerLoggerProxy.jar');
const java = process.env.LOGGER_TEST_JAVA
  || (process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java');
const root = fs.mkdtempSync(path.join(os.tmpdir(), 'clientgrabber-publication-'));
try {
  const manifest = fs.readFileSync(path.join(project, 'publication-files.txt'), 'utf8').trim().split(/\r?\n/);
  assert.equal(new Set(manifest).size, manifest.length);
  for (const relative of manifest) {
    assert.ok(!/(^|\/)(backups|diagnostics|logs|session-logs|world|build)(\/|$)/.test(relative), relative);
    assert.ok(!/\.(jsonl|bin|log|class)$/.test(relative), relative);
    const source = path.join(project, relative);
    assert.ok(fs.statSync(source).isFile(), relative);
    if (!relative.endsWith('.jar')) {
      const text = fs.readFileSync(source, 'utf8');
      assert.ok(!/C:[\\/]Users[\\/][A-Za-z0-9_-]+[\\/]/i.test(text), `Personal path in ${relative}`);
      assert.ok(!/gh[pousr]_[A-Za-z0-9]{20,}/.test(text), `Credential in ${relative}`);
    }
  }
  assert.ok(!manifest.includes('server/proxy/localplayerlogger-proxy.properties'));
  assert.ok(fs.readFileSync(path.join(project, 'server/proxy/localplayerlogger-proxy.properties.example'), 'utf8')
    .includes('backend_host=server.example.invalid'));

  const proxyDir = path.join(root, 'proxy');
  fs.mkdirSync(proxyDir);
  const unconfigured = spawnSync(java, ['-Dfile.encoding=UTF-8', '-Dsun.stdout.encoding=UTF-8', '-jar', jar],
    { cwd: proxyDir, windowsHide: true, encoding: 'utf8', timeout: 10000 });
  assert.equal(unconfigured.status, 2, unconfigured.stdout + unconfigured.stderr);
  assert.match(unconfigured.stdout + unconfigured.stderr, /заглушка/);
  assert.ok(!unconfigured.stdout.includes('ПРОКСИ ГОТОВ'));
  assert.ok(!fs.existsSync(path.join(proxyDir, 'server', 'session-logs')));
  assert.match(fs.readFileSync(path.join(proxyDir, 'server', 'proxy', 'localplayerlogger-proxy.properties'), 'utf8'), /server\.example\.invalid/);

  const sandbox = path.join(root, 'launcher');
  fs.mkdirSync(path.join(sandbox, 'server', 'proxy'), { recursive: true });
  for (const name of ['start-server.ps1', 'java-tools.ps1', 'build-plugin.ps1']) {
    fs.copyFileSync(path.join(project, name), path.join(sandbox, name));
  }
  fs.copyFileSync(path.join(project, 'server/proxy/localplayerlogger-proxy.properties.example'),
    path.join(sandbox, 'server/proxy/localplayerlogger-proxy.properties.example'));
  const launcher = spawnSync('powershell.exe', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', path.join(sandbox, 'start-server.ps1')],
    { cwd: sandbox, windowsHide: true, encoding: 'utf8', timeout: 10000 });
  assert.equal(launcher.status, 1, launcher.stdout + launcher.stderr);
  assert.match(launcher.stdout, /backend_host/);
  assert.ok(fs.existsSync(path.join(sandbox, 'server/proxy/localplayerlogger-proxy.properties')));
  assert.ok(!fs.existsSync(path.join(sandbox, 'server/session-logs')));

  const archive = path.join(root, 'ClientGrabber.zip');
  const packaged = spawnSync('powershell.exe', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File',
    path.join(project, 'package-release.ps1'), '-OutputPath', archive],
    { cwd: project, windowsHide: true, encoding: 'utf8', timeout: 15000 });
  assert.equal(packaged.status, 0, packaged.stdout + packaged.stderr);
  const literal = archive.replaceAll("'", "''");
  const inspect = spawnSync('powershell.exe', ['-NoProfile', '-Command',
    `Add-Type -AssemblyName System.IO.Compression.FileSystem; $zip=[IO.Compression.ZipFile]::OpenRead('${literal}'); try { @($zip.Entries | ForEach-Object {$_.FullName}) | ConvertTo-Json -Compress } finally {$zip.Dispose()}`],
    { windowsHide: true, encoding: 'utf8', timeout: 10000 });
  assert.equal(inspect.status, 0, inspect.stderr);
  assert.deepEqual(JSON.parse(inspect.stdout).sort(), manifest.map(name => `ClientGrabber/${name}`).sort());
  assert.ok(fs.existsSync(`${archive}.sha256`));
  console.log(`Publication smoke: ${manifest.length} allowlisted files, placeholder guards, clean ZIP and no personal paths PASS.`);
} finally {
  const absolute = path.resolve(root);
  assert.ok(absolute.startsWith(path.resolve(os.tmpdir()) + path.sep) && path.basename(absolute).startsWith('clientgrabber-publication-'));
  fs.rmSync(absolute, { recursive: true, force: true });
}
