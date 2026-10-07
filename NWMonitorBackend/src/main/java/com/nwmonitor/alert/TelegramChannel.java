package com.nwmonitor.alert;

import com.nwmonitor.settings.Settings;
import com.nwmonitor.settings.SettingsService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class TelegramChannel implements NotificationChannel {
    private final SettingsService settings;
    private final RestClient http = RestClient.create();

    public TelegramChannel(SettingsService settings) { this.settings = settings; }

    @Override
    public String name() { return "telegram"; }

    @Override
    public void send(Alert a) {
        Settings.Alerting s = settings.alerting();
        if (!s.telegramEnabled()) return;
        sendText(s.telegramBotToken(), s.telegramChatId(), format(a));
    }

    public void sendText(String token, String chatId, String text) {
        if (token == null || token.isBlank() || chatId == null || chatId.isBlank()) {
            throw new IllegalStateException("Telegram bot token and chat id are required");
        }
        http.post().uri("https://api.telegram.org/bot{token}/sendMessage", token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("chat_id", chatId, "text", text))
                .retrieve().toBodilessEntity();
    }

    private static String format(Alert a) {
        String icon = switch (a.getSeverity()) { case CRITICAL -> "🚨"; case WARNING -> "⚠️"; case INFO -> "ℹ️"; };
        return icon + " " + a.getTitle() + "\n" + (a.getMessage() == null ? "" : a.getMessage());
    }
}
