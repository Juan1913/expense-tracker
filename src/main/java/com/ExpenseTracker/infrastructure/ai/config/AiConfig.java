package com.ExpenseTracker.infrastructure.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Value("${app.ai.chat.base-url}")
    private String chatBaseUrl;

    @Value("${app.ai.chat.api-key}")
    private String chatApiKey;

    @Value("${app.ai.chat.model}")
    private String chatModelName;

    @Value("${app.ai.chat.temperature:0.7}")
    private double chatTemperature;

    @Value("${app.ai.embedding.base-url}")
    private String embeddingBaseUrl;

    @Value("${app.ai.embedding.api-key}")
    private String embeddingApiKey;

    @Value("${app.ai.embedding.model}")
    private String embeddingModelName;

    @Bean
    public OpenAiChatModel openAiChatModel() {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(chatBaseUrl)
                .apiKey(chatApiKey)
                .build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(chatModelName)
                .temperature(chatTemperature)
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .build();
    }

    @Bean
    public OpenAiEmbeddingModel openAiEmbeddingModel() {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(embeddingBaseUrl)
                .apiKey(embeddingApiKey)
                .build();
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(embeddingModelName)
                .build();
        return new OpenAiEmbeddingModel(api, MetadataMode.EMBED, options);
    }

    @Bean
    public ChatClient chatClient(OpenAiChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("""
                        Eres FinBot, un asesor financiero personal inteligente y empático.
                        Tu objetivo es ayudar al usuario a tomar decisiones financieras informadas
                        basándote en sus datos reales.
                        Responde siempre en español, de forma clara y directa.
                        """)
                .build();
    }
}
