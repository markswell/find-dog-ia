package com.markswell.domain.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphEntityExtractorTest {

    private final GraphEntityExtractor extractor = new GraphEntityExtractor();

    @Test
    void shouldExtractOnlyGraphEntitiesIgnoringStopwords() {
        var entities = extractor.extract("Quero um cão pequeno para apartamento, de preferência!");

        assertEquals(List.of("pequeno"), entities.portes());
        assertEquals(List.of("apartamento"), entities.ambientes());
    }

    @Test
    void shouldNormalizeAccentsPunctuationAndSynonyms() {
        var entities = extractor.extract("Um cachorro MÉDIO ou gigante? Moro numa chácara com jardim.");

        assertEquals(List.of("medio", "gigante"), entities.portes());
        assertEquals(List.of("sitio", "quintal"), entities.ambientes());
    }

    @Test
    void shouldReturnEmptyWhenQuestionHasNoGraphEntity() {
        var entities = extractor.extract("qual cachorro é mais carinhoso e a o e de um");

        assertTrue(entities.isEmpty());
    }

    @Test
    void shouldHandleNullAndProfileValues() {
        assertTrue(extractor.extract(null).isEmpty());
        assertEquals(List.of("pequeno"), extractor.portes(Set.of("Pequeno")));
        assertEquals(List.of("casa", "quintal"), extractor.ambientes(Set.of("casa_com_quintal")));
    }
}
