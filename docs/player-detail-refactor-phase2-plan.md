# 播放器解耦第二阶段实施计划：TMDB 详情页模式 Controller

**状态：** 待实施  
**创建日期：** 2026-07-14  
**预计工期：** 1.5-2 周  
**前置条件：** 第一阶段 `VodPlayerUiController` 已完成并接入

---

## 🎯 目标

将 `TmdbDetailActivity` 中散落的沉浸融合/炫彩详情/详情直放三种模式的判断逻辑（`isFusionMode()` / `isPlayerMode()` / `isCinemaMode()`）抽取到独立的模式 Controller 中，使模式差异集中管理、新增模式无需在 Activity 中散落 `if-else`。

---

## 📋 实施计划

### Phase 1：建立模式 Controller 基础框架（1-2 天）

#### 1.1 新增文件（4 个）

```
app/src/main/java/com/fongmi/android/tv/ui/detail/
├── TmdbDetailModeController.java      # 模式接口
├── BaseTmdbDetailModeController.java  # 三种模式共享实现
├── FusionDetailController.java        # 沉浸融合差异
└── TmdbDetailHost.java                # Activity 暴露给模式的能力接口
```

**为什么先建这 4 个：**
- 接口 + 抽象基类可以让三个子 Controller 共享代码，避免重复
- `TmdbDetailHost` 接口让 Controller 不直接依赖 Activity，保持可测试性
- 先建框架，让编译通过，再逐步迁移具体逻辑

#### 1.2 模式接口草案

```java
// TmdbDetailModeController.java
package com.fongmi.android.tv.ui.detail;

public interface TmdbDetailModeController {
    
    /**
     * 绑定 View 和设置监听
     */
    void bind();
    
    /**
     * 设置初始布局可见性
     */
    void applyInitialLayout();
    
    /**
     * 应用主题和样式
     */
    void applyTheme();
    
    /**
     * TMDB 内容加载完成回调
     */
    void onContentLoaded();
    
    /**
     * 播放开始回调
     */
    void onPlaybackStarted();
    
    /**
     * 是否显示内联播放器
     */
    boolean shouldShowInlinePlayer();
    
    /**
     * 是否自动播放
     */
    boolean shouldAutoPlay();
    
    /**
     * 处理返回键
     * @return true 表示已处理，Activity 不再处理
     */
    boolean handleBack();
    
    /**
     * 释放资源
     */
    void release();
}
```

#### 1.3 Host 接口草案（最小能力集）

```java
// TmdbDetailHost.java
package com.fongmi.android.tv.ui.detail;

import android.content.Context;
import androidx.viewbinding.ViewBinding;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.player.PlayerManager;

/**
 * Activity 暴露给模式 Controller 的能力接口。
 * <p>
 * 注意：每次只为已迁移代码添加方法，避免接口过大。
 */
public interface TmdbDetailHost {
    
    Context context();
    
    /**
     * 返回 ActivityTmdbDetailBinding
     */
    ViewBinding binding();
    
    TmdbItem getTmdbItem();
    
    PlayerManager player();
    
    void showEpisodes();
    
    void showQuality();
    
    void startFullscreenPlayback();
    
    void finishActivity();
    
    boolean isOwner();
}
```

#### 1.4 抽象基类

```java
// BaseTmdbDetailModeController.java
package com.fongmi.android.tv.ui.detail;

import android.view.View;

/**
 * 三种模式的共享实现基类。
 * <p>
 * 子类通过覆盖抽象方法和 protected 方法来表达差异。
 */
public abstract class BaseTmdbDetailModeController implements TmdbDetailModeController {
    
    protected final TmdbDetailHost host;
    
    public BaseTmdbDetailModeController(TmdbDetailHost host) {
        this.host = host;
    }
    
    @Override
    public void bind() {
        // 默认空实现，子类按需覆盖
    }
    
    @Override
    public void applyTheme() {
        // 共享主题逻辑（如有）
    }
    
    @Override
    public void onContentLoaded() {
        // 默认空实现
    }
    
    @Override
    public void onPlaybackStarted() {
        // 默认空实现
    }
    
    @Override
    public boolean shouldShowInlinePlayer() {
        return showInlinePlayer();
    }
    
    @Override
    public boolean shouldAutoPlay() {
        return autoPlay();
    }
    
    @Override
    public boolean handleBack() {
        return false; // 默认不处理
    }
    
    @Override
    public void release() {
        // 默认空实现
    }
    
    // 子类覆盖的差异点
    protected abstract boolean showInlinePlayer();
    protected abstract boolean autoPlay();
    protected abstract int heroSpacerVisibility();
}
```

