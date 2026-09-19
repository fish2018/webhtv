package com.fongmi.android.tv.player.exo.subtitle;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import androidx.media3.common.text.Cue;

import junit.framework.TestCase;

import java.util.Collections;
import java.util.List;

public class SecondarySubtitleCuesTest extends TestCase {
    public void testTextIsStackedAtTopWithoutChangingPrimaryStylesOrBidiText() {
        SpannableString text = new SpannableString("中文 English\nالعربية");
        text.setSpan(new ForegroundColorSpan(Color.RED), 0, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new StyleSpan(Typeface.BOLD), 0, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        Cue original = new Cue.Builder().setText(text).setLine(0.9f, Cue.LINE_TYPE_FRACTION)
                .setLineAnchor(Cue.ANCHOR_TYPE_END).setTextSize(0.2f, Cue.TEXT_SIZE_TYPE_FRACTIONAL).build();
        List<Cue> result = SecondarySubtitleCues.atTop(List.of(original, new Cue.Builder().setText("second line").build()));
        assertEquals(1, result.size());
        Cue secondary = result.get(0);
        assertEquals("中文 English\nالعربية\nsecond line", secondary.text.toString());
        assertFalse(secondary.text instanceof Spanned);
        assertEquals(Cue.ANCHOR_TYPE_START, secondary.lineAnchor);
        assertTrue(secondary.line < 0.1f);
        assertEquals(Cue.DIMEN_UNSET, secondary.textSize, 0f);
        assertTrue(original.text instanceof Spanned);
        assertEquals(2, ((Spanned) original.text).getSpans(0, 2, Object.class).length);
        assertEquals(0.9f, original.line, 0.0001f);
    }

    public void testBitmapRegionsKeepPixelsAndRelativeLayoutWhenMovedToTop() {
        Bitmap bitmap = Bitmap.createBitmap(8, 2, Bitmap.Config.ARGB_8888);
        try {
            Cue lower = new Cue.Builder().setBitmap(bitmap).setPosition(0.2f).setSize(0.25f).setBitmapHeight(0.1f)
                    .setLine(0.9f, Cue.LINE_TYPE_FRACTION).setLineAnchor(Cue.ANCHOR_TYPE_END).build();
            Cue upper = lower.buildUpon().setPosition(0.6f).setBitmapHeight(0.04f)
                    .setLine(0.85f, Cue.LINE_TYPE_FRACTION).setLineAnchor(Cue.ANCHOR_TYPE_START).build();
            List<Cue> result = SecondarySubtitleCues.atTop(List.of(lower, upper));
            assertEquals(2, result.size());
            assertSame(bitmap, result.get(0).bitmap);
            assertSame(bitmap, result.get(1).bitmap);
            assertEquals(0.08f, result.get(0).line, 0.0001f);
            assertEquals(0.13f, result.get(1).line, 0.0001f);
            assertEquals(0.2f, result.get(0).position, 0.0001f);
            assertEquals(0.6f, result.get(1).position, 0.0001f);
            assertEquals(lower.size, result.get(0).size, 0f);
            assertEquals(lower.bitmapHeight, result.get(0).bitmapHeight, 0f);
        } finally {
            bitmap.recycle();
        }
    }

    public void testEmptyOutputClearsRatherThanCreatingAnEmptyTextBox() {
        assertTrue(SecondarySubtitleCues.atTop(Collections.emptyList()).isEmpty());
        assertTrue(SecondarySubtitleCues.atTop(List.of(new Cue.Builder().setText("").build())).isEmpty());
    }
}
