package com.fongmi.android.tv.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.security.MessageDigest;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class OciRegistryClient {

    static final String MANIFEST_MEDIA_TYPE = "application/vnd.oci.image.manifest.v1+json";
    static final String ARTIFACT_TYPE = "application/vnd.webhtv.apk.v1";
    static final String CONFIG_MEDIA_TYPE = "application/vnd.oci.empty.v1+json";
    static final String APK_MEDIA_TYPE = "application/vnd.android.package-archive";
    private static final int MAX_MANIFEST_BYTES = 1024 * 1024;
    private static final int MAX_REDIRECTS = 5;

    private final OkHttpClient client;

    public OciRegistryClient(OkHttpClient client) {
        this.client = client;
    }

    public void pull(String endpoint, OciArtifact artifact, File file, Observer observer) throws Exception {
        if (artifact == null || !artifact.isValid()) throw new IOException("Invalid OCI artifact descriptor");
        HttpUrl manifestUrl = artifactUrl(endpoint, artifact.repository, "manifests", artifact.manifestDigest);
        Request manifestRequest = new Request.Builder()
                .url(manifestUrl)
                .header("Accept", MANIFEST_MEDIA_TYPE)
                .header("Accept-Encoding", "identity")
                .build();
        byte[] manifest;
        try (Response response = executeAuthorized(manifestRequest, artifact, observer)) {
            requireSuccess(response, "OCI manifest");
            String contentType = response.header("Content-Type", "");
            if (!contentType.isEmpty() && !contentType.startsWith(MANIFEST_MEDIA_TYPE)) throw new IOException("Unexpected OCI manifest content type");
            manifest = readLimited(response.body(), MAX_MANIFEST_BYTES);
        }
        verifyDigest(manifest, artifact.manifestDigest, "OCI manifest");
        validateManifest(manifest, artifact);
        downloadBlob(endpoint, artifact, file, observer);
    }

    private void downloadBlob(String endpoint, OciArtifact artifact, File file, Observer observer) throws Exception {
        HttpUrl blobUrl = artifactUrl(endpoint, artifact.repository, "blobs", artifact.layerDigest);
        Request request = new Request.Builder().url(blobUrl).header("Accept-Encoding", "identity").build();
        try (Response response = executeAuthorized(request, artifact, observer)) {
            requireSuccess(response, "OCI blob");
            ResponseBody body = response.body();
            if (body == null) throw new IOException("OCI blob is empty");
            long responseLength = body.contentLength();
            if (responseLength > 0 && responseLength != artifact.size) throw new IOException("OCI blob size mismatch");
            streamBlob(body.byteStream(), artifact, file, observer);
        }
    }

    private void streamBlob(InputStream source, OciArtifact artifact, File file, Observer observer) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Unable to create update directory");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (BufferedInputStream input = new BufferedInputStream(source); FileOutputStream output = new FileOutputStream(file)) {
            byte[] buffer = new byte[16384];
            long bytes = 0;
            long start = System.currentTimeMillis();
            long lastTime = start;
            long lastBytes = 0;
            int lastProgress = -1;
            observer.onProgress(0, 0, artifact.size, 0, 0);
            int count;
            while ((count = input.read(buffer)) != -1) {
                checkCanceled(observer);
                output.write(buffer, 0, count);
                digest.update(buffer, 0, count);
                bytes += count;
                if (bytes > artifact.size) throw new IOException("OCI blob exceeds expected size");
                long now = System.currentTimeMillis();
                int progress = (int) (bytes * 100 / artifact.size);
                if (progress == lastProgress && now - lastTime < 1000) continue;
                long speed = (bytes - lastBytes) * 1000 / Math.max(1, now - lastTime);
                observer.onProgress(progress, bytes, artifact.size, speed, now - start);
                lastProgress = progress;
                lastTime = now;
                lastBytes = bytes;
            }
            if (bytes != artifact.size) throw new IOException("OCI blob download is incomplete");
            String actual = "sha256:" + hex(digest.digest());
            if (!actual.equals(artifact.layerDigest)) throw new IOException("OCI blob digest mismatch");
        }
    }

    private Response executeAuthorized(Request request, OciArtifact artifact, Observer observer) throws Exception {
        Response response = execute(request, observer);
        if (response.code() != 401) return response;
        String header = response.header("WWW-Authenticate");
        Request challengedRequest = response.request();
        response.close();
        OciAuthChallenge challenge = OciAuthChallenge.parse(header);
        String token = token(challenge, artifact, challengedRequest.url().host(), observer);
        return execute(challengedRequest.newBuilder().header("Authorization", "Bearer " + token).build(), observer);
    }

    private String token(OciAuthChallenge challenge, OciArtifact artifact, String requestHost, Observer observer) throws Exception {
        String realm = UpdateUrl.requireHttpsUrl(challenge.realm);
        String realmHost = UpdateUrl.host(realm);
        if (!allowedAuthHost(realmHost, requestHost, artifact.registry)) throw new IOException("Untrusted Registry authentication host");
        HttpUrl parsed = HttpUrl.parse(realm);
        if (parsed == null) throw new IOException("Invalid Registry authentication URL");
        HttpUrl.Builder builder = parsed.newBuilder();
        String expectedScope = "repository:" + artifact.repository + ":pull";
        if (!challenge.scope.isEmpty() && !expectedScope.equals(challenge.scope)) throw new IOException("Unexpected Registry authentication scope");
        if (!challenge.service.isEmpty()) builder.addQueryParameter("service", challenge.service);
        builder.addQueryParameter("scope", expectedScope);
        Request request = new Request.Builder().url(builder.build()).header("Accept", "application/json").header("Accept-Encoding", "identity").build();
        try (Response response = execute(request, observer)) {
            requireSuccess(response, "Registry token");
            byte[] bytes = readLimited(response.body(), MAX_MANIFEST_BYTES);
            JsonObject object = JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            String token = string(object, "token");
            if (token.isEmpty()) token = string(object, "access_token");
            if (token.isEmpty()) throw new IOException("Registry token is missing");
            return token;
        }
    }

    private Response execute(Request initial, Observer observer) throws Exception {
        Request request = initial;
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            checkCanceled(observer);
            Call call = client.newCall(request);
            observer.onCall(call);
            Response response = call.execute();
            if (!isRedirect(response.code())) return response;
            String location = response.header("Location");
            HttpUrl next = location == null ? null : response.request().url().resolve(location);
            response.close();
            if (next == null || !next.isHttps()) throw new IOException("Unsafe OCI redirect");
            boolean sameOrigin = sameOrigin(request.url(), next);
            Request.Builder builder = request.newBuilder().url(next);
            if (!sameOrigin) builder.removeHeader("Authorization");
            request = builder.build();
        }
        throw new IOException("Too many OCI redirects");
    }

    void validateManifest(byte[] bytes, OciArtifact artifact) throws IOException {
        try {
            JsonObject object = JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            if (number(object, "schemaVersion") != 2) throw new IOException("Unsupported OCI schema version");
            if (!MANIFEST_MEDIA_TYPE.equals(string(object, "mediaType"))) throw new IOException("Unexpected OCI manifest media type");
            if (!ARTIFACT_TYPE.equals(string(object, "artifactType"))) throw new IOException("Unexpected OCI artifact type");
            JsonObject config = object.getAsJsonObject("config");
            if (config == null || !CONFIG_MEDIA_TYPE.equals(string(config, "mediaType"))) throw new IOException("Unexpected OCI config type");
            JsonArray layers = object.getAsJsonArray("layers");
            if (layers == null || layers.size() != 1) throw new IOException("OCI artifact must contain exactly one APK layer");
            JsonObject layer = layers.get(0).getAsJsonObject();
            if (!APK_MEDIA_TYPE.equals(string(layer, "mediaType"))) throw new IOException("Unexpected OCI layer media type");
            if (!artifact.layerDigest.equals(string(layer, "digest").toLowerCase(Locale.ROOT))) throw new IOException("OCI layer digest mismatch");
            if (longNumber(layer, "size") != artifact.size) throw new IOException("OCI layer size mismatch");
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Invalid OCI manifest", e);
        }
    }

    private HttpUrl artifactUrl(String endpoint, String repository, String operation, String value) throws IOException {
        HttpUrl base = HttpUrl.parse(UpdateUrl.requireHttpsOrigin(endpoint));
        if (base == null) throw new IOException("Invalid OCI endpoint");
        return base.newBuilder().addPathSegment("v2").addPathSegments(repository).addPathSegment(operation).addPathSegment(value).build();
    }

    private byte[] readLimited(ResponseBody body, int limit) throws IOException {
        if (body == null) throw new IOException("Response body is empty");
        long length = body.contentLength();
        if (length > limit) throw new IOException("Response body is too large");
        try (InputStream input = body.byteStream(); ByteArrayOutputStream output = new ByteArrayOutputStream(length > 0 ? (int) length : 8192)) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > limit) throw new IOException("Response body is too large");
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private void verifyDigest(byte[] bytes, String expected, String label) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String actual = "sha256:" + hex(digest.digest(bytes));
        if (!actual.equals(expected)) throw new IOException(label + " digest mismatch");
    }

    private void requireSuccess(Response response, String label) throws IOException {
        if (!response.isSuccessful()) throw new IOException(label + " failed: HTTP " + response.code());
    }

    private void checkCanceled(Observer observer) throws InterruptedIOException {
        if (observer.isCanceled() || Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Canceled");
    }

    private boolean allowedAuthHost(String realm, String request, String registry) {
        return realm.equalsIgnoreCase(request) || realm.equalsIgnoreCase(registry) || "auth.docker.io".equals(realm) || "token.docker.io".equals(realm);
    }

    private boolean sameOrigin(HttpUrl first, HttpUrl second) {
        return first.scheme().equals(second.scheme()) && first.host().equals(second.host()) && first.port() == second.port();
    }

    private boolean isRedirect(int code) {
        return code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
    }

    private String string(JsonObject object, String name) {
        return object != null && object.has(name) && !object.get(name).isJsonNull() ? object.get(name).getAsString() : "";
    }

    private int number(JsonObject object, String name) {
        return object != null && object.has(name) ? object.get(name).getAsInt() : 0;
    }

    private long longNumber(JsonObject object, String name) {
        return object != null && object.has(name) ? object.get(name).getAsLong() : 0;
    }

    private String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) builder.append(String.format(Locale.ROOT, "%02x", value));
        return builder.toString();
    }

    public interface Observer {

        boolean isCanceled();

        void onCall(Call call);

        void onProgress(int progress, long bytes, long total, long speed, long elapsed);
    }
}
