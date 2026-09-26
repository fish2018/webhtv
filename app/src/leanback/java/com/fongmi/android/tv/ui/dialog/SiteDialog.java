package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.Toast;
import java.io.File;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.DialogSiteBinding;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.impl.SiteListener;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.ui.adapter.SiteAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.FocusLoop;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.utils.Path;
import com.github.catvod.crawler.SpiderDebug;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SiteDialog extends BaseGlassDialog implements SiteAdapter.OnClickListener, SiteAdapter.OnDeleteListener {

    private static final int GRID_COUNT = 3;
    private static final String TAG = "site_dialog";
    private static final int ITEM_HEIGHT = 46;
    private static final int ITEM_SPACE = 12;
    private static final int MAX_HEIGHT = 344;
    private static final int INITIAL_BATCH = 48;

    private RecyclerView.ItemDecoration decoration;
    private DialogSiteBinding binding;
    private SiteListener listener;
    private SiteAdapter adapter;
    private long showStart;
    private boolean action;
    private boolean listLoaded;
    private int type;

    public static SiteDialog create() {
        return new SiteDialog();
    }

    public SiteDialog setListener(SiteListener listener) {
        this.listener = listener;
        return this;
    }

    public SiteDialog search() {
        type = 1;
        return this;
    }

    public SiteDialog action() {
        action = true;
        return this;
    }

    public void show(FragmentActivity activity) {
        showStart = System.currentTimeMillis();
        if (activity.isFinishing() || activity.isDestroyed()) return;
        log("click received action=%s type=%s", action, type);
        show(activity.getSupportFragmentManager(), TAG);
    }

    private int getCount() {
        return GRID_COUNT;
    }

    @Override
    protected float getWidthRatio() {
        return action ? 0.92f : 0.9f;
    }

    @Override
    protected boolean onMenuKey() {
        SiteSortDialog.create().setListener(() -> {
            // 排序完成后刷新站点列表并再次聚焦到当前站
            if (adapter != null) {
                adapter.refresh();
                binding.recycler.post(() -> scrollAndFocusActiveSite());
            }
        }).show(getActivity());
        return false;
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogSiteBinding.inflate(getLayoutInflater());
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (adapter != null && FocusLoop.handleRecyclerGrid(binding.recycler, adapter.getTotalCount(), GRID_COUNT, FocusLoop.Mode.BOTH, event))
                return true;
            if (KeyUtil.isMenuKey(event) && onMenuKey()) {
                dismiss();
                return true;
            }
            return false;
        });
        dialog.setOnShowListener(d -> {
            if (listLoaded) waitForLayoutAndFocus();
        });
        return dialog;
    }

    @Override
    protected void initView() {
        initShellView();
        loadList(true);
    }

    private void initShellView() {
        long start = System.currentTimeMillis();
        updateWindowWidth();
        setRecyclerHeight(INITIAL_BATCH);
        binding.searchBar.setVisibility(View.GONE);
        binding.keyword.setVisibility(View.GONE);
        binding.actionGap.setVisibility(View.GONE);
        binding.action.setVisibility(action ? View.VISIBLE : View.GONE);
        binding.search.setVisibility(action ? View.VISIBLE : View.GONE);
        binding.change.setVisibility(action ? View.VISIBLE : View.GONE);
        binding.select.setVisibility(action ? View.VISIBLE : View.GONE);
        binding.cancel.setVisibility(action ? View.VISIBLE : View.GONE);
        binding.mode.setVisibility(View.GONE);
        setActionEnabled(false);
        binding.recycler.setAdapter(null);
        binding.recycler.setItemAnimator(null);
        binding.recycler.setHasFixedSize(true);
        log("shell configured cost=%sms total=%sms", cost(start), cost());
    }

    private void loadList(boolean immediate) {
        if (binding == null || listLoaded) return;
        listLoaded = true;
        long start = System.currentTimeMillis();
        adapter = new SiteAdapter(this);
        adapter.setDisplayLimit(INITIAL_BATCH);
        log("adapter created cost=%sms items=%s action=%s immediate=%s", cost(start), adapter.getTotalCount(), action, immediate);
        if (adapter.getTotalCount() == 0) {
            log("dismiss empty total=%sms", cost());
            dismiss();
            return;
        }
        long layoutStart = System.currentTimeMillis();
        setType(type);
        setRecyclerView();
        setRecyclerHeight(adapter.getItemCount());
        setMode();
        setActionEnabled(true);
        log("view configured cost=%sms total=%sms", cost(layoutStart), cost());
        runAfterRecyclerLayout(() -> {
            if (adapter != null) adapter.showAll();
            log("list expanded total=%sms items=%s", cost(), adapter == null ? -1 : adapter.getItemCount());
        });
    }

    private void waitForLayoutAndFocus() {
        if (binding == null || binding.recycler == null) return;
        binding.recycler.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (adapter == null || adapter.getItemCount() < adapter.getTotalCount()) {
                    return;
                }
                ViewTreeObserver obs = binding.recycler.getViewTreeObserver();
                if (obs.isAlive()) obs.removeOnGlobalLayoutListener(this);
                scrollAndFocusActiveSite();
            }
        });
    }

    private boolean mScrolledToActive = false;

    private void scrollAndFocusActiveSite() {
        if (mScrolledToActive || adapter == null || binding == null) return;
        List<Site> showList = adapter.getItems();
        Site active = VodConfig.get().getHome();
        if (active == null || active.getKey() == null || active.getKey().isEmpty()) return;
        int targetPos = -1;
        for (int i = 0; i < showList.size(); i++) {
            if (showList.get(i).getKey().equals(active.getKey())) {
                targetPos = i;
                break;
            }
        }
        RecyclerView.LayoutManager lm = binding.recycler.getLayoutManager();
        if (targetPos < 0 || !(lm instanceof GridLayoutManager glm)) return;
        mScrolledToActive = true;
        final int finalTargetPos = targetPos;
        binding.recycler.post(() -> {
            if (binding == null || binding.recycler == null || adapter == null) return;
            int height = binding.recycler.getHeight();
            if (height <= 0) {
                mScrolledToActive = false;
                binding.recycler.postDelayed(() -> scrollAndFocusActiveSite(), 50);
                return;
            }
            int itemHeight = ResUtil.dp2px(ITEM_HEIGHT) + ResUtil.dp2px(ITEM_SPACE);
            int centerOffset = Math.max(0, (height - itemHeight) / 2);
            glm.scrollToPositionWithOffset(finalTargetPos, centerOffset);
            binding.recycler.postDelayed(() -> {
                if (binding == null || binding.recycler == null) return;
                RecyclerView.ViewHolder holder = binding.recycler.findViewHolderForAdapterPosition(finalTargetPos);
                if (holder != null && holder.itemView != null) {
                    holder.itemView.requestFocus();
                    log("success request focus pos=" + finalTargetPos);
                }
            }, 50);
        });
    }

    @Override
    protected void initEvent() {
        binding.config.setOnClickListener(v -> {
            FragmentActivity activity = requireActivity();
            dismiss();
            App.post(() -> HistoryDialog.create().vod().readOnly().show(activity, item -> loadConfig(activity, item)), 100);
        });
        binding.mode.setOnClickListener(this::onMode);
        binding.select.setOnClickListener(v -> {
            if (adapter != null) adapter.selectAll();
        });
        binding.cancel.setOnClickListener(v -> {
            if (adapter != null) adapter.cancelAll();
        });
        binding.search.setOnClickListener(v -> setType(v.isSelected() ? 0 : 1));
        binding.change.setOnClickListener(v -> setType(v.isSelected() ? 0 : 2));
        binding.keyword.addTextChangedListener(new com.fongmi.android.tv.ui.custom.CustomTextListener() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (adapter == null) return;
                adapter.filter(s.toString());
                setRecyclerView();
                setRecyclerHeight(adapter.getItemCount());
                setMode();
                updateWindowWidth();
                scrollAndFocusActiveSite();
            }
        });
    }

    private void setRecyclerView() {
        if (binding.recycler.getAdapter() == null) binding.recycler.setAdapter(adapter);
        binding.recycler.setHasFixedSize(true);
        binding.recycler.setItemAnimator(null);
        if (decoration == null) binding.recycler.addItemDecoration(decoration = new SpaceItemDecoration(getCount(), 16));
        if (binding.recycler.getLayoutManager() == null) binding.recycler.setLayoutManager(new GridLayoutManager(requireActivity(), getCount()));
        log("recycler ready adapter=%s layout=%s total=%sms", binding.recycler.getAdapter() != null, binding.recycler.getLayoutManager() != null, cost());
    }

    private void setRecyclerHeight(int count) {
        int rows = Math.max(1, (int) Math.ceil((double) Math.max(1, count) / getCount()));
        int height = rows * ResUtil.dp2px(ITEM_HEIGHT) + Math.max(0, rows - 1) * ResUtil.dp2px(ITEM_SPACE) + binding.recycler.getPaddingTop() + binding.recycler.getPaddingBottom();
        ViewGroup.LayoutParams params = binding.recycler.getLayoutParams();
        params.height = Math.min(height, ResUtil.dp2px(MAX_HEIGHT));
        binding.recycler.setLayoutParams(params);
    }

    private void setType(int type) {
        binding.search.setSelected(type == 1);
        binding.change.setSelected(type == 2);
        binding.select.setClickable(type > 0);
        binding.cancel.setClickable(type > 0);
        this.type = type;
        if (adapter != null) adapter.setType(type);
        setActionEnabled(listLoaded && adapter != null);
    }

    private void setMode() {
        binding.mode.setEnabled(false);
    }

    private void onMode(View view) {
        setRecyclerView();
        setMode();
        updateWindowWidth();
    }

    private void setActionEnabled(boolean enabled) {
        binding.search.setEnabled(enabled);
        binding.change.setEnabled(enabled);
        binding.select.setEnabled(enabled && type > 0);
        binding.cancel.setEnabled(enabled && type > 0);
    }

    @Override
    public void onItemClick(Site item) {
        if (listener != null) listener.setSite(item);
        dismiss();
    }

    @Override
    public void onDelete(Site item) {
        if (!item.isFile()) return;
        FragmentActivity act = requireActivity();
        if (act == null || act.isFinishing() || act.isDestroyed()) {
            return;
        }
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(act)
                .setTitle(R.string.setting_site_delete_title)
                .setMessage(getString(R.string.setting_site_delete_message, item.getName()))
                .setPositiveButton(android.R.string.ok, (d, which) -> deleteFileSite(item))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(glassBackground());
            View content = w.getDecorView().findViewById(android.R.id.content);
            if (content != null) content.setBackground(null);
        }
    }

    private void deleteFileSite(Site item) {
        String type = item.getFileType();
        String fileName = item.getFileName();
        if (fileName.isEmpty()) return;
        String subDir = switch (type) {
            case "XBPQ" -> "sites-json";
            case "JS" -> "sites-js/api";
            case "PY" -> "sites-py";
            case "RAW" -> "sites";
            default -> "";
        };
        if (subDir.isEmpty()) return;
        File file = new File(Path.root() + "/tvbox/" + subDir, fileName);
        boolean deleted = file.exists() && file.delete();
        if (!deleted && file.exists()) {
            Toast.makeText(requireActivity(), R.string.setting_site_delete_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        Site currentHome = VodConfig.get().getHome();
        List<Site> siteList = VodConfig.get().getSites();
        if (currentHome != null && currentHome.getKey().equals(item.getKey())) {
            int delIndex = -1;
            for (int i = 0; i < siteList.size(); i++) {
                if (siteList.get(i).getKey().equals(item.getKey())) {
                    delIndex = i;
                    break;
                }
            }
            Site fallbackHome = new Site();
            if (delIndex != -1 && siteList.size() > 1) {
                if (delIndex + 1 < siteList.size()) {
                    fallbackHome = siteList.get(delIndex + 1);
                } else {
                    fallbackHome = siteList.get(delIndex - 1);
                }
            }
            VodConfig.get().setHome(fallbackHome);
        }
        if (!siteList.isEmpty()) siteList.remove(item);
        Toast.makeText(requireActivity(), getString(R.string.setting_site_delete_done, item.getName()), Toast.LENGTH_SHORT).show();
        if (adapter != null && binding != null) {
            adapter.removeSite(item);
            setRecyclerHeight(adapter.getItemCount());
            binding.recycler.post(this::scrollAndFocusActiveSite);
        }
    }

    private void loadConfig(FragmentActivity activity, Config config) {
        if (config.getUrl().equals(VodConfig.getUrl())) return;
        VodConfig.load(config, new Callback() {
            @Override
            public void start() {
                Notify.progress(activity);
            }

            @Override
            public void success() {
                Notify.dismiss();
                LiveConfig.get().clear();
            }

            @Override
            public void error(String msg) {
                Notify.dismiss();
                Notify.show(msg);
            }
        });
    }

    private void runAfterRecyclerLayout(Runnable action) {
        if (binding == null || binding.recycler == null) {
            if (action != null) action.run();
            return;
        }
        binding.recycler.post(action);
    }

    private long cost() {
        return cost(showStart);
    }

    private long cost(long start) {
        return System.currentTimeMillis() - start;
    }

    private void log(String msg, Object... args) {
        if (!SpiderDebug.isEnabled()) return;
        SpiderDebug.log(TAG, msg, args);
    }

    @Override
    public void dismiss() {
        super.dismiss();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (adapter != null && adapter.getItemCount() == 0) dismiss();
        if (binding != null && binding.recycler != null && adapter != null) {
            binding.recycler.postDelayed(this::scrollAndFocusActiveSite, 50);
        }
    }
}
