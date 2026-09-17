package tv.danmaku.ijk.media.player;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.net.URLEncoder;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tv.danmaku.ijk.media.player.annotations.AccessedByNative;
import tv.danmaku.ijk.media.player.annotations.CalledByNative;
import tv.danmaku.ijk.media.player.misc.IAndroidIO;
import tv.danmaku.ijk.media.player.misc.IMediaDataSource;
import tv.danmaku.ijk.media.player.misc.ITrackInfo;
import tv.danmaku.ijk.media.player.misc.IjkTrackInfo;
import tv.danmaku.ijk.media.player.pragma.DebugLog;

/* loaded from: classes3.dex */
public final class IjkMediaPlayer extends AbstractMediaPlayer {
    public static final int FFP_PROPV_DECODER_AVCODEC = 1;
    public static final int FFP_PROPV_DECODER_MEDIACODEC = 2;
    public static final int FFP_PROPV_DECODER_UNKNOWN = 0;
    public static final int FFP_PROPV_DECODER_VIDEOTOOLBOX = 3;
    public static final int FFP_PROP_FLOAT_DROP_FRAME_RATE = 10007;
    public static final int FFP_PROP_FLOAT_PLAYBACK_RATE = 10003;
    public static final int FFP_PROP_INT64_ASYNC_STATISTIC_BUF_BACKWARDS = 20201;
    public static final int FFP_PROP_INT64_ASYNC_STATISTIC_BUF_CAPACITY = 20203;
    public static final int FFP_PROP_INT64_ASYNC_STATISTIC_BUF_FORWARDS = 20202;
    public static final int FFP_PROP_INT64_AUDIO_CACHED_BYTES = 20008;
    public static final int FFP_PROP_INT64_AUDIO_CACHED_DURATION = 20006;
    public static final int FFP_PROP_INT64_AUDIO_CACHED_PACKETS = 20010;
    public static final int FFP_PROP_INT64_AUDIO_DECODER = 20004;
    public static final int FFP_PROP_INT64_BIT_RATE = 20100;
    public static final int FFP_PROP_INT64_CACHE_STATISTIC_COUNT_BYTES = 20208;
    public static final int FFP_PROP_INT64_CACHE_STATISTIC_FILE_FORWARDS = 20206;
    public static final int FFP_PROP_INT64_CACHE_STATISTIC_FILE_POS = 20207;
    public static final int FFP_PROP_INT64_CACHE_STATISTIC_PHYSICAL_POS = 20205;
    public static final int FFP_PROP_INT64_IMMEDIATE_RECONNECT = 20211;
    public static final int FFP_PROP_INT64_LATEST_SEEK_LOAD_DURATION = 20300;
    public static final int FFP_PROP_INT64_LOGICAL_FILE_SIZE = 20209;
    public static final int FFP_PROP_INT64_SELECTED_AUDIO_STREAM = 20002;
    public static final int FFP_PROP_INT64_SELECTED_TIMEDTEXT_STREAM = 20011;
    public static final int FFP_PROP_INT64_SELECTED_VIDEO_STREAM = 20001;
    public static final int FFP_PROP_INT64_SHARE_CACHE_DATA = 20210;
    public static final int FFP_PROP_INT64_TCP_SPEED = 20200;
    public static final int FFP_PROP_INT64_TRAFFIC_STATISTIC_BYTE_COUNT = 20204;
    public static final int FFP_PROP_INT64_VIDEO_CACHED_BYTES = 20007;
    public static final int FFP_PROP_INT64_VIDEO_CACHED_DURATION = 20005;
    public static final int FFP_PROP_INT64_VIDEO_CACHED_PACKETS = 20009;
    public static final int FFP_PROP_INT64_VIDEO_DECODER = 20003;
    public static final int IJK_LOG_DEBUG = 3;
    public static final int IJK_LOG_DEFAULT = 1;
    public static final int IJK_LOG_ERROR = 6;
    public static final int IJK_LOG_FATAL = 7;
    public static final int IJK_LOG_INFO = 4;
    public static final int IJK_LOG_SILENT = 8;
    public static final int IJK_LOG_UNKNOWN = 0;
    public static final int IJK_LOG_VERBOSE = 2;
    public static final int IJK_LOG_WARN = 5;
    private static final int MEDIA_BUFFERING_UPDATE = 3;
    private static final int MEDIA_ERROR = 100;
    private static final int MEDIA_INFO = 200;
    private static final int MEDIA_NOP = 0;
    private static final int MEDIA_PLAYBACK_COMPLETE = 2;
    private static final int MEDIA_PREPARED = 1;
    private static final int MEDIA_SEEK_COMPLETE = 4;
    private static final int MEDIA_SET_VIDEO_SAR = 10001;
    private static final int MEDIA_SET_VIDEO_SIZE = 5;
    private static final int MEDIA_TIMED_TEXT = 99;
    public static final int OPT_CATEGORY_CODEC = 2;
    public static final int OPT_CATEGORY_FORMAT = 1;
    public static final int OPT_CATEGORY_PLAYER = 4;
    public static final int OPT_CATEGORY_SWS = 3;
    public static final int PROP_FLOAT_VIDEO_DECODE_FRAMES_PER_SECOND = 10001;
    public static final int PROP_FLOAT_VIDEO_OUTPUT_FRAMES_PER_SECOND = 10002;
    public static final int SDL_FCC_RV16 = 909203026;
    public static final int SDL_FCC_RV32 = 842225234;
    public static final int SDL_FCC_YV12 = 842094169;
    private static final String TAG = "tv.danmaku.ijk.media.player.IjkMediaPlayer";
    private EventHandler mEventHandler;

