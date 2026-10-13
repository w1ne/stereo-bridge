package app.stereobridge;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaCodec;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.util.*;
import android.view.Surface;
import app.stereobridge.mic.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns camera and direct microphone inputs. No privileged routing or network access. */
public final class OfflineRecorderService extends Service {
  public static final String START = "app.stereobridge.RECORD",
      STOP = "app.stereobridge.STOP_RECORDING";
  static final OfflineSessionState state = new OfflineSessionState();
  static volatile int leftLevel, rightLevel;
  static volatile long startedAt, drops;
  private final LocalBinder binder = new LocalBinder();
  private final AtomicBoolean active = new AtomicBoolean();
  private final TimedAudioFrameBuffer left = new TimedAudioFrameBuffer(),
      right = new TimedAudioFrameBuffer();
  private final List<MicSource> microphones = new ArrayList<>();
  private HandlerThread cameraThread;
  private Handler cameraHandler;
  private Handler main;
  private CameraDevice camera;
  private CameraCaptureSession cameraSession;
  private volatile Surface preview;
  private SurfaceTexture previewTexture;
  private final Object previewLock = new Object();
  private final Map<SurfaceTexture, TextureOwner> textureOwners = new IdentityHashMap<>();
  private PreviewLease previewLease;
  private int sessionUses;
  private final Set<CameraDevice> closingDevices =
      Collections.newSetFromMap(new IdentityHashMap<>());
  private CountDownLatch cameraCleanup;

  private void closeDevice(CameraDevice device) {
    closingDevices.add(device);
    device.close();
  }

  private void completeCameraCleanup() {
    if (cameraCleanup != null
        && !opening
        && camera == null
        && closingDevices.isEmpty()
        && sessionUses == 0) {
      CountDownLatch completed = cameraCleanup;
      cameraCleanup = null;
      completed.countDown();
    }
  }

  private final class TextureOwner {
    final SurfaceTexture texture;
    final List<PreviewLease> leases = new ArrayList<>();
    boolean destroyRequested, released;

    TextureOwner(SurfaceTexture texture) {
      this.texture = texture;
    }
  }

  private final class PreviewLease {
    final TextureOwner owner;
    final Surface surface;
    int uses;
    boolean retired, released;

    PreviewLease(TextureOwner owner) {
      this.owner = owner;
      surface = new Surface(owner.texture);
      owner.leases.add(this);
    }
  }

  private void releaseRetired(PreviewLease lease) {
    if (lease.retired && lease.uses == 0 && !lease.released) {
      lease.surface.release();
      lease.released = true;
    }
    releaseTexture(lease.owner);
  }

  private void releaseTexture(TextureOwner owner) {
    if (!owner.destroyRequested || owner.released) return;
    for (PreviewLease lease : owner.leases) if (!lease.released) return;
    owner.texture.release();
    owner.released = true;
    textureOwners.remove(owner.texture);
  }

  private void requestTextureDestruction(SurfaceTexture texture) {
    synchronized (previewLock) {
      TextureOwner owner = textureOwners.get(texture);
      if (owner == null) {
        texture.release();
        return;
      }
      owner.destroyRequested = true;
      releaseTexture(owner);
    }
  }

  private void maybeQuitCameraThread() {
    completeCameraCleanup();
    if (destroyed
        && worker == null
        && !opening
        && camera == null
        && closingDevices.isEmpty()
        && sessionUses == 0) cameraThread.quitSafely();
  }

  private boolean realtimeSensor;
  private OfflineVideoEncoder encoder;
  private volatile Thread worker;
  private volatile String failure;
  private CountDownLatch cameraReady;
  private String cameraId;
  private volatile Size size;
  private volatile int rotation;
  private Range<Integer> fps;
  private boolean opening;
  private int configuration;
  private PowerManager.WakeLock wakeLock;
  private BridgeSettings settings;
  private volatile boolean synthetic;
  private volatile boolean destroyed;

  public final class LocalBinder extends Binder {
    public int[] previewInfo() {
      Size current = size;
      return current == null
          ? new int[] {1920, 1080, 90}
          : new int[] {current.getWidth(), current.getHeight(), rotation};
    }

