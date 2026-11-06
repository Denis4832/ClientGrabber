import { test } from 'node:test';
import assert from 'node:assert/strict';
import net from 'node:net';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { fileURLToPath } from 'node:url';
import zlib from 'node:zlib';

const project = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const jar = process.env.LOGGER_TEST_JAR || path.join(project, 'server', 'proxy', 'LocalPlayerLoggerProxy.jar');
const java = process.env.LOGGER_TEST_JAVA
  || (process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java');
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));

function vi(value) {
  let n = value >>> 0;
  const out = [];
  do {
    const next = n & 127;
    n >>>= 7;
    out.push(next | (n ? 128 : 0));
  } while (n);
  return Buffer.from(out);
}

function readVi(bytes, offset = 0) {
  let value = 0;
  for (let i = 0; i < 5; i++) {
    if (offset + i >= bytes.length) return null;
    const byte = bytes[offset + i];
    value |= (byte & 127) << (7 * i);
    if (!(byte & 128)) return { value: value >>> 0, length: i + 1 };
  }
  throw new Error('Malformed test VarInt');
}

function str(value) {
  const bytes = Buffer.from(value, 'utf8');
  return Buffer.concat([vi(bytes.length), bytes]);
}

function numeric(value, kind = 'double') {
  const out = Buffer.alloc(kind === 'double' ? 8 : 4);
  if (kind === 'double') out.writeDoubleBE(value);
  else if (kind === 'float') out.writeFloatBE(value);
  else out.writeUInt32BE(value);
  return out;
}

function frame(id, body = Buffer.alloc(0), compressed = false) {
  let payload = Buffer.concat([vi(id), body]);
  if (compressed) payload = Buffer.concat([vi(payload.length), zlib.deflateSync(payload)]);
  return Buffer.concat([vi(payload.length), payload]);
}

function unpack(bytes, compressed = false) {
  let payload = bytes.subarray(readVi(bytes).length);
  if (compressed) {
    const size = readVi(payload);
    payload = payload.subarray(size.length);
    if (size.value) payload = zlib.inflateSync(payload);
  }
  const id = readVi(payload);
  return { id: id.value, body: payload.subarray(id.length) };
}

async function until(predicate, message, timeoutMs = 5000) {
  const deadline = Date.now() + timeoutMs;
  while (!predicate()) {
    if (Date.now() > deadline) throw new Error(message);
    await delay(5);
  }
}

class Peer {
  constructor(socket) {
    this.socket = socket;
    this.buffer = Buffer.alloc(0);
    this.closed = false;
    socket.on('data', bytes => { this.buffer = Buffer.concat([this.buffer, bytes]); });
    socket.on('close', () => { this.closed = true; });
    socket.on('error', () => {});
  }

  send(bytes) { this.socket.write(bytes); }

  async frame() {
    let prefix;
    await until(() => {
      prefix = readVi(this.buffer);
      return prefix && this.buffer.length >= prefix.length + prefix.value;
    }, 'Expected a forwarded Minecraft frame');
    const size = prefix.length + prefix.value;
    const result = this.buffer.subarray(0, size);
    this.buffer = this.buffer.subarray(size);
    return result;
  }

  async bytes(size) {
    await until(() => this.buffer.length >= size, 'Expected opaque forwarded bytes');
    const result = this.buffer.subarray(0, size);
    this.buffer = this.buffer.subarray(size);
    return result;
  }
}

async function freePort() {
  const server = net.createServer();
  server.listen(0, '127.0.0.1');
  await once(server, 'listening');
  const port = server.address().port;
  await new Promise(resolve => server.close(resolve));
  return port;
}

