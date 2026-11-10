package com.evilnanan.leigodtoken;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

public class CloudflareClientTest {
    private MockWebServer server;
    private CloudflareClient client;
    @Before public void start() throws Exception {
        server = new MockWebServer(); server.start();
        client = new CloudflareClient(server.url("/client/v4").toString(), server.url("/oauth2/token").toString());
    }
    @After public void stop() throws Exception { server.shutdown(); }
    private MockResponse tokens(String extra) {
        return new MockResponse().setBody("{\"access_token\":\"fake-access\",\"token_type\":\"Bearer\",\"expires_in\":3600" + extra + "}");
    }
    @Test public void exchangesPkceCodeDirectlyWithoutAClientSecretAndRefreshesWithRotation() throws Exception {
        server.enqueue(tokens(",\"refresh_token\":\"fake-refresh\""));
        CloudflareClient.Tokens first = client.exchange("public-client", "code&=value", "private-verifier");
        assertEquals("fake-refresh", first.refresh);
        RecordedRequest request = server.takeRequest();
        assertEquals("/oauth2/token", request.getPath());
        assertNull(request.getHeader("Authorization"));
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("code=code%26%3Dvalue")); assertTrue(body.contains("code_verifier=private-verifier"));
        assertFalse(body.contains("client_secret"));
        server.enqueue(tokens(",\"refresh_token\":\"rotated\""));
        assertEquals("rotated", client.refresh("public-client", first.refresh).refresh);
        request = server.takeRequest(); assertTrue(request.getBody().readUtf8().contains("grant_type=refresh_token"));
        server.enqueue(tokens(""));
        assertEquals("retained", client.refresh("public-client", "retained").refresh);
    }
    @Test public void updatesOnlyTheNamedSecretAfterConfirmingWorkerExists() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"success\":true,\"result\":[{\"name\":\"PUSHPLUS_TOKEN\"}]}"));
        server.enqueue(new MockResponse().setBody("{\"success\":true,\"result\":{\"name\":\"LEIGOD_TOKENS\"}}"));
        client.update("fake-access", CloudflareConfig.ACCOUNT, "leigod-auto-pause", "dummy-leigod-token");
        RecordedRequest read = server.takeRequest(), write = server.takeRequest();
        assertEquals("GET", read.getMethod()); assertEquals("PUT", write.getMethod());
        assertEquals(read.getPath(), write.getPath());
        assertTrue(write.getPath().endsWith("/workers/scripts/leigod-auto-pause/secrets"));
        assertEquals("Bearer fake-access", write.getHeader("Authorization"));
        JSONObject data = new JSONObject(write.getBody().readUtf8());
        assertEquals(3, data.length()); assertEquals("LEIGOD_TOKENS", data.getString("name"));
        assertEquals("secret_text", data.getString("type")); assertEquals("dummy-leigod-token", data.getString("text"));
    }
    @Test public void missingWorkersAndAuthorizationErrorsNeverPerformWrites() {
        for (int status : new int[]{401,403,404}) {
            server.enqueue(new MockResponse().setResponseCode(status));
            CloudflareClient.Failure failure = assertThrows(CloudflareClient.Failure.class,
                    () -> client.update("fake-access", CloudflareConfig.ACCOUNT, "worker", "dummy-token"));
            assertEquals(status == 401 ? CloudflareClient.Problem.AUTH : status == 403 ? CloudflareClient.Problem.PERMISSION : CloudflareClient.Problem.WORKER, failure.problem);
        }
        assertEquals(3, server.getRequestCount());
    }
    @Test public void redirectsNeverReceiveAuthorizationOrTokenPayloads() {
        server.enqueue(new MockResponse().setResponseCode(302).addHeader("Location", server.url("/attacker")));
        CloudflareClient.Failure failure = assertThrows(CloudflareClient.Failure.class,
                () -> client.exchange("public-client", "dummy-code", "dummy-verifier"));
        assertEquals(CloudflareClient.Problem.NETWORK, failure.problem);
        assertEquals(1, server.getRequestCount());
    }
    @Test public void rejectedRefreshAndMalformedSuccessCannotBecomeSavedCredentials() {
        server.enqueue(new MockResponse().setResponseCode(400).setBody("{\"error\":\"invalid_grant\",\"error_description\":\"private value\"}"));
        CloudflareClient.Failure failure = assertThrows(CloudflareClient.Failure.class,
                () -> client.refresh("public-client", "dummy-refresh"));
        assertEquals(CloudflareClient.Problem.AUTH, failure.problem); assertEquals("AUTH", failure.getMessage());
        server.enqueue(new MockResponse().setBody("{\"access_token\":\"missing-expiry-and-type\"}"));
        assertThrows(CloudflareClient.Failure.class, () -> client.exchange("public-client", "code", "verifier"));
    }
}
