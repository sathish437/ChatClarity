package com.example.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Application-owned record of a single question/answer exchange. It captures the
 * structured metadata (response type and language) that Spring AI's chat-memory table
 * does not retain, alongside the user question and the answer text.
 */
@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private ChatConversation conversation;

    @Column(name = "user_message", nullable = false, columnDefinition = "TEXT")
    private String userMessage;

    @Column(name = "answer", nullable = false, columnDefinition = "TEXT")
    private String answer;

    @Column(name = "response_type", length = 32)
    private String responseType;

    @Column(name = "language", length = 32)
    private String language;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ChatMessage() {
        // Required by JPA.
    }

    public ChatMessage(ChatConversation conversation, String userMessage, String answer,
            String responseType, String language, Instant createdAt) {
        this.conversation = conversation;
        this.userMessage = userMessage;
        this.answer = answer;
        this.responseType = responseType;
        this.language = language;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public ChatConversation getConversation() {
        return conversation;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public String getAnswer() {
        return answer;
    }

    public String getResponseType() {
        return responseType;
    }

    public String getLanguage() {
        return language;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
