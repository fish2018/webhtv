package com.fongmi.android.tv.ui.activity;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.ui.helper.EpisodeDisplayPolicy;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VideoActivityLayoutTest {

    private static final List<String> REQUIRED_EPISODE_IDS = Arrays.asList(
            "episodeTitleBar",
            "episodeTitle",
            "episodeViewMode"
    );
    private static final List<String> REQUIRED_TMDB_MOVABLE_IDS = Arrays.asList(
            "flagTitleBar",
            "flag",
            "quality_text",
            "quality",
            "episodeTitleBar",
            "episode"
    );
    private static final List<String> REQUIRED_FULLSCREEN_CONTROL_IDS = Arrays.asList(
            "cast",
            "keep",
            "osdDiagnostics",
            "info"
    );

    @Test
    public void mobileActivityVideoLayoutsExposeEpisodeModeControls() throws Exception {
        List<Path> layoutFiles = Files.walk(findMobileResPath())
                .filter(path -> path.getFileName().toString().equals("activity_video.xml"))
                .filter(path -> path.getParent().getFileName().toString().startsWith("layout"))
                .collect(Collectors.toList());

        assertFalse("No mobile activity_video.xml layouts found", layoutFiles.isEmpty());
        for (Path layoutFile : layoutFiles) {
            Set<String> ids = collectAndroidIds(layoutFile.toFile());
            for (String requiredId : REQUIRED_EPISODE_IDS) {
                assertTrue(layoutFile + " is missing @+id/" + requiredId, ids.contains(requiredId));
            }
        }
    }

    @Test
    public void mobileActivityVideoLayoutsExposeTmdbMovableContainers() throws Exception {
        List<Path> layoutFiles = Files.walk(findMobileResPath())
                .filter(path -> path.getFileName().toString().equals("activity_video.xml"))
                .filter(path -> path.getParent().getFileName().toString().startsWith("layout"))
                .collect(Collectors.toList());

        assertFalse("No mobile activity_video.xml layouts found", layoutFiles.isEmpty());
        for (Path layoutFile : layoutFiles) {
            Set<String> ids = collectAndroidIds(layoutFile.toFile());
            for (String requiredId : REQUIRED_TMDB_MOVABLE_IDS) {
                assertTrue(layoutFile + " is missing @+id/" + requiredId, ids.contains(requiredId));
            }
        }
    }

    @Test
    public void mobileLandscapeTmdbMovableIdsDoNotPointIntoAudioStage() throws Exception {
        List<Path> layoutFiles = Arrays.asList(
                findMobileResPath().resolve(Path.of("layout-land", "activity_video.xml")),
                findMobileResPath().resolve(Path.of("layout-sw600dp-land", "activity_video.xml"))
        );

        for (Path layoutFile : layoutFiles) {
            assertTrue(layoutFile + " must exist", Files.exists(layoutFile));
            Element audioStage = findAndroidId(layoutFile.toFile(), "audioStage");
            Element flagTitleBar = findAndroidId(layoutFile.toFile(), "flagTitleBar");
            Element episodeTitleBar = findAndroidId(layoutFile.toFile(), "episodeTitleBar");

            assertTrue(layoutFile + " is missing @+id/audioStage", audioStage != null);
            assertTrue(layoutFile + " is missing @+id/flagTitleBar", flagTitleBar != null);
            assertTrue(layoutFile + " is missing @+id/episodeTitleBar", episodeTitleBar != null);
            assertFalse(layoutFile + " must not bind flagTitleBar to the audio-stage layout",
                    hasAncestorAndroidId(flagTitleBar, "audioStage"));
            assertFalse(layoutFile + " must not bind episodeTitleBar to the audio-stage layout",
                    hasAncestorAndroidId(episodeTitleBar, "audioStage"));
            assertTrue(layoutFile + " must bind flagTitleBar to the source heading",
                    hasDescendantAndroidText(flagTitleBar, "@string/detail_flag"));
            assertTrue(layoutFile + " must bind episodeTitleBar to the episode heading",
                    hasDescendantAndroidText(episodeTitleBar, "@string/detail_episode"));
            assertTrue(layoutFile + " must place the source title before the episode title",
                    isAndroidIdBefore(layoutFile, "flagTitleBar", "episodeTitleBar"));
        }
    }
    @Test
    public void mobileActivityVideoLayoutsHaveFusionChromeHost() throws Exception {
        List<Path> layoutFiles = Files.walk(findMobileResPath())
                .filter(path -> path.getFileName().toString().equals("activity_video.xml"))
                .filter(path -> path.getParent().getFileName().toString().startsWith("layout"))
                .collect(Collectors.toList());

        assertFalse("No mobile activity_video.xml layouts found", layoutFiles.isEmpty());
        for (Path layoutFile : layoutFiles) {
            Element video = findAndroidId(layoutFile.toFile(), "video");
            assertTrue(layoutFile + " is missing @+id/video", video != null);
            Node parent = video.getParentNode();
            String parentName = parent == null ? "" : parent.getNodeName();
            assertTrue(layoutFile + " must keep @+id/video inside a RelativeLayout-compatible host",
                    "RelativeLayout".equals(parentName) || "com.fongmi.android.tv.ui.custom.ProgressLayout".equals(parentName));
        }
    }

    @Test
    public void mobilePortraitPlayerTouchesStatusBarWithoutExtraGap() throws Exception {
        Path layoutFile = findMobileResPath().resolve(Path.of("layout", "activity_video.xml"));
        Element video = findAndroidId(layoutFile.toFile(), "video");

        assertTrue(layoutFile + " is missing @+id/video", video != null);
        assertTrue("the portrait player must stay directly below the status bar inset",
                "@+id/statusBar".equals(video.getAttribute("android:layout_below")));
        assertFalse("the portrait player must not add a second gap below the status bar",
                video.hasAttribute("android:layout_marginTop"));
    }

    @Test
    public void mobileVodControlLayoutExposesFullscreenTopActions() throws Exception {
        Path controlLayout = findMobileResPath().resolve(Path.of("layout", "view_control_vod.xml"));
        Set<String> ids = collectAndroidIds(controlLayout.toFile());
        for (String requiredId : REQUIRED_FULLSCREEN_CONTROL_IDS) {
            assertTrue(controlLayout + " is missing @+id/" + requiredId, ids.contains(requiredId));
        }
    }

    @Test
    public void mobileVodControlOverlayRoutesBlankTouchesToGestureDetector() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int initEvent = source.indexOf("protected void initEvent()");
        int rootTouch = source.indexOf("mBinding.control.getRoot().setOnTouchListener(this::onPlayerControlTouch);", initEvent);
        int touchHandler = source.indexOf("private boolean onPlayerControlTouch(View view, MotionEvent event)");
        int gestureDispatch = source.indexOf("return mKeyDown.onTouchEvent(event);", touchHandler);

        assertTrue(sourcePath + " is missing initEvent", initEvent >= 0);
        assertTrue("the fullscreen control overlay must route blank touches directly to the gesture detector", rootTouch > initEvent);
        assertTrue("the control-overlay touch handler must dispatch the full gesture sequence", touchHandler >= 0 && gestureDispatch > touchHandler);
    }

    @Test
    public void mobileOverlayButtonsIgnorePlayerButtonSetting() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);

        // 悬浮/图标按钮（中间上下集、进度条旁全屏、顶部弹幕/投屏）只受集数、锁定、功能可用性控制，
        // 不受「播放器按钮设置」影响——那是仅面向底部横向动作栏的偏好。锁定这些可见性表达式，
        // 防止有人再次把 PlayerButtonSetting 判断加回悬浮按钮（历史回归点）。
        assertTrue("middle overlay next button must depend only on episode count",
                source.contains("mBinding.control.next.setVisibility(size < 2 ? View.GONE : View.VISIBLE);"));
        assertTrue("middle overlay prev button must depend only on episode count",
                source.contains("mBinding.control.prev.setVisibility(size < 2 ? View.GONE : View.VISIBLE);"));
        assertTrue("seekbar fullscreen button must depend only on lock and short-drama state",
                source.contains("mBinding.control.fullscreen.setVisibility(isLock() || shortDrama ? View.GONE : View.VISIBLE);"));
        assertTrue("top cast button must depend only on fullscreen and playback state",
                source.contains("mBinding.control.cast.setVisibility(isFullscreen() && mHistory != null && !player().isEmpty() ? View.VISIBLE : View.GONE);"));
        assertTrue("top danmaku button must depend only on lock and danmaku availability",
                source.contains("mBinding.control.danmaku.setVisibility(isLock() || !player().haveDanmaku() ? View.GONE : View.VISIBLE);"));

        for (String id : List.of("next", "prev", "fullscreen", "cast", "danmaku")) {
            int line = source.indexOf("mBinding.control." + id + ".setVisibility(");
            assertTrue("missing overlay visibility line for mBinding.control." + id, line >= 0);
            String stmt = source.substring(line, source.indexOf(';', line));
            assertFalse("overlay button mBinding.control." + id + " must not gate on PlayerButtonSetting", stmt.contains("PlayerButtonSetting"));
        }

        // 底部横向动作栏按钮仍必须通过 addActionButton 跟随设置，确认解耦没有误伤动作栏。
        assertTrue("bottom action bar fullscreen must still follow PlayerButtonSetting",
                source.contains("addActionButton(PlayerButtonSetting.FULLSCREEN, mBinding.control.action.fullscreen);"));
        assertTrue("bottom action bar prev must still follow PlayerButtonSetting",
                source.contains("addActionButton(PlayerButtonSetting.PREV, mBinding.control.action.prev);"));
        assertTrue("bottom action bar next must still follow PlayerButtonSetting",
                source.contains("addActionButton(PlayerButtonSetting.NEXT, mBinding.control.action.next);"));
    }

    @Test
    public void mobilePlayerGesturesUseVideoViewBoundsAfterFullscreen() throws Exception {
        List<Path> gestureFiles = Arrays.asList(
                findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "custom", "CustomKeyDown.java")),
                findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "custom", "PlayerGesture.java"))
        );

        for (Path gestureFile : gestureFiles) {
            String source = new String(Files.readAllBytes(gestureFile), StandardCharsets.UTF_8);
            assertTrue(gestureFile + " must map raw touch coordinates into the actual player view",
                    source.contains("videoView.getLocationOnScreen(videoLocation);")
                            && source.contains("return e.getRawX() - videoLocation[0];")
                            && source.contains("return e.getRawY() - videoLocation[1];"));
            assertTrue(gestureFile + " must use the current player view dimensions for gesture regions",
                    source.contains("private int getVideoWidth()")
                            && source.contains("videoView.getWidth()")
                            && source.contains("videoView.getMeasuredWidth()")
                            && source.contains("private int getVideoHeight()")
                            && source.contains("videoView.getHeight()")
                            && source.contains("videoView.getMeasuredHeight()"));
            assertFalse(gestureFile + " must not use app screen metrics for fullscreen gesture regions",
                    source.contains("ResUtil.isEdge(App.get()")
                            || source.contains("ResUtil.getScreenWidth(App.get())"));
        }
    }

    @Test
    public void mobileVideoRefreshesDanmakuControlsAfterLateDanmakuLoad() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void refreshDanmakuControls()");
        int action = source.indexOf("mBinding.control.action.danmaku.setVisibility", method);
        int quick = source.indexOf("mBinding.control.danmaku.setVisibility", method);
        int apiSearch = source.indexOf("DanmakuApi.search");
        int apiRefresh = source.indexOf("refreshDanmakuControls();", apiSearch);
        int event = source.indexOf("RefreshEvent.Type.DANMAKU");
        int eventRefresh = source.indexOf("refreshDanmakuControls();", event);

        assertTrue(sourcePath + " is missing refreshDanmakuControls", method >= 0);
        assertTrue("late danmaku refresh must update the fullscreen action button", action > method);
        assertTrue("late danmaku refresh must update the quick toggle button", quick > method);
        assertTrue("auto danmaku search must refresh controls after loading", apiRefresh > apiSearch);
        assertTrue("manual danmaku refresh event must refresh controls after loading", eventRefresh > event);
    }

    @Test
    public void mobileEpisodeNameToggleRestoresFallbackArtworkAfterAdapterRecreation() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void toggleEpisodeFileName()");
        int methodEnd = source.indexOf("private void updateEpisodeFileNameButton()", method);
        String methodBody = method >= 0 && methodEnd > method ? source.substring(method, methodEnd) : "";
        int recreateAdapter = methodBody.indexOf("mEpisodeAdapter = new EpisodeAdapter");
        int restoreFallback = methodBody.indexOf("updateEpisodeFallbackStillUrl();", recreateAdapter);
        int attachAdapter = methodBody.indexOf("mBinding.episode.setAdapter(mEpisodeAdapter);", recreateAdapter);

        assertTrue(sourcePath + " is missing toggleEpisodeFileName", method >= 0);
        assertTrue("name toggle must restore fallback artwork on the recreated episode adapter",
                recreateAdapter >= 0 && restoreFallback > recreateAdapter && restoreFallback < attachAdapter);
    }

    @Test
    public void mobileVideoKeepsParseRowHiddenInEmbeddedPlayerWhenPlaybackStarts() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void setPlayer(Result result)");
        int setUseParse = source.indexOf("setUseParse(result.shouldUseParse());", method);
        int guardedParseRow = source.indexOf("mBinding.control.parse.setVisibility(isFullscreen() && isUseParse() && PlayerButtonSetting.isVisible(PlayerButtonSetting.PARSE) ? View.VISIBLE : View.GONE);", setUseParse);
        int startPlayer = source.indexOf("startPlayer(getHistoryKey(), result, isUseParse()", setUseParse);

        assertTrue(sourcePath + " is missing setPlayer", method >= 0);
        assertTrue("parse row must only become visible in fullscreen during playback start", guardedParseRow > setUseParse && guardedParseRow < startPlayer);
    }

    @Test
    public void mobilePlayerKernelClickOpensChooserBeforeSwitching() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int clickMethod = source.indexOf("private void onPlayerKernel()");
        int clickMethodEnd = source.indexOf("private boolean onPlayerKernelLong()", clickMethod);
        String clickBody = clickMethod >= 0 && clickMethodEnd > clickMethod ? source.substring(clickMethod, clickMethodEnd) : "";
        int chooseMethod = source.indexOf("private void onChoose()");
        int chooseMethodEnd = source.indexOf("private void onPlayerKernel()", chooseMethod);
        String chooseBody = chooseMethod >= 0 && chooseMethodEnd > chooseMethod ? source.substring(chooseMethod, chooseMethodEnd) : "";
        int invalidateInternalRefresh = chooseBody.indexOf("playerKernelSwitchRequestId++;");
        int launchExternalPlayer = chooseBody.indexOf("PlayerHelper.choose", invalidateInternalRefresh);
        int resultMethod = source.indexOf("private void switchPlayerKernelWithResult(");
        int resultMethodEnd = source.indexOf("private boolean onTextLong()", resultMethod);
        String resultBody = resultMethod >= 0 && resultMethodEnd > resultMethod ? source.substring(resultMethod, resultMethodEnd) : "";
        int switchMethod = source.indexOf("private boolean refreshAndSwitchPlayerKernel(");
        int requestDeclaration = source.indexOf("int requestId = ++playerKernelSwitchRequestId;", switchMethod);
        int currentFlag = source.indexOf("Flag currentFlag = getFlag();", switchMethod);

        assertTrue(sourcePath + " is missing onPlayerKernel", clickMethod >= 0);
        assertTrue("player kernel click must open the shared chooser", clickBody.contains("onChoose();"));
        assertFalse("player kernel click must not switch to the next core before user selection", clickBody.contains("refreshAndSwitchPlayerKernel"));
        assertTrue("the selected core must retain the refreshed-source switch path", chooseBody.contains("refreshAndSwitchPlayerKernel(which)"));
        assertFalse("a later core selection must not be discarded while an earlier refresh is running", source.contains("if (playerKernelSwitchRefreshing) return true;"));
        assertTrue("only the latest core selection and current playback context may apply its refreshed result",
                resultBody.contains("requestId != playerKernelSwitchRequestId")
                        && resultBody.contains("isCurrentPlayerContentRequest(requestId, generation, key, flag, episode)")
                        && resultBody.contains("return;"));
        assertTrue("external playback selection must invalidate an in-flight internal core refresh", invalidateInternalRefresh >= 0 && launchExternalPlayer > invalidateInternalRefresh);
        assertTrue("an internal selection without refresh metadata must still invalidate an older request",
                switchMethod >= 0 && requestDeclaration >= switchMethod && currentFlag > requestDeclaration);
    }

    @Test
    public void leanbackAudioModeReconcilesAfterTracksAndPlayerBecomeReady() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String tracksBody = methodBody(source, "protected void onTracksChanged()", "protected void onTitlesChanged()");
        String stateBody = methodBody(source, "protected void onStateChanged(int state)", "protected void onPlayingChanged(boolean isPlaying)");

        assertTrue("TV track changes must re-evaluate immersive audio after native players publish their video track",
                tracksBody.indexOf("refreshLyrics();") >= 0
                        && tracksBody.indexOf("refreshLyrics();") < tracksBody.indexOf("setTrackVisible();"));
        assertTrue("TV READY state must correct any early audio-only classification made during prepare",
                stateBody.contains("case Player.STATE_READY:")
                        && stateBody.indexOf("refreshLyrics();") > stateBody.indexOf("case Player.STATE_READY:"));
    }

    @Test
    public void leanbackImmersiveAudioRequiresExplicitSessionActivation() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String policyBody = methodBody(source, "private boolean shouldUseImmersiveAudio()", "private void syncAudioStageSurface(boolean visible)");

        assertTrue("TV immersive audio must only continue after an explicit source launch or manual selection",
                policyBody.contains("return PlayerSetting.isImmersiveAudioMode() && mImmersiveAudioRequested;"));
        assertFalse("ordinary videos must not enter immersive audio from title or early track guesses",
                policyBody.contains("isAudioOnly()") || policyBody.contains("isMusicLike()"));
        assertTrue("configured audio-source launches must explicitly activate the immersive session",
                source.contains("mImmersiveAudioRequested = true;")
                        && source.indexOf("mImmersiveAudioRequested = true;") > source.indexOf("private void prepareImmersiveAudioPlayback("));
        String modeBody = methodBody(source, "public void onImmersiveAudioModeChanged()", "private boolean dispatchAudioStageKey");
        assertTrue("manual playback-style selection must explicitly update the immersive session",
                modeBody.contains("mImmersiveAudioRequested = enabled;"));
    }

    @Test
    public void leanbackImmersiveAudioSelectionPersistsForMatchingPlayback() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        Path settingPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "setting", "PlayerSetting.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String setting = new String(Files.readAllBytes(settingPath), StandardCharsets.UTF_8);
        String initBody = methodBody(source, "protected void initView(Bundle savedInstanceState)", "protected void initEvent()");
        String newIntentBody = methodBody(source, "protected void onNewIntent(Intent intent)", "protected void initView(Bundle savedInstanceState)");
        String restoreBody = methodBody(source, "private void restoreImmersiveAudioRequest()", "private Site getSite()");
        String modeBody = methodBody(source, "public void onImmersiveAudioModeChanged()", "private boolean dispatchAudioStageKey");
        String launchBody = methodBody(source, "private void prepareImmersiveAudioPlayback(AudioPlaybackResolver.Resolved resolved)", "private void applyImmersiveAudioSelection");

        assertTrue("the explicit TV immersive-audio request must have a playback-scoped preference",
                setting.contains("private static final String KEY_IMMERSIVE_AUDIO_PLAYBACK = \"immersive_audio_playback\";")
                        && setting.contains("public static boolean isImmersiveAudioPlayback(String playbackKey)")
                        && setting.contains("public static void putImmersiveAudioPlayback(String playbackKey)"));
        assertTrue("a recreated TV playback must restore only the matching explicit request",
                restoreBody.contains("mImmersiveAudioRequested = PlayerSetting.isImmersiveAudioPlayback(getHistoryKey());"));
        assertFalse("restoring an explicit request must not fall back to title or early-track guesses",
                restoreBody.contains("isAudioOnly()") || restoreBody.contains("isMusicLike()"));
        assertTrue("TV initialization must restore the playback-scoped request before playback setup",
                initBody.indexOf("restoreImmersiveAudioRequest();") >= 0
                        && initBody.indexOf("restoreImmersiveAudioRequest();") < initBody.indexOf("super.initView(savedInstanceState);"));

        int replaceIntent = newIntentBody.indexOf("getIntent().putExtras(intent);");
        int hideOldStage = newIntentBody.indexOf("setAudioStageVisible(false);");
        int restoreNewRequest = newIntentBody.indexOf("restoreImmersiveAudioRequest();");
        assertTrue("singleTop playback changes must hide the old stage and recompute the request for the new content",
                replaceIntent >= 0 && hideOldStage > replaceIntent && restoreNewRequest > hideOldStage);
        assertTrue("manual selection must remember or clear the current playback identity",
                modeBody.contains("boolean enabled = PlayerSetting.isImmersiveAudioMode();")
                        && modeBody.contains("mImmersiveAudioRequested = enabled;")
                        && modeBody.contains("PlayerSetting.putImmersiveAudioPlayback(enabled ? getHistoryKey() : \"\");"));

        int launchIdentity = launchBody.indexOf("getIntent().putExtra(\"id\", resolved.getVodId());");
        int rememberLaunch = launchBody.indexOf("PlayerSetting.putImmersiveAudioPlayback(getHistoryKey());");
        assertTrue("configured audio-source launches must also remember their resolved playback identity",
                launchIdentity >= 0 && rememberLaunch > launchIdentity);
    }

    @Test
    public void leanbackImmersiveAudioHighlightReflectsActivePlayback() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        Path dialogPath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "ControlDialog.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String dialog = new String(Files.readAllBytes(dialogPath), StandardCharsets.UTF_8);
        String actionBody = methodBody(source, "private void updateImmersiveAudioAction()", "private void toggleImmersiveAudioMode()");
        String toggleBody = methodBody(source, "private void toggleImmersiveAudioMode()", "private int getEpisodeColumn()");
        String dialogInit = methodBody(dialog, "protected void initView()", "protected void initEvent()");
        String dialogToggle = methodBody(dialog, "private void setImmersiveAudio()", "private void onTrack(View view)");

        assertTrue("the TV action highlight must represent the active playback session, not only the global preference",
                actionBody.contains("mBinding.control.action.immersiveAudio.setSelected(shouldUseImmersiveAudio());"));
        assertFalse("the TV action must not remain highlighted while the current playback request is absent",
                actionBody.contains("setSelected(PlayerSetting.isImmersiveAudioMode())"));
        assertTrue("clicking an inactive highlighted-capable action must enable this playback instead of disabling the global preference",
                toggleBody.contains("PlayerSetting.putImmersiveAudioMode(!shouldUseImmersiveAudio());"));
        assertTrue("the control dialog must initialize from the current playback action state",
                dialogInit.contains("binding.immersiveAudio.setSelected(parent.control.action.immersiveAudio.isSelected());"));
        assertTrue("the control dialog must toggle its actual session state rather than a stale global value",
                dialogToggle.contains("boolean enabled = !binding.immersiveAudio.isSelected();")
                        && dialogToggle.contains("PlayerSetting.putImmersiveAudioMode(enabled);")
                        && dialogToggle.contains("binding.immersiveAudio.setSelected(enabled);"));
    }

    @Test
    public void leanbackAudioStageReconcilesRestoredViewVisibility() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String visibilityBody = methodBody(source, "private void setAudioStageVisible(boolean visible)", "private boolean shouldUseImmersiveAudio()");

        int reconcileView = visibilityBody.indexOf("mBinding.audioStage.setVisibility(visible ? View.VISIBLE : View.GONE);");
        int stateFastPath = visibilityBody.indexOf("if (mAudioStageVisible == visible)");
        assertTrue("TV audio stage must reconcile the real View before trusting its cached visibility flag",
                reconcileView >= 0 && stateFastPath > reconcileView);
    }

    @Test
    public void leanbackVisibleAudioStageReclaimsForegroundOnSongChange() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String visibilityBody = methodBody(source, "private void setAudioStageVisible(boolean visible)", "private boolean shouldUseImmersiveAudio()");

        int stateFastPath = visibilityBody.indexOf("if (mAudioStageVisible == visible)");
        int bringToFront = visibilityBody.indexOf("mBinding.audioStage.bringToFront();");
        int hideProgress = visibilityBody.indexOf("hideProgress();");
        int hideControl = visibilityBody.indexOf("hideControl();");
        int hideInfo = visibilityBody.indexOf("hideInfo();");
        assertTrue("TV song changes must reclaim the audio-stage foreground even when its cached visibility is already true",
                stateFastPath >= 0
                        && bringToFront >= 0 && bringToFront < stateFastPath
                        && hideProgress >= 0 && hideProgress < stateFastPath
                        && hideControl >= 0 && hideControl < stateFastPath
                        && hideInfo >= 0 && hideInfo < stateFastPath);
    }

    @Test
    public void leanbackDisabledAudioStageHidesBeforeLyricsControllerEarlyReturn() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String refreshBody = methodBody(source, "private void refreshLyricsNow()", "private void scheduleRefreshKaraoke");

        int audioContent = refreshBody.indexOf("boolean audioContent = shouldUseImmersiveAudio();");
        int hideStage = refreshBody.indexOf("setAudioStageVisible(audioContent);");
        int earlyReturn = refreshBody.indexOf("if (mLyrics == null || service() == null) return;");
        assertTrue("TV disabled audio stage must be hidden even when lyrics controllers were never initialized",
                audioContent >= 0 && hideStage > audioContent && earlyReturn > hideStage);
    }

    @Test
    public void leanbackAudioStageOverlayIsNotManagedAsContent() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String setupBody = methodBody(source, "private void setupAudioStageOverlay()", "private void setupAudioStageFocusFeedback()");
        String progress = new String(Files.readAllBytes(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "custom", "ProgressLayout.java"))), StandardCharsets.UTF_8);

        int reattach = setupBody.indexOf("addOverlayView(mBinding.audioStage, params);");
        int resetState = setupBody.indexOf("mAudioStageVisible = false;");
        int hideView = setupBody.indexOf("mBinding.audioStage.setVisibility(View.GONE);");
        assertTrue("TV audio stage overlay must not become visible merely because it was reattached to the root",
                reattach >= 0 && resetState > reattach && hideView > resetState);
        assertTrue("ProgressLayout must support overlays that are not managed as normal content",
                progress.contains("public void addOverlayView(View child, ViewGroup.LayoutParams params)")
                        && progress.contains("mContentViews.remove(child);"));
        assertTrue("audio stage must be added as an unmanaged overlay so showContent cannot reveal it",
                setupBody.contains("mBinding.progressLayout.addOverlayView(mBinding.audioStage, params);"));
    }

    @Test
    public void leanbackAudioStageRestoreProtectionDoesNotClearActiveSession() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        Element audioStage = findAndroidId(findLeanbackResPath().resolve(Path.of("layout", "activity_video.xml")).toFile(), "audioStage");

        assertTrue("TV audio stage must opt out of restoring a previously visible overlay",
                audioStage != null
                        && "false".equals(audioStage.getAttribute("android:saveEnabled"))
                        && source.contains("mBinding.audioStage.setSaveFromParentEnabled(false);"));
        int onResume = source.indexOf("protected void onResume()");
        int nextOverride = onResume >= 0 ? source.indexOf("\n    @Override", onResume + 1) : -1;
        String resumeBody = onResume < 0 ? "" : source.substring(onResume, nextOverride > onResume ? nextOverride : source.length());
        assertFalse("TV resume must not clear an explicitly activated immersive-audio session",
                resumeBody.contains("setAudioStageVisible(false);"));
    }

    @Test
    public void videoPlaybackStartKeepsMergedAudioPreparation() throws Exception {
        assertVideoPlaybackStartKeepsMergedAudioPreparation("TV",
                findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));
        assertVideoPlaybackStartKeepsMergedAudioPreparation("mobile",
                findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));
    }

    private static void assertVideoPlaybackStartKeepsMergedAudioPreparation(String label, Path sourcePath) throws Exception {
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String body = methodBody(source, "private void getPlayer(Flag flag, Episode episode)", "private void setPlayer(Result result)");

        assertTrue(label + " playback must preserve per-episode play flags", body.contains("String playFlag = getEpisodePlayFlag(flag, episode);"));
        assertTrue(label + " playback must update the audio episode identity before loading", body.contains("mPlaybackEpisodeKey = audioQueueEpisodeKey(episode);"));
        assertTrue(label + " playback must load inline lyrics for the selected episode", body.contains("mInlineLyrics = getEpisodeInlineLyrics(episode);"));
        assertTrue(label + " playback must update artwork before the new item starts", body.contains("applyPlaybackArtwork(episode);"));
        assertTrue(label + " playback must clear lyrics and karaoke state between episodes",
                body.contains("clearLyrics();") && body.contains("clearKaraokeState();"));
        assertTrue(label + " playback must request content with the resolved per-episode flag and the show's kernel",
                body.contains("mViewModel.playerContent(getKey(), playFlag, episode.getUrl(), applyHistoryPlayerKernel());"));
    }

    @Test
    public void mobilePipAudioActionKeepsBackgroundPlayback() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String audioBody = methodBody(source, "public void onAudio()", "};");
        String pipModeBody = methodBody(source, "public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, @NonNull Configuration newConfig)", "protected void onResume()");

        int audioOnly = audioBody.indexOf("setAudioOnly(true);");
        int syncPipMode = audioBody.indexOf("syncPiPForPlaybackMode();");
        int moveToBackground = audioBody.indexOf("moveTaskToBack(true);");
        assertTrue("PiP audio action must enter audio-only mode before moving task to background",
                audioOnly >= 0 && syncPipMode > audioOnly && moveToBackground > syncPipMode);
        assertTrue("PiP exit must finish when playback has been marked stopped",
                pipModeBody.contains("if (isStop()) finish();"));
        assertFalse("PiP exit must not use a deferred lifecycle heuristic",
                source.contains("mKeepPlaybackAfterPipExit") || source.contains("finishIfPipClosed"));
    }

    @Test
    public void mobileAudioLifecycleKeepsStageStateInSync() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String tracksBody = methodBody(source, "protected void onTracksChanged()", "protected void onTitlesChanged()");
        String audioStateBody = methodBody(source, "private void updateAudioOnlyState()", "protected void onTitlesChanged()");
        String startBody = methodBody(source, "protected void onStart()", "protected void onStop()");
        String resumeBody = methodBody(source, "protected void onResume()", "public void onConfigurationChanged(@NonNull Configuration newConfig)");
        String configBody = methodBody(source, "public void onConfigurationChanged(@NonNull Configuration newConfig)", "public void onWindowFocusChanged(boolean hasFocus)");

        assertTrue("mobile track changes must refresh lyrics after reconciling the final track set",
                tracksBody.contains("updateAudioOnlyState();")
                        && tracksBody.contains("syncPiPForPlaybackMode();")
                        && tracksBody.contains("refreshLyrics();"));
        assertTrue("mobile audio state must update desktop lyrics, stage visibility, and karaoke actions",
                audioStateBody.contains("LyricsController.isAudioOnly(player())")
                        && audioStateBody.contains("syncDesktopLyricsAudioContent();")
                        && audioStateBody.contains("setAudioStageVisible(shouldUseImmersiveAudio());")
                        && audioStateBody.contains("setKaraokeActionState();"));
        assertTrue("mobile start must reset stale audio state before refreshing the current player",
                startBody.indexOf("setAudioOnly(false);") >= 0
                        && startBody.indexOf("setAudioOnly(false);") < startBody.indexOf("refreshLyrics();")
                        && startBody.contains("syncLyricsPlaybackState();")
                        && startBody.contains("syncKaraokePosition();"));
        assertTrue("mobile resume must restore the audio stage and synchronize lyrics playback",
                resumeBody.contains("if (mAudioStageVisible) restorePlaybackArtwork();")
                        && resumeBody.contains("if (mAudioStageVisible) applyAudioBackground();")
                        && resumeBody.contains("syncLyricsPlaybackState();")
                        && resumeBody.contains("syncKaraokePosition();"));
        assertTrue("mobile rotation must recreate a visible audio stage before applying video fullscreen rules",
                configBody.contains("if (shouldRecreateAudioStageForOrientation(newConfig))")
                        && configBody.contains("setAudioOnly(true);")
                        && configBody.contains("recreate();"));
    }

    @Test
    public void videoAudioControllersAreReleasedWithTheirActivity() throws Exception {
        Path leanbackPath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String leanback = new String(Files.readAllBytes(leanbackPath), StandardCharsets.UTF_8);
        String leanbackDestroy = methodBody(leanback, "protected void onDestroy()", "public String getSubtitlePlaybackKey()");
        assertTrue("TV destroy must invalidate lyric work and close lyric sheets",
                leanbackDestroy.contains("mLyricsSearchSeq++;")
                        && leanbackDestroy.contains("mLyricsRefreshSeq++;")
                        && leanbackDestroy.contains("dismissLyricsResultDialog();"));
        assertTrue("TV destroy must release lyric and karaoke controllers",
                leanbackDestroy.contains("mLyrics.release();") && leanbackDestroy.contains("mKaraoke.release();"));

        Path mobilePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String mobile = new String(Files.readAllBytes(mobilePath), StandardCharsets.UTF_8);
        String mobileDestroy = methodBody(mobile, "protected void onDestroy()", "public String getSubtitlePlaybackKey()");
        assertTrue("mobile destroy must dismiss configuration-owned karaoke UI and cancel pitch generation",
                mobileDestroy.contains("dismissKaraokeResultDialogForRecreation();")
                        && mobileDestroy.contains("cancelKaraokePitchGeneration(false);")
                        && mobileDestroy.contains("dismissLyricsResultDialog();"));
        assertTrue("mobile destroy must release lyric and karaoke controllers",
                mobileDestroy.contains("mLyrics.release();") && mobileDestroy.contains("mKaraoke.release();"));
    }

    @Test
    public void videoAudioCallbacksKeepPlaybackStateSynchronized() throws Exception {
        Path leanbackPath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String leanback = new String(Files.readAllBytes(leanbackPath), StandardCharsets.UTF_8);
        String leanbackError = methodBody(leanback, "protected void onError(String msg)", "protected void onReclaim()");
        String leanbackPlaying = methodBody(leanback, "protected void onPlayingChanged(boolean isPlaying)", "protected void onSizeChanged(VideoSize size)");
        assertTrue("TV playback errors must clear stale lyrics and karaoke state",
                leanbackError.contains("clearLyrics();") && leanbackError.contains("clearKaraokeState();"));
        assertTrue("TV play/pause changes must update karaoke position and the immersive audio transport",
                leanbackPlaying.contains("syncKaraokePosition();") && leanbackPlaying.contains("checkAudioPlayImg(isPlaying);"));

        Path mobilePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String mobile = new String(Files.readAllBytes(mobilePath), StandardCharsets.UTF_8);
        String mobileError = methodBody(mobile, "protected void onError(String msg)", "protected void onReload(String msg)");
        String mobileState = methodBody(mobile, "protected void onStateChanged(int state)", "protected void onPlayingChanged(boolean isPlaying)");
        String mobilePlaying = methodBody(mobile, "protected void onPlayingChanged(boolean isPlaying)", "protected void onSizeChanged(VideoSize size)");
        assertTrue("mobile playback errors must clear stale lyrics and karaoke state",
                mobileError.contains("clearLyrics();") && mobileError.contains("clearKaraokeState();"));
        assertTrue("mobile READY state must refresh lyrics and reset the karaoke result for a new playback",
                mobileState.contains("mPendingKaraokeResult == null")
                        && mobileState.contains("mKaraokeResultShown = false;")
                        && mobileState.contains("refreshLyrics();"));
        assertTrue("mobile play/pause changes must synchronize lyrics, karaoke, PiP, and the audio transport",
                mobilePlaying.contains("syncLyricsPlaybackState(isPlaying);")
                        && mobilePlaying.contains("syncKaraokePosition();")
                        && mobilePlaying.contains("syncPiPForPlaybackMode()")
                        && mobilePlaying.contains("checkAudioPlayImg("));
    }

    @Test
    public void videoPlayerResultsKeepMergedAudioMetadata() throws Exception {
        Path leanbackPath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String leanback = new String(Files.readAllBytes(leanbackPath), StandardCharsets.UTF_8);
        String leanbackPlayer = methodBody(leanback, "private void setPlayer(Result result)", "private boolean redirectToContentHandler(Result result)");
        assertTrue("TV results without artwork must restore the selected episode artwork", leanbackPlayer.contains("else applyPlaybackArtwork(getPlaybackEpisode());"));
        assertTrue("TV result descriptions must remain available as inline lyrics", leanbackPlayer.contains("setPlaybackLyrics(result.getDesc());"));
        assertTrue("TV audio queue metadata must be applied before playback starts", leanbackPlayer.contains("applyAudioQueueMetadata(getPlaybackEpisode());"));

        Path mobilePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String mobile = new String(Files.readAllBytes(mobilePath), StandardCharsets.UTF_8);
        String mobilePlayer = methodBody(mobile, "private void setPlayer(Result result)", "private boolean redirectToAudioIfNeeded(Result result)");
        assertTrue("mobile player results must wait until the playback service is connected",
                mobilePlayer.contains("if (service() == null)") && mobilePlayer.contains("mPendingPlayerResult = result;"));
        assertTrue("mobile results without artwork must restore the selected episode artwork", mobilePlayer.contains("else applyPlaybackArtwork(getPlaybackEpisode());"));
        assertTrue("mobile result descriptions must remain available as inline lyrics", mobilePlayer.contains("setPlaybackLyrics(result.getDesc());"));
        assertTrue("mobile audio queue metadata must be applied before playback starts", mobilePlayer.contains("applyAudioQueueMetadata(getPlaybackEpisode());"));
    }

    @Test
    public void mobilePlaybackServiceConsumesPendingMergedState() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String connected = methodBody(source, "protected void onServiceConnected()", "protected void onPlayerRebuilt()");
        String detail = methodBody(source, "private void setDetail(Vod item)", "private void setText(Vod item)");

        assertTrue("mobile service connection must restore desktop lyrics state before consuming queued playback",
                connected.contains("syncDesktopLyricsAudioContent();")
                        && connected.contains("if (consumePendingPlaybackResult()) return;")
                        && connected.indexOf("consumePendingPlaybackResult()") < connected.indexOf("checkId();"));
        assertTrue("mobile detail results must wait for the playback service just like player results",
                detail.contains("if (service() == null)") && detail.contains("mPendingDetailVod = item;"));
    }

    @Test
    public void detailSwitchClearsReusableAudioControllersInsteadOfReleasingThem() throws Exception {
        assertDetailSwitchKeepsReusableAudioControllers("TV",
                findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));
        assertDetailSwitchKeepsReusableAudioControllers("mobile",
                findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));
    }

    private static void assertDetailSwitchKeepsReusableAudioControllers(String label, Path sourcePath) throws Exception {
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String body = methodBody(source, "private void getDetail(Vod item)", "private void setDetail(Result result)");

        assertTrue(label + " detail switching must clear reusable lyrics and karaoke state",
                body.contains("clearLyrics();") && body.contains("clearKaraokeState();"));
        assertFalse(label + " detail switching must not release activity-owned audio controllers", body.contains(".release();"));
        assertTrue(label + " detail switching must invalidate lyrics searches exactly once", occurrences(body, "mLyricsSearchSeq++;") == 1);
    }

    @Test
    public void videoClockKeepsLyricsAndKaraokeAdvancing() throws Exception {
        Path leanbackPath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String leanback = new String(Files.readAllBytes(leanbackPath), StandardCharsets.UTF_8);
        String leanbackClock = methodBody(leanback, "public void onTimeChanged(long time)", "private void updatePlaybackHistoryPosition()");
        assertTrue("TV clock ticks must advance lyrics and karaoke state",
                leanbackClock.contains("syncKaraokePosition();")
                        && leanbackClock.contains("mLyrics.update(player());")
                        && leanbackClock.contains("mKaraoke.update(player(), mLyrics == null ? null : mLyrics.getLines());"));

        Path mobilePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String mobile = new String(Files.readAllBytes(mobilePath), StandardCharsets.UTF_8);
        String mobileClock = methodBody(mobile, "public void onTimeChanged(long time)", "private void updatePlaybackHistoryPosition()");
        assertTrue("mobile clock ticks must learn playlist metadata and advance lyrics and karaoke state",
                mobileClock.contains("syncCurrentAudioPlaylistMetadata();")
                        && mobileClock.contains("syncKaraokePosition();")
                        && mobileClock.contains("mLyrics.update(player());")
                        && mobileClock.contains("mKaraoke.update(player(), mLyrics == null ? null : mLyrics.getLines());"));
    }

    @Test
    public void videoClockIgnoresTicksBeforeHistoryInitialization() throws Exception {
        Path leanbackPath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String leanback = new String(Files.readAllBytes(leanbackPath), StandardCharsets.UTF_8);
        String leanbackClock = methodBody(leanback, "public void onTimeChanged(long time)", "private void updatePlaybackHistoryPosition()");
        assertTrue("TV clock ticks can arrive before history initialization",
                leanbackClock.contains("if (!isOwner() || mHistory == null) return;"));

        Path mobilePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String mobile = new String(Files.readAllBytes(mobilePath), StandardCharsets.UTF_8);
        String mobileClock = methodBody(mobile, "public void onTimeChanged(long time)", "private void updatePlaybackHistoryPosition()");
        assertTrue("mobile clock ticks can arrive before history initialization",
                mobileClock.contains("if (!isOwner() || mHistory == null) return;"));
    }

    @Test
    public void playbackHistoryKeepsUnknownDurationUntilMediaReportsOne() throws Exception {
        for (Path sourcePath : List.of(
                findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")),
                findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")))) {
            String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
            String body = methodBody(source,
                    "private void updatePlaybackHistoryPosition()",
                    "PlaybackEventCollector.get().updateHistory(mHistory)");
            assertTrue(sourcePath + " must ignore an unknown player duration",
                    body.contains("if (duration > 0) mHistory.setDuration(duration);"));
            assertFalse(sourcePath + " must not materialize an unknown duration as zero",
                    body.contains("mHistory.setDuration(0)"));
        }
    }

    @Test
    public void videoDetailTextKeepsInlineLyricsMetadata() throws Exception {
        Path leanbackPath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String leanback = new String(Files.readAllBytes(leanbackPath), StandardCharsets.UTF_8);
        String leanbackText = methodBody(leanback, "private void setText(Vod item)", "private void setText(TextView view");
        assertTrue("TV detail content must remain available as inline lyrics", leanbackText.contains("setDetailLyrics(item.getContent());"));
        assertTrue("TV detail binding must refresh immersive audio labels", leanbackText.contains("updateAudioStageText();"));

        Path mobilePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String mobile = new String(Files.readAllBytes(mobilePath), StandardCharsets.UTF_8);
        String mobileText = methodBody(mobile, "private void setText(Vod item)", "private boolean shouldUseTmdbTabletWideLayout()");
        assertTrue("mobile detail content must be captured before TMDB reveal can return early",
                mobileText.indexOf("setDetailLyrics(item.getContent());") >= 0
                        && mobileText.indexOf("setDetailLyrics(item.getContent());") < mobileText.indexOf("if (isTmdbDetailEnrichmentPending())"));
        assertTrue("mobile detail binding must refresh immersive audio labels", mobileText.contains("updateAudioStageText();"));
    }

    @Test
    public void mobileConfigurationRestorePreservesPlaybackAndAudioMetadata() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String init = methodBody(source, "protected void initView(Bundle savedInstanceState)", "private void setupIntroSkipConfirmListener()");
        String checkFlag = methodBody(source, "private void checkFlag(Vod item)", "private boolean checkHistory(Vod item)");
        String episodeClick = methodBody(source, "public void onItemClick(Episode item)", "public void onItemClick(EpisodeGroupAdapter.Group item)");

        assertTrue("mobile recreation must remember that an existing service playback should be preserved",
                init.contains("mRestoringConfigurationPlayback = savedInstanceState != null;"));
        assertTrue("mobile detail restore must bind flags without starting the same episode again",
                checkFlag.contains("boolean preservePlayback = mRestoringConfigurationPlayback")
                        && checkFlag.contains("restoreFlagSelectionWithoutPlayback();")
                        && checkFlag.contains("mRestoringConfigurationPlayback = false;"));
        assertTrue("mobile episode switches must persist current playlist metadata before selecting the next item",
                episodeClick.contains("syncCurrentAudioPlaylistMetadata();")
                        && episodeClick.contains("applyAudioQueueMetadata(item);")
                        && episodeClick.indexOf("syncCurrentAudioPlaylistMetadata();") < episodeClick.indexOf("mFlagAdapter.toggle(item);"));
    }

    @Test
    public void autoFfmpegFallbackResetRebuildsExoBeforeNextItem() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "player", "PlayerManager.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String resetFallback = methodBody(source, "private void resetFfmpegModeFallback()", "static boolean shouldStopOnManualSwitchFailure");
        String start = methodBody(source, "public void start(PlaySpec spec, long timeout, boolean playWhenReady)", "public void parse(String key, Result result, boolean useParse, MediaMetadata metadata)");
        String parse = methodBody(source, "public void parse(String key, Result result, boolean useParse, MediaMetadata metadata, boolean playWhenReady)", "private void stopParse()");
        String release = methodBody(source, "public void release()", "private void resetLutRuntimeState");

        assertTrue("clearing an AUTO override must remember that the current EXO engine was built with a stale renderer mode",
                resetFallback.contains("ffmpegModeEngineRefreshPending =")
                        && resetFallback.contains("PlayerSetting.clearFFmpegModeOverride();"));
        assertTrue("direct playback must refresh a stale AUTO-mode EXO engine before preparing the next item",
                start.contains("refreshFfmpegModeEngineIfNeeded();")
                        && start.indexOf("refreshFfmpegModeEngineIfNeeded();") < start.indexOf("setMediaItem(timeout);"));
        assertTrue("parsed playback must also refresh a stale AUTO-mode EXO engine before starting parse work",
                parse.contains("refreshFfmpegModeEngineIfNeeded();")
                        && parse.indexOf("refreshFfmpegModeEngineIfNeeded();") < parse.indexOf("ParseJob.create(this).start(result, useParse);"));
        assertTrue("destroying the manager must clear the process-wide AUTO override without scheduling another rebuild",
                release.contains("clearFfmpegModeFallbackState();"));
    }

    @Test
    public void playbackSpeedInitializationUsesPersonalDefaultSpeed() throws Exception {
        String mobile = new String(Files.readAllBytes(findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"))), StandardCharsets.UTF_8);
        String leanback = new String(Files.readAllBytes(findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"))), StandardCharsets.UTF_8);
        String mobileControl = new String(Files.readAllBytes(findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "ControlDialog.java"))), StandardCharsets.UTF_8);
        String leanbackControl = new String(Files.readAllBytes(findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "ControlDialog.java"))), StandardCharsets.UTF_8);
        String playerManager = new String(Files.readAllBytes(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "player", "PlayerManager.java"))), StandardCharsets.UTF_8);
        String mobileCheckHistory = methodBody(mobile, "private boolean checkHistory(Vod item)", "private void enrichHistoryMeta(Vod item)");
        String mobileSetSpeed = methodBody(mobile, "private void setSpeed()", "private void checkOrientation()");
        String mobilePlaybackSpeed = methodBody(mobile, "private float getPlaybackSpeed()", "private void checkOrientation()");
        String mobileSaveUserSpeed = methodBody(mobile, "private void saveUserSpeed()", "private void onReset()");
        String mobileSpeedLong = methodBody(mobile, "private boolean onSpeedLong()", "private void saveUserSpeed()");
        String mobileSpeedEnd = methodBody(mobile, "public void onSpeedEnd()", "public void onBright(int progress)");
        String mobileApplySpeed = methodBody(mobileControl, "private void applySpeed(float speed)", "private void setSpeedPreset(View view)");
        String leanbackFastHistory = methodBody(leanback, "private void prepareFastTmdbPlaybackHistory(Vod item, Flag flag, Episode episode)", "private void selectFastTmdbPlaybackEpisode(Vod item, Flag selectedFlag, Episode selectedEpisode)");
        String leanbackCheckHistory = methodBody(leanback, "private boolean checkHistory(Vod item)", "private void enrichHistoryMeta(Vod item)");
        String leanbackSetSpeed = methodBody(leanback, "private void setSpeed()", "private void checkEnded(boolean notify)");
        String leanbackPlaybackSpeed = methodBody(leanback, "private float getPlaybackSpeed()", "private void checkEnded(boolean notify)");
        String leanbackSaveUserSpeed = methodBody(leanback, "private void saveUserSpeed()", "private void onReset()");
        String leanbackSpeedLong = methodBody(leanback, "private boolean onSpeedLong()", "private void saveUserSpeed()");
        String leanbackSpeedEnd = methodBody(leanback, "public void onSpeedEnd()", "public void onKeyUp()");
        String leanbackApplySpeed = methodBody(leanbackControl, "private void applySpeed(float speed)", "private void setSpeedPreset(View view)");
        String defaultToggleSpeed = methodBody(playerManager, "public String toggleSpeed()", "public String toggleSpeed(float normalSpeed)");
        String toggleSpeed = methodBody(playerManager, "public String toggleSpeed(float normalSpeed)", "private float nextPresetSpeed()");

        assertTrue("mobile history speed fallback must use the default-aware helper", mobileCheckHistory.contains("float speed = getPlaybackSpeed();"));
        assertTrue("mobile checkHistory must not fall back to hardcoded 1.0x", !mobileCheckHistory.contains(": 1f"));
        assertFalse("mobile startup must not create a per-show speed override", mobileCheckHistory.contains("mHistory.setSpeed(player().getSpeed())"));
        assertTrue("mobile onPrepare speed restore must apply the default-aware helper", mobileSetSpeed.contains("player().setSpeed(getPlaybackSpeed())"));
        assertTrue("mobile speed helper must resolve explicit 1.0x separately from the personal default", mobilePlaybackSpeed.contains("mHistory.getPlaybackSpeed(PlayerSetting.getDefaultSpeed())"));
        assertTrue("mobile in-player speed changes must be saved only for the current show", mobileSaveUserSpeed.contains("mHistory.setUserSpeed(player().getSpeed())") && !mobileSaveUserSpeed.contains("PlayerSetting.putDefaultSpeed"));
        assertTrue("mobile persistent speed toggle must use the current show's effective speed", mobileSpeedLong.contains("player().toggleSpeed(getPlaybackSpeed())"));
        assertTrue("mobile speed dialog must be a per-show override", mobileApplySpeed.contains("history.setUserSpeed(player.getSpeed())") && !mobileApplySpeed.contains("PlayerSetting.putDefaultSpeed"));
        assertTrue("mobile hold release must restore the current show's effective speed", mobileSpeedEnd.contains("player().setSpeed(getPlaybackSpeed())"));
        assertTrue("leanback fast TMDB playback must use the default-aware helper", leanbackFastHistory.contains("float speed = getPlaybackSpeed();"));
        assertFalse("leanback fast startup must not create a per-show speed override", leanbackFastHistory.contains("mHistory.setSpeed(player().getSpeed())"));
        assertTrue("leanback history speed fallback must use the default-aware helper", leanbackCheckHistory.contains("float speed = getPlaybackSpeed();"));
        assertTrue("leanback checkHistory must not fall back to hardcoded 1.0x", !leanbackCheckHistory.contains(": 1f"));
        assertFalse("leanback startup must not create a per-show speed override", leanbackCheckHistory.contains("mHistory.setSpeed(player().getSpeed())"));
        assertTrue("leanback onPrepare speed restore must apply the default-aware helper", leanbackSetSpeed.contains("player().setSpeed(getPlaybackSpeed())"));
        assertTrue("leanback speed helper must resolve explicit 1.0x separately from the personal default", leanbackPlaybackSpeed.contains("mHistory.getPlaybackSpeed(PlayerSetting.getDefaultSpeed())"));
        assertTrue("leanback in-player speed changes must be saved only for the current show", leanbackSaveUserSpeed.contains("mHistory.setUserSpeed(player().getSpeed())") && !leanbackSaveUserSpeed.contains("PlayerSetting.putDefaultSpeed"));
        assertTrue("leanback persistent speed toggle must use the current show's effective speed", leanbackSpeedLong.contains("player().toggleSpeed(getPlaybackSpeed())"));
        assertTrue("leanback speed dialog must be a per-show override", leanbackApplySpeed.contains("history.setUserSpeed(player.getSpeed())") && !leanbackApplySpeed.contains("PlayerSetting.putDefaultSpeed"));
        assertTrue("leanback hold release must restore the current show's effective speed", leanbackSpeedEnd.contains("player().setSpeed(getPlaybackSpeed())"));
        assertTrue("shared speed toggle must preserve the normal 1.0x baseline for live and cast playback", defaultToggleSpeed.contains("return toggleSpeed(1.0f, 1.0f);"));
        assertTrue("VOD speed toggle must use the personal default when a restored player has no session baseline", toggleSpeed.contains("toggleSpeed(normalSpeed, PlayerSetting.getDefaultSpeed())"));
        assertTrue("speed toggle must resolve the target through the stable session state", toggleSpeed.contains("speedToggleState.next(getSpeed(), normalSpeed, PlayerSetting.getSpeed(), fallbackSpeed)"));
    }

    @Test
    public void leanbackSpeedBoostReleaseIsGuarded() throws Exception {
        String leanback = new String(Files.readAllBytes(findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"))), StandardCharsets.UTF_8);
        String keyDown = new String(Files.readAllBytes(findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "custom", "CustomKeyDownVod.java"))), StandardCharsets.UTF_8);
        String releaseBody = methodBody(keyDown, "public void releaseSpeed()", "public void setFull(boolean full)");

        assertTrue("CustomKeyDownVod must expose the guarded speed release state", keyDown.contains("public boolean isChangingSpeed()") && keyDown.contains("public void releaseSpeed()"));
        assertTrue("releaseSpeed must clear the flag and end the boost", releaseBody.contains("changeSpeed = false;") && releaseBody.contains("listener.onSpeedEnd();"));
        assertTrue("dispatchKeyEvent must release the boost when a key is released outside the state machine", methodBody(leanback, "public boolean dispatchKeyEvent(KeyEvent event)", "private boolean dispatchLutQuickKey(KeyEvent event)").contains("mKeyDown.releaseSpeed()"));
        assertTrue("onWindowFocusChanged must release the boost when window focus is lost", methodBody(leanback, "public void onWindowFocusChanged(boolean hasFocus)", "private boolean isInitAuto()").contains("mKeyDown.releaseSpeed()"));
        assertTrue("onStop must release the boost", methodBody(leanback, "protected void onStop()", "protected void onBackInvoked()").contains("mKeyDown.releaseSpeed()"));
    }

    @Test
    public void refreshedPlayerKernelSwitchKeepsManualFailureSemantics() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "player", "PlayerManager.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("public void switchPlayer(int type, Result result");
        int methodEnd = source.indexOf("private void switchPlayer(int type, boolean manual)", method);
        String methodBody = method >= 0 && methodEnd > method ? source.substring(method, methodEnd) : "";

        assertTrue(sourcePath + " is missing refreshed-result player switching", method >= 0);
        assertTrue("a user-selected refreshed core must stop instead of auto-falling back on its first failure", methodBody.contains("manualPlayerSwitchPending = true;"));
    }

    @Test
    public void playerKernelSwitchStaysScopedToCurrentPlayback() throws Exception {
        String player = new String(Files.readAllBytes(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "player", "PlayerManager.java"))), StandardCharsets.UTF_8);
        String leanback = new String(Files.readAllBytes(findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"))), StandardCharsets.UTF_8);
        String mobile = new String(Files.readAllBytes(findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"))), StandardCharsets.UTF_8);
        String tmdb = new String(Files.readAllBytes(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"))), StandardCharsets.UTF_8);
        String history = new String(Files.readAllBytes(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "bean", "History.java"))), StandardCharsets.UTF_8);

        assertFalse("switching cores inside the player must not rewrite the global default kernel", player.contains("PlayerSetting.putPlayer("));
        assertTrue("the running kernel must be published as session state instead", player.contains("PlayerSetting.putActivePlayer("));
        assertTrue("ending playback must drop the session kernel so the global default applies again", player.contains("PlayerSetting.clearActivePlayer();"));

        assertTrue("the per-show kernel must be persisted, not a transient field", history.contains("@SerializedName(\"player\")") && !history.contains("private transient int player"));

        for (String source : new String[]{leanback, mobile}) {
            assertTrue("playback must restore the show's remembered kernel before resolving the play url",
                    source.contains("player().preparePlayer(kernel);"));
            assertTrue("the remembered kernel must fall back to the global default", source.contains("mHistory.getPlayerOrDefault()"));
            assertTrue("the show's history must remember the user's selection, not an engine/session state",
                    source.contains("private void rememberPlayerKernel(int type)") && source.contains("mHistory.setPlayer(type);"));
            // 播放页会重叠存在：上一部剧的收尾存档若也写内核，就会用别人的会话内核
            // 覆盖本剧记住的选择，历史回归点。
            assertFalse("routine history saves must not rewrite the show's kernel",
                    source.contains("mHistory.setPlayer(PlayerSetting.getActivePlayer());"));
            // 服务可能是上一次播放留活的，它建 PlayerManager 时读到的是上一部剧的内核，
            // 所以服务就绪前定下的选择必须在连上后补落到引擎。
            assertTrue("a kernel chosen before the service was ready must be applied once it connects",
                    source.contains("mPendingPlayerKernel = kernel;")
                            && source.contains("private void applyPendingPlayerKernel()")
                            && methodBody(source, "protected void onServiceConnected()", "\n    }").contains("applyPendingPlayerKernel();"));
        }
        assertTrue("inline TMDB playback must resolve the play url with the show's kernel",
                tmdb.contains("SiteApi.playerContent(key, flag, episodeUrl, playerKernel)"));
        assertTrue("inline TMDB playback must restore the show's remembered kernel",
                tmdb.contains("player().preparePlayer(inlineHistoryPlayerKernel());"));
        assertTrue("inline TMDB playback must remember only the user's selection",
                tmdb.contains("private void rememberInlinePlayerKernel(int type)") && tmdb.contains("history.setPlayer(type);"));
        assertFalse("inline progress sync must not rewrite the show's kernel",
                tmdb.contains("history.setPlayer(PlayerSetting.getActivePlayer());"));
    }

    @Test
    public void videoActivitiesDelegateSharedPlayerUiSetup() throws Exception {
        String leanback = new String(Files.readAllBytes(findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"))), StandardCharsets.UTF_8);
        String mobile = new String(Files.readAllBytes(findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"))), StandardCharsets.UTF_8);
        String host = new String(Files.readAllBytes(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "player", "VodPlayerUiHost.java"))), StandardCharsets.UTF_8);
        String controller = new String(Files.readAllBytes(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "player", "VodPlayerUiController.java"))), StandardCharsets.UTF_8);
        String chrome = new String(Files.readAllBytes(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "player", "VodPlayerChrome.java"))), StandardCharsets.UTF_8);

        assertVideoActivityDelegatesSharedPlayerUiSetup("leanback VideoActivity", leanback, "VodPlayerChrome.fromVideo(mBinding, mBinding.widget.clock, 14f)");
        assertVideoActivityDelegatesSharedPlayerUiSetup("mobile VideoActivity", mobile, "VodPlayerChrome.fromVideo(mBinding, null, 12f)");
        assertTrue("shared video chrome factory must expose OSD, optional clock and optional diagnostics button",
                chrome.contains("fromVideo(ActivityVideoBinding binding, TextView clockView, float osdMiniSp)")
                        && chrome.contains("binding.osd.getRoot()")
                        && chrome.contains("clockView")
                        && chrome.contains("osdMiniSp"));
        assertTrue("shared player UI controller must create Clock with the optional chrome clock view",
                controller.contains("chrome.clockView == null ? Clock.create() : Clock.create(chrome.clockView)"));
        assertTrue("shared host should let activities preserve their previous diagnostics restore behavior",
                host.contains("default boolean restoreDiagnosticsOnStart()") && host.contains("return true;"));
        assertTrue("shared controller should only restore diagnostics visibility when the host opts in",
                controller.contains("if (host.restoreDiagnosticsOnStart()) osd.setDiagnosticsVisible(PlayerSetting.isOsdDiagnostics());"));
        assertTrue("mobile VideoActivity should restore persistent diagnostics via the host default",
                !mobile.contains("public boolean restoreDiagnosticsOnStart()"));
        assertTrue("leanback VideoActivity should keep persistent diagnostics restore via the host default",
                !leanback.contains("public boolean restoreDiagnosticsOnStart()"));
    }

    private static void assertVideoActivityDelegatesSharedPlayerUiSetup(String label, String source, String chromeFactory) {
        int init = source.indexOf("protected void initView(Bundle savedInstanceState)");
        int initEnd = source.indexOf("private void setRecyclerView()", init);
        int start = source.indexOf("protected void onStart()");
        int startEnd = source.indexOf("protected void onStop()", start);
        int stopEnd = source.indexOf("\n    @Override", startEnd + 1);
        int destroy = source.indexOf("protected void onDestroy()");
        int destroyEnd = source.indexOf("private boolean isOwner()", destroy);
        if (stopEnd < 0) stopEnd = destroy;
        if (destroyEnd < 0) destroyEnd = source.length();
        String initBody = source.substring(init, initEnd);
        String startBody = source.substring(start, startEnd);
        String stopBody = source.substring(startEnd, stopEnd);
        String destroyBody = source.substring(destroy, destroyEnd);

        assertTrue(label + " must own a shared player UI controller field", source.contains("private VodPlayerUiController mPlayerUi;"));
        assertTrue(label + " must create shared player UI controller with the variant chrome", initBody.contains("mPlayerUi = new VodPlayerUiController") && initBody.contains(chromeFactory));
        assertTrue(label + " must backfill legacy playback UI fields during migration",
                initBody.contains("mClock = mPlayerUi.clock();")
                        && initBody.contains("mOsd = mPlayerUi.osd();")
                        && initBody.contains("mPiP = mPlayerUi.pip();"));
        assertTrue(label + " should not instantiate duplicate OSD/Clock/PiP helpers in initView",
                !initBody.contains("new PlayerOsdController")
                        && !initBody.contains("Clock.create(")
                        && !initBody.contains("new PiP()"));
        assertTrue(label + " must delegate shared UI lifecycle",
                startBody.contains("mPlayerUi.onStart();")
                        && stopBody.contains("mPlayerUi.onStop();")
                        && destroyBody.contains("mPlayerUi.release();"));
    }

    @Test
    public void mobileShortDramaKeepsStandardSettingButtonVisible() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int showControl = source.indexOf("private void showControl()");
        int shortDrama = source.indexOf("boolean shortDrama = isShortDramaSource();", showControl);
        int setting = source.indexOf("mBinding.control.setting.setVisibility(mHistory == null || (isFullscreen() && !shortDrama) ? View.GONE : View.VISIBLE);", shortDrama);
        int shortDramaViews = source.indexOf("private View[] getShortDramaControlViews()");
        int dockedSetting = source.indexOf("mBinding.control.setting,", shortDramaViews);

        assertTrue(sourcePath + " is missing showControl", showControl >= 0);
        assertTrue("short drama mode must keep the standard setting button visible while fullscreen", setting > shortDrama);
        assertTrue("short drama floating controls must include the standard setting button", dockedSetting > shortDramaViews);
    }

    @Test
    public void mobileShortDramaDocksChangeSourceAndQuality() throws Exception {
        // 反馈回归：短剧模式把整条 action 栏 GONE 掉，换源与画质只有搬进 dock 才可达，
        // 否则短剧只能在选集里换线路(flag)，无法换站点、无法切画质。
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int views = source.indexOf("private View[] getShortDramaControlViews()");
        assertTrue("getShortDramaControlViews must exist", views >= 0);
        String body = source.substring(views, source.indexOf("\n    }", views));
        // dock 里全是 48dp 图标，换源/画质/选集必须用专用 ImageView 入口，
        // 不能直接搬 action 栏的 MaterialTextView，否则文字按钮与图标混排（见用户反馈截图）。
        assertTrue("short drama dock must expose the change-source icon",
                body.contains("mBinding.control.shortDramaChangeSource,"));
        assertTrue("short drama dock must expose the quality icon",
                body.contains("mBinding.control.shortDramaQuality,"));
        assertFalse("short drama dock must not mix in action-bar text buttons",
                body.contains("mBinding.control.action."));

    }

    @Test
    public void mobileShortDramaPresentationSurvivesSourceChange() throws Exception {
        // 反馈回归：换源(getDetail)会改写 intent 的 key，isShortDramaSource() 随之翻转为 false，
        // 同一个竖屏会话中途退回长视频布局（右侧 dock 被拆、横屏 action 栏与全屏按钮露出）。
        // 呈现形态与返回键行为必须读会话态，只有换到新条目(onNewIntent)才重置。
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);

        assertTrue("session flag must latch once the site-based check passes",
                source.contains("if (isShortDramaSource()) shortDramaSession = true;"));

        // 重置必须发生在 onNewIntent 里（换到新条目），而不是随便某处 —— 只断言赋值语句存在太弱。
        String newIntent = methodBody(source, "protected void onNewIntent(Intent intent)", "protected void initView");
        assertTrue("switching to another title must reset the session presentation",
                newIntent.contains("shortDramaSession = false;"));
        // 短剧 -> 非短剧新条目时必须交还竖屏方向与全屏布局，否则长视频卡在竖屏全屏无法旋转
        int restore = newIntent.indexOf("if (shortDramaSession && !isShortDramaSource()) exitFullscreen();");
        int reset = newIntent.indexOf("shortDramaSession = false;");
        assertTrue("leaving a short drama session must hand back the portrait fullscreen chrome", restore >= 0);
        assertTrue("the fullscreen handback must read the flag before it is cleared", restore < reset);

        int showControl = source.indexOf("private void showControl()");
        assertTrue("showControl must exist", showControl >= 0);
        assertTrue("showControl must drive chrome from the session state",
                source.indexOf("boolean shortDrama = isShortDramaSession();", showControl) > showControl);
        assertFalse("presentation call sites must not re-derive short drama from the current site",
                source.contains("canShowPiP(isShortDramaSource())")
                        || source.contains("isFullscreen() && isShortDramaSource()"));
    }

    @Test
    public void mobileShortDramaGesturesSwapAxesToAvoidMisfires() throws Exception {
        // 用户反馈：竖屏短剧铺满屏幕，左右 1/4 竖滑调亮度/音量与中间竖滑切集互相误触。
        // 短剧形态改为「整屏上下滑切集 + 长按后上下滑调亮度/音量」，两个手势类同步。
        List<Path> gestureFiles = Arrays.asList(
                findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "custom", "CustomKeyDown.java")),
                findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "custom", "PlayerGesture.java"))
        );

        for (Path gestureFile : gestureFiles) {
            String source = new String(Files.readAllBytes(gestureFile), StandardCharsets.UTF_8);
            assertTrue(gestureFile + " must expose the short drama gesture mode", source.contains("public void setShortDrama(boolean shortDrama)"));
            // 上下滑切集不再受左右 1/4 亮度/音量分区限制。
            assertTrue(gestureFile + " must let short drama flings cover the side quarters",
                    source.contains("(!shortDrama && isSide(e1))"));
            // 亮度/音量不再由滑动起点的左右 1/4 触发。
            assertTrue(gestureFile + " must stop routing short drama scrolls to brightness/volume",
                    source.contains("else if (!shortDrama && isSide(e2)) checkSide(e2);"));
            // 长按后 GestureDetector 不再回调 onScroll，调节必须由 onTouchEvent 自己驱动。
            assertTrue(gestureFile + " must drive the long-press adjust from onTouchEvent",
                    source.contains("action == MotionEvent.ACTION_MOVE") && source.contains("handleAdjust(e);"));
            assertTrue(gestureFile + " must hand back the speed-up before adjusting", source.contains("private void startAdjust(MotionEvent e)"));
            // 起点必须在 ACTION_DOWN 时记：onDown 会在边缘/缩放/锁定时提前返回，
            // 拿上一次手势的起点算位移会让长按转调节一按就跳。
            assertTrue(gestureFile + " must capture the down point from the raw stream",
                    source.contains("if (action == MotionEvent.ACTION_DOWN) {") && source.contains("downY = e.getY();"));
            assertFalse(gestureFile + " must not rely on onDown for the down point",
                    methodBody(source, "public boolean onDown(@NonNull MotionEvent e)", "\n    }").contains("downY = e.getY();"));
            // 切集方向在短剧下固定，不跟随直播的「反转」开关。
            assertTrue(gestureFile + " must pin the short drama fling direction",
                    source.contains("boolean invert = !shortDrama && LiveSetting.isInvert();"));

            // onDown 在边缘/缩放/锁定时提前返回不走 reset()，所以每次抬手都要主动清标记，
            // 否则上一次的 changeBright + anchorY 会让下一次手势没长按就跳亮度。
            assertTrue(gestureFile + " must clear gesture flags when the stream ends",
                    source.contains("private void clearGesture()") && source.contains("if (end) clearGesture();"));
            assertTrue(gestureFile + " must reset the adjust anchor on cleanup",
                    methodBody(source, "private void clearGesture()", "\n    }").contains("anchorY = Float.NaN;"));
            // 没有本次手势自己建立的基准就不许调节，避免 NaN 基准算出静音/跟随系统亮度。
            assertTrue(gestureFile + " must require an anchor established by this gesture",
                    source.contains("if (!changeSpeed && Float.isNaN(anchorY)) return;"));
            // CANCEL（被父容器拦截/来电）同样要交还倍速，否则播放卡在长按后的速率。
            assertTrue(gestureFile + " must hand back the speed boost on cancel too", source.contains("if (changeSpeed && end) listener.onSpeedEnd();"));
            // ACTION_POINTER_UP 不会复位 multiTouch，用实时指数判断才不会永久挡掉单指调节。
            assertTrue(gestureFile + " must gate the adjust on the live pointer count",
                    source.contains("action == MotionEvent.ACTION_MOVE && e.getPointerCount() == 1"));
            // 未测量的播放视图会让 deltaY/height 变成 Infinity/NaN，音量瞬间拉满或静音。
            assertTrue(gestureFile + " must floor the view height before dividing",
                    methodBody(source, "private void setVolume(float deltaY)", "\n    }").contains("Math.max(videoView.getMeasuredHeight(), 1)"));
            assertTrue(gestureFile + " must skip volume when the stream has no range",
                    source.contains("if (maxVolume <= 0) return;"));
        }

        // 手势轴向必须由「当前是否处于短剧全屏」推导：退出全屏回到内嵌小窗后若仍是短剧那套，
        // 详情页上竖滑就会误切集；换到另一部短剧时形态不变，标记也不该被清掉。
        String video = new String(Files.readAllBytes(findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"))), StandardCharsets.UTF_8);
        assertTrue("the gesture mode must be derived from the current presentation",
                video.contains("mKeyDown.setShortDrama(isFullscreen() && isShortDramaSession());"));
        assertFalse("no call site may pin the gesture mode to a literal",
                video.contains("mKeyDown.setShortDrama(true)") || video.contains("mKeyDown.setShortDrama(false)"));
        for (String host : Arrays.asList("private void enterShortDramaFullscreen()", "private void enterFullscreen()", "private void exitFullscreen()", "protected void onNewIntent(Intent intent)")) {
            int start = video.indexOf(host);
            assertTrue(host + " must exist in VideoActivity", start >= 0);
            int end = video.indexOf("\n    }", start);
            assertTrue("presentation change must resync the gesture axes: " + host,
                    end > start && video.substring(start, end).contains("syncShortDramaGesture();"));
        }

        String tmdb = new String(Files.readAllBytes(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"))), StandardCharsets.UTF_8);
        assertTrue("the inline gesture mode must be derived from the current presentation",
                tmdb.contains("inlineGestureDetector.setShortDrama(inlineFullscreen && shouldUseInlineShortDramaMode());"));
        assertFalse("no inline call site may pin the gesture mode to a literal",
                tmdb.contains("inlineGestureDetector.setShortDrama(true)") || tmdb.contains("inlineGestureDetector.setShortDrama(false)"));
        // enterInlineFullscreen 要直接重算：手动点全屏不走 applyInlineShortDramaMode。
        // exitInlineFullscreen 经 resetInlineShortDramaMode 间接重算（它先置 inlineFullscreen=false）。
        for (String host : Arrays.asList("private void applyInlineShortDramaMode()", "private void resetInlineShortDramaMode()", "private void enterInlineFullscreen()")) {
            int start = tmdb.indexOf(host);
            assertTrue(host + " must exist in TmdbDetailActivity", start >= 0);
            int end = tmdb.indexOf("\n    }", start);
            assertTrue("presentation change must resync the inline gesture axes: " + host,
                    end > start && tmdb.substring(start, end).contains("syncInlineShortDramaGesture();"));
        }
        int exitInline = tmdb.indexOf("private void exitInlineFullscreen()");
        assertTrue("exitInlineFullscreen must exist", exitInline >= 0);
        String exitBody = tmdb.substring(exitInline, tmdb.indexOf("\n    }", exitInline));
        int cleared = exitBody.indexOf("inlineFullscreen = false;");
        int resync = exitBody.indexOf("resetInlineShortDramaMode();");
        assertTrue("leaving inline fullscreen must resync the gesture axes after clearing the flag",
                cleared >= 0 && resync > cleared);
    }

    @Test
    public void mobileShortDramaQualityIconHasSingleSourceOfTruth() throws Exception {
        // setQualityVisible 有 6 个调用点，其中多处显式传 false（未选中集数、切线路重置），
        // 与 Result.isMulti() 的结论并不一致。dock 图标若自行推导 Result，就会与 action 栏
        // 按钮状态相反。两者必须共用 setQualityVisible 记下的同一个结论。
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);

        String setter = methodBody(source, "private void setQualityVisible(boolean visible)", "private void updateActionQuality");
        assertTrue("setQualityVisible must record its verdict for the dock icon to reuse",
                setter.contains("mQualityVisible = visible;"));

        String sync = methodBody(source, "private void syncShortDramaControlLayout(boolean shortDrama)", "private void dockShortDramaControls");
        assertTrue("the docked quality icon must reuse the recorded verdict",
                sync.contains("mBinding.control.shortDramaQuality.setVisibility(mQualityVisible ? View.VISIBLE : View.GONE);"));
        // 注释里会提到 isMulti() 来解释为何不用它，所以先剥掉注释再断言
        String syncCode = sync.replaceAll("(?m)//.*$", "");
        assertFalse("the docked quality icon must not re-derive visibility from the player Result",
                syncCode.contains("isMulti()") || syncCode.contains("isQualityAvailable()"));
    }

    @Test
    public void mobileShortDramaRestoreIsIndependentOfDeclarationOrder() throws Exception {
        // 同一容器现在有多个搬迁项（cast/keep/换源/画质/选集/设置同属顶部栏）。逐个「摘下并立刻插回」
        // 会让后来者挤掉前者的位置，且 PlayerButtonSetting.applyOrder 可能已重排容器，
        // 声明顺序不等于索引升序。还原必须先全部摘下、再按原始索引升序插回。
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int restore = source.indexOf("private void restoreShortDramaControls()");
        assertTrue("restoreShortDramaControls must exist", restore >= 0);
        String body = source.substring(restore, source.indexOf("\n    }", restore));

        int detachLoop = body.indexOf("parent.removeView(item.view);");
        int sort = body.indexOf("items.sort(Comparator.comparingInt(item -> item.index));");
        int attachLoop = body.indexOf("item.parent.addView(item.view,");
        assertTrue("restore must detach every docked view before re-attaching", detachLoop >= 0);
        assertTrue("restore must re-attach in ascending original index order", sort > detachLoop);
        assertTrue("restore must re-attach after sorting", attachLoop > sort);
    }

    @Test
    public void mobileQualityButtonStaysGatedByMultiUrlResult() throws Exception {
        // 画质入口只在站点返回多个播放地址时出现；短剧同走这条判定，
        // 不能为了让按钮常驻而绕开 isMulti()，否则会弹出只有一个选项的空面板。
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        assertTrue("quality visibility must stay driven by Url.isMulti()",
                source.contains("setQualityVisible(result.getUrl().isMulti());"));
    }

    @Test
    public void mobileFullscreenButtonRevealsControlsAfterNativeEnhancedLayoutSettles() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);

        int onFullscreen = source.indexOf("private void onFullscreen()");
        int onFullscreenEnd = source.indexOf("private void onPiP()", onFullscreen);
        int enterFullscreen = source.indexOf("private void enterFullscreen()");
        int schedule = source.indexOf("private void scheduleFullscreenControlReveal()", enterFullscreen);
        int guard = source.indexOf("private void showControlIfFullscreen()", schedule);
        int exitFullscreen = source.indexOf("private void exitFullscreen()", guard);
        int configChanged = source.indexOf("public void onConfigurationChanged(@NonNull Configuration newConfig)");
        int configEnd = source.indexOf("public void onWindowFocusChanged(boolean hasFocus)", configChanged);

        assertTrue(sourcePath + " is missing onFullscreen", onFullscreen >= 0 && onFullscreenEnd > onFullscreen);
        assertTrue(sourcePath + " is missing enterFullscreen", enterFullscreen >= 0 && schedule > enterFullscreen);
        assertTrue(sourcePath + " is missing scheduleFullscreenControlReveal", schedule >= 0 && guard > schedule);
        assertTrue(sourcePath + " is missing showControlIfFullscreen", guard >= 0 && exitFullscreen > guard);
        assertTrue(sourcePath + " is missing onConfigurationChanged", configChanged >= 0 && configEnd > configChanged);

        String onFullscreenBody = source.substring(onFullscreen, onFullscreenEnd);
        String enterFullscreenBody = source.substring(enterFullscreen, schedule);
        String scheduleBody = source.substring(schedule, guard);
        String guardBody = source.substring(guard, exitFullscreen);
        String configBody = source.substring(configChanged, configEnd);

        assertTrue("fullscreen button must reveal controls after the immediate showControl call",
                onFullscreenBody.contains("boolean exit = isFullscreen();")
                        && onFullscreenBody.contains("showControl();")
                        && onFullscreenBody.contains("if (!exit) scheduleFullscreenControlReveal();")
                        && onFullscreenBody.indexOf("showControl();") < onFullscreenBody.indexOf("scheduleFullscreenControlReveal();"));
        assertTrue("fullscreen player layer must sit above native enhanced detail content",
                enterFullscreenBody.contains("mBinding.video.bringToFront();"));
        assertTrue("fullscreen control reveal must run once after layout and once after orientation settles",
                scheduleBody.contains("mBinding.video.post(this::showControlIfFullscreen);")
                        && scheduleBody.contains("mBinding.video.postDelayed(this::showControlIfFullscreen, 300);"));
        assertTrue("delayed fullscreen control reveal must not run after exit, lock, or PiP",
                guardBody.contains("if (!isFullscreen() || isLock() || isInPictureInPictureMode()) return;")
                        && guardBody.contains("showControl();"));
        assertTrue("orientation changes must refresh visible fullscreen controls after landscape state is applied",
                configBody.contains("if (isFullscreen()) {")
                        && configBody.contains("Util.hideSystemUI(this);")
                        && configBody.contains("if (isVisible(mBinding.control.getRoot())) showControl();"));
    }

    @Test
    public void mobileFullscreenTransitionsUseStablePlayerSnapshot() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int enter = source.indexOf("private void enterFullscreen()");
        int schedule = source.indexOf("private void scheduleFullscreenControlReveal()", enter);
        int exit = source.indexOf("private void exitFullscreen()", schedule);
        int restore = source.indexOf("private void restoreEmbeddedVideoLayoutAfterFullscreen()", exit);
        int transition = source.indexOf("private void setTransition()", restore);
        int policy = source.indexOf("private boolean shouldAnimateVideoFrameTransition", transition);

        assertTrue(sourcePath + " is missing fullscreen transition methods",
                enter >= 0 && schedule > enter && exit > schedule && restore > exit && transition > restore && policy > transition);

        String enterBody = source.substring(enter, schedule);
        String exitBody = source.substring(exit, restore);
        String transitionBody = source.substring(transition, policy);
        assertTrue("fullscreen entry must snapshot the player before lifecycle-sensitive work",
                enterBody.contains("PlayerManager current = player();")
                        && enterBody.contains("if (current == null) return;")
                        && enterBody.contains("current.isPortrait()"));
        assertFalse("fullscreen entry must not repeatedly dereference a disconnectable service player",
                enterBody.contains("player().isPortrait()"));
        assertTrue("fullscreen exit must tolerate playback service disconnection",
                exitBody.contains("PlayerManager current = player();")
                        && exitBody.contains("current != null && isLand() && !current.isPortrait()"));
        assertFalse("fullscreen exit must not repeatedly dereference a disconnectable service player",
                exitBody.contains("player().isPortrait()"));
        assertTrue("frame transitions must use the same stable player snapshot",
                transitionBody.contains("PlayerManager current = player();")
                        && transitionBody.contains("shouldAnimateVideoFrameTransition(current)"));
        assertFalse("frame transitions must not dereference player() after the snapshot",
                transitionBody.contains("player()."));
    }

    @Test
    public void mobileVideoTmdbMovableViewsKeepQualityBetweenFlagsAndEpisodes() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private View[] getTmdbMovableViews()");
        int flag = source.indexOf("mBinding.flag,", method);
        int qualityText = source.indexOf("mBinding.qualityText,", method);
        int quality = source.indexOf("mBinding.quality,", method);
        int episodeTitle = source.indexOf("mBinding.episodeTitleBar,", method);
        int episode = source.indexOf("mBinding.episode,", method);

        assertTrue(sourcePath + " is missing getTmdbMovableViews", method >= 0);
        assertTrue("TMDB movable views must include flag", flag > method);
        assertTrue("TMDB movable views must include quality title", qualityText > method);
        assertTrue("TMDB movable views must include quality list", quality > method);
        assertTrue("TMDB movable views must include episode title", episodeTitle > method);
        assertTrue("TMDB movable views must include episode list", episode > method);
        assertTrue("quality title must move after flag list", flag < qualityText);
        assertTrue("quality list must move after quality title", qualityText < quality);
        assertTrue("episode title must move after quality list", quality < episodeTitle);
        assertTrue("episode list must move after episode title", episodeTitle < episode);
    }

    @Test
    public void mobileOriginalEnhancedHidesOriginalDetailActionRow() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void setNativeDetailInfoVisible(boolean visible)");
        int nextMethod = source.indexOf("private void setText(TextView view", method);
        String methodBody = nextMethod > method ? source.substring(method, nextMethod) : source.substring(method);
        int visibilityMethod = source.indexOf("private void setOriginalEnhancedActionVisibility(boolean hide)");

        assertTrue(sourcePath + " is missing setNativeDetailInfoVisible", method >= 0);
        assertTrue("native detail info visibility must not control the detail action row",
                !methodBody.contains("mBinding.actionRow.setVisibility(visibility)"));
        assertTrue(sourcePath + " is missing setOriginalEnhancedActionVisibility", visibilityMethod >= 0);
        assertTrue("native enhanced mode must hide the original detail action row",
                source.indexOf("mBinding.actionRow.setVisibility(hide ? View.GONE : View.VISIBLE)", visibilityMethod) > visibilityMethod);
    }

    @Test
    public void mobileTmdbDetailHidesNativeActionRowInColorfulMode() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void setDetail(Vod item)");

        assertTrue(sourcePath + " is missing setDetail", method >= 0);
        assertTrue("TMDB detail playback must hide the native action row for colorful/native styled detail pages",
                source.indexOf("setOriginalEnhancedActionVisibility(tmdbMode);", method) > method);
    }

    @Test
    public void mobileReadyStateKeepsPlaybackInitializationAfterPendingResumeSeek() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("protected void onStateChanged(int state)");
        int ready = source.indexOf("case Player.STATE_READY:", method);
        int seek = source.indexOf("boolean pendingResumeSeekApplied = applyPendingResumeSeek();", ready);
        int reset = source.indexOf("player().reset();", ready);
        int shortDrama = source.indexOf("applyShortDramaMode();", ready);
        int introSkip = source.indexOf("requestIntroSkipPlan();", ready);
        int autoSkipGuard = source.indexOf("if (!pendingResumeSeekApplied) applyAutoIntroSkip();", ready);

        assertTrue(sourcePath + " is missing onStateChanged", method >= 0);
        assertTrue("ready playback must capture whether a pending resume seek was applied", seek > ready);
        assertTrue("pending resume seek must not skip playback reset", reset > seek);
        assertTrue("pending resume seek must not skip short-drama readiness", shortDrama > reset);
        assertTrue("pending resume seek must not skip intro-skip planning", introSkip > shortDrama);
        assertTrue("auto intro skip should wait for the deferred seek to settle", autoSkipGuard > introSkip);
    }

    @Test
    public void leanbackReadyStateKeepsPlaybackInitializationAfterPendingResumeSeek() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("protected void onStateChanged(int state)");
        int ready = source.indexOf("case Player.STATE_READY:", method);
        int seek = source.indexOf("boolean pendingResumeSeekApplied = applyPendingResumeSeek();", ready);
        int reset = source.indexOf("player().reset();", ready);
        int shortDrama = source.indexOf("applyShortDramaMode();", ready);
        int introSkip = source.indexOf("requestIntroSkipPlan();", ready);
        int autoSkipGuard = source.indexOf("if (!pendingResumeSeekApplied) applyAutoIntroSkip();", ready);

        assertTrue(sourcePath + " is missing onStateChanged", method >= 0);
        assertTrue("TV ready playback must capture whether a pending resume seek was applied", seek > ready);
        assertTrue("TV pending resume seek must not skip playback reset", reset > seek);
        assertTrue("TV pending resume seek must not skip short-drama readiness", shortDrama > reset);
        assertTrue("TV pending resume seek must not skip intro-skip planning", introSkip > shortDrama);
        assertTrue("TV auto intro skip should wait for the deferred seek to settle", autoSkipGuard > introSkip);
    }

    @Test
    public void mobileTmdbImageReadyRebindsDeferredSummaryAndRevealsDetailContent() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int listener = source.indexOf("mTmdbHeaderView.setOnImagesLoadedListener");
        int callback = source.indexOf("onTmdbContentReady();", listener);
        int helper = source.indexOf("private void onTmdbContentReady()");
        int loaded = source.indexOf("mTmdbContentLoaded = true;", helper);
        int summary = source.indexOf("if (mVod != null) setText(mVod);", helper);
        int reveal = source.indexOf("showDetailContent();", helper);

        assertTrue("TMDB image completion must funnel through a dedicated content-ready callback", callback > listener);
        assertTrue(sourcePath + " is missing onTmdbContentReady", helper >= 0);
        assertTrue("TMDB content-ready callback must mark the TMDB page as loaded", loaded > helper);
        assertTrue("TMDB content-ready callback must rebind the deferred native summary text", summary > loaded);
        assertTrue("TMDB content-ready callback must reveal detail content independently from video readiness", reveal > summary);
    }

    @Test
    public void mobilePlaybackReadyAlwaysClearsVideoLoadingOverlay() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int playback = source.indexOf("private void showPlaybackContent()");
        int hide = source.indexOf("hideProgress();", playback);
        int detailCall = source.indexOf("showDetailContent();", hide);
        int detail = source.indexOf("private void showDetailContent()");
        int revealGuard = source.indexOf("if (!canRevealPlaybackContent()) return;", detail);
        int showContent = source.indexOf("mBinding.progressLayout.showContent();", detail);

        assertTrue(sourcePath + " is missing showPlaybackContent", playback >= 0);
        assertTrue("playback readiness must clear only the video loading overlay first", hide > playback && detailCall > hide);
        assertTrue("detail content reveal must remain independently gated by detail loading state", revealGuard > detail && showContent > revealGuard);
        assertTrue("detail content reveal must remain gated after the video overlay is cleared", showContent > revealGuard);
    }

    @Test
    public void mobileTmdbContentReadyDoesNotClearVideoLoadingBeforePlaybackReady() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void onTmdbContentReady()");
        int end = source.indexOf("private void showError(String text)", method);
        String body = method >= 0 && end > method ? source.substring(method, end) : "";

        assertTrue(sourcePath + " is missing onTmdbContentReady", method >= 0);
        assertTrue("TMDB completion must reveal detail content immediately", body.contains("showDetailContent();"));
        assertFalse("TMDB detail completion must not clear the independent video loading overlay", body.contains("hideProgress();"));
        assertFalse("TMDB detail completion must not wait for player READY", body.contains("Player.STATE_READY"));
        assertFalse("TMDB detail completion must not go through playback content helper", body.contains("showPlaybackContent();"));
    }

    @Test
    public void mobileTmdbPlaybackLoadingDoesNotRestartThePageSpinnerAfterSourceDetailLoads() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void showProgress()");
        int end = source.indexOf("private void hideProgress()", method);
        String body = method >= 0 && end > method ? source.substring(method, end) : "";
        int showOverlay = body.indexOf("mBinding.progress.getRoot().setVisibility(View.VISIBLE);");
        int initialDetailGuard = body.indexOf("if (mVod == null && shouldLoadTmdbDetail() && !mTmdbContentLoaded && !shouldRevealShellWhileLoading()) mBinding.progressLayout.showProgress();");
        int preserveDetail = body.indexOf("else if (mVod != null && !mBinding.progressLayout.isContent()) mBinding.progressLayout.showContent();");

        assertTrue(sourcePath + " is missing showProgress", method >= 0);
        assertTrue("video loading overlay must still be shown first", showOverlay >= 0);
        assertTrue("only the pre-detail phase may use the full-page loading state", initialDetailGuard > showOverlay);
        assertTrue("player buffering must preserve source detail content while TMDB enrichment continues", preserveDetail > initialDetailGuard);
    }

    @Test
    public void mobilePlaybackStartupDoesNotShowThePlayerSpinnerBeforeAPlayerRequestExists() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int init = source.indexOf("protected void initView(Bundle savedInstanceState)");
        int end = source.indexOf("private void setupIntroSkipConfirmListener()", init);
        String body = init >= 0 && end > init ? source.substring(init, end) : "";

        assertTrue(sourcePath + " is missing initView", init >= 0);
        assertTrue("startup without preview should still show the detail loading state", body.contains("else mBinding.progressLayout.showProgress();"));
        assertFalse("startup must not show the independent player spinner before getPlayer()",
                body.contains("\n        showProgress();"));
    }

    @Test
    public void leanbackHistoryPlaybackBindsAndSelectsTheCurrentEpisodeSegment() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int selector = source.indexOf("private int getSelectedEpisodePosition(List<Episode> episodes)");
        int selectorEnd = source.indexOf("private String getDanmakuEpisodeName()", selector);
        String selectorBody = selector >= 0 && selectorEnd > selector ? source.substring(selector, selectorEnd) : "";
        assertTrue("history playback must resolve the current episode before adapter selection is initialized", selectorBody.contains("Episode historyEpisode = mHistory.getEpisode();")
                && selectorBody.contains("episodes.get(i).matchesPlayback(historyEpisode)"));

        int adapter = source.indexOf("private void setEpisodeAdapter(List<Episode> items, boolean scrollToCurrent)");
        int end = source.indexOf("private void setArrayAdapter(int size)", adapter);
        String body = adapter >= 0 && end > adapter ? source.substring(adapter, end) : "";

        assertTrue(sourcePath + " is missing setEpisodeAdapter", adapter >= 0);
        assertTrue("history playback must calculate the selected episode from the full episode list", body.contains("int selectedPosition = getSelectedEpisodePosition(items);"));
        assertTrue("history playback must bind the segment containing the selected episode", body.contains("int segmentStart = EpisodeRangePolicy.segmentStart(size, selectedPosition, segmentSize);")
                && body.contains("items.subList(segmentStart, Math.min(segmentStart + segmentSize, size))"));
        assertTrue("history playback must select and scroll the matching segment instead of the first segment", body.contains("selectEpisodeSegmentPosition(EpisodeRangePolicy.segmentIndex(size, selectedPosition, segmentSize) + 2);"));

        int nativeAdapter = source.indexOf("private void setUpstreamNativeEpisodeItems(List<Episode> items, boolean scrollToCurrent)");
        int nativeEnd = source.indexOf("private void finishEpisodeLoading()", nativeAdapter);
        String nativeBody = nativeAdapter >= 0 && nativeEnd > nativeAdapter ? source.substring(nativeAdapter, nativeEnd) : "";
        assertTrue("the native episode module must also preserve the current segment", nativeBody.contains("int selectedPosition = getSelectedEpisodePosition(items);")
                && nativeBody.contains("EpisodeRangePolicy.segmentStart(size, selectedPosition, segmentSize)")
                && nativeBody.contains("selectEpisodeSegmentPosition(EpisodeRangePolicy.segmentIndex(size, selectedPosition, segmentSize) + 2);"));

        int segment = source.indexOf("private void showEpisodeSegment(int position)");
        int segmentEnd = source.indexOf("private boolean shouldEnterFullscreen(Episode item)", segment);
        String segmentBody = segment >= 0 && segmentEnd > segment ? source.substring(segment, segmentEnd) : "";
        assertTrue("focusing the current segment must preserve the current episode focus after adapter layout", segmentBody.contains("int selectedPosition = getSelectedEpisodePosition(episodes);")
                && segmentBody.contains("int positionInSegment = selectedPosition >= start && selectedPosition < end ? selectedPosition - start : 0;")
                && segmentBody.contains("scrollToEpisode(positionInSegment);"));

        int events = source.indexOf("private void setRecyclerView()");
        int eventsEnd = source.indexOf("private void setupTmdbGridViews()", events);
        String eventBody = events >= 0 && eventsEnd > events ? source.substring(events, eventsEnd) : "";
        int arrayKey = source.indexOf("private boolean onArrayKey(KeyEvent event)");
        int arrayKeyEnd = source.indexOf("private boolean onEpisodeKey(KeyEvent event)", arrayKey);
        String arrayKeyBody = arrayKey >= 0 && arrayKeyEnd > arrayKey ? source.substring(arrayKey, arrayKeyEnd) : "";
        assertTrue("segment DPAD down must explicitly focus the current episode", eventBody.contains("mArrayAdapter.setOnKeyListener((view, keyCode, event) -> onArrayKey(event));")
                && eventBody.contains("mBinding.array.setOnKeyListener((view, keyCode, event) -> onArrayKey(event));")
                && arrayKeyBody.contains("!KeyUtil.isActionDown(event) || !KeyUtil.isDownKey(event)")
                && arrayKeyBody.contains("selectEpisodeSegment(position, true);"));
        assertTrue("segment focus handoff must preserve history fallback when no episode is marked selected", source.contains("if (requestEpisodeFocus) scrollToEpisode(getSelectedEpisodePosition(mEpisodeAdapter.getItems()), true);"));

        Path arrayAdapterPath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "ArrayAdapter.java"));
        String arrayAdapter = new String(Files.readAllBytes(arrayAdapterPath), StandardCharsets.UTF_8);
        Path segmentSelectorPath = findLeanbackResPath().resolve(Path.of("drawable", "selector_video_item.xml"));
        String segmentSelector = new String(Files.readAllBytes(segmentSelectorPath), StandardCharsets.UTF_8);
        assertTrue("original detail modes must keep the active episode range highlighted after focus moves to an episode", source.contains("mArrayAdapter.setSelectedPosition(position);")
                && arrayAdapter.contains("setActivated(position == selectedPosition)")
                && segmentSelector.contains("android:state_activated=\"true\"")
                && segmentSelector.contains("#2CC56F"));
    }

    @Test
    public void mobilePlaybackPagesLargeEpisodeListsBeforeTmdbEpisodeMetadataArrives() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int adapter = source.indexOf("private void setEpisodeAdapter(List<Episode> items)");
        int end = source.indexOf("private boolean shouldUseUpstreamNativeEpisodeModule()", adapter);
        String body = adapter >= 0 && end > adapter ? source.substring(adapter, end) : "";

        assertTrue(sourcePath + " is missing setEpisodeAdapter", adapter >= 0);
        assertTrue("TMDB detail layout must cap the current page even before card metadata exists",
                body.contains("int maxGroupSize = shouldUseTmdbDetailLayout() ? EpisodeRangePolicy.CARD_PAGE_MAX_SIZE : 0;"));
        assertTrue("the already-computed card mode must be forwarded instead of scanning every episode twice",
                body.contains("setEpisodeItems(items, useTmdbCard);"));
        assertTrue("episode item binding must accept the precomputed card mode",
                source.contains("private void setEpisodeItems(List<Episode> items, boolean useTmdbCard)"));
    }

    @Test
    public void mobileInitialFlagSelectionBuildsTheEpisodePageOnlyOnce() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int click = source.indexOf("public void onItemClick(Flag item)");
        int end = source.indexOf("public void onItemClick(Episode item)", click);
        String body = click >= 0 && end > click ? source.substring(click, end) : "";

        assertTrue(sourcePath + " is missing flag click handler", click >= 0);
        assertTrue("history episode selection/playback should happen before the fallback page bind",
                body.contains("boolean episodeChanged = seamless(resolved);")
                        && body.contains("if (!episodeChanged) setEpisodeAdapter(resolved.getEpisodes());"));
        assertTrue("seamless selection must report whether it already rebuilt the page",
                source.contains("private boolean seamless(Flag flag)"));
    }

    @Test
    public void mobileEpisodeSwitchDoesNotResetLoadedTmdbDetailContent() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void getPlayer(Flag flag, Episode episode)");
        int end = source.indexOf("private void setPlayer(Result result)", method);
        String body = method >= 0 && end > method ? source.substring(method, end) : "";

        assertTrue(sourcePath + " is missing getPlayer", method >= 0);
        assertFalse("episode switches start playback but must not force the already-loaded TMDB detail area back to loading", body.contains("mTmdbContentLoaded = false"));
        assertTrue("episode switches should still show the video loading overlay", body.contains("showProgress();"));
    }

    @Test
    public void mobileDirectPlaybackUsesUpstreamNativeEpisodeModule() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int predicate = source.indexOf("private boolean shouldUseUpstreamNativeEpisodeModule()");
        int bind = source.indexOf("private void setUpstreamNativeEpisodeItems(List<Episode> items)");
        int viewport = source.indexOf("private void updateEpisodeViewportHeight()");
        int nextViewportMethod = source.indexOf("private boolean isTmdbEpisodeCardMode()", viewport);
        int setEpisode = source.indexOf("private void setEpisodeAdapter(List<Episode> items)");
        String viewportBody = nextViewportMethod > viewport ? source.substring(viewport, nextViewportMethod) : "";

        assertTrue(sourcePath + " is missing direct native episode predicate", predicate >= 0);
        assertTrue("direct native episode mode must be scoped to the 影视原生 setting",
                source.indexOf("return Setting.isDirectDetailPage() && !isTmdbMode();", predicate) > predicate);
        assertTrue("direct native playback must bypass enhanced episode binding",
                setEpisode >= 0 && source.indexOf("if (shouldUseUpstreamNativeEpisodeModule())", setEpisode) > setEpisode);
        assertTrue("direct native playback should keep upstream grouping while honoring the saved list/grid mode",
                bind >= 0
                        && source.indexOf("mEpisodeGridMode = Setting.getTmdbEpisodeGridMode();", bind) > bind
                        && source.indexOf("mBinding.more.setVisibility(View.GONE);", bind) > bind
                        && source.indexOf("EpisodeGroupAdapter.build(size, getSelectedEpisodePosition(items), mHistory != null && mHistory.isRevSort())", bind) > bind
                        && source.indexOf("mBinding.episodeGroup.setVisibility(groups.size() > 1 ? View.VISIBLE : View.GONE);", bind) > bind
                        && source.indexOf("setEpisodeItems(items, false);", bind) > bind);
        assertTrue("direct native episode module should use the standard viewport cap",
                viewport >= 0 && !viewportBody.contains("shouldUseUpstreamNativeEpisodeModule()"));
    }

    @Test
    public void mobileDirectPlaybackExposesUnifiedEpisodeToolbar() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int bind = source.indexOf("private void setUpstreamNativeEpisodeItems(List<Episode> items)");
        int setItems = source.indexOf("private void setEpisodeItems(List<Episode> items, boolean useTmdbCard)");
        int layout = source.indexOf("private void updateEpisodeLayout(List<Episode> items, boolean useTmdbCard)");
        int reverse = source.indexOf("private void updateEpisodeReverseButton()", setItems);

        assertTrue(sourcePath + " is missing native episode binding", bind >= 0);
        assertTrue("native mobile playback should preserve the configured list/grid default",
                source.indexOf("mEpisodeGridMode = Setting.getTmdbEpisodeGridMode();", bind) > bind);
        assertTrue("native mobile playback should expose the filename icon when multiple episodes exist",
                source.indexOf("mBinding.episodeFileName.setVisibility(size > 1 ? View.VISIBLE : View.GONE);", bind) > bind);
        assertTrue("native mobile playback should expose the list/grid icon when multiple episodes exist",
                source.indexOf("mBinding.episodeViewMode.setVisibility(size > 1 ? View.VISIBLE : View.GONE);", bind) > bind);
        assertTrue("native mobile playback should keep reverse accessibility state synchronized",
                reverse > setItems && source.indexOf("mBinding.reverse.setContentDescription", reverse) > reverse);
        assertTrue("native mobile list mode should use the horizontal episode holder instead of forcing grid mode",
                setItems >= 0
                        && source.indexOf("mEpisodeAdapter.setViewType(!mEpisodeGridMode ? ViewType.HORI : ViewType.GRID);", setItems) > setItems
                        && layout >= 0
                        && source.indexOf("if (!mEpisodeGridMode) {", layout) > layout);
    }

    @Test
    public void mobileNativeEnhancedEpisodeToolbarDoesNotWaitForTmdbCards() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int bind = source.indexOf("private void setEpisodeAdapter(List<Episode> items)");
        int setItems = source.indexOf("private void setEpisodeItems(List<Episode> items, boolean useTmdbCard)");
        int layout = source.indexOf("private void updateEpisodeLayout(List<Episode> items, boolean useTmdbCard)");
        int refresh = source.indexOf("private void refreshTmdbEpisodeTitles()");

        assertTrue("mobile native-enhanced playback must expose filename and view tools for every multi-episode source",
                bind >= 0 && source.indexOf("boolean showViewMode = size > 1;", bind) > bind);
        assertTrue("late TMDB metadata refresh must keep the same multi-episode toolbar rule",
                refresh >= 0 && source.indexOf("boolean showViewMode = size > 1;", refresh) > refresh);
        assertTrue("plain native episode cards must not be forced back to grid mode",
                setItems >= 0
                        && source.indexOf("if (items.size() < 2) mEpisodeGridMode = true;", setItems) > setItems
                        && !source.substring(setItems, layout).contains("!useTmdbCard && !shouldUseUpstreamNativeEpisodeModule()"));
        assertTrue("all mobile episode modes must honor horizontal list mode",
                setItems >= 0
                        && source.indexOf("mEpisodeAdapter.setViewType(!mEpisodeGridMode ? ViewType.HORI : ViewType.GRID);", setItems) > setItems
                        && layout >= 0
                        && source.indexOf("if (!mEpisodeGridMode) {", layout) > layout);
    }

    @Test
    public void leanbackDirectPlaybackUsesUpstreamNativeEpisodeModule() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int predicate = source.indexOf("private boolean shouldUseUpstreamNativeEpisodeModule()");
        int setEpisode = source.indexOf("private void setEpisodeAdapter(List<Episode> items, boolean scrollToCurrent)");
        int bind = source.indexOf("private void setUpstreamNativeEpisodeItems(List<Episode> items, boolean scrollToCurrent)");
        int viewport = source.indexOf("private void updateUpstreamNativeEpisodeGridViewport()");

        assertTrue(sourcePath + " is missing leanback direct native episode predicate", predicate >= 0);
        assertTrue("leanback direct native episode mode must be scoped to the 影视原生 setting",
                source.indexOf("return Setting.isDirectDetailPage() && !isTmdbMode();", predicate) > predicate);
        assertTrue("leanback direct native playback must bypass enhanced episode chrome",
                setEpisode >= 0 && source.indexOf("if (shouldUseUpstreamNativeEpisodeModule())", setEpisode) > setEpisode);
        assertTrue("leanback direct native playback should keep upstream grouping and vertical episode grid",
                bind >= 0
                        && source.indexOf("mBinding.episodeHeader.setVisibility(View.GONE);", bind) > bind
                        && source.indexOf("episodeGridMode = true;", bind) > bind
                        && source.indexOf("mBinding.episode.setVisibility(View.GONE);", bind) > bind
                        && source.indexOf("mBinding.episodeGrid.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);", bind) > bind
                        && source.indexOf("mEpisodeGridAdapter.setVerticalGridMode(true);", bind) > bind
                        && source.indexOf("setArrayAdapter(items.size());", bind) > bind);
        assertTrue("leanback direct native grouping must scroll the episode grid internally so the focused group stays visible",
                viewport >= 0
                        && source.indexOf("params.height = height;", viewport) > viewport
                        && source.indexOf("getUpstreamNativeEpisodeGridHeight(spacing)", viewport) > viewport
                        && source.indexOf("ResUtil.dp2px(64) * rows", viewport) > viewport
                        && source.indexOf("new SpaceItemDecoration(spanCount, 12)", viewport) > viewport
                        && source.indexOf("mBinding.episodeGrid.setNestedScrollingEnabled(true);", viewport) > viewport
                        && source.indexOf("updateUpstreamNativeEpisodeGridViewport();", bind) > bind
                        && source.indexOf("mBinding.episodeGrid.post(this::updateUpstreamNativeEpisodeGridViewport);", bind) > bind);
    }

    @Test
    public void leanbackNativeEpisodeGridRefreshesDecorationForCurrentSpanCount() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int viewport = source.indexOf("private void updateUpstreamNativeEpisodeGridViewport()");
        int nextMethod = source.indexOf("private int getUpstreamNativeEpisodeGridHeight", viewport);
        String body = viewport >= 0 && nextMethod > viewport ? source.substring(viewport, nextMethod) : "";
        int remove = body.indexOf("clearEpisodeGridDecoration();");
        int add = body.indexOf("mBinding.episodeGrid.addItemDecoration(episodeGridDecoration = new SpaceItemDecoration(spanCount, 12));");

        assertTrue(sourcePath + " is missing the native episode grid viewport updater", viewport >= 0);
        assertTrue("native episode spacing must replace the old decoration when the adaptive span count changes", remove >= 0 && add > remove);
        assertFalse("a one-shot decoration guard leaves offsets calculated with a stale span count", source.contains("episodeGridSpacingAdded"));
    }

    @Test
    public void leanbackLeavingNativeEpisodeGridClearsNativeDecoration() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int clear = source.indexOf("private void clearEpisodeGridDecoration()");
        int normalViewport = source.indexOf("private void updateEpisodeGridViewport()");
        int nativeViewport = source.indexOf("private void updateUpstreamNativeEpisodeGridViewport()", normalViewport);
        int clearAdapters = source.indexOf("private void clearDetailAdapters()");
        int nextClearMethod = source.indexOf("\n    private ", clear + 1);
        int nextNormalMethod = source.indexOf("\n    private ", normalViewport + 1);
        int nextAdaptersMethod = source.indexOf("\n    private ", clearAdapters + 1);
        String clearBody = clear >= 0 && nextClearMethod > clear ? source.substring(clear, nextClearMethod) : "";
        String normalBody = normalViewport >= 0 && nextNormalMethod > normalViewport ? source.substring(normalViewport, nextNormalMethod) : "";
        String adaptersBody = clearAdapters >= 0 && nextAdaptersMethod > clearAdapters ? source.substring(clearAdapters, nextAdaptersMethod) : "";

        assertTrue(sourcePath + " must provide one decoration cleanup path", clear >= 0);
        assertTrue("decoration cleanup must remove and forget the native grid decoration",
                clearBody.contains("mBinding.episodeGrid.removeItemDecoration(episodeGridDecoration);")
                        && clearBody.contains("episodeGridDecoration = null;"));
        assertTrue("normal/TMDB episode viewport must drop native spacing before laying out cards",
                normalBody.contains("clearEpisodeGridDecoration();"));
        assertTrue("reusing VideoActivity for another detail must not retain native spacing",
                adaptersBody.contains("clearEpisodeGridDecoration();"));
        assertTrue("normal viewport must be parsed before the native viewport", nativeViewport > normalViewport);
    }

    @Test
    public void leanbackScrapedEpisodesRenderBeforeTmdbMetadataFinishes() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int setEpisode = source.indexOf("private void setEpisodeAdapter(List<Episode> items, boolean scrollToCurrent)");
        int nextMethod = source.indexOf("private void setUpstreamNativeEpisodeItems", setEpisode);
        String body = setEpisode >= 0 && nextMethod > setEpisode ? source.substring(setEpisode, nextMethod) : "";
        int metadata = body.indexOf("boolean tmdbEpisodeMetadataLoaded = mTmdbUIAdapter != null && mTmdbUIAdapter.isEpisodeMetadataLoaded();");
        int pending = body.indexOf("boolean tmdbEpisodeEnrichmentPending = !mTmdbEpisodeFallbackReleased", metadata);
        int pendingCondition = body.indexOf("&& (mTmdbDetailLoading || (tmdbAdapterReady && !tmdbEpisodeMetadataLoaded));", pending);
        int wait = body.indexOf("EpisodeDisplayPolicy.shouldWaitForTmdbEpisodes(tmdbMode, tmdbEpisodeEnrichmentPending, tmdbAdapterReady, tmdbEpisodeMetadataLoaded, items);", pendingCondition);
        // 网格模式必须跟随「真有卡片数据」，否则纯文本项会被塞进按卡片宽度算 spanCount 的
        // 网格，而 episodeViewMode 按钮此时因 useTmdbCards=false 隐藏，用户切不回列表。
        int gridFollowsCards = body.indexOf("if (useTmdbCards && hasMultiple) episodeGridMode = Setting.getTmdbEpisodeGridMode();", wait);
        int gridResetFollowsCards = body.indexOf("if (!useTmdbCards || !hasMultiple) episodeGridMode = false;", gridFollowsCards);
        int finishLoading = source.indexOf("private void finishEpisodeLoading()");
        int refreshTitles = source.indexOf("private void refreshEpisodeTitles()", finishLoading);
        String finishBody = finishLoading >= 0 && refreshTitles > finishLoading ? source.substring(finishLoading, refreshTitles) : "";
        int metadataGuard = finishBody.indexOf("!mTmdbUIAdapter.isEpisodeMetadataLoaded()");

        assertTrue(sourcePath + " is missing setEpisodeAdapter", setEpisode >= 0);
        assertTrue("native-enhanced playback must track episode metadata separately from core TMDB detail", metadata >= 0);
        assertTrue("the episode area must still know enrichment is pending after core detail loads", pending > metadata && pendingCondition > pending && wait > pendingCondition);
        assertTrue("episode grid mode must follow real TMDB card data, not chrome visibility", gridFollowsCards > wait && gridResetFollowsCards > gridFollowsCards);
        // 隐藏选集只允许发生在 waitTmdbEpisodes 分支里，而该分支的条件由 EpisodeDisplayPolicy
        // 收窄为「一集都没有」。用真实的策略调用来断言，而不是匹配源码缩进（文件是 CRLF，
        // 带 \n 的字面量匹配会恒假，成为空断言）。
        assertFalse("scraped episodes must render instead of being hidden while enrichment is pending",
                EpisodeDisplayPolicy.shouldWaitForTmdbEpisodes(true, true, true, false, List.of(Episode.create("第1集", "u1"))));
        assertTrue("with no episode at all the placeholder is still the only thing to show",
                EpisodeDisplayPolicy.shouldWaitForTmdbEpisodes(true, true, true, false, List.of()));
        assertEquals("hiding the episode content must remain confined to the wait branch", 1, countOccurrences(body, "setEpisodeContentVisible(false);"));
        assertTrue("core TMDB completion must not trigger the plain-list fallback while episode metadata is still loading", metadataGuard >= 0);
    }

    private static int countOccurrences(String text, String token) {
        int count = 0;
        for (int at = text.indexOf(token); at >= 0; at = text.indexOf(token, at + token.length())) count++;
        return count;
    }

    @Test
    public void leanbackTmdbCardUpgradeHasSingleRefreshPath() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int updateFlag = source.indexOf("private void updateFlag(Flag activated, List<Flag> items)");
        int nextMethod = source.indexOf("\n    private final PlaybackService.NavigationCallback", updateFlag);
        String body = updateFlag >= 0 && nextMethod > updateFlag ? source.substring(updateFlag, nextMethod) : "";

        assertTrue(sourcePath + " is missing updateFlag", updateFlag >= 0);
        assertTrue("the activated line must rebind episodes so TMDB cards replace the plain text rows",
                body.contains("if (target.equals(activated)) setEpisodeAdapter(target.getEpisodes());"));
        // 占位符只在「一集都没有」时可见，与「已有卡片数据」互斥，那条淡入分支已无法命中。
        assertFalse("the unreachable placeholder fade-in branch must not come back",
                body.contains("mBinding.episodeLoadingIndicator.getVisibility() == View.VISIBLE"));
    }

    @Test
    public void leanbackTmdbEpisodeLoadingTimesOutToNativeFallback() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int timeoutField = source.indexOf("private Runnable mTmdbEpisodeTimeout;");
        int fallbackField = source.indexOf("private boolean mTmdbEpisodeFallbackReleased;");
        int init = source.indexOf("mTmdbEpisodeTimeout = this::showTmdbEpisodeFallback;");
        int setDetail = source.indexOf("private void setDetail(Vod item)");
        int reset = source.indexOf("mTmdbEpisodeFallbackReleased = false;", setDetail);
        int schedule = source.indexOf("App.post(mTmdbEpisodeTimeout, TMDB_DETAIL_LOAD_TIMEOUT);", reset);
        int setEpisode = source.indexOf("private void setEpisodeAdapter(List<Episode> items, boolean scrollToCurrent)");
        int terminalCancel = source.indexOf("if (tmdbEpisodeMetadataLoaded) App.removeCallbacks(mTmdbEpisodeTimeout);", setEpisode);
        int fallbackAwarePending = source.indexOf("boolean tmdbEpisodeEnrichmentPending = !mTmdbEpisodeFallbackReleased", setEpisode);
        int fallback = source.indexOf("private void showTmdbEpisodeFallback()");
        int metadataShortCircuit = source.indexOf("if (mTmdbUIAdapter.isEpisodeMetadataLoaded())", fallback);
        int release = source.indexOf("mTmdbEpisodeFallbackReleased = true;", metadataShortCircuit);
        int indicatorBranch = source.indexOf("if (mBinding.episodeLoadingIndicator.getVisibility() == View.VISIBLE) finishEpisodeLoading();", release);
        int finishMethod = source.indexOf("private void finishEpisodeLoading()");
        int allowTimedFallback = source.indexOf("&& !mTmdbEpisodeFallbackReleased", finishMethod);
        int destroy = source.indexOf("protected void onDestroy()");
        int destroyCancel = source.indexOf("App.removeCallbacks(mTmdbEpisodeTimeout);", destroy);

        assertTrue(sourcePath + " must own an independent TMDB episode timeout", timeoutField >= 0 && fallbackField > timeoutField && init > fallbackField);
        assertTrue("each enhanced detail load must reset and schedule the episode fallback", reset > setDetail && schedule > reset);
        assertTrue("completed episode metadata must cancel the fallback timer", terminalCancel > setEpisode);
        assertTrue("once timeout fallback is released, later core-detail refreshes must not hide the native list again", fallbackAwarePending > terminalCancel);
        // 选集现在先上屏，占位符只在「一集都没有」时出现，所以超时不能再以占位符可见为前提：
        // 必须无条件置位 fallbackReleased 让表头收起，再按占位符是否可见决定走哪条揭开路径。
        assertTrue("the timeout must release the pending flag regardless of placeholder visibility, then reveal the native episode list",
                fallback >= 0 && metadataShortCircuit > fallback && release > metadataShortCircuit && indicatorBranch > release);
        assertTrue("finishEpisodeLoading must allow the explicit timeout fallback through its metadata guard", allowTimedFallback > finishMethod);
        assertTrue("episode timeout callback must be removed when the activity is destroyed", destroyCancel > destroy);
    }
    @Test
    public void vodEventPageSuffixStripPreservesLeadingSlashIds() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "helper", "VodEventGuard.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("static String stripPageSuffix(String id)");
        int nextMethod = source.indexOf("private VodEventGuard()", method);
        String body = method >= 0 && nextMethod > method ? source.substring(method, nextMethod) : "";

        assertTrue(sourcePath + " is missing stripPageSuffix", method >= 0);
        assertTrue("VOD event ids may start with /index.php and must not be stripped to empty",
                body.contains("slash > 0 ? id.substring(0, slash) : id"));
        assertFalse("only real page suffixes after the first character should be stripped",
                body.contains("slash >= 0 ? id.substring(0, slash) : id"));
    }

    @Test
    public void playbackControllerUsesBoundSessionTokenAndSurvivesControllerSetupFailure() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "PlaybackActivity.java"));
        Path servicePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "service", "PlaybackService.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        String service = new String(Files.readAllBytes(servicePath), StandardCharsets.UTF_8);
        String bind = methodBody(activity, "private void bindPlaybackService()", "private void bindPlaybackServiceAfterFirstFrame()");
        String build = methodBody(activity, "private void buildControllerAsync(SessionToken token)", "private void handleControllerConnectionFailure(Exception e)");
        String failure = methodBody(activity, "private void handleControllerConnectionFailure(Exception e)", "protected void onControllerConnected()");
        String handle = methodBody(activity, "private void handleControllerConnected()", "private boolean shouldRejectPlaybackConnection()");
        String connected = methodBody(activity, "public void onServiceConnected(ComponentName name, IBinder binder)", "public void onServiceDisconnected(ComponentName name)");
        String disconnected = methodBody(activity, "public void onServiceDisconnected(ComponentName name)", "protected void onResume()");
        String token = methodBody(service, "public SessionToken getSessionToken()", "public PlayerManager player()");
        int controllerBuild = connected.indexOf("buildControllerAsync(mService.getSessionToken());");
        int rejectAfterBuild = connected.indexOf("if (shouldRejectPlaybackConnection()) return;", controllerBuild);

        assertFalse("binding must not resolve a component SessionToken before the local service is connected",
                bind.contains("buildControllerAsync()"));
        assertTrue("the connected service must supply the already-created MediaLibrarySession token",
                controllerBuild >= 0);
        assertTrue("controller setup must accept the bound service token",
                activity.contains("private void buildControllerAsync(SessionToken token)"));
        assertFalse("controller setup must not query PackageManager through a ComponentName token",
                build.contains("new SessionToken(") || build.contains("new ComponentName("));
        assertTrue("missing session tokens must fail gracefully instead of leaving a partial controller state",
                build.contains("if (token == null)")
                        && build.contains("handleControllerConnectionFailure(new IllegalStateException(\"Playback session token is unavailable\"));"));
        assertTrue("controller setup must reject duplicate or late connections",
                build.contains("if (mControllerFuture != null || shouldRejectPlaybackConnection()) return;"));
        assertTrue("synchronous and asynchronous controller failures must share the same cleanup path",
                build.contains("catch (RuntimeException e)")
                        && build.contains("handleControllerConnectionFailure(e);")
                        && handle.contains("catch (Exception e)")
                        && handle.contains("handleControllerConnectionFailure(e);")
                        && !handle.contains("catch (Exception ignored)"));
        assertTrue("controller failure must release partial state and exit playback without crashing the app",
                failure.contains("SpiderDebug.log(\"playback-flow\", e);")
                        && failure.indexOf("releaseController();") >= 0
                        && failure.indexOf("releaseController();") < failure.indexOf("finishPlayback();"));
        assertTrue("service setup must stop after a synchronous controller failure starts activity shutdown",
                controllerBuild >= 0 && rejectAfterBuild > controllerBuild);
        assertTrue("the playback service must expose its live session token without another manifest lookup",
                token.contains("return session == null ? null : session.getToken();"));
        assertTrue("a disconnected service must release the old session controller before reconnecting",
                disconnected.indexOf("releaseController();") >= 0
                        && disconnected.indexOf("releaseController();") < disconnected.indexOf("mService = null;"));
    }

    @Test
    public void playbackServiceRegistersDirectSessionForMediaNotificationLifecycle() throws Exception {
        Path servicePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "service", "PlaybackService.java"));
        String service = new String(Files.readAllBytes(servicePath), StandardCharsets.UTF_8);
        String onCreate = methodBody(service, "public void onCreate()", "private PendingIntent buildDefaultIntent()");
        int setupNotification = onCreate.indexOf("setupNotification();");
        int addSession = onCreate.indexOf("addSession(session);");

        assertTrue("the media notification provider must be configured before session registration", setupNotification >= 0);
        assertTrue("direct SessionToken controllers bypass MediaSessionService binding, so the session must be registered explicitly",
                addSession > setupNotification);
    }

    @Test
    public void playbackControllerConnectionDoesNotReplayStaleState() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "PlaybackActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int handle = source.indexOf("private void handleControllerConnected()");
        int addListener = source.indexOf("mController.addListener(this);", handle);
        int reconcile = source.indexOf("reconcileControllerReadyState();", addListener);
        int seekListener = source.indexOf("getSeekView().setSeekListener(this::onSeekStarted);", handle);
        int controllerHook = source.indexOf("onControllerConnected();", addListener);
        int serviceConnected = source.indexOf("public void onServiceConnected");
        int serviceHook = source.indexOf("onServiceConnected();", serviceConnected);

        assertTrue(sourcePath + " is missing handleControllerConnected", handle >= 0);
        assertTrue("controller seek events must be bridged to playback activities", seekListener > handle && seekListener < addListener);
        assertTrue("controller listener must be registered before the controller hook", addListener > handle && addListener < controllerHook);
        assertTrue("current-item READY must be reconciled after listener registration", reconcile > addListener && reconcile < controllerHook);
        assertTrue("controller-specific hook must still run", controllerHook > addListener);
        assertTrue("service-specific hook must still run", serviceHook > serviceConnected);
        assertFalse("controller connection must not replay stale READY/playing state and hide loading early",
                source.contains("syncControllerPlaybackState()"));
        assertFalse("READY reconciliation must not replay the full state callback with side effects",
                source.contains("onStateChanged(mController.getPlaybackState())"));
    }

    @Test
    public void playbackExitRejectsLateConnectionsAndClearsOnlyItsOwnNavigationCallback() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "PlaybackActivity.java"));
        Path servicePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "service", "PlaybackService.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        String service = new String(Files.readAllBytes(servicePath), StandardCharsets.UTF_8);
        String bind = methodBody(activity, "private void bindPlaybackService()", "private void bindPlaybackServiceAfterFirstFrame()");
        String controller = methodBody(activity, "private void handleControllerConnected()", "private PendingIntent buildSessionIntent()");
        String connected = methodBody(activity, "public void onServiceConnected(ComponentName name, IBinder binder)", "public void onServiceDisconnected(ComponentName name)");
        String release = methodBody(activity, "private void releaseService(boolean owner)", "private void detach()");
        String clearNavigation = methodBody(service, "public void clearNavigationCallback(NavigationCallback expected)", "public void addPlayerCallback(PlayerCallback callback)");

        assertTrue("service binding must not start after playback exit",
                bind.contains("if (bound || shouldRejectPlaybackConnection()) return;"));
        assertTrue("late controller completion must stop before attaching callbacks or subclass hooks",
                controller.indexOf("if (shouldRejectPlaybackConnection() || mControllerFuture == null) return;") >= 0
                        && controller.indexOf("if (shouldRejectPlaybackConnection() || mControllerFuture == null) return;")
                        < controller.indexOf("mController = mControllerFuture.get();"));
        assertTrue("late service connection must be rejected before it can register navigation or start playback",
                connected.indexOf("if (shouldRejectPlaybackConnection()) return;") >= 0
                        && connected.indexOf("if (shouldRejectPlaybackConnection()) return;")
                        < connected.indexOf("mService = connectedService;")
                        && connected.indexOf("if (shouldRejectPlaybackConnection()) return;")
                        < connected.indexOf("onServiceConnected();"));
        assertTrue("activity release must clear its callback even before player-key ownership is established",
                release.contains("mService.clearNavigationCallback(getNavigationCallback());"));
        assertTrue("callback cleanup must use identity so an older activity cannot clear a newer owner's callback",
                clearNavigation.contains("if (navigationCallback != expected) return;")
                        && clearNavigation.contains("setNavigationCallback(null, null);"));
    }

    @Test
    public void playbackActivityKeepsScreenOnFromPlaybackStateTransitions() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "PlaybackActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int sync = source.indexOf("private void syncKeepScreenOn()");
        int should = source.indexOf("private boolean shouldKeepScreenOn()", sync);
        int lifecycle = source.indexOf("private String lifecycleState()", should);
        String syncBody = sync >= 0 && should > sync ? source.substring(sync, should) : "";
        String shouldBody = should >= 0 && lifecycle > should ? source.substring(should, lifecycle) : "";
        int connected = source.indexOf("private void handleControllerConnected()");
        int connectedAddListener = source.indexOf("mController.addListener(this);", connected);
        int connectedSync = source.indexOf("syncKeepScreenOn();", connectedAddListener);
        int playing = source.indexOf("public void onIsPlayingChanged(boolean isPlaying)");
        int playingSync = source.indexOf("syncKeepScreenOn();", playing);
        int playWhenReady = source.indexOf("public void onPlayWhenReadyChanged(boolean playWhenReady, int reason)");
        int playWhenReadySync = source.indexOf("syncKeepScreenOn();", playWhenReady);
        int state = source.indexOf("public void onPlaybackStateChanged(int state)");
        int stateSync = source.indexOf("syncKeepScreenOn();", state);
        int service = source.indexOf("public void onServiceConnected(ComponentName name, IBinder binder)");
        int serviceSync = source.indexOf("syncKeepScreenOn();", service);
        int resume = source.indexOf("protected void onResume()");
        int resumeSync = source.indexOf("syncKeepScreenOn();", resume);

        assertTrue(sourcePath + " is missing syncKeepScreenOn", sync >= 0);
        assertTrue(sourcePath + " is missing shouldKeepScreenOn", should > sync);
        assertTrue("wake flag sync must add and clear the window flag from one place",
                syncBody.contains("addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)")
                        && syncBody.contains("clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)"));
        assertTrue("wake flag must stay on while playback is active or preparing to play",
                shouldBody.contains("active.isPlaying()")
                        && shouldBody.contains("active.getPlayWhenReady()")
                        && shouldBody.contains("state == Player.STATE_BUFFERING || state == Player.STATE_READY"));
        assertTrue("controller attachment must sync wake state in case playback was already active", connectedSync > connectedAddListener);
        assertTrue("playing changes must sync wake state", playingSync > playing);
        assertTrue("playWhenReady changes must sync wake state so pausing while buffering clears the flag", playWhenReady >= 0 && playWhenReadySync > playWhenReady);
        assertTrue("state changes must sync wake state so buffering/ready cannot miss the flag", stateSync > state);
        assertTrue("service connection must sync wake state before subclass hooks run", serviceSync > service);
        assertTrue("resume must resync wake state after returning to an active player", resumeSync > resume);
    }

    @Test
    public void playbackLoadingOnlyClearsFromReadyOrTmdbReady() throws Exception {
        Path mobilePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String mobile = new String(Files.readAllBytes(mobilePath), StandardCharsets.UTF_8);
        assertNoPrematurePlaybackReveal(mobilePath, mobile);

        Path leanbackPath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String leanback = new String(Files.readAllBytes(leanbackPath), StandardCharsets.UTF_8);
        assertNoPrematurePlaybackReveal(leanbackPath, leanback);
    }

    @Test
    public void seekRequestsShowPlaybackLoadingOverlay() throws Exception {
        Path playbackPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "PlaybackActivity.java"));
        String playback = new String(Files.readAllBytes(playbackPath), StandardCharsets.UTF_8);
        int hook = playback.indexOf("protected void onSeekStarted()");
        int seekTo = playback.indexOf("protected void seekTo(long time)");
        int seekHook = playback.indexOf("onSeekStarted();", seekTo);
        int controllerSeek = playback.indexOf("mController.seekTo", seekHook);
        int releaseController = playback.indexOf("private void releaseController()");
        int clearSeekPlayer = playback.indexOf("getSeekView().setPlayer(null);", releaseController);
        int removeControllerListener = playback.indexOf("mController.removeListener(this);", clearSeekPlayer);
        int releaseFuture = playback.indexOf("MediaController.releaseFuture", clearSeekPlayer);

        Path seekPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "custom", "CustomSeekView.java"));
        String seek = new String(Files.readAllBytes(seekPath), StandardCharsets.UTF_8);
        int listener = seek.indexOf("public interface SeekListener");
        int setListener = seek.indexOf("public void setSeekListener");
        int seekMethod = seek.indexOf("private void seekToTimeBarPosition");
        int commandPlayer = seek.indexOf("Player commandPlayer = player;", seekMethod);
        int nullGuard = seek.indexOf("if (commandPlayer == null)", commandPlayer);
        int notify = seek.indexOf("seekListener.onSeekStarted();", nullGuard);
        int playerSeek = seek.indexOf("commandPlayer.seekTo(positionMs);", notify);
        int playerPlay = seek.indexOf("commandPlayer.play();", playerSeek);

        Path mobilePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String mobile = new String(Files.readAllBytes(mobilePath), StandardCharsets.UTF_8);
        int mobileOverride = mobile.indexOf("protected void onSeekStarted()");
        int mobileShow = mobile.indexOf("showProgress();", mobileOverride);

        Path leanbackPath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String leanback = new String(Files.readAllBytes(leanbackPath), StandardCharsets.UTF_8);
        int leanbackOverride = leanback.indexOf("protected void onSeekStarted()");
        int leanbackShow = leanback.indexOf("showProgress();", leanbackOverride);

        assertTrue(playbackPath + " is missing onSeekStarted", hook >= 0);
        assertTrue("remote seek must show loading before seeking", seekHook > seekTo && seekHook < controllerSeek);
        assertTrue("controller release must detach the seek view before releasing the controller", clearSeekPlayer > releaseController && clearSeekPlayer < releaseFuture);
        assertTrue("controller release must remove listeners before releasing the controller", removeControllerListener > clearSeekPlayer && removeControllerListener < releaseFuture);
        assertTrue(seekPath + " is missing SeekListener", listener >= 0 && setListener > listener);
        assertTrue("drag seek must snapshot the controller before use", commandPlayer > seekMethod);
        assertTrue("drag seek must ignore an unavailable controller before showing loading", nullGuard > commandPlayer && nullGuard < notify);
        assertTrue("drag seek must notify before player.seekTo", notify > nullGuard && notify < playerSeek);
        assertTrue("drag seek must play through the same controller snapshot", playerPlay > playerSeek);
        assertTrue("mobile video seek must show loading", mobileOverride >= 0 && mobileShow > mobileOverride);
        assertTrue("leanback video seek must show loading", leanbackOverride >= 0 && leanbackShow > leanbackOverride);
    }

    @Test
    public void seekLoadingHasReadyStateFallbackWhenPlaybackStateDoesNotChange() throws Exception {
        Path mobilePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String mobile = new String(Files.readAllBytes(mobilePath), StandardCharsets.UTF_8);
        assertSeekProgressFallback(mobilePath, mobile);

        Path leanbackPath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String leanback = new String(Files.readAllBytes(leanbackPath), StandardCharsets.UTF_8);
        assertSeekProgressFallback(leanbackPath, leanback);
    }

    @Test
    public void mobileTmdbVodRefreshDoesNotClearVideoLoadingBeforePlaybackReady() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void updateVod(Vod item)");
        int loaded = source.indexOf("if (loaded) {", method);
        int fallback = source.indexOf("} else {", loaded);
        String loadedBody = loaded >= 0 && fallback > loaded ? source.substring(loaded, fallback) : "";
        int helper = source.indexOf("private void bindLoadedTmdbDetail()");
        int helperEnd = source.indexOf("private void updateFlag(", helper);
        String helperBody = helper >= 0 && helperEnd > helper ? source.substring(helper, helperEnd) : "";

        assertTrue(sourcePath + " is missing updateVod", method >= 0);
        assertTrue("TMDB VOD refresh must still bind the loaded header", loadedBody.contains("bindLoadedTmdbDetail();") && helperBody.contains("mTmdbHeaderView.bind(mTmdbUIAdapter);"));
        assertFalse("TMDB VOD refresh must not reveal detail content outside onTmdbContentReady", loadedBody.contains("mBinding.progressLayout.showContent();"));
        assertFalse("TMDB VOD refresh must not clear the independent video loading overlay", loadedBody.contains("hideProgress();"));
    }

    @Test
    public void mobileTmdbDetailLoadingHasTimeoutFallback() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int getDetail = source.indexOf("private void getDetail(boolean refresh)");
        int cancelStart = source.indexOf("cancelTmdbDetailFallback();", getDetail);
        int detailCall = source.indexOf("mViewModel.detailContent(getKey(), getId(), refresh);", getDetail);
        int setDetail = source.indexOf("private void setDetail(Vod item)");
        int schedule = source.indexOf("scheduleTmdbDetailFallback();", setDetail);
        int ready = source.indexOf("private void onTmdbContentReady()");
        int cancelReady = source.indexOf("cancelTmdbDetailFallback();", ready);
        int fallback = source.indexOf("private void showTmdbDetailFallback()");
        int end = source.indexOf("private void showNativeDetailFallback(Vod item)", fallback);
        String fallbackBody = fallback >= 0 && end > fallback ? source.substring(fallback, end) : "";

        assertTrue("mobile TMDB detail loading must use the same timeout as TV", source.contains("TMDB_DETAIL_LOAD_TIMEOUT = 15000"));
        assertTrue("new detail requests must cancel stale TMDB loading timeouts", cancelStart > getDetail && cancelStart < detailCall);
        assertTrue("mobile TMDB detail loading must schedule a timeout after the source detail arrives", schedule > setDetail);
        assertTrue("TMDB content-ready must cancel the timeout", cancelReady > ready);
        assertTrue(sourcePath + " is missing showTmdbDetailFallback", fallback >= 0);
        assertTrue("timeout should first bind already-loaded TMDB data, covering missed VOD refresh events", fallbackBody.contains("bindLoadedTmdbDetail();"));
        assertTrue("timeout must fall back to native details instead of leaving the content area blank", fallbackBody.contains("showNativeDetailFallback(mVod);"));
    }

    @Test
    public void leanbackEpisodeFocusOrderReachesTmdbRowsAfterEpisodes() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int order = source.indexOf("private List<Integer> getEpisodeFocusOrders()");
        int updateFocus = source.indexOf("private void updateFocus()", order);
        String orderBody = updateFocus > order ? source.substring(order, updateFocus) : "";
        int episodeGrid = orderBody.indexOf("R.id.episodeGrid");
        int photos = orderBody.indexOf("R.id.tmdbPhotos");
        int part = orderBody.indexOf("R.id.part");
        int quick = orderBody.indexOf("R.id.quick");
        int bindTmdb = source.indexOf("private void bindTmdbData()");
        int bindRatings = source.indexOf("private void bindTmdbOmdbRatings()", bindTmdb);
        String bindBody = bindRatings > bindTmdb ? source.substring(bindTmdb, bindRatings) : "";

        assertTrue(sourcePath + " is missing getEpisodeFocusOrders", order >= 0);
        assertTrue("leanback episode focus order must include the TMDB photos row", photos >= 0);
        assertTrue("TMDB photos must be after episode rows so DPAD_DOWN can leave the selected episode card", episodeGrid >= 0 && episodeGrid < photos);
        assertTrue("TMDB rows must stay before quick search rows in the detail-page focus order", photos < part && part < quick);
        assertTrue("binding visible TMDB rows must refresh episode card nextFocusDown targets",
                bindBody.contains("updateFocus();") && bindBody.indexOf("updateFocus();") < bindBody.indexOf("finishTmdbDetail();"));
    }

    @Test
    public void leanbackTmdbOmdbRatingChipsUseReadableDarkGlass() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private View createOmdbRatingChip(String platform, String value, String color)");
        int next = source.indexOf("private void setupBackdropSlideshow", method);
        String body = method >= 0 && next > method ? source.substring(method, next) : "";

        assertTrue(sourcePath + " is missing createOmdbRatingChip", method >= 0);
        assertTrue("leanback OMDB chips should use dark glass so white/yellow text remains visible on light artwork",
                body.contains("background.setColor(0x6610141A);")
                        && body.contains("background.setStroke(ResUtil.dp2px(1), 0x33FFFFFF);")
                        && body.contains("platformView.setTextColor(0xE6FFFFFF);")
                        && !body.contains("background.setColor(0x26FFFFFF);")
                        && !body.contains("platformView.setTextColor(0xFF9AA7B4);"));
    }

    @Test
    public void leanbackEpisodeHeaderToolsTrapHorizontalDpadInsideToolPair() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private boolean onEpisodeHeaderToolKey");
        int nextMethod = source.indexOf("private boolean isEpisodeListFocused()", method);
        String body = method >= 0 && nextMethod > method ? source.substring(method, nextMethod) : "";

        assertTrue("episode header reverse and grid/list buttons should handle DPAD horizontally themselves",
                source.contains("mBinding.episodeReverse.setOnKeyListener((view, keyCode, event) -> onEpisodeHeaderToolKey(view, keyCode, event));")
                        && source.contains("mBinding.episodeViewMode.setOnKeyListener((view, keyCode, event) -> onEpisodeHeaderToolKey(view, keyCode, event));"));
        assertTrue(sourcePath + " is missing onEpisodeHeaderToolKey", method >= 0);
        assertTrue("reverse DPAD_RIGHT should focus the grid/list button",
                body.contains("KeyUtil.isRightKey(event) && view == mBinding.episodeReverse")
                        && body.contains("mBinding.episodeViewMode.requestFocus(View.FOCUS_RIGHT);"));
        assertTrue("grid/list DPAD_LEFT should focus the reverse button",
                body.contains("KeyUtil.isLeftKey(event) && view == mBinding.episodeViewMode")
                        && body.contains("mBinding.episodeReverse.requestFocus(View.FOCUS_LEFT);"));
        assertTrue("unused horizontal directions should be consumed so focus cannot jump to unrelated action buttons",
                body.lastIndexOf("return true;") > body.lastIndexOf("mBinding.episodeReverse.requestFocus(View.FOCUS_LEFT);"));
    }

    @Test
    public void leanbackHorizontalEpisodeRowExposesVerticalFocusFromEveryCard() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "EpisodeAdapter.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int top = source.indexOf("private boolean isTopEdge(int position)");
        int bottom = source.indexOf("private boolean isBottomEdge(int position)");
        int interfaces = source.indexOf("public interface OnClickListener", bottom);
        String topBody = bottom > top ? source.substring(top, bottom) : "";
        String bottomBody = interfaces > bottom ? source.substring(bottom, interfaces) : "";

        assertTrue(sourcePath + " is missing isTopEdge", top >= 0);
        assertTrue(sourcePath + " is missing isBottomEdge", bottom >= 0);
        assertTrue("single-row horizontal episode cards must all expose nextFocusUp",
                topBody.contains("return !verticalGridMode || position == 0;"));
        assertTrue("single-row horizontal episode cards must all expose nextFocusDown",
                bottomBody.contains("return !verticalGridMode || position == getItemCount() - 1;"));
    }

    @Test
    public void leanbackTmdbDetailUsesChangeSourceActionOutsideDirectNative() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void setOriginalEnhancedActionVisibility(boolean hide)");
        int event = source.indexOf("protected void initEvent()");

        assertTrue(sourcePath + " is missing setOriginalEnhancedActionVisibility", method >= 0);
        assertTrue("native enhanced mode must hide the short display button",
                source.indexOf("mBinding.shortDisplay.setVisibility(hide ? View.GONE : View.VISIBLE)", method) > method);
        assertTrue("TMDB detail modes must show the source change button instead of the search button",
                source.indexOf("mBinding.change1.setVisibility(hide ? View.VISIBLE : View.GONE)", method) > method
                        && source.indexOf("mBinding.searchDetail.setVisibility(hide ? View.GONE : View.VISIBLE)", method) > method);
        assertTrue("source change action must keep long-press global search",
                event >= 0 && source.indexOf("mBinding.change1.setOnLongClickListener(view -> {", event) > event
                        && source.indexOf("onGlobalSearch();", source.indexOf("mBinding.change1.setOnLongClickListener(view -> {", event)) > event);
    }

    @Test
    public void leanbackDetailActionOrderPutsChangeBeforeKeepAndTmdb() throws Exception {
        String layout = new String(Files.readAllBytes(findLeanbackResPath().resolve(Path.of("layout", "activity_video.xml"))), StandardCharsets.UTF_8);
        int search = layout.indexOf("android:id=\"@+id/searchDetail\"");
        int change = layout.indexOf("android:id=\"@+id/change1\"");
        int keep = layout.indexOf("android:id=\"@+id/keep\"");
        int tmdb = layout.indexOf("android:id=\"@+id/tmdbRematch\"");

        assertTrue("direct native search action must stay in the row", search >= 0);
        assertTrue("TMDB detail action order must be change source, favorite, TMDB", change >= 0 && change < keep && keep < tmdb);
    }

    @Test
    public void leanbackGlobalSearchLongPressCarriesCurrentTitle() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void onGlobalSearch()");
        int methodEnd = source.indexOf("\n    }", method);
        String body = method >= 0 && methodEnd > method ? source.substring(method, methodEnd) : "";

        assertTrue(sourcePath + " is missing onGlobalSearch", method >= 0);
        assertTrue("global search must use the current detail title as keyword",
                body.contains("String keyword = mBinding.name.getText().toString().trim();")
                        && body.contains("if (TextUtils.isEmpty(keyword)) keyword = getName();"));
        assertTrue("global search must open SearchActivity with the title instead of a blank search page",
                body.contains("SearchActivity.start(this, keyword);")
                        && !body.contains("SearchActivity.start(this);"));
    }

    @Test
    public void leanbackDetailActionRowScrollsHorizontally() throws Exception {
        Path layoutFile = findLeanbackResPath().resolve(Path.of("layout", "activity_video.xml"));
        Element row = findAndroidId(layoutFile.toFile(), "row2");

        assertTrue(layoutFile + " is missing @+id/row2", row != null);
        assertTrue("leanback detail action row must scroll instead of clipping overflow",
                "HorizontalScrollView".equals(row.getNodeName()));
        assertTrue("leanback detail action row must fill the remaining right side",
                "match_parent".equals(row.getAttribute("android:layout_width"))
                        && "true".equals(row.getAttribute("android:layout_alignParentEnd")));
        assertTrue("leanback detail action row should hide scrollbars",
                "none".equals(row.getAttribute("android:scrollbars")));
    }

    @Test
    public void leanbackOriginalEnhancedRevealsShellInsteadOfStackingASecondLoadingLayer() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void checkCast()");
        int end = source.indexOf("private void checkId()", method);
        String body = method >= 0 && end > method ? source.substring(method, end) : "";
        String overlay = methodBody(source, "private boolean shouldShowTmdbLoadingOverlay()", "private boolean shouldRevealShellWhileLoading()");
        String shell = methodBody(source, "private boolean shouldRevealShellWhileLoading()", "private void setOriginalEnhancedActionVisibility(");
        String reveal = methodBody(source, "private void revealShellWhileTmdbLoads()", "private void finishTmdbDetail()");

        assertTrue(sourcePath + " is missing checkCast", method >= 0);
        assertFalse("original enhanced entry must not blank the whole page before the player window loading layer",
                body.contains("shouldLoadTmdbDetail() && Setting.isOriginalEnhancedDetailPage()"));
        assertTrue("original enhanced entry must reveal the initial preview shell", body.contains("hasInitialPreview()) showInitialPreview();"));
        assertTrue("the full-screen TMDB loading overlay must be suppressed while the shell is revealed",
                overlay.contains("!shouldRevealShellWhileLoading()"));
        assertTrue("shell reveal must be scoped to the original enhanced detail page",
                shell.contains("Setting.isOriginalEnhancedDetailPage()"));
        assertTrue("shell reveal must show content instead of leaving the page on progress",
                reveal.contains("mBinding.progressLayout.showContent();"));
        assertTrue("shell reveal must pre-suppress the source text that TMDB later overwrites",
                reveal.contains("suppressTmdbNativeTextFields();"));
    }

    @Test
    public void leanbackShellRevealDoesNotStealFocusOnTheLaterTmdbReveal() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String body = methodBody(source, "private void revealTmdbDetail()", "private void applyTmdbDetailFields()");
        String shell = methodBody(source, "private void revealShellWhileTmdbLoads()", "private void finishTmdbDetail()");

        assertTrue(sourcePath + " is missing revealTmdbDetail", !body.isEmpty());
        assertTrue("the later TMDB reveal must know whether loading actually hid the content",
                body.contains("boolean hiddenByLoading = !mBinding.progressLayout.isContent();"));
        assertTrue("focus must only be pulled back to the player when loading had hidden the content",
                body.contains("if (hiddenByLoading) mBinding.video.post(() -> mBinding.video.requestFocus());"));
        assertTrue("a shell revealed without an initial preview must restore focus only when nothing else is focused",
                shell.contains("boolean hiddenByLoading = !mBinding.progressLayout.isContent();")
                        && shell.contains("if (hiddenByLoading && !mBinding.getRoot().hasFocus())")
                        && shell.contains("mBinding.video.post(() -> {")
                        && shell.contains("if (!mBinding.getRoot().hasFocus()) mBinding.video.requestFocus();"));
    }

    @Test
    public void mobileOriginalEnhancedRevealsShellWithoutASecondDetailSpinner() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String waitReveal = methodBody(source, "private boolean shouldWaitForTmdbDetailReveal()", "private boolean shouldRevealShellWhileLoading()");
        String shell = methodBody(source, "private boolean shouldRevealShellWhileLoading()", "private void showInitialPreview()");
        String text = methodBody(source, "private void setText(Vod item)", "private boolean shouldUseTmdbTabletWideLayout()");

        assertTrue("the detail area must stop waiting for TMDB before revealing in original enhanced mode",
                waitReveal.contains("isTmdbDetailEnrichmentPending() && !shouldRevealShellWhileLoading()"));
        assertTrue("shell reveal must be scoped to the original enhanced detail page",
                shell.contains("Setting.isOriginalEnhancedDetailPage()"));
        assertTrue("source text must still wait for TMDB enrichment so the revealed shell does not swap text",
                text.contains("if (isTmdbDetailEnrichmentPending()) {"));
    }

    @Test
    public void mobileEveryFullPageProgressOnEntryIsGuardedByShellReveal() throws Exception {
        // 回归：删掉「整页转圈」这一层时只守住了 TMDB overlay 与缓存命中两条路，
        // 漏了 getDetail 这条常走的主路 —— 它无条件 showProgress()，把刚揭开的骨架又压回
        // INVISIBLE，于是原生增强进入播放页依旧是两层加载。这里把进入路径上每一处
        // 整页 showProgress() 都钉住，必须由 shouldRevealShellWhileLoading() 让路。
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String cached = methodBody(source, "private boolean setCachedTmdbDetail()", "private void checkLand()");
        String detail = methodBody(source, "private void getDetail(boolean refresh)", "private void prefetchDirectTmdbDetail()");
        String preview = methodBody(source, "private void showInitialPreview()", "private History createHistory(Vod item)");

        assertTrue("the cache-hit path must not blank the page once the shell is revealed",
                cached.contains("if (!shouldRevealShellWhileLoading()) mBinding.progressLayout.showProgress();"));
        assertTrue("the network detail path must not blank the page once the shell is revealed",
                detail.contains("if (!shouldRevealShellWhileLoading()) mBinding.progressLayout.showProgress();"));
        assertFalse("getDetail must not call showProgress unconditionally",
                detail.contains("\n        mBinding.progressLayout.showProgress();"));
        assertTrue("the initial preview must actually switch ProgressLayout to CONTENT, not just set artwork",
                preview.contains("mBinding.progressLayout.showContent();"));
    }

    @Test
    public void mobileEnhancedBackdropKeepsAnOpaqueBaseColorBehindTheShell() throws Exception {
        // 原生增强把 root/scroll/swipeLayout/progressLayout 全设成 TRANSPARENT 以便全屏 backdrop 透出，
        // 但 contextWall 初始是 gone、图还要等网络。root 若留透明，这段空窗期会露出
        // Material3 DynamicColors 的窗口底色(设备实测为紫色)。root 必须垫不透明底色。
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String base = methodBody(source, "private int enhancedBackdropBaseColor()", "private void applyOriginalEnhancedBackdropLayout()");
        String surface = methodBody(source, "private void applyFusionThemeSurface()", "private void applyContextWallScrimTheme()");

        assertTrue("the enhanced backdrop base color must be opaque in both themes",
                base.contains("0xFFF3F6F9") && base.contains("0xFF0F141A"));
        assertTrue("initTmdbMode must seed the enhanced root with the opaque base color",
                source.contains("mBinding.getRoot().setBackgroundColor(enhancedBackdropBaseColor());"));
        assertTrue("theme re-application must keep the opaque base under the backdrop surface",
                surface.contains("shouldUseTmdbBackdropSurface() ? enhancedBackdropBaseColor()"));
        assertFalse("the backdrop surface must no longer reset the root to a transparent window background",
                surface.contains("mTmdbFallbackToNative || shouldUseTmdbBackdropSurface() ? Color.TRANSPARENT"));
    }

    @Test
    public void leanbackVideoPageRevealsAsSingleComposedContentLayer() throws Exception {
        Path layoutFile = findLeanbackResPath().resolve(Path.of("layout", "activity_video.xml"));
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        Element root = factory.newDocumentBuilder().parse(layoutFile.toFile()).getDocumentElement();
        NodeList children = root.getChildNodes();
        int directContentLayers = 0;
        Element pageContent = null;
        for (int i = 0; i < children.getLength(); i++) {
            if (!(children.item(i) instanceof Element)) continue;
            directContentLayers++;
            pageContent = (Element) children.item(i);
        }

        assertTrue("ProgressLayout must reveal one composed page instead of fading each detail layer independently",
                directContentLayers == 1);
        assertTrue(layoutFile + " is missing @+id/videoPageContent",
                pageContent != null && "@+id/videoPageContent".equals(pageContent.getAttribute("android:id")));
        assertTrue("videoPageContent must be the direct RelativeLayout child managed by ProgressLayout",
                pageContent != null && "RelativeLayout".equals(pageContent.getNodeName()));
    }

    @Test
    public void leanbackAudioSurfaceDoesNotRestoreNativeTmdbMetadataOverSynopsis() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void setVideoDetailsVisible(boolean visible)");
        int end = source.indexOf("private void updateAudioStageText()", method);
        String body = method >= 0 && end > method ? source.substring(method, end) : "";

        assertTrue(sourcePath + " is missing setVideoDetailsVisible", method >= 0);
        assertTrue("TMDB enhanced mode must keep the native director/actor rows hidden when audio surface state is restored",
                body.contains("!shouldUseTmdbLayout() || isIntentTmdbPlayback()")
                        && body.contains("showNativeMetadata"));
    }

    @Test
    public void leanbackTmdbPlaybackOverviewWrapsWithinRightPane() throws Exception {
        Path layoutFile = findLeanbackResPath().resolve(Path.of("layout", "activity_video.xml"));
        Element overview = findAndroidId(layoutFile.toFile(), "tmdbOverview");

        assertTrue(layoutFile + " is missing @+id/tmdbOverview", overview != null);
        assertTrue("TMDB playback overview must be measured between the title column and screen edge so it wraps instead of ellipsizing as one long line",
                "match_parent".equals(overview.getAttribute("android:layout_width"))
                        && "@+id/name".equals(overview.getAttribute("android:layout_alignStart"))
                        && "true".equals(overview.getAttribute("android:layout_alignParentEnd")));
    }

    @Test
    public void leanbackDetailActionButtonsPreferSourceLineBeforeRecommendations() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void setDetailButtonsNextFocus(int fallback)");
        int methodEnd = source.indexOf("\n    }", method);
        String body = method >= 0 && methodEnd > method ? source.substring(method, methodEnd) : "";

        assertTrue(sourcePath + " is missing setDetailButtonsNextFocus fallback handling", method >= 0);
        assertTrue("detail action down focus must prefer the visible source line before TMDB recommendation rows",
                body.contains("int target = isVisible(mBinding.flag) ? R.id.flag : fallback;"));
        assertTrue("all visible/optional detail action buttons must share the same down-focus target",
                body.contains("mBinding.content.setNextFocusDownId(target);")
                        && body.contains("mBinding.shortDisplay.setNextFocusDownId(target);")
                        && body.contains("mBinding.searchDetail.setNextFocusDownId(target);")
                        && body.contains("mBinding.keep.setNextFocusDownId(target);")
                        && body.contains("mBinding.change1.setNextFocusDownId(target);")
                        && body.contains("mBinding.tmdbRematch.setNextFocusDownId(target);"));

        int bindTmdbData = source.indexOf("private void bindTmdbData()");
        int empty = source.indexOf("if (!hasTmdbContent)", bindTmdbData);
        int emptyEnd = source.indexOf("SpiderDebug.log(\"tmdb-tv\"", empty);
        String emptyBody = empty >= 0 && emptyEnd > empty ? source.substring(empty, emptyEnd) : "";
        assertTrue("empty TMDB detail rows must keep change and TMDB actions in the down-focus chain",
                emptyBody.contains("mBinding.change1.setNextFocusDownId(R.id.flag);")
                        && emptyBody.contains("mBinding.tmdbRematch.setNextFocusDownId(R.id.flag);"));
    }

    @Test
    public void leanbackTmdbEpisodeDialogUsesFullscreenAdaptiveCards() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "EpisodeListDialog.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int init = source.indexOf("protected void initView()");
        int width = source.indexOf("private int getPanelWidth()");
        int column = source.indexOf("private int getTmdbCardColumn()");
        int start = source.indexOf("public void onStart()");

        assertTrue(sourcePath + " is missing tmdb episode dialog hooks", init >= 0 && width > init && column > width && start > column);
        assertTrue("Episode dialog must use the full screen for both card and text modes",
                source.indexOf("return ResUtil.getScreenWidth(requireContext());", width) > width
                        && source.indexOf("int width = WindowManager.LayoutParams.MATCH_PARENT;", start) > start
                        && source.indexOf("int gravity = Gravity.CENTER;", start) > start);
        assertTrue("TMDB episode dialog must use the same adaptive TV card columns as TMDB detail",
                source.indexOf("return TmdbEpisodeGridPolicy.tvAdaptiveSpanCount(getResources().getConfiguration().screenWidthDp);", column) > column);
        assertTrue("TMDB episode dialog should use fullscreen optimized padding and background",
                source.indexOf("binding.getRoot().setBackgroundColor(0x80111820);", init) > init
                        && source.indexOf("binding.getRoot().setPadding(ResUtil.dp2px(24), ResUtil.dp2px(20), ResUtil.dp2px(24), ResUtil.dp2px(16));", init) > init);

        Path activityPath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        int span = activity.indexOf("private int getEpisodeGridSpanCount()");
        int setEpisode = activity.indexOf("private void setEpisodeAdapter(List<Episode> items, boolean scrollToCurrent)");
        int toggle = activity.indexOf("private void toggleEpisodeViewMode()");
        assertTrue("native enhanced playback page episode grid must use the shared adaptive TV card columns",
                span >= 0
                        && activity.indexOf("return TmdbEpisodeGridPolicy.tvAdaptiveSpanCount(getResources().getConfiguration().screenWidthDp);", span) > span
                        && setEpisode >= 0
                        && activity.indexOf("if (useTmdbCards && hasMultiple) episodeGridMode = Setting.getTmdbEpisodeGridMode();", setEpisode) > setEpisode
                        && activity.indexOf("mBinding.episodeViewMode.setVisibility(showTmdbEpisodeChrome && hasMultiple && useTmdbCards ? View.VISIBLE : View.GONE);", setEpisode) > setEpisode
                        && toggle >= 0
                        && activity.indexOf("if (mBinding.episodeViewMode.getVisibility() != View.VISIBLE) return;", toggle) > toggle
                        && activity.indexOf("Setting.putTmdbEpisodeGridMode(episodeGridMode);", toggle) > toggle);
    }

    @Test
    public void leanbackPlaybackEpisodeRangeButtonsApplyOnFocusAndHandleClick() throws Exception {
        Path adapterPath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "ArrayAdapter.java"));
        String adapter = new String(Files.readAllBytes(adapterPath), StandardCharsets.UTF_8);
        Path activityPath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String activity = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);
        Path dialogPath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "EpisodeListDialog.java"));
        String dialog = new String(Files.readAllBytes(dialogPath), StandardCharsets.UTF_8);

        int bind = adapter.indexOf("public void onBindViewHolder");
        int listener = adapter.indexOf("public interface OnClickListener");
        assertTrue("leanback episode range buttons must forward click events",
                bind >= 0
                        && adapter.indexOf("mListener.onSegmentClick(position)", bind) > bind
                        && adapter.indexOf("mListener.onSegmentFocus(position)", bind) > bind
                        && listener >= 0
                        && adapter.indexOf("void onSegmentClick(int position);", listener) > listener
                        && adapter.indexOf("void onSegmentFocus(int position);", listener) > listener);

        int recycler = activity.indexOf("mBinding.array.addOnChildViewHolderSelectedListener");
        assertTrue("playback page range focus must apply the segment without moving focus into episodes",
                recycler >= 0
                        && activity.indexOf("selectEpisodeSegment(position, false);", recycler) > recycler);

        int selector = activity.indexOf("private void selectEpisodeSegment(int position, boolean requestEpisodeFocus)");
        assertTrue("playback page must share segment focus and click behavior",
                selector >= 0
                        && activity.indexOf("if (position <= 1) return;", selector) > selector
                        && activity.indexOf("mBinding.array.setSelectedPosition(position);", selector) > selector
                        && activity.indexOf("showEpisodeSegment(position);", selector) > selector);

        int showSegment = activity.indexOf("private void showEpisodeSegment(int position)");
        assertTrue("playback page range focus must replace the visible episode items",
                showSegment >= 0
                        && activity.indexOf("List<Episode> episodes = getFlag().getEpisodes();", showSegment) > showSegment
                        && activity.indexOf("List<Episode> items = episodes.subList(start, end);", showSegment) > showSegment
                        && activity.indexOf("mEpisodeAdapter.addAll(items);", showSegment) > showSegment
                        && activity.indexOf("mEpisodeGridAdapter.addAll(items);", showSegment) > showSegment);

        int selectedPosition = activity.indexOf("private int getSelectedEpisodePosition(List<Episode> episodes)");
        int adjacent = activity.indexOf("private Episode getAdjacentEpisode(int offset)");
        assertTrue("playback next/previous must follow the selected episode after reverse sorting",
                selectedPosition >= 0
                        && activity.indexOf("episodes.get(i).isSelected()", selectedPosition) > selectedPosition
                        && adjacent >= 0
                        && activity.indexOf("int position = getSelectedEpisodePosition(episodes);", adjacent) > adjacent
                        && activity.indexOf("flag.getPosition()", adjacent) == -1);

        int handler = activity.indexOf("public void onSegmentClick(int position)");
        assertTrue("playback page must not jump focus away from the clicked episode range",
                handler >= 0
                        && activity.indexOf("selectEpisodeSegment(position, false);", handler) > handler);

        int focusHandler = activity.indexOf("public void onSegmentFocus(int position)");
        assertTrue("playback page must apply the focused episode range without jumping focus",
                focusHandler >= 0
                        && activity.indexOf("selectEpisodeSegment(position, false);", focusHandler) > focusHandler);

        int dialogHandler = dialog.indexOf("public void onSegmentClick(int position)");
        assertTrue("episode dialog must keep satisfying the ArrayAdapter click contract",
                dialogHandler >= 0
                        && dialog.indexOf("selectSegment(position, true);", dialogHandler) > dialogHandler
                        && dialog.indexOf("public void onSegmentFocus(int position)") > dialogHandler);
    }

    @Test
    public void leanbackPlaybackEpisodeDialogUsesSourceDisplayMode() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void onEpisodes()");
        int nextMethod = source.indexOf("private void onRepeat()", method);
        String methodBody = nextMethod > method ? source.substring(method, nextMethod) : source.substring(method);

        assertTrue(sourcePath + " is missing onEpisodes", method >= 0);
        assertTrue("playback episode selector must keep TMDB/native-enhanced card mode when the source policy requires it",
                methodBody.contains("EpisodeDisplayPolicy.shouldUseTmdbEpisodeCards(isTmdbSourceEnabled(), flag.getEpisodes())")
                        && methodBody.contains(".tmdbCard(tmdbCard)"));
    }

    @Test
    public void leanbackPlaybackEpisodeDialogFocusesCurrentEpisodeOnOpen() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "EpisodeListDialog.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int selected = source.indexOf("private void scrollToSelectedEpisode()");
        int selectedEnd = source.indexOf("private void scrollToSegment(int episodePosition)", selected);
        int focus = source.indexOf("private void focusPosition(BaseGridView grid, int position)");
        int focusEnd = source.indexOf("\n    private ", focus + 1);
        int scroll = source.indexOf("private void scrollToEpisode(int position, boolean requestFocus)");
        int scrollEnd = source.indexOf("\n    @Override", scroll);
        String selectedBody = selected >= 0 && selectedEnd > selected ? source.substring(selected, selectedEnd) : "";
        String focusBody = focus >= 0 && focusEnd > focus ? source.substring(focus, focusEnd) : "";
        String scrollBody = scroll >= 0 && scrollEnd > scroll ? source.substring(scroll, scrollEnd) : "";

        assertTrue(sourcePath + " is missing current episode focus hooks", selected >= 0 && focus >= 0 && scroll >= 0);
        assertTrue("episode dialog must resolve and reveal the currently playing episode when it opens",
                selectedBody.contains("int position = getSelectedEpisodePosition(allEpisodes);")
                        && selectedBody.contains("scrollToSegment(position);")
                        && selectedBody.contains("scrollToEpisode(position - getSegmentStart(selectedSegment), true);"));
        assertTrue("episode dialog must focus the current episode card instead of the grid's default child",
                scrollBody.contains("if (requestFocus) focusPosition(binding.episode, position);"));
        assertTrue("episode dialog must wait until Leanback has attached the current episode card before requesting focus",
                focusBody.contains("grid.setSelectedPosition(target, holder -> holder.itemView.requestFocus());"));
        assertFalse("episode dialog must not use a one-shot post that can run before the current episode card is attached",
                focusBody.contains("grid.post("));
        assertFalse("episode dialog must not fall back to container focus because it can select the wrong episode",
                focusBody.contains("grid.requestFocus();"));
        assertFalse("episode dialog must not rely on container focus because it can select the first or previously focused card",
                scrollBody.contains("if (requestFocus) binding.episode.requestFocus();"));
    }

    @Test
    public void leanbackNativeEnhancedEpisodeGridExpandsWithDetailScroll() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void updateEpisodeGridViewport()");
        int apply = source.indexOf("private void applyEpisodeViewMode(boolean scrollToCurrent)");
        int setEpisode = source.indexOf("private void setEpisodeAdapter(List<Episode> items, boolean scrollToCurrent)");
        int setR2 = source.indexOf("setR2Callback();", setEpisode);
        int nextMethod = source.indexOf("private void finishEpisodeLoading()", setEpisode);
        String setEpisodeBody = nextMethod > setEpisode ? source.substring(setEpisode, nextMethod) : source.substring(setEpisode);
        int setR2InBody = setEpisodeBody.indexOf("setR2Callback();");

        assertTrue(sourcePath + " is missing updateEpisodeGridViewport", method >= 0);
        assertTrue("leanback native enhanced episode grid must expand so source/page controls scroll away with it",
                source.indexOf("params.height = ViewGroup.LayoutParams.WRAP_CONTENT", method) > method);
        assertTrue("leanback native enhanced episode grid must leave vertical scrolling to the detail page",
                source.indexOf("mBinding.episodeGrid.setNestedScrollingEnabled(false)", method) > method);
        assertFalse("leanback native enhanced episode grid must not cap itself to an internal scroll viewport",
                source.substring(method, source.indexOf("private void scrollToCurrentEpisode()", method)).contains("TmdbEpisodeGridPolicy.layout("));
        assertTrue("episode view mode changes must refresh the grid viewport",
                apply >= 0 && source.indexOf("updateEpisodeGridViewport();", apply) > apply);
        assertTrue("episode binding must not schedule a second full adapter refresh after setup",
                setR2 > setEpisode && setR2InBody >= 0 && !setEpisodeBody.substring(setR2InBody).contains("notifyDataSetChanged()"));
    }

    @Test
    public void leanbackTmdbEpisodeCardsAvoidFocusJankHotspots() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "EpisodeAdapter.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int bind = source.indexOf("private void bindCardView(@NonNull ViewHolder holder, Episode item, int position)");
        int next = source.indexOf("private void applyCardSize(AdapterEpisodeCardBinding binding)", bind);
        String bindBody = next > bind ? source.substring(bind, next) : source.substring(bind);

        assertTrue(sourcePath + " is missing bindCardView", bind >= 0);
        assertFalse("TMDB episode card binding must not recreate the focus foreground on every bind",
                bindBody.contains("setForeground("));
        assertTrue("TMDB episode cards should keep long overviews out of the remote focus path",
                bindBody.contains("binding.overview.setText(\"\");")
                        && bindBody.contains("binding.overview.setVisibility(View.GONE);"));
    }

    @Test
    public void leanbackLightweightEpisodeSelectorsKeepTitlesReadable() throws Exception {
        Path adapterPath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "EpisodeAdapter.java"));
        String adapter = new String(Files.readAllBytes(adapterPath), StandardCharsets.UTF_8);
        int width = adapter.indexOf("private int getWidth()");
        int column = adapter.indexOf("public static int getColumn(List<Episode> items, int maxWidth)");
        int text = adapter.indexOf("private void bindTextView(@NonNull ViewHolder holder, Episode item, int position)");
        int addAll = adapter.indexOf("public void addAll(List<Episode> items)");
        int setUseTmdbCard = adapter.indexOf("public void setUseTmdbCard(boolean useTmdbCard)");
        int isUsingTmdbCard = adapter.indexOf("public boolean isUsingTmdbCard()");
        String addAllBody = addAll >= 0 && setUseTmdbCard > addAll ? adapter.substring(addAll, setUseTmdbCard) : "";
        String setUseTmdbCardBody = setUseTmdbCard >= 0 && isUsingTmdbCard > setUseTmdbCard ? adapter.substring(setUseTmdbCard, isUsingTmdbCard) : "";

        assertTrue(adapterPath + " is missing lightweight episode sizing hooks", width >= 0 && column >= 0 && text >= 0);
        assertFalse("episode data refresh must not override the externally configured grid column count",
                addAllBody.contains("column ="));
        assertFalse("episode card mode changes must not override the externally configured grid column count",
                setUseTmdbCardBody.contains("column ="));
        assertTrue("vertical lightweight episode buttons must use the full column width instead of the compact 120dp cap",
                adapter.indexOf("return verticalGridMode ? width : Math.min(width, ResUtil.dp2px(TEXT_BUTTON_MAX_WIDTH_DP));", width) > width);
        assertTrue("lightweight episode columns must be measured from the actual displayed title",
                adapter.indexOf("ResUtil.getTextWidth(getTitle(item), 16)", column) > column);
        assertTrue("lightweight episode buttons must stay single-line so numeric episode labels do not wrap",
                adapter.indexOf("textView.setLayoutParams(params);", text) > text
                        && adapter.indexOf("textView.setSingleLine(true);", text) > text
                        && adapter.indexOf("textView.setMaxLines(1);", text) > text
                        && adapter.indexOf("TextUtils.TruncateAt.MARQUEE", text) > text);

        Path dialogPath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "EpisodeListDialog.java"));
        String dialog = new String(Files.readAllBytes(dialogPath), StandardCharsets.UTF_8);
        int textColumn = dialog.indexOf("private int getTextColumn(List<Episode> episodes)");
        int setEpisodes = dialog.indexOf("private void setEpisodes(Flag flag)");
        assertTrue(dialogPath + " is missing text column policy", textColumn >= 0);
        assertTrue("playback lightweight selector should prefer wide two-column buttons for readable titles",
                dialog.indexOf("return Math.min(2, EpisodeAdapter.getColumn(episodes, getEpisodeContentWidth()));", textColumn) > textColumn);
        assertTrue("playback lightweight selector must set the Leanback grid column width, not only child view width",
                setEpisodes >= 0 && dialog.indexOf("if (!tmdbCard) binding.episode.setColumnWidth(getTextColumnWidth(column));", setEpisodes) > setEpisodes);

        Path nativeItemPath = findLeanbackResPath().resolve(Path.of("layout", "adapter_episode_dialog.xml"));
        String nativeItem = new String(Files.readAllBytes(nativeItemPath), StandardCharsets.UTF_8);
        assertTrue("native episode dialog item should keep upstream single-line marquee behavior",
                nativeItem.contains("android:layout_height=\"42dp\"")
                        && nativeItem.contains("android:singleLine=\"true\"")
                        && nativeItem.contains("android:ellipsize=\"marquee\""));
        assertFalse("native episode dialog item must not wrap compact episode numbers",
                nativeItem.contains("android:maxLines=\"2\""));
    }

    @Test
    public void leanbackTmdbEpisodeDialogAvoidsAlignedCardGridScrolling() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "EpisodeListDialog.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int recycler = source.indexOf("private void setRecyclerView()");
        int key = source.indexOf("private boolean onEpisodeKey(KeyEvent event)");

        assertTrue(sourcePath + " is missing TMDB card focus setup", recycler >= 0 && key > recycler);
        assertTrue("TMDB episode dialog should avoid aligned smooth scrolling for card grids",
                source.indexOf("if (tmdbCard) binding.episode.setFocusScrollStrategy(BaseGridView.FOCUS_SCROLL_ITEM);", recycler) > recycler);
        assertFalse("TMDB episode dialog must not swallow normal up/down focus navigation",
                source.contains("handleTmdbEpisodeGridKey"));
    }

    @Test
    public void leanbackPlaybackEpisodeDialogFallsBackToLastCardOnPartialRow() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "EpisodeListDialog.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int key = source.indexOf("private boolean onEpisodeKey(KeyEvent event)");
        int lower = source.indexOf("private boolean focusLowerFromEpisode()");
        int lowerEnd = source.indexOf("\n    private ", lower + 1);
        String lowerBody = lower >= 0 && lowerEnd > lower ? source.substring(lower, lowerEnd) : "";

        assertTrue(sourcePath + " is missing playback episode down key handling", key >= 0 && lower > key);
        assertTrue("playback episode grid must route the down key through the partial row fallback",
                source.indexOf("if (KeyUtil.isDownKey(event)) return focusLowerFromEpisode();", key) > key);
        assertTrue("playback episode grid must defer to Leanback while a card sits directly below",
                lowerBody.contains("if (position == RecyclerView.NO_POSITION || position + column < count) return false;"));
        assertTrue("playback episode grid must reuse the shared grid policy so a short last row stays reachable",
                lowerBody.contains("TmdbEpisodeGridPolicy.verticalFocusTarget(position, column, count, true)")
                        && lowerBody.contains("if (target == TmdbEpisodeGridPolicy.NO_FOCUS_TARGET) return false;")
                        && lowerBody.contains("focusPosition(binding.episode, target);"));
    }

    @Test
    public void leanbackPlaybackEpisodeKeyIgnoresMissingFocus() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private boolean onEpisodeKey(KeyEvent event)");
        int directCrashPath = source.indexOf("findContainingViewHolder(getCurrentFocus())", method);
        int focus = source.indexOf("View focus = getCurrentFocus();", method);
        int guard = source.indexOf("if (focus == null) return false;", focus);
        int holder = source.indexOf("findContainingViewHolder(focus)", guard);

        assertTrue(sourcePath + " is missing onEpisodeKey", method >= 0);
        assertFalse("episode key handling must not pass a null current focus into RecyclerView", directCrashPath >= 0);
        assertTrue("episode key handling must read current focus before resolving the RecyclerView holder", focus > method);
        assertTrue("episode key handling must ignore key events when focus has already been cleared", guard > focus);
        assertTrue("episode key handling must only resolve a holder after the focus null guard", holder > guard);
    }

    @Test
    public void leanbackEpisodeDialogLetsHeaderScrollWithEpisodes() throws Exception {
        Path layoutPath = findLeanbackResPath().resolve(Path.of("layout", "dialog_episode_list.xml"));
        String layout = new String(Files.readAllBytes(layoutPath), StandardCharsets.UTF_8);
        Path dialogPath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "EpisodeListDialog.java"));
        String source = new String(Files.readAllBytes(dialogPath), StandardCharsets.UTF_8);
        int recycler = source.indexOf("private void setRecyclerView()");
        int setSegmentEpisodes = source.indexOf("private void setSegmentEpisodes(int position)");
        int height = source.indexOf("private void updateEpisodeContentHeight()");
        int align = source.indexOf("private void alignEpisodeScroll(int position)");

        assertTrue("episode dialog must use a scroll root so line and segment controls move away with remote down",
                layout.contains("<androidx.core.widget.NestedScrollView")
                        && layout.contains("android:id=\"@+id/root\"")
                        && layout.contains("android:fillViewport=\"true\""));
        assertTrue("episode grid must expand inside the dialog scroll root instead of owning a fixed viewport",
                layout.contains("android:id=\"@+id/episode\"")
                        && layout.contains("android:layout_height=\"wrap_content\"")
                        && source.indexOf("binding.episode.setNestedScrollingEnabled(false);", recycler) > recycler);
        assertTrue("episode dialog must set an explicit content height because Leanback grids otherwise keep an internal viewport",
                height > setSegmentEpisodes
                        && source.indexOf("updateEpisodeContentHeight();", setSegmentEpisodes) > setSegmentEpisodes
                        && source.indexOf("params.height = getEpisodeContentHeight();", height) > height);
        assertTrue("episode focus changes must scroll the outer dialog instead of letting the inner grid flash under the header",
                align > recycler
                        && source.indexOf("binding.episode.addOnChildViewHolderSelectedListener", recycler) > recycler
                        && source.indexOf("alignEpisodeScroll(position);", recycler) > recycler
                        && source.indexOf("binding.getRoot().scrollTo(0, Math.max(0, targetY));", align) > align);
    }

    @Test
    public void leanbackFullscreenExitRestoresEmbeddedVideoLayout() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int exit = source.indexOf("private void exitFullscreen()");
        int restore = source.indexOf("private void restoreEmbeddedVideoLayoutAfterFullscreen()");
        int next = source.indexOf("private void onContent()", restore);

        assertTrue(sourcePath + " is missing exitFullscreen", exit >= 0);
        assertTrue(sourcePath + " is missing restoreEmbeddedVideoLayoutAfterFullscreen", restore >= 0 && next > restore);

        String exitBody = source.substring(exit, restore);
        String restoreBody = source.substring(restore, next);
        assertTrue("leanback fullscreen exit should reuse the embedded native player restore path",
                exitBody.contains("mBinding.video.setLayoutParams(mFrameParams);")
                        && exitBody.contains("restoreEmbeddedVideoLayoutAfterFullscreen();"));
        assertTrue("embedded native player restore must invalidate stale fullscreen layout measurements",
                restoreBody.contains("mBinding.video.forceLayout();")
                        && restoreBody.contains("mBinding.video.requestLayout();")
                        && restoreBody.contains("mBinding.exo.forceLayout();")
                        && restoreBody.contains("mBinding.exo.requestLayout();")
                        && restoreBody.contains("mBinding.scroll.forceLayout();")
                        && restoreBody.contains("mBinding.scroll.requestLayout();")
                        && restoreBody.contains("mBinding.progressLayout.requestLayout();")
                        && restoreBody.contains("mBinding.video.post(() -> {")
                        && restoreBody.contains("mBinding.progressLayout.postDelayed(() -> {")
                        && restoreBody.contains("}, 180);"));
    }

    @Test
    public void mobileFullscreenExitRestoresEmbeddedVideoLayout() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int exit = source.indexOf("private void exitFullscreen()");
        int restore = source.indexOf("private void restoreEmbeddedVideoLayoutAfterFullscreen()");
        int next = source.indexOf("private void setTransition()", restore);

        assertTrue(sourcePath + " is missing exitFullscreen", exit >= 0);
        assertTrue(sourcePath + " is missing restoreEmbeddedVideoLayoutAfterFullscreen", restore >= 0 && next > restore);

        String exitBody = source.substring(exit, restore);
        String restoreBody = source.substring(restore, next);
        assertTrue("mobile fullscreen exit should reuse the embedded native player restore path",
                exitBody.contains("mBinding.video.setLayoutParams(mFrameParams);")
                        && exitBody.contains("restoreEmbeddedVideoLayoutAfterFullscreen();"));
        assertTrue("mobile embedded native player restore must invalidate stale fullscreen layout measurements",
                restoreBody.contains("mBinding.video.forceLayout();")
                        && restoreBody.contains("mBinding.video.requestLayout();")
                        && restoreBody.contains("mBinding.exo.forceLayout();")
                        && restoreBody.contains("mBinding.exo.requestLayout();")
                        && restoreBody.contains("mBinding.scroll.forceLayout();")
                        && restoreBody.contains("mBinding.scroll.requestLayout();")
                        && restoreBody.contains("mBinding.progressLayout.requestLayout();")
                        && restoreBody.contains("mBinding.video.post(() -> {")
                        && restoreBody.contains("mBinding.progressLayout.postDelayed(() -> {")
                        && restoreBody.contains("}, 180);"));
    }

    @Test
    public void tmdbHeaderKeepsChangeSourceVisibleForEnhancedModes() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "custom", "TmdbHeaderView.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void updateOriginalEnhancedActionVisibility()");

        assertTrue(sourcePath + " is missing updateOriginalEnhancedActionVisibility", method >= 0);
        assertTrue("TMDB header must keep source change available in enhanced modes",
                source.indexOf("changeSource.setVisibility(View.VISIBLE)", method) > method);
    }

    @Test
    public void leanbackNativeActionButtonsShareMinimumWidth() throws Exception {
        Path layoutPath = findLeanbackResPath().resolve(Path.of("layout", "activity_video.xml"));
        for (String id : Arrays.asList("content", "shortDisplay", "searchDetail", "keep", "change1", "tmdbRematch")) {
            Element action = findAndroidId(layoutPath.toFile(), id);
            assertTrue(layoutPath + " is missing @+id/" + id, action != null);
            assertTrue(id + " must use the shared native action width",
                    "96dp".equals(action.getAttribute("android:minWidth")));
        }
    }

    @Test
    public void tmdbHeaderActionButtonsShareMinimumWidth() throws Exception {
        Path layoutPath = findMainResPath().resolve(Path.of("layout", "view_tmdb_header.xml"));
        for (String id : Arrays.asList("tmdbChangeSource", "tmdbKeep", "tmdbRematch")) {
            Element action = findAndroidId(layoutPath.toFile(), id);
            assertTrue(layoutPath + " is missing @+id/" + id, action != null);
            assertTrue(id + " must use the shared TMDB header action width",
                    "60dp".equals(action.getAttribute("android:minWidth")));
        }
    }

    @Test
    public void updateVodOnlyReplacesHistoryWhenKeyChangesAndAlwaysSyncsAfterReplace() throws Exception {
        for (Path root : List.of(findMobileJavaPath(), findLeanbackJavaPath())) {
            Path sourcePath = root.resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
            String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
            int method = source.indexOf("private void updateVod(Vod item)");
            int end = source.indexOf("\n    private ", method + 1);
            String body = method >= 0 && end > method ? source.substring(method, end) : "";

            assertTrue(sourcePath + " is missing updateVod", method >= 0);
            assertTrue(sourcePath + " must only replace history when key actually changes",
                    body.contains("keyChanged = !TextUtils.equals(mHistory.getKey(), nextKey)")
                            && body.contains("if (keyChanged) mHistory.replace(nextKey)"));
            assertFalse(sourcePath + " must not unconditionally replace history on every id update",
                    body.contains("if (id) mHistory.replace(getHistoryKey())"));
            assertTrue(sourcePath + " must sync history after key migration, an async metadata refresh, or TMDB identity stamping",
                    body.contains("if (keyChanged || pic || name || episodeTitleChanged || tmdbIdStamped) syncHistory()"));
        }
    }

    @Test
    public void manualTmdbMatchReloadsCrossSourceHistoryInBothNativePlayers() throws Exception {
        for (Path root : List.of(findMobileJavaPath(), findLeanbackJavaPath())) {
            Path sourcePath = root.resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
            String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
            int method = source.indexOf("private void updateVod(Vod item)");
            int end = source.indexOf("\n    private ", method + 1);
            String body = method >= 0 && end > method ? source.substring(method, end) : "";
            int reload = body.indexOf("reloadHistoryAfterTmdbMatch()");
            int updateFlag = body.indexOf("updateFlag(getFlag(), item.getFlags())");
            int resume = body.indexOf("if (historyReloaded) resumeHistoryAfterTmdbMatch();");

            assertTrue(sourcePath + " must reload history after the matched TMDB identity becomes available", reload >= 0);
            assertTrue(sourcePath + " must rebind flags before resuming the reloaded history", updateFlag > reload);
            assertTrue(sourcePath + " must reselect the matching line and episode after flags are rebound", resume > updateFlag);
            assertTrue(sourcePath + " must resolve playback with the explicit matched TMDB item",
                    source.contains("History.findPlayback(getHistoryKey(), List.of(item.getName(), getName()), item.getFlags(), matched, currentSourceSeasonNumber(item))"));

            int resumeMethod = source.indexOf("private void resumeHistoryAfterTmdbMatch()");
            int alignMethod = source.indexOf("private void alignHistoryWithSelectedEpisode", resumeMethod);
            String resumeBody = resumeMethod >= 0 && alignMethod > resumeMethod ? source.substring(resumeMethod, alignMethod) : "";
            int setPosition = resumeBody.indexOf("setPosition();");
            int applyPending = resumeBody.indexOf("applyPendingResumeSeek();");
            assertTrue(sourcePath + " must protect the cross-source position while switching playback",
                    source.contains("private boolean tmdbHistoryResumePending;")
                            && source.contains("tmdbHistoryResumePending = true;")
                            && source.contains("if (mHistory == null || tmdbHistoryResumePending) return;"));
            assertTrue(sourcePath + " must not cache or clear the target progress while the old player is stopping",
                    source.contains("if (!tmdbHistoryResumePending) {")
                            && source.contains("if (!sameEpisode && !tmdbHistoryResumePending) {"));
            int saveHistoryMethod = source.indexOf("private void saveHistory(boolean exit)");
            int saveHistoryEnd = source.indexOf("\n    private ", saveHistoryMethod + 1);
            String saveHistoryBody = saveHistoryMethod >= 0 && saveHistoryEnd > saveHistoryMethod
                    ? source.substring(saveHistoryMethod, saveHistoryEnd) : "";
            int saveCacheGuard = saveHistoryBody.indexOf("if (!tmdbHistoryResumePending) {");
            int saveCacheWrite = saveHistoryBody.indexOf("EpisodePositionCache.get().put(");
            assertTrue(sourcePath + " must not write the old player position into the resumed episode cache during refresh",
                    saveCacheGuard >= 0 && saveCacheWrite > saveCacheGuard);

            assertTrue(sourcePath + " must immediately apply a pending IJK seek when the selected episode is unchanged",
                    setPosition >= 0 && applyPending > setPosition);

            int manual = source.indexOf("private void applyManualTmdb(TmdbItem item)");
            int manualEnd = source.indexOf("\n    private ", manual + 1);
            String manualBody = manual >= 0 && manualEnd > manual ? source.substring(manual, manualEnd) : "";
            int explicitReload = manualBody.indexOf("reloadHistoryAfterTmdbMatch(item)");
            int explicitResume = manualBody.indexOf("resumeHistoryAfterTmdbMatch()", explicitReload);
            int load = manualBody.indexOf("mTmdbUIAdapter.load(item, mVod)");
            assertTrue(sourcePath + " must reload history directly from the manually selected TMDB identity", explicitReload >= 0);
            assertTrue(sourcePath + " must resume the resolved history before the asynchronous TMDB detail load",
                    explicitResume > explicitReload && load > explicitResume);
        }
    }

    @Test
    public void mobileDirectTmdbPlaybackUsesCarriedSynopsisForUnmatchedFallback() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int carried = source.indexOf("intent.putExtra(\"tmdb_vod_content\", vod.getContent());");
        int getter = source.indexOf("private String getTmdbVodContent()");
        int getterRead = source.indexOf("getIntent().getStringExtra(\"tmdb_vod_content\")", getter);
        int setDetail = source.indexOf("private void setDetail(Vod item)");
        int fallback = source.indexOf("item.checkContent(getTmdbVodContent());", setDetail);
        int render = source.indexOf("setText(item);", setDetail);

        assertTrue("direct colorful-detail playback must carry the source synopsis independently of TMDB matching", carried >= 0);
        assertTrue("mobile playback must read the carried source synopsis", getter >= 0 && getterRead > getter && getterRead < setDetail);
        assertTrue("unmatched TMDB playback must restore the carried synopsis before rendering native details",
                fallback > setDetail && render > fallback);
    }

    @Test
    public void mobileVideoDirectTmdbCarriesDetailThemeIntoPlayback() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);

        assertTrue("direct TMDB playback must persist the selected detail theme in its intent",
                source.contains("EXTRA_TMDB_DETAIL_THEME") && source.contains("intent.putExtra(EXTRA_TMDB_DETAIL_THEME, Setting.getTmdbDetailTheme())"));
        assertTrue("Fusion playback theme resolution must follow the current detail theme preference",
                source.contains("return Setting.getTmdbDetailTheme() == 1 ? 1 : 2;"));
        assertTrue("TMDB header view must receive the playback theme override before it draws the source panel",
                source.contains("mTmdbHeaderView.setDetailThemeMode(getFusionDetailThemeMode())"));
    }

    @Test
    public void mobileVideoDirectTmdbRevealsDetailAfterHeaderBindWithoutClearingVideoProgress() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void updateVod(Vod item)");
        int bind = source.indexOf("mTmdbHeaderView.bind(mTmdbUIAdapter);", method);
        int style = source.indexOf("styleTmdbSourceInFlagTitle();", bind);
        int contentReady = source.indexOf("private void onTmdbContentReady()");
        int reveal = source.indexOf("showDetailContent();", contentReady);

        assertTrue(sourcePath + " is missing updateVod", method >= 0);
        assertTrue("direct TMDB playback must bind the header when TMDB data is ready", bind > method);
        assertTrue("direct TMDB playback should continue styling after the header bind", style > bind);
        assertTrue("direct TMDB playback must reveal detail content from the TMDB content-ready callback", reveal > contentReady);
        assertFalse("direct TMDB header bind must not clear the independent video loading overlay",
                source.substring(bind, style).contains("hideProgress();"));
    }

    @Test
    public void mobileVideoFusionThemeToggleActuallyChangesThemeMode() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);

        assertTrue("fusion theme button must switch light to dark and dark to light",
                source.contains("int theme = isFusionLightTheme() ? 2 : 1;"));
    }

    @Test
    public void tmdbHeaderThemeToggleIsHiddenUntilVideoActivityAllowsFusionThemeSwitching() throws Exception {
        Path layoutPath = findMainResPath().resolve(Path.of("layout", "view_tmdb_header.xml"));
        Element themeToggle = findAndroidId(layoutPath.toFile(), "tmdbThemeToggle");
        Path headerPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "custom", "TmdbHeaderView.java"));
        String headerSource = new String(Files.readAllBytes(headerPath), StandardCharsets.UTF_8);
        Path videoPath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String videoSource = new String(Files.readAllBytes(videoPath), StandardCharsets.UTF_8);
        int styleFusion = headerSource.indexOf("private void styleFusionActions()");
        int styleFusionEnd = headerSource.indexOf("private void clearFusionActionStyling()", styleFusion);
        String styleFusionBody = headerSource.substring(styleFusion, styleFusionEnd);

        assertTrue(layoutPath + " is missing @+id/tmdbThemeToggle", themeToggle != null);
        assertTrue("TMDB header theme toggle must start hidden so colorful playback pages do not show it",
                "gone".equals(themeToggle.getAttribute("android:visibility")));
        assertFalse("TmdbHeaderView must not force the theme toggle visible; VideoActivity owns that mode decision",
                styleFusionBody.contains("themeToggle.setVisibility(View.VISIBLE)"));
        assertTrue("VideoActivity must still show the header theme toggle for the real fusion detail mode",
                videoSource.contains("DetailThemeVisibility.showFusionThemeButton(Setting.isFusionDetailPage(), isFullscreen(), isInPictureInPictureMode())"));
    }

    @Test
    public void mobileVideoFusionPlaybackControlsRefreshAfterBeingMoved() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void moveFlagAndEpisodeToTmdb()");
        int updateVisibility = source.indexOf("updateEpisodeGroupVisibility();", method);
        int refreshHeader = source.indexOf("mTmdbHeaderView.refreshTheme();", updateVisibility);
        int refreshSurface = source.indexOf("applyFusionThemeSurface();", refreshHeader);

        assertTrue(sourcePath + " is missing moveFlagAndEpisodeToTmdb", method >= 0);
        assertTrue("fusion playback controls must update visibility before final theme sync", updateVisibility > method);
        assertTrue("fusion playback controls must refresh header theme after all source and episode views are moved", refreshHeader > updateVisibility);
        assertTrue("fusion playback surface must sync after header playback controls are re-themed", refreshSurface > refreshHeader);
    }

    @Test
    public void mobileVideoFusionUsesNativePlayerActionButtons() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int move = source.indexOf("private void moveFusionPlayerActionsToTmdb");
        int actionRoot = source.indexOf("mBinding.control.action.getRoot()", move);
        int settings = source.indexOf("applyActionButtonSettings();", actionRoot);
        int layoutParams = source.indexOf("new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)", actionRoot);
        int docked = source.indexOf("private boolean isFusionPlayerActionsDocked()");

        assertTrue(sourcePath + " is missing moveFusionPlayerActionsToTmdb", move >= 0);
        assertTrue("fusion must reuse the native player action root", actionRoot > move);
        assertTrue("fusion reused player buttons must honor player button order and visibility settings", settings > actionRoot);
        assertTrue("fusion reused player action row must fill the TMDB playback controls width on narrow screens", layoutParams > actionRoot);
        assertTrue("showControl must keep docked fusion player buttons visible", docked > move);
        assertTrue("fusion player button text should share the moved playback control theme",
                source.indexOf("tintFusionPlaybackTextTree(mBinding.control.action.getRoot()", source.indexOf("private void applyTmdbPlaybackControlColors()")) > 0);
    }

    @Test
    public void mobileVideoFusionPlaybackControlsRetintAfterHeaderBind() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int bind = source.indexOf("mTmdbHeaderView.bind(mTmdbUIAdapter);");
        int sourceStyle = source.indexOf("styleTmdbSourceInFlagTitle();", bind);
        int controlStyle = source.indexOf("applyTmdbPlaybackControlColors();", sourceStyle);
        int method = source.indexOf("private void applyTmdbPlaybackControlColors()");
        int nextMethod = source.indexOf("private boolean isTmdbPlaybackLightTheme()", method);
        String methodBody = nextMethod > method ? source.substring(method, nextMethod) : source.substring(method);
        int viewModeButton = source.indexOf("private void updateEpisodeViewModeButton()");
        int viewModeIcon = source.indexOf("mBinding.episodeViewMode.setImageResource", viewModeButton);
        int viewModeRetint = source.indexOf("applyTmdbPlaybackControlColors();", viewModeIcon);

        assertTrue(sourcePath + " is missing header bind", bind >= 0);
        assertTrue("fusion playback controls must retint after source text is restyled following header bind", controlStyle > sourceStyle);
        assertTrue(sourcePath + " is missing applyTmdbPlaybackControlColors", method >= 0);
        assertTrue("fusion playback control retint must cover the moved line title", source.indexOf("mBinding.flagTitleBar", method) > method);
        assertTrue("fusion playback control retint must cover the moved episode title", source.indexOf("mBinding.episodeTitleBar", method) > method);
        assertTrue("fusion playback control retint must cover reverse icon", source.indexOf("mBinding.reverse", method) > method);
        assertTrue("fusion playback control retint must cover grid/list icon", source.indexOf("mBinding.episodeViewMode", method) > method);
        assertTrue("tmdb playback control retint must not depend solely on the global fusion setting",
                source.indexOf("if (!Setting.isFusionDetailPage()) return;", method) < 0);
        assertTrue("TMDB playback controls must not force non-fusion cinema pages into the light palette",
                source.indexOf("if (!Setting.isFusionDetailPage()) return true;", method) < 0);
        assertTrue("TMDB playback labels and icons must use the header's current detail theme",
                source.indexOf("mTmdbHeaderView.isCurrentDetailLightTheme()", method) > method);
        assertTrue("dynamic backdrop playback labels must force white text instead of profile dark text",
                methodBody.contains("boolean light = !shouldUseTmdbBackdropSurface() && isTmdbPlaybackLightTheme()")
                        && methodBody.contains("int color = shouldUseTmdbBackdropSurface() ? Color.WHITE : tmdbPlaybackControlColor(light)"));
        assertTrue("TMDB playback labels and icons must match the header section title color",
                source.indexOf("mTmdbHeaderView.getFusionSectionTitleColor()", method) > method);
        assertTrue("TMDB flag chips must use the same resolved playback theme as the moved labels",
                methodBody.contains("mFlagAdapter.setTmdbLight(light)"));
        assertTrue("fullscreen player action buttons must stay white instead of inheriting light TMDB text",
                methodBody.contains("boolean playerOverlay = isFullscreen() || mBinding.control.action.getRoot().getParent() == mBinding.control.bottom")
                        && methodBody.contains("playerOverlay ? Color.WHITE : color"));
        assertTrue("fusion playback icon retint must use a color filter", source.indexOf("setColorFilter(color)", method) > method);
        assertTrue("light fusion playback labels must clear inherited video shadows", source.indexOf("setShadowLayer(0, 0, 0, 0)", method) > method);
        assertTrue("episode view mode icon must be retinted after changing its drawable", viewModeRetint > viewModeIcon);
    }

    @Test
    public void mobileVideoEpisodeViewportKeepsItsCapUnlessThePageOwnsTmdbEpisodeScroll() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void updateEpisodeViewportHeight()");
        int nextMethod = source.indexOf("private boolean usesOuterEpisodePageScroll()", method);
        String methodBody = nextMethod > method ? source.substring(method, nextMethod) : source.substring(method);
        int outerScroll = source.indexOf("private boolean usesOuterEpisodePageScroll()");
        int outerScrollEnd = source.indexOf("private boolean isTmdbEpisodeCardMode()", outerScroll);
        String outerScrollBody = outerScrollEnd > outerScroll ? source.substring(outerScroll, outerScrollEnd) : source.substring(outerScroll);

        assertTrue(sourcePath + " is missing updateEpisodeViewportHeight", method >= 0);
        assertTrue("episode viewport must keep a stable dp cap unless the page owns the TMDB episode scroll",
                methodBody.contains("int height = usesOuterEpisodePageScroll() ? 0 : limit;")
                        && methodBody.contains("!usesOuterEpisodePageScroll() && isTmdbEpisodeCardMode()"));
        assertTrue("the outer page scroll contract must cover original enhanced pages and reparented backdrop TMDB playback",
                outerScrollBody.contains("Setting.isOriginalEnhancedDetailPage()")
                        && outerScrollBody.contains("mTmdbControlsMoved && shouldUseTmdbBackdropSurface()"));
        assertTrue("a reparented outer-scroll episode grid must release vertical gestures to its page",
                source.contains("mBinding.episode.setOnTouchListener((view, event) -> {")
                        && source.contains("if (!usesOuterEpisodePageScroll()) return false;")
                        && source.contains("int action = event.getActionMasked();")
                        && source.contains("action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE")
                        && source.contains("view.getParent().requestDisallowInterceptTouchEvent(false);"));
        assertTrue("episode viewport must not collapse based on current remaining screen height",
                !methodBody.contains("available ="));
        assertTrue("episode viewport must not depend on root height after the method starts",
                !methodBody.contains("mBinding.getRoot().getHeight()"));
    }

    @Test
    public void mobileOriginalEnhancedEpisodeViewportUsesOuterPageScrollWithoutHeightCap() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int outerScroll = source.indexOf("private boolean usesOuterEpisodePageScroll()");
        int outerScrollEnd = source.indexOf("private boolean isTmdbEpisodeCardMode()", outerScroll);
        String outerScrollBody = outerScrollEnd > outerScroll ? source.substring(outerScroll, outerScrollEnd) : source.substring(outerScroll);

        assertTrue(sourcePath + " is missing usesOuterEpisodePageScroll", outerScroll >= 0);
        assertTrue("original enhanced playback must remove the inner episode viewport height cap",
                outerScrollBody.contains("Setting.isOriginalEnhancedDetailPage()"));
        assertTrue("the first original-enhanced layout pass must replace the XML max-height default",
                source.contains("private int mEpisodeMaxHeight = -1;"));
    }

    @Test
    public void mobileBackdropTmdbEpisodeGridLetsTheOuterScrollContentGrowAfterReparenting() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int move = source.indexOf("private void moveFlagAndEpisodeToTmdb()");
        int restore = source.indexOf("private void restoreFlagAndEpisodeFromTmdb()");
        int helper = source.indexOf("private void updateTmdbPlaybackScrollContentHeight()");
        int helperEnd = source.indexOf("private void moveTmdbSourceToFlagTitle", helper);
        String moveBody = restore > move ? source.substring(move, restore) : source.substring(move);
        String restoreBody = helper > restore ? source.substring(restore, helper) : source.substring(restore);
        String helperBody = helperEnd > helper ? source.substring(helper, helperEnd) : source.substring(helper);

        assertTrue(sourcePath + " is missing TMDB playback scroll content height sync", helper >= 0);
        assertTrue("reparenting the episode grid must let the outer scroll child expand to content height",
                helperBody.contains("View child = mBinding.scroll.getChildAt(0);")
                        && helperBody.contains("if (!(child instanceof ViewGroup content)) return;")
                        && helperBody.contains("int height = mTmdbControlsMoved && usesOuterEpisodePageScroll()")
                        && helperBody.contains("? ViewGroup.LayoutParams.WRAP_CONTENT : ViewGroup.LayoutParams.MATCH_PARENT;")
                        && helperBody.contains("content.setLayoutParams(params);")
                        && helperBody.contains("mBinding.scroll.requestLayout();"));
        assertTrue("the expanded content height must be applied after moving the episode grid into TMDB controls",
                moveBody.contains("updateTmdbPlaybackScrollContentHeight();"));
        assertTrue("the full-height content contract must be restored when TMDB controls move back",
                restoreBody.contains("updateTmdbPlaybackScrollContentHeight();"));
    }

    @Test
    public void leanbackVideoContextWallIsCoveredByBackdropMask() throws Exception {
        Path layoutFile = findLeanbackResPath().resolve(Path.of("layout", "activity_video.xml"));
        Element contextWall = findAndroidId(layoutFile.toFile(), "contextWall");
        Element backdropMask = findAndroidId(layoutFile.toFile(), "backdropMask");

        assertTrue(layoutFile + " is missing @+id/contextWall", contextWall != null);
        assertTrue(layoutFile + " is missing @+id/backdropMask", backdropMask != null);
        assertTrue("context wall and backdrop mask must share a parent type so z-order protects playback text",
                contextWall.getParentNode().getNodeName().equals(backdropMask.getParentNode().getNodeName()));
        assertTrue("context wall must draw below backdrop mask", isAndroidIdBefore(layoutFile, "contextWall", "backdropMask"));
    }

    @Test
    public void mobileVideoContextWallHasFullScreenScrimAboveIt() throws Exception {
        List<Path> layoutFiles = Files.walk(findMobileResPath())
                .filter(path -> path.getFileName().toString().equals("activity_video.xml"))
                .filter(path -> path.getParent().getFileName().toString().startsWith("layout"))
                .collect(Collectors.toList());

        assertFalse("No mobile activity_video.xml layouts found", layoutFiles.isEmpty());
        for (Path layoutFile : layoutFiles) {
            Element contextWall = findAndroidId(layoutFile.toFile(), "contextWall");
            Element scrim = findAndroidId(layoutFile.toFile(), "videoContextScrim");

            assertTrue(layoutFile + " is missing @+id/contextWall", contextWall != null);
            assertTrue(layoutFile + " is missing @+id/videoContextScrim", scrim != null);
            assertTrue("context wall and scrim must share a parent type so z-order protects playback text",
                    contextWall.getParentNode().getNodeName().equals(scrim.getParentNode().getNodeName()));
            assertTrue(layoutFile + " must draw the scrim above the context wall", isAndroidIdBefore(layoutFile, "contextWall", "videoContextScrim"));
            assertTrue(layoutFile + " scrim must cover the full playback detail surface",
                    "match_parent".equals(scrim.getAttribute("android:layout_width"))
                            && "match_parent".equals(scrim.getAttribute("android:layout_height")));
        }
    }

    @Test
    public void mobileTmdbFallbackUsesAppWallpaperSurface() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int fallback = source.indexOf("private void showNativeDetailFallback(Vod item)");
        int surface = source.indexOf("private void applyNativeFallbackWallpaperSurface()");
        int theme = source.indexOf("private void applyFusionThemeSurface()");
        int scrim = source.indexOf("private void applyContextWallScrimTheme()");

        assertTrue(sourcePath + " is missing showNativeDetailFallback", fallback >= 0);
        assertTrue(sourcePath + " is missing applyNativeFallbackWallpaperSurface", surface >= 0);
        assertTrue("unmatched TMDB fallback must switch to the app wallpaper surface before native rows draw",
                source.indexOf("applyNativeFallbackWallpaperSurface();", fallback) > fallback);
        assertTrue("unmatched TMDB fallback must clear the opaque root background",
                source.indexOf("mBinding.getRoot().setBackgroundColor(Color.TRANSPARENT);", surface) > surface);
        assertTrue("unmatched TMDB fallback must clear the old TMDB scroll background",
                source.indexOf("mBinding.scroll.setBackgroundColor(Color.TRANSPARENT);", surface) > surface);
        assertTrue("unmatched TMDB fallback must keep a readable dark scrim over the app wallpaper",
                source.indexOf("mBinding.videoContextScrim.setBackgroundResource(R.drawable.shape_video_context_scrim);", scrim) > scrim
                        && source.indexOf("mBinding.videoContextScrim.setVisibility(View.VISIBLE);", scrim) > scrim);
        // 回退必须排在 backdrop surface 判定之前：原生增强下 TMDB 未匹配时，
        // 若先命中 backdrop surface 就会拿到不透明底色，App 壁纸再也透不出来。
        assertTrue("fusion theme refresh must not cover unmatched fallback with a solid color",
                source.indexOf("int base = mTmdbFallbackToNative ? Color.TRANSPARENT", theme) > theme);
    }

    @Test
    public void mobileColorfulAndNativeStyledTmdbDetailUseDynamicBackdropSurface() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int predicate = source.indexOf("private boolean shouldUseTmdbBackdropSurface()");
        int setContextWall = source.indexOf("private void setContextWall(String url, boolean skipLock)");
        int init = source.indexOf("private void initTmdbMode()");
        int themeSurface = source.indexOf("private void applyFusionThemeSurface()");
        int move = source.indexOf("private void moveFlagAndEpisodeToTmdb()");

        assertTrue(sourcePath + " is missing shouldUseTmdbBackdropSurface", predicate >= 0);
        assertTrue("colorful and native styled TMDB detail must opt into the dynamic backdrop surface",
                source.indexOf("Setting.getDetailOpenMode() == Setting.DETAIL_OPEN_ENHANCED", predicate) > predicate
                        && source.indexOf("Setting.isTmdbNativeStyle()", predicate) > predicate);
        assertTrue("dynamic backdrop surface must be allowed even when playback artwork wall is disabled",
                source.indexOf("!shouldUseTmdbBackdropSurface()", setContextWall) > setContextWall);
        assertTrue("colorful and native styled TMDB detail must receive backdrop slideshow changes",
                source.indexOf("shouldUseTmdbBackdropSurface()", init) > init
                        && source.indexOf("setContextWall(imageUrl, true);", init) > init);
        assertTrue("colorful and native styled TMDB detail must use the fullscreen backdrop layout with transparent inner layers",
                source.indexOf("applyOriginalEnhancedBackdropLayout();", init) > init
                        && source.indexOf("mBinding.scroll.setBackgroundColor(Color.TRANSPARENT);", init) > init);
        // root 例外：内层保持透明让 backdrop 透出，但 root 垫不透明底色。contextWall 初始 gone、
        // 图要等网络，root 若透明会露出 Material3 DynamicColors 窗口底色(实测紫)。
        // contextWall 是首个子视图、绘制在 root 底色之上，所以垫底不会遮住 backdrop。
        assertTrue("the backdrop surface must keep an opaque root base so the window background never shows through",
                source.indexOf("shouldUseTmdbBackdropSurface() ? enhancedBackdropBaseColor()", themeSurface) > themeSurface);
        assertTrue("header theme refresh must re-hide the standalone hero artwork",
                source.indexOf("mTmdbHeaderView.refreshTheme();", move) > move
                        && source.indexOf("mTmdbHeaderView.hideNativeHeroBackdrop();", move) > move);
    }

    @Test
    public void tmdbPlaybackBackdropSurfaceHidesTopPosterArtwork() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "custom", "TmdbHeaderView.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("public void hideNativeHeroBackdrop()");

        assertTrue(sourcePath + " is missing hideNativeHeroBackdrop", method >= 0);
        assertTrue("dynamic backdrop surface must keep the hidden backdrop from reserving hero height",
                source.indexOf("params.height = 1;", method) > method);
        assertTrue("dynamic backdrop surface must hide the top poster card",
                source.indexOf("R.id.tmdbPoster", method) > method
                        && source.indexOf("posterCard.setVisibility(View.GONE);", method) > method);
        assertTrue("title text must align after the poster card is removed",
                source.indexOf("textParams.setMarginStart(0);", method) > method);
        assertTrue("dynamic backdrop surface must force header text to white with shadow",
                source.indexOf("backdropSurfaceMode = true;", method) > method
                        && source.indexOf("applyBackdropSurfaceTextColors();", method) > method
                        && source.indexOf("private void applyBackdropSurfaceTextColors()") > method
                        && source.indexOf("styleFusionBackdropText(textView, COLOR_FUSION_BACKDROP_TEXT)", method) > method);
        assertTrue("dynamic backdrop surface must not recolor action button text on light pills",
                source.indexOf("!(view instanceof MaterialButton)", method) > method);
        assertTrue("dynamic backdrop surface must style rating chips and action buttons as dark translucent glass",
                source.contains("COLOR_BACKDROP_SURFACE_CONTROL_BG = 0x6610141A")
                        && source.contains("styleBackdropSurfaceRatingChips();")
                        && source.contains("tintActions(Setting.DETAIL_STYLE_CINEMA);")
                        && source.contains("if (backdropSurfaceMode) {")
                        && source.contains("button.setTextColor(COLOR_FUSION_BACKDROP_TEXT);"));
        assertTrue("rating chips created after backdrop mode starts must keep white text instead of source colors",
                source.contains("chip.setTextColor(backdropSurfaceMode ? COLOR_FUSION_BACKDROP_TEXT")
                        && source.contains("textView.setTextColor(backdropSurfaceMode ? COLOR_FUSION_BACKDROP_TEXT"));
        assertTrue("dynamic backdrop surface must be reported as a dark readable surface",
                source.contains("if (backdropSurfaceMode) return false;")
                        && source.contains("if (backdropSurfaceMode) return COLOR_FUSION_BACKDROP_TEXT;"));
    }

    @Test
    public void leanbackTmdbRecommendationPresenterReportsAlreadyFocusedAiCards() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "presenter", "TmdbRecommendationPresenter.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);

        assertTrue("AI recommendation reason must update when a rebound card is already focused",
                source.contains("holder.view.hasFocus() && mFocusListener != null") && source.contains("mFocusListener.onItemFocus(tmdbItem, true)"));
        assertTrue("AI recommendation reason must clear when a focused card is unbound",
                source.contains("viewHolder.view.hasFocus() && holder.item != null && mFocusListener != null")
                        && source.contains("mFocusListener.onItemFocus(holder.item, false)"));
    }

    @Test
    public void tmdbHeaderRefreshesThemeEvenWhenModeValueDidNotChange() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "custom", "TmdbHeaderView.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("public void setDetailThemeMode(int detailThemeMode)");
        int assign = source.indexOf("detailThemeModeOverride = normalized;", method);
        int apply = source.indexOf("applyTheme();", method);
        int earlyReturn = source.indexOf("return;", method);

        assertTrue(sourcePath + " is missing setDetailThemeMode", method >= 0);
        assertTrue("TMDB header must remember the normalized detail theme mode", assign > method);
        assertTrue("TMDB header must apply theme after receiving the detail theme mode", apply > assign);
        assertTrue("TMDB header must not skip theme refresh just because the numeric mode is unchanged",
                earlyReturn < 0 || earlyReturn > apply);
    }

    @Test
    public void tmdbDetailThemeToggleRestylesExternalLinks() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int cycle = source.indexOf("private void cycleThemeMode()");
        int apply = source.indexOf("applyDetailTheme();", cycle);
        int refresh = source.indexOf("refreshDetailThemeDynamicViews();", apply);
        int method = source.indexOf("private int addExternalLink(String name, String url)");
        int nextMethod = source.indexOf("private void openExternalLink(String url)", method);
        int style = source.indexOf("private void styleExternalLinkRow(View view, ThemeColors colors)");
        int styleEnd = source.indexOf("private void openExternalLink(String url)", style);
        String methodBody = nextMethod > method ? source.substring(method, nextMethod) : source.substring(method);
        String styleBody = styleEnd > style ? source.substring(style, styleEnd) : source.substring(style);

        assertTrue(sourcePath + " is missing cycleThemeMode", cycle >= 0);
        assertTrue(sourcePath + " is missing styleExternalLinkRow", style >= 0);
        assertTrue("theme toggle must restyle dynamic rows without rebuilding the whole detail section",
                refresh > apply);
        assertTrue("new external link rows must share the same styling path as theme refreshes",
                methodBody.contains("styleExternalLinkRow(row, colors);"));
        assertTrue("external link rows must repaint their chrome when remote focus changes",
                methodBody.contains("row.setOnFocusChangeListener((view, focused) -> styleExternalLinkRow(view, currentThemeColors()));"));
        assertTrue("direct detail external link labels must use resolved theme text color",
                styleBody.contains("label.setTextColor(colors.primary)"));
        assertTrue("direct detail external link icons must use resolved theme icon color",
                styleBody.contains("icon.setColorFilter(colors.secondary)"));
        assertTrue("focused direct detail external links must use the shared yellow focus stroke",
                styleBody.contains("boolean focused = row.hasFocus();")
                        && styleBody.contains("background.setStroke(ResUtil.dp2px(focused ? FOCUS_STROKE_DP : CHIP_STROKE_DP), focused ? FOCUS_STROKE : colors.line);"));
    }

    @Test
    public void mobileFusionBackdropFillsBehindTopChrome() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void applyFusionBackdropLayout()");

        assertTrue(sourcePath + " is missing applyFusionBackdropLayout", method >= 0);
        assertTrue("Fusion backdrop must clear below-video anchoring and align to the top of the root",
                source.indexOf("wallParams.addRule(RelativeLayout.ALIGN_PARENT_TOP)", method) > method);
        assertTrue("Fusion backdrop must keep covering the bottom of the root",
                source.indexOf("wallParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)", method) > method);
        assertTrue("Fusion status bar spacer must be transparent so the backdrop reaches the status icons",
                source.indexOf("mBinding.statusBar.setBackgroundColor(Color.TRANSPARENT)", method) > method);
        assertTrue("Fusion context wall scrim must follow the same full-screen layout as the backdrop",
                source.indexOf("mBinding.videoContextScrim.setLayoutParams(", method) > method);
        assertTrue("Fusion context wall scrim must remain visible over the full-screen artwork",
                source.indexOf("mBinding.videoContextScrim.setVisibility(View.VISIBLE)", method) > method);
    }

    @Test
    public void mobileHistoryEntryRespectsDetailModeAndPreservesExactPlaybackSelection() throws Exception {
        Path historyPath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "HistoryActivity.java"));
        Path videoPath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String history = new String(Files.readAllBytes(historyPath), StandardCharsets.UTF_8);
        String video = new String(Files.readAllBytes(videoPath), StandardCharsets.UTF_8);
        String compactHistory = history.replaceAll("\\s+", " ");
        int historyStart = video.indexOf("static void startFromHistory(Activity activity, History item)");
        int historyStartEnd = video.indexOf("public static void startDirect(", historyStart);
        String historyStartBody = historyStart >= 0 && historyStartEnd > historyStart ? video.substring(historyStart, historyStartEnd).replaceAll("\\s+", " ") : "";

        assertTrue("history clicks must use the history-aware playback entry point",
                compactHistory.contains("HistoryResumeCoordinator.open(this, item)"));
        assertTrue("history playback must respect the configured standalone detail mode",
                historyStartBody.contains("if (shouldOpenLegacyTmdbDetail(item.getSiteKey(), item.getVodId()))"));
        assertTrue("standalone detail mode must use the normal detail-aware start path",
                historyStartBody.contains("TmdbDetailActivity.startFromHistory(activity, item)"));
        assertTrue("non-detail playback must preserve flag, episode title, and episode url",
                historyStartBody.contains("startDirect(activity, item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic(), item.getVodRemarks(), item.getVodFlag(), item.getVodRemarks(), item.getEpisodeUrl(), item)"));
        assertTrue("direct playback must preserve the requested episode selection in the intent",
                video.contains("putIntentPlaybackSelection(intent, playFlag, playFlagKey, playEpisodeName, playEpisodeUrl);"));
    }

    @Test
    public void leanbackHistoryEntryRespectsDetailModeAndPreservesExactPlaybackSelection() throws Exception {
        Path historyPath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "HistoryActivity.java"));
        Path videoPath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String history = new String(Files.readAllBytes(historyPath), StandardCharsets.UTF_8);
        String video = new String(Files.readAllBytes(videoPath), StandardCharsets.UTF_8);
        String compactHistory = history.replaceAll("\\s+", " ");
        int historyStart = video.indexOf("static void startFromHistory(Activity activity, History item)");
        int historyStartEnd = video.indexOf("public static void startDirect(", historyStart);
        String historyStartBody = historyStart >= 0 && historyStartEnd > historyStart ? video.substring(historyStart, historyStartEnd).replaceAll("\\s+", " ") : "";

        assertTrue("TV history clicks must use the history-aware playback entry point",
                compactHistory.contains("HistoryResumeCoordinator.open(this, item)"));
        assertTrue("TV history playback must respect the configured standalone detail mode",
                historyStartBody.contains("if (shouldOpenLegacyTmdbDetail(item.getSiteKey(), item.getVodId(), false))"));
        assertTrue("TV standalone detail mode must use the normal detail-aware start path",
                historyStartBody.contains("TmdbDetailActivity.startFromHistory(activity, item)"));
        assertTrue("TV non-detail playback must preserve flag, episode title, and episode url",
                historyStartBody.contains("startDirect(activity, item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic(), item.getVodRemarks(), item.getVodFlag(), item.getVodRemarks(), item.getEpisodeUrl(), item)"));
        assertTrue("TV direct playback must preserve the requested episode selection in the intent",
                video.contains("putIntentPlaybackSelection(intent, playFlag, playFlagKey, playEpisodeName, playEpisodeUrl);"));
    }

    @Test
    public void bothPlaybackModesShareCanonicalEpisodeProgressWhenTmdbAggregationIsEnabled() throws Exception {
        for (Path root : List.of(findLeanbackJavaPath(), findMobileJavaPath())) {
            Path sourcePath = root.resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
            String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
            String selection = methodBody(source, "private void applyIntentPlaybackSelection(Vod item)", "private Flag findIntentPlaybackFlag");
            String update = methodBody(source, "private void updateHistory(Episode item)", "private void checkKeepImg()");

            assertTrue(sourcePath + " must mark history launches as resume requests",
                    source.contains("intent.putExtra(EXTRA_RESUME_FROM_HISTORY, resumeFromHistory);"));
            assertTrue(sourcePath + " must clear a stale history marker when singleTop handles another launch",
                    source.contains("getIntent().removeExtra(EXTRA_RESUME_FROM_HISTORY);"));
            assertTrue(sourcePath + " must clear stale explicit playback selection before merging another launch",
                    source.contains("getIntent().removeExtra(EXTRA_TMDB_PLAY_FLAG);")
                            && source.contains("getIntent().removeExtra(EXTRA_TMDB_PLAY_EPISODE_NAME);")
                            && source.contains("getIntent().removeExtra(EXTRA_TMDB_PLAY_EPISODE_URL);")
                            && source.contains("getIntent().removeExtra(EXTRA_TMDB_PLAY_SEASON_NUMBER);")
                            && source.contains("getIntent().removeExtra(EXTRA_TMDB_PLAY_EPISODE_NUMBER);"));
            assertTrue(sourcePath + " must share canonical episode progress when TMDB history aggregation is enabled",
                    selection.replaceAll("\\s+", " ").contains("boolean shareEpisodeProgress = crossSource || isResumeFromHistory() || Setting.isHistoryAggregationEffective();"));
            assertTrue(sourcePath + " must keep the original episode identity when aggregation and history resume are disabled",
                    selection.replaceAll("\\s+", " ").contains("shareEpisodeProgress ? historyEpisode.matchesPlayback(mHistory.getEpisode()) : episode.matches(mHistory.getEpisode())"));
            assertTrue(sourcePath + " must ignore source-line differences when shared progress is enabled",
                    selection.contains("boolean compatibleFlag = shareEpisodeProgress || TextUtils.equals(mHistory.getVodFlag(), flag.getFlag());"));
            assertTrue(sourcePath + " must preserve progress when a history source refresh changes only the episode URL",
                    selection.contains("historyEpisode.matchesPlayback(mHistory.getEpisode())"));
            assertTrue(sourcePath + " must use the same tolerant episode identity when playback updates history",
                    update.contains("historyEpisode.matchesPlayback(mHistory.getEpisode())"));
            if (source.contains("private void updateFastTmdbPlaybackHistory(Flag flag, Episode episode)")) {
                String fast = methodBody(source, "private void updateFastTmdbPlaybackHistory(Flag flag, Episode episode)", "private void resetDetailForNewIntent()");
                assertTrue(sourcePath + " fast TMDB playback must honor the aggregation progress-sharing switch",
                        fast.replaceAll("\\s+", " ").contains("boolean shareEpisodeProgress = crossSource || isResumeFromHistory() || Setting.isHistoryAggregationEffective();"));
                assertTrue(sourcePath + " fast TMDB playback must share progress across source lines when enabled",
                        fast.contains("boolean compatibleFlag = shareEpisodeProgress || TextUtils.equals(mHistory.getVodFlag(), flag.getFlag());"));
            }
        }
    }

    @Test
    public void mobileSaveHistoryKeepsRecordWithoutMergeDeletingSource() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int method = source.indexOf("private void saveHistory(boolean exit)");
        int end = source.indexOf("private void syncHistory()", method);
        String body = method >= 0 && end > method ? source.substring(method, end) : "";

        assertTrue(sourcePath + " is missing saveHistory(boolean)", method >= 0);
        assertTrue("saveHistory must treat an attached non-empty owner player as played content",
                body.contains("boolean hasPlayback = service() != null && isOwner() && !player().isEmpty();"));
        assertTrue("saveHistory must persist played content even before progress advances past zero",
                body.contains("if (!mHistory.canSave() && !hasPlayback) return;"));
        assertFalse("mobile playback save must not merge-delete existing history entries",
                body.contains("history.merge().save()"));
    }

    private static void assertNoPrematurePlaybackReveal(Path sourcePath, String source) {
        int playing = source.indexOf("protected void onPlayingChanged(boolean isPlaying)");
        int playingEnd = source.indexOf("protected void onSizeChanged", playing);
        String playingBody = playing >= 0 && playingEnd > playing ? source.substring(playing, playingEnd) : "";
        int time = source.indexOf("public void onTimeChanged(long time)");
        int timeEnd = source.indexOf("private void updatePlaybackHistoryPosition()", time);
        String timeBody = time >= 0 && timeEnd > time ? source.substring(time, timeEnd) : "";

        assertTrue(sourcePath + " is missing onPlayingChanged", playing >= 0);
        assertTrue(sourcePath + " is missing onTimeChanged", time >= 0);
        assertFalse("playing changes can arrive before first frame and must not hide loading", playingBody.contains("showPlaybackContent();"));
        assertFalse("time ticks can observe old playback and must not hide loading", timeBody.contains("showPlaybackContent();"));
    }

    private static void assertSeekProgressFallback(Path sourcePath, String source) {
        int field = source.indexOf("private Runnable mSeekProgressFallback;");
        int init = source.indexOf("mSeekProgressFallback = this::hideSeekProgressIfReady;");
        int started = source.indexOf("protected void onSeekStarted()");
        int show = source.indexOf("showProgress();", started);
        int remove = source.indexOf("App.removeCallbacks(mSeekProgressFallback);", show);
        int post = source.indexOf("App.post(mSeekProgressFallback, 500);", remove);
        int helper = source.indexOf("private void hideSeekProgressIfReady()");
        int readyGuard = source.indexOf("player().getPlaybackState() != Player.STATE_READY", helper);
        int reveal = source.indexOf("showPlaybackContent();", readyGuard);
        int destroy = source.indexOf("protected void onDestroy()");
        int destroyRemove = source.indexOf("mSeekProgressFallback", destroy);

        assertTrue(sourcePath + " is missing mSeekProgressFallback", field >= 0);
        assertTrue("seek fallback runnable must be initialized", init > field);
        assertTrue("seek must show loading before scheduling the READY fallback", show > started && remove > show && post > remove);
        assertTrue("seek fallback must only clear loading once playback is READY", readyGuard > helper && reveal > readyGuard);
        assertTrue("seek fallback callback must be removed on destroy", destroyRemove > destroy);
    }

    @Test
    public void duplicateNamedFlagsPreferExactEpisodeUrlInBothPlayers() throws Exception {
        for (Path root : List.of(findMobileJavaPath(), findLeanbackJavaPath())) {
            Path sourcePath = root.resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
            String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
            String body = methodBody(source, "private Flag findIntentPlaybackFlag", "private Episode findIntentPlaybackEpisode");

            assertTrue(sourcePath + " must delegate duplicate-line selection with the stable key and exact episode URL",
                    body.contains("TmdbUIAdapter.selectPlaybackFlag(")
                            && body.contains("flags, playFlagKey, playUrl, playFlag"));
        }
    }

    @Test
    public void duplicateNamedFlagsUseIdentitySelectionInBothAdapters() throws Exception {
        for (Path root : List.of(findMobileJavaPath(), findLeanbackJavaPath())) {
            Path sourcePath = root.resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "FlagAdapter.java"));
            String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
            String index = methodBody(source, "public int indexOf(Flag", "public int getPosition()");
            String selected = methodBody(source, "public void setSelected(Flag", "public void toggle(Episode");

            assertTrue(sourcePath + " must find the exact duplicate flag object before the legacy name fallback",
                    index.contains("mItems.get(i) ==")
                            && index.indexOf("mItems.get(i) ==") < index.indexOf("mItems.indexOf("));
            assertTrue(sourcePath + " must select exactly one resolved flag position",
                    selected.contains("setSelected(i == position)"));
            assertFalse(sourcePath + " must not delegate duplicate selection to Flag.equals",
                    selected.contains("setSelected(item)") || selected.contains("setSelected(flag)"));

            if (root.equals(findLeanbackJavaPath())) {
                Path videoPath = root.resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
                String video = new String(Files.readAllBytes(videoPath), StandardCharsets.UTF_8);
                String fastSelection = methodBody(video,
                        "private void selectFastTmdbPlaybackEpisode(Vod item, Flag selectedFlag, Episode selectedEpisode)",
                        "private void updateFastTmdbPlaybackHistory(Flag flag, Episode episode)");
                assertTrue("leanback fast TMDB playback must select only the exact duplicate Flag object",
                        fastSelection.contains("flag == selectedFlag"));
                assertFalse("leanback fast TMDB playback must not select duplicate Flags by display name",
                        fastSelection.contains("TextUtils.equals(flag.getFlag(), selectedFlag.getFlag())"));
            }
        }
    }

    @Test
    public void bothPlayersPersistStableFlagKeyWithHistory() throws Exception {
        for (Path root : List.of(findMobileJavaPath(), findLeanbackJavaPath())) {
            Path sourcePath = root.resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
            String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
            String body = methodBody(source, "private void setHistoryFlag", "private Episode getEpisode()");

            assertTrue(sourcePath + " must persist the identity-first adapter index with quarterly progress",
                    body.contains("mFlagAdapter.indexOf(flag)")
                            && body.contains("TmdbUIAdapter.flagKey(flag, index)")
                            && body.contains("mHistory.setSourceBindingKey(flagKey)"));
            assertTrue(sourcePath + " must recover the stable index before the TMDB adapter is bound",
                    body.contains("TmdbUIAdapter.flagIndex(mVod.getFlags(), flag)"));
        }
    }

    @Test
    public void bothPlayersResolveHistoryToActualFlagBeforeLoadingEpisodes() throws Exception {
        for (Path root : List.of(findMobileJavaPath(), findLeanbackJavaPath())) {
            Path sourcePath = root.resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
            String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
            String resolve = methodBody(source, "private Flag resolveHistoryPlaybackFlag", "private Episode getEpisode()");
            String click = methodBody(source, "public void onItemClick(Flag item)", "@Override");

            assertTrue(sourcePath + " must resolve current-list flags with stable key, exact URL, and legacy name",
                    resolve.contains("TmdbUIAdapter.selectPlaybackFlag(")
                            && resolve.contains("flags, flagKey, episodeUrl, flagName"));
            assertTrue(sourcePath + " must load episodes and TMDB state from the resolved adapter object",
                    click.contains("Flag resolved = mFlagAdapter.get(")
                            && click.contains("mTmdbUIAdapter.setActiveFlag(resolved)")
                            && click.contains("resolved.getEpisodes()"));
        }
    }

    @Test
    public void bothPlayersInitializeEpisodesAndPlaybackFromPreselectedCachedFlags() throws Exception {
        for (Path root : List.of(findMobileJavaPath(), findLeanbackJavaPath())) {
            Path sourcePath = root.resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
            String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
            String click = methodBody(source, "public void onItemClick(Flag item)", "@Override");

            assertFalse(sourcePath + " must not treat a cached selected flag as an initialized page",
                    click.contains("if (resolved.isSelected()) return;"));
            String initialBinding = root.equals(findMobileJavaPath())
                    ? "boolean initialBinding = mEpisodeAdapter == null || mEpisodeAdapter.isEmpty();"
                    : "boolean initialBinding = mEpisodeAdapter.getItemCount() == 0;";
            assertTrue(sourcePath + " must distinguish an empty episode adapter from a user re-click",
                    click.contains(initialBinding)
                            && click.contains("if (resolved.isSelected() && !initialBinding) return;"));
            assertTrue(sourcePath + " must start the inherited selected episode after rebuilding its list",
                    click.contains("if (initialBinding && !episodeChanged) onRefresh();"));
        }
    }

    private static Path findMobileResPath() {
        Path moduleRelative = Path.of("src", "mobile", "res");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "mobile", "res");
    }

    @Test
    public void mobileVideoLayoutTreeHasNoConflictingDuplicateIds() throws Exception {
        // 崩溃回归：activity_video.xml 与它 include 的布局属于同一棵视图树，ViewBinding.bind()
        // 用 findChildViewById 按 id 查找并强转成声明处的类型。同名 id 指向不同控件类型时，
        // 先命中的那个会被错误强转 —— 曾因新增 @id/quality(ImageView) 撞上详情页
        // @id/quality(RecyclerView) 导致 VideoActivity 启动即 ClassCastException。
        // 判定要贴合 findChildViewById 的真实语义：它返回「深度优先的第一个命中」。
        // 所以同名 id 本身不危险 —— 危险的是某个 binding 根布局直接声明的 id，在它自己的
        // inflate 顺序里被更早出现的 include 子树抢先命中且类型不同。
        // 既有的 @id/video、@id/karaoke 声明在 include 之前，先命中的就是自己，因此安全；
        // @id/prev、@id/next 分属两个各自独立的 binding 根，互不影响。
        // 必须覆盖 layout 的所有配置变体：ViewBinding 为它们生成同一个 binding 类，
        // bind() 在横屏/平板上跑的是另一份 XML，只扫 layout/ 会让变体里的撞名 id 完全没有防护。
        int checked = 0;
        for (Path variantDir : collectLayoutVariantDirs()) {
            for (Path layout : collectVideoLayoutTree(variantDir)) {
                List<String[]> flattened = flattenInflateOrder(layout, variantDir, new HashSet<>());
                Map<String, String> firstType = new HashMap<>();
                for (String[] entry : flattened) firstType.putIfAbsent(entry[0], entry[1]);
                for (String[] declared : collectDirectIdTypes(layout, variantDir)) {
                    assertEquals("@id/" + declared[0] + " declared in " + variantDir.getFileName() + "/" + layout.getFileName()
                                    + " is shadowed by an earlier include with a different view type; "
                                    + "ViewBinding.bind() will throw ClassCastException",
                            declared[1], firstType.get(declared[0]));
                }
                checked++;
            }
        }
        // 四个 activity_video 变体各自展开出多个布局，远多于 4；防止解析静默失效后测试空转
        assertTrue("layout variants must all be traversed, only checked " + checked, checked > 12);
    }

    /** 所有含 activity_video.xml 的 layout 配置变体目录（layout / layout-land / layout-sw600dp / ...）。 */
    private static List<Path> collectLayoutVariantDirs() throws Exception {
        List<Path> dirs;
        try (var stream = Files.list(findMobileResPath())) {
            dirs = stream.filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith("layout"))
                    .filter(path -> Files.exists(path.resolve("activity_video.xml")))
                    .sorted()
                    .collect(Collectors.toList());
        }
        assertFalse("no mobile layout variants with activity_video.xml found", dirs.isEmpty());
        return dirs;
    }

    /** 按 inflate 顺序展开 include，返回 (id, 标签名) 序列，模拟 findChildViewById 的深度优先查找。 */
    private static List<String[]> flattenInflateOrder(Path layout, Path variantDir, Set<String> guard) throws Exception {
        List<String[]> flattened = new ArrayList<>();
        if (layout == null || !guard.add(layout.toString())) return flattened;
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        NodeList nodes = factory.newDocumentBuilder().parse(layout.toFile()).getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            if (element.getTagName().equals("include")) {
                // include 自身的 android:id 会覆盖被包含布局的根 id，ViewBinding 也会为它生成字段，
                // 所以要按 inflate 顺序先记下它，再展开子树。
                // 类型必须取被包含布局的「根元素」：ViewBinding 生成的是该布局的 binding 类，
                // 其 rootView 就是根元素类型（如 ViewControlVodBinding.rootView 是 RelativeLayout）。
                // 不能用子树第一个带 id 的元素——根元素往往自己没有 id（view_progress 的根 FrameLayout
                // 无 id），那样会错记成它的某个子控件类型。
                String includeId = localId(element);
                String included = element.getAttribute("layout");
                int slash = included.indexOf('/');
                Path target = slash >= 0 ? resolveLayout(included.substring(slash + 1), variantDir) : null;
                if (includeId != null) {
                    String rootType = rootTagOf(target);
                    assertTrue("cannot resolve root element type of " + included, rootType != null);
                    flattened.add(new String[]{includeId, rootType});
                }
                flattened.addAll(flattenInflateOrder(target, variantDir, guard));
                continue;
            }
            String id = localId(element);
            if (id != null) flattened.add(new String[]{id, element.getTagName()});
        }
        return flattened;
    }

    /**
     * 只取本布局文件直接声明的 id 及其控件类型，即 ViewBinding 会为该布局生成的字段。
     * <p>
     * include 的 id 也要算进来：ViewBinding 同样为它生成字段，类型是被包含布局的根元素
     * （bind() 内部会走 XxxBinding.bind(view) 把它强转成根元素类型）。漏掉它就意味着
     * 「撞名方是 include 而非普通控件」的方向完全没有防护 —— 与曾导致启动崩溃的
     * @id/quality 事故只差一个方向。
     */
    private static List<String[]> collectDirectIdTypes(Path layout, Path variantDir) throws Exception {
        List<String[]> declared = new ArrayList<>();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        NodeList nodes = factory.newDocumentBuilder().parse(layout.toFile()).getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            String id = localId(element);
            if (id == null) continue;
            if (element.getTagName().equals("include")) {
                String included = element.getAttribute("layout");
                int slash = included.indexOf('/');
                String rootType = rootTagOf(slash >= 0 ? resolveLayout(included.substring(slash + 1), variantDir) : null);
                assertTrue("cannot resolve root element type of " + included, rootType != null);
                declared.add(new String[]{id, rootType});
                continue;
            }
            declared.add(new String[]{id, element.getTagName()});
        }
        return declared;
    }

    private static String localId(Element element) {
        String raw = element.getAttribute("android:id");
        int slash = raw.indexOf('/');
        return slash >= 0 && slash + 1 < raw.length() ? raw.substring(slash + 1) : null;
    }

    /** 布局文件根元素的标签名，即 ViewBinding 为它生成的 binding 类 rootView 的类型。 */
    private static String rootTagOf(Path layout) throws Exception {
        if (layout == null) return null;
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        return factory.newDocumentBuilder().parse(layout.toFile()).getDocumentElement().getTagName();
    }

    private static List<Path> collectVideoLayoutTree(Path variantDir) throws Exception {
        List<Path> layouts = new ArrayList<>();
        Deque<String> pending = new ArrayDeque<>(List.of("activity_video"));
        Set<String> seen = new HashSet<>();
        while (!pending.isEmpty()) {
            String name = pending.poll();
            if (!seen.add(name)) continue;
            Path layout = resolveLayout(name, variantDir);
            assertTrue("include target @layout/" + name + " referenced from " + variantDir.getFileName()
                    + " must resolve", layout != null);
            layouts.add(layout);
            String source = new String(Files.readAllBytes(layout), StandardCharsets.UTF_8)
                    .replaceAll("(?s)<!--.*?-->", "");
            Matcher matcher = Pattern.compile("layout=\"@layout/([A-Za-z0-9_]+)\"").matcher(source);
            while (matcher.find()) pending.add(matcher.group(1));
        }
        assertTrue("activity_video include tree must resolve", layouts.size() > 3);
        return layouts;
    }

    /**
     * 按 aapt 的配置匹配顺序解析布局：先找同配置变体目录，再退回 layout/，最后到 main flavor。
     * 例如横屏下 activity_video 用 layout-land/ 的版本，而它 include 的 view_control_vod
     * 只有 layout/ 一份，就退回 layout/。
     */
    private static Path resolveLayout(String name, Path variantDir) {
        List<Path> candidates = new ArrayList<>();
        candidates.add(variantDir.resolve(name + ".xml"));
        candidates.add(findMobileResPath().resolve(Path.of("layout", name + ".xml")));
        candidates.add(findMainResPath().resolve(Path.of("layout", name + ".xml")));
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) return candidate;
        }
        return null;
    }

    @Test
    public void leanbackTmdbPhotoViewerStartsAtClickedArtwork() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String photoClick = methodBody(source, "private void onTmdbPhotoClick(String url, int position)", "private void onTmdbPosterClick");
        String posterClick = methodBody(source, "private void onTmdbPosterClick(String url, int position)", "private void onTmdbRecommendationClick");

        assertTrue("leanback photo viewer must resolve the clicked backdrop URL instead of always opening index zero",
                photoClick.contains("Math.max(0, photos.indexOf(url))"));
        assertTrue("leanback photo viewer must resolve the clicked poster URL instead of always opening index zero",
                posterClick.contains("Math.max(0, posters.indexOf(url))"));
    }

    @Test
    public void leanbackTmdbArtworkSeparatesPostersAndUsesResponsiveBackdropSlides() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        Path presenterPath = findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "presenter", "TmdbPhotoPresenter.java"));
        Path layoutPath = findLeanbackResPath().resolve(Path.of("layout", "activity_video.xml"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String presenter = new String(Files.readAllBytes(presenterPath), StandardCharsets.UTF_8);
        String layout = new String(Files.readAllBytes(layoutPath), StandardCharsets.UTF_8);

        assertTrue("leanback detail must render the poster row below the backdrop row",
                layout.contains("android:id=\"@+id/tmdbPostersLabel\"")
                        && layout.contains("android:id=\"@+id/tmdbPosters\"")
                        && layout.indexOf("@+id/tmdbPostersLabel") > layout.indexOf("@+id/tmdbPhotos"));
        assertTrue("leanback detail must bind backdrops and posters separately while slides stay responsive",
                source.contains("mTmdbUIAdapter.getPhotos()")
                        && source.contains("mTmdbUIAdapter.getPosters()")
                        && source.contains("setupBackdropSlideshow(mTmdbUIAdapter.getBackgroundPhotos())"));
        assertTrue("leanback poster cards must keep portrait dimensions",
                presenter.contains("params.width = ResUtil.dp2px(148);")
                        && presenter.contains("params.height = ResUtil.dp2px(222);"));
    }


    private static Path findLeanbackResPath() {
        Path moduleRelative = Path.of("src", "leanback", "res");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "leanback", "res");
    }

    private static Path findMobileJavaPath() {
        Path moduleRelative = Path.of("src", "mobile", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "mobile", "java");
    }

    private static Path findMainJavaPath() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "java");
    }

    private static Path findMainResPath() {
        Path moduleRelative = Path.of("src", "main", "res");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "res");
    }

    private static Path findLeanbackJavaPath() {
        Path moduleRelative = Path.of("src", "leanback", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "leanback", "java");
    }

    private static String methodBody(String source, String startToken, String endToken) {
        int start = source.indexOf(startToken);
        int end = source.indexOf(endToken, start + startToken.length());
        assertTrue("Missing source token: " + startToken, start >= 0);
        assertTrue("Missing source token after " + startToken + ": " + endToken, end > start);
        return source.substring(start, end);
    }

    private static int occurrences(String source, String token) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }

    private static Set<String> collectAndroidIds(File file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        Set<String> ids = new HashSet<>();
        NodeList nodes = factory.newDocumentBuilder().parse(file).getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            String id = element.getAttribute("android:id");
            int slash = id.indexOf('/');
            if (slash >= 0 && slash + 1 < id.length()) ids.add(id.substring(slash + 1));
        }
        return ids;
    }

    private static Element findAndroidId(File file, String value) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        NodeList nodes = factory.newDocumentBuilder().parse(file).getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            String id = element.getAttribute("android:id");
            if (id.endsWith("/" + value)) return element;
        }
        return null;
    }

    private static boolean hasAncestorAndroidId(Element element, String value) {
        if (element == null) return false;
        Node current = element.getParentNode();
        while (current instanceof Element) {
            String id = ((Element) current).getAttribute("android:id");
            if (id.endsWith("/" + value)) return true;
            current = current.getParentNode();
        }
        return false;
    }

    private static boolean hasDescendantAndroidText(Element element, String value) {
        if (element == null) return false;
        NodeList nodes = element.getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element child = (Element) nodes.item(i);
            if (value.equals(child.getAttribute("android:text"))) return true;
        }
        return false;
    }

    private static boolean isAndroidIdBefore(Path file, String firstId, String secondId) throws Exception {
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        int first = source.indexOf("android:id=\"@+id/" + firstId + "\"");
        int second = source.indexOf("android:id=\"@+id/" + secondId + "\"");
        return first >= 0 && second >= 0 && first < second;
    }

    /**
     * 原生选集列表模式保持单行横向滚动。
     *
     * 「按钮内文本居中」的诉求由 adapter_episode_hori.xml 的 gravity=center 加左右成对内边距
     * 满足，不是把整条列表改成换行居中的 Flexbox —— 那会让列表模式看起来像网格模式，
     * 两种视图模式失去区分。
     */
    @Test
    public void mobileEpisodeListModeStaysHorizontalSingleRow() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);

        int method = source.indexOf("private void updateEpisodeLayout(List<Episode> items, boolean useTmdbCard)");
        assertTrue(sourcePath + " is missing updateEpisodeLayout", method >= 0);
        int gridBranch = source.indexOf("int span = getEpisodeSpan(items, useTmdbCard);", method);
        String listBranch = source.substring(method, gridBranch);

        assertTrue("list mode must use a horizontal LinearLayoutManager",
                listBranch.contains("new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)"));
        assertFalse("list mode must not wrap items with a Flexbox layout manager",
                listBranch.contains("FlexboxLayoutManager"));
        assertFalse(sourcePath + " must not import FlexboxLayoutManager for the episode list",
                source.contains("import com.google.android.flexbox.FlexboxLayoutManager;"));
    }

    /**
     * 原生选集按钮的文本靠左右成对内边距居中。
     *
     * shape_video_item 自带 12dp 左右内边距，setPadding 只覆盖左边会把右边一起清成 0，
     * 文本随之偏向一侧。徽标可见时左边额外让出 92dp 供徽标占位。
     */
    @Test
    public void mobileEpisodeHoriTextKeepsSymmetricHorizontalPadding() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "holder", "EpisodeHoriHolder.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);

        assertTrue(sourcePath + " must resolve a shared horizontal padding",
                source.contains("int horizontal = ResUtil.dp2px(12);"));
        assertTrue(sourcePath + " must apply the same padding on both sides",
                source.contains("binding.text.setPadding(visible ? ResUtil.dp2px(92) : horizontal, binding.text.getPaddingTop(), horizontal, binding.text.getPaddingBottom());"));

        Path layout = findMobileResPath().resolve(Path.of("layout", "adapter_episode_hori.xml"));
        String xml = new String(Files.readAllBytes(layout), StandardCharsets.UTF_8);
        int text = xml.indexOf("android:id=\"@+id/text\"");
        int card = xml.indexOf("android:id=\"@+id/card\"");
        assertTrue(layout + " is missing @+id/text", text >= 0);
        assertTrue("the episode button text must be centered inside the button",
                xml.indexOf("android:gravity=\"center\"", text) > text
                        && xml.indexOf("android:gravity=\"center\"", text) < card);
    }
}
