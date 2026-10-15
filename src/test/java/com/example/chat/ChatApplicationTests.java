package com.example.chat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.ai.google.genai.api-key=dummy-test-key")
class ChatApplicationTests {

	@Test
	void contextLoads() {
	}

}
