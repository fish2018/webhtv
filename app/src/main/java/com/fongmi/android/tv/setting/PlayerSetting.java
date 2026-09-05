package com.fongmi.android.tv.setting;

import android.content.Intent;
import android.provider.Settings;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.player.VideoAspectMode;
import com.fongmi.android.tv.utils.BrightnessPolicy;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.utils.Prefers;

public class PlayerSetting {

    public static final int EXO = 0;
    public static final int IJK = 1;
    public static final int SYSTEM = 2;
    public static final int MPV = 3;
    public static final int NONE = -1;
    /**
     * 内核优先级顺序：EXO → IJK → MPV → 系统。下标是位次，元素是内核常量。
     * 常量数值本身是持久化值（也是 select_player_kernel 等数组的下标），不能重排，
     * 所以顺序只在这张表里表达：选择菜单按它排列，播放失败的内核回退也按它推进。
     */
    public static final int[] KERNEL_ORDER = {EXO, IJK, MPV, SYSTEM};
    public static final int RENDER_SURFACE = 0;
    public static final int RENDER_TEXTURE = 1;
    public static final int FFMPEG_MODE_NEXTLIB = 0;
    public static final int FFMPEG_MODE_OFFICIAL = 1;
    public static final int FFMPEG_MODE_SIMPLE = 2;
    public static final int FFMPEG_MODE_AUTO = 3;
    public static final int MPV_RENDER_OPENGL = 0;
    public static final int MPV_RENDER_VULKAN = 1;
    public static final int AUDIO_BACKGROUND_ARTWORK = 0;
    public static final int AUDIO_BACKGROUND_DARK_NEON = 1;
    public static final int AUDIO_BACKGROUND_BLACK_GOLD = 2;
    public static final int AUDIO_BACKGROUND_SUNSET = 3;
    public static final int AUDIO_BACKGROUND_MINT = 4;
    public static final int AUDIO_BACKGROUND_CANDY = 5;
    public static final int AUDIO_BACKGROUND_SKY = 6;
    public static final int AUDIO_BACKGROUND_ROSE = 7;
    public static final int AUDIO_BACKGROUND_CYBER = 8;
    public static final int AUDIO_BACKGROUND_FOREST = 9;
    public static final int AUDIO_BACKGROUND_LEMON = 10;
    public static final int AUDIO_BACKGROUND_DUSK = 11;
    public static final int AUDIO_BACKGROUND_RANDOM = 12;
    public static final int AUDIO_PLAYBACK_STYLE_BUILT_IN = 0;
    public static final int AUDIO_PLAYBACK_STYLE_IMMERSIVE = 1;
    public static final int PAD_LIVE_FULLSCREEN = 0;
    public static final int PAD_LIVE_STANDARD = 1;
    public static final int FALLBACK_FULL = 0;
    public static final int FALLBACK_DECODE_ONLY = 1;
    public static final int FALLBACK_PLAYER_ONLY = 2;
    public static final int FALLBACK_DISABLED = 3;
    public static final int NIGHT_MODE_OFF = 0;
    public static final int NIGHT_MODE_LOW = 1;
    public static final int NIGHT_MODE_MEDIUM = 2;
    public static final int NIGHT_MODE_HIGH = 3;
    public static final int NIGHT_MODE_AUTO = 0;
    public static final int NIGHT_MODE_ALWAYS_OFF = 1;
    public static final int NIGHT_MODE_ALWAYS_ON = 2;
    private static final int DEFAULT_PLAY_CACHE_OPTION = 0;
    private static final String KEY_IMMERSIVE_AUDIO_PLAYBACK = "immersive_audio_playback";
    private static final String KEY_FAILURE_FALLBACK = "player_failure_fallback";
    private static final String KEY_FFMPEG_MODE = "ffmpeg_mode";
    private static final String KEY_CUSTOM_ASPECT_WIDTH = "custom_aspect_width";
    private static final String KEY_CUSTOM_ASPECT_HEIGHT = "custom_aspect_height";
    private static final String KEY_BRIGHTNESS_MIGRATED = "player_brightness_migrated";
    private static final String KEY_REMEMBER_BRIGHTNESS = "player_remember_brightness";
    private static final String KEY_DISPLAY_TIME = "display_time";
    private static final String KEY_DISPLAY_TRAFFIC = "display_traffic";
    private static final String KEY_DISPLAY_SIZE = "display_size";
    private static final String KEY_DISPLAY_PROGRESS = "display_progress";
    private static final String KEY_DISPLAY_MINI = "display_mini";
    private static final String KEY_DISPLAY_TITLE = "display_title";
    private static final String KEY_OSD_TITLE = "player_osd_title";
    private static final String KEY_OSD_RESOLUTION = "player_osd_resolution";
    private static final String KEY_OSD_TIME = "player_osd_time";
    private static final String KEY_OSD_PROGRESS = "player_osd_progress";
    private static final String KEY_OSD_TRAFFIC = "player_osd_traffic";
    private static final String KEY_OSD_MINI = "player_osd_mini";
    private static boolean legacyOsdMigrated;
    private static boolean legacyBrightnessMigrated;
    /**
     * 当前播放会话正在使用的内核（NONE 表示没有会话，按全局默认）。
     * 只存在于内存：进程内所有「现在用的是哪个内核」的判断都读它，退出播放后清空。
     * 由 PlayerManager 在建引擎/切引擎时写入，volatile 以便工作线程（取播放地址）读到最新值。
     */
    private static volatile int activePlayer = NONE;

