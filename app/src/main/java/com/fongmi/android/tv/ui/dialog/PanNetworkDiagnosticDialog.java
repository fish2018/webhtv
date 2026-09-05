package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.media3.common.Format;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.diagnostic.PanBenchmarkPlan;
import com.fongmi.android.tv.player.diagnostic.PanDiagnosticVerdict;
import com.fongmi.android.tv.player.diagnostic.PanEndpoint;
import com.fongmi.android.tv.player.diagnostic.PanEndpointParser;
import com.fongmi.android.tv.player.diagnostic.PanNetworkDiagnosticRunner;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PanNetworkDiagnosticDialog extends DialogFragment implements PanNetworkDiagnosticRunner.Listener {

    private static final int BLUE = Color.rgb(26, 115, 232);
    private static final int BLUE_DARK = Color.rgb(23, 78, 166);
    private static final int TEXT = Color.rgb(32, 33, 36);
    private static final int SECONDARY = Color.rgb(95, 99, 104);
    private static final int BORDER = Color.rgb(218, 220, 224);
    private static final int SURFACE = Color.rgb(248, 249, 250);
    private static final int SUCCESS = Color.rgb(24, 128, 56);
    private static final int WARNING = Color.rgb(180, 93, 0);
    private static final long HIGH_TRAFFIC_CONFIRM_BYTES = 2L * 1024L * 1024L * 1024L;
    private PlayerManager player;
    private PanNetworkDiagnosticRunner runner;
    private PanEndpoint endpoint;
    private LinearLayout root;
    private LinearLayout content;
    private LinearLayout resultFooter;
    private ScrollView contentScroll;
    private TextView subtitle;
    private EditText singleThreadInput;
    private EditText customThreadInput;
    private ChipGroup threadChips;
    private LinearLayout singleThreadPanel;
    private LinearLayout multiThreadPanel;
    private MaterialButton startButton;
    private final Map<Integer, Chip> threadChipMap = new LinkedHashMap<>();
    private final List<Integer> selectedThreadValues = new ArrayList<>();
    private TextView estimate;
    private TabLayout modeTabs;
    private LinearProgressIndicator progress;
    private TextView stage;
    private TextView liveSpeed;
    private TextView liveMeta;
    private SpeedGraphView graph;
    private PanBenchmarkPlan.Mode mode = PanBenchmarkPlan.Mode.QUICK;
    private boolean wasPlaying;
    private boolean playbackPaused;
    private boolean running;
    private boolean highThreadConfirmed;
    private boolean multiThreadMode;

    public static void show(FragmentActivity activity, PlayerManager player) {
        if (activity == null || player == null || player.isReleased()) return;
        if (activity.isFinishing() || activity.isDestroyed()) return;
        FragmentManager fragmentManager = activity.getSupportFragmentManager();
        if (fragmentManager.isStateSaved()) return;
        for (androidx.fragment.app.Fragment fragment : fragmentManager.getFragments()) {
            if (fragment instanceof PanNetworkDiagnosticDialog) return;
        }
        PanNetworkDiagnosticDialog dialog = new PanNetworkDiagnosticDialog();
        dialog.player = player;
        dialog.show(fragmentManager, PanNetworkDiagnosticDialog.class.getSimpleName());
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        runner = new PanNetworkDiagnosticRunner();
        root = vertical(0);
        root.setPadding(dp(Util.isLeanback() ? 24 : 18), dp(Util.isLeanback() ? 20 : 16), dp(Util.isLeanback() ? 24 : 18), dp(Util.isLeanback() ? 18 : 14));
        root.setBackground(round(Color.WHITE, 18, 0, Color.TRANSPARENT));
        addHeader();
        contentScroll = new ScrollView(requireContext());
        contentScroll.setFillViewport(false);
        contentScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        content = vertical(0);
        contentScroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int screenHeight = ResUtil.getScreenHeight(requireContext());
        int contentHeight = Util.isLeanback()
                ? Math.min(dp(460), Math.max(dp(400), screenHeight - dp(300)))
                : Math.min(dp(560), Math.max(dp(350), screenHeight * 5 / 8));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, contentHeight);
        scrollParams.topMargin = dp(10);
        root.addView(contentScroll, scrollParams);
        resultFooter = horizontal(Gravity.CENTER_VERTICAL);
        resultFooter.setVisibility(View.GONE);
        LinearLayout.LayoutParams footerParams = matchWrap(8, 0);
        root.addView(resultFooter, footerParams);
        resolveEndpoint();
        showConfig();
        Dialog dialog = new MaterialAlertDialogBuilder(requireActivity(), R.style.ThemeOverlay_WebHTV_LightDialog).setView(root).create();
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        Window window = getDialog() == null ? null : getDialog().getWindow();
        if (window == null) return;
        int screen = ResUtil.getScreenWidth(requireContext());
        int width = Util.isLeanback()
                ? Math.min(dp(1320), Math.round(screen * 0.84f))
                : ResUtil.isLand(requireContext()) ? Math.min(dp(780), Math.round(screen * 0.68f)) : Math.round(screen * 0.94f);
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = width;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.dimAmount = 0.62f;
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setAttributes(params);
        window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
        window.getDecorView().setPadding(0, 0, 0, 0);
    }

    private void addHeader() {
        LinearLayout bar = horizontal(Gravity.CENTER_VERTICAL);
        TextView title = text(getString(R.string.pan_diagnostic_title), 18, TEXT, true);
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        MaterialButton close = button("关闭", false, view -> {
            if (running) cancelTest();
            else dismiss();
        });
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(68), dp(40));
        closeParams.leftMargin = dp(10);
        bar.addView(close, closeParams);
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
        subtitle = text("定位源站、代理、App供数与播放层瓶颈", 12, SECONDARY, false);
        root.addView(subtitle, matchWrap(0, 0));
    }

    private void resolveEndpoint() {
        try {
            endpoint = PanEndpointParser.parse(player.getUrl(), player.getHeaders());
        } catch (RuntimeException ignored) {
            endpoint = null;
        }
    }

    private void showConfig() {
        running = false;
        hideResultFooter();
        content.removeAllViews();
        addSummaryCard();
        modeTabs = new TabLayout(requireContext());
        modeTabs.setTabMode(TabLayout.MODE_FIXED);
        modeTabs.setTabGravity(TabLayout.GRAVITY_FILL);
        styleTabs(modeTabs);
        modeTabs.setTabRippleColor(ColorStateList.valueOf(Color.TRANSPARENT));
        modeTabs.addTab(modeTabs.newTab().setText("快速"), false);
        modeTabs.addTab(modeTabs.newTab().setText("标准"), false);
        modeTabs.addTab(modeTabs.newTab().setText("深度"), false);
        modeTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                mode = switch (tab.getPosition()) {
                    case 0 -> PanBenchmarkPlan.Mode.QUICK;
                    case 2 -> PanBenchmarkPlan.Mode.DEEP;
                    default -> PanBenchmarkPlan.Mode.STANDARD;
                };
                updateEstimate();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });
        modeTabs.selectTab(modeTabs.getTabAt(mode == PanBenchmarkPlan.Mode.DEEP ? 2 : mode == PanBenchmarkPlan.Mode.STANDARD ? 1 : 0));
        if (Util.isLeanback()) {
            LinearLayout options = horizontal(Gravity.TOP);
            LinearLayout strengthColumn = vertical(0);
            strengthColumn.addView(text("测试强度", 14, TEXT, true), matchWrap(0, 5));
            strengthColumn.addView(modeTabs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
            options.addView(strengthColumn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            LinearLayout threadColumn = vertical(0);
            threadColumn.addView(text("线程模式", 14, TEXT, true), matchWrap(0, 5));
            LinearLayout.LayoutParams threadColumnParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            threadColumnParams.leftMargin = dp(14);
            options.addView(threadColumn, threadColumnParams);
            content.addView(options, matchWrap(8, 0));
            addThreadSelector(threadColumn);
        } else {
            addSectionTitle("测试强度");
            content.addView(modeTabs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
            addSectionTitle("选择要对比的线程");
            addThreadSelector(null);
        }

        estimate = text("", Util.isLeanback() ? 12 : 13, TEXT, false);
        estimate.setPadding(dp(14), dp(Util.isLeanback() ? 9 : 12), dp(14), dp(Util.isLeanback() ? 9 : 12));
        estimate.setBackground(round(SURFACE, 10, 1, BORDER));
        content.addView(estimate, matchWrap(10, 0));
        updateEstimate();

        if (!Util.isLeanback()) {
            TextView notice = text("为避免播放器与测速争抢同一链路，开始后会暂停播放；结束、取消或失败时自动恢复。请求强制直连，不使用系统代理或电脑翻墙代理。", 12, SECONDARY, false);
            notice.setLineSpacing(dp(2), 1f);
            content.addView(notice, matchWrap(8, 0));
        }

        startButton = button(endpoint == null ? "当前资源不支持诊断" : "开始诊断", true, v -> startTest());
        updateStartEnabled();
        content.addView(startButton, matchHeight(10, 0, Util.isLeanback() ? 50 : 48));
        startButton.postDelayed(() -> {
            updateStartEnabled();
            startButton.requestFocus();
        }, Util.isLeanback() ? 80 : 550);
        scrollToTop();
    }

    private void addThreadSelector(@Nullable ViewGroup tabParent) {
        TabLayout selectorTabs = new TabLayout(requireContext());
        selectorTabs.setTabMode(TabLayout.MODE_FIXED);
        selectorTabs.setTabGravity(TabLayout.GRAVITY_FILL);
        styleTabs(selectorTabs);
        selectorTabs.setTabRippleColor(ColorStateList.valueOf(Color.TRANSPARENT));
        selectorTabs.addTab(selectorTabs.newTab().setText("单线程测试"), false);
        selectorTabs.addTab(selectorTabs.newTab().setText("多组对比"), false);
        ViewGroup target = tabParent == null ? content : tabParent;
        target.addView(selectorTabs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));

        singleThreadPanel = vertical(0);
        LinearLayout singleRow = horizontal(Gravity.CENTER_VERTICAL);
        TextView singleLabel = text(Util.isLeanback() ? "线程数（1～256）" : "线程数", 13, TEXT, true);
        singleRow.addView(singleLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        singleThreadInput = numberInput(String.valueOf(defaultThreads()), "单线程测试值，范围1到256");
        singleThreadInput.addTextChangedListener(new SimpleTextWatcher(this::updateEstimate));
        singleRow.addView(singleThreadInput, new LinearLayout.LayoutParams(dp(160), dp(Util.isLeanback() ? 44 : 48)));
        singleThreadPanel.addView(singleRow, matchWrap(Util.isLeanback() ? 2 : 5, 0));
        if (!Util.isLeanback()) singleThreadPanel.addView(text("默认只执行这一种线程；可直接输入任意 1～256。", 12, SECONDARY, false), matchWrap(4, 0));
        content.addView(singleThreadPanel, matchWrap(0, 0));

        multiThreadPanel = vertical(0);
        multiThreadPanel.setVisibility(View.GONE);
        threadChips = new ChipGroup(requireContext());
        threadChips.setSingleSelection(false);
        threadChips.setSelectionRequired(false);
        threadChips.setChipSpacingHorizontal(dp(7));
        threadChips.setChipSpacingVertical(dp(5));
        threadChipMap.clear();
        if (selectedThreadValues.isEmpty()) selectedThreadValues.add(defaultThreads());
        for (int value : new int[]{1, 2, 4, 8, 16, 32, 64, 128, 256}) addThreadChip(value, selectedThreadValues.contains(value));
        for (int value : new ArrayList<>(selectedThreadValues)) if (!threadChipMap.containsKey(value)) addThreadChip(value, true);
        multiThreadPanel.addView(threadChips, matchWrap(Util.isLeanback() ? 4 : 8, 0));

        customThreadInput = numberInput("", "添加自定义线程，范围1到256");
        customThreadInput.setHint("自定义 1～256");
        MaterialButton add = button("添加", false, v -> addCustomThread());
        if (Util.isLeanback()) {
            LinearLayout tools = horizontal(Gravity.CENTER_VERTICAL);
            tools.addView(customThreadInput, new LinearLayout.LayoutParams(0, dp(44), 3));
            LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(dp(82), dp(44));
            addParams.leftMargin = dp(7);
            tools.addView(add, addParams);
            addShortcut(tools, "仅当前", () -> selectThreads(List.of(defaultThreads())));
            addShortcut(tools, "常用", () -> selectThreads(List.of(1, 4, 8, 16)));
            addShortcut(tools, "全量", () -> selectThreads(List.of(1, 2, 4, 8, 16, 32, 64, 128, 256)));
            addShortcut(tools, "清空", () -> selectThreads(List.of()));
            multiThreadPanel.addView(tools, matchWrap(6, 0));
        } else {
            LinearLayout customRow = horizontal(Gravity.CENTER_VERTICAL);
            customRow.addView(customThreadInput, new LinearLayout.LayoutParams(0, dp(48), 1));
            LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(dp(96), dp(48));
            addParams.leftMargin = dp(9);
            customRow.addView(add, addParams);
            multiThreadPanel.addView(customRow, matchWrap(8, 0));

            LinearLayout shortcuts = horizontal(Gravity.CENTER_VERTICAL);
            addShortcut(shortcuts, "仅当前", () -> selectThreads(List.of(defaultThreads())));
            addShortcut(shortcuts, "常用", () -> selectThreads(List.of(1, 4, 8, 16)));
            addShortcut(shortcuts, "全量", () -> selectThreads(List.of(1, 2, 4, 8, 16, 32, 64, 128, 256)));
            addShortcut(shortcuts, "清空", () -> selectThreads(List.of()));
            multiThreadPanel.addView(shortcuts, matchWrap(8, 0));
        }
        if (!Util.isLeanback()) multiThreadPanel.addView(text("只测试已勾选项；系统不会自动追加 1/2/4/8。", 12, SECONDARY, false), matchWrap(7, 0));
        content.addView(multiThreadPanel, matchWrap(0, 0));

        selectorTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                multiThreadMode = tab.getPosition() == 1;
                singleThreadPanel.setVisibility(multiThreadMode ? View.GONE : View.VISIBLE);
                multiThreadPanel.setVisibility(multiThreadMode ? View.VISIBLE : View.GONE);
                updateEstimate();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });
        selectorTabs.selectTab(selectorTabs.getTabAt(multiThreadMode ? 1 : 0));
    }

    private EditText numberInput(String value, String description) {
        EditText input = new EditText(requireContext());
        input.setText(value);
        input.setTextSize(16);
        input.setTextColor(TEXT);
        input.setHintTextColor(Color.rgb(128, 134, 139));
        input.setGravity(Gravity.CENTER);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(3)});
        input.setBackground(round(Color.WHITE, 8, 1, BORDER));
        input.setContentDescription(description);
        input.setFocusable(true);
        input.setFocusableInTouchMode(true);
        return input;
    }

    private void addThreadChip(int value, boolean checked) {
        Chip chip = new Chip(requireContext());
        chip.setText(String.valueOf(value));
        chip.setCheckable(true);
        chip.setChecked(checked);
        chip.setEnsureMinTouchTargetSize(!Util.isLeanback());
        chip.setMinHeight(dp(Util.isLeanback() ? 40 : 48));
        chip.setMinimumHeight(dp(Util.isLeanback() ? 40 : 48));
        chip.setTextSize(Util.isLeanback() ? 13 : 14);
        chip.setTextColor(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{Color.WHITE, BLUE_DARK}));
        chip.setChipBackgroundColor(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{BLUE, Color.WHITE}));
        chip.setChipStrokeColor(ColorStateList.valueOf(Color.rgb(138, 180, 248)));
        chip.setChipStrokeWidth(dp(1));
        chip.setContentDescription(value + "线程");
        chip.setOnCheckedChangeListener((button, isChecked) -> {
            if (isChecked && !selectedThreadValues.contains(value)) selectedThreadValues.add(value);
            if (!isChecked) selectedThreadValues.remove(Integer.valueOf(value));
            selectedThreadValues.sort(Integer::compareTo);
            updateEstimate();
        });
        threadChipMap.put(value, chip);
        threadChips.addView(chip);
    }

    private void addCustomThread() {
        int value = parseThread(customThreadInput == null ? "" : customThreadInput.getText().toString(), 0);
        if (value <= 0) {
            Notify.show("请输入 1～256 的线程数");
            return;
        }
        Chip chip = threadChipMap.get(value);
        if (chip == null) addThreadChip(value, true);
        else chip.setChecked(true);
        customThreadInput.setText("");
        updateEstimate();
    }

    private void addShortcut(LinearLayout parent, String label, Runnable action) {
        MaterialButton button = button(label, false, v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(46), 1);
        if (parent.getChildCount() > 0) params.leftMargin = dp(7);
        parent.addView(button, params);
    }

    private void selectThreads(List<Integer> values) {
        selectedThreadValues.clear();
        selectedThreadValues.addAll(PanBenchmarkPlan.sanitizeThreads(values));
        if (values.isEmpty()) selectedThreadValues.clear();
        for (Map.Entry<Integer, Chip> entry : threadChipMap.entrySet()) entry.getValue().setChecked(selectedThreadValues.contains(entry.getKey()));
        updateEstimate();
    }

    private void addSummaryCard() {
        LinearLayout card = vertical(Util.isLeanback() ? 3 : 7);
        card.setPadding(dp(13), dp(Util.isLeanback() ? 8 : 10), dp(13), dp(Util.isLeanback() ? 8 : 10));
        card.setBackground(round(Color.rgb(232, 240, 254), 12, 0, Color.TRANSPARENT));
        if (endpoint == null) {
            card.addView(text("未识别到可诊断的 HTTP 播放地址", 15, WARNING, true));
            card.addView(text("请在网盘、HTTP直链或 M3U8 等资源正在播放时打开。", 13, SECONDARY, false));
        } else {
            card.addView(text(endpoint.provider().label() + " · " + player.getPlayerText(), 16, BLUE_DARK, true));
            String route = endpoint.hasDirectUpstream() ? endpoint.playbackHost() + " → " + endpoint.upstreamHost() : endpoint.playbackHost() + "（直链）";
            if (Util.isLeanback()) {
                card.addView(text("链路 " + route + "    ·    资源需求 " + requiredText() + "    ·    Go线程 " + configuredThreadsText(), 13, SECONDARY, false));
            } else {
                card.addView(text("链路  " + route, 13, SECONDARY, false));
                card.addView(text("资源需求  " + requiredText() + "    当前Go线程  " + configuredThreadsText(), 13, SECONDARY, false));
            }
        }
        content.addView(card, matchWrap(0, 0));
    }

    private void startTest() {
        if (endpoint == null || player == null || player.isReleased()) return;
        List<Integer> threads = selectedThreads();
        if (threads.isEmpty()) {
            Notify.show("请至少选择一个线程值");
            return;
        }
        int maxThreads = threads.get(threads.size() - 1);
        long bytes = estimatePlanBytes(threads);
        if ((maxThreads > 64 || bytes >= HIGH_TRAFFIC_CONFIRM_BYTES) && !highThreadConfirmed) {
            confirmHighThread(threads, bytes);
            return;
        }
        highThreadConfirmed = false;
        startConfirmedTest(threads);
    }

    private void confirmHighThread(List<Integer> threads, long bytes) {
        int maxThreads = threads.get(threads.size() - 1);
        new MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_WebHTV_LightDialog)
                .setTitle("确认高流量诊断")
                .setMessage("将测试到 " + maxThreads + " 线程，预计流量上限约 " + PanNetworkDiagnosticRunner.formatBytes(bytes)
                        + "，已计入探测、预热、重复、直链对照、App DataSource、配对验证和各可重试阶段的一次瞬态断流重试。高线程可能增加耗电、内存占用，并触发源站或网盘限流、风控。")
                .setNegativeButton("返回调整", null)
                .setPositiveButton("继续诊断", (dialog, which) -> {
                    highThreadConfirmed = true;
                    startTest();
                })
                .show();
    }

    private void startConfirmedTest(List<Integer> threads) {
        wasPlaying = player.isPlaying();
        if (wasPlaying) {
            player.pause();
            playbackPaused = true;
        }
        running = true;
        showRunning();
        runner.start(new PanNetworkDiagnosticRunner.RequestConfig(
                player.getUrl(), player.getHeaders(), mode, threads, player.getPosition(), player.getDuration(),
                formatBitrate(player.getVideoFormat()), player.getRebufferCount(), player.getRebufferTotalMs(), player.getDroppedFrames()), this);
    }

    private void showRunning() {
        hideResultFooter();
        content.removeAllViews();
        stage = text("准备诊断…", 16, TEXT, true);
        content.addView(stage, matchWrap(2, 0));
        progress = new LinearProgressIndicator(requireContext());
        progress.setIndeterminate(false);
        progress.setMax(1000);
        progress.setProgress(0);
        progress.setTrackThickness(dp(7));
        progress.setIndicatorColor(BLUE);
        progress.setTrackColor(Color.rgb(220, 227, 239));
        content.addView(progress, matchHeight(13, 0, 22));
        liveSpeed = text("0.00 Mbps", 30, BLUE_DARK, true);
        liveSpeed.setGravity(Gravity.CENTER);
        content.addView(liveSpeed, matchWrap(18, 0));
        liveMeta = text("正在建立真实请求…", 13, SECONDARY, false);
        liveMeta.setGravity(Gravity.CENTER);
        content.addView(liveMeta, matchWrap(5, 0));
        graph = new SpeedGraphView(requireContext());
        graph.setContentDescription("实时吞吐趋势图");
        content.addView(graph, matchHeight(18, 0, 150));
        TextView note = text("进度按实际测试轮次计算；鉴权、Range或限流异常会记录到对应层，不会盲目重试，也不会把HTTP错误误判成网速慢。", 12, SECONDARY, false);
        content.addView(note, matchWrap(12, 0));
        MaterialButton cancel = button("取消诊断", false, v -> cancelTest());
        content.addView(cancel, matchHeight(16, 0, 50));
        cancel.post(cancel::requestFocus);
        scrollToTop();
    }

    private void cancelTest() {
        if (runner != null) runner.cancel();
        running = false;
        resumePlayback();
        showConfig();
    }

    @Override
    public void onProgress(PanNetworkDiagnosticRunner.Progress value) {
        if (!running || stage == null) return;
        stage.setText(value.stage());
        int total = Math.max(1, value.totalRounds());
        int base = Math.min(total, value.completedRounds());
        float byteWithin = value.budgetBytes() <= 0 ? 0 : Math.min(1f, (float) value.bytes() / value.budgetBytes());
        long timeLimit = PanBenchmarkPlan.roundTimeLimitMs(mode);
        float timeWithin = value.elapsedMs() <= 0 || timeLimit <= 0 ? 0 : Math.min(1f, (float) value.elapsedMs() / timeLimit);
        float within = Math.max(byteWithin, timeWithin);
        progress.setProgressCompat(Math.min(1000, Math.round((base + within) * 1000f / total)), true);
        liveSpeed.setText(PanNetworkDiagnosticRunner.formatMbps(value.bitsPerSecond()));
        String thread;
        if (value.stage().contains("上游单连接")) thread = " · 单连接直测";
        else if (value.stage().contains("直链并发")) thread = " · " + value.threads() + "路并发";
        else if (value.stage().contains("App DataSource")) thread = " · App供数层";
        else thread = value.threads() <= 0 ? "" : " · " + value.threads() + "线程";
        liveMeta.setText("第 " + Math.min(total, base + 1) + "/" + total + " 轮" + thread
                + " · 本轮 " + PanNetworkDiagnosticRunner.formatDuration(value.elapsedMs())
                + " · 已读取 " + PanNetworkDiagnosticRunner.formatBytes(value.bytes()));
        graph.setSamples(value.samples());
    }

    @Override
    public void onComplete(PanNetworkDiagnosticRunner.Report report) {
        if (!running || !isAdded()) return;
        running = false;
        resumePlayback();
        showResult(report);
    }

    @Override
    public void onCancelled() {
        if (!isAdded()) return;
        running = false;
        resumePlayback();
    }

    @Override
    public void onError(String message) {
        if (!running || !isAdded()) return;
        running = false;
        resumePlayback();
        hideResultFooter();
        content.removeAllViews();
        LinearLayout card = vertical(8);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(round(Color.rgb(253, 235, 236), 12, 1, Color.rgb(242, 184, 181)));
        card.addView(text("诊断已停止", 18, Color.rgb(197, 34, 31), true));
        card.addView(text(message, 14, TEXT, false));
        content.addView(card, matchWrap(4, 0));
        MaterialButton retry = button("返回设置", true, v -> showConfig());
        content.addView(retry, matchHeight(18, 0, 50));
        retry.post(retry::requestFocus);
        scrollToTop();
    }

    private void showResult(PanNetworkDiagnosticRunner.Report report) {
        content.removeAllViews();
        PanDiagnosticVerdict.Result verdict = report.verdict();
        int color = verdict.cause() == PanDiagnosticVerdict.Cause.SUFFICIENT ? SUCCESS : verdict.cause() == PanDiagnosticVerdict.Cause.INCONCLUSIVE ? WARNING : Color.rgb(197, 34, 31);
        LinearLayout verdictCard = vertical(4);
        verdictCard.setPadding(dp(13), dp(10), dp(13), dp(10));
        verdictCard.setBackground(round(withAlpha(color, 22), 12, 1, withAlpha(color, 75)));
        verdictCard.addView(text(resultTitle(report), 18, color, true));
        verdictCard.addView(text(resultExplanation(report), 13, TEXT, false));
        verdictCard.addView(text(report.repeats() + "轮中位数 · 证据稳定性 " + PanNetworkDiagnosticRunner.confidenceText(report.evidenceConfidence())
                + " · 结论置信度 " + PanNetworkDiagnosticRunner.confidenceText(verdict.confidence())
                + " · 安全需求约 " + PanNetworkDiagnosticRunner.formatMbps((long) (report.requiredBitsPerSecond() * 1.25d)), 11, SECONDARY, false));
        content.addView(verdictCard, matchWrap(0, 0));

        addResultSectionTitle("分层证据链");
        addLayer("上游单连接基准", report.upstream().bitsPerSecond(), report.requiredBitsPerSecond(), measurementDetail(report.upstream(), "单个真实HTTP连接；使用独立Range"));
        PanNetworkDiagnosticRunner.Measurement chainProxy = report.appProxy();
        PanNetworkDiagnosticRunner.Measurement direct = chainProxy == null ? report.primaryDirectComparison() : report.directForThreads(chainProxy.threads());
        if (direct != null && direct.threads() > 1) addLayer(direct.label(), direct.bitsPerSecond(), report.requiredBitsPerSecond(), measurementDetail(direct, "App直接并发请求同一上游；不经过Go"));
        if (chainProxy != null && chainProxy.successful()) addLayer("Go代理 · " + chainProxy.threads() + "线程", chainProxy.bitsPerSecond(), report.requiredBitsPerSecond(), measurementDetail(chainProxy, "并发聚合后的完整网络读取；使用独立Range"));
        else if (chainProxy != null) addLayer(chainProxy.label(), 0, report.requiredBitsPerSecond(), measurementDetail(chainProxy, "所选代理线程均未完成测量"));
        else if (!report.proxies().isEmpty()) addLayer(report.proxies().get(0).label(), 0, report.requiredBitsPerSecond(), measurementDetail(report.proxies().get(0), "所选代理线程均未完成测量"));
        else addLayer("Go代理", 0, report.requiredBitsPerSecond(), "当前播放地址为直链，无独立代理层");
        addLayer("App完整链路", report.dataSource().bitsPerSecond(), report.requiredBitsPerSecond(), measurementDetail(report.dataSource(), "播放器同类Media3 DataSource读取；使用独立Range"));
        addObservationRow("播放观察", "重缓冲 " + report.rebufferCount() + " 次 / " + report.rebufferTotalMs() + " ms · 掉帧 " + report.droppedFrames());

        if (report.proxies().size() > 1) {
            addResultSectionTitle("线程对比");
            for (PanNetworkDiagnosticRunner.Measurement item : report.proxies()) {
                String note = item.successful() ? stability(item.samples()) : item.error();
                PanNetworkDiagnosticRunner.Measurement reference = report.directForThreads(item.threads());
                String directValue = reference == null || !reference.successful() ? "直链未完成" : "直链 " + PanNetworkDiagnosticRunner.formatMbps(reference.bitsPerSecond());
                String goValue = item.successful() ? "Go " + PanNetworkDiagnosticRunner.formatMbps(item.bitsPerSecond()) : "Go未完成";
                addMetricRow(item.threads() + " 线程", directValue + " / " + goValue, note);
            }
        }
        if (!report.proxies().isEmpty()) {
            TextView recommendation = text(threadRecommendation(report), 13, BLUE_DARK, true);
            recommendation.setPadding(dp(11), dp(8), dp(11), dp(8));
            recommendation.setBackground(round(Color.rgb(232, 240, 254), 10, 0, Color.TRANSPARENT));
            content.addView(recommendation, matchWrap(7, 0));
        }

        TextView privacy = text("各轮使用远距独立Range，避免Go缓存污染；完整证据可复制查看。", 11, SECONDARY, false);
        privacy.setLineSpacing(dp(2), 1f);
        content.addView(privacy, matchWrap(8, 0));

        showResultFooter(report);
        scrollToTop();
    }

    private void showResultFooter(PanNetworkDiagnosticRunner.Report report) {
        if (resultFooter == null) return;
        resultFooter.removeAllViews();
        MaterialButton copy = button("复制脱敏报告", false, v -> copyReport(report));
        resultFooter.addView(copy, new LinearLayout.LayoutParams(0, dp(44), 1));
        MaterialButton retry = button("重新测试", true, v -> showConfig());
        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(0, dp(44), 1);
        retryParams.leftMargin = dp(10);
        resultFooter.addView(retry, retryParams);
        resultFooter.setVisibility(View.VISIBLE);
        copy.post(copy::requestFocus);
    }

    private void hideResultFooter() {
        if (resultFooter == null) return;
        resultFooter.removeAllViews();
        resultFooter.setVisibility(View.GONE);
    }

    private void scrollToTop() {
        if (contentScroll != null) contentScroll.post(() -> contentScroll.scrollTo(0, 0));
    }

    private void addLayer(String name, long speed, long required, String detail) {
        LinearLayout card = vertical(2);
        card.setPadding(dp(12), dp(8), dp(12), dp(8));
        card.setBackground(round(SURFACE, 10, 1, BORDER));
        LinearLayout top = horizontal(Gravity.CENTER_VERTICAL);
        top.addView(text(name, 13, TEXT, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        String value = speed <= 0 ? "观察项" : PanNetworkDiagnosticRunner.formatMbps(speed);
        int color = speed <= 0 ? SECONDARY : speed >= required * 1.25d ? SUCCESS : speed >= required ? WARNING : Color.rgb(197, 34, 31);
        top.addView(text(value, 13, color, true));
        card.addView(top);
        card.addView(text(detail, 11, SECONDARY, false));
        content.addView(card, matchWrap(5, 0));
    }

    private void addObservationRow(String name, String detail) {
        LinearLayout row = horizontal(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(6), dp(4), dp(3));
        row.addView(text(name, 12, TEXT, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView value = text(detail, 11, SECONDARY, false);
        value.setGravity(Gravity.END);
        row.addView(value);
        content.addView(row);
    }

    private void addMetricRow(String label, String value, String note) {
        LinearLayout row = horizontal(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(8), dp(4), dp(8));
        row.addView(text(label, 13, TEXT, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView metric = text(value + " · " + note, 13, SECONDARY, false);
        metric.setGravity(Gravity.END);
        row.addView(metric);
        content.addView(row);
    }

    private void copyReport(PanNetworkDiagnosticRunner.Report report) {
        ClipboardManager manager = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        manager.setPrimaryClip(ClipData.newPlainText("播放链路诊断报告", report.redactedText()));
        Notify.show("脱敏报告已复制");
    }

    private void updateEstimate() {
        List<Integer> values = selectedThreads();
        updateStartEnabled();
        if (estimate == null) return;
        if (values.isEmpty()) {
            estimate.setText("尚未选择测试线程。请勾选至少一项，或切回单线程测试输入一个值。");
            return;
        }
        int maxThreads = values.get(values.size() - 1);
        int repeats = PanBenchmarkPlan.repeats(mode);
        long bytes = estimatePlanBytes(values);
        int directGroups = 0;
        for (int value : values) if (value > 1) directGroups++;
        boolean proxied = endpoint != null && endpoint.hasDirectUpstream() && !endpoint.playbackUrl().equals(endpoint.upstreamUrl());
        int rounds = repeats * (2 + (proxied ? values.size() + directGroups : 0)) + (proxied && repeats > 1 ? repeats : 0);
        long seconds = 5 + rounds * PanBenchmarkPlan.roundTimeLimitMs(mode) / 1000L;
        String action = values.size() == 1 ? "将测试 " + values.get(0) + " 线程" : "将对比 " + join(values) + " 线程";
        String directLimit = maxThreads > PanBenchmarkPlan.MAX_DIRECT_CONCURRENCY ? "；直链并发基准安全封顶" + PanBenchmarkPlan.MAX_DIRECT_CONCURRENCY + "路" : "";
        String safety = Util.isLeanback() ? " · 开始后暂停播放，结束自动恢复；不使用系统代理" : "";
        estimate.setText(action + " · " + repeats + "轮取中位数" + directLimit + "\n预计流量上限约 " + PanNetworkDiagnosticRunner.formatBytes(bytes)
                + " · 基础耗时约 " + formatTime(seconds) + "（单轮 " + formatTime(PanBenchmarkPlan.roundTimeLimitMs(mode) / 1000L) + "，断流重试可能增加耗时）" + safety);
    }

    private long estimatePlanBytes(List<Integer> threads) {
        long required = formatBitrate(player == null ? null : player.getVideoFormat());
        boolean proxied = endpoint != null && endpoint.hasDirectUpstream() && !endpoint.playbackUrl().equals(endpoint.upstreamUrl());
        int configuredThreads = endpoint == null ? 0 : endpoint.configuredThreads();
        int appThreads = proxied ? Math.max(PanBenchmarkPlan.normalizeThreads(configuredThreads), threads.get(threads.size() - 1))
                : PanBenchmarkPlan.normalizeThreads(configuredThreads);
        return PanBenchmarkPlan.estimatePlannedBytes(required, threads, mode, proxied, appThreads);
    }

    private int defaultThreads() {
        if (endpoint == null || endpoint.configuredThreads() <= 0) return PanBenchmarkPlan.DEFAULT_MAX_THREADS;
        return endpoint.configuredThreads();
    }

    private List<Integer> selectedThreads() {
        if (!multiThreadMode) {
            int value = parseThread(singleThreadInput == null ? "" : singleThreadInput.getText().toString(), 0);
            return value <= 0 ? List.of() : List.of(value);
        }
        if (selectedThreadValues.isEmpty()) return List.of();
        return PanBenchmarkPlan.sanitizeThreads(selectedThreadValues);
    }

    private int parseThread(String text, int fallback) {
        try {
            int value = Integer.parseInt(text.trim());
            if (value < 1 || value > PanBenchmarkPlan.MAX_THREADS) return fallback;
            return value;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private void updateStartEnabled() {
        if (startButton == null) return;
        boolean enabled = endpoint != null && !selectedThreads().isEmpty();
        startButton.setEnabled(enabled);
        startButton.setAlpha(enabled ? 1f : 0.5f);
    }

    private String requiredText() {
        long bitrate = formatBitrate(player == null ? null : player.getVideoFormat());
        return bitrate <= 0 ? "测试后按文件大小计算" : PanNetworkDiagnosticRunner.formatMbps(bitrate);
    }

    private String configuredThreadsText() {
        return endpoint == null || endpoint.configuredThreads() <= 0 ? "未声明" : String.valueOf(endpoint.configuredThreads());
    }

    private static long formatBitrate(@Nullable Format format) {
        if (format == null) return 0;
        if (format.averageBitrate > 0) return format.averageBitrate;
        if (format.peakBitrate > 0) return format.peakBitrate;
        return Math.max(0, format.bitrate);
    }

    private String threadRecommendation(PanNetworkDiagnosticRunner.Report report) {
        PanNetworkDiagnosticRunner.Measurement best = report.bestProxy();
        if (best == null && report.proxies().isEmpty()) return "当前为直链资源，无需调整Go线程。";
        if (best == null) {
            String error = report.proxies().isEmpty() ? "" : report.proxies().get(0).error();
            if (error != null && error.contains("unexpected end of stream")) return "直链可读，但Go本地代理提前断流；建议重试，若重复出现则应排查Go代理。";
            return "所选Go线程均未完成有效测量；请根据HTTP错误检查鉴权、Range或代理请求兼容性。";
        }
        long target = (long) (report.requiredBitsPerSecond() * 1.25d);
        if (report.proxies().size() == 1) {
            if (report.dataSource().bitsPerSecond() >= target) return best.threads() + "线程的App完整链路已达到安全需求，无需盲目继续加线程。";
            return best.threads() + "线程的App完整链路仍低于安全需求；请用多组对比确认增加线程是否能稳定改善。";
        }
        PanNetworkDiagnosticRunner.Measurement efficient = null;
        for (PanNetworkDiagnosticRunner.Measurement item : report.proxies()) {
            if (item.bitsPerSecond() >= target && (efficient == null || item.threads() < efficient.threads())) efficient = item;
        }
        if (efficient != null) return "建议优先使用 " + efficient.threads() + " 线程：已满足安全需求，继续加线程通常只会增加耗电、内存和风控概率。";
        return "本轮最高为 " + best.threads() + " 线程，但仍未稳定达到资源安全需求；单纯继续加线程未必有效。";
    }

    private static String measurementDetail(PanNetworkDiagnosticRunner.Measurement value, String successText) {
        String detail = value.successful() ? successText : value.error();
        if (!value.successful()) return detail + " · " + value.method() + " · 典型用时 " + PanNetworkDiagnosticRunner.formatDuration(value.elapsedMs());
        return detail + " · 首字节 " + PanNetworkDiagnosticRunner.formatLatency(value.firstByteMs())
                + " · 含启动 " + PanNetworkDiagnosticRunner.formatMbps(value.effectiveBitsPerSecond())
                + " · " + value.method();
    }

    private String resultTitle(PanNetworkDiagnosticRunner.Report report) {
        PanDiagnosticVerdict.Cause cause = report.verdict().cause();
        if (cause == PanDiagnosticVerdict.Cause.EXTERNAL_PROXY
                || cause == PanDiagnosticVerdict.Cause.APP_DATA_SOURCE
                || cause == PanDiagnosticVerdict.Cause.MULTIPLE_BOTTLENECKS
                || cause == PanDiagnosticVerdict.Cause.UPSTREAM_CAPACITY
                || cause == PanDiagnosticVerdict.Cause.SINGLE_CONNECTION_LIMIT) return verdictTitle(cause);
        long required = report.requiredBitsPerSecond();
        long app = report.dataSource().bitsPerSecond();
        if (required > 0 && app >= required * 1.25d) return "完整链路达到安全需求";
        if (required > 0 && app >= required) return "可以播放，但安全余量不足";
        PanNetworkDiagnosticRunner.Measurement chainProxy = report.appProxy();
        if (chainProxy != null && report.upstream().bitsPerSecond() > 0 && chainProxy.bitsPerSecond() > report.upstream().bitsPerSecond() * 1.25d) return "并发提升有效，但链路仍不足";
        return verdictTitle(report.verdict().cause());
    }

    private String resultExplanation(PanNetworkDiagnosticRunner.Report report) {
        long required = report.requiredBitsPerSecond();
        PanNetworkDiagnosticRunner.Measurement best = report.appProxy();
        PanNetworkDiagnosticRunner.Measurement direct = best == null ? null : report.directForThreads(best.threads());
        long app = report.dataSource().bitsPerSecond();
        StringBuilder text = new StringBuilder();
        if (direct != null && direct.bitsPerSecond() > 0 && report.upstream().bitsPerSecond() > 0) {
            double ratio = (double) direct.bitsPerSecond() / report.upstream().bitsPerSecond();
            text.append("直链并发相对单连接").append(ratioText(ratio)).append("；");
        }
        if (best != null && direct != null && direct.bitsPerSecond() > 0 && best.bitsPerSecond() > 0) {
            text.append("Go保留直链并发能力的").append(percent(best.bitsPerSecond(), direct.bitsPerSecond())).append("；");
        }
        if (best != null && best.bitsPerSecond() > 0 && app > 0) {
            double appRatio = (double) app / best.bitsPerSecond();
            if (appRatio > 1.35d) text.append("App样本高于Go ").append(percent(app - best.bitsPerSecond(), best.bitsPerSecond())).append("，存在时间波动或突发缓冲，本轮不用于给App层定责。 ");
            else text.append("App保留Go供数的").append(percent(app, best.bitsPerSecond())).append("。 ");
        }
        if (required > 0 && app > 0) {
            if (app >= required * 1.25d) text.append("App完整链路已达到安全需求。");
            else if (app >= required) text.append("App完整链路达到最低需求，但没有25%安全余量。");
            else text.append("App完整链路仍低于资源最低需求，持续播放有卡顿风险。");
        } else {
            text.append(report.verdict().reason()).append('。');
        }
        return text.toString();
    }

    private static String ratioText(double ratio) {
        if (ratio <= 0 || Double.isNaN(ratio) || Double.isInfinite(ratio)) return "";
        return String.format(Locale.getDefault(), "提升 %.1f 倍", ratio);
    }

    private static String percent(long value, long reference) {
        if (value <= 0 || reference <= 0) return "未知";
        return String.format(Locale.getDefault(), "%.0f%%", value * 100d / reference);
    }

    private static String stability(List<Long> samples) {
        if (samples == null || samples.size() < 3) return "样本较少";
        double mean = 0;
        for (long value : samples) mean += value;
        mean /= samples.size();
        double variance = 0;
        for (long value : samples) variance += Math.pow(value - mean, 2);
        double cv = mean <= 0 ? 1 : Math.sqrt(variance / samples.size()) / mean;
        if (cv < 0.12) return "稳定";
        if (cv < 0.28) return "有波动";
        return "波动较大";
    }

    private static String verdictTitle(PanDiagnosticVerdict.Cause cause) {
        return switch (cause) {
            case DEVICE_NETWORK -> "本机公共网络不足";
            case UPSTREAM_PROVIDER -> "上游源站或节点受限";
            case EXTERNAL_PROXY -> "Go代理层是主要瓶颈";
            case APP_DATA_SOURCE -> "App供数层存在明显损耗";
            case UPSTREAM_CAPACITY -> "瓶颈位于Go之前";
            case MULTIPLE_BOTTLENECKS -> "检测到多重瓶颈";
            case SINGLE_CONNECTION_LIMIT -> "上游单连接受限，并发可改善";
            case PLAYER_BUFFERING -> "播放器缓冲策略需要排查";
            case DECODE_RENDER -> "解码或渲染性能不足";
            case SUFFICIENT -> "本次完整链路满足播放需求";
            case INCONCLUSIVE -> "本次未定位到明确瓶颈";
        };
    }

    private void resumePlayback() {
        if (!playbackPaused) return;
        playbackPaused = false;
        if (wasPlaying && player != null && !player.isReleased()) player.play();
    }

    @Override
    public void onDestroy() {
        running = false;
        if (runner != null) runner.release();
        resumePlayback();
        super.onDestroy();
    }

    private void addSectionTitle(String value) {
        TextView title = text(value, 14, TEXT, true);
        content.addView(title, matchWrap(Util.isLeanback() ? 8 : 12, Util.isLeanback() ? 3 : 5));
    }

    private void addResultSectionTitle(String value) {
        TextView title = text(value, 13, TEXT, true);
        content.addView(title, matchWrap(8, 2));
    }

    private MaterialButton button(String label, boolean primary, View.OnClickListener listener) {
        MaterialButton button = new MaterialButton(requireContext());
        button.setText(label);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setSingleLine(true);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPaddingRelative(dp(6), 0, dp(6), 0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setInsetLeft(0);
        button.setInsetRight(0);
        button.setCornerRadius(dp(9));
        button.setFocusable(true);
        button.setFocusableInTouchMode(Util.isLeanback());
        button.setTextColor(ColorStateList.valueOf(primary ? Color.WHITE : BLUE_DARK));
        button.setBackgroundTintList(ColorStateList.valueOf(primary ? BLUE : Color.WHITE));
        button.setStrokeColor(ColorStateList.valueOf(primary ? BLUE : Color.rgb(138, 180, 248)));
        button.setStrokeWidth(dp(1));
        button.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? 1.035f : 1f).scaleY(focused ? 1.035f : 1f).setDuration(100).start();
            if (!primary) {
                button.setTextColor(ColorStateList.valueOf(focused ? Color.WHITE : BLUE_DARK));
                button.setBackgroundTintList(ColorStateList.valueOf(focused ? BLUE : Color.WHITE));
            }
        });
        button.setOnClickListener(listener);
        return button;
    }

    private LinearLayout vertical(int dividerPadding) {
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout horizontal(int gravity) {
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(gravity);
        return layout;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(requireContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        view.setLineSpacing(dp(2), 1f);
        return view;
    }

    private GradientDrawable round(int color, int radiusDp, int strokeDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) drawable.setStroke(dp(strokeDp), strokeColor);
        return drawable;
    }

    private void styleTabs(TabLayout tabs) {
        tabs.setSelectedTabIndicatorColor(BLUE);
        tabs.setTabTextColors(SECONDARY, BLUE);
        if (Util.isLeanback()) tabs.setBackground(round(SURFACE, 9, 1, BORDER));
    }

    private LinearLayout.LayoutParams matchWrap(int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(top);
        params.bottomMargin = dp(bottom);
        return params;
    }

    private LinearLayout.LayoutParams matchHeight(int top, int bottom, int height) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(height));
        params.topMargin = dp(top);
        params.bottomMargin = dp(bottom);
        return params;
    }

    private int dp(int value) {
        return ResUtil.dp2px(value);
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static String join(List<Integer> values) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) result.append('/');
            result.append(values.get(i));
        }
        return result.toString();
    }

    private static String formatTime(long seconds) {
        if (seconds < 60) return seconds + " 秒";
        return (seconds / 60) + " 分 " + (seconds % 60) + " 秒";
    }

    private static final class SimpleTextWatcher implements android.text.TextWatcher {
        private final Runnable callback;

        private SimpleTextWatcher(Runnable callback) {
            this.callback = callback;
        }

        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { callback.run(); }
        @Override public void afterTextChanged(android.text.Editable s) { }
    }

    private static final class SpeedGraphView extends View {

        private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private List<Long> samples = new ArrayList<>();

        private SpeedGraphView(Context context) {
            super(context);
            grid.setColor(Color.rgb(232, 234, 237));
            grid.setStrokeWidth(1f);
            line.setColor(BLUE);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(context.getResources().getDisplayMetrics().density * 2.2f);
            line.setStrokeCap(Paint.Cap.ROUND);
            line.setStrokeJoin(Paint.Join.ROUND);
            fill.setColor(Color.argb(34, Color.red(BLUE), Color.green(BLUE), Color.blue(BLUE)));
            fill.setStyle(Paint.Style.FILL);
            setBackground(roundStatic(Color.rgb(248, 249, 250), context.getResources().getDisplayMetrics().density * 10, Color.rgb(218, 220, 224)));
        }

        private void setSamples(List<Long> values) {
            samples = values == null ? new ArrayList<>() : new ArrayList<>(values);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float pad = getResources().getDisplayMetrics().density * 12;
            float left = pad;
            float right = getWidth() - pad;
            float top = pad;
            float bottom = getHeight() - pad;
            for (int i = 1; i <= 3; i++) {
                float y = top + (bottom - top) * i / 4f;
                canvas.drawLine(left, y, right, y, grid);
            }
            if (samples.size() < 2) return;
            long max = 1;
            for (long sample : samples) max = Math.max(max, sample);
            Path path = new Path();
            for (int i = 0; i < samples.size(); i++) {
                float x = left + (right - left) * i / (samples.size() - 1f);
                float y = bottom - (bottom - top) * samples.get(i) / max;
                if (i == 0) path.moveTo(x, y);
                else path.lineTo(x, y);
            }
            canvas.drawPath(path, line);
            Path area = new Path(path);
            area.lineTo(right, bottom);
            area.lineTo(left, bottom);
            area.close();
            canvas.drawPath(area, fill);
            canvas.drawPath(path, line);
        }

        private static GradientDrawable roundStatic(int color, float radius, int strokeColor) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(color);
            drawable.setCornerRadius(radius);
            drawable.setStroke(1, strokeColor);
            return drawable;
        }
    }
}
