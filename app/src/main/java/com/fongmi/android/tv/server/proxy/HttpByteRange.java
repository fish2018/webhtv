package com.fongmi.android.tv.server.proxy;

public record HttpByteRange(long startInclusive, long endInclusive, long totalLength) {

    public HttpByteRange {
        if (totalLength <= 0) throw new IllegalArgumentException("totalLength must be positive");
        if (startInclusive < 0 || endInclusive < startInclusive || endInclusive >= totalLength) {
            throw new IllegalArgumentException("range must be inside the selected representation");
        }
    }

    public long length() {
        return endInclusive - startInclusive + 1;
    }

    public String requestHeader() {
        return "bytes=" + startInclusive + "-" + endInclusive;
    }

    public String contentRange() {
        return "bytes " + startInclusive + "-" + endInclusive + "/" + totalLength;
    }

    // RFC 9110 section 14.1.2 defines inclusive byte positions, open-ended ranges,
    // and suffix ranges after the selected representation length is known.
    // Source: https://www.rfc-editor.org/rfc/rfc9110.html#section-14.1.2
    public static ParseResult parse(String header, long totalLength) {
        if (totalLength < 0) throw new IllegalArgumentException("totalLength must not be negative");
        if (header == null || header.trim().isEmpty()) return ParseResult.failure(ParseStatus.ABSENT);

        String value = header.trim();
        int separator = value.indexOf('=');
        if (separator < 0) return ParseResult.failure(ParseStatus.MALFORMED);
        String unit = value.substring(0, separator).trim();
        if (!"bytes".equalsIgnoreCase(unit)) return ParseResult.failure(ParseStatus.UNSUPPORTED_UNIT);

        String specification = value.substring(separator + 1).trim();
        if (specification.indexOf(',') >= 0) return ParseResult.failure(ParseStatus.MULTIPLE_RANGES);
        int dash = specification.indexOf('-');
        if (dash < 0 || dash != specification.lastIndexOf('-')) {
            return ParseResult.failure(ParseStatus.MALFORMED);
        }

        String firstText = specification.substring(0, dash);
        String lastText = specification.substring(dash + 1);
        if (firstText.isEmpty() && lastText.isEmpty()) {
            return ParseResult.failure(ParseStatus.MALFORMED);
        }

        if (firstText.isEmpty()) return parseSuffix(lastText, totalLength);
        NumberResult first = parseNumber(firstText);
        if (!first.isValid()) return ParseResult.failure(first.failure());
        if (lastText.isEmpty()) return normalize(first.value(), null, totalLength);

        NumberResult last = parseNumber(lastText);
        if (!last.isValid()) return ParseResult.failure(last.failure());
        return normalize(first.value(), last.value(), totalLength);
    }

    private static ParseResult parseSuffix(String text, long totalLength) {
        NumberResult suffix = parseNumber(text);
        if (!suffix.isValid()) return ParseResult.failure(suffix.failure());
        if (suffix.value() == 0 || totalLength == 0) {
            return ParseResult.failure(ParseStatus.UNSATISFIABLE);
        }
        long start = suffix.value() >= totalLength ? 0 : totalLength - suffix.value();
        return ParseResult.valid(new HttpByteRange(start, totalLength - 1, totalLength));
    }

    private static ParseResult normalize(long first, Long requestedLast, long totalLength) {
        if (requestedLast != null && requestedLast < first) {
            return ParseResult.failure(ParseStatus.MALFORMED);
        }
        if (totalLength == 0 || first >= totalLength) {
            return ParseResult.failure(ParseStatus.UNSATISFIABLE);
        }
        long last = requestedLast == null
                ? totalLength - 1
                : Math.min(requestedLast, totalLength - 1);
        return ParseResult.valid(new HttpByteRange(first, last, totalLength));
    }

    private static NumberResult parseNumber(String text) {
        if (text.isEmpty()) return NumberResult.failure(ParseStatus.MALFORMED);
        long value = 0;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character < '0' || character > '9') {
                return NumberResult.failure(ParseStatus.MALFORMED);
            }
            int digit = character - '0';
            if (value > (Long.MAX_VALUE - digit) / 10) {
                return NumberResult.failure(ParseStatus.NUMBER_OVERFLOW);
            }
            value = value * 10 + digit;
        }
        return NumberResult.valid(value);
    }

    public enum ParseStatus {
        ABSENT,
        VALID,
        UNSUPPORTED_UNIT,
        MULTIPLE_RANGES,
        MALFORMED,
        NUMBER_OVERFLOW,
        UNSATISFIABLE
    }

    public record ParseResult(ParseStatus status, HttpByteRange range) {

        public boolean isValid() {
            return status == ParseStatus.VALID;
        }

        private static ParseResult valid(HttpByteRange range) {
            return new ParseResult(ParseStatus.VALID, range);
        }

        private static ParseResult failure(ParseStatus status) {
            return new ParseResult(status, null);
        }
    }

    private record NumberResult(long value, ParseStatus failure) {

        private boolean isValid() {
            return failure == null;
        }

        private static NumberResult valid(long value) {
            return new NumberResult(value, null);
        }

        private static NumberResult failure(ParseStatus failure) {
            return new NumberResult(0, failure);
        }
    }
}