    public static int getPlayer() {
        int player = Prefers.getInt("player", EXO);
        if (isPlayer(player)) return player;
        putPlayer(EXO);
        return EXO;
    }

    public static void putPlayer(int player) {
        Prefers.put("player", sanitizePlayer(player));
    }

    /**
     * 当前实际在跑的内核，与全局默认内核解耦。
     * 播放器里切内核只影响本次播放（并由 History 按剧集记住），不再回写全局默认，
     * 因此运行期凡是问「现在用的是哪个内核」的地方都要读这里，而不是 getPlayer()；
     * getPlayer() 只代表设置页里的全局默认值，也是没有播放会话时的兜底。
     */
    public static int getActivePlayer() {
        int player = activePlayer;
        return isPlayer(player) ? player : getPlayer();
    }

    public static void putActivePlayer(int player) {
        activePlayer = isPlayer(player) ? player : NONE;
    }

    public static void clearActivePlayer() {
        activePlayer = NONE;
    }

    public static boolean isImmersiveAudioMode() {
        return Prefers.getBoolean("immersive_audio_mode", true);
    }

    public static void putImmersiveAudioMode(boolean enabled) {
        Prefers.put("immersive_audio_mode", enabled);
    }

    public static boolean isImmersiveAudioPlayback(String playbackKey) {
        return playbackKey != null && !playbackKey.isEmpty() && playbackKey.equals(Prefers.getString(KEY_IMMERSIVE_AUDIO_PLAYBACK));
    }

    public static void putImmersiveAudioPlayback(String playbackKey) {
        Prefers.put(KEY_IMMERSIVE_AUDIO_PLAYBACK, playbackKey == null ? "" : playbackKey);
    }

    public static int getAudioPlaybackStyle() {
        return isImmersiveAudioMode() ? AUDIO_PLAYBACK_STYLE_IMMERSIVE : AUDIO_PLAYBACK_STYLE_BUILT_IN;
    }

    public static void putAudioPlaybackStyle(int style) {
        putImmersiveAudioMode(style != AUDIO_PLAYBACK_STYLE_BUILT_IN);
    }

    public static int getAudioBackground() {
        return Math.min(Math.max(Prefers.getInt("audio_background", AUDIO_BACKGROUND_ARTWORK), AUDIO_BACKGROUND_ARTWORK), AUDIO_BACKGROUND_RANDOM);
    }

    public static void putAudioBackground(int background) {
        Prefers.put("audio_background", Math.min(Math.max(background, AUDIO_BACKGROUND_ARTWORK), AUDIO_BACKGROUND_RANDOM));
    }

    public static int getAudioBackgroundSeed() {
        return Prefers.getInt("audio_background_seed", 0x5A17B3);
    }

    public static void putAudioBackgroundSeed(int seed) {
        Prefers.put("audio_background_seed", seed);
    }

    public static int getAudioBackgroundDecorationSeed() {
        return Prefers.getInt("audio_background_decoration_seed", getAudioBackgroundSeed());
    }

    public static void putAudioBackgroundDecorationSeed(int seed) {
        Prefers.put("audio_background_decoration_seed", seed);
    }

    public static boolean isAudioBackgroundDecorated() {
        return Prefers.getBoolean("audio_background_decorated", true);
    }

    public static void putAudioBackgroundDecorated(boolean decorated) {
        Prefers.put("audio_background_decorated", decorated);
    }

    public static boolean isAudioBackgroundLightEffect() {
        return Prefers.getBoolean("audio_background_light_effect", false);
    }

    public static void putAudioBackgroundLightEffect(boolean enabled) {
        Prefers.put("audio_background_light_effect", enabled);
    }

    public static boolean isPlayer(int player) {
        return player == EXO || player == IJK || player == SYSTEM || player == MPV;
    }

    public static int sanitizePlayer(int player) {
        return isPlayer(player) ? player : EXO;
    }

    public static int resolvePlayer(int player) {
        return isPlayer(player) ? player : getPlayer();
    }

    public static boolean isPlayerAvailable(int player) {
        return player != IJK || isIjkAvailable();
    }

    public static boolean isIjkAvailable() {
        return true;
    }

    public static int kernelCount() {
        return KERNEL_ORDER.length;
    }

    /**
     * 以内核常量为下标的数组该开多大（如回退已试标记表）。
     * 由顺序表推导而非写死某个常量，新增内核时不会漏掉长度、把下标撑越界。
     */
    public static int kernelIndexSize() {
        int max = 0;
        for (int kernel : KERNEL_ORDER) if (kernel > max) max = kernel;
        return max + 1;
    }

    /** 内核在优先级顺序里的位次，用于对话框选中项与排序展示。 */
    public static int kernelRank(int player) {
        int target = sanitizePlayer(player);
        for (int i = 0; i < KERNEL_ORDER.length; i++) if (KERNEL_ORDER[i] == target) return i;
        return 0;
    }

