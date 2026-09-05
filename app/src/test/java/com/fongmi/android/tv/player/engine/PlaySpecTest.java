package com.fongmi.android.tv.player.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import androidx.media3.common.C;

import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Sub;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class PlaySpecTest {

    @Test
    public void setSub_prependsSelectedSubtitleAndKeepsExistingSubtitles() {
        Sub english = Sub.create("English", "file:///english.srt", "en", "application/x-subrip");
        Sub assrt = Sub.create("assrt", "file:///assrt.srt", "", "application/x-subrip");
        Sub translated = Sub.create("AI 中文字幕", "file:///translated.zh-Hans.srt", "zh-Hans", "application/x-subrip");
        PlaySpec spec = specWithSubs(english, assrt);

        spec.setSub(translated);

        assertEquals(3, spec.getSubs().size());
        assertSame(translated, spec.getSubs().get(0));
        assertSame(english, spec.getSubs().get(1));
        assertSame(assrt, spec.getSubs().get(2));
        assertEquals(C.SELECTION_FLAG_DEFAULT, translated.getRawFlag());
        assertEquals(C.SELECTION_FLAG_AUTOSELECT, english.getRawFlag());
        assertEquals(C.SELECTION_FLAG_AUTOSELECT, assrt.getRawFlag());
    }

    @Test
    public void setSub_reselectsExistingSubtitleWithoutDuplicatingIt() {
        Sub english = Sub.create("English", "file:///english.srt", "en", "application/x-subrip");
        Sub translated = Sub.create("AI 中文字幕", "file:///translated.zh-Hans.srt", "zh-Hans", "application/x-subrip");
        PlaySpec spec = specWithSubs(english, translated);

        spec.setSub(translated);

        assertEquals(2, spec.getSubs().size());
        assertSame(translated, spec.getSubs().get(0));
        assertSame(english, spec.getSubs().get(1));
        assertEquals(C.SELECTION_FLAG_DEFAULT, translated.getRawFlag());
        assertEquals(C.SELECTION_FLAG_AUTOSELECT, english.getRawFlag());
    }

    @Test
    public void setSub_preservesNonDefaultSelectionFlags() {
        Sub forced = Sub.create("Forced", "file:///forced.srt", "en", "application/x-subrip");
        forced.setFlag(C.SELECTION_FLAG_DEFAULT | C.SELECTION_FLAG_FORCED);
        Sub translated = Sub.create("AI 中文字幕", "file:///translated.zh-Hans.srt", "zh-Hans", "application/x-subrip");
        translated.setFlag(C.SELECTION_FLAG_FORCED);
        PlaySpec spec = specWithSubs(forced);

        spec.setSub(translated);

        assertEquals(C.SELECTION_FLAG_DEFAULT | C.SELECTION_FLAG_FORCED, translated.getRawFlag());
        assertEquals(C.SELECTION_FLAG_FORCED, forced.getRawFlag());
    }

    private PlaySpec specWithSubs(Sub... subs) {
        Result result = new Result();
        result.setPlayUrl("https://play/");
        result.setUrl("movie.m3u8");
        result.setSubs(new ArrayList<>(List.of(subs)));
        return PlaySpec.from(result, "playback-1", null);
    }
}
