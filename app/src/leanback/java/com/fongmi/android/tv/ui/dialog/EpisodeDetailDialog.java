package com.fongmi.android.tv.ui.dialog;

import android.content.res.Configuration;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.TmdbConfig;
import com.fongmi.android.tv.bean.TmdbEpisode;
import com.fongmi.android.tv.bean.TmdbPerson;
import com.fongmi.android.tv.service.TmdbService;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.adapter.EpisodePhotoAdapter;
import com.fongmi.android.tv.ui.adapter.TmdbPersonAdapter;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * 剧集详情对话框
 */
public class EpisodeDetailDialog {

    public static void show(FragmentActivity activity, Episode episode) {
        show(activity, episode, null, null, null, null);
    }

    public static void show(FragmentActivity activity, Episode episode, Site site) {
        show(activity, episode, site, null, null, null);
    }

    public static void show(FragmentActivity activity, Episode episode, Site site,
                           java.util.List<String> preloadedPhotos,
                           java.util.List<TmdbPerson> preloadedGuests) {
        show(activity, episode, site, preloadedPhotos, preloadedGuests, null);
    }

    /**
     * 显示剧集详情对话框（可选预加载数据，避免重复请求 API）
     * @param preloadedPhotos 预加载的剧照列表，null 时异步请求
     * @param preloadedGuests 预加载的客串演员列表，null 时异步请求
     * @param dismissListener 对话框关闭回调，null 时不设置
     */
    public static void show(FragmentActivity activity, Episode episode, Site site,
                           java.util.List<String> preloadedPhotos,
                           java.util.List<TmdbPerson> preloadedGuests,
                           android.content.DialogInterface.OnDismissListener dismissListener) {
        TmdbEpisode tmdbEpisode = episode.getTmdbEpisode();
        if (tmdbEpisode == null) {
            // 电影没有分集对象，尝试从宿主获取影片级数据
            if (activity instanceof com.fongmi.android.tv.ui.host.TmdbDetailHost) {
                com.fongmi.android.tv.ui.host.TmdbDetailHost host = (com.fongmi.android.tv.ui.host.TmdbDetailHost) activity;
                com.fongmi.android.tv.bean.TmdbItem movieItem = host.getMatchedTmdbItem();
                if (movieItem != null && movieItem.isMovie()) {
                    showMovieDetail(activity, episode, movieItem, dismissListener);
                    return;
                }
            }
            // 没有TMDB数据，显示简单信息
            showSimpleDialog(activity, episode, dismissListener);
            return;
        }

        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_episode_detail, null);

        android.widget.ScrollView scrollView = view.findViewById(R.id.scrollView);
        androidx.cardview.widget.CardView stillCard = view.findViewById(R.id.stillCard);
        ImageView still = view.findViewById(R.id.still);
        TextView title = view.findViewById(R.id.title);
        TextView originalName = view.findViewById(R.id.originalName);
        TextView rating = view.findViewById(R.id.rating);
        TextView date = view.findViewById(R.id.date);
        TextView runtime = view.findViewById(R.id.runtime);
        TextView overview = view.findViewById(R.id.overview);
        TextView photosLabel = view.findViewById(R.id.photosLabel);
        androidx.leanback.widget.HorizontalGridView photosGrid = view.findViewById(R.id.photosGrid);
        TextView guestsLabel = view.findViewById(R.id.guestsLabel);
        androidx.leanback.widget.HorizontalGridView guestsGrid = view.findViewById(R.id.guestsGrid);

        boolean light = resolveLightTheme(activity);
        applyTheme(view, light);

        // 加载剧照 - 使用原图
        if (!tmdbEpisode.getStillUrl().isEmpty()) {
            Glide.with(activity)
                    .load(tmdbImageUrl(tmdbEpisode.getStillUrl(), "original"))
                    .placeholder(R.color.black)
                    .error(R.color.black)
                    .fitCenter()
                    .into(still);
            still.setVisibility(View.VISIBLE);
        } else {
            still.setVisibility(View.GONE);
        }

        // 设置标题
        title.setText(tmdbEpisode.getDisplayTitle());

        // 显示原始名称（刮削前的源站文件名）
        String sourceName = episode.getName();
        String displayTitle = tmdbEpisode.getDisplayTitle();
        // 只有当原始名称非空且与刮削标题不完全相同时才显示
        if (!android.text.TextUtils.isEmpty(sourceName) && !sourceName.equals(displayTitle)) {
            originalName.setText("原始名称：" + sourceName);
            originalName.setVisibility(View.VISIBLE);
        } else {
            originalName.setVisibility(View.GONE);
        }