#### 1.5 沉浸融合 Controller 初始框架

```java
// FusionDetailController.java
package com.fongmi.android.tv.ui.detail;

import android.view.View;

/**
 * 沉浸融合模式 Controller。
 * <p>
 * 特点：显示内联播放器，不自动播放，特殊按钮布局。
 */
public class FusionDetailController extends BaseTmdbDetailModeController {
    
    public FusionDetailController(TmdbDetailHost host) {
        super(host);
    }
    
    @Override
    protected boolean showInlinePlayer() {
        return true;
    }
    
    @Override
    protected boolean autoPlay() {
        return false; // 融合模式不自动播放
    }
    
    @Override
    protected int heroSpacerVisibility() {
        return View.GONE;
    }
    
    @Override
    public void applyInitialLayout() {
        // TODO: 迁移 TmdbDetailActivity 中的融合模式布局设置
        // binding().heroSpacer.setVisibility(View.GONE);
        // binding().inlinePlayerContainer.setVisibility(View.VISIBLE);
    }
    
    @Override
    public void onPlaybackStarted() {
        // TODO: 融合模式特有的播放开始逻辑
    }
}
```

---

### Phase 2：迁移沉浸融合模式（2-3 天）

**为什么先迁移融合模式：**
- 设计文档说 `initFusionPlayer()` 已经把融合模式逻辑集中在一处，边界最清晰
- 融合模式最复杂（内联播放器 + 特殊按钮布局），先啃硬骨头

#### 2.1 在 `TmdbDetailActivity` 中接入

```java
// TmdbDetailActivity.java (新增部分)
private TmdbDetailModeController modeController;

private void initModeController() {
    TmdbDetailHost host = new TmdbDetailHost() {
        @Override public Context context() { return TmdbDetailActivity.this; }
        @Override public ViewBinding binding() { return mBinding; }
        @Override public TmdbItem getTmdbItem() { return mTmdbItem; }
        @Override public PlayerManager player() { return mPlayer; }
        @Override public void showEpisodes() { TmdbDetailActivity.this.showEpisodes(); }
        @Override public void showQuality() { TmdbDetailActivity.this.showQuality(); }
        @Override public void startFullscreenPlayback() { TmdbDetailActivity.this.startFullscreenPlayback(); }
        @Override public void finishActivity() { finish(); }
        @Override public boolean isOwner() { return TmdbDetailActivity.this.isOwner(); }
    };
    
    if (isFusionMode()) {
        modeController = new FusionDetailController(host);
    } else {
        // 暂时保留旧逻辑
        modeController = null;
    }
    
    if (modeController != null) {
        modeController.bind();
        modeController.applyInitialLayout();
        modeController.applyTheme();
    }
}

@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // ... 现有初始化 ...
    initModeController();
}

@Override
protected void onDestroy() {
    if (modeController != null) modeController.release();
    super.onDestroy();
}

@Override
public void onBackPressed() {
    if (modeController != null && modeController.handleBack()) {
        return; // Controller 已处理
    }
    super.onBackPressed();
}
```

#### 2.2 逐步替换 `isFusionMode()` 判断

**策略：** 先共存，再替换，最后删除。

**步骤：**

1. **找出所有 `isFusionMode()` 判断**
   ```bash
   grep -n "isFusionMode()" app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java
   ```

2. **对每个判断点，评估迁移方式**
   - 布局可见性 → 移入 `applyInitialLayout()`
   - 播放行为 → 移入 `onPlaybackStarted()`
   - 按钮绑定 → 移入 `bind()`

3. **保持旧代码并行，添加新代码**
   ```java
   // 旧代码（暂时保留）
   if (isFusionMode()) {
       mBinding.heroSpacer.setVisibility(View.GONE);
   }
   
   // 新代码（并行运行）
   if (modeController != null) {
       modeController.applyInitialLayout();
   }
   ```

