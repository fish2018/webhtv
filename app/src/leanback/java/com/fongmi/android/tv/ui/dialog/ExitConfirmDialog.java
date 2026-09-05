package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogExitConfirmBinding;
import com.fongmi.android.tv.utils.ResUtil;

public final class ExitConfirmDialog extends DialogFragment {

    private static final String ARG_BACKGROUND = "background";

    private boolean handled;

    public static ExitConfirmDialog create(boolean showBackground) {
        ExitConfirmDialog dialog = new ExitConfirmDialog();
        Bundle args = new Bundle();
        args.putBoolean(ARG_BACKGROUND, showBackground);
        dialog.setArguments(args);
        return dialog;
    }

    public void show(FragmentActivity activity) {
        for (Fragment fragment : activity.getSupportFragmentManager().getFragments()) {
            if (fragment instanceof ExitConfirmDialog) return;
        }
        show(activity.getSupportFragmentManager(), ExitConfirmDialog.class.getSimpleName());
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        DialogExitConfirmBinding binding = DialogExitConfirmBinding.inflate(LayoutInflater.from(requireContext()));
        boolean showBackground = getArguments() != null && getArguments().getBoolean(ARG_BACKGROUND);
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(binding.getRoot());
        dialog.setCanceledOnTouchOutside(false);
        binding.message.setText(showBackground ? R.string.exit_confirm_playing_message : R.string.exit_confirm_message);
        binding.backgroundPlay.setVisibility(showBackground ? View.VISIBLE : View.GONE);
        binding.negative.setNextFocusRightId(showBackground ? binding.backgroundPlay.getId() : binding.positive.getId());
        binding.positive.setNextFocusLeftId(showBackground ? binding.backgroundPlay.getId() : binding.negative.getId());
        binding.negative.setOnClickListener(view -> dismiss());
        binding.backgroundPlay.setOnClickListener(view -> dispatch(false));
        binding.positive.setOnClickListener(view -> dispatch(true));
        dialog.setOnShowListener(view -> binding.positive.requestFocus());
        return dialog;
    }

    private void dispatch(boolean fullExit) {
        if (handled) return;
        handled = true;
        FragmentActivity activity = getActivity();
        dismissAllowingStateLoss();
        if (!(activity instanceof Listener listener)) return;
        if (fullExit) listener.onFullExit();
        else listener.onBackgroundPlayback();
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        Window window = dialog == null ? null : dialog.getWindow();
        if (window == null) return;
        int screenWidth = ResUtil.getScreenWidth(requireContext());
        int width = Math.max(ResUtil.dp2px(420), Math.min((int) (screenWidth * 0.48f), ResUtil.dp2px(620)));
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = width;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.CENTER;
        params.dimAmount = 0.58f;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setAttributes(params);
        window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
    }

    public interface Listener {

        void onBackgroundPlayback();

        void onFullExit();
    }
}
