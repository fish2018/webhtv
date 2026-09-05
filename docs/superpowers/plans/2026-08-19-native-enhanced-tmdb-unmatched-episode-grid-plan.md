# 原生增强模式 TMDB 未匹配选集按钮优化实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task with verification checkpoints.

**Goal:** 当原生增强模式整季没有有效 TMDB 分集匹配时，使用影视原生模式风格的紧凑选集按钮网格；有任意有效匹配时保持整季增强卡片。

**Architecture:** 在无 Android 依赖的 TmdbEpisodeFallbackPolicy 中集中计算整季有效匹配和回退条件。TmdbEpisodeAdapter 保持现有 Episode 数据、监听器和焦点逻辑，只通过 RecyclerView viewType 在整季无匹配时创建新的原生按钮 ViewHolder；普通场景继续使用原有卡片 ViewHolder。

**Tech Stack:** Java、Android RecyclerView、ViewBinding、JUnit 4、Gradle Android debug/unit test、ADB emulator-5556。

---

## 文件结构

- Create: app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbEpisodeFallbackPolicy.java
  - 计算有效 TMDB 分集是否存在，以及原生增强网格回退条件。
- Create: app/src/test/java/com/fongmi/android/tv/ui/helper/TmdbEpisodeFallbackPolicyTest.java
  - 覆盖整季无匹配、部分匹配、无效匹配、非增强模式和列表模式。
- Create: app/src/main/res/layout/adapter_tmdb_episode_native.xml
  - 原生回退按钮 item，允许两行源站标题，并提供独立文件大小标签。
- Create: app/src/main/res/drawable/shape_tmdb_episode_native_grid.xml
  - 主 source 可用的按钮背景，保持影视原生模式的状态颜色和圆角。
- Modify: app/src/main/java/com/fongmi/android/tv/ui/adapter/TmdbEpisodeAdapter.java
  - 增加整季回退状态、native viewType、原生按钮 ViewHolder 和绑定逻辑。
- Modify: docs/superpowers/specs/2026-08-19-native-enhanced-tmdb-unmatched-episode-grid-design.md
  - 仅在实现策略和已确认设计出现必要差异时同步；默认不改动。

## 约束

- 不修改 TMDB 请求、匹配服务、播放地址、详情页数据加载。
- 不在部分匹配的整季中混合卡片和按钮。
- 原生增强列表模式保持现有卡片行为；回退仅在 GRID 模式启用。
- 所有生产代码先有针对策略的失败测试，再实现最小代码。

### Task 1: 写有效匹配与回退策略的失败测试

**Files:**
- Create: app/src/test/java/com/fongmi/android/tv/ui/helper/TmdbEpisodeFallbackPolicyTest.java

- [ ] **Step 1: 创建失败测试**

~~~java
package com.fongmi.android.tv.ui.helper;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.TmdbEpisode;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TmdbEpisodeFallbackPolicyTest {

    @Test
    public void nativeEnhancedGridWithoutMatchedEpisodeUsesNativeGrid() {
        assertTrue(TmdbEpisodeFallbackPolicy.shouldUseNativeGrid(true, true, false));
    }

    @Test
    public void nativeEnhancedGridWithAnyMatchedEpisodeKeepsEnhancedCards() {
        assertFalse(TmdbEpisodeFallbackPolicy.shouldUseNativeGrid(true, true, true));
    }

    @Test
    public void nonNativeEnhancedNeverUsesNativeGridFallback() {
        assertFalse(TmdbEpisodeFallbackPolicy.shouldUseNativeGrid(false, true, false));
    }

    @Test
    public void listModeNeverUsesNativeGridFallback() {
        assertFalse(TmdbEpisodeFallbackPolicy.shouldUseNativeGrid(true, false, false));
    }

    @Test
    public void rejectedTmdbEpisodeDoesNotCountAsMatched() {
        Episode sourceEpisode = Episode.create("1. 源站标题", "url-1");
        TmdbEpisode wrongNumber = new TmdbEpisode(2, "TMDB 标题", "", "", "", 0, 0);

        assertFalse(TmdbEpisodeFallbackPolicy.hasMatchedEpisode(
                List.of(sourceEpisode),
                Map.of(1, wrongNumber),
                Map.of()));
    }

    @Test
    public void partialMatchCountsAsMatchedForWholeSeason() {
        Episode first = Episode.create("1. 第一集", "url-1");
        Episode second = Episode.create("2. 第二集", "url-2");
        TmdbEpisode firstMatch = new TmdbEpisode(1, "第一集", "", "", "", 0, 0);
        TmdbEpisode wrongSecond = new TmdbEpisode(9, "错误集", "", "", "", 0, 0);

        assertTrue(TmdbEpisodeFallbackPolicy.hasMatchedEpisode(
                List.of(first, second),
                Map.of(1, firstMatch, 2, wrongSecond),
                Map.of()));
    }
}
~~~

- [ ] **Step 2: 运行测试，确认按预期失败**

