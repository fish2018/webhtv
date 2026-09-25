package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class AudioPlaybackStyleRoutingTest {

    @Test
    public void immersiveAudioIsTheDefaultPlaybackStyle() throws Exception {
        String source = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "setting", "PlayerSetting.java")));

        assertTrue("Immersive audio must be the default when no style has been stored",
                source.contains("Prefers.getBoolean(\"immersive_audio_mode\", true)"));
        assertTrue("Playback styles must expose a built-in option",
                source.contains("AUDIO_PLAYBACK_STYLE_BUILT_IN"));
        assertTrue("Playback styles must expose an immersive option",
                source.contains("AUDIO_PLAYBACK_STYLE_IMMERSIVE"));
    }

    @Test
    public void audioContentHandlerDefersToVideoPlaybackInImmersiveMode() throws Exception {
        String source = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "content", "AudioContentHandler.java")));

        assertTrue("Audio sites must still be claimed so immersive mode can use the direct audio launch path",
                source.contains("return AudioUtil.isAudioSiteEnabled(key);"));
        assertTrue("Audio sites must be routed through the audio launcher in every playback style",
                source.contains("return AudioActivity.startSite(activity, key, id, name, pic, mark);"));
        assertTrue("Audio URLs must not be claimed by the built-in handler in immersive mode",
                source.contains("!PlayerSetting.isImmersiveAudioMode() && AudioUtil.isAudioUrl(url)"));
        assertTrue("Resolved audio results must fall through to the normal video path in immersive mode",
                source.contains("if (PlayerSetting.isImmersiveAudioMode()) return false;"));
    }

    @Test
    public void standaloneAudioActivityRejectsImmersivePlayback() throws Exception {
        String source = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "AudioActivity.java")));

        assertTrue("Direct audio-site playback must route to the immersive launcher instead of falling through to the detail page",
                source.contains("public static boolean startSite")
                        && source.contains("return VideoActivity.startImmersiveAudioSite(activity, key, id, name, pic, mark);"));
        assertTrue("Audio result redirection must be disabled in immersive mode",
                source.contains("public static boolean startIfAudio")
                        && source.contains("if (PlayerSetting.isImmersiveAudioMode()) return false;"));
    }

    @Test
    public void immersiveAudioSiteLaunchConsumesResolvedPlaybackBeforeDetailRequest() throws Exception {
        String mobile = read(findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));
        String leanback = read(findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));

        assertImmersiveAudioDirectLaunch("mobile", mobile);
        assertImmersiveAudioDirectLaunch("leanback", leanback);
    }

    @Test
    public void asynchronousAudioLaunchesUseLifecycleAwareFrameDeferral() throws Exception {
        String audio = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "AudioActivity.java")));
        String mobile = read(findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));
        String leanback = read(findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));
        String launch = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "utils", "ActivityLaunch.java")));

        assertTrue("built-in audio playback must defer its async Activity launch",
                audio.contains("ActivityLaunch.postOnAnimation(activity, () -> start("));
        assertTrue("mobile immersive audio playback must defer its async Activity launch",
                mobile.contains("ActivityLaunch.postOnAnimation(activity, () -> startResolvedImmersiveAudio("));
        assertTrue("leanback immersive audio playback must defer its async Activity launch",
                leanback.contains("ActivityLaunch.postOnAnimation(activity, () -> startResolvedImmersiveAudio("));
        assertTrue("shared launch guard must reject finishing and destroyed activities",
                launch.contains("!activity.isFinishing()") && launch.contains("!activity.isDestroyed()"));
        assertTrue("shared launch guard must leave the current input dispatch before launching",
                launch.contains("decor.postOnAnimation("));
        assertTrue("shared launch guard must recognize the Android ViewGroup cancellation crash",
                launch.contains("dispatchCancelPendingInputEvents"));
    }

    @Test
    public void immersiveAudioStageOpensBeforeTheServiceBindsSoTheVideoPageNeverFlashes() throws Exception {
        String mobile = read(findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));

        assertTrue("mobile must be able to detect a pending immersive audio launch without consuming it",
                mobile.contains("private boolean hasPendingImmersiveAudioLaunch()")
                        && mobile.contains("IMMERSIVE_AUDIO_LAUNCHES.containsKey(cacheKey)"));
        assertTrue("mobile must raise the audio stage during initView so the video detail page never renders first",
                mobile.contains("if (hasPendingImmersiveAudioLaunch()) setAudioStageVisible(true);")
                        && mobile.indexOf("if (hasPendingImmersiveAudioLaunch()) setAudioStageVisible(true);")
                                < mobile.indexOf("protected void initEvent()"));
    }

    private static void assertImmersiveAudioDirectLaunch(String variant, String source) {
        assertTrue(variant + " must expose a direct immersive audio site launcher",
                source.contains("static boolean startImmersiveAudioSite(Activity activity, String key, String id, String name, String pic, String mark)"));
        assertTrue(variant + " must consume the resolved audio launch before the normal detail request",
                source.contains("if (consumeImmersiveAudioLaunch()) return;")
                        && source.indexOf("if (consumeImmersiveAudioLaunch()) return;") < source.indexOf("checkId();"));
        assertTrue(variant + " must apply the already resolved player result directly",
                source.contains("prepareImmersiveAudioPlayback(resolved);")
                        && source.contains("setPlayer(resolved.getResult());"));
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path findMainJavaPath() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "java");
    }

    private static Path findMobileJavaPath() {
        Path moduleRelative = Path.of("src", "mobile", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "mobile", "java");
    }

    private static Path findLeanbackJavaPath() {
        Path moduleRelative = Path.of("src", "leanback", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "leanback", "java");
    }
}
