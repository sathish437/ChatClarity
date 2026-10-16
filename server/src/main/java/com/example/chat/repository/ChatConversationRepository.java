package com.example.chat.repository;

import com.example.chat.entity.ChatConversation;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for application-owned conversation records keyed by HTTP session id.
 */
public interface ChatConversationRepository extends JpaRepository<ChatConversation, String> {
}
