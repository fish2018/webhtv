# 独立语音广告关键词 Provider Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在当前音频去广告运行时中新增独立语音广告 Provider，复用 Sherpa-ONNX 将 PCM 转写为文本，按用户关键词产生候选，并支持默认确认或用户选择自动跳过。

**Architecture:** 新 Provider 与 PCM 指纹、Probe Provider 并行接入 `AdAudioDetectionMultiplexer`，不直接 seek；`AdSkipPolicyController` 按 `providerId` 解析确认/自动策略，`AdSkipCoordinator` 保持唯一 seek authority。语音识别通过窄公共门面复用现有 `RealtimeSubtitleRecognizer`，生命周期继续受 session/generation/timeline token 约束。

**Defaults:** 语音去广默认关闭，模式默认 `PROMPT`，跳过时长范围 `1..120` 秒；禁止记录原始文本和关键词正文。

**Tech Stack:** Android Java、Sherpa-ONNX、`PlaybackMediaSignalHub`、Java records/interfaces、JUnit、ViewBinding、Leanback DPAD、Mobile Fragment、Gradle ARM64 变体、ADB `emulator-5560`。

---

## 文件结构与职责

### 新建生产文件

- `app/src/main/java/com/fongmi/android/tv/ad/audio/SpeechAdKeywordSet.java`：关键词解析、规范化、边界匹配和容量限制。
- `app/src/main/java/com/fongmi/android/tv/ad/audio/SpeechAdConfig.java`：不可变配置快照和安全默认值。
- `app/src/main/java/com/fongmi/android/tv/ad/audio/SpeechAdSetting.java`：`Prefers` 持久化适配器。
- `app/src/main/java/com/fongmi/android/tv/ad/audio/SpeechAdSignalProvider.java`：独立语音广告 Provider。
- `app/src/main/java/com/fongmi/android/tv/subtitle/SpeechRecognitionFactory.java`：去广告侧可注入的识别会话契约。
- `app/src/main/java/com/fongmi/android/tv/subtitle/RealtimeSubtitleSpeechRecognitionFactory.java`：现有 Sherpa 识别器的生产适配器。

### 新建测试文件

- `app/src/test/java/com/fongmi/android/tv/ad/audio/SpeechAdKeywordSetTest.java`
- `app/src/test/java/com/fongmi/android/tv/ad/audio/SpeechAdConfigTest.java`
- `app/src/test/java/com/fongmi/android/tv/ad/audio/SpeechAdSignalProviderTest.java`
- `app/src/test/java/com/fongmi/android/tv/subtitle/RealtimeSubtitleSpeechRecognitionFactoryTest.java`
- `app/src/test/java/com/fongmi/android/tv/ui/activity/SpeechAdSettingSourceTest.java`

### 修改文件

- `app/src/main/java/com/fongmi/android/tv/ad/audio/AdSkipPolicyController.java`：按 Provider 路由模式。
- `app/src/main/java/com/fongmi/android/tv/ad/audio/AdAudioDiagnostics.java`：增加固定低基数语音诊断代码。
- `app/src/test/java/com/fongmi/android/tv/ad/audio/AdSkipPolicyControllerTest.java`：模式隔离回归。
- `app/src/main/java/com/fongmi/android/tv/ad/audio/AdAudioRuntimeController.java`：组合第三个 Provider。
- `app/src/test/java/com/fongmi/android/tv/ad/audio/AdAudioRuntimeControllerTest.java`：三 Provider 生命周期和热更新。
- `app/src/main/java/com/fongmi/android/tv/player/PlayerManager.java`：读取配置并刷新 Runtime。
- `app/src/main/java/com/fongmi/android/tv/subtitle/RealtimeSubtitleRecognizer.java`：仅扩大识别桥所需的包内复用边界，不改变识别算法。
- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/SettingEnhanceActivity.java`
- `app/src/leanback/res/layout/activity_setting_enhance.xml`
- `app/src/mobile/java/com/fongmi/android/tv/ui/fragment/SettingEnhanceFragment.java`
- `app/src/mobile/res/layout/fragment_setting_enhance.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-zh-rCN/strings.xml`
- `app/src/main/res/values-zh-rTW/strings.xml`

---

## Task 1：用失败测试锁定关键词规范化和匹配

**Files:**
- Create: `app/src/test/java/com/fongmi/android/tv/ad/audio/SpeechAdKeywordSetTest.java`
- Create: `app/src/main/java/com/fongmi/android/tv/ad/audio/SpeechAdKeywordSet.java`

- [ ] **Step 1：写失败测试**

```java
package com.fongmi.android.tv.ad.audio;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.List;

public class SpeechAdKeywordSetTest {

    @Test
    public void parseNormalizesMixedSeparatorsAndDeduplicates() {
        SpeechAdKeywordSet set = SpeechAdKeywordSet.parse(" 澳门，赌场;澳门\nＤＯＷＮＬＯＡＤ ");
        assertEquals(List.of("澳门", "赌场", "download"), set.values());
    }

    @Test
    public void chineseKeywordsUseSubstringMatching() {
        SpeechAdKeywordSet set = SpeechAdKeywordSet.parse("首充,提现");
        assertEquals("首充", set.firstMatch("现在完成首充即可提现").orElseThrow());
    }

    @Test
    public void asciiKeywordsRequireWordBoundaries() {
        SpeechAdKeywordSet set = SpeechAdKeywordSet.parse("ad");
        assertTrue(set.firstMatch("an ad starts now").isPresent());
        assertTrue(set.firstMatch("download finished").isEmpty());
    }

    @Test
    public void emptyAndPunctuationOnlyTokensAreDropped() {
        assertTrue(SpeechAdKeywordSet.parse("，；\n---").isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMoreThanConfiguredKeywordCapacity() {
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < 129; i++) value.append("词").append(i).append(',');
        SpeechAdKeywordSet.parse(value.toString());
    }
}
```

- [ ] **Step 2：运行测试，确认因类不存在而失败**

```text
rtk proxy cmd.exe /d /c gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ad.audio.SpeechAdKeywordSetTest --no-daemon --no-build-cache --console=plain
```

Expected: `cannot find symbol SpeechAdKeywordSet` 或测试类编译失败。

- [ ] **Step 3：实现最小关键词集合**

实现以下公开契约；容量固定为 128 条、单条 64 个字符、总输入 8192 个字符：

```java
package com.fongmi.android.tv.ad.audio;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public final class SpeechAdKeywordSet {
    static final int MAX_KEYWORDS = 128;
    static final int MAX_KEYWORD_LENGTH = 64;
    static final int MAX_INPUT_LENGTH = 8_192;

    private final List<String> values;
    private final List<Pattern> asciiPatterns;

    private SpeechAdKeywordSet(List<String> values) {
        this.values = List.copyOf(values);
        this.asciiPatterns = values.stream()
                .map(SpeechAdKeywordSet::asciiPattern)
                .toList();
    }

    public static SpeechAdKeywordSet parse(String input) {
        String source = input == null ? "" : input;
        if (source.length() > MAX_INPUT_LENGTH) throw new IllegalArgumentException("keyword input too long");
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String token : source.split("[,，;；\\r\\n]+")) {
            String value = normalize(token);
            if (value.isEmpty() || value.codePoints().noneMatch(Character::isLetterOrDigit)) continue;
            if (value.length() > MAX_KEYWORD_LENGTH) throw new IllegalArgumentException("keyword too long");
            unique.add(value);
            if (unique.size() > MAX_KEYWORDS) throw new IllegalArgumentException("too many keywords");
        }
        return new SpeechAdKeywordSet(new ArrayList<>(unique));
    }

