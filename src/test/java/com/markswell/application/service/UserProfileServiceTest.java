package com.markswell.application.service;

import com.markswell.domain.service.GraphEntityExtractor;

import com.markswell.domain.model.UserProfile;
import com.markswell.infrastructure.persistence.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserProfileServiceTest {

    private UserProfileService service;

    @BeforeEach
    void setUp() {
        service = new UserProfileService();
        service.repo = new UserProfileRepository();
        service.extractor = new GraphEntityExtractor();
    }

    @Test
    void shouldFillProfileFromFreeText() {
        service.updateProfile("u1", "Quero um cão pequeno para apartamento");

        UserProfile profile = service.get("u1");
        assertEquals(Set.of("pequeno"), profile.getPortes());
        assertEquals(Set.of("apartamento"), profile.getAmbientes());
    }

    @Test
    void shouldAccumulatePreferencesAcrossMessages() {
        service.updateProfile("u2", "moro num apto");
        service.updateProfile("u2", "e prefiro cães grandes");

        UserProfile profile = service.get("u2");
        assertEquals(Set.of("grande"), profile.getPortes());
        assertEquals(Set.of("apartamento"), profile.getAmbientes());
    }

    @Test
    void shouldKeepSupportingJsonAndNormalizeValues() {
        service.updateProfile("u3", """
                {"porte": "Médio", "ambiente": "casa_com_quintal", "temperamento": "calmo"}
                """);

        UserProfile profile = service.get("u3");
        assertEquals(Set.of("medio"), profile.getPortes());
        assertEquals(Set.of("casa", "quintal"), profile.getAmbientes());
        assertEquals(Set.of("calmo"), profile.getTemperamentos());
    }

    @Test
    void shouldIgnoreMessagesWithoutPreferences() {
        service.updateProfile("u4", "qual o cachorro mais carinhoso?");
        service.updateProfile("u4", "{ json inválido");
        service.updateProfile("u4", null);

        UserProfile profile = service.get("u4");
        assertTrue(profile.getPortes().isEmpty());
        assertTrue(profile.getAmbientes().isEmpty());
    }
}
