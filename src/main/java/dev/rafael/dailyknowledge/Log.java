package dev.rafael.dailyknowledge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/** Log mínimo: escreve em stderr e em {@code logs/yyyy-MM-dd.log}. */
public final class Log {

    private static Path file;

    private Log() {
    }

    public static void init(Path logsDir, ZonedDateTime now) {
        try {
            Files.createDirectories(logsDir);
            file = logsDir.resolve(now.format(DateTimeFormatter.ISO_LOCAL_DATE) + ".log");
        } catch (IOException e) {
            System.err.println("Não foi possível criar o diretório de logs: " + e.getMessage());
        }
    }

    public static void info(String msg) {
        write("INFO ", msg);
    }

    public static void warn(String msg) {
        write("WARN ", msg);
    }

    public static void error(String msg) {
        write("ERROR", msg);
    }

    private static synchronized void write(String level, String msg) {
        String line = ZonedDateTime.now(Main.ZONE).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                + " " + level + " " + msg;
        System.err.println(line);
        if (file != null) {
            try {
                Files.writeString(file, line + System.lineSeparator(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ignored) {
                // stderr já recebeu a linha
            }
        }
    }
}