    /** 位次还原成内核常量，越界时退回 EXO。 */
    public static int kernelAt(int rank) {
        return rank >= 0 && rank < KERNEL_ORDER.length ? KERNEL_ORDER[rank] : EXO;
    }

    /** 按优先级顺序把标签数组重排成菜单顺序，入参下标是内核常量。 */
    public static String[] orderKernels(String[] labels) {
        if (labels == null) return new String[0];
        String[] ordered = new String[KERNEL_ORDER.length];
        for (int i = 0; i < KERNEL_ORDER.length; i++) {
            int kernel = KERNEL_ORDER[i];
            ordered[i] = kernel < labels.length ? labels[kernel] : "";
        }
        return ordered;
    }

    /** 手动轮换：沿优先级顺序取下一个，走到末尾回到开头。 */
    public static int nextPlayer(int player) {
        return kernelAt((kernelRank(player) + 1) % KERNEL_ORDER.length);
    }

    /**
     * 回退用：从优先级顺序开头扫描，跳过已试过的内核（当前内核由调用方先标记）。
     * 所以无论从哪个内核失败，回退都按 EXO → IJK → MPV → 系统 推进，只是跳过自身。
     */
    public static int firstUntriedPlayer(boolean[] tried) {
        if (tried == null) return kernelAt(0);
        for (int kernel : KERNEL_ORDER) {
            // 越界的内核记不进 tried，返回它会让调用方标记不生效而反复拿到同一个，
            // 因此按「已试过」跳过：宁可少一次回退，也不能让回退循环停不下来。
            if (kernel >= tried.length || tried[kernel]) continue;
            return kernel;
        }
        return NONE;
    }

    public static int getRender() {
        return Math.min(Math.max(Prefers.getInt("render", RENDER_SURFACE), RENDER_SURFACE), RENDER_TEXTURE);
    }

    public static int getRender(int player) {
        return useNativeVideoOutput(player) ? 0 : getRender();
    }

    public static boolean useNativeVideoOutput(int player) {
        return player == IJK || player == SYSTEM || player == MPV;
    }

    public static void putRender(int render) {
        int value = Math.min(Math.max(render, RENDER_SURFACE), RENDER_TEXTURE);
        Prefers.put("render", value);
        if (isTunnel() && value == RENDER_TEXTURE) Prefers.put("tunnel", false);
        if (isExoEnhanced() && value == RENDER_TEXTURE) Prefers.put("exo_4k_compat", false);
    }

    public static int getMpvRender() {
        int render = Prefers.getInt("mpv_render", MPV_RENDER_OPENGL);
        return render == MPV_RENDER_VULKAN ? MPV_RENDER_VULKAN : MPV_RENDER_OPENGL;
    }

    public static void putMpvRender(int render) {
        Prefers.put("mpv_render", render == MPV_RENDER_VULKAN ? MPV_RENDER_VULKAN : MPV_RENDER_OPENGL);
    }

    public static int getPadLiveMode() {
        return Prefers.getInt("pad_live_mode", PAD_LIVE_FULLSCREEN) == PAD_LIVE_STANDARD ? PAD_LIVE_STANDARD : PAD_LIVE_FULLSCREEN;
    }

    public static void putPadLiveMode(int mode) {
        Prefers.put("pad_live_mode", mode == PAD_LIVE_STANDARD ? PAD_LIVE_STANDARD : PAD_LIVE_FULLSCREEN);
    }

    public static boolean isPadLiveFullscreen() {
        return getPadLiveMode() == PAD_LIVE_FULLSCREEN;
    }

    public static int getSize() {
        return Prefers.getInt("size", 2);
    }

    public static void putSize(int size) {
        Prefers.put("size", size);
    }

    public static int getScale() {
        return VideoAspectMode.sanitize(Prefers.getInt("scale"));
    }

    public static void putScale(int scale) {
        Prefers.put("scale", VideoAspectMode.sanitize(scale));
    }

    public static float getCustomAspectWidth() {
        float width = Prefers.getFloat(KEY_CUSTOM_ASPECT_WIDTH, VideoAspectMode.DEFAULT_CUSTOM_WIDTH);
        return Float.isFinite(width) && width > 0f ? width : VideoAspectMode.DEFAULT_CUSTOM_WIDTH;
    }

    public static float getCustomAspectHeight() {
        float height = Prefers.getFloat(KEY_CUSTOM_ASPECT_HEIGHT, VideoAspectMode.DEFAULT_CUSTOM_HEIGHT);
        return Float.isFinite(height) && height > 0f ? height : VideoAspectMode.DEFAULT_CUSTOM_HEIGHT;
    }

    public static float getCustomAspectRatio() {
        float width = getCustomAspectWidth();
        float height = getCustomAspectHeight();
        return VideoAspectMode.isValidDimensions(width, height) ? width / height : VideoAspectMode.DEFAULT_CUSTOM_RATIO;
    }

    public static void putCustomAspectRatio(float width, float height) {
        if (!VideoAspectMode.isValidDimensions(width, height)) return;
        Prefers.put(KEY_CUSTOM_ASPECT_WIDTH, width);
        Prefers.put(KEY_CUSTOM_ASPECT_HEIGHT, height);
    }

    public static int getEpisodeColumn() {
        return Math.min(Math.max(Prefers.getInt("episode_column", 2), 1), 2);
    }