async function harness(t, extraProperties = {}) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'localplayerlogger-test-'));
  const proxyDir = path.join(root, 'proxy');
  fs.mkdirSync(proxyDir);
  const backends = [];
  const backendServer = net.createServer(socket => backends.push(new Peer(socket)));
  backendServer.listen(0, '127.0.0.1');
  await once(backendServer, 'listening');
  const port = await freePort();
  const settings = {
    listen_host: '127.0.0.1', listen_port: port,
    backend_host: '127.0.0.1', backend_port: backendServer.address().port,
    upstream_host: 'example.test', upstream_port: 25565,
    ...extraProperties,
  };
  fs.writeFileSync(path.join(proxyDir, 'localplayerlogger-proxy.properties'),
    Object.entries(settings).map(([key, value]) => `${key}=${value}\n`).join(''));
  const child = spawn(java, ['-Dfile.encoding=UTF-8', '-Dsun.stdout.encoding=UTF-8', '-Xms32M', '-Xmx128M', '-jar', jar], {
    cwd: proxyDir, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'],
  });
  let output = '';
  let exited = false;
  let startError;
  child.stdout.on('data', bytes => { output += bytes.toString(); });
  child.stderr.on('data', bytes => { output += bytes.toString(); });
  child.on('error', error => { startError = error; exited = true; });
  child.on('exit', () => { exited = true; });
  const clients = [];
  let stopped = false;
  async function stop() {
    if (stopped) return;
    stopped = true;
    for (const peer of clients) peer.socket.destroy();
    for (const peer of backends) peer.socket.destroy();
    await until(() => {
      const logs = path.join(root, 'session-logs');
      if (!fs.existsSync(logs)) return true;
      return fs.readdirSync(logs).every(name => {
        try { return Boolean(JSON.parse(fs.readFileSync(path.join(logs, name, 'metadata.json'), 'utf8')).closedAt); }
        catch { return false; }
      });
    }, 'Proxy did not finalize session metadata after its sockets closed');
    if (!exited) child.kill();
    await until(() => exited, `Test proxy process did not stop: ${output}`);
    await new Promise(resolve => backendServer.close(resolve));
  }
  t.after(async () => {
    await stop();
    const absolute = path.resolve(root);
    assert.ok(absolute.startsWith(path.resolve(os.tmpdir()) + path.sep));
    assert.ok(path.basename(absolute).startsWith('localplayerlogger-test-'));
    fs.rmSync(absolute, { force: true, recursive: true });
  });
  await until(() => {
    if (exited) throw startError || new Error(`Test proxy exited during startup: ${output}`);
    return output.includes(`:${port}`);
  }, `Test proxy did not report its bound port: ${output}`);
  return {
    root, port, stop,
    output: () => output,
    async connect(waitForBackend = true) {
      const index = backends.length;
      const socket = net.createConnection(port, '127.0.0.1');
      const client = new Peer(socket);
      clients.push(client);
      await once(socket, 'connect');
      let actual;
      async function peer() {
        if (!actual) {
          await until(() => backends.length > index, 'Backend connection did not open');
          actual = backends[index];
        }
        return actual;
      }
      const lazyBackend = {
        async frame() { return (await peer()).frame(); },
        async bytes(size) { return (await peer()).bytes(size); },
        send(bytes) {
          assert.ok(actual, 'Read the first backend frame before sending its response');
          actual.send(bytes);
        },
        get socket() {
          assert.ok(actual, 'Read the first backend frame before accessing its socket');
          return actual.socket;
        },
      };
      return { client, backend: waitForBackend ? lazyBackend : null };
    },
    sessions() {
      const logs = path.join(root, 'session-logs');
      return fs.existsSync(logs) ? fs.readdirSync(logs).map(name => path.join(logs, name)) : [];
    },
  };
}

function rows(session, filename) {
  const content = fs.readFileSync(path.join(session, filename), 'utf8').trim();
  return content ? content.split('\n').map(row => JSON.parse(row)) : [];
}

function handshake(protocol, state = 2) {
  return frame(0, Buffer.concat([vi(protocol), str('localhost'), Buffer.from([0x63, 0xdd]), vi(state)]));
}

function checkHandshake(bytes, protocol, state) {
  const packet = unpack(bytes);
  assert.equal(packet.id, 0);
  const proto = readVi(packet.body);
  assert.equal(proto.value, protocol);
  const size = readVi(packet.body, proto.length);
  const offset = proto.length + size.length;
  assert.equal(packet.body.subarray(offset, offset + size.value).toString(), 'example.test');
  assert.equal(packet.body.readUInt16BE(offset + size.value), 25565);
  assert.equal(readVi(packet.body, offset + size.value + 2).value, state);
}

