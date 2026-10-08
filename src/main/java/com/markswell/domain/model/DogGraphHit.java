package com.markswell.domain.model;

import java.util.List;

/**
 * Resultado da busca no grafo: o cão encontrado e os fatos (relações) que justificam a recomendação.
 */
public record DogGraphHit(
        String nome,
        List<String> portes,
        List<String> ambientes,
        String temperamento,
        long score
) {

    public String toContext() {
        return "- %s | porte: %s | ambientes: %s | temperamento: %s".formatted(
                nome,
                String.join(", ", portes),
                String.join(", ", ambientes),
                temperamento == null ? "n/d" : temperamento
        );
    }
}
