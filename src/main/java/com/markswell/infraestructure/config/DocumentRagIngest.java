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
import java.util.Arrays;
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
            prepareGraphSchema(session);
            session.run("CALL db.awaitIndexes()");
            documents.forEach(d -> executorService.execute(() -> {
                String fileName = d.metadata().getString("file_name");

                try {
                    String hash = hash(d.text());

                    // grafo é sempre sincronizado (MERGE idempotente) para manter o modelo atualizado
                    createDogNode(d);

                    if (documentExists(hash)) {
                        System.out.println("Documento já ingerido".concat(fileName));
                    } else {
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

    /**
     * Garante unicidade dos nós do grafo. Sem constraint, MERGE concorrente (virtual threads)
     * cria nós duplicados (ex.: vários :Size {name:'medio'}) e o grafo deixa de ser conectado.
     * Os nós :Size/:Environment são derivados dos .md, então são recriados a cada startup
     * por createDogNode (isso também remove valores antigos concatenados como "casa|apartamento").
     */
    private void prepareGraphSchema(Session session) {
        session.run("DROP INDEX dog_name IF EXISTS");
        session.run("DROP INDEX size_name IF EXISTS");
        session.run("DROP INDEX env_name IF EXISTS");

        session.run("""
                MATCH (n)
                WHERE n:Size OR n:Environment
                DETACH DELETE n
                """);
        session.run("""
                MATCH (d:Dog)
                WITH d.nome AS nome, collect(d) AS dogs
                WHERE size(dogs) > 1
                UNWIND tail(dogs) AS duplicated
                DETACH DELETE duplicated
                """);

        session.run("""
                CREATE CONSTRAINT dog_nome_unique IF NOT EXISTS
                FOR (d:Dog) REQUIRE d.nome IS UNIQUE
                """);
        session.run("""
                CREATE CONSTRAINT size_name_unique IF NOT EXISTS
                FOR (s:Size) REQUIRE s.name IS UNIQUE
                """);
        session.run("""
                CREATE CONSTRAINT env_name_unique IF NOT EXISTS
                FOR (e:Environment) REQUIRE e.name IS UNIQUE
                """);
    }

    /** "casa_com_quintal|apartamento| casa" -> [casa_com_quintal, apartamento, casa] */
    static List<String> splitValues(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("\\|"))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(v -> !v.isEmpty())
                .distinct()
                .toList();
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
            params.put("portes", splitValues(d.metadata().getString("porte")));
            params.put("ambientes", splitValues(d.metadata().getString("ambiente_ideal")));
            params.put("temperamento", d.metadata().getString("temperamento"));
            params.put("descricao", d.text());

            session.executeWriteWithoutResult(tx -> tx.run("""
                    MERGE (dog:Dog {nome:$nome})
                    SET dog.descricao = $descricao,
                        dog.temperamento = $temperamento

                    WITH dog
                    OPTIONAL MATCH (dog)-[r:HAS_SIZE|GOOD_FOR]->()
                    DELETE r

                    WITH DISTINCT dog
                    FOREACH (porte IN $portes |
                        MERGE (size:Size {name:porte})
                        MERGE (dog)-[:HAS_SIZE]->(size)
                    )
                    FOREACH (ambiente IN $ambientes |
                        MERGE (env:Environment {name:ambiente})
                        MERGE (dog)-[:GOOD_FOR]->(env)
                    )
                    """, params));
        }
    }

}
