package com.evilnanan.leigodtoken;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** The only native API call is a read-only login/status query. */
final class LeigodClient {
    enum State { VALID_PAUSED, VALID_RUNNING, LOGIN_REJECTED, NETWORK_ERROR }
    private final URL endpoint;

    LeigodClient() {
        try {
            endpoint = new URL("https://webapi.leigod.com/api/user/info");
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    // Package-private constructor allows local HTTP fixtures in JVM tests.
    LeigodClient(URL endpoint) { this.endpoint = endpoint; }

    State check(String token) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) endpoint.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Referer", "https://www.leigod.com/");
            connection.setDoOutput(true);
            byte[] body = new JSONObject().put("account_token", token).put("lang", "zh_CN")
                    .toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream out = connection.getOutputStream()) { out.write(body); }
            int status = connection.getResponseCode();
            if (status == 401) return State.LOGIN_REJECTED;
            if (status < 200 || status >= 300) return State.NETWORK_ERROR;
            byte[] data;
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > 1024 * 1024) return State.NETWORK_ERROR;
                    output.write(buffer, 0, count);
                }
                data = output.toByteArray();
            }
            JSONObject result = new JSONObject(new String(data, StandardCharsets.UTF_8));
            int code = result.optInt("code", -1);
            if (code == 0) {
                JSONObject info = result.optJSONObject("data");
                if (info == null || !info.has("pause_status_id")) return State.NETWORK_ERROR;
                return info.getInt("pause_status_id") == 1 ? State.VALID_PAUSED : State.VALID_RUNNING;
            }
            // These are the logout codes handled by the official web frontend.
            if (code == 400006 || code == 400008 || code == 400816 || code == 400007
                    || code == 400334 || code == 400027) return State.LOGIN_REJECTED;
            return State.NETWORK_ERROR;
        } catch (Exception ignored) {
            // Never log exceptions or server bodies containing credentials/user information.
            return State.NETWORK_ERROR;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
