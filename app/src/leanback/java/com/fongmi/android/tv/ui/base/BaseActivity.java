package com.fongmi.android.tv.ui.base;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.Updater;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.server.process.ApkUrlPush;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.custom.CustomWallView;
import com.fongmi.android.tv.ui.helper.TouchOptimizationHelper;
import com.fongmi.android.tv.utils.Util;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import me.jessyan.autosize.AutoSizeConfig;
import me.jessyan.autosize.AutoSizeCompat;

public abstract class BaseActivity extends AppCompatActivity {

    private static final int DESIGN_WIDTH_IN_DP = 960;
    private static final int DESIGN_HEIGHT_IN_DP = 540;

    protected abstract ViewBinding getBinding();

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(Setting.wrapLanguage(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerFragmentLifecycleCallbacks();
        setContentView(getBinding().getRoot());
        EventBus.getDefault().register(this);
        initView(savedInstanceState);
        Util.hideSystemUI(this);
        setBackCallback();
        initEvent();
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        if (customWall()) addCustomWall();
        TouchOptimizationHelper.sync(getWindow().getDecorView());
    }

    private void addCustomWall() {
        ((ViewGroup) findViewById(android.R.id.content)).addView(new CustomWallView(this, null).setMotionEnabled(customWallMotion()), 0, new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));
    }

    protected FragmentActivity getActivity() {
        return this;
    }

    protected boolean customWall() {
        return true;
    }

    /**
     * 动态壁纸（视频/GIF）是否允许在本页面播放。返回 false 时壁纸降级为首帧静态图，
     * 避免与页面自身的播放器争抢 MediaCodec 硬解实例并按壁纸帧率重绘整个界面。
     */
    protected boolean customWallMotion() {
        return true;
    }

    protected void initView(Bundle savedInstanceState) {
    }

    protected void initEvent() {
    }

    protected boolean isVisible(View view) {
        return view.getVisibility() == View.VISIBLE;
    }

    protected boolean isGone(View view) {
        return view.getVisibility() == View.GONE;
    }

    protected void notifyItemChanged(RecyclerView view, RecyclerView.Adapter<?> adapter) {
        postRecyclerUpdate(view, adapter::notifyDataSetChanged);
    }

    protected void notifyItemsChanged(RecyclerView view, RecyclerView.Adapter<?> adapter, int... positions) {
        postRecyclerUpdate(view, () -> {
            for (int i = 0; i < positions.length; i++) {
                int position = positions[i];
                if (position < 0 || position >= adapter.getItemCount()) continue;
                boolean duplicate = false;
                for (int j = 0; j < i; j++) if (positions[j] == position) duplicate = true;
                if (!duplicate) adapter.notifyItemChanged(position);
            }
        });
    }

    private void postRecyclerUpdate(RecyclerView view, Runnable update) {
        view.post(new Runnable() {
            @Override
            public void run() {
                if (view.isComputingLayout()) {
                    view.postOnAnimation(this);
                    return;
                }
                update.run();
            }
        });
    }

    private void setBackCallback() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                onBackInvoked();
            }
        });
    }

    private Resources hackResources(Resources resources) {
        try {
            // Keep the TV AutoSize pipeline and express the user scale through its design size.
            applyUiScale();
            AutoSizeCompat.autoConvertDensityOfGlobal(resources);
            return resources;
        } catch (Exception ignored) {
            return resources;
        }
    }

    private void applyUiScale() {
        AutoSizeConfig config = AutoSizeConfig.getInstance();
        float factor = Setting.getUiScaleFactor(Setting.getUiScale());
        config.setDesignWidthInDp(Math.round(DESIGN_WIDTH_IN_DP / factor));
        config.setDesignHeightInDp(Math.round(DESIGN_HEIGHT_IN_DP / factor));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onSubscribe(Object o) {
        if (o instanceof RefreshEvent event && (event.getType() == RefreshEvent.Type.LANGUAGE || event.getType() == RefreshEvent.Type.UI_SCALE || event.getType() == RefreshEvent.Type.THEME)) recreate();
    }

    @Override
    public Resources getResources() {
        return hackResources(super.getResources());
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Util.hideSystemUI(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Util.hideSystemUI(this);
    }

    protected void onBackInvoked() {
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        TouchOptimizationHelper.sync(getWindow().getDecorView());
        Updater.create().resume(this);
        ApkUrlPush.get().resume(this);
    }

    private void registerFragmentLifecycleCallbacks() {
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(new FragmentManager.FragmentLifecycleCallbacks() {
            @Override
            public void onFragmentViewCreated(@NonNull FragmentManager fragmentManager, @NonNull Fragment fragment, @NonNull View view, Bundle savedInstanceState) {
                TouchOptimizationHelper.sync(view);
            }

            @Override
            public void onFragmentStarted(@NonNull FragmentManager fragmentManager, @NonNull Fragment fragment) {
                if (!(fragment instanceof DialogFragment dialog) || dialog.getDialog() == null) return;
                Window window = dialog.getDialog().getWindow();
                if (window == null) return;
                window.getDecorView().post(() -> TouchOptimizationHelper.sync(window.getDecorView()));
            }
        }, true);
    }

    @Override
    protected void onDestroy() {
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }
}
