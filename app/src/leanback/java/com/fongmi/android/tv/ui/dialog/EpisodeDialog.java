package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.databinding.AdapterEpisodeDialogBinding;
import com.fongmi.android.tv.databinding.DialogEpisodeBinding;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

public class EpisodeDialog extends BaseAlertDialog {

    private static final int PAGE_SIZE = 12;

    private final List<Page> pages = new ArrayList<>();
    private DialogEpisodeBinding binding;
    private EpisodePageAdapter adapter;
    private List<Episode> episodes;
    private Runnable reverseAction;

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
        adapter = new EpisodePageAdapter();
        binding.recycler.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        binding.recycler.setAdapter(adapter);
        renderPages();
    }

    @Override
    protected void initEvent() {
        binding.reverse.setOnClickListener(view -> onReverse());
        binding.tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                showPage(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
    }

    private void renderPages() {
        pages.clear();
        binding.tabs.removeAllTabs();
        if (episodes == null || episodes.isEmpty()) return;
        for (int start = 0; start < episodes.size(); start += PAGE_SIZE) {
            int end = Math.min(start + PAGE_SIZE, episodes.size());
            Page page = new Page(start, end);
            pages.add(page);
            binding.tabs.addTab(binding.tabs.newTab().setText(page.title()));
        }
        showPage(selectedPage());
    }

    private void showPage(int position) {
        if (pages.isEmpty()) return;
        int page = Math.max(0, Math.min(position, pages.size() - 1));
        Page item = pages.get(page);
        adapter.setItems(episodes.subList(item.start, item.end));
        if (binding.tabs.getSelectedTabPosition() != page) binding.tabs.selectTab(binding.tabs.getTabAt(page));
        binding.recycler.post(() -> binding.recycler.scrollToPosition(adapter.getSelectedPosition()));
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
