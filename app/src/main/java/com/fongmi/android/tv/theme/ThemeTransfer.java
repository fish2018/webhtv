package com.fongmi.android.tv.theme;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Authenticator;
import okhttp3.CookieJar;
import okhttp3.Dns;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Boundary helpers shared by import/export UI and tests. */
public final class ThemeTransfer {

    public static final int MAX_BYTES = ThemeProfileValidator.MAX_JSON_BYTES;
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .cookieJar(CookieJar.NO_COOKIES)
            .proxy(Proxy.NO_PROXY)
            .authenticator(Authenticator.NONE)
            .proxyAuthenticator(Authenticator.NONE)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .build();

    private ThemeTransfer() {
    }

    public static boolean isHttps(String value) {
        return ThemeProfileValidator.isSafeHttps(value);
    }

    public static String host(String value) {
        try {
            URI uri = new URI(value);
            return uri.getHost() == null ? "" : uri.getHost();
        } catch (URISyntaxException e) {
            return "";
        }
    }

    /** Reads a user-selected or downloaded JSON stream without trusting Content-Length. */
    public static String read(InputStream input) throws IOException {
        if (input == null) throw new IOException("theme source is unavailable");
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(MAX_BYTES, 16 * 1024));
        byte[] buffer = new byte[8192];
        int count;
        int total = 0;
        while ((count = input.read(buffer)) != -1) {
            if (count > MAX_BYTES - total) throw new IOException("theme JSON is too large");
            output.write(buffer, 0, count);
            total += count;
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    /** Fetches exactly one public HTTPS response; redirects and private destinations are rejected. */
    public static String fetch(String value) throws IOException {
        if (!isHttps(value)) throw new IOException("theme URL must be HTTPS");
        String requestedHost = host(value);
        List<InetAddress> addresses = lookupPublic(requestedHost);
        OkHttpClient client = CLIENT.newBuilder().dns(host -> {
            if (requestedHost.equalsIgnoreCase(host)) return addresses;
            return Dns.SYSTEM.lookup(host);
        }).build();
        Request request = new Request.Builder().url(value).get().build();
        try (Response response = client.newCall(request).execute()) {
            if (response.code() >= 300 && response.code() < 400) {
                throw new IOException("theme URL redirects are not allowed");
            }
            if (!response.isSuccessful()) throw new IOException("theme URL returned HTTP " + response.code());
            ResponseBody body = response.body();
            if (body == null) throw new IOException("theme URL returned an empty response");
            if (body.contentLength() > MAX_BYTES) throw new IOException("theme JSON is too large");
            return read(body.byteStream());
        }
    }

    static boolean isPublicAddress(InetAddress address) {
        if (address == null || address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) return false;
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) return isPublicIpv4(bytes, 0);
        if (bytes.length != 16) return false;
        // RFC 4193 ULA is not covered by InetAddress.isSiteLocalAddress().
        if ((bytes[0] & 0xFE) == 0xFC) return false;
        // RFC 3849 documentation space is not a routable public destination.
        if ((bytes[0] & 0xFF) == 0x20 && (bytes[1] & 0xFF) == 0x01
                && (bytes[2] & 0xFF) == 0x0D && (bytes[3] & 0xFF) == 0xB8) return false;
        // Prevent IPv4-mapped IPv6 answers from bypassing the IPv4 policy.
        boolean mapped = true;
        for (int i = 0; i < 10; i++) mapped &= bytes[i] == 0;
        mapped &= (bytes[10] & 0xFF) == 0xFF && (bytes[11] & 0xFF) == 0xFF;
        return !mapped || isPublicIpv4(bytes, 12);
    }

    private static boolean isPublicIpv4(byte[] bytes, int offset) {
        int first = bytes[offset] & 0xFF;
        int second = bytes[offset + 1] & 0xFF;
        int third = bytes[offset + 2] & 0xFF;
        if (first == 0 || first == 10 || first == 127 || first >= 224) return false;
        if (first == 100 && second >= 64 && second <= 127) return false; // RFC 6598 CGNAT
        if (first == 169 && second == 254) return false;
        if (first == 172 && second >= 16 && second <= 31) return false;
        if (first == 192 && (second == 168 || (second == 0 && third <= 2))) return false;
        if (first == 198 && (second == 18 || second == 19
                || (second == 51 && third == 100))) return false;
        return !(first == 203 && second == 0 && third == 113);
    }

    private static List<InetAddress> lookupPublic(String hostname) throws IOException {
        if (hostname == null || hostname.isBlank()) throw new IOException("theme URL host is missing");
        List<InetAddress> addresses;
        try {
            addresses = Dns.SYSTEM.lookup(hostname);
        } catch (RuntimeException e) {
            throw new IOException("theme URL host cannot be resolved", e);
        }
        if (addresses.isEmpty()) throw new IOException("theme URL host cannot be resolved");
        for (InetAddress address : addresses) {
            if (!isPublicAddress(address)) throw new IOException("private or special theme host is not allowed");
        }
        return addresses;
    }
}
