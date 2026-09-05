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
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.FocusHighlight;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.lifecycle.ViewModelProvider;
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
import com.fongmi.android.tv.impl.ConfigListener;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.service.DLNARendererService;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.setting.AutoBackupPolicy;
import com.fongmi.android.tv.setting.AppBranding;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.adapter.BaseDiffCallback;
import com.fongmi.android.tv.ui.adapter.TypeAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.CustomRowPresenter;
import com.fongmi.android.tv.ui.custom.CustomSelector;
import com.fongmi.android.tv.ui.custom.CustomTitleView;
import com.fongmi.android.tv.ui.dialog.HistoryDialog;
import com.fongmi.android.tv.ui.dialog.ExitConfirmDialog;
import com.fongmi.android.tv.ui.dialog.HomeMenuDialog;
import com.fongmi.android.tv.ui.dialog.SiteDialog;
import com.fongmi.android.tv.ui.fragment.FolderFragment;
import com.fongmi.android.tv.ui.presenter.FuncPresenter;
import com.fongmi.android.tv.ui.presenter.HeaderPresenter;
import com.fongmi.android.tv.ui.presenter.HistoryPresenter;
import com.fongmi.android.tv.ui.presenter.ProgressPresenter;
import com.fongmi.android.tv.ui.presenter.VodPresenter;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.android.tv.utils.Util;
import com.fongmi.android.tv.web.HomeWebController;
import com.fongmi.android.tv.web.WebHomeTarget;

