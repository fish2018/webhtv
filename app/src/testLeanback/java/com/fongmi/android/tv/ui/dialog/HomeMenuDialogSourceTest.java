package com.fongmi.android.tv.ui.dialog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 锁住 TV 版首页菜单键「选项弹窗」的修复：index 0 必须弹 HomeMenuDialog，
 * 而不是回落到 showDialog()（那样出来的是切换站源弹窗）。
 */
public class HomeMenuDialogSourceTest {

    @Test
    public void homeActivityOpensMenuDialogForFirstOption() throws Exception {
        String java = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/HomeActivity.java");
        assertTrue(java.contains("HomeMenuDialog.Listener"));
        assertTrue(java.contains("if (index == 0) HomeMenuDialog.create().show(this)"));
        assertTrue(java.contains("public void onHomeMenuItem(int index)"));
        // 修复前 case 0 落到 default -> showDialog()，等价于开站源弹窗
        assertTrue(!java.contains("default -> showDialog();"));
    }

    @Test
    public void menuDialogGuardsAgainstRecursionAndStacking() throws Exception {
        String java = read("app/src/leanback/java/com/fongmi/android/tv/ui/dialog/HomeMenuDialog.java");
        // NO_POSITION 时 position + 1 == 0，会再次弹出本弹窗
        assertTrue(java.contains("if (position == RecyclerView.NO_POSITION) return;"));
        assertTrue(java.contains("fragment instanceof HomeMenuDialog"));
        // 进程恢复后 listener 丢失需回退到宿主 Activity
        assertTrue(java.contains("getActivity() instanceof Listener"));
        assertTrue(java.contains("onHomeMenuItem(position + 1)"));
    }

    @Test
    public void menuOptionsSkipFirstEntryAndMapToActionIndexes() throws Exception {
        String java = read("app/src/leanback/java/com/fongmi/android/tv/ui/dialog/HomeMenuDialog.java");
        assertTrue(java.contains("items.remove(0)"));
        int options = countItems(read("app/src/main/res/values-zh-rCN/strings.xml"));
        // 数组共 10 项，去掉第 0 项后 position 0..8 对应 onHomeMenuItem 的 case 1..9
        assertEquals(10, options);
        String activity = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/HomeActivity.java");
        for (int index = 1; index <= options - 1; index++) {
            assertTrue("缺少 case " + index, activity.contains("case " + index + " ->"));
        }
    }

    @Test
    public void menuLayoutsExist() throws Exception {
        String dialog = read("app/src/leanback/res/layout/dialog_home_menu.xml");
        String adapter = read("app/src/leanback/res/layout/adapter_home_menu.xml");
        assertTrue(dialog.contains("@+id/recycler"));
        assertTrue(adapter.contains("@+id/text"));
        // 9 项按 3 列排布才能一屏显示完，单列会超出 maxHeight 需要滚动
        assertTrue(dialog.contains("app:spanCount=\"3\""));
        assertTrue(dialog.contains("GridLayoutManager"));
        // 英文文案最长 13 字符，18sp 在窄列上会被 ellipsize 截断，需要自适应缩字兜底
        assertTrue(adapter.contains("app:autoSizeTextType=\"uniform\""));
        assertTrue(adapter.contains("xmlns:app="));
        String java = read("app/src/leanback/java/com/fongmi/android/tv/ui/dialog/HomeMenuDialog.java");
        // 间距的列数必须与 XML 的 spanCount 一致，否则条目宽度不均
        assertTrue(java.contains("GRID_COUNT = 3"));
        assertTrue(java.contains("new SpaceItemDecoration(GRID_COUNT, 16)"));
    }

    private static int countItems(String strings) {
        Matcher array = Pattern.compile("<string-array name=\"select_home_menu_key\">(.*?)</string-array>", Pattern.DOTALL).matcher(strings);
        assertTrue("未找到 select_home_menu_key", array.find());
        Matcher item = Pattern.compile("<item>").matcher(array.group(1));
        int count = 0;
        while (item.find()) count++;
        return count;
    }

    private static String read(String path) throws Exception {
        Path direct = Path.of(path);
        if (Files.exists(direct)) return Files.readString(direct, StandardCharsets.UTF_8);
        return Files.readString(Path.of("..").resolve(path), StandardCharsets.UTF_8);
    }
}
