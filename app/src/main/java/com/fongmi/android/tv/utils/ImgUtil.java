package com.fongmi.android.tv.utils;

import static android.widget.ImageView.ScaleType.CENTER_CROP;
import static android.widget.ImageView.ScaleType.FIT_CENTER;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.impl.CustomTarget;
import com.github.catvod.utils.Json;
import com.google.common.net.HttpHeaders;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import jahirfiquitiva.libs.textdrawable.TextDrawable;

public class ImgUtil {

    private static final Set<String> failed = Collections.synchronizedSet(new HashSet<>());

    public static void logo(ImageView view) {
        logo(view, VodConfig.get().getConfig().getLogo());
    }

    public static void logo(ImageView view, String logo) {
        try {
            Glide.with(view).load(UrlUtil.convert(logo)).circleCrop().override(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL).error(R.drawable.ic_logo).into(view);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static void load(String url, CustomTarget<Bitmap> target) {
        try {
            Glide.with(App.get()).asBitmap().load(getUrl(url)).override(ResUtil.dp2px(96), ResUtil.dp2px(96)).error(R.drawable.artwork).into(target);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static void load(Context context, String url, CustomTarget<Drawable> target) {
        try {
            Glide.with(context).load(getUrl(url)).override(ResUtil.getScreenWidth(), ResUtil.getScreenHeight()).error(R.drawable.artwork).into(target);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static void load(Context context, String url, int width, int height, CustomTarget<Drawable> target) {
        try {
            Glide.with(context).load(getUrl(url)).override(width, height).error(R.drawable.artwork).into(target);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static void preload(Context context, String url) {
        if (TextUtils.isEmpty(url)) return;
        try {
            Glide.with(context).load(getUrl(url)).override(ResUtil.getScreenWidth(), ResUtil.getScreenHeight()).preload();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static void clear(ImageView view) {
        try {
            view.setImageDrawable(null);
            Glide.with(view).clear(view);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static void load(String text, String url, ImageView view) {
        load(text, url, view, true);
    }

    public static void load(String text, String url, ImageView view, boolean vod) {
        load(text, url, view, vod, 0, 0);
    }

    public static void load(String text, String url, ImageView view, int width, int height) {
        load(text, url, view, true, width, height);
    }

    public static void load(String text, String url, ImageView view, boolean vod, int width, int height) {
        load(text, url, "", view, vod, width, height);
    }

    public static void load(String text, String url, String fallbackUrl, ImageView view, boolean vod, int width, int height) {
        view.setScaleType(vod ? CENTER_CROP : FIT_CENTER);
        String fallback = TextUtils.equals(url, fallbackUrl) ? "" : fallbackUrl;
        if (!vod) view.setVisibility(TextUtils.isEmpty(url) && TextUtils.isEmpty(fallback) ? View.GONE : View.VISIBLE);
        if (TextUtils.isEmpty(url) || failed.contains(url)) {
            if (!TextUtils.isEmpty(fallback) && !failed.contains(fallback)) load(text, fallback, view, vod, width, height);
            else showTextDrawable(text, view, vod);
        } else try {
            RequestBuilder<Drawable> builder = Glide.with(view).load(getUrl(url));
            if (!TextUtils.isEmpty(fallback) && !failed.contains(fallback)) {
                builder.listener(getFallbackListener(text, url, fallback, view, vod, width, height));
            } else {
                builder.listener(getListener(text, url, view, vod));
            }
            if (width > 0 && height > 0) builder.override(width, height);
            if (vod) builder.centerCrop().into(view);
            else builder.fitCenter().into(view);
        } catch (Throwable e) {
            e.printStackTrace();
            showTextDrawable(text, view, vod);
        }
    }

    public static Object getUrl(String url) {
        String param = null;
        url = UrlUtil.convert(url);
        if (url.startsWith("data:")) return url;
        boolean hasReferer = false;
        LazyHeaders.Builder builder = new LazyHeaders.Builder();
        if (url.contains("@Headers=")) hasReferer |= addHeader(builder, param = url.split("@Headers=")[1].split("@")[0]);
        if (url.contains("@Cookie=")) builder.addHeader(HttpHeaders.COOKIE, param = url.split("@Cookie=")[1].split("@")[0]);
        if (url.contains("@Referer=")) {
            builder.addHeader(HttpHeaders.REFERER, param = url.split("@Referer=")[1].split("@")[0]);
            hasReferer = true;
        }
        if (url.contains("@User-Agent=")) builder.addHeader(HttpHeaders.USER_AGENT, param = url.split("@User-Agent=")[1].split("@")[0]);
        url = param == null ? url : url.split("@")[0];
        String referer = ImageHeaderPolicy.doubanImageReferer(url, hasReferer);
        if (!TextUtils.isEmpty(referer)) builder.addHeader(HttpHeaders.REFERER, referer);
        return TextUtils.isEmpty(url) ? null : new GlideUrl(url, builder.build());
    }

    private static boolean addHeader(LazyHeaders.Builder builder, String header) {
        boolean hasReferer = false;
        Map<String, String> map = Json.toMap(Json.parse(header));
        for (Map.Entry<String, String> entry : map.entrySet()) {
            String key = UrlUtil.fixHeader(entry.getKey());
            if (ImageHeaderPolicy.isReferer(key)) hasReferer = true;
            builder.addHeader(key, entry.getValue());
        }
        return hasReferer;
    }

    private static Drawable getTextDrawable(String text, boolean vod) {
        TextDrawable.Builder builder = new TextDrawable.Builder();
        text = TextUtils.isEmpty(text) ? "！" : text.substring(0, 1);
        if (vod) builder.buildRect(text, ColorGenerator.get400(text));
        return builder.buildRoundRect(text, ColorGenerator.get400(text), ResUtil.dp2px(4));
    }

    private static void showTextDrawable(String text, ImageView view, boolean vod) {
        showTextDrawable(text, view, vod, true);
    }

    private static void showTextDrawable(String text, ImageView view, boolean vod, boolean clear) {
        try {
            if (clear) Glide.with(view).clear(view);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        view.setImageDrawable(getTextDrawable(text, vod));
    }

    private static RequestListener<Drawable> getFallbackListener(String text, String url, String fallback, ImageView view, boolean vod, int width, int height) {
        return new RequestListener<>() {
            @Override
            public boolean onLoadFailed(@Nullable GlideException e, Object model, @NonNull Target<Drawable> target, boolean isFirstResource) {
                failed.add(url);
                if (!TextUtils.isEmpty(fallback) && !failed.contains(fallback)) load(text, fallback, view, vod, width, height);
                else showTextDrawable(text, view, vod, false);
                return true;
            }

            @Override
            public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                return false;
            }
        };
    }

    private static RequestListener<Drawable> getListener(String text, String url, ImageView view, boolean vod) {
        return new RequestListener<>() {
            @Override
            public boolean onLoadFailed(@Nullable GlideException e, Object model, @NonNull Target<Drawable> target, boolean isFirstResource) {
                showTextDrawable(text, view, vod, false);
                failed.add(url);
                return true;
            }

            @Override
            public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                return false;
            }
        };
    }
}
