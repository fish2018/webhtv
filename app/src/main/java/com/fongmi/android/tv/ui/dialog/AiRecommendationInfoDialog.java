package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.widget.NestedScrollView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.service.RecommendationFeedbackStore;
import com.fongmi.android.tv.ui.helper.TmdbRatingFormatter;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.TmdbImageSelector;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BooleanSupplier;

public class AiRecommendationInfoDialog {

    private static final float DIALOG_HEIGHT_FRACTION = 0.82f;
    private static final int DIALOG_VERTICAL_MARGIN_DP = 32;

    public interface OnNotInterestedListener {
        void onNotInterested(TmdbItem item);
    }

    public static void show(Activity activity, TmdbItem item) {
        show(activity, item, "ai", null);
    }

    public static void show(Activity activity, TmdbItem item, String source, OnNotInterestedListener listener) {
        show(activity, item, source, listener, null);
    }

    public static void show(Activity activity, TmdbItem item, String source, OnNotInterestedListener listener,
            BooleanSupplier active) {
        if (activity == null || item == null) return;
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_ai_recommendation_info, null);
        ImageView poster = view.findViewById(R.id.poster);
        TextView dialogTitle = view.findViewById(R.id.dialogTitle);
        TextView sourceLabel = view.findViewById(R.id.sourceLabel);
        TextView title = view.findViewById(R.id.title);
        TextView subtitle = view.findViewById(R.id.subtitle);
        TextView attributes = view.findViewById(R.id.attributes);
        TextView credit = view.findViewById(R.id.credit);
        View ratingGroup = view.findViewById(R.id.ratingGroup);
        TextView tmdbRating = view.findViewById(R.id.tmdbRating);
        TextView doubanRating = view.findViewById(R.id.doubanRating);
        TextView dataStatus = view.findViewById(R.id.dataStatus);
        NestedScrollView detailScroll = view.findViewById(R.id.detailScroll);
        View overviewCard = view.findViewById(R.id.overviewCard);
        TextView overview = view.findViewById(R.id.overview);
        View reasonCard = view.findViewById(R.id.reasonCard);
        TextView reason = view.findViewById(R.id.reason);
        MaterialButton notInterested = view.findViewById(R.id.notInterested);
        MaterialButton confirm = view.findViewById(R.id.confirm);

        dialogTitle.setText(R.string.ai_recommendation_info_title);
        sourceLabel.setText(sourceLabel(activity, source));
        title.setText(cleanText(item.getTitle()));
        bindText(subtitle, meta(activity, item));
        bindText(attributes, attributes(item));
        bindText(credit, item.getCredit());
        bindRatings(ratingGroup, tmdbRating, doubanRating, item, 0.0);
        bindText(dataStatus, dataStatus(activity, item));

        String overviewText = cleanText(item.getOverview());
        bindRequiredText(overview,
                TextUtils.isEmpty(overviewText) ? activity.getString(R.string.recommendation_overview_unavailable) : overviewText,
                !TextUtils.isEmpty(overviewText));
        overviewCard.setVisibility(View.VISIBLE);

        String reasonText = cleanText(item.getRecommendationReason());
        bindText(reason, reasonText);
        reasonCard.setVisibility(TextUtils.isEmpty(reasonText) ? View.GONE : View.VISIBLE);

        String image = TmdbImageSelector.cardImage(item, false);
        String fallbackImage = TmdbImageSelector.cardImage(item, true);
        ImgUtil.load(item.getTitle(), image, fallbackImage, poster, true, 300, 450);

