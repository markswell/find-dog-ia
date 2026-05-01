package com.markswell.interfaces.service;

import com.markswell.domain.model.UserProfile;
import com.markswell.infraestructure.persistence.GraphRepository;
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

    public List<String> search(String userId, List<String> question) {

        UserProfile profile = profileService.get(userId);

        String cypher = """
                        MATCH (d:Dog)
                             OPTIONAL MATCH (d)-[:HAS_SIZE]->(s:Size)
                             OPTIONAL MATCH (d)-[:GOOD_FOR]->(e:Environment)
                        
                             WITH d, s, e,
                        
                             CASE
                                 WHEN size($portes) = 0 THEN 0
                                 WHEN ANY(x IN $portes WHERE toLower(s.name) CONTAINS toLower(x)) THEN 1
                                 ELSE 0
                             END AS sizeScore,
                        
                             CASE
                                 WHEN size($ambientes) = 0 THEN 0
                                 WHEN ANY(x IN $ambientes WHERE toLower(e.name) CONTAINS toLower(x)) THEN 1
                                 ELSE 0
                             END AS envScore
                        
                             WITH d,
                             sizeScore,
                             envScore,
                             (sizeScore + envScore) AS totalScore
                        
                             WHERE
                                 (size($portes) > 0 OR size($ambientes) > 0)
                        
                             AND
                                 totalScore > 0
                        
                             RETURN
                                 d.nome AS nome
                        
                             ORDER BY
                                 totalScore DESC,
                                 sizeScore DESC,
                                 envScore DESC,
                                 nome ASC
                        
                             LIMIT 10
                        """;

        Map<String,Object> params = Map.of(
                "portes", question,
                "ambientes", question
        );

        return graph.query(cypher, params)
                .stream()
                .map(r -> r.get("nome").toString())
                .toList();
    }
}