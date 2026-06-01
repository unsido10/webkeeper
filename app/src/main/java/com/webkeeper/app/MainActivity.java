package com.webkeeper.app;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
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
    private Handler autoClickHandler = new Handler();
    private String targetUrl;

    private static final String DEFAULT_URL =
        "https://shell.cloud.google.com/?hl=en_US&theme=dark&authuser=1&fromcloudshell=true&show=terminal";

    // JS который ищет кнопку Reconnect и кликает её
    private static final String RECONNECT_JS =
        "(function() {" +
        "  var els = document.querySelectorAll('button, a, span, div');" +
        "  for(var i=0; i<els.length; i++) {" +
        "    if(els[i].innerText && els[i].innerText.trim() === 'Reconnect') {" +
        "      els[i].click();" +
        "      return 'clicked';" +
        "    }" +
        "  }" +
        "  return 'not found';" +
        "})();";

    // JS который проверяет есть ли жёлтая плашка
    private static final String CHECK_BANNER_JS =
        "(function() {" +
        "  var els = document.querySelectorAll('*');" +
        "  for(var i=0; i<els.length; i++) {" +
        "    if(els[i].innerText && els[i].innerText.includes('connection to your Cloud Shell was lost')) {" +
        "      return 'found';" +
        "    }" +
        "  }" +
        "  return 'none';" +
        "})();";

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

        CookieManager.getInstance().setAcceptCookie(true);

        prefs = getSharedPreferences("webkeeper", MODE_PRIVATE);
        targetUrl = prefs.getString("url", DEFAULT_URL);

        webView = findViewById(R.id.webView);
        urlInput = findViewById(R.id.urlInput);
        urlBar = findViewById(R.id.urlBar);
        statusText = findViewById(R.id.statusText);
        Button btnGo = findViewById(R.id.btnGo);
        Button btnMenu = findViewById(R.id.btnMenu);
        Button btnRefresh = findViewById(R.id.btnRefresh);

        setupWebView();

        btnMenu.setOnClickListener(v -> toggleUrlBar());
        btnGo.setOnClickListener(v -> navigateToUrl());
        btnRefresh.setOnClickListener(v -> webView.reload());

        urlInput.setText(targetUrl);

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
        webView.loadUrl(targetUrl);
        startAutoReconnect();
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
        settings.setDatabaseEnabled(true);
        settings.setUserAgentString(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        );
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.setScrollBarStyle(WebView.SCROLLBARS_INSIDE_OVERLAY);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                // Если ушли с целевого сайта — возвращаемся
                if (!url.contains("shell.cloud.google.com") &&
                    !url.contains("accounts.google.com") &&
                    !url.contains("oauth")) {
                    view.loadUrl(targetUrl);
                    return true;
                }
                if (url.contains("accounts.google.com") || url.contains("oauth")) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                    return true;
                }
                view.loadUrl(url);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                CookieManager.getInstance().flush();
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

    // Каждые 5 секунд проверяем есть ли плашка и кликаем Reconnect
    private void startAutoReconnect() {
        autoClickHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (webView != null) {
                    webView.evaluateJavascript(CHECK_BANNER_JS, result -> {
                        if (result != null && result.contains("found")) {
                            statusText.setText("● RECONNECTING...");
                            statusText.setTextColor(Color.parseColor("#FFAA00"));
                            webView.evaluateJavascript(RECONNECT_JS, null);
                        }
                    });
                }
                autoClickHandler.postDelayed(this, 5000);
            }
        }, 5000);
    }

    private void toggleUrlBar() {
        urlBarVisible = !urlBarVisible;
        urlBar.setVisibility(urlBarVisible ? View.VISIBLE : View.GONE);
        if (urlBarVisible) urlInput.requestFocus();
    }

    private void navigateToUrl() {
        String url = urlInput.getText().toString().trim();
        if (!url.isEmpty()) {
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }
            targetUrl = url;
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
        Intent serviceIntent = new Intent(this, WebKeeperService.class);
        serviceIntent.putExtra("url", targetUrl);
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
        CookieManager.getInstance().flush();
        startWebKeeperService();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        autoClickHandler.removeCallbacksAndMessages(null);
        webView.destroy();
    }
    }
