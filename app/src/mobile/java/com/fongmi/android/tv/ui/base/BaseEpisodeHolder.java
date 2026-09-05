package com.fongmi.android.tv.ui.base;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Episode;

public abstract class BaseEpisodeHolder extends RecyclerView.ViewHolder {

    public BaseEpisodeHolder(@NonNull View itemView) {
        super(itemView);
    }

    public void setUseTmdbCard(boolean useTmdbCard) {
    }

    public void setFallbackStillUrl(String fallbackStillUrl) {
    }

    public void setNativeGridExpanded(boolean nativeGridExpanded) {
    }

    public abstract void initView(Episode item);
}