    public void attachPreview(SurfaceTexture texture) {
      cameraHandler.post(
          () -> {
            if (destroyed) return;
            if (previewTexture == texture && preview != null) {
              ensureCamera();
              return;
            }
            retirePreview(false);
            try {
              chooseCamera(getSystemService(CameraManager.class));
              texture.setDefaultBufferSize(size.getWidth(), size.getHeight());
              synchronized (previewLock) {
                TextureOwner owner = textureOwners.get(texture);
                if (owner == null) {
                  owner = new TextureOwner(texture);
                  textureOwners.put(texture, owner);
                }
                if (owner.destroyRequested) return;
                previewLease = new PreviewLease(owner);
                preview = previewLease.surface;
                previewTexture = texture;
              }
              ensureCamera();
            } catch (Exception e) {
              cameraFailure(message(e));
            }
          });
    }

    public void detachPreview(SurfaceTexture texture, boolean releaseTexture) {
      if (texture == null) return;
      boolean posted =
          cameraHandler.post(
              () -> {
                if (previewTexture == texture) {
                  retirePreview(releaseTexture);
                  if (active.get()) configure();
                  else closeCamera();
                } else if (releaseTexture) requestTextureDestruction(texture);
                maybeQuitCameraThread();
              });
      if (!posted && releaseTexture) requestTextureDestruction(texture);
    }
  }

  @Override
  public IBinder onBind(Intent intent) {
    return binder;
  }