        // 设置评分
        if (tmdbEpisode.getVoteAverage() > 0) {
            rating.setText(String.format("★ %.1f", tmdbEpisode.getVoteAverage()));
            rating.setVisibility(View.VISIBLE);
        } else {
            rating.setVisibility(View.GONE);
        }

        // 设置日期
        if (!tmdbEpisode.getDate().isEmpty()) {
            date.setText(String.format("播出日期: %s", tmdbEpisode.getDate()));
            date.setVisibility(View.VISIBLE);
        } else {
            date.setVisibility(View.GONE);
        }

        // 设置时长
        if (tmdbEpisode.getRuntime() > 0) {
            runtime.setText(String.format("时长: %d 分钟", tmdbEpisode.getRuntime()));
            runtime.setVisibility(View.VISIBLE);
        } else {
            runtime.setVisibility(View.GONE);
        }

        // 设置简介
        if (!tmdbEpisode.getOverview().isEmpty()) {
            overview.setText(tmdbEpisode.getOverview());
            overview.setVisibility(View.VISIBLE);
        } else {
            overview.setText("暂无简介");
        }

        // 如果已预加载数据，直接显示；否则异步加载
        if (preloadedPhotos != null || preloadedGuests != null) {
            bindPreloadedMedia(activity, preloadedPhotos, preloadedGuests, light, photosLabel, photosGrid, guestsLabel, guestsGrid);
        } else {
            // 初始隐藏本集图片，异步加载
            photosLabel.setVisibility(View.GONE);
            photosGrid.setVisibility(View.GONE);
            guestsLabel.setVisibility(View.GONE);
            guestsGrid.setVisibility(View.GONE);
            // 异步加载本集图片与客串演员
            loadEpisodeMedia(activity, tmdbEpisode, site, light, photosLabel, photosGrid, guestsLabel, guestsGrid);
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setView(view);

        AlertDialog alertDialog = builder.create();
        if (dismissListener != null) alertDialog.setOnDismissListener(dismissListener);
        alertDialog.show();

        // 对话框级别的按键导航：无论焦点在哪都能捕获遥控器方向键
        setupDialogKeyNavigation(alertDialog, scrollView, stillCard, photosGrid, guestsGrid);

        // 设置全屏显示，不透明背景
        if (alertDialog.getWindow() != null) {
            alertDialog.getWindow().setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            );
            // Window 背景与当前详情主题保持一致，避免显示/关闭动画露出反色底色
            alertDialog.getWindow().setBackgroundDrawableResource(light ? android.R.color.white : android.R.color.black);
        }

