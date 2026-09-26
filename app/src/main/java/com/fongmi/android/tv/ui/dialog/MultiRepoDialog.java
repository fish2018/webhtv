package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Depot;
import com.fongmi.android.tv.bean.MultiRepo;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.setting.MultiRepoStore;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textview.MaterialTextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 多仓主弹窗：顶部仓库切换（标题栏 + 设置按钮），下方左接口列表 + 右站点网格。
 * 整体风格与 ChoiceDialog / PlayerKernelDialog 一致（MaterialButton + 蓝色焦点）。
 */
public class MultiRepoDialog extends DialogFragment {

    private RecyclerView repoList;    // 仓库名列表（顶部水平？不，按需求描述左侧显示仓库名称 —— 看图片是左侧竖排）
    private RecyclerView apiList;     // 接口列表（左栏）
    private RecyclerView siteGrid;    // 站点网格（右栏）
    private ProgressBar loading;

    private RepoLabelAdapter repoAdapter;
    private ApiAdapter apiAdapter;
    private SiteGridAdapter siteAdapter;

    private List<MultiRepo> repos = new ArrayList<>();
    private int currentRepoIndex = 0;
    private List<Depot> currentApis = new ArrayList<>();
    private List<Site> currentSites = new ArrayList<>();
    private int currentApiIndex = 0;

    private volatile boolean firstLoadDone = false;

