package com.example.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Application-owned record of a chat conversation, keyed by the HTTP session id that
 * Spring AI also uses as its chat-memory conversation id.
 *
 * <p>This is complementary to Spring AI's JDBC chat memory ({@code SPRING_AI_CHAT_MEMORY}),
 * which only replays raw messages to the model. It stores conversation metadata so the
 * application can list and audit exchanges without touching the framework-managed table.</p>
 */
@Entity
@Table(name = "chat_conversations")
public class ChatConversation {

    @Id
    @Column(name = "id", nullable = false, length = 128)
    private String id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ChatConversation() {
        // Required by JPA.
    }

    public ChatConversation(String id, Instant createdAt) {
        this.id = id;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