    public List<String> values() { return values; }
    public boolean isEmpty() { return values.isEmpty(); }

    public Optional<String> firstMatch(String recognizedText) {
        String text = normalize(recognizedText);
        for (int i = 0; i < values.size(); i++) {
            Pattern ascii = asciiPatterns.get(i);
            if (ascii != null ? ascii.matcher(text).find() : text.contains(values.get(i))) {
                return Optional.of(values.get(i));
            }
        }
        return Optional.empty();
    }

    static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .trim().toLowerCase(Locale.ROOT);
    }

    private static Pattern asciiPattern(String value) {
        boolean ascii = value.codePoints().allMatch(cp -> cp < 128 && Character.isLetterOrDigit(cp));
        return ascii ? Pattern.compile("(?<![a-z0-9])" + Pattern.quote(value) + "(?![a-z0-9])") : null;
    }
}
```

- [ ] **Step 4：运行测试确认通过**

重复 Step 2，Expected: `BUILD SUCCESSFUL`，5 项测试通过。

- [ ] **Step 5：提交**

```text
rtk git add app/src/main/java/com/fongmi/android/tv/ad/audio/SpeechAdKeywordSet.java app/src/test/java/com/fongmi/android/tv/ad/audio/SpeechAdKeywordSetTest.java
rtk git commit -m "feat: add speech ad keyword matching"
```

## Task 2：定义语音广告配置和安全默认值

**Files:**
- Create: `app/src/test/java/com/fongmi/android/tv/ad/audio/SpeechAdConfigTest.java`
- Create: `app/src/main/java/com/fongmi/android/tv/ad/audio/SpeechAdConfig.java`
- Create: `app/src/main/java/com/fongmi/android/tv/ad/audio/SpeechAdSetting.java`

- [ ] **Step 1：写失败测试**

```java
package com.fongmi.android.tv.ad.audio;

import static org.junit.Assert.*;

import org.junit.Test;

public class SpeechAdConfigTest {

    @Test
    public void defaultsAreDisabledPromptAndFifteenSeconds() {
        SpeechAdConfig config = SpeechAdConfig.defaults();
        assertFalse(config.enabled());
        assertEquals(AdSkipPolicyController.Mode.PROMPT, config.mode());
        assertEquals(15, config.skipSeconds());
        assertFalse(config.keywords().isEmpty());
    }

    @Test
    public void unsafeDurationIsClamped() {
        assertEquals(1, SpeechAdConfig.create(true, "赌场", 0, "PROMPT").skipSeconds());
        assertEquals(120, SpeechAdConfig.create(true, "赌场", 999, "AUTO").skipSeconds());
    }

    @Test
    public void unknownModeFallsBackToPrompt() {
        assertEquals(AdSkipPolicyController.Mode.PROMPT,
                SpeechAdConfig.create(true, "赌场", 15, "UNKNOWN").mode());
    }
}
```

- [ ] **Step 2：运行测试确认失败**

```text
rtk proxy cmd.exe /d /c gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ad.audio.SpeechAdConfigTest --no-daemon --no-build-cache --console=plain
```

Expected: `SpeechAdConfig` 不存在。

- [ ] **Step 3：实现配置快照和 Prefers 适配器**

```java
package com.fongmi.android.tv.ad.audio;

public record SpeechAdConfig(boolean enabled, SpeechAdKeywordSet keywords,
                             int skipSeconds, AdSkipPolicyController.Mode mode) {
    public static final String DEFAULT_KEYWORDS =
            "麻将来了,澳门,赌场,娱乐城,荷官,百家乐,老虎机,时时彩,六合彩,彩票,下注,投注,首充,提现,棋牌,捕鱼,斗地主";

    public SpeechAdConfig {
        if (keywords == null) throw new NullPointerException("keywords");
        skipSeconds = Math.max(1, Math.min(120, skipSeconds));
        if (mode == null) mode = AdSkipPolicyController.Mode.PROMPT;
    }

    public static SpeechAdConfig defaults() {
        return create(false, DEFAULT_KEYWORDS, 15, "PROMPT");
    }

    public static SpeechAdConfig create(boolean enabled, String keywords,
                                        int skipSeconds, String mode) {
        AdSkipPolicyController.Mode parsed;
        try { parsed = AdSkipPolicyController.Mode.valueOf(mode); }
        catch (RuntimeException ignored) { parsed = AdSkipPolicyController.Mode.PROMPT; }
        return new SpeechAdConfig(enabled, SpeechAdKeywordSet.parse(keywords), skipSeconds, parsed);
    }
}
```

`SpeechAdSetting` 必须只负责四个稳定 key，并返回不可变快照：

```java
public final class SpeechAdSetting {
    private static final String KEY_ENABLED = "speech_ad_enabled";
    private static final String KEY_KEYWORDS = "speech_ad_keywords";
    private static final String KEY_SKIP_SECONDS = "speech_ad_skip_seconds";
    private static final String KEY_SKIP_MODE = "speech_ad_skip_mode";

    public static SpeechAdConfig snapshot() {
        return SpeechAdConfig.create(
                Prefers.getBoolean(KEY_ENABLED, false),
                Prefers.getString(KEY_KEYWORDS, SpeechAdConfig.DEFAULT_KEYWORDS),
                Prefers.getInt(KEY_SKIP_SECONDS, 15),
                Prefers.getString(KEY_SKIP_MODE, AdSkipPolicyController.Mode.PROMPT.name()));
    }

    public static void setEnabled(boolean value) { Prefers.put(KEY_ENABLED, value); }
    public static void setKeywords(String value) { Prefers.put(KEY_KEYWORDS, String.join(",", SpeechAdKeywordSet.parse(value).values())); }
    public static void setSkipSeconds(int value) { Prefers.put(KEY_SKIP_SECONDS, Math.max(1, Math.min(120, value))); }
    public static void setMode(AdSkipPolicyController.Mode value) { Prefers.put(KEY_SKIP_MODE, value.name()); }

