package com.fongmi.android.tv.ui.activity;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.SubscriptionTmdbCredentialStore;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.setting.Setting;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class C16SubscriptionCredentialDeviceTest {

    private static final String FIXTURE_URL = "http://127.0.0.1:18081/config.json";
    private static final String NO_KEY_URL = "http://127.0.0.1:18081/no-key-config.json";
    private static final String INVALID_KEY_URL = "http://127.0.0.1:18081/invalid-key-config.json";
    private String previousTmdbConfig;
    private int previousDetailMode;
    private String previousConfigUrl;
    private int previousConfigId;
    private boolean fixtureLoaded;

    @Before
    public void loadSubscriptionFixture() throws Exception {
        Assume.assumeTrue("C16 subscription credential fixture is not enabled",
                "true".equals(InstrumentationRegistry.getArguments().getString("c16SubscriptionCredentialFixture")));
        previousTmdbConfig = Setting.getTmdbConfig();
        previousDetailMode = Setting.getDetailOpenMode();
        previousConfigUrl = Config.vod().getUrl();
        previousConfigId = Config.vod().getId();
        fixtureLoaded = true;
        Setting.putTmdbConfig("");
        Setting.putDetailOpenMode(Setting.DETAIL_OPEN_ENHANCED);
        SubscriptionTmdbCredentialStore.clear();
        reload(Config.find(FIXTURE_URL, 0).url(FIXTURE_URL).name("C16 subscription credential fixture"), true);
        assertFalse("fixture site did not load", VodConfig.get().getSite("c16_subscription_complete").isEmpty());
        assertTrue("subscription config root key was not accepted",
                SubscriptionTmdbCredentialStore.snapshot(SubscriptionTmdbCredentialStore.currentScope()).isPresent());
    }

    @After
    public void restoreConfiguration() throws Exception {
        assertTestKeyNotPersisted(ApplicationProvider.getApplicationContext());
        SubscriptionTmdbCredentialStore.clear();
        if (!fixtureLoaded) return;
        Setting.putTmdbConfig(previousTmdbConfig);
        Setting.putDetailOpenMode(previousDetailMode);
        if (previousConfigUrl == null || previousConfigUrl.isBlank()) return;
        Config previous = Config.find(previousConfigUrl, 0);
        if (previousConfigId > 0) previous.setId(previousConfigId);
        reload(previous, true);
    }

    @Test
    public void subscriptionConfigRootKeyIsAcceptedAndRendersSourceData() throws Exception {
        Result direct = SiteApi.detailContent("c16_subscription_complete", "c16-subscription-complete-1", true);
        assertFalse(direct.getList().isEmpty());
        assertTrue("subscription config root key was not accepted",
                SubscriptionTmdbCredentialStore.snapshot(SubscriptionTmdbCredentialStore.currentScope()).isPresent());
        assertDetailTextEventually("c16_subscription_complete", "C16 Subscription Complete",
                List.of("Embedded subscription complete", "Subscription Complete Actor"));
    }

    @Test
    public void partialSourceUsesSubscriptionConfigRootKeyAndFallsBackWithoutBreakingUi() {
        assertDetailTextEventually("c16_subscription_partial", "C16 Subscription Partial",
                List.of("Source partial overview", "C16 Subscription Partial"));
    }

    @Test
    public void sameSubscriptionReusesKeyAndSwitchBackReacquiresFromConfig() throws Exception {
        Result first = SiteApi.detailContent("c16_subscription_complete", "c16-subscription-complete-1", true);
        assertFalse(first.getList().isEmpty());
        SubscriptionTmdbCredentialStore.Scope firstScope = SubscriptionTmdbCredentialStore.currentScope();
        assertTrue(SubscriptionTmdbCredentialStore.snapshot(firstScope).isPresent());

        reload(Config.find(FIXTURE_URL, 0).url(FIXTURE_URL).name("C16 subscription credential fixture"), true);
        SubscriptionTmdbCredentialStore.Scope reloaded = SubscriptionTmdbCredentialStore.currentScope();
        assertTrue(SubscriptionTmdbCredentialStore.snapshot(reloaded).isPresent());

        reload(Config.find(NO_KEY_URL, 0).url(NO_KEY_URL).name("C16 no-key fixture"), true);
        assertTrue(SubscriptionTmdbCredentialStore.snapshot(SubscriptionTmdbCredentialStore.currentScope()).isEmpty());

        reload(Config.find(FIXTURE_URL, 0).url(FIXTURE_URL).name("C16 subscription credential fixture"), true);
        SubscriptionTmdbCredentialStore.Scope returned = SubscriptionTmdbCredentialStore.currentScope();
        assertNotEquals(firstScope.getEpoch(), returned.getEpoch());
        assertTrue("switching back did not reacquire the key from the new config response",
                SubscriptionTmdbCredentialStore.snapshot(returned).isPresent());
    }

    @Test
    public void userConfiguredCredentialWinsAndSubscriptionRootKeyIsNotStored() throws Exception {
        Setting.putTmdbConfig("{\"apiKey\":\"user-key-0123456789abcdef\"}");
        SubscriptionTmdbCredentialStore.clear();
        reload(Config.find(FIXTURE_URL, 0).url(FIXTURE_URL).name("C16 subscription credential fixture"), true);

        Result result = SiteApi.detailContent("c16_subscription_complete", "c16-subscription-complete-1", true);

        assertFalse(result.getList().isEmpty());
        assertTrue(SubscriptionTmdbCredentialStore.snapshot(SubscriptionTmdbCredentialStore.currentScope()).isEmpty());
    }

    @Test
    public void invalidRootKeyDoesNotPopulateSubscriptionScope() throws Exception {
        reload(Config.find(INVALID_KEY_URL, 0).url(INVALID_KEY_URL).name("C16 invalid key fixture"), true);
        assertTrue(SubscriptionTmdbCredentialStore.snapshot(SubscriptionTmdbCredentialStore.currentScope()).isEmpty());
    }

    private static void assertDetailTextEventually(String key, String name, List<String> expectedTexts) {
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(context, TmdbDetailActivity.class)
                .putExtra("detail_mode", Setting.DETAIL_OPEN_ENHANCED)
                .putExtra("fusion", false)
                .putExtra("auto_play", false)
                .putExtra("key", key)
                .putExtra("id", key.replace('_', '-') + "-1")
                .putExtra("name", name)
                .putExtra("pic", "")
                .putExtra("mark", "");
        try (ActivityScenario<TmdbDetailActivity> scenario = ActivityScenario.launch(intent)) {
            long deadline = SystemClock.uptimeMillis() + 20_000L;
            List<String> texts = new ArrayList<>();
            while (SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity(activity -> {
                    texts.clear();
                    collectText(activity.findViewById(android.R.id.content), texts);
                });
                if (containsAll(texts, expectedTexts)) {
                    assertFalse("temporary credential path must not show a TMDB key prompt", contains(texts, "请先配置"));
                    return;
                }
                SystemClock.sleep(250L);
            }
            assertTrue("missing UI text for " + key + "; visible=" + texts, containsAll(texts, expectedTexts));
        }
    }

    private static void reload(Config config, boolean required) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        VodConfig.selectConfig(config.save(), new Callback() {
            @Override
            public void success() {
                latch.countDown();
            }

            @Override
            public void error(String msg) {
                latch.countDown();
            }
        });
        if (!latch.await(15, TimeUnit.SECONDS) && required) throw new AssertionError("fixture config timeout");
    }

    private static void assertTestKeyNotPersisted(Context context) throws Exception {
        List<File> roots = new ArrayList<>();
        roots.add(context.getDataDir());
        File external = context.getExternalFilesDir(null);
        if (external != null) roots.add(external);
        for (File root : roots) scanForTestKey(root);
    }

    private static void scanForTestKey(File file) throws Exception {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) scanForTestKey(child);
            return;
        }
        if (file.length() <= 0 || file.length() > 16L * 1024L * 1024L) return;
        byte[] data = new byte[(int) file.length()];
        try (FileInputStream input = new FileInputStream(file)) {
            int offset = 0;
            while (offset < data.length) {
                int read = input.read(data, offset, data.length - offset);
                if (read < 0) break;
                offset += read;
            }
            String text = new String(data, 0, offset, java.nio.charset.StandardCharsets.ISO_8859_1);
            assertFalse("persistent test key found in " + file.getAbsolutePath(), text.contains("0123456789abcdef0123456789abcdef"));
        }
    }

    private static boolean containsAll(List<String> texts, List<String> expectedTexts) {
        for (String expected : expectedTexts) if (!contains(texts, expected)) return false;
        return true;
    }

    private static boolean contains(List<String> texts, String expected) {
        return texts.stream().anyMatch(text -> text != null && text.contains(expected));
    }

    private static void collectText(View view, List<String> texts) {
        if (view == null || view.getVisibility() != View.VISIBLE) return;
        if (view instanceof TextView textView) {
            CharSequence text = textView.getText();
            if (text != null && !text.toString().isBlank()) texts.add(text.toString());
        }
        if (!(view instanceof ViewGroup group)) return;
        for (int i = 0; i < group.getChildCount(); i++) collectText(group.getChildAt(i), texts);
    }
}
