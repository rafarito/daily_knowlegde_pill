package dev.rafael.dailyknowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Envia mensagens HTML pelo método sendMessage da Bot API do Telegram. */
public final class TelegramClient implements Sender {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final String token;
    private final String chatId;

    public TelegramClient(String token, String chatId) {
        if (token == null || chatId == null) {
            throw new IllegalStateException("TELEGRAM_BOT_TOKEN e TELEGRAM_CHAT_ID precisam estar no .env");
        }
        this.token = token;
        this.chatId = chatId;
    }

    @Override
    public void send(String html) throws IOException {
        ObjectNode body = Json.MAPPER.createObjectNode()
                .put("chat_id", chatId)
                .put("text", html)
                .put("parse_mode", "HTML");
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.telegram.org/bot" + token + "/sendMessage"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(Json.MAPPER.writeValueAsString(body)))
                .build();
        HttpResponse<String> res;
        try {
            res = http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("envio interrompido", e);
        }
        JsonNode json = Json.MAPPER.readTree(res.body());
        if (!json.path("ok").asBoolean(false)) {
            // A descrição do Telegram não contém o token; a URL nunca é logada.
            throw new IOException("Telegram recusou a mensagem (HTTP " + res.statusCode() + "): "
                    + json.path("description").asText());
        }
    }
}
