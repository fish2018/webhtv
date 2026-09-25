package com.fongmi.android.tv.ui.helper;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.service.PersonalRecommendationService;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class TmdbRecommendationRows {

    private TmdbRecommendationRows() {
    }

    public static List<TmdbItem> rankedRelated(@Nullable JsonObject detail, List<TmdbItem> recommendations, List<TmdbItem> similar) {
        return PersonalRecommendationService.rankTmdbItemsForContext(detail, recommendations, similar, Integer.MAX_VALUE);
    }

    public static List<TmdbItem> personalTmdb(List<TmdbItem> personalTmdb, List<TmdbItem> related) {
        return personalRecommendations(personalTmdb);
    }

    public static List<TmdbItem> personalDouban(List<TmdbItem> personalDouban, List<TmdbItem> related, List<TmdbItem> personalTmdb) {
        return personalRecommendations(personalDouban);
    }

    public static List<TmdbItem> personalAi(List<TmdbItem> personalAi, List<TmdbItem> related, List<TmdbItem> personalTmdb, List<TmdbItem> personalDouban) {
        return personalRecommendations(personalAi);
    }

    public static boolean sameDisplayList(List<TmdbItem> first, List<TmdbItem> second) {
        int firstSize = first == null ? 0 : first.size();
        int secondSize = second == null ? 0 : second.size();
        if (firstSize != secondSize) return false;
        for (int i = 0; i < firstSize; i++) {
            if (!sameDisplayItem(first.get(i), second.get(i))) return false;
        }
        return true;
    }

    private static List<TmdbItem> personalRecommendations(List<TmdbItem> recommendations) {
        List<TmdbItem> items = new ArrayList<>();
        for (TmdbItem item : safeList(recommendations)) {
            if (containsRecommendation(items, item)) continue;
            items.add(item);
        }
        return items;
    }

    private static List<TmdbItem> safeList(List<TmdbItem> items) {
        return items == null ? new ArrayList<>() : items;
    }

    private static boolean containsRecommendation(List<TmdbItem> items, TmdbItem target) {
        if (items == null || target == null) return false;
        for (TmdbItem item : items) if (sameIdentity(item, target)) return true;
        return false;
    }

    public static boolean sameIdentity(@Nullable TmdbItem first, @Nullable TmdbItem second) {
        if (first == second) return first != null;
        if (first == null || second == null) return false;
        if (first.getTmdbId() > 0 && second.getTmdbId() > 0) {
            return first.getTmdbId() == second.getTmdbId() && compatibleMediaType(first, second);
        }
        String firstTitle = normalizeTitle(first.getTitle());
        String secondTitle = normalizeTitle(second.getTitle());
        return !firstTitle.isEmpty() && firstTitle.equals(secondTitle) && compatibleMediaType(first, second);
    }

    private static boolean compatibleMediaType(TmdbItem first, TmdbItem second) {
        String firstType = normalizeMediaType(first.getMediaType());
        String secondType = normalizeMediaType(second.getMediaType());
        return firstType.isEmpty() || secondType.isEmpty() || firstType.equals(secondType);
    }

    private static boolean sameDisplayItem(TmdbItem first, TmdbItem second) {
        if (first == second) return true;
        if (first == null || second == null) return false;
        return first.getTmdbId() == second.getTmdbId()
                && Objects.equals(first.getMediaType(), second.getMediaType())
                && Objects.equals(first.getTitle(), second.getTitle())
                && Objects.equals(first.getSubtitle(), second.getSubtitle())
                && Objects.equals(first.getOverview(), second.getOverview())
                && Objects.equals(first.getRecommendationReason(), second.getRecommendationReason())
                && Objects.equals(first.getPosterUrl(), second.getPosterUrl())
                && Objects.equals(first.getBackdropUrl(), second.getBackdropUrl())
                && Objects.equals(first.getCredit(), second.getCredit())
                && Math.abs(first.getRating() - second.getRating()) < 0.001
                && Objects.equals(first.getOriginalLanguage(), second.getOriginalLanguage())
                && Objects.equals(first.getOriginCountry(), second.getOriginCountry())
                && Objects.equals(first.getGenreIds(), second.getGenreIds())
                && Objects.equals(first.getDepartment(), second.getDepartment())
                && Math.abs(first.getTmdbRating() - second.getTmdbRating()) < 0.001
                && Math.abs(first.getDoubanRating() - second.getDoubanRating()) < 0.001;
    }

    private static String normalizeTitle(String text) {
        return text == null || text.isEmpty() ? "" : text.replaceAll("[\\s·•・._\\-/\\\\|()（）\\[\\]【】《》<>:：,，.。]+", "").trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeMediaType(String text) {
        return Objects.toString(text, "").trim().toLowerCase(Locale.ROOT);
    }
}
