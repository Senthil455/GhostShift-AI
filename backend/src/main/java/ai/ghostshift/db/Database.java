package ai.ghostshift.db;

import ai.ghostshift.config.AppConfig;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Small, explicit SQLite access layer. SQLite is local and single-user, so we use
 * short-lived connections with WAL mode rather than a pool or an ORM.
 */
@Singleton
public class Database {
    private static final Logger LOG = LoggerFactory.getLogger(Database.class);
    private static final String MIGRATION_RESOURCE_PATH = "/db/migration/";

    private final AppConfig config;
    private final String jdbcUrl;

    public Database(AppConfig config) {
        this.config = config;
        this.jdbcUrl = "jdbc:sqlite:" + config.databasePath();
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("SQLite JDBC driver is not on the classpath", e);
        }
        migrate();
    }

    public Connection open() {
        try {
            Connection connection = DriverManager.getConnection(jdbcUrl);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
                statement.execute("PRAGMA busy_timeout = 5000");
                statement.execute("PRAGMA journal_mode = WAL");
                statement.execute("PRAGMA synchronous = NORMAL");
            }
            return connection;
        } catch (SQLException e) {
            throw new DbException("Could not open the local database", e);
        }
    }

    public <T> T withConnection(SqlFunction<Connection, T> work) {
        try (Connection connection = open()) {
            return work.apply(connection);
        } catch (SQLException e) {
            throw new DbException("Local database operation failed", e);
        }
    }

    public <T> T inTransaction(SqlFunction<Connection, T> work) {
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                T result = work.apply(connection);
                connection.commit();
                return result;
            } catch (RuntimeException | SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DbException("Local database transaction failed", e);
        }
    }

    public int update(String sql, Object... parameters) {
        return withConnection(connection -> execute(connection, sql, parameters));
    }

    public long insert(String sql, Object... parameters) {
        return withConnection(connection -> insertAndReturnKey(connection, sql, parameters));
    }

    public <T> List<T> query(String sql, RowMapper<T> mapper, Object... parameters) {
        return withConnection(connection -> query(connection, sql, mapper, parameters));
    }

    public <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... parameters) {
        List<T> results = query(sql, mapper, parameters);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public static int execute(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            return statement.executeUpdate();
        }
    }

    public static long insertAndReturnKey(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, parameters);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0L;
            }
        }
    }

    public static <T> List<T> query(Connection connection, String sql, RowMapper<T> mapper, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<T> results = new ArrayList<>();
                while (resultSet.next()) {
                    results.add(mapper.map(resultSet));
                }
                return results;
            }
        }
    }

    public static <T> Optional<T> queryOne(Connection connection, String sql, RowMapper<T> mapper, Object... parameters) throws SQLException {
        List<T> results = query(connection, sql, mapper, parameters);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    private static void bind(PreparedStatement statement, Object... parameters) throws SQLException {
        if (parameters == null) {
            return;
        }
        for (int i = 0; i < parameters.length; i++) {
            Object value = parameters[i];
            if (value instanceof Enum<?> enumValue) {
                statement.setString(i + 1, enumValue.name());
            } else if (value instanceof Boolean bool) {
                statement.setInt(i + 1, bool ? 1 : 0);
            } else {
                statement.setObject(i + 1, value);
            }
        }
    }

    private void migrate() {
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS schema_migration (
                            version INTEGER PRIMARY KEY,
                            name TEXT NOT NULL,
                            applied_at TEXT NOT NULL
                        )
                        """);
            }
            List<String> migrations = listMigrations();
            for (String migration : migrations) {
                int version = Integer.parseInt(migration.substring(1, migration.indexOf("__")));
                boolean applied;
                try (PreparedStatement check = connection.prepareStatement("SELECT 1 FROM schema_migration WHERE version = ?")) {
                    check.setInt(1, version);
                    try (ResultSet rs = check.executeQuery()) {
                        applied = rs.next();
                    }
                }
                if (applied) {
                    continue;
                }
                String sql = readResource(MIGRATION_RESOURCE_PATH + migration);
                for (String statementSql : splitStatements(sql)) {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute(statementSql);
                    }
                }
                try (PreparedStatement record = connection.prepareStatement(
                        "INSERT INTO schema_migration(version, name, applied_at) VALUES (?, ?, ?)")) {
                    record.setInt(1, version);
                    record.setString(2, migration);
                    record.setString(3, Instant.now().toString());
                    record.executeUpdate();
                }
                LOG.info("Applied database migration {}", migration);
            }
            connection.commit();
        } catch (SQLException | IOException e) {
            throw new DbException("Database migration failed", e);
        }
    }

    private List<String> listMigrations() throws IOException {
        // Migrations are committed resources; this list is intentionally explicit so
        // packaging mistakes are visible at startup.
        List<String> names = new ArrayList<>(List.of("V1__initial_schema.sql"));
        names.sort(Comparator.naturalOrder());
        return names;
    }

    private String readResource(String name) throws IOException {
        try (InputStream stream = Database.class.getResourceAsStream(name)) {
            if (stream == null) {
                throw new IOException("Missing database migration " + name);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static List<String> splitStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            }
            if (c == ';' && !inSingleQuote && !inDoubleQuote) {
                String statement = current.toString().strip();
                if (!statement.isBlank()) {
                    statements.add(statement);
                }
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        String tail = current.toString().strip();
        if (!tail.isBlank()) {
            statements.add(tail);
        }
        return statements;
    }

    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet resultSet) throws SQLException;
    }

    @FunctionalInterface
    public interface SqlFunction<T, R> {
        R apply(T value) throws SQLException;
    }

    public static class DbException extends RuntimeException {
        public DbException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
