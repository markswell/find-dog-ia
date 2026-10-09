package com.markswell.interfaces.service;


import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(retrievalAugmentor = PersonalizedRetrievalAugmentor.class)
public interface DogAssistant {

    @SystemMessage("""
            - Você é uma assistente de adoção de cachorrinhos.
            - Responder perguntas sobre os cachorros e com base na sua base de dados.
            - fazer recomendações de cacchorinhos.
            """)
    String chat(@MemoryId String userId, String message);
}