        // 延迟一帧确保布局完成后滚动到顶部
        scrollView.post(() -> scrollView.scrollTo(0, 0));
    }

    /**
     * 电影场景（TV版简化版）：长按线路展示 TmdbItem 基本信息
     * TV端 VideoActivity 只持有 TmdbItem，没有 detail JSON，
     * 所以不加载演员与剧照，只显示标题/评分/简介。
     */
    private static void showMovieDetail(FragmentActivity activity, Episode episode, com.fongmi.android.tv.bean.TmdbItem movieItem,
                                        android.content.DialogInterface.OnDismissListener dismissListener) {
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_episode_detail, null);

        android.widget.ScrollView scrollView = view.findViewById(R.id.scrollView);
        androidx.cardview.widget.CardView stillCard = view.findViewById(R.id.stillCard);
        ImageView still = view.findViewById(R.id.still);
        TextView title = view.findViewById(R.id.title);
        TextView originalName = view.findViewById(R.id.originalName);
        TextView rating = view.findViewById(R.id.rating);
        TextView date = view.findViewById(R.id.date);
        TextView runtime = view.findViewById(R.id.runtime);
        TextView overview = view.findViewById(R.id.overview);
        TextView photosLabel = view.findViewById(R.id.photosLabel);
        androidx.leanback.widget.HorizontalGridView photosGrid = view.findViewById(R.id.photosGrid);
        TextView guestsLabel = view.findViewById(R.id.guestsLabel);
        androidx.leanback.widget.HorizontalGridView guestsGrid = view.findViewById(R.id.guestsGrid);

        boolean light = resolveLightTheme(activity);
        applyTheme(view, light);

        // 背景图：影片 backdrop
        String backdrop = movieItem.getBackdropUrl();
        if (!android.text.TextUtils.isEmpty(backdrop)) {
            Glide.with(activity)
                    .load(backdrop)
                    .placeholder(R.color.black)
                    .error(R.color.black)
                    .fitCenter()
                    .into(still);
            still.setVisibility(View.VISIBLE);
        } else {
            still.setVisibility(View.GONE);
        }

        // 标题：影片名
        String movieTitle = movieItem.getTitle();
        title.setText(android.text.TextUtils.isEmpty(movieTitle) ? episode.getName() : movieTitle);

        // 原始名称：源站文件名，完整多行展示
        String sourceName = episode.getName();
        if (!android.text.TextUtils.isEmpty(sourceName) && !sourceName.equals(movieTitle)) {
            originalName.setText("原始名称：" + sourceName);
            originalName.setVisibility(View.VISIBLE);
        } else {
            originalName.setVisibility(View.GONE);
        }

        // 评分
        if (movieItem.getRating() > 0) {
            rating.setText(String.format("★ %.1f", movieItem.getRating()));
            rating.setVisibility(View.VISIBLE);
        } else {
            rating.setVisibility(View.GONE);
        }

        // 日期/时长：TmdbItem 里没有，隐藏
        date.setVisibility(View.GONE);
        runtime.setVisibility(View.GONE);

        // 简介
        String movieOverview = movieItem.getOverview();
        if (!android.text.TextUtils.isEmpty(movieOverview)) {
            overview.setText(movieOverview);
        } else {
            overview.setText("暂无简介");
        }

        // 演员与剧照：VideoActivity 没有 detail JSON，无法加载，隐藏
        photosLabel.setVisibility(View.GONE);
        photosGrid.setVisibility(View.GONE);
        guestsLabel.setVisibility(View.GONE);
        guestsGrid.setVisibility(View.GONE);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setView(view);

        AlertDialog alertDialog = builder.create();
        if (dismissListener != null) alertDialog.setOnDismissListener(dismissListener);
        alertDialog.show();

        // 设置全屏显示
        if (alertDialog.getWindow() != null) {
            alertDialog.getWindow().setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            );
            alertDialog.getWindow().setBackgroundDrawableResource(light ? android.R.color.white : android.R.color.black);
        }

        // 延迟一帧确保布局完成后滚动到顶部
        scrollView.post(() -> scrollView.scrollTo(0, 0));
    }

    private static boolean resolveLightTheme(FragmentActivity activity) {
        int night = activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return Setting.resolveTmdbDetailLightTheme(Setting.getTmdbDetailTheme(), night == Configuration.UI_MODE_NIGHT_YES);
    }

    private static void applyTheme(View view, boolean light) {
        View root = view.findViewById(R.id.root);
        androidx.cardview.widget.CardView stillCard = view.findViewById(R.id.stillCard);
        ImageView still = view.findViewById(R.id.still);
        TextView title = view.findViewById(R.id.title);
        TextView originalName = view.findViewById(R.id.originalName);
        TextView date = view.findViewById(R.id.date);
        TextView runtime = view.findViewById(R.id.runtime);
        TextView overviewLabel = view.findViewById(R.id.overviewLabel);
        TextView overview = view.findViewById(R.id.overview);
        TextView photosLabel = view.findViewById(R.id.photosLabel);
        TextView guestsLabel = view.findViewById(R.id.guestsLabel);

        int background = light ? 0xFFFFFFFF : 0xFF101214;
        int imageBackground = 0xFF1A1A1A;
        int primary = light ? 0xFF1A1A1A : 0xFFFFFFFF;
        int secondary = light ? 0xFF555555 : 0xFFAAAAAA;
        int body = light ? 0xFF333333 : 0xFFDDDDDD;

        root.setBackgroundColor(background);
        stillCard.setCardBackgroundColor(imageBackground);
        still.setBackgroundColor(imageBackground);
        title.setTextColor(primary);
        originalName.setTextColor(secondary);
        date.setTextColor(secondary);
        runtime.setTextColor(secondary);
        overviewLabel.setTextColor(primary);
        overview.setTextColor(body);
        photosLabel.setTextColor(primary);
        guestsLabel.setTextColor(primary);
    }

    private static void showSimpleDialog(FragmentActivity activity, Episode episode,
                                         android.content.DialogInterface.OnDismissListener dismissListener) {
        // 标题放固定文案，源站文件名放可换行的正文，避免长名被单行标题截断
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.detail_tmdb_empty)
                .setMessage(episode.getName())
                .setPositiveButton("关闭", null)
                .create();
        if (dismissListener != null) dialog.setOnDismissListener(dismissListener);
        dialog.show();
    }

    private static void loadEpisodeMedia(FragmentActivity activity, TmdbEpisode tmdbEpisode, Site site, boolean light,
                                         TextView photosLabel, androidx.leanback.widget.HorizontalGridView photosGrid,
                                         TextView guestsLabel, androidx.leanback.widget.HorizontalGridView guestsGrid) {
        // 只有有tmdbId才能加载图片
        if (tmdbEpisode.getTmdbId() == 0) {
            android.util.Log.d("EpisodeDetail", "跳过图片加载: tmdbId为0");
            return;
        }

        android.util.Log.d("EpisodeDetail", "开始加载图片: tmdbId=" + tmdbEpisode.getTmdbId() +
            ", season=" + tmdbEpisode.getSeasonNumber() + ", episode=" + tmdbEpisode.getNumber());

        new Thread(() -> {
            try {
                // 调用TMDB API获取剧集图片
                TmdbService service = new TmdbService();
                TmdbConfig config = TmdbConfig.objectFrom(Setting.getTmdbConfig());

                android.util.Log.d("EpisodeDetail", "开始请求TMDB API...");

                JsonObject episodeJson = service.episode(
                    tmdbEpisode.getTmdbId(),
                    tmdbEpisode.getSeasonNumber(),
                    tmdbEpisode.getNumber(),
                    config
                );

                android.util.Log.d("EpisodeDetail", "TMDB API返回成功");

                List<String> photos = service.episodePhotos(episodeJson, config);
                List<TmdbPerson> guests = service.episodeGuests(episodeJson, config);

                android.util.Log.d("EpisodeDetail", "图片数量: " + (photos != null ? photos.size() : 0));
                android.util.Log.d("EpisodeDetail", "客串演员数量: " + (guests != null ? guests.size() : 0));

                if ((photos != null && !photos.isEmpty()) || (guests != null && !guests.isEmpty())) {
                    activity.runOnUiThread(() -> {
                        if (photos != null && !photos.isEmpty()) {
                            photosLabel.setVisibility(View.VISIBLE);
                            photosGrid.setVisibility(View.VISIBLE);
                            photosGrid.setHorizontalSpacing(ResUtil.dp2px(12));
                            photosGrid.setRowHeight(ResUtil.dp2px(124));

                            EpisodePhotoAdapter photoAdapter = new EpisodePhotoAdapter(photos,
                                    (url, position) -> PhotoViewerDialog.show(activity, photos, position, null));
                            photoAdapter.setLight(light);
                            photosGrid.setAdapter(photoAdapter);
                        }

                        if (guests != null && !guests.isEmpty()) {
                            guestsLabel.setVisibility(View.VISIBLE);
                            guestsGrid.setVisibility(View.VISIBLE);
                            guestsGrid.setHorizontalSpacing(ResUtil.dp2px(12));
                            guestsGrid.setRowHeight(ResUtil.dp2px(154));

                            TmdbPersonAdapter guestAdapter = new TmdbPersonAdapter(person -> TmdbPersonDialog.show(activity, person, site));
                            guestAdapter.setLight(light);
                            guestAdapter.setItems(guests);
                            guestsGrid.setAdapter(guestAdapter);
                        }

                        android.util.Log.d("EpisodeDetail", "图片显示成功");
                    });
                } else {
                    android.util.Log.d("EpisodeDetail", "没有图片数据");
                }
            } catch (Exception e) {
                // 加载失败，保持隐藏状态
                android.util.Log.e("EpisodeDetail", "加载图片失败", e);
            }
        }).start();
    }

    private static String tmdbImageUrl(String url, String size) {
        if (url == null || url.isEmpty()) return "";
        String result = url.replaceFirst("(/t/p/)([^/]+)(/)", "$1" + size + "$3");
        return result.equals(url) ? url.replaceFirst("/(w\\d+|h\\d+|original)/", "/" + size + "/") : result;
    }

    /**
     * 绑定预加载的图片和客串数据（避免重复 API 请求）
     */
    private static void bindPreloadedMedia(FragmentActivity activity,
                                          java.util.List<String> photos,
                                          java.util.List<TmdbPerson> guests,
                                          boolean light,
                                          TextView photosLabel,
                                          androidx.leanback.widget.HorizontalGridView photosGrid,
                                          TextView guestsLabel,
                                          androidx.leanback.widget.HorizontalGridView guestsGrid) {
        if (photos != null && !photos.isEmpty()) {
            photosLabel.setVisibility(View.VISIBLE);
            photosGrid.setVisibility(View.VISIBLE);
            photosGrid.setHorizontalSpacing(ResUtil.dp2px(12));
            photosGrid.setRowHeight(ResUtil.dp2px(124));

            EpisodePhotoAdapter photoAdapter = new EpisodePhotoAdapter(photos,
                    (url, position) -> PhotoViewerDialog.show(activity, photos, position, null));
            photoAdapter.setLight(light);
            photosGrid.setAdapter(photoAdapter);
        } else {
            photosLabel.setVisibility(View.GONE);
            photosGrid.setVisibility(View.GONE);
        }

        if (guests != null && !guests.isEmpty()) {
            guestsLabel.setVisibility(View.VISIBLE);
            guestsGrid.setVisibility(View.VISIBLE);
            guestsGrid.setHorizontalSpacing(ResUtil.dp2px(12));
            guestsGrid.setRowHeight(ResUtil.dp2px(154));

            TmdbPersonAdapter guestAdapter = new TmdbPersonAdapter(person -> TmdbPersonDialog.show(activity, person, null));
            guestAdapter.setLight(light);
            guestAdapter.setItems(guests);
            guestsGrid.setAdapter(guestAdapter);
        } else {
            guestsLabel.setVisibility(View.GONE);
            guestsGrid.setVisibility(View.GONE);
        }
    }

    /**
     * 设置 ScrollView 按键监听，处理遥控器上下键滚动
     * 策略：ScrollView 持有焦点时，按下滚动；网格可见且有数据时，转移焦点到网格
     */
    /**
     * 对话框级别的按键导航：拦截方向键实现焦点在大海报与网格间跳转
     * 优势：无论当前焦点在哪都能捕获按键，比视图级监听器更可靠
     */
    private static void setupDialogKeyNavigation(AlertDialog dialog,
                                                 android.widget.ScrollView scrollView,
                                                 View stillCard,
                                                 androidx.leanback.widget.HorizontalGridView photosGrid,
                                                 androidx.leanback.widget.HorizontalGridView guestsGrid) {
        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) return false;

            View focus = dialog.getCurrentFocus();

            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                // 从大海报往下：跳到第一个有数据的网格
                if (focus == stillCard || (focus != null && focus.getParent() == stillCard)) {
                    if (photosGrid != null && photosGrid.getVisibility() == View.VISIBLE
                        && photosGrid.getAdapter() != null && photosGrid.getAdapter().getItemCount() > 0) {
                        photosGrid.requestFocus();
                        return true;
                    }
                    if (guestsGrid != null && guestsGrid.getVisibility() == View.VISIBLE
                        && guestsGrid.getAdapter() != null && guestsGrid.getAdapter().getItemCount() > 0) {
                        guestsGrid.requestFocus();
                        return true;
                    }
                }
            }

            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP) {
                // 客串卡片先回到剧照，剧照再回到大海报；焦点实际在网格 item 上，需要检查父级关系
                if (focus != null && (focus == guestsGrid || isDescendantOf(focus, guestsGrid))) {
                    if (photosGrid != null && photosGrid.getVisibility() == View.VISIBLE
                            && photosGrid.getAdapter() != null && photosGrid.getAdapter().getItemCount() > 0) {
                        photosGrid.requestFocus();
                    } else {
                        stillCard.requestFocus();
                    }
                    return true;
                }
                if (focus != null && (focus == photosGrid || isDescendantOf(focus, photosGrid))) {
                    stillCard.requestFocus();
                    return true;
                }
            }

            return false;
        });
    }

    /**
     * 检查 view 是否是 ancestor 的子孙视图
     */
    private static boolean isDescendantOf(View view, View ancestor) {
        if (ancestor == null) return false;
        ViewParent parent = view.getParent();
        while (parent != null) {
            if (parent == ancestor) return true;
            parent = parent.getParent();
        }
        return false;
    }
}
