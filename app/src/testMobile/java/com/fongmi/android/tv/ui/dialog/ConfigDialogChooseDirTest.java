package com.fongmi.android.tv.ui.dialog;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 猫源本地包是一整个文件夹（{@code index.js} + {@code index.config.js}），系统文件选择器
 * 选不到目录——两个 flavor 都必须另有「选目录」入口，否则这个功能从界面上根本用不了。
 *
 * <p>按本仓库既有做法断言源码文本：这两处是纯 UI 接线，起 Activity 才能验证的成本远高于收益。
 */
public class ConfigDialogChooseDirTest {

    private static final String MOBILE = "app/src/mobile/java/com/fongmi/android/tv/ui/dialog/ConfigDialog.java";
    private static final String LEANBACK = "app/src/leanback/java/com/fongmi/android/tv/ui/dialog/ConfigDialog.java";

    @Test
    public void bothFlavorsExposeDirectoryChooser() throws Exception {
        for (String file : new String[]{MOBILE, LEANBACK}) {
            String source = read(file);
            assertTrue(file + " 必须有选目录的处理方法", source.contains("private void onChooseDir(View view)"));
            assertTrue(file + " 选目录必须走 showDirectory()，show() 选不到文件夹",
                    source.contains("FileChooser.from(launcher).showDirectory();"));
        }
    }

    /** 入口只写方法不接线等于没有，所以单独钉住监听器注册。 */
    @Test
    public void directoryChooserIsWiredToAControl() throws Exception {
        String mobile = read(MOBILE);
        String leanback = read(LEANBACK);
        assertTrue("mobile 只有点播配置显示目录入口，避免给直播/壁纸保存无效路径",
                mobile.contains("binding.choose.setStartIconVisible(type == 0);")
                        && mobile.contains("if (type == 0) binding.choose.setStartIconOnClickListener(this::onChooseDir);"));
        assertTrue("leanback 要把选目录挂到独立按钮",
                leanback.contains("binding.chooseDir.setOnClickListener(this::onChooseDir);"));
        assertTrue("leanback 只有点播配置显示目录入口，避免给直播/壁纸保存无效路径",
                leanback.contains("binding.chooseDir.setVisibility(type == 0 ? View.VISIBLE : View.GONE);"));
    }

    /** 选文件那条路不能被顶掉：本地包 zip 仍然靠它选。 */
    @Test
    public void fileChooserStaysAvailable() throws Exception {
        assertTrue(read(MOBILE).contains("binding.choose.setEndIconOnClickListener(this::onChoose);"));
        assertTrue(read(LEANBACK).contains("binding.choose.setOnClickListener(this::onChoose);"));
    }

    @Test
    public void layoutsProvideTheControlsThoseListenersBindTo() throws Exception {
        String mobile = read("app/src/mobile/res/layout/dialog_config.xml");
        assertTrue("mobile 布局要有 startIcon，否则 setStartIconOnClickListener 点不到",
                mobile.contains("app:startIconDrawable="));
        assertTrue("startIcon 要有无障碍描述", mobile.contains("app:startIconContentDescription="));
        assertTrue("leanback 布局要有 chooseDir 按钮，否则 binding.chooseDir 编译不过",
                read("app/src/leanback/res/layout/dialog_config.xml").contains("android:id=\"@+id/chooseDir\""));
    }

    /**
     * 配置导入要落到不会被系统清理的目录。
     *
     * <p>「同一份内容只存一份」「流会关闭」这些语义由 {@code FileChooserImportTest} 做真实
     * 行为验证，这里只钉住入口和目标目录——那两条是编译期看不出来的接线。
     */
    @Test
    public void configImportUsesPersistentDirectory() throws Exception {
        String chooser = read("app/src/main/java/com/fongmi/android/tv/utils/FileChooser.java");
        assertTrue("配置导入必须使用持久化 URI 路径，不能落到可清理 cache",
                chooser.contains("public static String getPersistentPathFromUri(Uri uri)")
                        && chooser.contains("Path.files(\"cat_source_imports\")"));
    }

    /** 选完什么都不发生是最糟的失败方式，用户不知道该重选还是该换包。 */
    @Test
    public void unreadableSelectionTellsTheUser() throws Exception {
        for (String file : new String[]{MOBILE, LEANBACK}) {
            assertTrue(file + " 读不到所选内容时必须给提示，不能静默 return",
                    read(file).contains("Notify.show(R.string.dialog_config_choose_failed);"));
        }
        // 漏 locale 只会在运行时炸，编译期查不出来。
        for (String values : new String[]{"values", "values-zh-rCN", "values-zh-rTW"}) {
            assertTrue(values + " 缺 dialog_config_choose_failed",
                    read("app/src/main/res/" + values + "/strings.xml").contains("\"dialog_config_choose_failed\""));
        }
    }

    private static String read(String file) throws Exception {
        Path root = Files.exists(Path.of("app")) ? Path.of("") : Path.of("..");
        return Files.readString(root.resolve(file), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
