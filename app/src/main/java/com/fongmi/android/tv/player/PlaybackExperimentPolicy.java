package com.fongmi.android.tv.player;

import java.util.HashSet;
import java.util.Set;

/** Pure, versioned admission policy for automatic optimizations and internal experiments. */
public final class PlaybackExperimentPolicy {

    public static final int CURRENT_SCHEMA_VERSION = 2;
    public static final String STABLE_STRATEGY_ID = "playback-auto-production-v2";
    public static final String EXPERIMENT_STRATEGY_ID =
            "playback-internal-experiment-v2";

    private PlaybackExperimentPolicy() {
    }

    public static Resolution resolve(RawState rawState) {
        RawState raw = rawState == null ? RawState.missing() : rawState;
        Integer version = integer(raw.schemaVersion());
        if (version == null) {
            return stable(raw.schemaVersion() == null
                    ? Status.UNINITIALIZED : Status.CORRUPT, true);
        }
        if (version > CURRENT_SCHEMA_VERSION) {
            return stable(Status.FUTURE_SCHEMA, false);
        }
        if (version < 0) {
            return stable(Status.CORRUPT, true);
        }
        if (version == 0) {
            Boolean enabled = bool(raw.enabled());
            if (enabled == null) {
                return stable(Status.CORRUPT, true);
            }
            return migratedStable();
        }
        if (version == 1) {
            Boolean enabled = bool(raw.enabled());
            Boolean exo = bool(raw.exoEnabled());
            Boolean mpv = bool(raw.mpvEnabled());
            Boolean ijk = bool(raw.ijkEnabled());
            if (enabled == null || exo == null || mpv == null || ijk == null) {
                return stable(Status.CORRUPT, true);
            }
            return migratedStable();
        }
        Boolean enabled = bool(raw.enabled());
        Boolean exo = bool(raw.exoEnabled());
        Boolean mpv = bool(raw.mpvEnabled());
        Boolean ijk = bool(raw.ijkEnabled());
        if (enabled == null || exo == null || mpv == null || ijk == null) {
            return stable(Status.CORRUPT, true);
        }
        return new Resolution(
                new State(CURRENT_SCHEMA_VERSION, enabled, exo, mpv, ijk),
                Status.CURRENT,
                false,
                true);
    }

    public static boolean registryIsValid() {
        Set<String> ids = new HashSet<>();
        for (Action action : Action.values()) {
            if (action.id().isBlank()
                    || action.introducedInSchema() <= 0
                    || action.introducedInSchema() > CURRENT_SCHEMA_VERSION
                    || !ids.add(action.id())) {
                return false;
            }
        }
        return true;
    }

    private static Resolution stable(Status status, boolean writeBack) {
        return new Resolution(State.stable(), status, writeBack, false);
    }

    private static Resolution migratedStable() {
        return new Resolution(
                State.stable(), Status.MIGRATED, true, true);
    }

    private static Integer integer(Object value) {
        if (!(value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long)) return null;
        Number number = (Number) value;
        long result = number.longValue();
        return result < Integer.MIN_VALUE || result > Integer.MAX_VALUE
                ? null : (int) result;
    }

    private static Boolean bool(Object value) {
        return value instanceof Boolean booleanValue ? booleanValue : null;
    }

    public enum Domain {
        SHARED,
        EXO,
        MPV,
        IJK
    }

    public enum Risk {
        SAFETY_PROTECTION,
        STABLE_BASELINE,
        AUTOMATIC_OPTIMIZATION,
        INTERNAL_EXPERIMENT
    }

