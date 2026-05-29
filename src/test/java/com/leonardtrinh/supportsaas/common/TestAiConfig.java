package com.leonardtrinh.supportsaas.common;

import org.mockito.Mockito;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("test")
class TestAiConfig {

    @Bean
    EmbeddingModel embeddingModel() {
        return Mockito.mock(EmbeddingModel.class);
    }
}
