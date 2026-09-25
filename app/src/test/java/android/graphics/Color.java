package android.graphics;

public final class Color {

    private Color() {
    }

    public static int rgb(int red, int green, int blue) {
        return 0xFF000000 | ((red & 0xFF) << 16) | ((green & 0xFF) << 8) | (blue & 0xFF);
    }
}
