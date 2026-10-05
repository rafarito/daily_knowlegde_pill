package dev.rafael.dailyknowledge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Faz commit do {@code data/seen.json} no repositório git local, se existir. */
public final class GitCommitter {

    private GitCommitter() {
    }

    public static void commitSeen(Path projectDir, String message) {
        if (!Files.isDirectory(projectDir.resolve(".git"))) {
            Log.warn("Sem repositório git em " + projectDir + "; commit ignorado");
            return;
        }
        try {
            run(projectDir, List.of("git", "add", "data/seen.json"));
            run(projectDir, List.of("git", "commit", "-m", message, "--", "data/seen.json"));
            Log.info("Commit feito: " + message);
        } catch (IOException e) {
            // Falha de commit não invalida o envio já feito.
            Log.warn("Falha ao commitar o seen.json: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void run(Path dir, List<String> cmd) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(60, TimeUnit.SECONDS) || p.exitValue() != 0) {
            throw new IOException(String.join(" ", cmd) + " falhou: " + out.strip());
        }
    }
}
