package com.markswell.infrastructure.config;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Provedor de memória de chat por usuário (@MemoryId).
 * O AI Service declarativo é @RequestScoped e, ao ser destruído no fim de cada requisição,
 * o Quarkus chama {@link ChatMemory#clear()} para todas as memórias. Para o histórico
 * sobreviver entre requisições, as memórias ficam num mapa deste bean (@ApplicationScoped)
 * e o {@code clear()} é ignorado.
 */
@ApplicationScoped
public class ChatMemoryConfig implements Supplier<ChatMemoryProvider> {

    private static final int MAX_MESSAGES = 20;

    private final Map<Object, ChatMemory> memories = new ConcurrentHashMap<>();

    @Override
    public ChatMemoryProvider get() {
        return memoryId -> memories.computeIfAbsent(memoryId, id ->
                new PersistentChatMemory(MessageWindowChatMemory.builder()
                        .id(id)
                        .maxMessages(MAX_MESSAGES)
                        .build()));
    }

    /**
     * Encapsula um {@link ChatMemory} isolando o metodo {@link ChatMemory#clear()}
     * para que as mensagens não se percam do contexto no final da requisição
     */
    private record PersistentChatMemory(ChatMemory delegate) implements ChatMemory {

        @Override
        public Object id() {
            return delegate.id();
        }

        @Override
        public void add(ChatMessage message) {
            delegate.add(message);
        }

        @Override
        public List<ChatMessage> messages() {
            return delegate.messages();
        }

        @Override
        public void clear() {
            // intencionalmente vazio: evita o descarte do histórico ao fim da requisição
        }
    }
}

