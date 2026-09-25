package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.databinding.DialogEpisodeGridBinding;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.adapter.EpisodeAdapter;
import com.fongmi.android.tv.ui.fragment.EpisodeFragment;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.List;

public class EpisodeGridDialog extends BaseBottomSheetDialog {

    private final List<String> titles;
    private DialogEpisodeGridBinding binding;
    private List<Episode> episodes;
    private boolean reverse;
    private boolean tmdbCard;
    private int spanCount;
    private int itemCount;

    public EpisodeGridDialog() {
        this.titles = new ArrayList<>();
        this.spanCount = 5;
    }

    public static EpisodeGridDialog create() {
        return new EpisodeGridDialog();
    }

    public EpisodeGridDialog reverse(boolean reverse) {
        this.reverse = reverse;
        return this;
    }

    public EpisodeGridDialog episodes(List<Episode> episodes) {
        this.episodes = episodes;
        return this;
    }

    public EpisodeGridDialog tmdbCard(boolean tmdbCard) {
        this.tmdbCard = tmdbCard;
        return this;
    }

    public void show(FragmentActivity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || activity.getSupportFragmentManager().isStateSaved()) return;
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof EpisodeGridDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogEpisodeGridBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        setSpanCount();
        setTitles();
        setPager();
        updateEpisodeFileNameButton();
    }

    @Override
    protected void initEvent() {
        binding.episodeFileName.setOnClickListener(this::onEpisodeFileNameToggle);
        binding.column.setOnClickListener(this::onColumnToggle);
        getChildFragmentManager().setFragmentResultListener("result", this, (requestKey, bundle) -> {
            ((EpisodeAdapter.OnClickListener) requireActivity()).onItemClick(bundle.getParcelable("episode"));
            dismiss();
        });
    }

    private void onColumnToggle(View view) {
        PlayerSetting.putEpisodeColumn(spanCount == 1 ? 2 : 1);
        setSpanCount();
        setTitles();
        setPager();
    }

    private void onEpisodeFileNameToggle(View view) {
        Setting.putTmdbEpisodeShowScrapedName(!Setting.getTmdbEpisodeShowScrapedName());
        updateEpisodeFileNameButton();
        setPager();
    }

    private void updateEpisodeFileNameButton() {
        binding.episodeFileName.setVisibility(tmdbCard ? View.VISIBLE : View.GONE);
        if (!tmdbCard) return;
        // 图标表达当前标题状态，描述表达点击后的动作。
        boolean showScraped = Setting.getTmdbEpisodeShowScrapedName();
        binding.episodeFileName.setImageResource(showScraped ? R.drawable.ic_action_name_short : R.drawable.ic_action_name_full);
        binding.episodeFileName.setContentDescription(getString(showScraped ? R.string.detail_episode_file_name_original_action : R.string.detail_episode_file_name_scraped_action));
    }

    private void setSpanCount() {
        int avg = (int) Math.ceil(episodes.stream().mapToInt(e -> e.getName().length()).average().orElse(0));
        int max = episodes.stream().mapToInt(e -> e.getDesc().concat(e.getName()).length()).max().orElse(0);
        boolean longTitle = avg >= 8 || max >= 12;
        if (longTitle) spanCount = PlayerSetting.getEpisodeColumn();
        else if (avg >= 4) spanCount = 3;
        else if (avg >= 2) spanCount = 4;
        else spanCount = 5;
        itemCount = episodes.size() <= 60 ? 20 : spanCount * (ResUtil.isLand(requireActivity()) ? 5 : 10);
        binding.column.setVisibility(longTitle ? View.VISIBLE : View.GONE);
        binding.column.setImageResource(spanCount == 1 ? R.drawable.ic_site_double_column : R.drawable.ic_site_single_column);
    }

    private void setTitles() {
        titles.clear();
        if (reverse) for (int i = episodes.size(); i > 0; i -= itemCount) titles.add(i + " - " + Math.max(i - itemCount + 1, 1));
        else for (int i = 0; i < episodes.size(); i += itemCount) titles.add((i + 1) + " - " + Math.min(i + itemCount, episodes.size()));
    }

    private void setPager() {
        binding.tabs.removeAllTabs();
        binding.pager.setAdapter(new PageAdapter(this));
        new TabLayoutMediator(binding.tabs, binding.pager, (tab, position) -> tab.setText(titles.get(position))).attach();
        setCurrentPage();
    }

    private void setCurrentPage() {
        for (int i = 0; i < episodes.size(); i++) {
            if (episodes.get(i).isSelected()) {
                binding.pager.setCurrentItem(i / itemCount);
                break;
            }
        }
    }

    class PageAdapter extends FragmentStateAdapter {

        public PageAdapter(@NonNull Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return EpisodeFragment.newInstance(spanCount, episodes.subList(position * itemCount, Math.min(position * itemCount + itemCount, episodes.size())), tmdbCard);
        }

        @Override
        public int getItemCount() {
            return titles.size();
        }
    }
}
