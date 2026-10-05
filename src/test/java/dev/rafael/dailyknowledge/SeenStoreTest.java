package dev.rafael.dailyknowledge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeenStoreTest {

    @TempDir
    Path tmp;

    @Test
    void loadsTheRealSeed() throws Exception {
        SeenStore store = SeenStore.load(Path.of("data/seen.json"));
        assertEquals(7, store.all().size());
        assertTrue(store.all().stream().allMatch(e -> SeenEntry.SEED.equals(e.source())));
        assertTrue(store.contains("https://github.com/jgm/pandoc.git"));
        assertTrue(store.contains("http://www.github.com/cjpais/handy/"));
        assertFalse(store.contains("https://github.com/someone/else"));
    }

    @Test
    void saveAndReloadRoundTrip() throws Exception {
        Path file = tmp.resolve("data/seen.json");
        SeenStore store = SeenStore.load(file);
        assertFalse(store.isDirty());
        store.add(SeenEntry.daily("A", "https://github.com/a/a", "faz A", "2026-10-05T08:00:00-03:00", true));
        store.add(SeenEntry.rejected("Fake", "https://github.com/fake/fake", "2026-10-05T08:01:00-03:00", "404"));
        assertTrue(store.isDirty());
        store.save();

        assertFalse(Files.exists(file.resolveSibling("seen.json.tmp")));
        String json = Files.readString(file);
        assertTrue(json.contains("\"sent_at\""), json);
        assertTrue(json.contains("\"theme_match\" : true"), json);
        assertFalse(json.contains("\"reason\" : null"), "campos nulos não são gravados");

        SeenStore reloaded = SeenStore.load(file);
        assertEquals(2, reloaded.all().size());
        assertEquals("404", reloaded.all().get(1).reason());
        assertTrue(reloaded.contains("https://github.com/fake/fake"));
    }

    @Test
    void weeklyWindowOnlyIncludesDailyEntriesFromLast7Days() {
        SeenStore store = new SeenStore(tmp.resolve("x.json"), List.of(
                new SeenEntry("Seed", "https://s", null, null, SeenEntry.SEED, null, null),
                SeenEntry.daily("Old", "https://old", null, "2026-09-27T08:00:00-03:00", true),
                SeenEntry.daily("Mon", "https://mon", null, "2026-09-28T08:00:00-03:00", true),
                SeenEntry.rejected("Rej", "https://rej", "2026-10-01T08:00:00-03:00", "404"),
                SeenEntry.daily("Sun", "https://sun", null, "2026-10-04T08:00:00-03:00", true)));
        List<SeenEntry> week = store.sentInWeekEnding(LocalDate.of(2026, 10, 4));
        assertEquals(List.of("Mon", "Sun"), week.stream().map(SeenEntry::name).toList());
    }
}
