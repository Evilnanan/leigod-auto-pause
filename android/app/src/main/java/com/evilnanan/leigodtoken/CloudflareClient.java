package com.evilnanan.leigodtoken;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Talks directly to official Cloudflare endpoints. No app backend or dashboard cookies. */
final class CloudflareClient {
    enum Problem { AUTH, PERMISSION, WORKER, NETWORK, API }
    static final class Failure extends Exception {
        final Problem problem;
        Failure(Problem problem) { super(problem.name()); this.problem = problem; }
    }
    static final class Tokens {
        final String access, refresh;
        final long expiresAt;
        Tokens(String access, String refresh, long expiresAt) { this.access = access; this.refresh = refresh; this.expiresAt = expiresAt; }
        JSONObject json() throws Exception {
            return new JSONObject().put("access", access).put("refresh", refresh).put("expiresAt", expiresAt);
        }
        static Tokens from(JSONObject json) throws Exception {
            return new Tokens(json.getString("access"), json.getString("refresh"), json.getLong("expiresAt"));
        }
    }
    private final String apiBase, tokenEndpoint;
    CloudflareClient() { this("https://api.cloudflare.com/client/v4", CloudflareConfig.TOKEN_ENDPOINT); }
    CloudflareClient(String apiBase, String tokenEndpoint) { this.apiBase = apiBase; this.tokenEndpoint = tokenEndpoint; }

    Tokens exchange(String clientId, String code, String verifier) throws Failure {
        return token("grant_type=authorization_code&client_id=" + CloudflareOAuth.encode(clientId)
                + "&code=" + CloudflareOAuth.encode(code) + "&redirect_uri=" + CloudflareOAuth.encode(CloudflareConfig.CALLBACK)
                + "&code_verifier=" + CloudflareOAuth.encode(verifier), "");
    }
    Tokens refresh(String clientId, String refresh) throws Failure {
        if (refresh.isEmpty()) throw new Failure(Problem.AUTH);
        return token("grant_type=refresh_token&client_id=" + CloudflareOAuth.encode(clientId)
                + "&refresh_token=" + CloudflareOAuth.encode(refresh), refresh);
    }
    private Tokens token(String body, String oldRefresh) throws Failure {
        JSONObject data = request(tokenEndpoint, "POST", null, body, "application/x-www-form-urlencoded");
        String access = data.optString("access_token", "");
        String refresh = data.optString("refresh_token", oldRefresh);
        int lifetime = data.optInt("expires_in", 0);
        if (access.isEmpty() || lifetime <= 0 || !"Bearer".equalsIgnoreCase(data.optString("token_type"))) throw new Failure(Problem.API);
        return new Tokens(access, refresh, System.currentTimeMillis() + Math.min((long) lifetime, 31536000L) * 1000);
    }
    void update(String access, String account, String worker, String token) throws Failure {
        if (!CloudflareConfig.validAccount(account) || !CloudflareConfig.validWorker(worker)
                || token.trim().isEmpty() || token.length() > 16384) throw new Failure(Problem.API);
        String endpoint = apiBase + "/accounts/" + account + "/workers/scripts/" + worker + "/secrets";
        // Verify the Worker exists before writing; never create an empty Worker by mistake.
        JSONObject existing = request(endpoint, "GET", access, null, null);
        if (!existing.optBoolean("success")) throw new Failure(Problem.API);
        try {
            String body = new JSONObject().put("name", "LEIGOD_TOKENS").put("type", "secret_text").put("text", token).toString();
            JSONObject result = request(endpoint, "PUT", access, body, "application/json; charset=utf-8");
            if (!result.optBoolean("success")) throw new Failure(Problem.API);
        } catch (Failure failure) { throw failure; }
        catch (Exception ignored) { throw new Failure(Problem.API); }
    }
    private JSONObject request(String url, String method, String bearer, String body, String type) throws Failure {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15000); connection.setReadTimeout(20000);
            connection.setRequestMethod(method);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "LeigodCloudflareHelper/0.2.0");
            if (bearer != null) connection.setRequestProperty("Authorization", "Bearer " + bearer);
            if (body != null) {
                connection.setDoOutput(true); connection.setRequestProperty("Content-Type", type);
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8); connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream out = connection.getOutputStream()) { out.write(bytes); }
            }
            int status = connection.getResponseCode();
            if (status == 401) throw new Failure(Problem.AUTH);
            if (status == 403) throw new Failure(Problem.PERMISSION);
            if (status == 404) throw new Failure(Problem.WORKER);
            if (status >= 300 && status < 400) throw new Failure(Problem.NETWORK);
            InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            if (stream == null) throw new Failure(Problem.NETWORK);
            byte[] bytes;
            try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096]; int count;
                while ((count = in.read(buffer)) != -1) {
                    if (out.size() + count > 1024 * 1024) throw new Failure(Problem.API);
                    out.write(buffer, 0, count);
                }
                bytes = out.toByteArray();
            }
            JSONObject result = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            if ("invalid_grant".equals(result.optString("error")) || "invalid_token".equals(result.optString("error"))) throw new Failure(Problem.AUTH);
            if (status < 200 || status >= 300) throw new Failure(status >= 500 || status == 429 ? Problem.NETWORK : Problem.API);
            return result;
        } catch (Failure failure) { throw failure; }
        catch (Exception ignored) { throw new Failure(Problem.NETWORK); }
        finally { if (connection != null) connection.disconnect(); }
    }
}
