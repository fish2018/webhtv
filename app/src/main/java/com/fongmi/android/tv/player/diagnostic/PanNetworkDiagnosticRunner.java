package com.fongmi.android.tv.player.diagnostic;

import android.net.Uri;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.okhttp.OkHttpDataSource;

import com.fongmi.android.tv.App;
import com.github.catvod.crawler.SpiderDebug;

import java.io.InputStream;
import java.io.IOException;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Call;
import okhttp3.ConnectionPool;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Runs a bounded, cancellable benchmark against the exact URL used by playback. */
public final class PanNetworkDiagnosticRunner {

    private static final int CONNECT_TIMEOUT_SECONDS = 12;
    private static final int READ_TIMEOUT_SECONDS = 20;
    private static final long PROGRESS_INTERVAL_MS = 250;
    private static final long STAGE_COOLDOWN_MS = 350;
    private static final long MIB = 1024L * 1024L;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final PanDiagnosticRunGeneration runGeneration = new PanDiagnosticRunGeneration();
    private final Set<Call> calls = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final OkHttpClient client;
    private volatile Future<?> future;
    private volatile DataSource activeDataSource;

    public PanNetworkDiagnosticRunner() {
        client = new OkHttpClient.Builder()
                .proxy(Proxy.NO_PROXY)
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(0, 1, TimeUnit.MILLISECONDS))
                .addNetworkInterceptor(chain -> {
                    Request request = chain.request();
                    HttpUrl originalUrl = chain.call().request().url();
                    String originalHost = originalUrl.host();
                    if (!PanNetworkSafety.isLoopbackHost(originalHost) || !originalHost.equalsIgnoreCase(request.url().host())) {
                        PanNetworkSafety.requireSafeRemoteHost(request.url().host());
                    }
                    return chain.proceed(PanNetworkSafety.stripSensitiveHeadersOnRedirect(request, originalUrl));
                })
                .protocols(List.of(Protocol.HTTP_1_1))
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
    }

    public void start(RequestConfig config, Listener listener) {
        cancel();
        cancelled.set(false);
        long runId = runGeneration.next();
        future = executor.submit(() -> run(config, currentListener(runId, listener)));
    }

    public void cancel() {
        runGeneration.invalidate();
        cancelled.set(true);
        Future<?> running = future;
        future = null;
        if (running != null) running.cancel(true);
        for (Call call : calls) call.cancel();
        calls.clear();
        DataSource source = activeDataSource;
        if (source != null) {
            try {
                source.close();
            } catch (IOException ignored) {
            }
        }
    }

    public void release() {
        cancel();
        executor.shutdownNow();
    }

    private Listener currentListener(long runId, Listener listener) {
        return new Listener() {
            @Override
            public void onProgress(Progress progress) {
                runGeneration.runIfCurrent(runId, () -> listener.onProgress(progress));
            }

            @Override
            public void onComplete(Report report) {
                runGeneration.runIfCurrent(runId, () -> listener.onComplete(report));
            }

            @Override
            public void onCancelled() {
                runGeneration.runIfCurrent(runId, listener::onCancelled);
            }

            @Override
            public void onError(String message) {
                runGeneration.runIfCurrent(runId, () -> listener.onError(message));
            }
        };
    }

    private void run(RequestConfig config, Listener listener) {
        try {
            PanEndpoint endpoint = PanEndpointParser.parse(config.playbackUrl, config.playbackHeaders);
            String directUrl = endpoint.hasDirectUpstream() ? endpoint.upstreamUrl() : endpoint.playbackUrl();
            Map<String, String> directHeaders = endpoint.hasDirectUpstream() ? endpoint.upstreamHeaders() : config.playbackHeaders;
            boolean proxied = endpoint.hasDirectUpstream() && !sameUrl(endpoint.playbackUrl(), endpoint.upstreamUrl());
            if (endpoint.hasDirectUpstream()) PanNetworkSafety.requireSafeRemoteHost(endpoint.upstreamHost());
            List<Integer> threads = PanBenchmarkPlan.sanitizeThreads(config.threadValues);
            int repeats = PanBenchmarkPlan.repeats(config.mode);
            int directGroups = 0;
            if (proxied) for (int thread : threads) if (thread > 1) directGroups++;
            int pairedValidationRounds = proxied && repeats > 1 ? repeats : 0;
            int totalRounds = repeats * (2 + (proxied ? threads.size() + directGroups : 0)) + pairedValidationRounds;
            int round = 0;

            post(listener, new Progress("正在探测资源与鉴权", round, totalRounds, 0, 0, 0, 0, 0, Collections.emptyList()));
            String probeUrl = proxied ? endpoint.playbackUrl() : directUrl;
            Map<String, String> probeHeaders = proxied ? config.playbackHeaders : directHeaders;
            Probe probe;
            try {
                probe = probe(probeUrl, probeHeaders, proxied ? "本地Go代理探测" : "播放直链探测");
            } catch (IOException error) {
                probe = new Probe(-1, 0, "");
                post(listener, new Progress("文件探测受限，改用当前媒体码率继续", round, totalRounds, 0, 0, 0, 0, 0, Collections.emptyList()));
            }
            ensureRunning();
            long required = requiredBitsPerSecond(probe.length, config.durationMs, config.formatBitsPerSecond);
            long preferredPosition = rangePosition(probe.length, config.positionMs, config.durationMs);
            long roundTimeLimitMs = PanBenchmarkPlan.roundTimeLimitMs(config.mode);
            if (proxied) warmUpProxy(endpoint.playbackUrl(), config.playbackHeaders, probe.length, listener, totalRounds);
            long upstreamBudget = PanBenchmarkPlan.roundBudgetBytes(required, 1, config.mode);
            Map<Integer, List<Measurement>> directRuns = new LinkedHashMap<>();
            Map<Integer, List<Measurement>> proxyRuns = new LinkedHashMap<>();
            List<Measurement> upstreamRuns = new ArrayList<>();
            for (int thread : threads) {
                directRuns.put(thread, new ArrayList<>());
                proxyRuns.put(thread, new ArrayList<>());
            }

            for (int repeat = 0; repeat < repeats; repeat++) {
                round++;
                long upstreamPosition = isolatedRangePosition(preferredPosition, probe.length, upstreamBudget, round - 1, totalRounds);
                upstreamRuns.add(measureHttpSafe("上游单连接基准", directUrl, directHeaders, upstreamPosition, upstreamBudget,
                        roundTimeLimitMs, 1, round, totalRounds, listener));
                ensureRunning();

                if (!proxied) continue;
                for (int thread : threads) {
                    boolean proxyFirst = (repeat & 1) == 1;
                    if (proxyFirst) {
                        round++;
                        proxyRuns.get(thread).add(measureProxySafe(endpoint, config, preferredPosition, probe.length, required,
                                roundTimeLimitMs, thread, round, totalRounds, listener));
                    }
                    if (thread > 1) {
                        round++;
                        int directConcurrency = PanBenchmarkPlan.directConcurrency(thread);
                        long budget = PanBenchmarkPlan.roundBudgetBytes(required, directConcurrency, config.mode);
                        long position = isolatedRangePosition(preferredPosition, probe.length, budget, round - 1, totalRounds);
                        String label = "直链并发 " + directConcurrency + "路" + (directConcurrency == thread ? "" : "（Go对照" + thread + "线程）");
                        directRuns.get(thread).add(measureConcurrentHttpSafe(label, directUrl, directHeaders, position, budget,
                                roundTimeLimitMs, thread, directConcurrency, round, totalRounds, listener));
                    } else {
                        directRuns.get(thread).add(upstreamRuns.get(upstreamRuns.size() - 1));
                    }
                    if (!proxyFirst) {
                        round++;
                        proxyRuns.get(thread).add(measureProxySafe(endpoint, config, preferredPosition, probe.length, required,
                                roundTimeLimitMs, thread, round, totalRounds, listener));
                    }
                    ensureRunning();
                }
            }

            Measurement upstream = aggregate("上游单连接基准", 1, upstreamRuns, repeats);
            List<Measurement> directMeasurements = aggregateByThread("直链并发", threads, directRuns, repeats);
            List<Measurement> proxyMeasurements = proxied ? aggregateByThread("Go代理", threads, proxyRuns, repeats) : new ArrayList<>();
            Measurement bestProxy = best(proxyMeasurements);
            AppPhaseResult appPhase = measureAppPhase(new AppPhaseInput(endpoint, config, listener, preferredPosition,
                    probe.length, required, roundTimeLimitMs, totalRounds, round, repeats, proxied, bestProxy,
                    proxyMeasurements, proxyRuns));
            round = appPhase.round;
            Measurement dataSource = appPhase.dataSource;
            bestProxy = appPhase.proxy;

            long proxySpeed = bestProxy == null ? upstream.bitsPerSecond : bestProxy.bitsPerSecond;
            Measurement directReference = bestProxy == null ? upstream : findByThreads(directMeasurements, bestProxy.threads);
            long directConcurrentSpeed = directReference == null ? upstream.bitsPerSecond : directReference.bitsPerSecond;
            PanDiagnosticVerdict.Confidence evidenceConfidence = evidenceConfidence(config.mode, upstreamRuns, directRuns, proxyRuns, appPhase.dataSourceRuns);
            PanDiagnosticVerdict.Result verdict = PanDiagnosticVerdict.resolve(new PanDiagnosticVerdict.Input(
                    required, 0, upstream.bitsPerSecond, directConcurrentSpeed, proxySpeed, dataSource.bitsPerSecond,
                    config.rebufferCount, safeInt(config.droppedFrames), evidenceConfidence));
            if (proxied && bestProxy == null && directReference != null && directReference.successful()) {
                verdict = new PanDiagnosticVerdict.Result(PanDiagnosticVerdict.Cause.EXTERNAL_PROXY,
                        PanDiagnosticVerdict.Confidence.LOW, "直链读取正常，但Go代理未完成有效读取");
            } else if (bestProxy != null && bestProxy.successful() && !dataSource.successful()) {
                verdict = new PanDiagnosticVerdict.Result(PanDiagnosticVerdict.Cause.APP_DATA_SOURCE,
                        PanDiagnosticVerdict.Confidence.LOW, "Go代理读取成功，但App DataSource未完成有效读取");
            }
            Report report = new Report(endpoint, probe.length, required, upstream, directMeasurements, proxyMeasurements, dataSource,
                    verdict, evidenceConfidence, repeats, config.rebufferCount, config.rebufferTotalMs, config.droppedFrames, config.mode);
            App.post(() -> listener.onComplete(report));
        } catch (CancelledException ignored) {
            App.post(listener::onCancelled);
        } catch (Throwable error) {
            if (cancelled.get()) App.post(listener::onCancelled);
            else App.post(() -> listener.onError(readableError(error)));
        } finally {
            calls.clear();
        }
    }

    private AppPhaseResult measureAppPhase(AppPhaseInput input) throws CancelledException {
        Measurement proxy = input.proxy();
        int dataSourceThreads = proxy == null ? Math.max(1, input.endpoint().configuredThreads()) : proxy.threads;
        String dataSourceUrl = !input.proxied() || dataSourceThreads == input.endpoint().configuredThreads()
                ? input.endpoint().playbackUrl() : withThread(input.endpoint().playbackUrl(), dataSourceThreads);
        long dataSourceBudget = PanBenchmarkPlan.roundBudgetBytes(input.required(), Math.max(1, dataSourceThreads), input.config().mode);
        List<Measurement> dataSourceRuns = new ArrayList<>();
        List<Measurement> pairedProxyRuns = new ArrayList<>();
        int round = input.startRound();
        for (int repeat = 0; repeat < input.repeats(); repeat++) {
            if (input.proxied() && proxy == null) {
                round = measureAppAfterMissingProxy(input, dataSourceUrl, dataSourceBudget, dataSourceThreads,
                        dataSourceRuns, pairedProxyRuns, round);
                continue;
            }
            boolean appFirst = input.proxied() && input.repeats() > 1 && (repeat & 1) == 1;
            if (!appFirst && input.proxied() && input.repeats() > 1) {
                round++;
                pairedProxyRuns.add(measureProxySafe(input.endpoint(), input.config(), input.preferredPosition(), input.length(),
                        input.required(), input.timeLimitMs(), dataSourceThreads, round, input.totalRounds(), input.listener()));
            }
            round++;
            long position = isolatedRangePosition(input.preferredPosition(), input.length(), dataSourceBudget, round - 1, input.totalRounds());
            dataSourceRuns.add(measureDataSourceSafe(dataSourceUrl, input.config().playbackHeaders, position, dataSourceBudget,
                    input.length(), input.timeLimitMs(), dataSourceThreads, round, input.totalRounds(), input.listener()));
            if (appFirst) {
                round++;
                pairedProxyRuns.add(measureProxySafe(input.endpoint(), input.config(), input.preferredPosition(), input.length(),
                        input.required(), input.timeLimitMs(), dataSourceThreads, round, input.totalRounds(), input.listener()));
            }
            ensureRunning();
        }
        Measurement dataSource = aggregate("App DataSource", dataSourceThreads, dataSourceRuns, input.repeats());
        if (!pairedProxyRuns.isEmpty()) {
            input.proxyRuns().put(dataSourceThreads, pairedProxyRuns);
            Measurement validated = aggregate("Go代理 " + dataSourceThreads + "线程", dataSourceThreads, pairedProxyRuns, input.repeats());
            replaceByThreads(input.proxyMeasurements(), validated);
            proxy = validated;
        }
        return new AppPhaseResult(round, dataSource, proxy, dataSourceRuns);
    }

    private int measureAppAfterMissingProxy(AppPhaseInput input, String dataSourceUrl, long dataSourceBudget,
                                            int dataSourceThreads, List<Measurement> dataSourceRuns,
                                            List<Measurement> pairedProxyRuns, int round) throws CancelledException {
        if (input.repeats() > 1) {
            round++;
            Measurement validation = measureProxySafe(input.endpoint(), input.config(), input.preferredPosition(), input.length(),
                    input.required(), input.timeLimitMs(), dataSourceThreads, round, input.totalRounds(), input.listener());
            pairedProxyRuns.add(validation);
            round++;
            if (validation.successful()) {
                long position = isolatedRangePosition(input.preferredPosition(), input.length(), dataSourceBudget, round - 1, input.totalRounds());
                dataSourceRuns.add(measureDataSourceSafe(dataSourceUrl, input.config().playbackHeaders, position, dataSourceBudget,
                        input.length(), input.timeLimitMs(), dataSourceThreads, round, input.totalRounds(), input.listener()));
                return round;
            }
        } else {
            round++;
        }
        Measurement skipped = failed("App DataSource", dataSourceThreads, 0, new IOException("依赖Go代理未完成，App层未执行"));
        dataSourceRuns.add(skipped);
        post(input.listener(), new Progress("App层因Go代理失败未执行", round, input.totalRounds(), 0, 0,
                dataSourceBudget, dataSourceThreads, 0, Collections.emptyList()));
        return round;
    }

    private static void replaceByThreads(List<Measurement> values, Measurement replacement) {
        for (int index = 0; index < values.size(); index++) {
            if (values.get(index).threads == replacement.threads) {
                values.set(index, replacement);
                return;
            }
        }
        values.add(replacement);
    }

    private Probe probe(String url, Map<String, String> headers, String label) throws IOException, CancelledException {
        Request.Builder builder = request(url, headers).header("Range", "bytes=0-0");
        try (Response response = execute(builder.build())) {
            logResponse(label, url, response.code());
            if (!response.isSuccessful() && response.code() != 416) throw httpError(response, label);
            long length = totalLength(response);
            ResponseBody body = response.body();
            if (body != null && body.contentLength() >= 0 && body.contentLength() <= PanBenchmarkPlan.PROBE_BYTES) {
                byte[] buffer = new byte[1024];
                long bytes = 0;
                InputStream stream = body.byteStream();
                while (bytes < PanBenchmarkPlan.PROBE_BYTES) {
                    ensureRunning();
                    int read = stream.read(buffer, 0, (int) Math.min(buffer.length, PanBenchmarkPlan.PROBE_BYTES - bytes));
                    if (read < 0) break;
                    bytes += read;
                }
            }
            if (SpiderDebug.isEnabled()) SpiderDebug.log("pan-diagnostic", "stage=%s length=%d contentLength=%d hasContentRange=%s", label, length, response.body() == null ? -1 : response.body().contentLength(), response.header("Content-Range") != null);
            return new Probe(length, response.code(), response.header("Accept-Ranges", ""));
        }
    }

    private void warmUpProxy(String url, Map<String, String> headers, long length, Listener listener,
                             int totalRounds) throws CancelledException {
        post(listener, new Progress("正在预热Go代理", 0, totalRounds, 0, 0, PanBenchmarkPlan.PROXY_WARMUP_BYTES, 0, 0, Collections.emptyList()));
        IOException lastError = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            long maxStart = Math.max(0, length - PanBenchmarkPlan.PROXY_WARMUP_BYTES);
            long position = length <= 0 ? 0 : Math.min(maxStart, Math.max(0, length / 20 + attempt * PanBenchmarkPlan.PROXY_WARMUP_BYTES * 2));
            Request request = request(url, headers).header("Range", "bytes=" + position + "-" + safeEnd(position, PanBenchmarkPlan.PROXY_WARMUP_BYTES)).build();
            long bytes = 0;
            try (Response response = execute(request)) {
                logResponse("Go代理预热", url, response.code());
                if (!response.isSuccessful()) throw httpError(response, "Go代理预热");
                ResponseBody body = response.body();
                if (body == null) throw new IOException("Go代理预热响应没有数据");
                byte[] buffer = new byte[64 * 1024];
                while (bytes < PanBenchmarkPlan.PROXY_WARMUP_BYTES) {
                    ensureRunning();
                    int read = body.byteStream().read(buffer, 0, (int) Math.min(buffer.length, PanBenchmarkPlan.PROXY_WARMUP_BYTES - bytes));
                    if (read < 0) break;
                    bytes += read;
                }
                if (bytes >= 64 * 1024) {
                    cooldown();
                    return;
                }
                lastError = new IOException("Go代理预热读取不足");
            } catch (IOException error) {
                lastError = error;
            }
            cooldown();
        }
        String message = lastError == null ? "未知错误" : readableError(lastError);
        post(listener, new Progress("Go代理预热未完成：" + message, 0, totalRounds, 0, 0, PanBenchmarkPlan.PROXY_WARMUP_BYTES, 0, 0, Collections.emptyList()));
    }

    private Measurement measureProxySafe(PanEndpoint endpoint, RequestConfig config, long preferredPosition, long length,
                                         long required, long timeLimitMs, int thread, int round, int totalRounds,
                                         Listener listener) throws CancelledException {
        String label = "Go代理 " + thread + "线程";
        String url = thread == endpoint.configuredThreads() ? endpoint.playbackUrl() : withThread(endpoint.playbackUrl(), thread);
        long budget = PanBenchmarkPlan.roundBudgetBytes(required, thread, config.mode);
        long position = isolatedRangePosition(preferredPosition, length, budget, round - 1, totalRounds);
        Measurement first = measureHttpSafe(label, url, config.playbackHeaders, position, budget, timeLimitMs, thread, round, totalRounds, listener);
        if (!isTransientEof(first)) return first;
        post(listener, new Progress(label + "瞬态断流，正在换Range重试", round - 1, totalRounds, 0, 0, budget, thread, 0, Collections.emptyList()));
        long retryPosition = retryRangePosition(position, length, budget);
        Measurement retry = measureHttpSafe(label, url, config.playbackHeaders, retryPosition, budget, timeLimitMs, thread, round, totalRounds, listener);
        return markRetried(retry);
    }

    private Measurement measureHttpSafe(String label, String url, Map<String, String> headers, long position, long budget,
                                        long timeLimitMs, int threads, int round, int totalRounds, Listener listener) throws CancelledException {
        long started = SystemClock.elapsedRealtime();
        Measurement result;
        try {
            result = measureHttp(label, url, headers, position, budget, timeLimitMs, threads, round, totalRounds, listener);
        } catch (IOException error) {
            long elapsed = elapsedSince(started);
            post(listener, new Progress(label + "失败，继续下一项", round, totalRounds, 0, 0, budget, threads, elapsed, Collections.emptyList()));
            result = failed(label, threads, elapsed, error);
        }
        cooldown();
        return result;
    }

    private Measurement measureConcurrentHttpSafe(String label, String url, Map<String, String> headers, long position, long budget,
                                                  long timeLimitMs, int requestedThreads, int concurrency, int round,
                                                  int totalRounds, Listener listener) throws CancelledException {
        long started = SystemClock.elapsedRealtime();
        Measurement result;
        try {
            result = measureConcurrentHttp(label, url, headers, position, budget, timeLimitMs, requestedThreads,
                    concurrency, round, totalRounds, listener);
        } catch (IOException error) {
            long elapsed = elapsedSince(started);
            post(listener, new Progress(label + "失败，继续下一项", round, totalRounds, 0, 0, budget, concurrency, elapsed, Collections.emptyList()));
            result = failed(label, requestedThreads, elapsed, error);
        }
        cooldown();
        return result;
    }

    private Measurement measureDataSourceSafe(String url, Map<String, String> headers, long position, long budget,
                                              long length, long timeLimitMs, int threads, int round, int totalRounds,
                                              Listener listener) throws CancelledException {
        long started = SystemClock.elapsedRealtime();
        Measurement result;
        try {
            result = measureDataSource(url, headers, position, budget, timeLimitMs, threads, round, totalRounds, listener);
        } catch (IOException error) {
            long elapsed = elapsedSince(started);
            post(listener, new Progress("App DataSource测量失败，继续生成有限结论", round, totalRounds, 0, 0, budget, threads, elapsed, Collections.emptyList()));
            result = failed("App DataSource", threads, elapsed, error);
        }
        cooldown();
        if (!isTransientEof(result)) return result;
        post(listener, new Progress("App DataSource瞬态断流，正在换Range重试", round - 1, totalRounds, 0, 0, budget, threads, 0, Collections.emptyList()));
        long retryPosition = retryRangePosition(position, length, budget);
        long retryStarted = SystemClock.elapsedRealtime();
        try {
            result = measureDataSource(url, headers, retryPosition, budget, timeLimitMs, threads, round, totalRounds, listener);
        } catch (IOException error) {
            long elapsed = elapsedSince(retryStarted);
            post(listener, new Progress("App DataSource重试仍失败", round, totalRounds, 0, 0, budget, threads, elapsed, Collections.emptyList()));
            result = failed("App DataSource", threads, elapsed, error);
        }
        cooldown();
        return markRetried(result);
    }

    private Measurement measureConcurrentHttp(String label, String url, Map<String, String> headers, long position, long budget,
                                              long timeLimitMs, int requestedThreads, int concurrency, int round,
                                              int totalRounds, Listener listener) throws IOException, CancelledException {
        int workers = Math.max(1, concurrency);
        long baseBudget = budget / workers;
        long remainder = budget % workers;
        long started = SystemClock.elapsedRealtime();
        long deadline = started + timeLimitMs;
        AtomicLong totalBytes = new AtomicLong();
        AtomicLong firstByteAt = new AtomicLong();
        AtomicInteger workersWithData = new AtomicInteger();
        AtomicReference<IOException> firstError = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(workers);
        Set<Call> stageCalls = Collections.newSetFromMap(new ConcurrentHashMap<>());
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        List<Long> samples = new ArrayList<>();
        for (int index = 0; index < workers; index++) {
            final int workerIndex = index;
            pool.execute(() -> {
                long workerBudget = baseBudget + (workerIndex < remainder ? 1 : 0);
                long offset = baseBudget * workerIndex + Math.min(workerIndex, (int) remainder);
                long workerBytes = 0;
                Call call = null;
                try {
                    if (cancelled.get()) return;
                    long startAt = Math.max(0, position + offset);
                    Request request = request(url, headers).header("Range", "bytes=" + startAt + "-" + safeEnd(startAt, workerBudget)).build();
                    call = client.newCall(request);
                    calls.add(call);
                    stageCalls.add(call);
                    try (Response response = call.execute()) {
                        logResponse(label, url, response.code());
                        if (!response.isSuccessful()) throw httpError(response, label);
                        ResponseBody body = response.body();
                        if (body == null) throw new IOException("响应没有数据");
                        byte[] buffer = new byte[64 * 1024];
                        while (workerBytes < workerBudget && !cancelled.get() && SystemClock.elapsedRealtime() < deadline) {
                            int read = body.byteStream().read(buffer, 0, (int) Math.min(buffer.length, workerBudget - workerBytes));
                            if (read < 0) break;
                            firstByteAt.compareAndSet(0, SystemClock.elapsedRealtime());
                            workerBytes += read;
                            totalBytes.addAndGet(read);
                        }
                    }
                } catch (IOException error) {
                    if (!cancelled.get()) firstError.compareAndSet(null, error);
                } finally {
                    if (workerBytes > 0) workersWithData.incrementAndGet();
                    if (call != null) {
                        stageCalls.remove(call);
                        calls.remove(call);
                    }
                    done.countDown();
                }
            });
        }

        long lastProgress = started;
        boolean timedSample = false;
        try {
            while (done.getCount() > 0) {
                ensureRunning();
                long now = SystemClock.elapsedRealtime();
                if (now >= deadline) {
                    timedSample = true;
                    for (Call call : stageCalls) call.cancel();
                }
                if (now - lastProgress >= PROGRESS_INTERVAL_MS) {
                    long bytes = totalBytes.get();
                    long transferStarted = firstByteAt.get() > 0 ? firstByteAt.get() : started;
                    long currentSpeed = speed(bytes, Math.max(1, now - transferStarted));
                    samples.add(currentSpeed);
                    post(listener, new Progress(label, round - 1, totalRounds, currentSpeed, bytes, budget,
                            workers, now - started, tail(samples)));
                    lastProgress = now;
                }
                if (done.await(PROGRESS_INTERVAL_MS, TimeUnit.MILLISECONDS)) break;
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new CancelledException();
        } finally {
            for (Call call : stageCalls) call.cancel();
            pool.shutdownNow();
        }

        long elapsed = Math.max(1, elapsedSince(started));
        long bytes = totalBytes.get();
        if (bytes <= 0) {
            IOException error = firstError.get();
            throw error == null ? new IOException(label + "未读取到数据") : error;
        }
        int minimumWorkers = Math.max(1, (int) Math.ceil(workers * 0.8d));
        if (workersWithData.get() < minimumWorkers) {
            throw new IOException(label + "仅 " + workersWithData.get() + "/" + workers + " 路读取到数据");
        }
        long firstByteMs = firstByteAt.get() <= 0 ? 0 : Math.max(0, firstByteAt.get() - started);
        long transferElapsed = Math.max(1, elapsed - firstByteMs);
        long bitsPerSecond = speed(bytes, transferElapsed);
        long effectiveBitsPerSecond = speed(bytes, elapsed);
        samples.add(bitsPerSecond);
        post(listener, new Progress(label, round, totalRounds, bitsPerSecond, bytes, budget, workers, elapsed, tail(samples)));
        String method = "直链并发Range（" + workersWithData.get() + "/" + workers + "路有效" + (timedSample ? "，定时取样" : "") + "）";
        return new Measurement(label, bitsPerSecond, effectiveBitsPerSecond, firstByteMs, bytes, elapsed, requestedThreads, samples, method, "");
    }

    private Measurement measureHttp(String label, String url, Map<String, String> headers, long position, long budget,
                                    long timeLimitMs, int threads, int round, int totalRounds, Listener listener) throws IOException, CancelledException {
        long startAt = Math.max(0, position);
        long endAt = safeEnd(startAt, budget);
        Request request = request(url, headers).header("Range", "bytes=" + startAt + "-" + endAt).build();
        long started = SystemClock.elapsedRealtime();
        long lastProgress = started;
        long bytes = 0;
        long firstByteAt = 0;
        boolean partialEof = false;
        boolean timedSample = false;
        List<Long> samples = new ArrayList<>();
        try (Response response = execute(request)) {
            logResponse(label, url, response.code());
            if (!response.isSuccessful()) throw httpError(response, label);
            ResponseBody body = response.body();
            if (body == null) throw new IOException("响应没有数据");
            byte[] buffer = new byte[64 * 1024];
            while (bytes < budget) {
                ensureRunning();
                if (bytes > 0 && elapsedSince(started) >= timeLimitMs) {
                    timedSample = true;
                    break;
                }
                int read;
                try {
                    read = body.byteStream().read(buffer, 0, (int) Math.min(buffer.length, budget - bytes));
                } catch (IOException error) {
                    if (bytes < 4 * MIB) throw error;
                    partialEof = true;
                    break;
                }
                if (read < 0) break;
                if (firstByteAt <= 0) firstByteAt = SystemClock.elapsedRealtime();
                bytes += read;
                long now = SystemClock.elapsedRealtime();
                if (now - lastProgress >= PROGRESS_INTERVAL_MS) {
                    long speed = speed(bytes, Math.max(1, now - firstByteAt));
                    samples.add(speed);
                    post(listener, new Progress(label, round - 1, totalRounds, speed, bytes, budget, threads, now - started, tail(samples)));
                    lastProgress = now;
                }
            }
        }
        long elapsed = Math.max(1, SystemClock.elapsedRealtime() - started);
        if (bytes <= 0) throw new IOException(label + "未读取到数据");
        long firstByteMs = firstByteAt <= 0 ? 0 : Math.max(0, firstByteAt - started);
        long bitsPerSecond = speed(bytes, Math.max(1, elapsed - firstByteMs));
        long effectiveBitsPerSecond = speed(bytes, elapsed);
        samples.add(bitsPerSecond);
        post(listener, new Progress(label, round, totalRounds, bitsPerSecond, bytes, budget, threads, elapsed, tail(samples)));
        String method = timedSample ? "HTTP Range（定时取样）" : partialEof ? "HTTP Range（按已读取有效样本）" : "HTTP Range";
        return new Measurement(label, bitsPerSecond, effectiveBitsPerSecond, firstByteMs, bytes, elapsed, threads, samples, method, "");
    }

    private Measurement measureDataSource(String url, Map<String, String> headers, long position, long budget,
                                          long timeLimitMs, int threads, int round, int totalRounds, Listener listener) throws IOException, CancelledException {
        OkHttpDataSource.Factory factory = new OkHttpDataSource.Factory(client);
        Map<String, String> requestHeaders = new LinkedHashMap<>(headers == null ? Collections.emptyMap() : headers);
        requestHeaders.put("Accept-Encoding", "identity");
        requestHeaders.put("Connection", "close");
        factory.setDefaultRequestProperties(requestHeaders);
        DataSource source = factory.createDataSource();
        activeDataSource = source;
        long started = SystemClock.elapsedRealtime();
        long lastProgress = started;
        long bytes = 0;
        long firstByteAt = 0;
        boolean partialEof = false;
        boolean timedSample = false;
        List<Long> samples = new ArrayList<>();
        try {
            DataSpec spec = new DataSpec.Builder().setUri(Uri.parse(url)).setPosition(Math.max(0, position)).setLength(budget).build();
            source.open(spec);
            byte[] buffer = new byte[64 * 1024];
            while (bytes < budget) {
                ensureRunning();
                if (bytes > 0 && elapsedSince(started) >= timeLimitMs) {
                    timedSample = true;
                    break;
                }
                int read;
                try {
                    read = source.read(buffer, 0, (int) Math.min(buffer.length, budget - bytes));
                } catch (IOException error) {
                    if (bytes < 4 * MIB) throw error;
                    partialEof = true;
                    break;
                }
                if (read < 0) break;
                if (firstByteAt <= 0) firstByteAt = SystemClock.elapsedRealtime();
                bytes += read;
                long now = SystemClock.elapsedRealtime();
                if (now - lastProgress >= PROGRESS_INTERVAL_MS) {
                    long speed = speed(bytes, Math.max(1, now - firstByteAt));
                    samples.add(speed);
                    post(listener, new Progress("App DataSource", round - 1, totalRounds, speed, bytes, budget, threads, now - started, tail(samples)));
                    lastProgress = now;
                }
            }
        } finally {
            source.close();
            if (activeDataSource == source) activeDataSource = null;
        }
        long elapsed = Math.max(1, SystemClock.elapsedRealtime() - started);
        if (bytes <= 0) throw new IOException("App DataSource未读取到数据");
        long firstByteMs = firstByteAt <= 0 ? 0 : Math.max(0, firstByteAt - started);
        long bitsPerSecond = speed(bytes, Math.max(1, elapsed - firstByteMs));
        long effectiveBitsPerSecond = speed(bytes, elapsed);
        samples.add(bitsPerSecond);
        post(listener, new Progress("App DataSource", round, totalRounds, bitsPerSecond, bytes, budget, threads, elapsed, tail(samples)));
        String method = timedSample ? "Media3 OkHttpDataSource（定时取样）" : partialEof ? "Media3 OkHttpDataSource（按已读取有效样本）" : "Media3 OkHttpDataSource";
        return new Measurement("App DataSource", bitsPerSecond, effectiveBitsPerSecond, firstByteMs, bytes, elapsed, threads, samples, method, "");
    }

    private Response execute(Request request) throws IOException, CancelledException {
        ensureRunning();
        Call call = client.newCall(request);
        calls.add(call);
        return call.execute();
    }

    private Request.Builder request(String url, @Nullable Map<String, String> headers) {
        Request.Builder builder = new Request.Builder().url(url)
                .header("Accept", "*/*")
                .header("Accept-Encoding", "identity")
                .header("Connection", "close");
        if (headers != null) {
            Headers safe = Headers.of(headers);
            for (int i = 0; i < safe.size(); i++) {
                String name = safe.name(i);
                if ("range".equalsIgnoreCase(name) || "accept-encoding".equalsIgnoreCase(name)) continue;
                builder.header(name, safe.value(i));
            }
        }
        return builder;
    }

    private void post(Listener listener, Progress progress) {
        App.post(() -> listener.onProgress(progress));
    }

    private void ensureRunning() throws CancelledException {
        if (cancelled.get() || Thread.currentThread().isInterrupted()) throw new CancelledException();
    }

    private void cooldown() throws CancelledException {
        long until = SystemClock.elapsedRealtime() + STAGE_COOLDOWN_MS;
        while (SystemClock.elapsedRealtime() < until) {
            ensureRunning();
            SystemClock.sleep(Math.min(50, until - SystemClock.elapsedRealtime()));
        }
    }

    public static List<Integer> threads(PanBenchmarkPlan.Mode mode, int maxThreads, int configuredThreads) {
        int max = PanBenchmarkPlan.normalizeThreads(maxThreads);
        List<Integer> values = switch (mode == null ? PanBenchmarkPlan.Mode.STANDARD : mode) {
            case QUICK -> PanBenchmarkPlan.sanitizeThreads(List.of(1, max));
            case STANDARD -> PanBenchmarkPlan.sweep(Math.min(8, max), configuredThreads, configuredThreads > 8 && configuredThreads <= max);
            case DEEP -> PanBenchmarkPlan.sweep(max, configuredThreads, true);
        };
        if (!values.contains(max)) {
            values = new ArrayList<>(values);
            values.add(max);
            values.sort(Integer::compareTo);
        }
        return values;
    }

    public static String withThread(String url, int threads) {
        if (url == null || url.isEmpty()) return url;
        String value = String.valueOf(PanBenchmarkPlan.normalizeThreads(threads));
        int query = url.indexOf('?');
        if (query < 0) return url + "?thread=" + value;
        int start = query + 1;
        while (start <= url.length()) {
            int end = url.indexOf('&', start);
            if (end < 0) end = url.length();
            int equals = url.indexOf('=', start);
            if (equals >= start && equals < end && "thread".equals(url.substring(start, equals))) {
                return url.substring(0, equals + 1) + value + url.substring(end);
            }
            if (end == url.length()) break;
            start = end + 1;
        }
        return url + "&thread=" + value;
    }

    public static long requiredBitsPerSecond(long fileBytes, long durationMs, long formatBitsPerSecond) {
        long average = 0;
        if (fileBytes > 0 && durationMs > 0 && fileBytes <= Long.MAX_VALUE / 8_000L) average = fileBytes * 8_000L / durationMs;
        return Math.max(average, Math.max(0, formatBitsPerSecond));
    }

    private static long rangePosition(long length, long positionMs, long durationMs) {
        if (length <= 0 || positionMs <= 0 || durationMs <= 0) return 0;
        double ratio = Math.min(0.95d, Math.max(0d, (double) positionMs / durationMs));
        return Math.max(0, (long) (length * ratio));
    }

    static long isolatedRangePosition(long preferredPosition, long length, long budget, int slot, int totalSlots) {
        if (length <= 0 || budget <= 0) return Math.max(0, preferredPosition);
        long maxStart = Math.max(0, length - Math.min(length, budget));
        if (maxStart <= 0) return 0;
        int count = Math.max(1, totalSlots);
        int index = Math.max(0, Math.min(slot, count - 1));
        return (long) (maxStart * ((index + 1d) / (count + 1d)));
    }

    static long retryRangePosition(long currentPosition, long length, long budget) {
        if (length <= 0 || budget <= 0) return safeEnd(Math.max(0, currentPosition), Math.max(1, budget * 2));
        long maxStart = Math.max(0, length - Math.min(length, budget));
        if (maxStart <= 0) return 0;
        long step = (long) Math.min(maxStart, Math.max((double) budget * 3d, length / 13d));
        if (currentPosition <= maxStart - step) return currentPosition + step;
        return Math.max(0, currentPosition - step);
    }

    private static long totalLength(Response response) {
        String range = response.header("Content-Range", "");
        int slash = range.lastIndexOf('/');
        if (slash >= 0) {
            try {
                return Long.parseLong(range.substring(slash + 1));
            } catch (RuntimeException ignored) {
            }
        }
        return response.body() == null ? -1 : response.body().contentLength();
    }

    private static IOException httpError(Response response, String stage) {
        int code = response.code();
        String hint = switch (code) {
            case 401, 403 -> "鉴权已失效或请求头不完整";
            case 412 -> "请求条件与真实播放请求不一致，已停止本层测试";
            case 416 -> "服务端拒绝Range范围";
            case 429 -> "请求过于频繁，已停止以避免触发风控";
            default -> "HTTP " + code;
        };
        return new IOException(stage + "：" + hint + "（HTTP " + code + "）");
    }

    private static void logResponse(String stage, String url, int code) {
        if (!SpiderDebug.isEnabled()) return;
        HttpUrl parsed = HttpUrl.parse(url);
        SpiderDebug.log("pan-diagnostic", "stage=%s host=%s code=%d", stage, parsed == null ? "" : parsed.host(), code);
    }

    private static long safeEnd(long start, long bytes) {
        long delta = Math.max(0, bytes - 1);
        return start > Long.MAX_VALUE - delta ? Long.MAX_VALUE : start + delta;
    }

    private static long speed(long bytes, long elapsedMs) {
        if (bytes <= 0 || elapsedMs <= 0) return 0;
        double value = bytes * 8_000d / elapsedMs;
        return value >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) value;
    }

    private static long elapsedSince(long startedAt) {
        return Math.max(0, SystemClock.elapsedRealtime() - startedAt);
    }

    private static int safeInt(long value) {
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, value));
    }

    private static List<Long> tail(List<Long> values) {
        int from = Math.max(0, values.size() - 24);
        return new ArrayList<>(values.subList(from, values.size()));
    }

    private static List<Measurement> aggregateByThread(String prefix, List<Integer> threads,
                                                       Map<Integer, List<Measurement>> runs, int expectedRuns) {
        List<Measurement> result = new ArrayList<>();
        for (int thread : threads) {
            List<Measurement> values = runs.getOrDefault(thread, Collections.emptyList());
            String label;
            if (values.isEmpty()) label = prefix + " " + thread + "线程";
            else label = values.get(0).label;
            result.add(aggregate(label, thread, values, expectedRuns));
        }
        return result;
    }

    private static Measurement aggregate(String label, int threads, List<Measurement> runs, int expectedRuns) {
        List<Measurement> successful = new ArrayList<>();
        if (runs != null) for (Measurement value : runs) if (value.successful()) successful.add(value);
        if (successful.isEmpty()) {
            String error = "没有有效样本";
            if (runs != null) for (Measurement value : runs) if (value.error != null && !value.error.isEmpty()) {
                error = value.error;
                break;
            }
            return new Measurement(label, 0, 0, 0, 0, 0, threads, Collections.emptyList(),
                    "0/" + Math.max(1, expectedRuns) + "轮有效", error);
        }
        successful.sort((first, second) -> Long.compare(first.bitsPerSecond, second.bitsPerSecond));
        List<Long> speeds = new ArrayList<>();
        List<Long> effectiveSpeeds = new ArrayList<>();
        List<Long> firstByteTimes = new ArrayList<>();
        List<Long> elapsed = new ArrayList<>();
        long bytes = 0;
        boolean retried = false;
        for (Measurement value : successful) {
            speeds.add(value.bitsPerSecond);
            effectiveSpeeds.add(value.effectiveBitsPerSecond);
            firstByteTimes.add(value.firstByteMs);
            elapsed.add(value.elapsedMs);
            bytes += value.bytes;
            retried |= value.method != null && value.method.contains("瞬态重试");
        }
        long medianSpeed = median(speeds);
        long medianEffectiveSpeed = median(effectiveSpeeds);
        long medianFirstByte = median(firstByteTimes);
        long medianElapsed = median(elapsed);
        String method = successful.size() + "/" + Math.max(expectedRuns, runs == null ? 0 : runs.size()) + "轮有效 · 中位数" + (retried ? " · 含瞬态重试" : "");
        return new Measurement(label, medianSpeed, medianEffectiveSpeed, medianFirstByte, bytes, medianElapsed, threads, speeds, method, "");
    }

    private static long median(List<Long> values) {
        if (values == null || values.isEmpty()) return 0;
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compareTo);
        int middle = sorted.size() / 2;
        if ((sorted.size() & 1) == 1) return sorted.get(middle);
        return sorted.get(middle - 1) / 2 + sorted.get(middle) / 2;
    }

    private static Measurement findByThreads(List<Measurement> values, int threads) {
        if (values == null) return null;
        for (Measurement value : values) if (value.threads == threads) return value;
        return null;
    }

    private static PanDiagnosticVerdict.Confidence evidenceConfidence(PanBenchmarkPlan.Mode mode,
                                                                      List<Measurement> upstreamRuns,
                                                                      Map<Integer, List<Measurement>> directRuns,
                                                                      Map<Integer, List<Measurement>> proxyRuns,
                                                                      List<Measurement> dataSourceRuns) {
        int repeats = PanBenchmarkPlan.repeats(mode);
        if (repeats <= 1) return PanDiagnosticVerdict.Confidence.LOW;
        List<List<Measurement>> series = new ArrayList<>();
        series.add(upstreamRuns);
        series.addAll(directRuns.values());
        series.addAll(proxyRuns.values());
        series.add(dataSourceRuns);
        double worstRatio = 1d;
        boolean retried = false;
        for (List<Measurement> runs : series) {
            if (runs == null || runs.size() < repeats) return PanDiagnosticVerdict.Confidence.LOW;
            long min = Long.MAX_VALUE;
            long max = 0;
            int successful = 0;
            for (Measurement value : runs) {
                if (!value.successful()) continue;
                successful++;
                retried |= value.method != null && value.method.contains("瞬态重试");
                min = Math.min(min, value.bitsPerSecond);
                max = Math.max(max, value.bitsPerSecond);
            }
            if (successful < repeats || min <= 0) return PanDiagnosticVerdict.Confidence.LOW;
            worstRatio = Math.max(worstRatio, (double) max / min);
        }
        if (repeats >= 3 && worstRatio <= 1.35d) return retried ? PanDiagnosticVerdict.Confidence.MEDIUM : PanDiagnosticVerdict.Confidence.HIGH;
        if (worstRatio <= 1.60d) return PanDiagnosticVerdict.Confidence.MEDIUM;
        return PanDiagnosticVerdict.Confidence.LOW;
    }

    private static Measurement best(List<Measurement> values) {
        Measurement best = null;
        for (Measurement value : values) if (value.bitsPerSecond > 0 && (best == null || value.bitsPerSecond > best.bitsPerSecond)) best = value;
        return best;
    }

    private static Measurement failed(String label, int threads, long elapsedMs, IOException error) {
        return new Measurement(label, 0, 0, 0, 0, elapsedMs, threads, Collections.emptyList(), "未完成", readableError(error));
    }

    private static boolean isTransientEof(Measurement value) {
        if (value == null || value.successful() || value.error == null) return false;
        String error = value.error.toLowerCase(Locale.ROOT);
        return error.contains("unexpected end of stream") || error.contains("premature eof");
    }

    private static Measurement markRetried(Measurement value) {
        if (value == null) return null;
        String method = value.method == null || value.method.isEmpty() ? "瞬态重试1次" : value.method + " · 瞬态重试1次";
        return new Measurement(value.label, value.bitsPerSecond, value.effectiveBitsPerSecond, value.firstByteMs,
                value.bytes, value.elapsedMs, value.threads, value.samples, method, value.error);
    }

    private static boolean sameUrl(String first, String second) {
        return first != null && first.equals(second);
    }

    private static String readableError(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) message = error.getClass().getSimpleName();
        return message.replaceAll("https?://\\S+", "[地址已脱敏]");
    }

    public interface Listener {
        void onProgress(Progress progress);

        void onComplete(Report report);

        void onCancelled();

        void onError(String message);
    }

    public record RequestConfig(String playbackUrl, Map<String, String> playbackHeaders, PanBenchmarkPlan.Mode mode,
                                List<Integer> threadValues, long positionMs, long durationMs, long formatBitsPerSecond,
                                int rebufferCount, long rebufferTotalMs, long droppedFrames) {
        public RequestConfig {
            playbackHeaders = playbackHeaders == null ? Collections.emptyMap() : new LinkedHashMap<>(playbackHeaders);
            mode = mode == null ? PanBenchmarkPlan.Mode.STANDARD : mode;
            threadValues = PanBenchmarkPlan.sanitizeThreads(threadValues);
        }
    }

    public record Progress(String stage, int completedRounds, int totalRounds, long bitsPerSecond, long bytes,
                           long budgetBytes, int threads, long elapsedMs, List<Long> samples) {
    }

    public record Measurement(String label, long bitsPerSecond, long effectiveBitsPerSecond, long firstByteMs,
                              long bytes, long elapsedMs, int threads, List<Long> samples, String method, String error) {
        public double megabitsPerSecond() {
            return bitsPerSecond / 1_000_000d;
        }

        public boolean successful() {
            return bitsPerSecond > 0 && (error == null || error.isEmpty());
        }
    }

    public record Report(PanEndpoint endpoint, long fileBytes, long requiredBitsPerSecond, Measurement upstream,
                         List<Measurement> directComparisons, List<Measurement> proxies, Measurement dataSource,
                         PanDiagnosticVerdict.Result verdict, PanDiagnosticVerdict.Confidence evidenceConfidence,
                         int repeats, int rebufferCount, long rebufferTotalMs, long droppedFrames,
                         PanBenchmarkPlan.Mode mode) {

        public Measurement bestProxy() {
            return best(proxies);
        }

        public Measurement appProxy() {
            return findByThreads(proxies, dataSource.threads);
        }

        public Measurement directForThreads(int threads) {
            Measurement value = findByThreads(directComparisons, threads);
            return value == null ? upstream : value;
        }

        public Measurement primaryDirectComparison() {
            if (directComparisons == null) return null;
            for (Measurement value : directComparisons) if (value.threads > 1) return value;
            return directComparisons.isEmpty() ? null : directComparisons.get(0);
        }

        public String redactedText() {
            StringBuilder text = new StringBuilder();
            text.append("播放链路诊断报告\n");
            text.append("资源来源：").append(endpoint.provider().label()).append('\n');
            text.append("播放域名：").append(endpoint.playbackHost()).append('\n');
            text.append("上游域名：").append(endpoint.upstreamHost()).append('\n');
            text.append("资源大小：").append(formatBytes(fileBytes)).append('\n');
            text.append("资源需求：").append(formatMbps(requiredBitsPerSecond)).append('\n');
            text.append("直链单连接基准：").append(measurementText(upstream)).append('\n');
            for (Measurement item : directComparisons) if (item.threads > 1) text.append(item.label).append("：").append(measurementText(item)).append('\n');
            for (Measurement item : proxies) text.append("Go代理 ").append(item.threads).append("线程：").append(measurementText(item)).append('\n');
            text.append("App DataSource：").append(measurementText(dataSource)).append('\n');
            text.append("播放观察：重缓冲 ").append(rebufferCount).append(" 次 / ").append(rebufferTotalMs).append(" ms，掉帧 ").append(droppedFrames).append('\n');
            text.append("采样：").append(repeats).append("轮，中位数；证据稳定性 ").append(confidenceText(evidenceConfidence)).append('\n');
            text.append("结论：").append(verdict.reason()).append("（").append(confidenceText(verdict.confidence())).append("置信度）\n");
            text.append("说明：各网络轮次使用相隔较远的独立Range，降低本地Go缓存和预取对后续结果的污染；报告不包含URL、Cookie、Token或请求头内容；公共网络未借助第三方跨地域测速点。");
            return text.toString();
        }
    }

    private record Probe(long length, int statusCode, String acceptRanges) {
    }

    private record AppPhaseInput(PanEndpoint endpoint, RequestConfig config, Listener listener,
                                 long preferredPosition, long length, long required, long timeLimitMs,
                                 int totalRounds, int startRound, int repeats, boolean proxied, Measurement proxy,
                                 List<Measurement> proxyMeasurements, Map<Integer, List<Measurement>> proxyRuns) {
    }

    private record AppPhaseResult(int round, Measurement dataSource, Measurement proxy, List<Measurement> dataSourceRuns) {
    }

    private static final class CancelledException extends Exception {
    }

    public static String formatMbps(long bitsPerSecond) {
        return bitsPerSecond <= 0 ? "未知" : String.format(Locale.getDefault(), "%.2f Mbps", bitsPerSecond / 1_000_000d);
    }

    public static String formatBytes(long bytes) {
        if (bytes < 0) return "未知";
        if (bytes >= 1024L * MIB) return String.format(Locale.getDefault(), "%.2f GiB", bytes / (1024d * MIB));
        if (bytes >= MIB) return String.format(Locale.getDefault(), "%.1f MiB", bytes / (double) MIB);
        return String.format(Locale.getDefault(), "%.1f KiB", bytes / 1024d);
    }

    public static String formatDuration(long elapsedMs) {
        long totalSeconds = Math.max(0, Math.round(elapsedMs / 1000d));
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return minutes <= 0 ? seconds + "秒" : String.format(Locale.getDefault(), "%d分%02d秒", minutes, seconds);
    }

    public static String formatLatency(long elapsedMs) {
        if (elapsedMs < 1000) return Math.max(0, elapsedMs) + "ms";
        return String.format(Locale.getDefault(), "%.2f秒", elapsedMs / 1000d);
    }

    public static String confidenceText(PanDiagnosticVerdict.Confidence confidence) {
        return switch (confidence) {
            case HIGH -> "高";
            case MEDIUM -> "中";
            case LOW -> "低";
        };
    }

    private static String measurementText(Measurement value) {
        String elapsed = "，用时 " + formatDuration(value.elapsedMs);
        String timing = value.successful() ? "，首字节 " + formatLatency(value.firstByteMs) + "，含启动 " + formatMbps(value.effectiveBitsPerSecond) : "";
        String method = value.method == null || value.method.isEmpty() ? "" : "，" + value.method;
        return value.successful() ? formatMbps(value.bitsPerSecond) + timing + elapsed + method : "未完成（" + value.error + elapsed + method + "）";
    }
}
