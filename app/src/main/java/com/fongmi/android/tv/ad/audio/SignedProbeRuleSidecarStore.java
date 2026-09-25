package com.fongmi.android.tv.ad.audio;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

public final class SignedProbeRuleSidecarStore {

    private static final int STATE_SCHEMA_VERSION = 1;
    private static final int MAX_STATE_BYTES = 4 * 1024;
    private static final Pattern INTEGER = Pattern.compile("[0-9]+");
    private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");
    private static final ConcurrentMap<Path, Object> DIRECTORY_LOCKS = new ConcurrentHashMap<>();

    private final Path directory;
    private final Path currentFile;
    private final Path previousFile;
    private final Path stateFile;
    private final Path currentTemp;
    private final Path previousTemp;
    private final Path stateTemp;
    private final SignedProbeRuleSidecarVerifier verifier;
    private final String packageId;
    private final Object operationLock;

    public SignedProbeRuleSidecarStore(Path directory,
                                       SignedProbeRuleSidecarVerifier verifier) {
        if (directory == null || verifier == null) {
            throw new IllegalArgumentException("directory and verifier are required");
        }
        this.directory = directory;
        this.currentFile = directory.resolve("current.json");
        this.previousFile = directory.resolve("previous.json");
        this.stateFile = directory.resolve("state.json");
        this.currentTemp = directory.resolve("current.tmp");
        this.previousTemp = directory.resolve("previous.tmp");
        this.stateTemp = directory.resolve("state.tmp");
        this.verifier = verifier;
        this.packageId = verifier.expectedPackageId();
        this.operationLock = DIRECTORY_LOCKS.computeIfAbsent(
                directory.toAbsolutePath().normalize(), ignored -> new Object());
    }

    String packageId() {
        return packageId;
    }

    public InstallResult install(byte[] signedPackage) throws SignedRulePackageException {
        synchronized (operationLock) {
            return installLocked(signedPackage);
        }
    }

    private InstallResult installLocked(byte[] signedPackage)
            throws SignedRulePackageException {
        byte[] candidateBytes = signedPackage == null ? null : signedPackage.clone();
        VerifiedProbeRuleSidecarPackage candidate =
                verifier.verify(candidateBytes, VerificationMode.INSTALL);
        StorageView view;
        try {
            cleanupTemps();
            view = inspectStorage();
        } catch (IOException e) {
            throw error(SignedRulePackageException.Code.CACHE_IO_FAILED);
        }

        HighWater highest = view.highest();
        if (highest != null && candidate.revision() < highest.revision()) {
            throw error(SignedRulePackageException.Code.REVISION_ROLLBACK);
        }
        if (highest != null && candidate.revision() == highest.revision()
                && hasDigestConflict(view, candidate.revision(), candidate.artifactSha256())) {
            throw error(SignedRulePackageException.Code.REVISION_CONFLICT);
        }

        boolean sameRevision = highest != null && candidate.revision() == highest.revision();
        boolean currentMatches = matches(view.current(), candidate);
        boolean stateMatches = matches(view.state(), candidate);
        if (sameRevision && currentMatches && stateMatches) {
            return new InstallResult(candidate, false, highest.revision());
        }

        try {
            Files.createDirectories(directory);
            if (!sameRevision && view.current() != null) {
                writeAtomically(previousTemp, previousFile, view.current().rawBytes());
            }
            if (!currentMatches) {
                writeAtomically(currentTemp, currentFile, candidateBytes);
            }
            if (!stateMatches) {
                writeState(new HighWater(candidate.revision(), candidate.artifactSha256()));
            }
            cleanupTemps();
            return new InstallResult(candidate, true, candidate.revision());
        } catch (IOException e) {
            throw error(SignedRulePackageException.Code.CACHE_IO_FAILED);
        }
    }

    public LoadResult load() {
        synchronized (operationLock) {
            return loadLocked();
        }
    }

    private LoadResult loadLocked() {
        StorageView view;
        try {
            cleanupTemps();
            view = inspectStorage();
        } catch (IOException e) {
            return LoadResult.failure(0L, SignedRulePackageException.Code.CACHE_IO_FAILED);
        }

        HighWater highest = view.highest();
        List<String> warnings = new ArrayList<>();
        if (highest != null && !sameHighWater(view.state(), highest)) {
            try {
                Files.createDirectories(directory);
                writeState(highest);
            } catch (IOException e) {
                warnings.add("CACHE_IO_FAILED");
            }
        }

        long highestRevision = highest == null ? 0L : highest.revision();
        if (view.current() != null) {
            return LoadResult.success(view.current().verifiedPackage(), false,
                    highestRevision, warnings);
        }
        if (view.previous() != null) {
            warnings.add("CACHE_RECOVERED");
            return LoadResult.success(view.previous().verifiedPackage(), true,
                    highestRevision, warnings);
        }
        return LoadResult.failure(highestRevision,
                SignedRulePackageException.Code.NO_VALID_SIGNED_PACKAGE);
    }

