package com.evilnanan.leigodtoken;

import android.webkit.WebView;
import android.webkit.WebSettings;
import android.widget.Button;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class TokenWebViewTest {
    @Test public void nativeScreenUsesAnIsolatedDesktopWebView() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                WebView web = activity.findViewById(R.id.official_web);
                assertTrue(web.getSettings().getJavaScriptEnabled());
                assertTrue(web.getSettings().getDomStorageEnabled());
                assertFalse(web.getSettings().getAllowFileAccess());
                assertFalse(web.getSettings().getAllowContentAccess());
                assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW, web.getSettings().getMixedContentMode());
                assertFalse(web.getSettings().getUserAgentString().contains("Android"));
                Button copy = activity.findViewById(R.id.copy_token);
                assertFalse(copy.isEnabled());
                web.stopLoading();
            });
        }
    }

    @Test public void extractionRunsOnOfficialOriginAndHandlesStorage() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                WebView web = activity.findViewById(R.id.official_web);
                web.stopLoading();
                web.loadDataWithBaseURL("https://www.leigod.com/", "<html><body>Local test fixture</body></html>", "text/html", "UTF-8", null);
            });
            waitForOrigin(scenario);
            JSONObject missing = extract(scenario, "localStorage.removeItem('account_token');");
            assertEquals("missing", missing.getString("status"));
            JSONObject found = extract(scenario, "localStorage.setItem('account_token', JSON.stringify({account_token:'dummy-test-token'}));");
            assertEquals("dummy-test-token", found.getString("token"));
            JSONObject broken = extract(scenario, "localStorage.setItem('account_token', '{broken');");
            assertEquals("invalid_storage", broken.getString("status"));
        }
    }

    private void waitForOrigin(ActivityScenario<MainActivity> scenario) throws Exception {
        for (int attempt = 0; attempt < 30; attempt++) {
            AtomicReference<String> origin = new AtomicReference<>();
            CountDownLatch done = new CountDownLatch(1);
            scenario.onActivity(activity -> ((WebView) activity.findViewById(R.id.official_web))
                    .evaluateJavascript("location.origin", value -> { origin.set(value); done.countDown(); }));
            assertTrue(done.await(5, TimeUnit.SECONDS));
            if ("\"https://www.leigod.com\"".equals(origin.get())) return;
            Thread.sleep(100);
        }
        fail("Local official-origin fixture did not load");
    }

    private JSONObject extract(ActivityScenario<MainActivity> scenario, String setup) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();
        // Remove the dummy immediately inside the same JS turn, before app polling.
        String script = "(function(){" + setup + "var result=" + TokenPolicy.READ_TOKEN
                + ";localStorage.removeItem('account_token');return result;})()";
        scenario.onActivity(activity -> ((WebView) activity.findViewById(R.id.official_web))
                .evaluateJavascript(script, value -> { result.set(value); done.countDown(); }));
        assertTrue(done.await(10, TimeUnit.SECONDS));
        return new JSONObject((String) new JSONTokener(result.get()).nextValue());
    }
}
