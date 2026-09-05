package com.fongmi.android.tv.ui.dialog;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.databinding.AdapterAudioCommentBinding;
import com.fongmi.android.tv.databinding.DialogAudioCommentBinding;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.AudioCommentUtil;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.Task;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class AudioCommentDialog extends BaseBottomSheetDialog {

    private static final int COMMENT_LIMIT = 30;

    private final CommentAdapter adapter;
    private DialogAudioCommentBinding binding;
    private String title;
    private String subtitle;
    private long durationMs;
    private boolean hot = true;
    private int request;

    public static AudioCommentDialog create(String title, String subtitle, long durationMs) {
        return new AudioCommentDialog().media(title, subtitle, durationMs);
    }

    public AudioCommentDialog() {
        this.adapter = new CommentAdapter();
    }

    private AudioCommentDialog media(String title, String subtitle, long durationMs) {
        this.title = Objects.toString(title, "");
        this.subtitle = Objects.toString(subtitle, "");
        this.durationMs = Math.max(0, durationMs);
        return this;
    }

    public void show(FragmentActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        for (Fragment fragment : activity.getSupportFragmentManager().getFragments()) if (fragment instanceof AudioCommentDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogAudioCommentBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        binding.recycler.setItemAnimator(null);
        binding.recycler.setAdapter(adapter);
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 8));
        updateTabs();
        load();
    }

    @Override
    protected void initEvent() {
        binding.hotTab.setOnClickListener(view -> select(true));
        binding.latestTab.setOnClickListener(view -> select(false));
        binding.refresh.setOnClickListener(view -> load());
        binding.close.setOnClickListener(view -> dismiss());
    }

    @Override
    protected boolean transparent() {
        return true;
    }

    private void select(boolean hot) {
        if (this.hot == hot && adapter.getItemCount() > 0) return;
        this.hot = hot;
        updateTabs();
        load();
    }

    private void updateTabs() {
        if (binding == null) return;
        binding.hotTab.setSelected(hot);
        binding.latestTab.setSelected(!hot);
        binding.hotTab.setAlpha(hot ? 1f : 0.72f);
        binding.latestTab.setAlpha(hot ? 0.72f : 1f);
    }

    private void load() {
        int current = ++request;
        setLoading(true);
        Task.execute(() -> {
            AudioCommentUtil.Page page = AudioCommentUtil.load(title, subtitle, durationMs, hot, COMMENT_LIMIT);
            App.post(() -> apply(current, page));
        });
    }

    private void setLoading(boolean loading) {
        if (binding == null) return;
        binding.loading.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.recycler.setVisibility(loading ? View.GONE : binding.recycler.getVisibility());
        binding.empty.setVisibility(loading ? View.GONE : binding.empty.getVisibility());
        binding.meta.setText(loading ? "正在匹配评论" : binding.meta.getText());
    }

    private void apply(int current, AudioCommentUtil.Page page) {
        if (binding == null || current != request) return;
        binding.loading.setVisibility(View.GONE);
        binding.meta.setText(page.getMeta());
        adapter.setItems(page.getComments());
        boolean empty = adapter.getItemCount() == 0;
        binding.recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.empty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.empty.setText(page.getMessage());
    }

    private static final class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.ViewHolder> {

        private final List<AudioCommentUtil.Comment> items = new ArrayList<>();

        void setItems(List<AudioCommentUtil.Comment> comments) {
            items.clear();
            if (comments != null) items.addAll(comments);
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(AdapterAudioCommentBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(items.get(position));
        }

        private static final class ViewHolder extends RecyclerView.ViewHolder {

            private final AdapterAudioCommentBinding binding;

            private ViewHolder(@NonNull AdapterAudioCommentBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }

            private void bind(AudioCommentUtil.Comment item) {
                binding.user.setText(item.getUser());
                binding.meta.setText(item.getMeta());
                binding.content.setText(item.getContent());
                binding.reply.setVisibility(TextUtils.isEmpty(item.getReply()) ? View.GONE : View.VISIBLE);
                binding.reply.setText(item.getReply());
                ImgUtil.load(item.getUser(), item.getAvatar(), binding.avatar);
            }
        }
    }
}
