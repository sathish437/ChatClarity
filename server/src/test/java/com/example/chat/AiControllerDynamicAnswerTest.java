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
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Mocked end-to-end checks that the controller serves question-type-specific
 * {@link DynamicAnswer} shapes straight from structured output: lean for general
 * chat, sections for exam prep, code blocks for programming questions.
 */
@WebMvcTest(AiController.class)
class AiControllerDynamicAnswerTest {

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
    private ChatClient.AdvisorSpec advisorSpec;

    @BeforeEach
    void stubChatClientChain() {
        requestSpec = Mockito.mock(ChatClient.ChatClientRequestSpec.class);
        callResponseSpec = Mockito.mock(ChatClient.CallResponseSpec.class);
        advisorSpec = Mockito.mock(ChatClient.AdvisorSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.advisors(Mockito.<Consumer<ChatClient.AdvisorSpec>>any())).thenAnswer(invocation -> {
            Consumer<ChatClient.AdvisorSpec> configure = invocation.getArgument(0);
            configure.accept(advisorSpec);
            return requestSpec;
        });
        when(requestSpec.call()).thenReturn(callResponseSpec);
    }

    private void stubEntity(DynamicAnswer answer) {
        when(callResponseSpec.entity(eq(DynamicAnswer.class))).thenReturn(answer);
    }

    @Test
    void generalQuestionReturnsLeanAnswer() throws Exception {
        stubEntity(new DynamicAnswer("general", "Vanakkam! How can I help?", "Thanglish",
                null, null, null, null, null, null));

        mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Hi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseType").value("general"))
                .andExpect(jsonPath("$.answer").value("Vanakkam! How can I help?"))
                .andExpect(jsonPath("$.language").value("Thanglish"))
                .andExpect(jsonPath("$.sections").doesNotExist())
                .andExpect(jsonPath("$.codeExamples").doesNotExist());

        verify(chatHistoryService).recordExchange(anyString(), eq("Hi"), Mockito.any(DynamicAnswer.class));
    }

    @Test
    void examQuestionReturnsSectionsAndKeyPoints() throws Exception {
        stubEntity(new DynamicAnswer("explanation", "TQM focuses on continuous improvement.",
                "English",
                List.of("Customer focus", "Continuous improvement"),
                List.of(
                        new DynamicAnswer.AnswerSection("Definition", "TQM is an organization-wide approach to quality."),
                        new DynamicAnswer.AnswerSection("Core Principles", "Focus on customers and continuous improvement.")),
                List.of("Pareto 80/20"),
                null,
                "TQM improves quality through the participation of everyone in the organization.",
                List.of("Want practice questions?")));

        mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Explain TQM for my exam\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseType").value("explanation"))
                .andExpect(jsonPath("$.keyPoints[0]").value("Customer focus"))
                .andExpect(jsonPath("$.sections[0].heading").value("Definition"))
                .andExpect(jsonPath("$.sections[0].body").value("TQM is an organization-wide approach to quality."))
                .andExpect(jsonPath("$.sections[1].heading").value("Core Principles"))
                .andExpect(jsonPath("$.sections[1].body").value("Focus on customers and continuous improvement."))
                .andExpect(jsonPath("$.examples[0]").value("Pareto 80/20"))
                .andExpect(jsonPath("$.conclusion").value("TQM improves quality through the participation of everyone in the organization."))
                .andExpect(jsonPath("$.codeExamples").doesNotExist());
    }

    @Test
    void aptitudeQuestionReturnsCalculationStepsAndFinalAnswer() throws Exception {
        stubEntity(new DynamicAnswer("steps", "15% of 240 is 36.", "English",
                null,
                List.of(
                        new DynamicAnswer.AnswerSection("Step 1", "10% of 240 = 24."),
                        new DynamicAnswer.AnswerSection("Step 2", "5% of 240 = 12."),
                        new DynamicAnswer.AnswerSection("Step 3", "24 + 12 = 36.")),
                null, null, "Answer: 36.", null));

        mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"What is 15% of 240? Show the steps.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseType").value("steps"))
                .andExpect(jsonPath("$.sections[0].body").value("10% of 240 = 24."))
                .andExpect(jsonPath("$.sections[1].body").value("5% of 240 = 12."))
                .andExpect(jsonPath("$.sections[2].body").value("24 + 12 = 36."))
                .andExpect(jsonPath("$.conclusion").value("Answer: 36."));
    }

    @Test
    void programmingQuestionReturnsCodeExamples() throws Exception {
        stubEntity(new DynamicAnswer("code", "Use a record for the DTO.",
                "English",
                null, null, null,
                List.of(new DynamicAnswer.CodeExample("java", "record Hi() {}")),
                null, null));

        mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"How do I write a Java record?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseType").value("code"))
                .andExpect(jsonPath("$.codeExamples[0].language").value("java"))
                .andExpect(jsonPath("$.codeExamples[0].code").value("record Hi() {}"))
                .andExpect(jsonPath("$.sections").doesNotExist());
    }

    @Test
    void tamilQuestionReturnsTamilResponse() throws Exception {
        stubEntity(new DynamicAnswer("general", "வணக்கம்! எப்படி உதவலாம்?", "Tamil",
                null, null, null, null, null, null));

        mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"வணக்கம்\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("வணக்கம்! எப்படி உதவலாம்?"))
                .andExpect(jsonPath("$.language").value("Tamil"));
    }

    @Test
    void thanglishQuestionReturnsThanglishResponse() throws Exception {
        stubEntity(new DynamicAnswer("general", "Vanakkam! Eppadi help pannattum?", "Thanglish",
                null, null, null, null, null, null));

        mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Vanakkam, enakku help venum\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Vanakkam! Eppadi help pannattum?"))
                .andExpect(jsonPath("$.language").value("Thanglish"));
    }

    @Test
    void followUpRequestsReuseTheSameSessionConversationId() throws Exception {
        MockHttpSession session = new MockHttpSession();
        when(callResponseSpec.entity(eq(DynamicAnswer.class))).thenReturn(
                new DynamicAnswer("explanation", "A variable stores a value.", "English",
                        null, null, null, null, null, null),
                new DynamicAnswer("explanation", "A local variable exists within a method.", "English",
                        null, null, null, null, null, null));

        mockMvc.perform(post("/api/ai/ask")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"What is a variable?\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/ai/ask")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Where does it exist?\"}"))
                .andExpect(status().isOk());

        verify(advisorSpec, times(2)).param(ChatMemory.CONVERSATION_ID, session.getId());
    }
}
