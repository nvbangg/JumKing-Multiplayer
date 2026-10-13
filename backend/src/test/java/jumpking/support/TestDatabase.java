package jumpking.support;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;

public final class TestDatabase {
    private static TestDatabase instance;
    public final DriverManagerDataSource dataSource;
    public final JdbcTemplate jdbc;
    private TestDatabase() throws Exception {
        String url = System.getenv("JK_TEST_DATABASE_URL");
        if (url == null) {
            var container = new MySQLContainer<>("mysql:8.4").withDatabaseName("jumpking_it"); container.start();
            Runtime.getRuntime().addShutdownHook(new Thread(container::stop));
            dataSource = new DriverManagerDataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword());
        } else dataSource = new DriverManagerDataSource(url, System.getenv("JK_TEST_DATABASE_USERNAME"), System.getenv("JK_TEST_DATABASE_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        String name = jdbc.queryForObject("SELECT DATABASE()", String.class);
        if (name == null || !name.startsWith("jumpking_it")
                || jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()", Integer.class) != 0) {
            throw new IllegalStateException("Integration requires a new, empty jumpking_it database");
        }
        for (String statement : Files.readString(Path.of("../scripts/schema.sql")).split(";")) {
            if (!statement.isBlank() && !statement.strip().startsWith("CREATE DATABASE") && !statement.strip().startsWith("USE ")) jdbc.execute(statement);
        }
    }
    public static synchronized TestDatabase get() {
        if (instance == null) try { instance = new TestDatabase(); }
        catch (Exception error) { throw new IllegalStateException("Could not initialize isolated integration database", error); }
        return instance;
    }
}
