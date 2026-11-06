package local.logger;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.*;

/** Transparent relay. Only the destination in the initial handshake is changed. */
public final class MinecraftLoggerPlugin {
    private static final String CONFIG = "localplayerlogger-proxy.properties";
    private static final String DEFAULT_HOST = "server.example.invalid";
    private static final int MAX_FRAME = 8 * 1024 * 1024, MAX_INFLATED = 16 * 1024 * 1024;
    private static final Set<Socket> SOCKETS = ConcurrentHashMap.newKeySet();
    private static final Set<SessionLog> SESSIONS = ConcurrentHashMap.newKeySet();
    private static final Base64.Encoder BASE64 = Base64.getEncoder();
    private static final boolean COLOR = System.console() != null && System.getenv("NO_COLOR") == null;

    public static void main(String[] args) throws Exception {
        File root = new File(".").getCanonicalFile();
        File proxyDir = new File(root, CONFIG).isFile() ? root : new File(root, "server/proxy");
        Files.createDirectories(proxyDir.toPath());
        File configFile = new File(proxyDir, CONFIG);
        if (!configFile.exists()) Files.writeString(configFile.toPath(),
                "listen_host=127.0.0.1\nlisten_port=25565\nbackend_host=" + DEFAULT_HOST + "\nbackend_port=25565\n", StandardCharsets.UTF_8);
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(configFile.toPath(), StandardCharsets.UTF_8)) { properties.load(reader); }
        Settings settings;
        try { settings = new Settings(properties); }
        catch (IllegalArgumentException ex) {
            error(ex.getMessage());
            System.exit(2);
            return;
        }
        Path logRoot = proxyDir.toPath().getParent().resolve("session-logs");
        Files.createDirectories(logRoot);
        ExecutorService pool = Executors.newCachedThreadPool();
        ServerSocket listener = new ServerSocket();
        try { listener.bind(new InetSocketAddress(settings.listenHost, settings.listenPort)); }
        catch (IOException ex) {
            error("НЕ УДАЛОСЬ ЗАПУСТИТЬ ПРОКСИ: " + networkMessage(ex));
            error("Адрес " + settings.listenHost + ":" + settings.listenPort + ". Возможно, уже запущено другое окно прокси.");
            pool.shutdownNow(); throw ex;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            info("Завершение работы. Сохраняю логи...");
            close(listener); SOCKETS.forEach(MinecraftLoggerPlugin::close); pool.shutdown();
            try { pool.awaitTermination(3, TimeUnit.SECONDS); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            SESSIONS.forEach(SessionLog::close);
        }, "logger-shutdown"));
        System.out.println();
        red("======================================================================");
        red("       НЕ ЗАКРЫВАЙТЕ КОНСОЛЬ ВО ВРЕМЯ ИГРЫ");
        red("       ЗАКРЫТИЕ ЭТОГО ОКНА ПРЕРВЁТ СОЕДИНЕНИЕ С СЕРВЕРОМ");
        red("======================================================================");
        info("ПРОКСИ ГОТОВ: " + settings.listenHost + ":" + listener.getLocalPort());
        info("В Minecraft укажите: localhost:" + listener.getLocalPort());
        info("Сервер назначения: " + settings.backendHost + ":" + settings.backendPort);
        info("Папка логов: " + logRoot.toAbsolutePath());
        info("Подробная запись: каждое полученное движение и действие, без искусственных повторов.");
        info("Для остановки нажмите Ctrl+C после выхода из игры.");
        while (!listener.isClosed()) {
            try {
                Socket client = listener.accept(); SOCKETS.add(client);
                pool.submit(() -> handleClient(client, settings, logRoot));
            } catch (SocketException ex) { if (!listener.isClosed()) throw ex; }
        }
    }

    private static void handleClient(Socket client, Settings settings, Path logRoot) {
        SessionLog session = null;
        Context c = new Context();
        Socket backend = new Socket(); SOCKETS.add(backend);
        try {
            client.setTcpNoDelay(true); client.setSoTimeout(30000);
            session = new SessionLog(logRoot, client, settings); SESSIONS.add(session);
            Frame first = firstFrame(client.getInputStream());
            if (first == null) return;
            ByteArrayInputStream input = new ByteArrayInputStream(first.payload);
            if (varInt(input) != 0) throw new IOException("Неверный начальный пакет Minecraft");
            c.protocol = varInt(input);
            String originalHost = string(input);
            int originalPort = new DataInputStream(input).readUnsignedShort(), nextState = varInt(input);
            if (nextState != 1 && nextState != 2 && nextState != 3) throw new IOException("Неизвестная фаза подключения: " + nextState);
            session.protocol(c.protocol);
            session.packet(true, "HANDSHAKE", 0, "handshake", first.payload, first.bytes, false,
                    Json.map("protocolVersion", c.protocol, "host", originalHost, "port", originalPort, "nextState", nextState), null, System.nanoTime());
            c.clientPhase = c.serverPhase = nextState == 1 ? "STATUS" : "LOGIN";
            int suffix = originalHost.indexOf('\0');
            String host = settings.upstreamHost + (suffix < 0 ? "" : originalHost.substring(suffix));
            ByteArrayOutputStream rewritten = new ByteArrayOutputStream();
            writeVarInt(rewritten, 0); writeVarInt(rewritten, c.protocol); writeString(rewritten, host);
            new DataOutputStream(rewritten).writeShort(settings.upstreamPort); writeVarInt(rewritten, nextState);
            byte[] forwarded = frame(rewritten.toByteArray(), -1);
            session.event("handshake_rewritten", Json.map("protocolVersion", c.protocol,
                    "originalHost", originalHost, "originalPort", originalPort,
                    "rewrittenHost", host, "rewrittenPort", settings.upstreamPort,
                    "rewrittenFrameBase64", BASE64.encodeToString(forwarded)));
            if (!ProtocolDecoder.supports(c.protocol)) {
                session.event("unsupported_protocol", Json.map("protocolVersion", c.protocol,
                        "message", "Версия не распознана: пакеты сохраняются без подробной расшифровки."));
                warn("Протокол " + c.protocol + ": подробная расшифровка недоступна, исходный трафик записывается.");
            }
            backend.connect(new InetSocketAddress(settings.backendHost, settings.backendPort), 10000);
            backend.setTcpNoDelay(true); client.setSoTimeout(0);
            backend.getOutputStream().write(forwarded);
            SessionLog active = session;
            Thread downstream = new Thread(() -> pump(backend, client, false, active, c), "logger-server-to-client");
            downstream.start(); pump(client, backend, true, session, c);
            close(backend); close(client); downstream.join();
        } catch (Exception ex) {
            if (session != null) {
                session.event("proxy_error", Json.map("error", ex.toString(), "message", networkMessage(ex)));
                localDisconnect(client, c, session, "Локальный прокси: " + networkMessage(ex));
            }
            error("Ошибка подключения: " + networkMessage(ex));
        } finally {
            close(backend); close(client); SOCKETS.remove(backend); SOCKETS.remove(client);
            if (session != null) {
                session.event("client_disconnected", Json.map("movementCount", session.movements));
                session.close(); SESSIONS.remove(session);
            }
        }
    }

    private static void pump(Socket source, Socket target, boolean serverbound, SessionLog log, Context c) {
        FrameBuffer pending = new FrameBuffer();
        try {
            InputStream input = source.getInputStream(); OutputStream output = target.getOutputStream();
            byte[] chunk = new byte[65536]; int count;
            while ((count = input.read(chunk)) != -1) {
                long observedNano = System.nanoTime();
                pending.append(chunk, count);
                // Preserve received batches; do not turn one server read into hundreds of tiny writes.
                // Disk operations run on another thread and never hold this socket lock.
                synchronized (target) {
                    while (pending.size() > 0) {
                        if (c.opaque) {
                            log.opaque(serverbound, pending.takeAll());
                            continue;
                        }
                        Frame next = pending.poll(); if (next == null) break;
                        inspect(next, serverbound, log, c, observedNano);
                    }
                    output.write(chunk, 0, count); output.flush();
                }
            }
            if (pending.size() != 0) throw new EOFException("Соединение оборвалось посреди пакета");
            if (!serverbound && !c.kicked) {
                log.event("backend_closed", Json.map("message", "Сервер закрыл соединение."));
                if ("PLAY".equals(c.serverPhase)) warn("Сервер закрыл соединение.");
                localDisconnect(target, c, log, "Локальный прокси: сервер закрыл соединение.");
            }
        } catch (Exception ex) {
            if (!source.isClosed() && !target.isClosed()) {
                String message = networkMessage(ex);
                log.event(serverbound ? "upstream_error" : "downstream_error", Json.map("error", ex.toString(), "message", message));
                error((serverbound ? "Ошибка отправки на сервер: " : "Ошибка соединения с сервером: ") + message);
                localDisconnect(serverbound ? source : target, c, log, "Локальный прокси: " + message);
            }
        } finally { close(source); close(target); }
    }

    private static void inspect(Frame frame, boolean serverbound, SessionLog log, Context c, long observedNano) throws IOException {
        synchronized (c) {
            String phase = serverbound ? c.clientPhase : c.serverPhase;
            byte[] packet = unpack(frame.payload, c.compression);
            ByteArrayInputStream input = new ByteArrayInputStream(packet); int id = varInt(input);
            String name = ProtocolDecoder.packetName(c.protocol, phase, serverbound, id);
            if (phase.equals("LOGIN")) name = serverbound
                    ? switch (id) { case 0 -> "login_start"; case 1 -> "encryption_begin"; case 3 -> "login_acknowledged"; default -> name; }
                    : switch (id) { case 0 -> "disconnect"; case 1 -> "encryption_begin"; case 2 -> "success"; case 3 -> "compress"; default -> name; };
            Map<String, Object> fields; String decodeError = null;
            try { fields = ProtocolDecoder.fields(c.protocol, phase, serverbound, id, packet); }
            catch (Exception ex) { fields = Json.map(); decodeError = ex.toString(); }
            if (phase.equals("LOGIN") && serverbound && id == 0) {
                String player = string(input); fields.put("name", player); log.player(player);
                info("Игрок подключается: " + player + " | " + ProtocolDecoder.versionName(c.protocol));
                info("Логи этой сессии: " + log.directory.toAbsolutePath());
                log.event("login_start", Json.map("playerName", player));
            }
            long seq = log.packet(serverbound, phase, id, name, packet, frame.bytes,
                    c.compression >= 0 && varInt(new ByteArrayInputStream(frame.payload)) > 0, fields, decodeError, observedNano);
            if (phase.equals("PLAY") && ProtocolDecoder.supports(c.protocol) && decodeError == null) {
                if (serverbound) {
                    c.tracker.action(name, fields);
                    Map<String, Object> movement = c.tracker.movement(name, fields, observedNano);
                    if (movement != null) {
                        movement.put("observedNano", Long.toString(observedNano));
                        movement.put("observedElapsedNs", Long.toString(observedNano - log.began));
                        log.movement(movement, seq);
                    }
                    else if (!Set.of("keep_alive", "pong", "tick_end", "chunk_batch_received").contains(name)) log.action(name, fields, seq);
                } else c.tracker.serverPacket(name, fields);
            }
            if (!serverbound && (name.equals("disconnect") || name.equals("kick_disconnect"))) {
                c.kicked = true; String reason;
                try { reason = component(input, c.protocol, phase); }
                catch (Exception ex) { reason = "Причина сохранена в исходном пакете: " + ex.getMessage(); }
                log.event("server_kick", Json.map("reason", reason, "packetSeq", seq));
                warn("СЕРВЕР ОТКЛЮЧИЛ ИГРОКА: " + reason);
            }
            if (phase.equals("LOGIN") && !serverbound && id == 3) {
                int threshold = varInt(input);
                c.compression = threshold < 0 ? -1 : threshold;
                log.event("set_compression", Json.map("threshold", threshold));
            } else if (phase.equals("LOGIN") && !serverbound && id == 2) {
                c.serverPhase = c.protocol >= 764 ? "CONFIGURATION" : "PLAY";
                if (c.protocol < 764) c.clientPhase = "PLAY";
                log.event("login_success", Json.map("protocolVersion", c.protocol)); info("Вход на сервер подтверждён. Запись включена.");
            } else if (phase.equals("LOGIN") && serverbound && id == 3 && c.protocol >= 764) c.clientPhase = "CONFIGURATION";
            else if (phase.equals("CONFIGURATION") && name.equals("finish_configuration")) {
                if (serverbound) c.clientPhase = "PLAY"; else c.serverPhase = "PLAY";
                log.event("phase_changed", Json.map("direction", serverbound ? "client_to_server" : "server_to_client", "phase", "PLAY"));
            } else if (phase.equals("PLAY") && !serverbound && name.equals("start_configuration")) {
                c.serverPhase = "CONFIGURATION"; c.tracker.serverPacket("respawn", Collections.emptyMap());
            } else if (phase.equals("PLAY") && serverbound && name.equals("configuration_acknowledged")) c.clientPhase = "CONFIGURATION";
            else if (phase.equals("LOGIN") && serverbound && id == 1) {
                c.opaque = true; log.encrypted = true;
                log.event("encryption_enabled", Json.map("message", "Включено шифрование: сохраняются исходные байты; движения внутри зашифрованного потока недоступны."));
                warn("Сервер включил шифрование. Соединение работает; подробная расшифровка движений недоступна.");
            }
        }
    }

    private static void localDisconnect(Socket client, Context c, SessionLog log, String reason) {
        synchronized (client) { sendLocalDisconnect(client, c, log, reason); }
    }
    private static void sendLocalDisconnect(Socket client, Context c, SessionLog log, String reason) {
        if (c.opaque || c.kicked || c.clientPhase.equals("HANDSHAKE") || c.clientPhase.equals("STATUS") || !c.errorSent.compareAndSet(false, true)) return;
        String phase = c.serverPhase;
        Integer id = phase.equals("LOGIN") ? Integer.valueOf(0)
                : ProtocolDecoder.packetId(c.protocol, phase, false, phase.equals("PLAY") ? "kick_disconnect" : "disconnect");
        if (id == null) return;
        try {
            ByteArrayOutputStream body = new ByteArrayOutputStream(); writeVarInt(body, id);
            if (!phase.equals("LOGIN") && c.protocol >= 765) {
                body.write(8); byte[] utf8 = reason.getBytes(StandardCharsets.UTF_8);
                new DataOutputStream(body).writeShort(utf8.length); body.write(utf8);
            } else writeString(body, Json.stringify(Json.map("text", reason, "color", "red")));
            synchronized (client) { client.getOutputStream().write(frame(body.toByteArray(), c.compression)); client.getOutputStream().flush(); }
            log.event("local_disconnect", Json.map("reason", reason, "phase", phase));
        } catch (IOException ignored) { }
    }

    private static String component(InputStream input, int protocol, String phase) throws IOException {
        if (phase.equals("LOGIN") || protocol < 765) return string(input);
        DataInputStream in = new DataInputStream(input);
        return Json.stringify(nbt(in, in.readUnsignedByte(), 0));
    }
    private static Object nbt(DataInputStream in, int type, int depth) throws IOException {
        if (depth > 32) throw new IOException("Слишком сложный текст причины кика");
        return switch (type) {
            case 0 -> null; case 1 -> in.readByte(); case 2 -> in.readShort(); case 3 -> in.readInt(); case 4 -> in.readLong();
            case 5 -> in.readFloat(); case 6 -> in.readDouble();
            case 8 -> new String(readFully(in, in.readUnsignedShort()), StandardCharsets.UTF_8);
            case 9 -> {
                int element = in.readUnsignedByte(), count = in.readInt();
                if (count < 0 || count > 10000) throw new IOException("Слишком длинный текст причины кика");
                List<Object> values = new ArrayList<>();
                for (int i = 0; i < count; i++) values.add(nbt(in, element, depth + 1));
                yield values;
            }
            case 10 -> {
                Map<String, Object> values = new LinkedHashMap<>(); int child;
                while ((child = in.readUnsignedByte()) != 0) {
                    if (values.size() > 10000) throw new IOException("Слишком большой текст причины кика");
                    String key = new String(readFully(in, in.readUnsignedShort()), StandardCharsets.UTF_8);
                    values.put(key, nbt(in, child, depth + 1));
                }
                yield values;
            }
            default -> throw new IOException("Неподдерживаемый NBT-тег текста: " + type);
        };
    }

    private static byte[] unpack(byte[] payload, int threshold) throws IOException {
        if (threshold < 0) return payload;
        ByteArrayInputStream input = new ByteArrayInputStream(payload); int length = varInt(input);
        if (length < 0 || length > MAX_INFLATED) throw new IOException("Пакет превышает допустимый размер после распаковки");
        byte[] body = input.readAllBytes(); if (length == 0) return body;
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(body); byte[] result = new byte[length]; int offset = 0;
            while (!inflater.finished() && offset < length) { int n = inflater.inflate(result, offset, length - offset); if (n == 0) break; offset += n; }
            if (!inflater.finished() && offset == length && inflater.inflate(new byte[1]) != 0) throw new IOException("Распакованный пакет длиннее заявленного");
            if (offset != length || !inflater.finished() || inflater.getRemaining() != 0) throw new IOException("Повреждён сжатый пакет Minecraft");
            return result;
        } catch (DataFormatException ex) { throw new IOException("Не удалось распаковать пакет Minecraft", ex); }
        finally { inflater.end(); }
    }
    private static byte[] frame(byte[] packet, int compression) throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        if (compression >= 0) {
            if (packet.length >= compression) {
                writeVarInt(payload, packet.length); Deflater deflater = new Deflater();
                try {
                    deflater.setInput(packet); deflater.finish(); byte[] buffer = new byte[8192];
                    while (!deflater.finished()) { int n = deflater.deflate(buffer); payload.write(buffer, 0, n); }
                } finally { deflater.end(); }
            } else { writeVarInt(payload, 0); payload.write(packet); }
        } else payload.write(packet);
        ByteArrayOutputStream result = new ByteArrayOutputStream(); writeVarInt(result, payload.size()); payload.writeTo(result); return result.toByteArray();
    }
    private static Frame firstFrame(InputStream in) throws IOException {
        int first = in.read(); if (first < 0) return null;
        ByteArrayOutputStream prefix = new ByteArrayOutputStream(); prefix.write(first); int current = first;
        while ((current & 128) != 0) {
            if (prefix.size() >= 3) throw new IOException("Некорректная длина начального пакета");
            current = in.read(); if (current < 0) throw new EOFException(); prefix.write(current);
        }
        int length = varInt(new ByteArrayInputStream(prefix.toByteArray()));
        if (length < 1 || length > 65536) throw new IOException("Некорректный размер начального пакета");
        byte[] payload = readFully(in, length); ByteArrayOutputStream bytes = new ByteArrayOutputStream(); prefix.writeTo(bytes); bytes.write(payload);
        return new Frame(bytes.toByteArray(), payload);
    }
    private static int varInt(InputStream in) throws IOException {
        int value = 0;
        for (int i = 0; i < 5; i++) {
            int b = in.read(); if (b < 0) throw new EOFException("Неполный VarInt");
            if (i == 4 && (b & 240) != 0) throw new IOException("Некорректный VarInt");
            value |= (b & 127) << (7 * i); if ((b & 128) == 0) return value;
        }
        throw new IOException("Слишком длинный VarInt");
    }
    private static void writeVarInt(OutputStream out, int value) throws IOException {
        do { int b = value & 127; value >>>= 7; out.write(value == 0 ? b : b | 128); } while (value != 0);
    }
    private static String string(InputStream in) throws IOException {
        int length = varInt(in); if (length < 0 || length > 131068) throw new IOException("Некорректная длина строки");
        return new String(readFully(in, length), StandardCharsets.UTF_8);
    }
    private static void writeString(OutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8); writeVarInt(out, bytes.length); out.write(bytes);
    }
    private static byte[] readFully(InputStream in, int length) throws IOException {
        byte[] bytes = in.readNBytes(length); if (bytes.length != length) throw new EOFException("Неполный пакет Minecraft"); return bytes;
    }
    private static String networkMessage(Exception ex) {
        if (ex instanceof UnknownHostException) return "Не удалось найти адрес сервера. Проверьте адрес и подключение к интернету.";
        if (ex instanceof ConnectException) return "Не удалось подключиться к серверу: соединение отклонено или сервер недоступен.";
        if (ex instanceof SocketTimeoutException) return "Превышено время ожидания ответа сервера.";
        if (ex instanceof BindException) return "Локальный порт занят другим приложением.";
        if (ex instanceof SocketException) return "Сетевое соединение разорвано. " + ex.getMessage();
        if (ex instanceof EOFException) return "Соединение оборвалось во время получения пакета.";
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }
    private static void info(String s) { System.out.println("[ПРОКСИ] " + s); }
    private static void warn(String s) { System.out.println((COLOR ? "\033[1;33m" : "") + "[ВНИМАНИЕ] " + s + (COLOR ? "\033[0m" : "")); }
    private static void red(String s) { System.out.println((COLOR ? "\033[1;31m" : "") + s + (COLOR ? "\033[0m" : "")); }
    private static void error(String s) { red("[ОШИБКА] " + s); }
    private static void close(Closeable value) { if (value != null) try { value.close(); } catch (IOException ignored) { } }

    private record Frame(byte[] bytes, byte[] payload) { }
    private static final class FrameBuffer {
        private byte[] bytes = new byte[65536]; private int start, end;
        int size() { return end - start; }
        void append(byte[] chunk, int count) throws IOException {
            if (size() + count > MAX_FRAME + 65541) throw new IOException("Превышен размер буфера пакета");
            if (bytes.length - end < count) {
                int size = size();
                if (bytes.length < size + count) {
                    byte[] replacement = new byte[Math.max(size + count, Math.min(MAX_FRAME + 65541, bytes.length * 2))];
                    System.arraycopy(bytes, start, replacement, 0, size); bytes = replacement;
                } else System.arraycopy(bytes, start, bytes, 0, size);
                start = 0; end = size;
            }
            System.arraycopy(chunk, 0, bytes, end, count); end += count;
        }
        Frame poll() throws IOException {
            int length = 0, prefix = 0;
            while (prefix < size()) {
                int b = bytes[start + prefix] & 255;
                if (prefix >= 4 || (prefix == 3 && (b & 248) != 0)) throw new IOException("Некорректная длина пакета");
                length |= (b & 127) << (7 * prefix++);
                if ((b & 128) == 0) {
                    if (length < 1 || length > MAX_FRAME) throw new IOException("Пакет превышает допустимый размер");
                    if (size() < prefix + length) return null;
                    byte[] whole = Arrays.copyOfRange(bytes, start, start + prefix + length);
                    byte[] payload = Arrays.copyOfRange(bytes, start + prefix, start + prefix + length);
                    start += prefix + length; if (start == end) start = end = 0;
                    return new Frame(whole, payload);
                }
            }
            return null;
        }
        byte[] takeAll() { byte[] result = Arrays.copyOfRange(bytes, start, end); start = end = 0; return result; }
    }
    private static final class Settings {
        final String listenHost, backendHost, upstreamHost; final int listenPort, backendPort, upstreamPort;
        Settings(Properties p) {
            listenHost = p.getProperty("listen_host", "127.0.0.1").strip();
            backendHost = p.getProperty("backend_host", DEFAULT_HOST).strip();
            upstreamHost = p.getProperty("upstream_host", backendHost).strip();
            if (backendHost.isEmpty() || upstreamHost.isEmpty() || backendHost.equalsIgnoreCase(DEFAULT_HOST)
                    || upstreamHost.equalsIgnoreCase(DEFAULT_HOST))
                throw new IllegalArgumentException("Укажите адрес вашего сервера в " + CONFIG
                        + " (backend_host). Сейчас вместо сервера указана заглушка.");
            listenPort = port(p, "listen_port", 25565); backendPort = port(p, "backend_port", 25565); upstreamPort = port(p, "upstream_port", backendPort);
        }
        private static int port(Properties p, String key, int fallback) {
            int value = Integer.parseInt(p.getProperty(key, String.valueOf(fallback)).strip());
            if (value < 1 || value > 65535) throw new IllegalArgumentException("Неверный порт в настройке " + key); return value;
        }
    }
    private static final class Context {
        volatile String clientPhase = "HANDSHAKE", serverPhase = "HANDSHAKE";
        volatile int protocol = -1, compression = -1; volatile boolean opaque, kicked;
        final AtomicBoolean errorSent = new AtomicBoolean(); final MovementTracker tracker = new MovementTracker();
    }
    private static final class SessionLog implements Closeable {
        final Path directory; final Map<String, BufferedWriter> writers = new LinkedHashMap<>();
        final Map<String, OutputStream> binaries = new LinkedHashMap<>(); final Map<String, Object> metadata;
        final long began = System.nanoTime(); long sequence, movements; int protocol = -1;
        String player = "unknown"; boolean closed, encrypted;
        AsyncLogWriter disk;
        SessionLog(Path root, Socket client, Settings settings) throws IOException {
            String date = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").format(LocalDateTime.now());
            directory = Files.createDirectory(root.resolve(date + "_pending_" + UUID.randomUUID().toString().substring(0, 8)));
            for (String name : List.of("events", "client_to_server", "server_to_client", "movements", "actions"))
                writers.put(name, Files.newBufferedWriter(directory.resolve(name + ".jsonl"), StandardCharsets.UTF_8));
            for (String name : List.of("client_to_server", "server_to_client"))
                binaries.put(name, new BufferedOutputStream(Files.newOutputStream(directory.resolve(name + ".bin")), 65536));
            metadata = Json.map("schemaVersion", 2, "createdAt", Instant.now().toString(), "proxyMode", true,
                    "clientAddress", String.valueOf(client.getRemoteSocketAddress()), "backendAddress", settings.backendHost + ":" + settings.backendPort,
                    "upstreamHost", settings.upstreamHost, "upstreamPort", settings.upstreamPort,
                    "capture", "Observed input bytes, before handshake rewriting. Following traffic is forwarded unchanged.",
                    "movementTiming", "Proxy observation time, not client time or server ticks. No synthetic samples.",
                    "loggingMode", "bounded_async", "logQueueBytes", 32L * 1024 * 1024, "flushIntervalMs", 100,
                    "files", List.of("events.jsonl", "movements.jsonl", "actions.jsonl", "client_to_server.jsonl", "server_to_client.jsonl", "client_to_server.bin", "server_to_client.bin"));
            metadata();
            Files.writeString(directory.resolve("README.md"), "# Логи подключения\n\nПрочитайте metadata.json и LOG_SCHEMA_FOR_AI.md в корне проекта.\n"
                    + "movements.jsonl — каждое доступное движение; actions.jsonl — действия; events.jsonl — подключение, кики и ошибки.\n"
                    + "Бинарные файлы содержат полученные прокси байты. Изменённый при отправке handshake записан в events.jsonl.\n"
                    + "При шифровании сохраняется непрозрачный поток; подробные данные внутри него недоступны.\n", StandardCharsets.UTF_8);
            disk = new AsyncLogWriter(8192, 32L * 1024 * 1024, 100,
                    this::flushFiles, this::closeFiles, this::fail);
            event("client_connected", Json.map("clientAddress", String.valueOf(client.getRemoteSocketAddress())));
        }
        synchronized void protocol(int value) throws IOException { protocol = value; metadata(); }
        synchronized void player(String value) throws IOException { player = value; metadata(); }
        private void metadata() throws IOException {
            metadata.put("playerName", player); metadata.put("protocolVersion", protocol); metadata.put("version", ProtocolDecoder.versionName(protocol));
            metadata.put("detailedDecodingSupported", ProtocolDecoder.supports(protocol)); metadata.put("encrypted", encrypted); metadata.put("movementCount", movements);
            if (disk != null) {
                metadata.put("captureComplete", disk.failure() == null);
                metadata.put("rejectedLogEntries", disk.rejectedEntries());
                metadata.put("logError", disk.failure() == null ? null : disk.failure().getMessage());
            } else metadata.put("captureComplete", true);
            String snapshot = Json.stringify(metadata) + "\n";
            if (disk == null || closed) Files.writeString(directory.resolve("metadata.json"), snapshot, StandardCharsets.UTF_8);
            else disk.submit(2048 + snapshot.length() * 2L,
                    () -> Files.writeString(directory.resolve("metadata.json"), snapshot, StandardCharsets.UTF_8));
        }
        synchronized long row(String file, String stream, Map<String, Object> values) throws IOException {
            return row(file, stream, values, null, null, 2048);
        }
        private synchronized long row(String file, String stream, Map<String, Object> values,
                                      byte[] binary, AsyncLogWriter.IOAction prepare, long estimatedBytes) {
            if (closed) return -1; long now = System.nanoTime();
            Map<String, Object> row = Json.map("seq", ++sequence, "ts", Instant.now().toString(), "epochMs", System.currentTimeMillis(),
                    "nano", now, "elapsedNs", Long.toString(now - began), "stream", stream, "playerName", player);
            row.putAll(values);
            disk.submit(estimatedBytes, () -> {
                if (binary != null) binaries.get(file).write(binary);
                if (prepare != null) prepare.run();
                BufferedWriter writer = writers.get(file);
                writer.write(Json.stringify(row)); writer.newLine();
            });
            return sequence;
        }
        synchronized void event(String type, Map<String, Object> values) {
            try { Map<String, Object> data = Json.map("type", type); data.putAll(values); row("events", "event", data); }
            catch (IOException ex) { fail(ex); }
        }
        synchronized long packet(boolean serverbound, String phase, int id, String name, byte[] packet, byte[] frame,
                                 boolean compressed, Map<String, Object> fields, String decodeError, long observedNano) throws IOException {
            String direction = serverbound ? "client_to_server" : "server_to_client";
            Map<String, Object> data = Json.map("direction", direction, "phase", phase,
                    "packetId", id, "packetName", name, "packetNameGuess", name, "compressed", compressed, "frameLength", frame.length,
                    "observedNano", Long.toString(observedNano), "observedElapsedNs", Long.toString(observedNano - began),
                    "fields", fields, "decodeError", decodeError);
            return row(direction, "packet", Json.map("packetData", data), frame, () -> {
                data.put("frameBase64", BASE64.encodeToString(frame));
                data.put("packetDataBase64", BASE64.encodeToString(packet));
            }, 2048L + frame.length + packet.length);
        }
        synchronized void opaque(boolean serverbound, byte[] data) throws IOException {
            String direction = serverbound ? "client_to_server" : "server_to_client";
            Map<String, Object> packet = Json.map("direction", direction, "phase", "ENCRYPTED", "encrypted", true, "chunkLength", data.length);
            row(direction, "opaque", Json.map("packetData", packet), data,
                    () -> packet.put("chunkBase64", BASE64.encodeToString(data)), 2048L + data.length);
        }
        synchronized void movement(Map<String, Object> value, long seq) throws IOException { movements++; row("movements", "movement", Json.map("packetSeq", seq, "movement", value)); }
        synchronized void action(String name, Map<String, Object> fields, long seq) throws IOException { row("actions", "action", Json.map("packetSeq", seq, "action", Json.map("packet", name, "fields", fields))); }
        private void fail(IOException ex) { error("ЗАПИСЬ ЛОГОВ ОСТАНОВЛЕНА: " + ex.getMessage() + " | " + directory + ". Соединение с игрой сохраняется."); }
        private void flushFiles() throws IOException {
            for (BufferedWriter writer : writers.values()) writer.flush();
            for (OutputStream stream : binaries.values()) stream.flush();
        }
        private void closeFiles() {
            writers.values().forEach(MinecraftLoggerPlugin::close);
            binaries.values().forEach(MinecraftLoggerPlugin::close);
        }
        @Override public synchronized void close() {
            if (closed) return;
            closed = true;
            boolean drained = false;
            try { drained = disk.finish(5000); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            try {
                metadata.put("closedAt", Instant.now().toString());
                metadata.put("loggerDrained", drained);
                metadata();
                if (!drained) {
                    metadata.put("captureComplete", false);
                    metadata.put("logError", "Запись не завершилась за 5 секунд");
                    Files.writeString(directory.resolve("metadata.json"), Json.stringify(metadata) + "\n", StandardCharsets.UTF_8);
                    warn("Логи ещё записываются: " + directory);
                }
            } catch (IOException ex) { fail(ex); }
            if (!player.equals("unknown")) info("Сессия " + player + " завершена. Движений: " + movements + ". Логи: " + directory);
        }
    }
}
