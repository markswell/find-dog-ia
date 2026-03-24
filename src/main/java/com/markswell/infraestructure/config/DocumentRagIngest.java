package com.markswell.infraestructure.config;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;

import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;

@ApplicationScoped
public class DocumentRagIngest {

    @Inject
    EmbeddingStore<TextSegment> store;

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    Driver driver;

    public void onStart(@Observes StartupEvent event) {

        List<Document> documents = FileSystemDocumentLoader.loadDocuments(Paths.get("src/main/resources/rag"))
                .stream()
                .filter(d -> d.metadata().getString("file_name").endsWith(".md") &&
                !d.metadata().getString("file_name").equals("racas.md"))
                .toList();

        documents.forEach(d -> d.metadata().put("tipo", "raca_canina"));

        DocumentSplitter splitter = DocumentSplitters.recursive(500, 100);

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(splitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(store)
                .build();
        try(ExecutorService executorService = newVirtualThreadPerTaskExecutor()) {
            documents.forEach(d -> executorService.execute(() -> {
                try {
                    String hash = hash(d.text());
                    if (documentExists(hash)) {
                        System.out.println("Documento já ingerido".concat(d.metadata().getString("file_name")));
                        return;
                    }
                    d.metadata().put("hash", hash);
                } catch (Exception e) {
                    return;
                }
                System.out.println("ingerindo documento. ".concat(d.metadata().getString("file_name")));
                ingestor.ingest(d);
            }));
        }

    }

    private String hash(String content) throws Exception {

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] encoded = digest.digest(content.getBytes());

        return java.util.HexFormat.of().formatHex(encoded);
    }

    public boolean documentExists(String hash) {

        try (Session session = driver.session()) {

            var result = session.run(
                    "MATCH (d:Document {hash: $hash}) RETURN d LIMIT 1",
                    Map.of("hash", hash)
            );

            return result.hasNext();
        }
    }
}
