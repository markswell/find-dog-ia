package com.markswell.interfaces.service;


import dev.langchain4j.service.MemoryId;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(retrievalAugmentor = PersonalizedRetrievalAugmentor.class)
public interface DogAssistant {

    String chat(@MemoryId String userId, String message);
}