Run:

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.fongmi.android.tv.ui.helper.TmdbEpisodeFallbackPolicyTest
~~~

Expected: FAIL，原因是 TmdbEpisodeFallbackPolicy 尚不存在，而不是测试代码语法错误。

### Task 2: 实现策略并让策略测试通过

**Files:**
- Create: app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbEpisodeFallbackPolicy.java

- [ ] **Step 1: 添加最小实现**

~~~java
package com.fongmi.android.tv.ui.helper;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.TmdbEpisode;

import java.util.List;
import java.util.Map;

public final class TmdbEpisodeFallbackPolicy {

    private TmdbEpisodeFallbackPolicy() {
    }

    public static boolean shouldUseNativeGrid(boolean nativeEnhanced, boolean gridMode, boolean hasMatchedEpisode) {
        return nativeEnhanced && gridMode && !hasMatchedEpisode;
    }

    public static boolean hasMatchedEpisode(List<Episode> episodes, Map<Integer, TmdbEpisode> tmdbEpisodes, Map<Episode, Integer> numbers) {
        if (episodes == null || episodes.isEmpty() || tmdbEpisodes == null || tmdbEpisodes.isEmpty()) return false;
        for (int index = 0; index < episodes.size(); index++) {
            Episode episode = episodes.get(index);
            Integer mappedNumber = numbers == null ? null : numbers.get(episode);
            int episodeNumber = mappedNumber == null ? index + 1 : mappedNumber;
            if (TmdbEpisodeMatcher.shouldApply(episode, tmdbEpisodes.get(episodeNumber), episodeNumber)) return true;
        }
        return false;
    }
}
~~~

- [ ] **Step 2: 运行测试确认通过**

Run:

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.fongmi.android.tv.ui.helper.TmdbEpisodeFallbackPolicyTest
~~~

Expected: PASS，6 tests passed。

### Task 3: 增加原生回退按钮布局与背景

**Files:**
- Create: app/src/main/res/layout/adapter_tmdb_episode_native.xml
- Create: app/src/main/res/drawable/shape_tmdb_episode_native_grid.xml

- [ ] **Step 1: 创建主 source 可用的状态背景**

布局使用 MaterialTextView 作为根视图，根视图 match_parent 宽度、wrap_content 高度、minHeight 40dp、padding 8dp 12dp、最多两行、居中显示；背景使用 selector + ripple：默认半透明黑色，focused 使用更亮背景，activated/selected 使用原生高亮背景。文件大小使用右上角独立 MaterialTextView 标签，初始 GONE。

- [ ] **Step 2: 编译资源确认 ViewBinding 可生成**

Run:

~~~powershell
.\gradlew.bat :app:compileMobileDebugJavaWithJavac
~~~

Expected: PASS，生成 AdapterTmdbEpisodeNativeBinding，且无资源合并错误。

### Task 4: 在 TmdbEpisodeAdapter 中接入整季回退 viewType

**Files:**
- Modify: app/src/main/java/com/fongmi/android/tv/ui/adapter/TmdbEpisodeAdapter.java

- [ ] **Step 1: 添加状态、viewType 和 native binding 字段**

新增 AdapterTmdbEpisodeNativeBinding import、TmdbEpisodeFallbackPolicy import、VIEW_TYPE_CARD、VIEW_TYPE_NATIVE_GRID 常量和 nativeGridFallback 字段。ViewHolder 同时持有普通卡片 binding 或原生按钮 binding，并提供两个构造函数。

- [ ] **Step 2: 在 setItems、setNativeEnhanced、setMode、setDisplayMode 后更新回退状态**

新增私有方法：

~~~java
private void updateNativeGridFallback() {
    boolean value = TmdbEpisodeFallbackPolicy.shouldUseNativeGrid(
            nativeEnhanced,
            mode == Mode.GRID,
            TmdbEpisodeFallbackPolicy.hasMatchedEpisode(items, tmdbItems, episodeNumbers));
    if (nativeGridFallback == value) return;
    nativeGridFallback = value;
    if (!items.isEmpty()) notifyDataSetChanged();
}
~~~

在 setItems 完成三张数据表更新后调用；模式或 nativeEnhanced 改变时也调用。更新顺序必须保证 items、tmdbItems、episodeNumbers 已经是新季数据后再计算。

- [ ] **Step 3: 增加 getItemViewType 和双布局 onCreateViewHolder**

~~~java
@Override
public int getItemViewType(int position) {
    return nativeGridFallback ? VIEW_TYPE_NATIVE_GRID : VIEW_TYPE_CARD;
}

@Override
public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    LayoutInflater inflater = LayoutInflater.from(parent.getContext());
    if (viewType == VIEW_TYPE_NATIVE_GRID) {
        return new ViewHolder(AdapterTmdbEpisodeNativeBinding.inflate(inflater, parent, false));
    }
    return new ViewHolder(AdapterTmdbEpisodeBinding.inflate(inflater, parent, false));
}
~~~

