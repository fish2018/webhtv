package com.fongmi.android.tv.player.diagnostic;

import com.google.common.net.InetAddresses;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;

import okhttp3.HttpUrl;
import okhttp3.Request;

final class PanNetworkSafety {

    private PanNetworkSafety() {
    }

    static boolean isAllowedPlaybackHost(String host) {
        return isLoopbackHost(host) || !isObviouslyUnsafeHost(host);
    }

    static boolean isObviouslyUnsafeHost(String host) {
        String value = normalizeHost(host);
        if (value.isEmpty() || "localhost".equals(value) || value.endsWith(".localhost")) return true;
        if (!InetAddresses.isInetAddress(value)) return false;
        return isUnsafeAddress(InetAddresses.forString(value));
    }

    static void requireSafeRemoteHost(String host) throws IOException {
        if (isObviouslyUnsafeHost(host)) throw new IOException("诊断已拒绝本地或私有网络目标");
        try {
            InetAddress[] addresses = InetAddress.getAllByName(normalizeHost(host));
            if (addresses.length == 0) throw new IOException("诊断目标没有可用地址");
            for (InetAddress address : addresses) {
                if (isUnsafeAddress(address)) throw new IOException("诊断已拒绝解析到本地或私有网络的目标");
            }
        } catch (UnknownHostException e) {
            throw new IOException("诊断目标域名无法解析", e);
        }
    }

    static Request stripSensitiveHeadersOnRedirect(Request request, String originalHost) {
        if (normalizeHost(originalHost).equals(normalizeHost(request.url().host()))) return request;
        Request.Builder builder = request.newBuilder();
        for (String name : request.headers().names()) if (isSensitiveHeader(name)) builder.removeHeader(name);
        return builder.build();
    }

    static Request stripSensitiveHeadersOnRedirect(Request request, HttpUrl originalUrl) {
        if (originalUrl == null || sameOrigin(originalUrl, request.url())) return request;
        Request.Builder builder = request.newBuilder();
        for (String name : request.headers().names()) if (isSensitiveHeader(name)) builder.removeHeader(name);
        return builder.build();
    }

    static boolean isLoopbackHost(String host) {
        String value = normalizeHost(host);
        return "localhost".equals(value) || value.endsWith(".localhost") || "127.0.0.1".equals(value) || "::1".equals(value);
    }

    private static boolean isSensitiveHeader(String name) {
        String value = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return value.equals("authorization") || value.equals("proxy-authorization") || value.equals("cookie")
                || value.equals("set-cookie") || value.equals("x-api-key") || value.equals("api-key")
                || value.contains("token");
    }

    private static boolean sameOrigin(HttpUrl left, HttpUrl right) {
        return left != null && right != null
                && normalizeHost(left.host()).equals(normalizeHost(right.host()))
                && left.port() == right.port()
                && left.scheme().equalsIgnoreCase(right.scheme());
    }

    private static boolean isUnsafeAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return true;
        byte[] bytes = address.getAddress();
        if (address instanceof Inet6Address) return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
        if (!(address instanceof Inet4Address) || bytes.length != 4) return true;
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        if (first == 100 && second >= 64 && second <= 127) return true;
        if (first == 198 && (second == 18 || second == 19)) return true;
        return first == 192 && second == 0 && (bytes[2] & 0xff) == 0;
    }

    private static String normalizeHost(String host) {
        String value = host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("[") && value.endsWith("]") && value.length() > 1) value = value.substring(1, value.length() - 1);
        while (value.endsWith(".")) value = value.substring(0, value.length() - 1);
        return value;
    }
}
