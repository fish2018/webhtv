package com.fongmi.android.tv.utils;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;

import com.fongmi.android.tv.App;
import com.github.catvod.Proxy;

public class NsdDeviceDiscovery {

    private static final String SERVICE_TYPE = "_webhtv._tcp.";

    private static NsdManager.RegistrationListener registration;

    private final Listener listener;
    private NsdManager.DiscoveryListener discovery;
    private WifiManager.MulticastLock lock;

    public NsdDeviceDiscovery(Listener listener) {
        this.listener = listener;
    }

    public static synchronized void register() {
        if (registration != null || Proxy.getPort() <= 0) return;
        NsdServiceInfo service = new NsdServiceInfo();
        service.setServiceName("WebHTV-" + Util.getDeviceName());
        service.setServiceType(SERVICE_TYPE);
        service.setPort(Proxy.getPort());
        registration = new NsdManager.RegistrationListener() {
            @Override
            public void onServiceRegistered(NsdServiceInfo serviceInfo) {
            }

            @Override
            public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                registration = null;
            }

            @Override
            public void onServiceUnregistered(NsdServiceInfo serviceInfo) {
                registration = null;
            }

            @Override
            public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
            }
        };
        getManager().registerService(service, NsdManager.PROTOCOL_DNS_SD, registration);
    }

    public void start() {
        stop();
        acquireLock();
        discovery = new NsdManager.DiscoveryListener() {
            @Override
            public void onDiscoveryStarted(String serviceType) {
            }

            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                if (SERVICE_TYPE.equals(serviceInfo.getServiceType())) resolve(serviceInfo);
            }

            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                stop();
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
            }
        };
        getManager().discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discovery);
    }

    public void stop() {
        if (discovery != null) {
            try {
                getManager().stopServiceDiscovery(discovery);
            } catch (Exception ignored) {
            }
            discovery = null;
        }
        releaseLock();
    }

    private void resolve(NsdServiceInfo serviceInfo) {
        getManager().resolveService(serviceInfo, new NsdManager.ResolveListener() {
            @Override
            public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
            }

            @Override
            public void onServiceResolved(NsdServiceInfo serviceInfo) {
                if (serviceInfo.getHost() == null || serviceInfo.getPort() <= 0) return;
                String url = "http://" + serviceInfo.getHost().getHostAddress() + ":" + serviceInfo.getPort();
                App.post(() -> listener.onServiceFound(url));
            }
        });
    }

    private void acquireLock() {
        WifiManager manager = (WifiManager) App.get().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (manager == null) return;
        lock = manager.createMulticastLock("webhtv-nsd");
        lock.setReferenceCounted(false);
        lock.acquire();
    }

    private void releaseLock() {
        if (lock == null) return;
        try {
            if (lock.isHeld()) lock.release();
        } catch (Exception ignored) {
        }
        lock = null;
    }

    private static NsdManager getManager() {
        return (NsdManager) App.get().getSystemService(Context.NSD_SERVICE);
    }

    public interface Listener {

        void onServiceFound(String url);
    }
}
