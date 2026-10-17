package com.example.basealarm;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Отправка сообщения в Discord через вебхук, в отдельном потоке, чтобы не тормозить игру */
public class DiscordNotifier {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public static void sendAlert(String webhookUrl, String playerName, double distance) {
        if (webhookUrl == null || webhookUrl.isBlank()) return;

        String content = "@everyone \u26A0 Игрок **" + escape(playerName)
                + "** приближается к базе! Расстояние: " + Math.round(distance) + " блоков";

        String json = "{"
                + "\"content\":\"" + content + "\","
                + "\"allowed_mentions\":{\"parse\":[\"everyone\"]}"
                + "}";

        Thread.ofVirtual().start(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(webhookUrl))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json, java.nio.charset.StandardCharsets.UTF_8))
                        .build();
                CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
            } catch (Exception ignored) {
                // если нет интернета/вебхук неверный - просто не отправится, игру это не должно ронять
            }
        });
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
