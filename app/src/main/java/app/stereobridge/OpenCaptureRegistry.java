package app.stereobridge;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Process lifetime leases. A new registry after process death permits durable journal recovery. */
final class OpenCaptureRegistry {
  private final Map<String, Integer> open = new HashMap<>();
  synchronized Lease open(String uri) {
    Objects.requireNonNull(uri);
    open.put(uri, open.getOrDefault(uri, 0) + 1);
    return new Lease(uri);
  }
  synchronized boolean isOpen(String uri) { return open.containsKey(uri); }
  final class Lease implements AutoCloseable {
    private final String uri;
    private boolean closed;
    private Lease(String uri) { this.uri = uri; }
    @Override public void close() {
      synchronized (OpenCaptureRegistry.this) {
        if (closed) return;
        closed = true;
        int count = open.get(uri);
        if (count == 1) open.remove(uri);
        else open.put(uri, count - 1);
      }
    }
  }
}
