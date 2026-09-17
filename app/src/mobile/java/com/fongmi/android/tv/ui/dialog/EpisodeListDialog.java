package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.databinding.DialogEpisodeListBinding;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.ui.adapter.EpisodeAdapter;
import com.fongmi.android.tv.ui.adapter.EpisodeGroupAdapter;
import com.fongmi.android.tv.ui.adapter.FlagAdapter;
import com.fongmi.android.tv.ui.base.ViewType;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.ui.helper.EpisodeRangePolicy;
import com.fongmi.android.tv.ui.helper.EpisodeSeasonSegments;
import com.fongmi.android.tv.utils.EpisodeTitleCompact;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EpisodeListDialog extends AppCompatDialogFragment implements FlagAdapter.OnClickListener, EpisodeGroupAdapter.OnClickListener, EpisodeAdapter.OnClickListener {

    private DialogEpisodeListBinding binding;
    private EpisodeGroupAdapter groupAdapter;
    private EpisodeGroupAdapter seasonAdapter;
    private EpisodeAdapter episodeAdapter;
    private SpaceItemDecoration episodeDecoration;
    private FlagAdapter flagAdapter;
    private List<Flag> flags;
    private int episodeSpanCount = 4;
    private boolean reverse;
    private boolean tmdbCard;
    private String fallbackStillUrl = "";
    private List<Integer> tmdbSeasons = List.of();
    private Map<Integer, Integer> tmdbSeasonCounts = Map.of();
    private List<EpisodeSeasonSegments.Segment> seasonSegments = List.of();
    private int selectedSeason = Integer.MIN_VALUE;

    public static EpisodeListDialog create() {
        return new EpisodeListDialog();
    }

    public EpisodeListDialog flags(List<Flag> flags) {
        this.flags = flags;
        return this;
    }

    public EpisodeListDialog reverse(boolean reverse) {
        this.reverse = reverse;
        return this;
    }

    public EpisodeListDialog tmdbCard(boolean tmdbCard) {
        this.tmdbCard = tmdbCard;
        return this;
    }

    public EpisodeListDialog fallbackStill(String fallbackStillUrl) {
        this.fallbackStillUrl = fallbackStillUrl;
        return this;
    }

    public EpisodeListDialog seasons(List<Integer> seasons, Map<Integer, Integer> seasonCounts) {
        this.tmdbSeasons = seasons == null ? List.of() : seasons;
        this.tmdbSeasonCounts = seasonCounts == null ? Map.of() : seasonCounts;
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof EpisodeListDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        configureWindow(dialog);
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        configureWindow(getDialog());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DialogEpisodeListBinding.inflate(inflater, container, false);
        FrameLayout overlay = new FrameLayout(requireContext());
        overlay.setBackgroundColor(Color.TRANSPARENT);
        overlay.setOnClickListener(view -> dismiss());
        binding.getRoot().setClickable(true);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(getWidth(), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END);
        overlay.addView(binding.getRoot(), params);
        return overlay;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initView();
    }

    private int getWidth() {
        int screen = ResUtil.getScreenWidth(requireContext());
        return Math.max(ResUtil.dp2px(360), Math.min(ResUtil.dp2px(560), Math.round(screen * 0.44f)));
    }

    private void configureWindow(Dialog dialog) {
        if (dialog == null || dialog.getWindow() == null) return;
        Window window = dialog.getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.setDimAmount(0f);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        Util.hideSystemUI(window);
    }

    private void initView() {
        setRecyclerView();
        flagAdapter.addAll(flags == null ? new ArrayList<>() : flags);
        setGroups(getSelectedFlag());
        binding.flag.scrollToPosition(flagAdapter.getPosition());
    }

    private void setRecyclerView() {
        binding.flag.setHasFixedSize(true);
        binding.flag.setItemAnimator(null);
        binding.flag.setAdapter(flagAdapter = new FlagAdapter(this));
        binding.season.setHasFixedSize(true);
        binding.season.setItemAnimator(null);
        // 复用分组按钮的 adapter/布局，季度按钮与选集分组按钮样式自然一致
        binding.season.setAdapter(seasonAdapter = new EpisodeGroupAdapter(this::onSeasonClick));
        binding.group.setHasFixedSize(true);
        binding.group.setItemAnimator(null);
        binding.group.setAdapter(groupAdapter = new EpisodeGroupAdapter(this));
        binding.episode.setHasFixedSize(true);
        binding.episode.setItemAnimator(null);
        binding.episode.setLayoutManager(new GridLayoutManager(requireContext(), episodeSpanCount));
        binding.episode.addItemDecoration(episodeDecoration = new SpaceItemDecoration(episodeSpanCount, 8));
        binding.episode.setAdapter(episodeAdapter = new EpisodeAdapter(this, ViewType.GRID));
        episodeAdapter.setUseTmdbCard(tmdbCard);
        // 弹层自建 adapter，不共享播放页那个实例；不设兜底图的话无 TMDB 数据的集永远是无图卡片
        episodeAdapter.setFallbackStillUrl(fallbackStillUrl);
        binding.episode.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                syncEpisodeGroupByScroll();
            }
        });
    }

    private Flag getSelectedFlag() {
        if (flagAdapter.isEmpty()) return null;
        return flagAdapter.get(flagAdapter.getPosition());
    }

    private void setGroups(Flag flag) {
        if (flag == null) return;
        List<Episode> sourceEpisodes = flag.getEpisodes();
        seasonSegments = EpisodeSeasonSegments.build(sourceEpisodes.size(), tmdbSeasons, tmdbSeasonCounts);
        selectedSeason = defaultSeason(sourceEpisodes);
        renderSeasons();
        bindEpisodesForSeason(sourceEpisodes);
    }

    /** 默认落在当前播放集所属的季，让弹层一打开就停在用户正在看的地方。 */
    private int defaultSeason(List<Episode> sourceEpisodes) {
        if (seasonSegments.isEmpty()) return Integer.MIN_VALUE;
        int index = EpisodeSeasonSegments.indexOf(seasonSegments, getSelectedEpisodePosition(sourceEpisodes));
        return seasonSegments.get(index < 0 ? 0 : index).season();
    }

    private void renderSeasons() {
        boolean hasSeasons = !seasonSegments.isEmpty();
        binding.seasonLabel.setVisibility(hasSeasons ? View.VISIBLE : View.GONE);
        binding.season.setVisibility(hasSeasons ? View.VISIBLE : View.GONE);
        if (!hasSeasons) return;
        List<EpisodeGroupAdapter.Group> groups = new ArrayList<>();
        for (EpisodeSeasonSegments.Segment segment : seasonSegments) {
            EpisodeGroupAdapter.Group group = new EpisodeGroupAdapter.Group(
                    seasonLabel(segment.season()), segment.start(), segment.end());
            group.selected = segment.season() == selectedSeason;
            groups.add(group);
        }
        seasonAdapter.addAll(groups);
        // 用 scrollToPosition 会把选中季顶到最左，导致左侧相邻季被裁掉半个字（"第1季"显示成"季"）。
        // 留出一段偏移，让选中季前面的一季仍完整可见，也提示用户左边还有内容。
        scrollHorizontallyWithOffset(binding.season, seasonAdapter.getPosition());
    }

    private void scrollHorizontallyWithOffset(RecyclerView recyclerView, int position) {
        RecyclerView.LayoutManager manager = recyclerView.getLayoutManager();
        if (manager instanceof LinearLayoutManager linear) linear.scrollToPositionWithOffset(position, ResUtil.dp2px(72));
        else recyclerView.scrollToPosition(position);
    }

    private String seasonLabel(int season) {
        return EpisodeSeasonSegments.isOther(season)
                ? getString(R.string.detail_season_other)
                : getString(R.string.detail_season_format, season);
    }

    private void onSeasonClick(EpisodeGroupAdapter.Group item) {
        int index = seasonAdapter.getItems().indexOf(item);
        if (index < 0 || index >= seasonSegments.size()) return;
        int season = seasonSegments.get(index).season();
        if (season == selectedSeason) return;
        selectedSeason = season;
        seasonAdapter.setSelected(item);
        Flag flag = getSelectedFlag();
        if (flag != null) bindEpisodesForSeason(flag.getEpisodes());
    }

    /** 按当前季切出子列表，再据此重建分组按钮与选集网格。 */
    private void bindEpisodesForSeason(List<Episode> sourceEpisodes) {
        List<Episode> episodes = visibleEpisodes(sourceEpisodes);
        int maxGroupSize = tmdbCard ? EpisodeRangePolicy.CARD_PAGE_MAX_SIZE : 0;
        List<EpisodeGroupAdapter.Group> groups =
                EpisodeGroupAdapter.build(episodes.size(), getSelectedEpisodePosition(episodes), reverse, maxGroupSize);
        groupAdapter.addAll(withGlobalEpisodeLabels(groups, seasonOffset(), episodes.size()));
        setEpisodes(episodes);
        binding.group.scrollToPosition(groupAdapter.getPosition());
        binding.episode.scrollToPosition(episodeAdapter.getPosition());
    }

    /** 当前季在源列表里的起始下标；用于把分组标签还原成全局集号。 */
    private int seasonOffset() {
        if (seasonSegments.isEmpty()) return 0;
        for (EpisodeSeasonSegments.Segment segment : seasonSegments) {
            if (segment.season() == selectedSeason) return segment.start();
        }
        return 0;
    }

    /**
     * EpisodeRangePolicy 的标签基于子列表下标，按季切片后会从 1 重新开始（第 7 季显示成 1-20）。
     * 详情页显示的是全局集号（196-215），这里加上季偏移对齐，避免同一集在两处编号不同。
     * 倒序时 EpisodeRangePolicy 用的是 size-start 口径，必须同样保持倒序，否则会把倒序标签
     * 覆盖成正序编号。
     */
    private List<EpisodeGroupAdapter.Group> withGlobalEpisodeLabels(List<EpisodeGroupAdapter.Group> groups, int offset, int size) {
        if (offset <= 0) return groups;
        List<EpisodeGroupAdapter.Group> shifted = new ArrayList<>(groups.size());
        for (EpisodeGroupAdapter.Group group : groups) {
            int labelStart = offset + (reverse ? size - group.start : group.start + 1);
            int labelEnd = offset + (reverse ? size - group.end + 1 : group.end);
            String name = labelStart == labelEnd ? String.valueOf(labelStart) : labelStart + "-" + labelEnd;
            EpisodeGroupAdapter.Group copy = new EpisodeGroupAdapter.Group(name, group.start, group.end);
            copy.selected = group.selected;
            shifted.add(copy);
        }
        return shifted;
    }

    private List<Episode> visibleEpisodes(List<Episode> sourceEpisodes) {
        if (seasonSegments.isEmpty()) return sourceEpisodes;
        List<Episode> sliced = EpisodeSeasonSegments.slice(sourceEpisodes, seasonSegments, selectedSeason);
        if (!sliced.isEmpty()) return sliced;
        // 源列表在 segments 快照之后被改短（如弹层打开期间倒序/合并集数），切不出内容时回退成
        // 完整列表。此时必须同步清掉选中季，否则 seasonOffset() 仍返回该季起点，会在已是全局
        // 编号的列表上再叠一次偏移，标签与内容错位。
        selectedSeason = Integer.MIN_VALUE;
        return sourceEpisodes;
    }

    private void setEpisodes(List<Episode> episodes) {
        setEpisodeItems(episodes);
        selectEpisodeGroupByPosition(episodeAdapter.getPosition());
    }

    private void setEpisodeItems(List<Episode> episodes) {
        episodeAdapter.setUseTmdbCard(tmdbCard);
        updateEpisodeSpan(episodes);
        episodeAdapter.addAll(episodes);
    }

    private void updateEpisodeSpan(List<Episode> episodes) {
        int span = getEpisodeSpan(episodes);
        if (span == episodeSpanCount) return;
        episodeSpanCount = span;
        binding.episode.setLayoutManager(new GridLayoutManager(requireContext(), episodeSpanCount));
        if (episodeDecoration != null) binding.episode.removeItemDecoration(episodeDecoration);
        binding.episode.addItemDecoration(episodeDecoration = new SpaceItemDecoration(episodeSpanCount, 8));
    }

    private int getEpisodeSpan(List<Episode> episodes) {
        if (tmdbCard) return 2;
        EpisodeTitleCompact.apply(episodes);
        int maxLen = 0;
        for (Episode item : episodes) maxLen = Math.max(maxLen, item.getDisplayName().length());
        if (maxLen >= 12) return PlayerSetting.getEpisodeColumn();
        int ideal = maxLen >= 10 ? 130 : maxLen >= 7 ? 104 : 80;
        int available = Math.max(ResUtil.dp2px(240), getWidth() - ResUtil.dp2px(28));
        int span = available / ResUtil.dp2px(ideal);
        return Math.max(2, Math.min(4, span));
    }

    private int getSelectedEpisodePosition(List<Episode> episodes) {
        for (int i = 0; i < episodes.size(); i++) if (episodes.get(i).isSelected()) return i;
        return 0;
    }

    @Override
    public void onItemClick(Flag item) {
        ((FlagAdapter.OnClickListener) requireActivity()).onItemClick(item);
        flagAdapter.notifyItemRangeChanged(0, flagAdapter.getItemCount());
        setGroups(item);
    }

    @Override
    public void onItemClick(EpisodeGroupAdapter.Group item) {
        groupAdapter.setSelected(item);
        scrollEpisodeToPosition(item.start);
        binding.group.scrollToPosition(groupAdapter.getPosition());
    }

    private void syncEpisodeGroupByScroll() {
        RecyclerView.LayoutManager manager = binding.episode.getLayoutManager();
        if (!(manager instanceof GridLayoutManager)) return;
        int position = getEpisodeGroupSyncPosition((GridLayoutManager) manager);
        if (position == RecyclerView.NO_POSITION) return;
        selectEpisodeGroupByPosition(position);
    }

    private int getEpisodeGroupSyncPosition(GridLayoutManager manager) {
        if (!binding.episode.canScrollVertically(1) && binding.episode.canScrollVertically(-1)) {
            return manager.findLastVisibleItemPosition();
        }
        return manager.findFirstVisibleItemPosition();
    }

    private void selectEpisodeGroupByPosition(int position) {
        if (groupAdapter == null || groupAdapter.isEmpty()) return;
        int current = groupAdapter.getPosition();
        List<EpisodeGroupAdapter.Group> groups = groupAdapter.getItems();
        for (int i = 0; i < groups.size(); i++) {
            EpisodeGroupAdapter.Group group = groups.get(i);
            if (position < group.start || position >= group.end) continue;
            if (i != current) {
                groupAdapter.setSelected(group);
                binding.group.scrollToPosition(i);
            }
            return;
        }
    }

    private void scrollEpisodeToPosition(int position) {
        RecyclerView.LayoutManager manager = binding.episode.getLayoutManager();
        if (manager instanceof GridLayoutManager) ((GridLayoutManager) manager).scrollToPositionWithOffset(position, 0);
        else binding.episode.scrollToPosition(position);
    }

    @Override
    public void onItemClick(Episode item) {
        ((EpisodeAdapter.OnClickListener) requireActivity()).onItemClick(item);
        dismiss();
    }
}