    @AccessedByNative
    private int mListenerContext;

    @AccessedByNative
    private long mNativeAndroidIO;

    @AccessedByNative
    private long mNativeMediaDataSource;

    @AccessedByNative
    private long mNativeMediaPlayer;

    @AccessedByNative
    private int mNativeSurfaceTexture;
    private OnControlMessageListener mOnControlMessageListener;
    private OnMediaCodecSelectListener mOnMediaCodecSelectListener;
    private OnNativeInvokeListener mOnNativeInvokeListener;
    private boolean mScreenOnWhilePlaying;
    private boolean mStayAwake;
    private SurfaceHolder mSurfaceHolder;
    private boolean mAudioCodecInfoFailureLogged;
    private boolean mVideoCodecInfoFailureLogged;
    private int mVideoHeight;
    private int mVideoSarDen;
    private int mVideoSarNum;
    private int mVideoWidth;
    private PowerManager.WakeLock mWakeLock;
    private static final IjkLibLoader LOADER = new AnonymousClass1("ijkffmpeg", "ijksdl", "ijkplayer");
    private static volatile boolean mIsNativeInitialized = false;
    private static volatile boolean mIsPropertyFloatAvailable = true;
    private static volatile boolean mIsVideoSurfaceAvailable = true;
    private static volatile boolean mIsAudioCodecInfoAvailable = true;
    private static volatile boolean mIsVideoCodecInfoAvailable = true;

    /* renamed from: tv.danmaku.ijk.media.player.IjkMediaPlayer$1, reason: invalid class name */
    public static class AnonymousClass1 extends IjkLibLoader {
        public AnonymousClass1(String... strArr) {
            super(strArr);
        }

        @Override // tv.danmaku.ijk.media.player.IjkLibLoader
        public void loadLibrary(String str) {
            System.loadLibrary(str);
        }
    }

    public static class DefaultMediaCodecSelector implements OnMediaCodecSelectListener {
        public static final DefaultMediaCodecSelector sInstance = new DefaultMediaCodecSelector();

        @Override // tv.danmaku.ijk.media.player.IjkMediaPlayer.OnMediaCodecSelectListener
        public String onMediaCodecSelect(IMediaPlayer iMediaPlayer, String str, int i8, int i9) {
            String[] supportedTypes;
            IjkMediaCodecInfo ijkMediaCodecInfo;
            if (TextUtils.isEmpty(str)) {
                return null;
            }
            String strAccess$100 = IjkMediaPlayer.access$100();
            Locale locale = Locale.US;
            Log.i(strAccess$100, "onSelectCodec: mime=" + str + ", profile=" + i8 + ", level=" + i9);
            ArrayList arrayList = new ArrayList();
            int codecCount = MediaCodecList.getCodecCount();
            for (int i10 = 0; i10 < codecCount; i10++) {
                MediaCodecInfo codecInfoAt = MediaCodecList.getCodecInfoAt(i10);
                String strAccess$1002 = IjkMediaPlayer.access$100();
                Locale locale2 = Locale.US;
                Log.d(strAccess$1002, "  found codec: " + codecInfoAt.getName());
                if (!codecInfoAt.isEncoder() && (supportedTypes = codecInfoAt.getSupportedTypes()) != null) {
                    for (String str2 : supportedTypes) {
                        if (!TextUtils.isEmpty(str2)) {
                            String strAccess$1003 = IjkMediaPlayer.access$100();
                            Locale locale3 = Locale.US;
                            Log.d(strAccess$1003, "    mime: " + str2);
                            if (str2.equalsIgnoreCase(str) && (ijkMediaCodecInfo = IjkMediaCodecInfo.setupCandidate(codecInfoAt, str)) != null) {
                                arrayList.add(ijkMediaCodecInfo);
                                Log.i(IjkMediaPlayer.access$100(), "candidate codec: " + codecInfoAt.getName() + " rank=" + ijkMediaCodecInfo.mRank);
                                ijkMediaCodecInfo.dumpProfileLevels(str);
                            }
                        }
                    }
                }
            }
            if (arrayList.isEmpty()) {
                return null;
            }
            IjkMediaCodecInfo ijkMediaCodecInfo2 = (IjkMediaCodecInfo) arrayList.get(0);
            Iterator it = arrayList.iterator();
            while (it.hasNext()) {
                IjkMediaCodecInfo ijkMediaCodecInfo3 = (IjkMediaCodecInfo) it.next();
                if (ijkMediaCodecInfo3.mRank > ijkMediaCodecInfo2.mRank) {
                    ijkMediaCodecInfo2 = ijkMediaCodecInfo3;
                }
            }
            if (ijkMediaCodecInfo2.mRank < 600) {
                String strAccess$1004 = IjkMediaPlayer.access$100();
                Locale locale4 = Locale.US;
                Log.w(strAccess$1004, "unaccetable codec: " + ijkMediaCodecInfo2.mCodecInfo.getName());
                return null;
            }
            String strAccess$1005 = IjkMediaPlayer.access$100();
            Locale locale5 = Locale.US;
            Log.i(strAccess$1005, "selected codec: " + ijkMediaCodecInfo2.mCodecInfo.getName() + " rank=" + ijkMediaCodecInfo2.mRank);
            return ijkMediaCodecInfo2.mCodecInfo.getName();
        }
    }

