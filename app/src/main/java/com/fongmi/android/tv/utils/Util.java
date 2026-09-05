package com.fongmi.android.tv.utils;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.text.Html;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.view.ViewCompat;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.R;
import com.github.catvod.utils.Shell;

import java.net.NetworkInterface;
import java.util.Formatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Util {

    private static final Pattern EPISODE = Pattern.compile("(?i)(?:ep|第|e|[\\-\\.\\s])\\s?(\\d{1,4})");
    private static final Pattern SEASON_EPISODE = Pattern.compile("[Ss](?:[0-9]{1,2})?[-._\\s]*[Ee]([0-9]{1,3})", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHINESE_EPISODE = Pattern.compile("第\\s*([零一二三四五六七八九十百千万0-9]+)\\s*[集话章节回期]");
    private static final Pattern EP_PREFIX = Pattern.compile("\\b(?:EP|E)[-._\\s]*([0-9]{1,3})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMERIC_RANGE = Pattern.compile("(?<!\\d)\\d{1,4}\\s*[-~～至]\\s*\\d{1,4}(?!\\d)");
    private static final Pattern TRAILING_SEASON_EPISODE = Pattern.compile("(?<!\\d)([1-9]\\d?)[_.-]0*([1-9]\\d{0,2})(?=$|\\s|\\[|\\(|\\.(?:mp4|mkv|avi|flv|ts|m3u8|webm|mov|wmv|rmvb))", Pattern.CASE_INSENSITIVE);
    private static volatile String serial;

    public static void toggleFullscreen(Activity activity, boolean fullscreen) {
        if (fullscreen) hideSystemUI(activity);
        else showSystemUI(activity);
    }

    public static void moveToBackground(Activity activity) {
        try {
            activity.moveTaskToBack(true);
        } catch (NullPointerException ignored) {
            activity.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    public static boolean resetApp() {
        try {
            ActivityManager manager = App.get().getSystemService(ActivityManager.class);
            return manager != null && manager.clearApplicationUserData();
        } catch (RuntimeException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void hideSystemUI(Activity activity) {
        hideSystemUI(activity.getWindow());
    }

    public static void hideSystemUI(Window window) {
        WindowInsetsControllerCompat insets = WindowCompat.getInsetsController(window, window.getDecorView());
        insets.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        insets.hide(WindowInsetsCompat.Type.systemBars());
    }

    public static void showSystemUI(Activity activity) {
        showSystemUI(activity.getWindow());
    }

    public static void showSystemUI(Window window) {
        WindowCompat.getInsetsController(window, window.getDecorView()).show(WindowInsetsCompat.Type.systemBars());
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    public static void showKeyboard(View view) {
        if (!view.requestFocus()) return;
        InputMethodManager imm = (InputMethodManager) App.get().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) view.postDelayed(() -> imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT), 250);
    }

    public static void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) App.get().getSystemService(Context.INPUT_METHOD_SERVICE);
        IBinder windowToken = view.getWindowToken();
        if (imm == null || windowToken == null) return;
        imm.hideSoftInputFromWindow(windowToken, 0);
    }

    public static float getBrightness(Activity activity) {
        try {
            float value = activity.getWindow().getAttributes().screenBrightness;
            if (WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL >= value && value >= WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF) return value;
            // 自动亮度下 SCREEN_BRIGHTNESS 只是手动滑条的残留值，与屏幕实际亮度无关，
            // 拿它当手势基准会导致「想调暗反而更亮」，此时用中间值起步更稳。
            if (isAutoBrightness(activity)) return 0.5f;
            return BrightnessPolicy.normalize(getSystemBrightness(activity), getBrightnessScale());
        } catch (Exception e) {
            return 0.5f;
        }
    }

    private static boolean isAutoBrightness(Activity activity) {
        try {
            return Settings.System.getInt(activity.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 少数 ROM 把 SCREEN_BRIGHTNESS 存成 "128.0" 这类浮点字符串，getInt 会直接抛异常，
     * 所以按字符串读再解析，避免整个亮度读取退化成兜底值。
     */
    private static int getSystemBrightness(Activity activity) {
        String value = Settings.System.getString(activity.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
        if (TextUtils.isEmpty(value)) return -1;
        try {
            return Math.round(Float.parseFloat(value.trim()));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Settings.System.SCREEN_BRIGHTNESS 的量程由厂商决定：Pixel 多为 255，
     * 小米/OPPO 常见 2047，一加 1023，华为部分机型 4095。写死 255 会让归一化后的
     * 基准亮度远大于 1，导致手势调节被永久夹到最亮。这里读取系统真实上限。
     */
    public static int getBrightnessScale() {
        try {
            int id = Resources.getSystem().getIdentifier("config_screenBrightnessSettingMaximum", "integer", "android");
            if (id != 0) {
                int max = Resources.getSystem().getInteger(id);
                if (max > 0) return max;
            }
        } catch (Exception ignored) {
        }
        return BrightnessPolicy.DEFAULT_SCALE;
    }

    public static CharSequence getClipText() {
        ClipboardManager manager = (ClipboardManager) App.get().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clipData = manager == null ? null : manager.getPrimaryClip();
        if (clipData == null || clipData.getItemCount() == 0) return "";
        return clipData.getItemAt(0).getText();
    }

    public static void copy(String text) {
        try {
            ClipboardManager manager = (ClipboardManager) App.get().getSystemService(Context.CLIPBOARD_SERVICE);
            manager.setPrimaryClip(ClipData.newPlainText("", text));
            Notify.show(R.string.copied);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 从文件名或备注文本中提取集数
     *
     * @param text 输入文本（文件名、备注等）
     * @return 集数（1-999），无效或无法提取时返回 -1
     */
    public static int getEpisodeNumber(String text) {
        try {
            if (TextUtils.isEmpty(text)) return -1;

            // 预处理：移除常见干扰信息
            String processed = preprocessEpisodeText(text);

            // 1. 纯数字区间不是单集，例如 601-620、621-27。
            String trimmed = processed.trim();
            if (NUMERIC_RANGE.matcher(trimmed).matches()) return -1;

            // 2. 独立纯数字检测（如播放器传来的 "17" 或 "03"）。
            if (trimmed.matches("\\d{1,3}")) {
                int value = Integer.parseInt(trimmed);
                return (value > 0 && value <= 999) ? value : -1;
            }

            // 2. S01E03 格式（优先级最高）
            Matcher matcher = SEASON_EPISODE.matcher(processed);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }

            // 3. 中文格式：第XX集/话/章（支持中文数字）
            matcher = CHINESE_EPISODE.matcher(processed);
            if (matcher.find()) {
                String chineseNum = matcher.group(1);
                int result = convertChineseNumberToArabic(chineseNum);
                if (result > 0) return result;
            }

            // 4. EP03, E03 格式
            matcher = EP_PREFIX.matcher(processed);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }

            // 6. 作品名后的“季_集 / 季-集 / 季.集”，例如“一人之下6_01”。
            matcher = TRAILING_SEASON_EPISODE.matcher(processed);
            if (matcher.find()) return Integer.parseInt(matcher.group(2));

            // 7. 原有逻辑（兼容旧格式）
            matcher = EPISODE.matcher(processed);
            if (matcher.find()) return Integer.parseInt(matcher.group(1));

            // 8. 最后只接受唯一的数字段，禁止把 6 和 01 拼成 601。
            Matcher digits = Pattern.compile("(?<!\\d)(\\d{1,3})(?!\\d)").matcher(processed);
            int value = -1;
            while (digits.find()) {
                int candidate = Integer.parseInt(digits.group(1));
                if (candidate <= 0 || candidate >= 1900 && candidate <= 2099) continue;
                if (value > 0) return -1;
                value = candidate;
            }
            if (value > 0 && value <= 999) return value;

            return -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private static String preprocessEpisodeText(String text) {
        // 移除文件扩展名（避免从 mp4, mkv 等提取数字）
        // 注意：不用 $ 锚点，扩展名后面可能还跟着文件大小等信息（如 02.mp4[5.17GB]）
        String processed = text.replaceAll("(?i)\\.(mp4|mkv|avi|flv|ts|m3u8|webm|mov|wmv|rmvb)(?![a-z0-9])", " ");

        // 完整日期中的月/日不能被误认成“季_集”，例如 show_2026_07_18。
        processed = processed.replaceAll("(?<!\\d)(?:19|20)\\d{2}[-_.](?:0?[1-9]|1[0-2])[-_.](?:0?[1-9]|[12]\\d|3[01])(?!\\d)", " ");

        // 移除文件大小格式：[210.03G], (1.2GB) 等
        processed = processed
                .replaceAll("\\[[0-9]+(?:\\.[0-9]+)?[GMK]B?\\]", " ")
                .replaceAll("\\([0-9]+(?:\\.[0-9]+)?[GMK]B?\\)", " ")
                .replaceAll("【[0-9]+(?:\\.[0-9]+)?[GMK]B?】", " ");

        // 移除常见的画质、编码信息
        processed = processed
                .replaceAll("(?i)(?<![a-z0-9])(?:2160|1080|720|480)p(?![a-z0-9])", " ")
                .replaceAll("(?i)(?<![a-z0-9])(?:4K|2K|HD|SD|FHD|UHD)(?![a-z0-9])", " ")
                .replaceAll("\\b(?:x264|x265|H264|H265|AVC|HEVC)\\b", " ")
                .replaceAll("\\b(?:AAC|AC3|DTS|FLAC)\\b", " ")
                .replaceAll("\\b(?:WEB[-._\\s]*DL|BluRay|BDRip|REMUX|HDTV)\\b", " ");

        // 移除版本信息：v2, ver2.0
        processed = processed.replaceAll("\\b[vV](?:[0-9]+(?:\\.[0-9]+)?)\\b", " ");

        // 移除年份（但保留在"第X集"等明确格式中的数字）
        processed = processed.replaceAll("\\b(19|20)\\d{2}\\b", " ");

        // 移除括号内容（但保留括号外的文本）
        processed = processed.replaceAll("\\[.*?\\]|\\(.*?\\)", " ");

        // 合并多个空格
        return processed.replaceAll("\\s+", " ").trim();
    }

    private static int convertChineseNumberToArabic(String chineseNum) {
        if (TextUtils.isEmpty(chineseNum)) return -1;

        String normalized = chineseNum.trim().replace("两", "二");
        if (TextUtils.isEmpty(normalized)) return -1;

        // 尝试直接解析阿拉伯数字
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException e) {
            // 继续中文数字解析
        }

        // 检查是否全是中文数字
        if (!normalized.matches("[零一二三四五六七八九十百千万]+")) {
            return -1;
        }

        // 处理简单的连续数字（一二三 → 123）
        boolean hasUnit = normalized.matches(".*[十百千万].*");
        if (!hasUnit) {
            StringBuilder digits = new StringBuilder();
            for (int i = 0; i < normalized.length(); i++) {
                int digit = chineseDigitToInt(normalized.charAt(i));
                if (digit < 0) return -1;
                digits.append(digit);
            }
            try {
                return Integer.parseInt(digits.toString());
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        // 处理带单位的中文数字（二十三 → 23，一百零五 → 105）
        int result = 0;
        int section = 0;
        int number = 0;

        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            int value = chineseValueToInt(c);
            if (value < 0) return -1;

            if (value == 0) {
                continue;
            }

            if (value < 10) {
                number = value;
                continue;
            }

            if (value == 10000) {
                section += number;
                if (section == 0) section = 1;
                result += section * value;
                section = 0;
                number = 0;
                continue;
            }

            if (number == 0) number = 1;
            section += number * value;
            number = 0;
        }

        result += section + number;
        return result > 0 ? result : -1;
    }

    private static int chineseDigitToInt(char c) {
        switch (c) {
            case '零': return 0;
            case '一': return 1;
            case '二': return 2;
            case '三': return 3;
            case '四': return 4;
            case '五': return 5;
            case '六': return 6;
            case '七': return 7;
            case '八': return 8;
            case '九': return 9;
            default: return -1;
        }
    }

    private static int chineseValueToInt(char c) {
        switch (c) {
            case '零': return 0;
            case '一': return 1;
            case '二': return 2;
            case '三': return 3;
            case '四': return 4;
            case '五': return 5;
            case '六': return 6;
            case '七': return 7;
            case '八': return 8;
            case '九': return 9;
            case '十': return 10;
            case '百': return 100;
            case '千': return 1000;
            case '万': return 10000;
            default: return -1;
        }
    }

    public static String clean(String text) {
        if (!text.contains("<")) return text;
        StringBuilder sb = new StringBuilder();
        text = Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString().replace("\u00A0", " ").replace("\u3000", " ");
        for (String line : text.split("\\r?\\n")) sb.append(line.trim()).append("\n");
        return substring(sb.toString()).trim();
    }

    public static String getAndroidId() {
        try {
            String id = Settings.Secure.getString(App.get().getContentResolver(), Settings.Secure.ANDROID_ID);
            if (TextUtils.isEmpty(id)) throw new NullPointerException();
            return id;
        } catch (Exception e) {
            return "0000000000000000";
        }
    }

    public static String getSerial() {
        if (serial != null) return serial;
        synchronized (Util.class) {
            if (serial == null) serial = Shell.exec("getprop ro.serialno").replace("\n", "");
            return serial;
        }
    }

    public static String getMac(String name) {
        try {
            StringBuilder sb = new StringBuilder();
            NetworkInterface nif = NetworkInterface.getByName(name);
            if (nif.getHardwareAddress() == null) return "";
            for (byte b : nif.getHardwareAddress()) sb.append(String.format("%02X:", b));
            return substring(sb.toString());
        } catch (Exception e) {
            return "";
        }
    }

    public static String getDeviceName() {
        String model = TextUtils.isEmpty(Build.MODEL) ? "Android" : Build.MODEL.trim();
        String manufacturer = TextUtils.isEmpty(Build.MANUFACTURER) ? "" : Build.MANUFACTURER.trim();
        if (TextUtils.isEmpty(manufacturer)) return model;
        return model.startsWith(manufacturer) ? model : manufacturer + " " + model;
    }

    public static String substring(String text) {
        return substring(text, 1);
    }

    public static String substring(String text, int num) {
        if (text != null && text.length() > num) return text.substring(0, text.length() - num);
        return text;
    }

    public static boolean isLeanback() {
        return "leanback".equals(BuildConfig.FLAVOR_mode);
    }

    public static boolean isMobile() {
        return "mobile".equals(BuildConfig.FLAVOR_mode);
    }

    public static boolean isFullscreen(Activity activity) {
        if (activity == null || activity.getWindow() == null) return false;
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(activity.getWindow().getDecorView());
        if (insets != null) return isLeanback() || !insets.isVisible(WindowInsetsCompat.Type.systemBars());
        return isLeanback() || (activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0;
    }

    public static boolean isFullscreenLand(Activity activity) {
        return isFullscreen(activity) && !isLeanback() && ResUtil.isLand(activity);
    }

    public static String format(StringBuilder builder, Formatter formatter, long timeMs) {
        try {
            return androidx.media3.common.util.Util.getStringForTime(builder, formatter, timeMs);
        } catch (Exception e) {
            return "";
        }
    }

    public static String timeMs(long timeMs) {
        StringBuilder sb = new StringBuilder();
        return format(sb, new Formatter(sb, Locale.getDefault()), timeMs);
    }
}
