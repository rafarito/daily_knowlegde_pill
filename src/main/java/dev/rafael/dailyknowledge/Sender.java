package dev.rafael.dailyknowledge;

import java.io.IOException;

/** Destino das mensagens (Telegram ou stdout no --dry-run). */
public interface Sender {

    void send(String html) throws IOException;
}
