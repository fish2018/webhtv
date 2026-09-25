package com.fongmi.android.tv.ui.dialog;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigHistoryCurrentSourceGuardTest {

    private static final String MOBILE = "src/mobile/java/com/fongmi/android/tv/ui/adapter/ConfigAdapter.java";
    private static final String LEANBACK = "src/leanback/java/com/fongmi/android/tv/ui/adapter/ConfigAdapter.java";

    @Test
    public void managerShowsCurrentConfigButProtectsItsUseAndDeleteActions() throws Exception {
        assertCurrentConfigProtected(MOBILE);
        assertCurrentConfigProtected(LEANBACK);
    }

    @Test
    public void deletingAConfigRequiresConfirmation() throws Exception {
        assertDeleteConfirmation("src/mobile/java/com/fongmi/android/tv/ui/dialog/HistoryDialog.java");
        assertDeleteConfirmation("src/leanback/java/com/fongmi/android/tv/ui/dialog/HistoryDialog.java");
    }

    private static void assertCurrentConfigProtected(String file) throws Exception {
        String source = Files.readString(Path.of(file), StandardCharsets.UTF_8);
        assertTrue(source.contains("private boolean protectCurrent;"));
        assertTrue(source.contains("if (!readOnly && !protectCurrent && !TextUtils.isEmpty(currentUrl))"));
        assertTrue(source.contains("mItems.removeIf(item -> TextUtils.equals(item.getUrl(), currentUrl));"));
        assertTrue(source.contains("holder.binding.delete.setVisibility(readOnly ? View.GONE : View.VISIBLE);"));
        assertTrue(source.contains("holder.binding.delete.setAlpha(current ? 0.38f : 1f);"));
        if (file.contains("leanback")) {
            assertTrue(source.contains("holder.binding.text.setFocusable(true)"));
            assertTrue(source.contains("KEYCODE_DPAD_DOWN"));
            assertFalse(source.contains("holder.binding.text.setFocusable(!current)"));
            if (file.contains("leanback")) {
                assertTrue(source.contains("recycler.stopScroll()"));
                assertTrue(source.contains("recycler.addOnChildAttachStateChangeListener(this)"));
                assertTrue(source.contains("onChildViewAttachedToWindow(@NonNull View view)"));
                assertTrue(source.contains("recycler.removeOnChildAttachStateChangeListener(this)"));
                assertTrue(source.contains("recycler.postOnAnimation(focus::run)"));
                assertFalse(source.contains("postDelayed(focus"));
            }
        }
        assertTrue(source.contains("if (!current) listener.onTextClick(item);"));
        assertFalse(source.contains("if (type != 0 && !readOnly && !TextUtils.isEmpty(currentUrl))"));
    }

    private static void assertDeleteConfirmation(String file) throws Exception {
        String source = Files.readString(Path.of(file), StandardCharsets.UTF_8);
        assertTrue(source.contains(".setTitle(R.string.config_delete_title)"));
        assertTrue(source.contains(".setMessage(getString(R.string.config_delete_message, item.getDesc()))"));
        assertFalse(source.contains(".setMessage(getString(R.string.config_delete_message, item.getName()))"));
        assertTrue(source.contains(".setNegativeButton(R.string.dialog_negative, null)"));
        assertTrue(source.contains(".setPositiveButton(R.string.dialog_positive"));
        if (file.contains("leanback")) {
            assertTrue(source.contains("dialog.setOnShowListener"));
            assertTrue(source.contains("BUTTON_NEGATIVE).requestFocus()"));
        }
    }
}
