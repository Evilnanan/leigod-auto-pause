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
import android.text.InputType;
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
import android.widget.ScrollView;
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
import java.util.Map;

public final class MainActivity extends Activity {
    private static final int GREEN = Color.rgb(33, 107, 94);
    private static final int INK = Color.rgb(25, 45, 39);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private WebView web;
    private TextView status, detail, origin;
    private Button copy, update, cloudflare;
    private TextView cloudflareStatus;
    private SecureStore secure;
    private boolean cloudflareBusy;
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
            adaptPage();
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
        secure = new SecureStore(getApplicationContext());
        buildUi();
        configureWebView();
        web.loadUrl(TokenPolicy.LOGIN_URL);
        handleCloudflareCallback(getIntent());
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
        TextView name = text("雷神 · Cloudflare", 20, INK);
        name.setTypeface(null, Typeface.BOLD);
        title.addView(name);
        title.addView(text("官网登录 · 一键更新云端凭证", 12, Color.rgb(88, 107, 99)));
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
        LinearLayout cloudActions = row();
        cloudActions.setPadding(0, dp(8), 0, 0);
        update = button("一键更新 Cloudflare", true);
        update.setId(R.id.update_cloudflare);
        update.setOnClickListener(view -> updateCloudflare());
        LinearLayout.LayoutParams updateParams = new LinearLayout.LayoutParams(0, dp(48), 2);
        updateParams.setMarginEnd(dp(8));
        cloudActions.addView(update, updateParams);
        cloudflare = button("连接云端", false);
        cloudflare.setId(R.id.connect_cloudflare);
        cloudflare.setOnClickListener(view -> cloudflareMenu(view));
        cloudActions.addView(cloudflare, new LinearLayout.LayoutParams(0, dp(48), 1));
        root.addView(cloudActions); chromeViews.add(cloudActions);
        cloudflareStatus = text("", 11, Color.rgb(88, 107, 99));
        cloudflareStatus.setId(R.id.cloudflare_status);
        cloudflareStatus.setPadding(0, dp(6), 0, 0);
        cloudflareStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        root.addView(cloudflareStatus); chromeViews.add(cloudflareStatus);
        refreshCloudflareUi();
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
                if (value >= 70) adaptPage();
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
        refreshCloudflareUi();
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
                refreshCloudflareUi();
                switch (state) {
                    case VALID_PAUSED -> setStatus("登录有效 · 时长已暂停", "点击“一键更新 Cloudflare”保存新凭证。", true);
                    case VALID_RUNNING -> setStatus("登录有效 · 时长正在计费", "可以更新云端凭证；App 只检查，不执行暂停。", true);
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
                    .setMessage("当前 Token 未通过验证。可以先重新登录或检查网络，也可手动复制后在 Cloudflare 验证。")
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
        toast("Token 已复制，可在 Cloudflare 更新 LEIGOD_TOKENS");
    }

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("重新打开官网").setOnMenuItemClickListener(item -> { web.loadUrl(TokenPolicy.LOGIN_URL); return true; });
        menu.getMenu().add("Cloudflare 连接设置").setOnMenuItemClickListener(item -> { editCloudflare(); return true; });
        menu.getMenu().add("打开 Cloudflare Secret 设置").setOnMenuItemClickListener(item -> { openCloudflareDashboard(); return true; });
        menu.getMenu().add("清除本机登录记录").setOnMenuItemClickListener(item -> {
            new AlertDialog.Builder(this).setTitle("清除本机登录记录？")
                    .setMessage("清除本 App 内的雷神官网登录数据，不会修改 Cloudflare Secret 或断开云端授权。")
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
                    .setMessage("1. 在下方雷神官网登录，等待登录检查通过。\n2. 首次点击“连接云端”，在系统浏览器登录 Cloudflare 并授权。\n3. 返回后点击“一键更新 Cloudflare”。以后通常只需第 1、3 步。\n\n更新仅替换指定 Worker 的 LEIGOD_TOKENS，其他 Secret 和定时设置不变。此按钮适用于单账号；多账号请复制全部 Token，在云端手动合并保存。\n\n网页登录授权需先创建一次私有 OAuth 客户端，填写公开 Client ID。应用不收集 Cloudflare 密码。提取 Token 不会延长有效期，也不会暂停或恢复时长。\n\n这是个人辅助工具，非雷神或 Cloudflare 官方 App。")
                    .setPositiveButton("知道了", null).show(); return true;
        });
        menu.show();
    }

    private void clearToken() {
        generation++;
        token = ""; verified = false; checking = false;
        copy.setEnabled(false); copy.setAlpha(0.5f);
        refreshCloudflareUi();
    }

    private String account() { return preferences.getString("cf_account", CloudflareConfig.ACCOUNT); }
    private String worker() { return preferences.getString("cf_worker", CloudflareConfig.WORKER); }
    private String clientId() { return preferences.getString("cf_client", CloudflareConfig.CLIENT_ID); }
    private boolean connected() {
        try { return !secure.get("credentials").isEmpty(); } catch (Exception ignored) { return false; }
    }

    private void refreshCloudflareUi() {
        if (update == null) return;
        update.setEnabled(verified && !cloudflareBusy);
        update.setAlpha(update.isEnabled() ? 1f : 0.5f);
        update.setText(cloudflareBusy ? "正在处理…" : "一键更新 Cloudflare");
        cloudflare.setEnabled(!cloudflareBusy);
        cloudflare.setText(connected() ? "云端设置" : "连接云端");
        cloudflareStatus.setText((connected() ? "Cloudflare 已连接 · " : "首次连接需网页登录授权 · ") + worker()
                + "\n单账号更新会替换 LEIGOD_TOKENS；多账号请到云端手动合并。");
    }

    private void cloudflareMenu(View anchor) {
        if (cloudflareBusy) return;
        if (!connected()) { beginCloudflareLogin(false); return; }
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("连接设置").setOnMenuItemClickListener(item -> { editCloudflare(); return true; });
        menu.getMenu().add("重新登录授权").setOnMenuItemClickListener(item -> { beginCloudflareLogin(false); return true; });
        menu.getMenu().add("打开 Secret 设置").setOnMenuItemClickListener(item -> { openCloudflareDashboard(); return true; });
        menu.getMenu().add("断开本机连接").setOnMenuItemClickListener(item -> {
            secure.remove("credentials"); secure.remove("pending");
            preferences.edit().remove("cf_continue_update").apply();
            refreshCloudflareUi(); toast("已删除本机授权凭据；云端 Secret 和定时任务不受影响"); return true;
        });
        menu.show();
    }

    private void openCloudflareDashboard() { openBrowser(CloudflareConfig.dashboard(account(), worker())); }

    private EditText field(LinearLayout form, String label, String value) {
        TextView caption = text(label, 13, INK); caption.setPadding(0, dp(10), 0, 0); form.addView(caption);
        EditText input = new EditText(this); input.setSingleLine(true); input.setText(value);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setAutofillHints((String[]) null); form.addView(input); return input;
    }
    private void editCloudflare() {
        if (cloudflareBusy) { toast("请等待当前请求完成"); return; }
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(20), 0, dp(20), dp(12));
        form.addView(text("首次填写公开 Client ID，之后在浏览器登录授权。账号和 Worker 已预填。", 13, INK));
        EditText client = field(form, "OAuth Client ID（不是 Secret）", clientId()); client.setId(R.id.cloudflare_client);
        EditText account = field(form, "Cloudflare Account ID", account());
        EditText worker = field(form, "Worker 名称", worker());
        ScrollView scroll = new ScrollView(this); scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Cloudflare 连接设置")
                .setView(scroll).setPositiveButton("保存并登录", null).setNeutralButton("打开云端设置", null).setNegativeButton("取消", null).create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> openCloudflareDashboard());
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                String c = client.getText().toString().trim(), a = account.getText().toString().trim(), w = worker.getText().toString().trim();
                if (!CloudflareConfig.validClient(c)) { client.setError("请填写 OAuth 客户端的公开 Client ID"); return; }
                if (!CloudflareConfig.validAccount(a)) { account.setError("请填写有效 Account ID"); return; }
                if (!CloudflareConfig.validWorker(w)) { worker.setError("请输入 Worker 名称，不是网址"); return; }
                if (!c.equals(clientId())) { secure.remove("credentials"); secure.remove("pending"); }
                preferences.edit().putString("cf_client", c).putString("cf_account", a).putString("cf_worker", w).apply();
                dialog.dismiss(); refreshCloudflareUi(); beginCloudflareLogin(false);
            });
        });
        dialog.show();
    }

    private void beginCloudflareLogin(boolean continueUpdate) {
        if (cloudflareBusy) return;
        if (!CloudflareConfig.validClient(clientId())) { editCloudflare(); return; }
        try {
            String state = CloudflareOAuth.random(), verifier = CloudflareOAuth.random();
            secure.put("pending", new JSONObject().put("state", state).put("verifier", verifier)
                    .put("client", clientId()).put("createdAt", System.currentTimeMillis()).toString());
            preferences.edit().putBoolean("cf_continue_update", continueUpdate).apply();
            openBrowser(CloudflareOAuth.authorize(clientId(), state, verifier));
            cloudflareStatus.setText("请在浏览器登录 Cloudflare 并授权，完成后返回 App。");
        } catch (Exception ignored) { toast("无法安全保存登录状态，请检查本机锁屏和系统存储"); }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent); handleCloudflareCallback(intent);
    }
    private void handleCloudflareCallback(Intent intent) {
        Uri data = intent == null ? null : intent.getData();
        if (data == null) return;
        intent.setData(null);
        if (!CloudflareOAuth.isCallback(data.toString()) || cloudflareBusy) return;
        try {
            Map<String, String> values = CloudflareOAuth.parameters(data.toString());
            String saved = secure.get("pending");
            if (saved.isEmpty()) throw new IllegalArgumentException("no pending login");
            JSONObject pending = new JSONObject(saved);
            if (!CloudflareOAuth.pendingIsValid(pending.getLong("createdAt"), System.currentTimeMillis(),
                    pending.getString("client"), clientId(), pending.getString("state"), values.get("state"))) throw new IllegalArgumentException("invalid state");
            secure.remove("pending"); // One use, including denial/error callbacks.
            if (values.containsKey("error")) { toast("Cloudflare 授权未完成，请重新连接"); return; }
            String code = values.get("code");
            if (code == null || code.isEmpty()) throw new IllegalArgumentException("missing code");
            String client = pending.getString("client"), verifier = pending.getString("verifier");
            cloudflareBusy = true; refreshCloudflareUi(); cloudflareStatus.setText("正在完成 Cloudflare 授权…");
            network.execute(() -> {
                boolean success = false;
                try {
                    CloudflareClient.Tokens tokens = new CloudflareClient().exchange(client, code, verifier);
                    secure.put("credentials", tokens.json().toString()); success = true;
                } catch (Exception ignored) { /* No URLs, codes or tokens in logs. */ }
                boolean connected = success;
                handler.post(() -> {
                    if (isDestroyed()) return;
                    cloudflareBusy = false; refreshCloudflareUi();
                    boolean proceed = preferences.getBoolean("cf_continue_update", false);
                    preferences.edit().remove("cf_continue_update").apply();
                    if (connected) {
                        toast("Cloudflare 已连接");
                        if (proceed && verified) updateCloudflare();
                    } else cloudflareStatus.setText("授权未完成。请核对客户端类型、回调地址和权限后重新登录。");
                });
            });
        } catch (Exception ignored) { toast("未接受该登录回调，请从 App 重新发起 Cloudflare 登录"); }
    }

    private void updateCloudflare() {
        if (cloudflareBusy || !verified || token.isEmpty()) return;
        if (!connected()) { beginCloudflareLogin(true); return; }
        String candidate = token, targetAccount = account(), targetWorker = worker(), client = clientId();
        cloudflareBusy = true; refreshCloudflareUi(); cloudflareStatus.setText("正在复查雷神登录并更新 " + targetWorker + "…");
        network.execute(() -> {
            LeigodClient.State fresh = new LeigodClient().check(candidate);
            if (fresh != LeigodClient.State.VALID_PAUSED && fresh != LeigodClient.State.VALID_RUNNING) {
                handler.post(() -> {
                    if (isDestroyed()) return;
                    cloudflareBusy = false; verified = false; refreshCloudflareUi();
                    cloudflareStatus.setText(fresh == LeigodClient.State.LOGIN_REJECTED
                            ? "雷神登录已失效，请重新登录后更新。云端 Secret 未修改。"
                            : "暂时无法验证雷神登录，请检查网络后点“获取 / 检查”。云端 Secret 未修改。");
                });
                return;
            }
            String result;
            try {
                CloudflareClient api = new CloudflareClient();
                CloudflareClient.Tokens credentials = CloudflareClient.Tokens.from(new JSONObject(secure.get("credentials")));
                if (credentials.expiresAt <= System.currentTimeMillis() + 60000) {
                    credentials = api.refresh(client, credentials.refresh); secure.put("credentials", credentials.json().toString());
                }
                try { api.update(credentials.access, targetAccount, targetWorker, candidate); }
                catch (CloudflareClient.Failure failure) {
                    if (failure.problem != CloudflareClient.Problem.AUTH) throw failure;
                    credentials = api.refresh(client, credentials.refresh); secure.put("credentials", credentials.json().toString());
                    api.update(credentials.access, targetAccount, targetWorker, candidate);
                }
                result = "已更新 Cloudflare · " + targetWorker + "\nLEIGOD_TOKENS 已保存并部署；定时设置和其他 Secret 保持不变。";
            } catch (CloudflareClient.Failure failure) {
                result = switch (failure.problem) {
                    case AUTH -> { secure.remove("credentials"); yield "Cloudflare 授权已失效，请重新连接后更新。"; }
                    case PERMISSION -> "没有更新权限。请确认授权包含 Workers Scripts Write，且选择了正确的账号。";
                    case WORKER -> "未找到 Worker，请检查 Account ID 和 Worker 名称。";
                    case NETWORK -> "暂时无法确认保存结果。请检查网络或云端设置后再试。";
                    case API -> "Cloudflare 未确认保存成功，请检查连接设置后重试。";
                };
            } catch (Exception ignored) { result = "本机授权存储无法读取，请重新连接 Cloudflare。"; }
            String message = result;
            handler.post(() -> {
                if (isDestroyed()) return;
                cloudflareBusy = false; refreshCloudflareUi(); cloudflareStatus.setText(message);
            });
        });
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
