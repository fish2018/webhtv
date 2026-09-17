package com.fongmi.android.tv.ui.adapter;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.TmdbEpisode;
import com.fongmi.android.tv.databinding.AdapterEpisodeGridBinding;
import com.fongmi.android.tv.databinding.AdapterEpisodeHoriBinding;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.base.BaseEpisodeHolder;
import com.fongmi.android.tv.ui.base.ViewType;
import com.fongmi.android.tv.ui.custom.EpisodeTitlePopup;
import com.fongmi.android.tv.ui.holder.EpisodeGridHolder;
import com.fongmi.android.tv.ui.holder.EpisodeHoriHolder;
import com.fongmi.android.tv.utils.EpisodeTitleCompact;
import com.fongmi.android.tv.utils.EpisodeTitleFormatter;
import com.fongmi.android.tv.utils.Task;
import com.google.android.material.textview.MaterialTextView;

import java.util.ArrayList;
import java.util.List;

public class EpisodeAdapter extends RecyclerView.Adapter<BaseEpisodeHolder> {

    private final OnClickListener listener;
    private final List<Episode> mItems;
    private OnTitleReadyListener titleReadyListener;
    private List<Episode> pendingTitleItems;
    private List<String> pendingTitleRawNames;
    private boolean pendingTitleCompact;
    private int titleRequestId;
    private int viewType;
    private boolean useTmdbCard;
    private boolean nativeGridExpanded;
    private String fallbackStillUrl = "";

    public EpisodeAdapter(OnClickListener listener, int viewType) {
        this(listener, viewType, new ArrayList<>());
    }

    public EpisodeAdapter(OnClickListener listener, int viewType, ArrayList<Episode> items) {
        this.listener = listener;
        this.viewType = viewType;
        this.mItems = items;
    }

    public interface OnClickListener {

        void onItemClick(Episode item);
    }

    public interface OnTitleReadyListener {

        void onTitleReady();
    }

    public void setOnTitleReadyListener(OnTitleReadyListener listener) {
        this.titleReadyListener = listener;
    }

    public void addAll(List<Episode> items) {
        if (titleReadyListener == null) {
            invalidateTitleRequest();
            ArrayList<Episode> snapshot = items == null ? new ArrayList<>() : new ArrayList<>(items);
            EpisodeTitleCompact.apply(snapshot);
            mItems.clear();
            mItems.addAll(snapshot);
            notifyDataSetChanged();
            return;
        }
        requestTitleCompaction(items, true);
    }

    public void refreshTitles() {
        if (titleReadyListener == null) {
            invalidateTitleRequest();
            EpisodeTitleCompact.apply(mItems);
            notifyDataSetChanged();
            return;
        }
        requestTitleCompaction(mItems, false);
    }


    public void refreshMetadata(List<Episode> items) {
        invalidateTitleRequest();
        ArrayList<Episode> snapshot = items == null ? new ArrayList<>() : new ArrayList<>(items);
        if (hasSameItems(snapshot)) {
            if (!mItems.isEmpty()) notifyItemRangeChanged(0, getItemCount());
            return;
        }
        mItems.clear();
        mItems.addAll(snapshot);
        notifyDataSetChanged();
    }

    private boolean hasSameItems(List<Episode> items) {
        if (items.size() != mItems.size()) return false;
        for (int i = 0; i < items.size(); i++) if (items.get(i) != mItems.get(i)) return false;
        return true;
    }

    private void requestTitleCompaction(List<Episode> items, boolean replaceItems) {
        ArrayList<Episode> snapshot = items == null ? new ArrayList<>() : new ArrayList<>(items);
        ArrayList<String> rawNames = new ArrayList<>(snapshot.size());
        for (Episode episode : snapshot) {
            rawNames.add(episode.getRawDisplayName());
            episode.setDisplayName(null);
        }
        if (replaceItems) {
            mItems.clear();
            mItems.addAll(snapshot);
        }
        notifyDataSetChanged();
        boolean compact = Setting.isCompactEpisodeTitle();
        if (!compact || snapshot.isEmpty()) {
            invalidateTitleRequest();
            notifyTitleReady();
            return;
        }
        if (isPendingTitleRequest(snapshot, rawNames, compact)) return;
        int requestId = ++titleRequestId;
        pendingTitleItems = snapshot;
        pendingTitleRawNames = rawNames;
        pendingTitleCompact = compact;
        Task.submit(() -> {
            List<String> displayNames;
            try {
                displayNames = EpisodeTitleCompact.computeRaw(rawNames, compact);
            } catch (Throwable ignored) {
                displayNames = null;
            }
            List<String> result = displayNames;
            App.post(() -> finishTitleCompaction(requestId, snapshot, rawNames, result));
        });
    }