    private SpeechAdSetting() {}
}
```

- [ ] **Step 4：运行 Task 1–2 测试**

```text
rtk proxy cmd.exe /d /c gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ad.audio.SpeechAdKeywordSetTest --tests com.fongmi.android.tv.ad.audio.SpeechAdConfigTest --no-daemon --no-build-cache --console=plain
```

Expected: 全部通过。

- [ ] **Step 5：提交**

```text
rtk git add app/src/main/java/com/fongmi/android/tv/ad/audio/SpeechAdConfig.java app/src/main/java/com/fongmi/android/tv/ad/audio/SpeechAdSetting.java app/src/test/java/com/fongmi/android/tv/ad/audio/SpeechAdConfigTest.java
rtk git commit -m "feat: persist speech ad settings"
```

## Task 3：让跳过策略按 Provider 隔离

**Files:**
- Modify: `app/src/main/java/com/fongmi/android/tv/ad/audio/AdSkipPolicyController.java`
- Modify: `app/src/test/java/com/fongmi/android/tv/ad/audio/AdSkipPolicyControllerTest.java`

- [ ] **Step 1：先写 provider-aware 失败测试**

```java
@Test
public void speechAutoDoesNotChangePcmOrProbePolicy() {
    List<String> prompted = new ArrayList<>();
    List<String> automated = new ArrayList<>();
    AdSkipPolicyController policy = controller(prompted, automated);
    policy.setModeResolver(provider -> "speech".equals(provider)
            ? AdSkipPolicyController.Mode.AUTO
            : AdSkipPolicyController.Mode.PROMPT);

    policy.onCandidate(candidate("speech", "speech-keyword", 1_000L, 16_000L));
    policy.onCandidate(candidate("pcm", "fingerprint", 20_000L, 30_000L));
    policy.onCandidate(candidate("probe", "probe-rule", 40_000L, 50_000L));

    assertEquals(List.of("pcm", "probe"), prompted);
    assertEquals(List.of("speech"), automated);
}

@Test
public void unknownProviderDefaultsToPrompt() {
    AdSkipPolicyController policy = controller(prompted, automated);
    policy.setModeResolver(provider -> null);
    policy.onCandidate(candidate("future", "x", 1_000L, 2_000L));
    assertEquals(1, prompted.size());
    assertTrue(automated.isEmpty());
}
```

- [ ] **Step 2：运行测试，确认当前全局模式导致失败**

```text
rtk proxy cmd.exe /d /c gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ad.audio.AdSkipPolicyControllerTest --no-daemon --no-build-cache --console=plain
```

Expected: `setModeResolver` 不存在。

- [ ] **Step 3：实现最小 ModeResolver**

在 `AdSkipPolicyController` 增加：

```java
@FunctionalInterface
public interface ModeResolver {
    Mode modeFor(String providerId);
}

private ModeResolver modeResolver = ignored -> mode;

public synchronized void setModeResolver(ModeResolver resolver) {
    modeResolver = Objects.requireNonNull(resolver, "resolver");
    modeSwitches++;
}

private Mode resolvedMode(AdAudioSignalProvider.AdAudioCandidate candidate) {
    Mode resolved;
    try { resolved = modeResolver.modeFor(candidate.providerId()); }
    catch (RuntimeException ignored) { resolved = null; }
    return resolved == null ? Mode.PROMPT : resolved;
}
```

把新候选的决策从 `mode == Mode.PROMPT` 改为：

```java
Decision decision = resolvedMode(candidate) == Mode.PROMPT
        ? Decision.PROMPTED : Decision.AUTO_APPLIED;
```

保留 `setMode(Mode)` 作为 pcm/probe 默认模式兼容入口；历史 `decisions` 不清空、不重放。

- [ ] **Step 4：运行策略测试和现有 Runtime 测试**

```text
rtk proxy cmd.exe /d /c gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ad.audio.AdSkipPolicyControllerTest --tests com.fongmi.android.tv.ad.audio.AdAudioRuntimeControllerTest --no-daemon --no-build-cache --console=plain
```

Expected: 新旧测试全部通过。

- [ ] **Step 5：提交**

```text
rtk git add app/src/main/java/com/fongmi/android/tv/ad/audio/AdSkipPolicyController.java app/src/test/java/com/fongmi/android/tv/ad/audio/AdSkipPolicyControllerTest.java
rtk git commit -m "feat: route ad skip policy by provider"
```
## Task 4：建立可注入的 Sherpa 识别门面

**Files:**
- Create: `app/src/main/java/com/fongmi/android/tv/subtitle/SpeechRecognitionFactory.java`
- Create: `app/src/main/java/com/fongmi/android/tv/subtitle/RealtimeSubtitleSpeechRecognitionFactory.java`
- Create: `app/src/test/java/com/fongmi/android/tv/subtitle/RealtimeSubtitleSpeechRecognitionFactoryTest.java`
- Modify: `app/src/main/java/com/fongmi/android/tv/subtitle/RealtimeSubtitleRecognizer.java`

- [ ] **Step 1：写失败测试，锁定不下载模型的工厂行为**

```java
package com.fongmi.android.tv.subtitle;

import static org.junit.Assert.*;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class RealtimeSubtitleSpeechRecognitionFactoryTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void missingModelIsReportedWithoutCreatingRecognizer() throws Exception {
        RealtimeSubtitleSpeechRecognitionFactory factory =
                RealtimeSubtitleSpeechRecognitionFactory.forRoot(temporary.newFolder());
        assertFalse(factory.isReady());
    }

    @Test
    public void contractExposesResetAcceptAndClose() {
        assertNotNull(SpeechRecognitionFactory.Session.class);
        assertNotNull(SpeechRecognitionFactory.Listener.class);
    }
}
```

- [ ] **Step 2：运行测试确认失败**

```text
rtk proxy cmd.exe /d /c gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.subtitle.RealtimeSubtitleSpeechRecognitionFactoryTest --no-daemon --no-build-cache --console=plain
```

Expected: 两个新类型不存在。

- [ ] **Step 3：实现窄公共契约和生产适配器**

```java
package com.fongmi.android.tv.subtitle;

public interface SpeechRecognitionFactory {
    interface Listener {
        void onResult(String text, long startUs, long endUs, int timelineToken);
        void onError(Throwable error);
    }

    interface Session extends AutoCloseable {
        void accept(float[] samples, long startUs, long endUs, int timelineToken);
        void reset();
        @Override void close();
    }