    public static void putEpisodeColumn(int column) {
        Prefers.put("episode_column", column == 1 ? 1 : 2);
    }

    public static int getBuffer() {
        return getBuffer(getPlayer());
    }

    public static int getBuffer(int kernel) {
        return KernelPerformanceSetting.getBuffer(sanitizePlayer(kernel));
    }

    public static void putBuffer(int buffer) {
        KernelPerformanceSetting.putBuffer(getPlayer(), buffer);
    }

    public static int getBufferBytesOption() {
        return KernelPerformanceSetting.getBufferBytesOption(getPlayer());
    }

    public static void putBufferBytesOption(int option) {
        KernelPerformanceSetting.putBufferBytesOption(getPlayer(), option);
    }

    public static int getBufferBytes() {
        return getBufferBytes(getPlayer());
    }

    public static int getBufferBytes(int kernel) {
        return switch (KernelPerformanceSetting.getBufferBytesOption(sanitizePlayer(kernel))) {
            case 1 -> 64 * 1024 * 1024;
            case 2 -> 128 * 1024 * 1024;
            case 3 -> 256 * 1024 * 1024;
            default -> 0;
        };
    }

    public static int getBackBufferOption() {
        return getBackBufferOption(getPlayer());
    }

    public static int getBackBufferOption(int kernel) {
        return KernelPerformanceSetting.getBackBufferOption(sanitizePlayer(kernel));
    }

    public static void putBackBufferOption(int option) {
        KernelPerformanceSetting.putBackBufferOption(getPlayer(), option);
    }

    public static int getBackBufferMs() {
        return getBackBufferMs(getPlayer());
    }

    public static int getBackBufferMs(int kernel) {
        return switch (KernelPerformanceSetting.getBackBufferOption(sanitizePlayer(kernel))) {
            case 1 -> 15_000;
            case 2 -> 30_000;
            case 3 -> 60_000;
            default -> 0;
        };
    }

    public static int getPlayCacheOption() {
        return KernelPerformanceSetting.getPlayCacheOption(getPlayer());
    }

    public static void putPlayCacheOption(int option) {
        KernelPerformanceSetting.putPlayCacheOption(getPlayer(), option);
    }

    public static long getPlayCacheSize() {
        return getPlayCacheSize(getPlayer());
    }

    public static long getPlayCacheSize(int kernel) {
        return switch (KernelPerformanceSetting.getPlayCacheOption(sanitizePlayer(kernel))) {
            case 1 -> 256L * 1024 * 1024;
            case 2 -> 512L * 1024 * 1024;
            case 3 -> 1024L * 1024 * 1024;
            case 4 -> 2L * 1024 * 1024 * 1024;
            default -> 128L * 1024 * 1024;
        };
    }

    public static boolean isAutoChange() {
        return Prefers.getBoolean("player_auto_change", true);
    }

    public static void putAutoChange(boolean autoChange) {
        Prefers.put("player_auto_change", autoChange);
    }

    public static int getFailureFallback() {
        int mode = Prefers.getInt(KEY_FAILURE_FALLBACK, FALLBACK_FULL);
        return mode >= FALLBACK_FULL && mode <= FALLBACK_DISABLED ? mode : FALLBACK_FULL;
    }

    public static void putFailureFallback(int mode) {
        Prefers.put(KEY_FAILURE_FALLBACK, mode >= FALLBACK_FULL && mode <= FALLBACK_DISABLED ? mode : FALLBACK_FULL);
    }

    public static boolean isAutoPlay() {
        return Prefers.getBoolean("player_auto_play", true);
    }

    public static void putAutoPlay(boolean autoPlay) {
        Prefers.put("player_auto_play", autoPlay);
    }

    public static int getBackground() {
        int stored = Prefers.getInt("background", BackgroundPlaybackPolicy.ON);
        int normalized = BackgroundPlaybackPolicy.normalize(stored);
        if (stored != normalized) Prefers.put("background", normalized);
        return normalized;
    }

    public static void putBackground(int background) {
        Prefers.put("background", BackgroundPlaybackPolicy.normalize(background));
    }

    public static boolean isMusicNotification() {
        return Prefers.getBoolean("audio_music_notification", true);
    }

    public static void putMusicNotification(boolean notification) {
        Prefers.put("audio_music_notification", notification);
    }

    public static boolean isAudioBookNotification() {
        return Prefers.getBoolean("audio_book_notification", true);
    }

    public static void putAudioBookNotification(boolean notification) {
        Prefers.put("audio_book_notification", notification);
    }

    public static boolean isBackgroundOff() {
        return !isBackgroundOn();
    }

    public static boolean isBackgroundOn() {
        return BackgroundPlaybackPolicy.isEnabled(getBackground());
    }

    public static float getSpeed() {
        return Math.min(Math.max(Prefers.getFloat("speed", 3), 2), 5);
    }

    public static void putSpeed(float speed) {
        Prefers.put("speed", speed);
    }

    public static float getDefaultSpeed() {
        return Math.min(Math.max(Prefers.getFloat("play_speed", 1), 0.5f), 5);
    }

    public static void putDefaultSpeed(float speed) {
        Prefers.put("play_speed", Math.min(Math.max(speed, 0.5f), 5));
    }

