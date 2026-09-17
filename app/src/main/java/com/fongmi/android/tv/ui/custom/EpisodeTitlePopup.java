package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.bean.TmdbEpisode;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.textview.MaterialTextView;

public class EpisodeTitlePopup {

    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static PopupWindow popupWindow;

    /**
     * 显示简单标题（兼容旧版）
     */
    public static boolean show(View anchor, CharSequence title) {
        if (anchor == null || TextUtils.isEmpty(title)) return false;
        dismiss();
        MaterialTextView textView = createTextView(anchor.getContext(), title);
        PopupWindow popup = new PopupWindow(textView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, false);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setClippingEnabled(true);
        popupWindow = popup;
        showAtAnchor(anchor, textView, popup);
        HANDLER.postDelayed(EpisodeTitlePopup::dismiss, 6000);
        return true;
    }

    /**
     * 显示完整集数信息（标题 + 介绍 + 剧照）
     */
    public static boolean show(View anchor, TmdbEpisode episode) {
        if (anchor == null || episode == null) return false;
        dismiss();
        View contentView = createEpisodeView(anchor.getContext(), episode);
        PopupWindow popup = new PopupWindow(contentView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, false);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setClippingEnabled(true);
        popupWindow = popup;
        showAtAnchor(anchor, contentView, popup);
        HANDLER.postDelayed(EpisodeTitlePopup::dismiss, 8000);
        return true;
    }

    public static void dismiss() {
        HANDLER.removeCallbacksAndMessages(null);
        if (popupWindow == null) return;
        popupWindow.dismiss();
        popupWindow = null;
    }

    private static MaterialTextView createTextView(Context context, CharSequence title) {
        MaterialTextView textView = new MaterialTextView(context);
        textView.setText(title);
        textView.setTextColor(Color.WHITE);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        textView.setGravity(Gravity.CENTER_VERTICAL);
        textView.setLineSpacing(ResUtil.dp2px(3), 1.0f);
        textView.setSingleLine(false);
        textView.setMaxLines(8);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setPadding(ResUtil.dp2px(16), ResUtil.dp2px(10), ResUtil.dp2px(16), ResUtil.dp2px(10));
        textView.setMinWidth(ResUtil.dp2px(200));
        textView.setMaxWidth(Math.min(ResUtil.dp2px(520), (int) (ResUtil.getScreenWidth(context) * 0.78f)));
        textView.setBackground(background());
        return textView;
    }

    private static View createEpisodeView(Context context, TmdbEpisode episode) {
        int dp16 = ResUtil.dp2px(16);
        int dp12 = ResUtil.dp2px(12);
        int dp8 = ResUtil.dp2px(8);

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackground(background());
        container.setPadding(dp16, dp12, dp16, dp12);
        int maxWidth = Math.min(ResUtil.dp2px(520), (int) (ResUtil.getScreenWidth(context) * 0.78f));
        container.setMinimumWidth(ResUtil.dp2px(200));

        // 标题
        MaterialTextView titleView = new MaterialTextView(context);
        titleView.setText(episode.getDisplayTitle());
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);  // 粗体
        titleView.setSingleLine(false);
        titleView.setMaxLines(2);
        container.addView(titleView);

        // 剧照
        String stillUrl = episode.getStillUrl();
        if (!TextUtils.isEmpty(stillUrl)) {
            ImageView stillImage = new ImageView(context);
            stillImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
            LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(maxWidth - dp16 * 2, ResUtil.dp2px(180));
            imageParams.topMargin = dp8;
            imageParams.bottomMargin = dp8;
            stillImage.setLayoutParams(imageParams);
            Glide.with(context)
                    .load(stillUrl)
                    .override(maxWidth - dp16 * 2, ResUtil.dp2px(180))
                    .into(stillImage);
            container.addView(stillImage);
        }

        // 介绍
        String overview = episode.getOverview();
        if (!TextUtils.isEmpty(overview)) {
            MaterialTextView overviewView = new MaterialTextView(context);
            overviewView.setText(overview);
            overviewView.setTextColor(0xFFCCCCCC);
            overviewView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            overviewView.setLineSpacing(ResUtil.dp2px(2), 1.0f);
            overviewView.setSingleLine(false);
            overviewView.setMaxLines(6);
            overviewView.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams overviewParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            overviewParams.topMargin = dp8;
            overviewView.setLayoutParams(overviewParams);
            container.addView(overviewView);
        }

        // 滚动容器
        ScrollView scrollView = new ScrollView(context);
        scrollView.addView(container);
        scrollView.setVerticalScrollBarEnabled(false);
        return scrollView;
    }

    private static GradientDrawable background() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(0xF0222730);
        drawable.setCornerRadius(ResUtil.dp2px(8));
        drawable.setStroke(ResUtil.dp2px(1), 0x66FFFFFF);
        return drawable;
    }

    private static void showAtAnchor(View anchor, View content, PopupWindow popup) {
        int margin = ResUtil.dp2px(12);
        int gap = ResUtil.dp2px(8);
        int screenWidth = ResUtil.getScreenWidth(anchor.getContext());
        int screenHeight = ResUtil.getScreenHeight(anchor.getContext());
        int maxWidth = Math.min(ResUtil.dp2px(520), screenWidth - margin * 2);
        int maxHeight = screenHeight - margin * 2;
        content.measure(View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST));
        int popupWidth = Math.min(Math.max(content.getMeasuredWidth(), Math.min(anchor.getWidth(), maxWidth)), maxWidth);
        int popupHeight = Math.min(content.getMeasuredHeight(), maxHeight);
        popup.setWidth(popupWidth);
        popup.setHeight(popupHeight);

        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        int x = location[0] + (anchor.getWidth() - popupWidth) / 2;
        int y = location[1] - popupHeight - gap;
        if (y < margin) y = location[1] + anchor.getHeight() + gap;
        if (y + popupHeight > screenHeight - margin) y = screenHeight - popupHeight - margin;
        x = Math.max(margin, Math.min(x, screenWidth - popupWidth - margin));
        popup.showAtLocation(anchor.getRootView(), Gravity.NO_GRAVITY, x, Math.max(margin, y));
    }
}
