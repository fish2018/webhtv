package com.fongmi.android.tv.theme;

import java.util.Locale;

/** Small, dependency-free color helpers shared by validation and token resolution. */
public final class ThemeColorUtil {

    public static final int WHITE = 0xFFFFFFFF;
    public static final int BLACK = 0xFF000000;

    private ThemeColorUtil() {
    }

    public static String normalize(String value) {
        if (value == null) return null;
        String color = value.trim().toUpperCase(Locale.ROOT);
        if (!color.startsWith("#")) return null;
        if (color.length() == 4 && isHex(color, 1)) {
            return "#" + color.charAt(1) + color.charAt(1)
                    + color.charAt(2) + color.charAt(2)
                    + color.charAt(3) + color.charAt(3);
        }
        if (color.length() == 7 && isHex(color, 1)) return color;
        if (color.length() == 9 && color.startsWith("#FF") && isHex(color, 3)) return color.substring(3);
        return null;
    }

    private static boolean isHex(String value, int start) {
        for (int i = start; i < value.length(); i++) {
            char character = value.charAt(i);
            if ((character < '0' || character > '9')
                    && (character < 'A' || character > 'F')) return false;
        }
        return true;
    }

    public static boolean isValid(String value) {
        return normalize(value) != null;
    }

    /**
     * Normalizes an opaque CSS color used by TweakCN registry JSON. The adapter deliberately
     * supports only color functions with deterministic conversion; CSS, URLs and alpha colors
     * are not executable or silently accepted.
     */
    public static String normalizeCss(String value) {
        String hex = normalize(value);
        if (hex != null) return hex;
        if (value == null) return null;
        String color = value.trim().toLowerCase(Locale.ROOT);
        try {
            if (color.startsWith("oklch(") && color.endsWith(")")) return format(parseOklch(color.substring(6, color.length() - 1)));
            if ((color.startsWith("hsl(") || color.startsWith("hsla(")) && color.endsWith(")")) {
                int offset = color.startsWith("hsla(") ? 5 : 4;
                return format(parseHsl(color.substring(offset, color.length() - 1)));
            }
            if ((color.startsWith("rgb(") || color.startsWith("rgba(")) && color.endsWith(")")) {
                int offset = color.startsWith("rgba(") ? 5 : 4;
                return format(parseRgb(color.substring(offset, color.length() - 1)));
            }
            // Older shadcn exports store HSL channels without the hsl() wrapper.
            if (color.matches("[-+0-9.]+\\s+[-+0-9.]+%\\s+[-+0-9.]+%(\\s+[-+0-9.]+%?)?")) {
                return format(parseHsl(color));
            }
        } catch (RuntimeException ignored) {
            // An unsupported CSS color is an import warning, not a reason to crash the editor.
        }
        return null;
    }

    private static int parseOklch(String body) {
        String[] parts = components(body);
        if (parts.length != 3 && parts.length != 4) throw new IllegalArgumentException("oklch components");
        if (parts.length == 4 && !isOpaqueAlpha(parts[3])) throw new IllegalArgumentException("transparent color");
        double lightness = number(parts[0], 1d, false);
        double chroma = number(parts[1], 0.4d, true);
        double hue = number(parts[2], 360d, false);
        if (lightness < 0d || lightness > 1d || chroma < 0d || !finite(lightness, chroma, hue)) {
            throw new IllegalArgumentException("oklch range");
        }
        double radians = Math.toRadians(hue % 360d);
        double a = chroma * Math.cos(radians);
        double b = chroma * Math.sin(radians);
        double l = lightness + 0.3963377774d * a + 0.2158037573d * b;
        double m = lightness - 0.1055613458d * a - 0.0638541728d * b;
        double s = lightness - 0.0894841775d * a - 1.2914855480d * b;
        double l3 = l * l * l;
        double m3 = m * m * m;
        double s3 = s * s * s;
        return rgb(
                gamma(4.0767416621d * l3 - 3.3077115913d * m3 + 0.2309699292d * s3),
                gamma(-1.2684380046d * l3 + 2.6097574011d * m3 - 0.3413193965d * s3),
                gamma(-0.0041960863d * l3 - 0.7034186147d * m3 + 1.7076147010d * s3));
    }

