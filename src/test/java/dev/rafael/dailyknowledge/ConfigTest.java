package dev.rafael.dailyknowledge;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ConfigTest {

    @Test
    void parsesEnvFile() {
        Map<String, String> env = Config.parseEnv("""
                # comentário
                TELEGRAM_BOT_TOKEN=123:abc
                TELEGRAM_CHAT_ID = "42"

                AGY_MODEL='gemini-3.1-pro-high'
                INVALID_LINE
                """);
        assertEquals("123:abc", env.get("TELEGRAM_BOT_TOKEN"));
        assertEquals("42", env.get("TELEGRAM_CHAT_ID"));
        assertEquals("gemini-3.1-pro-high", env.get("AGY_MODEL"));
        assertEquals(3, env.size());
    }

    @Test
    void parsesTimeout() {
        Config c = new Config(null, null, null, "agy", "m", "10m", 2);
        assertEquals(Duration.ofMinutes(10), c.agyTimeoutDuration());
    }

    @Test
    void toStringNeverLeaksToken() {
        Config c = new Config(null, "SECRET-TOKEN", "1", "agy", "m", "10m", 2);
        assertFalse(c.toString().contains("SECRET-TOKEN"));
    }
}
