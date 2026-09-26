package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.databinding.DialogHomeMenuBinding;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.activity.SettingActivity;
import com.fongmi.android.tv.ui.adapter.HomeMenuAdapter;
import com.fongmi.android.tv.utils.FocusLoop;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.ResUtil;

public class HomeMenuDialog extends DialogFragment implements HomeMenuAdapter.OnClickListener {

    private DialogHomeMenuBinding binding;
    private String[] items;
    private int spanCount;

    public static HomeMenuDialog create() {
        return new HomeMenuDialog();
    }

    public HomeMenuDialog items(String[] items) {
        this.items = items;
        this.spanCount = calcSpanCount(items.length);
        return this;
    }

    private int calcSpanCount(int count) {
        int span = (int) Math.ceil(Math.sqrt(count));
        return Math.max(2, Math.min(5, span));
    }

    public void show(FragmentActivity activity) {
        show(activity.getSupportFragmentManager(), HomeMenuDialog.class.getSimpleName());
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = new Dialog(requireActivity());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DialogHomeMenuBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.root.setBackground(glassBackground());
        binding.handle.setBackground(handleBackground());
        binding.recycler.setHasFixedSize(true);
        binding.recycler.setLayoutManager(new GridLayoutManager(getActivity(), spanCount));
        binding.recycler.addItemDecoration(new GridSpacingItemDecoration(8, spanCount));
        binding.recycler.setAdapter(new HomeMenuAdapter(this, items, spanCount));
        Dialog dialog = getDialog();
        if (dialog != null) {
            dialog.setOnKeyListener((d, keyCode, event) -> {
                if (KeyUtil.isMenuKey(event)) {
                    SettingActivity.start(requireActivity());
                    dismiss();
                    return true;
                }
                return FocusLoop.handleRecyclerGrid(binding.recycler, items.length, spanCount, FocusLoop.Mode.BOTH, event);
            });
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Window window = getDialog() == null ? null : getDialog().getWindow();
        if (window == null) return;
        window.setBackgroundDrawableResource(android.R.color.transparent);
        float width = 0.32f + spanCount * 0.1f;
        window.setLayout((int) (ResUtil.getScreenWidth() * Math.min(width, 0.7f)), ViewGroup.LayoutParams.WRAP_CONTENT);
        window.setGravity(Gravity.CENTER);
        binding.recycler.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                binding.recycler.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                RecyclerView.ViewHolder vh = binding.recycler.findViewHolderForAdapterPosition(0);
                if (vh != null) vh.itemView.requestFocus();
            }
        });
    }

    @Override
    public void onItemClick(int position) {
        ((Listener) requireActivity()).onHomeMenu(position);
        dismiss();
    }

    private GradientDrawable glassBackground() {
        int wallColor = Setting.getWallColor();
        if (wallColor == 0) wallColor = Setting.getBuiltInWallColor(Setting.getWall());
        int r = (int) (((wallColor >> 16) & 0xFF) * 0.42);
        int g = (int) (((wallColor >> 8) & 0xFF) * 0.42);
        int b = (int) ((wallColor & 0xFF) * 0.42);
        int rgb = (r << 16) | (g << 8) | b;
        int[] colors = new int[]{0xEE000000 | rgb, 0xE6000000 | rgb, 0xDE000000 | rgb};
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        drawable.setCornerRadius(ResUtil.dp2px(22));
        drawable.setStroke(ResUtil.dp2px(1), 0x66FFFFFF);
        return drawable;
    }

    private GradientDrawable handleBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(0x55FFFFFF);
        drawable.setCornerRadius(ResUtil.dp2px(2));
        return drawable;
    }

    private static class GridSpacingItemDecoration extends RecyclerView.ItemDecoration {
        private final int spacing;
        private final int spanCount;

        GridSpacingItemDecoration(int spacingDp, int spanCount) {
            this.spacing = ResUtil.dp2px(spacingDp);
            this.spanCount = spanCount;
        }

        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            int position = parent.getChildAdapterPosition(view);
            if (position == RecyclerView.NO_POSITION) return;
            int column = position % spanCount;
            outRect.left = column * spacing / spanCount;
            outRect.right = spacing - (column + 1) * spacing / spanCount;
            if (position >= spanCount) outRect.top = spacing;
        }
    }

    public interface Listener {
        void onHomeMenu(int which);
    }
}