test('baseline: rewrites handshake host while preserving modern protocol and bidirectional status bytes', async t => {
  const h = await harness(t);
  const pair = await h.connect();
  const initial = handshake(770, 1);
  // A TCP read is not necessarily a full Minecraft frame.
  pair.client.send(initial.subarray(0, 2));
  pair.client.send(initial.subarray(2));
  checkHandshake(await pair.backend.frame(), 770, 1);
  const request = frame(0);
  const ping = frame(1, Buffer.from('0011223344556677', 'hex'));
  pair.client.send(Buffer.concat([request, ping]));
  assert.deepEqual(await pair.backend.frame(), request);
  assert.deepEqual(await pair.backend.frame(), ping);
  const response = frame(0, str('{"version":{"name":"test","protocol":770},"players":{"max":1,"online":0},"description":{"text":"Привет"}}'));
  const pong = frame(1, Buffer.from('0011223344556677', 'hex'));
  pair.backend.send(Buffer.concat([response, pong]));
  assert.deepEqual(await pair.client.frame(), response);
  assert.deepEqual(await pair.client.frame(), pong);
  await h.stop();
  const [session] = h.sessions();
  assert.deepEqual(fs.readFileSync(path.join(session, 'client_to_server.bin')), Buffer.concat([initial, request, ping]));
  assert.deepEqual(fs.readFileSync(path.join(session, 'server_to_client.bin')), Buffer.concat([response, pong]));
  assert.equal(rows(session, 'client_to_server.jsonl').length, 3);
  assert.equal(rows(session, 'server_to_client.jsonl').length, 2);
  assert.equal(JSON.parse(fs.readFileSync(path.join(session, 'metadata.json'), 'utf8')).proxyMode, true);
});

test('baseline: existing 1.16.5 login and unknown PLAY frames remain transparent', async t => {
  const h = await harness(t);
  const pair = await h.connect();
  const initial = handshake(754);
  pair.client.send(initial);
  checkHandshake(await pair.backend.frame(), 754, 2);
  const start = frame(0, str('LegacyPlayer'));
  pair.client.send(start);
  assert.deepEqual(await pair.backend.frame(), start);
  const success = frame(2, Buffer.concat([Buffer.alloc(16), str('LegacyPlayer')]));
  pair.backend.send(success);
  assert.deepEqual(await pair.client.frame(), success);
  const arbitrary = frame(0x70, Buffer.from('exact-payload'));
  pair.client.send(arbitrary);
  assert.deepEqual(await pair.backend.frame(), arbitrary);
  await h.stop();
  const [session] = h.sessions();
  const packets = rows(session, 'client_to_server.jsonl');
  assert.equal(packets.at(-1).packetData.phase, 'PLAY');
  assert.equal(packets.at(-1).packetData.packetId, 0x70);
  assert.equal(packets.at(-1).packetData.frameBase64, arbitrary.toString('base64'));
  assert.ok(rows(session, 'events.jsonl').some(row => row.type === 'login_start' && row.playerName === 'LegacyPlayer'));
});

async function enterPlay(pair, { protocol = 769, compressed = false, username = 'TestPlayer' } = {}) {
  pair.client.send(handshake(protocol));
  checkHandshake(await pair.backend.frame(), protocol, 2);
  pair.client.send(frame(0, Buffer.concat([str(username), Buffer.alloc(16)])));
  await pair.backend.frame();
  if (compressed) {
    pair.backend.send(frame(3, vi(1)));
    assert.equal(unpack(await pair.client.frame()).id, 3);
  }
  pair.backend.send(frame(2, Buffer.concat([Buffer.alloc(16), str(username), vi(0)]), compressed));
  assert.equal(unpack(await pair.client.frame(), compressed).id, 2);
  pair.client.send(frame(3, Buffer.alloc(0), compressed));
  assert.equal(unpack(await pair.backend.frame(), compressed).id, 3);
  pair.backend.send(frame(3, Buffer.alloc(0), compressed));
  assert.equal(unpack(await pair.client.frame(), compressed).id, 3);
  pair.client.send(frame(3, Buffer.alloc(0), compressed));
  assert.equal(unpack(await pair.backend.frame(), compressed).id, 3);
}

function positionLook(x, y, z, yaw, pitch, flags = 3) {
  return Buffer.concat([
    ...[x, y, z].map(value => numeric(value)),
    numeric(yaw, 'float'), numeric(pitch, 'float'), Buffer.from([flags]),
  ]);
}

function anonymousNbtString(value) {
  const text = Buffer.from(value, 'utf8');
  const header = Buffer.alloc(3);
  header[0] = 8;
  header.writeUInt16BE(text.length, 1);
  return Buffer.concat([header, text]);
}

