# Mobile Aspect Choice Dialog Height Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ensure the mobile landscape fullscreen aspect-ratio choice dialog keeps every option visible and reachable without changing its existing visual or selection behavior.

**Architecture:** Keep `VideoAspectModeDialog` on the shared `ChoiceDialog` path. Make `ChoiceDialog` adapt its scroll-list height on every device after the window has been measured, while retaining selected-item focus work as a Leanback-only behavior.

**Tech Stack:** Android Java, AndroidX `DialogFragment`, Android `ScrollView`, JUnit 4 source-contract tests, Gradle Android plugin, ADB.

---

### Task 1: Add the mobile height regression test

**Files:**
- Modify: `app/src/testMobile/java/com/fongmi/android/tv/ui/dialog/VideoAspectUiSourceTest.java:61`

- [x] **Step 1: Replace the TV-only height assertion with a mobile-aware ordering assertion**

Replace the existing `choiceDialogUsesAdaptiveTvHeightAndSequentialDpadFocus` test with:

```java
@Test
public void choiceDialogAdaptsHeightForMobileAndKeepsSequentialDpadFocus() throws Exception {
    String dialog = read(source("main", "java", "com", "fongmi", "android", "tv", "ui", "dialog", "ChoiceDialog.java"));
    Pattern postLayout = Pattern.compile("window\\.getDecorView\\(\\)\\.post\\(\\(\\) -> \\{\\s*"
            + "adaptListHeight\\(window\\);\\s*"
            + "if \\(Util\\.isLeanback\\(\\)\\) window\\.getDecorView\\(\\)\\.post\\(this::focusSelectedItem\\);\\s*"
            + "}\\);");

    assertTrue("height adaptation and Leanback focus must stay inside the post-layout callback", postLayout.matcher(dialog).find());
    assertTrue("choice list height must use the post-layout visible window frame", dialog.contains("getWindowVisibleDisplayFrame(frame)"));
    assertFalse("choice list height must not treat WRAP_CONTENT decor height as the viewport", dialog.contains("adaptiveListHeight(window.getDecorView().getHeight()"));
    assertFalse("choice list height must not depend on stale orientation metrics", dialog.contains("ResUtil.getScreenHeight(requireContext())"));
    assertTrue("choice list height must measure fixed dialog chrome directly", dialog.contains("dialogChromeHeight(rootGroup, scroll)"));
    assertTrue("choice list height must recheck after the first layout changes its height", dialog.contains("scroll.post(() -> adaptListHeight(window))"));

}
```

- [x] **Step 2: Run the focused test and verify RED**

Run:

```powershell
rtk proxy .\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.ui.dialog.VideoAspectUiSourceTest.choiceDialogAdaptsHeightForMobileAndKeepsSequentialDpadFocus"
```

Expected: the test fails at `mobile choice list height must adapt before Leanback-only focus work`, because the current `Util.isLeanback()` condition appears before `adaptListHeight(window)`.

### Task 2: Apply adaptive height on mobile

**Files:**
- Modify: `app/src/main/java/com/fongmi/android/tv/ui/dialog/ChoiceDialog.java:245`
- Test: `app/src/testMobile/java/com/fongmi/android/tv/ui/dialog/VideoAspectUiSourceTest.java:61`

- [x] **Step 1: Make height adaptation common and keep focus Leanback-only**

In `ChoiceDialog.onStart()`, replace:

```java
if (Util.isLeanback()) window.getDecorView().post(() -> {
    adaptListHeight(window);
    window.getDecorView().post(this::focusSelectedItem);
});
```

with:

```java
window.getDecorView().post(() -> {
    adaptListHeight(window);
    if (Util.isLeanback()) window.getDecorView().post(this::focusSelectedItem);
});
```

Measure the scroll view's direct container after layout and use `getWindowVisibleDisplayFrame` as the available viewport. Include the 32dp safety margin in the fit check, then post one additional `adaptListHeight(window)` after the first height change so the second pass uses the settled `WRAP_CONTENT` chrome measurement. The existing equal-height return terminates the recheck. Keep the arithmetic in `calculateAdaptiveListHeight(...)` so single-item, two-item, near-fit, and constrained boundary behavior is directly testable.

- [x] **Step 2: Run the focused test and verify GREEN**

Run:

```powershell
rtk proxy .\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.ui.dialog.VideoAspectUiSourceTest.choiceDialogAdaptsHeightForMobileAndKeepsSequentialDpadFocus"
```

Expected: `BUILD SUCCESSFUL` and the focused test passes.

- [x] **Step 3: Run the complete height regression test class**

Run:

```powershell
rtk proxy .\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.ui.dialog.VideoAspectUiSourceTest"
```

Expected: every `VideoAspectUiSourceTest` test passes with no new warning or failure.

- [x] **Step 4: Run formatting and diff checks**

Run:

```powershell
rtk git diff --check
```

Expected: no output and exit code 0.

### Task 3: Build and validate on emulator-5554