import com.fongmi.android.tv.web.WebHomeViewport;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.google.common.collect.Lists;
import com.google.gson.JsonObject;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HomeActivity extends BaseActivity implements ExitConfirmDialog.Listener, CustomTitleView.Listener, VodPresenter.OnClickListener, FuncPresenter.OnClickListener, HistoryPresenter.OnClickListener, TypeAdapter.OnClickListener, HomeWebController.Listener, ConfigListener, HomeMenuDialog.Listener, FolderFragment.FilterHost, FolderFragment.ScrollHeaderHost, FolderFragment.CategoryEdgeHost {

    private static final String TV_NORMAL = "tv-normal";
    private static final String TV_TOOLBAR_HIDDEN = "tv-toolbar-hidden";
    private static final String TV_OVERLAY = "tv-overlay";
    private static final String TV_FULL = "tv-full";
    private static final long TYPE_SWITCH_DELAY_MS = 100;
    private static final long CONFIRM_LONG_PRESS_MS = 550;

    private ActivityHomeBinding mBinding;
    private ArrayObjectAdapter mHistoryAdapter;
    private ArrayObjectAdapter mFuncAdapter;
    private ArrayObjectAdapter mAdapter;
    private HistoryPresenter mPresenter;
    private SiteViewModel mViewModel;
    private TypeAdapter mTypeAdapter;
    private FolderFragment mFolder;
    private HomeWebController mWeb;
    private WebView mHomeWeb;
    private Result mResult;
    private Result mHomeResult;
    private Clock mClock;
    private View mSelectedTypeView;
    private Class mCurrentType;
    private int mPendingTypePosition = -1;
    private String mCategorySiteKey = "";
    private String webChromeMode = TV_NORMAL;
    private String webDefaultChromeMode = TV_FULL;
    private boolean webToolbarVisible = true;
    private boolean loadingHomeCategory;
    private boolean skipNextVodConfigRefresh;
    private boolean pendingOpenVod; // 手动点击"点播"后等待数据加载完成再进分类页
    private boolean webConfirmKeyDown;
    private boolean webConfirmLongPress;
    private final Runnable mTypeSwitch = this::switchType;
    private final Runnable mWebConfirmLongPress = this::triggerWebFocusedLongPress;
    private final Runnable mDelayedInitConfig = this::initConfig;
    private final Runnable mDelayedPermissionRequest = () -> {
        if (!isFinishing() && !isDestroyed()) PermissionUtil.requestFile(this, allGranted -> PermissionUtil.requestNotify(this));
    };
    private final Runnable mDelayedDlnaStart = () -> {
        if (!isFinishing() && !isDestroyed()) DLNARendererService.start(this);
    };

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
        App.resumeBackgroundServices();
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        SpiderDebug.log("startup", "home initView start cost=%sms", System.currentTimeMillis() - App.time());
        mResult = Result.empty();
        mHomeResult = Result.empty();
        mClock = Clock.create(mBinding.clock);
        setLogo();
        syncHomeSiteLock();
        mBinding.progressLayout.showProgress();
        setRecyclerView();
        setViewModel();
        setAdapter();
        runAfterFirstFrame(this::initAfterFirstFrame);
        SpiderDebug.log("startup", "home initView end cost=%sms", System.currentTimeMillis() - App.time());
    }

    private void initAfterFirstFrame() {
        SpiderDebug.log("startup", "home first frame cost=%sms", System.currentTimeMillis() - App.time());
        App.post(mDelayedInitConfig, 80);
        App.post(mDelayedPermissionRequest, 1800);
        App.post(mDelayedDlnaStart, 2500);
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
        mBinding.recycler.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (!isCategoryVisible()) {
                    boolean headerVisible = isTopRow(position);
                    updateTypeRecyclerVisibility(headerVisible);
                    updateToolbarVisibility(headerVisible);
                }
                if (mPresenter.isDelete()) setHistoryDelete(false);
            }
        });
        mBinding.typeRecycler.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (mSelectedTypeView != null) mSelectedTypeView.setSelected(false);
                if (child == null) return;
                mSelectedTypeView = child.itemView;
                mSelectedTypeView.setSelected(true);
                if (parent.hasFocus()) updateToolbarVisibility(true);
                if (Setting.isHomeVodAutoLoad()) scheduleTypeSwitch(position);
            }
        });
    }

    private void scheduleTypeSwitch(int position) {
        mPendingTypePosition = position;
        mBinding.typeRecycler.removeCallbacks(mTypeSwitch);
        mBinding.typeRecycler.postDelayed(mTypeSwitch, TYPE_SWITCH_DELAY_MS);
    }

    private void resumeTypeSwitch() {
        if (!Setting.isHomeVodAutoLoad() || mBinding.typeRecycler.getVisibility() != View.VISIBLE) return;
        int position = mBinding.typeRecycler.getSelectedPosition();
        if (position >= 0) scheduleTypeSwitch(position);
    }

    private void switchType() {
        int position = mPendingTypePosition;
        if (isFinishing() || isDestroyed() || getSupportFragmentManager().isStateSaved()) return;
        if (!Setting.isHomeVodAutoLoad() || position < 0 || position >= mTypeAdapter.getItemCount()) return;
        Class item = mTypeAdapter.get(position);
        if (item.isHome()) showHomeContent();
        else showCategoryContent(item);
    }

    @Override
    public void onCategoryContentHorizontalEdge(Class item, int contentRow, boolean towardEnd) {
        if (!isCurrentCategory(item) || contentRow < 0) return;
        item = getAdjacentCategory(item, towardEnd);
        if (item == null) return;
        mBinding.typeRecycler.removeCallbacks(mTypeSwitch);
        int position = mTypeAdapter.indexOf(item);
        mBinding.typeRecycler.setSelectedPosition(position);
        if (contentRow == 0) focusFirstCard(item);
        else focusCategoryButton(item);
    }

    private Class getAdjacentCategory(Class item, boolean towardEnd) {
        int position = mTypeAdapter.indexOf(item);
        int target = position + (towardEnd ? 1 : -1);
        if (position < 0 || target < 0 || target >= mTypeAdapter.getItemCount()) return null;
        Class candidate = mTypeAdapter.get(target);
        return candidate.isHome() ? null : candidate;
    }

    private void focusFirstCard(Class item) {
        showCategoryContent(item);
        getSupportFragmentManager().executePendingTransactions();
        if (mFolder != null && isCurrentCategory(item)) mFolder.requestContentFocus(0);
    }

    private void focusCategoryButton(Class item) {
        mBinding.typeRecycler.setVisibility(View.VISIBLE);
        updateToolbarVisibility(true);
        mBinding.typeRecycler.requestFocus();
        showCategoryContent(item);
    }

    private void showHomeContent() {
        mCurrentType = null;
        mBinding.progressLayout.setVisibility(View.VISIBLE);
        mBinding.categoryContainer.setVisibility(View.GONE);
        if (mFolder != null && mFolder.isAdded() && !mFolder.isHidden()) {
            mFolder.clearContentFocusRequest();
            mFolder.setUserVisibleHint(false);
            getSupportFragmentManager().beginTransaction().hide(mFolder).commit();
        }
    }

    private void showCategoryContent(Class item) {
        showCategoryContent(item, false);
    }

    private void showCategoryContent(Class item, boolean toggleFilter) {
        if (getSupportFragmentManager().isStateSaved()) return;
        getSupportFragmentManager().executePendingTransactions();
        if (item == null || item.isHome()) {
            showHomeContent();
            return;
        }
        if (isCurrentCategory(item)) {
            if (toggleFilter) updateFilter(item);
            return;
        }
        boolean keepTypeFocus = mBinding.typeRecycler.hasFocus();
        applyTvChrome(TV_NORMAL);
        if (mWeb != null) mWeb.hide();
        hideWebOverlay();
        mBinding.progressLayout.setVisibility(View.GONE);
        mBinding.categoryContainer.setVisibility(View.VISIBLE);

        String tag = "home-category:" + mCategorySiteKey + ":" + item.getTypeId();
        Fragment existing = getSupportFragmentManager().findFragmentByTag(tag);
        FolderFragment target = existing instanceof FolderFragment ? (FolderFragment) existing : FolderFragment.newInstance(getHome().getKey(), item);
        if (target == mFolder && target.isAdded() && !target.isHidden()) {
            mCurrentType = item;
            if (toggleFilter) updateFilter(item);
            return;
        }

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        if (mFolder != null && mFolder.isAdded()) {
            mFolder.clearContentFocusRequest();
            mFolder.setUserVisibleHint(false);
            transaction.hide(mFolder);
        }
        if (target.isAdded()) transaction.show(target);
        else transaction.add(R.id.categoryContainer, target, tag);
        target.setUserVisibleHint(true);
        transaction.runOnCommit(() -> {
            restoreTypeFocus(keepTypeFocus, item);
            if (toggleFilter && target == mFolder && isCurrentCategory(item)) updateFilter(item);
        });
        transaction.commit();
        mFolder = target;
        mCurrentType = item;
    }

    private void restoreTypeFocus(boolean keepTypeFocus, Class item) {
        if (!keepTypeFocus) return;
        int position = mTypeAdapter.indexOf(item);
        if (!isCategoryVisible() || position < 0 || mBinding.typeRecycler.getSelectedPosition() != position) return;
        mBinding.typeRecycler.setVisibility(View.VISIBLE);
        updateToolbarVisibility(true);
        mBinding.typeRecycler.requestFocus();
    }

    private void syncCategorySite() {
        String key = getHome().getKey();
        if (TextUtils.equals(mCategorySiteKey, key)) return;
        mCategorySiteKey = key;
        clearCategoryContent();
    }

    private void clearCategoryContent() {
        mBinding.typeRecycler.removeCallbacks(mTypeSwitch);
        mPendingTypePosition = -1;
        mCurrentType = null;
        mFolder = null;
        mBinding.progressLayout.setVisibility(View.VISIBLE);
        mBinding.categoryContainer.setVisibility(View.GONE);

        FragmentTransaction transaction = null;
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (!(fragment instanceof FolderFragment folder)) continue;
            folder.clearContentFocusRequest();
            if (transaction == null) transaction = getSupportFragmentManager().beginTransaction();
            transaction.remove(fragment);
        }
        if (transaction != null) transaction.commit();
    }

    private void updateToolbarVisibility(boolean visible) {
        mBinding.toolbar.setVisibility(visible && webToolbarVisible ? View.VISIBLE : View.GONE);
        syncNativeContentInset();
        syncWebOverlayLayout();
    }

    private void syncNativeContentInset() {
        int top = isToolbarVisible() ? toolbarHeight() : 0;
        if (mBinding.nativeContent.getPaddingTop() == top) return;
        mBinding.nativeContent.setPadding(mBinding.nativeContent.getPaddingLeft(), top, mBinding.nativeContent.getPaddingRight(), mBinding.nativeContent.getPaddingBottom());
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

    private boolean isTopRow(int position) {
        int history = mAdapter.indexOf(R.string.home_history);
        return history == -1 || position < history;
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
        selector.addPresenter(ListRow.class, new CustomRowPresenter(16), FuncPresenter.class);
        selector.addPresenter(ListRow.class, new CustomRowPresenter(16, FocusHighlight.ZOOM_FACTOR_SMALL, HorizontalGridView.FOCUS_SCROLL_ALIGNED), HistoryPresenter.class);
        mBinding.recycler.setAdapter(new ItemBridgeAdapter(mAdapter = new ArrayObjectAdapter(selector)));
        mBinding.recycler.setVerticalSpacing(ResUtil.dp2px(16));
        mBinding.typeRecycler.setHorizontalSpacing(ResUtil.dp2px(16));
        mBinding.typeRecycler.setRowHeight(android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.typeRecycler.setAdapter(mTypeAdapter = new TypeAdapter(this));
    }

    private void setWebView() {
        try {
            SpiderDebug.log("startup", "webview create start cost=%sms", System.currentTimeMillis() - App.time());
            WebView webView = getHomeWeb();
            if (webView == null) {
                SpiderDebug.log("startup", "webview unavailable, web home disabled");
                return;
            }
            mWeb = new HomeWebController(this, webView, this);
            mWeb.setViewport(tvViewport(webChromeMode));
            SpiderDebug.log("startup", "webview create end cost=%sms", System.currentTimeMillis() - App.time());
        } catch (Throwable e) {
            SpiderDebug.log("startup", "webview init failed: %s", e.toString());
            mHomeWeb = null;
            mWeb = null;
        }
    }

    private void ensureWebView() {
        if (mWeb == null) setWebView();
    }

    private WebView getHomeWeb() {
        if (mHomeWeb != null) return mHomeWeb;
        try {
            mHomeWeb = new WebView(this);
            mHomeWeb.setFocusable(true);
            mHomeWeb.setFocusableInTouchMode(true);
            mHomeWeb.setVisibility(View.GONE);
            mBinding.webOverlay.addView(mHomeWeb, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            return mHomeWeb;
        } catch (Throwable e) {
            SpiderDebug.log("startup", "webview construction failed: %s", e.toString());
            mHomeWeb = null;
            return null;
        }
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
            // 如果用户手动点击了"点播"按钮，数据加载完成后自动进入分类页
            if (pendingOpenVod && !result.getTypes().isEmpty()) {
                pendingOpenVod = false;
                openCategory(result.getTypes().get(0));
            }
        });
    }

    private boolean isHomeCategoryResult(Result result) {
        return loadingHomeCategory && result.getTypes().isEmpty();
    }

    private void setAdapter() {
        mHistoryAdapter = new ArrayObjectAdapter(mPresenter = new HistoryPresenter(this));
        mAdapter.add(new ListRow(mFuncAdapter = new ArrayObjectAdapter(new FuncPresenter(this))));
        if (Setting.isHomeHistory()) mAdapter.add(R.string.home_history);
        mAdapter.add(R.string.home_recommend);
    }

    private void setTitle() {
        mBinding.title.setText(AppBranding.getDisplayName(this, getHome().getDisplayName(), getConfig().getName()));
    }

    private void syncHomeSiteLock() {
        mBinding.title.setSiteLocked(Setting.isHomeSiteLock());
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
                skipNextVodConfigRefresh = true;
                showContent();
            }

            @Override
            public void error(String msg) {
                SpiderDebug.log("startup", "config load error cost=%sms msg=%s", System.currentTimeMillis() - App.time(), msg);
                Notify.show(msg);
                skipNextVodConfigRefresh = true;
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
        getHistory();
        getVideo();
        setFocus();
        App.post(this::prewarmWebView, 1500);
        SpiderDebug.log("startup", "home showContent end cost=%sms", System.currentTimeMillis() - App.time());
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
        if (!mBinding.title.hasFocus()) mBinding.recycler.requestFocus();
    }

    private void getVideo() {
        getVideo(false);
    }

    private void getVideo(boolean forceNative) {
        syncCategorySite();
        if (!forceNative && WebHomeTarget.canLoad(getHome())) {
            ensureWebView();
        }
        if (!forceNative && mWeb != null && mWeb.load(getHome())) {
            mBinding.typeRecycler.setVisibility(View.GONE);
            mBinding.recycler.setVisibility(View.GONE);
            if (mWeb.isReady()) {
                mBinding.progressLayout.showContent();
                showWebOverlay();
            } else {
                hideWebOverlay();
                mBinding.progressLayout.showProgress();
            }
            return;
        }
        if (mWeb != null) mWeb.hide();
        hideWebOverlay();
        applyTvChrome(TV_NORMAL);
        mBinding.recycler.setVisibility(View.VISIBLE);
        mResult = Result.empty();
        mHomeResult = Result.empty();
        loadingHomeCategory = false;
        if (!Setting.isHomeVodAutoLoad()) mBinding.typeRecycler.setVisibility(View.GONE);
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
            showHomeContent();
            return;
        }
        List<Class> items = new ArrayList<>();
        if (Setting.isHomeVodAutoLoad()) items.add(createHomeType());
        items.addAll(result.getTypes());
        mTypeAdapter.addAll(items);
        if (Setting.isHomeVodAutoLoad()) {
            mBinding.typeRecycler.setSelectedPosition(0);
            scheduleTypeSwitch(0);
        }
        updateTypeRecyclerVisibility();
    }

    private Class createHomeType() {
        Class home = new Class();
        home.setTypeId("home");
        home.setTypeName(getString(R.string.home));
        return home;
    }

    private void updateTypeRecyclerVisibility() {
        updateTypeRecyclerVisibility(isCategoryVisible() || isTopRow(mBinding.recycler.getSelectedPosition()));
    }

    private void updateTypeRecyclerVisibility(boolean headerVisible) {
        boolean enabled = mTypeAdapter.getItemCount() > 0 && Setting.isHomeVodAutoLoad();
        mBinding.typeRecycler.setVisibility(enabled && headerVisible ? View.VISIBLE : View.GONE);
        if (!enabled) showHomeContent();
    }

    private void syncTypeItems() {
        boolean enabled = Setting.isHomeVodAutoLoad();
        boolean hasHome = mTypeAdapter.getItemCount() > 0 && mTypeAdapter.get(0).isHome();
        if (mHomeResult != null && !mHomeResult.getTypes().isEmpty() && enabled != hasHome) setTypes(mHomeResult);
        else updateTypeRecyclerVisibility();
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
        List<Func> items = new ArrayList<>();
        for (com.fongmi.android.tv.bean.HomeButton button : com.fongmi.android.tv.bean.HomeButton.getVisibleButtons()) {
            items.add(Func.create(button.getResId()));
        }
        mFuncAdapter.setItems(items, new BaseDiffCallback<Func>());
    }

    private void getHistory() {
        getHistory(false);
    }

    private void getHistory(boolean renew) {
        if (!Setting.isHomeHistory()) {
            removeHistoryRows();
            return;
        }
        int headerIndex = mAdapter.indexOf(R.string.home_history);
        if (headerIndex == -1) mAdapter.add(getRecommendHeaderIndex(), R.string.home_history);
        List<History> items = History.getForDisplay();
        int historyIndex = getHistoryIndex();
        int recommendIndex = getRecommendIndex();
        boolean exist = recommendIndex - historyIndex == 2;
        if (renew) mHistoryAdapter = new ArrayObjectAdapter(mPresenter = new HistoryPresenter(this));
        if ((items.isEmpty() && exist) || (renew && exist)) mAdapter.removeItems(historyIndex, 1);
        if ((!items.isEmpty() && !exist) || (renew && exist)) mAdapter.add(historyIndex, new ListRow(mHistoryAdapter));
        mHistoryAdapter.setItems(items, new BaseDiffCallback<History>());
    }

    private void removeHistoryRows() {
        int headerIndex = mAdapter.indexOf(R.string.home_history);
        if (headerIndex == -1) return;
        int recommendIndex = mAdapter.indexOf(R.string.home_recommend);
        mAdapter.removeItems(headerIndex, recommendIndex - headerIndex);
        mHistoryAdapter.clear();
        mPresenter.setDelete(false);
    }

    private int getRecommendHeaderIndex() {
        return mAdapter.indexOf(R.string.home_recommend);
    }

    private void setHistoryDelete(boolean delete) {
        mPresenter.setDelete(delete);
        mHistoryAdapter.notifyArrayItemRangeChanged(0, mHistoryAdapter.size());
    }

    private void clearHistory() {
        if (!Setting.isGlobalHistoryEnabled()) {
            performClearHistory();
            return;
        }
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.dialog_delete_record)
                .setMessage(R.string.dialog_delete_global_history)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, (dialog, which) -> performClearHistory())
                .show();
    }

    private void performClearHistory() {
        mAdapter.removeItems(getHistoryIndex(), 1);
        History.deleteForDisplay();
        mPresenter.setDelete(false);
        mHistoryAdapter.clear();
    }

    private int getHistoryIndex() {
        return mAdapter.indexOf(R.string.home_history) + 1;
    }

    private int getRecommendIndex() {
        return mAdapter.indexOf(R.string.home_recommend) + 1;
    }

    private void setLogo() {
        AppBranding.applyLogo(mBinding.logo);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        switch (event.type()) {
            case VOD:
                if (skipNextVodConfigRefresh) {
                    skipNextVodConfigRefresh = false;
                    SpiderDebug.log("startup", "skip duplicate vod config refresh");
                } else {
                    RefreshEvent.history();
                    RefreshEvent.home();
                }
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
            case HISTORY:
                getHistory();
                break;
            case SIZE:
                if (mWeb != null && mWeb.isVisible()) return;
                getVideo();
                getHistory(true);
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
        if (VodConfig.get().getConfig().equals(event.config())) {
            VideoActivity.cast(this, event.history().save(VodConfig.getCid()));
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
        if (item.getResId() == R.string.home_vod) {
            // mHomeResult 才可靠保存首页分类（mResult 可能被分类内容结果覆盖）
            Result homeResult = mHomeResult != null && !mHomeResult.getTypes().isEmpty() ? mHomeResult : mResult;
            if (homeResult.getTypes().isEmpty()) {
                // 内容未加载（如 Web 站源首页未拉取原生分类），强制加载原生数据后自动进分类页
                pendingOpenVod = true;
                getVideo(true);
            } else {
                // 内容已加载，进入第一个分类
                openCategory(homeResult.getTypes().get(0));
            }
        } else if (item.getResId() == R.string.home_live) LiveActivity.start(this);
        else if (item.getResId() == R.string.home_keep) KeepActivity.start(this);
        else if (item.getResId() == R.string.home_push) PushActivity.start(this);
        else if (item.getResId() == R.string.home_search) SearchActivity.start(this);
        else if (item.getResId() == R.string.home_setting) SettingActivity.start(this);
        else if (item.getResId() == R.string.home_cast) PushActivity.start(this, 3);
        else if (item.getResId() == R.string.home_history_button) HistoryActivity.start(this);
    }

    @Override
    public boolean onLongClick(Func item) {
        if (item.getResId() != R.string.home_search) return false;
        SearchActivity.start(this, "", getHome().getKey());
        return true;
    }

    private boolean isCategoryVisible() {
        return mCurrentType != null && mBinding.categoryContainer.getVisibility() == View.VISIBLE;
    }

    private boolean isCurrentCategory(Class item) {
        return item != null && item.equals(mCurrentType) && mFolder != null && mFolder.isAdded() && !mFolder.isHidden() && isCategoryVisible();
    }

    private boolean isFilterVisible() {
        return isCategoryVisible() && mCurrentType.getFilter();
    }

    private void updateFilter(Class item) {
        if (item == null || item.isHome() || !isCurrentCategory(item)) return;
        item.setFilter(!item.getFilter());
        mFolder.toggleFilter(item.getFilter());
        int position = mTypeAdapter.indexOf(item);
        if (position >= 0) mTypeAdapter.notifyItemRangeChanged(position, 1);
    }

    @Override
    public void closeFilter() {
        if (isFilterVisible()) updateFilter(mCurrentType);
    }

    @Override
    public int[] getScrollHeaderIds() {
        return new int[]{R.id.typeRecycler, R.id.toolbar};
    }

    @Override
    public void onScrollHeaderVisibilityChanged(boolean visible) {
        updateToolbarVisibility(visible);
    }

    private void openCategory(Class item) {
        if (Setting.isHomeVodAutoLoad()) {
            int position = mTypeAdapter.indexOf(item);
            if (position >= 0) {
                mBinding.typeRecycler.setSelectedPosition(position);
                mBinding.typeRecycler.requestFocus();
                scheduleTypeSwitch(position);
                return;
            }
        }
        Result result = mHomeResult == null || mHomeResult.getTypes().isEmpty() ? mResult : mHomeResult;
        VodActivity.start(this, getHome().getKey(), result, result.getTypes().indexOf(item));
    }

    @Override
    public void onItemClick(Class item) {
        if (item.isHome()) showHomeContent();
        else if (isCurrentCategory(item)) updateFilter(item);
        else {
            mBinding.typeRecycler.removeCallbacks(mTypeSwitch);
            showCategoryContent(item, true);
        }
    }

    @Override
    public void onRefresh(Class item) {
        if (item.isHome()) onRefresh();
        else if (mFolder != null && item.equals(mCurrentType)) mFolder.onRefresh();
    }

    @Override
    public void onItemClick(Vod item) {
        if (item.isAction()) mViewModel.action(getHome().getKey(), item.getAction());
        else if (getHome().isIndex()) CollectActivity.start(this, item.getName());
        else VideoActivity.start(this, getHome().getKey(), item.getId(), item.getName(), item.getPic());
    }

    @Override
    public boolean onLongClick(Vod item) {
        if (item.isAction()) return false;
        CollectActivity.start(this, item.getName());
        return true;
    }

    @Override
    public void onItemClick(History item) {
        HistoryResumeCoordinator.open(this, item);
    }

    @Override
    public void onItemDelete(History item) {
        mHistoryAdapter.remove(item.deleteDisplayItem());
        if (mHistoryAdapter.size() > 0) return;
        mAdapter.removeItems(getHistoryIndex(), 1);
        mPresenter.setDelete(false);
    }

    @Override
    public boolean onLongClick() {
        if (mPresenter.isDelete()) clearHistory();
        else setHistoryDelete(true);
        return true;
    }

    @Override
    public void showDialog() {
        long start = System.currentTimeMillis();
        SpiderDebug.log("site-dialog", "open requested cost=%sms", System.currentTimeMillis() - App.time());
        SiteDialog.create().show(this);
        SpiderDebug.log("site-dialog", "show returned delay=%sms", System.currentTimeMillis() - start);
    }

    private void onHomeMenuKey() {
        int index = Setting.getHomeMenuKey();
        if (index == 0) HomeMenuDialog.create().show(this);
        else onHomeMenuItem(index);
    }

    /**
     * 执行 select_home_menu_key 中某一项对应的动作，下标 1..9（0 是「选项弹窗」自身，不会走到这里）。
     */
    @Override
    public void onHomeMenuItem(int index) {
        switch (index) {
            case 1 -> SiteDialog.create().action().show(this);
            case 2 -> HistoryDialog.create().vod().show(this);
            case 3 -> LiveActivity.start(this);
            case 4 -> HistoryActivity.start(this);
            case 5 -> SearchActivity.start(this);
            case 6 -> PushActivity.start(this);
            case 7 -> PushActivity.start(this, 3);
            case 8 -> KeepActivity.start(this);
            case 9 -> SettingActivity.start(this);
        }
    }

    @Override
    public void onRefresh() {
        if (mWeb != null && mWeb.isVisible()) mWeb.reload();
        else getVideo();
    }

    @Override
    public void reloadConfig() {
        VodConfig.get().clear("leanback-home-reload").config(getConfig()).load(new Callback() {
            @Override
            public void start() {
                mBinding.progressLayout.showProgress();
            }

            @Override
            public void success() {
                showContent();
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
                showContent();
            }
        });
    }

    @Override
    public void setConfig(Config config) {
        if (config.getType() != 0) return;
        if (config.getUrl().startsWith("file")) {
            PermissionUtil.requestFile(this, allGranted -> VodConfig.load(config, getCallback()));
        } else {
            VodConfig.load(config, getCallback());
        }
    }

    @Override
    public void setSite(Site item) {
        SpiderDebug.log("site-dialog", "set site key=%s name=%s homePage=%s", item.getKey(), item.getName(), item.hasHomePage());
        VodConfig.get().setHome(item);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (KeyUtil.isMenuKey(event)) {
            if (isCategoryVisible()) updateFilter(mCurrentType);
            else onHomeMenuKey();
            return true;
        }
        if (mWeb != null && mWeb.isVisible() && mBinding.webOverlay.getVisibility() == View.VISIBLE) {
            if (KeyUtil.isBackKey(event)) {
                if (KeyUtil.isActionUp(event)) onBackInvoked();
                return true;
            }
            if (mBinding.toolbar.hasFocus()) {
                if (KeyUtil.isActionDown(event) && KeyUtil.isDownKey(event)) return requestWebFocus();
                return super.dispatchKeyEvent(event);
            }
            if (KeyUtil.isEnterKey(event)) return dispatchWebConfirmKey(event);
            if (KeyUtil.isUpKey(event) && isToolbarVisible()) return super.dispatchKeyEvent(event);
            if (mWeb.dispatchKeyEvent(event)) return true;
            return super.dispatchKeyEvent(event);
        }
        if (KeyUtil.isActionDown(event) & KeyUtil.isUpKey(event) && mBinding.typeRecycler.hasFocus()) return requestTitleFocus();
        if (KeyUtil.isActionDown(event) & KeyUtil.isDownKey(event) && mBinding.typeRecycler.hasFocus()) return requestContentFocus();
        if (KeyUtil.isActionDown(event) & KeyUtil.isUpKey(event) && mBinding.recycler.hasFocus() && mBinding.typeRecycler.getVisibility() == View.VISIBLE) updateToolbarVisibility(true);
        if (KeyUtil.isActionDown(event) & KeyUtil.isDownKey(event) && getCurrentFocus() == mBinding.title) return requestHomeFocus();
        return super.dispatchKeyEvent(event);
    }

    private boolean dispatchWebConfirmKey(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (!webConfirmKeyDown) {
                webConfirmKeyDown = true;
                webConfirmLongPress = false;
                mBinding.webOverlay.postDelayed(mWebConfirmLongPress, CONFIRM_LONG_PRESS_MS);
            }
            if (event.isLongPress() || event.getRepeatCount() > 0) triggerWebFocusedLongPress();
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_UP) {
            mBinding.webOverlay.removeCallbacks(mWebConfirmLongPress);
            boolean click = webConfirmKeyDown && !webConfirmLongPress && !event.isCanceled();
            webConfirmKeyDown = false;
            webConfirmLongPress = false;
            if (click && mWeb != null) mWeb.dispatchFocusedClick();
            return true;
        }
        return true;
    }

    private void triggerWebFocusedLongPress() {
        if (!webConfirmKeyDown || webConfirmLongPress || mWeb == null) return;
        mBinding.webOverlay.removeCallbacks(mWebConfirmLongPress);
        webConfirmLongPress = mWeb.dispatchFocusedLongPress();
    }

    private void cancelWebConfirmKey() {
        if (mBinding != null) mBinding.webOverlay.removeCallbacks(mWebConfirmLongPress);
        webConfirmKeyDown = false;
        webConfirmLongPress = false;
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
        if (isCategoryVisible()) return mFolder != null && mFolder.requestContentFocus();
        if (mBinding.recycler.getVisibility() != View.VISIBLE || mBinding.recycler.getChildCount() == 0) return false;
        View child = mBinding.recycler.getFocusedChild();
        if (child == null) child = mBinding.recycler.getChildAt(0);
        return child != null && child.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mClock.start();
        syncHomeSiteLock();
        if (mWeb != null) mWeb.onResume();
        setFunc();
        syncTypeItems();
        resumeTypeSwitch();
    }

    @Override
    protected void onPause() {
        mBinding.typeRecycler.removeCallbacks(mTypeSwitch);
        cancelWebConfirmKey();
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
        } else if (isCategoryVisible()) {
            if (isFilterVisible()) closeFilter();
            else if (mFolder != null && mFolder.canBack()) mFolder.goBack();
            else selectHomeType();
        } else if (mBinding.progressLayout.isProgress()) {
            showContent();
        } else if (mPresenter.isDelete()) {
            setHistoryDelete(false);
        } else if (mBinding.recycler.getSelectedPosition() != 0) {
            mBinding.recycler.scrollToPosition(0);
        } else {
            exitHome();
        }
    }

    private void selectHomeType() {
        if (mTypeAdapter.getItemCount() == 0 || !mTypeAdapter.get(0).isHome()) {
            showHomeContent();
            return;
        }
        mBinding.typeRecycler.setSelectedPosition(0);
        mBinding.typeRecycler.requestFocus();
        showHomeContent();
    }

    private boolean consumeTvFullscreenBack() {
        if (!TV_FULL.equals(webChromeMode) && !TV_TOOLBAR_HIDDEN.equals(webChromeMode)) return false;
        applyTvChrome(TV_NORMAL);
        requestTitleFocus();
        return true;
    }

    private void exitHome() {
        ExitConfirmDialog.create(PlaybackService.canContinueInBackground()).show(this);
    }

    private void continueInBackground() {
        moveTaskToBack(true);
    }

    private void exitCompletely() {
        cancelPendingStartupTasks();
        AppExitCoordinator.exit(this);
    }

    private void cancelPendingStartupTasks() {
        App.removeCallbacks(mDelayedInitConfig, mDelayedPermissionRequest, mDelayedDlnaStart);
    }

    @Override
    protected void onDestroy() {
        mBinding.typeRecycler.removeCallbacks(mTypeSwitch);
        cancelPendingStartupTasks();
        if (mWeb != null) mWeb.destroy();
        DLNARendererService.stop(this);
        LiveConfig.get().clear();
        VodConfig.get().clear("leanback-home-destroy");
        if (AutoBackupPolicy.shouldRun(
                Setting.isAutoBackup(),
                Setting.hasFileAccess(),
                isFinishing(),
                isChangingConfigurations())) {
            AppDatabase.autoBackup();
        }
        OkHttp.get().clear();
        Source.get().exit();
        Server.get().stop();
        super.onDestroy();
    }

    @Override
    public void onBackgroundPlayback() {
        continueInBackground();
    }

    @Override
    public void onFullExit() {
        exitCompletely();
    }

    @Override
    public void onWebLoading() {
        cancelWebConfirmKey();
        hideWebOverlay();
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
    public void openSite() {
        showDialog();
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
