package dev.rafael.dailyknowledge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailyRunnerTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 5);
    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 10, 5, 8, 0, 0, 0, Main.ZONE);

    @TempDir
    Path tmp;

    /** Devolve as respostas em ordem e guarda os prompts recebidos. */
    static final class ScriptedResearcher implements Researcher {
        final Deque<Object> answers = new ArrayDeque<>();
        final List<String> prompts = new ArrayList<>();

        ScriptedResearcher(Object... answers) {
            this.answers.addAll(List.of(answers));
        }

        @Override
        public Discovery research(String prompt, int attempt) throws ResearchException {
            prompts.add(prompt);
            Object next = answers.poll();
            if (next instanceof Discovery d) {
                return d;
            }
            throw new ResearchException("falha simulada");
        }
    }

    /** Verificador sem rede: mapa URL → resultado; ausente = encontrado com a própria URL. */
    static final class FakeVerifier extends Verifier {
        final Map<String, Verification> results;

        FakeVerifier(Map<String, Verification> results) {
            super(null);
            this.results = results;
        }

        @Override
        public Verification verify(String url) {
            return results.getOrDefault(url, Verification.reachable(url));
        }
    }

    private SeenStore seedStore() {
        return new SeenStore(tmp.resolve("data/seen.json"), List.of(
                new SeenEntry("Pandoc", "https://github.com/jgm/pandoc", null, null, SeenEntry.SEED, null, null)));
    }

    private DailyRunner runner(SeenStore store, Researcher r, Verifier v, List<String> sent, boolean persist) {
        return new DailyRunner(DATE, 2, store, PromptBuilder.fromResource(), r, v, sent::add, persist, () -> NOW);
    }

    @Test
    void sendsFirstValidCandidate() throws Exception {
        SeenStore store = seedStore();
        List<String> sent = new ArrayList<>();
        Optional<Discovery> result = runner(store,
                new ScriptedResearcher(Fixtures.discovery("New", "https://github.com/n/new")),
                new FakeVerifier(Map.of()), sent, true).run();

        assertTrue(result.isPresent());
        assertEquals(1, sent.size());
        assertEquals(2, store.all().size());
        SeenEntry last = store.all().get(1);
        assertEquals(SeenEntry.DAILY, last.source());
        assertEquals("2026-10-05T08:00:00-03:00", last.sentAt());
        assertTrue(SeenStore.load(tmp.resolve("data/seen.json")).contains("https://github.com/n/new"), "persistido");
    }

    @Test
    void retriesWhenFirstCandidateIsDuplicate() throws Exception {
        SeenStore store = seedStore();
        List<String> sent = new ArrayList<>();
        ScriptedResearcher r = new ScriptedResearcher(
                Fixtures.discovery("Pandoc", "https://github.com/jgm/pandoc.git"),
                Fixtures.discovery("New", "https://github.com/n/new"));
        Optional<Discovery> result = runner(store, r, new FakeVerifier(Map.of()), sent, true).run();

        assertEquals("New", result.orElseThrow().name());
        assertEquals(1, sent.size());
        assertEquals(2, r.prompts.size());
        assertEquals(r.prompts.get(0), r.prompts.get(1), "prompt idêntico entre tentativas (nada novo a registrar)");
    }

    @Test
    void detectsRenamedRepoViaCanonicalUrl() throws Exception {
        SeenStore store = seedStore();
        List<String> sent = new ArrayList<>();
        FakeVerifier v = new FakeVerifier(Map.of("https://github.com/old/pandoc",
                new Verification(true, "https://github.com/jgm/pandoc", 1, "GPL-2.0", null, null)));
        Optional<Discovery> result = runner(store,
                new ScriptedResearcher(Fixtures.discovery("Pandoc", "https://github.com/old/pandoc")),
                v, sent, true).run();

        assertFalse(result.isPresent());
        assertTrue(sent.isEmpty());
    }

    @Test
    void rejectedCandidateIsPersistedAndAppearsInNextPrompt() throws Exception {
        SeenStore store = seedStore();
        List<String> sent = new ArrayList<>();
        ScriptedResearcher r = new ScriptedResearcher(
                Fixtures.discovery("Ghost", "https://github.com/ghost/ghost"),
                Fixtures.discovery("New", "https://github.com/n/new"));
        FakeVerifier v = new FakeVerifier(Map.of("https://github.com/ghost/ghost", Verification.notFound("404")));

        Optional<Discovery> result = runner(store, r, v, sent, true).run();

        assertEquals("New", result.orElseThrow().name());
        assertFalse(r.prompts.get(0).contains("Ghost"));
        assertTrue(r.prompts.get(1).contains("- Ghost — https://github.com/ghost/ghost"));
        SeenStore reloaded = SeenStore.load(tmp.resolve("data/seen.json"));
        SeenEntry rejected = reloaded.all().stream().filter(e -> e.name().equals("Ghost")).findFirst().orElseThrow();
        assertEquals(SeenEntry.REJECTED, rejected.source());
        assertEquals("404", rejected.reason());
    }

    @Test
    void givesUpAfterMaxAttempts() throws Exception {
        SeenStore store = seedStore();
        List<String> sent = new ArrayList<>();
        ScriptedResearcher r = new ScriptedResearcher("erro", "erro", Fixtures.discovery("Never", "https://x"));
        Optional<Discovery> result = runner(store, r, new FakeVerifier(Map.of()), sent, true).run();

        assertFalse(result.isPresent());
        assertTrue(sent.isEmpty());
        assertEquals(2, r.prompts.size(), "máximo de 2 tentativas");
        assertFalse(store.isDirty());
    }

    @Test
    void dryRunDoesNotWriteFile() throws Exception {
        SeenStore store = seedStore();
        List<String> sent = new ArrayList<>();
        runner(store, new ScriptedResearcher(Fixtures.discovery("New", "https://github.com/n/new")),
                new FakeVerifier(Map.of()), sent, false).run();

        assertEquals(1, sent.size());
        assertFalse(tmp.resolve("data/seen.json").toFile().exists());
    }
}
