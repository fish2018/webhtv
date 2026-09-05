package android.text;

import java.lang.reflect.Array;

public final class SpannedString implements Spanned {

    private final String text;

    private SpannedString(CharSequence text) {
        this.text = text == null ? "" : text.toString();
    }

    public static SpannedString valueOf(CharSequence text) {
        return new SpannedString(text);
    }

    @Override
    public int length() {
        return text.length();
    }

    @Override
    public char charAt(int index) {
        return text.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return text.subSequence(start, end);
    }

    @Override
    public String toString() {
        return text;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T[] getSpans(int start, int end, Class<T> type) {
        return (T[]) Array.newInstance(type, 0);
    }

    @Override
    public int getSpanStart(Object tag) {
        return -1;
    }

    @Override
    public int getSpanEnd(Object tag) {
        return -1;
    }

    @Override
    public int getSpanFlags(Object tag) {
        return 0;
    }

    @Override
    public int nextSpanTransition(int start, int limit, Class type) {
        return limit;
    }
}