for (const protocol of [769, 770]) {
  test(`protocol ${protocol}: modern phases, four movement variants and precise movement deltas`, async t => {
    const h = await harness(t);
    const pair = await h.connect();
    await enterPlay(pair, { protocol });
    const packets = [
      frame(0x1d, positionLook(10, 64, -3, 179, 10)),
      frame(0x1c, Buffer.concat([...[11, 64.5, -3].map(v => numeric(v)), Buffer.from([1])])),
      frame(0x1e, Buffer.concat([numeric(-179, 'float'), numeric(12, 'float'), Buffer.from([0])])),
      frame(0x1f, Buffer.from([3])),
    ];
    for (const packet of packets) {
      await delay(12);
      pair.client.send(packet.subarray(0, 2));
      pair.client.send(packet.subarray(2));
      assert.deepEqual(await pair.backend.frame(), packet);
    }
    await h.stop();
    const [session] = h.sessions();
    const movementRows = rows(session, 'movements.jsonl');
    const movements = movementRows.map(row => row.movement);
    assert.deepEqual(movements.map(m => m.variant), ['position_look', 'position', 'look', 'flying']);
    assert.equal(movements[0].x, 10);
    assert.equal(movements[0].horizontalCollision, true);
    assert.equal(movements[1].delta.x, 1);
    assert.equal(movements[1].delta.y, 0.5);
    assert.ok(movements[1].dtMs > 0);
    assert.ok(movements[1].speedBlocksPerSecond > 0);
    assert.equal(movements[2].x, 11);
    assert.equal(movements[2].delta.yaw, 2);
    assert.equal(movements[2].onGround, false);
    assert.equal(movements[3].horizontalCollision, true);
    assert.equal(typeof movementRows[0].elapsedNs, 'string');
    assert.ok(Number.isFinite(movements[0].lookVector.x));
    const clientPackets = rows(session, 'client_to_server.jsonl');
    const serverPackets = rows(session, 'server_to_client.jsonl');
    const packet = (packets, name) => packets.find(row => row.packetData.packetName === name).packetData;
    assert.equal(packet(clientPackets, 'login_acknowledged').phase, 'LOGIN');
    assert.equal(packet(clientPackets, 'finish_configuration').phase, 'CONFIGURATION');
    assert.equal(packet(serverPackets, 'success').phase, 'LOGIN');
    assert.equal(packet(clientPackets, 'position_look').phase, 'PLAY');
    assert.ok(clientPackets.every(row => row.packetData.decodeError == null));
    const combined = [...movementRows, ...clientPackets, ...serverPackets, ...rows(session, 'events.jsonl')];
    assert.equal(new Set(combined.map(row => row.seq)).size, combined.length, 'Sequence numbers must be unique across all streams');
  });
}

test('compressed play packets preserve exact bytes and expose input, sprint and attack actions', async t => {
  const h = await harness(t);
  const pair = await h.connect();
  await enterPlay(pair, { compressed: true });
  const packets = [
    frame(0x1d, positionLook(0, 80, 0, 90, 0), true),
    frame(0x29, Buffer.from([0b1010001]), true),
    frame(0x28, Buffer.concat([vi(7), vi(3), vi(0)]), true),
    frame(0x18, Buffer.concat([vi(13), vi(1), Buffer.from([1])]), true),
  ];
  pair.client.send(Buffer.concat(packets));
  for (const packet of packets) assert.deepEqual(await pair.backend.frame(), packet);
  await h.stop();
  const [session] = h.sessions();
  const packetsLog = rows(session, 'client_to_server.jsonl').map(row => row.packetData);
  const input = packetsLog.find(packet => packet.packetName === 'player_input');
  assert.equal(input.fields.inputs.forward, true);
  assert.equal(input.fields.inputs.jump, true);
  assert.equal(input.fields.inputs.sprint, true);
  assert.equal(input.fields.inputs.backward, false);
  const sprint = packetsLog.find(packet => packet.packetName === 'entity_action').fields;
  assert.equal(sprint.actionId, 3);
  assert.equal(sprint.action, 'start_sprinting');
  assert.equal(packetsLog.find(packet => packet.packetName === 'use_entity').fields.target, 13);
  assert.equal(rows(session, 'movements.jsonl').length, 1);
  const actions = rows(session, 'actions.jsonl').map(row => row.action);
  for (const name of ['player_input', 'entity_action', 'use_entity']) {
    assert.ok(actions.some(action => action.packet === name), `Missing action ${name}`);
  }
});