    private static int parseHsl(String body) {
        String[] parts = components(body);
        if (parts.length != 3 && parts.length != 4) throw new IllegalArgumentException("hsl components");
        if (parts.length == 4 && !isOpaqueAlpha(parts[3])) throw new IllegalArgumentException("transparent color");
        double h = number(parts[0], 360d, false) % 360d;
        double s = number(parts[1], 1d, true);
        double l = number(parts[2], 1d, true);
        if (!finite(h, s, l) || s < 0d || s > 1d || l < 0d || l > 1d) throw new IllegalArgumentException("hsl range");
        double c = (1d - Math.abs(2d * l - 1d)) * s;
        double x = c * (1d - Math.abs((h / 60d) % 2d - 1d));
        double match = l - c / 2d;
        double r = 0d, g = 0d, b = 0d;
        if (h < 60d) { r = c; g = x; } else if (h < 120d) { r = x; g = c; }
        else if (h < 180d) { g = c; b = x; } else if (h < 240d) { g = x; b = c; }
        else if (h < 300d) { r = x; b = c; } else { r = c; b = x; }
        return rgb(r + match, g + match, b + match);
    }

    private static int parseRgb(String body) {
        String[] parts = components(body);
        if (parts.length != 3 && parts.length != 4) throw new IllegalArgumentException("rgb components");
        if (parts.length == 4 && !isOpaqueAlpha(parts[3])) throw new IllegalArgumentException("transparent color");
        return rgb(channelNumber(parts[0]), channelNumber(parts[1]), channelNumber(parts[2]));
    }

    private static String[] components(String body) {
        return body.trim().replace(',', ' ').replace('/', ' ').trim().split("\\s+");
    }

    private static double number(String value, double percentScale, boolean percentageAllowed) {
        String part = value.trim();
        if (part.endsWith("%")) {
            if (!percentageAllowed) throw new IllegalArgumentException("unexpected percentage");
            return Double.parseDouble(part.substring(0, part.length() - 1)) / 100d * percentScale;
        }
        return Double.parseDouble(part);
    }

    private static double channelNumber(String value) {
        String part = value.trim();
        double result = part.endsWith("%")
                ? Double.parseDouble(part.substring(0, part.length() - 1)) / 100d
                : Double.parseDouble(part) / 255d;
        if (!finite(result) || result < 0d || result > 1d) throw new IllegalArgumentException("rgb range");
        return result;
    }

    private static boolean isOpaqueAlpha(String value) {
        double alpha = number(value, 1d, true);
        return finite(alpha) && alpha >= 0.999d;
    }

    private static boolean finite(double... values) {
        for (double value : values) if (Double.isNaN(value) || Double.isInfinite(value)) return false;
        return true;
    }

    private static double gamma(double value) {
        if (!finite(value)) throw new IllegalArgumentException("color conversion");
        return value <= 0.0031308d ? 12.92d * value : 1.055d * Math.pow(value, 1d / 2.4d) - 0.055d;
    }

    private static int rgb(double red, double green, double blue) {
        if (!finite(red, green, blue)) throw new IllegalArgumentException("color conversion");
        int r = Math.round((float) (Math.max(0d, Math.min(1d, red)) * 255d));
        int g = Math.round((float) (Math.max(0d, Math.min(1d, green)) * 255d));
        int b = Math.round((float) (Math.max(0d, Math.min(1d, blue)) * 255d));
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    public static int parse(String value, int fallback) {
        String normalized = normalize(value);
        if (normalized == null) return fallback;
        try {
            return 0xFF000000 | Integer.parseInt(normalized.substring(1), 16);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static String format(int color) {
        return String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF);
    }

    public static double contrast(int foreground, int background) {
        double first = luminance(foreground);
        double second = luminance(background);
        double lighter = Math.max(first, second);
        double darker = Math.min(first, second);
        return (lighter + 0.05) / (darker + 0.05);
    }

    public static int readableOn(int background) {
        return contrast(WHITE, background) >= contrast(BLACK, background) ? WHITE : BLACK;
    }

    public static int mix(int first, int second, float amountOfSecond) {
        float amount = clamp(amountOfSecond, 0f, 1f);
        int r = Math.round(red(first) + (red(second) - red(first)) * amount);
        int g = Math.round(green(first) + (green(second) - green(first)) * amount);
        int b = Math.round(blue(first) + (blue(second) - blue(first)) * amount);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public static int ensureContrast(int foreground, int background, double minimum) {
        if (contrast(foreground, background) >= minimum) return foreground;
        return readableOn(background);
    }

    public static int red(int color) {
        return color >> 16 & 0xFF;
    }

    public static int green(int color) {
        return color >> 8 & 0xFF;
    }

    public static int blue(int color) {
        return color & 0xFF;
    }

    private static double luminance(int color) {
        double r = channel(red(color));
        double g = channel(green(color));
        double b = channel(blue(color));
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channel(int value) {
        double normalized = value / 255d;
        return normalized <= 0.03928
                ? normalized / 12.92
                : Math.pow((normalized + 0.055) / 1.055, 2.4);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
