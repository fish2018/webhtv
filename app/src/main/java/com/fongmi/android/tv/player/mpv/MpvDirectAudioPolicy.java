package com.fongmi.android.tv.player.mpv;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Set;

public final class MpvDirectAudioPolicy {

    private MpvDirectAudioPolicy() {
    }

    public static Selection select(List<Candidate> candidates, String currentId) {
        return select(candidates, currentId, "");
    }

    public static Selection select(List<Candidate> candidates, String currentId,
                                   String passthroughCodecs) {
        Candidate current = findById(candidates, currentId);
        if (current == null) return new Selection(currentId, false, "current-track-unavailable");
        if (supportsPassthrough(current, passthroughCodecs)) {
            return new Selection(current.id(), false, "current-track-passthrough");
        }
        if (isStereo(current)) return new Selection(current.id(), false, "current-track-stereo");

        Candidate stereo = bestCandidate(candidates, current, true);
        if (stereo != null) return new Selection(stereo.id(), true, "same-language-stereo");
        if (isEfficient(current)) return new Selection(current.id(), false, "current-track-efficient");

        Candidate efficient = bestCandidate(candidates, current, false);
        if (efficient != null) return new Selection(efficient.id(), true, "same-language-efficient");
        return new Selection(current.id(), false, "no-compatible-alternative");
    }

    private static boolean supportsPassthrough(Candidate candidate, String passthroughCodecs) {
        Set<String> supported = new HashSet<>();
        if (passthroughCodecs != null) {
            for (String codec : passthroughCodecs.split(",")) {
                if (codec != null && !codec.isBlank()) supported.add(codec.trim().toLowerCase(Locale.US));
            }
        }
        if (supported.isEmpty()) return false;
        String text = normalizedDescription(candidate);
        if (text.contains("truehd") || text.contains("true hd") || text.contains("mlp")) return supported.contains("truehd");
        if (text.contains("dts-hd") || text.contains("dts_hd") || text.contains("dtshd") || text.contains("dts hd")) {
            return supported.contains("dts-hd") || supported.contains("dts");
        }
        if (text.contains("dts")) return supported.contains("dts");
        if (text.contains("eac3") || text.contains("e-ac-3") || text.contains("e-ac3")) return supported.contains("eac3");
        if (text.contains("ac3") || text.contains("ac-3")) return supported.contains("ac3");
        return false;
    }

    private static Candidate findById(List<Candidate> candidates, String id) {
        if (candidates == null || id == null) return null;
        for (Candidate candidate : candidates) {
            if (candidate != null && id.equals(candidate.id())) return candidate;
        }
        return null;
    }

    private static Candidate bestCandidate(List<Candidate> candidates, Candidate current,
                                           boolean stereoOnly) {
        Candidate best = null;
        int bestCodecRank = Integer.MIN_VALUE;
        for (Candidate candidate : candidates) {
            if (candidate == null || candidate.id().equals(current.id())) continue;
            if (!sameLanguage(current.language(), candidate.language())) continue;
            if (stereoOnly ? !isStereo(candidate) : !isEfficient(candidate)) continue;
            int codecRank = codecRank(candidate);
            if (best == null || codecRank > bestCodecRank) {
                best = candidate;
                bestCodecRank = codecRank;
            }
        }
        return best;
    }

    private static boolean sameLanguage(String first, String second) {
        String firstCode = primaryLanguage(first);
        String secondCode = primaryLanguage(second);
        if (firstCode.isEmpty() || secondCode.isEmpty()) return firstCode.equals(secondCode);
        if (firstCode.equals(secondCode)) return true;
        return iso3(firstCode).equals(iso3(secondCode));
    }

    private static String primaryLanguage(String value) {
        if (value == null) return "";
        String normalized = value.trim().toLowerCase(Locale.US).replace('_', '-');
        int separator = normalized.indexOf('-');
        return separator < 0 ? normalized : normalized.substring(0, separator);
    }

    private static String iso3(String language) {
        if (language.length() != 2) return language;
        try {
            return new Locale(language).getISO3Language();
        } catch (MissingResourceException ignored) {
            return language;
        }
    }

    private static boolean isStereo(Candidate candidate) {
        if (candidate.channels() > 0 && candidate.channels() <= 2) return true;
        String text = normalizedDescription(candidate);
        return text.contains("stereo") || text.contains("2.0") || text.contains("2ch")
                || text.contains("2 ch") || text.contains("\u7acb\u4f53\u58f0")
                || text.contains("\u53cc\u58f0\u9053");
    }

    private static boolean isEfficient(Candidate candidate) {
        return !isHighComplexity(candidate) && codecRank(candidate) > 0;
    }

    private static boolean isHighComplexity(Candidate candidate) {
        String text = normalizedDescription(candidate);
        return text.contains("truehd") || text.contains("true hd") || text.contains("mlp")
                || text.contains("dts-hd") || text.contains("dts_hd")
                || text.contains("dtshd") || text.contains("dts hd")
                || text.contains("dts:x") || text.contains("dtsx");
    }

    private static int codecRank(Candidate candidate) {
        String text = normalizedDescription(candidate);
        if (text.contains("aac")) return 60;
        if (text.contains("eac3") || text.contains("e-ac-3") || text.contains("e-ac3")) return 40;
        if (text.contains("ac3") || text.contains("ac-3")) return 50;
        if (text.contains("opus")) return 30;
        if (text.contains("vorbis")) return 20;
        if (text.contains("mp3") || text.contains("mpeg audio")) return 10;
        return 0;
    }

    private static String normalizedDescription(Candidate candidate) {
        return (candidate.codec() + " " + candidate.title()).toLowerCase(Locale.US);
    }

    public record Candidate(String id, String language, String codec,
                            String title, int channels) {
        public Candidate {
            id = id == null ? "" : id;
            language = language == null ? "" : language;
            codec = codec == null ? "" : codec;
            title = title == null ? "" : title;
        }
    }

    public record Selection(String id, boolean changed, String reason) {
    }
}
