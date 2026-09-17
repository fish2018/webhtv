package com.fongmi.android.tv;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UpdateChannelTest {

    @Test
    public void updaterShortCircuitsOnStaticChannelManifestBeforeGithubApi() throws Exception {
        String source = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "Updater.java")));
        String method = between(source, "private Update getUpdate", "private Update getGithubStableUpdate");

        int channelAsset = method.indexOf("Github.getChannelAsset");
        int shortCircuit = method.indexOf("hasManifest()) return", channelAsset);
        int betaApi = method.indexOf("getGithubBetaUpdate", shortCircuit);

        assertTrue("The fixed GitHub channel manifest must be tried first", channelAsset >= 0);
        assertTrue("A valid static manifest must prevent an unnecessary GitHub API request", shortCircuit > channelAsset);
        assertTrue("The GitHub Releases API must be fallback-only", betaApi > shortCircuit);
    }

    @Test
    public void betaKeepsCnbManifestAsMigrationFallbackBeforeGithubApi() throws Exception {
        String source = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "Updater.java")));
        String method = between(source, "private Update getUpdate", "private Update getGithubStableUpdate");

        int cnbFallback = method.indexOf("Github.getCnbMirrorAsset");
        int cnbShortCircuit = method.indexOf("hasManifest()) return", cnbFallback);
        int betaApi = method.indexOf("getGithubBetaUpdate", cnbShortCircuit);

        assertTrue("Beta should retain the currently published CNB manifest as a migration fallback", cnbFallback >= 0);
        assertTrue("A valid CNB beta manifest must prevent a direct GitHub API request", cnbShortCircuit > cnbFallback);
        assertTrue("The direct GitHub API must remain the last resort", betaApi > cnbShortCircuit);
    }

    @Test
    public void releaseWorkflowPublishesManifestsToFixedChannelRelease() throws Exception {
        String workflow = read(findRepoPath().resolve(Path.of(".github", "workflows", "android-release.yml")));
        String publishStep = between(workflow, "- name: Publish update channel manifests", "- name: Sync to CNB");

        assertTrue("The workflow should reserve a fixed release tag for channel manifests",
                workflow.contains("UPDATE_CHANNEL_TAG: update-channel"));
        assertTrue("Future deadline regressions must block a release",
                workflow.contains("--tests 'com.fongmi.android.tv.UpdaterFutureTest'"));
        assertTrue("Both update channels must remain visible in mobile and TV release builds",
                workflow.contains("--tests 'com.fongmi.android.tv.ui.dialog.UpdateDialogChannelVisibilityTest'"));
        assertTrue("TV release builds must verify that update actions stay inside the dialog window",
                workflow.contains(":app:testLeanbackArm64_v8aDebugUnitTest")
                        && workflow.contains("--tests 'com.fongmi.android.tv.ui.dialog.UpdateDialogLayoutTest'"));
        assertTrue("The fixed channel release must be a prerelease so it cannot replace the stable latest release",
                publishStep.contains("--prerelease"));
        assertTrue("The fixed channel release should be seeded with the other channel when it already exists",
                publishStep.contains("gh release download \"$OTHER_TAG\""));
        assertTrue("Every release should replace the complete stable/beta channel manifest set",
                publishStep.contains("gh release upload \"$UPDATE_CHANNEL_TAG\" channel-manifests/*.json --clobber"));
    }

    @Test
    public void releaseWorkflowSerializesUpdatesToTheSharedChannelRelease() throws Exception {
        String workflow = read(findRepoPath().resolve(Path.of(".github", "workflows", "android-release.yml")));

        assertTrue("Stable and beta workflows must not update the shared release concurrently",
                workflow.contains("concurrency:\n  group: android-release-update-channel\n  cancel-in-progress: false"));
    }

    @Test
    public void releaseWorkflowCanRepairAnExistingReleaseOnRetry() throws Exception {
        String workflow = read(findRepoPath().resolve(Path.of(".github", "workflows", "android-release.yml")));
        String resolveStep = between(workflow, "- name: Resolve release tag", "- name: Configure release signing");
        String releaseStep = between(workflow, "- name: Create GitHub Release", "- name: Publish update channel manifests");

        assertFalse("An existing tag at the same commit should be reusable",
                resolveStep.contains("Release tag exists::$TAG already exists"));
        assertTrue("The default tag must remain stable when the same commit is retried",
                resolveStep.contains("git show -s --format=%ct \"$GITHUB_SHA\""));
        assertFalse("A retry must not generate a different tag from the current wall clock",
                resolveStep.contains("$(date +%Y%m%d%H%M)"));
        assertTrue("A retry must reject an existing tag that targets another commit",
                resolveStep.contains("Existing release tag targets another commit"));
        assertTrue("A retry must detect an already-created GitHub Release",
                releaseStep.contains("if gh release view \"${{ steps.meta.outputs.tag }}\""));
        assertTrue("A retry must repair partially uploaded release assets",
                releaseStep.contains("gh release upload \"${{ steps.meta.outputs.tag }}\" dist/*.apk dist/*.json --clobber"));
    }

    private static String between(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from + start.length());
        return source.substring(from, to);
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }

    private static Path findMainJavaPath() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "java");
    }

    private static Path findRepoPath() {
        if (Files.exists(Path.of(".github"))) return Path.of("");
        return Path.of("..");
    }
}