- [ ] **Step 4: 添加原生按钮绑定分支**

在 onBindViewHolder 开始处先判断 nativeGridFallback；回退绑定逻辑使用 episodeNumber(episode, position)、getCleanTitle(episode, episodeNumber, "") 和 episodeFileSize(episode)，设置按钮文本、文件大小、activated 状态、点击、长按、OnKeyListener 和焦点监听后直接 return。按钮不加载图片，也不绑定 TMDB 日期、评分或简介。

- [ ] **Step 5: 复用焦点和选中逻辑但避免访问空 binding**

为回退 ViewHolder 增加独立的 applyNativeGridFocus 方法：使用同一 activeStrokeColor 和 TmdbCardFocusHelper，当前项使用 activated 状态，焦点时保留现有 foregroundBorder 和 elevation 行为；setMarquee 改为接受 TextView 和状态，或增加原生按钮专用重载。普通卡片路径保持原代码行为。

- [ ] **Step 6: 更新回收逻辑和布局尺寸**

onViewRecycled 仅在普通卡片 binding 非空时清理 still；原生按钮无需图片清理。回退布局 item 高度采用 minHeight + wrap_content，网格列数仍由外层 RecyclerView 的 gridSpanCount 决定；普通卡片的 applyCardSize 不改变。

### Task 5: 运行策略、编译和回归测试

**Files:**
- Modify: 无

- [ ] **Step 1: 运行新增策略测试**

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.fongmi.android.tv.ui.helper.TmdbEpisodeFallbackPolicyTest
~~~

Expected: PASS，6 tests passed。

- [ ] **Step 2: 运行受影响的 UI/Helper 测试**

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.fongmi.android.tv.ui.helper.* --tests com.fongmi.android.tv.ui.adapter.*
~~~

Expected: PASS；如果当前分支没有对应测试类，Gradle 应明确报告无匹配测试，不得把编译错误当作通过。

- [ ] **Step 3: 构建 mobile debug APK**

~~~powershell
.\gradlew.bat :app:assembleMobileDebug
~~~

Expected: BUILD SUCCESSFUL，生成 app/build/outputs/apk/mobile/debug/app-mobile-debug.apk 或项目实际配置对应的 mobile debug APK。

- [ ] **Step 4: 检查工作树和变更范围**

~~~powershell
git diff --check
git status --short
git diff --stat
~~~

Expected: 无空白错误；只包含策略、Adapter、布局、drawable 和测试相关变更。

### Task 6: 在 emulator-5556 上验证实际 UI

**Files:**
- Modify: 无

- [ ] **Step 1: 确认设备并安装 APK**

~~~powershell
adb devices
adb -s emulator-5556 install -r app/build/outputs/apk/mobile/debug/app-mobile-debug.apk
~~~

Expected: devices 中出现 emulator-5556，安装返回 Success；如果 APK 文件名不同，使用 assemble 输出的实际文件。

- [ ] **Step 2: 启动应用并采集当前页面状态**

使用项目现有启动 Activity；若需要从 launcher 启动，先执行：

~~~powershell
adb -s emulator-5556 shell monkey -p com.fongmi.android.tv 1
~~~

再通过现有测试数据或用户指定站点进入原生增强模式选集页。

- [ ] **Step 3: 验证四类视觉场景**

逐项截图确认：

1. 整季 TMDB 无有效匹配：显示紧凑原生按钮网格，不显示空海报卡片；
2. 2 列和 3 列：按钮间距、行高和标题均不重叠；
3. 长标题与文件大小：标题最多两行，文件大小标签不覆盖标题；
4. 存在一个有效 TMDB 匹配：整季仍为增强卡片，不出现混排。

- [ ] **Step 4: 验证交互**

用 adb 输入方向键和确认键，确认当前项焦点描边、选中高亮、点击播放和长按菜单都正常。

### Task 7: 完成前验证与提交

**Files:**
- Modify: 本次实现文件

- [ ] **Step 1: 运行完整 mobile debug 单元测试**

~~~powershell
.\gradlew.bat :app:testMobileDebugUnitTest
~~~

Expected: BUILD SUCCESSFUL，测试失败数为 0。

- [ ] **Step 2: 再次运行 git diff --check 和 git status**

~~~powershell
git diff --check
git status --short
~~~

Expected: 无格式错误，并确认没有意外生成文件。

- [ ] **Step 3: 提交实现**

~~~powershell
git add app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbEpisodeFallbackPolicy.java app/src/test/java/com/fongmi/android/tv/ui/helper/TmdbEpisodeFallbackPolicyTest.java app/src/main/java/com/fongmi/android/tv/ui/adapter/TmdbEpisodeAdapter.java app/src/main/res/layout/adapter_tmdb_episode_native.xml app/src/main/res/drawable/shape_tmdb_episode_native_grid.xml
git commit -m "优化原生增强模式未匹配 TMDB 的选集按钮"
~~~

Expected: commit 成功，提交内容仅包含本功能实现。
