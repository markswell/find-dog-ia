package com.markswell.interfaces.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.markswell.domain.model.UserProfile;
import com.markswell.infraestructure.persistence.UserProfileRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;

@ApplicationScoped
public class UserProfileService {

    @Inject
    UserProfileRepository repo;

    ObjectMapper mapper = new ObjectMapper();

    public void updateProfile(String userId, String json) {
        try {

            Map<String, String> data = mapper.readValue(json, Map.class);

            UserProfile profile = repo.get(userId);

            if (data.containsKey("porte"))
                profile.getPortes().add(data.get("porte"));

            if (data.containsKey("ambiente"))
                profile.getAmbientes().add(data.get("ambiente"));

            if (data.containsKey("temperamento"))
                profile.getTemperamentos().add(data.get("temperamento"));

            repo.save(profile);

        } catch (Exception ignored) {}
    }

    public UserProfile get(String userId) {
        return repo.get(userId);
    }
}
