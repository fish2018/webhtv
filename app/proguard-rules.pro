# TV
-keep class androidx.leanback.widget.** { *; }
-keep class com.fongmi.quickjs.method.** { *; }

# MPV JNI bridge
-keep class is.xyz.mpv.MPVLib { *; }
-keep class is.xyz.mpv.MPVLib$* { *; }

# libplayer.so resolves this class and these methods by their literal JNI names.
-keep class com.fongmi.android.tv.player.iso.IsoSessionManager {
    public static long length(long);
    public static int readAt(long, long, java.nio.ByteBuffer, int);
    public static void close(long);
    public static void prepareTrackMetadata(long, int);
}

# MPV owns one process-wide native context. Keep its lifecycle code intact so
# release inlining does not amplify timing-sensitive create/destroy transitions.
-keep,allowobfuscation class androidx.media3.mpvplayer.MpvPlayer { *; }
-keep,allowobfuscation class androidx.media3.mpvplayer.MpvPlayer$* { *; }
-keep,allowobfuscation class com.fongmi.android.tv.player.engine.MpvPlayerEngine { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }

# App code: keep everything, no obfuscation.
# R8 fullMode strips generic Signature from obfuscated classes, so any persisted
# class holding an object/numeric generic collection field (Map<String,Integer>,
# List<Entry>, ...) without an explicit TypeToken would have its generics erased,
# making Gson deserialize numbers as Double and objects as LinkedTreeMap -> later
# casts crash with ClassCastException. Obfuscation also renames fields lacking
# @SerializedName, silently breaking persisted JSON key lookups. Keeping the whole
# app package eliminates both classes of failure globally instead of patching each
# offending class one by one. The dex size cost is a few MB; total APK size is
# dominated by native .so libraries, so this is negligible.
-keep class com.fongmi.android.tv.** { *; }

# SimpleXML
-keep interface org.simpleframework.xml.core.Label { public *; }
-keep class * implements org.simpleframework.xml.core.Label { public *; }
-keep interface org.simpleframework.xml.core.Parameter { public *; }
-keep class * implements org.simpleframework.xml.core.Parameter { public *; }
-keep interface org.simpleframework.xml.core.Extractor { public *; }
-keep class * implements org.simpleframework.xml.core.Extractor { public *; }
-keepclassmembers,allowobfuscation class * { @org.simpleframework.xml.Path <fields>; }
-keepclassmembers,allowobfuscation class * { @org.simpleframework.xml.Root <fields>; }
-keepclassmembers,allowobfuscation class * { @org.simpleframework.xml.Text <fields>; }
-keepclassmembers,allowobfuscation class * { @org.simpleframework.xml.Element <fields>; }
-keepclassmembers,allowobfuscation class * { @org.simpleframework.xml.Attribute <fields>; }
-keepclassmembers,allowobfuscation class * { @org.simpleframework.xml.ElementList <fields>; }

# OkHttp
-dontwarn okhttp3.**
-keep class okio.** { *; }
-keep class okhttp3.** { *; }

# Kotlin
-keeppackagenames kotlin.**
-keep class kotlin.** { *; }

# JGit
-dontwarn java.lang.ProcessHandle
-dontwarn java.lang.management.ManagementFactory
-dontwarn org.eclipse.jgit.**
-keep class org.eclipse.jgit.** { *; }
-keeppackagenames org.slf4j.**
-keep class org.slf4j.** { *; }

# CatVod
-keep class com.github.catvod.Proxy { *; }
-keep class com.github.catvod.crawler.** { *; }
-keep class * extends com.github.catvod.crawler.Spider

# Jianpian
-keep class com.p2p.** { *; }

# JUPnP
-dontwarn org.jupnp.**
-keep class org.jupnp.** { *; }
-keep class javax.xml.** { *; }

# Inline quick search is reflected from the shared TMDB detail activity into each
# flavor's own dialog. Must be -keepclassmembers, not -keepclassmembernames:
# the latter implies allowshrinking, so R8 may delete methods only reached by
# reflection. Losing them makes the in-page search silently fall back to the
# global search page in release builds only.
-keepclassmembers class com.fongmi.android.tv.ui.dialog.QuickSearchDialog {
    public static com.fongmi.android.tv.ui.dialog.QuickSearchDialog create();
    public void show(androidx.fragment.app.FragmentActivity);
    public void addAll(java.util.List);
    public void clear();
    public *** listener(***);
    public *** items(java.util.List);
    public *** title(java.lang.String);
    public *** keyword(java.lang.String);
    public *** setProgress(int, int, boolean);
    public *** searchListener(***);
    public *** dismissListener(***);
}
-keep interface com.fongmi.android.tv.ui.dialog.QuickSearchDialog$* { *; }
-keep interface com.fongmi.android.tv.ui.adapter.QuickAdapter$OnClickListener { *; }
# Inherited from DialogFragment, so the rule above cannot match it.
-keepclassmembers class * extends androidx.fragment.app.DialogFragment {
    public void dismissAllowingStateLoss();
}

# Mobile inline cast uses reflection from the shared TMDB detail activity.
-keepclassmembernames class com.fongmi.android.tv.ui.dialog.CastDialog {
    public static com.fongmi.android.tv.ui.dialog.CastDialog create();
    public com.fongmi.android.tv.ui.dialog.CastDialog history(com.fongmi.android.tv.bean.History);
    public com.fongmi.android.tv.ui.dialog.CastDialog video(com.fongmi.android.tv.bean.CastVideo);
    public com.fongmi.android.tv.ui.dialog.CastDialog fm(boolean);
    public void show(androidx.fragment.app.FragmentActivity);
}

# Nano
-keep class fi.iki.elonen.** { *; }

# NewPipeExtractor
-keep class javax.script.** { *; }
-keep class jdk.dynalink.** { *; }
-keep class org.mozilla.javascript.* { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.javascript.engine.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
-keep class org.schabi.newpipe.extractor.services.youtube.protos.** { *; }
-dontwarn org.mozilla.javascript.JavaToJSONConverters
-dontwarn org.mozilla.javascript.tools.**
-dontwarn com.google.re2j.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**

# Sardine
-keep class com.thegrizzlylabs.sardineandroid.** { *; }

# TVBus
-keep class com.tvbus.engine.** { *; }

# XunLei
-keep class com.xunlei.downloadlib.** { *; }

# Zxing
-keep class com.google.zxing.** { *; }

# sherpa-onnx JNI resolves Java class and method names directly.
-keep class com.k2fsa.sherpa.onnx.** { *; }
