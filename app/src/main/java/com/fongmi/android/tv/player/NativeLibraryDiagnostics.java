package com.fongmi.android.tv.player;

import com.fongmi.android.tv.App;
import com.github.catvod.crawler.DebugLogStore;
import com.github.catvod.crawler.diagnostics.DiagnosticEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Reads only this process's loaded code, off the player/UI thread. Never loads a library. */
public final class NativeLibraryDiagnostics {
    private static final AtomicBoolean pending = new AtomicBoolean();
    private static final java.util.concurrent.ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "diagnostic-library-inventory"); thread.setDaemon(true); return thread;
    });
    private static final LinkedHashMap<String, Identity> identities = new LinkedHashMap<>();
    private record Mapping(File file, long offset) {}
    private record Library(File file, String name, String source, long offset, long size, String asset) {}
    private record Identity(String abi, String buildId, String actual, String expected) {}
    private NativeLibraryDiagnostics() {}

    public static void request() {
        if (!DebugLogStore.acceptsEvent("env.native") || !pending.compareAndSet(false, true)) return;
        worker.schedule(() -> {
            try {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                if (DebugLogStore.acceptsEvent("env.native")) capture();
            } catch (Exception ignored) { DebugLogStore.collectorFailure(); }
            finally { pending.set(false); }
        }, 1500, TimeUnit.MILLISECONDS);
    }

    public static void failure(String name, Throwable error) {
        if (!DebugLogStore.acceptsEvent("env.native")) return;
        DebugLogStore.event(new DiagnosticEvent("env.native", "none", "process", 0, 0).severity("error")
                .observed("library", name).observed("loadResult", "failed").observed("javaClass", error.getClass().getName()).message(error.getMessage()));
    }

    private static void capture() throws Exception {
        android.content.pm.ApplicationInfo info = App.get().getApplicationInfo();
        Set<String> apks = new LinkedHashSet<>(); apks.add(info.sourceDir);
        if (info.splitSourceDirs != null) java.util.Collections.addAll(apks, info.splitSourceDirs);
        List<Mapping> mappings = new ArrayList<>(); Set<String> files = new LinkedHashSet<>();
        List<Library> libraries = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/maps"))) {
            String line; int lines = 0;
            while ((line = reader.readLine()) != null && ++lines <= 32768) {
                String[] fields = line.split("\\s+", 6);
                if (fields.length < 6 || !fields[1].contains("x")) continue;
                String path = fields[5];
                if (apks.contains(path)) mappings.add(new Mapping(new File(path), Long.parseLong(fields[2], 16)));
                else if (path.endsWith(".so") && (path.startsWith(info.nativeLibraryDir + "/") || path.startsWith(info.dataDir + "/app_mpv-libs/")) && files.add(path)) {
                    File file = new File(path);
                    String asset = path.startsWith(info.dataDir + "/app_mpv-libs/") ? "mpv-libs/" + file.getParentFile().getName() + "/" + file.getName() : null;
                    libraries.add(new Library(file, file.getName(), asset == null ? "nativeLibraryDir" : "extracted-mpv-asset", 0, file.length(), asset));
                }
            }
        }
        for (String path : apks) apkLibraries(new File(path), mappings, libraries);
        int count = 0;
        for (Library library : libraries) {
            if (!DebugLogStore.acceptsEvent("env.native") || count++ >= 128) break;
            String key = library.file + ":" + library.offset + ":" + library.size + ":" + library.file.lastModified();
            Identity identity = identities.get(key);
            try {
                if (identity == null) {
                    identity = identify(library);
                    if (identities.size() >= 128) identities.remove(identities.keySet().iterator().next());
                    identities.put(key, identity);
                }
                DiagnosticEvent event = new DiagnosticEvent("env.native", "none", "process", 0, 0)
                        .observed("library", library.name).observed("source", library.source).observed("loadResult", "executable-mapping-observed")
                        .observed("abi", identity.abi).observed("buildId", identity.buildId).observed("actualDigest", identity.actual)
                        .observed("expectedDigest", identity.expected).observed("manifestMatch", identity.expected == null ? null : identity.expected.equals(identity.actual))
                        .observed("config", library.asset == null ? null : com.fongmi.android.tv.BuildConfig.MPV_NATIVE_SOURCES)
                        .observed("metricScope", "own process executable mappings and backing ELF/package bytes; not memory-content hashing")
                        .pin("library-" + library.name);
                DebugLogStore.event(event);
            } catch (Exception error) {
                DebugLogStore.event(new DiagnosticEvent("env.native", "none", "process", 0, 0).observed("library", library.name)
                        .observed("loadResult", "executable-mapping-observed").unknown("buildId", DiagnosticEvent.Status.READ_ERROR));
            }
        }
        if (libraries.isEmpty()) DebugLogStore.event(new DiagnosticEvent("env.native", "none", "process", 0, 0)
                .observed("count", 0).observed("reason", "no app-owned executable library mapping at capture"));
    }

    /** Android maps uncompressed APK entries directly. Match executable offsets, never package presence. */
    private static void apkLibraries(File apk, List<Mapping> mappings, List<Library> libraries) throws Exception {
        if (mappings.stream().noneMatch(mapping -> mapping.file.equals(apk))) return;
        try (RandomAccessFile file = new RandomAccessFile(apk, "r")) {
            int tailLength = (int) Math.min(65557, file.length());
            byte[] tail = new byte[tailLength]; file.seek(file.length() - tailLength); file.readFully(tail);
            ByteBuffer end = ByteBuffer.wrap(tail).order(ByteOrder.LITTLE_ENDIAN); int offset = tailLength - 22;
            while (offset >= 0 && end.getInt(offset) != 0x06054b50) offset--;
            if (offset < 0) return;
            int entries = Short.toUnsignedInt(end.getShort(offset + 10));
            long cursor = Integer.toUnsignedLong(end.getInt(offset + 16));
            for (int i = 0; i < entries && i < 65535; i++) {
                if (cursor < 0 || cursor + 46 > file.length()) break;
                file.seek(cursor); byte[] header = new byte[46]; file.readFully(header);
                ByteBuffer h = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
                if (h.getInt(0) != 0x02014b50) break;
                int nameLength = Short.toUnsignedInt(h.getShort(28)), extra = Short.toUnsignedInt(h.getShort(30)), comment = Short.toUnsignedInt(h.getShort(32));
                byte[] nameBytes = new byte[nameLength]; file.readFully(nameBytes);
                String name = new String(nameBytes, java.nio.charset.StandardCharsets.UTF_8);
                cursor += 46L + nameLength + extra + comment;
                if (h.getShort(10) != 0 || !name.startsWith("lib/") || !name.endsWith(".so")) continue;
                long local = Integer.toUnsignedLong(h.getInt(42));
                if (local + 30 > file.length()) continue;
                file.seek(local); byte[] localHeader = new byte[30]; file.readFully(localHeader);
                ByteBuffer l = ByteBuffer.wrap(localHeader).order(ByteOrder.LITTLE_ENDIAN);
                if (l.getInt(0) != 0x04034b50) continue;
                long start = local + 30 + Short.toUnsignedInt(l.getShort(26)) + Short.toUnsignedInt(l.getShort(28));
                long size = Integer.toUnsignedLong(h.getInt(24));
                if (size <= 0 || size > (128L << 20) || start + size > file.length()) continue;
                if (mappings.stream().anyMatch(mapping -> mapping.file.equals(apk) && mapping.offset >= start && mapping.offset < start + size))
                    libraries.add(new Library(apk, name.substring(name.lastIndexOf('/') + 1), "mapped-apk-entry:" + name, start, size, null));
            }
        }
    }

    private static Identity identify(Library library) throws Exception {
        if (library.size < 64 || library.size > (128L << 20)) throw new java.io.IOException("ELF budget");
        try (RandomAccessFile file = new RandomAccessFile(library.file, "r")) {
            file.seek(library.offset); byte[] bytes = new byte[64]; file.readFully(bytes);
            if (bytes[0] != 0x7f || bytes[1] != 'E' || bytes[2] != 'L' || bytes[3] != 'F') throw new java.io.IOException("ELF magic");
            ByteBuffer header = ByteBuffer.wrap(bytes).order(bytes[5] == 2 ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
            boolean wide = bytes[4] == 2; int machine = Short.toUnsignedInt(header.getShort(18));
            String abi = machine == 183 ? "arm64-v8a" : machine == 40 ? "armeabi-v7a" : "elf-machine-" + machine;
            long phoff = wide ? header.getLong(32) : Integer.toUnsignedLong(header.getInt(28));
            int phsize = Short.toUnsignedInt(header.getShort(wide ? 54 : 42)), count = Short.toUnsignedInt(header.getShort(wide ? 56 : 44));
            String buildId = null;
            if (phsize >= (wide ? 56 : 32) && phsize <= 256 && count <= 256 && phoff >= 0 && phoff + (long) phsize * count <= library.size)
                for (int i = 0; i < count; i++) {
                    file.seek(library.offset + phoff + (long) i * phsize); byte[] ph = new byte[phsize]; file.readFully(ph);
                    ByteBuffer p = ByteBuffer.wrap(ph).order(header.order()); if (p.getInt() != 4) continue;
                    long noteOffset = wide ? p.getLong(8) : Integer.toUnsignedLong(p.getInt(4));
                    long size = wide ? p.getLong(32) : Integer.toUnsignedLong(p.getInt(16));
                    if (size < 12 || size > 65536 || noteOffset < 0 || noteOffset + size > library.size) continue;
                    byte[] note = new byte[(int) size]; file.seek(library.offset + noteOffset); file.readFully(note);
                    ByteBuffer n = ByteBuffer.wrap(note).order(header.order());
                    for (int at = 0; at + 12 <= note.length;) {
                        int names = n.getInt(at), data = n.getInt(at + 4), type = n.getInt(at + 8);
                        if (names < 0 || names > 256 || data < 0 || data > 4096) break;
                        int value = at + 12 + ((names + 3) & ~3), next = value + ((data + 3) & ~3);
                        if (next > note.length) break;
                        if (type == 3 && names == 4 && data > 0 && data <= 64 && note[at + 12] == 'G' && note[at + 13] == 'N' && note[at + 14] == 'U')
                            buildId = hex(java.util.Arrays.copyOfRange(note, value, value + data));
                        at = next;
                    }
                }
            MessageDigest digest = MessageDigest.getInstance("SHA-256"); byte[] buffer = new byte[16384]; long remaining = library.size;
            file.seek(library.offset);
            while (remaining > 0) { int length = file.read(buffer, 0, (int) Math.min(buffer.length, remaining)); if (length < 0) throw new java.io.EOFException(); digest.update(buffer, 0, length); remaining -= length; }
            String actual = hex(digest.digest()), expected = null;
            if (library.asset != null) try (InputStream input = App.get().getAssets().open(library.asset)) {
                digest.reset(); int length; long size = 0;
                while ((length = input.read(buffer)) >= 0) { if ((size += length) > (128L << 20)) throw new java.io.IOException("asset budget"); digest.update(buffer, 0, length); }
                expected = hex(digest.digest());
            }
            else if (library.source.startsWith("mapped-apk-entry:")) {
                // The executable map points into this exact packaged ELF; no extracted copy exists.
                expected = actual;
            } else {
                android.content.pm.ApplicationInfo info = App.get().getApplicationInfo();
                List<String> packages = new ArrayList<>(); packages.add(info.sourceDir);
                if (info.splitSourceDirs != null) java.util.Collections.addAll(packages, info.splitSourceDirs);
                for (String path : packages) try (java.util.zip.ZipFile archive = new java.util.zip.ZipFile(path)) {
                    java.util.zip.ZipEntry entry = archive.getEntry("lib/" + abi + "/" + library.name);
                    if (entry == null || entry.getSize() != library.size) continue;
                    digest.reset();
                    try (InputStream input = archive.getInputStream(entry)) {
                        int length; long size = 0;
                        while ((length = input.read(buffer)) >= 0) {
                            if ((size += length) > (128L << 20)) throw new java.io.IOException("package budget");
                            digest.update(buffer, 0, length);
                        }
                    }
                    expected = hex(digest.digest()); break;
                }
            }
            return new Identity(abi, buildId, actual, expected);
        }
    }
    public static String digestText(String value) {
        try { return hex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException error) { return "unavailable"; }
    }
    private static String hex(byte[] bytes) { StringBuilder value = new StringBuilder(bytes.length * 2); for (byte item : bytes) value.append(Character.forDigit((item >>> 4) & 15, 16)).append(Character.forDigit(item & 15, 16)); return value.toString(); }
}
