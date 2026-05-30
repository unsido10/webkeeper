package com.webkeeper.app;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private EditText urlInput;
    private LinearLayout urlBar;
    private TextView statusText;
    private SharedPreferences prefs;
    private boolean urlBarVisible = false;

    @SuppressLint({"SetJavaScriptEnabled", "BatteryLife"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("webkeeper", MODE_PRIVATE);

        webView = findViewById(R.id.webView);
        urlInput = findViewById(R.id.urlInput);
        urlBar = findViewById(R.id.urlBar);
        statusText = findViewById(R.id.statusText);
        ImageButton btnGo = findViewById(R.id.btnGo);
        ImageButton btnMenu = findViewById(R.id.btnMenu);
        ImageButton btnRefresh = findViewById(R.id.btnRefresh);

        setupWebView();

        btnMenu.setOnClickListener(v -> toggleUrlBar());
        btnGo.setOnClickListener(v -> navigateToUrl());
        btnRefresh.setOnClickListener(v -> webView.reload());

        urlInput.setOnEditorActionListener((v, actionId, event) -> {
            navigateToUrl();
            return true;
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1);
        }

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }

        startWebKeeperService();

        String savedUrl = prefs.getString("url", "https://runkoda.com");
        urlInput.setText(savedUrl);
        webView.loadUrl(savedUrl);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        // Desktop User Agent — сайт покажет десктопную версию
        settings.setUserAgentString(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        );

        webView.setScrollBarStyle(WebView.SCROLLBARS_INSIDE_OVERLAY);
        webView.setInitialScale(1);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                view.loadUrl(request.getUrl().toString());
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Inject JS to force desktop viewport
                view.evaluateJavascript(
                    "document.querySelector('meta[name=viewport]') && " +
                    "(document.querySelector('meta[name=viewport]').content = " +
                    "'width=1280, initial-scale=0.5');",
                    null
                );
                urlInput.setText(url);
                prefs.edit().putString("url", url).apply();
                statusText.setText("● LIVE");
                statusText.setTextColor(Color.parseColor("#00FF88"));
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                statusText.setText("● OFFLINE");
                statusText.setTextColor(Color.parseColor("#FF4444"));
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    statusText.setText("● " + newProgress + "%");
                    statusText.setTextColor(Color.parseColor("#FFAA00"));
                }
            }
        });
    }

    private void toggleUrlBar() {
        urlBarVisible = !urlBarVisible;
        urlBar.setVisibility(urlBarVisible ? View.VISIBLE : View.GONE);
        if (urlBarVisible) {
            urlInput.requestFocus();
        }
    }

    private void navigateToUrl() {
        String url = urlInput.getText().toString().trim();
        if (!url.isEmpty()) {
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }
            prefs.edit().putString("url", url).apply();
            webView.loadUrl(url);
            urlBar.setVisibility(View.GONE);
            urlBarVisible = false;
            Intent serviceIntent = new Intent(this, WebKeeperService.class);
            serviceIntent.putExtra("url", url);
            startService(serviceIntent);
        }
    }

    private void startWebKeeperService() {
        String url = prefs.getString("url", "https://runkoda.com");
        Intent serviceIntent = new Intent(this, WebKeeperService.class);
        serviceIntent.putExtra("url", url);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            moveTaskToBack(true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
        startWebKeeperService();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        webView.destroy();
    }
}
