package com.fongmi.android.tv.update;

import java.net.URI;
import java.util.Locale;

public final class UpdateUrl {

    private UpdateUrl() {
    }

    public static String requireHttpsOrigin(String value) {
        try {
            String text = value == null ? "" : value.trim();
            while (text.endsWith("/")) text = text.substring(0, text.length() - 1);
            URI uri = URI.create(text);
            if (!"https".equalsIgnoreCase(uri.getScheme())) throw new IllegalArgumentException("HTTPS required");
            if (uri.getHost() == null || uri.getHost().isEmpty()) throw new IllegalArgumentException("Host required");
            if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) throw new IllegalArgumentException("Credentials, query and fragment are not allowed");
            String path = uri.getPath();
            if (path != null && !path.isEmpty() && !"/".equals(path)) throw new IllegalArgumentException("Origin must not contain a path");
            return text;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid HTTPS origin", e);
        }
    }

    public static String requireHttpsUrl(String value) {
        try {
            String text = value == null ? "" : value.trim();
            URI uri = URI.create(text);
            if (!"https".equalsIgnoreCase(uri.getScheme())) throw new IllegalArgumentException("HTTPS required");
            if (uri.getHost() == null || uri.getHost().isEmpty()) throw new IllegalArgumentException("Host required");
            if (uri.getUserInfo() != null) throw new IllegalArgumentException("Credentials are not allowed");
            return text;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid HTTPS URL", e);
        }
    }

    public static String host(String value) {
        try {
            String host = URI.create(value).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return "";
        }
    }
}
