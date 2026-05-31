package com.fongmi.android.tv.ui.presenter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.AdapterCollectListBinding;
import com.fongmi.android.tv.ui.base.BaseVodHolder;
import com.fongmi.android.tv.utils.ImgUtil;

public class CollectListPresenter extends Presenter {

    private final VodPresenter.OnClickListener listener;

    public CollectListPresenter(VodPresenter.OnClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent) {
        return new Holder(AdapterCollectListBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), listener);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, Object item) {
        ((Holder) viewHolder).initView((Vod) item);
    }

    @Override
    public void onUnbindViewHolder(@NonNull ViewHolder viewHolder) {
        ((Holder) viewHolder).unbind();
    }

    private static class Holder extends BaseVodHolder {

        private final VodPresenter.OnClickListener listener;
        private final AdapterCollectListBinding binding;

        Holder(@NonNull AdapterCollectListBinding binding, VodPresenter.OnClickListener listener) {
            super(binding.getRoot());
            this.binding = binding;
            this.listener = listener;
        }

        @Override
        public void initView(Vod item) {
            binding.name.setText(item.getName());
            binding.site.setText(item.getSiteName());
            binding.remark.setText(item.getRemarks());
            binding.site.setVisibility(item.getSiteVisible());
            binding.remark.setVisibility(item.getRemarkVisible());
            binding.getRoot().setOnClickListener(v -> listener.onItemClick(item));
            binding.getRoot().setOnLongClickListener(v -> listener.onLongClick(item));
            ImgUtil.load(item.getName(), item.getPic(), binding.image);
        }

        @Override
        public void unbind() {
            Glide.with(binding.image).clear(binding.image);
        }
    }
}
