package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.TmdbConfig;
import com.fongmi.android.tv.bean.TmdbEpisode;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.TmdbPerson;
import com.fongmi.android.tv.service.TmdbService;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.adapter.EpisodeStillAdapter;
import com.fongmi.android.tv.ui.adapter.TmdbPersonAdapter;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class EpisodeDetailDialog {

    public static void show(FragmentActivity activity, Episode episode) {
        show(activity, episode, null, null, null, null);
    }

    public static void show(FragmentActivity activity, Episode episode,
                           com.fongmi.android.tv.bean.Site site,
                           java.util.List<String> preloadedPhotos,
                           java.util.List<TmdbPerson> preloadedGuests) {
        show(activity, episode, site, preloadedPhotos, preloadedGuests, null);
    }

    public static void show(FragmentActivity activity, Episode episode,
                           com.fongmi.android.tv.bean.Site site,
                           java.util.List<String> preloadedPhotos,
                           java.util.List<TmdbPerson> preloadedGuests,
                           android.content.DialogInterface.OnDismissListener dismissListener) {
        if (activity == null || episode == null) return;
        TmdbEpisode tmdbEpisode = episode.getTmdbEpisode();
        if (tmdbEpisode == null) {
            // 电影没有分集对象，尝试从详情页获取影片级数据
            if (activity instanceof com.fongmi.android.tv.ui.host.TmdbDetailHost) {
                com.fongmi.android.tv.ui.host.TmdbDetailHost host = (com.fongmi.android.tv.ui.host.TmdbDetailHost) activity;
                TmdbItem movieItem = host.getMatchedTmdbItem();
                JsonObject movieDetail = host.getMatchedTmdbDetail();
                if (movieItem != null && movieItem.isMovie()) {
                    showMovieDetail(activity, episode, movieItem, movieDetail, dismissListener);
                    return;
                }
            }
            showSimpleDialog(activity, episode, dismissListener);
            return;
        }

        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_episode_detail, null);
        ImageView still = view.findViewById(R.id.still);
        TextView title = view.findViewById(R.id.title);
        TextView originalName = view.findViewById(R.id.originalName);
        TextView meta = view.findViewById(R.id.meta);
        TextView overview = view.findViewById(R.id.overview);
        TextView photoTitle = view.findViewById(R.id.photoTitle);
        RecyclerView photoList = view.findViewById(R.id.photoList);
        TextView guestsTitle = view.findViewById(R.id.guestsTitle);
        RecyclerView guestsList = view.findViewById(R.id.guestsList);
        boolean light = resolveLightTheme(activity);

        applyTheme(view, light);
        bindBasicInfo(activity, episode, tmdbEpisode, still, title, originalName, meta, overview);
        bindHorizontalList(photoList, 12);
        bindHorizontalList(guestsList, 12);

        Dialog dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(view);
        view.findViewById(R.id.close).setOnClickListener(v -> dialog.dismiss());
        if (dismissListener != null) dialog.setOnDismissListener(dismissListener);
        dialog.show();
        applyWindowSize(dialog);

        // 若已预加载数据，直接绑定，避免重复 API 请求；否则异步加载
        if (preloadedPhotos != null || preloadedGuests != null) {
            bindMedia(activity, preloadedPhotos, preloadedGuests, light, photoTitle, photoList, guestsTitle, guestsList);
        } else {
            loadEpisodeMedia(activity, tmdbEpisode, light, photoTitle, photoList, guestsTitle, guestsList);
        }
    }

    /**
     * 电影场景：长按线路/清晰度时展示影片级 TMDB 详情。
     * 复用剧集详情布局，标题=影片名，原始名称=源站文件名（完整多行展示），
     * meta=评分/上映日期/时长，简介=影片简介，剧照/演员异步加载。
     */
    private static void showMovieDetail(FragmentActivity activity, Episode episode, TmdbItem movieItem, JsonObject movieDetail,
                                        android.content.DialogInterface.OnDismissListener dismissListener) {
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_episode_detail, null);
        ImageView still = view.findViewById(R.id.still);
        TextView title = view.findViewById(R.id.title);
        TextView originalName = view.findViewById(R.id.originalName);
        TextView meta = view.findViewById(R.id.meta);
        TextView overview = view.findViewById(R.id.overview);
        TextView photoTitle = view.findViewById(R.id.photoTitle);
        RecyclerView photoList = view.findViewById(R.id.photoList);
        TextView guestsTitle = view.findViewById(R.id.guestsTitle);
        RecyclerView guestsList = view.findViewById(R.id.guestsList);
        boolean light = resolveLightTheme(activity);

        applyTheme(view, light);

        // 剧照区域展示影片背景图
        String backdrop = movieItem.getBackdropUrl();
        if (!TextUtils.isEmpty(backdrop)) {
            still.setVisibility(View.VISIBLE);
            Glide.with(activity)
                    .load(tmdbImageUrl(backdrop, "w780"))
                    .placeholder(R.color.black)
                    .error(R.color.black)
                    .centerCrop()
                    .into(still);
        } else {
            still.setVisibility(View.GONE);
        }

        // 标题：影片名（TMDB 归一化标题）
        String movieTitle = movieItem.getTitle();
        title.setText(TextUtils.isEmpty(movieTitle) ? episode.getName() : movieTitle);

        // 原始名称：源站文件名，完整多行展示（解决长名截断问题）
        String sourceName = episode.getName();
        if (!TextUtils.isEmpty(sourceName) && !sourceName.equals(movieTitle)) {
            originalName.setText("原始名称：" + sourceName);
            originalName.setVisibility(View.VISIBLE);
        } else {
            originalName.setVisibility(View.GONE);
        }

        // meta：评分 / 上映日期 / 时长
        meta.setText(movieMeta(movieItem, movieDetail));
        meta.setVisibility(TextUtils.isEmpty(meta.getText()) ? View.GONE : View.VISIBLE);

        // 简介
        String movieOverview = movieItem.getOverview();
        if (TextUtils.isEmpty(movieOverview) && movieDetail != null) movieOverview = jsonString(movieDetail, "overview");
        overview.setText(TextUtils.isEmpty(movieOverview) ? "暂无简介" : movieOverview);

        // 演员区域改用“演员”标题（电影没有客串演员概念）
        guestsTitle.setText(R.string.detail_tmdb_cast);

        bindHorizontalList(photoList, 12);
        bindHorizontalList(guestsList, 12);

        Dialog dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(view);
        view.findViewById(R.id.close).setOnClickListener(v -> dialog.dismiss());
        if (dismissListener != null) dialog.setOnDismissListener(dismissListener);
        dialog.show();
        applyWindowSize(dialog);

        loadMovieMedia(activity, movieDetail, light, photoTitle, photoList, guestsTitle, guestsList);
    }

    private static String movieMeta(TmdbItem movieItem, JsonObject detail) {
        List<String> metas = new ArrayList<>();
        double rating = movieItem.getRating();
        if (rating <= 0 && detail != null) rating = jsonDouble(detail, "vote_average");
        if (rating > 0) metas.add(String.format(java.util.Locale.US, "%.1f", rating));
        if (detail != null) {
            String date = jsonString(detail, "release_date");
            if (!TextUtils.isEmpty(date)) metas.add(date);
            int runtime = (int) jsonDouble(detail, "runtime");
            if (runtime > 0) metas.add(runtime + "m");
        }
        return TextUtils.join(" / ", metas);
    }

    private static void loadMovieMedia(FragmentActivity activity, JsonObject movieDetail, boolean light, TextView photoTitle, RecyclerView photoList, TextView guestsTitle, RecyclerView guestsList) {
        if (movieDetail == null) return;
        Task.execute(() -> {
            try {
                TmdbConfig config = TmdbConfig.objectFrom(Setting.getTmdbConfig());
                if (config == null || !config.isReady()) return;
                TmdbService service = new TmdbService();
                List<String> photos = service.photos(movieDetail, config);
                List<TmdbPerson> cast = service.cast(movieDetail, config);
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    bindMedia(activity, photos, cast, light, photoTitle, photoList, guestsTitle, guestsList);
                });
            } catch (Exception e) {
                android.util.Log.w("EpisodeDetailDialog", "load movie detail failed", e);
            }
        });
    }

    private static String jsonString(JsonObject obj, String key) {
        try {
            if (obj != null && obj.has(key) && !obj.get(key).isJsonNull()) return obj.get(key).getAsString();
        } catch (Exception ignored) {
        }
        return "";
    }

    private static double jsonDouble(JsonObject obj, String key) {
        try {
            if (obj != null && obj.has(key) && !obj.get(key).isJsonNull()) return obj.get(key).getAsDouble();
        } catch (Exception ignored) {
        }
        return 0;
    }

    private static void applyWindowSize(Dialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) return;
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.setDimAmount(0f);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
    }

    private static void bindBasicInfo(Activity activity, Episode episode, TmdbEpisode tmdbEpisode, ImageView still, TextView title, TextView originalName, TextView meta, TextView overview) {
        if (!TextUtils.isEmpty(tmdbEpisode.getStillUrl())) {
            still.setVisibility(View.VISIBLE);
            Glide.with(activity)
                    .load(tmdbImageUrl(tmdbEpisode.getStillUrl(), "w780"))
                    .placeholder(R.color.black)
                    .error(R.color.black)
                    .centerCrop()
                    .into(still);
        } else {
            still.setVisibility(View.GONE);
        }
        title.setText(tmdbEpisode.getDisplayTitle());

        // 显示原始名称（刮削前的源站文件名）
        String sourceName = episode.getName();
        String displayTitle = tmdbEpisode.getDisplayTitle();
        // 只有当原始名称非空且与刮削标题不完全相同时才显示
        if (!TextUtils.isEmpty(sourceName) && !sourceName.equals(displayTitle)) {
            originalName.setText("原始名称：" + sourceName);
            originalName.setVisibility(View.VISIBLE);
        } else {
            originalName.setVisibility(View.GONE);
        }

        List<String> metas = new ArrayList<>();
        if (tmdbEpisode.getVoteAverage() > 0) metas.add(String.format(java.util.Locale.US, "%.1f", tmdbEpisode.getVoteAverage()));
        if (!TextUtils.isEmpty(tmdbEpisode.getDate())) metas.add(tmdbEpisode.getDate());
        if (tmdbEpisode.getRuntime() > 0) metas.add(tmdbEpisode.getRuntime() + "m");
        meta.setText(TextUtils.join(" / ", metas));
        meta.setVisibility(metas.isEmpty() ? View.GONE : View.VISIBLE);
        overview.setText(TextUtils.isEmpty(tmdbEpisode.getOverview()) ? "暂无简介" : tmdbEpisode.getOverview());
    }

    private static void applyTheme(View view, boolean light) {
        View root = view.findViewById(R.id.root);
        MaterialCardView panel = view.findViewById(R.id.panel);
        ImageView still = view.findViewById(R.id.still);
        TextView title = view.findViewById(R.id.title);
        TextView originalName = view.findViewById(R.id.originalName);
        TextView meta = view.findViewById(R.id.meta);
        TextView overview = view.findViewById(R.id.overview);
        TextView photoTitle = view.findViewById(R.id.photoTitle);
        TextView guestsTitle = view.findViewById(R.id.guestsTitle);
        MaterialButton close = view.findViewById(R.id.close);

        int overlay = light ? 0x99F4F7FA : 0xB3000000;
        int panelColor = light ? 0xFFF4F7FA : 0xFF2A2A2A;
        int imageBg = light ? 0xFFE7EDF3 : 0xFF1A1A1A;
        int primary = light ? 0xFF12202D : 0xFFFFFFFF;
        int secondary = light ? 0x9912202D : 0xFFAAAAAA;
        int body = light ? 0xCC12202D : 0xFFDDDDDD;
        int stroke = light ? 0x33424B57 : 0xFF4A4A4A;
        int control = light ? 0xFFE7EDF3 : 0xFF2A2A2A;

        if (root != null) root.setBackgroundColor(overlay);
        if (panel != null) {
            panel.setCardBackgroundColor(panelColor);
            panel.setStrokeColor(stroke);
        }
        if (still != null) still.setBackgroundColor(imageBg);
        title.setTextColor(primary);
        originalName.setTextColor(secondary);
        meta.setTextColor(secondary);
        overview.setTextColor(body);
        photoTitle.setTextColor(primary);
        guestsTitle.setTextColor(primary);
        close.setTextColor(primary);
        close.setStrokeColor(ColorStateList.valueOf(stroke));
        close.setBackgroundTintList(ColorStateList.valueOf(control));
        close.setRippleColor(ColorStateList.valueOf(light ? 0x1F12202D : 0x33FFFFFF));
    }

    private static void bindHorizontalList(RecyclerView view, int spacingDp) {
        view.setLayoutManager(new LinearLayoutManager(view.getContext(), LinearLayoutManager.HORIZONTAL, false));
        view.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull android.graphics.Rect outRect, @NonNull View child, RecyclerView parent, @NonNull RecyclerView.State state) {
                RecyclerView.Adapter<?> adapter = parent.getAdapter();
                if (adapter != null && parent.getChildAdapterPosition(child) < adapter.getItemCount() - 1) outRect.right = ResUtil.dp2px(spacingDp);
            }
        });
    }

    private static void loadEpisodeMedia(FragmentActivity activity, TmdbEpisode episode, boolean light, TextView photoTitle, RecyclerView photoList, TextView guestsTitle, RecyclerView guestsList) {
        if (episode.getTmdbId() == 0) return;
        Task.execute(() -> {
            try {
                TmdbConfig config = TmdbConfig.objectFrom(Setting.getTmdbConfig());
                if (config == null || !config.isReady()) return;
                TmdbService service = new TmdbService();
                JsonObject episodeJson = service.episode(episode.getTmdbId(), episode.getSeasonNumber(), episode.getNumber(), config);
                List<String> photos = service.episodePhotos(episodeJson, config);
                List<TmdbPerson> guests = service.episodeGuests(episodeJson, config);
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    bindMedia(activity, photos, guests, light, photoTitle, photoList, guestsTitle, guestsList);
                });
            } catch (Exception e) {
                android.util.Log.w("EpisodeDetailDialog", "load episode detail failed", e);
            }
        });
    }

    private static void bindMedia(FragmentActivity activity, List<String> photos, List<TmdbPerson> guests, boolean light, TextView photoTitle, RecyclerView photoList, TextView guestsTitle, RecyclerView guestsList) {
        if (photos != null && !photos.isEmpty()) {
            photoTitle.setVisibility(View.VISIBLE);
            photoList.setVisibility(View.VISIBLE);
            photoList.setAdapter(new EpisodeStillAdapter(photos, (url, position) -> PhotoViewerDialog.show(activity, photos, position, null)));
        }
        if (guests != null && !guests.isEmpty()) {
            guestsTitle.setVisibility(View.VISIBLE);
            guestsList.setVisibility(View.VISIBLE);
            TmdbPersonAdapter adapter = new TmdbPersonAdapter(person -> TmdbPersonDialog.show(activity, person, null));
            adapter.setLight(light);
            adapter.setItems(guests);
            guestsList.setAdapter(adapter);
        }
    }

    private static boolean resolveLightTheme(Activity activity) {
        int night = activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return Setting.resolveTmdbDetailLightTheme(Setting.getTmdbDetailTheme(), night == Configuration.UI_MODE_NIGHT_YES);
    }

    private static void showSimpleDialog(FragmentActivity activity, Episode episode,
                                         android.content.DialogInterface.OnDismissListener dismissListener) {
        // 标题放固定文案，源站文件名放可换行的正文，避免长名被单行标题截断
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.detail_tmdb_empty)
                .setMessage(episode.getName())
                .setPositiveButton(R.string.dialog_negative, null)
                .create();
        if (dismissListener != null) dialog.setOnDismissListener(dismissListener);
        dialog.show();
    }

    private static String tmdbImageUrl(String url, String size) {
        if (url == null || url.isEmpty()) return "";
        String result = url.replaceFirst("(/t/p/)([^/]+)(/)", "$1" + size + "$3");
        return result.equals(url) ? url.replaceFirst("/(w\\d+|h\\d+|original)/", "/" + size + "/") : result;
    }
}
