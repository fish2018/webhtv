package com.fongmi.android.tv.ui.activity;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.parsers.DocumentBuilderFactory;

public class CrashActivityDetailsTest {

    @Test
    public void foregroundCrashAlwaysShowsTheCustomRecoveryScreen() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/Startup.java");
        assertTrue("Foreground crashes must open CrashActivity instead of silently terminating",
                source.contains("backgroundMode(CaocConfig.BACKGROUND_MODE_SHOW_CUSTOM)"));
        assertTrue(source.contains("errorActivity(CrashActivity.class)"));
    }

    @Test
    public void errorDetailsUseTheFullBuildVersionShownByAbout() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/ui/activity/CrashActivity.java");
        int message = source.indexOf(".setMessage(getString(R.string.crash_details_message");
        int version = source.indexOf("AppVersion.fullName()", message);
        int stackTrace = source.indexOf("CustomActivityOnCrash.getAllErrorDetailsFromIntent(this, getIntent())", version);

        assertTrue("Crash details must prepend the About screen's timestamped version to the stack trace",
                message >= 0 && version > message && stackTrace > version);
    }

    @Test
    public void crashScreenProvidesSeparateNormalAndSilentRestartActions() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/ui/activity/CrashActivity.java");
        assertTrue(source.contains("mBinding.restart.setOnClickListener"));
        assertTrue(source.contains("mBinding.silentRestart.setOnClickListener"));
        int silentClick = source.indexOf("mBinding.silentRestart.setOnClickListener");
        int arm = source.indexOf("CrashRestartMode.arm()", silentClick);
        int restart = source.indexOf("restartApplication()", arm);
        assertTrue("Silent restart must arm one-shot mode before restarting", silentClick >= 0 && arm > silentClick && restart > arm);
        assertTrue(source.contains("CustomActivityOnCrash.restartApplication"));
    }

    @Test
    public void bothHomeVariantsConsumeCrashModeBeforeLoadingConfig() throws Exception {
        assertCrashModeBeforeConfig("app/src/mobile/java/com/fongmi/android/tv/ui/activity/HomeActivity.java");
        assertCrashModeBeforeConfig("app/src/leanback/java/com/fongmi/android/tv/ui/activity/HomeActivity.java");
    }

    @Test
    public void crashRestartMarkerIsCommittedBeforeTheProcessIsKilled() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/utils/CrashRestartMode.java");
        int arm = source.indexOf("public static void arm()");
        int put = source.indexOf("putBoolean(KEY, true)", arm);
        int commit = source.indexOf(".commit()", put);
        assertTrue("The one-shot marker must be committed synchronously before restart kills the process",
                arm >= 0 && put > arm && commit > put);
    }

    private static void assertCrashModeBeforeConfig(String file) throws Exception {
        String source = read(file);
        int consume = source.indexOf("CrashRestartMode.consume()");
        int load = source.indexOf(".load(getCallback())", consume);
        assertTrue(file + " must consume crash restart mode before loading remote config", consume >= 0 && load > consume);
    }

    @Test
    public void localizedMessagesPlaceVersionBeforeStackTrace() throws Exception {
        assertVersionBeforeStackTrace("app/src/main/res/values/strings.xml");
        assertVersionBeforeStackTrace("app/src/main/res/values-zh-rCN/strings.xml");
        assertVersionBeforeStackTrace("app/src/main/res/values-zh-rTW/strings.xml");
    }

    private static void assertVersionBeforeStackTrace(String file) throws Exception {
        String message = readStringResource(file, "crash_details_message");
        int version = message.indexOf("%1$s");
        int stackTrace = message.indexOf("%2$s");
        assertTrue(file + " must place the full version before the stack trace",
                version >= 0 && stackTrace > version);
    }

    private static String readStringResource(String file, String name) throws Exception {
        Path root = Files.exists(Path.of("app")) ? Path.of("") : Path.of("..");
        NodeList strings = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(root.resolve(file).toFile())
                .getElementsByTagName("string");
        for (int i = 0; i < strings.getLength(); i++) {
            Element item = (Element) strings.item(i);
            if (name.equals(item.getAttribute("name"))) return item.getTextContent();
        }
        throw new AssertionError("Missing string resource " + name + " in " + file);
    }

    private static String read(String file) throws Exception {
        Path root = Files.exists(Path.of("app")) ? Path.of("") : Path.of("..");
        return Files.readString(root.resolve(file), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