    public void clearDownloaded() throws SignedRulePackageException {
        synchronized (operationLock) {
            try {
                cleanupTemps();
                Files.deleteIfExists(currentFile);
                Files.deleteIfExists(previousFile);
            } catch (IOException e) {
                throw error(SignedRulePackageException.Code.CACHE_IO_FAILED);
            }
        }
    }

    private StorageView inspectStorage() throws IOException {
        HighWater state = readState();
        SlotData current = readSlot(currentFile);
        SlotData previous = readSlot(previousFile);
        HighWater signedHighest = newer(
                current == null ? null : highWater(current.verifiedPackage()),
                previous == null ? null : highWater(previous.verifiedPackage()));
        HighWater highest = newer(state, signedHighest);
        if (state != null && signedHighest != null
                && state.revision() == signedHighest.revision()) {
            highest = signedHighest;
        }
        return new StorageView(state, current, previous, highest);
    }

    private SlotData readSlot(Path file) {
        if (!Files.exists(file)) return null;
        try {
            byte[] raw = readLimited(file, SignedProbeRuleSidecarCodec.MAX_PACKAGE_BYTES);
            VerifiedProbeRuleSidecarPackage verified =
                    verifier.verify(raw, VerificationMode.CACHE);
            return new SlotData(raw, verified);
        } catch (IOException | SignedRulePackageException e) {
            return null;
        }
    }