    boolean isReady();
    Session create(Listener listener);
}
```

`RealtimeSubtitleSpeechRecognitionFactory` 必须：

- 默认根目录为 `new File(App.get().getFilesDir(), "realtime_subtitle")`；
- 模型来自 `RealtimeSubtitleModelCatalog.find(Setting.getRealtimeSubtitleModel())`；
- `isReady()` 对模型文件和共享 VAD 文件调用现有 `RealtimeSubtitleModelVerifier.isVerified(...)`；
- 提供 `public static boolean isSelectedModelReady()` 给设置页显示状态，该方法只做文件校验，不下载、不加载 JNI；
- `create()` 在未就绪时抛出 `IllegalStateException("speech model is not ready")`；
- 在同一 `subtitle` 包中调用 package-private `RealtimeSubtitleRecognizer.create(...)`；
- 返回的 `Session.close()` 只调用一次 `recognizer.release()`。

核心适配代码：

```java
RealtimeSubtitleRecognizer recognizer = RealtimeSubtitleRecognizer.create(
        modelDirectory(spec), vadFile(), spec, new RealtimeSubtitleRecognizer.Listener() {
            @Override public void onResult(String text, long startUs, long endUs, int token) {
                listener.onResult(text, startUs, endUs, token);
            }
            @Override public void onError(Throwable error) { listener.onError(error); }
        });
return new SpeechRecognitionFactory.Session() {
    private boolean closed;
    @Override public void accept(float[] samples, long startUs, long endUs, int token) {
        if (!closed) recognizer.accept(samples, startUs, endUs, token);
    }
    @Override public void reset() { if (!closed) recognizer.reset(); }
    @Override public void close() {
        if (closed) return;
        closed = true;
        recognizer.release();
    }
};
```

`RealtimeSubtitleRecognizer` 只补充必要的 package-private 测试/适配入口；不得修改 VAD、在线/离线识别算法、队列容量或字幕现有调用语义。

- [ ] **Step 4：运行工厂、Recognizer 和实时字幕测试**

```text
rtk proxy cmd.exe /d /c gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.subtitle.RealtimeSubtitleSpeechRecognitionFactoryTest --tests com.fongmi.android.tv.subtitle.RealtimeSubtitleRecognizerTest --tests com.fongmi.android.tv.subtitle.RealtimeSubtitleMediaConsumerTest --no-daemon --no-build-cache --console=plain
```

Expected: 全部通过，且测试不下载模型、不加载 JNI。

- [ ] **Step 5：提交**

```text
rtk git add app/src/main/java/com/fongmi/android/tv/subtitle/SpeechRecognitionFactory.java app/src/main/java/com/fongmi/android/tv/subtitle/RealtimeSubtitleSpeechRecognitionFactory.java app/src/main/java/com/fongmi/android/tv/subtitle/RealtimeSubtitleRecognizer.java app/src/test/java/com/fongmi/android/tv/subtitle/RealtimeSubtitleSpeechRecognitionFactoryTest.java
rtk git commit -m "refactor: expose reusable speech recognition sessions"
```

## Task 5：TDD 实现独立语音广告 Provider

**Files:**
- Create: `app/src/test/java/com/fongmi/android/tv/ad/audio/SpeechAdSignalProviderTest.java`
- Create: `app/src/main/java/com/fongmi/android/tv/ad/audio/SpeechAdSignalProvider.java`

- [ ] **Step 1：写 Provider 失败测试**

测试使用直接 executor、真实 `PlaybackMediaSignalHub` 和 fake `SpeechRecognitionFactory`，至少包含：

```java
@Test
public void keywordMatchEmitsBoundedSpeechCandidate() {
    FakeRecognizerFactory recognizer = new FakeRecognizerFactory(true);
    SpeechAdConfig config = SpeechAdConfig.create(true, "赌场", 15, "PROMPT");
    SpeechAdSignalProvider provider = provider(recognizer, () -> config);
    List<AdAudioSignalProvider.AdAudioCandidate> emitted = new ArrayList<>();

    provider.setEnabled(true);
    provider.start(context(7L, 2L), rules("v1"), listener(emitted));
    provider.onHostPosition(new AdAudioSignalProvider.HostPosition(
            7L, 2L, 10_000L, 20_000L, true, false));
    hub.publishPcm(hub.session().frame(new float[]{0.1f, 0.2f}, 16_000, 10_000L));
    recognizer.emit("欢迎来到赌场", 10_000_000L, 10_500_000L, recognizer.timelineToken());

    assertEquals(1, emitted.size());
    AdAudioSignalProvider.AdAudioCandidate candidate = emitted.get(0);
    assertEquals(SpeechAdSignalProvider.ID, candidate.providerId());
    assertEquals(SpeechAdSignalProvider.RULE_ID, candidate.ruleId());
    assertEquals(10_000L, candidate.startMs());
    assertEquals(20_000L, candidate.endMs());
}

@Test
public void providerDoesNotRunWithoutModelKeywordsOrSeekableVod() {
    assertEquals(AdAudioSignalProvider.ProviderState.DEGRADED,
            startedProvider(false, config(true, "赌场"), seekableVod()).state());
    assertNotEquals(AdAudioSignalProvider.ProviderState.RUNNING,
            startedProvider(true, config(true, ""), seekableVod()).state());
    assertNotEquals(AdAudioSignalProvider.ProviderState.RUNNING,
            startedProvider(true, config(true, "赌场"), livePosition()).state());
}

@Test
public void resetDropsLateRecognizerCallback() {
    SpeechAdSignalProvider provider = runningProvider();
    int staleToken = recognizer.timelineToken();
    provider.onTimelineReset(new AdAudioSignalProvider.TimelineReset(
            7L, 3L, AdAudioSignalProvider.ResetReason.SEEK, 30_000L));
    recognizer.emit("赌场", 1L, 2L, staleToken);
    assertTrue(emitted.isEmpty());
}

@Test
public void cooldownSuppressesPartialAndFinalDuplicates() {
    SpeechAdSignalProvider provider = runningProvider();
    recognizer.emit("赌场", 1L, 2L, token);
    recognizer.emit("欢迎来到赌场", 2L, 3L, token);
    assertEquals(1, emitted.size());
}
```

还要覆盖：PCM session/generation 错配、媒体尾部钳制、识别器异常、listener 异常、邮箱淘汰、幂等 close 和 capture lease 释放。

- [ ] **Step 2：运行测试确认失败**

```text
rtk proxy cmd.exe /d /c gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ad.audio.SpeechAdSignalProviderTest --no-daemon --no-build-cache --console=plain
```

Expected: `SpeechAdSignalProvider` 不存在。

- [ ] **Step 3：实现 Provider 的最小闭环**

公开契约固定为：

```java
public final class SpeechAdSignalProvider implements AdAudioSignalProvider {
    public static final String ID = "speech";
    public static final String RULE_ID = "speech-keyword";
    static final long MATCH_COOLDOWN_MS = 30_000L;