    public enum Action {
        READ_ONLY_TELEMETRY("shared.telemetry", Domain.SHARED,
                Risk.STABLE_BASELINE, 1),
        DISK_CAPACITY_PROTECTION("shared.disk-capacity-protection", Domain.SHARED,
                Risk.SAFETY_PROTECTION, 1),
        MEMORY_PRESSURE_SHRINK("shared.memory-pressure-shrink", Domain.SHARED,
                Risk.SAFETY_PROTECTION, 1),
        SHARED_PROFILE_AB_VALIDATION("shared.profile-ab-validation",
                Domain.SHARED, Risk.INTERNAL_EXPERIMENT, 1),
        EXO_INITIAL_BASELINE("exo.initial-baseline", Domain.EXO,
                Risk.STABLE_BASELINE, 1),
        EXO_AUTO_PRELOAD("exo.auto-preload", Domain.EXO,
                Risk.AUTOMATIC_OPTIMIZATION, 1),
        EXO_SHORT_DRAMA_QUEUE("exo.short-drama-queue", Domain.EXO,
                Risk.AUTOMATIC_OPTIMIZATION, 2),
        EXO_NETWORK_SPEED("exo.network-speed", Domain.EXO,
                Risk.AUTOMATIC_OPTIMIZATION, 1),
        EXO_RTSP_RECOVERY("exo.rtsp-recovery", Domain.EXO,
                Risk.AUTOMATIC_OPTIMIZATION, 1),
        EXO_VIDEO_CONSTRAINT("exo.video-constraint", Domain.EXO,
                Risk.AUTOMATIC_OPTIMIZATION, 1),
        EXO_DECODER_RUNTIME_REBUILD("exo.decoder-runtime-rebuild", Domain.EXO,
                Risk.AUTOMATIC_OPTIMIZATION, 1),
        EXO_FRAME_SCHEDULING_AB("exo.frame-scheduling-ab", Domain.EXO,
                Risk.INTERNAL_EXPERIMENT, 1),
        MPV_INITIAL_BASELINE("mpv.initial-baseline", Domain.MPV,
                Risk.STABLE_BASELINE, 1),
        MPV_CACHE_SHRINK("mpv.cache-shrink", Domain.MPV,
                Risk.SAFETY_PROTECTION, 1),
        MPV_CACHE_EXPANSION("mpv.cache-expansion", Domain.MPV,
                Risk.AUTOMATIC_OPTIMIZATION, 1),
        MPV_AUTO_PRELOAD("mpv.auto-preload", Domain.MPV,
                Risk.AUTOMATIC_OPTIMIZATION, 1),
        MPV_HLS_RUNTIME_RELOAD("mpv.hls-runtime-reload", Domain.MPV,
                Risk.AUTOMATIC_OPTIMIZATION, 1),
        IJK_INITIAL_BASELINE("ijk.initial-baseline", Domain.IJK,
                Risk.STABLE_BASELINE, 1),
        IJK_BUFFER_SAFETY_RELOAD("ijk.buffer-safety-reload", Domain.IJK,
                Risk.SAFETY_PROTECTION, 1),
        IJK_BUFFER_RELOAD("ijk.buffer-reload", Domain.IJK,
                Risk.AUTOMATIC_OPTIMIZATION, 1),
        IJK_REALTIME_REBUILD("ijk.realtime-rebuild", Domain.IJK,
                Risk.AUTOMATIC_OPTIMIZATION, 1),
        IJK_DECODE_REBUILD("ijk.decode-rebuild", Domain.IJK,
                Risk.AUTOMATIC_OPTIMIZATION, 1),
        IJK_RUNTIME_KERNEL_FALLBACK("ijk.runtime-kernel-fallback", Domain.IJK,
                Risk.AUTOMATIC_OPTIMIZATION, 1);

        private final String id;
        private final Domain domain;
        private final Risk risk;
        private final int introducedInSchema;

        Action(String id, Domain domain, Risk risk, int introducedInSchema) {
            this.id = id;
            this.domain = domain;
            this.risk = risk;
            this.introducedInSchema = introducedInSchema;
        }

        public String id() {
            return id;
        }

        public Domain domain() {
            return domain;
        }

        public Risk risk() {
            return risk;
        }

        public int introducedInSchema() {
            return introducedInSchema;
        }
    }

    public enum Status {
        CURRENT,
        UNINITIALIZED,
        MIGRATED,
        CORRUPT,
        FUTURE_SCHEMA
    }

    public record RawState(
            Object schemaVersion,
            Object enabled,
            Object exoEnabled,
            Object mpvEnabled,
            Object ijkEnabled) {

        public static RawState missing() {
            return new RawState(null, null, null, null, null);
        }
    }

    public record State(
            int schemaVersion,
            boolean enabled,
            boolean exoEnabled,
            boolean mpvEnabled,
            boolean ijkEnabled) {

        public State {
            schemaVersion = CURRENT_SCHEMA_VERSION;
        }

        public static State stable() {
            return new State(CURRENT_SCHEMA_VERSION, false, true, true, true);
        }

        public boolean allows(Action action) {
            if (action == null) return false;
            if (action.risk() != Risk.INTERNAL_EXPERIMENT) return true;
            if (!enabled) return false;
            return switch (action.domain()) {
                case EXO -> exoEnabled;
                case MPV -> mpvEnabled;
                case IJK -> ijkEnabled;
                case SHARED -> true;
            };
        }

        public boolean domainEnabled(Domain domain) {
            if (!enabled || domain == null) return false;
            return switch (domain) {
                case EXO -> exoEnabled;
                case MPV -> mpvEnabled;
                case IJK -> ijkEnabled;
                case SHARED -> true;
            };
        }

        public String strategyId() {
            return enabled ? EXPERIMENT_STRATEGY_ID : STABLE_STRATEGY_ID;
        }
    }

    public record Resolution(
            State state,
            Status status,
            boolean writeBack,
            boolean sourceValid) {

        public Resolution {
            state = state == null ? State.stable() : state;
            status = status == null ? Status.CORRUPT : status;
        }

        public boolean failClosed() {
            return !sourceValid || status == Status.FUTURE_SCHEMA;
        }
    }
}
