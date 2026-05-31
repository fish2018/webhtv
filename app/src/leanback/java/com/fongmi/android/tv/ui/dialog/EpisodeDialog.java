package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.databinding.AdapterEpisodeDialogBinding;
import com.fongmi.android.tv.databinding.AdapterEpisodePageBinding;
import com.fongmi.android.tv.databinding.DialogEpisodeBinding;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class EpisodeDialog extends BaseAlertDialog {

    private static final int PAGE_SIZE = 12;
    private static final int SPAN_COUNT = 3;

    private final List<Page> pages = new ArrayList<>();
    private DialogEpisodeBinding binding;
    private EpisodePageAdapter adapter;
    private PageAdapter pageAdapter;
    private List<Episode> episodes;
    private Runnable reverseAction;
    private int pageIndex;

    public static EpisodeDialog create() {
        return new EpisodeDialog();
    }

    public EpisodeDialog episodes(List<Episode> episodes) {
        this.episodes = episodes;
        return this;
    }

    public EpisodeDialog reverseAction(Runnable reverseAction) {
        this.reverseAction = reverseAction;
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment fragment : activity.getSupportFragmentManager().getFragments()) if (fragment instanceof EpisodeDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogEpisodeBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    public void onStart() {
        super.onStart();
        setSize();
    }

    private void setSize() {
        if (getDialog() == null) return;
        Window window = getDialog().getWindow();
        if (window == null) return;
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = (int) (ResUtil.getScreenWidth(requireContext()) * 0.84f);
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        window.setAttributes(params);
    }

    @Override
    protected void initView() {
        pageAdapter = new PageAdapter();
        adapter = new EpisodePageAdapter();
        binding.page.setHorizontalSpacing(ResUtil.dp2px(16));
        binding.page.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        binding.page.setAdapter(pageAdapter);
        binding.recycler.setLayoutManager(new GridLayoutManager(requireContext(), SPAN_COUNT));
        binding.recycler.setAdapter(adapter);
        renderPages();
    }

    @Override
    protected void initEvent() {
        binding.reverse.setOnClickListener(view -> onReverse());
    }

    private void renderPages() {
        pages.clear();
        pageAdapter.clear();
        if (episodes == null || episodes.isEmpty()) return;
        for (int start = 0; start < episodes.size(); start += PAGE_SIZE) {
            int end = Math.min(start + PAGE_SIZE, episodes.size());
            pages.add(new Page(start, end));
        }
        pageAdapter.addAll(pages);
        showPage(selectedPage(), true);
    }

    private void showPage(int position) {
        showPage(position, false);
    }

    private void showPage(int position, boolean focusEpisode) {
        if (pages.isEmpty()) return;
        pageIndex = Math.max(0, Math.min(position, pages.size() - 1));
        Page item = pages.get(pageIndex);
        adapter.setItems(episodes.subList(item.start, item.end));
        pageAdapter.setSelected(pageIndex);
        binding.page.setSelectedPosition(pageIndex);
        focusEpisode(focusEpisode);
    }

    private void focusEpisode(boolean requestFocus) {
        binding.recycler.post(() -> {
            int position = adapter.getSelectedPosition();
            binding.recycler.scrollToPosition(position);
            if (!requestFocus) return;
            binding.recycler.post(() -> requestEpisodeFocus(position));
        });
    }

    private void requestEpisodeFocus(int position) {
        RecyclerView.ViewHolder holder = binding.recycler.findViewHolderForAdapterPosition(position);
        if (holder == null) holder = binding.recycler.findViewHolderForAdapterPosition(0);
        if (holder != null) holder.itemView.requestFocus();
        else binding.recycler.requestFocus();
    }

    private int selectedPage() {
        for (int i = 0; i < episodes.size(); i++) {
            if (episodes.get(i).isSelected()) return i / PAGE_SIZE;
        }
        return 0;
    }

    private void onReverse() {
        if (reverseAction != null) reverseAction.run();
        renderPages();
    }

    private final class PageAdapter extends RecyclerView.Adapter<PageAdapter.ViewHolder> {

        private final List<Page> items = new ArrayList<>();
        private int selected;

        void addAll(List<Page> pages) {
            items.clear();
            items.addAll(pages);
            notifyDataSetChanged();
        }

        void clear() {
            items.clear();
            notifyDataSetChanged();
        }

        void setSelected(int selected) {
            this.selected = selected;
            notifyItemRangeChanged(0, getItemCount());
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(AdapterEpisodePageBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Page item = items.get(position);
            holder.binding.text.setText(item.title());
            holder.binding.text.setSelected(position == selected);
            holder.binding.text.setOnFocusChangeListener((view, hasFocus) -> {
                if (hasFocus && pageIndex != position) showPage(position);
            });
            holder.binding.getRoot().setOnClickListener(view -> focusEpisode(true));
        }

        private final class ViewHolder extends RecyclerView.ViewHolder {

            private final AdapterEpisodePageBinding binding;

            private ViewHolder(@NonNull AdapterEpisodePageBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }

    private record Page(int start, int end) {

        String title() {
            return (start + 1) + " - " + end;
        }
    }

    private final class EpisodePageAdapter extends RecyclerView.Adapter<EpisodePageAdapter.ViewHolder> {

        private final List<Episode> items = new ArrayList<>();

        void setItems(List<Episode> episodes) {
            items.clear();
            items.addAll(episodes);
            notifyDataSetChanged();
        }

        int getSelectedPosition() {
            for (int i = 0; i < items.size(); i++) if (items.get(i).isSelected()) return i;
            return 0;
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(AdapterEpisodeDialogBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Episode item = items.get(position);
            holder.binding.text.setText(item.getDisplayName());
            holder.binding.text.setSelected(item.isSelected());
            holder.binding.text.setNextFocusUpId(position < SPAN_COUNT ? R.id.page : View.NO_ID);
            holder.binding.getRoot().setOnClickListener(view -> {
                ((com.fongmi.android.tv.ui.adapter.EpisodeAdapter.OnClickListener) requireActivity()).onItemClick(item);
                dismiss();
            });
        }

        private final class ViewHolder extends RecyclerView.ViewHolder {

            private final AdapterEpisodeDialogBinding binding;

            private ViewHolder(@NonNull AdapterEpisodeDialogBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
