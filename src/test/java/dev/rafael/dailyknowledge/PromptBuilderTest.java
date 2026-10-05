package dev.rafael.dailyknowledge;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptBuilderTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);

    @Test
    void fillsAllPlaceholders() {
        String template = PromptBuilder.readResource("/prompt.md");
        List<SeenEntry> seen = List.of(
                new SeenEntry("Pandoc", "https://github.com/jgm/pandoc", null, null, "seed", null, null),
                new SeenEntry("Handy", "https://github.com/cjpais/Handy", null, null, "seed", null, null));

        String prompt = PromptBuilder.fromResource().build(MONDAY, seen);

        assertFalse(prompt.contains("{{"), "nenhum placeholder deve sobrar");
        assertTrue(prompt.contains("Data: 2026-10-05 (segunda-feira)"));
        assertTrue(prompt.contains("Foco sugerido do dia: Bibliotecas e SDKs (qualquer linguagem)"));
        assertTrue(prompt.contains("- Pandoc — https://github.com/jgm/pandoc\n- Handy — https://github.com/cjpais/Handy"));

        // Fora dos placeholders, o texto deve ser idêntico ao recurso.
        String rebuilt = prompt
                .replace("2026-10-05", "{{DATE}}")
                .replace("(segunda-feira)", "({{WEEKDAY}})")
                .replace("Bibliotecas e SDKs (qualquer linguagem)", "{{THEME}}")
                .replace("- Pandoc — https://github.com/jgm/pandoc\n- Handy — https://github.com/cjpais/Handy", "{{SEEN_LIST}}");
        assertEquals(template, rebuilt);
    }

    @Test
    void emptySeenList() {
        assertEquals("(nenhum)", PromptBuilder.seenList(List.of()));
    }

    @Test
    void everyWeekdayHasATheme() {
        for (DayOfWeek d : DayOfWeek.values()) {
            assertNotNull(Themes.forDay(d), d.toString());
        }
        assertEquals("Livre / curiosidade / clássicos esquecidos", Themes.forDay(DayOfWeek.SUNDAY));
    }
}
