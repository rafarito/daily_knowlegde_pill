package dev.rafael.dailyknowledge;

import java.util.List;

/** Contrato JSON devolvido pelo agente (ver {@code prompt.md} e {@code discovery.schema.json}). */
public record Discovery(
        String name,
        String url,
        String website,
        String category,
        Boolean themeMatch,
        String license,
        String language,
        String tagline,
        String whatItDoes,
        String contextAndNeed,
        String origin,
        String whyInteresting,
        String tryIt,
        List<String> alternatives,
        List<String> sources) {

    public Discovery {
        alternatives = alternatives == null ? List.of() : List.copyOf(alternatives);
        sources = sources == null ? List.of() : List.copyOf(sources);
        website = blankToNull(website);
        origin = blankToNull(origin);
    }

    /** Lança {@link IllegalArgumentException} se faltar algum campo obrigatório. */
    public void validate() {
        require(name, "name");
        require(url, "url");
        require(category, "category");
        require(license, "license");
        require(language, "language");
        require(tagline, "tagline");
        require(whatItDoes, "what_it_does");
        require(contextAndNeed, "context_and_need");
        require(whyInteresting, "why_interesting");
        require(tryIt, "try_it");
        if (themeMatch == null) {
            throw new IllegalArgumentException("campo obrigatório ausente: theme_match");
        }
    }

    public boolean isThemeMatch() {
        return Boolean.TRUE.equals(themeMatch);
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("campo obrigatório ausente: " + field);
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() || s.equalsIgnoreCase("null") ? null : s;
    }
}
