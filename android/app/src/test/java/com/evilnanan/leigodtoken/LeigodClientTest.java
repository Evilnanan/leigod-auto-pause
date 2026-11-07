package com.evilnanan.leigodtoken;

import com.sun.net.httpserver.HttpServer;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class LeigodClientTest {
    private HttpServer server;
    private final AtomicInteger httpCode = new AtomicInteger(200);
    private final AtomicReference<String> response = new AtomicReference<>();
    private final AtomicReference<String> posted = new AtomicReference<>();
    private final AtomicReference<String> method = new AtomicReference<>();
    private final AtomicInteger redirected = new AtomicInteger();
    private LeigodClient client;

    @Before public void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/user/info", exchange -> {
            method.set(exchange.getRequestMethod());
            posted.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            if (httpCode.get() == 302) exchange.getResponseHeaders().set("Location", "/redirected");
            byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(httpCode.get(), bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.createContext("/redirected", exchange -> {
            redirected.incrementAndGet(); exchange.sendResponseHeaders(200, -1); exchange.close();
        });
        server.start();
        client = new LeigodClient(new URL("http://127.0.0.1:" + server.getAddress().getPort() + "/api/user/info"));
    }

    @After public void stop() { server.stop(0); }

    @Test public void queryIsReadOnlyAndReportsBothPauseStates() throws Exception {
        response.set("{\"code\":0,\"data\":{\"pause_status_id\":1}}");
        assertEquals(LeigodClient.State.VALID_PAUSED, client.check("dummy-token"));
        assertEquals("POST", method.get());
        assertEquals("dummy-token", new JSONObject(posted.get()).getString("account_token"));
        assertEquals("zh_CN", new JSONObject(posted.get()).getString("lang"));
        response.set("{\"code\":0,\"data\":{\"pause_status_id\":0}}");
        assertEquals(LeigodClient.State.VALID_RUNNING, client.check("dummy-token"));
    }

    @Test public void distinguishesExpiredLoginFromNetworkAndMalformedResponses() {
        response.set("{\"code\":400006}");
        assertEquals(LeigodClient.State.LOGIN_REJECTED, client.check("dummy-token"));
        response.set("{\"code\":500001}");
        assertEquals(LeigodClient.State.NETWORK_ERROR, client.check("dummy-token"));
        response.set("<html>temporary error</html>");
        assertEquals(LeigodClient.State.NETWORK_ERROR, client.check("dummy-token"));
        response.set("{\"code\":0}");
        assertEquals(LeigodClient.State.NETWORK_ERROR, client.check("dummy-token"));
        httpCode.set(503);
        assertEquals(LeigodClient.State.NETWORK_ERROR, client.check("dummy-token"));
    }

    @Test public void neverForwardsTokensToRedirectTargets() {
        response.set("redirect"); httpCode.set(302);
        assertEquals(LeigodClient.State.NETWORK_ERROR, client.check("dummy-token"));
        assertEquals(0, redirected.get());
    }
}