    public static boolean isDisplayTime() {
        migrateLegacyOsd();
        return Prefers.getBoolean(KEY_DISPLAY_TIME);
    }

    public static void putDisplayTime(boolean displayTime) {
        Prefers.put(KEY_DISPLAY_TIME, displayTime);
    }

    public static boolean isDisplayTraffic() {
        migrateLegacyOsd();
        return Prefers.getBoolean(KEY_DISPLAY_TRAFFIC);
    }

    public static void putDisplayTraffic(boolean displayTraffic) {
        Prefers.put(KEY_DISPLAY_TRAFFIC, displayTraffic);
    }

    public static boolean isDisplaySize() {
        migrateLegacyOsd();
        return Prefers.getBoolean(KEY_DISPLAY_SIZE, true);
    }

    public static void putDisplaySize(boolean displaySize) {
        Prefers.put(KEY_DISPLAY_SIZE, displaySize);
    }

    public static boolean isDisplayProgress() {
        migrateLegacyOsd();
        return Prefers.getBoolean(KEY_DISPLAY_PROGRESS, true);
    }

    public static void putDisplayProgress(boolean displayProgress) {
        Prefers.put(KEY_DISPLAY_PROGRESS, displayProgress);
    }

    public static boolean isDisplayMini() {
        migrateLegacyOsd();
        return Prefers.getBoolean(KEY_DISPLAY_MINI);
    }

    public static void putDisplayMini(boolean displayMini) {
        Prefers.put(KEY_DISPLAY_MINI, displayMini);
    }

    public static boolean isDisplayTitle() {
        migrateLegacyOsd();
        return Prefers.getBoolean(KEY_DISPLAY_TITLE, true);
    }

    public static void putDisplayTitle(boolean displayTitle) {
        Prefers.put(KEY_DISPLAY_TITLE, displayTitle);
    }

    public static boolean[] getDisplayChecked() {
        return new boolean[]{isDisplayTime(), isDisplayTraffic(), isDisplaySize(), isDisplayProgress(), isDisplayMini(), isDisplayTitle(), isOsdDiagnostics()};
    }

    public static void putDisplayChecked(boolean[] checked) {
        putDisplayTime(valueAt(checked, 0, isDisplayTime()));
        putDisplayTraffic(valueAt(checked, 1, isDisplayTraffic()));
        putDisplaySize(valueAt(checked, 2, isDisplaySize()));
        putDisplayProgress(valueAt(checked, 3, isDisplayProgress()));
        putDisplayMini(valueAt(checked, 4, isDisplayMini()));
        putDisplayTitle(valueAt(checked, 5, isDisplayTitle()));
        putOsdDiagnostics(valueAt(checked, 6, isOsdDiagnostics()));
    }

    public static boolean[] getLiveDisplayChecked() {
        return new boolean[]{isDisplayTime(), isDisplayTraffic(), isDisplaySize(), isDisplayTitle(), isOsdDiagnostics()};
    }

    public static void putLiveDisplayChecked(boolean[] checked) {
        putDisplayTime(valueAt(checked, 0, isDisplayTime()));
        putDisplayTraffic(valueAt(checked, 1, isDisplayTraffic()));
        putDisplaySize(valueAt(checked, 2, isDisplaySize()));
        putDisplayTitle(valueAt(checked, 3, isDisplayTitle()));
        putOsdDiagnostics(valueAt(checked, 4, isOsdDiagnostics()));
    }

    /**
     * 是否记住播放页亮度。关闭时播放页完全不接管窗口亮度（跟随系统），
     * 手势调节只在当次播放生效、不落盘，退出播放页即还原。
     * <p>
     * 默认关闭：历史上「一进播放页就自动变亮」的反馈都源于无条件套用记忆值，
     * 而自动亮度机型的手势基准是固定的 0.5（见 Util.getBrightness），
     * 随手一滑就会把偏高的值永久固化，用户没有任何入口关掉它。
     */
    public static boolean isRememberBrightness() {
        return Prefers.getBoolean(KEY_REMEMBER_BRIGHTNESS, false);
    }

    /**
     * 切换开关时两个方向都丢弃记忆值，让每次开启都从「跟随系统」起步。
     * <p>
     * 只在关闭时清理是不对称的：旧版用户升级后 prefs 里可能残留一个很旧甚至被夹到 1.0 的值，
     * 开启开关会立刻把屏幕拉到那个亮度，正是本开关要消除的现象。
     * 由用户开启后自己滑一次来建立记忆值，语义最清晰。
     */
    public static void putRememberBrightness(boolean remember) {
        Prefers.put(KEY_REMEMBER_BRIGHTNESS, remember);
        Prefers.remove("player_brightness");
    }

    public static float getBrightness() {
        if (!isRememberBrightness()) return BrightnessPolicy.FOLLOW_SYSTEM;
        migrateLegacyBrightness();
        return Math.min(Math.max(Prefers.getFloat("player_brightness", -1), -1), 1);
    }

    public static void putBrightness(float brightness) {
        if (!isRememberBrightness()) return;
        legacyBrightnessMigrated = true;
        Prefers.put(KEY_BRIGHTNESS_MIGRATED, true);
        Prefers.put("player_brightness", Math.min(Math.max(brightness, 0), 1));
    }

