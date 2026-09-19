package com.fongmi.android.tv.player.exo.subtitle;

import android.text.Layout;

import androidx.media3.common.text.Cue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** MPV's default secondary-sub-ass-override=strip policy, applied to already decoded Cues. */
final class SecondarySubtitleCues {
    static final float TOP_PADDING = 0.08f;

    private SecondarySubtitleCues() { }

    static List<Cue> atTop(List<Cue> input) {
        if (input.isEmpty()) return Collections.emptyList();
        StringBuilder text = new StringBuilder();
        float firstBitmapTop = Float.POSITIVE_INFINITY;
        for (Cue cue : input) {
            if (cue.text != null && cue.text.length() > 0) {
                if (text.length() > 0) text.append('\n');
                // Converting to String strips spans, including ASS font/size/colour overrides.
                text.append(cue.text.toString());
            } else if (cue.bitmap != null) {
                firstBitmapTop = Math.min(firstBitmapTop, bitmapTop(cue));
            }
        }
        List<Cue> output = new ArrayList<>();
        if (text.length() > 0) {
            output.add(new Cue.Builder().setText(text.toString())
                    .setTextAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setPosition(0.5f).setPositionAnchor(Cue.ANCHOR_TYPE_MIDDLE)
                    .setLine(TOP_PADDING, Cue.LINE_TYPE_FRACTION).setLineAnchor(Cue.ANCHOR_TYPE_START)
                    .setSize(0.9f).build());
        }
        for (Cue cue : input) {
            if (cue.bitmap == null) continue;
            // Move the whole bitmap composition, retaining its width, height and relative regions.
            output.add(cue.buildUpon()
                    .setLine(TOP_PADDING + bitmapTop(cue) - firstBitmapTop, Cue.LINE_TYPE_FRACTION)
                    .setLineAnchor(Cue.ANCHOR_TYPE_START).build());
        }
        return Collections.unmodifiableList(output);
    }

    private static float bitmapTop(Cue cue) {
        float line = cue.lineType == Cue.LINE_TYPE_FRACTION && cue.line != Cue.DIMEN_UNSET
                && Float.isFinite(cue.line) ? cue.line : 0f;
        float height = cue.bitmapHeight != Cue.DIMEN_UNSET && Float.isFinite(cue.bitmapHeight)
                ? cue.bitmapHeight : 0f;
        if (cue.lineAnchor == Cue.ANCHOR_TYPE_END) line -= height;
        else if (cue.lineAnchor == Cue.ANCHOR_TYPE_MIDDLE) line -= height / 2f;
        return line;
    }
}