    public static class EventHandler extends Handler {
        private final WeakReference<IjkMediaPlayer> mWeakPlayer;

        public EventHandler(IjkMediaPlayer ijkMediaPlayer, Looper looper) {
            super(looper);
            this.mWeakPlayer = new WeakReference<>(ijkMediaPlayer);
        }

        @Override // android.os.Handler
        public void handleMessage(Message message) {
            Object obj;
            IjkMediaPlayer ijkMediaPlayer = this.mWeakPlayer.get();
            if (ijkMediaPlayer != null) {
                if (IjkMediaPlayer.access$000(ijkMediaPlayer) != 0) {
                    int i8 = message.what;
                    if (i8 != 0) {
                        if (i8 == 1) {
                            ijkMediaPlayer.notifyOnPrepared();
                            return;
                        }
                        if (i8 == 2) {
                            IjkMediaPlayer.access$200(ijkMediaPlayer, false);
                            ijkMediaPlayer.notifyOnCompletion();
                            return;
                        }
                        if (i8 == 3) {
                            long j8 = message.arg1;
                            if (j8 < 0) {
                                j8 = 0;
                            }
                            long duration = ijkMediaPlayer.getDuration();
                            long j9 = duration > 0 ? (j8 * 100) / duration : 0L;
                            long j10 = j9 < 100 ? j9 : 100L;
                            ijkMediaPlayer.notifyOnBufferingUpdate(j8);
                            ijkMediaPlayer.notifyOnBufferingUpdate((int) j10);
                            return;
                        }
                        if (i8 != 4) {
                            if (i8 == 5) {
                                IjkMediaPlayer.access$302(ijkMediaPlayer, message.arg1);
                                IjkMediaPlayer.access$402(ijkMediaPlayer, message.arg2);
                                ijkMediaPlayer.notifyOnVideoSizeChanged(IjkMediaPlayer.access$300(ijkMediaPlayer), IjkMediaPlayer.access$400(ijkMediaPlayer), IjkMediaPlayer.access$500(ijkMediaPlayer), IjkMediaPlayer.access$600(ijkMediaPlayer));
                                return;
                            }
                            if (i8 == 99) {
                                ijkMediaPlayer.notifyOnTimedText(IjkTimedText.create((message.arg1 >= 2 || (obj = message.obj) == null) ? "" : obj.toString()));
                                return;
                            }
                            if (i8 == 100) {
                                DebugLog.e(IjkMediaPlayer.access$100(), "Error (" + message.arg1 + "," + message.arg2 + ")");
                                if (!ijkMediaPlayer.notifyOnError(message.arg1, message.arg2)) {
                                    ijkMediaPlayer.notifyOnCompletion();
                                }
                                IjkMediaPlayer.access$200(ijkMediaPlayer, false);
                                return;
                            }
                            if (i8 == 200) {
                                if (message.arg1 == 3) {
                                    DebugLog.i(IjkMediaPlayer.access$100(), "Info: MEDIA_INFO_VIDEO_RENDERING_START\n");
                                }
                                ijkMediaPlayer.notifyOnInfo(message.arg1, message.arg2);
                                return;
                            } else if (i8 == 10001) {
                                IjkMediaPlayer.access$502(ijkMediaPlayer, message.arg1);
                                IjkMediaPlayer.access$602(ijkMediaPlayer, message.arg2);
                                ijkMediaPlayer.notifyOnVideoSizeChanged(IjkMediaPlayer.access$300(ijkMediaPlayer), IjkMediaPlayer.access$400(ijkMediaPlayer), IjkMediaPlayer.access$500(ijkMediaPlayer), IjkMediaPlayer.access$600(ijkMediaPlayer));
                                return;
                            } else {
                                DebugLog.e(IjkMediaPlayer.access$100(), "Unknown message type " + message.what);
                                return;
                            }
                        }
                        return;
                    }
                    return;
                }
            }
            DebugLog.w(IjkMediaPlayer.access$100(), "IjkMediaPlayer went away with unhandled events");
        }
    }

    public interface OnControlMessageListener {
        String onControlResolveSegmentUrl(int i8);
    }

    public interface OnMediaCodecSelectListener {
        String onMediaCodecSelect(IMediaPlayer iMediaPlayer, String str, int i8, int i9);
    }

