package dev.rafael.dailyknowledge;

import java.time.DayOfWeek;
import java.util.EnumMap;
import java.util.Map;

/** Foco sugerido por dia da semana (preferência, não restrição — ver {@code theme_match}). */
public final class Themes {

    private static final Map<DayOfWeek, String> THEMES = new EnumMap<>(DayOfWeek.class);

    static {
        THEMES.put(DayOfWeek.MONDAY, "Bibliotecas e SDKs (qualquer linguagem)");
        THEMES.put(DayOfWeek.TUESDAY, "Ferramentas CLI");
        THEMES.put(DayOfWeek.WEDNESDAY, "Self-hosted (alternativas a SaaS)");
        THEMES.put(DayOfWeek.THURSDAY, "Dados, documentos e conversão (estilo Pandoc)");
        THEMES.put(DayOfWeek.FRIDAY, "IA e automação local (estilo Handy)");
        THEMES.put(DayOfWeek.SATURDAY, "Apps desktop e produtividade");
        THEMES.put(DayOfWeek.SUNDAY, "Livre / curiosidade / clássicos esquecidos");
    }

    private Themes() {
    }

    public static String forDay(DayOfWeek day) {
        return THEMES.get(day);
    }
}