    private HighWater readState() {
        if (!Files.exists(stateFile)) return null;
        try {
            String json = decodeUtf8(readLimited(stateFile, MAX_STATE_BYTES));
            try (JsonReader reader = new JsonReader(new StringReader(json))) {
                reader.setLenient(false);
                if (reader.peek() != JsonToken.BEGIN_OBJECT) return null;
                reader.beginObject();
                Set<String> names = new HashSet<>();
                Integer schemaVersion = null;
                String storedPackageId = null;
                Long revision = null;
                String digest = null;
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (!names.add(name)) return null;
                    switch (name) {
                        case "schemaVersion" -> schemaVersion = readInt(reader);
                        case "packageId" -> storedPackageId = readString(reader);
                        case "highestAcceptedRevision" -> revision = readLong(reader);
                        case "artifactSha256" -> digest = readString(reader);
                        default -> {
                            return null;
                        }
                    }
                }
                reader.endObject();
                if (reader.peek() != JsonToken.END_DOCUMENT
                        || schemaVersion == null || schemaVersion != STATE_SCHEMA_VERSION
                        || !packageId.equals(storedPackageId)
                        || revision == null || revision <= 0L
                        || digest == null || !DIGEST.matcher(digest).matches()) {
                    return null;
                }
                return new HighWater(revision, digest);
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private void writeState(HighWater state) throws IOException {
        StringWriter output = new StringWriter(192);
        try (JsonWriter writer = new JsonWriter(output)) {
            writer.setHtmlSafe(false);
            writer.beginObject();
            writer.name("schemaVersion").value(STATE_SCHEMA_VERSION);
            writer.name("packageId").value(packageId);
            writer.name("highestAcceptedRevision").value(state.revision());
            writer.name("artifactSha256").value(state.artifactSha256());
            writer.endObject();
        }
        writeAtomically(stateTemp, stateFile,
                output.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void writeAtomically(Path temporary, Path target, byte[] bytes)
            throws IOException {
        try (FileOutputStream output = new FileOutputStream(temporary.toFile())) {
            output.write(bytes);
            output.getFD().sync();
        }
        moveAtomically(temporary, target);
    }

    private void cleanupTemps() throws IOException {
        Files.deleteIfExists(currentTemp);
        Files.deleteIfExists(previousTemp);
        Files.deleteIfExists(stateTemp);
    }

    private static byte[] readLimited(Path file, int limit) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(16_384, limit));
            byte[] buffer = new byte[8_192];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (count == 0) continue;
                total += count;
                if (total > limit) throw new IOException("file size limit exceeded");
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private static String decodeUtf8(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
    }

    private static Integer readInt(JsonReader reader) throws IOException {
        Long value = readLong(reader);
        if (value == null || value > Integer.MAX_VALUE) return null;
        return value.intValue();
    }

    private static Long readLong(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.NUMBER) return null;
        String value = reader.nextString();
        if (!INTEGER.matcher(value).matches()) return null;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String readString(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.STRING) return null;
        return reader.nextString();
    }

    private static boolean hasDigestConflict(StorageView view, long revision,
                                             String digest) {
        return conflicts(view.state(), revision, digest)
                || conflicts(view.current(), revision, digest)
                || conflicts(view.previous(), revision, digest);
    }

    private static boolean conflicts(HighWater value, long revision, String digest) {
        return value != null && value.revision() == revision
                && !value.artifactSha256().equals(digest);
    }

    private static boolean conflicts(SlotData value, long revision, String digest) {
        return value != null && value.verifiedPackage().revision() == revision
                && !value.verifiedPackage().artifactSha256().equals(digest);
    }

    private static boolean matches(SlotData value,
                                   VerifiedProbeRuleSidecarPackage candidate) {
        return value != null && value.verifiedPackage().revision() == candidate.revision()
                && value.verifiedPackage().artifactSha256().equals(candidate.artifactSha256());
    }

    private static boolean matches(HighWater value,
                                   VerifiedProbeRuleSidecarPackage candidate) {
        return value != null && value.revision() == candidate.revision()
                && value.artifactSha256().equals(candidate.artifactSha256());
    }

    private static boolean sameHighWater(HighWater first, HighWater second) {
        return first != null && second != null
                && first.revision() == second.revision()
                && first.artifactSha256().equals(second.artifactSha256());
    }

    private static HighWater newer(HighWater first, HighWater second) {
        if (first == null) return second;
        if (second == null || first.revision() >= second.revision()) return first;
        return second;
    }

    private static HighWater highWater(VerifiedProbeRuleSidecarPackage verified) {
        return new HighWater(verified.revision(), verified.artifactSha256());
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static SignedRulePackageException error(SignedRulePackageException.Code code) {
        return new SignedRulePackageException(code);
    }

    public record InstallResult(VerifiedProbeRuleSidecarPackage verifiedPackage,
                                boolean changed, long highestAcceptedRevision) {
        public InstallResult {
            if (verifiedPackage == null || highestAcceptedRevision <= 0L) {
                throw new IllegalArgumentException("install result is invalid");
            }
        }
    }

    public static final class LoadResult {
        private final VerifiedProbeRuleSidecarPackage verifiedPackage;
        private final boolean recoveredPrevious;
        private final long highestAcceptedRevision;
        private final List<String> warnings;
        private final SignedRulePackageException.Code errorCode;

        private LoadResult(VerifiedProbeRuleSidecarPackage verifiedPackage,
                           boolean recoveredPrevious, long highestAcceptedRevision,
                           List<String> warnings,
                           SignedRulePackageException.Code errorCode) {
            this.verifiedPackage = verifiedPackage;
            this.recoveredPrevious = recoveredPrevious;
            this.highestAcceptedRevision = Math.max(0L, highestAcceptedRevision);
            this.warnings = List.copyOf(warnings);
            this.errorCode = errorCode;
        }

        static LoadResult success(VerifiedProbeRuleSidecarPackage verifiedPackage,
                                  boolean recoveredPrevious, long highestAcceptedRevision,
                                  List<String> warnings) {
            return new LoadResult(verifiedPackage, recoveredPrevious,
                    highestAcceptedRevision, warnings, null);
        }

        static LoadResult failure(long highestAcceptedRevision,
                                  SignedRulePackageException.Code errorCode) {
            return new LoadResult(null, false, highestAcceptedRevision,
                    List.of(), errorCode);
        }

        public boolean hasPackage() {
            return verifiedPackage != null;
        }

        public VerifiedProbeRuleSidecarPackage verifiedPackage() {
            return verifiedPackage;
        }

        public boolean recoveredPrevious() {
            return recoveredPrevious;
        }

        public long highestAcceptedRevision() {
            return highestAcceptedRevision;
        }

        public List<String> warnings() {
            return warnings;
        }

        public SignedRulePackageException.Code errorCode() {
            return errorCode;
        }
    }

    private record HighWater(long revision, String artifactSha256) {
    }

    private record SlotData(byte[] rawBytes,
                            VerifiedProbeRuleSidecarPackage verifiedPackage) {
        private SlotData {
            rawBytes = rawBytes.clone();
        }

        @Override
        public byte[] rawBytes() {
            return rawBytes.clone();
        }
    }

    private record StorageView(HighWater state, SlotData current,
                               SlotData previous, HighWater highest) {
    }
}
