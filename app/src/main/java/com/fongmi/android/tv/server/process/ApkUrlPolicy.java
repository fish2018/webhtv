package com.fongmi.android.tv.server.process;

import com.google.common.net.InetAddresses;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import okhttp3.Dns;
import okhttp3.HttpUrl;

final class ApkUrlPolicy {

    static final int MAX_REDIRECTS = 5;
    static final long MAX_BYTES = 512L * 1024 * 1024;
    static final long STORAGE_RESERVE_BYTES = 16L * 1024 * 1024;

    private ApkUrlPolicy() {
    }

    static HttpUrl parse(String value) {
        HttpUrl url = HttpUrl.parse(value == null ? "" : value.trim());
        if (url == null || !"https".equals(url.scheme())) throw new IllegalArgumentException("Only HTTPS APK links are allowed");
        if (!url.username().isEmpty() || !url.password().isEmpty()) throw new IllegalArgumentException("URL credentials are not allowed");
        if (InetAddresses.isInetAddress(url.host()) && !isPublicAddress(InetAddresses.forString(url.host()))) throw new IllegalArgumentException("Private or special-use destinations are not allowed");
        return url;
    }

    static HttpUrl redirect(HttpUrl current, String location) {
        if (location == null || location.isBlank()) throw new IllegalArgumentException("Redirect is missing a destination");
        HttpUrl next = current.resolve(location);
        if (next == null) throw new IllegalArgumentException("Invalid redirect URL");
        return parse(next.toString());
    }

    static boolean isRedirect(int code) {
        return code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
    }

    static Dns publicDns() {
        return hostname -> requirePublicAddresses(hostname, Dns.SYSTEM.lookup(hostname));
    }

    static List<InetAddress> requirePublicAddresses(String hostname, List<InetAddress> addresses) throws UnknownHostException {
        if (addresses == null || addresses.isEmpty()) throw new UnknownHostException(hostname);
        for (InetAddress address : addresses) {
            if (!isPublicAddress(address)) throw new UnknownHostException("Blocked private or special-use address for " + hostname);
        }
        return addresses;
    }

    static boolean isPublicAddress(InetAddress address) {
        if (address == null || address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) return isPublicIpv4(bytes);
        if (!(address instanceof Inet6Address) || bytes.length != 16) return false;
        int first = unsigned(bytes[0]);
        int second = unsigned(bytes[1]);
        if ((first & 0xFE) == 0xFC) return false;
        if (first == 0x20 && second == 0x01 && unsigned(bytes[2]) == 0x0D && unsigned(bytes[3]) == 0xB8) return false;
        if (first == 0x20 && second == 0x01 && unsigned(bytes[2]) == 0 && unsigned(bytes[3]) == 0) return false;
        if (first == 0x20 && second == 0x02) return false;
        if (isIpv4Mapped(bytes) || isIpv4Compatible(bytes) || isNat64(bytes)) return isPublicIpv4(lastFour(bytes));
        return true;
    }

    private static boolean isPublicIpv4(byte[] bytes) {
        if (bytes.length != 4) return false;
        int a = unsigned(bytes[0]);
        int b = unsigned(bytes[1]);
        int c = unsigned(bytes[2]);
        if (a == 0 || a == 10 || a == 127 || a >= 224) return false;
        if (a == 100 && b >= 64 && b <= 127) return false;
        if (a == 169 && b == 254) return false;
        if (a == 172 && b >= 16 && b <= 31) return false;
        if (a == 192 && b == 0 && c == 0) return false;
        if (a == 192 && b == 0 && c == 2) return false;
        if (a == 192 && b == 88 && c == 99) return false;
        if (a == 192 && b == 168) return false;
        if (a == 198 && (b == 18 || b == 19)) return false;
        if (a == 198 && b == 51 && c == 100) return false;
        return !(a == 203 && b == 0 && c == 113);
    }

    private static boolean isIpv4Mapped(byte[] bytes) {
        for (int i = 0; i < 10; i++) if (bytes[i] != 0) return false;
        return unsigned(bytes[10]) == 0xFF && unsigned(bytes[11]) == 0xFF;
    }

    private static boolean isIpv4Compatible(byte[] bytes) {
        for (int i = 0; i < 12; i++) if (bytes[i] != 0) return false;
        return true;
    }

    private static boolean isNat64(byte[] bytes) {
        return unsigned(bytes[0]) == 0 && unsigned(bytes[1]) == 0x64 && unsigned(bytes[2]) == 0xFF && unsigned(bytes[3]) == 0x9B
                && bytes[4] == 0 && bytes[5] == 0 && bytes[6] == 0 && bytes[7] == 0 && bytes[8] == 0 && bytes[9] == 0 && bytes[10] == 0 && bytes[11] == 0;
    }

    private static byte[] lastFour(byte[] bytes) {
        return new byte[]{bytes[12], bytes[13], bytes[14], bytes[15]};
    }

    private static int unsigned(byte value) {
        return value & 0xFF;
    }
}
