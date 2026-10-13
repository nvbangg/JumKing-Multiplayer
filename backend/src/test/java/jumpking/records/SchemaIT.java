package jumpking.records;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;

class SchemaIT {
    private static MySQLContainer<?> container;
    private static JdbcTemplate jdbc;
    private static DriverManagerDataSource dataSource;

    @BeforeAll static void startDatabase() throws Exception {
        String url = System.getenv("JK_TEST_DATABASE_URL");
        if (url == null) {
            container = new MySQLContainer<>("mysql:8.4").withDatabaseName("jumpking_it");
            container.start();
            url = container.getJdbcUrl();
            dataSource = new DriverManagerDataSource(url, container.getUsername(), container.getPassword());
        } else {
            dataSource = new DriverManagerDataSource(url, System.getenv("JK_TEST_DATABASE_USERNAME"), System.getenv("JK_TEST_DATABASE_PASSWORD"));
        }
        jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).startsWith("jumpking_it");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()", Integer.class)).isZero();
        String script = Files.readString(Path.of("../scripts/schema.sql"));
        for (String statement : script.split(";")) {
            if (statement.isBlank() || statement.strip().startsWith("CREATE DATABASE") || statement.strip().startsWith("USE ")) continue;
            jdbc.execute(statement);
        }
    }

    @AfterAll static void stopDatabase() {
        if (container != null) container.stop();
        // Instance do runner sở hữu; không xóa database bên ngoài.
    }

    private long account() {
        String token = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO users(type,username,email,password_hash,created_at) VALUES ('ACCOUNT',?,?,?,UTC_TIMESTAMP(3))", token.substring(0, 24), token + "@example.test", "hash");
        return jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, token + "@example.test");
    }

    private void community(long userId) {
        jdbc.update("INSERT INTO play_runs(id,user_id,kind,status,snapshot,started_at,updated_at) VALUES (?,?,'COMMUNITY','ACTIVE',JSON_OBJECT('codecVersion',1),UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))", UUID.randomUUID().toString(), userId);
    }

    @Test void validatesActualSchemaAndIndexes() { new SchemaValidator(jdbc).validate(); }

    @Test void rejectsMissingConstraints() {
        jdbc.execute("ALTER TABLE matches DROP CHECK ck_matches_ticks");
        try { assertThatThrownBy(() -> new SchemaValidator(jdbc).validate()).hasMessageContaining("CHECK"); }
        finally { jdbc.execute("ALTER TABLE matches ADD CONSTRAINT ck_matches_ticks CHECK (duration_ticks BETWEEN 0 AND 9007199254740991)"); }
    }

    @Test void oneActiveCommunityAndRollback() {
        long owner = account();
        community(owner);
        assertThatThrownBy(() -> community(owner)).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        assertThatThrownBy(() -> tx.execute(status -> {
            jdbc.update("UPDATE play_runs SET status='ABANDONED',snapshot=NULL,ended_at=UTC_TIMESTAMP(3) WHERE user_id=?", owner);
            throw new IllegalStateException("rollback");
        })).hasMessage("rollback");
        assertThat(jdbc.queryForObject("SELECT status FROM play_runs WHERE user_id=?", String.class, owner)).isEqualTo("ACTIVE");
    }

    @Test void rejectsInvalidIdentityAndNullTimeLimit() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO users(type,created_at) VALUES ('ACCOUNT',UTC_TIMESTAMP(3))")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO matches(id,room_code,mode,started_at) VALUES (?,'ABCDEF','TIME_LIMIT',UTC_TIMESTAMP(3))", UUID.randomUUID().toString())).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test void rejectsFinishWithoutTimeAndInvalidSnapshot() {
        long owner = account();
        assertThatThrownBy(() -> jdbc.update("INSERT INTO play_runs(id,user_id,kind,status,started_at,updated_at,ended_at) VALUES (?,?,'SOLO','FINISHED',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))", UUID.randomUUID().toString(), owner)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO play_runs(id,user_id,kind,status,snapshot,started_at,updated_at) VALUES (?,?,'COMMUNITY','ACTIVE',JSON_ARRAY(),UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))", UUID.randomUUID().toString(), owner)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        community(owner);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM users WHERE id=?", owner)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
