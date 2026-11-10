package com.evilnanan.leigodtoken;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Button;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class CloudflareStorageTest {
    @Test public void credentialsAreEncryptedAtRestAndCanBeDeleted() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SecureStore store = new SecureStore(context);
        try {
            store.put("test_record", "private-refresh-token");
            String saved = context.getSharedPreferences("cloudflare_secure", Context.MODE_PRIVATE).getString("test_record", "");
            assertFalse(saved.contains("private-refresh-token"));
            assertEquals("private-refresh-token", store.get("test_record"));
            // Each write gets a fresh IV.
            store.put("test_record", "private-refresh-token");
            assertNotEquals(saved, context.getSharedPreferences("cloudflare_secure", Context.MODE_PRIVATE).getString("test_record", ""));
            store.remove("test_record"); assertEquals("", store.get("test_record"));
        } finally { store.remove("test_record"); }
    }
    @Test public void unrequestedCallbackCannotConnectOrEnableSecretUpdates() {
        // The activity scrubs callback data. Deliver after launch so ActivityScenario's
        // launch-intent matcher does not confuse that scrubbing with a missing lifecycle event.
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.onNewIntent(new Intent(activity.getIntent())
                        .setData(Uri.parse(CloudflareConfig.CALLBACK + "?code=spoof&state=spoof")));
                assertFalse(((Button) activity.findViewById(R.id.update_cloudflare)).isEnabled());
                assertNotNull(activity.findViewById(R.id.connect_cloudflare));
            });
        }
    }
}
