package dev.rafael.dailyknowledge;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Preenche os placeholders do {@code prompt.md} sem alterar o restante do texto. */
public final class PromptBuilder {

    static final Locale PT_BR = Locale.of("pt", "BR");

    private final String template;

    public PromptBuilder(String template) {
        this.template = template;
    }

    public static PromptBuilder fromResource() {
        return new PromptBuilder(readResource("/prompt.md"));
    }

    public String build(LocalDate date, List<SeenEntry> seen) {
        return template
                .replace("{{DATE}}", date.format(DateTimeFormatter.ISO_LOCAL_DATE))
                .replace("{{WEEKDAY}}", weekdayName(date))
                .replace("{{THEME}}", Themes.forDay(date.getDayOfWeek()))
                .replace("{{SEEN_LIST}}", seenList(seen));
    }

    static String weekdayName(LocalDate date) {
        return date.getDayOfWeek().getDisplayName(TextStyle.FULL, PT_BR);
    }

    static String seenList(List<SeenEntry> seen) {
        if (seen.isEmpty()) {
            return "(nenhum)";
        }
        return seen.stream()
                .map(e -> "- " + e.name() + " — " + e.url())
                .collect(Collectors.joining("\n"));
    }

    static String readResource(String path) {
        try (InputStream in = PromptBuilder.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Recurso não encontrado: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
