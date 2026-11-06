import local.logger.AsyncLogWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/** Behavioral checks for disk isolation. Run with Java 17; no test library required. */
public final class AsyncLogWriterChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        blockedIoDoesNotBlockSubmitAndAcceptedWorkRemainsOrdered();
        overflowRejectsFurtherCaptureAndDrainsAcceptedJobs();
        oversizedWorkIsRejectedBeforeExecution();
        ioFailureIsReportedOnceAndStaysOffTheCaller();
        finishIsBoundedWhileIoIsBlocked();
        System.out.println("AsyncLogWriterChecks: " + checks + " assertions passed.");
    }

    private static void blockedIoDoesNotBlockSubmitAndAcceptedWorkRemainsOrdered() throws Exception {
        CountDownLatch began = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger warnings = new AtomicInteger(), flushes = new AtomicInteger();
        List<Integer> completed = Collections.synchronizedList(new ArrayList<>());
        AsyncLogWriter writer = new AsyncLogWriter(32, 4096, 25, flushes::incrementAndGet,
                error -> warnings.incrementAndGet());
        try {
            check(writer.submit(8, () -> { began.countDown(); await(release); completed.add(0); }), "first job accepted");
            check(began.await(2, TimeUnit.SECONDS), "consumer started blocked I/O");
            long before = System.nanoTime();
            for (int i = 1; i < 9; i++) {
                final int value = i;
                check(writer.submit(8, () -> completed.add(value)), "queued job accepted: " + i);
            }
            check(elapsedMs(before) < 500, "submit stays responsive while disk task is blocked");
            check(completed.isEmpty(), "blocked job has not finished on the producer thread");
            release.countDown();
            check(writer.finish(5000), "finish drains all accepted jobs");
            check(completed.equals(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8)), "accepted jobs execute in FIFO order");
            check(flushes.get() > 0, "finish flushes data");
            check(warnings.get() == 0, "healthy consumer emits no warnings");
            check(writer.failure() == null, "healthy capture has no failure");
            check(writer.rejectedEntries() == 0, "healthy capture rejects no entries");
        } finally { release.countDown(); writer.close(); }
    }

    private static void overflowRejectsFurtherCaptureAndDrainsAcceptedJobs() throws Exception {
        CountDownLatch began = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger warnings = new AtomicInteger();
        List<Integer> completed = Collections.synchronizedList(new ArrayList<>());
        List<Integer> accepted = new ArrayList<>();
        AsyncLogWriter writer = new AsyncLogWriter(2, 128, 25, () -> { }, error -> warnings.incrementAndGet());
        try {
            check(writer.submit(8, () -> { began.countDown(); await(release); completed.add(0); }), "overflow fixture starts");
            accepted.add(0);
            check(began.await(2, TimeUnit.SECONDS), "consumer blocked before overflow");
            long before = System.nanoTime();
            for (int i = 1; i <= 8; i++) {
                final int value = i;
                if (writer.submit(8, () -> completed.add(value))) accepted.add(i);
            }
            check(elapsedMs(before) < 500, "overflow rejects without waiting for disk");
            check(accepted.size() < 9, "entry budget bounds pending capture");
            check(writer.rejectedEntries() > 0, "overflow records rejected capture");
            check(writer.failure() != null, "overflow exposes incomplete-capture failure");
            check(!writer.submit(1, () -> completed.add(99)), "capture remains disabled after overflow");
            release.countDown();
            check(writer.finish(5000), "overflow still drains accepted work");
            check(completed.equals(accepted), "overflow preserves FIFO for every accepted job");
            until(() -> warnings.get() > 0, "overflow warning delivered");
            check(warnings.get() == 1, "overflow is reported once");
        } finally { release.countDown(); writer.close(); }
    }

    private static void oversizedWorkIsRejectedBeforeExecution() throws Exception {
        AtomicInteger runs = new AtomicInteger(), warnings = new AtomicInteger();
        AsyncLogWriter writer = new AsyncLogWriter(10, 16, 25, () -> { }, error -> warnings.incrementAndGet());
        try {
            check(!writer.submit(32, runs::incrementAndGet), "single job exceeding byte budget is rejected");
            check(writer.failure() != null, "byte-budget failure is explicit");
            check(writer.rejectedEntries() > 0, "byte-budget rejection is counted");
            check(writer.finish(5000), "oversized rejection leaves no hanging consumer");
            check(runs.get() == 0, "oversized job never executes");
            until(() -> warnings.get() > 0, "byte-budget warning delivered");
            check(warnings.get() == 1, "byte-budget failure is reported once");
        } finally { writer.close(); }
    }

    private static void ioFailureIsReportedOnceAndStaysOffTheCaller() throws Exception {
        AtomicInteger warnings = new AtomicInteger(), runsAfterFailure = new AtomicInteger();
        AsyncLogWriter writer = new AsyncLogWriter(10, 1024, 25, () -> { }, error -> warnings.incrementAndGet());
        try {
            check(writer.submit(8, () -> { throw new IOException("controlled disk failure"); }), "failing job accepted asynchronously");
            until(() -> writer.failure() != null, "I/O failure becomes visible");
            check(writer.failure().getMessage().contains("controlled disk failure"), "original I/O cause is retained");
            check(!writer.submit(8, runsAfterFailure::incrementAndGet), "subsequent submit rejects instead of throwing I/O error");
            check(writer.finish(5000), "I/O failure does not strand finish");
            until(() -> warnings.get() > 0, "I/O warning delivered");
            check(warnings.get() == 1, "I/O failure warning delivered once");
            check(runsAfterFailure.get() == 0, "rejected work is not executed after disk failure");
        } finally { writer.close(); }
    }

    private static void finishIsBoundedWhileIoIsBlocked() throws Exception {
        CountDownLatch began = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger flushes = new AtomicInteger();
        AsyncLogWriter writer = new AsyncLogWriter(4, 1024, 25, flushes::incrementAndGet, error -> { });
        try {
            check(writer.submit(8, () -> { began.countDown(); await(release); }), "bounded-finish fixture starts");
            check(began.await(2, TimeUnit.SECONDS), "bounded-finish consumer reached blocked I/O");
            long before = System.nanoTime();
            check(!writer.finish(50), "finish returns false when disk does not drain before deadline");
            check(elapsedMs(before) < 500, "finish timeout stays bounded");
            release.countDown();
            check(writer.finish(5000), "finish can observe completion after blocked disk resumes");
            check(flushes.get() > 0, "resumed completion flushes accepted capture");
        } finally { release.countDown(); writer.close(); }
    }

    private static void await(CountDownLatch latch) throws IOException {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IOException("test gate timed out");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("test consumer interrupted", error);
        }
    }

    private static void until(BooleanSupplier condition, String message) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) throw new AssertionError(message);
            Thread.sleep(5);
        }
        check(true, message);
    }

    private static double elapsedMs(long before) { return (System.nanoTime() - before) / 1_000_000.0; }
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
