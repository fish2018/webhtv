package com.fongmi.android.tv.ui.dialog;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class PanNetworkDiagnosticDialogSourceTest {

    @Test
    public void highTrafficConfirmationUsesSharedEstimateAndLifecycleGuards() throws Exception {
        String source = read(sourcePath("main", "java", "com", "fongmi", "android", "tv", "ui", "dialog", "PanNetworkDiagnosticDialog.java"));
        String show = methodBody(source, "public static void show(FragmentActivity activity, PlayerManager player)", "@NonNull");
        String startTest = methodBody(source, "private void startTest()", "private void confirmHighThread");
        String confirm = methodBody(source, "private void confirmHighThread(List<Integer> threads, long bytes)", "private void startConfirmedTest");
        String update = methodBody(source, "private void updateEstimate()", "private int defaultThreads()");

        assertTrue(show.contains("activity.isFinishing()"));
        assertTrue(show.contains("activity.isDestroyed()"));
        assertTrue(show.contains("fragmentManager.isStateSaved()"));
        assertTrue(startTest.contains("estimatePlanBytes(threads)"));
        assertTrue(startTest.contains("bytes >= HIGH_TRAFFIC_CONFIRM_BYTES"));
        assertTrue(confirm.contains("预计流量上限约"));
        assertTrue(confirm.contains("预热"));
        assertTrue(update.contains("estimatePlanBytes(values)"));
        assertTrue(update.contains("预计流量上限约"));
    }

    @Test
    public void mobileDelayPopupChecksLifecycleBeforePostingDiagnostic() throws Exception {
        String source = read(sourcePath("mobile", "java", "com", "fongmi", "android", "tv", "ui", "dialog", "ControlDialog.java"));
        String method = methodBody(source, "private void onPanDiagnostic()", "private void setImmersiveAudio()");

        assertTrue(method.contains("FragmentManager fragmentManager = activity.getSupportFragmentManager();"));
        assertTrue(method.contains("activity.isFinishing()"));
        assertTrue(method.contains("activity.isDestroyed()"));
        assertTrue(method.contains("fragmentManager.isStateSaved()"));
        assertTrue(method.contains("current.isReleased()"));
        assertTrue(method.contains("App.post(() -> {"));
        assertTrue(method.contains("PanNetworkDiagnosticDialog.show(activity, current);"));
    }

    private static String methodBody(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from);
        assertTrue("Missing method: " + start, from >= 0);
        assertTrue("Missing method boundary: " + end, to > from);
        return source.substring(from, to);
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path sourcePath(String... parts) {
        Path path = Path.of("src", parts[0], parts[1]);
        for (int i = 2; i < parts.length; i++) path = path.resolve(parts[i]);
        if (Files.exists(path)) return path;
        path = Path.of("app", "src", parts[0], parts[1]);
        for (int i = 2; i < parts.length; i++) path = path.resolve(parts[i]);
        return path;
    }
}
