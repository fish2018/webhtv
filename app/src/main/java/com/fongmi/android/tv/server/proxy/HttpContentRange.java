package com.fongmi.android.tv.server.proxy;

public record HttpContentRange(long startInclusive, long endInclusive, long totalLength) {

    public HttpContentRange {
        if (totalLength <= 0) throw new IllegalArgumentException("totalLength must be positive");
        if (startInclusive < 0 || endInclusive < startInclusive || endInclusive >= totalLength) {
            throw new IllegalArgumentException("invalid Content-Range interval");
        }
    }

    public long length() {
        return endInclusive - startInclusive + 1;
    }

    public String value() {
        return "bytes " + startInclusive + "-" + endInclusive + "/" + totalLength;
    }

    public static ParseResult parse(String header) {
        if (header == null || header.trim().isEmpty()) return ParseResult.failure(ParseStatus.MISSING);
        String value = header.trim();
        int separator = value.indexOf(' ');
        if (separator < 0) return ParseResult.failure(ParseStatus.MALFORMED);
        if (!"bytes".equalsIgnoreCase(value.substring(0, separator).trim())) {
            return ParseResult.failure(ParseStatus.UNSUPPORTED_UNIT);
        }

        String specification = value.substring(separator + 1).trim();
        int slash = specification.indexOf('/');
        if (slash < 0 || slash != specification.lastIndexOf('/')) {
            return ParseResult.failure(ParseStatus.MALFORMED);
        }
        String interval = specification.substring(0, slash).trim();
        String totalText = specification.substring(slash + 1).trim();
        if ("*".equals(interval)) {
            NumberResult total = parseNumber(totalText);
            if (!total.isValid()) return ParseResult.failure(total.failure());
            return ParseResult.failure(ParseStatus.UNSATISFIED, total.value());
        }
        if ("*".equals(totalText)) return ParseResult.failure(ParseStatus.UNKNOWN_TOTAL);

        NumberResult total = parseNumber(totalText);
        if (!total.isValid()) return ParseResult.failure(total.failure());
        if (total.value() <= 0) return ParseResult.failure(ParseStatus.MALFORMED);

        int dash = interval.indexOf('-');
        if (dash < 0 || dash != interval.lastIndexOf('-')) {
            return ParseResult.failure(ParseStatus.MALFORMED);
        }
        NumberResult start = parseNumber(interval.substring(0, dash).trim());
        NumberResult end = parseNumber(interval.substring(dash + 1).trim());
        if (!start.isValid()) return ParseResult.failure(start.failure());
        if (!end.isValid()) return ParseResult.failure(end.failure());
        if (end.value() < start.value() || end.value() >= total.value()) {
            return ParseResult.failure(ParseStatus.MALFORMED);
        }
        return ParseResult.valid(new HttpContentRange(start.value(), end.value(), total.value()));
    }

    public enum ParseStatus {
        VALID,
        MISSING,
        UNSATISFIED,
        UNSUPPORTED_UNIT,
        UNKNOWN_TOTAL,
        MALFORMED,
        NUMBER_OVERFLOW
    }

    public record ParseResult(ParseStatus status, HttpContentRange range, long totalLength) {

        public boolean isValid() {
            return status == ParseStatus.VALID;
        }

        private static ParseResult valid(HttpContentRange range) {
            return new ParseResult(ParseStatus.VALID, range, range.totalLength());
        }

        private static ParseResult failure(ParseStatus status) {
            return failure(status, -1);
        }

        private static ParseResult failure(ParseStatus status, long totalLength) {
            return new ParseResult(status, null, totalLength);
        }
    }

    private record NumberResult(long value, ParseStatus failure) {

        private boolean isValid() {
            return failure == null;
        }

        private static NumberResult failure(ParseStatus failure) {
            return new NumberResult(0, failure);
        }
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
        return new NumberResult(value, null);
    }
}
