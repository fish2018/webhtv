package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
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
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.utils.Path;
import com.github.catvod.crawler.SpiderDebug;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SiteDialog extends BaseAlertDialog implements SiteAdapter.OnClickListener, SiteAdapter.OnDeleteListener {

    private static final int GRID_COUNT = 3;
    private static final String TAG = "site_dialog";
    private static final int ITEM_HEIGHT = 46;
    private static final int ITEM_SPACE = 12;
    private static final int MAX_HEIGHT = 344;
    private static final int INITIAL_BATCH = 48;

    private RecyclerView.ItemDecoration decoration;
    private DialogSiteBinding binding;
    private FragmentActivity activity;
    private Dialog directDialog;
    private SiteListener listener;
    private SiteAdapter adapter;
    private long showStart;
    private boolean action;
    private boolean listLoaded;
    private int type;

    public static SiteDialog create() {
        return new SiteDialog();
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
        this.activity = activity;
        if (activity instanceof SiteListener) listener = (SiteListener) activity;
        if (activity.isFinishing() || activity.isDestroyed()) return;
        log("click received action=%s type=%s", action, type);
        showDirect(activity);
    }

    private int getCount() {
        return GRID_COUNT;
    }

    private float getWidth() {
        return action ? 0.92f : 0.9f;
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogSiteBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = new Dialog(requireActivity());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(getBinding().getRoot());
        initView();
        initEvent();
        return dialog;
    }

    private void showDirect(FragmentActivity activity) {
        long start = System.currentTimeMillis();
        log("inflate start");
        binding = DialogSiteBinding.inflate(activity.getLayoutInflater());
        log("inflate end cost=%sms", cost(start));
        long dialogStart = System.currentTimeMillis();
        directDialog = new Dialog(activity);
        directDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        directDialog.setContentView(binding.getRoot());
        log("dialog content ready cost=%sms total=%sms", cost(dialogStart), cost());
        long initStart = System.currentTimeMillis();
        initShellView();
        initEvent();
        log("shell init end cost=%sms total=%sms", cost(initStart), cost());
        directDialog.setOnDismissListener(d -> {
            directDialog = null;
            binding = null;
            this.activity = null;
        });

        directDialog.setOnShowListener(dialogInterface -> {
            if (binding == null || adapter == null) return;
            binding.recycler.post(() -> {
                binding.recycler.post(() -> {
                    List<Site> showList = adapter.getItems();
                    Site active = VodConfig.get().getHome();
                    int targetPos = -1;
                    for (int i = 0; i < showList.size(); i++) {
                        if (showList.get(i).getKey().equals(active.getKey())) {
                            targetPos = i;
                            break;
                        }
                    }
                    if (targetPos < 0) return;
                    RecyclerView.ViewHolder holder = binding.recycler.findViewHolderForAdapterPosition(targetPos);
                    if (holder != null && holder.itemView != null) {
                        boolean ret = holder.itemView.requestFocus();
                        log("onShow real request focus pos=%d ret=%b", targetPos, ret);
                    } else {
                        log("onShow holder null pos=%d", targetPos);
                    }
                });
            });
        });

        runAfterFirstPreDraw("shell preDraw", () -> loadList(false));
        long showDialogStart = System.currentTimeMillis();
        log("show call start total=%sms", cost());
        directDialog.show();
        log("show call end cost=%sms total=%sms", cost(showDialogStart), cost());
        applyWindow(directDialog.getWindow());
        log("window applied total=%sms", cost());
    }

    @Override
    protected void initView() {
        initShellView();
        loadList(true);
    }

    private void initShellView() {
        long start = System.currentTimeMillis();
        setRootWidth();
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
        runAfterFirstPreDraw("list preDraw", () -> {
            if (adapter != null) adapter.showAll();
            log("list expanded total=%sms items=%s", cost(), adapter == null ? -1 : adapter.getItemCount());
            if (adapter == null || binding == null) return;
            List<Site> showList = adapter.getItems();
            Site active = VodConfig.get().getHome();
            int targetPos = -1;
            for (int i = 0; i < showList.size(); i++) {
                if (showList.get(i).getKey().equals(active.getKey())) {
                    targetPos = i;
                    break;
                }
            }
            RecyclerView.LayoutManager lm = binding.recycler.getLayoutManager();
            if (targetPos < 0) {
                binding.recycler.scrollToPosition(0);
            } else if (lm instanceof GridLayoutManager glm) {
                int recyclerHeight = binding.recycler.getHeight();
                int offset = recyclerHeight / 2;
                glm.scrollToPositionWithOffset(targetPos, offset);
            }
        });
    }

    @Override
    protected void initEvent() {
        binding.config.setOnClickListener(v -> {
            FragmentActivity activity = getDialogActivity();
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
                setWidth();
            }
        });
    }

    private void setRecyclerView() {
        if (binding.recycler.getAdapter() == null) binding.recycler.setAdapter(adapter);
        binding.recycler.setHasFixedSize(true);
        binding.recycler.setItemAnimator(null);
        if (decoration == null) binding.recycler.addItemDecoration(decoration = new SpaceItemDecoration(getCount(), 16));
        if (binding.recycler.getLayoutManager() == null) binding.recycler.setLayoutManager(new GridLayoutManager(getDialogActivity(), getCount()));
        log("recycler ready adapter=%s layout=%s total=%sms", binding.recycler.getAdapter() != null, binding.recycler.getLayoutManager() != null, cost());
    }

    private void setRecyclerHeight(int count) {
        int rows = Math.max(1, (int) Math.ceil((double) Math.max(1, count) / getCount()));
        int height = rows * ResUtil.dp2px(ITEM_HEIGHT) + Math.max(0, rows - 1) * ResUtil.dp2px(ITEM_SPACE) + binding.recycler.getPaddingTop() + binding.recycler.getPaddingBottom();
        ViewGroup.LayoutParams params = binding.recycler.getLayoutParams();
        params.height = Math.min(height, ResUtil.dp2px(MAX_HEIGHT));
        binding.recycler.setLayoutParams(params);
    }

    private void setRootWidth() {
        ViewGroup.LayoutParams params = binding.getRoot().getLayoutParams();
        if (params == null) params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.width = (int) (ResUtil.getScreenWidth() * getWidth());
        binding.getRoot().setLayoutParams(params);
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

    private void setWidth() {
        setWidth(getWidth());
    }

    private void onMode(View view) {
        setRecyclerView();
        setMode();
        setWidth();
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
        new MaterialAlertDialogBuilder(getDialogActivity())
                .setTitle(R.string.setting_site_delete_title)
                .setMessage(getString(R.string.setting_site_delete_message, item.getName()))
                .setPositiveButton(android.R.string.ok, (dialog, which) -> deleteFileSite(item))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
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
        if (file.exists()) file.delete();
        if (activity != null) Toast.makeText(activity, getString(R.string.setting_site_delete_done, item.getName()), Toast.LENGTH_SHORT).show();
        dismiss();
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

    private FragmentActivity getDialogActivity() {
        return activity != null ? activity : requireActivity();
    }

    private void applyWindow(Window window) {
        if (window == null) return;
        window.setWindowAnimations(0);
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = (int) (ResUtil.getScreenWidth() * getWidth());
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        window.setAttributes(params);
    }

    private void runAfterFirstPreDraw(String label, Runnable action) {
        View root = binding == null ? null : binding.getRoot();
        if (root == null) {
            if (action != null) action.run();
            return;
        }
        root.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (root.getViewTreeObserver().isAlive()) root.getViewTreeObserver().removeOnPreDrawListener(this);
                log("%s total=%sms items=%s", label, cost(), adapter == null ? -1 : adapter.getItemCount());
                if (action != null) root.post(action);
                return true;
            }
        });
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
        if (directDialog != null) directDialog.dismiss();
        else super.dismiss();
    }

    @Override
    public void onStart() {
        super.onStart();
        Window window = getDialog() == null ? null : getDialog().getWindow();
        applyWindow(window);
        if (adapter != null && adapter.getItemCount() == 0) dismiss();
    }
}
