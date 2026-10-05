package dev.rafael.dailyknowledge;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Entrada do {@code seen.json}.
 *
 * @param source "seed", "daily" (enviado) ou "rejected" (candidato descartado, nunca mais deve voltar)
 * @param reason motivo da rejeição (só quando source = rejected)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SeenEntry(
        String name,
        String url,
        String tagline,
        String sentAt,
        String source,
        Boolean themeMatch,
        String reason) {

    public static final String SEED = "seed";
    public static final String DAILY = "daily";
    public static final String REJECTED = "rejected";

    public static SeenEntry daily(String name, String url, String tagline, String sentAt, boolean themeMatch) {
        return new SeenEntry(name, url, tagline, sentAt, DAILY, themeMatch, null);
    }

    public static SeenEntry rejected(String name, String url, String at, String reason) {
        return new SeenEntry(name, url, null, at, REJECTED, null, reason);
    }
}
