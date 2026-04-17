package com.ExpenseTracker.infrastructure.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
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
