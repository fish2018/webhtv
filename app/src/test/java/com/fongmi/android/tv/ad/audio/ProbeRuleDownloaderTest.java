package com.fongmi.android.tv.ad.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;

public class ProbeRuleDownloaderTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private MockWebServer server;
    private OkHttpClient client;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        // 必须与生产配置一致：重定向由 ProbeRuleDownloader 手动逐跳跟随，
        // 用默认 followRedirects(true) 的 client 会让重定向相关断言失去意义。
        client = new OkHttpClient.Builder().followRedirects(false).build();
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    /** 规则源不签名，所以传输层必须是 HTTPS，否则任何中间人都能改指纹。 */
    @Test
    public void plainHttpUrlIsRejectedBeforeAnyRequest() throws Exception {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ProbeRuleDownloader.refresh("http://example.com/rules.json", store()));

        assertEquals("probe rule url must be https", error.getMessage());
        assertEquals(0, server.getRequestCount());
    }

    @Test
    public void successfulDownloadIsInstalled() throws Exception {
        server.enqueue(new MockResponse().setBody(community()));
        ProbeRuleStore store = store();

        AdAudioRuleSnapshot snapshot = ProbeRuleDownloader.refresh(url(), store, client);

        assertEquals(4, snapshot.ruleSet().rules().size());
        assertTrue(snapshot.version().startsWith("2:"));
        assertEquals(2L, store.revision());
    }

    @Test
    public void oversizedBodyIsRejectedAndNothingIsCached() throws Exception {
        Buffer body = new Buffer();
        body.write(new byte[ProbeRuleStore.MAX_DOWNLOAD_BYTES + 1024]);
        server.enqueue(new MockResponse().setBody(body));
        ProbeRuleStore store = store();

        assertThrows(IOException.class, () -> ProbeRuleDownloader.refresh(url(), store, client));

        assertEquals(0L, store.revision());
    }

    @Test
    public void serverErrorIsRejected() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500));

        assertThrows(IOException.class, () -> ProbeRuleDownloader.refresh(url(), store(), client));
    }

    @Test
    public void malformedBodyLeavesTheStoreEmpty() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"format\":\"nope\"}"));
        ProbeRuleStore store = store();

        assertThrows(IllegalArgumentException.class,
                () -> ProbeRuleDownloader.refresh(url(), store, client));

        assertEquals(0L, store.revision());
    }

    /** 304 表示内容没变，应当返回现有缓存而不是当成下载失败。 */
    @Test
    public void notModifiedKeepsTheCachedSnapshot() throws Exception {
        ProbeRuleStore store = store();
        store.install(community().getBytes(StandardCharsets.UTF_8));
        server.enqueue(new MockResponse().setResponseCode(304));

        AdAudioRuleSnapshot snapshot = ProbeRuleDownloader.refresh(url(), store, client);

        assertEquals(4, snapshot.ruleSet().rules().size());
        assertEquals(2L, store.revision());
    }

    /**
     * chunked 响应没有 Content-Length，声明长度检查会拿到 -1，
     * 因此边读边计数的上限必须独立生效。
     */
    @Test
    public void oversizedChunkedBodyIsStillRejected() throws Exception {
        Buffer body = new Buffer();
        body.write(new byte[ProbeRuleStore.MAX_DOWNLOAD_BYTES + 1024]);
        server.enqueue(new MockResponse().setChunkedBody(body, 64 * 1024));
        ProbeRuleStore store = store();

        assertThrows(IOException.class, () -> ProbeRuleDownloader.refresh(url(), store, client));

        assertEquals(0L, store.revision());
    }

    /**
     * URL 非法时必须回调，否则设置页置上的「正在刷新」文本会一直停在那里。
     * 这里只覆盖不联网的分支，联网分支由 {@link #successfulDownloadIsInstalled()} 覆盖。
     */
    @Test
    public void invalidUrlReportsFailureExactlyOnce() {
        RecordingCallback callback = new RecordingCallback();

        boolean started = ProbeRuleDownloader.refreshNow(
                callback, "http://example.com/rules.json", null);

        assertTrue("caller must not have to reset its own state", started);
        assertEquals(0, callback.success.get());
        assertEquals(0, callback.disabled.get());
        assertNotNull(callback.failure.get());
        assertEquals(0, server.getRequestCount());
    }

    /**
     * 空地址表示用户关闭了社区规则源，属于正常状态，必须走 onDisabled 而不是
     * 把「必须是 https」这种内部英文消息弹给用户。
     */
    @Test
    public void emptySourceReportsDisabledInsteadOfAnError() {
        RecordingCallback callback = new RecordingCallback();

        boolean started = ProbeRuleDownloader.refreshNow(callback, "", null);

        assertTrue(started);
        assertEquals(1, callback.disabled.get());
        assertNull(callback.failure.get());
        assertEquals(0, callback.success.get());
        assertEquals(0, server.getRequestCount());
    }

    /**
     * 规则源无签名，传输层是唯一保障，所以 https 到明文的降级必须在**发出明文请求之前**拒绝。
     * OkHttp 的 followSslRedirects(false) 实测挡不住这一跳（它照样跟随），
     * 所以重定向改成手动逐跳跟随，判定逻辑就是这里直接测的这个函数。
     */
    @Test
    public void redirectOffHttpsIsRejectedBeforeTheNextRequest() throws Exception {
        HttpUrl current = HttpUrl.get("https://rules.example.com/rules.json");

        assertThrows(IOException.class,
                () -> ProbeRuleDownloader.nextRedirect(current, "http://evil.example.com/x.json", true));

        // 协议相对地址会沿用当前协议，仍是 https，不算降级；换主机本身是允许的。
        assertEquals("https://evil.example.com/x.json",
                ProbeRuleDownloader.nextRedirect(current, "//evil.example.com/x.json", true));
    }

    @Test
    public void redirectWithinHttpsIsAccepted() throws Exception {
        HttpUrl current = HttpUrl.get("https://rules.example.com/rules.json");

        assertEquals("https://rules.example.com/v2.json",
                ProbeRuleDownloader.nextRedirect(current, "/v2.json", true));
        assertEquals("https://cdn.example.com/v2.json",
                ProbeRuleDownloader.nextRedirect(current, "https://cdn.example.com/v2.json", true));
    }

    @Test
    public void redirectWithoutLocationIsRejected() {
        HttpUrl current = HttpUrl.get("https://rules.example.com/rules.json");

        assertThrows(IOException.class, () -> ProbeRuleDownloader.nextRedirect(current, null, true));
        assertThrows(IOException.class, () -> ProbeRuleDownloader.nextRedirect(current, "", true));
    }

    /** 同协议内的重定向必须能正常跟随。 */
    @Test
    public void sameSchemeRedirectIsFollowedEndToEnd() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(302).setHeader("Location", "/final.json"));
        server.enqueue(new MockResponse().setBody(community()));
        ProbeRuleStore store = store();

        AdAudioRuleSnapshot snapshot = ProbeRuleDownloader.refresh(url(), store, client);

        assertEquals(4, snapshot.ruleSet().rules().size());
        assertEquals(2, server.getRequestCount());
    }

    /** 重定向循环必须有界，否则会一直打请求。 */
    @Test
    public void redirectLoopIsBounded() throws Exception {
        for (int i = 0; i < 20; i++) {
            server.enqueue(new MockResponse().setResponseCode(302).setHeader("Location", "/again.json"));
        }

        assertThrows(IOException.class, () -> ProbeRuleDownloader.refresh(url(), store(), client));

        assertTrue("redirects must be bounded, saw " + server.getRequestCount(),
                server.getRequestCount() <= 8);
    }

    private static final class RecordingCallback implements ProbeRuleDownloader.Callback {

        private final AtomicInteger success = new AtomicInteger();
        private final AtomicInteger disabled = new AtomicInteger();
        private final AtomicReference<Throwable> failure = new AtomicReference<>();

        @Override
        public void onSuccess(AdAudioRuleSnapshot snapshot) {
            success.incrementAndGet();
        }

        @Override
        public void onFailure(Throwable error) {
            failure.set(error);
        }

        @Override
        public void onDisabled() {
            disabled.incrementAndGet();
        }
    }

    private String url() {
        return server.url("/rules.json").toString();
    }

    private ProbeRuleStore store() throws IOException {
        return new ProbeRuleStore(temporaryFolder.newFolder().toPath());
    }

    private String community() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/probe-rules-v1-community.json")) {
            assertNotNull(stream);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
