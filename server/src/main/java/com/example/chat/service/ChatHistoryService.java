package com.example.chat.service;

import com.example.chat.dto.DynamicAnswer;
import com.example.chat.entity.ChatConversation;
import com.example.chat.entity.ChatMessage;
import com.example.chat.repository.ChatConversationRepository;
import com.example.chat.repository.ChatMessageRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists application-owned conversation history for each successful AI exchange.
 *
 * <p>This is deliberately separate from Spring AI's JDBC chat memory: the memory store
 * feeds prior turns back to the model, while this service keeps a queryable record that
 * includes the structured answer metadata (response type and language).</p>
 */
@Service
public class ChatHistoryService {

    private static final Logger log = LoggerFactory.getLogger(ChatHistoryService.class);

    private final ChatConversationRepository conversations;
    private final ChatMessageRepository messages;

    public ChatHistoryService(ChatConversationRepository conversations, ChatMessageRepository messages) {
        this.conversations = conversations;
        this.messages = messages;
    }

    /**
     * Records a question/answer exchange for the given conversation, creating the
     * conversation record on first use. Failures propagate to the caller, which treats
     * history persistence as best-effort.
     */
    @Transactional
    public void recordExchange(String conversationId, String userMessage, DynamicAnswer answer) {
        Instant now = Instant.now();

        ChatConversation conversation = conversations.findById(conversationId).orElse(null);
        if (conversation == null) {
            conversation = conversations.save(new ChatConversation(conversationId, now));
        } else {
            conversation.setUpdatedAt(now);
        }

        messages.save(new ChatMessage(conversation, userMessage, answer.answer(),
                answer.responseType(), answer.language(), now));

        log.debug("Recorded chat exchange. responseType={}", answer.responseType());
    }
}