    /**
     * 旧版 Util.getBrightness() 把 Settings.System.SCREEN_BRIGHTNESS 写死除以 255，
     * 在 1023/2047/4095 量程机型上算出的基准值远大于 1，手势结果被永久夹到 1.0 并持久化，
     * 表现为「一进播放页屏幕自动变到最亮且调不下来」。这里一次性丢弃这个污染值，
     * 让亮度回到跟随系统，用户重新调节即可。
     * <p>
     * 自「记住播放亮度」开关引入后，putRememberBrightness 在开关的两个方向都会清空
     * player_brightness，历史污染值已在开关开启时被无条件清掉，这段迁移退化为兜底：
     * 只有当将来出现「不经开关就启用记忆」的新路径时才会再次生效。
     */
    private static void migrateLegacyBrightness() {
        if (legacyBrightnessMigrated) return;
        legacyBrightnessMigrated = true;
        if (Prefers.getBoolean(KEY_BRIGHTNESS_MIGRATED)) return;
        Prefers.put(KEY_BRIGHTNESS_MIGRATED, true);
        float saved = Prefers.getFloat("player_brightness", -1);
        // 只丢弃大量程机型上的 1.0：255 量程机型不会产生这个 bug，那里的 1.0 是用户主动设的最亮
        if (BrightnessPolicy.isLegacyPollutedValue(saved, Util.getBrightnessScale())) Prefers.remove("player_brightness");
    }

    public static boolean isCaption() {
        return Prefers.getBoolean("caption");
    }

    public static void putCaption(boolean caption) {
        Prefers.put("caption", caption);
    }

    public static boolean hasCaption() {
        return new Intent(Settings.ACTION_CAPTIONING_SETTINGS).resolveActivity(App.get().getPackageManager()) != null;
    }

    public static boolean isTunnel() {
        return Prefers.getBoolean("tunnel");
    }

    public static void putTunnel(boolean tunnel) {
        Prefers.put("tunnel", tunnel);
        if (tunnel) Prefers.put("render", RENDER_SURFACE);
    }

    public static boolean isTunnelingEnabled() {
        return isTunnel() && getRender() == RENDER_SURFACE;
    }

    public static boolean isExo4KCompat() {
        return isExoEnhanced();
    }

    public static boolean isExoEnhanced() {
        return Prefers.getBoolean("exo_4k_compat");
    }

    public static void putExo4KCompat(boolean value) {
        putExoEnhanced(value);
    }

    public static void putExoEnhanced(boolean value) {
        Prefers.put("exo_4k_compat", value);
        if (value) Prefers.put("render", RENDER_SURFACE);
    }

    public static boolean isAudioPrefer() {
        return isAudioPrefer(getPlayer());
    }

    public static boolean isAudioPrefer(int kernel) {
        return KernelPerformanceSetting.isAudioPrefer(sanitizePlayer(kernel));
    }

    public static void putAudioPrefer(boolean audioPrefer) {
        KernelPerformanceSetting.putAudioPrefer(getPlayer(), audioPrefer);
    }

    public static boolean isAudioPassThrough() {
        return isAudioPassThrough(getPlayer());
    }

    public static boolean isAudioPassThrough(int kernel) {
        return KernelPerformanceSetting.isAudioPassThrough(sanitizePlayer(kernel));
    }

    public static void putAudioPassThrough(boolean audioPassThrough) {
        putAudioPassThrough(getPlayer(), audioPassThrough);
    }

    public static void putAudioPassThrough(int kernel, boolean audioPassThrough) {
        KernelPerformanceSetting.putAudioPassThrough(sanitizePlayer(kernel), audioPassThrough);
    }

    public static boolean isVideoPrefer() {
        return isVideoPrefer(getPlayer());
    }

    public static boolean isVideoPrefer(int kernel) {
        return KernelPerformanceSetting.isVideoPrefer(sanitizePlayer(kernel));
    }

    public static void putVideoPrefer(boolean videoPrefer) {
        KernelPerformanceSetting.putVideoPrefer(getPlayer(), videoPrefer);
    }

    // AUTO 模式失败遍历顺序：能力从全到简（NextLib 有 ffmpeg 音视频兜底+解码调度调优；Simple 有音视频兜底；Official 纯官方）。
    public static final int[] FFMPEG_AUTO_ORDER = {FFMPEG_MODE_NEXTLIB, FFMPEG_MODE_SIMPLE, FFMPEG_MODE_OFFICIAL};

    // 进程内运行时覆盖，仅在设置为 AUTO 时生效，用于失败链遍历具体模式；不写盘。
    private static volatile int ffmpegModeOverride = NONE;

    public static int getFFmpegMode() {
        int defaultMode = getDefaultFFmpegMode();
        return sanitizeFFmpegMode(Prefers.getInt(KEY_FFMPEG_MODE, defaultMode), defaultMode);
    }

    public static void putFFmpegMode(int mode) {
        Prefers.put(KEY_FFMPEG_MODE, sanitizeFFmpegMode(mode, getDefaultFFmpegMode()));
        clearFFmpegModeOverride();
    }

    // ExoUtil 构造渲染器时使用的具体模式。AUTO 时解析成运行时覆盖，无覆盖则取遍历首项。
    public static int getEffectiveFFmpegMode() {
        int mode = getFFmpegMode();
        if (mode != FFMPEG_MODE_AUTO) return mode;
        int override = ffmpegModeOverride;
        return isConcreteFFmpegMode(override) ? override : FFMPEG_AUTO_ORDER[0];
    }

