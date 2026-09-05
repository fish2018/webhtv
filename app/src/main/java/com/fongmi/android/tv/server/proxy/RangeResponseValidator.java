package com.fongmi.android.tv.server.proxy;

import java.util.Objects;

public final class RangeResponseValidator {

    private RangeResponseValidator() {
    }

    public static Result validate(
            HttpByteRange requested,
            int responseStatus,
            String contentRangeHeader,
            long bodyLength,
            String contentEncoding) {
        return validate(
                requested,
                responseStatus,
                contentRangeHeader,
                bodyLength,
                contentEncoding,
                null,
                null,
                null);
    }

    public static Result validate(
            HttpByteRange requested,
            int responseStatus,
            String contentRangeHeader,
            long bodyLength,
            String contentEncoding,
            UpstreamResourceValidator expectedValidator,
            String responseEtag,
            String responseLastModified) {
        Objects.requireNonNull(requested, "requested");

        if (responseStatus == 412) {
            return Result.of(Status.PRECONDITION_FAILED, null);
        }
        if (responseStatus == 200) {
            return Result.of(Status.FALLBACK_SINGLE_CONNECTION, null);
        }
        if (responseStatus == 416) {
            HttpContentRange.ParseResult metadata = HttpContentRange.parse(contentRangeHeader);
            Status status = metadata.status() == HttpContentRange.ParseStatus.UNSATISFIED
                    ? Status.UNSATISFIABLE
                    : Status.INVALID_CONTENT_RANGE;
            return Result.of(status, null);
        }
        if (responseStatus != 206) {
            return Result.of(Status.UNEXPECTED_STATUS, null);
        }
        if (!isIdentity(contentEncoding)) {
            return Result.of(Status.ENCODED_RESPONSE, null);
        }

        HttpContentRange.ParseResult metadata = HttpContentRange.parse(contentRangeHeader);
        if (metadata.status() == HttpContentRange.ParseStatus.MISSING) {
            return Result.of(Status.MISSING_CONTENT_RANGE, null);
        }
        if (!metadata.isValid()) {
            return Result.of(Status.INVALID_CONTENT_RANGE, null);
        }

        HttpContentRange contentRange = metadata.range();
        if (contentRange.startInclusive() != requested.startInclusive()
                || contentRange.endInclusive() != requested.endInclusive()
                || contentRange.totalLength() != requested.totalLength()) {
            return Result.of(Status.CONTENT_RANGE_MISMATCH, contentRange);
        }
        if (bodyLength >= 0 && bodyLength != contentRange.length()) {
            return Result.of(Status.BODY_LENGTH_MISMATCH, contentRange);
        }
        if (expectedValidator != null
                && expectedValidator.isUsable()
                && !expectedValidator.matches(responseEtag, responseLastModified)) {
            return Result.of(Status.RESOURCE_CHANGED, contentRange);
        }
        return Result.of(Status.ACCEPTED, contentRange);
    }

    private static boolean isIdentity(String contentEncoding) {
        return contentEncoding == null
                || contentEncoding.trim().isEmpty()
                || "identity".equalsIgnoreCase(contentEncoding.trim());
    }

    public enum Status {
        ACCEPTED,
        FALLBACK_SINGLE_CONNECTION,
        UNSATISFIABLE,
        PRECONDITION_FAILED,
        MISSING_CONTENT_RANGE,
        INVALID_CONTENT_RANGE,
        CONTENT_RANGE_MISMATCH,
        BODY_LENGTH_MISMATCH,
        RESOURCE_CHANGED,
        ENCODED_RESPONSE,
        UNEXPECTED_STATUS
    }

    public record Result(Status status, HttpContentRange contentRange) {

        public Result {
            Objects.requireNonNull(status, "status");
        }

        public boolean isAccepted() {
            return status == Status.ACCEPTED;
        }

        private static Result of(Status status, HttpContentRange contentRange) {
            return new Result(status, contentRange);
        }
    }
}