    public interface OnNativeInvokeListener {
        public static final String ARG_ERROR = "error";
        public static final String ARG_FAMILIY = "family";
        public static final String ARG_FD = "fd";
        public static final String ARG_FILE_SIZE = "file_size";
        public static final String ARG_HTTP_CODE = "http_code";
        public static final String ARG_IP = "ip";
        public static final String ARG_OFFSET = "offset";
        public static final String ARG_PORT = "port";
        public static final String ARG_RETRY_COUNTER = "retry_counter";
        public static final String ARG_SEGMENT_INDEX = "segment_index";
        public static final String ARG_URL = "url";
        public static final int CTRL_DID_TCP_OPEN = 131074;
        public static final int CTRL_WILL_CONCAT_RESOLVE_SEGMENT = 131079;
        public static final int CTRL_WILL_HTTP_OPEN = 131075;
        public static final int CTRL_WILL_LIVE_OPEN = 131077;
        public static final int CTRL_WILL_TCP_OPEN = 131073;
        public static final int EVENT_DID_HTTP_OPEN = 2;
        public static final int EVENT_DID_HTTP_SEEK = 4;
        public static final int EVENT_WILL_HTTP_OPEN = 1;
        public static final int EVENT_WILL_HTTP_SEEK = 3;

        boolean onNativeInvoke(int i8, Bundle bundle);
    }

    public IjkMediaPlayer() {
        initPlayer();
        initHandler();
    }

    private native String _getAudioCodecInfo();

    private static native String _getColorFormatName(int i8);

    private native int _getLoopCount();

    private native Bundle _getMediaMeta();

    private native float _getPropertyFloat(int i8, float f);

    private native long _getPropertyLong(int i8, long j8);

    private native String _getVideoCodecInfo();

    private native void _pause();

    private native void _release();

    private native void _reset();

    private native void _setAndroidIOCallback(IAndroidIO iAndroidIO);

    private native void _setDataSource(String str, String[] strArr, String[] strArr2);

    private native void _setDataSource(IMediaDataSource iMediaDataSource);

    private native void _setDataSourceFd(int i8);

    private native void _setFrameAtTime(String str, long j8, long j9, int i8, int i9);

    private native void _setLoopCount(int i8);

    private native void _setOption(int i8, String str, long j8);

    private native void _setOption(int i8, String str, String str2);

    private native void _setPropertyFloat(int i8, float f);

    private native void _setPropertyLong(int i8, long j8);

    private float getPropertyFloat(int property, float defaultValue) {
        if (!mIsPropertyFloatAvailable) return defaultValue;
        try {
            return _getPropertyFloat(property, defaultValue);
        } catch (UnsatisfiedLinkError e) {
            mIsPropertyFloatAvailable = false;
            Log.w(TAG, "IJK float property is unavailable in current native library", e);
            return defaultValue;
        }
    }

    private void setPropertyFloat(int property, float value) {
        if (!mIsPropertyFloatAvailable) return;
        try {
            _setPropertyFloat(property, value);
        } catch (UnsatisfiedLinkError e) {
            mIsPropertyFloatAvailable = false;
            Log.w(TAG, "IJK float property is unavailable in current native library", e);
        }
    }

    private native void _setStreamSelected(int i8, boolean z6);

    private native void _setVideoSurface(Surface surface);

    private void setVideoSurface(Surface surface) {
        if (!mIsVideoSurfaceAvailable) return;
        try {
            _setVideoSurface(surface);
        } catch (UnsatisfiedLinkError e) {
            mIsVideoSurfaceAvailable = false;
            Log.w(TAG, "IJK video surface is unavailable in current native library", e);
        }
    }

    private native void _start();

    private native void _stop();

    public static /* synthetic */ long access$000(IjkMediaPlayer ijkMediaPlayer) {
        return ijkMediaPlayer.mNativeMediaPlayer;
    }

    public static /* synthetic */ String access$100() {
        return TAG;
    }

    public static /* synthetic */ void access$200(IjkMediaPlayer ijkMediaPlayer, boolean z6) {
        ijkMediaPlayer.stayAwake(z6);
    }

    public static /* synthetic */ int access$300(IjkMediaPlayer ijkMediaPlayer) {
        return ijkMediaPlayer.mVideoWidth;
    }

    public static /* synthetic */ int access$302(IjkMediaPlayer ijkMediaPlayer, int i8) {
        ijkMediaPlayer.mVideoWidth = i8;
        return i8;
    }

    public static /* synthetic */ int access$400(IjkMediaPlayer ijkMediaPlayer) {
        return ijkMediaPlayer.mVideoHeight;
    }

    public static /* synthetic */ int access$402(IjkMediaPlayer ijkMediaPlayer, int i8) {
        ijkMediaPlayer.mVideoHeight = i8;
        return i8;
    }

    public static /* synthetic */ int access$500(IjkMediaPlayer ijkMediaPlayer) {
        return ijkMediaPlayer.mVideoSarNum;
    }

    public static /* synthetic */ int access$502(IjkMediaPlayer ijkMediaPlayer, int i8) {
        ijkMediaPlayer.mVideoSarNum = i8;
        return i8;
    }

