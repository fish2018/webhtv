package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.databinding.DialogDownloadEpisodeBinding;
import com.fongmi.android.tv.ui.activity.DownloadListActivity;
import com.fongmi.android.tv.ui.adapter.DownloadEpisodeAdapter;
import com.fongmi.android.tv.ui.dialog.OnDownload;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 劇集多選下載彈窗，以浮層形式覆蓋在播放頁上方。
 */
public class DownloadEpisodeDialog extends AppCompatDialogFragment implements DownloadEpisodeAdapter.OnSelectListener {

    private DialogDownloadEpisodeBinding binding;
    private DownloadEpisodeAdapter adapter;
    private List<Episode> episodes;
    private Set<Integer> downloaded;
    private OnDownload listener;

    public static void show(FragmentActivity activity, List<Episode> episodes, Set<Integer> downloaded, OnDownload listener) {
        if (episodes == null || episodes.isEmpty()) return;
        DownloadEpisodeDialog dialog = new DownloadEpisodeDialog();
        dialog.episodes = episodes;
        dialog.downloaded = downloaded == null ? new HashSet<>() : downloaded;
        dialog.listener = listener;
        dialog.show(activity.getSupportFragmentManager(), null);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        configureWindow(dialog);
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        configureWindow(getDialog());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DialogDownloadEpisodeBinding.inflate(inflater, container, false);
        FrameLayout overlay = new FrameLayout(requireContext());
        overlay.setBackgroundColor(Color.TRANSPARENT);
        overlay.setOnClickListener(v -> dismiss());
        binding.getRoot().setClickable(true);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, getHeight(), Gravity.BOTTOM);
        overlay.addView(binding.getRoot(), params);
        return overlay;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setRecyclerView();
    }

    private int getHeight() {
        int screen = ResUtil.getScreenHeight(requireContext());
        return Math.max(ResUtil.dp2px(240), Math.round(screen * 0.45f));
    }

    private void configureWindow(Dialog dialog) {
        if (dialog == null || dialog.getWindow() == null) return;
        Window window = dialog.getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.setDimAmount(0f);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        Util.hideSystemUI(window);
    }

    private void setRecyclerView() {
        binding.episode.setHasFixedSize(true);
        binding.episode.setItemAnimator(null);
        binding.episode.setAdapter(adapter = new DownloadEpisodeAdapter(this));
        adapter.setItems(episodes, downloaded);
        binding.downloadList.setOnClickListener(v -> {
            DownloadListActivity.start(requireActivity());
            dismiss();
        });
        binding.selectAll.setOnClickListener(v -> {
            if (adapter.isAllSelected()) adapter.selectNone();
            else adapter.selectAll();
        });
        binding.download.setOnClickListener(v -> {
            List<Episode> selected = adapter.getSelected();
            if (selected.isEmpty()) return;
            listener.onDownload(selected);
            dismiss();
            DownloadListActivity.start(requireActivity());
        });
    }

    @Override
    public void onSelectChange(int count) {
        binding.selectAll.setText(adapter.isAllSelected() ? R.string.download_select_none : R.string.download_select_all);
        binding.download.setText(count == 0 ? getString(R.string.download) : getString(R.string.download) + " (" + count + ")");
    }
}
