package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.TmdbImageSelector;
import com.fongmi.android.tv.utils.TmdbImageSaver;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.button.MaterialButton;

import java.util.List;

/**
 * 照片全屏查看器 + 左右滑动 + 保存 + 旋转。
 */
public class PhotoViewerDialog {

    public interface OnSaveListener {
        void onSave(String url);
    }

    public static void show(Activity activity, List<String> photos, int position, OnSaveListener saveListener) {
        new PhotoViewerDialog(activity, photos, position, saveListener).show();
    }

    private final Activity activity;
    private final List<String> photos;
    private final int startPosition;
    private final OnSaveListener saveListener;
    private int currentPosition;
    private int originalOrientation;

    private PhotoViewerDialog(Activity activity, List<String> photos, int position, OnSaveListener saveListener) {
        this.activity = activity;
        this.photos = photos;
        this.startPosition = position;
        this.currentPosition = position;
        this.saveListener = saveListener;
        this.originalOrientation = activity.getRequestedOrientation();
    }

    private void show() {
        Dialog dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);

        // 全屏根容器
        FrameLayout root = new FrameLayout(activity);
        root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(Color.BLACK);
        root.setFocusable(Util.isLeanback());
        root.setFocusableInTouchMode(Util.isLeanback());

        // ViewPager2 支持左右滑动
        ViewPager2 viewPager = new ViewPager2(activity);
        viewPager.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        viewPager.setAdapter(new PhotoPagerAdapter(photos));
        if (photos.size() > 1) viewPager.setOffscreenPageLimit(1);
        viewPager.setCurrentItem(startPosition, false);
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentPosition = position;
                preloadNearby(position);
            }
        });
        root.addView(viewPager);
        preloadNearby(startPosition);

        // 右上角保存按钮 - 始终显示
        FrameLayout saveBtnWrapper = new FrameLayout(activity);
        FrameLayout.LayoutParams saveWrapperParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END
        );
        saveWrapperParams.setMargins(16, 48, 16, 16);
        saveBtnWrapper.setLayoutParams(saveWrapperParams);
        if (Util.isLeanback()) {
            saveBtnWrapper.setFocusable(true);
            saveBtnWrapper.setFocusableInTouchMode(true);
            saveBtnWrapper.setClickable(true);
            saveBtnWrapper.setBackground(ContextCompat.getDrawable(activity, R.drawable.selector_photo_viewer_save));
            saveBtnWrapper.setStateListAnimator(null);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) saveBtnWrapper.setDefaultFocusHighlightEnabled(false);
        } else {
            saveBtnWrapper.setBackground(ContextCompat.getDrawable(activity, R.drawable.selector_photo_viewer_save));
        }
        saveBtnWrapper.setPadding(ResUtil.dp2px(22), ResUtil.dp2px(12), ResUtil.dp2px(22), ResUtil.dp2px(12));

        TextView saveBtn = new TextView(activity);
        saveBtn.setText(R.string.detail_image_save);
        saveBtn.setTextColor(Color.WHITE);
        saveBtn.setTextSize(18);
        saveBtn.setGravity(Gravity.CENTER);
        saveBtn.setIncludeFontPadding(false);
        saveBtn.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        saveBtnWrapper.addView(saveBtn);

        // TV版由wrapper处理点击（焦点效果），手机版由按钮直接处理
        View.OnClickListener saveAction = v -> {
            if (currentPosition < photos.size()) {
                String url = photos.get(currentPosition);
                if (saveListener != null) {
                    saveListener.onSave(url);
                } else {
                    savePhotoDefault(url);
                }
            }
        };
        saveBtnWrapper.setOnClickListener(saveAction);
        saveBtn.setClickable(false);
        saveBtn.setFocusable(false);
        saveBtn.setFocusableInTouchMode(false);
        root.addView(saveBtnWrapper);

        // 左上角旋转按钮 - 仅非TV版显示
        if (!Util.isLeanback()) {
            MaterialButton rotateBtn = new MaterialButton(activity);
            rotateBtn.setText(R.string.detail_image_rotate);
            rotateBtn.setTextColor(Color.WHITE);
            rotateBtn.setBackgroundColor(0x80000000);
            rotateBtn.setPadding(32, 16, 32, 16);
            FrameLayout.LayoutParams rotateBtnParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.START
            );
            rotateBtnParams.setMargins(16, 48, 16, 16);
            rotateBtn.setLayoutParams(rotateBtnParams);
            rotateBtn.setOnClickListener(v -> toggleOrientation());
            root.addView(rotateBtn);
        }

        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawableResource(android.R.color.black);
            window.setDimAmount(0f);
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) window.getDecorView().setDefaultFocusHighlightEnabled(false);
        }

        if (Util.isLeanback()) {
            View.OnKeyListener keyListener = (view, keyCode, event) -> handleTvKey(root, saveBtnWrapper, viewPager, event);
            dialog.setOnKeyListener((d, keyCode, event) -> handleTvKey(root, saveBtnWrapper, viewPager, event));
            root.setOnKeyListener(keyListener);
            viewPager.setOnKeyListener(keyListener);
            saveBtnWrapper.setOnKeyListener(keyListener);
        }

        // 关闭对话框时恢复原始方向
        dialog.setOnDismissListener(d -> {
            activity.setRequestedOrientation(originalOrientation);
        });

        dialog.show();
        if (Util.isLeanback()) saveBtnWrapper.requestFocus();
    }

    private boolean handleTvKey(View root, View saveBtnWrapper, ViewPager2 viewPager, KeyEvent event) {
        if (!Util.isLeanback() || !KeyUtil.isActionDown(event)) return false;
        if (KeyUtil.isLeftKey(event)) return movePhoto(viewPager, false);
        if (KeyUtil.isRightKey(event)) return movePhoto(viewPager, true);
        if (KeyUtil.isUpKey(event)) return saveBtnWrapper.requestFocus();
        if (KeyUtil.isDownKey(event) && saveBtnWrapper.hasFocus()) return root.requestFocus();
        return false;
    }

    private boolean movePhoto(ViewPager2 viewPager, boolean next) {
        if (photos == null || photos.size() <= 1) return true;
        int target = currentPosition + (next ? 1 : -1);
        if (target < 0 || target >= photos.size()) return true;
        currentPosition = target;
        viewPager.setCurrentItem(target, true);
        preloadNearby(target);
        return true;
    }

    private void preloadNearby(int position) {
        preload(position - 1);
        preload(position + 1);
    }

    private void preload(int position) {
        if (photos == null || position < 0 || position >= photos.size()) return;
        String url = convertToHighRes(photos.get(position));
        if (TextUtils.isEmpty(url)) return;
        Glide.with(activity)
                .load(ImgUtil.getUrl(url))
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .preload();
    }

    private void toggleOrientation() {
        int current = activity.getRequestedOrientation();
        // 获取当前实际方向
        int actualOrientation = activity.getResources().getConfiguration().orientation;

        // 如果当前是竖屏或未指定，切换到横屏
        if (actualOrientation == android.content.res.Configuration.ORIENTATION_PORTRAIT ||
            current == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED ||
            current == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT ||
            current == ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT) {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else {
            // 否则切换到竖屏
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    }

    /**
     * 默认保存逻辑（转换为高清图并保存）。
     */
    private void savePhotoDefault(String url) {
        if (TextUtils.isEmpty(url)) return;
        if (!(activity instanceof androidx.fragment.app.FragmentActivity)) {
            Notify.show(R.string.detail_image_save_failed);
            return;
        }
        Notify.show(R.string.detail_image_saving);
        String highResUrl = convertToHighRes(url);
        TmdbImageSaver.save((androidx.fragment.app.FragmentActivity) activity, highResUrl, new TmdbImageSaver.Callback() {
            @Override
            public void success(String name) {
                Notify.show(activity.getString(R.string.detail_image_save_success, name));
            }

            @Override
            public void error(String message) {
                String prefix = activity.getString(R.string.detail_image_save_failed);
                Notify.show(TextUtils.isEmpty(message) || prefix.equals(message) ? prefix : prefix + "\n" + message);
            }
        });
    }

    /**
     * 转换为高清图片 URL（original 尺寸）。
     * 兼容 TMDB 官方域名和自定义代理。
     */
    private String convertToHighRes(String url) {
        return TmdbImageSelector.originalUrl(url);
    }

    /**
     * 照片 ViewPager 适配器。
     */
    private class PhotoPagerAdapter extends RecyclerView.Adapter<PhotoPagerAdapter.PhotoViewHolder> {
        private final List<String> urls;

        PhotoPagerAdapter(List<String> urls) {
            this.urls = urls;
        }

        @NonNull
        @Override
        public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout root = new FrameLayout(parent.getContext());
            root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            root.setBackgroundColor(Color.BLACK);

            ImageView imageView = new ImageView(parent.getContext());
            imageView.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageView.setBackgroundColor(Color.BLACK);
            root.addView(imageView);

            ProgressBar progress = new ProgressBar(parent.getContext());
            progress.setIndeterminate(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) progress.setIndeterminateTintList(ColorStateList.valueOf(Color.WHITE));
            FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
            progress.setLayoutParams(progressParams);
            root.addView(progress);

            return new PhotoViewHolder(root, imageView, progress);
        }

        @Override
        public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
            holder.progress.setVisibility(View.VISIBLE);
            holder.imageView.setImageDrawable(null);
            Glide.with(holder.imageView.getContext())
                    .load(ImgUtil.getUrl(convertToHighRes(urls.get(position))))
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            holder.progress.setVisibility(View.GONE);
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            holder.progress.setVisibility(View.GONE);
                            return false;
                        }
                    })
                    .into(holder.imageView);
        }

        @Override
        public int getItemCount() {
            return urls.size();
        }

        class PhotoViewHolder extends RecyclerView.ViewHolder {
            ImageView imageView;
            ProgressBar progress;

            PhotoViewHolder(View view, ImageView imageView, ProgressBar progress) {
                super(view);
                this.imageView = imageView;
                this.progress = progress;
            }
        }
    }
}
