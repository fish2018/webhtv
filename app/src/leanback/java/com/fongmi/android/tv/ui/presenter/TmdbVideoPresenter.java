package com.fongmi.android.tv.ui.presenter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.fongmi.android.tv.bean.TmdbVideo;
import com.fongmi.android.tv.databinding.AdapterTmdbVideoBinding;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.ResUtil;

public class TmdbVideoPresenter extends Presenter {

    public interface OnClickListener {
        void onItemClick(TmdbVideo item);
    }

    private final OnClickListener listener;

    public TmdbVideoPresenter(OnClickListener listener) {
        this.listener = listener;
    }

    @Override
    public Presenter.ViewHolder onCreateViewHolder(ViewGroup parent) {
        return new ViewHolder(AdapterTmdbVideoBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder viewHolder, Object item) {
        TmdbVideo video = (TmdbVideo) item;
        ViewHolder holder = (ViewHolder) viewHolder;
        String name = video.getName().isEmpty() ? video.getDisplayType() : video.getName();
        holder.binding.title.setText(name);
        holder.binding.subtitle.setText(video.getDisplayType() + " ? " + video.getScopeLabel());
        ImgUtil.load(name, video.getThumbnailUrl(), holder.binding.poster, true, 300, 169);
        setOnClickListener(holder, view -> {
            if (listener != null) listener.onItemClick(video);
        });
        holder.view.setOnFocusChangeListener((view, focused) -> {
            float scale = focused ? 1.04f : 1.0f;
            view.animate().scaleX(scale).scaleY(scale).setDuration(120).start();
            holder.binding.getRoot().setStrokeWidth(ResUtil.dp2px(focused ? 2 : 1));
            holder.binding.getRoot().setStrokeColor(focused ? 0xFFFFD166 : 0x33FFFFFF);
        });
    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
        viewHolder.view.setOnFocusChangeListener(null);
    }

    static final class ViewHolder extends Presenter.ViewHolder {
        private final AdapterTmdbVideoBinding binding;

        ViewHolder(@NonNull AdapterTmdbVideoBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
