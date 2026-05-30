package com.webkeeper.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import androidx.core.app.NotificationCompat;

public class WebKeeperService extends Service {

    private static final String CHANNEL_ID = "webkeeper_channel";
    private static final int NOTIFICATION_ID = 1337;
    private PowerManager.WakeLock wakeLock;
    private String currentUrl = "https://runkoda.com";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        acquireWakeLock();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("url")) {
            currentUrl = intent.getStringExtra("url");
        } else {
            SharedPreferences prefs = getSharedPreferences("webkeeper", MODE_PRIVATE);
            currentUrl = prefs.getString("url", "https://runkoda.com");
        }
        startForeground(NOTIFICATION_ID, buildNotification(currentUrl));
        return START_STICKY;
    }

    private void acquireWakeLock() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WebKeeper::WakeLock");
            wakeLock.acquire();
        }
    }

    private Notification buildNotification(String url) {
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        String shortUrl = url.replaceAll("https?://", "").replaceAll("/.*", "");
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WebKeeper — активен")
            .setContentText("Держит открытым: " + shortUrl)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "WebKeeper фоновый режим", NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        Intent restartIntent = new Intent(this, WebKeeperService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restartIntent);
        } else {
            startService(restartIntent);
        }
    }
          }
