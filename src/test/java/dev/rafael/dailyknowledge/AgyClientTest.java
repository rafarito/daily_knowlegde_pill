package dev.rafael.dailyknowledge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Usa um script falso no lugar do agy (AGY_BIN configurável). */
class AgyClientTest {

    private static final String VALID = """
            {"conversation_id":"x","status":"SUCCESS","response":"```json {} ``` lixo",
             "structured_output":{"name":"Tool","url":"https://github.com/o/tool","website":null,
               "category":"cli","theme_match":true,"license":"MIT","language":"Rust","tagline":"t",
               "what_it_does":"w","context_and_need":"c","origin":null,"why_interesting":"y",
               "try_it":"cargo install tool","alternatives":[],"sources":["https://github.com/o/tool"]}}
            """;

    @TempDir
    Path tmp;

    private AgyClient clientWithFakeAgy(String stdout, int exitCode) throws Exception {
        Path out = tmp.resolve("canned.json");
        Files.writeString(out, stdout);
        Path args = tmp.resolve("args.txt");
        Path script = tmp.resolve("fake-agy.sh");
        Files.writeString(script, """
                #!/bin/sh
                pwd > "%s"
                printf '%%s\\n' "$@" >> "%s"
                cat "%s"
                exit %d
                """.formatted(args, args, out, exitCode));
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"));
        Config cfg = new Config(tmp, null, null, script.toString(), "gemini-3.1-pro-high", "1m", 2);
        return new AgyClient(cfg, "{\"type\":\"object\"}", LocalDate.of(2026, 10, 5));
    }

    @Test
    void readsStructuredOutputAndPassesExpectedFlags() throws Exception {
        Discovery d = clientWithFakeAgy(VALID, 0).research("PROMPT", 1);
        assertEquals("Tool", d.name());
        assertEquals("https://github.com/o/tool", d.url());
        assertTrue(d.alternatives().isEmpty());

        String args = Files.readString(tmp.resolve("args.txt"));
        assertTrue(args.startsWith(tmp.resolve("work").toString()), "roda no diretório isolado work/");
        for (String flag : new String[]{"-p", "PROMPT", "--model", "gemini-3.1-pro-high", "--sandbox",
                "--dangerously-skip-permissions", "--output-format", "json", "--json-schema", "--print-timeout"}) {
            assertTrue(args.contains(flag + "\n"), "faltou " + flag);
        }
        assertTrue(Files.exists(tmp.resolve("logs/agy-2026-10-05-attempt1.out.json")));
    }

    @Test
    void failsOnNonZeroExit() throws Exception {
        AgyClient c = clientWithFakeAgy(VALID, 3);
        var e = assertThrows(Researcher.ResearchException.class, () -> c.research("p", 1));
        assertTrue(e.getMessage().contains("código 3"));
    }

    @Test
    void failsWithoutStructuredOutput() throws Exception {
        AgyClient c = clientWithFakeAgy("{\"status\":\"SUCCESS\",\"response\":\"oi\"}", 0);
        var e = assertThrows(Researcher.ResearchException.class, () -> c.research("p", 1));
        assertTrue(e.getMessage().contains("structured_output ausente"));
    }

    @Test
    void failsOnErrorStatus() throws Exception {
        AgyClient c = clientWithFakeAgy("{\"status\":\"ERROR\"}", 0);
        assertThrows(Researcher.ResearchException.class, () -> c.research("p", 1));
    }

    @Test
    void failsOnContractViolation() {
        String missingTagline = VALID.replace("\"tagline\":\"t\",", "");
        var e = assertThrows(Researcher.ResearchException.class, () -> AgyClient.parse(missingTagline));
        assertTrue(e.getMessage().contains("tagline"));
    }

    @Test
    void failsOnGarbage() {
        assertThrows(Researcher.ResearchException.class, () -> AgyClient.parse("not json"));
    }
}
