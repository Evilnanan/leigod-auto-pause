package com.evilnanan.leigodtoken;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

final class CloudflareOAuth {
    static String random() {
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    static String challenge(String verifier) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
    static String encode(String value) {
        try { return URLEncoder.encode(value, "UTF-8"); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
    static String authorize(String clientId, String state, String verifier) {
        if (!CloudflareConfig.validClient(clientId)) throw new IllegalArgumentException("invalid client");
        return CloudflareConfig.AUTHORIZE + "?response_type=code&client_id=" + encode(clientId)
                + "&redirect_uri=" + encode(CloudflareConfig.CALLBACK) + "&state=" + encode(state)
                + "&scope=" + encode(CloudflareConfig.SCOPES)
                + "&code_challenge_method=S256&code_challenge=" + challenge(verifier);
    }
    static boolean isCallback(String value) {
        try {
            URI uri = new URI(value);
            if (uri.getRawFragment() != null || uri.getRawUserInfo() != null || uri.getPort() != -1) return false;
            String base = new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), null, null).toString();
            return CloudflareConfig.CALLBACK.equals(base) || CloudflareConfig.APP_CALLBACK.equals(base);
        } catch (Exception ignored) { return false; }
    }
    static Map<String, String> parameters(String url) throws Exception {
        if (!isCallback(url)) throw new IllegalArgumentException("wrong callback");
        Map<String, String> values = new HashMap<>();
        String query = new URI(url).getRawQuery();
        if (query == null || query.length() > 8192) throw new IllegalArgumentException("missing query");
        for (String item : query.split("&")) {
            String[] pair = item.split("=", 2);
            String key = URLDecoder.decode(pair[0], "UTF-8");
            String value = pair.length == 2 ? URLDecoder.decode(pair[1], "UTF-8") : "";
            if (values.put(key, value) != null) throw new IllegalArgumentException("duplicate parameter");
        }
        return values;
    }
    static boolean matchesState(String expected, String actual) {
        return expected != null && actual != null && expected.length() >= 32
                && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
    static boolean pendingIsValid(long createdAt, long now, String pendingClient, String currentClient,
                                  String expectedState, String receivedState) {
        long age = now - createdAt;
        return age >= 0 && age <= 10 * 60 * 1000 && currentClient.equals(pendingClient)
                && matchesState(expectedState, receivedState);
    }
}
