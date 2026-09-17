package com.fongmi.android.tv.ui.adapter;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VodAdapterLayoutTest {

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final List<String> VERTICAL_ANGLES = List.of("90", "270");
    /** Roboto's default line height at includeFontPadding="false", as a multiple of the text size. */
    private static final float LINE_HEIGHT_RATIO = 1.172f;
    /** Alpha a black scrim needs for white text to clear WCAG AA 4.5:1 over a white poster pixel. */
    private static final float MIN_ALPHA = 0.535f;

    @Test
    public void mobileCategoryTitlesMarqueeOnlyWhenOverflowing() throws Exception {
        assertTitleMarquee("list", "adapter_vod_list.xml", "VodListHolder.java");
        assertTitleMarquee("rect", "adapter_vod_rect.xml", "VodRectHolder.java");
        assertTitleMarquee("oval", "adapter_vod_oval.xml", "VodOvalHolder.java");
    }

    @Test
    public void mobileRectRemarkIsFullWidthAndCentredOnThePoster() throws Exception {
        Element remark = findById(parseLayout(read(findMobileResPath().resolve(Path.of("layout", "adapter_vod_rect.xml")))), "@+id/remark");

        assertEquals("rect remark must start at the poster's start edge", "@+id/image", androidAttribute(remark, "layout_alignStart"));
        assertEquals("rect remark must end at the poster's end edge", "@+id/image", androidAttribute(remark, "layout_alignEnd"));
        assertEquals("rect remark must anchor to the poster, not to the title, which is GONE for unnamed items",
                "@+id/image", androidAttribute(remark, "layout_alignBottom"));
        assertEquals("rect remark must centre its text", "center", androidAttribute(remark, "gravity"));
    }

    @Test
    public void mobileRectRemarkStaysLegibleOverTheScrim() throws Exception {
        Element remark = rectRemark();
        Element gradient = directChild(rectScrim(), "gradient");

        assertEquals("rect remark must sit on the full-width scrim", "@drawable/shape_vod_remark_full", androidAttribute(remark, "background"));
        assertEquals("rect remark must use light text over the scrim", "@color/white", androidAttribute(remark, "textColor"));
        assertEquals("rect remark must reuse the shared text-shadow contract instead of inline shadow attributes",
                "@style/Video.ContextText", remark.getAttribute("style"));
        assertTrue("scrim must be a vertical gradient so the text band can sit on its dark end, was angle=" + androidAttribute(gradient, "angle"),
                VERTICAL_ANGLES.contains(androidAttribute(gradient, "angle")));
        assertEquals("scrim must fade out completely at its top so it never masks the poster",
                0f, scrimAlphaAt(gradient, 1f), 0.001f);
    }

    /**
     * The scrim only earns its keep if the glyphs land on the dark part of the ramp. Assert that as
     * arithmetic over the real geometry (shape padding + text size + gradient stops) rather than by
     * pinning the literals, so any equivalent rewrite stays green and any change that actually walks
     * the text into the transparent half fails.
     */
    @Test
    public void mobileRectRemarkTextBandKeepsAContrastFloor() throws Exception {
        Element remark = rectRemark();
        Element scrim = rectScrim();
        Element gradient = directChild(scrim, "gradient");
        Element padding = directChild(scrim, "padding");

        float textHeight = LINE_HEIGHT_RATIO * dimension(androidAttribute(remark, "textSize"));
        float bottom = dimension(androidAttribute(padding, "bottom"));
        float band = dimension(androidAttribute(padding, "top")) + textHeight + bottom;
        float alphaAtGlyphTops = scrimAlphaAt(gradient, (bottom + textHeight) / band);

        assertTrue("white " + androidAttribute(remark, "textSize") + " text needs the scrim at >= " + MIN_ALPHA
                        + " alpha where the glyph tops sit to clear 4.5:1 over a bright poster, but the "
                        + band + "dp band only reaches " + alphaAtGlyphTops,
                alphaAtGlyphTops >= MIN_ALPHA);
    }

    private static void assertTitleMarquee(String style, String layoutName, String holderName) throws Exception {
        Element name = findById(parseLayout(read(findMobileResPath().resolve(Path.of("layout", layoutName)))), "@+id/name");
        String holder = read(findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "holder", holderName)));

        assertTrue(style + " category title must marquee only when its text overflows",
                "marquee".equals(androidAttribute(name, "ellipsize"))
                        && "marquee_forever".equals(androidAttribute(name, "marqueeRepeatLimit"))
                        && "true".equals(androidAttribute(name, "scrollHorizontally"))
                        && "true".equals(androidAttribute(name, "singleLine")));
        assertTrue(style + " category holder must activate the title marquee",
                holder.contains("binding.name.setSelected(true);"));
    }

    private static Element parseLayout(String layout) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(layout.getBytes(StandardCharsets.UTF_8)))
                .getDocumentElement();
    }

    private static Element findById(Element root, String id) {
        if (id.equals(androidAttribute(root, "id"))) return root;
        NodeList nodes = root.getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            if (id.equals(androidAttribute(element, "id"))) return element;
        }
        throw new AssertionError("Missing layout view " + id);
    }

    private static Element directChild(Element parent, String tag) {
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element element && tag.equals(element.getTagName())) return element;
        }
        throw new AssertionError("Missing <" + tag + "> directly inside <" + parent.getTagName() + ">");
    }

    private static Element rectRemark() throws Exception {
        return findById(parseLayout(read(findMobileResPath().resolve(Path.of("layout", "adapter_vod_rect.xml")))), "@+id/remark");
    }

    private static Element rectScrim() throws Exception {
        return parseLayout(read(findMobileResPath().resolve(Path.of("drawable", "shape_vod_remark_full.xml"))));
    }

    /**
     * Alpha of the scrim at {@code fromBottom} (0 = the band's bottom edge, 1 = its top edge),
     * mirroring GradientDrawable: angle 90 puts startColor at the bottom and 270 flips it, and a
     * centerColor sits at centerX when that is overridden, otherwise at centerY.
     */
    private static float scrimAlphaAt(Element gradient, float fromBottom) throws Exception {
        float offset = "90".equals(androidAttribute(gradient, "angle")) ? fromBottom : 1f - fromBottom;
        float start = colorAlpha(androidAttribute(gradient, "startColor"));
        float end = colorAlpha(androidAttribute(gradient, "endColor"));
        String center = androidAttribute(gradient, "centerColor");
        if (center.isEmpty()) return start + (end - start) * offset;
        float centerX = fraction(androidAttribute(gradient, "centerX"));
        float middle = centerX == 0.5f ? fraction(androidAttribute(gradient, "centerY")) : centerX;
        float centerAlpha = colorAlpha(center);
        if (offset <= middle) return start + (centerAlpha - start) * (offset / middle);
        return centerAlpha + (end - centerAlpha) * ((offset - middle) / (1f - middle));
    }

    private static float colorAlpha(String reference) throws Exception {
        String argb = reference.startsWith("#") ? reference : namedColour(reference.substring(reference.indexOf('/') + 1));
        if (argb.length() != 9) throw new AssertionError("Expected an #AARRGGBB colour for " + reference + ", was " + argb);
        return Integer.parseInt(argb.substring(1, 3), 16) / 255f;
    }

    private static String namedColour(String name) throws Exception {
        Element colours = parseLayout(read(findMainResPath().resolve(Path.of("values", "colors.xml"))));
        NodeList nodes = colours.getElementsByTagName("color");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element colour = (Element) nodes.item(i);
            if (name.equals(colour.getAttribute("name"))) return colour.getTextContent().trim();
        }
        throw new AssertionError("Missing colour @color/" + name);
    }

    private static float fraction(String value) {
        return value.isEmpty() ? 0.5f : Float.parseFloat(value);
    }

    private static float dimension(String value) {
        return Float.parseFloat(value.replaceAll("[a-z]+$", ""));
    }

    private static String androidAttribute(Element element, String name) {
        return element.getAttributeNS(ANDROID_NS, name);
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path findMobileJavaPath() {
        Path moduleRelative = Path.of("src", "mobile", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "mobile", "java");
    }

    private static Path findMobileResPath() {
        Path moduleRelative = Path.of("src", "mobile", "res");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "mobile", "res");
    }

    private static Path findMainResPath() {
        Path moduleRelative = Path.of("src", "main", "res");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "res");
    }
}
