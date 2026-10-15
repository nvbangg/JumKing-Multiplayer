package jumpking.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Single-instance: đóng các trận mất state RAM trước khi ứng dụng sẵn sàng. */
@Component
public final class RoomRecovery implements ApplicationRunner {
    private final RoomRuns records;
    public RoomRecovery(RoomRuns records) { this.records = records; }
    @Override public void run(ApplicationArguments arguments) { records.recoverAborted(); }
}
