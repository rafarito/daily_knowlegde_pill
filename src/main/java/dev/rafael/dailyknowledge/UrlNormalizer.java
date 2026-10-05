package dev.rafael.dailyknowledge;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;

/** Normalização de URLs para deduplicação. */
public final class UrlNormalizer {

    private UrlNormalizer() {
    }

    /** minúsculas, https, sem www., sem .git, sem / final, sem query/fragmento. */
    public static String normalize(String url) {
        if (url == null) {
            return "";
        }
        String u = url.strip().toLowerCase(Locale.ROOT);
        int cut = indexOfAny(u, '?', '#');
        if (cut >= 0) {
            u = u.substring(0, cut);
        }
        if (u.startsWith("http://")) {
            u = "https://" + u.substring("http://".length());
        } else if (!u.startsWith("https://")) {
            u = "https://" + u;
        }
        if (u.startsWith("https://www.")) {
            u = "https://" + u.substring("https://www.".length());
        }
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        if (u.endsWith(".git")) {
            u = u.substring(0, u.length() - 4);
        }
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    /** Extrai "owner/repo" de uma URL de repositório do GitHub, se for uma. */
    public static Optional<String> githubRepo(String url) {
        String n = normalize(url);
        String prefix = "https://github.com/";
        if (!n.startsWith(prefix)) {
            return Optional.empty();
        }
        String[] parts = n.substring(prefix.length()).split("/");
        if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
            return Optional.empty();
        }
        return Optional.of(parts[0] + "/" + parts[1]);
    }

    /** Host sem "www." — usado para exibir as fontes de forma curta. */
    public static String host(String url) {
        try {
            String h = URI.create(url.strip()).getHost();
            if (h == null) {
                return url;
            }
            return h.startsWith("www.") ? h.substring(4) : h;
        } catch (IllegalArgumentException e) {
            return url;
        }
    }

    private static int indexOfAny(String s, char a, char b) {
        int ia = s.indexOf(a);
        int ib = s.indexOf(b);
        if (ia < 0) {
            return ib;
        }
        if (ib < 0) {
            return ia;
        }
        return Math.min(ia, ib);
    }
}
