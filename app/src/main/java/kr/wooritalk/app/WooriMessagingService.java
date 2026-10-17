package kr.wooritalk.app;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class WooriMessagingService extends FirebaseMessagingService {
    @Override public void onNewToken(String token) {
        super.onNewToken(token);
        WooriApp.prefs(this).edit().putString("fcm_token", token).apply();
    }

    @Override public void onMessageReceived(RemoteMessage message) {
        super.onMessageReceived(message);
        if (!WooriApp.prefs(this).getBoolean("notifications_enabled", false)) return;
        Map<String,String> data = message.getData();
        String title = data.get("title");
        String body = data.get("body");
        String url = data.get("url");
        NotificationHelper.show(this, title, body, url);
    }
}
