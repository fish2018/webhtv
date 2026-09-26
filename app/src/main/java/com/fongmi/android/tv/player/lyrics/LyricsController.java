package com.fongmi.android.tv.player.lyrics;

import android.net.Uri;
import android.text.TextUtils;

import androidx.media3.common.C;
import androidx.media3.common.MediaMetadata;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.ui.custom.LyricsOverlayView;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class LyricsController {

    private static final String TAG = "lyrics-ui";
    private static final long DURATION_WAIT_DELAY_MS = 350;
    private static final int DURATION_WAIT_LIMIT = 4;
    private static final List<String> AUDIO_EXTS = List.of(".mp3", ".flac", ".wav", ".m4a", ".aac", ".ogg", ".oga", ".opus", ".ape", ".wma", ".alac", ".amr", ".mka");

    private final LyricsRepository repository = new LyricsRepository();
    private final LyricsOverlayView view;
    private LyricsOverlayView secondaryView;
    private Listener listener;
    private List<LyricsLine> lines = Collections.emptyList();
    private LyricsResult result;
    private String loadingSignature;
    private String activeSignature;
    private String emptySignature;
    private String durationWaitSignature;
    private int durationWaitAttempts;
    private Runnable durationWaitRunnable;
    private int sequence;

    public interface Callback {
        void onResult(LyricsResult result);
    }

    public interface SearchCallback {
        void onResult(List<LyricsResult> results, boolean complete);
    }

    public interface Listener {
        void onLyricsChanged(LyricsResult result, List<LyricsLine> lines);
    }

    public LyricsController(LyricsOverlayView view) {
        this.view = view;
    }

    public void setSecondaryView(LyricsOverlayView view) {
        this.secondaryView = view;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
        if (result != null && !lines.isEmpty()) notifyListener();
    }

    public void refresh(PlayerManager player) {
        refresh(player, isAudioOnly(player));
    }

    public void refresh(PlayerManager player, boolean audioOnly) {
        if (player == null || !audioOnly) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "refresh clear player=%s audioOnly=%s", player != null, audioOnly);
            cancelDurationWait();
            clear();
            return;
        }
        LyricsRequest request = LyricsRequest.from(player);
        if (!request.isValid()) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "refresh invalid parse=%s", request.getParseInfo());
            cancelDurationWait();
            clear();
            return;
        }
        String signature = request.stableSignature();
        if (signature.equals(activeSignature) || signature.equals(loadingSignature) || signature.equals(emptySignature)) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "refresh skip title=%s artist=%s parse=%s active=%s loading=%s empty=%s", request.getTitle(), request.getArtist(), request.getParseInfo(), signature.equals(activeSignature), signature.equals(loadingSignature), signature.equals(emptySignature));
            return;
        }
        if (shouldWaitDuration(player, audioOnly, request)) return;
        if (request.getDurationMs() > 0) cancelDurationWait();
        if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "refresh start title=%s artist=%s parse=%s duration=%d", request.getTitle(), request.getArtist(), request.getParseInfo(), request.getDurationMs());
        loadingSignature = signature;
        activeSignature = null;
        lines = Collections.emptyList();
        clearViews();
        int current = ++sequence;
        repository.loadPreferWord(request, result -> {
            if (current != sequence) {
                if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "refresh drop stale title=%s current=%d sequence=%d", request.getTitle(), current, sequence);
                return;
            }
            loadingSignature = null;
            if (result == null || !result.isValid()) {
                if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "refresh empty title=%s artist=%s parse=%s", request.getTitle(), request.getArtist(), request.getParseInfo());
                emptySignature = signature;
                clearViews();
                return;
            }
            ArrayList<LyricsLine> parsed = new ArrayList<>(result.getLines(request.getDurationMs()));
            if (parsed.isEmpty()) {
                if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "refresh parsed empty source=%s track=%s artist=%s synced=%s word=%s lyrics=%d", result.getSource(), result.getTrackName(), result.getArtistName(), result.isSynced(), result.hasWordTiming(), result.getLyrics() == null ? 0 : result.getLyrics().length());
                emptySignature = signature;
                clearViews();
                return;
            }
            activeSignature = signature;
            lines = parsed;
            setLyrics(result, parsed);
            update(player);
            if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "refresh applied source=%s track=%s artist=%s lines=%d synced=%s word=%s", result.getSource(), result.getTrackName(), result.getArtistName(), parsed.size(), result.isSynced(), result.hasWordTiming());
        });
    }

    public boolean hasChoice(PlayerManager player) {
        if (player == null) return false;
        LyricsRequest request = LyricsRequest.from(player);
        return request.isValid() && repository.hasChoice(request);
    }

    public boolean hasChoice(LyricsRequest request) {
        return request != null && request.isValid() && repository.hasChoice(request);
    }

    public void reload(PlayerManager player, boolean audioOnly, String keyword, Callback callback) {
        if (player == null || !audioOnly) {
            clear();
            if (callback != null) callback.onResult(null);
            return;
        }
        LyricsRequest request = LyricsRequest.from(player).withKeyword(keyword);
        if (!request.isValid()) {
            clear();
            if (callback != null) callback.onResult(null);
            return;
        }
        String signature = request.stableSignature();
        loadingSignature = signature;
        activeSignature = null;
        emptySignature = null;
        lines = Collections.emptyList();
        clearViews();
        notifyListener();
        int current = ++sequence;
        repository.loadPreferWord(request, true, result -> {
            if (current != sequence) return;
            loadingSignature = null;
            if (result == null || !result.isValid()) {
                emptySignature = signature;
                clearViews();
                if (callback != null) callback.onResult(null);
                return;
            }
            ArrayList<LyricsLine> parsed = new ArrayList<>(result.getLines(request.getDurationMs()));
            if (parsed.isEmpty()) {
                emptySignature = signature;
                clearViews();
                if (callback != null) callback.onResult(null);
                return;
            }
            activeSignature = signature;
            lines = parsed;
            setLyrics(result, parsed);
            update(player);
            if (callback != null) callback.onResult(result);
        });
    }

    public void search(PlayerManager player, boolean audioOnly, String keyword, SearchCallback callback) {
        if (player == null || !audioOnly) {
            if (callback != null) callback.onResult(Collections.emptyList(), true);
            return;
        }
        LyricsRequest request = LyricsRequest.from(player).withKeyword(keyword);
        search(request, callback);
    }

    public void search(LyricsRequest request, SearchCallback callback) {
        if (request == null || !request.isValid()) {
            if (callback != null) callback.onResult(Collections.emptyList(), true);
            return;
        }
        repository.search(request, (results, complete) -> {
            if (callback != null) callback.onResult(results, complete);
        });
    }

    public void apply(PlayerManager player, LyricsResult result, boolean remember, Callback callback) {
        if (player == null || result == null || !result.isValid()) {
            if (callback != null) callback.onResult(null);
            return;
        }
        LyricsRequest request = LyricsRequest.from(player);
        ArrayList<LyricsLine> parsed = new ArrayList<>(result.getLines(request.getDurationMs()));
        if (parsed.isEmpty()) {
            if (callback != null) callback.onResult(null);
            return;
        }
        int current = ++sequence;
        loadingSignature = null;
        emptySignature = null;
        activeSignature = request.stableSignature();
        lines = parsed;
        setLyrics(result, parsed);
        update(player);
        if (remember) repository.remember(request, result);
        if (current == sequence && callback != null) callback.onResult(result);
    }

    public boolean setInlineLyrics(String signature, String title, String artist, String lyrics, long durationMs, long positionMs) {
        if (!hasTimedLyrics(lyrics)) return false;
        String key = Util.md5(TextUtils.join("|", new String[]{safe(signature), safe(title), safe(artist), String.valueOf(lyrics.hashCode())}));
        if (key.equals(activeSignature) && !lines.isEmpty()) {
            update(positionMs);
            return true;
        }
        int current = ++sequence;
        LyricsResult result = new LyricsResult("Detail", title, artist, "", lyrics, durationMs, true, 100);
        ArrayList<LyricsLine> parsed = new ArrayList<>(result.getLines(durationMs));
        if (current != sequence || parsed.isEmpty()) {
            clear();
            return false;
        }
        loadingSignature = null;
        emptySignature = null;
        activeSignature = key;
        lines = parsed;
        setLyrics(result, parsed);
        update(positionMs);
        if (!result.hasWordTiming()) upgradeInlineLyrics(current, key, signature, title, artist, durationMs, positionMs);
        return true;
    }

    private void upgradeInlineLyrics(int current, String inlineKey, String signature, String title, String artist, long durationMs, long positionMs) {
        LyricsRequest request = new LyricsRequest(signature, "", title, artist, "", durationMs);
        if (!request.isValid()) return;
        String requestSignature = request.stableSignature();
        if (requestSignature.equals(loadingSignature) || requestSignature.equals(emptySignature)) return;
        loadingSignature = requestSignature;
        repository.loadPreferWord(request, result -> {
            if (current != sequence || !inlineKey.equals(activeSignature)) return;
            if (requestSignature.equals(loadingSignature)) loadingSignature = null;
            if (result == null || !result.isValid() || !result.hasWordTiming()) {
                emptySignature = requestSignature;
                return;
            }
            ArrayList<LyricsLine> parsed = new ArrayList<>(result.getLines(durationMs));
            if (parsed.isEmpty() || !hasWordTiming(parsed)) {
                emptySignature = requestSignature;
                return;
            }
            emptySignature = null;
            lines = parsed;
            setLyrics(result, parsed);
            update(positionMs);
        });
    }

    public void update(long positionMs) {
        if (lines.isEmpty()) return;
        view.update(adjust(positionMs));
        if (secondaryView != null) secondaryView.update(adjust(positionMs));
    }

    public void update(PlayerManager player) {
        if (player == null) return;
        update(player, player.isPlaying());
    }

    public void update(PlayerManager player, boolean playing) {
        if (player == null || lines.isEmpty()) return;
        long position = adjust(player.getPosition());
        view.update(position, playing);
        if (secondaryView != null) secondaryView.update(position, playing);
    }

    public void refreshStyle() {
        view.refreshStyle();
        if (secondaryView != null) secondaryView.refreshStyle();
    }

    public List<LyricsLine> getLines() {
        return lines;
    }

    public void applySnapshot(LyricsResult result, List<LyricsLine> lines, PlayerManager player) {
        if (result == null || lines == null || lines.isEmpty()) {
            clear();
            return;
        }
        cancelDurationWait();
        sequence++;
        loadingSignature = null;
        emptySignature = null;
        activeSignature = "snapshot";
        this.lines = new ArrayList<>(lines);
        setLyrics(result, this.lines);
        update(player);
    }

    public void clear() {
        cancelDurationWait();
        sequence++;
        loadingSignature = null;
        activeSignature = null;
        emptySignature = null;
        lines = Collections.emptyList();
        clearViews();
    }

    private boolean shouldWaitDuration(PlayerManager player, boolean audioOnly, LyricsRequest request) {
        if (request.getDurationMs() > 0) return false;
        String contentSignature = request.contentSignature();
        if (!TextUtils.equals(durationWaitSignature, contentSignature)) {
            durationWaitSignature = contentSignature;
            durationWaitAttempts = 0;
        }
        if (durationWaitAttempts >= DURATION_WAIT_LIMIT) return false;
        durationWaitAttempts++;
        if (durationWaitRunnable != null) App.removeCallbacks(durationWaitRunnable);
        durationWaitRunnable = () -> {
            if (TextUtils.equals(durationWaitSignature, contentSignature)) refresh(player, audioOnly);
        };
        App.post(durationWaitRunnable, DURATION_WAIT_DELAY_MS);
        if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "refresh wait duration title=%s artist=%s parse=%s attempt=%d/%d", request.getTitle(), request.getArtist(), request.getParseInfo(), durationWaitAttempts, DURATION_WAIT_LIMIT);
        return true;
    }

    private void cancelDurationWait() {
        if (durationWaitRunnable != null) App.removeCallbacks(durationWaitRunnable);
        durationWaitRunnable = null;
        durationWaitSignature = null;
        durationWaitAttempts = 0;
    }

    private void setLyrics(LyricsResult result, List<LyricsLine> lines) {
        this.result = result;
        view.setLyrics(result, lines);
        if (secondaryView != null) secondaryView.setLyrics(result, lines);
        notifyListener();
    }

    private void clearViews() {
        if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "views clear");
        result = null;
        view.clear();
        if (secondaryView != null) secondaryView.clear();
    }

    private void notifyListener() {
        if (listener != null) listener.onLyricsChanged(result, new ArrayList<>(lines));
    }

    public void release() {
        clear();
    }

    public static boolean isAudioOnly(PlayerManager player) {
        if (player == null || player.isEmpty()) return false;
        if (player.haveTrack(C.TRACK_TYPE_VIDEO)) return false;
        if (player.haveTrack(C.TRACK_TYPE_AUDIO)) return true;
        return isAudioUrl(player.getUrl());
    }

    public static boolean isAudioContent(PlayerManager player) {
        if (player == null || player.isEmpty()) return false;
        return isAudioOnly(player) || isAudioUrl(player.getUrl()) || isMusicLike(player);
    }

    public static boolean isMusicLike(PlayerManager player) {
        if (player == null) return false;
        StringBuilder builder = new StringBuilder();
        append(builder, player.getKey());
        append(builder, player.getUrl());
        MediaMetadata metadata = player.getMetadata();
        if (metadata != null) {
            append(builder, metadata.title);
            append(builder, metadata.artist);
            append(builder, metadata.albumTitle);
        }
        return isMusicLikeText(builder.toString());
    }

    public static boolean isMusicLikeText(String text) {
        String lower = safe(text).toLowerCase(Locale.ROOT);
        return lower.contains("音乐") || lower.contains("音樂")
                || lower.contains("music") || lower.contains("song")
                || lower.contains("歌曲") || lower.contains("歌单") || lower.contains("歌單")
                || lower.contains("聽歌") || lower.contains("听歌")
                || lower.contains("有声") || lower.contains("有聲")
                || lower.contains("听书") || lower.contains("聽書")
                || lower.contains("[听]") || lower.contains("[聽]")
                || lower.contains("【听】") || lower.contains("【聽】")
                || lower.contains("audiobook") || lower.contains("audio book")
                || lower.contains("podcast") || lower.contains("播客")
                || lower.contains("电台") || lower.contains("電台") || lower.contains("radio");
    }

    public static boolean hasTimedLyrics(String text) {
        return LyricsParser.hasTimedLine(text);
    }

    private static boolean isAudioUrl(String url) {
        if (TextUtils.isEmpty(url)) return false;
        String path = url;
        try {
            Uri uri = Uri.parse(url);
            if (!TextUtils.isEmpty(uri.getPath())) path = uri.getPath();
        } catch (Exception ignored) {
        }
        String lower = path.toLowerCase(Locale.ROOT);
        for (String ext : AUDIO_EXTS) if (lower.endsWith(ext)) return true;
        return false;
    }

    private static String safe(String text) {
        return text == null ? "" : text;
    }

    private static void append(StringBuilder builder, CharSequence text) {
        if (TextUtils.isEmpty(text)) return;
        if (builder.length() > 0) builder.append(' ');
        builder.append(text);
    }

    private static boolean hasWordTiming(List<LyricsLine> lines) {
        for (LyricsLine line : lines) if (line.hasWords()) return true;
        return false;
    }

    private static long adjust(long positionMs) {
        return Math.max(0, positionMs + PlayerSetting.getLyricsTimeOffsetMs());
    }
}