    public static MultiRepoDialog show(@NonNull FragmentActivity activity) {
        MultiRepoDialog dialog = new MultiRepoDialog();
        dialog.show(activity.getSupportFragmentManager(), MultiRepoDialog.class.getSimpleName());
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog == null) return;
        Window window = dialog.getWindow();
        if (window == null) return;
        WindowManager.LayoutParams params = window.getAttributes();
        boolean land = ResUtil.isLand(requireContext());
        params.width = (int) (ResUtil.getScreenWidth(requireContext()) * (land ? 0.78f : 0.94f));
        params.height = (int) (ResUtil.getScreenHeight(requireContext()) * (land ? 0.80f : 0.85f));
        params.dimAmount = 0.58f;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setPadding(0, 0, 0, 0);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setAttributes(params);
        window.setLayout(params.width, params.height);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.shape_shell_proxy_dialog);
        int horizontal = ResUtil.dp2px(16);
        int vertical = ResUtil.dp2px(14);
        root.setPadding(horizontal, vertical, horizontal, vertical);

        buildTitleBar(root);
        buildContentArea(root);

        root.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (KeyUtil.isMenuKey(event)) {
                // 菜单键触发维护弹窗
                openManageDialog();
                return true;
            }
            return false;
        });

        // 加载仓库列表
        loadRepos();
        return root;
    }

    private void buildTitleBar(LinearLayout root) {
        LinearLayout titleBar = new LinearLayout(requireContext());
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setPadding(ResUtil.dp2px(4), 0, ResUtil.dp2px(4), 0);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        root.addView(titleBar, barParams);

        // 左侧：仓库名列表（横滑的 RecyclerView）
        repoList = new RecyclerView(requireContext());
        repoList.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        repoList.setHorizontalScrollBarEnabled(false);
        repoList.setVerticalScrollBarEnabled(false);
        LinearLayout.LayoutParams repoParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        repoParams.rightMargin = ResUtil.dp2px(4);
        repoList.setLayoutParams(repoParams);
        titleBar.addView(repoList);

        // 右侧：设置按钮
        MaterialButton settingsBtn = new MaterialButton(requireContext());
        settingsBtn.setAllCaps(false);
        settingsBtn.setIconResource(R.drawable.ic_remote_settings);
        settingsBtn.setIconSize(ResUtil.dp2px(20));
        settingsBtn.setText("");
        settingsBtn.setMinHeight(ResUtil.dp2px(36));
        settingsBtn.setMinWidth(ResUtil.dp2px(36));
        settingsBtn.setInsetTop(0);
        settingsBtn.setInsetBottom(0);
        settingsBtn.setInsetLeft(ResUtil.dp2px(0));
        settingsBtn.setInsetRight(ResUtil.dp2px(0));
        settingsBtn.setIconPadding(0);
        settingsBtn.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        settingsBtn.setStrokeWidth(ResUtil.dp2px(1));
        settingsBtn.setCornerRadius(ResUtil.dp2px(6));
        settingsBtn.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
        settingsBtn.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#DADCE0")));
        settingsBtn.setIconTint(ColorStateList.valueOf(Color.parseColor("#5F6368")));
        settingsBtn.setOnFocusChangeListener((v, hasFocus) -> styleIconBtn(v, hasFocus));
        settingsBtn.setOnClickListener(v -> openManageDialog());
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(ResUtil.dp2px(36), ResUtil.dp2px(36));
        btnParams.leftMargin = ResUtil.dp2px(4);
        settingsBtn.setLayoutParams(btnParams);
        titleBar.addView(settingsBtn);
    }

    private void styleIconBtn(View v, boolean hasFocus) {
        MaterialButton btn = (MaterialButton) v;
        if (hasFocus) {
            btn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#1A73E8")));
            btn.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#174EA6")));
            btn.setIconTint(ColorStateList.valueOf(Color.WHITE));
        } else {
            btn.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
            btn.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#DADCE0")));
            btn.setIconTint(ColorStateList.valueOf(Color.parseColor("#5F6368")));
        }
    }

    private void buildContentArea(LinearLayout root) {
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        cp.topMargin = ResUtil.dp2px(12);
        content.setLayoutParams(cp);

        // 左栏：接口列表
        LinearLayout leftPane = new LinearLayout(requireContext());
        leftPane.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.28f);
        lp.rightMargin = ResUtil.dp2px(12);
        leftPane.setLayoutParams(lp);

        MaterialTextView leftLabel = new MaterialTextView(requireContext());
        leftLabel.setText(R.string.multi_repo_config_list);
        leftLabel.setTextColor(Color.parseColor("#5F6368"));
        leftLabel.setTextSize(13);
        leftLabel.setPadding(ResUtil.dp2px(3), 0, 0, ResUtil.dp2px(3));
        leftPane.addView(leftLabel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        apiList = new RecyclerView(requireContext());
        apiList.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false));
        apiList.setHasFixedSize(true);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        apiList.setLayoutParams(ap);
        leftPane.addView(apiList);

        content.addView(leftPane);

        // 右栏：站点网格
        LinearLayout rightPane = new LinearLayout(requireContext());
        rightPane.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.72f);
        rightPane.setLayoutParams(rp);

        MaterialTextView rightLabel = new MaterialTextView(requireContext());
        rightLabel.setText(R.string.multi_repo_site_list);
        rightLabel.setTextColor(Color.parseColor("#5F6368"));
        rightLabel.setTextSize(13);
        rightLabel.setPadding(ResUtil.dp2px(3), 0, 0, ResUtil.dp2px(3));
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rl.leftMargin = ResUtil.dp2px(4);
        rightPane.addView(rightLabel, rl);

        LinearLayout gridWrap = new LinearLayout(requireContext());
        gridWrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams gwp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        gridWrap.setLayoutParams(gwp);

        loading = new ProgressBar(requireContext(), null, android.R.attr.progressBarStyleSmall);
        loading.setVisibility(View.GONE);
        LinearLayout.LayoutParams lpBar = new LinearLayout.LayoutParams(ResUtil.dp2px(28), ResUtil.dp2px(28));
        lpBar.gravity = Gravity.CENTER;
        loading.setLayoutParams(lpBar);
        gridWrap.addView(loading);

        siteGrid = new RecyclerView(requireContext());
        siteGrid.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        siteGrid.setHasFixedSize(false);
        siteGrid.setItemAnimator(null);
        siteGrid.setVerticalScrollBarEnabled(true);
        LinearLayout.LayoutParams sgp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        siteGrid.setLayoutParams(sgp);
        gridWrap.addView(siteGrid);

        rightPane.addView(gridWrap);

        content.addView(rightPane);
        root.addView(content);
    }

    // ============== 仓库切换 ==============

    private void loadRepos() {
        repos = new ArrayList<>(MultiRepoStore.get());
        if (repos.isEmpty()) {
            // 第一次使用：自动打开维护弹窗
            ChoiceDialog.showConfirm(this, R.string.multi_repo_manage_title, getString(R.string.multi_repo_first_open), R.string.dialog_positive, () -> {
                openManageDialog();
            });
            ensureAdapters();
            repoAdapter.updateData(new ArrayList<>(), -1);
            apiAdapter.updateData(new ArrayList<>(), -1);
            siteAdapter.updateData(new ArrayList<>());
            loading.setVisibility(View.GONE);
            return;
        }
        currentRepoIndex = 0;
        currentApiIndex = 0;
        setupRepoHeader();
        refreshApiList();
    }

    private void ensureAdapters() {
        if (repoAdapter == null) {
            repoAdapter = new RepoLabelAdapter(new ArrayList<>(), -1, index -> {
                if (index == currentRepoIndex) return;
                currentRepoIndex = index;
                currentApiIndex = 0;
                setupRepoHeader();
                refreshApiList();
            });
            repoList.setAdapter(repoAdapter);
        }
        if (apiAdapter == null) {
            apiAdapter = new ApiAdapter(new ArrayList<>(), -1, depot -> onApiSelected(depot));
            apiList.setAdapter(apiAdapter);
        }
        if (siteAdapter == null) {
            siteAdapter = new SiteGridAdapter(new ArrayList<>(), site -> onSiteClicked(site));
            siteGrid.setAdapter(siteAdapter);
        }
    }

    private void setupRepoHeader() {
        ensureAdapters();
        repoAdapter.updateData(repos, currentRepoIndex);
    }

    private void refreshApiList() {
        if (repos.isEmpty()) return;
        ensureAdapters();
        MultiRepo repo = repos.get(currentRepoIndex);
        // 从缓存立即渲染（缓存可能为空）
        List<Depot> cachedApis = MultiRepoFetcher.parseRepoContent(repo.getCache());
        currentApis = cachedApis;
        apiAdapter.updateData(currentApis, currentApiIndex);
        // 如果有缓存，先加载第一个接口的站点
        if (!currentApis.isEmpty()) {
            onApiSelected(currentApis.get(currentApiIndex));
        } else {
            siteAdapter.updateData(new ArrayList<>());
            loading.setVisibility(View.GONE);
        }
        // 同时后台异步 fetch 更新缓存
        MultiRepoFetcher.fetchRepo(repo, false, new MultiRepoFetcher.RepoCallback() {
            @Override
            public void onSuccess(MultiRepoFetcher.RepoResult result) {
                // 更新缓存到 store
                repo.setCache(result.content);
                repo.setEtag(result.etag);
                repo.setLastModified(result.lastModified);
                repo.setTime(System.currentTimeMillis());
                MultiRepoStore.update(repo);
                // 如果当前还是这个仓库，刷新
                if (!isAdded() || repos.isEmpty()) return;
                MultiRepo current = repos.get(currentRepoIndex);
                if (current.getUrl().equals(result.repo.getUrl())) {
                    List<Depot> freshApis = MultiRepoFetcher.parseRepoContent(result.content);
                    // 保持当前选中的接口（按 URL 匹配），避免刷新后选中跳回第一个
                    int newIndex = 0;
                    if (currentApiIndex < currentApis.size()) {
                        String curUrl = currentApis.get(currentApiIndex).getUrl();
                        for (int i = 0; i < freshApis.size(); i++) {
                            if (freshApis.get(i).getUrl().equals(curUrl)) {
                                newIndex = i;
                                break;
                            }
                        }
                    }
                    currentApis = freshApis;
                    currentApiIndex = Math.min(newIndex, Math.max(0, freshApis.size() - 1));
                    apiAdapter.updateData(currentApis, currentApiIndex);
                    // 保持当前焦点，避免在线刷新导致焦点跳回顶部
                    final int focusPos = currentApiIndex;
                    apiList.post(() -> {
                        RecyclerView.ViewHolder vh = apiList.findViewHolderForAdapterPosition(focusPos);
                        if (vh != null) vh.itemView.requestFocus();
                    });
                    if (!currentApis.isEmpty()) {
                        onApiSelected(currentApis.get(currentApiIndex));
                    }
                }
            }

            @Override
            public void onError(String msg) {
                // 已经有缓存 fallback，只显示 toast
                Notify.show(msg);
            }
        });
    }

    // ============== 接口切换 ==============

    private void onApiSelected(Depot depot) {
        if (currentApis.isEmpty()) return;
        // 更新高亮索引
        for (int i = 0; i < currentApis.size(); i++) {
            if (currentApis.get(i).getUrl().equals(depot.getUrl())) {
                currentApiIndex = i;
                break;
            }
        }
        // 同步更新 Adapter 内部的 selected，否则 notifyDataSetChanged 后高亮仍指向旧索引
        apiAdapter.setSelected(currentApiIndex);
        apiAdapter.notifyDataSetChanged();
        // notifyDataSetChanged 会 rebinding 可见项，导致被点击按钮丢失焦点，
        // 系统默认把焦点移到第一个可聚焦控件。这里在布局完成后把焦点请求回点击位置。
        final int focusPos = currentApiIndex;
        apiList.post(() -> {
            RecyclerView.ViewHolder vh = apiList.findViewHolderForAdapterPosition(focusPos);
            if (vh != null) vh.itemView.requestFocus();
        });
        loading.setVisibility(View.VISIBLE);
        siteAdapter.updateData(new ArrayList<>());
        RepoApiLoader.loadSites(depot, new RepoApiLoader.Callback() {
            @Override
            public void onSuccess(@NonNull List<Site> sites) {
                if (!isAdded()) return;
                currentSites = sites;
                loading.setVisibility(View.GONE);
                siteAdapter.updateData(sites);
            }

            @Override
            public void onError(@NonNull String msg) {
                if (!isAdded()) return;
                loading.setVisibility(View.GONE);
                Notify.show(msg);
            }
        });
    }

    // ============== 站点点击：切换接口 + 设置首页源 ==============

    private void onSiteClicked(Site site) {
        // 找到当前选中的 Depot
        if (currentApis.isEmpty() || currentApiIndex >= currentApis.size()) return;
        Depot depot = currentApis.get(currentApiIndex);
        // 关闭弹窗
        dismissAllowingStateLoss();
        // 构造 Config 并加载
        Config config = Config.find(depot, VodConfig.VOD);
        VodConfig.load(config, new Callback() {
            @Override
            public void start() {
            }

            @Override
            public void success() {
                if (!VodConfig.get().getSites().isEmpty()) {
                    VodConfig.get().setHome(site);
                }
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
            }
        });
    }

    private void openManageDialog() {
        MultiRepoManageDialog.show(requireActivity(), () -> {
            // 管理后刷新
            loadRepos();
        });
    }

    // ============== Adapters ==============

    /** 顶部仓库名水平切换 Adapter */
    private static class RepoLabelAdapter extends RecyclerView.Adapter<RepoLabelAdapter.VH> {
        List<MultiRepo> items;
        int selected;
        final OnSelect onClick;

        RepoLabelAdapter(List<MultiRepo> items, int selected, OnSelect onClick) {
            this.items = items;
            this.selected = selected;
            this.onClick = onClick;
        }

        void updateData(List<MultiRepo> items, int selected) {
            this.items = items;
            this.selected = selected;
            notifyDataSetChanged();
        }

        interface OnSelect {
            void onSelect(int index);
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            MaterialButton btn = new MaterialButton(parent.getContext());
            btn.setAllCaps(false);
            btn.setGravity(Gravity.CENTER);
            btn.setMinHeight(ResUtil.dp2px(26));
            btn.setInsetTop(0);
            btn.setInsetBottom(0);
            btn.setInsetLeft(0);
            btn.setInsetRight(0);
            btn.setPadding(ResUtil.dp2px(3), ResUtil.dp2px(3), ResUtil.dp2px(3), ResUtil.dp2px(3));
            btn.setStrokeWidth(ResUtil.dp2px(1));
            btn.setCornerRadius(ResUtil.dp2px(6));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = ResUtil.dp2px(3);
            btn.setLayoutParams(lp);
            return new VH(btn);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            boolean isSel = position == selected;
            MaterialButton btn = holder.btn;
            btn.setText(items.get(position).getName());
            btn.setBackgroundTintList(ColorStateList.valueOf(isSel ? Color.parseColor("#E8F0FE") : Color.WHITE));
            btn.setStrokeColor(ColorStateList.valueOf(isSel ? Color.parseColor("#8AB4F8") : Color.parseColor("#DADCE0")));
            btn.setTextColor(ColorStateList.valueOf(isSel ? Color.parseColor("#174EA6") : Color.parseColor("#202124")));
            btn.setOnClickListener(v -> onClick.onSelect(position));
            btn.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus && position != selected) {
                    btn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#1A73E8")));
                    btn.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#174EA6")));
                    btn.setTextColor(ColorStateList.valueOf(Color.WHITE));
                } else {
                    // 恢复选中态或未选中态
                    btn.setBackgroundTintList(ColorStateList.valueOf(isSel ? Color.parseColor("#E8F0FE") : Color.WHITE));
                    btn.setStrokeColor(ColorStateList.valueOf(isSel ? Color.parseColor("#8AB4F8") : Color.parseColor("#DADCE0")));
                    btn.setTextColor(ColorStateList.valueOf(isSel ? Color.parseColor("#174EA6") : Color.parseColor("#202124")));
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final MaterialButton btn;
            VH(MaterialButton btn) { super(btn); this.btn = btn; }
        }
    }

    /** 左栏接口列表 Adapter */
    private static class ApiAdapter extends RecyclerView.Adapter<ApiAdapter.VH> {
        List<Depot> items;
        int selected;
        final OnSelectApi onClick;

        ApiAdapter(List<Depot> items, int selected, OnSelectApi onClick) {
            this.items = items;
            this.selected = selected;
            this.onClick = onClick;
        }

        void setSelected(int selected) {
            this.selected = selected;
        }

        void updateData(List<Depot> items, int selected) {
            this.items = items;
            this.selected = selected;
            notifyDataSetChanged();
        }

        interface OnSelectApi {
            void onSelect(Depot depot);
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            MaterialButton btn = new MaterialButton(parent.getContext());
            btn.setAllCaps(false);
            btn.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            btn.setSingleLine(false);
            btn.setMinHeight(ResUtil.dp2px(26));
            btn.setInsetTop(0);
            btn.setInsetBottom(0);
            btn.setInsetLeft(0);
            btn.setInsetRight(0);
            btn.setPadding(ResUtil.dp2px(3), ResUtil.dp2px(3), ResUtil.dp2px(3), ResUtil.dp2px(3));
            btn.setStrokeWidth(ResUtil.dp2px(1));
            btn.setCornerRadius(ResUtil.dp2px(6));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = ResUtil.dp2px(6);
            btn.setLayoutParams(lp);
            return new VH(btn);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            boolean isSel = position == selected;
            MaterialButton btn = holder.btn;
            btn.setText(items.get(position).getName());
            btn.setBackgroundTintList(ColorStateList.valueOf(isSel ? Color.parseColor("#E8F0FE") : Color.WHITE));
            btn.setStrokeColor(ColorStateList.valueOf(isSel ? Color.parseColor("#8AB4F8") : Color.parseColor("#DADCE0")));
            btn.setTextColor(ColorStateList.valueOf(isSel ? Color.parseColor("#174EA6") : Color.parseColor("#202124")));
            btn.setOnClickListener(v -> onClick.onSelect(items.get(position)));
            btn.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus && position != selected) {
                    btn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#1A73E8")));
                    btn.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#174EA6")));
                    btn.setTextColor(ColorStateList.valueOf(Color.WHITE));
                } else {
                    // 恢复选中态或未选中态
                    btn.setBackgroundTintList(ColorStateList.valueOf(isSel ? Color.parseColor("#E8F0FE") : Color.WHITE));
                    btn.setStrokeColor(ColorStateList.valueOf(isSel ? Color.parseColor("#8AB4F8") : Color.parseColor("#DADCE0")));
                    btn.setTextColor(ColorStateList.valueOf(isSel ? Color.parseColor("#174EA6") : Color.parseColor("#202124")));
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final MaterialButton btn;
            VH(MaterialButton btn) { super(btn); this.btn = btn; }
        }
    }

    /** 右栏站点网格 Adapter */
    private static class SiteGridAdapter extends RecyclerView.Adapter<SiteGridAdapter.VH> {
        List<Site> items;
        final OnClickSite onClick;

        SiteGridAdapter(List<Site> items, OnClickSite onClick) {
            this.items = items;
            this.onClick = onClick;
        }

        void updateData(List<Site> items) {
            this.items = items;
            notifyDataSetChanged();
        }

        interface OnClickSite {
            void onSelect(Site site);
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            MaterialButton btn = new MaterialButton(parent.getContext());
            btn.setAllCaps(false);
            btn.setGravity(Gravity.CENTER);
            btn.setMinHeight(ResUtil.dp2px(26));
            btn.setInsetTop(0);
            btn.setInsetBottom(0);
            btn.setInsetLeft(0);
            btn.setInsetRight(0);
            btn.setPadding(ResUtil.dp2px(3), ResUtil.dp2px(3), ResUtil.dp2px(3), ResUtil.dp2px(3));
            btn.setStrokeWidth(ResUtil.dp2px(1));
            btn.setCornerRadius(ResUtil.dp2px(6));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = ResUtil.dp2px(4);
            lp.bottomMargin = ResUtil.dp2px(6);
            btn.setLayoutParams(lp);
            return new VH(btn);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Site site = items.get(position);
            MaterialButton btn = holder.btn;
            btn.setText(site.getName());
            btn.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
            btn.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#DADCE0")));
            btn.setTextColor(ColorStateList.valueOf(Color.parseColor("#202124")));
            btn.setOnClickListener(v -> onClick.onSelect(site));
            btn.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    btn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#1A73E8")));
                    btn.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#174EA6")));
                    btn.setTextColor(ColorStateList.valueOf(Color.WHITE));
                } else {
                    btn.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
                    btn.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#DADCE0")));
                    btn.setTextColor(ColorStateList.valueOf(Color.parseColor("#202124")));
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final MaterialButton btn;
            VH(MaterialButton btn) { super(btn); this.btn = btn; }
        }
    }
}