4. **验证无问题后，删除旧代码**

#### 2.3 迁移清单（融合模式）

需要从 `TmdbDetailActivity` 迁移到 `FusionDetailController` 的逻辑：

- [ ] `initFusionPlayer()` 中的布局设置
- [ ] 内联播放器容器显示/隐藏
- [ ] `heroSpacer` 可见性控制
- [ ] 融合模式特有的按钮绑定
- [ ] 播放开始后的 UI 更新
- [ ] 主题切换时的样式应用
- [ ] 返回键特殊处理（如有）

#### 2.4 验证清单（融合模式）

- [ ] 融合模式进入详情页，布局正确
- [ ] 内联播放器正常显示
- [ ] 切换到全屏再返回，状态恢复
- [ ] 返回键行为不变
- [ ] 主题切换生效
- [ ] 旋转屏幕布局不错乱
- [ ] 焦点导航正常（TV 端）

---

### Phase 3：迁移炫彩详情和详情直放模式（1-2 天）

#### 3.1 新增两个文件

```
app/src/main/java/com/fongmi/android/tv/ui/detail/
├── EnhancedDetailController.java      # 炫彩详情
└── PlayerDetailController.java        # 详情直放
```

#### 3.2 实现差异

```java
// EnhancedDetailController.java
package com.fongmi.android.tv.ui.detail;

import android.view.View;

/**
 * 炫彩详情模式 Controller（影视原生）。
 * <p>
 * 特点：不显示内联播放器，不自动播放，标准详情页布局。
 */
public class EnhancedDetailController extends BaseTmdbDetailModeController {
    
    public EnhancedDetailController(TmdbDetailHost host) {
        super(host);
    }
    
    @Override
    protected boolean showInlinePlayer() {
        return false;
    }
    
    @Override
    protected boolean autoPlay() {
        return false;
    }
    
    @Override
    protected int heroSpacerVisibility() {
        return View.VISIBLE;
    }
    
    @Override
    public void applyInitialLayout() {
        // 炫彩详情特有布局
    }
}

// PlayerDetailController.java
package com.fongmi.android.tv.ui.detail;

import android.view.View;

/**
 * 详情直放模式 Controller。
 * <p>
 * 特点：不显示内联播放器，自动播放，内容加载完立即进入全屏。
 */
public class PlayerDetailController extends BaseTmdbDetailModeController {
    
    public PlayerDetailController(TmdbDetailHost host) {
        super(host);
    }
    
    @Override
    protected boolean showInlinePlayer() {
        return false;
    }
    
    @Override
    protected boolean autoPlay() {
        return true; // 直放自动播放
    }
    
    @Override
    protected int heroSpacerVisibility() {
        return View.VISIBLE;
    }
    
    @Override
    public void onContentLoaded() {
        // 内容加载完立即播放
        host.startFullscreenPlayback();
    }
}
```

#### 3.3 完善工厂方法

```java
// TmdbDetailActivity.java
private TmdbDetailModeController createModeController(TmdbDetailHost host) {
    if (isFusionMode()) {
        return new FusionDetailController(host);
    }
    if (isPlayerMode()) {
        return new PlayerDetailController(host);
    }
    // 默认炫彩详情（包括 isCinemaMode() 和其他情况）
    return new EnhancedDetailController(host);
}

private void initModeController() {
    TmdbDetailHost host = createHost();
    modeController = createModeController(host);
    modeController.bind();
    modeController.applyInitialLayout();
    modeController.applyTheme();
}
```

#### 3.4 迁移清单（另外两种模式）

- [ ] 炫彩详情布局设置
- [ ] 详情直放自动播放逻辑
- [ ] 各模式的按钮可见性差异
- [ ] 各模式的主题应用差异

---

### Phase 4：消除 Activity 中的模式判断（1-2 天）

#### 4.1 全局搜索

```bash
# 搜索所有模式判断
grep -rn "isFusionMode\|isPlayerMode\|isCinemaMode" app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java
```

#### 4.2 替换策略