    private void finishTitleCompaction(int requestId, List<Episode> snapshot, List<String> rawNames, List<String> displayNames) {
        if (requestId != titleRequestId) return;
        clearPendingTitleRequest();
        if (!isCurrentTitleRequest(snapshot, rawNames)) return;
        if (displayNames != null) EpisodeTitleCompact.apply(snapshot, displayNames);
        notifyDataSetChanged();
        notifyTitleReady();
    }

    private boolean isPendingTitleRequest(List<Episode> snapshot, List<String> rawNames, boolean compact) {
        if (pendingTitleItems == null || pendingTitleRawNames == null || pendingTitleCompact != compact) return false;
        if (snapshot.size() != pendingTitleItems.size() || rawNames.size() != pendingTitleRawNames.size()) return false;
        for (int i = 0; i < snapshot.size(); i++) {
            if (snapshot.get(i) != pendingTitleItems.get(i) || !TextUtils.equals(rawNames.get(i), pendingTitleRawNames.get(i))) return false;
        }
        return true;
    }

    private boolean isCurrentTitleRequest(List<Episode> snapshot, List<String> rawNames) {
        if (snapshot.size() != mItems.size()) return false;
        for (int i = 0; i < snapshot.size(); i++) {
            Episode current = mItems.get(i);
            if (current != snapshot.get(i) || !TextUtils.equals(rawNames.get(i), current.getRawDisplayName())) return false;
        }
        return true;
    }

    private void invalidateTitleRequest() {
        titleRequestId++;
        clearPendingTitleRequest();
    }

    private void clearPendingTitleRequest() {
        pendingTitleItems = null;
        pendingTitleRawNames = null;
    }

    private void notifyTitleReady() {
        if (titleReadyListener != null) titleReadyListener.onTitleReady();
    }

    public void setUseTmdbCard(boolean useTmdbCard) {
        if (this.useTmdbCard == useTmdbCard) return;
        this.useTmdbCard = useTmdbCard;
        notifyDataSetChanged();
    }

    public void setViewType(int viewType) {
        if (this.viewType == viewType) return;
        this.viewType = viewType;
        notifyDataSetChanged();
    }

    public boolean isUsingTmdbCard() {
        return useTmdbCard;
    }

    public void setNativeGridExpanded(boolean nativeGridExpanded) {
        if (this.nativeGridExpanded == nativeGridExpanded) return;
        this.nativeGridExpanded = nativeGridExpanded;
        notifyDataSetChanged();
    }

    public void setFallbackStillUrl(String fallbackStillUrl) {
        String value = TextUtils.isEmpty(fallbackStillUrl) ? "" : fallbackStillUrl;
        if (this.fallbackStillUrl.equals(value)) return;
        this.fallbackStillUrl = value;
        if (useTmdbCard) notifyDataSetChanged();
    }

    public int getPosition() {
        for (int i = 0; i < mItems.size(); i++) if (mItems.get(i).isSelected()) return i;
        return 0;
    }

    public int getPosition(Episode item) {
        return mItems.indexOf(item);
    }

    public Episode getActivated() {
        return mItems.get(getPosition());
    }

    public Episode getNext() {
        int current = getPosition();
        int max = getItemCount() - 1;
        current = ++current > max ? max : current;
        return mItems.get(current);
    }

    public Episode getPrev() {
        int current = getPosition();
        current = --current < 0 ? 0 : current;
        return mItems.get(current);
    }

    public List<Episode> getItems() {
        return mItems;
    }

    public boolean isEmpty() {
        return getItemCount() == 0;
    }

    /**
     * 绑定标题和长按事件（供 Holder 调用）
     */
    public static String getTitle(Episode item) {
        if (item == null) return "";
        TmdbEpisode tmdbEpisode = item.getTmdbEpisode();
        if (tmdbEpisode != null) return getTmdbTitle(item, tmdbEpisode);
        return getNativeTitle(item);
    }

    public static String getNativeTitle(Episode item) {
        if (item == null) return "";
        String title = TextUtils.isEmpty(item.getDisplayName()) ? item.getName() : item.getDisplayName();
        if (TextUtils.isEmpty(item.getDesc()) || title.startsWith(item.getDesc())) return title;
        return item.getDesc().concat(title);
    }

    public static String getNativeDisplayTitle(Episode item) {
        return getNativeDisplayTitle(item, Setting.isTmdbEpisodeFileSize());
    }

    static String getNativeDisplayTitle(Episode item, boolean separateFileSize) {
        String title = getNativeTitle(item);
        if (!separateFileSize) return title;
        return EpisodeTitleFormatter.removeFileSizes(title);
    }

