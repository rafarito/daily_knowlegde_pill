package dev.rafael.dailyknowledge;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Ponto de entrada.
 * <ul>
 *   <li>{@code java -jar daily-knowledge.jar} — execução normal</li>
 *   <li>{@code --dry-run} — imprime as mensagens em vez de enviar e não grava o histórico</li>
 *   <li>{@code --weekly-only} — envia só o resumo semanal</li>
 * </ul>
 */
public final class Main {

    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        Set<String> flags = Set.of(args);
        boolean dryRun = flags.contains("--dry-run");
        boolean weeklyOnly = flags.contains("--weekly-only");

        Path projectDir = projectDir();
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        LocalDate today = now.toLocalDate();

        Config cfg = Config.load(projectDir);
        Log.init(cfg.logsDir(), now);
        Log.info("Início (dryRun=" + dryRun + ", weeklyOnly=" + weeklyOnly + ") " + cfg);

        SeenStore store = SeenStore.load(cfg.seenFile());
        Sender sender = dryRun
                ? html -> System.out.println("\n===== MENSAGEM (dry-run) =====\n" + html + "\n==============================\n")
                : new TelegramClient(cfg.telegramBotToken(), cfg.telegramChatId());

        boolean ok = true;
        String commitMessage = "chore(seen): rejeitados de " + today;

        if (!weeklyOnly) {
            String schema = PromptBuilder.readResource("/discovery.schema.json");
            DailyRunner runner = new DailyRunner(today, cfg.maxAttempts(), store, PromptBuilder.fromResource(),
                    new AgyClient(cfg, schema, today), new Verifier(), sender, !dryRun,
                    () -> ZonedDateTime.now(ZONE));
            try {
                Optional<Discovery> sent = runner.run();
                ok = sent.isPresent();
                if (sent.isPresent()) {
                    commitMessage = "chore(seen): " + sent.get().name() + " (" + today + ")";
                }
            } catch (IOException e) {
                Log.error("Falha no fluxo diário: " + e.getMessage());
                ok = false;
            }
        }

        if (weeklyOnly || today.getDayOfWeek() == DayOfWeek.SUNDAY) {
            List<SeenEntry> week = store.sentInWeekEnding(today);
            try {
                sender.send(MessageFormatter.weekly(today, week));
                Log.info("Resumo semanal enviado (" + week.size() + " projeto(s))");
            } catch (IOException e) {
                Log.error("Falha ao enviar o resumo semanal: " + e.getMessage());
                ok = false;
            }
        }

        if (!dryRun && store.isDirty()) {
            GitCommitter.commitSeen(cfg.projectDir(), commitMessage);
        }

        Log.info("Fim (" + (ok ? "sucesso" : "falha") + ")");
        System.exit(ok ? 0 : 1);
    }

    /** Raiz do projeto: pai de {@code target/} quando rodando do jar; senão, o diretório atual. */
    static Path projectDir() {
        try {
            Path location = Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (location.toString().endsWith(".jar") && location.getParent() != null
                    && location.getParent().getFileName().toString().equals("target")) {
                return location.getParent().getParent();
            }
        } catch (URISyntaxException | SecurityException | NullPointerException ignored) {
            // cai para o diretório atual
        }
        return Path.of("").toAbsolutePath();
    }
}