        int dialogHeightPx = calculateDialogHeightPx(
                ResUtil.getScreenHeight(activity),
                dp(activity, DIALOG_VERTICAL_MARGIN_DP));
        Dialog dialog = LightDialog.create(
                activity,
                null,
                view,
                0.68f,
                0.94f,
                620,
                dialogHeightPx);
        notInterested.setOnClickListener(v -> {
            if (active != null && !active.getAsBoolean()) {
                dialog.dismiss();
                return;
            }
            RecommendationFeedbackStore.add(item, source);
            if (listener != null) listener.onNotInterested(item);
            Toast.makeText(activity, R.string.recommendation_not_interested_recorded, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        confirm.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        if (Util.isLeanback()) {
            detailScroll.setFocusable(true);
            detailScroll.setFocusableInTouchMode(true);
            notInterested.setFocusableInTouchMode(true);
            confirm.setFocusableInTouchMode(true);
            installDetailScrollKeys(activity, detailScroll, notInterested);
            confirm.post(confirm::requestFocus);
        }
    }

    private static void bindRatings(View group, TextView tmdbView, TextView doubanView, TmdbItem item, double loadedDoubanRating) {
        TmdbRatingFormatter.Ratings ratings = TmdbRatingFormatter.completeRatings(item, loadedDoubanRating);
        tmdbView.setText(ratings.getTmdb());
        tmdbView.setVisibility(View.VISIBLE);
        tmdbView.setAlpha(ratings.hasTmdbRating() ? 1.0f : 0.55f);
        doubanView.setText(ratings.getDouban());
        doubanView.setVisibility(View.VISIBLE);
        doubanView.setAlpha(ratings.hasDoubanRating() ? 1.0f : 0.55f);
        group.setVisibility(View.VISIBLE);
    }

    private static void bindText(TextView view, String text) {
        String value = cleanText(text);
        view.setText(value);
        view.setVisibility(TextUtils.isEmpty(value) ? View.GONE : View.VISIBLE);
    }

    private static void bindRequiredText(TextView view, String text, boolean available) {
        view.setText(cleanText(text));
        view.setVisibility(View.VISIBLE);
        view.setAlpha(available ? 1.0f : 0.62f);
    }

    static int calculateDialogHeightPx(int screenHeightPx, int verticalMarginPx) {
        if (screenHeightPx <= 0) return 0;
        int availableHeightPx = Math.max(0, screenHeightPx - Math.max(0, verticalMarginPx));
        int preferredHeightPx = Math.round(screenHeightPx * DIALOG_HEIGHT_FRACTION);
        return Math.min(availableHeightPx, preferredHeightPx);
    }

    private static void installDetailScrollKeys(Activity activity, NestedScrollView scroll, View next) {
        scroll.setOnKeyListener((view, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            int page = Math.max(dp(activity, 40), scroll.getHeight() - dp(activity, 16));
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (scroll.canScrollVertically(1)) {
                    scroll.smoothScrollBy(0, page);
                    return true;
                }
                return next != null && next.requestFocus();
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && scroll.canScrollVertically(-1)) {
                scroll.smoothScrollBy(0, -page);
                return true;
            }
            return false;
        });
    }

    private static int dp(Activity activity, int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                activity.getResources().getDisplayMetrics()));
    }

    private static String cleanText(String raw) {
        String value = Objects.toString(raw, "");
        StringBuilder result = new StringBuilder(value.length());
        boolean spacing = true;
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            int type = Character.getType(codePoint);
            boolean separator = Character.isWhitespace(codePoint)
                    || codePoint == 0x00A0
                    || type == Character.CONTROL
                    || type == Character.FORMAT
                    || type == Character.LINE_SEPARATOR
                    || type == Character.PARAGRAPH_SEPARATOR;
            if (separator) {
                if (!spacing) result.append(' ');
                spacing = true;
            } else {
                result.appendCodePoint(codePoint);
                spacing = false;
            }
        }
        return result.toString().trim();
    }

    private static String sourceLabel(Activity activity, String source) {
        int resId;
        switch (Objects.toString(source, "")) {
            case "tmdb":
                resId = R.string.recommendation_source_tmdb;
                break;
            case "douban":
                resId = R.string.recommendation_source_douban;
                break;
            case "ai":
                resId = R.string.recommendation_source_ai;
                break;
            default:
                resId = R.string.recommendation_source_related;
                break;
        }
        return activity.getString(resId);
    }

    private static String meta(Activity activity, TmdbItem item) {
        List<String> values = new ArrayList<>();
        values.add(activity.getString(item.isTv() ? R.string.detail_media_tv : R.string.detail_media_movie));
        for (String raw : Objects.toString(item.getSubtitle(), "").split("[·•/、,，]")) {
            String value = raw == null ? "" : raw.trim();
            if (TextUtils.isEmpty(value)) continue;
            String lower = value.toLowerCase(Locale.ROOT);
            if (value.startsWith("评分") || lower.startsWith("score")) continue;
            if (!values.contains(value)) values.add(value);
            if (values.size() >= 3) break;
        }
        return TextUtils.join(" · ", values);
    }

    private static String attributes(TmdbItem item) {
        List<String> values = new ArrayList<>();
        addValue(values, item.getOriginCountry());
        String language = Objects.toString(item.getOriginalLanguage(), "").trim();
        addValue(values, TextUtils.isEmpty(language) ? "" : language.toUpperCase(Locale.ROOT));
        addValue(values, item.getDepartment());
        return TextUtils.join(" · ", values);
    }

    private static void addValue(List<String> values, String raw) {
        String value = Objects.toString(raw, "").trim();
        if (!TextUtils.isEmpty(value) && !values.contains(value)) values.add(value);
    }

    private static String dataStatus(Activity activity, TmdbItem item) {
        List<String> values = new ArrayList<>();
        double doubanRating = item.getDoubanRating();
        if (item.getTmdbId() > 0) {
            values.add(activity.getString(R.string.recommendation_data_tmdb, item.getTmdbId()));
            if (doubanRating > 0) {
                values.add(activity.getString(R.string.recommendation_data_douban_matched));
            } else {
                values.add(activity.getString(R.string.recommendation_data_douban_unavailable));
            }
        } else if (doubanRating > 0) {
            values.add(activity.getString(R.string.recommendation_data_douban_only));
        } else {
            values.add(activity.getString(R.string.recommendation_data_metadata_only));
        }
        if (TextUtils.isEmpty(item.getPosterUrl()) && TextUtils.isEmpty(item.getBackdropUrl())) {
            values.add(activity.getString(R.string.recommendation_data_no_poster));
        }
        return TextUtils.join(" · ", values);
    }
}