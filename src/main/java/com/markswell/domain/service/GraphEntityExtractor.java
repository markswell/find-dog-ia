package com.markswell.domain.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.text.Normalizer;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Extrai da pergunta (ou do perfil) as entidades que existem no grafo, já normalizadas
 * para o vocabulário usado na ingestão (nós :Size e :Environment).
 * Palavras fora do dicionário (stopwords, pontuação etc.) são descartadas.
 */
@ApplicationScoped
public class GraphEntityExtractor {

    private static final Map<String, String> PORTES = Map.ofEntries(
            Map.entry("pequeno", "pequeno"),
            Map.entry("pequena", "pequeno"),
            Map.entry("pequenos", "pequeno"),
            Map.entry("pequenas", "pequeno"),
            Map.entry("pequenino", "pequeno"),
            Map.entry("mini", "pequeno"),
            Map.entry("miniatura", "pequeno"),
            Map.entry("toy", "pequeno"),
            Map.entry("medio", "medio"),
            Map.entry("medios", "medio"),
            Map.entry("grande", "grande"),
            Map.entry("grandes", "grande"),
            Map.entry("gigante", "gigante"),
            Map.entry("gigantes", "gigante"),
            Map.entry("enorme", "gigante")
    );

    private static final Map<String, String> AMBIENTES = Map.ofEntries(
            Map.entry("apartamento", "apartamento"),
            Map.entry("apartamentos", "apartamento"),
            Map.entry("apto", "apartamento"),
            Map.entry("ape", "apartamento"),
            Map.entry("kitnet", "apartamento"),
            Map.entry("kitinete", "apartamento"),
            Map.entry("flat", "apartamento"),
            Map.entry("casa", "casa"),
            Map.entry("casas", "casa"),
            Map.entry("quintal", "quintal"),
            Map.entry("quintais", "quintal"),
            Map.entry("jardim", "quintal"),
            Map.entry("sitio", "sitio"),
            Map.entry("sitios", "sitio"),
            Map.entry("chacara", "sitio"),
            Map.entry("fazenda", "sitio"),
            Map.entry("rural", "sitio")
    );

    public record Entities(List<String> portes, List<String> ambientes) {
        public boolean isEmpty() {
            return portes.isEmpty() && ambientes.isEmpty();
        }
    }

    public Entities extract(String text) {
        List<String> tokens = tokenize(text);
        return new Entities(map(tokens, PORTES), map(tokens, AMBIENTES));
    }

    public List<String> portes(Collection<String> values) {
        return map(tokenize(values), PORTES);
    }

    public List<String> ambientes(Collection<String> values) {
        return map(tokenize(values), AMBIENTES);
    }

    /** minúsculas, sem acento, sem pontuação; '_' e '|' viram separadores. */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String noAccents = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return noAccents.toLowerCase()
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    public static List<String> tokenize(String text) {
        String normalized = normalize(text);
        if (normalized.isEmpty()) {
            return List.of();
        }
        return List.of(normalized.split("\\s+"));
    }

    private static List<String> tokenize(Collection<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .flatMap(v -> tokenize(v).stream())
                .toList();
    }

    private static List<String> map(List<String> tokens, Map<String, String> dictionary) {
        Set<String> result = tokens.stream()
                .map(dictionary::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return List.copyOf(result);
    }
}
