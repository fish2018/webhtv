package android.text;

import java.util.Objects;

public final class TextUtils {

    private TextUtils() {
    }

    public static boolean isEmpty(CharSequence text) {
        return text == null || text.length() == 0;
    }

    public static boolean equals(CharSequence a, CharSequence b) {
        return Objects.equals(a == null ? null : a.toString(), b == null ? null : b.toString());
    }
}
