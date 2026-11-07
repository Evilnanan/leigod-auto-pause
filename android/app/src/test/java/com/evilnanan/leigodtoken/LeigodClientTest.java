package com.evilnanan.leigodtoken;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class LeigodClientTest {
    private MockWebServer server;
    private LeigodClient client;

    @Before public void start() throws Exception {
        server = new MockWebServer(); server.start();
        client = new LeigodClient(server.url("/api/user/info").url());
    }

    @After public void stop() throws Exception { server.shutdown(); }

    @Test public void queryIsReadOnlyAndReportsBothPauseStates() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"code\":0,\"data\":{\"pause_status_id\":1}}"));
        assertEquals(LeigodClient.State.VALID_PAUSED, client.check("dummy-token"));
        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/api/user/info", request.getPath());
        JSONObject posted = new JSONObject(request.getBody().readUtf8());
        assertEquals("dummy-token", posted.getString("account_token"));
        assertEquals("zh_CN", posted.getString("lang"));
        server.enqueue(new MockResponse().setBody("{\"code\":0,\"data\":{\"pause_status_id\":0}}"));
        assertEquals(LeigodClient.State.VALID_RUNNING, client.check("dummy-token"));
    }

    @Test public void distinguishesExpiredLoginFromNetworkAndMalformedResponses() {
        server.enqueue(new MockResponse().setBody("{\"code\":400006}"));
        assertEquals(LeigodClient.State.LOGIN_REJECTED, client.check("dummy-token"));
        for (String body : new String[] {"{\"code\":500001}", "<html>temporary error</html>", "{\"code\":0}"}) {
            server.enqueue(new MockResponse().setBody(body));
            assertEquals(LeigodClient.State.NETWORK_ERROR, client.check("dummy-token"));
        }
        server.enqueue(new MockResponse().setResponseCode(503));
        assertEquals(LeigodClient.State.NETWORK_ERROR, client.check("dummy-token"));
    }

    @Test public void neverForwardsTokensToRedirectTargets() {
        server.enqueue(new MockResponse().setResponseCode(302).addHeader("Location", server.url("/redirected")));
        server.enqueue(new MockResponse().setBody("unexpected redirect target"));
        assertEquals(LeigodClient.State.NETWORK_ERROR, client.check("dummy-token"));
        assertEquals(1, server.getRequestCount());
    }
}
