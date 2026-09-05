# 夜间防爆闪功能设计文档

## 一、需求背景

### 用户痛点
用户反馈:晚上看视频时,画面突然切到高亮场景(白场、雪景、闪光镜头)会造成"爆闪"刺眼,影响观看体验。

### 问题根源分析
- **爆闪的本质**:视频内容本身的高亮度帧,而非手机屏幕背光设置问题
- **系统亮度调节的局限**:手势调亮度只能改变窗口背光,无法压制视频内容的峰值亮度
- **现有功能缺失**:项目已有左右滑动调亮度/音量手势(CustomKeyDown.java:191-197),但缺少对视频内容亮度上限的主动控制

### 业界方案对比

| 方案 | 技术实现 | 防爆闪效果 | 性能开销 | 适配成本 |
|------|---------|-----------|---------|---------|
| 手势调亮度 | WindowManager.LayoutParams.screenBrightness | ❌ 无效(只改背光) | 极低 | 已实现 |
| 窗口亮度上限+暗层 | screenBrightness 天花板 + 半透明黑色 View | ✅ 有效 | 极低 | 低 |
| 护眼/暖色滤镜 | ColorMatrix 降蓝光 | △ 缓解疲劳,对爆闪帮助有限 | 低 | 低 |
| 逐帧削峰(APL检测) | 实时分析每帧亮度+动态调整 | ✅✅ 最佳 | 高(需接管渲染) | 高 |

**结论**:方案2(窗口亮度上限+暗层)性价比最高,技术成熟且成本可控。

---

## 二、功能设计

### 核心方案
**夜间护眼模式**:给播放窗口设置亮度上限,并叠加可调节透明度的暗色滤镜层,压制视频内容的峰值亮度。

### 功能特性
1. **即时生效**:点击按钮立即压暗当前画面,无需重新加载播放
2. **状态持久化**:记住用户上次的开关状态,下次播放时自动应用
3. **三级强度**:
   - 轻度(Low):暗层透明度 15%,亮度上限 0.7
   - 中度(Medium):暗层透明度 25%,亮度上限 0.5 **(默认)**
   - 重度(High):暗层透明度 35%,亮度上限 0.3

4. **智能默认值**:
   - 检测系统深色模式状态(Configuration.UI_MODE_NIGHT_YES)
   - 若系统处于深色模式,播放页启动时默认开启中度护眼
   - 用户手动操作后优先级高于自动判断

### 交互设计

#### 主入口:播放器控制栏按钮
位置:`view_control_vod.xml` 顶部 `top` 布局的右侧按钮组

```xml
<!-- 插入位置:在 cast 和 keep 之间 -->
<androidx.appcompat.widget.AppCompatImageView
    android:id="@+id/nightMode"
    android:layout_width="48dp"
    android:layout_height="48dp"
    android:background="?attr/selectableItemBackgroundBorderless"
    android:contentDescription="@string/play_night_mode"
    android:scaleType="center"
    android:src="@drawable/ic_control_night_mode_off" />
```

**图标设计要求**:
- `ic_control_night_mode_off.xml`: 月亮轮廓(关闭状态)
- `ic_control_night_mode_on.xml`: 实心月亮+星星(开启状态)
- 尺寸:24dp x 24dp,与现有控制栏图标风格一致

#### 辅助入口:设置页快捷开关
位置:`fragment_setting_player.xml`,在 `playerButtons` 项下方新增

```xml
<androidx.appcompat.widget.LinearLayoutCompat
    android:id="@+id/nightModeDefault"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="16dp"
    android:background="@drawable/shape_item"
    android:orientation="horizontal">

    <com.google.android.material.textview.MaterialTextView
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:text="@string/player_night_mode_default"
        android:textColor="@color/white"
        android:textSize="16sp" />

    <com.google.android.material.textview.MaterialTextView
        android:id="@+id/nightModeDefaultText"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:gravity="end"
        android:textColor="@color/white"
        android:textSize="16sp"
        tools:text="跟随系统" />

</androidx.appcompat.widget.LinearLayoutCompat>
```