| 旧代码 | 新代码 | 位置 |
|-------|-------|------|
| `if (isFusionMode()) { ... }` | `modeController.onXXX()` | Controller 对应方法 |
| `mBinding.xxx.setVisibility(isFusionMode() ? GONE : VISIBLE)` | 移入 `applyInitialLayout()` | Controller |
| `if (isPlayerMode()) startPlay();` | 移入 `PlayerDetailController.onContentLoaded()` | Controller |
| `isFusionMode() && ...` | `modeController.shouldShowInlinePlayer() && ...` | Activity |

#### 4.3 保留的判断

以下判断**暂时保留**（第三阶段再处理）：
- 数据加载相关的判断（移入 ViewModel）
- 跨 Activity 的路由判断（如 Intent 参数解析）
- `isFusionMode()` / `isPlayerMode()` / `isCinemaMode()` 方法本身保留，但**只在工厂方法中使用**

#### 4.4 最终目标代码结构

```java
// TmdbDetailActivity.java (理想状态)
private TmdbDetailModeController modeController;

// ✅ 唯一使用模式判断的地方
private TmdbDetailModeController createModeController(TmdbDetailHost host) {
    if (isFusionMode()) return new FusionDetailController(host);
    if (isPlayerMode()) return new PlayerDetailController(host);
    return new EnhancedDetailController(host);
}

// ✅ 其他地方全部通过 modeController 委托
private void someMethod() {
    if (modeController.shouldShowInlinePlayer()) {
        // ...
    }
}

private void onSomeEvent() {
    modeController.onContentLoaded();
}
```

---

### Phase 5：增加单元测试（1 天）

#### 5.1 测试文件

```
app/src/test/java/com/fongmi/android/tv/ui/detail/
├── TmdbDetailModeControllerTest.java
├── FusionDetailControllerTest.java
├── EnhancedDetailControllerTest.java
└── PlayerDetailControllerTest.java
```

#### 5.2 测试要点

```java
// FusionDetailControllerTest.java
package com.fongmi.android.tv.ui.detail;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class FusionDetailControllerTest {
    
    @Mock
    private TmdbDetailHost mockHost;
    
    private FusionDetailController controller;
    
    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new FusionDetailController(mockHost);
    }
    
    @Test
    public void fusionMode_shouldShowInlinePlayer() {
        assertTrue(controller.shouldShowInlinePlayer());
    }
    
    @Test
    public void fusionMode_shouldNotAutoPlay() {
        assertFalse(controller.shouldAutoPlay());
    }
    
    @Test
    public void applyInitialLayout_shouldHideHeroSpacer() {
        // 验证布局设置调用
        controller.applyInitialLayout();
        // 这里需要验证 binding 的调用，具体取决于实现
    }
}

// PlayerDetailControllerTest.java
public class PlayerDetailControllerTest {
    
    @Mock
    private TmdbDetailHost mockHost;
    
    private PlayerDetailController controller;
    
    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new PlayerDetailController(mockHost);
    }
    
    @Test
    public void playerMode_shouldNotShowInlinePlayer() {
        assertFalse(controller.shouldShowInlinePlayer());
    }
    
    @Test
    public void playerMode_shouldAutoPlay() {
        assertTrue(controller.shouldAutoPlay());
    }
    
    @Test
    public void onContentLoaded_shouldStartFullscreenPlayback() {
        controller.onContentLoaded();
        
        verify(mockHost).startFullscreenPlayback();
    }
}
```

#### 5.3 运行测试

```bash
.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "*TmdbDetailModeControllerTest"
.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "*FusionDetailControllerTest"
.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "*EnhancedDetailControllerTest"
.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "*PlayerDetailControllerTest"
```

---

## 📊 时间估算

| 阶段 | 工作内容 | 工作量 | 累计 |
|-----|---------|-------|------|
| Phase 1 | 建立基础框架（4 个文件） | 1-2 天 | 1-2 天 |
| Phase 2 | 迁移融合模式 | 2-3 天 | 3-5 天 |
| Phase 3 | 迁移另外两种模式 | 1-2 天 | 4-7 天 |
| Phase 4 | 消除 Activity 判断 | 1-2 天 | 5-9 天 |
| Phase 5 | 单元测试 | 1 天 | 6-10 天 |

**总计：约 1.5-2 周**

---

## ⚠️ 风险与对策

