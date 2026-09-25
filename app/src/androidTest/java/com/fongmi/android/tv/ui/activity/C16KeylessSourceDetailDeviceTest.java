package com.fongmi.android.tv.ui.activity;

import static org.junit.Assert.assertFalse;
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

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.setting.Setting;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class C16KeylessSourceDetailDeviceTest {

    private static final String FIXTURE_URL = "http://127.0.0.1:18080/config.json";
    private String previousTmdbConfig;
    private int previousDetailMode;
    private String previousConfigUrl;
    private int previousConfigId;
    private boolean fixtureLoaded;

    @Before
    public void loadKeylessFixture() throws Exception {
        Assume.assumeTrue("C16 keyless fixture is not enabled",
                "true".equals(InstrumentationRegistry.getArguments().getString("c16KeylessFixture")));
        previousTmdbConfig = Setting.getTmdbConfig();
        previousDetailMode = Setting.getDetailOpenMode();
        previousConfigUrl = Config.vod().getUrl();
        previousConfigId = Config.vod().getId();
        fixtureLoaded = true;
        Setting.putTmdbConfig("");

        Config fixture = Config.find(FIXTURE_URL, 0).url(FIXTURE_URL).name("C16 keyless fixture");
        reload(fixture, true);
        assertFalse("fixture site did not load", VodConfig.get().getSite("c16_keyless_complete").isEmpty());
    }

    @After
    public void restoreConfiguration() throws Exception {
        if (!fixtureLoaded) return;
        Setting.putTmdbConfig(previousTmdbConfig);
        Setting.putDetailOpenMode(previousDetailMode);
        if (previousConfigUrl == null || previousConfigUrl.isBlank()) return;
        Config previous = Config.find(previousConfigUrl, 0);
        if (previousConfigId > 0) previous.setId(previousConfigId);
        reload(previous, false);
    }

    @Test
    public void completePayloadRendersStandaloneDetailWithoutKey() {
        Setting.putDetailOpenMode(Setting.DETAIL_OPEN_ENHANCED);
        assertTextEventually(
                TmdbDetailActivity.class,
                "c16_keyless_complete",
                "C16 Keyless Complete",
                List.of("Keyless Complete TMDB", "Embedded keyless overview", "Keyless Actor"),
                true);
    }

    @Test
    public void completePayloadRendersNativeEnhancedDetailWithoutKey() {
        Setting.putDetailOpenMode(Setting.DETAIL_OPEN_ORIGINAL_ENHANCED);
        assertTextEventually(
                VideoActivity.class,
                "c16_keyless_complete",
                "C16 Keyless Complete",
                List.of("Keyless Complete TMDB", "Embedded keyless overview", "Keyless Actor"),
                true);
    }

    @Test
    public void legacyPayloadFallsBackToNativeWithoutKeyPrompt() {
        Setting.putDetailOpenMode(Setting.DETAIL_OPEN_ENHANCED);
        assertTextEventually(
                VideoActivity.class,
                "c16_keyless_legacy",
                "Fight Club",
                List.of("Fight Club", "line-a", "正片"),
                false);
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

    private static void assertTextEventually(Class<? extends android.app.Activity> activityClass, String key, String name,
                                             List<String> expectedTexts, boolean sourceTmdb) {
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(context, activityClass)
                .putExtra("detail_mode", sourceTmdb ? Setting.getDetailOpenMode() : Setting.DETAIL_OPEN_ENHANCED)
                .putExtra("fusion", false)
                .putExtra("auto_play", false)
                .putExtra("key", key)
                .putExtra("id", key.replace('_', '-') + "-1")
                .putExtra("name", name)
                .putExtra("pic", "")
                .putExtra("mark", "");
        try (ActivityScenario<?> scenario = ActivityScenario.launch(intent)) {
            long deadline = SystemClock.uptimeMillis() + 20_000L;
            List<String> texts = new ArrayList<>();
            while (SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity(activity -> {
                    texts.clear();
                    collectText(activity.findViewById(android.R.id.content), texts);
                });
                if (containsAll(texts, expectedTexts)) {
                    assertFalse("source-only page must not show a TMDB key prompt", contains(texts, "请先配置"));
                    return;
                }
                SystemClock.sleep(250L);
            }
            assertTrue("missing UI text for " + key + "; visible=" + texts, containsAll(texts, expectedTexts));
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
