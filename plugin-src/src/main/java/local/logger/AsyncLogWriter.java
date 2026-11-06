package local.logger;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/** A slow disk must never hold a socket reader or grow an unbounded capture queue. */
public final class AsyncLogWriter implements Closeable {
    @FunctionalInterface public interface IOAction { void run() throws IOException; }
    private record Entry(long bytes, IOAction action) { }

    private final Object lock = new Object();
    private final Deque<Entry> queue = new ArrayDeque<>();
    private final int maxEntries;
    private final long maxBytes, flushIntervalMs;
    private final IOAction flush, cleanup;
    private final Consumer<IOException> onFailure;
    private final Thread worker;
    private long pendingBytes, rejected;
    private int pendingEntries;
    private volatile IOException failure;
    private boolean finishing, finished, writeFailed;

    public AsyncLogWriter(int maxEntries, long maxBytes, long flushIntervalMs,
                          IOAction flush, Consumer<IOException> onFailure) {
        this(maxEntries, maxBytes, flushIntervalMs, flush, () -> { }, onFailure);
    }

    public AsyncLogWriter(int maxEntries, long maxBytes, long flushIntervalMs,
                          IOAction flush, IOAction cleanup, Consumer<IOException> onFailure) {
        if (maxEntries < 1 || maxBytes < 1 || flushIntervalMs < 1) throw new IllegalArgumentException("Invalid log queue limits");
        this.maxEntries = maxEntries; this.maxBytes = maxBytes; this.flushIntervalMs = flushIntervalMs;
        this.flush = flush; this.cleanup = cleanup; this.onFailure = onFailure;
        worker = new Thread(this::run, "logger-disk-writer");
        worker.setDaemon(true); worker.start();
    }

    /** false is an explicit capture failure, never a request to stall the connection. */
    public boolean submit(long estimatedBytes, IOAction action) {
        IOException problem = null;
        synchronized (lock) {
            if (finishing || failure != null) { rejected++; return false; }
            long size = Math.max(1, estimatedBytes);
            if (pendingEntries >= maxEntries || size > maxBytes - pendingBytes) {
                failure = problem = new IOException("Диск не успевает записывать логи: очередь заполнена. Запись остановлена, игра продолжает работать.");
                rejected++;
            } else {
                queue.addLast(new Entry(size, action)); pendingEntries++; pendingBytes += size; lock.notifyAll(); return true;
            }
        }
        notifyFailure(problem);
        return false;
    }

    public IOException failure() { return failure; }
    public long rejectedEntries() { synchronized (lock) { return rejected; } }

    public boolean finish(long timeoutMillis) throws InterruptedException {
        synchronized (lock) { finishing = true; lock.notifyAll(); }
        if (Thread.currentThread() == worker) return false;
        if (timeoutMillis > 0) worker.join(timeoutMillis);
        synchronized (lock) { return finished; }
    }

    private void run() {
        long lastFlush = System.nanoTime();
        try {
            while (true) {
                Entry entry;
                synchronized (lock) {
                    if (queue.isEmpty() && !finishing) lock.wait(flushIntervalMs);
                    entry = queue.pollFirst();
                    if (entry == null && finishing) break;
                }
                if (entry != null) {
                    if (!writeFailed) {
                        try { entry.action.run(); }
                        catch (IOException | RuntimeException ex) { diskFailure(ex); }
                    } else synchronized (lock) { rejected++; }
                    synchronized (lock) { pendingBytes -= entry.bytes; pendingEntries--; }
                }
                long now = System.nanoTime();
                if (!writeFailed && now - lastFlush >= flushIntervalMs * 1_000_000L) {
                    try { flush.run(); } catch (IOException | RuntimeException ex) { diskFailure(ex); }
                    lastFlush = now;
                }
            }
            if (!writeFailed) try { flush.run(); } catch (IOException | RuntimeException ex) { diskFailure(ex); }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); diskFailure(new IOException("Запись логов прервана", ex));
        } finally {
            try { cleanup.run(); } catch (IOException | RuntimeException ex) { diskFailure(ex); }
            synchronized (lock) {
                rejected += queue.size(); queue.clear(); pendingBytes = 0; pendingEntries = 0; finished = true; lock.notifyAll();
            }
        }
    }

    private void diskFailure(Exception cause) {
        IOException problem = cause instanceof IOException io ? io : new IOException("Ошибка записи логов", cause);
        boolean report;
        synchronized (lock) {
            writeFailed = true; rejected++;
            report = failure == null;
            if (report) failure = problem;
        }
        if (report) notifyFailure(problem);
    }

    private void notifyFailure(IOException problem) {
        if (problem != null) try { onFailure.accept(problem); } catch (RuntimeException ignored) { }
    }

    @Override public void close() {
        try { finish(5000); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
    }
}
