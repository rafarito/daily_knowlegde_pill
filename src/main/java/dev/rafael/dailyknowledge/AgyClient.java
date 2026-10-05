package dev.rafael.dailyknowledge;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Executa {@code agy -p} como subprocesso, num diretório de trabalho vazio, com sandbox,
 * e lê o campo {@code structured_output} (validado pelo {@code --json-schema}).
 * O campo {@code response} é ignorado porque pode conter texto extra.
 */
public final class AgyClient implements Researcher {

    private final Config cfg;
    private final String schema;
    private final LocalDate date;

    public AgyClient(Config cfg, String schema, LocalDate date) {
        this.cfg = cfg;
        this.schema = schema;
        this.date = date;
    }

    @Override
    public Discovery research(String prompt, int attempt) throws ResearchException {
        try {
            Files.createDirectories(cfg.workDir());
            Files.createDirectories(cfg.logsDir());
            Path out = cfg.logsDir().resolve("agy-" + date + "-attempt" + attempt + ".out.json");
            Path err = cfg.logsDir().resolve("agy-" + date + "-attempt" + attempt + ".err.txt");

            List<String> cmd = List.of(cfg.agyBin(), "-p", prompt,
                    "--model", cfg.agyModel(),
                    "--sandbox", "--dangerously-skip-permissions",
                    "--output-format", "json",
                    "--json-schema", schema,
                    "--print-timeout", cfg.agyTimeout());

            Log.info("Tentativa " + attempt + ": executando agy (modelo " + cfg.agyModel() + ")");
            Process p = new ProcessBuilder(cmd)
                    .directory(cfg.workDir().toFile())
                    .redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")))
                    .redirectOutput(out.toFile())
                    .redirectError(err.toFile())
                    .start();

            Duration hardLimit = cfg.agyTimeoutDuration().plusMinutes(1);
            if (!p.waitFor(hardLimit.toSeconds(), TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new ResearchException("agy não terminou em " + hardLimit);
            }
            String stdout = Files.readString(out, StandardCharsets.UTF_8);
            if (p.exitValue() != 0) {
                throw new ResearchException("agy saiu com código " + p.exitValue() + " (ver " + err.getFileName() + ")");
            }
            return parse(stdout);
        } catch (IOException e) {
            throw new ResearchException("falha ao executar o agy: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResearchException("interrompido", e);
        }
    }

    static Discovery parse(String stdout) throws ResearchException {
        JsonNode root;
        try {
            root = Json.MAPPER.readTree(stdout);
        } catch (IOException e) {
            throw new ResearchException("saída do agy não é JSON", e);
        }
        if (root == null || !"SUCCESS".equals(root.path("status").asText())) {
            throw new ResearchException("status do agy diferente de SUCCESS: "
                    + (root == null ? "vazio" : root.path("status").asText()));
        }
        JsonNode structured = root.get("structured_output");
        if (structured == null || !structured.isObject()) {
            throw new ResearchException("structured_output ausente");
        }
        try {
            Discovery d = Json.MAPPER.treeToValue(structured, Discovery.class);
            d.validate();
            return d;
        } catch (IOException | IllegalArgumentException e) {
            throw new ResearchException("structured_output fora do contrato: " + e.getMessage(), e);
        }
    }
}
