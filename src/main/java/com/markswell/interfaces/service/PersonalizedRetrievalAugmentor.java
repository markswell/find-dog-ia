package com.markswell.interfaces.service;

import com.markswell.domain.model.DogGraphHit;
import com.markswell.infraestructure.persistence.CacheRepository;
import com.markswell.interfaces.controller.DogResource;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@ApplicationScoped
public class PersonalizedRetrievalAugmentor implements RetrievalAugmentor, Supplier<RetrievalAugmentor> {

    @Inject
    CacheRepository cacheRepository;

    @Inject
    EmbeddingStore<TextSegment> store;

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    PersonalizedGraphRagService graphRag;

    private final Logger LOG = LoggerFactory.getLogger(DogResource.class);

    private static final int MODEL_CONTEXT_WINDOW = 8192;
    private static final int RESERVED_FOR_RESPONSE = 1500;
    private static final int RESERVED_FOR_SYSTEM_PROMPT = 500;

    @Override
    public AugmentationResult augment(AugmentationRequest request) {

        String userId = request.metadata().chatMemoryId().toString();

        String question = getQuestion(request);


        // 🔥 busca no grafo (entidades da pergunta + perfil do usuário)
        List<DogGraphHit> graphHits = graphRag.search(userId, question);

        Map<String, Long> graphScores = graphHits.stream()
                .collect(Collectors.toMap(
                        h -> GraphEntityExtractor.normalize(h.nome()),
                        DogGraphHit::score,
                        Math::max));

        // 🔥 embedding da query
        var results = getSegmentEmbeddingSearchResult(question);

        // 🔥 re-ranking (grafo + similaridade vetorial + overlap de termos)
        List<TextSegment> selectedSegments = results.matches().stream()
                .sorted(Comparator.comparingDouble(
                        (EmbeddingMatch<TextSegment> m) -> score(m, question, graphScores)).reversed())
                .map(EmbeddingMatch::embedded)
                .toList();

        LOG.info("Graph hits: " + graphHits.size() + " | Chunks selected: " + selectedSegments.size());


        // 🔥 transforma em conteúdo para o LLM
        StringBuilder contextBuilder = new StringBuilder();

        if (!graphHits.isEmpty()) {
            contextBuilder.append("Cães recomendados pelo grafo de conhecimento (porte/ambiente compatíveis):\n");
            graphHits.forEach(h -> contextBuilder.append(h.toContext()).append('\n'));
            contextBuilder.append('\n');
        }

        contextBuilder.append("Informações relevantes sobre cães:\n\n");


        for (TextSegment s : selectedSegments) {
            var dog = s.metadata().getString("nome");
            var temperamento = s.metadata().getString("temperamento");

            contextBuilder.append("""
                    Dog: %s
                    Temperamento: %s
                    Descrição: %s
                    
                    """.formatted(
                    dog,
                    temperamento,
                    s.text()));
        }

        var context = contextBuilder.toString();
        var content = Content.from(context);
        var contents = new ArrayList<Content>();
        contents.add(content);

        return AugmentationResult.builder()
            .contents(contents)
            .chatMessage(request.chatMessage())
            .build();
    }

    @WithSpan("rag.embeddingSearch")
    public EmbeddingSearchResult<TextSegment> getSegmentEmbeddingSearchResult(String question) {
        var queryEmbedding = embeddingModel.embed(question).content();

        // 🔥 NOVA API de search
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(10)
                .build();

        var results = store.search(searchRequest);
        return results;
    }

    private static String getQuestion(AugmentationRequest request) {
        String text = request.chatMessage().toString();
        int index = text.indexOf("text");
        return text.substring(index)
                .split("}")[0].split("=")[1]
                .replace("\"", "")
                .trim();
    }

    static double score(EmbeddingMatch<TextSegment> match, String question, Map<String, Long> graphScores) {
        TextSegment segment = match.embedded();
        double score = match.score() == null ? 0 : match.score();

        // 1️⃣ chunk pertence a um cão recomendado pelo grafo (via metadata, não via texto)
        String dog = GraphEntityExtractor.normalize(segment.metadata().getString("nome"));
        score += graphScores.getOrDefault(dog, 0L);

        // 2️⃣ keyword overlap (normalizado, sem acentos, ignorando palavras curtas)
        Set<String> textTokens = new HashSet<>(GraphEntityExtractor.tokenize(segment.text()));
        for (var token : GraphEntityExtractor.tokenize(question)) {
            if (token.length() > 3 && textTokens.contains(token)) {
                score += 0.5;
            }
        }

        return score;
    }

    @Override
    public RetrievalAugmentor get() {
        return this;
    }
}