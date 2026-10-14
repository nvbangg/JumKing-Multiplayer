package jumpking.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import jumpking.JumpKingApplication;
import jumpking.records.RunOwners;
import jumpking.room.Memberships;
import jumpking.support.TestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(classes = JumpKingApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GuestHistoryIT {
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        var db = TestDatabase.get().dataSource;
        properties.add("spring.datasource.url", db::getUrl); properties.add("spring.datasource.username", db::getUsername); properties.add("spring.datasource.password", db::getPassword);
    }
    @Autowired GuestHistoryService history;
    @Autowired RunOwners owners;
    @Autowired JdbcTemplate jdbc;
    @Autowired Memberships memberships;
    private long guest() {
        String hash = UUID.randomUUID().toString().replace("-", "").repeat(2);
        jdbc.update("INSERT INTO users(type,guest_token_hash,created_at) VALUES ('GUEST',?,UTC_TIMESTAMP(3))", hash);
        return jdbc.queryForObject("SELECT id FROM users WHERE guest_token_hash=?", Long.class, hash);
    }
    private long account() {
        String name = UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO users(type,username,email,password_hash,created_at) VALUES ('ACCOUNT',?,?,?,UTC_TIMESTAMP(3))", name, name + "@example.test", "fixture-password-hash");
        return jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, name + "@example.test");
    }
    private String match() {
        String id = UUID.randomUUID().toString(); jdbc.update("INSERT INTO matches(id,room_code,mode,started_at) VALUES (?,'ABCDEF','RACE',UTC_TIMESTAMP(3))", id); return id;
    }
    private String run(long owner, String match, int jumps) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO play_runs(id,user_id,kind,match_id,status,nickname,jumps,started_at,updated_at) VALUES (?,?,'ROOM',?,'ACTIVE','Guest Player',?,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))", id, owner, match, jumps);
        return id;
    }
    @Test void mergePreservesBothParticipationsAndIsIdempotentWithLateWriters() {
        long guest = guest(), account = account(); String match = match(), run = run(guest, match, 3); run(account, match, 5);
        history.merge(guest, account); history.merge(guest, account);
        assertThat(owners.read(guest).mergedInto()).isEqualTo(account);
        assertThat(jdbc.queryForObject("SELECT guest_token_hash FROM users WHERE id=?", String.class, guest)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM play_runs WHERE match_id=? AND user_id=?", Integer.class, match, account)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT SUM(jumps) FROM play_runs WHERE match_id=? AND user_id=?", Integer.class, match, account)).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT match_id) FROM play_runs WHERE user_id=?", Integer.class, account)).isEqualTo(1);
        owners.write(guest, canonical -> jdbc.update("UPDATE play_runs SET jumps=7,revision=2 WHERE id=? AND user_id=?", run, canonical));
        assertThat(jdbc.queryForObject("SELECT jumps FROM play_runs WHERE id=?", Integer.class, run)).isEqualTo(7);
        assertThatThrownBy(() -> history.merge(guest, account())).isInstanceOf(jumpking.network.ApiException.class);
    }
    @Test void mergeAndAnInFlightWriterSerializeWithoutLosingCounters() throws Exception {
        long account = account(), guest = guest(); String run = run(guest, match(), 1);
        var locked = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var threads = Executors.newFixedThreadPool(2)) {
            var writing = threads.submit(() -> owners.write(guest, canonical -> {
                locked.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Writer coordination timeout"); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
                return jdbc.update("UPDATE play_runs SET jumps=9,revision=2 WHERE id=? AND user_id=?", run, canonical);
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            var merging = threads.submit(() -> history.merge(guest, account)); release.countDown();
            assertThat(writing.get(5, TimeUnit.SECONDS)).isEqualTo(1); merging.get(5, TimeUnit.SECONDS);
        } finally { release.countDown(); }
        assertThat(jdbc.queryForObject("SELECT user_id FROM play_runs WHERE id=?", Long.class, run)).isEqualTo(account);
        assertThat(jdbc.queryForObject("SELECT jumps FROM play_runs WHERE id=?", Integer.class, run)).isEqualTo(9);
    }
    @Test void failedPointerWriteRollsBackTransferredRuns() {
        long guest = guest(), account = account(); String run = run(guest, match(), 2), trigger = "merge_fail_" + guest;
        jdbc.execute("CREATE TRIGGER " + trigger + " BEFORE UPDATE ON users FOR EACH ROW BEGIN IF NEW.id=" + guest
            + " AND NEW.merged_into_user_id IS NOT NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='integration rollback'; END IF; END");
        try { assertThatThrownBy(() -> history.merge(guest, account)).isInstanceOf(org.springframework.dao.DataAccessException.class); }
        finally { jdbc.execute("DROP TRIGGER " + trigger); }
        assertThat(owners.read(guest).mergedInto()).isNull();
        assertThat(jdbc.queryForObject("SELECT guest_token_hash FROM users WHERE id=?", String.class, guest)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT user_id FROM play_runs WHERE id=?", Long.class, run)).isEqualTo(guest);
    }
    @Test void activeMembershipPreventsMergeAndWriterRollbackKeepsMetrics() {
        long guest = guest(), account = account(); String run = run(guest, match(), 2);
        var member = new Memberships.Membership("ABCDEF", UUID.randomUUID().toString()); memberships.claim(account, member);
        try { assertThatThrownBy(() -> history.merge(guest, account)).isInstanceOf(jumpking.network.ApiException.class); }
        finally { memberships.release(account, member); }
        assertThat(owners.read(guest).mergedInto()).isNull();
        assertThatThrownBy(() -> owners.write(guest, canonical -> {
            jdbc.update("UPDATE play_runs SET jumps=10 WHERE id=? AND user_id=?", run, canonical); throw new IllegalStateException("rollback");
        })).hasMessage("rollback");
        assertThat(jdbc.queryForObject("SELECT jumps FROM play_runs WHERE id=?", Integer.class, run)).isEqualTo(2);
    }
}
