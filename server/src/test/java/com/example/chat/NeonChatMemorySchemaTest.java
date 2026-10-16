package com.example.chat;

import com.example.chat.entity.ChatConversation;
import com.example.chat.entity.ChatMessage;
import com.example.chat.repository.ChatConversationRepository;
import com.example.chat.repository.ChatMessageRepository;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies non-destructive schema initialization against the configured Neon database
 * when DB_URL is supplied to the test JVM: Spring AI's JDBC chat-memory table plus the
 * application-owned JPA history tables created by Hibernate {@code ddl-auto=update}.
 */
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = "^jdbc:postgresql://.+")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.autoconfigure.exclude=",
                "spring.jpa.hibernate.ddl-auto=update",
                "spring.ai.google.genai.api-key=test-key",
                "spring.ai.chat.memory.repository.jdbc.initialize-schema=always"
        })
class NeonChatMemorySchemaTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("DB_PASSWORD"));
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JdbcChatMemoryRepository chatMemoryRepository;

    @Autowired
    private ChatConversationRepository conversationRepository;

    @Autowired
    private ChatMessageRepository messageRepository;

    @Test
    void springAiJdbcMemoryRepositoryAndTableAreInitialized() {
        Boolean tableExists = jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.spring_ai_chat_memory') IS NOT NULL",
                Boolean.class);

        assertThat(chatMemoryRepository).isNotNull();
        assertThat(tableExists).isTrue();
    }

    @Test
    void applicationJpaHistoryTablesAreCreated() {
        Boolean conversationTableExists = jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.chat_conversations') IS NOT NULL",
                Boolean.class);
        Boolean messageTableExists = jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.chat_messages') IS NOT NULL",
                Boolean.class);

        assertThat(conversationRepository).isNotNull();
        assertThat(messageRepository).isNotNull();
        assertThat(conversationTableExists).isTrue();
        assertThat(messageTableExists).isTrue();
    }

    @Test
    @Transactional
    void historyRoundTripPersistsAndRollsBack() {
        String conversationId = "test-" + UUID.randomUUID();
        Instant now = Instant.now();

        conversationRepository.save(new ChatConversation(conversationId, now));
        messageRepository.save(new ChatMessage(
                conversationRepository.getReferenceById(conversationId),
                "hello", "hi", "general", "English", now));

        List<ChatMessage> messages =
                messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getAnswer()).isEqualTo("hi");
        assertThat(messages.get(0).getResponseType()).isEqualTo("general");
        assertThat(messages.get(0).getLanguage()).isEqualTo("English");
    }
}
