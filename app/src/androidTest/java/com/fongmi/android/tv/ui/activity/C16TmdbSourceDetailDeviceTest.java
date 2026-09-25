package com.fongmi.android.tv.ui.activity;

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

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.setting.Setting;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class C16TmdbSourceDetailDeviceTest {

    @Before
    public void loadFixtureSource() {
        Assume.assumeTrue("C16 fixture source is not configured", isFixtureConfigured());
        VodConfig.get().ensureLoaded();
        long deadline = SystemClock.uptimeMillis() + 15_000L;
        while (SystemClock.uptimeMillis() < deadline) {
            if (!VodConfig.get().getSite("c16_t4_complete").isEmpty()) return;
            SystemClock.sleep(250L);
        }
        assertTrue("fixture VOD config did not load", !VodConfig.get().getSite("c16_t4_complete").isEmpty());
    }

    private static boolean isFixtureConfigured() {
        return "http://127.0.0.1:18080/config.json".equals(Config.vod().getUrl());
    }

    @Test
    public void completeT4RendersEmbeddedCoreCreditsAndImages() {
        assertDetailEventually(
                "c16_t4_complete",
                "c16-t4-complete-1",
                "C16 Complete T4",
                List.of("C16 Complete T4", "Embedded complete overview", "Embedded Actor")
        );
    }

    @Test
    public void partialT4KeepsSourceFieldsWhileTmdbFillsGaps() {
        assertDetailEventually(
                "c16_t4_partial",
                "c16-t4-partial-1",
                "C16 Partial T4",
                List.of("C16 Partial T4", "Source partial overview must win")
        );
    }

    @Test
    public void legacySourceWithoutTmdbStillRendersOrdinaryDetail() {
        assertDetailEventually(
                "c16_legacy",
                "c16-legacy-1",
                "Fight Club",
                List.of("Fight Club", "line-a", "正片")
        );
    }

    @Test
    public void completeT3RendersEmbeddedCoreCreditsAndImages() {
        assertDetailEventually(
                "c16_t3_complete",
                "c16-t3-complete-1",
                "C16 Complete T4",
                List.of("C16 Complete T4", "Embedded complete overview", "Embedded Actor")
        );
    }

    @Test
    public void completeTvUsesEmbeddedSeasonEpisodeAndVideoCapabilities() {
        assertDetailEventually(
                "c16_t4_tv_complete",
                "c16-t4-tv-1",
                "C16 Complete TV",
                List.of("C16 Complete TV", "Embedded complete TV overview", "Embedded Episode 1")
        );
    }

    private static void assertDetailEventually(String key, String id, String name, List<String> expectedTexts) {
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(context, TmdbDetailActivity.class)
                .putExtra("detail_mode", Setting.DETAIL_OPEN_ENHANCED)
                .putExtra("fusion", false)
                .putExtra("auto_play", false)
                .putExtra("key", key)
                .putExtra("id", id)
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
                if (containsAll(texts, expectedTexts)) return;
                SystemClock.sleep(250L);
            }
            assertTrue("missing UI text for " + key + "; visible=" + texts, containsAll(texts, expectedTexts));
        }
    }

    private static boolean containsAll(List<String> texts, List<String> expectedTexts) {
        for (String expected : expectedTexts) {
            boolean found = texts.stream().anyMatch(text -> text != null && text.contains(expected));
            if (!found) return false;
        }
        return true;
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