    public static boolean isAutoFFmpegMode() {
        return getFFmpegMode() == FFMPEG_MODE_AUTO;
    }

    public static void setFFmpegModeOverride(int mode) {
        ffmpegModeOverride = isConcreteFFmpegMode(mode) ? mode : NONE;
    }

    public static void clearFFmpegModeOverride() {
        ffmpegModeOverride = NONE;
    }

    static int getDefaultFFmpegMode() {
        return sanitizeFFmpegMode(App.get().getResources().getInteger(R.integer.default_ffmpeg_mode), FFMPEG_MODE_SIMPLE);
    }

    static int sanitizeFFmpegMode(int mode, int defaultMode) {
        if (isFFmpegMode(mode)) return mode;
        return isFFmpegMode(defaultMode) ? defaultMode : FFMPEG_MODE_SIMPLE;
    }

    private static boolean isFFmpegMode(int mode) {
        return isConcreteFFmpegMode(mode) || mode == FFMPEG_MODE_AUTO;
    }

    private static boolean isConcreteFFmpegMode(int mode) {
        return mode == FFMPEG_MODE_NEXTLIB || mode == FFMPEG_MODE_OFFICIAL || mode == FFMPEG_MODE_SIMPLE;
    }

    public static boolean useNextLibFFmpeg() {
        return getEffectiveFFmpegMode() == FFMPEG_MODE_NEXTLIB;
    }

    public static void putUseNextLibFFmpeg(boolean useNextLib) {
        putFFmpegMode(useNextLib ? FFMPEG_MODE_NEXTLIB : FFMPEG_MODE_OFFICIAL);
    }

    public static boolean isPreferAAC() {
        return isPreferAAC(getPlayer());
    }

    public static boolean isPreferAAC(int kernel) {
        return KernelPerformanceSetting.isPreferAac(sanitizePlayer(kernel));
    }

    public static void putPreferAAC(boolean preferAAC) {
        KernelPerformanceSetting.putPreferAac(getPlayer(), preferAAC);
    }

    public static float getSubtitleTextSize() {
        return Prefers.getFloat("subtitle_text_size");
    }

    public static void putSubtitleTextSize(float value) {
        Prefers.put("subtitle_text_size", value);
    }

    public static float getSubtitlePosition() {
        return Prefers.getFloat("subtitle_position");
    }

    public static void putSubtitlePosition(float value) {
        Prefers.put("subtitle_position", value);
    }

    public static boolean isDesktopLyrics() {
        return isImmersiveAudioMode() && Prefers.getBoolean("desktop_lyrics");
    }

    public static void putDesktopLyrics(boolean value) {
        Prefers.put("desktop_lyrics", value);
    }

    public static int getDesktopLyricsX(int defaultValue) {
        return Prefers.getInt("desktop_lyrics_x", defaultValue);
    }

    public static int getDesktopLyricsY(int defaultValue) {
        return Prefers.getInt("desktop_lyrics_y", defaultValue);
    }

    public static void putDesktopLyricsPosition(int x, int y) {
        Prefers.put("desktop_lyrics_x", x);
        Prefers.put("desktop_lyrics_y", y);
    }

    public static void resetDesktopLyricsPosition() {
        Prefers.remove("desktop_lyrics_x");
        Prefers.remove("desktop_lyrics_y");
    }

    public static long getLyricsTimeOffsetMs() {
        return Math.min(Math.max(Prefers.getLong("lyrics_time_offset", 0L), -5000L), 5000L);
    }

    public static void putLyricsTimeOffsetMs(long value) {
        Prefers.put("lyrics_time_offset", Math.min(Math.max(value, -5000L), 5000L));
    }

    public static int getLyricsRows() {
        return Math.min(Math.max(Prefers.getInt("lyrics_rows", 5), 1), 5);
    }

    public static void putLyricsRows(int value) {
        Prefers.put("lyrics_rows", Math.min(Math.max(value, 1), 5));
    }

    public static int getLyricsTextSizeOption() {
        return Math.min(Math.max(Prefers.getInt("lyrics_text_size", 1), 0), 3);
    }

    public static void putLyricsTextSizeOption(int value) {
        Prefers.put("lyrics_text_size", Math.min(Math.max(value, 0), 3));
    }

    public static float getLyricsTextSizeScale() {
        return switch (getLyricsTextSizeOption()) {
            case 0 -> 0.85f;
            case 2 -> 1.15f;
            case 3 -> 1.3f;
            default -> 1f;
        };
    }

    public static boolean isKaraokeMode() {
        return isImmersiveAudioMode() && Prefers.getBoolean("karaoke_mode");
    }

    public static void putKaraokeMode(boolean value) {
        Prefers.put("karaoke_mode", value);
    }

    public static int getKaraokeDifficulty() {
        return Math.min(Math.max(Prefers.getInt("karaoke_difficulty", 0), 0), 2);
    }

    public static void putKaraokeDifficulty(int value) {
        Prefers.put("karaoke_difficulty", Math.min(Math.max(value, 0), 2));
    }

