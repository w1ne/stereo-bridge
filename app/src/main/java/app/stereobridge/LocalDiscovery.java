package app.stereobridge;

import android.content.Context;
import android.net.nsd.*;
import android.os.*;
import java.net.*;
import java.util.*;

/** Discovers only ADB advertisements belonging to this phone. */
final class LocalDiscovery {
  interface Found {
    void port(int port);
  }

  static boolean local(InetAddress address) {
    if (address == null) return false;
    if (address.isLoopbackAddress()) return true;
    try {
      Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
      while (interfaces.hasMoreElements()) {
        Enumeration<InetAddress> addresses = interfaces.nextElement().getInetAddresses();
        while (addresses.hasMoreElements())
          if (address.equals(addresses.nextElement())) return true;
      }
    } catch (Exception ignored) {
    }
    return false;
  }

  static void discover(Context context, String type, Found found) {
    Context c = context.getApplicationContext();
    NsdManager nsd = c.getSystemService(NsdManager.class);
    if (nsd == null) return;
    Handler handler = new Handler(Looper.getMainLooper());
    NsdManager.DiscoveryListener listener =
        new NsdManager.DiscoveryListener() {
          boolean resolving;

          public void onDiscoveryStarted(String s) {}

          public void onServiceFound(NsdServiceInfo info) {
            if (resolving) return;
            resolving = true;
            try {
              nsd.resolveService(
                  info,
                  new NsdManager.ResolveListener() {
                    public void onResolveFailed(NsdServiceInfo i, int error) {
                      resolving = false;
                    }

                    public void onServiceResolved(NsdServiceInfo i) {
                      resolving = false;
                      if (local(i.getHost()) && i.getPort() > 0 && i.getPort() <= 65535)
                        handler.post(() -> found.port(i.getPort()));
                    }
                  });
            } catch (Exception e) {
              resolving = false;
            }
          }

          public void onServiceLost(NsdServiceInfo i) {}

          public void onDiscoveryStopped(String s) {}

          public void onStartDiscoveryFailed(String s, int e) {}

          public void onStopDiscoveryFailed(String s, int e) {}
        };
    try {
      nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener);
      handler.postDelayed(
          () -> {
            try {
              nsd.stopServiceDiscovery(listener);
            } catch (Exception ignored) {
            }
          },
          25000);
    } catch (Exception ignored) {
    }
  }

  static int find(Context c, String type, long timeout) throws InterruptedException {
    java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.atomic.AtomicInteger value =
        new java.util.concurrent.atomic.AtomicInteger();
    discover(
        c,
        type,
        p -> {
          value.set(p);
          ready.countDown();
        });
    ready.await(timeout, java.util.concurrent.TimeUnit.MILLISECONDS);
    return value.get();
  }

  static void refresh(Context c) {
    discover(
        c,
        "_adb-tls-connect._tcp",
        port -> new BridgeSettings(c).set("adb_port", Integer.toString(port)));
  }
}
