package com.fongmi.android.tv.bean;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fongmi.android.tv.utils.VodDetailCache;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** C16 source-embedded TMDB payload. */
@JsonAdapter(TmdbSourcePayload.ProtocolAdapter.class)
public final class TmdbSourcePayload implements Parcelable {

    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_PARCEL_BYTES = 256 * 1024;
    public static final String SOURCE_TMDB = "source_tmdb";
    public static final String CACHE_TMDB = "cache_tmdb";
    public static final String REMOTE_TMDB = "remote_tmdb";

    @SerializedName("schema")
    private int schema;
    @SerializedName("id")
    private int tmdbId;
    @SerializedName("media_type")
    private String mediaType;
    @SerializedName("season_number")
    private int seasonNumber;
    @SerializedName("language")
    private String language;
    @SerializedName("fetched_at")
    private String fetchedAt;
    @SerializedName("complete")
    private Set<String> complete;
    @SerializedName("detail")
    private String detailJson;
    private transient String sourceKind;

    public TmdbSourcePayload() {
        schema = 0;
        mediaType = "";
        language = "";
        fetchedAt = "";
        complete = new LinkedHashSet<>();
        detailJson = "{}";
        sourceKind = SOURCE_TMDB;
    }

    public int getSchema() {
        return schema;
    }

    public void setSchema(int schema) {
        this.schema = schema;
    }

    public int getTmdbId() {
        return tmdbId;
    }

    public void setTmdbId(int tmdbId) {
        this.tmdbId = tmdbId;
    }

    public String getMediaType() {
        return mediaType == null ? "" : mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public int getSeasonNumber() {
        return seasonNumber;
    }

    public void setSeasonNumber(int seasonNumber) {
        this.seasonNumber = seasonNumber;
    }

    public String getLanguage() {
        return language == null ? "" : language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getFetchedAt() {
        return fetchedAt == null ? "" : fetchedAt;
    }

    public void setFetchedAt(String fetchedAt) {
        this.fetchedAt = fetchedAt;
    }

    public Set<String> getComplete() {
        return complete == null ? Collections.emptySet() : Collections.unmodifiableSet(complete);
    }

    public void setComplete(@Nullable Collection<String> complete) {
        this.complete = complete == null ? new LinkedHashSet<>() : new LinkedHashSet<>(complete);
    }

    public boolean hasCapability(String group) {
        return getComplete().contains(group);
    }

    public String getDetailJson() {
        return detailJson == null || detailJson.isBlank() ? "{}" : detailJson;
    }

    public void setDetailJson(@Nullable String detailJson) {
        this.detailJson = detailJson == null || detailJson.isBlank() ? "{}" : detailJson;
    }

    public TmdbSourceDetail getDetail() {
        return new TmdbSourceDetail(getDetailJson());
    }

    public String getSourceKind() {
        return sourceKind == null || sourceKind.isBlank() ? SOURCE_TMDB : sourceKind;
    }

    public void setSourceKind(String sourceKind) {
        this.sourceKind = sourceKind;
    }

    public TmdbSourcePayload copy() {
        TmdbSourcePayload copy = new TmdbSourcePayload();
        copy.schema = schema;
        copy.tmdbId = tmdbId;
        copy.mediaType = mediaType;
        copy.seasonNumber = seasonNumber;
        copy.language = language;
        copy.fetchedAt = fetchedAt;
        copy.complete = new LinkedHashSet<>(getComplete());
        copy.detailJson = getDetailJson();
        copy.sourceKind = getSourceKind();
        return copy;
    }

    public boolean isIdentityValid() {
        return schema == SCHEMA_VERSION && tmdbId > 0 && ("movie".equals(getMediaType()) || "tv".equals(getMediaType()))
                && (("movie".equals(getMediaType()) && seasonNumber == 0) || ("tv".equals(getMediaType()) && seasonNumber >= 0));
    }

    public String identityKey() {
        return getMediaType() + ':' + tmdbId + ":season=" + seasonNumber;
    }

    public long estimatedParcelBytes() {
        long size = 32L;
        size += utf8Length(mediaType);
        size += utf8Length(language);
        size += utf8Length(fetchedAt);
        size += utf8Length(detailJson);
        size += utf8Length(sourceKind);
        for (String group : getComplete()) size += 8L + utf8Length(group);
        return size;
    }

    public boolean requiresParcelCache() {
        return estimatedParcelBytes() > MAX_PARCEL_BYTES;
    }

    String putParcelCacheCopy() {
        Vod holder = new Vod();
        holder.setTmdb(copy());
        return VodDetailCache.put(holder);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        if (requiresParcelCache()) {
            dest.writeByte((byte) 2);
            dest.writeString(putParcelCacheCopy());
            return;
        }
        dest.writeByte((byte) 1);
        dest.writeInt(schema);
        dest.writeInt(tmdbId);
        dest.writeString(mediaType);
        dest.writeInt(seasonNumber);
        dest.writeString(language);
        dest.writeString(fetchedAt);
        dest.writeStringList(new ArrayList<>(getComplete()));
        dest.writeString(getDetailJson());
        dest.writeString(getSourceKind());
    }

    public static final Creator<TmdbSourcePayload> CREATOR = new Creator<>() {
        @Override
        public TmdbSourcePayload createFromParcel(Parcel source) {
            byte mode = source.readByte();
            if (mode == 2) return takeParcelCache(source.readString());
            TmdbSourcePayload payload = new TmdbSourcePayload();
            payload.schema = source.readInt();
            payload.tmdbId = source.readInt();
            payload.mediaType = source.readString();
            payload.seasonNumber = source.readInt();
            payload.language = source.readString();
            payload.fetchedAt = source.readString();
            ArrayList<String> complete = new ArrayList<>();
            source.readStringList(complete);
            payload.complete = new LinkedHashSet<>(complete);
            payload.detailJson = source.readString();
            payload.sourceKind = source.readString();
            return payload;
        }

        @Override
        public TmdbSourcePayload[] newArray(int size) {
            return new TmdbSourcePayload[size];
        }
    };

    @Nullable
    private static TmdbSourcePayload takeParcelCache(String key) {
        Vod cached = VodDetailCache.take(key);
        return cached == null ? null : cached.getTmdb();
    }

    private static int utf8Length(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof TmdbSourcePayload it)) return false;
        return schema == it.schema && tmdbId == it.tmdbId && seasonNumber == it.seasonNumber
                && Objects.equals(getMediaType(), it.getMediaType()) && Objects.equals(getLanguage(), it.getLanguage())
                && Objects.equals(getFetchedAt(), it.getFetchedAt()) && Objects.equals(getComplete(), it.getComplete())
                && Objects.equals(getDetailJson(), it.getDetailJson());
    }