    public static double getKaraokeToleranceSemitones() {
        return switch (getKaraokeDifficulty()) {
            case 1 -> 1.5;
            case 2 -> 1.0;
            default -> 2.0;
        };
    }

    public static long getKaraokeMicDelayMs() {
        return Math.min(Math.max(Prefers.getLong("karaoke_mic_delay", 0L), -1000L), 1000L);
    }

    public static void putKaraokeMicDelayMs(long value) {
        Prefers.put("karaoke_mic_delay", Math.min(Math.max(value, -1000L), 1000L));
    }

    public static boolean isKaraokeBasicPitchTflite() {
        return Prefers.getBoolean("karaoke_basic_pitch_tflite");
    }

    public static void putKaraokeBasicPitchTflite(boolean value) {
        Prefers.put("karaoke_basic_pitch_tflite", value);
    }

    public static String getKaraokeGithubSources() {
        return Prefers.getString("karaoke_github_sources");
    }

    public static void putKaraokeGithubSources(String value) {
        Prefers.put("karaoke_github_sources", value == null ? "" : value.trim());
    }

    public static boolean isOsdTitle() {
        return isDisplayTitle();
    }

    public static void putOsdTitle(boolean value) {
        putDisplayTitle(value);
    }

    public static boolean isOsdResolution() {
        return isDisplaySize();
    }

    public static void putOsdResolution(boolean value) {
        putDisplaySize(value);
    }

    public static boolean isOsdTime() {
        return isDisplayTime();
    }

    public static void putOsdTime(boolean value) {
        putDisplayTime(value);
    }

    public static boolean isOsdProgress() {
        return isDisplayProgress();
    }

    public static void putOsdProgress(boolean value) {
        putDisplayProgress(value);
    }

    public static boolean isOsdTraffic() {
        return isDisplayTraffic();
    }

    public static void putOsdTraffic(boolean value) {
        putDisplayTraffic(value);
    }

    public static boolean isOsdMini() {
        return isDisplayMini();
    }

    public static void putOsdMini(boolean value) {
        putDisplayMini(value);
    }

    public static boolean isOsdSize() {
        return isDisplaySize();
    }

    public static void putOsdSize(boolean value) {
        putDisplaySize(value);
    }

    public static boolean isOsdDiagnostics() {
        return Prefers.getBoolean("player_osd_diagnostics");
    }

    public static void putOsdDiagnostics(boolean value) {
        Prefers.put("player_osd_diagnostics", value);
    }

    public static boolean isOsdEnabled() {
        return isOsdTitle() || isOsdTime() || isOsdSize() || isOsdProgress() || isOsdTraffic() || isOsdMini() || isOsdDiagnostics();
    }

    private static boolean valueAt(boolean[] checked, int index, boolean fallback) {
        return checked != null && checked.length > index ? checked[index] : fallback;
    }

    private static void migrateLegacyOsd() {
        if (legacyOsdMigrated) return;
        legacyOsdMigrated = true;
        if (hasAny(KEY_DISPLAY_TIME, KEY_DISPLAY_TRAFFIC, KEY_DISPLAY_SIZE, KEY_DISPLAY_PROGRESS, KEY_DISPLAY_MINI, KEY_DISPLAY_TITLE)) return;
        if (!hasAny(KEY_OSD_TITLE, KEY_OSD_RESOLUTION, KEY_OSD_TIME, KEY_OSD_PROGRESS, KEY_OSD_TRAFFIC, KEY_OSD_MINI)) return;
        putDisplayTime(Prefers.getBoolean(KEY_OSD_TIME));
        putDisplayTraffic(Prefers.getBoolean(KEY_OSD_TRAFFIC));
        putDisplaySize(Prefers.getPrefers().contains(KEY_OSD_RESOLUTION) ? Prefers.getBoolean(KEY_OSD_RESOLUTION) : Prefers.getBoolean(KEY_OSD_TITLE));
        putDisplayProgress(Prefers.getBoolean(KEY_OSD_PROGRESS));
        putDisplayMini(Prefers.getBoolean(KEY_OSD_MINI));
        putDisplayTitle(Prefers.getBoolean(KEY_OSD_TITLE));
    }

    private static boolean hasAny(String... keys) {
        for (String key : keys) if (Prefers.getPrefers().contains(key)) return true;
        return false;
    }

    public static int getNightModeLevel() {
        int level = Prefers.getInt("night_mode_level", NIGHT_MODE_OFF);
        return level >= NIGHT_MODE_OFF && level <= NIGHT_MODE_HIGH ? level : NIGHT_MODE_OFF;
    }

    public static void putNightModeLevel(int level) {
        Prefers.put("night_mode_level", Math.min(Math.max(level, NIGHT_MODE_OFF), NIGHT_MODE_HIGH));
    }

    public static int getNightModeDefault() {
        int mode = Prefers.getInt("night_mode_default", NIGHT_MODE_AUTO);
        return mode >= NIGHT_MODE_AUTO && mode <= NIGHT_MODE_ALWAYS_ON ? mode : NIGHT_MODE_AUTO;
    }

    public static void putNightModeDefault(int mode) {
        Prefers.put("night_mode_default", Math.min(Math.max(mode, NIGHT_MODE_AUTO), NIGHT_MODE_ALWAYS_ON));
    }
}
