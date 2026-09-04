package com.fongmi.android.tv.server.process;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import org.junit.Test;

import okhttp3.HttpUrl;

public class ApkUrlPolicyTest {

    @Test
    public void acceptsPublicHttpsUrlAndHttpsRedirect() {
        HttpUrl source = ApkUrlPolicy.parse("https://gh.acmsz.top/https://github.com/app.apk");
        assertEquals("https", source.scheme());
        assertEquals("cdn.example.com", ApkUrlPolicy.redirect(source, "https://cdn.example.com/app.apk").host());
    }

    @Test
    public void rejectsUnsafeSchemesCredentialsAndLiteralAddresses() {
        assertThrows(IllegalArgumentException.class, () -> ApkUrlPolicy.parse("http://example.com/app.apk"));
        assertThrows(IllegalArgumentException.class, () -> ApkUrlPolicy.parse("file:///tmp/app.apk"));
        assertThrows(IllegalArgumentException.class, () -> ApkUrlPolicy.parse("https://user:pass@example.com/app.apk"));
        assertThrows(IllegalArgumentException.class, () -> ApkUrlPolicy.parse("https://127.0.0.1/app.apk"));
        assertThrows(IllegalArgumentException.class, () -> ApkUrlPolicy.parse("https://10.0.0.1/app.apk"));
        assertThrows(IllegalArgumentException.class, () -> ApkUrlPolicy.parse("https://[::1]/app.apk"));
        assertThrows(IllegalArgumentException.class, () -> ApkUrlPolicy.parse("https://[fc00::1]/app.apk"));
    }

    @Test
    public void rejectsRedirectDowngradeAndPrivateDnsAnswers() throws Exception {
        HttpUrl source = ApkUrlPolicy.parse("https://example.com/app.apk");
        assertThrows(IllegalArgumentException.class, () -> ApkUrlPolicy.redirect(source, null));
        assertThrows(IllegalArgumentException.class, () -> ApkUrlPolicy.redirect(source, "http://cdn.example.com/app.apk"));
        assertThrows(UnknownHostException.class, () -> ApkUrlPolicy.requirePublicAddresses("example.com", List.of(InetAddress.getByName("192.168.1.2"))));
        assertThrows(UnknownHostException.class, () -> ApkUrlPolicy.requirePublicAddresses("example.com", List.of(InetAddress.getByName("8.8.8.8"), InetAddress.getByName("127.0.0.1"))));
    }

    @Test
    public void recognizesPublicAndSpecialUseAddresses() throws Exception {
        assertTrue(ApkUrlPolicy.isPublicAddress(InetAddress.getByName("8.8.8.8")));
        assertTrue(ApkUrlPolicy.isPublicAddress(InetAddress.getByName("2606:4700:4700::1111")));
        assertFalse(ApkUrlPolicy.isPublicAddress(InetAddress.getByName("100.64.0.1")));
        assertFalse(ApkUrlPolicy.isPublicAddress(InetAddress.getByName("198.18.0.1")));
        assertFalse(ApkUrlPolicy.isPublicAddress(InetAddress.getByName("2001:db8::1")));
    }
}
