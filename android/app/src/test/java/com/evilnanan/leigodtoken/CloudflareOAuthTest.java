package com.evilnanan.leigodtoken;

import org.junit.Test;
import static org.junit.Assert.*;

public class CloudflareOAuthTest {
    @Test public void pkceMatchesRfc7636VectorAndUsesFreshRandomState() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                CloudflareOAuth.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"));
        String first = CloudflareOAuth.random(), second = CloudflareOAuth.random();
        assertTrue(first.matches("[A-Za-z0-9_-]{43}")); assertNotEquals(first, second);
        String url = CloudflareOAuth.authorize("public-client-id", first, second);
        assertTrue(url.startsWith("https://dash.cloudflare.com/oauth2/auth?"));
        assertTrue(url.contains("code_challenge_method=S256"));
        assertFalse(url.contains(second)); assertFalse(url.contains("client_secret"));
        assertTrue(CloudflareOAuth.matchesState(first, first));
        assertFalse(CloudflareOAuth.matchesState(first, second));
        assertFalse(CloudflareOAuth.matchesState("", ""));
    }
    @Test public void callbacksRejectLookalikeHostsAlternatePortsFragmentsAndDuplicateParameters() throws Exception {
        assertTrue(CloudflareOAuth.isCallback(CloudflareConfig.CALLBACK + "?code=abc&state=123"));
        assertTrue(CloudflareOAuth.isCallback(CloudflareConfig.APP_CALLBACK + "?code=abc&state=123"));
        for (String bad : new String[]{
                CloudflareConfig.CALLBACK + "/evil", CloudflareConfig.CALLBACK + "#code=abc",
                "http://leigod-auto-pause.evilnanan.workers.dev/oauth/callback",
                "https://leigod-auto-pause.evilnanan.workers.dev.attacker.test/oauth/callback",
                "https://leigod-auto-pause.evilnanan.workers.dev:443/oauth/callback",
                "com.evilnanan.leigodtoken://attacker/callback"}) assertFalse(bad, CloudflareOAuth.isCallback(bad));
        assertEquals("a+b&c", CloudflareOAuth.parameters(CloudflareConfig.CALLBACK + "?code=a%2Bb%26c&state=x").get("code"));
        assertThrows(IllegalArgumentException.class, () -> CloudflareOAuth.parameters(CloudflareConfig.CALLBACK + "?state=x&state=y"));
    }
    @Test public void configurationCannotInjectPathsOrChangeTheCloudflareHost() {
        assertTrue(CloudflareConfig.validAccount(CloudflareConfig.ACCOUNT));
        assertTrue(CloudflareConfig.validWorker("leigod-auto-pause"));
        assertFalse(CloudflareConfig.validAccount("../../other"));
        assertFalse(CloudflareConfig.validWorker("worker/secrets"));
        assertFalse(CloudflareConfig.validClient("client&redirect_uri=evil"));
        assertThrows(IllegalArgumentException.class, () -> CloudflareConfig.dashboard("evil.test", "worker"));
    }
    @Test public void pendingLoginExpiresAndMustBelongToTheSameClientAndState() {
        String state = CloudflareOAuth.random();
        assertTrue(CloudflareOAuth.pendingIsValid(1000, 2000, "client", "client", state, state));
        assertFalse(CloudflareOAuth.pendingIsValid(1000, 601001, "client", "client", state, state));
        assertFalse(CloudflareOAuth.pendingIsValid(2000, 1000, "client", "client", state, state));
        assertFalse(CloudflareOAuth.pendingIsValid(1000, 2000, "other", "client", state, state));
        assertFalse(CloudflareOAuth.pendingIsValid(1000, 2000, "client", "client", state, "spoofed"));
    }
}