test('relative server position correction becomes the baseline for the next client movement', async t => {
  const h = await harness(t);
  const pair = await h.connect();
  await enterPlay(pair);
  pair.client.send(frame(0x1d, positionLook(10, 64, 10, 20, 5, 1)));
  await pair.backend.frame();
  const correction = Buffer.concat([
    vi(42), ...[5, 70, -2, 0, 0, 0].map(v => numeric(v)),
    numeric(30, 'float'), numeric(0, 'float'), numeric(1 | 4 | 8, 'uint'),
  ]);
  pair.backend.send(frame(0x42, correction));
  await pair.client.frame();
  pair.client.send(frame(0, vi(42)));
  await pair.backend.frame();
  pair.client.send(frame(0x1d, positionLook(16, 70, 8, 50, 0, 1)));
  await pair.backend.frame();
  await h.stop();
  const [session] = h.sessions();
  const movement = rows(session, 'movements.jsonl').at(-1).movement;
  assert.equal(movement.delta.x, 1);
  assert.equal(movement.delta.y, 0);
  assert.equal(movement.delta.z, 0);
  assert.equal(movement.delta.yaw, 0);
});

test('unknown protocol forwards packets but records no fabricated movement', async t => {
  const h = await harness(t);
  const pair = await h.connect();
  pair.client.send(handshake(9999));
  checkHandshake(await pair.backend.frame(), 9999, 2);
  const payload = frame(0x1d, positionLook(100, 20, 300, 45, 0));
  pair.client.send(payload);
  assert.deepEqual(await pair.backend.frame(), payload);
  await h.stop();
  const [session] = h.sessions();
  assert.equal(rows(session, 'movements.jsonl').length, 0);
  assert.ok(rows(session, 'events.jsonl').some(row => /unknown|unsupported|schema/i.test(JSON.stringify(row))));
});

test('encryption response switches both directions to opaque byte forwarding', async t => {
  const h = await harness(t);
  const pair = await h.connect();
  pair.client.send(handshake(769));
  await pair.backend.frame();
  pair.client.send(frame(0, Buffer.concat([str('Encrypted'), Buffer.alloc(16)])));
  await pair.backend.frame();
  const request = frame(1, Buffer.concat([str(''), vi(4), Buffer.alloc(4), vi(4), Buffer.alloc(4), Buffer.from([1])]));
  pair.backend.send(request);
  assert.deepEqual(await pair.client.frame(), request);
  const response = frame(1, Buffer.concat([vi(16), Buffer.alloc(16), vi(16), Buffer.alloc(16)]));
  pair.client.send(response);
  assert.deepEqual(await pair.backend.frame(), response);
  const opaqueClient = Buffer.from([255, 255, 255, 255, 255, 255, 12, 88, 1]);
  const opaqueServer = Buffer.from([253, 252, 251, 250, 249, 248, 247]);
  pair.client.send(opaqueClient);
  pair.backend.send(opaqueServer);
  assert.deepEqual(await pair.backend.bytes(opaqueClient.length), opaqueClient);
  assert.deepEqual(await pair.client.bytes(opaqueServer.length), opaqueServer);
  await h.stop();
  const [session] = h.sessions();
  assert.equal(rows(session, 'movements.jsonl').length, 0);
  assert.ok(rows(session, 'events.jsonl').some(row => row.type === 'encryption_enabled'));
  assert.ok(fs.readFileSync(path.join(session, 'client_to_server.bin')).subarray(-opaqueClient.length).equals(opaqueClient));
  assert.ok(fs.readFileSync(path.join(session, 'server_to_client.bin')).subarray(-opaqueServer.length).equals(opaqueServer));
});

test('backend closing first closes the player socket and finalizes the connection log', async t => {
  const h = await harness(t);
  const pair = await h.connect();
  pair.client.send(handshake(769));
  await pair.backend.frame();
  pair.backend.socket.end();
  await until(() => pair.client.closed, 'Player socket remained open after backend ended');
  await h.stop();
  const [session] = h.sessions();
  assert.ok(rows(session, 'events.jsonl').some(row => row.type === 'client_disconnected'));
});

