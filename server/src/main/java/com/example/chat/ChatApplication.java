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

			Respond in the language and style requested by the user. If no language is requested,
			use the language and script of the latest user message.

			If the user uses English, respond in English.
			If the user uses Tamil script, respond in Tamil script.
			If the user uses Thanglish, respond in Thanglish.
			If the user mixes languages, respond naturally using the same language mix.

			Do not translate the user's message unless explicitly requested.

			Choose the responseType that best fits the latest question: general, explanation, steps,
			comparison, or code. Answer follow-up questions using the conversation context, focus on
			the new question, and do not repeat the previous answer unless needed for clarity.
			Use simple language when requested.

			- General questions: give a direct answer, then only useful explanation or examples.
			- Aptitude/math questions: use responseType 'steps'; include given information and formula
			  when useful, numbered calculation sections, an optional shortcut, and the final answer
			  in conclusion. Check the arithmetic.
			- Programming questions: explain the concept and approach in 'answer'; include labelled
			  codeExamples only when code is requested or materially helps answer the question, and
			  explain non-obvious code.
			- Exam questions: give a short introduction, clear titled sections with explanations,
			  relevant examples and keyPoints, then a concise conclusion when useful.
			- Comparison questions: use sections with the same comparison criteria for each option.
			- Greetings and simple facts: give a brief direct answer without unnecessary sections.

			Keep every field in the user's requested language and script where applicable. Set
			'language' to exactly English, Tamil, Thanglish, or Mixed. Do not mix languages unless
			the user does or requests it.
			Only include relevant optional fields. Keep answer, sections, keyPoints, examples, and
			conclusion complementary rather than repeating the same information. Do not emit empty
			items, duplicate headings, or follow-up questions unless they add value.
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