    public static /* synthetic */ int access$600(IjkMediaPlayer ijkMediaPlayer) {
        return ijkMediaPlayer.mVideoSarDen;
    }

    public static /* synthetic */ int access$602(IjkMediaPlayer ijkMediaPlayer, int i8) {
        ijkMediaPlayer.mVideoSarDen = i8;
        return i8;
    }

    private String encodeSpaceChinese(String str) {
        Matcher matcher = Pattern.compile("[一-龥]").matcher(str);
        StringBuffer stringBuffer = new StringBuffer();
        while (matcher.find()) {
            try {
                matcher.appendReplacement(stringBuffer, URLEncoder.encode(matcher.group(0), "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                matcher.appendReplacement(stringBuffer, matcher.group(0));
            }
        }
        matcher.appendTail(stringBuffer);
        return stringBuffer.toString();
    }

    public static String getColorFormatName(int i8) {
        return _getColorFormatName(i8);
    }

    private void initHandler() {
        Looper looperMyLooper = Looper.myLooper();
        if (looperMyLooper == null) {
            looperMyLooper = Looper.getMainLooper();
        }
        this.mEventHandler = new EventHandler(this, looperMyLooper);
    }

    private static void initNativeOnce() {
        if (mIsNativeInitialized) {
            return;
        }
        native_init();
        setDot(0);
        native_setLogLevel(IJK_LOG_SILENT);
        mIsNativeInitialized = true;
    }

    private static void setDot(int dot) {
        try {
            native_setDot(dot);
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "IJK dot setting is unavailable in current native library", e);
        }
    }

    private void initPlayer() {
        if (LOADER.isAvailable()) {
            initNativeOnce();
            native_setup(new WeakReference(this));
        }
    }

    private native void native_finalize();

    private static native void native_init();

    private native void native_message_loop(Object obj);

    public static native void native_profileBegin(String str);

    public static native void native_profileEnd();

    public static native void native_setDot(int i8);

    public static native void native_setLogLevel(int i8);

    public static native void native_setReqLevel(int i8);

    private native void native_setup(Object obj);

    @CalledByNative
    private static boolean onNativeInvoke(Object obj, int i8, Bundle bundle) {
        OnControlMessageListener onControlMessageListener;
        DebugLog.ifmt(TAG, "onNativeInvoke %d", Integer.valueOf(i8));
        if (!(obj instanceof WeakReference)) {
            throw new IllegalStateException("<null weakThiz>.onNativeInvoke()");
        }
        IjkMediaPlayer ijkMediaPlayer = (IjkMediaPlayer) ((WeakReference) obj).get();
        if (ijkMediaPlayer == null) {
            throw new IllegalStateException("<null weakPlayer>.onNativeInvoke()");
        }
        OnNativeInvokeListener onNativeInvokeListener = ijkMediaPlayer.mOnNativeInvokeListener;
        if (onNativeInvokeListener != null && onNativeInvokeListener.onNativeInvoke(i8, bundle)) {
            return true;
        }
        if (i8 != 131079 || (onControlMessageListener = ijkMediaPlayer.mOnControlMessageListener) == null) {
            return false;
        }
        int i9 = bundle.getInt(OnNativeInvokeListener.ARG_SEGMENT_INDEX, -1);
        if (i9 < 0) {
            throw new InvalidParameterException("onNativeInvoke(invalid segment index)");
        }
        String strOnControlResolveSegmentUrl = onControlMessageListener.onControlResolveSegmentUrl(i9);
        if (strOnControlResolveSegmentUrl == null) {
            throw new RuntimeException(new IOException("onNativeInvoke() = <NULL newUrl>"));
        }
        bundle.putString(OnNativeInvokeListener.ARG_URL, strOnControlResolveSegmentUrl);
        return true;
    }

    @CalledByNative
    private static String onSelectCodec(Object obj, String str, int i8, int i9) {
        IjkMediaPlayer ijkMediaPlayer;
        if (!(obj instanceof WeakReference) || (ijkMediaPlayer = (IjkMediaPlayer) ((WeakReference) obj).get()) == null) {
            return null;
        }
        OnMediaCodecSelectListener onMediaCodecSelectListener = ijkMediaPlayer.mOnMediaCodecSelectListener;
        if (onMediaCodecSelectListener == null) {
            onMediaCodecSelectListener = DefaultMediaCodecSelector.sInstance;
        }
        return onMediaCodecSelectListener.onMediaCodecSelect(ijkMediaPlayer, str, i8, i9);
    }

    @CalledByNative
    private static void postEventFromNative(Object obj, int i8, int i9, int i10, Object obj2) {
        IjkMediaPlayer ijkMediaPlayer;
        if (obj == null || (ijkMediaPlayer = (IjkMediaPlayer) ((WeakReference) obj).get()) == null) {
            return;
        }
        if (i8 == 200 && i9 == 2) {
            ijkMediaPlayer.start();
        }
        EventHandler eventHandler = ijkMediaPlayer.mEventHandler;
        if (eventHandler != null) {
            ijkMediaPlayer.mEventHandler.sendMessage(eventHandler.obtainMessage(i8, i9, i10, obj2));
        }
    }

    private void stayAwake(boolean z6) {
        PowerManager.WakeLock wakeLock = this.mWakeLock;
        if (wakeLock != null) {
            if (z6 && !wakeLock.isHeld()) {
                this.mWakeLock.acquire(600000L);
            } else if (!z6 && this.mWakeLock.isHeld()) {
                this.mWakeLock.release();
            }
        }
        this.mStayAwake = z6;
        updateSurfaceScreenOn();
    }

    private void updateSurfaceScreenOn() {
        SurfaceHolder surfaceHolder = this.mSurfaceHolder;
        if (surfaceHolder != null) {
            surfaceHolder.setKeepScreenOn(this.mScreenOnWhilePlaying && this.mStayAwake);
        }
    }

    public native void _prepareAsync();

    @Override // tv.danmaku.ijk.media.player.IMediaPlayer
    public void deselectTrack(int i8) {
        try {
            _setStreamSelected(i8, false);
        } catch (Throwable unused) {
        }
    }

    public void finalize() throws Throwable {
        super.finalize();
        native_finalize();
    }

    public long getAsyncStatisticBufBackwards() {
        return _getPropertyLong(FFP_PROP_INT64_ASYNC_STATISTIC_BUF_BACKWARDS, 0L);
    }

    public long getAsyncStatisticBufCapacity() {
        return _getPropertyLong(FFP_PROP_INT64_ASYNC_STATISTIC_BUF_CAPACITY, 0L);
    }

    public long getAsyncStatisticBufForwards() {
        return _getPropertyLong(FFP_PROP_INT64_ASYNC_STATISTIC_BUF_FORWARDS, 0L);
    }

    public long getAudioCachedBytes() {
        return _getPropertyLong(FFP_PROP_INT64_AUDIO_CACHED_BYTES, 0L);
    }

    public long getAudioCachedDuration() {
        return _getPropertyLong(FFP_PROP_INT64_AUDIO_CACHED_DURATION, 0L);
    }

    public long getAudioCachedPackets() {
        return _getPropertyLong(FFP_PROP_INT64_AUDIO_CACHED_PACKETS, 0L);
    }

    @Override // tv.danmaku.ijk.media.player.IMediaPlayer
    public native int getAudioSessionId();

    public long getBitRate() {
        return _getPropertyLong(FFP_PROP_INT64_BIT_RATE, 0L);
    }

    public String getAudioCodecInfo() {
        if (!LOADER.isAvailable() || !mIsAudioCodecInfoAvailable || mNativeMediaPlayer == 0) return "";
        try {
            String value = _getAudioCodecInfo();
            return value == null ? "" : value;
        } catch (Throwable error) {
            if (error instanceof UnsatisfiedLinkError) mIsAudioCodecInfoAvailable = false;
            if (!mAudioCodecInfoFailureLogged) {
                mAudioCodecInfoFailureLogged = true;
                Log.w(TAG, "IJK audio codec info unavailable type=" + error.getClass().getSimpleName());
            }
            return "";
        }
    }

    public long getCacheStatisticCountBytes() {
        return _getPropertyLong(FFP_PROP_INT64_CACHE_STATISTIC_COUNT_BYTES, 0L);
    }

    public long getCacheStatisticFileForwards() {
        return _getPropertyLong(FFP_PROP_INT64_CACHE_STATISTIC_FILE_FORWARDS, 0L);
    }

    public long getCacheStatisticFilePos() {
        return _getPropertyLong(FFP_PROP_INT64_CACHE_STATISTIC_FILE_POS, 0L);
    }

    public long getCacheStatisticPhysicalPos() {
        return _getPropertyLong(FFP_PROP_INT64_CACHE_STATISTIC_PHYSICAL_POS, 0L);
    }

    @Override // tv.danmaku.ijk.media.player.IMediaPlayer
    public native boolean getCurrentFrame(Bitmap bitmap);

    @Override // tv.danmaku.ijk.media.player.IMediaPlayer
    public native long getCurrentPosition();

    public float getDropFrameRate() {
        return getPropertyFloat(10007, 0.0f);
    }

    @Override // tv.danmaku.ijk.media.player.IMediaPlayer
    public native long getDuration();

    public long getFileSize() {
        return _getPropertyLong(FFP_PROP_INT64_LOGICAL_FILE_SIZE, 0L);
    }

    public Bundle getMediaMeta() {
        return _getMediaMeta();
    }

    public long getSeekLoadDuration() {
        return _getPropertyLong(FFP_PROP_INT64_LATEST_SEEK_LOAD_DURATION, 0L);
    }

    @Override // tv.danmaku.ijk.media.player.IMediaPlayer
    public int getSelectedTrack(int i8) {
        long j_getPropertyLong;
        if (i8 == 1) {
            j_getPropertyLong = _getPropertyLong(FFP_PROP_INT64_SELECTED_AUDIO_STREAM, -1L);
        } else if (i8 == 2) {
            j_getPropertyLong = _getPropertyLong(FFP_PROP_INT64_SELECTED_VIDEO_STREAM, -1L);
        } else {
            if (i8 != 3) {
                return -1;
            }
            j_getPropertyLong = _getPropertyLong(FFP_PROP_INT64_SELECTED_TIMEDTEXT_STREAM, -1L);
        }
        return (int) j_getPropertyLong;
    }

    @Override // tv.danmaku.ijk.media.player.IMediaPlayer
    public float getSpeed() {
        return getPropertyFloat(10003, 1.0f);
    }

    public long getTcpSpeed() {
        return _getPropertyLong(FFP_PROP_INT64_TCP_SPEED, 0L);
    }

    @Override // tv.danmaku.ijk.media.player.IMediaPlayer
    public List<ITrackInfo> getTrackInfo() {
        return LOADER.isAvailable() ? IjkTrackInfo.fromMediaMeta(getMediaMeta()) : new ArrayList();
    }

    public long getTrafficStatisticByteCount() {
        return _getPropertyLong(FFP_PROP_INT64_TRAFFIC_STATISTIC_BYTE_COUNT, 0L);
    }

    public long getVideoCachedBytes() {
        return _getPropertyLong(FFP_PROP_INT64_VIDEO_CACHED_BYTES, 0L);
    }

    public long getVideoCachedDuration() {
        return _getPropertyLong(FFP_PROP_INT64_VIDEO_CACHED_DURATION, 0L);
    }

    public long getVideoCachedPackets() {
        return _getPropertyLong(FFP_PROP_INT64_VIDEO_CACHED_PACKETS, 0L);
    }

    public float getVideoDecodeFramesPerSecond() {
        return getPropertyFloat(10001, 0.0f);
    }

    public int getVideoDecoder() {
        return (int) _getPropertyLong(FFP_PROP_INT64_VIDEO_DECODER, 0L);
    }

    public String getVideoCodecInfo() {
        if (!LOADER.isAvailable() || !mIsVideoCodecInfoAvailable || mNativeMediaPlayer == 0) return "";
        try {
            String value = _getVideoCodecInfo();
            return value == null ? "" : value;
        } catch (Throwable error) {
            if (error instanceof UnsatisfiedLinkError) mIsVideoCodecInfoAvailable = false;
            if (!mVideoCodecInfoFailureLogged) {
                mVideoCodecInfoFailureLogged = true;
                Log.w(TAG, "IJK video codec info unavailable type=" + error.getClass().getSimpleName());
            }
            return "";
        }
    }

    @Override // tv.danmaku.ijk.media.player.IMediaPlayer
    public int getVideoHeight() {
        return this.mVideoHeight;
    }

    public float getVideoOutputFramesPerSecond() {
        return getPropertyFloat(10002, 0.0f);
    }

    @Override // tv.danmaku.ijk.media.player.IMediaPlayer
    public int getVideoSarDen() {
        return this.mVideoSarDen;
    }

    @Override // tv.danmaku.ijk.media.player.IMediaPlayer
    public int getVideoSarNum() {
        return this.mVideoSarNum;
    }

    @Override // tv.danmaku.ijk.media.player.IMediaPlayer
    public int getVideoWidth() {
        return this.mVideoWidth;
    }

    public void httphookReconnect() {
        _setPropertyLong(FFP_PROP_INT64_IMMEDIATE_RECONNECT, 1L);
    }

    @Override // tv.danmaku.ijk.media.player.IMediaPlayer
    public boolean isLooping() {
        return _getLoopCount() != 1;
    }

    @Override // tv.danmaku.ijk.media.player.IMediaPlayer
    public boolean isPlayable() {
        return true;
    }

    @Override // tv.danmaku.ijk.media.player.IMediaPlayer
    public native boolean isPlaying();

    @Override // tv.danmaku.ijk.media.player.IMediaPlayer
    public void pause() {
        stayAwake(false);
        _pause();
    }

    @Override // tv.danmaku.ijk.media.player.IMediaPlayer
    public void prepareAsync() {
        _prepareAsync();
    }

    @Override // tv.danmaku.ijk.media.player.IMediaPlayer
    public void release() {
        stayAwake(false);
        resetListeners();
        updateSurfaceScreenOn();
        if (LOADER.isAvailable()) {
            _release();
        }
    }

    @Override // tv.danmaku.ijk.media.player.IMediaPlayer
    public void reset() {
        stayAwake(false);
        this.mEventHandler.removeCallbacksAndMessages(null);
        if (LOADER.isAvailable()) {
            _reset();
        }
        this.mVideoWidth = 0;
        this.mVideoHeight = 0;
    }

    @Override // tv.danmaku.ijk.media.player.AbstractMediaPlayer
    public void resetListeners() {
        super.resetListeners();
        this.mOnMediaCodecSelectListener = null;
    }

    @Override // tv.danmaku.ijk.media.player.IMediaPlayer
    public native void seekTo(long j8);

    @Override // tv.danmaku.ijk.media.player.IMediaPlayer
    public void selectTrack(int i8) {
        try {
            _setStreamSelected(i8, true);
        } catch (Throwable unused) {
        }
    }

    @Override // tv.danmaku.ijk.media.player.IMediaPlayer
    public void setAudioStreamType(int i8) {
    }

    public void setCacheShare(int i8) {
        _setPropertyLong(FFP_PROP_INT64_SHARE_CACHE_DATA, i8);
    }

    @Override // tv.danmaku.ijk.media.player.IMediaPlayer
    public void setDataSource(Context context, Uri uri, Map<String, String> map) {
        if ("file".equals(uri.getScheme())) {
            setDataSource(uri.getPath());
        } else {
            setDataSource(encodeSpaceChinese(uri.toString()), map);
        }
    }

    @Override // tv.danmaku.ijk.media.player.IMediaPlayer
    public void setDataSource(String str) {
        _setDataSource(str, null, null);
    }

    public void setDataSource(String str, Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            setDataSource(str);
            return;
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (TextUtils.isEmpty(key)) continue;
            if ("User-Agent".equalsIgnoreCase(key)) {
                if (!TextUtils.isEmpty(value)) setOption(OPT_CATEGORY_FORMAT, "user_agent", value);
                continue;
            }
            builder.append(key).append(":");
            if (!TextUtils.isEmpty(value)) builder.append(value);
            builder.append("\r\n");
        }
        if (builder.length() > 0) setOption(OPT_CATEGORY_FORMAT, "headers", builder.toString());
        setDataSource(str);
    }