    public interface ConfigSource { SpeechAdConfig snapshot(); }

    public SpeechAdSignalProvider(PlaybackMediaSignalHub hub,
                                  SpeechRecognitionFactory recognizerFactory,
                                  ConfigSource configSource,
                                  Executor worker,
                                  AdAudioDiagnostics diagnostics) {
    }
}
```

实现必须遵守：

1. `start()` 保存 `SessionContext` 和 `rules.version()`，注册 ID 为 `speech-ad` 的 Hub consumer，并仅在模型、关键词和 HostPosition 条件满足后创建识别会话；
2. 使用 `PlaybackMediaSignalHub.ConsumerKind.AD_AUDIO` 申请捕获租约；
3. `onPcm` 校验 frame session/generation，将 frame 放入固定容量邮箱，由 worker 调用 `PlaybackMediaAudioProcessor.resample(..., 16_000)`；
4. 识别回调再次检查 provider instance token、session、generation、timeline token；
5. 命中时读取最新 HostPosition，候选区间为 `[positionMs, min(durationMs, positionMs + skipSeconds * 1000L)]`；
6. `ruleVersion` 必须等于 `start()` 收到的规则版本，`providerId/RULE_ID` 使用常量，不输出原始文本；
7. 同一 Provider 的任意关键词命中使用 30 秒冷却；
8. `onTimelineReset` 清空邮箱、冷却和 HostPosition，提升 token，并调用识别会话 `reset()`；
9. `close()` 顺序为标记关闭、注销 registration、关闭 capture lease、清邮箱、关闭 session；
10. 所有 listener/recognizer 异常转为固定诊断计数，不向 Hub 音频线程抛出。

`AdAudioDiagnostics.Code` 增加 `SPEECH_MODEL_UNAVAILABLE`、`SPEECH_START_FAILED`、`SPEECH_TEXT_EMPTY`、`SPEECH_MATCHED`、`SPEECH_COOLDOWN`、`SPEECH_STALE_CALLBACK`；测试至少断言模型缺失、命中、冷却和旧回调计数。

- [ ] **Step 4：运行 Provider、Hub 和 Recognizer 测试**

```text
rtk proxy cmd.exe /d /c gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ad.audio.SpeechAdSignalProviderTest --tests com.fongmi.android.tv.player.audio.PlaybackMediaSignalHubTest --tests com.fongmi.android.tv.subtitle.RealtimeSubtitleRecognizerTest --no-daemon --no-build-cache --console=plain
```

Expected: 全部通过。

- [ ] **Step 5：提交**

```text
rtk git add app/src/main/java/com/fongmi/android/tv/ad/audio/SpeechAdSignalProvider.java app/src/main/java/com/fongmi/android/tv/ad/audio/AdAudioDiagnostics.java app/src/test/java/com/fongmi/android/tv/ad/audio/SpeechAdSignalProviderTest.java
rtk git commit -m "feat: add speech keyword ad provider"
```

## Task 6：把第三个 Provider 接入 Runtime 和 Multiplexer

**Files:**
- Modify: `app/src/main/java/com/fongmi/android/tv/ad/audio/AdAudioRuntimeController.java`
- Modify: `app/src/test/java/com/fongmi/android/tv/ad/audio/AdAudioRuntimeControllerTest.java`
- Modify: `app/src/test/java/com/fongmi/android/tv/ad/audio/AdAudioDetectionMultiplexerTest.java`

- [ ] **Step 1：写三 Provider 组合失败测试**

```java
@Test
public void speechRunsWithoutFingerprintRulesAndUsesOwnMode() {
    FakeProvider pcm = new FakeProvider("pcm");
    FakeProvider probe = new FakeProvider("probe");
    FakeProvider speech = new FakeProvider("speech");
    SpeechAdConfig speechConfig = SpeechAdConfig.create(true, "赌场", 15, "AUTO");
    AdAudioRuntimeController runtime = runtime(emptyRules(), pcm, probe, speech);

    runtime.setSpeechConfig(speechConfig);
    runtime.start(false);
    runtime.bindUi(ui);

    assertEquals(AdAudioSignalProvider.ProviderState.RUNNING, speech.state());
    speech.emit(providerCandidate(session, SpeechAdSignalProvider.RULE_ID,
            10_000L, 25_000L, "speech"));
    assertEquals(List.of(25_000L), playback.seekTargets);
    assertEquals(0, ui.candidateShows);
}

@Test
public void speechFailureDoesNotStopPcmOrProbe() {
    speech.failOnStart = true;
    runtime.setSpeechConfig(enabledSpeech());
    runtime.start(true);
    runtime.bindUi(ui);
    assertTrue(pcm.started);
    assertTrue(probe.started);
}

@Test
public void speechModeSwitchAffectsOnlyFutureSpeechCandidates() {
    runtime.setSpeechConfig(promptSpeech());
    speech.emit(candidateAt(1_000L));
    runtime.setSpeechConfig(autoSpeech());
    speech.emit(candidateAt(30_000L));
    assertEquals(1, ui.candidateShows);
    assertEquals(1, playback.seekTargets.size());
}
```

Multiplexer 测试必须确认 `speech-keyword` 在允许集合中时通过，其他未知 ruleId 仍拒绝。

- [ ] **Step 2：运行 Runtime/Mux 测试确认失败**

```text
rtk proxy cmd.exe /d /c gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ad.audio.AdAudioRuntimeControllerTest --tests com.fongmi.android.tv.ad.audio.AdAudioDetectionMultiplexerTest --no-daemon --no-build-cache --console=plain
```

Expected: Runtime 没有 speech factory/config/provider API。

- [ ] **Step 3：最小扩展 Runtime**

新增工厂和状态：

```java
@FunctionalInterface
public interface SpeechProviderFactory {
    AdAudioSignalProvider create();
}

private final SpeechProviderFactory speechProviderFactory;
private SpeechAdConfig speechConfig = SpeechAdConfig.defaults();
private AdAudioSignalProvider speechProvider;

