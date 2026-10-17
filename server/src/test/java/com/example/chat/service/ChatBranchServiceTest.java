package com.example.chat.service;

import com.example.chat.entity.ChatConversation;
import com.example.chat.entity.ChatMessage;
import com.example.chat.repository.ChatMessageRepository;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * Checks that branch truncation rewinds both the Spring AI chat memory and the
 * application-owned history, scoped to the given conversation id.
 */
@ExtendWith(MockitoExtension.class)
class ChatBranchServiceTest {

    @Mock
    private ChatMemory chatMemory;

    @Mock
    private ChatMessageRepository messages;

    private ChatBranchService branches;

    @BeforeEach
    void setUp() {
        branches = new ChatBranchService(chatMemory, messages);
    }

    private static List<Message> memoryOfThreeExchanges() {
        return List.of(
                new UserMessage("first question"),
                new AssistantMessage("first answer"),
                new UserMessage("second question"),
                new AssistantMessage("second answer"),
                new UserMessage("third question"),
                new AssistantMessage("third answer"));
    }

    private static List<ChatMessage> historyOfThreeExchanges() {
        ChatConversation conversation = new ChatConversation("session-1", Instant.now());
        Instant now = Instant.now();
        return List.of(
                new ChatMessage(conversation, "first question", "first answer", "general", "English", now),
                new ChatMessage(conversation, "second question", "second answer", "general", "English", now),
                new ChatMessage(conversation, "third question", "third answer", "general", "English", now));
    }

    @Test
    void truncateToOneExchangeKeepsOnlyFirstPair() {
        when(chatMemory.get("session-1")).thenReturn(memoryOfThreeExchanges());
        when(messages.findByConversationIdOrderByIdAsc("session-1")).thenReturn(historyOfThreeExchanges());

        branches.truncateToExchanges("session-1", 1);

        verify(chatMemory).clear("session-1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Message>> kept = ArgumentCaptor.forClass(List.class);
        verify(chatMemory).add(eq("session-1"), kept.capture());
        List<Message> retained = kept.getValue();
        assertEquals(2, retained.size());
        assertEquals("first question", ((UserMessage) retained.get(0)).getText());
        assertEquals("first answer", ((AssistantMessage) retained.get(1)).getText());

        ArgumentCaptor<List<Long>> deleted = ArgumentCaptor.forClass(List.class);
        verify(messages).deleteAllById(deleted.capture());
        assertEquals(2, deleted.getValue().size());
    }

    @Test
    void truncateToZeroClearsEverything() {
        when(chatMemory.get("session-1")).thenReturn(memoryOfThreeExchanges());
        when(messages.findByConversationIdOrderByIdAsc("session-1")).thenReturn(historyOfThreeExchanges());

        branches.truncateToExchanges("session-1", 0);

        verify(chatMemory).clear("session-1");
        verify(chatMemory, never()).add(anyString(), anyList());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> deleted = ArgumentCaptor.forClass(List.class);
        verify(messages).deleteAllById(deleted.capture());
        assertEquals(3, deleted.getValue().size());
    }

    @Test
    void truncateWithinBoundsLeavesStoresUntouched() {
        when(chatMemory.get("session-1")).thenReturn(memoryOfThreeExchanges());
        when(messages.findByConversationIdOrderByIdAsc("session-1")).thenReturn(historyOfThreeExchanges());

        branches.truncateToExchanges("session-1", 3);

        verify(chatMemory, never()).clear(anyString());
        verify(chatMemory, never()).add(anyString(), anyList());
        verify(messages, never()).deleteAllById(anyList());
    }
}