test('oversized frame length terminates only the malformed connection', async t => {
  const h = await harness(t);
  const bad = await h.connect();
  bad.client.send(vi(64 * 1024 * 1024));
  await until(() => bad.client.closed, 'Oversized frame was not rejected');
  const good = await h.connect();
  await enterPlay(good);
  good.client.send(frame(0x1d, positionLook(1, 64, 2, 0, 0)));
  await good.backend.frame();
  await h.stop();
  assert.equal(h.sessions().length, 2);
  const counts = h.sessions().map(session => rows(session, 'movements.jsonl').length).sort();
  assert.deepEqual(counts, [0, 1]);
});

test('malformed frame VarInt terminates without hanging the relay', async t => {
  const h = await harness(t);
  const pair = await h.connect();
  pair.client.send(Buffer.from([128, 128, 128, 128, 128, 1]));
  await until(() => pair.client.closed, 'Malformed VarInt did not terminate the session');
});

test('oversized decompression declaration is rejected before memory allocation', async t => {
  const h = await harness(t);
  const pair = await h.connect();
  await enterPlay(pair, { compressed: true });
  const payload = Buffer.concat([vi(64 * 1024 * 1024), zlib.deflateSync(Buffer.from([0x1f, 1]))]);
  pair.client.send(Buffer.concat([vi(payload.length), payload]));
  await until(() => pair.client.closed, 'Oversized decompression declaration was not rejected');
});

test('backend connection refusal returns a readable Russian login disconnect', async t => {
  const h = await harness(t, { backend_port: await freePort() });
  const pair = await h.connect(false);
  pair.client.send(Buffer.concat([
    handshake(769), frame(0, Buffer.concat([str('ConnectionFailure'), Buffer.alloc(16)])),
  ]));
  const response = unpack(await pair.client.frame());
  assert.equal(response.id, 0);
  const size = readVi(response.body);
  const reason = response.body.subarray(size.length, size.length + size.value).toString('utf8');
  assert.match(reason, /Локальн|прокси|сервер|соединен/i);
  assert.doesNotThrow(() => JSON.parse(reason));
});

test('backend login kick is forwarded exactly and its Russian reason appears in the console', async t => {
  const h = await harness(t);
  const pair = await h.connect();
  pair.client.send(handshake(769));
  await pair.backend.frame();
  pair.client.send(frame(0, Buffer.concat([str('KickedPlayer'), Buffer.alloc(16)])));
  await pair.backend.frame();
  const kick = frame(0, str('{"text":"Сервер временно недоступен"}'));
  pair.backend.send(kick);
  assert.deepEqual(await pair.client.frame(), kick);
  await until(() => h.output().includes('Сервер временно недоступен'), 'Backend kick reason was not shown in the console');
});

test('compressed PLAY backend EOF returns a correctly encoded Russian in-game NBT kick', async t => {
  const h = await harness(t);
  const pair = await h.connect();
  await enterPlay(pair, { compressed: true });
  pair.backend.socket.end();
  const kick = unpack(await pair.client.frame(), true);
  assert.equal(kick.id, 0x1d);
  assert.equal(kick.body[0], 8, 'Modern PLAY chat component must use an anonymous NBT StringTag');
  const length = kick.body.readUInt16BE(1);
  assert.equal(kick.body.length, length + 3);
  const reason = kick.body.subarray(3).toString('utf8');
  assert.match(reason, /Локальн|прокси|сервер|соединен/i);
  await until(() => pair.client.closed, 'Client remained connected after the local EOF kick');
});

test('compressed backend PLAY NBT kick is forwarded exactly and shown in Russian', async t => {
  const h = await harness(t);
  const pair = await h.connect();
  await enterPlay(pair, { compressed: true });
  const reason = 'Технические работы: повторите вход позже';
  const kick = frame(0x1d, anonymousNbtString(reason), true);
  pair.backend.send(kick);
  assert.deepEqual(await pair.client.frame(), kick);
  await until(() => h.output().includes(reason), 'PLAY NBT kick reason was not shown in the console');
});

