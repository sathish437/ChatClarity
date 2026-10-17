package com.example.chat;

import com.example.chat.service.ChatBranchService;
import com.example.chat.service.ChatHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = "spring.ai.google.genai.api-key=dummy-test-key")
class ChatApplicationTests {

	// The real service needs a JPA repository, which is excluded in tests; replace it
	// so the context still loads without a database.
	@MockitoBean
	ChatHistoryService chatHistoryService;

	// Same exclusion applies to the branch-truncation service used by /api/ai/edit.
	@MockitoBean
	ChatBranchService chatBranchService;

	@Test
	void contextLoads() {
	}

}
