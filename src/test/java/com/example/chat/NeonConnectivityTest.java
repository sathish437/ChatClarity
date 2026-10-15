package com.example.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Test;

/**
 * Live Neon PostgreSQL connectivity check. Read-only ({@code SELECT 1}); it
 * never creates, modifies or deletes data, and never logs credentials.
 *
 * <p>Runs only when {@code DB_URL}, {@code DB_USERNAME} and {@code DB_PASSWORD}
 * are present in the environment; otherwise it aborts without failing, so the
 * default suite stays green without secrets.
 */
class NeonConnectivityTest {

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
        }
    }
}
