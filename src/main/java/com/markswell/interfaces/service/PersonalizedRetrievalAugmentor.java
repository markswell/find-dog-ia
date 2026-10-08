package com.markswell.interfaces.service;

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
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

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


        // 🔥 busca no grafo
        List<String> dogs = graphRag.search(userId, questionAsStringList(question));

        // 🔥 embedding da query
        var results = getSegmentEmbeddingSearchResult(question);

        List<TextSegment> segments = results.matches().stream()
                .map(EmbeddingMatch::embedded)
                .toList();

        // 🔥 re-ranking
        List<TextSegment> ranked = segments.stream()
                .sorted((a, b) ->
                        Double.compare(
                                score(b, question, dogs),
                                score(a, question, dogs)
                        )
                )
                .toList();

        List<TextSegment> selectedSegments = new ArrayList<>();

        for (TextSegment segment : ranked) {

            String segmentText = """
            Dog: %s
            Temperamento: %s
            Descrição: %s
            
            """.formatted(
                    segment.metadata().getString("dog"),
                    segment.metadata().getString("temperamento"),
                    segment.text()
            );
            selectedSegments.add(segment);
        }

        LOG.info("Chunks selected: " + selectedSegments.size());


        // 🔥 transforma em conteúdo para o LLM
        StringBuilder contextBuilder = new StringBuilder();

        contextBuilder.append("Informações relevantes sobre cães:\n\n");


        for (TextSegment s : selectedSegments) {
            var dog = s.metadata().getString("dog");
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

    private List<String> questionAsStringList(String question) {
        return Arrays.stream(question.split(" ")).filter(a -> !a.equals(" ")).toList();
    }

    private static String getQuestion(AugmentationRequest request) {
        String text = request.chatMessage().toString();
        int index = text.indexOf("text");
        return text.substring(index)
                .split("}")[0].split("=")[1]
                .replace("\"", "")
                .trim();
    }

    private Double score(TextSegment segment, String question, List<String> dogs) {
        double score = 0;

        var text = segment.text().toLowerCase();
        var q = question.toLowerCase();

        // 1️⃣ match com cães recomendados pelo grafo
        for (var dog : dogs) {
            if (text.contains(dog.toLowerCase())) {
                score += 3;
            }
        }

        // 2️⃣ keyword overlap
        for (var token : q.replaceAll("[^a-z ]","").split("\\s+")) {
            if (text.contains(token)) {
                score += 1;
            }
        }

        return score;
    }

    @Override
    public RetrievalAugmentor get() {
        return this;
    }
}