  @Override
  public void onCreate() {
    super.onCreate();
    main = new Handler(Looper.getMainLooper());
    settings = new BridgeSettings(this);
    state.restoreLast(settings.get("last_video_uri", ""));
    cameraThread = new HandlerThread("offline-camera");
    cameraThread.start();
    cameraHandler = new Handler(cameraThread.getLooper());
    NotificationManager nm = getSystemService(NotificationManager.class);
    nm.createNotificationChannel(
        new NotificationChannel(
            "recording", "Video recording", NotificationManager.IMPORTANCE_LOW));
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int id) {
    if (intent == null || STOP.equals(intent.getAction())) {
      stopRecording();
      return START_NOT_STICKY;
    }
    if (!START.equals(intent.getAction()) || worker != null || !state.begin())
      return START_NOT_STICKY;
    failure = null;
    leftLevel = rightLevel = 0;
    drops = 0;
    left.clear();
    right.clear();
    synthetic = BuildConfig.DEBUG && intent.getBooleanExtra("synthetic", false);
    try {
      for (String p :
          new String[] {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.BLUETOOTH_CONNECT
          })
        if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED)
          throw new SecurityException("Allow camera, microphone and nearby devices access first.");
      startForeground(
          11,
          notification("Connecting microphones…"),
          android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
              | android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
      active.set(true);
      wakeLock =
          getSystemService(PowerManager.class)
              .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "StereoBridge:video");
      wakeLock.acquire(6 * 60 * 60 * 1000L);
      worker = new Thread(this::record, "offline-recording");
      worker.start();
    } catch (Exception e) {
      state.fail(message(e));
      active.set(false);
      stopForeground(STOP_FOREGROUND_REMOVE);
      stopSelf();
    }
    return START_NOT_STICKY;
  }

  private void record() {
    File take = null;
    boolean videoStarted = false;
    OfflineVideoEncoder current = null;
    try {
      connectMicrophones();
      TimedAudioFrameBuffer.Frame firstLeft = left.poll(20, TimeUnit.SECONDS),
          firstRight = right.poll(20, TimeUnit.SECONDS);
      checkActive();
      if (firstLeft == null || firstRight == null)
        throw new IOException(
            "Both microphones must send audio before recording. Check power and Bluetooth"
                + " pairing.");
      CountDownLatch prepared = new CountDownLatch(1);
      cameraReady = prepared;
      cameraHandler.post(this::ensureCamera);
      if (!prepared.await(8, TimeUnit.SECONDS))
        throw new IOException(
            "Camera did not become ready. Close other camera apps and try again.");
      checkActive();
      if (failure != null) throw new IOException(failure);
      File directory = new File(getFilesDir(), "takes");
      if (!directory.isDirectory() && !directory.mkdirs())
        throw new IOException("Could not create recording storage.");
      take = File.createTempFile("take-", ".mp4", directory);
      current = new OfflineVideoEncoder(take, size.getWidth(), size.getHeight(), rotation);
      long epoch = SystemClock.elapsedRealtimeNanos();
      current.start(epoch);
      synchronized (this) {
        checkActive();
        encoder = current;
      }
      CountDownLatch firstVideo = new CountDownLatch(1);
      cameraReady = firstVideo;
      cameraHandler.post(this::configure);
      if (!firstVideo.await(8, TimeUnit.SECONDS))
        throw new IOException("Camera did not start video. Nothing has been saved.");
      checkActive();
      left.beginLive();
      right.beginLive();
      TimedAudioFrameBuffer.Frame lf = left.poll(1, TimeUnit.SECONDS),
          rf = right.poll(1, TimeUnit.SECONDS);
      long alignmentDeadline = SystemClock.elapsedRealtime() + 2000;
      while (lf != null && rf != null && Math.abs(lf.elapsedNanos - rf.elapsedNanos) > 5_000_000L) {
        checkActive();
        if (SystemClock.elapsedRealtime() > alignmentDeadline)
          throw new IOException("Microphones could not align startup audio. Try again.");
        if (lf.elapsedNanos < rf.elapsedNanos) lf = left.poll(250, TimeUnit.MILLISECONDS);
        else rf = right.poll(250, TimeUnit.MILLISECONDS);
      }
      checkActive();
      if (lf == null || rf == null)
        throw new IOException("Both microphones must keep sending audio.");
      long audioBaseUs = Math.max(0, (Math.max(lf.elapsedNanos, rf.elapsedNanos) - epoch) / 1000);
      long initialDrops = left.droppedFrames() + right.droppedFrames();
      PcmTimeline timeline = new PcmTimeline();
      startedAt = SystemClock.elapsedRealtime();
      state.recording();
      videoStarted = true;
      getSystemService(NotificationManager.class)
          .notify(11, notification("Recording video · stereo microphones"));
      while (active.get()) {
        checkActive();
        if (lf == null || rf == null)
          throw new IOException(
              "Microphone audio stopped. Check both transmitters; recording has stopped.");
        short[] l = lf.samples, r = rf.samples;
        float lg = settings.flag("mute_left") ? 0 : settings.gain(true),
            rg = settings.flag("mute_right") ? 0 : settings.gain(false);
        leftLevel = Math.round(StereoPcmMixer.peak(l, lg) * 32768);
        rightLevel = Math.round(StereoPcmMixer.peak(r, rg) * 32768);
        byte[] pcm = StereoPcmMixer.mix(l, r, lg, rg, settings.flag("swap"));
        current.audio(pcm, audioBaseUs + timeline.ptsUs());
        timeline.advance(l.length);
        drops = left.droppedFrames() + right.droppedFrames() - initialDrops;
        if (drops > 0)
          throw new IOException(
              "Microphone buffering could not keep up. Recording stopped to avoid silent audio"
                  + " loss.");
        lf = left.poll(250, TimeUnit.MILLISECONDS);
        rf = right.poll(250, TimeUnit.MILLISECONDS);
      }
    } catch (CancellationException cancelled) {
    } catch (InterruptedException cancelled) {
      /* User Stop: drain a started take without a false failure. */
    } catch (Exception e) {
      if (active.get()) failure = message(e);
      active.set(false);
    } finally {
      Thread.interrupted();
      active.set(false);
      closeMicrophones();
      CountDownLatch closed = new CountDownLatch(1);
      cameraHandler.post(
          () -> {
            cameraCleanup = closed;
            encoder = null;
            closeCamera();
            completeCameraCleanup();
          });
      try {
        if (!closed.await(5, TimeUnit.SECONDS)) throw new IOException("Camera cleanup timed out");
      } catch (Exception e) {
        if (failure == null) failure = message(e);
      }
      boolean saved = false;
      if (current != null) {
        try {
          if (videoStarted) {
            state.saving();
            current.finish();
            Uri uri = publish(take);
            settings.set("last_video_uri", uri.toString());
            state.saved(uri.toString());
            saved = true;
          } else current.abort();
        } catch (Exception e) {
          current.abort();
          if (failure == null) failure = "Could not save video: " + message(e);
        }
      }
      if (saved && take != null && !take.delete())
        android.util.Log.w("StereoRecorder", "Temporary take retained after publication");
      if (failure != null) state.fail((saved ? "Partial video saved. " : "") + failure);
      else if (!saved) state.idle();
      leftLevel = rightLevel = 0;
      if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
      wakeLock = null;
      main.post(
          () -> {
            stopForeground(STOP_FOREGROUND_REMOVE);
            worker = null;
            if (failure != null) notifyFailure();
            if (destroyed) {
              cameraHandler.post(this::maybeQuitCameraThread);
            } else if (preview != null) cameraHandler.post(this::ensureCamera);
            stopSelf();
          });
    }
  }

  private void checkActive() {
    if (!active.get()) throw new CancellationException();
  }

  private void connectMicrophones() throws IOException {
    if (synthetic) {
      startSynthetic();
      return;
    }
    if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
        != PackageManager.PERMISSION_GRANTED)
      throw new SecurityException("Allow nearby devices access.");
    BluetoothManager manager = getSystemService(BluetoothManager.class);
    BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
    if (adapter == null || !adapter.isEnabled())
      throw new IOException("Turn on Bluetooth and power on both microphones.");
    String l = settings.address(true), r = settings.address(false);
    if (l.isEmpty() || r.isEmpty() || l.equals(r))
      throw new IOException("Select two different paired microphones in Advanced.");
    for (int side = 0; side < 2; side++) {
      final boolean isLeft = side == 0;
      String address = isLeft ? l : r;
      if (!BluetoothAdapter.checkBluetoothAddress(address)
          || adapter.getRemoteDevice(address).getBondState() != BluetoothDevice.BOND_BONDED)
        throw new IOException("Pair both selected microphones in Android Bluetooth settings.");
      MicSource source = new DjiMicSource(adapter, address);
      synchronized (microphones) {
        checkActive();
        microphones.add(source);
      }
      source.start(
          new MicSource.Listener() {
            public void onAudio(short[] pcm, long timestamp) {
              if (active.get())
                (isLeft ? left : right)
                    .offer(
                        pcm, timestamp + (SystemClock.elapsedRealtimeNanos() - System.nanoTime()));
            }

            public void onStatus(String status) {
              if (active.get() && status.startsWith("Sequence discontinuity")) {
                failure =
                    (isLeft ? "Left" : "Right")
                        + " microphone lost a block of audio. Recording stopped to preserve"
                        + " synchronization.";
                stopRecording();
              }
            }

            public void onError(Exception e) {
              if (active.get()) {
                failure = (isLeft ? "Left" : "Right") + " microphone disconnected: " + message(e);
                stopRecording();
              }
            }
          });
    }
  }

  private void startSynthetic() {
    final Thread sessionWorker = worker;
    // Compiled DEBUG guard at entry; never reachable in a release APK.
    new Thread(
            () -> {
              long sample = 0, next = System.nanoTime();
              while (active.get() && worker == sessionWorker) {
                short[] l = new short[240], r = new short[240];
                for (int n = 0; n < 240; n++, sample++) {
                  l[n] = (short) (Math.sin(2 * Math.PI * 440 * sample / 48000) * 7000);
                  r[n] = (short) (Math.sin(2 * Math.PI * 880 * sample / 48000) * 5000);
                }
                long capture = SystemClock.elapsedRealtimeNanos();
                left.offer(l, capture);
                right.offer(r, capture);
                next += 5_000_000L;
                long wait = next - System.nanoTime();
                if (wait > 0) java.util.concurrent.locks.LockSupport.parkNanos(wait);
              }
            },
            "test-microphones")
        .start();
  }

  private void closeMicrophones() {
    List<MicSource> list;
    synchronized (microphones) {
      list = new ArrayList<>(microphones);
      microphones.clear();
    }
    for (MicSource mic : list)
      try {
        mic.close();
      } catch (Exception ignored) {
      }
  }

  private void stopRecording() {
    if (!active.getAndSet(false)) return;
    Thread t = worker;
    if (t != null) t.interrupt();
    new Thread(this::closeMicrophones, "mic-cancellation").start();
  }

  private void retirePreview(boolean releaseTexture) {
    synchronized (previewLock) {
      PreviewLease old = previewLease;
      previewLease = null;
      preview = null;
      previewTexture = null;
      if (old == null) return;
      old.retired = true;
      if (releaseTexture) old.owner.destroyRequested = true;
      releaseRetired(old);
    }
  }

  private void ensureCamera() {
    if (destroyed || cameraCleanup != null) return;
    try {
      if (camera != null) {
        if (cameraReady != null && encoder == null) cameraReady.countDown();
        configure();
        return;
      }
      if (opening || (!active.get() && preview == null)) return;
      CameraManager manager = getSystemService(CameraManager.class);
      chooseCamera(manager);
      opening = true;
      if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
        throw new SecurityException("Allow camera access.");
      manager.openCamera(
          cameraId,
          new CameraDevice.StateCallback() {
            public void onOpened(CameraDevice device) {
              opening = false;
              if (destroyed || cameraCleanup != null || (!active.get() && preview == null)) {
                closeDevice(device);
                maybeQuitCameraThread();
                return;
              }
              camera = device;
              if (cameraReady != null && encoder == null) cameraReady.countDown();
              configure();
            }

            public void onDisconnected(CameraDevice device) {
              closeDevice(device);
              if (camera == device) camera = null;
              opening = false;
              cameraFailure("Camera disconnected.");
              maybeQuitCameraThread();
            }

            public void onError(CameraDevice device, int code) {
              closeDevice(device);
              if (camera == device) camera = null;
              opening = false;
              cameraFailure("Camera unavailable (" + code + "). Close other camera apps.");
              maybeQuitCameraThread();
            }

            public void onClosed(CameraDevice device) {
              closingDevices.remove(device);
              maybeQuitCameraThread();
            }
          },
          cameraHandler);
    } catch (Exception e) {
      opening = false;
      cameraFailure(message(e));
      maybeQuitCameraThread();
    }
  }

  private void chooseCamera(CameraManager manager) throws CameraAccessException, IOException {
    cameraId = null;
    for (String id : manager.getCameraIdList()) {
      CameraCharacteristics c = manager.getCameraCharacteristics(id);
      if (cameraId == null) cameraId = id;
      if (Objects.equals(
          c.get(CameraCharacteristics.LENS_FACING), CameraCharacteristics.LENS_FACING_BACK)) {
        cameraId = id;
        break;
      }
    }
    if (cameraId == null) throw new IOException("This device has no available camera.");
    CameraCharacteristics c = manager.getCameraCharacteristics(cameraId);
    realtimeSensor =
        Objects.equals(
            c.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE),
            CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME);
    StreamConfigurationMap map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
    if (map == null) throw new IOException("Camera capabilities unavailable.");
    Size[] choices = map.getOutputSizes(MediaCodec.class);
    if (choices == null || choices.length == 0)
      throw new IOException("Camera cannot record video.");
    size = null;
    for (Size candidate : choices) {
      if (candidate.getWidth() <= 1920
          && candidate.getHeight() <= 1080
          && candidate.getWidth() * 9 == candidate.getHeight() * 16
          && (size == null || candidate.getWidth() > size.getWidth())) size = candidate;
    }
    if (size == null) throw new IOException("Camera has no supported 720p/1080p video mode.");
    rotation = Objects.requireNonNullElse(c.get(CameraCharacteristics.SENSOR_ORIENTATION), 90);
    fps = null;
    Range<Integer>[] ranges = c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
    if (ranges != null)
      for (Range<Integer> range : ranges)
        if (range.getLower() <= 30
            && range.getUpper() >= 30
            && (fps == null
                || range.getUpper() < fps.getUpper()
                || range.getLower() > fps.getLower())) fps = range;
  }

  private void configure() {
    if (camera == null) return;
    final int version = ++configuration;
    final OfflineVideoEncoder target = encoder;
    final PreviewLease lease =
        previewLease != null && previewLease.surface.isValid() ? previewLease : null;
    final boolean[] sessionReleased = {false};
    List<Surface> surfaces = new ArrayList<>();
    if (lease != null) surfaces.add(lease.surface);
    if (target != null) surfaces.add(target.videoSurface());
    if (surfaces.isEmpty()) {
      if (!active.get()) closeCamera();
      return;
    }
    if (cameraSession != null) {
      cameraSession.close();
      cameraSession = null;
    }
    sessionUses++;
    synchronized (previewLock) {
      if (lease != null) lease.uses++;
    }
    Runnable releaseSession =
        () -> {
          if (sessionReleased[0]) return;
          sessionReleased[0] = true;
          sessionUses--;
          synchronized (previewLock) {
            if (lease != null) {
              lease.uses--;
              releaseRetired(lease);
            }
          }
          maybeQuitCameraThread();
        };
    try {
      CaptureRequest.Builder request =
          camera.createCaptureRequest(
              target == null ? CameraDevice.TEMPLATE_PREVIEW : CameraDevice.TEMPLATE_RECORD);
      for (Surface s : surfaces) request.addTarget(s);
      request.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
      if (fps != null) request.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fps);
      camera.createCaptureSession(
          surfaces,
          new CameraCaptureSession.StateCallback() {
            public void onConfigured(CameraCaptureSession session) {
              if (version != configuration) {
                session.close();
                return;
              }
              cameraSession = session;
              try {
                session.setRepeatingRequest(
                    request.build(),
                    new CameraCaptureSession.CaptureCallback() {
                      boolean first = true;

                      @Override
                      public void onCaptureStarted(
                          CameraCaptureSession s, CaptureRequest req, long timestamp, long frame) {
                        if (first && target != null) {
                          first = false;
                          target.videoStarted(
                              realtimeSensor ? timestamp : SystemClock.elapsedRealtimeNanos());
                          if (cameraReady != null) cameraReady.countDown();
                        }
                      }
                    },
                    cameraHandler);
              } catch (Exception e) {
                cameraFailure(message(e));
              }
            }

            public void onConfigureFailed(CameraCaptureSession session) {
              session.close();
              if (version == configuration) cameraFailure("Camera capture configuration failed.");
            }

            public void onClosed(CameraCaptureSession session) {
              releaseSession.run();
            }
          },
          cameraHandler);
    } catch (Exception e) {
      releaseSession.run();
      cameraFailure(message(e));
    }
  }

  private void closeCamera() {
    configuration++;
    if (cameraSession != null) {
      cameraSession.close();
      cameraSession = null;
    }
    if (camera != null) {
      CameraDevice old = camera;
      camera = null;
      closeDevice(old);
    }
  }

  private void cameraFailure(String error) {
    if (destroyed || cameraCleanup != null) return;
    if (active.get()) {
      failure = error;
      stopRecording();
    } else state.fail(error);
    if (cameraReady != null) cameraReady.countDown();
  }

  private Uri publish(File file) throws IOException {
    String name =
        "StereoBridge_"
            + new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.ROOT).format(new Date())
            + ".mp4";
    ContentValues v = new ContentValues();
    v.put(MediaStore.Video.Media.DISPLAY_NAME, name);
    v.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
    v.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Stereo Bridge");
    v.put(MediaStore.Video.Media.IS_PENDING, 1);
    Uri uri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v);
    if (uri == null) throw new IOException("Could not create video in Movies.");
    try {
      try (InputStream input = new FileInputStream(file);
          OutputStream output = getContentResolver().openOutputStream(uri)) {
        if (output == null) throw new IOException("Could not open video storage.");
        byte[] b = new byte[65536];
        int n;
        while ((n = input.read(b)) >= 0) output.write(b, 0, n);
      }
      v.clear();
      v.put(MediaStore.Video.Media.IS_PENDING, 0);
      if (getContentResolver().update(uri, v, null, null) != 1)
        throw new IOException("Video publication failed.");
      return uri;
    } catch (Exception e) {
      try {
        getContentResolver().delete(uri, null, null);
      } catch (Exception cleanup) {
        e.addSuppressed(cleanup);
      }
      throw new IOException("Not enough storage or video save failed.", e);
    }
  }

  private Notification notification(String text) {
    PendingIntent open =
        PendingIntent.getActivity(
            this,
            0,
            new Intent(this, MainActivity.class),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    PendingIntent stop =
        PendingIntent.getService(
            this,
            1,
            new Intent(this, OfflineRecorderService.class).setAction(STOP),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    return new Notification.Builder(this, "recording")
        .setSmallIcon(app.stereobridge.R.drawable.ic_notification)
        .setContentTitle("Stereo Bridge")
        .setContentText(text)
        .setContentIntent(open)
        .setOngoing(true)
        .addAction(new Notification.Action.Builder(null, "Stop", stop).build())
        .build();
  }

  private void notifyFailure() {
    NotificationManager nm = getSystemService(NotificationManager.class);
    nm.createNotificationChannel(
        new NotificationChannel("errors", "Recording alerts", NotificationManager.IMPORTANCE_HIGH));
    nm.notify(
        12,
        new Notification.Builder(this, "errors")
            .setSmallIcon(app.stereobridge.R.drawable.ic_notification)
            .setContentTitle("Recording stopped")
            .setContentText(state.message())
            .setStyle(new Notification.BigTextStyle().bigText(state.message()))
            .setAutoCancel(true)
            .build());
  }

  private static String message(Exception e) {
    return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
  }

  @Override
  public void onDestroy() {
    destroyed = true;
    stopRecording();
    cameraHandler.post(
        () -> {
          retirePreview(false);
          closeCamera();
          maybeQuitCameraThread();
        });
    super.onDestroy();
  }
}
