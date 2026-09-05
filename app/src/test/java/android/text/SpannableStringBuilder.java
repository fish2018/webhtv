package android.text;

import java.lang.reflect.Array;

public final class SpannableStringBuilder implements Spanned {

    private final StringBuilder text = new StringBuilder();

    public SpannableStringBuilder append(char value) {
        text.append(value);
        return this;
    }

    public SpannableStringBuilder append(CharSequence value) {
        text.append(value);
        return this;
    }

    public SpannableStringBuilder append(CharSequence value, int start, int end) {
        text.append(value, start, end);
        return this;
    }

    public SpannableStringBuilder delete(int start, int end) {
        text.delete(start, end);
        return this;
    }

    public void setSpan(Object what, int start, int end, int flags) {
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
        return text.toString();
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
