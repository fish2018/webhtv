package com.fongmi.android.tv.ui.adapter;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HistoryAdapterTest {

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String APP_NS = "http://schemas.android.com/apk/res-auto";

    @Test
    public void historyCardsShowPlaybackProgressAndTime() throws Exception {
        String layout = read(findLeanbackResPath().resolve(Path.of("layout", "adapter_vod.xml")));
        String mobileLayout = read(findMobileResPath().resolve(Path.of("layout", "adapter_vod.xml")));
        String adapter = read(findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "HistoryAdapter.java")));
        String mobileAdapter = read(findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "HistoryAdapter.java")));
        String presenter = read(findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "presenter", "HistoryPresenter.java")));
        String keepAdapter = read(findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "KeepAdapter.java")));
        String mobileKeepAdapter = read(findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "KeepAdapter.java")));
        String mobileHistoryActivity = read(findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "HistoryActivity.java")));
        String mobileKeepActivity = read(findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "KeepActivity.java")));
        String historyBean = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "bean", "History.java")));
        String tmdbDetailActivity = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java")));

        assertHistoryCardLayout("TV", layout);
        assertHistoryCardLayout("mobile", mobileLayout);
        assertMobileDeleteOverlayIsCentered(mobileLayout, mobileAdapter);
        assertBindsPlaybackProgress("TV history page", adapter);
        assertBindsPlaybackProgress("mobile history page", mobileAdapter);
        assertBindsPlaybackProgress("TV home recent row", presenter);
        assertHidesHistoryOnlyViews("TV keep page", keepAdapter);
        assertHidesHistoryOnlyViews("mobile keep page", mobileKeepAdapter);
        assertFocusMarquee("TV history page", adapter);
        assertFocusMarquee("TV home recent row", presenter);
        assertFocusMarquee("TV keep page", keepAdapter);
        assertVisibleMobileMarquee("mobile history page", mobileAdapter, mobileHistoryActivity);
        assertVisibleMobileMarquee("mobile keep page", mobileKeepAdapter, mobileKeepActivity);
        assertPersistsTmdbScrapedHistoryLabels("TMDB detail playback", tmdbDetailActivity);
        assertTrue("History model must format playback time from current position and duration",
                historyBean.contains("public boolean hasPlaybackTime()")
                        && historyBean.contains("return Util.timeMs(position) + \" / \" + Util.timeMs(duration);"));
    }

    private static void assertHistoryCardLayout(String owner, String layout) throws Exception {
        Element root = parseLayout(layout);
        Element progress = findById(root, "@+id/progress");
        Element info = findById(root, "@+id/history_info");
        Element site = findById(root, "@+id/site");
        Element playback = findById(root, "@+id/playback");
        Element name = findById(root, "@+id/name");
        Element remark = findById(root, "@+id/remark");

        assertTrue(owner + " shared vod card must expose a playback progress bar",
                "@+id/image".equals(androidAttribute(progress, "layout_below"))
                        && "2dp".equals(progress.getAttributeNS(APP_NS, "trackThickness")));
        assertTrue(owner + " shared vod card must place title and episode in a two-line info area below the poster",
                "@+id/progress".equals(androidAttribute(info, "layout_below"))
                        && "vertical".equals(androidAttribute(info, "orientation"))
                        && "@drawable/shape_vod_name".equals(androidAttribute(info, "background"))
                        && name.getParentNode() == info
                        && remark.getParentNode() == info
                        && childElementIndex(info, name) < childElementIndex(info, remark));
        assertTrue(owner + " episode text must be plain secondary text rather than a tag",
                androidAttribute(remark, "background").isEmpty()
                        && "center".equals(androidAttribute(remark, "gravity")));
        assertTrue(owner + " playback time must remain a single tag on the poster",
                "@+id/image".equals(androidAttribute(playback, "layout_alignStart"))
                        && "@+id/image".equals(androidAttribute(playback, "layout_alignBottom"))
                        && "@drawable/shape_vod_remark".equals(androidAttribute(playback, "background")));
        assertStaticEllipsis(owner + " source tag", site);
        assertStaticEllipsis(owner + " playback tag", playback);
        assertMarquee(owner + " title", name);
        assertMarquee(owner + " episode", remark);
    }

    private static void assertMobileDeleteOverlayIsCentered(String layout, String adapter) throws Exception {
        Element root = parseLayout(layout);
        Element image = findById(root, "@+id/image");
        Element site = findById(root, "@+id/site");
        Element delete = findById(root, "@+id/delete");
        assertTrue("mobile delete overlay must span the card instead of becoming a clipped fixed-size dot",
                delete.getParentNode() == root
                        && "wrap_content".equals(androidAttribute(delete, "layout_width"))
                        && "wrap_content".equals(androidAttribute(delete, "layout_height"))
                        && "@+id/image".equals(androidAttribute(delete, "layout_alignStart"))
                        && "@+id/image".equals(androidAttribute(delete, "layout_alignTop"))
                        && "@+id/image".equals(androidAttribute(delete, "layout_alignEnd"))
                        && "@+id/history_info".equals(androidAttribute(delete, "layout_alignBottom")));
        assertTrue("mobile delete overlay must dim the card and center the full white trash icon",
                "@color/black_50".equals(androidAttribute(delete, "background"))
                        && "center".equals(androidAttribute(delete, "scaleType"))
                        && "@drawable/ic_vod_delete".equals(androidAttribute(delete, "src"))
                        && "@style/Vod.Grid.Large".equals(delete.getAttributeNS(APP_NS, "shapeAppearanceOverlay")));
        assertTrue("mobile delete overlay must render above the poster and below the source label",
                childElementIndex(root, image) < childElementIndex(root, delete)
                        && childElementIndex(root, delete) < childElementIndex(root, site));
        assertTrue("mobile delete mode must keep a distinct episode name visible",
                adapter.contains("binding.remark.setVisibility(same ? View.INVISIBLE : View.VISIBLE);"));
    }

    private static void assertHidesHistoryOnlyViews(String owner, String source) {
        assertTrue(owner + " reuses the card layout and must hide the history-only progress bar and time label",
                source.contains("holder.binding.progress.setVisibility(View.GONE);")
                        && source.contains("holder.binding.playback.setVisibility(View.GONE);"));
    }

    private static void assertBindsPlaybackProgress(String owner, String source) {
        assertTrue(owner + " must render the stored history title and episode text",
                source.contains("binding.name.setText(item.getVodName());")
                        && source.contains("binding.remark.setText"));
        assertTrue(owner + " must calculate duration from history",
                source.contains("Math.max(0, item.getDuration())"));
        assertTrue(owner + " must calculate position from history",
                source.contains("Math.max(0, item.getPosition())"));
        assertTrue(owner + " must set the progress max from duration",
                source.contains("binding.progress.setMax(duration > 0 ? duration : 1);"));
        assertTrue(owner + " must clamp progress to the duration",
                source.contains("binding.progress.setProgress(duration > 0 ? Math.min(progress, duration) : 0"));
        assertTrue(owner + " must bind the playback time label",
                source.contains("binding.playback.setText(item.getPlaybackTimeText());")
                        && source.contains("binding.playback.setVisibility(")
                        && source.contains("item.hasPlaybackTime()")
                        && source.contains("!delete && item.hasPlaybackTime()"));
        assertTrue(owner + " must keep the second metadata line aligned when no distinct episode exists",
                source.contains("binding.remark.setVisibility(delete || same ? View.INVISIBLE : View.VISIBLE);")
                        || source.contains("binding.remark.setVisibility(same ? View.INVISIBLE : View.VISIBLE);"));
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

    private static String androidAttribute(Element element, String name) {
        return element.getAttributeNS(ANDROID_NS, name);
    }

    private static int childElementIndex(Element parent, Element child) {
        NodeList nodes = parent.getChildNodes();
        int index = 0;
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) continue;
            if (node == child) return index;
            index++;
        }
        return -1;
    }

    private static void assertMarquee(String owner, Element element) {
        assertTrue(owner + " must marquee only when its text overflows",
                "marquee".equals(androidAttribute(element, "ellipsize"))
                        && "marquee_forever".equals(androidAttribute(element, "marqueeRepeatLimit"))
                        && "true".equals(androidAttribute(element, "scrollHorizontally"))
                        && "true".equals(androidAttribute(element, "singleLine")));
    }

    private static void assertStaticEllipsis(String owner, Element element) {
        assertTrue(owner + " must stay static and truncate at the end",
                "end".equals(androidAttribute(element, "ellipsize"))
                        && "true".equals(androidAttribute(element, "singleLine")));
        assertTrue(owner + " must not run an independent marquee animation",
                androidAttribute(element, "marqueeRepeatLimit").isEmpty()
                        && androidAttribute(element, "scrollHorizontally").isEmpty());
    }

    private static void assertFocusMarquee(String owner, String source) {
        assertTrue(owner + " must run title and episode marquee only while its card has focus",
                source.contains("setMarquee(hasFocus);")
                        && source.contains("binding.name.setSelected(active);")
                        && source.contains("binding.remark.setSelected(active);"));
        assertFalse(owner + " must not animate source and playback labels",
                source.contains("binding.site.setSelected(true);")
                        || source.contains("binding.playback.setSelected(true);"));
    }

    private static void assertVisibleMobileMarquee(String owner, String adapter, String activity) {
        assertTrue(owner + " adapter must activate every card in the visible range",
                adapter.contains("public void setMarqueeRange(int firstPosition, int lastPosition)")
                        && adapter.contains("binding.name.setSelected(active);")
                        && adapter.contains("binding.remark.setSelected(active);"));
        assertFalse(owner + " adapter must not change text layer types while scrolling",
                adapter.contains("setLayerType("));
        assertFalse(owner + " must not animate source and playback labels",
                adapter.contains("binding.site.setSelected(true);")
                        || adapter.contains("binding.playback.setSelected(true);"));
        assertTrue(owner + " must pause while scrolling and activate the visible range when idle",
                activity.contains("RecyclerView.SCROLL_STATE_IDLE")
                        && activity.contains("setMarqueeRange(RecyclerView.NO_POSITION, RecyclerView.NO_POSITION)")
                        && activity.contains("findMarqueeRange(")
                        && activity.contains("findViewById(R.id.history_info)")
                        && activity.contains("getChildAdapterPosition(child)"));
    }

    private static void assertPersistsTmdbScrapedHistoryLabels(String owner, String source) {
        assertTrue(owner + " must save scraped TMDB title and episode label into history",
                source.contains("history.setVodName(playbackHistoryName());")
                        && source.contains("history.setVodRemarks(historyEpisodeTitle(item));"));
        assertTrue(owner + " must refresh existing history entries with scraped TMDB labels",
                source.contains("saved.setVodName(playbackHistoryName());")
                        && source.contains("saved.setVodRemarks(title);"));
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path findLeanbackJavaPath() {
        Path moduleRelative = Path.of("src", "leanback", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "leanback", "java");
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

    private static Path findMobileResPath() {
        Path moduleRelative = Path.of("src", "mobile", "res");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "mobile", "res");
    }

    private static Path findMainJavaPath() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "java");
    }
}