public synchronized void setSpeechConfig(SpeechAdConfig config) {
    if (closed) return;
    SpeechAdConfig next = Objects.requireNonNull(config, "config");
    boolean rebuild = !next.equals(speechConfig);
    speechConfig = next;
    if (policy != null) installModeResolver(policy);
    if (rebuild) reconfigureLocked();
}
```

`refreshLocked()` 改为分别计算：

```java
boolean fingerprintReady = enabled && !snapshot.hasError() && snapshot.hasRules();
boolean speechReady = speechConfig.enabled() && !speechConfig.keywords().isEmpty();
if (ui == null || (!fingerprintReady && !speechReady)) {
    deactivateLocked();
    return;
}
```

激活时：

- allowed rule IDs 为 fingerprint IDs 加上 `SpeechAdSignalProvider.RULE_ID`（仅 speechReady 时）；
- pcm/probe 只在 fingerprintReady 时启用；
- speech 只在 speechReady 时启用；
- `installModeResolver(policy)` 返回 speechConfig.mode() 或现有 skipMode；
- timeline reset、HostPosition、deactivate、close 对三个 Provider 对称处理；
- 任一 Provider 失败只关闭自身；
- 空指纹规则时创建 `routingSnapshot`：若 `snapshot.version()` 为空，则复制 snapshot 的 sourceId/ruleSet/warnings/lastError/probeSidecar 并把版本设为 `speech-runtime-v1`；policy、mux 和全部 `startProvider` 都必须使用该 `routingSnapshot`，保证 candidate 版本一致。

模式路由代码：

```java
private void installModeResolver(AdSkipPolicyController target) {
    target.setMode(skipMode);
    target.setModeResolver(providerId -> SpeechAdSignalProvider.ID.equals(providerId)
            ? speechConfig.mode() : skipMode);
}
```

- [ ] **Step 4：运行音频 Runtime 关键回归**

```text
rtk proxy cmd.exe /d /c gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ad.audio.AdAudioRuntimeControllerTest --tests com.fongmi.android.tv.ad.audio.AdAudioDetectionMultiplexerTest --tests com.fongmi.android.tv.ad.audio.AdSkipPolicyControllerTest --tests com.fongmi.android.tv.ad.audio.SpeechAdSignalProviderTest --no-daemon --no-build-cache --console=plain
```

Expected: 全部通过。

- [ ] **Step 5：提交**

```text
rtk git add app/src/main/java/com/fongmi/android/tv/ad/audio/AdAudioRuntimeController.java app/src/test/java/com/fongmi/android/tv/ad/audio/AdAudioRuntimeControllerTest.java app/src/test/java/com/fongmi/android/tv/ad/audio/AdAudioDetectionMultiplexerTest.java
rtk git commit -m "feat: compose speech ad provider at runtime"
```
## Task 7：在 PlayerManager 中加载配置并实时刷新

**Files:**
- Modify: `app/src/main/java/com/fongmi/android/tv/player/PlayerManager.java`
- Modify: `app/src/main/java/com/fongmi/android/tv/ad/audio/AdAudioRuntimeController.java`
- Modify: `app/src/test/java/com/fongmi/android/tv/ad/audio/AdAudioRuntimeControllerTest.java`

- [ ] **Step 1：写生产组合失败测试**

在 Runtime 测试中增加构造器契约：生产构造器接收 `SpeechRecognitionFactory`，设置刷新不会重放旧候选。

```java
@Test
public void replacingSpeechConfigClosesOldProviderBeforeStartingNext() {
    runtime.setSpeechConfig(SpeechAdConfig.create(true, "赌场", 15, "PROMPT"));
    runtime.start(false);
    runtime.bindUi(ui);
    FakeProvider first = createdSpeechProviders.get(0);

    runtime.setSpeechConfig(SpeechAdConfig.create(true, "首充", 30, "AUTO"));

    assertTrue(first.closed);
    assertEquals(2, createdSpeechProviders.size());
    first.emit(candidateAt(10_000L));
    assertTrue(playback.seekTargets.isEmpty());
}
```

- [ ] **Step 2：运行测试确认失败**

```text
rtk proxy cmd.exe /d /c gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ad.audio.AdAudioRuntimeControllerTest --no-daemon --no-build-cache --console=plain
```

Expected: 生产 recognition factory 构造器或刷新行为不存在。

- [ ] **Step 3：接入 PlayerManager**

`AdAudioRuntimeController` 增加生产构造器：

```java
public AdAudioRuntimeController(PlaybackMediaSignalHub hub, PlaybackMediaClock clock,
                                AdAudioRuleSource ruleSource, PlaybackPort playback,
                                SpeechRecognitionFactory recognitionFactory) {
    this(hub, clock, ruleSource, playback, createWorker(), recognitionFactory);
}
```

内部 `SpeechProviderFactory` 使用 Runtime 自己的 `worker/diagnostics` 创建：

```java
() -> new SpeechAdSignalProvider(
        hub, recognitionFactory, () -> speechConfig, worker, diagnostics)
```

`PlayerManager` 构造时传入：

```java
new RealtimeSubtitleSpeechRecognitionFactory()
```

集中刷新方法：

```java
private void configureAdAudioRuntime() {
    adAudioRuntime.setSkipMode(AdAudioSetting.isAutoSkipEnabled()
            ? AdSkipPolicyController.Mode.AUTO
            : AdSkipPolicyController.Mode.PROMPT);
    adAudioRuntime.setSpeechConfig(SpeechAdSetting.snapshot());
    adAudioRuntime.start(AdAudioSetting.isEnabled());
}

public void reloadAdAudioSettings() {
    if (isReleased()) return;
    configureAdAudioRuntime();
    refreshAdAudioRuntime();
}
```

把 `bindAdAudioUi()`、`reloadAdAudioRules()` 和初始化路径统一调用 `configureAdAudioRuntime()`；保留 `reloadAdAudioRules()` 兼容方法并委托 `reloadAdAudioSettings()`。不得让 Runtime 自己读取 `Prefers`。

- [ ] **Step 4：运行 Runtime、PlayerManager 相关回归**

```text
rtk proxy cmd.exe /d /c gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ad.audio.AdAudioRuntimeControllerTest --tests com.fongmi.android.tv.player.audio.PlaybackMediaAudioPipelineTest --tests com.fongmi.android.tv.player.audio.PlaybackMediaSessionControllerTest --no-daemon --no-build-cache --console=plain
```

Expected: 全部通过。

- [ ] **Step 5：提交**

```text
rtk git add app/src/main/java/com/fongmi/android/tv/player/PlayerManager.java app/src/main/java/com/fongmi/android/tv/ad/audio/AdAudioRuntimeController.java app/src/test/java/com/fongmi/android/tv/ad/audio/AdAudioRuntimeControllerTest.java
rtk git commit -m "feat: refresh speech ad runtime settings"
```

## Task 8：恢复 Leanback 的四项语音去广设置

**Files:**
- Modify: `app/src/leanback/java/com/fongmi/android/tv/ui/activity/SettingEnhanceActivity.java`
- Modify: `app/src/leanback/res/layout/activity_setting_enhance.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Modify: `app/src/main/java/com/fongmi/android/tv/ui/dialog/AdSkipPromptPresenter.java`
- Create: `app/src/test/java/com/fongmi/android/tv/ui/activity/SpeechAdSettingSourceTest.java`

