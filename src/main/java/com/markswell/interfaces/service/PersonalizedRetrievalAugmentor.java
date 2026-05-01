package com.markswell.interfaces.service;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.*;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

@ApplicationScoped
public class PersonalizedRetrievalAugmentor implements RetrievalAugmentor, Supplier<RetrievalAugmentor> {

    @Inject
    EmbeddingStore<TextSegment> store;

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    PersonalizedGraphRagService graphRag;

    @Override
    public AugmentationResult augment(AugmentationRequest request) {

        String userId = request.metadata().chatMemoryId().toString();

        String question = getQuestion(request);

        // 🔥 busca no grafo
        List<String> dogs = graphRag.search(userId, questionAsStringList(question));

        // 🔥 embedding da query
        var queryEmbedding = embeddingModel.embed(question).content();

        // 🔥 NOVA API de search
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(10)
                .build();

        var results = store.search(searchRequest);

        List<TextSegment> segments = results.matches().stream()
                .map(EmbeddingMatch::embedded)
                .toList();

        // 🔥 re-ranking
        List<TextSegment> ranked = segments.stream()
                .sorted((a,b) ->
                        Double.compare(
                                score(b, question, dogs),
                                score(a, question, dogs)
                        )
                )
                .limit(5)
                .toList();

        // 🔥 transforma em conteúdo para o LLM
        StringBuilder contextBuilder = new StringBuilder();

        contextBuilder.append("Informações relevantes sobre cães:\n\n");


        for (TextSegment s : ranked) {
            String dog = s.metadata().getString("dog");
            String temperamento = s.metadata().getString("temperamento");

            contextBuilder.append("""
                    Dog: %s
                    Temperamento: %s
                    Descrição: %s
                    
                    """.formatted(
                    dog,
                    temperamento,
                    s.text()));
        }

        String context = contextBuilder.toString();
        Content content = Content.from(context);
        List<Content> contents = new ArrayList<>();
        contents.add(content);

        return AugmentationResult.builder()
            .contents(contents)
            .chatMessage(request.chatMessage())
            .build();
    }

    private List<String> questionAsStringList(String question) {
        return Arrays.stream(question.split(" ")).filter(a -> !a.equals(" ")).toList();
    }

    private static String getQuestion(AugmentationRequest request) {
        String text = request.chatMessage().toString();
        int index = text.indexOf("text");
        String question = text.substring(index).split("}")[0].split("=")[1].replace("\"", "").trim();
        return question;
    }

    private Double score(TextSegment segment, String question, List<String> dogs) {
        double score = 0;

        String text = segment.text().toLowerCase();
        String q = question.toLowerCase();

        // 1️⃣ match com cães recomendados pelo grafo
        for (String dog : dogs) {
            if (text.contains(dog.toLowerCase())) {
                score += 3;
            }
        }

        // 2️⃣ keyword overlap
        for (String token : q.replaceAll("[^a-z ]","").split("\\s+")) {
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