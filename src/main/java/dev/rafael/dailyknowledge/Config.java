package dev.rafael.dailyknowledge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuração lida do arquivo {@code .env} na raiz do projeto.
 * Variáveis de ambiente reais têm prioridade sobre o arquivo.
 */
public record Config(
        Path projectDir,
        String telegramBotToken,
        String telegramChatId,
        String agyBin,
        String agyModel,
        String agyTimeout,
        int maxAttempts,
        String githubToken) {

    public static Config load(Path projectDir) throws IOException {
        Map<String, String> values = new HashMap<>();
        Path envFile = projectDir.resolve(".env");
        if (Files.exists(envFile)) {
            values.putAll(parseEnv(Files.readString(envFile, StandardCharsets.UTF_8)));
        }
        values.putAll(System.getenv());
        return new Config(
                projectDir.toAbsolutePath().normalize(),
                blankToNull(values.get("TELEGRAM_BOT_TOKEN")),
                blankToNull(values.get("TELEGRAM_CHAT_ID")),
                values.getOrDefault("AGY_BIN", "/usr/bin/agy"),
                values.getOrDefault("AGY_MODEL", "gemini-3.1-pro-high"),
                values.getOrDefault("AGY_TIMEOUT", "10m"),
                Integer.parseInt(values.getOrDefault("MAX_ATTEMPTS", "2")),
                blankToNull(values.get("GITHUB_TOKEN")));
    }

    static Map<String, String> parseEnv(String content) {
        Map<String, String> result = new HashMap<>();
        for (String raw : content.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).strip();
            String value = line.substring(eq + 1).strip();
            if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                    || value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }
            result.put(key, value);
        }
        return result;
    }

    /** Converte valores como "10m", "90s" ou "1h" em {@link Duration}. */
    public Duration agyTimeoutDuration() {
        String t = agyTimeout.strip().toLowerCase();
        long n = Long.parseLong(t.substring(0, t.length() - 1));
        return switch (t.charAt(t.length() - 1)) {
            case 's' -> Duration.ofSeconds(n);
            case 'm' -> Duration.ofMinutes(n);
            case 'h' -> Duration.ofHours(n);
            default -> throw new IllegalArgumentException("AGY_TIMEOUT inválido: " + agyTimeout);
        };
    }

    public Path seenFile() {
        return projectDir.resolve("data").resolve("seen.json");
    }

    public Path workDir() {
        return projectDir.resolve("work");
    }

    public Path logsDir() {
        return projectDir.resolve("logs");
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    @Override
    public String toString() {
        // Nunca expor o token em logs.
        return "Config[projectDir=" + projectDir + ", agyBin=" + agyBin + ", agyModel=" + agyModel
                + ", agyTimeout=" + agyTimeout + ", maxAttempts=" + maxAttempts
                + ", telegramConfigured=" + (telegramBotToken != null && telegramChatId != null) + "]";
    }
}
