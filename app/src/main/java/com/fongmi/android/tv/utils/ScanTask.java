package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.server.Server;
import com.github.catvod.net.OkHttp;
import com.github.catvod.Proxy;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import okhttp3.OkHttpClient;
import okhttp3.Response;

public class ScanTask {

    private static final int PORT_START = 9978;
    private static final int PORT_END = 9998;
    private static final int PARALLELISM = 64;

    private final CopyOnWriteArrayList<Future<?>> future;
    private final ExecutorService executor;
    private final OkHttpClient client;
    private Listener listener;

    public ScanTask(Listener listener) {
        this.client = OkHttp.client(500);
        this.executor = Executors.newFixedThreadPool(PARALLELISM);
        this.future = new CopyOnWriteArrayList<>();
        this.listener = listener;
    }

    public void start() {
        Server.get().start();
        Task.execute(() -> {
            List<String> localIps = getLocalIps();
            List<String> localUrls = getLocalUrls(localIps);
            List<Device> devices = Device.getAll().stream().filter(device -> device.isApp() && !Device.get().equals(device)).toList();
            List<String> bases = getBase(localIps);
            List<String> urls = new ArrayList<>(getUrl(bases, PORT_START));
            run(devices, urls, true, localUrls);
            run(List.of(), getUrl(bases, PORT_START + 1, PORT_END), false, localUrls);
        });
    }

    public void start(String url) {
        Server.get().start();
        Task.execute(() -> submit(url, null, null, getLocalUrls(getLocalIps())));
    }

    public void stop() {
        listener = null;
        OkHttp.cancel(client, "scan");
        future.forEach(f -> f.cancel(true));
        future.clear();
        executor.shutdownNow();
    }

    private void run(List<Device> devices, List<String> urls, boolean notifyFinish, List<String> localUrls) {
        AtomicInteger count = new AtomicInteger(devices.size() + urls.size());
        if (notifyFinish && count.get() == 0) finish();
        for (Device device : devices) submit(device.getIp(), device, notifyFinish ? count : null, localUrls);
        for (String url : urls) submit(url, null, notifyFinish ? count : null, localUrls);
    }

    private void submit(String url, Device source, AtomicInteger count, List<String> localUrls) {
        future.add(executor.submit(() -> {
            try {
                findDevice(url, source, localUrls);
            } finally {
                if (count != null && count.decrementAndGet() == 0) finish();
            }
        }));
    }

    private List<String> getBase(List<String> ips) {
        return ips.stream().map(ip -> "http://" + ip.substring(0, ip.lastIndexOf(".") + 1)).distinct().toList();
    }

    private List<String> getLocalUrls(List<String> ips) {
        return ips.stream().map(ip -> "http://" + ip + ":" + Proxy.getPort()).toList();
    }

    private List<String> getUrl(List<String> bases, int port) {
        return bases.stream().flatMap(base -> IntStream.range(1, 256).mapToObj(i -> base + i + ":" + port)).distinct().toList();
    }

    private List<String> getUrl(List<String> bases, int startPort, int endPort) {
        return IntStream.rangeClosed(startPort, endPort).boxed().flatMap(port -> getUrl(bases, port).stream()).distinct().toList();
    }

    private List<String> getLocalIps() {
        LinkedHashSet<String> ips = new LinkedHashSet<>();
        try {
            for (var en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface nif = en.nextElement();
                if (!nif.isUp() || nif.isLoopback()) continue;
                for (var addresses = nif.getInetAddresses(); addresses.hasMoreElements(); ) {
                    InetAddress addr = addresses.nextElement();
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) ips.add(addr.getHostAddress());
                }
            }
        } catch (Exception ignored) {
        }
        return new ArrayList<>(ips);
    }

    private boolean isLocal(String url, List<String> localUrls) {
        return localUrls.contains(url) || url.equals(Server.get().getAddress(true));
    }

    private void findDevice(String url, Device source, List<String> localUrls) {
        if (isLocal(url, localUrls)) return;
        try (Response res = OkHttp.newCall(client, url.concat("/device"), "scan").execute()) {
            if (!res.isSuccessful() || res.body() == null) throw new IllegalStateException();
            Device device = Device.objectFrom(res.body().string());
            if (device != null) device.setIp(url);
            if (source != null && (device == null || !source.equals(device))) lost(source);
            if (Device.get().equals(device)) return;
            if (device != null) App.post(() -> {
                if (listener != null) listener.onFind(device.save());
            });
        } catch (Exception ignored) {
            if (source != null) lost(source);
        }
    }

    private void lost(Device device) {
        Device.delete(device);
        App.post(() -> {
            if (listener != null) listener.onLost(device);
        });
    }

    private void finish() {
        App.post(() -> {
            if (listener != null) listener.onFinish();
        });
    }

    public interface Listener {

        void onFind(Device device);

        default void onLost(Device device) {
        }

        default void onFinish() {
        }
    }
}
