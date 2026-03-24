package com.markswell.infraestructure.persistence;

import com.markswell.domain.model.UserProfile;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class UserProfileRepository {

    private final ConcurrentHashMap<String, UserProfile> db = new ConcurrentHashMap<>();

    public UserProfile get(String userId) {
        return db.computeIfAbsent(userId, id -> {
            UserProfile p = new UserProfile();
            p.setUserId(id);
            return p;
        });
    }

    public void save(UserProfile profile) {
        db.put(profile.getUserId(), profile);
    }
}
