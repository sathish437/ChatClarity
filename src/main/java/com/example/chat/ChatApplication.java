package com.example.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ChatApplication {

	private static final Logger log = LoggerFactory.getLogger(ChatApplication.class);

	private static final String MULTILINGUAL_SYSTEM_PROMPT = """
			You are a multilingual conversational assistant.

			The user may communicate in English, Tamil, Thanglish, or a mixture of these languages.

			Understand the user's intended meaning regardless of the language or script.

			Respond in the same language and style used by the user.

			If the user uses English, respond in English.
			If the user uses Tamil script, respond in Tamil script.
			If the user uses Thanglish, respond in Thanglish.
			If the user mixes languages, respond naturally using the same language mix.

			Do not translate the user's message unless explicitly requested.

			When responding with structured output, put your reply in the 'answer' field using the same language and style, classify the language in the 'language' field, and suggest up to 3 follow-up questions in the 'followUpQuestions' field.
			""";

	public static void main(String[] args) {
		SpringApplication.run(ChatApplication.class, args);
	}

	@Bean
	ApplicationRunner logApiKeyStatus(org.springframework.core.env.Environment env) {
		return args -> {
			String apiKey = env.getProperty("spring.ai.google.genai.api-key");
			boolean isConfigured = apiKey != null && !apiKey.trim().isEmpty();
			log.info("Gemini API key configured: {}", isConfigured);
		};
	}

	@Bean
	ChatClient chatClient(ChatClient.Builder builder, ChatMemory chatMemory) {
		return builder
				.defaultSystem(MULTILINGUAL_SYSTEM_PROMPT)
				.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
				.build();
	}

	@Bean
	public io.swagger.v3.oas.models.OpenAPI customOpenAPI() {
		return new io.swagger.v3.oas.models.OpenAPI()
				.info(new io.swagger.v3.oas.models.info.Info()
						.title("Spring AI Gemini Chat API")
						.description("OpenAPI documentation for Spring AI ChatClient integration with Google Gemini, Chat Memory, and Multilingual support")
						.version("1.0.0"));
	}

}
