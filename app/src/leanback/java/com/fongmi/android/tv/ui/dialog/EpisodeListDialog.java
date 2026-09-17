package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.leanback.widget.BaseGridView;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.databinding.DialogEpisodeListBinding;
import com.fongmi.android.tv.ui.adapter.ArrayAdapter;
import com.fongmi.android.tv.ui.adapter.EpisodeAdapter;
import com.fongmi.android.tv.ui.adapter.FlagAdapter;
import com.fongmi.android.tv.ui.helper.EpisodeRangePolicy;
import com.fongmi.android.tv.ui.helper.EpisodeSeasonSegments;
import com.fongmi.android.tv.ui.helper.TmdbEpisodeGridPolicy;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EpisodeListDialog extends BaseAlertDialog implements FlagAdapter.OnClickListener, ArrayAdapter.OnClickListener, EpisodeAdapter.OnClickListener {

    private static final int TEXT_EPISODE_ROW_HEIGHT_DP = 40;

    private final List<Integer> segmentStarts;
    private final List<Integer> segmentEnds;
    private final List<Episode> allEpisodes;

    private DialogEpisodeListBinding binding;
    private EpisodeAdapter episodeAdapter;
    private ArrayAdapter arrayAdapter;
    private ArrayAdapter seasonAdapter;
    private FlagAdapter flagAdapter;
    private DialogInterface.OnDismissListener dismissListener;
    private List<Flag> flags;
    private int panelWidth;
    private boolean tmdbCard;
    private String fallbackStillUrl = "";
    private List<Integer> tmdbSeasons = List.of();
    private Map<Integer, Integer> tmdbSeasonCounts = Map.of();
    private List<EpisodeSeasonSegments.Segment> seasonSegments = List.of();
    private int selectedSeason = Integer.MIN_VALUE;
    private int selectedSegment;

    public EpisodeListDialog() {
        segmentStarts = new ArrayList<>();
        segmentEnds = new ArrayList<>();
        allEpisodes = new ArrayList<>();
    }

    public static EpisodeListDialog create() {
        return new EpisodeListDialog();
    }

    public EpisodeListDialog flags(List<Flag> flags) {
        this.flags = flags;
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

    public EpisodeListDialog dismissListener(DialogInterface.OnDismissListener dismissListener) {
        this.dismissListener = dismissListener;
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment fragment : activity.getSupportFragmentManager().getFragments()) if (fragment instanceof EpisodeListDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogEpisodeListBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        panelWidth = getPanelWidth();
        if (tmdbCard) {
            // TMDB 卡片模式：全屏显示，深色半透明背景，适中内边距
            binding.getRoot().setBackgroundColor(0x80111820);
            binding.getRoot().setPadding(ResUtil.dp2px(24), ResUtil.dp2px(20), ResUtil.dp2px(24), ResUtil.dp2px(16));
        }
        setRecyclerView();
        flagAdapter.addAll(flags == null ? new ArrayList<>() : flags);
        setEpisodes(getSelectedFlag());
        binding.flag.setSelectedPosition(flagAdapter.getPosition());
    }

    private void setRecyclerView() {
        int spacing = ResUtil.dp2px(8);
        binding.flag.setHorizontalSpacing(spacing);
        binding.flag.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        binding.flag.setAdapter(flagAdapter = new FlagAdapter(this));
        flagAdapter.setOnKeyListener((view, keyCode, event) -> onFlagKey(event));
        binding.array.setHorizontalSpacing(spacing);
        binding.array.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        binding.array.setAdapter(arrayAdapter = new ArrayAdapter(this));
        arrayAdapter.setOnKeyListener((view, keyCode, event) -> onArrayKey(event));
        binding.array.setOnKeyListener((view, keyCode, event) -> onArrayKey(event));
        binding.season.setHorizontalSpacing(spacing);
        binding.season.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        // 复用分段按钮的 adapter/布局，季度按钮与分段按钮样式自然一致；
        // 只取 onSegmentClick 当"选季"，其余回调（换序/正反播）与季度无关。
        binding.season.setAdapter(seasonAdapter = new ArrayAdapter(new ArrayAdapter.OnClickListener() {
            @Override
            public void onRevSort() {
            }

            @Override
            public void onRevPlay(TextView view) {
            }

            @Override
            public void onSegmentClick(int position) {
                onSeasonClick(position);
            }

            @Override
            public void onSegmentFocus(int position) {
            }
        }));
        seasonAdapter.setOnKeyListener((view, keyCode, event) -> onSeasonKey(event));
        binding.season.setOnKeyListener((view, keyCode, event) -> onSeasonKey(event));
        binding.episode.setHorizontalSpacing(spacing);
        binding.episode.setVerticalSpacing(spacing);
        binding.episode.setNestedScrollingEnabled(false);
        if (tmdbCard) binding.episode.setFocusScrollStrategy(BaseGridView.FOCUS_SCROLL_ITEM);
        binding.episode.setAdapter(episodeAdapter = new EpisodeAdapter(this, getEpisodeContentWidth()));
        episodeAdapter.setUseTmdbCard(tmdbCard);
        // 弹层自建 adapter，不共享播放页那个实例；不设兜底图的话无 TMDB 数据的集永远是无图卡片
        episodeAdapter.setFallbackStillUrl(fallbackStillUrl);
        episodeAdapter.setOnKeyListener((view, keyCode, event) -> onEpisodeKey(event));
        binding.episode.setOnKeyListener((view, keyCode, event) -> onEpisodeKey(event));
        binding.flag.setOnKeyListener((view, keyCode, event) -> onFlagKey(event));
        binding.array.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (child != null) selectSegment(position, true);
            }
        });
        binding.episode.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (child != null) alignEpisodeScroll(position);
            }
        });
    }

    private int getPanelWidth() {
        // 文本与卡片模式统一使用全屏宽度，避免长剧名在右侧窄抽屉中被截断
        return ResUtil.getScreenWidth(requireContext());
    }

    private int getEpisodeContentWidth() {
        return panelWidth - ResUtil.dp2px(40);
    }

    private Flag getSelectedFlag() {
        if (flagAdapter == null || flagAdapter.getItemCount() == 0) return null;
        return flagAdapter.get(flagAdapter.getPosition());
    }

    private void setEpisodes(Flag flag) {
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
        if (!hasSeasons) {
            seasonAdapter.clear();
            return;
        }
        List<String> labels = new ArrayList<>();
        int selectedIndex = 0;
        for (int i = 0; i < seasonSegments.size(); i++) {
            int season = seasonSegments.get(i).season();
            labels.add(seasonLabel(season));
            if (season == selectedSeason) selectedIndex = i;
        }
        seasonAdapter.addAll(labels);
        seasonAdapter.setSelectedPosition(selectedIndex);
        // 向下的焦点目标要看分段行是否显示，这里还不知道；统一由随后的 setSegments 设置。
        binding.season.setSelectedPosition(selectedIndex);
    }

    private String seasonLabel(int season) {
        return EpisodeSeasonSegments.isOther(season)
                ? getString(R.string.detail_season_other)
                : getString(R.string.detail_season_format, season);
    }

    private void onSeasonClick(int position) {
        if (position < 0 || position >= seasonSegments.size()) return;
        int season = seasonSegments.get(position).season();
        if (season == selectedSeason) return;
        selectedSeason = season;
        seasonAdapter.setSelectedPosition(position);
        Flag flag = getSelectedFlag();
        if (flag != null) bindEpisodesForSeason(flag.getEpisodes());
    }

    /** 按当前季切出子列表，再据此重建分段按钮与选集网格。 */
    private void bindEpisodesForSeason(List<Episode> sourceEpisodes) {
        List<Episode> episodes = visibleEpisodes(sourceEpisodes);
        allEpisodes.clear();
        allEpisodes.addAll(episodes);
        int column = tmdbCard ? getTmdbCardColumn() : getTextColumn(episodes);
        binding.episode.setNumColumns(column);
        if (!tmdbCard) binding.episode.setColumnWidth(getTextColumnWidth(column));
        episodeAdapter.setUseTmdbCard(tmdbCard);
        episodeAdapter.setGridMode(tmdbCard);
        episodeAdapter.setVerticalGridMode(true);
        episodeAdapter.setColumn(column);
        setSegments(episodes.size());
        setSegmentEpisodes(selectedSegment);
        scrollToSelectedEpisode();
    }

    private List<Episode> visibleEpisodes(List<Episode> sourceEpisodes) {
        if (seasonSegments.isEmpty()) return sourceEpisodes;
        List<Episode> sliced = EpisodeSeasonSegments.slice(sourceEpisodes, seasonSegments, selectedSeason);
        if (!sliced.isEmpty()) return sliced;
        // 源列表在 segments 快照之后被改短时切不出内容，回退成完整列表。必须同步清掉选中季，
        // 否则 seasonOffset() 仍返回该季起点，会在已是全局编号的列表上再叠一次偏移。
        selectedSeason = Integer.MIN_VALUE;
        return sourceEpisodes;
    }

    /** 当前季在源列表里的起始下标；用于把分段标签还原成全局集号。 */
    private int seasonOffset() {
        if (seasonSegments.isEmpty()) return 0;
        for (EpisodeSeasonSegments.Segment segment : seasonSegments) {
            if (segment.season() == selectedSeason) return segment.start();
        }
        return 0;
    }

    private void setSegments(int size) {
        int segment = tmdbCard ? getTmdbEpisodeSegmentSize(size) : getEpisodeSegmentSize(size);
        List<String> items = new ArrayList<>();
        int selectedEpisode = getSelectedEpisodePosition(allEpisodes);
        segmentStarts.clear();
        segmentEnds.clear();
        // 分段标签加上季偏移，显示全局集号（第 7 季 = 196-215），与详情页编号一致
        int offset = seasonOffset();
        // 修复：对于 tmdbCard 模式，始终创建分段（即使 size <= segment），避免一次性加载所有剧集
        if (tmdbCard) {
            for (int i = 0; i < size; i += segment) {
                segmentStarts.add(i);
                int end = Math.min(i + segment, size);
                segmentEnds.add(end);
                items.add((offset + i + 1) + "-" + (offset + end));
            }
        } else if (size > segment) {
            for (int i = 0; i < size; i += segment) {
                segmentStarts.add(i);
                int end = Math.min(i + segment, size);
                segmentEnds.add(end);
                items.add((offset + i + 1) + "-" + (offset + end));
            }
        }
        selectedSegment = resolveSelectedSegment(selectedEpisode);
        arrayAdapter.setSegmentSize(segment);
        arrayAdapter.addAll(items);
        binding.array.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        // 焦点链：线路 -> 季度(有则) -> 分段(有则) -> 选集，声明式 ID 与按键处理必须一致
        boolean hasSeason = !seasonSegments.isEmpty();
        flagAdapter.setNextFocusDown(hasSeason ? R.id.season : items.isEmpty() ? R.id.episode : R.id.array);
        if (hasSeason) seasonAdapter.setNextFocus(R.id.flag, items.isEmpty() ? R.id.episode : R.id.array);
        arrayAdapter.setNextFocus(hasSeason ? R.id.season : R.id.flag, R.id.episode);
        episodeAdapter.setNextFocusUp(items.isEmpty() ? hasSeason ? R.id.season : R.id.flag : R.id.array);
        episodeAdapter.setNextFocusDown(0);
    }

    private int getTextColumn(List<Episode> episodes) {
        return Math.min(2, EpisodeAdapter.getColumn(episodes, getEpisodeContentWidth()));
    }

    private int getTextColumnWidth(int column) {
        return (getEpisodeContentWidth() - ResUtil.dp2px(8) * (Math.max(1, column) - 1)) / Math.max(1, column);
    }

    private int resolveSelectedSegment(int episodePosition) {
        if (segmentStarts.isEmpty()) return 0;
        for (int i = 0; i < segmentStarts.size(); i++) {
            if (episodePosition >= segmentStarts.get(i) && episodePosition < segmentEnds.get(i)) return i;
        }
        return 0;
    }

    private void selectSegment(int position, boolean keepSegmentFocus) {
        if (position < 0 || position >= segmentStarts.size() || position == selectedSegment) return;
        selectedSegment = position;
        setSegmentEpisodes(position);
        if (keepSegmentFocus) keepArrayFocus(position);
    }

    private void setSegmentEpisodes(int position) {
        if (segmentStarts.isEmpty()) {
            episodeAdapter.addAll(allEpisodes);
            updateEpisodeContentHeight();
            return;
        }
        position = Math.max(0, Math.min(position, segmentStarts.size() - 1));
        int start = segmentStarts.get(position);
        int end = segmentEnds.get(position);
        episodeAdapter.addAll(EpisodeRangePolicy.slice(allEpisodes, new EpisodeRangePolicy.Range("", start, end, position == selectedSegment)));
        updateEpisodeContentHeight();
    }

    private void updateEpisodeContentHeight() {
        ViewGroup.LayoutParams params = binding.episode.getLayoutParams();
        if (params == null) return;
        params.height = getEpisodeContentHeight();
        binding.episode.setLayoutParams(params);
    }

    private int getEpisodeContentHeight() {
        int count = Math.max(0, episodeAdapter.getItemCount());
        if (count == 0) return ViewGroup.LayoutParams.WRAP_CONTENT;
        int column = Math.max(1, episodeAdapter.getColumn());
        int rows = Math.max(1, (count + column - 1) / column);
        int spacing = ResUtil.dp2px(8);
        int rowHeight = getEpisodeRowHeight();
        return rowHeight * rows + spacing * Math.max(0, rows - 1) + binding.episode.getPaddingTop() + binding.episode.getPaddingBottom();
    }

    private int getEpisodeRowHeight() {
        return tmdbCard
                ? ResUtil.dp2px(EpisodeAdapter.GRID_CARD_HEIGHT_DP + EpisodeAdapter.GRID_CARD_BOTTOM_MARGIN_DP)
                : ResUtil.dp2px(TEXT_EPISODE_ROW_HEIGHT_DP);
    }

    private void alignEpisodeScroll(int position) {
        if (position == RecyclerView.NO_POSITION || episodeAdapter == null) return;
        int column = Math.max(1, episodeAdapter.getColumn());
        int rowStart = Math.max(0, position - position % column);
        binding.episode.post(() -> {
            if (binding == null) return;
            if (rowStart == 0) {
                binding.getRoot().scrollTo(0, 0);
                return;
            }
            int rowIndex = rowStart / Math.max(1, episodeAdapter.getColumn());
            int targetY = binding.episode.getTop() + rowIndex * (getEpisodeRowHeight() + ResUtil.dp2px(8)) - ResUtil.dp2px(8);
            binding.getRoot().scrollTo(0, Math.max(0, targetY));
        });
    }

    private void keepArrayFocus(int position) {
        binding.array.post(() -> {
            if (binding == null) return;
            binding.array.setSelectedPosition(position);
            RecyclerView.ViewHolder holder = binding.array.findViewHolderForAdapterPosition(position);
            if (holder != null) holder.itemView.requestFocus();
            else binding.array.requestFocus();
        });
    }

    private boolean onFlagKey(KeyEvent event) {
        if (!KeyUtil.isActionDown(event) || !KeyUtil.isDownKey(event)) return false;
        focusLowerFromFlag();
        return true;
    }

    private boolean onSeasonKey(KeyEvent event) {
        if (!KeyUtil.isActionDown(event)) return false;
        if (KeyUtil.isUpKey(event)) {
            focusFlag();
            return true;
        }
        if (KeyUtil.isDownKey(event)) {
            if (isVisible(binding.array) && arrayAdapter.getItemCount() > 0) focusArray();
            else focusPosition(binding.episode, episodeAdapter.getPosition());
            return true;
        }
        return false;
    }

    private boolean onArrayKey(KeyEvent event) {
        if (!KeyUtil.isActionDown(event)) return false;
        if (KeyUtil.isUpKey(event)) {
            focusUpperFromArray();
            return true;
        }
        if (KeyUtil.isDownKey(event)) {
            focusEpisodeFromArray();
            return true;
        }
        return false;
    }

    private boolean onEpisodeKey(KeyEvent event) {
        if (!KeyUtil.isActionDown(event)) return false;
        if (KeyUtil.isDownKey(event)) return focusLowerFromEpisode();
        if (!KeyUtil.isUpKey(event)) return false;
        int position = getFocusedPosition(binding.episode);
        int column = Math.max(1, episodeAdapter.getColumn());
        if (position == RecyclerView.NO_POSITION || position >= column) return false;
        focusUpperFromEpisode();
        return true;
    }

    private boolean focusLowerFromEpisode() {
        int position = getFocusedPosition(binding.episode);
        int column = Math.max(1, episodeAdapter.getColumn());
        int count = episodeAdapter.getItemCount();
        // 正下方有卡片时交给 Leanback 原生逐行移动，只兜底末行不满导致同列为空、下键卡住的情况
        if (position == RecyclerView.NO_POSITION || position + column < count) return false;
        int target = TmdbEpisodeGridPolicy.verticalFocusTarget(position, column, count, true);
        if (target == TmdbEpisodeGridPolicy.NO_FOCUS_TARGET) return false;
        focusPosition(binding.episode, target);
        return true;
    }

    private void focusUpperFromEpisode() {
        if (isVisible(binding.array) && arrayAdapter.getItemCount() > 0) focusArray();
        else focusUpperFromArray();
    }

    /** 分段行往上：有季度行就落季度，否则落线路。 */
    private void focusUpperFromArray() {
        if (isVisible(binding.season) && seasonAdapter.getItemCount() > 0) focusSeason();
        else focusFlag();
    }

    private void focusLowerFromFlag() {
        if (isVisible(binding.season) && seasonAdapter.getItemCount() > 0) focusSeason();
        else if (isVisible(binding.array) && arrayAdapter.getItemCount() > 0) focusArray();
        else focusPosition(binding.episode, episodeAdapter.getPosition());
    }

    private void focusEpisodeFromArray() {
        int position = binding.array.getSelectedPosition();
        selectSegment(position, false);
        focusPosition(binding.episode, getSegmentFocusPosition(position));
    }

    private int getSegmentFocusPosition(int position) {
        if (position < 0 || position >= segmentStarts.size()) return episodeAdapter.getPosition();
        int selectedEpisode = getSelectedEpisodePosition(allEpisodes);
        int start = segmentStarts.get(position);
        int end = segmentEnds.get(position);
        return selectedEpisode >= start && selectedEpisode < end ? selectedEpisode - start : 0;
    }

    private void focusFlag() {
        focusPosition(binding.flag, flagAdapter.getPosition());
    }

    private void focusArray() {
        int position = Math.max(0, binding.array.getSelectedPosition());
        focusPosition(binding.array, position);
    }

    private void focusSeason() {
        int position = Math.max(0, binding.season.getSelectedPosition());
        focusPosition(binding.season, position);
    }

    private int getFocusedPosition(RecyclerView recycler) {
        View child = recycler.getFocusedChild();
        return child == null ? RecyclerView.NO_POSITION : recycler.getChildAdapterPosition(child);
    }

    private void focusPosition(BaseGridView grid, int position) {
        if (grid.getAdapter() == null || grid.getAdapter().getItemCount() == 0) return;
        int target = Math.max(0, Math.min(position, grid.getAdapter().getItemCount() - 1));
        grid.setSelectedPosition(target, holder -> holder.itemView.requestFocus());
    }

    private boolean isVisible(View view) {
        return view.getVisibility() == View.VISIBLE;
    }

    private int getEpisodeSegmentSize(int size) {
        return size <= 60 ? 20 : 40;
    }

    private int getTmdbEpisodeSegmentSize(int size) {
        // 强制使用固定分段大小，避免一次性加载过多剧集导致卡顿
        if (size <= 30) return size;  // 30集以下不分段
        if (size <= 100) return 30;   // 31-100集，每段30集
        return 40;                    // 100集以上，每段40集
    }

    private int getTmdbCardColumn() {
        return TmdbEpisodeGridPolicy.tvAdaptiveSpanCount(getResources().getConfiguration().screenWidthDp);
    }

    private void scrollToSelectedEpisode() {
        int position = getSelectedEpisodePosition(allEpisodes);
        scrollToSegment(position);
        scrollToEpisode(position - getSegmentStart(selectedSegment), true);
    }

    private void scrollToSegment(int episodePosition) {
        if (segmentStarts.isEmpty()) return;
        selectedSegment = resolveSelectedSegment(episodePosition);
        binding.array.setSelectedPosition(selectedSegment);
        binding.array.scrollToPosition(selectedSegment);
    }

    private int getSegmentStart(int position) {
        if (position < 0 || position >= segmentStarts.size()) return 0;
        return segmentStarts.get(position);
    }

    private int getSelectedEpisodePosition(List<Episode> episodes) {
        if (episodes == null || episodes.isEmpty()) return 0;
        for (int i = 0; i < episodes.size(); i++) if (episodes.get(i).isSelected()) return i;
        return 0;
    }

    private void scrollToEpisode(int position, boolean requestFocus) {
        if (position < 0 || position >= episodeAdapter.getItemCount()) return;
        binding.episode.post(() -> {
            if (binding == null) return;
            binding.episode.setSelectedPosition(position);
            if (requestFocus) focusPosition(binding.episode, position);
            alignEpisodeScroll(position);
        });
    }

    @Override
    public void onItemClick(Flag item) {
        ((FlagAdapter.OnClickListener) requireActivity()).onItemClick(item);
        flagAdapter.notifyItemRangeChanged(0, flagAdapter.getItemCount());
        setEpisodes(item);
    }

    @Override
    public void onItemClick(Episode item) {
        ((EpisodeAdapter.OnClickListener) requireActivity()).onItemClick(item);
        dismiss();
    }

    @Override
    public void onRevSort() {
    }

    @Override
    public void onRevPlay(TextView view) {
    }

    @Override
    public void onSegmentClick(int position) {
        selectSegment(position, true);
    }

    @Override
    public void onSegmentFocus(int position) {
        selectSegment(position, true);
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        if (dismissListener != null) dismissListener.onDismiss(dialog);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable android.os.Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setWindowAnimations(0);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0f);
        }
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        Window window = getDialog() == null ? null : getDialog().getWindow();
        if (window == null || binding == null) return;
        window.getDecorView().setPadding(0, 0, 0, 0);
        clearParentPaddingAndFillHeight();
        // 选集对话框统一全屏居中显示，文本模式不再使用右侧侧抽屉
        int gravity = Gravity.CENTER;
        int width = WindowManager.LayoutParams.MATCH_PARENT;
        window.setGravity(gravity);
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = width;
        params.height = WindowManager.LayoutParams.MATCH_PARENT;
        params.gravity = gravity;
        params.x = 0;
        params.y = 0;
        window.setAttributes(params);
        window.setLayout(width, WindowManager.LayoutParams.MATCH_PARENT);
        binding.getRoot().post(() -> {
            clearParentPaddingAndFillHeight();
            window.setLayout(width, WindowManager.LayoutParams.MATCH_PARENT);
        });
    }

    private void clearParentPaddingAndFillHeight() {
        View view = binding.getRoot();
        fillHeight(view);
        while (view.getParent() instanceof View parent) {
            if (parent instanceof ViewGroup group) group.setPadding(0, 0, 0, 0);
            fillHeight(parent);
            view = parent;
        }
    }

    private void fillHeight(View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params != null) {
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.height = ViewGroup.LayoutParams.MATCH_PARENT;
            view.setLayoutParams(params);
        }
        view.setMinimumHeight(ResUtil.getScreenHeight(requireContext()));
    }
}