点击弹出对话框,三选一:
- 始终关闭
- 跟随系统深色模式 **(默认)**
- 始终开启

---

## 三、技术实现

### 3.1 核心类职责划分

#### PlayerSetting.java (存储层)
```java
// 新增方法
public static int getNightModeLevel() {
    return Prefers.getInt("night_mode_level", NIGHT_MODE_OFF);
}

public static void putNightModeLevel(int level) {
    Prefers.put("night_mode_level", level);
}

public static int getNightModeDefault() {
    return Prefers.getInt("night_mode_default", NIGHT_MODE_AUTO);
}

public static void putNightModeDefault(int mode) {
    Prefers.put("night_mode_default", mode);
}

// 常量定义
public static final int NIGHT_MODE_OFF = 0;      // 关闭
public static final int NIGHT_MODE_LOW = 1;      // 轻度
public static final int NIGHT_MODE_MEDIUM = 2;   // 中度
public static final int NIGHT_MODE_HIGH = 3;     // 重度

public static final int NIGHT_MODE_AUTO = 0;     // 跟随系统
public static final int NIGHT_MODE_ALWAYS_OFF = 1;
public static final int NIGHT_MODE_ALWAYS_ON = 2;
```

#### VideoActivity.java (业务层)

**新增字段**:
```java
private View mNightModeOverlay;  // 暗色滤镜层
private int mNightModeLevel = PlayerSetting.NIGHT_MODE_OFF;
```

**生命周期挂载**:
```java
@Override
protected void initView() {
    // ... 现有初始化逻辑 ...
    
    // 创建夜间模式暗层(挂载到 video FrameLayout,控制栏下方、OSD上方)
    mNightModeOverlay = new View(this);
    mNightModeOverlay.setBackgroundColor(Color.BLACK);
    mNightModeOverlay.setVisibility(View.GONE);
    mNightModeOverlay.setClickable(false); // 不拦截触摸事件
    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
    );
    mBinding.video.addView(mNightModeOverlay, 
        mBinding.video.indexOfChild(mBinding.control.getRoot())); // 插入到控制栏之前
    
    // 应用初始状态
    applyNightModeFromDefault();
}

private void applyNightModeFromDefault() {
    int defaultMode = PlayerSetting.getNightModeDefault();
    if (defaultMode == PlayerSetting.NIGHT_MODE_ALWAYS_ON) {
        setNightModeLevel(PlayerSetting.NIGHT_MODE_MEDIUM);
    } else if (defaultMode == PlayerSetting.NIGHT_MODE_AUTO) {
        // 检测系统深色模式
        int nightMode = getResources().getConfiguration().uiMode 
            & Configuration.UI_MODE_NIGHT_MASK;
        if (nightMode == Configuration.UI_MODE_NIGHT_YES) {
            setNightModeLevel(PlayerSetting.NIGHT_MODE_MEDIUM);
        }
    } else {
        // 读取上次手动设置的强度(优先级最高)
        int lastLevel = PlayerSetting.getNightModeLevel();
        if (lastLevel != PlayerSetting.NIGHT_MODE_OFF) {
            setNightModeLevel(lastLevel);
        }
    }
}
```

