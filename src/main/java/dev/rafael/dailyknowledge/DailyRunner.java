package dev.rafael.dailyknowledge;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Fluxo diário: pesquisa → verificação → deduplicação → envio → histórico,
 * com até {@code maxAttempts} tentativas. O texto do prompt nunca muda entre tentativas;
 * candidatos rejeitados entram no histórico e, portanto, no {{SEEN_LIST}} da próxima.
 */
public final class DailyRunner {

    private final LocalDate date;
    private final int maxAttempts;
    private final SeenStore store;
    private final PromptBuilder promptBuilder;
    private final Researcher researcher;
    private final Verifier verifier;
    private final Sender sender;
    private final boolean persist;
    private final Supplier<ZonedDateTime> clock;

    public DailyRunner(LocalDate date, int maxAttempts, SeenStore store, PromptBuilder promptBuilder,
                       Researcher researcher, Verifier verifier, Sender sender, boolean persist,
                       Supplier<ZonedDateTime> clock) {
        this.date = date;
        this.maxAttempts = maxAttempts;
        this.store = store;
        this.promptBuilder = promptBuilder;
        this.researcher = researcher;
        this.verifier = verifier;
        this.sender = sender;
        this.persist = persist;
        this.clock = clock;
    }

    /** @return a descoberta enviada, ou vazio se todas as tentativas falharam. */
    public Optional<Discovery> run() throws IOException {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String prompt = promptBuilder.build(date, store.all());
            Discovery d;
            try {
                d = researcher.research(prompt, attempt);
            } catch (Researcher.ResearchException e) {
                Log.warn("Tentativa " + attempt + " falhou: " + e.getMessage());
                continue;
            }
            Log.info("Tentativa " + attempt + ": candidato " + d.name() + " — " + d.url());

            if (store.contains(d.url())) {
                Log.warn("Candidato já está no histórico: " + d.url());
                continue;
            }
            Verification v = verifier.verify(d.url());
            if (!v.found()) {
                Log.warn("Candidato rejeitado: " + v.failureReason());
                store.add(SeenEntry.rejected(d.name(), UrlNormalizer.normalize(d.url()), now(), v.failureReason()));
                save();
                continue;
            }
            if (store.contains(v.canonicalUrl())) {
                Log.warn("Candidato é renomeação/redirecionamento de um projeto já enviado: " + v.canonicalUrl());
                continue;
            }

            sender.send(MessageFormatter.daily(date, d, v));
            Log.info("Descoberta enviada: " + d.name());
            store.add(SeenEntry.daily(d.name(), v.canonicalUrl(), d.tagline(), now(), d.isThemeMatch()));
            save();
            return Optional.of(d);
        }
        Log.error("Nenhuma descoberta válida após " + maxAttempts + " tentativa(s)");
        return Optional.empty();
    }

    private void save() throws IOException {
        if (persist) {
            store.save();
        }
    }

    private String now() {
        return clock.get().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
