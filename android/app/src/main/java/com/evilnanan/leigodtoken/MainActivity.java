package com.evilnanan.leigodtoken;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.view.Gravity;
import android.view.View;
import android.text.TextUtils;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.net.http.SslError;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int GREEN = Color.rgb(33, 107, 94);
    private static final int INK = Color.rgb(25, 45, 39);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private WebView web;
    private TextView status, detail, origin;
    private Button copy;
    private ProgressBar progress;
    private final List<View> chromeViews = new ArrayList<>();
    private Button fullscreenButton;
    private LinearLayout root;
    private boolean fullscreen;
    private String token = "";
    private boolean verified, resumed, reading, checking;
    private int generation;
    private SharedPreferences preferences;
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            readToken(false);
            handler.postDelayed(this, 2000);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        preferences = getSharedPreferences("settings", MODE_PRIVATE);
        buildUi();
        configureWebView();
        web.loadUrl(TokenPolicy.LOGIN_URL);
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(243, 246, 244));
        root.setPadding(dp(16), dp(16), dp(16), dp(12));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top, bottom, left, right;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets area = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                top = area.top; bottom = area.bottom; left = area.left; right = area.right;
            } else {
                top = insets.getSystemWindowInsetTop(); bottom = insets.getSystemWindowInsetBottom();
                left = insets.getSystemWindowInsetLeft(); right = insets.getSystemWindowInsetRight();
            }
            int side = dp(fullscreen ? 8 : 16);
            view.setPadding(left + side, top + dp(10), right + side, bottom + dp(10));
            return insets;
        });
        setContentView(root);

        LinearLayout heading = row();
        TextView logo = text("T", 22, Color.WHITE);
        logo.setGravity(Gravity.CENTER);
        logo.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        logo.setBackground(shape(GREEN, 14));
        heading.addView(logo, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout title = new LinearLayout(this);
        title.setOrientation(LinearLayout.VERTICAL);
        title.setPadding(dp(12), 0, 0, 0);
        TextView name = text("雷神 Token 助手", 20, INK);
        name.setTypeface(null, Typeface.BOLD);
        title.addView(name);
        title.addView(text("官网手动登录 · 一键复制凭证", 12, Color.rgb(88, 107, 99)));
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView menu = text("⋮", 28, INK);
        menu.setGravity(Gravity.CENTER);
        menu.setContentDescription("更多设置");
        heading.addView(menu, new LinearLayout.LayoutParams(dp(44), dp(48)));
        menu.setOnClickListener(this::showMenu);
        root.addView(heading);
        chromeViews.add(heading);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        card.setBackground(shape(Color.WHITE, 16));
        status = text("等待官网登录", 16, INK);
        status.setId(R.id.token_status);
        status.setTypeface(null, Typeface.BOLD);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        detail = text("在下方官网主页登录，并完成验证码。", 12, Color.rgb(88, 107, 99));
        detail.setPadding(0, dp(4), 0, 0);
        card.addView(status); card.addView(detail);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.setMargins(0, dp(12), 0, dp(10));
        root.addView(card, cardParams);
        chromeViews.add(card);

        LinearLayout webTools = row();
        origin = text("www.leigod.com", 12, GREEN);
        origin.setGravity(Gravity.CENTER_VERTICAL);
        origin.setSingleLine(true); origin.setEllipsize(TextUtils.TruncateAt.END);
        webTools.addView(origin, new LinearLayout.LayoutParams(0, dp(40), 1));
        Button smaller = button("−", false);
        smaller.setContentDescription("缩小网页");
        smaller.setId(R.id.zoom_out);
        smaller.setOnClickListener(view -> { adaptPage(); web.zoomOut(); });
        webTools.addView(smaller, new LinearLayout.LayoutParams(dp(42), dp(40)));
        Button larger = button("+", false);
        larger.setContentDescription("放大网页");
        larger.setId(R.id.zoom_in);
        larger.setOnClickListener(view -> { adaptPage(); web.zoomIn(); });
        webTools.addView(larger, new LinearLayout.LayoutParams(dp(42), dp(40)));
        Button fit = button("适应", false);
        fit.setOnClickListener(view -> fitPage());
        webTools.addView(fit, new LinearLayout.LayoutParams(dp(52), dp(40)));
        fullscreenButton = button("全屏", false);
        fullscreenButton.setId(R.id.fullscreen_web);
        fullscreenButton.setOnClickListener(view -> toggleFullscreen());
        webTools.addView(fullscreenButton, new LinearLayout.LayoutParams(dp(76), dp(40)));
        root.addView(webTools);
        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(shape(Color.WHITE, 12));
        frame.setClipToOutline(true);
        web = new WebView(this);
        web.setId(R.id.official_web);
        frame.addView(web, new FrameLayout.LayoutParams(-1, -1));
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        frame.addView(progress, new FrameLayout.LayoutParams(-1, dp(3), Gravity.TOP));
        root.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout actions = row();
        actions.setPadding(0, dp(10), 0, 0);
        Button read = button("获取 / 检查", false);
        read.setOnClickListener(view -> readToken(true));
        copy = button("复制 Token", true);
        copy.setId(R.id.copy_token);
        copy.setEnabled(false);
        copy.setAlpha(0.5f);
        copy.setOnClickListener(view -> copyToken());
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, dp(48), 1);
        half.setMarginEnd(dp(8));
        actions.addView(read, half);
        actions.addView(copy, new LinearLayout.LayoutParams(0, dp(48), 1));
        root.addView(actions);
        chromeViews.add(actions);
        Button github = button("打开 GitHub Secret 设置  ↗", false);
        github.setOnClickListener(view -> openBrowser("https://github.com/" + repository() + "/settings/secrets/actions"));
        LinearLayout.LayoutParams githubParams = new LinearLayout.LayoutParams(-1, dp(44));
        githubParams.topMargin = dp(8);
        root.addView(github, githubParams);
        chromeViews.add(github);
        TextView footer = text("复制后粘贴到 LEIGOD_TOKENS 并保存。", 11, Color.rgb(88, 107, 99));
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(6), 0, 0);
        root.addView(footer);
        chromeViews.add(footer);
    }

    private void configureWebView() {
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSafeBrowsingEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        Matcher chrome = Pattern.compile("Chrome/[0-9.]+").matcher(WebSettings.getDefaultUserAgent(this));
        String version = chrome.find() ? chrome.group() : "Chrome/130.0.0.0";
        settings.setUserAgentString("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "
                + version + " Safari/537.36");
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int value) {
                progress.setProgress(value);
                progress.setVisibility(value < 100 ? View.VISIBLE : View.GONE);
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                String url = request.getUrl().toString();
                if (TokenPolicy.isOfficialPage(url)) return false;
                if (request.hasGesture() && "https".equals(request.getUrl().getScheme())) openBrowser(url);
                return true;
            }
            @Override public void onPageFinished(WebView view, String url) {
                origin.setText(TokenPolicy.isOfficialPage(url) ? "www.leigod.com" : "当前不是雷神官网");
                adaptPage();
                readToken(false);
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) setStatus("官网暂时无法打开", "检查网络后，在右上角选择“重新打开官网”。", false);
            }
            @Override public void onReceivedSslError(WebView view, SslErrorHandler ssl, SslError error) {
                ssl.cancel();
                setStatus("官网安全连接失败", "请检查系统时间、网络和 Android System WebView 更新。", false);
            }
        });
    }

    private void adaptPage() {
        if (TokenPolicy.isOfficialPage(web.getUrl())) web.evaluateJavascript(PageLayout.SCRIPT, null);
    }

    private void fitPage() {
        adaptPage();
        web.postDelayed(() -> {
            if (isDestroyed()) return;
            float scale = web.getScale();
            if (scale > 0) web.zoomBy(Math.max(0.01f, Math.min(100f, getResources().getDisplayMetrics().density / scale)));
        }, 100);
    }

    private void toggleFullscreen() {
        fullscreen = !fullscreen;
        for (View view : chromeViews) view.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        fullscreenButton.setText(fullscreen ? "退出全屏" : "全屏");
        root.requestApplyInsets();
        web.postDelayed(this::adaptPage, 100);
    }

    private void readToken(boolean requested) {
        if (reading || checking || !TokenPolicy.isOfficialPage(web.getUrl())) {
            if (requested && !TokenPolicy.isOfficialPage(web.getUrl())) toast("请先重新打开雷神官网");
            return;
        }
        reading = true;
        int pageGeneration = generation;
        web.evaluateJavascript(TokenPolicy.READ_TOKEN, value -> {
            reading = false;
            if (isDestroyed() || pageGeneration != generation || !TokenPolicy.isOfficialPage(web.getUrl())) return;
            try {
                Object decoded = new JSONTokener(value).nextValue();
                JSONObject result = new JSONObject((String) decoded);
                if (!"found".equals(result.optString("status"))) {
                    if (!token.isEmpty()) clearToken();
                    if (requested) setStatus("尚未找到 Token", "请在官网重新登录并完成验证码，再点“获取 / 检查”。", false);
                    return;
                }
                String found = result.getString("token");
                if (requested || !found.equals(token)) {
                    token = found;
                    checkToken(found);
                }
            } catch (Exception ignored) {
                if (requested) setStatus("读取失败", "请重新打开官网后再试。", false);
            }
        });
    }

    private void checkToken(String candidate) {
        checking = true;
        verified = false;
        copy.setEnabled(false);
        copy.setAlpha(0.5f);
        int checkGeneration = ++generation;
        setStatus("已读取，正在检查登录", "只查询账号状态，不会暂停或恢复时长。", false);
        network.execute(() -> {
            LeigodClient.State state = new LeigodClient().check(candidate);
            handler.post(() -> {
                if (isDestroyed() || checkGeneration != generation) return;
                checking = false;
                copy.setEnabled(true);
                copy.setAlpha(1f);
                verified = state == LeigodClient.State.VALID_PAUSED || state == LeigodClient.State.VALID_RUNNING;
                switch (state) {
                    case VALID_PAUSED -> setStatus("登录有效 · 时长已暂停", "可以复制 Token，然后到 GitHub 更新 Secret。", true);
                    case VALID_RUNNING -> setStatus("登录有效 · 时长正在计费", "可以复制 Token；本 App 不会执行暂停。", true);
                    case LOGIN_REJECTED -> setStatus("登录已失效", "请在官网重新登录，再点“获取 / 检查”。", false);
                    case NETWORK_ERROR -> setStatus("已读取，但暂时无法验证", "可检查网络后重试；这不一定代表 Token 过期。", false);
                }
            });
        });
    }

    private void copyToken() {
        if (token.isEmpty()) return;
        if (!verified) {
            new AlertDialog.Builder(this).setTitle("尚未确认有效")
                    .setMessage("当前 Token 未通过验证。可以先重新登录或检查网络，也可手动复制后在 GitHub 验证。")
                    .setPositiveButton("仍然复制", (dialog, which) -> writeClipboard())
                    .setNegativeButton("取消", null).show();
        } else writeClipboard();
    }

    private void writeClipboard() {
        ClipData clip = ClipData.newPlainText("雷神 account_token", token);
        PersistableBundle extras = new PersistableBundle();
        extras.putBoolean("android.content.extra.IS_SENSITIVE", true);
        clip.getDescription().setExtras(extras);
        ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(clip);
        toast("Token 已复制，请到 GitHub 更新 LEIGOD_TOKENS");
    }

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("重新打开官网").setOnMenuItemClickListener(item -> { web.loadUrl(TokenPolicy.LOGIN_URL); return true; });
        menu.getMenu().add("GitHub 仓库").setOnMenuItemClickListener(item -> { editRepository(); return true; });
        menu.getMenu().add("清除本机登录记录").setOnMenuItemClickListener(item -> {
            new AlertDialog.Builder(this).setTitle("清除本机登录记录？")
                    .setMessage("清除本 App 内的官网登录数据，不会修改 GitHub 中保存的 Secret。")
                    .setPositiveButton("清除", (dialog, which) -> {
                        clearToken();
                        WebStorage.getInstance().deleteAllData();
                        CookieManager.getInstance().removeAllCookies(done -> {
                            CookieManager.getInstance().flush(); web.loadUrl(TokenPolicy.LOGIN_URL);
                        });
                        setStatus("等待官网登录", "请重新登录官网并完成验证码。", false);
                    }).setNegativeButton("取消", null).show();
            return true;
        });
        menu.getMenu().add("使用说明").setOnMenuItemClickListener(item -> {
            new AlertDialog.Builder(this).setTitle("使用说明")
                    .setMessage("1. 在官网主页打开登录窗口并登录。\n2. 等待自动检测，或点“获取 / 检查”。\n3. 复制 Token，打开 GitHub 设置。\n4. 更新 LEIGOD_TOKENS 并保存。\n\n登录框可竖向滚动；上方提供缩放、适应和全屏按钮。多个账号请用英文逗号合并全部 Token。提取不会延长有效期。\n\n这是个人辅助工具，非雷神官方 App。")
                    .setPositiveButton("知道了", null).show(); return true;
        });
        menu.show();
    }

    private void clearToken() {
        generation++;
        token = ""; verified = false; checking = false;
        copy.setEnabled(false); copy.setAlpha(0.5f);
    }

    private String repository() { return preferences.getString("repository", TokenPolicy.DEFAULT_REPOSITORY); }

    private void editRepository() {
        EditText input = new EditText(this);
        input.setSingleLine(true); input.setText(repository()); input.setPadding(dp(20), dp(12), dp(20), dp(12));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("GitHub 仓库（用户名/仓库名）")
                .setView(input).setPositiveButton("保存", null).setNegativeButton("取消", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String value = input.getText().toString().trim();
            if (!TokenPolicy.validRepository(value)) { input.setError("请输入 用户名/仓库名"); return; }
            preferences.edit().putString("repository", value).apply(); dialog.dismiss();
        }));
        dialog.show();
    }

    private void openBrowser(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception ignored) { toast("请先安装或启用浏览器"); }
    }

    private void setStatus(String title, String message, boolean good) {
        status.setText(title); status.setTextColor(good ? GREEN : INK); detail.setText(message);
    }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    private LinearLayout row() { LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); return row; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); return view;
    }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(radius)); return drawable;
    }
    private Button button(String label, boolean primary) {
        Button button = new Button(this); button.setText(label); button.setTextSize(14); button.setAllCaps(false);
        button.setPadding(dp(4), 0, dp(4), 0);
        button.setTextColor(primary ? Color.WHITE : GREEN); button.setBackground(shape(primary ? GREEN : Color.rgb(225, 235, 229), 12));
        button.setMinHeight(0); button.setMinimumHeight(0); button.setElevation(0); return button;
    }

    @Override protected void onResume() { super.onResume(); resumed = true; web.onResume(); handler.post(poll); }
    @Override protected void onPause() { resumed = false; handler.removeCallbacks(poll); web.onPause(); CookieManager.getInstance().flush(); super.onPause(); }
    @Override protected void onDestroy() {
        generation++; handler.removeCallbacksAndMessages(null); network.shutdownNow();
        web.stopLoading(); web.destroy(); super.onDestroy();
    }
    @Override public void onBackPressed() {
        if (fullscreen) toggleFullscreen();
        else if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
}
