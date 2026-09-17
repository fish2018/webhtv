package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.TmdbConfig;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.TmdbPerson;
import com.fongmi.android.tv.service.TmdbService;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.ui.adapter.TmdbPersonPhotoAdapter;
import com.fongmi.android.tv.ui.adapter.TmdbPersonWorkAdapter;
import com.fongmi.android.tv.ui.helper.TmdbNavigation;
import com.fongmi.android.tv.ui.helper.TmdbPersonWorkFilters;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.TmdbImageSaver;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * TMDB 人物详情全屏弹窗。
 */
public class TmdbPersonDialog {

    private final Activity activity;
    private final TmdbPerson person;
    private final Site site;
    private final TmdbService tmdbService;
    private final TmdbConfig tmdbConfig;

    private Dialog dialog;
    private TextView biography;
    private TextView stats;
    private LinearLayout biographySection;
    private LinearLayout photosSection;
    private LinearLayout filterSection;
    private RecyclerView photosRecycler;
    private RecyclerView worksRecycler;
    private NestedScrollView contentScroll;
    private ChipGroup departmentChips;
    private ChipGroup mediaChips;

    private TmdbPersonPhotoAdapter photoAdapter;
    private TmdbPersonWorkAdapter workAdapter;
    private boolean light;

    private TmdbPersonWorkFilters workFilters = TmdbPersonWorkFilters.from(null, null);
    private String currentDepartmentFilter = TmdbPersonWorkFilters.ALL;
    private String currentMediaFilter = TmdbPersonWorkFilters.ALL;
    private List<String> personPhotos = new ArrayList<>();

    // 懒加载相关
    private static final int PAGE_SIZE_INITIAL = 20;
    private static final int PAGE_SIZE_LOAD_MORE = 12;
    private boolean isLoadingMore = false;
    private boolean initialFocusDone = false;

    public static void show(Activity activity, TmdbPerson person) {
        show(activity, person, currentSite(activity));
    }

    public static void show(Activity activity, TmdbPerson person, Site site) {
        new TmdbPersonDialog(activity, person, site).show();
    }

    private static Site currentSite(Activity activity) {
        String key = activity == null || activity.getIntent() == null ? "" : activity.getIntent().getStringExtra("key");
        return VodConfig.get().getSite(key);
    }

    private TmdbPersonDialog(Activity activity, TmdbPerson person, Site site) {
        this.activity = activity;
        this.person = person;
        this.site = site;
        this.tmdbService = new TmdbService();
        this.tmdbConfig = TmdbConfig.objectFrom(Setting.getTmdbConfig());
        this.light = resolveLightTheme(activity);
    }

    private void show() {
        dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_tmdb_person, null);
        dialog.setContentView(view);

        applyWindowAttrs();
        initView(view);