| 风险 | 表现 | 对策 |
|-----|------|------|
| Host 接口过大 | 方法数快速膨胀，变成 Activity 代理垃圾桶 | 每次只为已迁移代码添加方法，禁止预留暂时不用的方法 |
| 焦点导航被破坏 | TV 端遥控器焦点跳转错误 | 每迁移一组按钮就跑对应布局测试 |
| 三种模式共享逻辑难以收敛 | Base 类越来越空，子类重复代码多 | 先放 Base 类，发现共性后再提取；允许一定重复 |
| 迁移过程中新功能并行开发 | 新功能不知道走新路径还是旧路径 | 新功能先走旧路径，Phase 4 统一替换 |
| 测试覆盖不足 | 回归时发现遗漏 | Phase 2/3 每完成一个模式就手工验收一遍 |
| 旧代码删除过早 | 发现问题无法快速回滚 | Phase 4 前不删除任何旧代码，只标记为 `@Deprecated` |

---

## ✅ 验收标准

### 代码验收
- [ ] `TmdbDetailActivity` 中无 `isFusionMode()` / `isPlayerMode()` / `isCinemaMode()` 散落判断（工厂方法除外）
- [ ] 三种模式的差异集中在各自 Controller 中
- [ ] 新增一个模式时，只需：
  1. 新建一个 `XXXDetailController extends BaseTmdbDetailModeController`
  2. 在工厂方法中加一行
  3. 无需在 Activity 中散落新增大量 `if`
- [ ] `TmdbDetailHost` 接口方法数 ≤ 15 个
- [ ] 单元测试覆盖率 ≥ 80%

### 功能验收
- [ ] 沉浸融合模式：
  - [ ] 进入详情页，内联播放器显示
  - [ ] 点击播放，内联播放器播放
  - [ ] 点击全屏按钮，进入全屏
  - [ ] 全屏返回，恢复内联播放
  - [ ] 主题切换生效
- [ ] 炫彩详情模式：
  - [ ] 进入详情页，标准布局
  - [ ] 点击播放，进入全屏
  - [ ] 主题切换生效
- [ ] 详情直放模式：
  - [ ] 进入详情页，自动开始播放（全屏）
  - [ ] 返回到详情页
- [ ] 通用验收：
  - [ ] 返回键行为不变
  - [ ] 焦点导航正常（TV 端）
  - [ ] 旋转屏幕布局不错乱（mobile 端）

### 测试验收
```bash
# 单元测试全部通过
.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest

# 布局测试全部通过
.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "*TmdbDetailActivityLayoutTest"
```

---

## 📝 实施检查清单

### 开始前
- [ ] 确认第一阶段 `VodPlayerUiController` 已稳定
- [ ] 当前 `TmdbDetailActivity` 无未合并的并行开发分支
- [ ] 备份当前代码到独立分支

### Phase 1
- [ ] 创建 4 个基础文件
- [ ] 编译通过
- [ ] 代码评审通过

### Phase 2
- [ ] `FusionDetailController` 实现完整
- [ ] 旧代码并行保留
- [ ] 融合模式手工验收通过
- [ ] 提交一次可回滚的 commit

### Phase 3
- [ ] `EnhancedDetailController` 和 `PlayerDetailController` 实现完整
- [ ] 三种模式都手工验收通过
- [ ] 提交一次可回滚的 commit

### Phase 4
- [ ] 删除所有旧代码
- [ ] 只在工厂方法中保留 `isFusionMode()` 等判断
- [ ] 全模式回归测试通过
- [ ] 提交最终 commit

### Phase 5
- [ ] 单元测试全部通过
- [ ] 测试覆盖率达标
- [ ] 文档更新（主设计文档标记第二阶段完成）

---

## 🔄 回滚策略

如果在 Phase 4 前发现严重问题：
1. 直接 `git revert` 到 Phase 开始前的 commit
2. 保留 4 个新文件，但不再使用
3. `TmdbDetailActivity` 回到旧逻辑

如果在 Phase 4 后发现问题：
1. 短期：通过 `modeController == null` 条件回退到旧逻辑
2. 中期：修复具体问题，不回滚整个阶段

---

## 📚 参考资料

- [播放器与 TMDB 详情页解耦设计](player-detail-refactor-design.md)
- Android Architecture Components - ViewModel Best Practices
- Clean Architecture - Robert C. Martin

---

**创建者：** Claude Code  
**最后更新：** 2026-07-14