- [ ] **Step 1：写 Leanback 源码/布局失败测试**

```java
package com.fongmi.android.tv.ui.activity;

import static org.junit.Assert.*;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class SpeechAdSettingSourceTest {
    @Test
    public void leanbackExposesAllSpeechAdControls() throws Exception {
        String java = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/SettingEnhanceActivity.java");
        String xml = read("app/src/leanback/res/layout/activity_setting_enhance.xml");
        assertTrue(xml.contains("@+id/speechAdEnabled"));
        assertTrue(xml.contains("@+id/speechAdKeywords"));
        assertTrue(xml.contains("@+id/speechAdSkipSeconds"));
        assertTrue(xml.contains("@+id/speechAdSkipMode"));
        assertTrue(java.contains("SpeechAdSetting.setEnabled"));
        assertTrue(java.contains("SpeechAdSetting.setKeywords"));
        assertTrue(java.contains("SpeechAdSetting.setSkipSeconds"));
        assertTrue(java.contains("SpeechAdSetting.setMode"));
        assertTrue(java.contains("reloadAdAudioSettings"));
    }

    private static String read(String path) throws Exception {
        Path direct = Path.of(path);
        if (Files.exists(direct)) return Files.readString(direct, StandardCharsets.UTF_8);
        String modulePath = path.startsWith("app/") ? path.substring(4) : path;
        return Files.readString(Path.of(modulePath), StandardCharsets.UTF_8);
    }
}
```

- [ ] **Step 2：运行测试确认失败**

```text
rtk proxy cmd.exe /d /c gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.activity.SpeechAdSettingSourceTest --no-daemon --no-build-cache --console=plain
```

Expected: 四个 ID 和处理方法不存在。

- [ ] **Step 3：实现 Leanback 设置和通用提示文案**

在 `activity_setting_enhance.xml` 的规则管理与音频指纹之间增加四个 `LinearLayoutCompat` 焦点行，每行使用 `@drawable/selector_item`、`focusable=true`，右侧状态 TextView ID 分别为：

```text
speechAdEnabledText
speechAdKeywordsText
speechAdSkipSecondsText
speechAdSkipModeText
```

在 `reorderItems()` 中按以下顺序插入：

```java
mBinding.adRuleManage,
mBinding.speechAdEnabled,
mBinding.speechAdKeywords,
mBinding.speechAdSkipSeconds,
mBinding.speechAdSkipMode,
mBinding.adAudioFingerprint,
```

事件处理必须是：

```java
mBinding.speechAdEnabled.setOnClickListener(v -> {
    SpeechAdSetting.setEnabled(!SpeechAdSetting.snapshot().enabled());
    notifyAdAudioRuntime();
    setText();
});
mBinding.speechAdKeywords.setOnClickListener(this::editSpeechAdKeywords);
mBinding.speechAdSkipSeconds.setOnClickListener(this::editSpeechAdSkipSeconds);
mBinding.speechAdSkipMode.setOnClickListener(this::selectSpeechAdSkipMode);
```

关键词对话框使用多行 `EditText`，确认时调用 `SpeechAdSetting.setKeywords(input.getText().toString())`；数字对话框使用 `TYPE_CLASS_NUMBER`，非法输入显示 `speech_ad_skip_seconds_invalid` 且不关闭；模式对话框只有“弹窗确认后跳过”和“自动跳过”，默认焦点落在当前项。

`setText()` 读取一次快照并显示；启用但 `RealtimeSubtitleSpeechRecognitionFactory.isSelectedModelReady()` 为 false 时，开关摘要显示“启用 · 模型未就绪”：

```java
SpeechAdConfig speech = SpeechAdSetting.snapshot();
mBinding.speechAdEnabledText.setText(getSwitch(speech.enabled()));
mBinding.speechAdKeywordsText.setText(getString(R.string.speech_ad_keyword_count, speech.keywords().values().size()));
mBinding.speechAdSkipSecondsText.setText(getString(R.string.speech_ad_skip_seconds_value, speech.skipSeconds()));
mBinding.speechAdSkipModeText.setText(speech.mode() == AdSkipPolicyController.Mode.AUTO
        ? R.string.speech_ad_skip_mode_auto : R.string.speech_ad_skip_mode_prompt);
```

`notifyAdAudioRuntime()` 改调用 `service.player().reloadAdAudioSettings()`。

新增英文、简体中文、繁体中文字符串：标题、关键词、关键词数量、跳过秒数、确认模式、自动模式、输入错误、模型未就绪、疑似语音广告提示。`AdSkipPromptPresenter` 在 `prompt.ruleId().equals(SpeechAdSignalProvider.RULE_ID)` 时使用通用语音广告标题/消息，不显示 `speech-keyword`。

- [ ] **Step 4：运行源测试和 Leanback Java 编译**

```text
rtk proxy cmd.exe /d /c gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.activity.SpeechAdSettingSourceTest --no-daemon --no-build-cache --console=plain
rtk proxy cmd.exe /d /c gradlew.bat :app:compileLeanbackArm64_v8aDebugJavaWithJavac --no-daemon --no-build-cache --console=plain
```

Expected: 测试和编译均成功。

- [ ] **Step 5：提交**

```text
rtk git add app/src/leanback/java/com/fongmi/android/tv/ui/activity/SettingEnhanceActivity.java app/src/leanback/res/layout/activity_setting_enhance.xml app/src/main/res/values/strings.xml app/src/main/res/values-zh-rCN/strings.xml app/src/main/res/values-zh-rTW/strings.xml app/src/main/java/com/fongmi/android/tv/ui/dialog/AdSkipPromptPresenter.java app/src/test/java/com/fongmi/android/tv/ui/activity/SpeechAdSettingSourceTest.java
rtk git commit -m "feat: expose speech ad settings on tv"
```

## Task 9：同步 Mobile 设置界面

**Files:**
- Modify: `app/src/mobile/java/com/fongmi/android/tv/ui/fragment/SettingEnhanceFragment.java`
- Modify: `app/src/mobile/res/layout/fragment_setting_enhance.xml`
- Modify: `app/src/test/java/com/fongmi/android/tv/ui/activity/SpeechAdSettingSourceTest.java`

- [ ] **Step 1：扩展失败测试覆盖 Mobile**

在 `SpeechAdSettingSourceTest` 增加：