        dialog.show();
        applyWindowAttrs();
        bringDialogToFront();
        requestInitialFocus();
        loadPersonDetail();
    }

    private void applyWindowAttrs() {
        if (dialog == null) return;
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            window.setGravity(Gravity.CENTER);
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setDimAmount(0f);
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) window.getDecorView().setDefaultFocusHighlightEnabled(false);
            window.getDecorView().setStateListAnimator(null);
        }
    }

    private void bringDialogToFront() {
        if (dialog == null || dialog.getWindow() == null) return;
        View decor = dialog.getWindow().getDecorView();
        decor.bringToFront();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) decor.setTranslationZ(1000f);
        decor.post(decor::bringToFront);
    }

    private void initView(View view) {
        ImageView profile = view.findViewById(R.id.profile);
        TextView name = view.findViewById(R.id.name);
        TextView role = view.findViewById(R.id.role);
        biography = view.findViewById(R.id.biography);
        stats = view.findViewById(R.id.stats);
        biographySection = view.findViewById(R.id.biographySection);
        photosSection = view.findViewById(R.id.photosSection);
        filterSection = view.findViewById(R.id.filterSection);
        photosRecycler = view.findViewById(R.id.photosRecycler);
        worksRecycler = view.findViewById(R.id.worksRecycler);
        contentScroll = view.findViewById(R.id.contentScroll);
        departmentChips = view.findViewById(R.id.departmentChips);
        mediaChips = view.findViewById(R.id.mediaChips);
        View closeBtn = view.findViewById(R.id.closeBtn);

        applyPanelSize(view);
        applyTheme(view);
        name.setText(person.getName());
        role.setText(person.getSubtitle());

        ImgUtil.load(person.getName(), person.getProfileUrl(), profile, true, 200, 300);

        // 设置照片列表（带点击事件）
        photoAdapter = new TmdbPersonPhotoAdapter(this::onPhotoClick);
        photosRecycler.setLayoutManager(new LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false));
        photosRecycler.setAdapter(photoAdapter);
        photosRecycler.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);

        // 设置作品列表（旧版纵向信息卡，懒加载）
        workAdapter = new TmdbPersonWorkAdapter(this::onWorkClick);
        workAdapter.setLight(light);
        LinearLayoutManager workLayoutManager = new LinearLayoutManager(activity);
        worksRecycler.setLayoutManager(workLayoutManager);
        worksRecycler.setAdapter(workAdapter);
        worksRecycler.setHasFixedSize(false);
        worksRecycler.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        worksRecycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0) return;
                int total = workLayoutManager.getItemCount();
                int visible = workLayoutManager.getChildCount();
                int first = workLayoutManager.findFirstVisibleItemPosition();
                if (first + visible >= total - 4) {
                    loadMoreWorks();
                }
            }
        });

        // 关闭按钮（TV版隐藏，遥控器无法获取焦点）
        if (Util.isLeanback()) {
            closeBtn.setVisibility(View.GONE);
        } else {
            closeBtn.setOnClickListener(v -> dialog.dismiss());
        }
    }

    private void applyPanelSize(View view) {
        View panel = view.findViewById(R.id.panel);
        if (panel == null) return;
        ViewGroup.LayoutParams params = panel.getLayoutParams();
        int width = activity.getResources().getDisplayMetrics().widthPixels;
        float ratio = width >= 1200 ? 0.78f : 0.94f;
        params.width = (int) (width * ratio);
        panel.setLayoutParams(params);
    }

    /**
     * 设置简介文本（含展开/收起）。
     */
    private void setBiography(String bio) {
        if (biography == null || bio == null || bio.isEmpty()) return;
        activity.runOnUiThread(() -> {
            if (dialog != null && dialog.isShowing()) {
                biography.setText(bio);
                biography.setOnClickListener(v -> {
                    int currentMaxLines = biography.getMaxLines();
                    biography.setMaxLines(currentMaxLines == 8 ? Integer.MAX_VALUE : 8);
                });
                biographySection.setVisibility(View.VISIBLE);
            }
        });
    }

    /**
     * 设置照片列表。
     */
    private void setPhotos(List<String> photos) {
        if (photos == null || photos.isEmpty()) return;
        personPhotos = photos;
        activity.runOnUiThread(() -> {
            if (dialog != null && dialog.isShowing()) {
                photoAdapter.setItems(photos);
                photosSection.setVisibility(View.VISIBLE);
                keepTopPositionDuringInitialRender();
            }
        });
    }

    /**
     * 设置作品列表和筛选器。
     */
    private void setWorks(List<TmdbItem> castWorks, List<TmdbItem> crewWorks) {
        if ((castWorks == null || castWorks.isEmpty()) && (crewWorks == null || crewWorks.isEmpty())) return;

        workFilters = TmdbPersonWorkFilters.from(castWorks, crewWorks);
        currentDepartmentFilter = TmdbPersonWorkFilters.ALL;
        currentMediaFilter = TmdbPersonWorkFilters.ALL;

        // 计算统计信息
        double totalRating = 0;
        int ratedCount = 0;
        for (TmdbItem item : workFilters.filter(TmdbPersonWorkFilters.ALL, TmdbPersonWorkFilters.ALL)) {
            if (item.getRating() > 0) {
                totalRating += item.getRating();
                ratedCount++;
            }
        }
        final double avgRating = ratedCount > 0 ? totalRating / ratedCount : 0;
        final int workCount = workFilters.allCount();

        activity.runOnUiThread(() -> {
            if (dialog == null || !dialog.isShowing()) return;

            // 显示统计
            if (workCount > 0) {
                String statsText = String.format(Locale.getDefault(),
                    "参与 %d 部作品" + (avgRating > 0 ? " · 平均 %.1f 分" : ""),
                    workCount, avgRating);
                stats.setText(statsText);
                stats.setVisibility(View.VISIBLE);
            }

            // 设置筛选器
            setupFilters();

            // 显示作品
            filterWorks();
        });
    }

    /**
     * 设置筛选标签。
     */
    private void setupFilters() {
        departmentChips.removeAllViews();
        mediaChips.removeAllViews();
        addFilterChips(departmentChips, workFilters.departmentOptions(), currentDepartmentFilter, key -> {
            currentDepartmentFilter = key;
            filterWorks();
        });
        addFilterChips(mediaChips, workFilters.mediaOptions(), currentMediaFilter, key -> {
            currentMediaFilter = key;
            filterWorks();
        });

        filterSection.setVisibility(View.VISIBLE);
    }

    private void addFilterChips(ChipGroup group, List<TmdbPersonWorkFilters.Option> options, String current, FilterCallback callback) {
        for (TmdbPersonWorkFilters.Option option : options) {
            Chip chip = new Chip(activity);
            chip.setId(View.generateViewId());
            chip.setText(String.format(Locale.getDefault(), "%s (%d)", option.label(), option.count()));
            chip.setCheckable(true);
            chip.setChecked(option.key().equals(current));
            chip.setOnClickListener(v -> {
                callback.onFilter(option.key());
                applyFilterChipStyles(departmentChips);
                applyFilterChipStyles(mediaChips);
            });
            chip.setOnCheckedChangeListener((button, checked) -> applyChipStyle(chip, checked, chip.hasFocus()));
            chip.setOnFocusChangeListener((view, focused) -> applyChipStyle(chip, chip.isChecked(), focused));
            applyChipStyle(chip, chip.isChecked(), false);
            if (Util.isLeanback()) {
                chip.setFocusable(true);
                chip.setChipStrokeWidth(2f);
            }
            group.addView(chip);
        }
    }

    private void applyFilterChipStyles(ChipGroup group) {
        if (group == null) return;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof Chip chip) applyChipStyle(chip, chip.isChecked(), chip.hasFocus());
        }
    }

    private void applyChipStyle(Chip chip, boolean checked, boolean focused) {
        chip.setCheckedIconVisible(false);
        int background = chipBackgroundColor(checked, focused);
        int text = chipTextColor(checked);
        int stroke = chipStrokeColor(checked, focused);
        chip.setChipBackgroundColor(ColorStateList.valueOf(background));
        chip.setTextColor(ColorStateList.valueOf(text));
        chip.setChipStrokeColor(ColorStateList.valueOf(stroke));
        chip.setRippleColor(ColorStateList.valueOf(light ? 0x1F12202D : 0x33FFFFFF));
        chip.setChipStrokeWidth(focused || checked ? 2f : 1f);
    }

    private int chipBackgroundColor(boolean checked, boolean focused) {
        if (light) return checked ? 0xFF12202D : focused ? 0xFFE7EDF3 : 0xFFFFFFFF;
        return checked ? 0xFFEAF2F8 : focused ? 0xFF2F4F6F : 0xFF1A2530;
    }

    private int chipTextColor(boolean checked) {
        if (light) return checked ? 0xFFFFFFFF : 0xCC12202D;
        return checked ? 0xFF101820 : 0xFFEAF2F8;
    }

    private int chipStrokeColor(boolean checked, boolean focused) {
        if (light) return checked ? 0xFF12202D : focused ? 0x66424B57 : 0x33424B57;
        return checked ? 0xFFEAF2F8 : focused ? 0x99FFFFFF : 0x4DFFFFFF;
    }

    private void applyTheme(View view) {
        MaterialCardView panel = view.findViewById(R.id.panel);
        MaterialCardView profileCard = view.findViewById(R.id.profileCard);
        MaterialCardView biographyCard = view.findViewById(R.id.biographyCard);
        ImageView profile = view.findViewById(R.id.profile);
        ImageView close = view.findViewById(R.id.closeBtn);
        int overlay = light ? 0x99F4F7FA : 0x8F000000;
        int panelColor = light ? 0xFFF4F7FA : 0xF2101821;
        int cardColor = light ? 0xFFFFFFFF : 0x261C2833;
        int imageBg = light ? 0xFFE7EDF3 : 0xFF25313D;
        int stroke = light ? 0x33424B57 : 0x33FFFFFF;
        int subtleStroke = light ? 0x26424B57 : 0x1FFFFFFF;
        int primary = light ? 0xFF12202D : 0xFFFFFFFF;
        int secondary = light ? 0xB312202D : 0xB3FFFFFF;
        int muted = light ? 0x9912202D : 0x99FFFFFF;
        int body = light ? 0xDD12202D : 0xDDEAF2F8;

        view.setBackgroundColor(overlay);
        panel.setCardBackgroundColor(panelColor);
        panel.setStrokeColor(stroke);
        profileCard.setCardBackgroundColor(imageBg);
        profileCard.setStrokeColor(subtleStroke);
        biographyCard.setCardBackgroundColor(cardColor);
        biographyCard.setStrokeColor(subtleStroke);
        profile.setBackgroundColor(imageBg);
        close.setColorFilter(primary);
        tint(view.findViewById(R.id.castTitle), secondary);
        tint(view.findViewById(R.id.name), primary);
        tint(view.findViewById(R.id.role), secondary);
        tint(view.findViewById(R.id.stats), muted);
        tint(view.findViewById(R.id.biographyTitle), primary);
        tint(view.findViewById(R.id.biography), body);
        tint(view.findViewById(R.id.workTitle), primary);
        tint(view.findViewById(R.id.photosTitle), primary);
    }

    private void tint(TextView view, int color) {
        if (view != null) view.setTextColor(color);
    }

    private static boolean resolveLightTheme(Activity activity) {
        int night = activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return Setting.resolveTmdbDetailLightTheme(Setting.getTmdbDetailTheme(), night == Configuration.UI_MODE_NIGHT_YES);
    }

    /**
     * 根据筛选条件显示作品（分批懒加载）。
     */
    private void filterWorks() {
        List<TmdbItem> items = workFilters.filter(currentDepartmentFilter, currentMediaFilter);
        isLoadingMore = false;
        int initialCount = Math.min(PAGE_SIZE_INITIAL, items.size());
        workAdapter.setItems(items.subList(0, initialCount));
        worksRecycler.scrollToPosition(0);
        worksRecycler.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        keepTopPositionDuringInitialRender();
    }

    private void requestInitialFocus() {
        if (!Util.isLeanback() || initialFocusDone || dialog == null || !dialog.isShowing()) return;
        View panel = dialog.findViewById(R.id.panel);
        View root = dialog.findViewById(R.id.root);
        View target = panel != null ? panel : root;
        if (target == null) return;
        target.setFocusable(true);
        target.setFocusableInTouchMode(true);
        if (target.requestFocus()) initialFocusDone = true;
        keepTopPositionDuringInitialRender();
    }

    private void keepTopPositionDuringInitialRender() {
        if (!Util.isLeanback() || contentScroll == null || dialog == null || !dialog.isShowing()) return;
        bringDialogToFront();
        contentScroll.scrollTo(0, 0);
        contentScroll.post(() -> contentScroll.scrollTo(0, 0));
    }

    /**
     * 加载更多作品（懒加载）。
     */
    private void loadMoreWorks() {
        if (isLoadingMore) return;
        List<TmdbItem> items = workFilters.filter(currentDepartmentFilter, currentMediaFilter);
        int loaded = workAdapter.getLoadedCount();
        if (loaded >= items.size()) return;
        isLoadingMore = true;
        int to = Math.min(loaded + PAGE_SIZE_LOAD_MORE, items.size());
        workAdapter.addItems(new ArrayList<>(items.subList(loaded, to)));
        isLoadingMore = false;
    }

    /**
     * 异步加载人物详情，补充简介、照片和作品列表。
     */
    private void loadPersonDetail() {
        int personId = person.getPersonId();
        if (personId <= 0 || !tmdbConfig.isReady()) return;

        Task.execute(() -> {
            try {
                JsonObject detail = tmdbService.person(personId, tmdbConfig);

                // 简介
                TmdbPerson full = tmdbService.personProfile(detail, tmdbConfig);
                String bio = full.getBiography();
                if (bio != null && !bio.isEmpty()) {
                    setBiography(bio);
                }

                // 照片
                List<String> photos = tmdbService.personPhotos(detail, tmdbConfig);
                if (photos != null && !photos.isEmpty()) {
                    setPhotos(photos);
                }

                // 作品
                List<TmdbItem> castWorks = tmdbService.personCastWorks(detail, tmdbConfig);
                List<TmdbItem> crewWorks = tmdbService.personCrewWorks(detail, tmdbConfig);
                setWorks(castWorks, crewWorks);

            } catch (Exception e) {
                android.util.Log.w("TmdbPersonDialog", "加载人物详情失败: " + e.getMessage());
            }
        });
    }

    /**
     * 照片点击 - 全屏查看 + 保存 + 左右滑动。
     */
    private void onPhotoClick(int position, String url) {
        if (personPhotos.isEmpty()) return;
        PhotoViewerDialog.show(activity, personPhotos, position, this::savePhoto);
    }

    /**
     * 作品点击 - 优先本站搜索，搜不到再全局搜索。
     */
    private void onWorkClick(TmdbItem item) {
        if (item == null || TextUtils.isEmpty(item.getTitle())) return;
        TmdbNavigation.open(activity, item, site, (targetSite, match) -> VideoActivity.start(activity, targetSite.getKey(), match.getId(), match.getName(), match.getPic()), this::dismissDelayed);
    }

    /**
     * 延迟关闭对话框，让新页面先渲染。
     */
    private void dismissDelayed() {
        if (dialog != null && dialog.isShowing()) {
            dialog.getWindow().getDecorView().postDelayed(() -> {
                if (dialog != null && dialog.isShowing()) {
                    dialog.dismiss();
                }
            }, 300);  // 300ms 延迟
        }
    }

    /**
     * 保存照片。
     */
    private void savePhoto(String url) {
        if (TextUtils.isEmpty(url)) return;
        if (!(activity instanceof androidx.fragment.app.FragmentActivity)) {
            Notify.show(R.string.detail_image_save_failed);
            return;
        }
        Notify.show(R.string.detail_image_saving);
        TmdbImageSaver.save((androidx.fragment.app.FragmentActivity) activity, highResTmdbImage(url), new TmdbImageSaver.Callback() {
            @Override
            public void success(String name) {
                Notify.show(activity.getString(R.string.detail_image_save_success, name));
            }

            @Override
            public void error(String message) {
                String prefix = activity.getString(R.string.detail_image_save_failed);
                Notify.show(TextUtils.isEmpty(message) || prefix.equals(message) ? prefix : prefix + "\n" + message);
            }
        });
    }

    /**
     * 转换为高清图片 URL（original 尺寸）。
     * 兼容 TMDB 官方域名和自定义代理。
     */
    private String highResTmdbImage(String url) {
        if (TextUtils.isEmpty(url)) return url;
        // 匹配 /t/p/wXXX/ 或 /t/p/hXXX/ 模式，替换为 /t/p/original/
        String result = url.replaceFirst("(/t/p/)(w\\d+|h\\d+)(/)", "$1original$3");
        if (!result.equals(url)) return result;
        // 兜底：匹配通用 /wXXX/ 模式
        return url.replaceFirst("/w\\d+/", "/original/");
    }

    private interface FilterCallback {
        void onFilter(String key);
    }
}
