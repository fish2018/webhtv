package com.fongmi.android.tv.ui.adapter;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.TmdbEpisode;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EpisodeAdapterTest {

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    @Test
    public void tmdbCardTitleSeparatesSourceFileSize() {
        Episode episode = Episode.create("[5.32G] 01.mkv", "https://example.test/1");
        TmdbEpisode tmdbEpisode = new TmdbEpisode(1, "楚云嫁入齐府成...", "2026-06-20", "", "", 0, 47);
        episode.setTmdbEpisode(tmdbEpisode);

        String title = EpisodeAdapter.getCardTitle(episode);

        assertEquals("1. 楚云嫁入齐府成...", title);
        assertEquals("[5.32G]", EpisodeAdapter.getCardFileSize(episode, title, true));
        assertEquals("", EpisodeAdapter.getCardFileSize(episode, title, false));
    }

    @Test
    public void nativeFallbackSeparatesFileSizeWhenTmdbEpisodeScrapingFails() {
        Episode episode = Episode.create(
                "[我不是药神 2018 Dying to survive][原盘国语简体字幕花絮][44.01G].iso",
                "[44.01GB] ",
                "https://example.test/movie");

        assertEquals("[44.01GB]", EpisodeAdapter.getNativeFileSize(episode, true));
        assertEquals("[我不是药神 2018 Dying to survive][原盘国语简体字幕花絮].iso", EpisodeAdapter.getNativeDisplayTitle(episode, true));
        assertEquals("[44.01GB] [我不是药神 2018 Dying to survive][原盘国语简体字幕花絮][44.01G].iso", EpisodeAdapter.getNativeDisplayTitle(episode, false));
    }

    @Test
    public void mobileEpisodeCardsAndNativeFallbackBindFileSizeBadges() throws Exception {
        String gridHolder = read(findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "holder", "EpisodeGridHolder.java")));
        String horiHolder = read(findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "holder", "EpisodeHoriHolder.java")));
        String gridLayout = read(findMobileResPath().resolve(Path.of("layout", "adapter_episode_grid.xml")));
        String horiLayout = read(findMobileResPath().resolve(Path.of("layout", "adapter_episode_hori.xml")));

        assertTrue("mobile grid native fallback must expose a stable file-size badge",
                gridLayout.contains("android:id=\"@+id/nativeFileSize\"")
                        && gridHolder.contains("applyNativeLayout(EpisodeAdapter.getNativeFileSize(item));")
                        && gridHolder.contains("bindNativeFileSize(fileSize, stacked);")
                        && gridHolder.contains("EpisodeAdapter.getNativeDisplayTitle(item)"));        assertTrue("mobile horizontal native fallback must expose a stable file-size badge",
                horiLayout.contains("android:id=\"@+id/nativeFileSize\"")
                        && horiHolder.contains("bindNativeFileSize(EpisodeAdapter.getNativeFileSize(item));")
                        && horiHolder.contains("EpisodeAdapter.getNativeDisplayTitle(item)"));
        assertTrue("mobile grid TMDB cards must expose a fileSize badge",
                gridLayout.contains("android:id=\"@+id/fileSize\"")
                        && gridHolder.contains("binding.cardTitle.setText(cardTitle);")
                        && gridHolder.contains("bindFileSize(EpisodeAdapter.getCardFileSize(item, cardTitle), showMeta);"));
        assertTrue("mobile horizontal TMDB cards must expose a fileSize badge",
                horiLayout.contains("android:id=\"@+id/fileSize\"")
                        && horiHolder.contains("binding.cardTitle.setText(cardTitle);")
                        && horiHolder.contains("bindFileSize(EpisodeAdapter.getCardFileSize(item, cardTitle));"));
    }

    @Test
    public void mobileNativeEpisodeGridPreservesUpstreamTextWidth() throws Exception {
        String gridLayout = read(findMobileResPath().resolve(Path.of("layout", "adapter_episode_grid.xml")));
        String gridHolder = read(findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "holder", "EpisodeGridHolder.java")));
        Element root = parseLayout(gridLayout);

        assertTrue("native episode rows must not inherit the TMDB card margin",
                androidAttribute(root, "layout_margin").isEmpty());
        assertTrue("TMDB card spacing must be applied only when card mode is active",
                gridHolder.contains("int margin = useTmdbCard ? cardMargin : 0;")
                        && gridHolder.contains("marginParams.setMargins(margin, margin, margin, margin);"));
        assertTrue("native episode labels must preserve the title suffix with end ellipsis",
                gridHolder.contains("binding.text.setEllipsize(TextUtils.TruncateAt.END);"));
    }

    @Test
    public void leanbackEpisodeCardsAndNativeFallbackBindFileSizeBadges() throws Exception {
        String adapter = read(findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "EpisodeAdapter.java")));
        String layout = read(findLeanbackResPath().resolve(Path.of("layout", "adapter_episode_card.xml")));
        String nativeLayout = read(findLeanbackResPath().resolve(Path.of("layout", "adapter_episode.xml")));

        assertTrue("TV native fallback must expose a stable file-size badge",
                nativeLayout.contains("android:id=\"@+id/nativeFileSize\"")
                        && adapter.contains("getNativeFileSize(item)")
                        && adapter.contains("getNativeDisplayTitle(item)"));
        assertTrue("TV native file-size badge must stay inside the episode card",
                nativeLayout.contains("<FrameLayout")
                        && nativeLayout.indexOf("android:id=\"@+id/text\"") < nativeLayout.indexOf("android:id=\"@+id/nativeFileSize\"")
                        && nativeLayout.contains("android:layout_gravity=\"start|center_vertical\"")
                        && adapter.contains("int titleStartPadding = showFileSize ? ResUtil.dp2px(104) : horizontalPadding;")
                        && adapter.contains("if (showFileSize && !verticalGridMode) width += ResUtil.dp2px(104);")
                        && adapter.contains("textView.setPaddingRelative(titleStartPadding")
                        && !adapter.contains("width - ResUtil.dp2px(96)"));
        assertTrue("TV TMDB episode cards must expose a fileSize badge",
                layout.contains("android:id=\"@+id/fileSize\"")
                        && adapter.contains("getCardTitle(item, tmdbEpisode)")
                        && adapter.contains("binding.cardTitle.setText(cardTitle);")
                        && adapter.contains("bindFileSize(binding, getCardFileSize(item, cardTitle), showMeta);"));
    }

    @Test
    public void leanbackEpisodeGridKeepsOneUniformViewTypePerMode() throws Exception {
        String adapter = read(findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "EpisodeAdapter.java")));

        assertTrue("TV episode view type must depend only on the card mode, never on per-episode TMDB data;"
                        + " EpisodeListDialog sizes rows from a single mode-wide height, so mixing card and text"
                        + " holders in one grid misaligns every row that contains both",
                adapter.contains("return useTmdbCard ? VIEW_TYPE_CARD : VIEW_TYPE_TEXT;"));
        assertFalse("TV episode view type must not branch on getTmdbEpisode()",
                adapter.contains("useTmdbCard && item.getTmdbEpisode() != null"));
        assertFalse("bindCardView must degrade unmatched episodes instead of returning an unbound 190dp card",
                adapter.contains("if (tmdbEpisode == null) return;"));
        assertTrue("unmatched episodes must fall back to the native title, not the rejected TMDB name",
                adapter.contains("tmdbEpisode == null ? getNativeDisplayTitle(item) : getCardTitle(item, tmdbEpisode)"));
        assertTrue("card binding must null-guard every TMDB dereference so degraded cards still render",
                adapter.contains("tmdbEpisode == null ? \"\" : tmdbEpisode.getStillUrl()")
                        && adapter.contains("tmdbEpisode == null ? \"\" : getMeta(tmdbEpisode)"));
        assertTrue("TV cards must follow the detail page showVisual model: hide the full-bleed still and its"
                        + " scrim when no image resolves, and clear the recycled bitmap, or the previous"
                        + " episode's still bleeds through and the scrim darkens a flat card",
                adapter.contains("boolean showVisual = !TextUtils.isEmpty(imageUrl);")
                        && adapter.contains("binding.still.setVisibility(showVisual ? View.VISIBLE : View.GONE);")
                        && adapter.contains("binding.scrim.setVisibility(showVisual ? View.VISIBLE : View.GONE);")
                        && adapter.contains("binding.still.setImageDrawable(null);")
                        && adapter.contains("binding.textPanel.setGravity(showVisual ? Gravity.NO_GRAVITY : Gravity.CENTER_VERTICAL);"));
    }

    @Test
    public void detailEpisodeNamesAndLabelsMarqueeWhenActive() throws Exception {
        String detailLayout = read(findMainResPath().resolve(Path.of("layout", "adapter_tmdb_episode.xml")));
        String detailAdapter = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "TmdbEpisodeAdapter.java")));
        assertMarquee("detail episode name", detailLayout, "@+id/index");
        assertConstrainedMarquee("detail episode date", detailLayout, "@+id/date");
        assertConstrainedMarquee("detail episode badge", detailLayout, "@+id/badge");
        assertConstrainedMarquee("detail episode file size", detailLayout, "@+id/fileSize");
        assertTrue("detail episode cards must activate the name and related labels together",
                detailAdapter.contains("holder.binding.index.setSelected(active);")
                        && detailAdapter.contains("holder.binding.date.setSelected(active);")
                        && detailAdapter.contains("holder.binding.badge.setSelected(active);")
                        && detailAdapter.contains("holder.binding.fileSize.setSelected(active);"));
    }

    @Test
    public void mobileOriginalEnhancedFallbackShowsTwoLineNativeTitlesWithoutChangingDirectMode() throws Exception {
        String gridLayout = read(findMobileResPath().resolve(Path.of("layout", "adapter_episode_grid.xml")));
        String adapter = read(findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "EpisodeAdapter.java")));
        String gridHolder = read(findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "holder", "EpisodeGridHolder.java")));
        String activity = read(findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));
        Element text = findById(parseLayout(gridLayout), "@+id/text");

        assertEquals("40dp", androidAttribute(text, "layout_height"));
        assertEquals("true", androidAttribute(text, "singleLine"));
        assertTrue("only the original-enhanced unmatched grid may opt into the expanded native button",
                activity.contains("setNativeGridExpanded(mEpisodeGridMode && !useTmdbCard && isOriginalEnhancedEpisodeFallback())")
                        && adapter.contains("public void setNativeGridExpanded(boolean nativeGridExpanded)")
                        && adapter.contains("holder.setNativeGridExpanded(nativeGridExpanded);"));
        assertTrue("expanded native grid titles must use two lines and end ellipsis",
                gridHolder.contains("if (multiLine)")
                        && gridHolder.contains("binding.text.setMaxLines(2);")
                        && gridHolder.contains("binding.text.setEllipsize(TextUtils.TruncateAt.END);"));
        assertTrue("direct/native mode must retain its compact single-line marquee",
                gridHolder.contains("binding.text.setSingleLine(true);")
                        && gridHolder.contains("focused ? TextUtils.TruncateAt.MARQUEE : TextUtils.TruncateAt.START"));
        assertTrue("a wide native button keeps the badge beside the title like upstream",
                gridLayout.contains("android:id=\"@+id/nativeFileSize\"")
                        && gridHolder.contains("else binding.text.setPadding(visible ? ResUtil.dp2px(96) : horizontalPadding, 0, horizontalPadding, 0);"));
        assertTrue("a narrow native button must stack the badge above the title instead of squeezing it to zero width",
                gridHolder.contains("boolean stacked = !TextUtils.isEmpty(fileSize) && !hasRoomForSideFileSize();")
                        && gridHolder.contains("if (stacked) binding.text.setPadding(horizontalPadding, ResUtil.dp2px(28), horizontalPadding, 0);")
                        && gridHolder.contains("Gravity.START | (stacked ? Gravity.TOP : Gravity.CENTER_VERTICAL)"));
        assertTrue("the first bind happens before addView, so stacking must be decided from the injected RecyclerView span, not the item's own width",
                adapter.contains("listener, parent);")
                        && gridHolder.contains("int width = estimateItemWidth();")
                        && gridHolder.contains("if (width <= 0) width = binding.getRoot().getWidth();")
                        && gridHolder.contains("if (!(recycler instanceof RecyclerView view)) return 0;")
                        && gridHolder.contains("gridManager.getSpanCount()"));
        assertTrue("the estimate must be re-checked once layout settles so a span change cannot leave a squeezed title",
                gridHolder.contains("if (boundItem == null) return;")
                        && gridHolder.contains("applyNativeLayout(EpisodeAdapter.getNativeFileSize(boundItem));"));
        assertTrue("the deferred re-check must restore the marquee, because applyNativeLayout resets ellipsize",
                gridHolder.contains("applyNativeLayout(EpisodeAdapter.getNativeFileSize(boundItem));")
                        && gridHolder.contains("setNativeActive(binding.text.hasFocus() || binding.text.isActivated());"));
        assertTrue("the deferred re-check must read the currently bound item, never a captured file size, or a recycled holder shows the previous episode's badge",
                !gridHolder.contains("binding.text.post(() -> applyNativeLayout(fileSize));")
                        && gridHolder.contains("boundItem = item;")
                        && gridHolder.contains("boundItem = null;"));
        assertTrue("the card branch must clear the native button's stacked state so a recycled holder cannot inherit it",
                gridHolder.contains("nativeMultiLine = false;")
                        && gridHolder.contains("binding.text.setMinHeight(0);"));
        assertTrue("two-line titles must keep the END ellipsis, so the marquee override has to follow the effective multi-line state, not just the mode flag",
                gridHolder.contains("if (!nativeMultiLine) {")
                        && gridHolder.contains("this.nativeMultiLine = multiLine;"));
        assertTrue("a stacked button needs two lines and extra height for the badge",
                gridHolder.contains("boolean multiLine = nativeGridExpanded || stacked;")
                        && gridHolder.contains("binding.text.setMinHeight(stacked ? ResUtil.dp2px(76)"));
        assertTrue("the fallback must keep click and long-press behavior on the native button",
                gridHolder.contains("binding.text.setOnClickListener(v -> listener.onItemClick(item));")
                        && gridHolder.contains("EpisodeAdapter.bindNativeTitlePopup(binding.text, item);"));
    }

    @Test
    public void mobilePlaybackEpisodeNamesAndLabelsMarqueeWhenActive() throws Exception {
        String mobileGridLayout = read(findMobileResPath().resolve(Path.of("layout", "adapter_episode_grid.xml")));
        String mobileHoriLayout = read(findMobileResPath().resolve(Path.of("layout", "adapter_episode_hori.xml")));
        String mobileGridHolder = read(findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "holder", "EpisodeGridHolder.java")));
        String mobileHoriHolder = read(findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "holder", "EpisodeHoriHolder.java")));
        assertMarquee("mobile grid episode name", mobileGridLayout, "@+id/cardTitle");
        assertConstrainedMarquee("mobile grid episode meta", mobileGridLayout, "@+id/meta");
        assertConstrainedMarquee("mobile grid episode file size", mobileGridLayout, "@+id/fileSize");
        assertMarquee("mobile horizontal episode text", mobileHoriLayout, "@+id/text");
        assertMarquee("mobile horizontal episode name", mobileHoriLayout, "@+id/cardTitle");
        assertConstrainedMarquee("mobile horizontal episode file size", mobileHoriLayout, "@+id/fileSize");
        assertTrue("mobile grid episode cards must activate the name and related labels together",
                mobileGridHolder.contains("binding.cardTitle.setSelected(active);")
                        && mobileGridHolder.contains("binding.meta.setSelected(active);")
                        && mobileGridHolder.contains("binding.fileSize.setSelected(active);"));
        assertTrue("mobile horizontal episode items must activate overflowing text",
                mobileHoriHolder.contains("binding.text.setOnFocusChangeListener")
                        && mobileHoriHolder.contains("binding.cardTitle.setSelected(active);")
                        && mobileHoriHolder.contains("binding.fileSize.setSelected(active);"));
        assertTrue("recycled mobile holders must clear hidden text marquee state",
                mobileGridHolder.contains("binding.text.setActivated(false);")
                        && mobileGridHolder.contains("clearNativeActive();")
                        && mobileHoriHolder.contains("binding.text.setActivated(false);")
                        && mobileHoriHolder.contains("setTextMarquee(false);")
                        && mobileHoriHolder.contains("binding.text.isActivated()"));
    }

    @Test
    public void tvPlaybackEpisodeNamesAndLabelsMarqueeWhenActive() throws Exception {
        String leanbackTextLayout = read(findLeanbackResPath().resolve(Path.of("layout", "adapter_episode.xml")));
        String leanbackCardLayout = read(findLeanbackResPath().resolve(Path.of("layout", "adapter_episode_card.xml")));
        String leanbackAdapter = read(findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "EpisodeAdapter.java")));
        assertMarquee("TV episode text", leanbackTextLayout, "@+id/text");
        assertMarquee("TV episode name", leanbackCardLayout, "@+id/cardTitle");
        assertConstrainedMarquee("TV episode date", leanbackCardLayout, "@+id/dateBadge");
        assertConstrainedMarquee("TV episode file size", leanbackCardLayout, "@+id/fileSize");
        assertTrue("TV episode items must activate overflowing text while selected or focused",
                leanbackAdapter.contains("textView.setOnFocusChangeListener")
                        && leanbackAdapter.contains("binding.cardTitle.setSelected(active);")
                        && leanbackAdapter.contains("binding.dateBadge.setSelected(active);")
                        && leanbackAdapter.contains("binding.fileSize.setSelected(active);"));
    }

    @Test
    public void detailAndPlaybackSourceLabelsMarqueeWhenActive() throws Exception {
        assertConstrainedMarquee("mobile source label", read(findMobileResPath().resolve(Path.of("layout", "adapter_flag.xml"))), "@+id/text");
        assertConstrainedMarquee("mobile TMDB source label", read(findMobileResPath().resolve(Path.of("layout", "adapter_flag_tmdb.xml"))), "@+id/text");
        assertConstrainedMarquee("TV source label", read(findLeanbackResPath().resolve(Path.of("layout", "adapter_flag.xml"))), "@+id/text");

        String detailActivity = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java")));
        assertTrue("programmatic detail labels must be width-constrained marquees",
                detailActivity.contains("button.setMaxWidth(ResUtil.dp2px(CHIP_MAX_WIDTH_DP));")
                        && detailActivity.contains("button.setEllipsize(TextUtils.TruncateAt.MARQUEE);")
                        && detailActivity.contains("button.setMarqueeRepeatLimit(-1);")
                        && detailActivity.contains("button.setHorizontallyScrolling(true);")
                        && detailActivity.contains("button.setSingleLine(true);"));
        assertTrue("programmatic detail labels must marquee while visible on mobile or active on TV",
                detailActivity.contains("button.setSelected(!Util.isLeanback() || selected || focused);"));
    }

    @Test
    public void mobilePlaybackCompactsEpisodeTitlesOffMainThread() throws Exception {
        String adapter = read(findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "EpisodeAdapter.java")));
        String activity = read(findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java")));
        int spanStart = activity.indexOf("private int getEpisodeSpan(List<Episode> items, boolean useTmdbCard)");
        int spanEnd = activity.indexOf("private List<Episode> getEpisodeDisplayItems", spanStart);

        assertTrue("mobile playback must publish the list before background title work",
                adapter.indexOf("mItems.addAll(snapshot);") < adapter.indexOf("Task.submit(() ->"));
        assertTrue("mobile playback must compute and apply compact titles across a worker/UI boundary",
                adapter.contains("EpisodeTitleCompact.computeRaw(rawNames, compact)")
                        && adapter.contains("App.post(() -> finishTitleCompaction"));
        assertTrue("VideoActivity must opt into asynchronous title compaction",
                activity.contains("mEpisodeAdapter.setOnTitleReadyListener(this::onEpisodeTitlesReady);"));
        assertTrue(spanStart >= 0 && spanEnd > spanStart);
        assertFalse("episode span calculation must not synchronously compact the full list",
                activity.substring(spanStart, spanEnd).contains("EpisodeTitleCompact.apply("));
    }

    @Test
    public void mobilePlaybackCoalescesIdenticalTitleRequests() throws Exception {
        String adapter = read(findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "EpisodeAdapter.java")));
        int requestStart = adapter.indexOf("private void requestTitleCompaction");
        int finishStart = adapter.indexOf("private void finishTitleCompaction", requestStart);
        String request = adapter.substring(requestStart, finishStart);

        assertTrue("identical in-flight title work must be detected before another task is submitted",
                request.contains("isPendingTitleRequest(snapshot, rawNames, compact)")
                        && request.indexOf("isPendingTitleRequest(snapshot, rawNames, compact)") < request.indexOf("Task.submit(() ->"));
    }

    @Test
    public void mobilePlaybackRefreshesTmdbMetadataWithoutRecompactingTitles() throws Exception {
        String adapter = read(findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "EpisodeAdapter.java")));
        int refresh = adapter.indexOf("public void refreshMetadata(List<Episode> items)");
        int next = adapter.indexOf("private void requestTitleCompaction", refresh);
        String body = refresh >= 0 && next > refresh ? adapter.substring(refresh, next) : "";

        assertTrue("TMDB metadata refresh must cancel stale background title work",
                body.contains("invalidateTitleRequest();"));
        assertTrue("TMDB metadata refresh should reuse the current page when its episode identities are unchanged",
                body.contains("hasSameItems(snapshot)"));
        assertTrue("metadata-only updates should rebind existing holders instead of replacing the whole adapter",
                body.contains("notifyItemRangeChanged(0, getItemCount());"));
        assertFalse("TMDB metadata refresh must not compact scraped titles again",
                body.contains("EpisodeTitleCompact") || body.contains("Task.submit("));
    }


    @Test
    public void mobileEpisodeDetailPhotosOpenOnFirstTapAcrossEpisodeModes() throws Exception {
        String photoLayout = read(findMainResPath().resolve(Path.of("layout", "adapter_tmdb_photo.xml")));
        String stillAdapter = read(findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "EpisodeStillAdapter.java")));
        String detailDialog = read(findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "EpisodeDetailDialog.java")));
        String gridHolder = read(findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "holder", "EpisodeGridHolder.java")));
        String horiHolder = read(findMobileJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "holder", "EpisodeHoriHolder.java")));
        String tvAdapter = read(findLeanbackJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "EpisodePhotoAdapter.java")));

        assertTrue("shared photo cards may remain touch-focusable for TV navigation",
                photoLayout.contains("android:focusable=\"true\"")
                        && photoLayout.contains("android:focusableInTouchMode=\"true\""));
        assertTrue("mobile episode stills must bypass first-tap focus and open the original immediately",
                stillAdapter.contains("itemView.setFocusable(false);")
                        && stillAdapter.contains("itemView.setFocusableInTouchMode(false);")
                        && stillAdapter.contains("itemView.setOnClickListener"));
        assertTrue("both mobile grid and horizontal episode modes must open the same detail dialog",
                gridHolder.contains("EpisodeDetailDialog.show(activity, item);")
                        && horiHolder.contains("EpisodeDetailDialog.show(activity, item);"));
        assertTrue("the shared mobile episode dialog must route every theme through the immediate-touch adapter",
                detailDialog.contains("new EpisodeStillAdapter(photos")
                        && detailDialog.contains("PhotoViewerDialog.show(activity, photos, position, null)"));
        assertFalse("TV episode photos must keep their focus-first navigation behavior",
                tvAdapter.contains("itemView.setFocusable(false);")
                        || tvAdapter.contains("itemView.setFocusableInTouchMode(false);"));
    }

    private static void assertMarquee(String owner, String layout, String id) throws Exception {
        Element element = findById(parseLayout(layout), id);
        assertTrue(owner + " must marquee only when its text overflows",
                "marquee".equals(androidAttribute(element, "ellipsize"))
                        && "marquee_forever".equals(androidAttribute(element, "marqueeRepeatLimit"))
                        && "true".equals(androidAttribute(element, "scrollHorizontally"))
                        && "true".equals(androidAttribute(element, "singleLine")));
    }

    private static void assertConstrainedMarquee(String owner, String layout, String id) throws Exception {
        Element element = findById(parseLayout(layout), id);
        assertMarquee(owner, layout, id);
        assertTrue(owner + " must have a finite width so overflow can occur",
                !"wrap_content".equals(androidAttribute(element, "layout_width"))
                        || !androidAttribute(element, "maxWidth").isEmpty());
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

    private static Path findMainJavaPath() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "java");
    }

    private static Path findMainResPath() {
        Path moduleRelative = Path.of("src", "main", "res");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "res");
    }
}