test('PLAY reconfiguration acknowledges each phase and resumes movement decoding', async t => {
  const h = await harness(t);
  const pair = await h.connect();
  await enterPlay(pair, { compressed: true });
  const startConfiguration = frame(0x70, Buffer.alloc(0), true);
  pair.backend.send(startConfiguration);
  assert.deepEqual(await pair.client.frame(), startConfiguration);
  const acknowledge = frame(0x0e, Buffer.alloc(0), true);
  pair.client.send(acknowledge);
  assert.deepEqual(await pair.backend.frame(), acknowledge);
  const finish = frame(3, Buffer.alloc(0), true);
  pair.backend.send(finish);
  assert.deepEqual(await pair.client.frame(), finish);
  pair.client.send(finish);
  assert.deepEqual(await pair.backend.frame(), finish);
  const move = frame(0x1d, positionLook(17, 72, -9, 45, 15), true);
  pair.client.send(move);
  assert.deepEqual(await pair.backend.frame(), move);
  await h.stop();
  const [session] = h.sessions();
  const clientPackets = rows(session, 'client_to_server.jsonl').map(row => row.packetData);
  const serverPackets = rows(session, 'server_to_client.jsonl').map(row => row.packetData);
  assert.equal(serverPackets.find(packet => packet.packetName === 'start_configuration').phase, 'PLAY');
  assert.equal(clientPackets.find(packet => packet.packetName === 'configuration_acknowledged').phase, 'PLAY');
  assert.equal(clientPackets.filter(packet => packet.packetName === 'finish_configuration' && packet.phase === 'CONFIGURATION').length, 2);
  const [movement] = rows(session, 'movements.jsonl').map(row => row.movement);
  assert.equal(movement.variant, 'position_look');
  assert.equal(movement.x, 17);
  assert.equal(movement.z, -9);
});

function burstFrames(compressed) {
  let seed = 0x12345678;
  return Array.from({ length: 3000 }, (_, index) => {
    // Unknown packet IDs test transparent transport without changing Minecraft state.
    // Occasional larger, poorly compressible payloads also cross TCP-read boundaries.
    const payload = Buffer.alloc(index % 100 === 0 ? 32768 : 12);
    for (let offset = 0; offset < payload.length; offset++) {
      seed ^= seed << 13; seed ^= seed >>> 17; seed ^= seed << 5;
      payload[offset] = seed & 255;
    }
    payload.writeUInt32BE(index, 0);
    return frame(0x1ff, payload, compressed);
  });
}

async function exchangeBurst(pair, serverFrames, clientFrames) {
  const serverBytes = Buffer.concat(serverFrames);
  const clientBytes = Buffer.concat(clientFrames);
  async function feed(peer, bytes) {
    const sizes = [1, 2, 31, 65536, 257, 4096];
    let offset = 0, chunk = 0;
    while (offset < bytes.length) {
      const next = Math.min(bytes.length, offset + sizes[chunk % sizes.length]);
      peer.send(bytes.subarray(offset, next));
      offset = next;
      if (++chunk % sizes.length === 0) await delay(1);
    }
  }
  const start = performance.now();
  const [, , receivedServer, receivedClient] = await Promise.all([
    feed(pair.backend, serverBytes), feed(pair.client, clientBytes),
    pair.client.bytes(serverBytes.length), pair.backend.bytes(clientBytes.length),
  ]);
  assert.deepEqual(receivedServer, serverBytes, 'Server burst was changed, lost, reordered or duplicated');
  assert.deepEqual(receivedClient, clientBytes, 'Concurrent player traffic was changed, lost, reordered or duplicated');
  assert.equal(pair.client.closed, false, 'Player connection closed during the burst');
  assert.equal(pair.backend.socket.destroyed, false, 'Backend connection closed during the burst');
  return performance.now() - start;
}

