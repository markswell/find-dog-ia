package com.markswell.interfaces.service;

import com.markswell.domain.model.DogGraphHit;
import com.markswell.domain.model.UserProfile;
import com.markswell.infraestructure.persistence.GraphRepository;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;

@ApplicationScoped
public class PersonalizedGraphRagService {

    @Inject
    GraphRepository graph;

    @Inject
    UserProfileService profileService;

    @Inject
    GraphEntityExtractor extractor;

    static final int LIMIT = 10;

    /**
     * Graph retrieval:
     *  1. entidades da pergunta (peso 3) e do perfil do usuário (peso 1), normalizadas ao vocabulário do grafo;
     *  2. travessia Dog -[:HAS_SIZE]-> Size e Dog -[:GOOD_FOR]-> Environment, agregada por cão (1 linha por cão);
     *  3. match por termo do grafo: igualdade ou token delimitado por '_' (ex.: 'quintal' casa com
     *     'casa_com_quintal', mas 'a' não casa com 'grande');
     *  4. retorna os fatos (porte, ambientes, temperamento) para virar contexto do LLM.
     */
    static final String CYPHER = """
            MATCH (d:Dog)
            OPTIONAL MATCH (d)-[:HAS_SIZE]->(s:Size)
            OPTIONAL MATCH (d)-[:GOOD_FOR]->(e:Environment)
            WITH d,
                 [x IN collect(DISTINCT s.name) WHERE x IS NOT NULL] AS portes,
                 [x IN collect(DISTINCT e.name) WHERE x IS NOT NULL] AS ambientes
            WITH d, portes, ambientes,
                 size([p IN portes    WHERE any(t IN $qPortes    WHERE t IN split(p, '_'))]) AS qSize,
                 size([a IN ambientes WHERE any(t IN $qAmbientes WHERE t IN split(a, '_'))]) AS qEnv,
                 size([p IN portes    WHERE any(t IN $pPortes    WHERE t IN split(p, '_'))]) AS pSize,
                 size([a IN ambientes WHERE any(t IN $pAmbientes WHERE t IN split(a, '_'))]) AS pEnv
            WITH d, portes, ambientes,
                 CASE WHEN qSize > 0 THEN 3 ELSE 0 END
               + CASE WHEN qEnv  > 0 THEN 3 ELSE 0 END
               + CASE WHEN pSize > 0 THEN 1 ELSE 0 END
               + CASE WHEN pEnv  > 0 THEN 1 ELSE 0 END AS score
            WHERE score > 0
            RETURN d.nome AS nome, portes, ambientes, d.temperamento AS temperamento, score
            ORDER BY score DESC, nome ASC
            LIMIT $limit
            """;

    @WithSpan("rag.graphquery")
    public List<DogGraphHit> search(String userId, String question) {

        UserProfile profile = profileService.get(userId);

        var fromQuestion = extractor.extract(question);
        var profilePortes = extractor.portes(profile.getPortes());
        var profileAmbientes = extractor.ambientes(profile.getAmbientes());

        if (fromQuestion.isEmpty() && profilePortes.isEmpty() && profileAmbientes.isEmpty()) {
            return List.of();
        }

        Map<String, Object> params = Map.of(
                "qPortes", fromQuestion.portes(),
                "qAmbientes", fromQuestion.ambientes(),
                "pPortes", profilePortes,
                "pAmbientes", profileAmbientes,
                "limit", LIMIT
        );

        return graph.query(CYPHER, params)
                .stream()
                .map(PersonalizedGraphRagService::toHit)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static DogGraphHit toHit(Map<String, Object> r) {
        return new DogGraphHit(
                (String) r.get("nome"),
                ((List<Object>) r.get("portes")).stream().map(Object::toString).toList(),
                ((List<Object>) r.get("ambientes")).stream().map(Object::toString).toList(),
                (String) r.get("temperamento"),
                ((Number) r.get("score")).longValue()
        );
    }
}