**核心方法**:
```java
private void setNightModeLevel(int level) {
    mNightModeLevel = level;
    PlayerSetting.putNightModeLevel(level);
    
    if (level == PlayerSetting.NIGHT_MODE_OFF) {
        mNightModeOverlay.setVisibility(View.GONE);
        // 恢复原始亮度策略
        applyBrightness();
    } else {
        // 配置暗层透明度
        float alpha = 0;
        float brightnessLimit = 1.0f;
        switch (level) {
            case PlayerSetting.NIGHT_MODE_LOW:
                alpha = 0.15f;
                brightnessLimit = 0.7f;
                break;
            case PlayerSetting.NIGHT_MODE_MEDIUM:
                alpha = 0.25f;
                brightnessLimit = 0.5f;
                break;
            case PlayerSetting.NIGHT_MODE_HIGH:
                alpha = 0.35f;
                brightnessLimit = 0.3f;
                break;
        }
        mNightModeOverlay.setAlpha(alpha);
        mNightModeOverlay.setVisibility(View.VISIBLE);
        
        // 设置窗口亮度上限
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        float currentBrightness = PlayerSetting.getBrightness();
        if (currentBrightness < 0) currentBrightness = Util.getBrightness(this);
        attributes.screenBrightness = Math.min(currentBrightness, brightnessLimit);
        getWindow().setAttributes(attributes);
    }
    
    // 更新按钮图标
    updateNightModeIcon();
}

private void updateNightModeIcon() {
    int iconRes = mNightModeLevel == PlayerSetting.NIGHT_MODE_OFF 
        ? R.drawable.ic_control_night_mode_off 
        : R.drawable.ic_control_night_mode_on;
    mBinding.control.top.nightMode.setImageResource(iconRes);
}

private void onNightModeClick() {
    // 循环切换:关闭 -> 轻度 -> 中度 -> 重度 -> 关闭
    int nextLevel = (mNightModeLevel + 1) % 4;
    setNightModeLevel(nextLevel);
    
    // Toast 提示
    String[] labels = {"关闭", "轻度护眼", "中度护眼", "重度护眼"};
    Notify.show(labels[nextLevel]);
}
```

**事件绑定**:
```java
@Override
protected void initEvent() {
    // ... 现有事件绑定 ...
    
    mBinding.control.top.nightMode.setOnClickListener(v -> onNightModeClick());
}
```

#### CustomKeyDown.java (手势层兼容)
确保夜间模式不干扰现有亮度手势:

```java
private void setBright(float deltaY) {
    // ... 现有逻辑保持不变 ...
    
    // 夜间模式开启时,手势调亮度仍然有效,但受上限约束
    // WindowManager.LayoutParams.screenBrightness 的值由手势和夜间模式共同决定
    // 无需修改此方法,因为 setNightModeLevel 已在窗口属性层面设置了上限
}
```

---

### 3.2 设置页交互实现

#### SettingPlayerFragment.java

**新增字段**:
```java
private TextView mNightModeDefaultText;
```

**初始化**:
```java
@Override
protected void initView() {
    // ... 现有逻辑 ...
    
    mNightModeDefaultText = mBinding.nightModeDefaultText;
    updateNightModeDefaultText();
}

@Override
protected void initEvent() {
    // ... 现有逻辑 ...
    
    mBinding.nightModeDefault.setOnClickListener(v -> onNightModeDefaultClick());
}

private void updateNightModeDefaultText() {
    int mode = PlayerSetting.getNightModeDefault();
    String[] options = {getString(R.string.player_night_mode_auto),
                       getString(R.string.player_night_mode_always_off),
                       getString(R.string.player_night_mode_always_on)};
    mNightModeDefaultText.setText(options[mode]);
}

private void onNightModeDefaultClick() {
    String[] options = {getString(R.string.player_night_mode_auto),
                       getString(R.string.player_night_mode_always_off),
                       getString(R.string.player_night_mode_always_on)};
    int current = PlayerSetting.getNightModeDefault();
    
    new androidx.appcompat.app.AlertDialog.Builder(requireContext())
        .setTitle(R.string.player_night_mode_default)
        .setSingleChoiceItems(options, current, (dialog, which) -> {
            PlayerSetting.putNightModeDefault(which);
            updateNightModeDefaultText();
            dialog.dismiss();
        })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
}
```

---

### 3.3 资源文件

#### strings.xml
```xml
<!-- 夜间防爆闪相关 -->
<string name="play_night_mode">夜间护眼</string>
<string name="player_night_mode_default">夜间模式默认行为</string>
<string name="player_night_mode_auto">跟随系统深色模式</string>
<string name="player_night_mode_always_off">始终关闭</string>
<string name="player_night_mode_always_on">始终开启</string>
<string name="night_mode_off">护眼关闭</string>
<string name="night_mode_low">轻度护眼</string>
<string name="night_mode_medium">中度护眼</string>
<string name="night_mode_high">重度护眼</string>
```

