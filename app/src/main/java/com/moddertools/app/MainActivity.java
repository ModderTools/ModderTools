package com.moddertools.app;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

/**
 * Modder Tools — native Android shell.
 *
 * This activity hosts a single full-screen WebView pointed at the bundled
 * public web app (assets/www/index.html — the same SPA used on the website,
 * unmodified in its Home/tool behaviour) and overlays a floating "glass"
 * bottom navigation bar with four tabs: Home, Search, Files, App Info.
 *
 * Tapping a tab simply changes the SPA's URL hash via WebView.loadUrl():
 *   Home      -> #/home
 *   Search    -> #/search
 *   Files     -> #/files
 *   App Info  -> #/app-info
 * index.html's own router (unchanged architecture from the website) renders
 * the right view for each hash, so there is only one WebView instance and
 * no reloading between tabs — navigation feels instant.
 */
public class MainActivity extends AppCompatActivity {

    private static final String START_URL = "file:///android_asset/www/index.html";
    // Appended to the WebView's user-agent so index.html can detect it's
    // running inside the native app (e.g. to add bottom padding behind the
    // floating nav bar) without changing anything for normal website visitors.
    private static final String APP_UA_SUFFIX = " ModderToolsApp/1.0";

    private WebView webView;
    private SwipeRefreshLayout swipeRefresh;
    private View offlineView;
    private LinearLayout glassNav;

    private ImageButton navHome, navSearch, navFiles, navInfo;
    private View navHomePill, navSearchPill, navFilesPill, navInfoPill;

    private String currentTab = "home";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        offlineView = findViewById(R.id.offlineView);
        glassNav = findViewById(R.id.glassNav);

        navHome = findViewById(R.id.navHome);
        navSearch = findViewById(R.id.navSearch);
        navFiles = findViewById(R.id.navFiles);
        navInfo = findViewById(R.id.navInfo);
        navHomePill = findViewById(R.id.navHomePill);
        navSearchPill = findViewById(R.id.navSearchPill);
        navFilesPill = findViewById(R.id.navFilesPill);
        navInfoPill = findViewById(R.id.navInfoPill);

        setupWebView();
        setupNav();
        setupSwipeRefresh();
        setupRetryButton();

        if (isOnline()) {
            webView.loadUrl(START_URL);
        } else {
            showOffline();
        }
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setAllowFileAccess(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUserAgentString(settings.getUserAgentString() + APP_UA_SUFFIX);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                // Keep navigation inside local app pages (file:// asset URLs and hash
                // changes on them) within the WebView; send every external http(s)
                // link (social shares, developer/GitHub/Telegram/website links,
                // banner external URLs) out to the system browser/app instead.
                if ("file".equals(scheme)) {
                    return false;
                }
                if ("http".equals(scheme) || "https".equals(scheme)) {
                    openExternally(uri);
                    return true;
                }
                // tel:, mailto:, intent:// (Telegram deep links), market:// etc.
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception ignored) { /* no app can handle this scheme — ignore */ }
                return true;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    showOffline();
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                swipeRefresh.setRefreshing(false);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                // target="_blank" links (share overlay, socials) — resolve the
                // intended URL and open it externally rather than opening a
                // blank child WebView.
                WebView.HitTestResult result = view.getHitTestResult();
                String targetUrl = result != null ? result.getExtra() : null;
                if (targetUrl != null) {
                    openExternally(Uri.parse(targetUrl));
                }
                return false;
            }
        });

        // Lets the system/public Download Manager handle app & file
        // download links from the Download pages instead of the WebView
        // trying (and failing) to render the binary inline.
        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception e) {
                try {
                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                    if (dm != null) dm.enqueue(request);
                } catch (Exception ignored) { /* nothing we can do — surface nothing fatal */ }
            }
        });
    }

    private void openExternally(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception ignored) { /* no browser/app available — ignore */ }
    }

    private void setupNav() {
        navHome.setOnClickListener(v -> selectTab("home"));
        navSearch.setOnClickListener(v -> selectTab("search"));
        navFiles.setOnClickListener(v -> selectTab("files"));
        navInfo.setOnClickListener(v -> selectTab("app-info"));
        updateNavTint();
    }

    private void selectTab(String tab) {
        currentTab = tab;
        webView.evaluateJavascript("location.hash = '#/" + tab + "';", null);
        updateNavTint();
    }

    private void updateNavTint() {
        int inactive = getColor(R.color.nav_icon_inactive);
        int active = getColor(R.color.accent_ink);
        navHome.setColorFilter("home".equals(currentTab) ? active : inactive);
        navSearch.setColorFilter("search".equals(currentTab) ? active : inactive);
        navFiles.setColorFilter("files".equals(currentTab) ? active : inactive);
        navInfo.setColorFilter("app-info".equals(currentTab) ? active : inactive);

        navHomePill.setBackgroundResource("home".equals(currentTab) ? R.drawable.bg_nav_active_pill : 0);
        navSearchPill.setBackgroundResource("search".equals(currentTab) ? R.drawable.bg_nav_active_pill : 0);
        navFilesPill.setBackgroundResource("files".equals(currentTab) ? R.drawable.bg_nav_active_pill : 0);
        navInfoPill.setBackgroundResource("app-info".equals(currentTab) ? R.drawable.bg_nav_active_pill : 0);
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setColorSchemeColors(getColor(R.color.accent_ink));
        swipeRefresh.setOnRefreshListener(() -> {
            if (isOnline()) {
                webView.reload();
            } else {
                swipeRefresh.setRefreshing(false);
                showOffline();
            }
        });
    }

    private void setupRetryButton() {
        Button retry = findViewById(R.id.retryButton);
        retry.setOnClickListener(v -> {
            if (isOnline()) {
                hideOffline();
                webView.loadUrl(START_URL);
            }
        });
    }

    private void showOffline() {
        offlineView.setVisibility(View.VISIBLE);
        swipeRefresh.setVisibility(View.GONE);
        glassNav.setVisibility(View.GONE);
    }

    private void hideOffline() {
        offlineView.setVisibility(View.GONE);
        swipeRefresh.setVisibility(View.VISIBLE);
        glassNav.setVisibility(View.VISIBLE);
    }

    @SuppressWarnings("deprecation")
    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