    @Override // tv.danmaku.ijk.media.player.IMediaPlayer
    public void setDisplay(SurfaceHolder surfaceHolder) {
        this.mSurfaceHolder = surfaceHolder;
        setVideoSurface(surfaceHolder != null ? surfaceHolder.getSurface() : null);
        updateSurfaceScreenOn();
    }

    @Override // tv.danmaku.ijk.media.player.IMediaPlayer
    public void setKeepInBackground(boolean z6) {
    }

    @Override // tv.danmaku.ijk.media.player.IMediaPlayer
    public void setLogEnabled(boolean z6) {
    }

    @Override // tv.danmaku.ijk.media.player.IMediaPlayer
    public void setLooping(boolean z6) {
        int i8 = !z6 ? 1 : 0;
        setOption(4, "loop", i8);
        _setLoopCount(i8);
    }

    public void setOnControlMessageListener(OnControlMessageListener onControlMessageListener) {
        this.mOnControlMessageListener = onControlMessageListener;
    }

    public void setOnMediaCodecSelectListener(OnMediaCodecSelectListener onMediaCodecSelectListener) {
        this.mOnMediaCodecSelectListener = onMediaCodecSelectListener;
    }

    public void setOnNativeInvokeListener(OnNativeInvokeListener onNativeInvokeListener) {
        this.mOnNativeInvokeListener = onNativeInvokeListener;
    }