**Files:**
- Generated: `app/build/outputs/apk/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk`
- Generated evidence: `F:/temp/webhtv-aspect-post-merge.png`, `F:/temp/webhtv-aspect-post-merge-bottom.png`, `F:/temp/webhtv-custom-post-merge.png`

- [x] **Step 1: Build the mobile ARM64 debug APK**

Run:

```powershell
rtk proxy .\gradlew.bat :app:assembleMobileArm64_v8aDebug
```

Expected: `BUILD SUCCESSFUL` and the APK exists at `app/build/outputs/apk/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk`.

- [x] **Step 2: Install the APK without clearing emulator data**

Run:

```powershell
rtk adb -s emulator-5554 install -r app/build/outputs/apk/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk
```

Expected: `Success`.

- [x] **Step 3: Reproduce the playback path**

Run:

```powershell
rtk adb -s emulator-5554 shell monkey -p com.silent.android.webhtv -c android.intent.category.LAUNCHER 1
```

Using the emulator, open any playable item, enter full-screen landscape playback, tap the player once, and select the current aspect label (for example `原始`) from the bottom control row.

Expected: the `缩放比例` choice dialog is fully contained within the 1920 x 1080 display and leaves visible space around its outer panel.

- [x] **Step 4: Validate the bottom options and interaction**

Scroll the list to the end and verify all of the following:

- `自适应` is fully visible and clickable.
- `自定义` is fully visible and clickable.
- `取消` does not overlap either option.
- Selecting `自适应` closes the dialog and updates the control label.
- Reopening the dialog and selecting `自定义` opens the custom ratio input dialog.

- [x] **Step 5: Capture final visual evidence and runtime errors**

Run:

```powershell
rtk adb -s emulator-5554 shell screencap -p /sdcard/webhtv-aspect-post-merge-bottom.png
rtk adb -s emulator-5554 pull /sdcard/webhtv-aspect-post-merge-bottom.png F:\temp\webhtv-aspect-post-merge-bottom.png
rtk adb -s emulator-5554 logcat -d -t 500 AndroidRuntime:E WindowManager:E *:S
```

Expected: the screenshot shows both bottom options unobstructed, and logcat contains no new application crash or window-layout error.

### Task 4: Integrate remote changes and review until clean

**Files:**
- Review every file reported by `rtk git diff --name-only` after the merge.

- [x] **Step 1: Fetch and merge the latest remote beta branch**

Run:

```powershell
rtk git fetch origin
rtk git merge origin/beta
```

Expected: either `Already up to date.` or a clean merge. Resolve only conflicts that overlap this task, preserving unrelated user changes.

- [x] **Step 2: Re-run focused tests and emulator validation after the merge**

Run:

```powershell
rtk proxy .\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.ui.dialog.VideoAspectUiSourceTest"
rtk proxy .\gradlew.bat :app:assembleMobileArm64_v8aDebug
```

Reinstall the rebuilt APK on `emulator-5554` and repeat Task 3 Steps 3-5.

Expected: tests, build, device interaction, screenshot, and logs all remain clean.

- [x] **Step 3: Review the complete task diff**

Review for behavioral regressions, incorrect mobile/Leanback branching, missing tests, stale comments, unnecessary scope, merge conflict artifacts, and accidental generated-file changes.

Run:

```powershell
rtk git diff --check
rtk git status --short
rtk git diff -- app/src/main/java/com/fongmi/android/tv/ui/dialog/ChoiceDialog.java app/src/testMobile/java/com/fongmi/android/tv/ui/dialog/VideoAspectUiSourceTest.java docs/superpowers
```

Expected: no correctness or maintainability findings. If a finding exists, add or adjust the failing test first, make the smallest correction, rerun Tasks 2-3, and repeat this review step.

### Task 5: Commit, push, and synchronize

**Files:**
- Stage only the implementation, regression test, design, and plan files from this task.

- [x] **Step 1: Run final verification**

Run:

```powershell
rtk proxy .\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.ui.dialog.VideoAspectUiSourceTest"
rtk git diff --check
rtk git status --short --branch
```

Expected: tests pass, diff check is empty, and only intended task files are uncommitted.

- [ ] **Step 2: Create the requested Chinese commit**

Run:

```powershell
rtk git add app/src/main/java/com/fongmi/android/tv/ui/dialog/ChoiceDialog.java app/src/testMobile/java/com/fongmi/android/tv/ui/dialog/VideoAspectUiSourceTest.java docs/superpowers/specs/2026-08-17-mobile-aspect-choice-dialog-height-design.md
rtk git add -f docs/superpowers/plans/2026-08-17-mobile-aspect-choice-dialog-height.md
rtk git commit -m "修复手机版缩放比例弹窗高度适配"
```

Expected: one commit containing only this task's four files.

- [ ] **Step 3: Push and perform the final pull**

Run:

```powershell
rtk git push origin beta
rtk git pull --ff-only origin beta
rtk git status --short --branch
```

Expected: push succeeds, the final pull reports up to date or fast-forwards cleanly, and `beta` is clean and synchronized with `origin/beta`.
