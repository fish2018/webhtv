package com.fongmi.android.tv.ad.audio;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public final class SpeechAdKeywordSet {

    static final int MAX_KEYWORDS = 128;
    static final int MAX_KEYWORD_LENGTH = 64;
    static final int MAX_INPUT_LENGTH = 8_192;

    private final List<String> values;
    private final List<Pattern> asciiPatterns;

    private SpeechAdKeywordSet(List<String> values) {
        this.values = List.copyOf(values);
        this.asciiPatterns = values.stream().map(SpeechAdKeywordSet::asciiPattern).toList();
    }

    public static SpeechAdKeywordSet parse(String input) {
        String source = input == null ? "" : input;
        if (source.length() > MAX_INPUT_LENGTH) {
            throw new IllegalArgumentException("keyword input too long");
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String token : source.split("[,，;；\\r\\n]+")) {
            String value = normalize(token);
            if (value.isEmpty() || value.codePoints().noneMatch(Character::isLetterOrDigit)) {
                continue;
            }
            if (value.length() > MAX_KEYWORD_LENGTH) {
                throw new IllegalArgumentException("keyword too long");
            }
            unique.add(value);
            if (unique.size() > MAX_KEYWORDS) {
                throw new IllegalArgumentException("too many keywords");
            }
        }
        return new SpeechAdKeywordSet(new ArrayList<>(unique));
    }

    public List<String> values() {
        return values;
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public Optional<String> firstMatch(String recognizedText) {
        String text = normalize(recognizedText);
        for (int i = 0; i < values.size(); i++) {
            Pattern ascii = asciiPatterns.get(i);
            if (ascii != null ? ascii.matcher(text).find() : text.contains(values.get(i))) {
                return Optional.of(values.get(i));
            }
        }
        return Optional.empty();
    }

    static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static Pattern asciiPattern(String value) {
        boolean ascii = value.codePoints().allMatch(cp -> cp < 128 && Character.isLetterOrDigit(cp));
        return ascii
                ? Pattern.compile("(?<![a-z0-9])" + Pattern.quote(value) + "(?![a-z0-9])")
                : null;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof SpeechAdKeywordSet)) return false;
        SpeechAdKeywordSet that = (SpeechAdKeywordSet) object;
        return values.equals(that.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values);
    }
}