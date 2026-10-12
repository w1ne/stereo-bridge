package io.github.muntashirakon.adb;

import static org.junit.Assert.*;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.security.*;
import java.security.cert.Certificate;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public class AdbOpenTimeoutTest {
  private AdbConnection connection(ServerSocket server) throws Exception {
    PrivateKey key =
        new PrivateKey() {
          public String getAlgorithm() {
            return "RSA";
          }

          public String getFormat() {
            return "PKCS#8";
          }

          public byte[] getEncoded() {
            return new byte[0];
          }

          public void destroy() {}
        };
    Certificate certificate =
        new Certificate("X.509") {
          public byte[] getEncoded() {
            return new byte[0];
          }

          public void verify(PublicKey k) {}

          public void verify(PublicKey k, String p) {}

          public PublicKey getPublicKey() {
            return null;
          }

          public String toString() {
            return "test";
          }
        };
    AdbConnection connection =
        AdbConnection.create("127.0.0.1", server.getLocalPort(), key, certificate, 30);
    for (String name : new String[] {"mConnectAttempted", "mConnectionEstablished"}) {
      Field field = AdbConnection.class.getDeclaredField(name);
      field.setAccessible(true);
      field.set(connection, true);
    }
    return connection;
  }

  private AdbStream open(AdbConnection connection, long millis) throws Throwable {
    Method method;
    try {
      method = AdbConnection.class.getMethod("open", String.class, long.class, TimeUnit.class);
    } catch (NoSuchMethodException e) {
      fail("ADB stream open must have an explicit deadline");
      return null;
    }
    try {
      return (AdbStream) method.invoke(connection, "shell:test", millis, TimeUnit.MILLISECONDS);
    } catch (InvocationTargetException e) {
      throw e.getCause();
    }
  }

  @SuppressWarnings("unchecked")
  private Map<Integer, AdbStream> streams(AdbConnection c) throws Exception {
    Field field = AdbConnection.class.getDeclaredField("mOpenedStreams");
    field.setAccessible(true);
    return (Map<Integer, AdbStream>) field.get(c);
  }

  @Test
  public void unansweredOpenTimesOutAndRemovesPendingStream() throws Throwable {
    try (ServerSocket server = new ServerSocket(0);
        AdbConnection c = connection(server);
        Socket peer = server.accept()) {
      long started = System.nanoTime();
      try {
        open(c, 100);
        fail("Unanswered open succeeded");
      } catch (SocketTimeoutException expected) {
      }
      assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 2000);
      assertTrue(streams(c).isEmpty());
    }
  }

  @Test
  public void interruptedOpenRemovesPendingStream() throws Throwable {
    try (ServerSocket server = new ServerSocket(0);
        AdbConnection c = connection(server);
        Socket peer = server.accept()) {
      AtomicReference<Throwable> failure = new AtomicReference<>();
      Thread opener =
          new Thread(
              () -> {
                try {
                  open(c, 10000);
                } catch (Throwable e) {
                  failure.set(e);
                }
              });
      opener.start();
      peer.setSoTimeout(2000);
      assertTrue(peer.getInputStream().read() >= 0);
      opener.interrupt();
      opener.join(2000);
      assertFalse(opener.isAlive());
      assertTrue(failure.get() instanceof InterruptedException);
      assertTrue(streams(c).isEmpty());
    }
  }

  @Test
  public void spuriousNotifyCannotCompleteOpen() throws Throwable {
    try (ServerSocket server = new ServerSocket(0);
        AdbConnection c = connection(server);
        Socket peer = server.accept()) {
      AtomicReference<Throwable> failure = new AtomicReference<>();
      Thread opener =
          new Thread(
              () -> {
                try {
                  open(c, 200);
                } catch (Throwable e) {
                  failure.set(e);
                }
              });
      opener.start();
      peer.setSoTimeout(2000);
      assertTrue(peer.getInputStream().read() >= 0);
      AdbStream stream = streams(c).values().iterator().next();
      synchronized (stream) {
        stream.notifyAll();
      }
      opener.join(2000);
      assertFalse(opener.isAlive());
      assertTrue(failure.get() instanceof SocketTimeoutException);
    }
  }

  @Test
  public void defaultConnectWaitsForPendingHandshake() throws Throwable {
    try (ServerSocket server = new ServerSocket(0);
        AdbConnection c = connection(server);
        Socket peer = server.accept()) {
      for (String name : new String[] {"mConnectAttempted", "mConnectionEstablished"}) {
        Field field = AdbConnection.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(c, false);
      }
      AtomicReference<Throwable> failure = new AtomicReference<>();
      AtomicReference<Boolean> result = new AtomicReference<>();
      Thread connector =
          new Thread(
              () -> {
                try {
                  result.set(c.connect());
                } catch (Throwable e) {
                  failure.set(e);
                }
              });
      connector.start();
      peer.setSoTimeout(2000);
      assertTrue(peer.getInputStream().read() >= 0);
      peer.getOutputStream().write(AdbProtocol.generateConnect(30));
      connector.join(2000);
      assertFalse("Default connect must finish on daemon handshake", connector.isAlive());
      assertNull(failure.get());
      assertEquals(Boolean.TRUE, result.get());
    }
  }

  @Test
  public void peerCloseDrainsFinalDiagnosticPayloadThenReturnsEof() throws Throwable {
    try (ServerSocket server = new ServerSocket(0);
        AdbConnection c = connection(server);
        Socket peer = server.accept()) {
      AdbStream stream = new AdbStream(c, 1);
      stream.addPayload(new byte[] {1, 2, 3, 4});
      stream.notifyClose(true);
      InputStream input = stream.openInputStream();
      byte[] result = new byte[2];
      assertEquals(2, input.read(result));
      assertArrayEquals(new byte[] {1, 2}, result);
      assertEquals(2, input.read(result));
      assertArrayEquals(new byte[] {3, 4}, result);
      AtomicReference<Integer> eof = new AtomicReference<>();
      AtomicReference<Throwable> error = new AtomicReference<>();
      Thread reader =
          new Thread(
              () -> {
                try {
                  eof.set(input.read());
                } catch (Throwable e) {
                  error.set(e);
                }
              });
      reader.start();
      reader.join(500);
      boolean blocked = reader.isAlive();
      if (blocked) {
        reader.interrupt();
        reader.join(1000);
      }
      assertFalse("Final CLSE must not leave EOF read blocked", blocked);
      assertNull(error.get());
      assertEquals(Integer.valueOf(-1), eof.get());
      assertEquals(-1, input.read());
    }
  }

  @Test
  public void peerCloseWhilePayloadPartlyBufferedPreservesRemainingBytes() throws Throwable {
    try (ServerSocket server = new ServerSocket(0);
        AdbConnection c = connection(server);
        Socket peer = server.accept()) {
      AdbStream stream = new AdbStream(c, 1);
      stream.addPayload(new byte[] {1, 2, 3, 4});
      InputStream input = stream.openInputStream();
      byte[] result = new byte[2];
      assertEquals(2, input.read(result));
      stream.notifyClose(true);
      assertEquals("Buffered bytes must survive CLSE", 2, input.read(result));
      assertArrayEquals(new byte[] {3, 4}, result);
      assertEquals(-1, input.read());
    }
  }
}