function assertBurstCapture(session, serverFrames, clientFrames) {
  const serverRows = rows(session, 'server_to_client.jsonl');
  const clientRows = rows(session, 'client_to_server.jsonl');
  for (const [direction, packetRows] of [['server_to_client', serverRows], ['client_to_server', clientRows]]) {
    const captured = Buffer.concat(packetRows.map(row => Buffer.from(row.packetData.frameBase64, 'base64')));
    assert.deepEqual(fs.readFileSync(path.join(session, `${direction}.bin`)), captured,
      `${direction}: binary capture differs from the complete JSONL packet sequence`);
    assert.ok(packetRows.every((row, index) => index === 0 || row.seq > packetRows[index - 1].seq),
      `${direction}: packet sequence is not strictly increasing`);
    assert.ok(packetRows.every(row => row.packetData.decodeError == null), `${direction}: unexpected decoder failure`);
  }
  const capturedServerBurst = serverRows.filter(row => row.packetData.packetId === 0x1ff);
  assert.equal(capturedServerBurst.length, serverFrames.length, 'Finalized log lost server packets');
  assert.deepEqual(Buffer.concat(capturedServerBurst.map(row => Buffer.from(row.packetData.frameBase64, 'base64'))), Buffer.concat(serverFrames));
  // Client burst fixtures use position_look or keep_alive; the login fixture sends neither.
  const capturedClientBurst = clientRows.filter(row => ['position_look', 'keep_alive'].includes(row.packetData.packetName));
  assert.equal(capturedClientBurst.length, clientFrames.length, 'Finalized log lost player packets');
  assert.deepEqual(Buffer.concat(capturedClientBurst.map(row => Buffer.from(row.packetData.frameBase64, 'base64'))), Buffer.concat(clientFrames));
  const allRows = ['events.jsonl', 'movements.jsonl', 'actions.jsonl', 'client_to_server.jsonl', 'server_to_client.jsonl'].flatMap(name => rows(session, name));
  assert.equal(new Set(allRows.map(row => row.seq)).size, allRows.length, 'Global sequence numbers must remain unique under bidirectional load');
  assert.equal(rows(session, 'movements.jsonl').length, 100, 'Every movement packet must be retained');
  assert.ok(!rows(session, 'events.jsonl').some(row => ['proxy_error', 'upstream_error', 'downstream_error'].includes(row.type)),
    'A healthy burst should not produce a proxy/network error');
}

test('burst regression: 1.16.5 preserves 3000 server frames and concurrent movement without log loss', async t => {
  const h = await harness(t);
  const pair = await h.connect();
  pair.client.send(handshake(754));
  await pair.backend.frame();
  pair.client.send(frame(0, str('BurstLegacy')));
  await pair.backend.frame();
  pair.backend.send(frame(2, Buffer.concat([Buffer.alloc(16), str('BurstLegacy')])));
  await pair.client.frame();
  const serverFrames = burstFrames(false);
  const clientFrames = Array.from({ length: 100 }, (_, index) => [
    frame(0x13, positionLook(index / 10, 64, -index / 20, index, 0, 1)),
    frame(0x10, Buffer.from('0011223344556677', 'hex')),
  ]).flat();
  const elapsed = await exchangeBurst(pair, serverFrames, clientFrames);
  await h.stop();
  const [session] = h.sessions();
  assertBurstCapture(session, serverFrames, clientFrames);
  t.diagnostic(`Legacy burst: ${serverFrames.length} S2C + ${clientFrames.length} C2S packets delivered in ${elapsed.toFixed(1)} ms; this is a transport stress check, not a reproduction of the real client hang.`);
});

test('burst regression: compressed 1.21.11 traffic stays exact across reconfiguration and log finalization', async t => {
  const h = await harness(t);
  const pair = await h.connect();
  await enterPlay(pair, { protocol: 774, compressed: true, username: 'BurstModern' });
  const serverFrames = burstFrames(true);
  const clientFrames = Array.from({ length: 100 }, (_, index) => [
    frame(0x1e, positionLook(index / 10, 70, -index / 20, index, 0, 3), true),
    frame(0x1b, Buffer.from('0011223344556677', 'hex'), true),
  ]).flat();
  let elapsed = await exchangeBurst(pair, serverFrames.slice(0, 1500), clientFrames.slice(0, 100));
  const start = frame(0x74, Buffer.alloc(0), true);
  pair.backend.send(start); assert.deepEqual(await pair.client.frame(), start);
  const acknowledge = frame(0x0f, Buffer.alloc(0), true);
  pair.client.send(acknowledge); assert.deepEqual(await pair.backend.frame(), acknowledge);
  const finish = frame(3, Buffer.alloc(0), true);
  pair.backend.send(finish); assert.deepEqual(await pair.client.frame(), finish);
  pair.client.send(finish); assert.deepEqual(await pair.backend.frame(), finish);
  elapsed += await exchangeBurst(pair, serverFrames.slice(1500), clientFrames.slice(100));
  await h.stop();
  const [session] = h.sessions();
  assertBurstCapture(session, serverFrames, clientFrames);
  const clientRows = rows(session, 'client_to_server.jsonl');
  assert.equal(clientRows.filter(row => row.packetData.packetName === 'finish_configuration').length, 2);
  assert.equal(clientRows.at(-1).packetData.phase, 'PLAY');
  t.diagnostic(`Modern burst: ${serverFrames.length} S2C + ${clientFrames.length} C2S packets delivered in ${elapsed.toFixed(1)} ms, with a complete reconfiguration between halves.`);
});
