package com.example.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Live Neon PostgreSQL connectivity check. Read-only ({@code SELECT 1}); it
 * never creates, modifies or deletes data, and never logs credentials.
 *
 * <p>Runs only when {@code DB_URL}, {@code DB_USERNAME} and {@code DB_PASSWORD}
 * are present in the environment; otherwise it aborts without failing, so the
 * default suite stays green without secrets.
 */
class NeonConnectivityTest {

    private static final Logger log = LoggerFactory.getLogger(NeonConnectivityTest.class);

    @Test
    void datasourceConnectsAndAnswersSelectOne() throws Exception {
        String url = System.getenv("DB_URL");
        String username = System.getenv("DB_USERNAME");
        String password = System.getenv("DB_PASSWORD");
        assumeTrue(url != null && !url.isBlank()
                        && username != null && !username.isBlank()
                        && password != null,
                "DB_URL/DB_USERNAME/DB_PASSWORD not set; skipping live Neon check");
        assertThat(url).startsWith("jdbc:postgresql://");
        assertThat(url).contains("sslmode=require");

        try (Connection connection = DriverManager.getConnection(url, username, password);
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT 1")) {
            assertThat(connection.isValid(10)).isTrue();
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(1);

            List<String> userTables = new ArrayList<>();
            try (Statement metadata = connection.createStatement();
                    ResultSet tables = metadata.executeQuery("""
                            SELECT table_schema, table_name
                            FROM information_schema.tables
                            WHERE table_schema NOT IN ('pg_catalog', 'information_schema')
                              AND table_type = 'BASE TABLE'
                            ORDER BY table_schema, table_name
                            """)) {
                while (tables.next()) {
                    userTables.add(tables.getString("table_schema") + "." + tables.getString("table_name"));
                }
            }
            log.info("Neon connectivity verified with SELECT 1; user base tables found={}", userTables);
        }
    }
}
