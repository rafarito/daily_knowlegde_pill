package dev.rafael.dailyknowledge;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Histórico persistido em {@code data/seen.json}. */
public final class SeenStore {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record FileFormat(List<SeenEntry> projects) {
    }

    private final Path file;
    private final List<SeenEntry> entries;
    private boolean dirty;

    SeenStore(Path file, List<SeenEntry> entries) {
        this.file = file;
        this.entries = new ArrayList<>(entries);
    }

    public static SeenStore load(Path file) throws IOException {
        if (!Files.exists(file)) {
            return new SeenStore(file, List.of());
        }
        FileFormat ff = Json.MAPPER.readValue(file.toFile(), FileFormat.class);
        return new SeenStore(file, ff.projects() == null ? List.of() : ff.projects());
    }

    public List<SeenEntry> all() {
        return Collections.unmodifiableList(entries);
    }

    public boolean contains(String url) {
        String n = UrlNormalizer.normalize(url);
        return entries.stream().anyMatch(e -> UrlNormalizer.normalize(e.url()).equals(n));
    }

    public void add(SeenEntry entry) {
        entries.add(entry);
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    /** Escreve num arquivo temporário e renomeia (move atômico) para não corromper o histórico. */
    public void save() throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Json.MAPPER.writeValue(tmp.toFile(), new FileFormat(entries));
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /** Projetos enviados (source=daily) nos últimos 7 dias, incluindo {@code today}. */
    public List<SeenEntry> sentInWeekEnding(LocalDate today) {
        LocalDate start = today.minusDays(6);
        return entries.stream()
                .filter(e -> SeenEntry.DAILY.equals(e.source()) && e.sentAt() != null)
                .filter(e -> {
                    LocalDate d = OffsetDateTime.parse(e.sentAt()).atZoneSameInstant(Main.ZONE).toLocalDate();
                    return !d.isBefore(start) && !d.isAfter(today);
                })
                .toList();
    }
}