    @Override // tv.danmaku.ijk.media.player.IMediaPlayer
    public void setOption(int i8, String str, long j8) {
        _setOption(i8, str, j8);
    }

    @Override // tv.danmaku.ijk.media.player.IMediaPlayer
    public void setOption(int i8, String str, String str2) {
        _setOption(i8, str, str2);
    }

    @Override // tv.danmaku.ijk.media.player.IMediaPlayer
    public void setScreenOnWhilePlaying(boolean z6) {
        if (this.mScreenOnWhilePlaying != z6) {
            this.mScreenOnWhilePlaying = z6;
            updateSurfaceScreenOn();
        }
    }

    @Override // tv.danmaku.ijk.media.player.IMediaPlayer
    public void setSpeed(float f) {
        setPropertyFloat(10003, f);
    }

    @Override // tv.danmaku.ijk.media.player.IMediaPlayer
    public void setSurface(Surface surface) {
        this.mSurfaceHolder = null;
        setVideoSurface(surface);
        updateSurfaceScreenOn();
    }

    @Override // tv.danmaku.ijk.media.player.IMediaPlayer
    public native void setVolume(float f, float f4);

    @Override // tv.danmaku.ijk.media.player.IMediaPlayer
    public void setWakeMode(Context context, int i8) {
        boolean z6;
        PowerManager.WakeLock wakeLock = this.mWakeLock;
        if (wakeLock != null) {
            if (wakeLock.isHeld()) {
                this.mWakeLock.release();
                z6 = true;
            } else {
                z6 = false;
            }
            this.mWakeLock = null;
        } else {
            z6 = false;
        }
        PowerManager.WakeLock wakeLockNewWakeLock = ((PowerManager) context.getSystemService("power")).newWakeLock(i8 | 536870912, IjkMediaPlayer.class.getName());
        this.mWakeLock = wakeLockNewWakeLock;
        wakeLockNewWakeLock.setReferenceCounted(false);
        if (z6) {
            this.mWakeLock.acquire(600000L);
        }
    }

    @Override // tv.danmaku.ijk.media.player.IMediaPlayer
    public void start() {
        stayAwake(true);
        _start();
    }

    @Override // tv.danmaku.ijk.media.player.IMediaPlayer
    public void stop() {
        stayAwake(false);
        _stop();
    }
}
