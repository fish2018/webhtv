package com.fongmi.android.tv.ui.dialog;

import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.AdapterSyncPathDirBinding;
import com.fongmi.android.tv.databinding.DialogSyncPathBinding;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.SyncFiles;
import com.github.catvod.utils.Path;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SyncPathDialog extends BaseAlertDialog {

    private static final int TREE_LIMIT = 300;
    private static final int INDENT_DP = 18;

    private final Set<String> selected = new LinkedHashSet<>();
    private final Set<String> expanded = new LinkedHashSet<>();
    private final List<Item> items = new ArrayList<>();

    private DialogSyncPathBinding binding;
    private DirAdapter adapter;
    private Runnable callback;

    public static void show(Fragment fragment, Runnable callback) {
        SyncPathDialog dialog = new SyncPathDialog();
        dialog.callback = callback;
        dialog.show(fragment.getChildFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogSyncPathBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return new MaterialAlertDialogBuilder(requireActivity(), R.style.Theme_WebHTV_LightDialog).setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        selected.addAll(SyncFiles.getPaths(Setting.getSyncPaths()));
        expanded.add("");
        selected.forEach(this::expandPath);
        binding.recycler.setHasFixedSize(false);
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 4));
        binding.recycler.setAdapter(adapter = new DirAdapter());
        rebuild();
    }

    @Override
    protected void initEvent() {
        binding.up.setOnClickListener(v -> collapseAll());
        binding.refresh.setOnClickListener(v -> rebuild());
        binding.reset.setOnClickListener(v -> reset());
        binding.negative.setOnClickListener(v -> dismiss());
        binding.positive.setOnClickListener(v -> onPositive());
    }

    @Override
    public void onStart() {
        super.onStart();
        setWidth(ResUtil.isLand(requireContext()) ? 0.52f : 0.92f);
    }

    private void rebuild() {
        items.clear();
        appendChildren("", 0);
        adapter.notifyDataSetChanged();
        updateState();
    }

    private void appendChildren(String path, int depth) {
        File root;
        File dir;
        try {
            root = Path.root().getCanonicalFile();
            dir = path.isEmpty() ? root : new File(root, path).getCanonicalFile();
            if (!inside(root, dir) || !dir.isDirectory()) return;
            File[] files = dir.listFiles(file -> !file.getName().startsWith("."));
            if (files != null) {
                Arrays.sort(files, (a, b) -> {
                    if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                    return a.getName().compareToIgnoreCase(b.getName());
                });
            }
            int count = 0;
            for (File file : files == null ? new File[0] : files) {
                if (count++ >= TREE_LIMIT) break;
                String child = relativeTo(root, file);
                if (child.isEmpty()) continue;
                boolean isDir = file.isDirectory();
                Item item = new Item(file.getName(), child, depth, isDir && hasSubDirs(file), !isDir);
                items.add(item);
                if (isDir && expanded.contains(child)) appendChildren(child, depth + 1);
            }
        } catch (Exception ignored) {
        }
    }

    private boolean hasSubDirs(File dir) {
        File[] files = dir.listFiles(file -> file.isDirectory() && !file.getName().startsWith("."));
        return files != null && files.length > 0;
    }

    private void collapseAll() {
        expanded.clear();
        expanded.add("");
        selected.forEach(this::expandPath);
        rebuild();
    }

    private void reset() {
        selected.clear();
        collapseAll();
    }

    private void toggle(String path) {
        path = SyncFiles.normalize(path);
        if (path.isEmpty()) return;
        if (selected.contains(path)) selected.remove(path);
        else selected.add(path);
        rebuild();
    }

    private void toggleExpanded(String path) {
        path = SyncFiles.normalize(path);
        if (expanded.contains(path)) expanded.remove(path);
        else expanded.add(path);
        rebuild();
    }

    private void expandPath(String path) {
        path = SyncFiles.normalize(path);
        while (!path.isEmpty()) {
            int index = path.lastIndexOf('/');
            path = index < 0 ? "" : path.substring(0, index);
            expanded.add(path);
        }
    }

    private void onPositive() {
        Setting.putSyncPaths(SyncFiles.getPathsText(new ArrayList<>(selected)));
        if (callback != null) callback.run();
        dismiss();
    }

    private void updateState() {
        binding.current.setText("/");
        binding.up.setEnabled(expanded.size() > 1);
        binding.recycler.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        binding.empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        List<String> paths = SyncFiles.getPaths(SyncFiles.getPathsText(new ArrayList<>(selected)));
        selected.clear();
        selected.addAll(paths);
        binding.summary.setText(paths.isEmpty() ? getString(R.string.sync_paths_selected_empty) : getString(R.string.sync_paths_selected, TextUtils.join(", ", paths)));
    }

    // ============ 方案A新增：前缀匹配判断子项是否被父目录选中 ============
    private boolean isPathSelected(String childPath) {
        for (String sel : selected) {
            if (sel.isEmpty()) continue;
            if (childPath.equals(sel) || childPath.startsWith(sel + "/")) {
                return true;
            }
        }
        return false;
    }

    private boolean inside(File root, File file) throws IOException {
        String rootPath = root.getCanonicalPath();
        String filePath = file.getCanonicalPath();
        return filePath.equals(rootPath) || filePath.startsWith(rootPath + File.separator);
    }

    private String relativeTo(File root, File file) throws IOException {
        return root.toPath().relativize(file.getCanonicalFile().toPath()).toString().replace(File.separatorChar, '/');
    }

    private static class Item {

        private final String name;
        private final String path;
        private final int depth;
        private final boolean hasChildren;
        private final boolean isFile;

        private Item(String name, String path, int depth, boolean hasChildren, boolean isFile) {
            this.name = name;
            this.path = path;
            this.depth = depth;
            this.hasChildren = hasChildren;
            this.isFile = isFile;
        }
    }

    private class DirAdapter extends RecyclerView.Adapter<DirAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(AdapterSyncPathDirBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(items.get(position));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        private class ViewHolder extends RecyclerView.ViewHolder {

            private final AdapterSyncPathDirBinding binding;

            private ViewHolder(@NonNull AdapterSyncPathDirBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }

            private void bind(Item item) {
                ViewGroup.MarginLayoutParams iconParams = (ViewGroup.MarginLayoutParams) binding.icon.getLayoutParams();
                iconParams.leftMargin = ResUtil.dp2px(4 + item.depth * INDENT_DP);
                binding.icon.setLayoutParams(iconParams);
                binding.name.setText(item.name);
                binding.path.setText(item.path);
                // 【改动点】由前缀匹配代替直接 contains 判断
                binding.check.setChecked(isPathSelected(item.path));
                binding.icon.setImageResource(item.isFile ? R.drawable.ic_file : R.drawable.ic_folder);
                binding.enter.setVisibility(item.isFile ? View.INVISIBLE : (item.hasChildren ? View.VISIBLE : View.INVISIBLE));
                binding.enter.setImageResource(expanded.contains(item.path) ? R.drawable.ic_detail_minus : R.drawable.ic_detail_plus);
                binding.enter.setOnClickListener(v -> {
                    if (item.hasChildren) toggleExpanded(item.path);
                });
                binding.getRoot().setOnClickListener(v -> toggle(item.path));
                binding.getRoot().setOnKeyListener((v, keyCode, event) -> {
                    if (event.getAction() != KeyEvent.ACTION_DOWN || keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
                    if (!item.isFile && item.hasChildren) toggleExpanded(item.path);
                    return true;
                });
            }
        }
    }
}
