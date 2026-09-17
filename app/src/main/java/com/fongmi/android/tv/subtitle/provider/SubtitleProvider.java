package com.fongmi.android.tv.subtitle.provider;

import com.fongmi.android.tv.subtitle.model.SubtitleAsset;
import com.fongmi.android.tv.subtitle.model.SubtitleCandidate;
import com.fongmi.android.tv.subtitle.model.SubtitleContext;
import com.fongmi.android.tv.subtitle.model.SubtitleQuery;

import java.util.List;

public interface SubtitleProvider {

    String getName();

    boolean isEnabled();

    default boolean isQueryIndependent() {
        return false;
    }

    List<SubtitleCandidate> search(SubtitleQuery query, SubtitleContext context) throws Exception;

    SubtitleAsset resolve(SubtitleCandidate candidate, SubtitleContext context) throws Exception;
}
