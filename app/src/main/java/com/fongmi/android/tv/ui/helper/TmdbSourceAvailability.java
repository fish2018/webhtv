package com.fongmi.android.tv.ui.helper;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.bean.TmdbSourcePayload;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.setting.TmdbSourceState;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;

/** Classifies whether a source payload is merely valid identity data or can render TMDB UI. */
public final class TmdbSourceAvailability {

    public static TmdbSourceState classify(
            @Nullable Vod vod,
            @Nullable TmdbSourcePayload payload,
            @Nullable TmdbBundle bundle
    ) {
        TmdbSourcePayload normalized = TmdbSourcePayloadParser.parse(payload);
        if (normalized == null || bundle == null || bundle.item() == null) return TmdbSourceState.ABSENT_OR_INVALID;
        if (!matches(normalized, bundle.item().getTmdbId(), bundle.item().getMediaType())) {
            return TmdbSourceState.ABSENT_OR_INVALID;
        }
        if (vod != null && vod.getTmdb() != null) {
            TmdbSourcePayload vodPayload = TmdbSourcePayloadParser.parse(vod.getTmdb());
            if (vodPayload == null || !sameIdentity(normalized, vodPayload)) {
                return TmdbSourceState.ABSENT_OR_INVALID;
            }
        }
        return isRenderable(bundle) ? TmdbSourceState.RENDERABLE : TmdbSourceState.IDENTITY_ONLY;
    }

    public static boolean isRenderable(@Nullable TmdbBundle bundle) {
        if (bundle == null || bundle.item() == null) return false;
        if (hasItems(bundle.cast()) || hasItems(bundle.creators()) || hasItems(bundle.photos()) || hasItems(bundle.related())) {
            return true;
        }
        if (hasItems(bundle.seasons()) || hasItems(bundle.seasonCounts()) || hasItems(bundle.seasonEpisodes())
                || hasItems(bundle.seasonCast()) || hasItems(bundle.seasonPhotos())) {
            return true;
        }
        return hasVisibleFields(bundle.detail());
    }

    private static boolean hasVisibleFields(@Nullable JsonObject detail) {
        if (detail == null || detail.isEmpty()) return false;
        if (hasNonBlank(detail, "title", "name", "original_title", "original_name", "overview", "tagline", "status",
                "release_date", "first_air_date", "original_language", "poster_path", "backdrop_path")) {
            return true;
        }
        if (hasPositive(detail, "vote_average", "vote_count", "runtime", "number_of_seasons", "number_of_episodes")) {
            return true;
        }
        if (hasArrayItems(detail, "genres", "origin_country", "episode_run_time", "seasons", "episodes")) return true;
        if (hasArrayItems(object(detail, "images"), "backdrops", "posters", "logos")) return true;
        if (hasArrayItems(object(detail, "credits"), "cast", "crew")) return true;
        if (hasArrayItems(object(detail, "aggregate_credits"), "cast", "crew")) return true;
        if (hasArrayItems(object(detail, "videos"), "results")) return true;
        if (hasArrayItems(object(detail, "recommendations"), "results")) return true;
        if (hasArrayItems(object(detail, "similar"), "results")) return true;
        return hasExternalIds(detail.get("external_ids"));
    }

    private static boolean hasNonBlank(JsonObject object, String... keys) {
        if (object == null) return false;
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                    && !value.getAsString().trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasPositive(JsonObject object, String... keys) {
        if (object == null) return false;
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                try {
                    if (value.getAsDouble() > 0d) return true;
                } catch (RuntimeException ignored) {
                    // Keep checking the remaining fields.
                }
            }
        }
        return false;
    }

    private static boolean hasArrayItems(@Nullable JsonObject object, String... keys) {
        if (object == null) return false;
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value != null && value.isJsonArray() && !value.getAsJsonArray().isEmpty()) return true;
        }
        return false;
    }

    private static boolean hasExternalIds(@Nullable JsonElement value) {
        if (value == null || !value.isJsonObject()) return false;
        JsonObject ids = value.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : ids.entrySet()) {
            JsonElement id = entry.getValue();
            if (id == null || id.isJsonNull() || !id.isJsonPrimitive()) continue;
            if (id.getAsJsonPrimitive().isString() && !id.getAsString().trim().isEmpty()) return true;
            if (id.getAsJsonPrimitive().isNumber()) {
                try {
                    if (id.getAsLong() > 0) return true;
                } catch (RuntimeException ignored) {
                    // Keep checking the remaining IDs.
                }
            }
        }
        return false;
    }

    private static JsonObject object(@Nullable JsonObject parent, String key) {
        if (parent == null || !parent.has(key) || !parent.get(key).isJsonObject()) return null;
        return parent.getAsJsonObject(key);
    }

    private static boolean hasItems(@Nullable Iterable<?> values) {
        return values != null && values.iterator().hasNext();
    }

    private static boolean hasItems(@Nullable Map<?, ?> values) {
        return values != null && !values.isEmpty();
    }

    private static boolean hasItems(@Nullable List<?> values) {
        return values != null && !values.isEmpty();
    }

    private static boolean matches(TmdbSourcePayload payload, int tmdbId, String mediaType) {
        return tmdbId > 0 && payload.getTmdbId() == tmdbId
                && payload.getMediaType().equalsIgnoreCase(mediaType == null ? "" : mediaType);
    }

    private static boolean sameIdentity(TmdbSourcePayload first, TmdbSourcePayload second) {
        return first.getTmdbId() == second.getTmdbId()
                && first.getMediaType().equalsIgnoreCase(second.getMediaType());
    }

    private TmdbSourceAvailability() {
    }
}