    @Override
    public int hashCode() {
        return Objects.hash(schema, tmdbId, getMediaType(), seasonNumber, getLanguage(), getFetchedAt(), getComplete(), getDetailJson());
    }

    public static final class ProtocolAdapter implements JsonDeserializer<TmdbSourcePayload>, JsonSerializer<TmdbSourcePayload> {

        @Override
        public TmdbSourcePayload deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
            if (json == null || !json.isJsonObject()) return null;
            JsonObject object = json.getAsJsonObject();
            TmdbSourcePayload payload = new TmdbSourcePayload();
            payload.schema = intValue(object.get("schema"), 0);
            payload.tmdbId = intValue(object.get("id"), 0);
            payload.mediaType = stringValue(object.get("media_type"));
            payload.seasonNumber = intValue(object.get("season_number"), 0);
            payload.language = stringValue(object.get("language"));
            payload.fetchedAt = stringValue(object.get("fetched_at"));
            payload.complete = stringSet(object.get("complete"));
            payload.detailJson = objectString(object.get("detail"));
            payload.sourceKind = SOURCE_TMDB;
            return payload;
        }

        @Override
        public JsonElement serialize(TmdbSourcePayload payload, Type type, JsonSerializationContext context) {
            if (payload == null) return JsonNull.INSTANCE;
            JsonObject object = new JsonObject();
            object.addProperty("schema", payload.schema);
            object.addProperty("id", payload.tmdbId);
            if (!payload.getMediaType().isEmpty()) object.addProperty("media_type", payload.getMediaType());
            if (payload.seasonNumber != 0 || "tv".equals(payload.getMediaType())) object.addProperty("season_number", payload.seasonNumber);
            if (!payload.getLanguage().isEmpty()) object.addProperty("language", payload.getLanguage());
            if (!payload.getFetchedAt().isEmpty()) object.addProperty("fetched_at", payload.getFetchedAt());
            if (!payload.getComplete().isEmpty()) {
                JsonArray complete = new JsonArray();
                for (String group : new TreeSet<>(payload.getComplete())) complete.add(group);
                object.add("complete", complete);
            }
            object.add("detail", parseObject(payload.getDetailJson()));
            return object;
        }

        private static int intValue(JsonElement element, int fallback) {
            if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) return fallback;
            try {
                return element.getAsInt();
            } catch (RuntimeException ignored) {
                return fallback;
            }
        }

        private static String stringValue(JsonElement element) {
            if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) return "";
            String value = element.getAsString();
            return value == null ? "" : value;
        }

        private static Set<String> stringSet(JsonElement element) {
            LinkedHashSet<String> result = new LinkedHashSet<>();
            if (element == null || !element.isJsonArray()) return result;
            for (JsonElement item : element.getAsJsonArray()) {
                String value = stringValue(item);
                if (!value.isEmpty()) result.add(value);
            }
            return result;
        }

        private static String objectString(JsonElement element) {
            return element != null && element.isJsonObject() ? element.getAsJsonObject().toString() : "{}";
        }

        private static JsonObject parseObject(String json) {
            try {
                JsonElement element = JsonParser.parseString(json);
                return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
            } catch (RuntimeException ignored) {
                return new JsonObject();
            }
        }
    }
}
