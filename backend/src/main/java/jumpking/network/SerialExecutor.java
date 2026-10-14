package jumpking.network;

import java.util.ArrayDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

/** Hàng đợi tuần tự có giới hạn; không giữ lock trong khi chạy task. */
public final class SerialExecutor implements Executor {
    private final Executor workers;
    private final int capacity;
    private final Consumer<Throwable> failure;
    private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
    private boolean running;
    public SerialExecutor(Executor workers, int capacity, Consumer<Throwable> failure) {
        this.workers = workers; this.capacity = capacity; this.failure = failure;
    }
    @Override public synchronized void execute(Runnable task) {
        if (queue.size() >= capacity) throw new RejectedExecutionException("Game command queue is full");
        queue.add(task);
        if (!running) {
            running = true;
            try { workers.execute(this::drain); }
            catch (RejectedExecutionException error) { running = false; queue.removeLast(); throw error; }
        }
    }
    private void drain() {
        for (;;) {
            Runnable next;
            synchronized (this) { next = queue.poll(); if (next == null) { running = false; return; } }
            try { next.run(); } catch (Throwable error) { failure.accept(error); }
        }
    }
}
