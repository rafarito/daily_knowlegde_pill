package dev.rafael.dailyknowledge;

/**
 * Resultado da verificação de uma URL.
 *
 * @param canonicalUrl URL a usar para deduplicar/exibir (html_url do GitHub, quando houver)
 * @param license      spdx_id do GitHub, ou null se indisponível/NOASSERTION
 */
public record Verification(
        boolean found,
        String canonicalUrl,
        Integer stars,
        String license,
        String pushedAt,
        String failureReason) {

    public static Verification notFound(String reason) {
        return new Verification(false, null, null, null, null, reason);
    }

    public static Verification reachable(String url) {
        return new Verification(true, url, null, null, null, null);
    }
}
