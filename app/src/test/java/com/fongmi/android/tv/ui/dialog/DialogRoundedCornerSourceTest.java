package com.fongmi.android.tv.ui.dialog;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class DialogRoundedCornerSourceTest {

    @Test
    public void outerDialogsUseTheUnifiedTwentyTwoDpRadius() throws Exception {
        String[] drawables = {
                "src/main/res/drawable/shape_shell_proxy_dialog.xml",
                "src/main/res/drawable/shape_display_dialog_panel.xml",
                "src/main/res/drawable/shape_one_key_sync_dialog.xml",
                "src/leanback/res/drawable/shape_config_history_dialog.xml",
                "src/leanback/res/drawable/shape_episode_dialog_panel.xml",
                "src/main/res/drawable/shape_site_dialog.xml",
                "src/main/res/drawable/shape_dialog_glass_toolbar.xml",
                "src/main/res/drawable/shape_audio_playlist_panel.xml",
                "src/main/res/drawable/shape_disc_menu_panel.xml",
                "src/leanback/res/drawable/shape_exit_confirm_dialog.xml",
                "src/mobile/res/drawable/selector_control_sheet_button.xml",
                "src/mobile/res/drawable/selector_player_child_sheet_button.xml",
                "src/mobile/res/drawable/selector_live_action_button.xml",
                "src/main/res/drawable/selector_dialog_step_button.xml"
        };

        for (String drawable : drawables) {
            Path path = Path.of(drawable);
            String source = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            assertTrue(drawable + " should use the unified 22dp corner radius",
                    source.contains("<corners android:radius=\"22dp\" />"));
        }
    }

    @Test
    public void materialDialogShapeUsesTheUnifiedTwentyTwoDpRadius() throws Exception {
        String styles = Files.readString(Path.of("src/main/res/values/styles.xml"), StandardCharsets.UTF_8);
        assertTrue("Material dialog shape should use the unified 22dp corner radius",
                styles.contains("<style name=\"ShapeAppearance.WebHTV.Dialog\" parent=\"\">\n        <item name=\"cornerFamily\">rounded</item>\n        <item name=\"cornerSize\">22dp</item>\n    </style>"));

        String baseDialog = Files.readString(Path.of("src/main/java/com/fongmi/android/tv/ui/dialog/BaseAlertDialog.java"), StandardCharsets.UTF_8);
        assertTrue("BaseAlertDialog builder should apply the unified rounded style",
                baseDialog.contains("new MaterialAlertDialogBuilder(requireActivity(), R.style.MaterialAlertDialog_WebHTV_Rounded)"));

        String mobileStyles = Files.readString(Path.of("src/mobile/res/values/styles.xml"), StandardCharsets.UTF_8);
        String leanbackStyles = Files.readString(Path.of("src/leanback/res/values/styles.xml"), StandardCharsets.UTF_8);
        assertTrue("Mobile theme should apply the unified rounded dialog overlay",
                mobileStyles.contains("<item name=\"materialAlertDialogTheme\">@style/ThemeOverlay.WebHTV.LightDialog</item>"));
        assertTrue("Leanback theme should apply the unified rounded dialog overlay",
                leanbackStyles.contains("<item name=\"materialAlertDialogTheme\">@style/ThemeOverlay.WebHTV.LightDialog</item>"));
    }
}
