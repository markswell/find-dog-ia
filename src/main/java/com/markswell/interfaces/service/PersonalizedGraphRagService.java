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

    public List<String> search(String userId, String question) {

        UserProfile profile = profileService.get(userId);

        StringBuilder cypher = new StringBuilder("""
            MATCH (d:Dog)
            WHERE 1=1
        """);

        Map<String, Object> params = new java.util.HashMap<>();

        params.put("question", question);

        if (!profile.getPortes().isEmpty()) {
            cypher.append(" AND d.porte IN $portes");
            params.put("portes", profile.getPortes());
        }

        if (!profile.getAmbientes().isEmpty()) {
            cypher.append(" AND d.ambiente IN $ambientes");
            params.put("ambientes", profile.getAmbientes());
        }

        cypher.append(" AND toLower(d.descricao) CONTAINS toLower($question)");
        cypher.append(" RETURN d.nome AS nome");

        List<Map<String, Object>> result = graph.query(cypher.toString(), params);

        return result.stream()
                .map(r -> r.get("nome").toString())
                .toList();
    }
}