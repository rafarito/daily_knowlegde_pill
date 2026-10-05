package dev.rafael.dailyknowledge;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Monta as mensagens HTML do Telegram (descoberta diária e resumo semanal). */
public final class MessageFormatter {

    public static final int TELEGRAM_LIMIT = 4096;
    private static final int MIN_FIELD = 80;
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");

    /** Campos de texto longo que podem ser encurtados se a mensagem passar do limite. */
    enum Field { WHAT, CONTEXT, ORIGIN, WHY }

    private MessageFormatter() {
    }

    public static String daily(LocalDate date, Discovery d, Verification v) {
        Map<Field, String> texts = new EnumMap<>(Field.class);
        texts.put(Field.WHAT, d.whatItDoes());
        texts.put(Field.CONTEXT, d.contextAndNeed());
        if (d.origin() != null) {
            texts.put(Field.ORIGIN, d.origin());
        }
        texts.put(Field.WHY, d.whyInteresting());

        boolean withSources = true;
        boolean withAlternatives = true;
        String msg = renderDaily(date, d, v, texts, withSources, withAlternatives);
        for (int i = 0; msg.length() > TELEGRAM_LIMIT && i < 50; i++) {
            Field longest = longest(texts);
            if (longest == null) {
                break;
            }
            int excess = msg.length() - TELEGRAM_LIMIT;
            String t = texts.get(longest);
            int newLen = Math.max(MIN_FIELD, t.length() - excess - 10);
            texts.put(longest, t.substring(0, newLen).strip() + "…");
            msg = renderDaily(date, d, v, texts, withSources, withAlternatives);
        }
        if (msg.length() > TELEGRAM_LIMIT) {
            withSources = false;
            msg = renderDaily(date, d, v, texts, withSources, withAlternatives);
        }
        if (msg.length() > TELEGRAM_LIMIT) {
            withAlternatives = false;
            msg = renderDaily(date, d, v, texts, withSources, withAlternatives);
        }
        return msg;
    }

    private static Field longest(Map<Field, String> texts) {
        Field best = null;
        int bestLen = MIN_FIELD + 1;
        for (Map.Entry<Field, String> e : texts.entrySet()) {
            if (e.getValue().length() > bestLen) {
                best = e.getKey();
                bestLen = e.getValue().length();
            }
        }
        return best;
    }

    private static String renderDaily(LocalDate date, Discovery d, Verification v, Map<Field, String> texts,
                                      boolean withSources, boolean withAlternatives) {
        String url = v.canonicalUrl() != null ? v.canonicalUrl() : d.url();
        String license = v.license() != null ? v.license() : d.license();

        StringBuilder sb = new StringBuilder();
        sb.append("🧭 <b>Descoberta do dia</b> · ")
                .append(esc(PromptBuilder.weekdayName(date))).append(", ").append(date.format(DAY_MONTH))
                .append(" · ").append(esc(Themes.forDay(date.getDayOfWeek()))).append('\n');
        sb.append("<a href=\"").append(attr(url)).append("\"><b>").append(esc(d.name())).append("</b></a> — ")
                .append(esc(d.tagline())).append('\n');

        List<String> meta = new ArrayList<>(List.of(d.category(), d.language(), license));
        if (v.stars() != null) {
            meta.add("⭐ " + formatStars(v.stars()));
        }
        if (v.pushedAt() != null && v.pushedAt().length() >= 10) {
            meta.add("último push " + v.pushedAt().substring(0, 10));
        }
        sb.append("<i>").append(esc(String.join(" · ", meta))).append("</i>\n");
        if (!d.isThemeMatch()) {
            sb.append("⚠️ fora do foco do dia\n");
        }

        section(sb, "O que faz", texts.get(Field.WHAT));
        section(sb, "Contexto e necessidade", texts.get(Field.CONTEXT));
        if (texts.containsKey(Field.ORIGIN)) {
            section(sb, "Origem", texts.get(Field.ORIGIN));
        }
        section(sb, "Por que vale a pena", texts.get(Field.WHY));
        sb.append("\n<b>Teste em 5 min</b>\n<code>").append(esc(d.tryIt())).append("</code>\n");

        if (withAlternatives && !d.alternatives().isEmpty()) {
            sb.append("\n<b>Alternativas:</b> ").append(esc(String.join(", ", d.alternatives()))).append('\n');
        }
        if (d.website() != null) {
            sb.append("🌐 <a href=\"").append(attr(d.website())).append("\">site oficial</a>\n");
        }
        if (withSources && !d.sources().isEmpty()) {
            sb.append("\n<b>Fontes:</b> ").append(sourceLinks(d.sources()));
        }
        return sb.toString().strip();
    }

    private static void section(StringBuilder sb, String title, String body) {
        sb.append("\n<b>").append(esc(title)).append("</b>\n").append(esc(body)).append('\n');
    }

    static String sourceLinks(List<String> sources) {
        Map<String, Integer> hostCount = new HashMap<>();
        List<String> links = new ArrayList<>();
        for (String s : sources.stream().distinct().toList()) {
            String host = UrlNormalizer.host(s);
            int n = hostCount.merge(host, 1, Integer::sum);
            String label = n == 1 ? host : host + " (" + n + ")";
            links.add("<a href=\"" + attr(s) + "\">" + esc(label) + "</a>");
        }
        return String.join(" · ", links);
    }

    public static String weekly(LocalDate today, List<SeenEntry> week) {
        StringBuilder sb = new StringBuilder();
        sb.append("📚 <b>Resumo da semana</b> (").append(today.minusDays(6).format(DAY_MONTH))
                .append(" – ").append(today.format(DAY_MONTH)).append(")\n");
        if (week.isEmpty()) {
            sb.append("Nenhum projeto enviado nesta semana.");
            return sb.toString();
        }
        for (SeenEntry e : week) {
            sb.append("• <a href=\"").append(attr(e.url())).append("\">").append(esc(e.name())).append("</a>");
            if (e.tagline() != null) {
                sb.append(" — ").append(esc(e.tagline()));
            }
            sb.append('\n');
        }
        return sb.toString().strip();
    }

    static String formatStars(int stars) {
        if (stars < 1000) {
            return Integer.toString(stars);
        }
        String s = String.format(Locale.ROOT, "%.1f", stars / 1000.0);
        if (s.endsWith(".0")) {
            s = s.substring(0, s.length() - 2);
        }
        return s + "k";
    }

    static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String attr(String s) {
        return esc(s).replace("\"", "&quot;");
    }
}
