package jumpking.records;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import jumpking.game.RoomRanking;
import jumpking.network.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Một bản ghi tích lũy của toàn roster; match và metrics được commit cùng transaction. */
@Service
public class RoomRuns {
    public record Run(String id, long owner, String nickname, String status, long ticks, int jumps, int falls,
        double progress, double maximum, long peak, Long finish, Integer rank, boolean winner, Instant endedAt) {
        public Run { endedAt = millis(endedAt); }
        Run owned(long value) { return new Run(id, value, nickname, status, ticks, jumps, falls, progress, maximum, peak, finish, rank, winner, endedAt); }
        Run unranked() { return new Run(id, owner, nickname, status, ticks, jumps, falls, progress, maximum, peak, finish, null, false, endedAt); }
    }
    public record Write(String id, String roomCode, String mode, Integer minutes, long revision, Instant startedAt, Instant endedAt,
        long duration, String reason, List<Run> runs) {
        public Write { startedAt = millis(startedAt); endedAt = millis(endedAt); runs = List.copyOf(runs); }
    }
    private record Match(String roomCode, String mode, Integer minutes, Instant startedAt, Instant endedAt, long duration, String reason) {}
    private record Stored(Run run, long revision) {}
    private final JdbcTemplate jdbc;
    private final RunOwners owners;
    private final Clock clock;
    private final int capacity;
    public RoomRuns(JdbcTemplate jdbc, RunOwners owners, Clock clock, jumpking.game.SharedData shared) { this.jdbc = jdbc; this.owners = owners; this.clock = clock; capacity = shared.rules().path("capacity").asInt(); }
    public void save(Write write) {
        validate(write);
        owners.writeAll(write.runs.stream().map(Run::owner).toList(), ownership -> {
            var next = write.runs.stream().map(run -> run.owned(ownership.get(run.owner))).toList();
            var matches = jdbc.query("SELECT room_code,mode,time_limit_minutes,started_at,ended_at,duration_ticks,end_reason FROM matches WHERE id=? FOR UPDATE",
                (row, index) -> new Match(row.getString("room_code"), row.getString("mode"), row.getObject("time_limit_minutes", Integer.class), row.getTimestamp("started_at").toInstant(), instant(row.getTimestamp("ended_at")), row.getLong("duration_ticks"), row.getString("end_reason")), write.id);
            var old = jdbc.query("SELECT id,user_id,nickname,status,play_ticks,jumps,falls,current_progress,max_progress,peak_reached_ticks,finish_ticks,`rank`,is_winner,ended_at,revision FROM play_runs WHERE match_id=? AND kind='ROOM' FOR UPDATE",
                (row, index) -> new Stored(new Run(row.getString("id"), row.getLong("user_id"), row.getString("nickname"), row.getString("status"), row.getLong("play_ticks"), row.getInt("jumps"), row.getInt("falls"),
                    row.getDouble("current_progress"), row.getDouble("max_progress"), row.getLong("peak_reached_ticks"), row.getObject("finish_ticks", Long.class), row.getObject("rank", Integer.class), row.getBoolean("is_winner"), instant(row.getTimestamp("ended_at"))), row.getLong("revision")), write.id);
            var desired = new Match(write.roomCode, write.mode, write.minutes, write.startedAt, write.endedAt, write.duration, write.reason);
            if (!matches.isEmpty()) {
                var previous = matches.getFirst();
                if (!previous.roomCode.equals(write.roomCode) || !previous.mode.equals(write.mode) || !Objects.equals(previous.minutes, write.minutes) || !previous.startedAt.equals(write.startedAt) || old.size() != next.size()) throw conflict();
                var byId = new HashMap<String, Stored>(); old.forEach(value -> byId.put(value.run.id, value));
                for (var run : next) { var value = byId.get(run.id); if (value == null || value.run.owner != run.owner || !value.run.nickname.equals(run.nickname)) throw conflict(); }
                long revision = old.stream().mapToLong(Stored::revision).max().orElseThrow();
                if (write.revision < revision) return null;
                if (write.revision == revision) {
                    if (!previous.equals(desired) || next.stream().anyMatch(run -> !byId.get(run.id).run.equals(run))) throw conflict();
                    return null;
                }
                if (previous.endedAt != null || write.duration < previous.duration) throw conflict();
                for (var run : next) {
                    var before = byId.get(run.id).run;
                    if (!before.status.equals("ACTIVE") && !before.unranked().equals(run.unranked())) throw conflict();
                    if (run.ticks < before.ticks || run.jumps < before.jumps || run.falls < before.falls || run.maximum < before.maximum || run.peak < before.peak || run.maximum == before.maximum && run.peak != before.peak) throw conflict();
                }
            } else {
                if (!old.isEmpty()) throw conflict();
                jdbc.update("INSERT INTO matches(id,room_code,mode,time_limit_minutes,started_at) VALUES (?,?,?,?,?)", write.id, write.roomCode, write.mode, write.minutes, ts(write.startedAt));
                for (var run : next) jdbc.update("INSERT INTO play_runs(id,user_id,kind,match_id,status,nickname,started_at,updated_at) VALUES (?,?,'ROOM',?,'ACTIVE',?,?,?)", run.id, run.owner, write.id, run.nickname, ts(write.startedAt), ts(clock.instant()));
            }
            jdbc.update("UPDATE matches SET duration_ticks=?,ended_at=?,end_reason=? WHERE id=?", write.duration, ts(write.endedAt), write.reason, write.id);
            for (var run : next) jdbc.update("UPDATE play_runs SET revision=?,status=?,play_ticks=?,jumps=?,falls=?,current_progress=?,max_progress=?,peak_reached_ticks=?,finish_ticks=?,`rank`=?,is_winner=?,ended_at=?,updated_at=? WHERE id=? AND match_id=? AND user_id=?",
                write.revision, run.status, run.ticks, run.jumps, run.falls, run.progress, run.maximum, run.peak, run.finish, run.rank, run.winner, ts(run.endedAt), ts(clock.instant()), run.id, write.id, run.owner);
            return null;
        });
    }
    private void validate(Write value) {
        if (value.revision < 1 || value.duration < 0 || value.startedAt == null || value.runs.size() < 2 || value.runs.size() > capacity
                || (value.endedAt == null) != (value.reason == null) || value.endedAt != null && value.endedAt.isBefore(value.startedAt)) throw conflict();
        var ids = new HashSet<String>();
        for (var run : value.runs) {
            if (!ids.add(run.id) || !Set.of("ACTIVE", "FINISHED", "UNFINISHED", "DNF").contains(run.status) || run.ticks < 0 || run.ticks > value.duration || run.jumps < 0 || run.falls < 0
                    || !Double.isFinite(run.progress) || !Double.isFinite(run.maximum) || run.progress < 0 || run.maximum < run.progress || run.peak < 0 || run.peak > run.ticks
                    || run.status.equals("FINISHED") != (run.finish != null) || run.finish != null && (run.finish < 0 || run.finish > value.duration)
                    || run.status.equals("ACTIVE") != (run.endedAt == null) || run.endedAt != null && (run.endedAt.isBefore(value.startedAt) || value.endedAt != null && run.endedAt.isAfter(value.endedAt))) throw conflict();
            if (value.endedAt == null && (run.rank != null || run.winner || run.status.equals("FINISHED") || run.status.equals("UNFINISHED"))) throw conflict();
            if (value.endedAt != null && run.status.equals("ACTIVE")) throw conflict();
        }
        if (value.endedAt != null) {
            var scores = value.runs.stream().map(run -> new RoomRanking.Score(run.id, run.nickname, run.status, run.progress, run.maximum, run.finish)).toList();
            var ranks = RoomRanking.rank(scores, value.mode, value.reason.equals("FINISH"));
            for (var run : value.runs) { var ranked = ranks.stream().filter(row -> row.score().playerId().equals(run.id)).findFirst().orElseThrow(); if (!Objects.equals(run.rank, ranked.rank()) || run.winner != ranked.winner()) throw conflict(); }
            if (value.reason.equals("FINISH") && ranks.stream().noneMatch(RoomRanking.Ranked::winner)) throw conflict();
        }
    }
    private static Instant millis(Instant time) { return time == null ? null : time.truncatedTo(java.time.temporal.ChronoUnit.MILLIS); }
    private static Instant instant(Timestamp time) { return time == null ? null : time.toInstant(); }
    private static Timestamp ts(Instant time) { return time == null ? null : Timestamp.from(time); }
    private static ApiException conflict() { return new ApiException(409, "CONFLICT", "Room results conflict with the committed match"); }
}
