package com.fongmi.android.tv.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import okhttp3.OkHttpClient;
import okhttp3.Request;

public class WebThemeManifestHttpTest {

    private MockWebServer server;
    private OkHttpClient client;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build();
    }

    @After
    public void tearDown() throws Exception {
        if (server != null) server.close();
    }

    @Test
    public void conditionalRequestSendsEtagAndAcceptsNotModified() throws Exception {
        server.enqueue(new MockResponse.Builder()
                .code(304)
                .setHeader("ETag", "\"theme-v1\"")
                .build());
        Request request = WebThemeManifestLoader.buildRequest(
                server.url("/theme.json").toString(), "\"theme-v1\"");

        WebThemeManifestLoader.FetchResult result =
                WebThemeManifestLoader.execute(client, request);
        RecordedRequest recorded = server.takeRequest();

        assertEquals("\"theme-v1\"", recorded.getHeaders().get("If-None-Match"));
        assertEquals("application/json", recorded.getHeaders().get("Accept"));
        assertTrue(result.notModified());
        assertEquals("\"theme-v1\"", result.etag());
    }

    @Test
    public void successfulResponseReturnsBodyAndNormalizedEtag() throws Exception {
        server.enqueue(new MockResponse.Builder()
                .code(200)
                .setHeader("ETag", "  \"theme-v2\"  ")
                .body("{\"schemaVersion\":2}")
                .build());
        Request request = WebThemeManifestLoader.buildRequest(
                server.url("/theme.json").toString(), "");

        WebThemeManifestLoader.FetchResult result =
                WebThemeManifestLoader.execute(client, request);

        assertFalse(result.notModified());
        assertEquals("{\"schemaVersion\":2}", result.json());
        assertEquals("\"theme-v2\"", result.etag());
    }

    @Test
    public void unusableEtagIsNeverCopiedIntoARequestHeader() {
        Request request = WebThemeManifestLoader.buildRequest(
                "https://themes.example/theme.json", "bad\r\nheader");

        assertNull(request.header("If-None-Match"));
    }

    @Test
    public void nonAsciiEtagIsDiscardedBeforeItCanBecomeARequestHeader() {
        WebThemeManifestLoader.FetchResult fetched = WebThemeManifestLoader.FetchResult.modified(
                "{}", "\"thème\"");

        assertEquals("", fetched.etag());
        assertNull(WebThemeManifestLoader.buildRequest(
                "https://themes.example/theme.json", "\"thème\"")
                .header("If-None-Match"));
    }
}
