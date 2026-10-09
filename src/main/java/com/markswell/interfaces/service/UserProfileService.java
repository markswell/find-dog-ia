package com.markswell.interfaces.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.markswell.domain.model.UserProfile;
import com.markswell.infraestructure.persistence.UserProfileRepository;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class UserProfileService {

    private static final Logger LOG = LoggerFactory.getLogger(UserProfileService.class);

    @Inject
    UserProfileRepository repo;

    @Inject
    GraphEntityExtractor extractor;

    ObjectMapper mapper = new ObjectMapper();

    /**
     * Atualiza o perfil do usuário a partir da mensagem recebida.
     * Aceita JSON ({"porte": "...", "ambiente": "...", "temperamento": "..."}) ou texto livre
     * (ex.: "quero um cão pequeno para apartamento"), do qual as preferências são extraídas
     * e normalizadas para o vocabulário do grafo.
     */
    @WithSpan("user.updateUser")
    public void updateProfile(String userId, String message) {
        if (message == null || message.isBlank()) {
            return;
        }

        UserProfile profile = repo.get(userId);

        Map<String, String> data = parseJson(message);

        String freeText = data == null
                ? message
                : String.join(" ", data.getOrDefault("porte", ""), data.getOrDefault("ambiente", ""));

        var entities = extractor.extract(freeText);

        addAll(profile, entities.portes(), entities.ambientes());

        if (data != null && data.get("temperamento") != null && !data.get("temperamento").isBlank()) {
            synchronized (profile) {
                profile.getTemperamentos().add(data.get("temperamento").trim());
            }
        }

        repo.save(profile);
        LOG.debug("Perfil {} atualizado: portes={} ambientes={}", userId, profile.getPortes(), profile.getAmbientes());
    }

    private static void addAll(UserProfile profile, List<String> portes, List<String> ambientes) {
        synchronized (profile) {
            profile.getPortes().addAll(portes);
            profile.getAmbientes().addAll(ambientes);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseJson(String message) {
        String trimmed = message.trim();
        if (!trimmed.startsWith("{")) {
            return null;
        }
        try {
            Map<String, Object> raw = mapper.readValue(trimmed, Map.class);
            Map<String, String> data = new HashMap<>();
            raw.forEach((k, v) -> {
                if (v != null) {
                    data.put(k, v.toString());
                }
            });
            return data;
        } catch (Exception e) {
            // não é JSON válido: tratado como texto livre
            return null;
        }
    }

    public UserProfile get(String userId) {
        return repo.get(userId);
    }
}
