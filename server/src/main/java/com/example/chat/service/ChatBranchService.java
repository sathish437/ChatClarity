package com.example.chat.service;

import com.example.chat.entity.ChatMessage;
import com.example.chat.repository.ChatMessageRepository;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rewinds a conversation to an earlier branch point so an edited user message can be
 * resent without stale context.
 *
 * <p>Two stores must be rewound together, both keyed by the HTTP session id that is also
 * the chat-memory conversation id:</p>
 * <ul>
 *   <li>Spring AI's JDBC chat memory ({@code SPRING_AI_CHAT_MEMORY}), which the model
 *       advisor replays on every call. The {@link ChatMemory} API only supports
 *       get/add/clear, so rewinding is implemented as get, clear, then re-add the
 *       retained prefix.</li>
 *   <li>The application-owned {@code chat_messages} history, where rows after the
 *       retained prefix are deleted.</li>
 * </ul>
 *
 * <p>Isolation is preserved because every operation is scoped to this session's
 * conversation id; other sessions are untouched.</p>
 */
@Service
public class ChatBranchService {

    private static final Logger log = LoggerFactory.getLogger(ChatBranchService.class);

    private final ChatMemory chatMemory;
    private final ChatMessageRepository messages;

    public ChatBranchService(ChatMemory chatMemory, ChatMessageRepository messages) {
        this.chatMemory = chatMemory;
        this.messages = messages;
    }

    /**
     * Discards every exchange after the first {@code keepExchanges} completed exchanges.
     * Each exchange is one user message plus its assistant response.
     */
    @Transactional
    public void truncateToExchanges(String conversationId, int keepExchanges) {
        int keep = Math.max(0, keepExchanges);

        List<Message> retained = new ArrayList<>(chatMemory.get(conversationId));
        if (retained.size() > keep * 2) {
            retained = new ArrayList<>(retained.subList(0, keep * 2));
            chatMemory.clear(conversationId);
            if (!retained.isEmpty()) {
                chatMemory.add(conversationId, retained);
            }
        }

        List<Long> staleIds = messages.findByConversationIdOrderByIdAsc(conversationId).stream()
                .skip(keep)
                .map(ChatMessage::getId)
                .toList();
        if (!staleIds.isEmpty()) {
            messages.deleteAllById(staleIds);
        }

        log.debug("Truncated conversation branch. keepExchanges={}", keep);
    }
}
