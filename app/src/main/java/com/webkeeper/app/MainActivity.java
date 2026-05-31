package com.webkeeper.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;

public class MainActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private EditText urlInput;
    private static final String DEFAULT_URL =
        "https://shell.cloud.google.com/?hl=en_US&theme=dark&authuser=1&fromcloudshell=true&show=terminal";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("webkeeper", MODE_PRIVATE);

        urlInput = findViewById(R.id.urlInput);
        Button btnOpen = findViewById(R.id.btnOpen);

        String savedUrl = prefs.getString("url", DEFAULT_URL);
        urlInput.setText(savedUrl);

        btnOpen.setOnClickListener(v -> openUrl());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1);
        }

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }

        // Автоматически открываем сохранённый сайт
        openUrl();
    }

    private void openUrl() {
        String url = urlInput.getText().toString().trim();
        if (url.isEmpty()) url = DEFAULT_URL;
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        prefs.edit().putString("url", url).apply();

        // Обновляем уведомление сервиса с актуальным URL
        Intent serviceIntent = new Intent(this, WebKeeperService.class);
        serviceIntent.putExtra("url", url);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setUrlBarHidingEnabled(false)
            .build();
        customTabsIntent.launchUrl(this, Uri.parse(url));
    }
}
