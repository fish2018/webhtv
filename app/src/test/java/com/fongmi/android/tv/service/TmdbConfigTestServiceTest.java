package com.fongmi.android.tv.service;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Base64;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TmdbConfigTestServiceTest {

    private MockWebServer server;
    private OkHttpClient client;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        client = new OkHttpClient();
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    public void acceptsValidTmdbDataAndPngImage() throws Exception {
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody("{\"images\":{}}"));
        server.enqueue(new MockResponse().setHeader("Content-Type", "image/png")
                .setBody(new okio.Buffer().write(Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAAB"))));
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setBody("{\"Response\":\"True\",\"imdbID\":\"tt0111161\"}"));
        TmdbConfigTestService.Result result = TmdbConfigTestService.test(client, "key", server.url("/").toString(), server.url("/").toString(), "omdb-key", server.url("/").toString());
        assertTrue(result.api.success);
        assertTrue(result.image.success);
        assertTrue(result.omdb.success);
        assertTrue(result.api.latencyMillis >= 0);
        assertTrue(result.image.latencyMillis >= 0);
        assertTrue(result.omdb.latencyMillis >= 0);
        assertEquals("/3/configuration?api_key=key", server.takeRequest().getPath());
        assertEquals("/t/p/w342/wwemzKWzjKYJFfCeiB57q3r4Bcm.png", server.takeRequest().getPath());
        assertEquals("/?i=tt0111161&apikey=omdb-key", server.takeRequest().getPath());
    }

    @Test
    public void rejectsUnauthorizedApiAndNonImageResponse() {
        server.enqueue(new MockResponse().setResponseCode(401));
        server.enqueue(new MockResponse().setHeader("Content-Type", "text/html").setBody("not an image"));
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setBody("{\"Response\":\"False\",\"Error\":\"Invalid API key!\"}"));
        TmdbConfigTestService.Result result = TmdbConfigTestService.test(client, "bad", server.url("/").toString(), server.url("/").toString(), "bad-omdb-key", server.url("/").toString());
        assertFalse(result.api.success);
        assertFalse(result.image.success);
        assertFalse(result.omdb.success);
        assertEquals("Invalid API key!", result.omdb.message);
    }

    @Test
    public void rejectsJsonWithoutTmdbConfigurationShape() {
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody("{\"ok\":true}"));
        assertFalse(TmdbConfigTestService.testApi(client, "key", server.url("/").toString()).success);
    }

    @Test
    public void rejectsFakeImageBytes() {
        server.enqueue(new MockResponse().setHeader("Content-Type", "image/png").setBody("fake"));
        assertFalse(TmdbConfigTestService.testImage(client, server.url("/").toString()).success);
    }

    @Test
    public void normalizesSavedApiAndImagePaths() throws Exception {
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody("{\"images\":{}}"));
        server.enqueue(new MockResponse().setHeader("Content-Type", "image/png")
                .setBody(new okio.Buffer().write(Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAAB"))));

        assertTrue(TmdbConfigTestService.testApi(client, "first.second.third", server.url("/3").toString()).success);
        assertTrue(TmdbConfigTestService.testImage(client, server.url("/t/p/w185").toString()).success);

        RecordedRequest apiRequest = server.takeRequest();
        assertEquals("/3/configuration", apiRequest.getPath());
        assertEquals("Bearer first.second.third", apiRequest.getHeader("Authorization"));
        assertEquals("/t/p/w342/wwemzKWzjKYJFfCeiB57q3r4Bcm.png", server.takeRequest().getPath());
    }

    @Test
    public void invalidBearerHeaderBecomesFailedCheck() {
        assertFalse(TmdbConfigTestService.testApi(client, "first.second.\nbad", server.url("/").toString()).success);
    }
    @Test
    public void proxyBaseRoutesApiAndImageChecksToMirrorEndpoint() throws Exception {
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody("{\"images\":{}}"));
        server.enqueue(new MockResponse().setHeader("Content-Type", "image/png")
                .setBody(new okio.Buffer().write(Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAAB"))));

        String proxy = server.url("/tmdb-mirror").toString();
        TmdbConfigTestService.Result result = TmdbConfigTestService.test(client, "key", "https://api.tmdb.org", "https://images.tmdb.org", proxy, "", server.url("/").toString());

        assertTrue(result.api.success);
        assertTrue(result.image.success);
        assertEquals("/tmdb-mirror/3/configuration?api_key=key", server.takeRequest().getPath());
        assertEquals("/tmdb-mirror/t/p/w342/wwemzKWzjKYJFfCeiB57q3r4Bcm.png", server.takeRequest().getPath());
    }

}