    public static String getNativeFileSize(Episode item) {
        return getNativeFileSize(item, Setting.isTmdbEpisodeFileSize());
    }

    static String getNativeFileSize(Episode item, boolean includeFileSize) {
        if (item == null || !includeFileSize) return "";
        return EpisodeTitleFormatter.extractFileSize(item.getRawDisplayName());
    }

    private static String getTmdbTitle(Episode item, TmdbEpisode tmdbEpisode) {
        String title = getCardTitle(item);
        return EpisodeTitleFormatter.withSourceFileSize(item.getRawDisplayName(), title, Setting.isTmdbEpisodeFileSize());
    }

    public static String getCardTitle(Episode item) {
        return getCardTitle(item, item == null ? null : item.getTmdbEpisode());
    }

    public static String getCardTitle(Episode item, TmdbEpisode tmdbEpisode) {
        if (item == null) return "";
        if (tmdbEpisode == null) return getNativeTitle(item);
        int number = tmdbEpisode.getNumber();
        String label = number > 0 ? String.valueOf(number) : item.getName();
        String title = EpisodeTitleFormatter.formatTmdbTitle(label, item.getName(), tmdbEpisode.getTitle(), Setting.getTmdbEpisodeShowScrapedName());
        if (TextUtils.isEmpty(title)) title = TextUtils.isEmpty(item.getName()) ? item.getDisplayName() : item.getName();
        return title;
    }

    public static String getCardFileSize(Episode item, String title) {
        return getCardFileSize(item, title, Setting.isTmdbEpisodeFileSize());
    }

    static String getCardFileSize(Episode item, String title, boolean includeFileSize) {
        if (item == null || !includeFileSize) return "";
        String fileSize = EpisodeTitleFormatter.extractFileSize(item.getRawDisplayName());
        if (TextUtils.isEmpty(fileSize) || EpisodeTitleFormatter.containsFileSize(title)) return "";
        return fileSize;
    }

    public static void bindTitle(MaterialTextView text, Episode item) {
        String title = getTitle(item);
        text.setText(title);
        applyMarquee(text, item.isSelected(), text.hasFocus());
        text.setOnFocusChangeListener((view, hasFocus) -> applyMarquee(text, item.isSelected(), hasFocus));
        bindTitlePopup(text, item);
    }

    public static void bindTitlePopup(View view, Episode item) {
        bindTitlePopup(view, item, true);
    }

    public static void bindNativeTitlePopup(View view, Episode item) {
        bindTitlePopup(view, item, false);
    }

    private static void bindTitlePopup(View view, Episode item, boolean tmdbTitle) {
        if (view == null) return;
        view.setOnLongClickListener(anchor -> showTitlePopup(anchor, item, tmdbTitle));
        view.setOnTouchListener(new View.OnTouchListener() {
            private final Handler handler = new Handler(Looper.getMainLooper());
            private final int slop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
            private float downX;
            private float downY;
            private boolean shown;
            private final Runnable show = () -> shown = showTitlePopup(view, item, tmdbTitle);

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        shown = false;
                        downX = event.getX();
                        downY = event.getY();
                        handler.postDelayed(show, ViewConfiguration.getLongPressTimeout());
                        return false;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(event.getX() - downX) > slop || Math.abs(event.getY() - downY) > slop) handler.removeCallbacks(show);
                        return false;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        handler.removeCallbacks(show);
                        return shown;
                    default:
                        return false;
                }
            }
        });
    }

    public static boolean showTitlePopup(View anchor, Episode item) {
        return showTitlePopup(anchor, item, true);
    }

    private static boolean showTitlePopup(View anchor, Episode item, boolean tmdbTitle) {
        return EpisodeTitlePopup.show(anchor, tmdbTitle ? getTitle(item) : getNativeTitle(item));
    }

    public static void dismissTitlePopup() {
        EpisodeTitlePopup.dismiss();
    }

    private static void applyMarquee(MaterialTextView text, boolean selected, boolean focused) {
        text.setSelected(selected || focused);
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @Override
    public int getItemViewType(int position) {
        return viewType;
    }

    @Override
    public void onBindViewHolder(@NonNull BaseEpisodeHolder holder, int position) {
        holder.setUseTmdbCard(useTmdbCard);
        holder.setFallbackStillUrl(fallbackStillUrl);
        holder.setNativeGridExpanded(nativeGridExpanded);
        holder.initView(mItems.get(position));
    }

    @NonNull
    @Override
    public BaseEpisodeHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == ViewType.HORI) {
            return new EpisodeHoriHolder(AdapterEpisodeHoriBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), listener);
        } else {
            return new EpisodeGridHolder(AdapterEpisodeGridBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), listener, parent);
        }
    }
}
