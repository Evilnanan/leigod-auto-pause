package com.evilnanan.leigodtoken;

import android.webkit.WebView;
import android.webkit.WebSettings;
import android.widget.Button;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
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
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
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
                web.loadDataWithBaseURL("https://www.leigod.com/", "<html><body>Local test fixture</body></html>", "text/html", "UTF-8", "https://www.leigod.com/");
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

    @Test public void loginCodeButtonFitsAndReceivesRealTouch() throws Exception {
        String fixture;
        try (InputStream in = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("fixed-login.html");
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            fixture = new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                WebView web = activity.findViewById(R.id.official_web); web.stopLoading();
                web.loadDataWithBaseURL("https://www.leigod.com/", fixture, "text/html", "UTF-8", "https://www.leigod.com/");
            });
            waitForOrigin(scenario);
            String metricsScript = "JSON.stringify((function(){var e=document.getElementById('send-code');var r=e.getBoundingClientRect();return {adapted:!!document.querySelector('.token-helper-login-shell'),x:r.x,y:r.y,width:r.width,height:r.height,viewport:innerWidth}})())";
            JSONObject metrics = null;
            for (int i=0;i<40;i++) {
                metrics = new JSONObject((String)new JSONTokener(evaluate(scenario, metricsScript)).nextValue());
                if (metrics.getBoolean("adapted")) break;
                Thread.sleep(100);
            }
            assertNotNull(metrics); assertTrue("Adapter not installed; native URL is " + nativeUrl(scenario) + "; metrics=" + metrics,metrics.getBoolean("adapted"));
            assertTrue(metrics.getDouble("x")>=0);
            assertTrue(metrics.getDouble("x")+metrics.getDouble("width")<=metrics.getDouble("viewport")+1);
            evaluate(scenario, "document.getElementById('send-code').scrollIntoView({block:'center'})");
            JSONObject point = new JSONObject((String)new JSONTokener(evaluate(scenario,
                    "JSON.stringify((function(){var r=document.getElementById('send-code').getBoundingClientRect();return{x:r.x+r.width/2-visualViewport.offsetLeft,y:r.y+r.height/2-visualViewport.offsetTop}})())")).nextValue());
            AtomicReference<float[]> screenPoint = new AtomicReference<>();
            scenario.onActivity(activity -> {
                WebView web = activity.findViewById(R.id.official_web);
                int[] location = new int[2]; web.getLocationOnScreen(location);
                screenPoint.set(new float[]{location[0]+(float)point.optDouble("x")*web.getScale(),
                        location[1]+(float)point.optDouble("y")*web.getScale()});
            });
            float[] xy = screenPoint.get(); long now = SystemClock.uptimeMillis();
            MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, xy[0],xy[1],0);
            InstrumentationRegistry.getInstrumentation().sendPointerSync(down);
            SystemClock.sleep(60);
            MotionEvent up = MotionEvent.obtain(now, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP,xy[0],xy[1],0);
            InstrumentationRegistry.getInstrumentation().sendPointerSync(up);
            down.recycle(); up.recycle();
            assertEquals("true", evaluate(scenario,"window.fixtureCodeClicked === true"));

            float before = ((Number) new JSONTokener(evaluate(scenario,"visualViewport.scale")).nextValue()).floatValue();
            scenario.onActivity(activity -> activity.findViewById(R.id.zoom_in).performClick());
            boolean zoomed = false;
            for (int i=0;i<30;i++) {
                float scale = ((Number)new JSONTokener(evaluate(scenario,"visualViewport.scale")).nextValue()).floatValue();
                if (scale > before + 0.01f) { zoomed = true; break; }
                Thread.sleep(100);
            }
            assertTrue("Native zoom button must override the site's zoom lock",zoomed);
            scenario.onActivity(activity -> {
                activity.findViewById(R.id.fullscreen_web).performClick();
                View statusCard = (View) activity.findViewById(R.id.token_status).getParent();
                assertEquals(View.GONE,statusCard.getVisibility());
                activity.onBackPressed();
                assertEquals(View.VISIBLE,((View)activity.findViewById(R.id.token_status).getParent()).getVisibility());
            });
        }
    }

    private String evaluate(ActivityScenario<MainActivity> scenario, String script) throws Exception {
        AtomicReference<String> result = new AtomicReference<>(); CountDownLatch done = new CountDownLatch(1);
        scenario.onActivity(activity -> ((WebView) activity.findViewById(R.id.official_web))
                .evaluateJavascript(script,value -> {result.set(value); done.countDown();}));
        assertTrue(done.await(10,TimeUnit.SECONDS)); return result.get();
    }

    private String nativeUrl(ActivityScenario<MainActivity> scenario) {
        AtomicReference<String> url = new AtomicReference<>();
        scenario.onActivity(activity -> url.set(((WebView) activity.findViewById(R.id.official_web)).getUrl()));
        return url.get();
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
