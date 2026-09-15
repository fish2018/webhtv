package com.fongmi.android.tv.ui.activity;

import android.annotation.SuppressLint;
import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.webkit.WebView;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.lifecycle.ViewModelProvider;
import androidx.leanback.widget.HorizontalGridView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.bean.Cache;
import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Func;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Style;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivityHomeBinding;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.CastEvent;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.event.ServerEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.service.DLNARendererService;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.adapter.BaseDiffCallback;
import com.fongmi.android.tv.ui.adapter.TypeAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.activity.VodActivity;
import com.fongmi.android.tv.ui.custom.CustomRowPresenter;
import com.fongmi.android.tv.ui.custom.CustomSelector;
import com.fongmi.android.tv.ui.custom.CustomTitleView;
import com.fongmi.android.tv.ui.dialog.HomeMenuDialog;
import com.fongmi.android.tv.ui.dialog.SiteDialog;
import com.fongmi.android.tv.ui.presenter.FuncPresenter;
import com.fongmi.android.tv.ui.presenter.HeaderPresenter;
import com.fongmi.android.tv.ui.presenter.ProgressPresenter;
import com.fongmi.android.tv.ui.presenter.VodPresenter;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.android.tv.utils.Util;
import com.fongmi.android.tv.web.HomeWebController;
import com.github.catvod.utils.Prefers;
import com.fongmi.android.tv.web.WebHomeViewport;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Json;
import com.google.common.collect.Lists;
import com.google.gson.JsonObject;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class HomeActivity extends BaseActivity implements CustomTitleView.Listener, VodPresenter.OnClickListener, FuncPresenter.OnClickListener, FuncPresenter.OnBoundaryListener, TypeAdapter.OnClickListener, HomeWebController.Listener, HomeMenuDialog.Listener {

    private static final String TV_NORMAL = "tv-normal";
    private static final String TV_TOOLBAR_HIDDEN = "tv-toolbar-hidden";
    private static final String TV_OVERLAY = "tv-overlay";
    private static final String TV_FULL = "tv-full";

    private ActivityHomeBinding mBinding;
    private ArrayObjectAdapter mFuncAdapter;
    private ArrayObjectAdapter mAdapter;
    private SiteViewModel mViewModel;
    private TypeAdapter mTypeAdapter;
    private HomeWebController mWeb;
    private WebView mHomeWeb;
    private Result mResult;
    private Result mHomeResult;
    private Clock mClock;
    private String webChromeMode = TV_NORMAL;
    private String webDefaultChromeMode = TV_FULL;
    private boolean webToolbarVisible = true;
    private boolean loadingHomeCategory;
    private boolean mStartupActionDone;
    private int mMenuButtonCount;

    public static List<Vod> sRecommendCache = new ArrayList<>();
    public static Vod sPendingVideo = null;

    private Site getHome() {
        return VodConfig.get().getHome();
    }

    private Config getConfig() {
        return VodConfig.get().getConfig();
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityHomeBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        checkAction(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_App);
        super.onCreate(savedInstanceState);
        if (Prefers.getBoolean("skip_startup", false)) {
            Prefers.remove("skip_startup");
            mStartupActionDone = true;
        }
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        SpiderDebug.log("startup", "home initView start cost=%sms", System.currentTimeMillis() - App.time());
        mResult = Result.empty();
        mHomeResult = Result.empty();
        mClock = Clock.create(mBinding.clock);
        mBinding.progressLayout.showProgress();
        setRecyclerView();
        setViewModel();
        setAdapter();
        runAfterFirstFrame(this::initAfterFirstFrame);
        SpiderDebug.log("startup", "home initView end cost=%sms", System.currentTimeMillis() - App.time());
    }

    private void initAfterFirstFrame() {
        SpiderDebug.log("startup", "home first frame cost=%sms", System.currentTimeMillis() - App.time());
        App.post(this::initConfig, 80);
        App.post(() -> PermissionUtil.requestFile(this, allGranted -> {
            PermissionUtil.requestNotify(this);
            if (allGranted) initConfig();
        }), 1800);
        App.post(() -> DLNARendererService.start(this), 2500);
    }

    private void runAfterFirstFrame(Runnable runnable) {
        View root = mBinding.getRoot();
        root.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (root.getViewTreeObserver().isAlive()) root.getViewTreeObserver().removeOnPreDrawListener(this);
                root.post(runnable);
                return true;
            }
        });
    }

    @Override
    protected void initEvent() {
        mBinding.title.setListener(this);
        mBinding.toolbar.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            syncNativeContentInset();
            syncWebOverlayLayout();
        });
    }

    private void updateToolbarVisibility(boolean visible) {
        mBinding.toolbar.setVisibility(visible && webToolbarVisible ? View.VISIBLE : View.GONE);
        syncNativeContentInset();
        syncWebOverlayLayout();
    }

    private void syncNativeContentInset() {
        int top = isToolbarVisible() ? toolbarHeight() : 0;
        if (mBinding.nativeContent.getPaddingTop() != top) {
            mBinding.nativeContent.setPadding(mBinding.nativeContent.getPaddingLeft(), top, mBinding.nativeContent.getPaddingRight(), mBinding.nativeContent.getPaddingBottom());
        }
        mBinding.progressLayout.setProgressTopOffset(top * 3);
    }

    private void syncWebOverlayLayout() {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) mBinding.webOverlay.getLayoutParams();
        int top = constrainWebBelowToolbar() ? toolbarHeight() : 0;
        if (params.topMargin == top) return;
        params.topMargin = top;
        mBinding.webOverlay.setLayoutParams(params);
    }

    private boolean constrainWebBelowToolbar() {
        return (TV_NORMAL.equals(webChromeMode) || TV_OVERLAY.equals(webChromeMode)) && isToolbarVisible();
    }

    private boolean isToolbarVisible() {
        return mBinding.toolbar.getVisibility() == View.VISIBLE;
    }

    private int toolbarHeight() {
        int height = mBinding.toolbar.getHeight();
        if (height <= 0) height = mBinding.toolbar.getMeasuredHeight();
        return height > 0 ? height : ResUtil.dp2px(80);
    }

    private void checkAction(Intent intent) {
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            VideoActivity.push(this, intent.getStringExtra(Intent.EXTRA_TEXT));
        } else if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            PermissionUtil.requestFile(this, allGranted -> checkType(intent));
        } else if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String keyword = intent.getStringExtra(SearchManager.QUERY);
            if (!TextUtils.isEmpty(keyword)) SearchActivity.start(this, keyword);
        }
    }

    private void checkType(Intent intent) {
        if ("text/plain".equals(intent.getType()) || UrlUtil.path(intent.getData()).endsWith(".m3u")) {
            loadLive("file:/" + FileChooser.getPathFromUri(intent.getData()));
        } else {
            VideoActivity.push(this, intent.getData().toString());
        }
    }

    @SuppressLint("RestrictedApi")
    private void setRecyclerView() {
        CustomSelector selector = new CustomSelector();
        selector.addPresenter(Integer.class, new HeaderPresenter());
        selector.addPresenter(String.class, new ProgressPresenter());
        selector.addPresenter(Vod.class, new VodPresenter(this, Style.list()));
        selector.addPresenter(ListRow.class, new CustomRowPresenter(16), VodPresenter.class);
        mBinding.recycler.setAdapter(new ItemBridgeAdapter(mAdapter = new ArrayObjectAdapter(selector)));
        mBinding.recycler.setVerticalSpacing(ResUtil.dp2px(16));
        mBinding.typeRecycler.setHorizontalSpacing(ResUtil.dp2px(16));
        mBinding.typeRecycler.setRowHeight(android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.typeRecycler.setAdapter(mTypeAdapter = new TypeAdapter(this));
        FuncPresenter funcPresenter = new FuncPresenter(this);
        funcPresenter.setOnBoundaryListener(this);
        mBinding.funcRecycler.setAdapter(new ItemBridgeAdapter(mFuncAdapter = new ArrayObjectAdapter(funcPresenter)));
        mBinding.funcRecycler.setHorizontalSpacing(ResUtil.dp2px(4));
        mBinding.funcRecycler.setRowHeight(ResUtil.dp2px(40));
    }

    private void setWebView() {
        SpiderDebug.log("startup", "webview create start cost=%sms", System.currentTimeMillis() - App.time());
        mWeb = new HomeWebController(this, getHomeWeb(), this);
        mWeb.setViewport(tvViewport(webChromeMode));
        SpiderDebug.log("startup", "webview create end cost=%sms", System.currentTimeMillis() - App.time());
    }

    private void ensureWebView() {
        if (mWeb == null) setWebView();
    }

    private WebView getHomeWeb() {
        if (mHomeWeb != null) return mHomeWeb;
        mHomeWeb = new WebView(this);
        mHomeWeb.setFocusable(true);
        mHomeWeb.setFocusableInTouchMode(true);
        mHomeWeb.setVisibility(View.GONE);
        mBinding.webOverlay.addView(mHomeWeb, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        return mHomeWeb;
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getResult().observe(this, result -> {
            boolean categoryResult = isHomeCategoryResult(result);
            mAdapter.remove("progress");
            if (!categoryResult) {
                Cache.clear().put(result);
                setTypes(mHomeResult = result);
            }
            mResult = result;
            addVideo(result);
        });
    }

    private boolean isHomeCategoryResult(Result result) {
        return loadingHomeCategory && result.getTypes().isEmpty();
    }

    private void setAdapter() {
    }

    private void setTitle() {
        List<String> items = Arrays.asList(getHome().getName(), getConfig().getName(), getString(R.string.app_name));
        Optional<String> optional = items.stream().filter(s -> !TextUtils.isEmpty(s)).findFirst();
        optional.ifPresent(s -> mBinding.title.setText(s));
    }

    private void initConfig() {
        SpiderDebug.log("startup", "config load start cost=%sms", System.currentTimeMillis() - App.time());
        VodConfig.get().init().load(getCallback());
        LiveConfig.get().init().load();
        WallConfig.get().init();
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void success() {
                SpiderDebug.log("startup", "config load success cost=%sms", System.currentTimeMillis() - App.time());
                showContent();
            }

            @Override
            public void error(String msg) {
                SpiderDebug.log("startup", "config load error cost=%sms msg=%s", System.currentTimeMillis() - App.time(), msg);
                Notify.show(msg);
                showContent();
            }
        };
    }

    private void showContent() {
        SpiderDebug.log("startup", "home showContent start cost=%sms", System.currentTimeMillis() - App.time());
        mBinding.progressLayout.showContent();
        checkAction(getIntent());
        setTitle();
        setLogo();
        setFunc();
        getVideo();
        setFocus();
        App.post(this::prewarmWebView, 1500);
        SpiderDebug.log("startup", "home showContent end cost=%sms", System.currentTimeMillis() - App.time());
        runStartupAction();
    }

    private void runStartupAction() {
        if (mStartupActionDone) return;
        if (shouldSkipStartup()) return;
        mStartupActionDone = true;
        switch (Setting.getDefaultLaunch()) {
            case Setting.DEFAULT_LAUNCH_LIVE:
                LiveActivity.start(this);
                break;
            case Setting.DEFAULT_LAUNCH_RECENT:
                List<History> history = History.get();
                if (!history.isEmpty()) VideoActivity.startFullscreen(this, history.get(0));
                break;
            default:
                break;
        }
    }

    private void prewarmWebView() {
        if (isFinishing() || mWeb != null) return;
        boolean hasWebHome = VodConfig.get().getSites().stream().anyMatch(Site::hasHomePage);
        if (!hasWebHome) return;
        SpiderDebug.log("startup", "webview prewarm start cost=%sms", System.currentTimeMillis() - App.time());
        ensureWebView();
        SpiderDebug.log("startup", "webview prewarm end cost=%sms", System.currentTimeMillis() - App.time());
    }

    private void loadLive(String url) {
        LiveConfig.load(Config.find(url, 1), new Callback() {
            @Override
            public void success() {
                LiveActivity.start(getActivity());
            }
        });
    }

    private void setFocus() {
        mBinding.title.setSelected(true);
        mBinding.title.setFocusable(true);
        mBinding.title.requestFocus();
    }

    private void getVideo() {
        getVideo(false);
    }

    private void getVideo(boolean forceNative) {
        if (!forceNative && getHome().hasHomePage()) {
            ensureWebView();
        }
        if (!forceNative && mWeb != null && mWeb.load(getHome())) {
            mBinding.typeRecycler.setVisibility(View.GONE);
            mBinding.recycler.setVisibility(View.GONE);
            mBinding.progressLayout.showContent();
            showWebOverlay();
            return;
        }
        if (mWeb != null) mWeb.hide();
        hideWebOverlay();
        applyTvChrome(TV_NORMAL);
        mBinding.recycler.setVisibility(View.VISIBLE);
        mResult = Result.empty();
        mHomeResult = Result.empty();
        loadingHomeCategory = false;
        clearRecommendRows();
        mAdapter.add("progress");
        mViewModel.homeContent();
    }

    private void showWebOverlay() {
        mBinding.webOverlay.setVisibility(View.VISIBLE);
        syncWebOverlayLayout();
    }

    private void hideWebOverlay() {
        mBinding.webOverlay.setVisibility(View.GONE);
    }

    private void setTypes(Result result) {
        if (result.getTypes().isEmpty()) {
            mTypeAdapter.addAll(java.util.Collections.emptyList());
            mBinding.typeRecycler.setVisibility(View.GONE);
            return;
        }
        List<Class> types = new ArrayList<>();
        if (!result.getList().isEmpty()) {
            Class recommend = new Class();
            recommend.setTypeName(ResUtil.getString(R.string.home_recommend));
            types.add(recommend);
        }
        types.addAll(result.getTypes());
        mTypeAdapter.addAll(types);
        mBinding.typeRecycler.setVisibility(View.VISIBLE);
    }

    private void addVideo(Result result) {
        if (!loadingHomeCategory && result.getList().isEmpty() && !result.getTypes().isEmpty()) {
            Class type = result.getTypes().get(0);
            SpiderDebug.log("home", "home list empty, auto open first category key=%s tid=%s", getHome().getKey(), type.getTypeId());
            loadingHomeCategory = true;
            mAdapter.add("progress");
            mViewModel.categoryContent(getHome().getKey(), type.getTypeId(), "1", true, new java.util.HashMap<>());
            return;
        }
        loadingHomeCategory = false;
        Style style = result.getStyle(getHome().getStyle());
        if (style.isList()) mAdapter.addAll(mAdapter.size(), result.getList());
        else addGrid(result.getList(), style);
    }

    private void clearRecommendRows() {
        mAdapter.remove("progress");
        int index = getRecommendIndex();
        if (mAdapter.size() > index) mAdapter.removeItems(index, mAdapter.size() - index);
    }

    private void addGrid(List<Vod> items, Style style) {
        List<ListRow> rows = new ArrayList<>();
        VodPresenter presenter = new VodPresenter(this, style);
        for (List<Vod> part : Lists.partition(items, Product.getColumn(style))) {
            ArrayObjectAdapter adapter = new ArrayObjectAdapter(presenter);
            adapter.addAll(0, part);
            rows.add(new ListRow(adapter));
        }
        mAdapter.addAll(mAdapter.size(), rows);
    }

    private void setFunc() {
        mFuncAdapter.setItems(getFuncItems(), new BaseDiffCallback<Func>());
        int count = mFuncAdapter.size();
        if (count > 0) {
            android.view.ViewGroup.LayoutParams params = mBinding.funcRecycler.getLayoutParams();
            params.width = ResUtil.dp2px(64 * count + 4 * (count - 1) + 68);
            mBinding.funcRecycler.setLayoutParams(params);
        }
    }

    private List<Func> getFuncItems() {
        List<Func> items = new ArrayList<>();
        if (LiveConfig.hasLoadedLives()) items.add(Func.create(R.string.home_live));
        items.add(Func.create(R.string.home_search));
        items.add(Func.create(R.string.home_history));
        items.add(Func.create(R.string.home_keep));
        items.add(Func.create(R.string.home_push));
        items.add(Func.create(R.string.home_setting));
        return items;
    }

    private int getRecommendIndex() {
        return mAdapter.indexOf(R.string.home_recommend) + 1;
    }

    private void setLogo() {
        ImgUtil.logo(mBinding.logo);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        switch (event.type()) {
            case VOD:
                RefreshEvent.history();
                RefreshEvent.home();
                setLogo();
                break;
            case COMMON:
                setFunc();
                break;
            case BOOT:
                LiveActivity.start(this);
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        switch (event.getType()) {
            case HOME:
                setTitle();
                SpiderDebug.log("site-dialog", "home refresh start key=%s homePage=%s", getHome().getKey(), getHome().hasHomePage());
                if (mWeb != null && mWeb.isVisible()) {
                    if (!mWeb.load(getHome(), true)) getVideo(true);
                } else {
                    getVideo();
                }
                SpiderDebug.log("site-dialog", "home refresh end key=%s", getHome().getKey());
                break;
            case SIZE:
                getVideo();
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServerEvent(ServerEvent event) {
        switch (event.type()) {
            case SEARCH:
                SearchActivity.start(this, event.text());
                break;
            case PUSH:
                VideoActivity.push(this, event.text());
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCastEvent(CastEvent event) {
        if (App.activity() instanceof VideoActivity video) {
            video.finishVideoForCast();
            App.post(() -> dispatchCast(event), 300);
        } else {
            dispatchCast(event);
        }
    }

    private void dispatchCast(CastEvent event) {
        if (VodConfig.get().getConfig().equals(event.config())) {
            VideoActivity.cast(this, event.history());
        } else {
            VodConfig.load(event.config(), getCallback(event));
        }
    }

    private Callback getCallback(CastEvent event) {
        return new Callback() {
            @Override
            public void success() {
                onCastEvent(event);
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
            }
        };
    }

    @Override
    public void onItemClick(Func item) {
        if (item.getResId() == R.string.home_live) LiveActivity.start(this);
        else if (item.getResId() == R.string.home_keep) KeepActivity.start(this);
        else if (item.getResId() == R.string.home_history) HistoryActivity.start(this);
        else if (item.getResId() == R.string.home_push) PushActivity.start(this);
        else if (item.getResId() == R.string.home_search) SearchActivity.start(this);
        else if (item.getResId() == R.string.home_setting) SettingActivity.start(this);
    }

    @Override
    public boolean onLongClick(Func item) {
        if (item.getResId() != R.string.home_search) return false;
        SearchActivity.start(this, "", getHome().getKey());
        return true;
    }

    @Override
    public void onItemClick(Class item) {
        Result result = mHomeResult == null || mHomeResult.getTypes().isEmpty() ? mResult : mHomeResult;
        boolean hasRecommend = mHomeResult != null && !mHomeResult.getList().isEmpty();
        int typeIndex = mTypeAdapter.indexOf(item);
        int realIndex = hasRecommend ? typeIndex - 1 : typeIndex;
        if (hasRecommend) {
            sRecommendCache = new ArrayList<>(mHomeResult.getList());
            List<Class> types = new ArrayList<>();
            Class recommend = new Class();
            recommend.setTypeId("home");
            recommend.setTypeName(ResUtil.getString(R.string.home_recommend));
            types.add(recommend);
            types.addAll(result.getTypes());
            Result merged = new Result();
            merged.setTypes(types);
            VodActivity.start(this, getHome().getKey(), merged, hasRecommend && item.getTypeName().equals(ResUtil.getString(R.string.home_recommend)) ? 0 : realIndex + 1);
        } else {
            VodActivity.start(this, getHome().getKey(), result, realIndex);
        }
    }

    @Override
    public void onRefresh(Class item) {
        onItemClick(item);
    }

    @Override
    public void onItemClick(Vod item) {
        if (item.isAction()) {
            mViewModel.action(getHome().getKey(), item.getAction());
            return;
        }
        boolean hasRecommend = mHomeResult != null && !mHomeResult.getList().isEmpty();
        if (hasRecommend) {
            sRecommendCache = new ArrayList<>(mHomeResult.getList());
            sPendingVideo = item;
            List<Class> types = new ArrayList<>();
            Class recommend = new Class();
            recommend.setTypeId("home");
            recommend.setTypeName(ResUtil.getString(R.string.home_recommend));
            types.add(recommend);
            types.addAll(mHomeResult.getTypes());
            Result merged = new Result();
            merged.setTypes(types);
            VodActivity.start(this, getHome().getKey(), merged, 0);
        } else {
            if (item.isFolder()) {
                Class type = new Class();
                type.setTypeId(item.getId());
                type.setTypeName(item.getName());
                Result result = new Result();
                result.setTypes(java.util.Collections.singletonList(type));
                VodActivity.start(this, getHome().getKey(), result);
            } else if (getHome().isIndex()) {
                CollectActivity.start(this, item.getName());
            } else {
                VideoActivity.start(this, getHome().getKey(), item.getId(), item.getName(), item.getPic());
            }
        }
    }

    @Override
    public boolean onLongClick(Vod item) {
        if (item.isAction()) return false;
        CollectActivity.start(this, item.getName());
        return true;
    }

    @Override
    public void showDialog() {
        long start = System.currentTimeMillis();
        SpiderDebug.log("site-dialog", "open requested cost=%sms", System.currentTimeMillis() - App.time());
        SiteDialog.create().setListener(this).show(this);
        SpiderDebug.log("site-dialog", "show returned delay=%sms", System.currentTimeMillis() - start);
    }

    private void showHomeMenu() {
        List<Func> funcs = getFuncItems();
        boolean hasRecommend = mHomeResult != null && !mHomeResult.getList().isEmpty();
        List<Class> types = mHomeResult == null ? new ArrayList<>() : mHomeResult.getTypes();
        mMenuButtonCount = 1 + funcs.size();
        int categoryCount = (hasRecommend ? 1 : 0) + types.size();
        String[] items = new String[mMenuButtonCount + 1 + categoryCount];
        items[0] = ResUtil.getString(R.string.home_switch);
        for (int i = 0; i < funcs.size(); i++) items[1 + i] = funcs.get(i).getText();
        items[mMenuButtonCount] = ResUtil.getString(R.string.home_refresh);
        int offset = mMenuButtonCount + 1;
        if (hasRecommend) items[offset++] = ResUtil.getString(R.string.home_recommend);
        for (int i = 0; i < types.size(); i++) items[offset + i] = types.get(i).getTypeName();
        HomeMenuDialog.create().items(items).show(this);
    }

    @Override
    public void onHomeMenu(int which) {
        if (which == 0) {
            showDialog();
            return;
        }
        if (which < mMenuButtonCount) {
            onItemClick(getFuncItems().get(which - 1));
            return;
        }
        if (which == mMenuButtonCount) {
            initConfig();
            return;
        }
        int categoryIndex = which - mMenuButtonCount - 1;
        boolean hasRecommend = mHomeResult != null && !mHomeResult.getList().isEmpty();
        if (hasRecommend && categoryIndex == 0) {
            getVideo();
            return;
        }
        List<Class> types = mHomeResult == null ? new ArrayList<>() : mHomeResult.getTypes();
        int realIndex = hasRecommend ? categoryIndex - 1 : categoryIndex;
        if (realIndex < 0 || realIndex >= types.size()) return;
        Result result = mHomeResult == null || mHomeResult.getTypes().isEmpty() ? mResult : mHomeResult;
        VodActivity.start(this, getHome().getKey(), result, realIndex);
    }

    @Override
    public void onTitleLeft() {
        focusLastFunc();
    }

    @Override
    public void onTitleRight() {
        focusFirstFunc();
    }

    @Override
    public boolean onLeftBoundary() {
        mBinding.title.setFocusable(true);
        mBinding.title.post(() -> mBinding.title.requestFocus());
        return true;
    }

    @Override
    public boolean onRightBoundary() {
        mBinding.title.setFocusable(true);
        mBinding.title.post(() -> mBinding.title.requestFocus());
        return true;
    }

    private void focusFirstFunc() {
        mBinding.funcRecycler.post(() -> {
            RecyclerView.ViewHolder holder = mBinding.funcRecycler.findViewHolderForAdapterPosition(0);
            if (holder != null) holder.itemView.requestFocus();
        });
    }

    private void focusLastFunc() {
        int count = mFuncAdapter.size();
        if (count <= 0) return;
        mBinding.funcRecycler.post(() -> {
            RecyclerView.ViewHolder holder = mBinding.funcRecycler.findViewHolderForAdapterPosition(count - 1);
            if (holder != null) holder.itemView.requestFocus();
        });
    }

    @Override
    public void onRefresh() {
        initConfig();
    }

    @Override
    public void reloadConfig() {
        onRefresh();
    }

    @Override
    public void setSite(Site item) {
        SpiderDebug.log("site-dialog", "set site key=%s name=%s homePage=%s", item.getKey(), item.getName(), item.hasHomePage());
        VodConfig.get().setHome(item);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (KeyUtil.isMenuKey(event)) {
            showHomeMenu();
            return true;
        }
        // 视频列表/筛选左右环形跳转（放在最前面，确保在VerticalGridView处理之前消费）
        if (KeyUtil.isActionDown(event) && (KeyUtil.isLeftKey(event) || KeyUtil.isRightKey(event))) {
            View focus = getCurrentFocus();
            if (focus != null && mBinding.recycler.getVisibility() == View.VISIBLE) {
                HorizontalGridView grid = findHorizontalGridView(focus);
                if (grid != null && grid.getAdapter() != null) {
                    int position = grid.getSelectedPosition();
                    int count = grid.getAdapter().getItemCount();
                    if (count > 1 && position >= 0) {
                        if (KeyUtil.isLeftKey(event) && position == 0) {
                            grid.setSelectedPosition(count - 1);
                            return true;
                        }
                        if (KeyUtil.isRightKey(event) && position == count - 1) {
                            grid.setSelectedPosition(0);
                            return true;
                        }
                    }
                }
            }
        }
        if (mWeb != null && mWeb.isVisible()) {
            if (KeyUtil.isBackKey(event)) {
                if (KeyUtil.isActionUp(event)) onBackInvoked();
                return true;
            }
            if (mBinding.toolbar.hasFocus()) {
                if (KeyUtil.isActionDown(event) && KeyUtil.isDownKey(event)) return requestWebFocus();
                return super.dispatchKeyEvent(event);
            }
            if (KeyUtil.isUpKey(event) && isToolbarVisible()) return super.dispatchKeyEvent(event);
            if (mWeb.dispatchKeyEvent(event)) return true;
            return super.dispatchKeyEvent(event);
        }
        if (KeyUtil.isActionDown(event) & KeyUtil.isLeftKey(event) && mBinding.typeRecycler.hasFocus()) {
            if (mBinding.typeRecycler.getSelectedPosition() == 0 && mTypeAdapter.getItemCount() > 1) {
                mBinding.typeRecycler.setSelectedPosition(mTypeAdapter.getItemCount() - 1);
                return true;
            }
        }
        if (KeyUtil.isActionDown(event) & KeyUtil.isRightKey(event) && mBinding.typeRecycler.hasFocus()) {
            if (mBinding.typeRecycler.getSelectedPosition() == mTypeAdapter.getItemCount() - 1 && mTypeAdapter.getItemCount() > 1) {
                mBinding.typeRecycler.setSelectedPosition(0);
                return true;
            }
        }
        if (KeyUtil.isActionDown(event) & KeyUtil.isUpKey(event) && mBinding.typeRecycler.hasFocus()) return requestTitleFocus();
        if (KeyUtil.isActionDown(event) & KeyUtil.isDownKey(event) && mBinding.typeRecycler.hasFocus()) {
            if (mBinding.recycler.getVisibility() == View.VISIBLE && mBinding.recycler.getChildCount() > 0) return requestContentFocus();
            return true;
        }
        if (KeyUtil.isActionDown(event) & KeyUtil.isDownKey(event) && (getCurrentFocus() == mBinding.title || mBinding.funcRecycler.hasFocus())) {
            if (hasHomeContent()) {
                return requestHomeFocus();
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private HorizontalGridView findHorizontalGridView(View view) {
        View parent = view;
        while (parent != null) {
            if (parent instanceof HorizontalGridView) return (HorizontalGridView) parent;
            if (!(parent.getParent() instanceof View)) break;
            parent = (View) parent.getParent();
        }
        return null;
    }

    private boolean hasHomeContent() {
        return mBinding.typeRecycler.getVisibility() == View.VISIBLE || mBinding.recycler.getChildCount() > 0;
    }

    private boolean requestTitleFocus() {
        updateToolbarVisibility(true);
        mBinding.title.setFocusable(true);
        return mBinding.title.requestFocus();
    }

    private boolean requestHomeFocus() {
        if (mBinding.typeRecycler.getVisibility() == View.VISIBLE) return mBinding.typeRecycler.requestFocus();
        return requestContentFocus();
    }

    private boolean requestWebFocus() {
        return mWeb != null && mWeb.isVisible() && mWeb.requestFocus("toolbar-down");
    }

    private boolean requestContentFocus() {
        if (mBinding.recycler.getVisibility() != View.VISIBLE || mBinding.recycler.getChildCount() == 0) return false;
        View child = mBinding.recycler.getFocusedChild();
        if (child == null) child = mBinding.recycler.getChildAt(0);
        return child != null && child.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mClock.start();
        if (mWeb != null) mWeb.onResume();
    }

    @Override
    protected void onPause() {
        if (mWeb != null) mWeb.onPause();
        super.onPause();
        mClock.stop();
    }

    @Override
    protected void onBackInvoked() {
        if (mWeb != null && mWeb.isVisible() && mWeb.handleBack()) {
            return;
        } else if (mWeb != null && mWeb.isVisible() && consumeTvFullscreenBack()) {
            return;
        } else if (mWeb != null && mWeb.isVisible()) {
            exitHome();
            return;
        } else if (mBinding.progressLayout.isProgress()) {
            showContent();
        } else if (mBinding.recycler.hasFocus()) {
            mBinding.typeRecycler.requestFocus();
        } else {
            exitHome();
        }
    }

    private boolean consumeTvFullscreenBack() {
        if (!TV_FULL.equals(webChromeMode) && !TV_TOOLBAR_HIDDEN.equals(webChromeMode)) return false;
        applyTvChrome(TV_NORMAL);
        requestTitleFocus();
        return true;
    }

    private void exitHome() {
        AppDatabase.autoBackupOnExit();
        confirmExitHome();
    }

    private void confirmExitHome() {
        if (PlaybackService.isRunning()) Util.moveToBackground(this);
        else super.onBackInvoked();
    }

    @Override
    protected void onDestroy() {
        if (mWeb != null) mWeb.destroy();
        DLNARendererService.stop(this);
        LiveConfig.get().clear();
        VodConfig.get().clear();
        OkHttp.get().clear();
        if (Setting.isAutoClearCache()) Path.clear(Path.cache());
        Source.get().exit();
        Server.get().stop();
        super.onDestroy();
    }

    @Override
    public void onWebLoading() {
        showWebOverlay();
        mBinding.progressLayout.showProgress();
    }

    @Override
    public void onWebReady() {
        showWebOverlay();
        mBinding.progressLayout.showContent();
        mBinding.typeRecycler.setVisibility(View.GONE);
        mBinding.recycler.setVisibility(View.GONE);
    }

    @Override
    public void onWebError() {
        applyTvChrome(TV_NORMAL);
        if (mWeb != null) mWeb.hide();
        hideWebOverlay();
        mBinding.recycler.setVisibility(View.VISIBLE);
        getVideo(true);
    }

    @Override
    public void setToolbar(boolean visible) {
        if (!Setting.isWebHomeFullscreen()) {
            applyTvChrome(TV_NORMAL);
            return;
        }
        applyTvChrome(visible ? webDefaultChromeMode : TV_TOOLBAR_HIDDEN);
    }

    @Override
    public void applyDefaultChrome(Site site) {
        if (!Setting.isWebHomeFullscreen()) {
            webDefaultChromeMode = TV_NORMAL;
            applyTvChrome(TV_NORMAL);
            return;
        }
        webDefaultChromeMode = tvDefaultMode(site == null ? "" : site.getChromeMode());
        applyTvChrome(webDefaultChromeMode);
    }

    @Override
    public void setChrome(JsonObject payload) {
        if (!Setting.isWebHomeFullscreen()) {
            applyTvChrome(TV_NORMAL);
            return;
        }
        applyTvChrome(tvRuntimeMode(Json.safeString(payload, "mode")));
    }

    @Override
    public void restoreChrome() {
        if (!Setting.isWebHomeFullscreen()) {
            applyTvChrome(TV_NORMAL);
            return;
        }
        applyTvChrome(webDefaultChromeMode);
    }

    @Override
    public WebHomeViewport getViewport() {
        return tvViewport(webChromeMode);
    }

    @Override
    public void openVod() {
        applyTvChrome(TV_NORMAL);
        if (mWeb != null) mWeb.hide();
        hideWebOverlay();
        getVideo(true);
    }

    @Override
    public void openSetting() {
        SettingActivity.start(this);
    }

    private void applyTvChrome(String mode) {
        webChromeMode = mode;
        webToolbarVisible = TV_NORMAL.equals(mode) || TV_OVERLAY.equals(mode);
        updateToolbarVisibility(webToolbarVisible);
        syncWebOverlayLayout();
        if (mWeb != null) mWeb.setViewport(tvViewport(mode));
    }

    private String tvDefaultMode(String mode) {
        return tvMode(mode, TV_FULL);
    }

    private String tvRuntimeMode(String mode) {
        return tvMode(mode, webChromeMode);
    }

    private String tvMode(String mode, String fallback) {
        String value = TextUtils.isEmpty(mode) ? "" : mode.trim().toLowerCase(Locale.ROOT);
        if (TV_NORMAL.equals(value) || "normal".equals(value)) return TV_NORMAL;
        if (TV_TOOLBAR_HIDDEN.equals(value)) return TV_TOOLBAR_HIDDEN;
        if (TV_OVERLAY.equals(value)) return TV_OVERLAY;
        if (TV_FULL.equals(value) || "edge".equals(value) || "immersive".equals(value)) return TV_FULL;
        return fallback;
    }

    private WebHomeViewport tvViewport(String mode) {
        return WebHomeViewport.fixed(ResUtil.dp2px(28), ResUtil.dp2px(48), ResUtil.dp2px(28), ResUtil.dp2px(48), mode);
    }

}
