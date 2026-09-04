package com.fongmi.android.tv.update;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;

public class OciRegistryClientTest {

    private final OciArtifact artifact = new OciArtifact(
            "registry-1.docker.io",
            "fish2018/webhtv-apk",
            "v1-mobile-arm64_v8a",
            "sha256:" + "1".repeat(64),
            "sha256:" + "2".repeat(64),
            1024);

    @Test
    public void acceptsExactlyOnePinnedApkLayer() throws Exception {
        new OciRegistryClient(null).validateManifest(manifest(layer()), artifact);
    }

    @Test
    public void rejectsMultipleLayersAndWrongMediaType() {
        String twoLayers = layer() + "," + layer();
        assertThrows(IOException.class, () -> new OciRegistryClient(null).validateManifest(manifest(twoLayers), artifact));
        assertThrows(IOException.class, () -> new OciRegistryClient(null).validateManifest(
                manifest(layer().replace(OciRegistryClient.APK_MEDIA_TYPE, "application/octet-stream")), artifact));
    }

    @Test
    public void rejectsDescriptorDigestOrSizeMismatch() {
        assertThrows(IOException.class, () -> new OciRegistryClient(null).validateManifest(
                manifest(layer().replace(artifact.layerDigest, "sha256:" + "3".repeat(64))), artifact));
        assertThrows(IOException.class, () -> new OciRegistryClient(null).validateManifest(
                manifest(layer().replace("\"size\":1024", "\"size\":1025")), artifact));
    }

    @Test
    public void pullsThroughBearerAuthAndStripsAuthorizationOnCdnRedirect() throws Exception {
        byte[] apk = "signed-apk-bytes".getBytes(StandardCharsets.UTF_8);
        String layerDigest = digest(apk);
        OciArtifact provisional = new OciArtifact(
                "registry-1.docker.io",
                "fish2018/webhtv-apk",
                "v1-mobile-arm64_v8a",
                "sha256:" + "0".repeat(64),
                layerDigest,
                apk.length);
        byte[] manifest = manifest(layer(provisional));
        OciArtifact target = new OciArtifact(
                provisional.registry,
                provisional.repository,
                provisional.reference,
                digest(manifest),
                provisional.layerDigest,
                provisional.size);
        AtomicInteger tokenCalls = new AtomicInteger();
        AtomicInteger manifestCalls = new AtomicInteger();
        AtomicInteger blobCalls = new AtomicInteger();
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain -> {
            Request request = chain.request();
            String host = request.url().host();
            if ("mirror.example".equals(host)) {
                return new Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(307)
                        .message("Temporary Redirect")
                        .header("Location", "https://registry.example" + request.url().encodedPath())
                        .body(new BytesBody(new byte[0], null))
                        .build();
            }
            if ("registry.example".equals(host) && "/token".equals(request.url().encodedPath())) {
                tokenCalls.incrementAndGet();
                return response(request, 200, "OK", "{\"token\":\"test-token\"}".getBytes(StandardCharsets.UTF_8), "application/json");
            }
            if ("cdn.example.com".equals(host)) {
                assertNull(request.header("Authorization"));
                return response(request, 200, "OK", apk, "application/octet-stream");
            }
            if (request.url().encodedPath().contains("/manifests/")) {
                manifestCalls.incrementAndGet();
                if (request.header("Authorization") == null) return unauthorized(request, artifact.repository);
                assertEquals("Bearer test-token", request.header("Authorization"));
                return response(request, 200, "OK", manifest, OciRegistryClient.MANIFEST_MEDIA_TYPE);
            }
            if (request.url().encodedPath().contains("/blobs/")) {
                blobCalls.incrementAndGet();
                if (request.header("Authorization") == null) return unauthorized(request, artifact.repository);
                return new Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(302)
                        .message("Found")
                        .header("Location", "https://cdn.example.com/apk")
                        .body(new BytesBody(new byte[0], null))
                        .build();
            }
            throw new IOException("Unexpected request: " + request.url());
        }).followRedirects(false).build();
        File file = File.createTempFile("oci-update", ".apk");
        try {
            new OciRegistryClient(client).pull("https://mirror.example", target, file, new Observer());
            assertArrayEquals(apk, Files.readAllBytes(file.toPath()));
            assertEquals(2, tokenCalls.get());
            assertEquals(2, manifestCalls.get());
            assertEquals(2, blobCalls.get());
        } finally {
            Files.deleteIfExists(file.toPath());
        }
    }

    @Test
    public void rejectsBearerScopeForAnotherRepository() {
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain -> {
            Request request = chain.request();
            return unauthorized(request, "other/repository");
        }).followRedirects(false).build();

        assertThrows(IOException.class, () -> new OciRegistryClient(client).pull(
                "https://mirror.example", artifact, new File("unused.apk"), new Observer()));
    }

    private byte[] manifest(String layers) {
        String json = "{" +
                "\"schemaVersion\":2," +
                "\"mediaType\":\"" + OciRegistryClient.MANIFEST_MEDIA_TYPE + "\"," +
                "\"artifactType\":\"" + OciRegistryClient.ARTIFACT_TYPE + "\"," +
                "\"config\":{\"mediaType\":\"" + OciRegistryClient.CONFIG_MEDIA_TYPE + "\",\"digest\":\"sha256:" + "0".repeat(64) + "\",\"size\":2}," +
                "\"layers\":[" + layers + "]}";
        return json.getBytes(StandardCharsets.UTF_8);
    }

    private String layer() {
        return "{\"mediaType\":\"" + OciRegistryClient.APK_MEDIA_TYPE + "\",\"digest\":\"" + artifact.layerDigest + "\",\"size\":1024}";
    }

    private String layer(OciArtifact value) {
        return "{\"mediaType\":\"" + OciRegistryClient.APK_MEDIA_TYPE + "\",\"digest\":\"" + value.layerDigest + "\",\"size\":" + value.size + "}";
    }

    private String digest(byte[] value) throws Exception {
        byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value);
        StringBuilder builder = new StringBuilder("sha256:");
        for (byte item : bytes) builder.append(String.format(Locale.ROOT, "%02x", item));
        return builder.toString();
    }

    private Response unauthorized(Request request, String repository) {
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(401)
                .message("Unauthorized")
                .header("WWW-Authenticate", "Bearer realm=\"https://registry.example/token\",service=\"registry.example\",scope=\"repository:" + repository + ":pull\"")
                .body(new BytesBody(new byte[0], null))
                .build();
    }

    private Response response(Request request, int code, String message, byte[] body, String mediaType) {
        Response.Builder builder = new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(message)
                .body(new BytesBody(body, mediaType));
        if (mediaType != null) builder.header("Content-Type", mediaType);
        return builder.build();
    }

    private static final class Observer implements OciRegistryClient.Observer {

        @Override
        public boolean isCanceled() {
            return false;
        }

        @Override
        public void onCall(Call call) {
        }

        @Override
        public void onProgress(int progress, long bytes, long total, long speed, long elapsed) {
        }
    }

    private static final class BytesBody extends ResponseBody {

        private final byte[] bytes;
        private final MediaType mediaType;

        private BytesBody(byte[] bytes, String mediaType) {
            this.bytes = bytes;
            this.mediaType = mediaType == null ? null : MediaType.parse(mediaType);
        }

        @Override
        public MediaType contentType() {
            return mediaType;
        }

        @Override
        public long contentLength() {
            return bytes.length;
        }

        @Override
        public BufferedSource source() {
            return new Buffer().write(bytes);
        }
    }
}
