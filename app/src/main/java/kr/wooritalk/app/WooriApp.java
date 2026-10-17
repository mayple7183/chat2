package kr.wooritalk.app;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

public class WooriApp extends Application {
    public static final String PREFS = "wooritalk_native";
    public static final String BASE_URL = "https://ydms3105.synology.me:8443";

    @Override public void onCreate() {
        super.onCreate();
        initFirebaseFromPrefs(this);
    }

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized boolean initFirebaseFromPrefs(Context context) {
        try {
            if (!FirebaseApp.getApps(context).isEmpty()) return true;
            SharedPreferences p = prefs(context);
            String appId = p.getString("fcm_app_id", "");
            String apiKey = p.getString("fcm_api_key", "");
            String projectId = p.getString("fcm_project_id", "");
            String senderId = p.getString("fcm_sender_id", "");
            if (appId.isEmpty() || apiKey.isEmpty() || projectId.isEmpty() || senderId.isEmpty()) return false;
            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setApplicationId(appId)
                    .setApiKey(apiKey)
                    .setProjectId(projectId)
                    .setGcmSenderId(senderId)
                    .build();
            FirebaseApp.initializeApp(context, options);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static synchronized boolean saveFirebaseConfig(Context context, String appId, String apiKey, String projectId, String senderId) {
        prefs(context).edit()
                .putString("fcm_app_id", appId)
                .putString("fcm_api_key", apiKey)
                .putString("fcm_project_id", projectId)
                .putString("fcm_sender_id", senderId)
                .apply();
        return initFirebaseFromPrefs(context);
    }
}
