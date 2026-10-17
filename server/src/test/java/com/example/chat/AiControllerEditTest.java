package com.example.chat;

import com.example.chat.controller.AiController;
import com.example.chat.dto.DynamicAnswer;
import com.example.chat.service.ChatBranchService;
import com.example.chat.service.ChatHistoryService;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Checks that {@code POST /api/ai/edit} rewinds the stored conversation branch
 * before answering the edited message.
 */
@WebMvcTest(AiController.class)
class AiControllerEditTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatClient chatClient;

    @MockitoBean
    private ChatHistoryService chatHistoryService;

    @MockitoBean
    private ChatBranchService chatBranchService;

    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec callResponseSpec;

    @BeforeEach
    void stubChatClientChain() {
        requestSpec = org.mockito.Mockito.mock(ChatClient.ChatClientRequestSpec.class);
        callResponseSpec = org.mockito.Mockito.mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.advisors(org.mockito.ArgumentMatchers.<Consumer<ChatClient.AdvisorSpec>>any()))
                .thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
    }

    @Test
    void editTruncatesBranchThenAnswers() throws Exception {
        DynamicAnswer stubbed = new DynamicAnswer(
                "general",
                "Updated answer.",
                "English",
                null, null, null, null, null,
                null);
        when(callResponseSpec.entity(eq(DynamicAnswer.class))).thenReturn(stubbed);

        mockMvc.perform(post("/api/ai/edit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Edited question\",\"keepExchanges\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Updated answer."));

        verify(chatBranchService, times(1)).truncateToExchanges(anyString(), eq(1));
        verify(chatHistoryService, times(1)).recordExchange(anyString(), eq("Edited question"), eq(stubbed));
    }

    @Test
    void editRejectsBlankMessage() throws Exception {
        mockMvc.perform(post("/api/ai/edit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"  \",\"keepExchanges\":0}"))
                .andExpect(status().isBadRequest());

        verify(chatBranchService, times(0)).truncateToExchanges(anyString(), eq(0));
    }

    @Test
    void editRejectsNegativeKeepExchanges() throws Exception {
        mockMvc.perform(post("/api/ai/edit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Edited question\",\"keepExchanges\":-1}"))
                .andExpect(status().isBadRequest());

        verify(chatBranchService, times(0)).truncateToExchanges(anyString(), eq(-1));
    }
}
