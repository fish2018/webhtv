package com.fongmi.android.tv.ui.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.setting.Setting;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TmdbDetailDirectPlayInstrumentedTest {

    @Test
    public void directModeWaitsForExplicitPlayAction() {
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(context, TmdbDetailActivity.class)
                .putExtra("detail_mode", Setting.DETAIL_OPEN_PLAYER)
                .putExtra("fusion", false)
                .putExtra("auto_play", false)
                .putExtra("key", "")
                .putExtra("id", "instrumented-direct-play")
                .putExtra("name", "详情直放验收")
                .putExtra("pic", "")
                .putExtra("mark", "");

        try (ActivityScenario<TmdbDetailActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                assertEquals(TmdbDetailActivity.class, activity.getClass());
                assertNotNull(activity.findViewById(R.id.detailActions));
                assertEquals(android.view.View.VISIBLE, activity.findViewById(R.id.detailActions).getVisibility());
                assertNotNull(activity.findViewById(R.id.playerPanel));
                assertEquals(android.view.View.GONE, activity.findViewById(R.id.playerPanel).getVisibility());
            });
        }
    }


}
