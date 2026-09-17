package com.fongmi.android.tv.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class WebViewDataDirectoryGuardTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void zombieOwnerLockIsRemoved() throws Exception {
        File dataDir = temporaryFolder.newFolder("data");
        File procRoot = temporaryFolder.newFolder("proc");
        File lock = writeLock(dataDir, 2293, "com.silent.android.webhtv");
        writeProcess(procRoot, 2293, "Z", "com.silent.android.webhtv");

        assertTrue(WebViewDataDirectoryGuard.clearStaleLock(dataDir, procRoot, 8088));
        assertFalse(lock.exists());
    }

    @Test
    public void missingOwnerLockIsRemoved() throws Exception {
        File dataDir = temporaryFolder.newFolder("data");
        File procRoot = temporaryFolder.newFolder("proc");
        File lock = writeLock(dataDir, 2293, "com.silent.android.webhtv");

        assertTrue(WebViewDataDirectoryGuard.clearStaleLock(dataDir, procRoot, 8088));
        assertFalse(lock.exists());
    }

    @Test
    public void liveOwnerLockIsPreserved() throws Exception {
        File dataDir = temporaryFolder.newFolder("data");
        File procRoot = temporaryFolder.newFolder("proc");
        File lock = writeLock(dataDir, 2293, "com.silent.android.webhtv");
        writeProcess(procRoot, 2293, "S", "com.silent.android.webhtv");

        assertFalse(WebViewDataDirectoryGuard.clearStaleLock(dataDir, procRoot, 8088));
        assertTrue(lock.exists());
    }

    @Test
    public void currentProcessLockIsPreserved() throws Exception {
        File dataDir = temporaryFolder.newFolder("data");
        File procRoot = temporaryFolder.newFolder("proc");
        File lock = writeLock(dataDir, 8088, "com.silent.android.webhtv");

        assertFalse(WebViewDataDirectoryGuard.clearStaleLock(dataDir, procRoot, 8088));
        assertTrue(lock.exists());
    }

    @Test
    public void reusedPidLockIsRemoved() throws Exception {
        File dataDir = temporaryFolder.newFolder("data");
        File procRoot = temporaryFolder.newFolder("proc");
        File lock = writeLock(dataDir, 2293, "com.silent.android.webhtv");
        writeProcess(procRoot, 2293, "S", "other.process");

        assertTrue(WebViewDataDirectoryGuard.clearStaleLock(dataDir, procRoot, 8088));
        assertFalse(lock.exists());
    }

    private static File writeLock(File dataDir, int pid, String processName) throws Exception {
        File directory = new File(dataDir, "app_webview");
        assertTrue(directory.mkdirs());
        File lock = new File(directory, "webview_data.lock");
        try (DataOutputStream output = new DataOutputStream(new FileOutputStream(lock))) {
            output.writeInt(pid);
            output.writeUTF(processName);
        }
        return lock;
    }

    private static void writeProcess(File procRoot, int pid, String state, String processName) throws Exception {
        File directory = new File(procRoot, String.valueOf(pid));
        assertTrue(directory.mkdirs());
        Files.writeString(new File(directory, "status").toPath(), "Name:\ttest\nState:\t" + state + " (state)\n", StandardCharsets.UTF_8);
        Files.write(new File(directory, "cmdline").toPath(), (processName + "\0").getBytes(StandardCharsets.UTF_8));
    }
}
