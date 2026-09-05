package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import androidx.appcompat.widget.AppCompatImageView;
import androidx.fragment.app.FragmentActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.TmdbImageSaver;
import com.fongmi.android.tv.utils.TmdbImageSelector;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Reusable native viewer for images exposed through WebTheme opaque references. */
public final class WebThemeImageViewer {

    private WebThemeImageViewer() {
    }

    public static void show(Activity activity, List<String> values, int selectedIndex) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        List<String> images = images(values);
        if (images.isEmpty()) return;
        int[] current = {Math.max(0, Math.min(selectedIndex, images.size() - 1))};
        int[] request = {0};
        int[] rotation = {0};

        Dialog dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        FrameLayout content = new FrameLayout(activity);
        content.setBackgroundColor(0xFF000000);
        ImageView image = new AppCompatImageView(activity) {
            @Override
            public boolean performClick() {
                super.performClick();
                return true;
            }
        };
        image.setId(View.generateViewId());
        image.setFocusable(true);
        image.setFocusableInTouchMode(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        content.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ProgressBar progress = new ProgressBar(activity);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                ResUtil.dp2px(52), ResUtil.dp2px(52), Gravity.CENTER);
        content.addView(progress, progressParams);

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        actions.setPadding(ResUtil.dp2px(10), ResUtil.dp2px(8), ResUtil.dp2px(10), ResUtil.dp2px(8));
        actions.setBackgroundColor(0xCC11141B);
        boolean compactActions = !Util.isLeanback();
        FrameLayout.LayoutParams actionParams = new FrameLayout.LayoutParams(
                compactActions ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT,
                ResUtil.dp2px(64), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        actionParams.bottomMargin = ResUtil.dp2px(28);
        if (compactActions) {
            actionParams.leftMargin = ResUtil.dp2px(12);
            actionParams.rightMargin = ResUtil.dp2px(12);
        }
        content.addView(actions, actionParams);

        MaterialButton previous = button(activity, R.string.detail_image_previous, compactActions);
        MaterialButton rotate = button(activity, R.string.detail_image_rotate, compactActions);
        MaterialButton save = button(activity, R.string.detail_image_save, compactActions);
        MaterialButton next = button(activity, R.string.detail_image_next, compactActions);
        MaterialButton close = button(activity, R.string.detail_image_close, compactActions);
        actions.addView(previous);
        actions.addView(rotate);
        actions.addView(save);
        actions.addView(next);
        actions.addView(close);
        image.setNextFocusDownId(save.getId());
        for (MaterialButton action : List.of(previous, rotate, save, next, close)) {
            action.setNextFocusUpId(image.getId());
        }

        Runnable load = () -> load(image, progress, images.get(current[0]), request, rotation[0]);
        previous.setOnClickListener(view -> move(images, current, -1, load));
        next.setOnClickListener(view -> move(images, current, 1, load));
        rotate.setOnClickListener(view -> {
            rotation[0] = (rotation[0] + 90) % 360;
            image.animate().rotation(rotation[0]).setDuration(150).start();
        });
        save.setOnClickListener(view -> save(activity, images.get(current[0])));
        close.setOnClickListener(view -> dialog.dismiss());

        GestureDetector gestures = new GestureDetector(activity, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent event) {
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent event) {
                if (images.size() <= 1) {
                    dialog.dismiss();
                } else if (event.getX() < image.getWidth() * 0.33f) {
                    move(images, current, -1, load);
                } else if (event.getX() > image.getWidth() * 0.67f) {
                    move(images, current, 1, load);
                } else {
                    dialog.dismiss();
                }
                return true;
            }

            @Override
            public boolean onFling(MotionEvent down, MotionEvent up, float velocityX, float velocityY) {
                if (images.size() <= 1 || down == null || up == null) return false;
                float distance = up.getX() - down.getX();
                if (Math.abs(distance) < ResUtil.dp2px(48) || Math.abs(velocityX) < 120f) return false;
                move(images, current, distance < 0 ? 1 : -1, load);
                return true;
            }

            @Override
            public void onLongPress(MotionEvent event) {
                save(activity, images.get(current[0]));
            }
        });
        image.setOnTouchListener((view, event) -> {
            boolean handled = gestures.onTouchEvent(event);
            if (event.getActionMasked() == MotionEvent.ACTION_UP && handled) view.performClick();
            return handled;
        });
        dialog.setOnKeyListener((instance, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && image.hasFocus()) {
                    save.requestFocus();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP && actions.hasFocus()) {
                    image.requestFocus();
                    return true;
                }
                return false;
            }
            if (event.getAction() != KeyEvent.ACTION_UP || actions.hasFocus()) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                move(images, current, -1, load);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                move(images, current, 1, load);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_MENU) {
                save(activity, images.get(current[0]));
                return true;
            }
            return false;
        });
        dialog.setOnDismissListener(instance -> Glide.with(image).clear(image));
        dialog.setContentView(content);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            Util.hideSystemUI(window);
        }
        image.requestFocus();
        load.run();
    }

    public static void save(Activity activity, String url) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        if (!(activity instanceof FragmentActivity host)) {
            Notify.show(R.string.detail_image_save_failed);
            return;
        }
        String original = TmdbImageSelector.originalUrl(url);
        if (original.isEmpty()) return;
        Notify.show(R.string.detail_image_saving);
        TmdbImageSaver.save(host, original, new TmdbImageSaver.Callback() {
            @Override
            public void success(String name) {
                Notify.show(activity.getString(R.string.detail_image_save_success, name));
            }

            @Override
            public void error(String message) {
                String prefix = activity.getString(R.string.detail_image_save_failed);
                Notify.show(message == null || message.isEmpty() || prefix.equals(message)
                        ? prefix : prefix + "\n" + message);
            }
        });
    }

    private static MaterialButton button(Activity activity, int text, boolean compact) {
        MaterialButton button = new MaterialButton(activity);
        button.setId(View.generateViewId());
        button.setText(text);
        button.setMinWidth(0);
        button.setSingleLine(true);
        button.setFocusable(true);
        button.setStrokeWidth(ResUtil.dp2px(1));
        button.setStrokeColor(ColorStateList.valueOf(0x66FFFFFF));
        button.setOnFocusChangeListener((view, focused) -> {
            button.setStrokeWidth(ResUtil.dp2px(focused ? 3 : 1));
            button.setStrokeColor(ColorStateList.valueOf(focused ? 0xFFFFFFFF : 0x66FFFFFF));
        });
        if (compact) {
            button.setPadding(ResUtil.dp2px(3), 0, ResUtil.dp2px(3), 0);
            button.setTextSize(12);
        }
        LinearLayout.LayoutParams params = compact
                ? new LinearLayout.LayoutParams(0, ResUtil.dp2px(48), 1f)
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ResUtil.dp2px(48));
        params.setMargins(ResUtil.dp2px(compact ? 2 : 4), 0, ResUtil.dp2px(compact ? 2 : 4), 0);
        button.setLayoutParams(params);
        return button;
    }

    private static void move(List<String> images, int[] current, int direction, Runnable load) {
        if (images.size() <= 1) return;
        current[0] = (current[0] + direction + images.size()) % images.size();
        load.run();
    }

    private static void load(ImageView image, ProgressBar progress, String url, int[] request, int rotation) {
        int token = ++request[0];
        progress.setVisibility(View.VISIBLE);
        image.setRotation(rotation);
        Glide.with(image).clear(image);
        Glide.with(image)
                .load(ImgUtil.getUrl(TmdbImageSelector.originalUrl(url)))
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .fitCenter()
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(GlideException error, Object model, Target<Drawable> target,
                            boolean firstResource) {
                        if (token == request[0]) progress.setVisibility(View.GONE);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target,
                            DataSource dataSource, boolean firstResource) {
                        if (token == request[0]) progress.setVisibility(View.GONE);
                        return false;
                    }
                })
                .into(image);
    }

    private static List<String> images(List<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                String original = TmdbImageSelector.originalUrl(value);
                if (!original.isEmpty()) result.add(original);
            }
        }
        return new ArrayList<>(result);
    }
}