#### drawable/ic_control_night_mode_off.xml
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M12,3c-4.97,0 -9,4.03 -9,9s4.03,9 9,9 9,-4.03 9,-9c0,-0.46 -0.04,-0.92 -0.1,-1.36 -0.98,1.37 -2.58,2.26 -4.4,2.26 -2.98,0 -5.4,-2.42 -5.4,-5.4 0,-1.81 0.89,-3.42 2.26,-4.4C12.92,3.04 12.46,3 12,3L12,3z"/>
</vector>
```

#### drawable/ic_control_night_mode_on.xml
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M12,3c-4.97,0 -9,4.03 -9,9s4.03,9 9,9 9,-4.03 9,-9c0,-0.46 -0.04,-0.92 -0.1,-1.36 -0.98,1.37 -2.58,2.26 -4.4,2.26 -2.98,0 -5.4,-2.42 -5.4,-5.4 0,-1.81 0.89,-3.42 2.26,-4.4C12.92,3.04 12.46,3 12,3L12,3zM17,8l-0.5,1.5L15,10l1.5,0.5L17,12l0.5,-1.5L19,10l-1.5,-0.5L17,8z"/>
</vector>
```

---

## 四、实现优先级与迭代计划

### MVP版本 (第一期,必需)
- ✅ 控制栏夜间模式按钮(四档循环切换)
- ✅ 暗层叠加 + 窗口亮度上限
- ✅ 状态持久化
- ✅ 跟随系统深色模式自动开启

**工作量估算**:2-3人日

### 增强版本 (第二期,可选)
- 设置页默认行为选项
- 播放器内长按按钮快捷调节强度(弹出滑动条)
- 直播页同步支持(LiveActivity)

**工作量估算**:1-2人日

### 未来扩展 (第三期,待评估)
- 环境光传感器自适应(Sensor.TYPE_LIGHT)
- 暖色滤镜叠加(ColorMatrix调色温)
- 用户自定义暗层透明度和亮度上限

---

## 五、风险评估与兼容性

### 已知兼容性约束
1. **与现有亮度手势共存**:
   - CustomKeyDown 的 setBright 方法通过 WindowManager.LayoutParams 设置亮度
   - 夜间模式通过同一属性设置上限,两者不冲突
   - 用户滑动时仍可调亮度,但不会超过夜间模式设定的上限

2. **暗层层级顺序**:
   - 插入位置:`video` FrameLayout 内,控制栏(`control`)之下,`PlayerView`(`exo`)之上
   - 确保:不遮挡控制栏、不拦截触摸事件(setClickable(false))

3. **多风味(flavor)兼容性**:
   - 布局修改仅涉及 `app/src/mobile/res/layout`,leanback 风味不受影响
   - 若 TV 端需要,可在 leanback 下复制相同结构

### 性能影响
- **暗层渲染**:单个半透明 View,GPU 合成开销可忽略(< 0.1ms/frame)
- **内存占用**:+1 View 对象,约 1KB
- **电量消耗**:降低屏幕峰值亮度,理论上降低功耗

### 测试建议
1. **功能测试**:
   - 四档切换流畅性
   - 状态持久化(杀进程重启)
   - 深色模式自动开启(系统设置切换)
   - 与亮度手势联动(滑动调亮度时上限生效)

2. **兼容性测试**:
   - 不同 Android 版本(API 21-34)
   - 横屏/竖屏切换
   - 全屏/非全屏模式
   - 画中画(PiP)模式下状态保持

3. **边界测试**:
   - 暗层在不同主题下的视觉效果
   - 极低亮度环境下叠加暗层是否过暗
   - 控制栏快速显示/隐藏时暗层闪烁

---

## 六、实现检查清单

