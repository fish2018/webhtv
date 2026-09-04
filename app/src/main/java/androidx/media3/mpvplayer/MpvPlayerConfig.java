package androidx.media3.mpvplayer;

import android.content.Context;

import androidx.annotation.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MpvPlayerConfig {

    public static final long DEFAULT_DEMUXER_BYTES = 64L * 1024L * 1024L;
    public static final int DEFAULT_CACHE_SECONDS = 20;
    public static final int DEFAULT_DEMUXER_READAHEAD_SECONDS = 1;
    public static final int DEFAULT_DEMUXER_HYSTERESIS_SECONDS = 0;

    private final File configDir;
    private final File cacheDir;
    private final File caFile;
    private final String userAgent;
    private final String referer;
    private final String hwdec;
    private final String vo;
    private final String gpuContext;
    private final String gpuApi;
    private final String ao;
    private final String audioSpdif;
    private final String logLevel;
    private final boolean openglEs;
    private final boolean tlsVerify;
    private final boolean cache;
    private final long demuxerMaxBytes;
    private final long demuxerMaxBackBytes;
    private final int cacheSeconds;
    private final int demuxerReadaheadSeconds;
    private final int demuxerHysteresisSeconds;
    private final int rebufferMs;
    private final boolean performanceOptionsPriority;
    private final boolean automaticCacheTime;
    private final boolean automaticHlsVariant;
    private final boolean deferStartupTrackRefresh;
    private final Map<String, String> extraOptions;

    private MpvPlayerConfig(Builder builder) {
        configDir = builder.configDir;
        cacheDir = builder.cacheDir;
        caFile = builder.caFile;
        userAgent = builder.userAgent;
        referer = builder.referer;
        hwdec = builder.hwdec;
        vo = builder.vo;
        gpuContext = builder.gpuContext;
        gpuApi = builder.gpuApi;
        ao = builder.ao;
        audioSpdif = builder.audioSpdif;
        logLevel = builder.logLevel;
        openglEs = builder.openglEs;
        tlsVerify = builder.tlsVerify;
        cache = builder.cache;
        demuxerMaxBytes = builder.demuxerMaxBytes;
        demuxerMaxBackBytes = builder.demuxerMaxBackBytes;
        cacheSeconds = builder.cacheSeconds;
        demuxerReadaheadSeconds = builder.demuxerReadaheadSeconds;
        demuxerHysteresisSeconds = builder.demuxerHysteresisSeconds;
        rebufferMs = builder.rebufferMs;
        performanceOptionsPriority = builder.performanceOptionsPriority;
        automaticCacheTime = builder.automaticCacheTime;
        automaticHlsVariant = builder.automaticHlsVariant;
        deferStartupTrackRefresh = builder.deferStartupTrackRefresh;
        extraOptions = Collections.unmodifiableMap(new LinkedHashMap<>(builder.extraOptions));
    }

    public static Builder builder(Context context) {
        return new Builder(context);
    }

    public File configDir() {
        return configDir;
    }

    public File cacheDir() {
        return cacheDir;
    }

    public File caFile() {
        return caFile;
    }

    @Nullable
    public String userAgent() {
        return userAgent;
    }

    @Nullable
    public String referer() {
        return referer;
    }

    public String hwdec() {
        return hwdec;
    }

    public String vo() {
        return vo;
    }

    public String gpuContext() {
        return gpuContext;
    }

    public String gpuApi() {
        return gpuApi;
    }

    public String ao() {
        return ao;
    }

    public String audioSpdif() {
        return audioSpdif;
    }

    public String logLevel() {
        return logLevel;
    }

    public boolean openglEs() {
        return openglEs;
    }

    public boolean tlsVerify() {
        return tlsVerify;
    }

    public boolean cache() {
        return cache;
    }

    public long demuxerMaxBytes() {
        return demuxerMaxBytes;
    }

    public long demuxerMaxBackBytes() {
        return demuxerMaxBackBytes;
    }

    public int cacheSeconds() {
        return cacheSeconds;
    }

    public int demuxerReadaheadSeconds() {
        return demuxerReadaheadSeconds;
    }

    public int demuxerHysteresisSeconds() {
        return demuxerHysteresisSeconds;
    }

    public int rebufferMs() {
        return rebufferMs;
    }

    public boolean performanceOptionsPriority() {
        return performanceOptionsPriority;
    }

    public boolean automaticCacheTime() {
        return automaticCacheTime;
    }

    public boolean automaticHlsVariant() {
        return automaticHlsVariant;
    }

    public boolean deferStartupTrackRefresh() {
        return deferStartupTrackRefresh;
    }

    public Map<String, String> extraOptions() {
        return extraOptions;
    }

    public static final class Builder {

        private final Map<String, String> extraOptions = new LinkedHashMap<>();
        private File configDir;
        private File cacheDir;
        private File caFile;
        private String userAgent;
        private String referer;
        private String hwdec = "mediacodec,mediacodec-copy";
        private String vo = "gpu";
        private String gpuContext = "android";
        private String gpuApi = "";
        private String ao = "audiotrack,opensles";
        private String audioSpdif = "";
        private String logLevel = "all=v";
        private boolean openglEs = true;
        private boolean tlsVerify = true;
        private boolean cache = true;
        private long demuxerMaxBytes = DEFAULT_DEMUXER_BYTES;
        private long demuxerMaxBackBytes = DEFAULT_DEMUXER_BYTES;
        private int cacheSeconds = DEFAULT_CACHE_SECONDS;
        private int demuxerReadaheadSeconds = DEFAULT_DEMUXER_READAHEAD_SECONDS;
        private int demuxerHysteresisSeconds = DEFAULT_DEMUXER_HYSTERESIS_SECONDS;
        private int rebufferMs = 5_000;
        private boolean performanceOptionsPriority = true;
        private boolean automaticCacheTime;
        private boolean automaticHlsVariant;
        private boolean deferStartupTrackRefresh;

        private Builder(Context context) {
            Context app = context.getApplicationContext();
            configDir = app.getFilesDir();
            cacheDir = app.getCacheDir();
            caFile = new File(app.getFilesDir(), "cacert.pem");
        }

        public Builder configDir(File configDir) {
            this.configDir = configDir;
            return this;
        }

        public Builder cacheDir(File cacheDir) {
            this.cacheDir = cacheDir;
            return this;
        }

        public Builder caFile(File caFile) {
            this.caFile = caFile;
            return this;
        }

        public Builder userAgent(@Nullable String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public Builder referer(@Nullable String referer) {
            this.referer = referer;
            return this;
        }

        public Builder hwdec(String hwdec) {
            this.hwdec = hwdec;
            return this;
        }

        public Builder vo(String vo) {
            this.vo = vo;
            return this;
        }

        public Builder gpuContext(String gpuContext) {
            this.gpuContext = gpuContext;
            return this;
        }

        public Builder gpuApi(String gpuApi) {
            this.gpuApi = gpuApi;
            return this;
        }

        public Builder openglEs(boolean openglEs) {
            this.openglEs = openglEs;
            return this;
        }

        public Builder ao(String ao) {
            this.ao = ao;
            return this;
        }

        public Builder audioSpdif(String audioSpdif) {
            this.audioSpdif = audioSpdif;
            return this;
        }

        public Builder logLevel(String logLevel) {
            this.logLevel = logLevel;
            return this;
        }

        public Builder tlsVerify(boolean tlsVerify) {
            this.tlsVerify = tlsVerify;
            return this;
        }

        public Builder cache(boolean cache) {
            this.cache = cache;
            return this;
        }

        public Builder demuxerMaxBytes(long demuxerMaxBytes) {
            this.demuxerMaxBytes = demuxerMaxBytes;
            return this;
        }

        public Builder demuxerMaxBackBytes(long demuxerMaxBackBytes) {
            this.demuxerMaxBackBytes = demuxerMaxBackBytes;
            return this;
        }

        public Builder cacheSeconds(int cacheSeconds) {
            this.cacheSeconds = cacheSeconds;
            return this;
        }

        public Builder demuxerReadaheadSeconds(int demuxerReadaheadSeconds) {
            this.demuxerReadaheadSeconds = demuxerReadaheadSeconds;
            return this;
        }

        public Builder demuxerHysteresisSeconds(int demuxerHysteresisSeconds) {
            this.demuxerHysteresisSeconds = demuxerHysteresisSeconds;
            return this;
        }

        public Builder rebufferMs(int rebufferMs) {
            this.rebufferMs = Math.max(0, rebufferMs);
            return this;
        }

        public Builder performanceOptionsPriority(boolean performanceOptionsPriority) {
            this.performanceOptionsPriority = performanceOptionsPriority;
            return this;
        }

        public Builder automaticCacheTime(boolean automaticCacheTime) {
            this.automaticCacheTime = automaticCacheTime;
            return this;
        }

        public Builder automaticHlsVariant(boolean automaticHlsVariant) {
            this.automaticHlsVariant = automaticHlsVariant;
            return this;
        }

        public Builder deferStartupTrackRefresh(boolean deferStartupTrackRefresh) {
            this.deferStartupTrackRefresh = deferStartupTrackRefresh;
            return this;
        }

        public Builder option(String name, String value) {
            extraOptions.put(name, value);
            return this;
        }

        public MpvPlayerConfig build() {
            return new MpvPlayerConfig(this);
        }
    }
}
