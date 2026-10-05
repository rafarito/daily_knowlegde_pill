package dev.rafael.dailyknowledge;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageFormatterTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);

    @Test
    void fullMessage() {
        Verification v = new Verification(true, "https://github.com/Owner/Tool", 12345, "Apache-2.0",
                "2026-10-01T10:00:00Z", null);
        String msg = MessageFormatter.daily(MONDAY, Fixtures.discovery("Tool", "https://github.com/owner/tool"), v);

        assertTrue(msg.startsWith("🧭 <b>Descoberta do dia</b> · segunda-feira, 05/10 · Bibliotecas e SDKs (qualquer linguagem)"));
        assertTrue(msg.contains("<a href=\"https://github.com/Owner/Tool\"><b>Tool</b></a> — Uma ferramenta &lt;útil&gt; &amp; rápida"));
        assertTrue(msg.contains("<i>cli · Rust · Apache-2.0 · ⭐ 12.3k · último push 2026-10-01</i>"),
                "licença do GitHub tem prioridade sobre a do modelo");
        assertFalse(msg.contains("fora do foco"));
        assertTrue(msg.contains("<b>Origem</b>"));
        assertTrue(msg.contains("<code>cargo install tool</code>"));
        assertTrue(msg.contains("<b>Alternativas:</b> Alt1, Alt2"));
        assertTrue(msg.contains("🌐 <a href=\"https://example.org\">site oficial</a>"));
        assertTrue(msg.contains("<b>Fontes:</b> <a href=\"https://github.com/x/y\">github.com</a> · <a href=\"https://example.org/docs\">example.org</a>"));
    }

    @Test
    void omitsOptionalSectionsAndFlagsOffTheme() {
        Verification v = Verification.reachable("https://tool.dev");
        String msg = MessageFormatter.daily(MONDAY, Fixtures.minimal("Tool", "https://tool.dev"), v);

        assertTrue(msg.contains("⚠️ fora do foco do dia"));
        assertTrue(msg.contains("<i>cli · Go · MIT</i>"), "sem estrelas/push quando não é GitHub");
        assertFalse(msg.contains("Origem"));
        assertFalse(msg.contains("Alternativas"));
        assertFalse(msg.contains("site oficial"));
        assertFalse(msg.contains("Fontes"));
    }

    @Test
    void fallsBackToModelLicenseWhenGithubHasNone() {
        Verification v = new Verification(true, "https://github.com/a/b", 5, null, null, null);
        String msg = MessageFormatter.daily(MONDAY, Fixtures.minimal("B", "https://github.com/a/b"), v);
        assertTrue(msg.contains("<i>cli · Go · MIT · ⭐ 5</i>"));
    }

    @Test
    void respectsTelegramLimit() {
        String huge = "x".repeat(3000);
        Discovery d = new Discovery("Big", "https://big.dev", null, "outro", true, "MIT", "C",
                "t", huge, huge, huge, huge, "make", List.of(), List.of("https://big.dev"));
        String msg = MessageFormatter.daily(MONDAY, d, Verification.reachable("https://big.dev"));
        assertTrue(msg.length() <= MessageFormatter.TELEGRAM_LIMIT, "tamanho: " + msg.length());
        assertTrue(msg.contains("<a href=\"https://big.dev\"><b>Big</b></a>"), "link nunca é cortado");
        assertTrue(msg.contains("…"));
    }

    @Test
    void weeklySummary() {
        List<SeenEntry> week = List.of(
                SeenEntry.daily("A", "https://github.com/a/a", "faz A", "2026-09-30T08:00:00-03:00", true),
                SeenEntry.daily("B & C", "https://b.dev", null, "2026-10-04T08:00:00-03:00", false));
        String msg = MessageFormatter.weekly(LocalDate.of(2026, 10, 4), week);
        assertEquals("""
                📚 <b>Resumo da semana</b> (28/09 – 04/10)
                • <a href="https://github.com/a/a">A</a> — faz A
                • <a href="https://b.dev">B &amp; C</a>""", msg);
    }

    @Test
    void emptyWeek() {
        assertTrue(MessageFormatter.weekly(LocalDate.of(2026, 10, 4), List.of()).endsWith("Nenhum projeto enviado nesta semana."));
    }

    @Test
    void stars() {
        assertEquals("999", MessageFormatter.formatStars(999));
        assertEquals("1k", MessageFormatter.formatStars(1000));
        assertEquals("12.3k", MessageFormatter.formatStars(12345));
        assertEquals("124k", MessageFormatter.formatStars(123953));
    }
}
