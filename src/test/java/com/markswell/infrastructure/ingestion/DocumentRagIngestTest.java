package com.markswell.infrastructure.ingestion;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentRagIngestTest {

    @Test
    void shouldSplitMultiValuedFrontMatterIntoGraphNodes() {
        assertEquals(List.of("casa_com_quintal", "apartamento_com_passeios"),
                DocumentRagIngest.splitValues("casa_com_quintal|apartamento_com_passeios"));
        assertEquals(List.of("apartamento", "casa"),
                DocumentRagIngest.splitValues("apartamento| casa"));
        assertEquals(List.of("medio"), DocumentRagIngest.splitValues("medio"));
    }

    @Test
    void shouldReturnEmptyForMissingValues() {
        assertEquals(List.of(), DocumentRagIngest.splitValues(null));
        assertEquals(List.of(), DocumentRagIngest.splitValues("  "));
    }
}
