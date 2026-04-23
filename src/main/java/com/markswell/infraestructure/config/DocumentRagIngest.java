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
import java.util.HashMap;
import java.util.HexFormat;
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

        documents.forEach(d -> {
            String text = d.text();
            parseFrontMatter(text).forEach((k, v) -> d.metadata().put(k, v));
        });

        DocumentSplitter splitter = DocumentSplitters.recursive(500, 100);

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(splitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(store)
                .build();

        try(ExecutorService executorService = newVirtualThreadPerTaskExecutor();
            Session session = driver.session()) {
            session.run("""
                CREATE VECTOR INDEX vector IF NOT EXISTS
                FOR (d:Document) ON (d.embedding)
                OPTIONS {
                  indexConfig: {
                    `vector.dimensions`: 768,
                    `vector.similarity_function`: 'cosine'
                  }
                }
                """);
            session.run("""
                CREATE INDEX dog_name IF NOT EXISTS
                FOR (d:Dog) ON (d.nome)
                """);
            session.run("""
                CREATE INDEX size_name IF NOT EXISTS
                FOR (s:Size) ON (s.name)
                """);
            session.run("""
                CREATE INDEX env_name IF NOT EXISTS
                FOR (e:Environment) ON (e.name)
                """);
            session.run("CALL db.awaitIndexes()");
            documents.forEach(d -> executorService.execute(() -> {
                String fileName = d.metadata().getString("file_name");

                try {
                    String hash = hash(d.text());

                    if (documentExists(hash)) {
                        System.out.println("Documento já ingerido".concat(fileName));
                    } else {
                        createDogNode(d);
                        d.metadata().put("hash", hash);
                        System.out.println("ingerindo documento. ".concat(fileName));
                        ingestor.ingest(d);

                    }
                } catch (Exception e) {
                    System.out.println("Erro ao ingerir arquivo: %s".formatted(fileName));
                    System.out.println(e.getMessage());
                }
            }));
        }

    }

    private String hash(String content) throws Exception {

        var digest = MessageDigest.getInstance("SHA-256");
        byte[] encoded = digest.digest(content.getBytes());

        return HexFormat.of().formatHex(encoded);
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

    public static Map<String,String> parseFrontMatter(String text) {

        Map<String,String> map = new HashMap<>();

        if (!text.startsWith("---"))
            return map;

        int end = text.indexOf("\n---", 3);
        if (end == -1)
            return map;

        String yaml = text.substring(3, end);

        for (String line : yaml.split("\n")) {

            line = line.trim();

            if (!line.contains(":"))
                continue;

            String[] parts = line.split(":",2);

            map.put(parts[0].trim(), parts[1].trim());
        }

        return map;
    }

    private void createDogNode(Document d) {

        try( Session session = driver.session()) {
            Map<String, Object> params = new HashMap<>();
            String nome = d.metadata().getString("nome");
            params.put("nome", nome);
            params.put("id", nome.concat("_dog"));
            params.put("porte", d.metadata().getString("porte"));
            params.put("ambiente", d.metadata().getString("ambiente_ideal"));
            params.put("temperamento", d.metadata().getString("temperamento"));
            params.put("descricao", d.text());

            session.run("""
                    MERGE (dog:Dog {nome:$nome})
                    SET dog.descricao = $descricao
                    
                    MERGE (size:Size {name:$porte})
                    MERGE (env:Environment {name:$ambiente})
                    
                    MERGE (dog)-[:HAS_SIZE]->(size)
                    MERGE (dog)-[:GOOD_FOR]->(env)
                    """, params);
        }
    }

}
