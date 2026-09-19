package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.media3.mpvplayer.MpvPlayer;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.databinding.DialogDiscMenuBinding;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.bottomsheet.BottomSheetDialog;

public final class DiscMenuDialog extends BaseBottomSheetDialog {

    private static final String TAG = "disc-menu-controls";

    private DialogDiscMenuBinding binding;
    private MpvPlayer player;

    public static void show(FragmentActivity activity, MpvPlayer player) {
        if (activity.getSupportFragmentManager().isStateSaved()
                || activity.getSupportFragmentManager().findFragmentByTag(TAG) != null) return;
        DiscMenuDialog dialog = new DiscMenuDialog();
        dialog.player = player;
        dialog.show(activity.getSupportFragmentManager(), TAG);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogDiscMenuBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        if (player == null || !player.isDiscMenuAvailable()) {
            dismiss();
            return;
        }
        binding.up.setOnClickListener(view -> player.sendDiscNav("up"));
        binding.down.setOnClickListener(view -> player.sendDiscNav("down"));
        binding.left.setOnClickListener(view -> player.sendDiscNav("left"));
        binding.right.setOnClickListener(view -> player.sendDiscNav("right"));
        binding.select.setOnClickListener(view -> activate("select"));
        binding.rootMenu.setOnClickListener(view -> activate("menu"));
        binding.popup.setOnClickListener(view -> activate("popup"));
        binding.previous.setOnClickListener(view -> activate("prev"));
        binding.close.setOnClickListener(view -> dismiss());
    }

    private void activate(String action) {
        if (player.sendDiscNav(action)) dismiss();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        Window window = dialog.getWindow();
        if (window != null) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0f);
        }
        return dialog;
    }

    @Override
    protected void setBehavior(BottomSheetDialog dialog) {
        super.setBehavior(dialog);
        FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet != null) {
            sheet.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            ViewGroup.LayoutParams params = sheet.getLayoutParams();
            params.width = Math.min(ResUtil.dp2px(384),
                    getResources().getDisplayMetrics().widthPixels - ResUtil.dp2px(24));
            if (params instanceof CoordinatorLayout.LayoutParams layout) {
                layout.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                layout.bottomMargin = ResUtil.dp2px(8);
            }
            sheet.setLayoutParams(params);
        }
        Window window = dialog.getWindow();
        if (window != null && getActivity() != null && Util.isFullscreen(getActivity()))
            Util.hideSystemUI(window);
    }
}
