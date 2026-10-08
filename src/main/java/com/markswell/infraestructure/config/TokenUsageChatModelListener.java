package com.markswell.infraestructure.config;

import com.markswell.infraestructure.persistence.CacheRepository;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.langchain4j.runtime.ContextLocals;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class TokenUsageChatModelListener implements ChatModelListener {

    private static final Logger LOG = LoggerFactory.getLogger(TokenUsageChatModelListener.class);

    @Inject
    CacheRepository cacheRepository;

    @Inject
    RedisConfig redisConfig;

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        TokenUsage tokenUsage = responseContext.chatResponse().metadata().tokenUsage();
        String userId = ContextLocals.get("userId");

        if (tokenUsage == null) {
            LOG.warn("O modelo não retornou informações de TokenUsage na resposta.");
            return;
        }

        int inputTokens = tokenUsage.inputTokenCount() != null ? tokenUsage.inputTokenCount() : 0;
        int outputTokens = tokenUsage.outputTokenCount() != null ? tokenUsage.outputTokenCount() : 0;
        int totalTokens = tokenUsage.totalTokenCount() != null
                ? tokenUsage.totalTokenCount()
                : inputTokens + outputTokens;

        LOG.info("Token usage (real, retornado pelo modelo) -> input: {}, output: {}, total: {}",
                inputTokens, outputTokens, totalTokens);

        long accumulatedInput = cacheRepository.incrementBy(redisConfig.inputTokens().formatted(userId), inputTokens);
        long accumulatedOutput = cacheRepository.incrementBy(redisConfig.outputTokens().formatted(userId), outputTokens);
        long accumulatedTotal = cacheRepository.incrementBy(redisConfig.totalTokens().formatted(userId), totalTokens);

        LOG.info("Token usage acumulado (Redis) -> input: {}, output: {}, total: {}",
                accumulatedInput, accumulatedOutput, accumulatedTotal);
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        LOG.error("Falha na chamada ao chat model: {}", errorContext.error().getMessage(), errorContext.error());
    }
}
