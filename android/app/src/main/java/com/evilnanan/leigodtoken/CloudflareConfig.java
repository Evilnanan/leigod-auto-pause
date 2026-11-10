package com.evilnanan.leigodtoken;

final class CloudflareConfig {
    static final String ACCOUNT = "88d3a93ffffffca40c129d87c9a298cb";
    static final String WORKER = "leigod-auto-pause";
    static final String CLIENT_ID = "cd13467206cfc2cb1e53ec38da2c1ca5";
    static final String CALLBACK = "https://leigod-auto-pause.evilnanan.workers.dev/oauth/callback";
    static final String APP_CALLBACK = "com.evilnanan.leigodtoken://oauth/callback";
    static final String AUTHORIZE = "https://dash.cloudflare.com/oauth2/auth";
    static final String TOKEN_ENDPOINT = "https://dash.cloudflare.com/oauth2/token";
    static final String WORKER_SCOPE = "workers-scripts.edit";
    static final String SCOPES = WORKER_SCOPE + " offline_access";

    static boolean validAccount(String value) { return value != null && value.matches("[a-fA-F0-9]{24,64}"); }
    static boolean validWorker(String value) { return value != null && value.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,62}"); }
    static boolean validClient(String value) { return value != null && value.matches("[a-zA-Z0-9_-]{8,128}"); }
    static String dashboard(String account, String worker) {
        if (!validAccount(account) || !validWorker(worker)) throw new IllegalArgumentException("invalid target");
        return "https://dash.cloudflare.com/" + account + "/workers/services/view/" + worker + "/production/settings";
    }
}