### 代码层面
- [ ] `PlayerSetting.java` 新增 4 个常量 + 4 个方法
- [ ] `VideoActivity.java` 新增字段 `mNightModeOverlay` + `mNightModeLevel`
- [ ] `VideoActivity.initView()` 创建暗层并挂载
- [ ] `VideoActivity.applyNightModeFromDefault()` 启动时应用默认策略
- [ ] `VideoActivity.setNightModeLevel()` 实现四档切换逻辑
- [ ] `VideoActivity.onNightModeClick()` 绑定按钮点击
- [ ] `SettingPlayerFragment.java` 新增设置页交互(第二期)

### 资源层面
- [ ] `view_control_vod.xml` 插入夜间模式按钮
- [ ] `fragment_setting_player.xml` 插入默认行为设置项(第二期)
- [ ] `strings.xml` 新增 8 个字符串资源
- [ ] `ic_control_night_mode_off.xml` 图标(月亮轮廓)
- [ ] `ic_control_night_mode_on.xml` 图标(实心月亮+星星)

### 测试层面
- [ ] 单元测试:`PlayerSetting` 存储读取
- [ ] UI 测试:按钮四档循环 + Toast 提示
- [ ] 集成测试:与亮度手势联动
- [ ] 回归测试:不影响现有播放流程

---

## 七、参考资料

### 业界实现
- **哔哩哔哩**:播放器设置 -> 夜间模式(单开关)
- **爱奇艺**:播放器右上角 -> 护眼模式(单开关)
- **MX Player**:设置 -> 播放器 -> 亮度上限(滑动条)
- **VLC Android**:无内置功能,依赖系统夜间模式

### Android API
- [WindowManager.LayoutParams.screenBrightness](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#screenBrightness)
- [Configuration.UI_MODE_NIGHT_MASK](https://developer.android.com/reference/android/content/res/Configuration#UI_MODE_NIGHT_MASK)
- [View.setAlpha()](https://developer.android.com/reference/android/view/View#setAlpha(float))

### 项目现有实现
- 亮度手势:`app/src/mobile/java/com/fongmi/android/tv/ui/custom/CustomKeyDown.java:200-210`
- 亮度持久化:`app/src/main/java/com/fongmi/android/tv/setting/PlayerSetting.java:376-382`
- 控制栏布局:`app/src/mobile/res/layout/view_control_vod.xml`
- Widget 层级:`app/src/mobile/res/layout/activity_video.xml:30-76`

---

## 八、附录:技术细节补充

### 暗层透明度与亮度上限的选择依据
经验值来源:
- **轻度**(15% / 0.7):适用于"有点亮但还能接受"的场景,保留较多画面细节
- **中度**(25% / 0.5):平衡点,既能防爆闪又不过分压暗,推荐默认
- **重度**(35% / 0.3):深夜极暗环境,牺牲部分细节换取舒适度

测试方法:
1. 在暗室中播放测试视频(包含白场、雪景、闪光等高亮场景)
2. 调节暗层透明度和亮度上限组合,记录用户主观舒适度
3. 确保暗层不影响字幕清晰度(白色字幕在黑色暗层上仍可读)

### 为什么不用 ColorMatrix 暖色滤镜
- **ColorMatrix** 可以降蓝光、加暖色调,缓解眼疲劳
- 但对突然爆闪的帮助有限,因为暖色滤镜不改变亮度峰值
- 且 ColorMatrix 需要操作 Paint,对 PlayerView 的侵入性较大
- 第二期可叠加实现(暗层 + 暖色),作为"超级护眼"选项

### 与画中画(PiP)模式的兼容性
- 进入 PiP 时,`mNightModeOverlay` 随 `video` FrameLayout 一起缩放
- 窗口亮度上限在 PiP 模式下失效(PiP 窗口不占用全屏,无法控制系统背光)
- 建议:进入 PiP 时自动关闭夜间模式,退出时恢复(通过 `onPictureInPictureModeChanged` 回调)

---

**文档版本**:v1.0  
**最后更新**:2026-07-14  
**作者**:Claude (Kiro)  
**审核状态**:待审核