```java
@Test
public void mobileExposesAllSpeechAdControls() throws Exception {
    String java = read("app/src/mobile/java/com/fongmi/android/tv/ui/fragment/SettingEnhanceFragment.java");
    String xml = read("app/src/mobile/res/layout/fragment_setting_enhance.xml");
    assertTrue(xml.contains("@+id/speechAdEnabled"));
    assertTrue(xml.contains("@+id/speechAdKeywords"));
    assertTrue(xml.contains("@+id/speechAdSkipSeconds"));
    assertTrue(xml.contains("@+id/speechAdSkipMode"));
    assertTrue(java.contains("SpeechAdSetting.setEnabled"));
    assertTrue(java.contains("reloadAdAudioSettings"));
}
```

- [ ] **Step 2：运行测试确认 Mobile 尚未实现**

```text
rtk proxy cmd.exe /d /c gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.activity.SpeechAdSettingSourceTest --no-daemon --no-build-cache --console=plain
```

Expected: `mobileExposesAllSpeechAdControls` 失败。

- [ ] **Step 3：实现 Mobile 对等行为**

在 `fragment_setting_enhance.xml` 增加与 Leanback 同名的四个行容器和状态 TextView；在 Fragment 的 `reorderItems()`、`initEvent()`、`setText()` 中使用完全相同的共享配置语义。

Fragment 新增统一刷新方法：

```java
private void notifyAdAudioRuntime() {
    PlaybackService service = Server.get().getService();
    if (service == null || service.player() == null || service.player().isReleased()) return;
    service.player().reloadAdAudioSettings();
}
```

关键词、时长和模式对话框复用 Task 8 的字符串和校验规则；不得复制另一套默认关键词或时长范围。

- [ ] **Step 4：运行源测试和 Mobile Java 编译**

```text
rtk proxy cmd.exe /d /c gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.activity.SpeechAdSettingSourceTest --no-daemon --no-build-cache --console=plain
rtk proxy cmd.exe /d /c gradlew.bat :app:compileMobileArm64_v8aDebugJavaWithJavac --no-daemon --no-build-cache --console=plain
```

Expected: 全部成功。

- [ ] **Step 5：提交**

```text
rtk git add app/src/mobile/java/com/fongmi/android/tv/ui/fragment/SettingEnhanceFragment.java app/src/mobile/res/layout/fragment_setting_enhance.xml app/src/test/java/com/fongmi/android/tv/ui/activity/SpeechAdSettingSourceTest.java
rtk git commit -m "feat: expose speech ad settings on mobile"
```

## Task 10：完整回归、APK 和 emulator-5560 验证

**Files:**
- Modify only if a failing test reveals a defect in files already listed above.

- [ ] **Step 1：运行语音去广聚焦测试**

```text
rtk proxy cmd.exe /d /c gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ad.audio.SpeechAdKeywordSetTest --tests com.fongmi.android.tv.ad.audio.SpeechAdConfigTest --tests com.fongmi.android.tv.ad.audio.SpeechAdSignalProviderTest --tests com.fongmi.android.tv.ad.audio.AdSkipPolicyControllerTest --tests com.fongmi.android.tv.ad.audio.AdAudioRuntimeControllerTest --tests com.fongmi.android.tv.subtitle.RealtimeSubtitleSpeechRecognitionFactoryTest --tests com.fongmi.android.tv.subtitle.RealtimeSubtitleRecognizerTest --tests com.fongmi.android.tv.ui.activity.SpeechAdSettingSourceTest --no-daemon --no-build-cache --console=plain
```

Expected: 全部通过，0 failures/errors/skipped。

- [ ] **Step 2：运行 Leanback 全量单测和 Mobile 编译**

```text
rtk proxy cmd.exe /d /c gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --no-daemon --no-build-cache --console=plain
rtk proxy cmd.exe /d /c gradlew.bat :app:compileMobileArm64_v8aDebugJavaWithJavac --no-daemon --no-build-cache --console=plain
```

Expected: 两个命令均 `BUILD SUCCESSFUL`；读取 JUnit XML 汇总并确认 failures/errors 为 0。

- [ ] **Step 3：构建并安装 Leanback ARM64 Debug**

```text
rtk proxy cmd.exe /d /c gradlew.bat :app:assembleLeanbackArm64_v8aDebug --no-daemon --no-build-cache --console=plain
rtk proxy adb -s emulator-5560 install -r app/build/outputs/apk/leanbackArm64_v8a/debug/app-leanback-arm64_v8a-debug.apk
```

Expected: Gradle 成功，ADB 返回 `Success`，不卸载应用、不清空数据。

- [ ] **Step 4：从真实 UI 路径验证配置和两种策略**

使用遥控器路径：

```text
HomeActivity → 设置 → 增强功能
```

核对：

1. 四个语音去广设置项都可由 DPAD 到达；
2. 默认关闭、默认确认、默认 15 秒、默认关键词可见；
3. 修改关键词/时长/模式后 SharedPreferences 正确写入；
4. 强制停止并从 `HomeActivity` 重启后配置仍保持；
5. 使用 fake/test recognizer 或固定音频 fixture 产生“赌场”识别结果；
6. PROMPT 模式只显示提示，确认后才 seek；
7. AUTO 模式只对切换后的新候选自动 seek；
8. 两种模式跳过后均可撤销；
9. seek/切源后旧识别回调不提示、不跳转；
10. PCM 指纹开关和策略未被语音模式改变。

读取状态和日志：

```text
rtk proxy adb -s emulator-5560 shell dumpsys activity activities
rtk proxy adb -s emulator-5560 shell run-as com.silent.android.webhtv cat shared_prefs/com.silent.android.webhtv_preferences.xml
rtk proxy adb -s emulator-5560 logcat -d -v brief
```

Expected: 无应用相关 FATAL/ANR，配置键与 UI 一致，至少一条可控语音命中完成确认跳过和自动跳过。

- [ ] **Step 5：最终质量门禁和提交**

```text
rtk git diff --check
rtk git status --short
```

仅在出现验证期修复时创建最后提交：

```text
rtk git add app/src/main app/src/leanback app/src/mobile app/src/test
rtk git commit -m "fix: harden speech ad integration"
```

最终报告必须列出：测试数量、失败数量、Leanback/Mobile 构建结果、APK 安装结果、确认/自动跳过证据、最终偏好状态和 Git 状态。

---

## 执行顺序检查点

- Task 1–3 完成后：纯 JVM 关键词、配置和策略已可验证，不依赖模型。
- Task 4–6 完成后：fake recognizer 可通过真实 Hub/Runtime 产生候选并控制确认/自动。
- Task 7–9 完成后：TV/Mobile 均可配置且实时刷新。
- Task 10 完成后：才允许声明“真实配置和可控跳过验证通过”。