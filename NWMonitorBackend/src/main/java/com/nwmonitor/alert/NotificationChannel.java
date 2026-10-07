package com.nwmonitor.alert;

/** Extension point: add an implementation (e-mail, FCM push for a mobile app, ...) and it receives every new alert. */
public interface NotificationChannel {
    String name();

    void send(Alert alert);
}
