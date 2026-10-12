package com.markswell.interfaces.service;

import com.markswell.infrastructure.config.ChatMemoryConfig;
import com.markswell.infrastructure.tool.AdoptDog;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import com.markswell.infrastructure.rag.PersonalizedRetrievalAugmentor;

@RegisterAiService(
        retrievalAugmentor = PersonalizedRetrievalAugmentor.class,
        tools = { AdoptDog.class },
        chatMemoryProviderSupplier = ChatMemoryConfig.class
)
public interface DogAssistant {

    @SystemMessage("""
            - Você é uma assistente de adoção de cachorros.
            - Responder perguntas sobre os cachorros e com base na sua base de dados.
            - Fazer recomendações de cachorinhos.
            - Sempre verificar histórico da conversa para manter o contexto antes de responder
            """)
    String chat(@MemoryId String userId, String message);
}
