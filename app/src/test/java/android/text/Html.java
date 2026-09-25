package android.text;

public final class Html {

    public static final int FROM_HTML_MODE_LEGACY = 0x00000001;
    public static final int FROM_HTML_MODE_COMPACT = 0x00000063;

    private Html() {
    }

    public static Spanned fromHtml(String source) {
        String text = source == null ? "" : source.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("<[^>]+>", "");
        return SpannedString.valueOf(text);
    }

    /** 标志位只影响换行/段落的细节，对去标签的结果没有区别，所以与单参版同实现。 */
    public static Spanned fromHtml(String source, int flags) {
        return fromHtml(source);
    }
}
