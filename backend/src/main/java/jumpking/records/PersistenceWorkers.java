package jumpking.records;

import java.util.concurrent.*;
import jumpking.config.PersistenceSettings;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(PersistenceSettings.class)
public class PersistenceWorkers implements AutoCloseable {
    private final PersistenceSettings settings;
    private final ThreadPoolExecutor workers;
    private final ScheduledThreadPoolExecutor retries = new ScheduledThreadPoolExecutor(1, Thread.ofPlatform().daemon().name("checkpoint-retry").factory());
    public PersistenceWorkers(PersistenceSettings settings) {
        this.settings = settings;
        workers = new ThreadPoolExecutor(settings.persistenceThreads(), settings.persistenceThreads(), 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(settings.persistenceQueue()), Thread.ofPlatform().daemon().name("persistence-", 0).factory(), new ThreadPoolExecutor.AbortPolicy());
        retries.setRemoveOnCancelPolicy(true);
    }
    public void execute(Runnable task) { workers.execute(task); }
    public <T> CheckpointWriter<T> writer(java.util.function.Consumer<T> save) {
        return new CheckpointWriter<>(workers, retries, save, settings.persistenceRunQueue(), settings.persistenceRetryCount(), settings.persistenceRetryDelayMs());
    }
    @Override public void close() {
        retries.shutdown();
        try { if (!retries.awaitTermination(10, TimeUnit.SECONDS)) retries.shutdownNow(); }
        catch (InterruptedException stopped) { Thread.currentThread().interrupt(); retries.shutdownNow(); }
        workers.shutdown();
        try { if (!workers.awaitTermination(10, TimeUnit.SECONDS)) workers.shutdownNow(); }
        catch (InterruptedException stopped) { Thread.currentThread().interrupt(); workers.shutdownNow(); }
    }
}
