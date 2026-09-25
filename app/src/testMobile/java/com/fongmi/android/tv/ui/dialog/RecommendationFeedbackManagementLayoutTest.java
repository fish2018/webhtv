package com.fongmi.android.tv.ui.dialog;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class RecommendationFeedbackManagementLayoutTest {

    @Test
    public void aiSettingsExposeFeedbackManagementAndCount() throws Exception {
        String activity = read(findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "SettingAiActivity.java")));
        String layout = read(findLeanbackResPath().resolve(Path.of("layout", "activity_setting_ai.xml")));

        assertTrue(layout.contains("@+id/recommendationFeedback")
                && layout.contains("@+id/recommendationFeedbackText")
                && layout.contains("@string/setting_recommendation_feedback"));
        assertTrue(activity.contains("mBinding.recommendationFeedback.setOnClickListener(this::manageRecommendationFeedback)")
                && activity.contains("RecommendationFeedbackDialog.create()")
                && activity.contains("RecommendationFeedbackStore.size()"));
    }

    @Test
    public void managementDialogSupportsRemoteRestoreClearAndClose() throws Exception {
        String dialog = read(findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "RecommendationFeedbackDialog.java")));
        String adapter = read(findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "RecommendationFeedbackAdapter.java")));
        String layout = read(findLeanbackResPath().resolve(Path.of("layout", "dialog_recommendation_feedback.xml")));
        String item = read(findLeanbackResPath().resolve(Path.of("layout", "adapter_recommendation_feedback.xml")));

        assertTrue(layout.contains("@+id/recycler")
                && layout.contains("@+id/empty")
                && layout.contains("@+id/clearAll")
                && layout.contains("@+id/close"));
        assertTrue(layout.contains("android:nextFocusRight=\"@id/close\"")
                && layout.contains("android:nextFocusLeft=\"@id/clearAll\""));
        assertTrue(item.contains("android:focusable=\"true\"")
                && item.contains("@drawable/selector_light_dialog_item"));
        assertTrue(adapter.contains("setNextFocusDownId(") && adapter.contains("R.id.clearAll")
                && dialog.contains("adapter.focusFirst(binding.recycler)")
                && dialog.contains("RecommendationFeedbackStore.remove(item)")
                && dialog.contains("RecommendationFeedbackStore.clear()"));
    }

    @Test
    public void mobileAiSettingsExposeFeedbackManagementAndDialog() throws Exception {
        String fragment = read(findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "fragment", "SettingAiFragment.java")));
        String layout = read(findMobileResPath().resolve(Path.of("layout", "fragment_setting_ai.xml")));
        String dialog = read(findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "RecommendationFeedbackDialog.java")));
        String dialogLayout = read(findMobileResPath().resolve(Path.of("layout", "dialog_recommendation_feedback.xml")));
        String item = read(findMobileResPath().resolve(Path.of("layout", "adapter_recommendation_feedback.xml")));

        assertTrue(layout.contains("@+id/recommendationFeedback")
                && layout.contains("@+id/recommendationFeedbackText"));
        assertTrue(fragment.contains("mBinding.recommendationFeedback.setOnClickListener(this::manageRecommendationFeedback)")
                && fragment.contains("RecommendationFeedbackStore.size()")
                && fragment.contains("RecommendationFeedbackDialog.create(requireActivity())"));
        assertTrue(dialogLayout.contains("@+id/recycler")
                && dialogLayout.contains("@+id/empty"));
        assertTrue(item.contains("@+id/title") && item.contains("@+id/meta"));
        assertTrue(dialog.contains("RecommendationFeedbackStore.remove(item)")
                && dialog.contains("RecommendationFeedbackStore.clear()")
                && dialog.contains("RefreshEvent.history()"));
    }
    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path findMobileJavaPath() {
        Path moduleRelative = Path.of("src", "mobile", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "mobile", "java");
    }

    private static Path findMobileResPath() {
        Path moduleRelative = Path.of("src", "mobile", "res");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "mobile", "res");
    }
    private static Path findLeanbackJavaPath() {
        Path moduleRelative = Path.of("src", "leanback", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "leanback", "java");
    }

    private static Path findLeanbackResPath() {
        Path moduleRelative = Path.of("src", "leanback", "res");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "leanback", "res");
    }
}
