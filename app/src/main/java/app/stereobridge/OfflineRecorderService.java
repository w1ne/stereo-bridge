package app.stereobridge;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.*;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.params.DynamicRangeProfiles;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaCodec;
import android.net.Uri;
import android.os.*;
import android.util.*;
import android.view.Surface;
import app.stereobridge.mic.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns camera and direct microphone inputs. No privileged routing or network access. */
public final class OfflineRecorderService extends Service {
  public static final String START = "app.stereobridge.RECORD",
      STOP = "app.stereobridge.STOP_RECORDING",
      RECOVER = "app.stereobridge.RECOVER_VIDEO";
  static final OfflineSessionState state = new OfflineSessionState();
  static volatile int leftLevel, rightLevel;
  static volatile long startedAt, drops;
  static volatile boolean needsRecovery;
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
  private final Set<CameraDevice> deferredDeviceCloses =
      Collections.newSetFromMap(new IdentityHashMap<>());
  private CountDownLatch cameraCleanup;

  private void closeDevice(CameraDevice device) {
    // Session.close drains capture sequences asynchronously. Closing its device before
    // onClosed suppresses the sequence callbacks on Android 12 and strands that drain.
    deferredDeviceCloses.add(device);
    closeDrainedDevices();
  }

  private void closeDrainedDevices() {
    if (sessionUses != 0) return;
    for (CameraDevice device : new ArrayList<>(deferredDeviceCloses)) {
      deferredDeviceCloses.remove(device);
      closingDevices.add(device);
      device.close();
    }
  }

  private void completeCameraCleanup() {
    closeDrainedDevices();
    if (cameraCleanup != null
        && !opening
        && camera == null
        && closingDevices.isEmpty()
        && deferredDeviceCloses.isEmpty()
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
        && deferredDeviceCloses.isEmpty()
        && sessionUses == 0) cameraThread.quitSafely();
  }

  private volatile boolean preparingCamera;
  private volatile String startupCameraFailure;
  private final Set<String> rejectedModes = java.util.concurrent.ConcurrentHashMap.newKeySet();
  private String selectedMode;
  private volatile VideoMode mode;
  private volatile List<VideoMode> feasibleModes = Collections.emptyList();
  private volatile String codecMime = CodecPolicy.AVC;
  private volatile boolean hdrActive;
  private volatile CameraCapabilities capabilities = CameraCapabilities.NONE;
  private int stabMode = StabilizationPolicy.NONE;
  private volatile boolean oisOffered, zoomSupported, torchOn, focusLock;
  private volatile float zoomRatio = 1f;
  private float[] focusPoint;
  private android.graphics.Rect activeArray;
  private List<Surface> sessionSurfaces = Collections.emptyList();
  private boolean sessionRecording;
  private CameraCaptureSession.CaptureCallback sessionCallback;
  private Runnable focusRevert;

  private boolean realtimeSensor;
  private OfflineVideoEncoder encoder;
  private volatile Thread worker;
  private volatile String failure;
  private CountDownLatch cameraReady;
  private String cameraId;
  private volatile Size size;
  private volatile int rotation;
  private volatile int displayDegrees;
  private int sensorOrientation = 90;
  private boolean frontFacing;
  private int frameRate = 30;
  private Range<Integer> fps;
  private boolean opening;
  private int configuration;
  private PowerManager.WakeLock wakeLock;
  private BridgeSettings settings;
  private RecordingStore recordingStore;
  private volatile boolean synthetic;
  private volatile boolean destroyed;

  public final class LocalBinder extends Binder {
    public int[] previewInfo() {
      Size current = size;
      return current == null
          ? new int[] {1920, 1080, 90, 0, 90}
          : new int[] {current.getWidth(), current.getHeight(), CameraGeometry.rotation(sensorOrientation, displayDegrees, frontFacing), frontFacing ? 1 : 0, sensorOrientation};
    }

    public void setDisplayRotation(int degrees) {
      displayDegrees = Math.floorMod(degrees, 360);
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

    public CameraCapabilities capabilities() { return capabilities; }
    public String modeLabel() {
      VideoMode current = mode;
      return current == null ? "" : current.label() + (hdrActive ? " · HDR" : "");
    }
    public float zoom() { return zoomRatio; }
    public boolean torch() { return torchOn; }
    public boolean focusLocked() { return focusLock; }
    public boolean modeFeasible(String key) {
      VideoMode candidate = VideoMode.fromKey(key);
      return candidate != null && feasibleModes.contains(candidate);
    }
    public void setZoom(float ratio) {
      cameraHandler.post(() -> {
        if (!zoomSupported) return;
        zoomRatio = ZoomPolicy.clamp(ratio, capabilities.zoomMin, Math.min(capabilities.zoomMax, 8f));
        applyControls();
      });
    }
    public void setFocusPoint(float nx, float ny) {
      cameraHandler.post(() -> {
        if (capabilities.maxAfRegions <= 0) return;
        focusPoint = new float[] {nx, ny};
        cameraHandler.removeCallbacks(focusRevert);
        if (!focusLock) cameraHandler.postDelayed(focusRevert, 4000);
        applyControls();
      });
    }
    public void setFocusLock(boolean locked) {
      cameraHandler.post(() -> {
        focusLock = locked;
        cameraHandler.removeCallbacks(focusRevert);
        if (locked && focusPoint == null) focusPoint = new float[] {0.5f, 0.5f};
        if (!locked) focusPoint = null;
        applyControls();
      });
    }
    public void setTorch(boolean on) {
      cameraHandler.post(() -> {
        if (!capabilities.flash) return;
        torchOn = on;
        applyControls();
      });
    }
    public void flip() {
      cameraHandler.post(() -> {
        if (worker != null) return;
        settings.set("facing", frontFacing ? "back" : "front");
        cameraId = null;
        zoomRatio = 1f;
        focusPoint = null;
        focusLock = false;
        torchOn = false;
        try {
          drainCameraForRetry();
        } catch (Exception e) {
          cameraFailure(message(e));
          return;
        }
        ensureCamera();
      });
    }
    public void requestMode(String modeKey, boolean hdr, String codec) {
      cameraHandler.post(() -> {
        if (worker != null) return;
        settings.set("video_mode", modeKey);
        settings.flag("video_hdr", hdr);
        settings.set("video_codec", codec);
        cameraId = null;
        zoomRatio = 1f;
        focusPoint = null;
        focusLock = false;
        try {
          drainCameraForRetry();
        } catch (Exception e) {
          cameraFailure(message(e));
          return;
        }
        ensureCamera();
      });
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
    recordingStore = new RecordingStore(this);
    needsRecovery = recordingStore.hasPending();
    state.restoreLast(settings.get("last_video_uri", ""));
    cameraThread = new HandlerThread("offline-camera");
    cameraThread.start();
    cameraHandler = new Handler(cameraThread.getLooper());
    focusRevert = () -> {
      if (!focusLock) {
        focusPoint = null;
        applyControls();
      }
    };
    NotificationManager nm = getSystemService(NotificationManager.class);
    nm.createNotificationChannel(
        new NotificationChannel(
            "recording", "Video recording", NotificationManager.IMPORTANCE_LOW));
    if (needsRecovery) startRecovery();
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int id) {
    if (intent != null && RECOVER.equals(intent.getAction())) {
      if (worker == null) startRecovery();
      return START_NOT_STICKY;
    }
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
      if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
          || (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED))
        throw new SecurityException("Allow camera and nearby devices access first.");
      int foregroundTypes = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE;
      if (Build.VERSION.SDK_INT >= 30)
        foregroundTypes |= android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;
      startForeground(11, notification("Connecting microphones…"), foregroundTypes);
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
    RecordingStore.Capture take = null;
    boolean videoStarted = false;
    OfflineVideoEncoder current = null;
    Uri savedOutput = null;
    ScheduledExecutorService storageMonitor = null;
    try {
      Uri recovered = recordingStore.recover();
      if (recovered != null) state.rememberSaved(recovered.toString());
      needsRecovery = recordingStore.hasPending();
      if (needsRecovery) throw new IOException("An earlier video is retained. Retry saving it before recording.");
      if (RecordingSpacePolicy.low(availableStorageBytes()))
        throw new IOException("Not enough storage. Free at least 64 MB before recording.");
      connectMicrophones();
      TimedAudioFrameBuffer.Frame firstLeft = left.poll(20, TimeUnit.SECONDS),
          firstRight = right.poll(20, TimeUnit.SECONDS);
      checkActive();
      if (firstLeft == null || firstRight == null)
        throw new IOException(
            "Both microphones must send audio before recording. Check power and Bluetooth"
                + " pairing.");
      long epoch = 0;
      preparingCamera = true;
      rejectedModes.clear();
      int captureDisplayDegrees = displayDegrees;
      for (int attempt = 0; attempt < StartupRetryPolicy.MAX_ATTEMPTS; attempt++) {
        startupCameraFailure = null;
        try {
          drainCameraForRetry();
          CountDownLatch prepared = new CountDownLatch(1);
          cameraReady = prepared;
          cameraHandler.post(() -> {
            cameraId = null;
            ensureCamera();
          });
          if (!prepared.await(8, TimeUnit.SECONDS)) throw new IOException("Camera did not become ready.");
          checkActive();
          if (startupCameraFailure != null) throw new IOException(startupCameraFailure);
          take = recordingStore.begin();
          rotation = CameraGeometry.rotation(sensorOrientation, captureDisplayDegrees, frontFacing);
          current = new OfflineVideoEncoder(take.descriptor.getFileDescriptor(), size.getWidth(), size.getHeight(), rotation, frameRate, codecMime, hdrActive);
          epoch = SystemClock.elapsedRealtimeNanos();
          current.start(epoch);
          synchronized (this) { checkActive(); encoder = current; }
          CountDownLatch firstVideo = new CountDownLatch(1);
          cameraReady = firstVideo;
          cameraHandler.post(this::configure);
          if (!firstVideo.await(8, TimeUnit.SECONDS)) throw new IOException("Camera did not start video.");
          checkActive();
          if (startupCameraFailure != null) throw new IOException(startupCameraFailure);
          break;
        } catch (IOException startup) {
          checkActive();
          // Stop camera production before releasing its encoder surface or storage FD.
          drainCameraForRetry();
          if (current != null) {
            current.abort();
            if (!current.isReleased()) throw new IOException("Video encoder is still closing.", startup);
          }
          if (take != null) {
            take.close();
            recordingStore.recover();
            if (recordingStore.hasPending()) throw new IOException("Failed startup video retained for recovery.",startup);
          }
          current = null;
          take = null;
          if (selectedMode == null) throw startup;
          rejectedModes.add(selectedMode);
          if (!StartupRetryPolicy.canRetry(preparingCamera,true,true,attempt+1)) throw startup;
          android.util.Log.w("StereoRecorder","Retrying another camera video mode",startup);
        }
      }
      preparingCamera = false;
      checkActive();
      if (startupCameraFailure != null) throw new IOException(startupCameraFailure);
      final java.util.concurrent.atomic.AtomicLong availableSpace =
          new java.util.concurrent.atomic.AtomicLong(availableStorageBytes());
      if (RecordingSpacePolicy.low(availableSpace.get()))
        throw new IOException("Not enough storage. Free at least 64 MB before recording.");
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
      final Thread storageSession = Thread.currentThread();
      storageMonitor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "recording-storage"); t.setDaemon(true); return t;
      });
      storageMonitor.scheduleWithFixedDelay(() -> {
        long available = availableStorageBytes();
        if (active.get() && worker == storageSession) availableSpace.set(available);
      }, 5, 5, TimeUnit.SECONDS);
      while (active.get()) {
        checkActive();
        if (RecordingSpacePolicy.low(availableSpace.get())) {
          failure = "Storage is almost full. Free space before recording again.";
          break;
        }
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
      preparingCamera = false;
      active.set(false);
      if (storageMonitor != null) storageMonitor.shutdownNow();
      if (videoStarted) state.saving();
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
            take.close();
            Uri uri = recordingStore.finishAndPublish(take);
            savedOutput = uri;
            saved = true;
          } else current.abort();
        } catch (Exception e) {
          current.abort();
          if (failure == null) failure = "Could not save video: " + message(e);
        }
      }
      if (take != null && !saved && current != null && !current.isReleased()) {
        deferRecovery(current, take);
        if (failure == null) failure = "Video is still closing. It is retained for recovery.";
      } else if (take != null && !saved) {
        try {
          take.close();
          Uri recovered = recordingStore.recover();
          if (recovered != null) {
            savedOutput = recovered;
            saved = true;
          }
        } catch (Exception recovery) {
          if (failure == null) failure = "Video retained. Tap Retry save when storage is available.";
          android.util.Log.w("StereoRecorder", "Video recovery deferred", recovery);
        }
      }
      needsRecovery = recordingStore.hasPending();
      if (needsRecovery && failure != null) failure += " Video retained; tap Retry save.";
      final Uri completedOutput = savedOutput;
      final String completedFailure = failure == null ? null : (saved ? "Partial video saved. " : "") + failure;
      leftLevel = rightLevel = 0;
      if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
      wakeLock = null;
      main.post(
          () -> {
            stopForeground(STOP_FOREGROUND_REMOVE);
            worker = null;
            if (completedOutput != null) state.rememberSaved(completedOutput.toString());
            if (completedFailure != null) state.fail(completedFailure);
            else if (completedOutput != null) state.saved(completedOutput.toString());
            else state.idle();
            if (completedFailure != null) notifyFailure();
            if (destroyed) {
              cameraHandler.post(this::maybeQuitCameraThread);
            } else if (preview != null) cameraHandler.post(this::ensureCamera);
            stopSelf();
          });
    }
  }

  private void drainCameraForRetry() throws IOException, InterruptedException {
    if (Looper.myLooper() == cameraHandler.getLooper()) {
      encoder = null;
      closeCamera();
      completeCameraCleanup();
      return;
    }
    CountDownLatch closed = new CountDownLatch(1);
    cameraHandler.post(() -> {
      cameraCleanup = closed;
      encoder = null;
      closeCamera();
      completeCameraCleanup();
    });
    if (!closed.await(5,TimeUnit.SECONDS)) throw new IOException("Camera cleanup timed out; retry is unsafe.");
  }

  private long availableStorageBytes() {
    try { return new StatFs(Environment.getExternalStorageDirectory().getAbsolutePath()).getAvailableBytes(); }
    catch (RuntimeException unknown) { return -1; }
  }

  private void checkActive() {
    if (!active.get()) throw new CancellationException();
  }

  private void connectMicrophones() throws IOException {
    if (synthetic) {
      startSynthetic();
      return;
    }
    if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
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
                android.util.Log.w("StereoRecorder", (isLeft ? "Left " : "Right ") + status);
                failure =
                    (isLeft ? "Left" : "Right")
                        + " microphone lost a block of audio. Recording stopped to preserve"
                        + " synchronization.";
                stopRecording();
              }
            }

            public void onError(Exception e) {
              if (active.get()) {
                android.util.Log.w("StereoRecorder", (isLeft ? "Left" : "Right") + " microphone disconnected", e);
                failure = (isLeft ? "Left" : "Right")
                    + " microphone disconnected. Check power and Bluetooth, then tap Record.";
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
      if (opening || !deferredDeviceCloses.isEmpty() || !closingDevices.isEmpty()
          || (!active.get() && preview == null)) return;
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
              if (!destroyed && cameraCleanup == null && preview != null && worker == null)
                ensureCamera();
            }
          },
          cameraHandler);
    } catch (Exception e) {
      opening = false;
      cameraFailure(message(e));
      maybeQuitCameraThread();
    }
  }

  private String firstSupportedCodec(VideoMode candidate) {
    for (String mime : CodecPolicy.order(settings.get("video_codec", "avc")))
      if (OfflineVideoEncoder.supportsVideo(candidate.width, candidate.height, candidate.rate, mime))
        return mime;
    return null;
  }

  private static int facingRank(CameraManager manager, String id, boolean wantFront) {
    try {
      Integer facing = manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
      boolean front = Objects.equals(facing, CameraCharacteristics.LENS_FACING_FRONT);
      boolean back = Objects.equals(facing, CameraCharacteristics.LENS_FACING_BACK);
      if (wantFront) return front ? 0 : back ? 1 : 2;
      return back ? 0 : front ? 1 : 2;
    } catch (CameraAccessException e) { return 3; }
  }

  private void chooseCamera(CameraManager manager) throws CameraAccessException, IOException {
    if (cameraId != null && (camera != null || opening)) return;
    boolean wantFront = "front".equals(settings.get("facing", "back"));
    List<String> ids = new ArrayList<>(Arrays.asList(manager.getCameraIdList()));
    ids.sort((a, b) -> Integer.compare(facingRank(manager, a, wantFront), facingRank(manager, b, wantFront)));
    selectedMode = null;
    mode = null;
    feasibleModes = Collections.emptyList();
    codecMime = CodecPolicy.AVC;
    hdrActive = false;
    for (String id : ids) {
      CameraCharacteristics c;
      try { c = manager.getCameraCharacteristics(id); } catch (CameraAccessException e) { continue; }
      StreamConfigurationMap map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
      if (map == null) continue;
      Size[] recordSizes = map.getOutputSizes(MediaCodec.class);
      Size[] previewSizes = map.getOutputSizes(SurfaceTexture.class);
      if (recordSizes == null || previewSizes == null) continue;
      List<Size> recordList = Arrays.asList(recordSizes), previewList = Arrays.asList(previewSizes);
      Range<Integer>[] advertised = c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
      int[][] ranges = null;
      if (advertised != null) {
        ranges = new int[advertised.length][2];
        for (int n = 0; n < advertised.length; n++) {
          ranges[n][0] = advertised[n].getLower();
          ranges[n][1] = advertised[n].getUpper();
        }
      }
      List<VideoMode> feasible = new ArrayList<>();
      for (VideoMode candidate : VideoMode.FALLBACK) {
        Size candidateSize = new Size(candidate.width, candidate.height);
        if (!recordList.contains(candidateSize) || !previewList.contains(candidateSize)) continue;
        if (!ModeMatrixPolicy.fpsRangeCovers(ranges, candidate.rate)) continue;
        if (firstSupportedCodec(candidate) == null) continue;
        feasible.add(candidate);
      }
      if (feasible.isEmpty()) continue;
      List<VideoMode> ordered = ModeMatrixPolicy.order(feasible, settings.get("video_mode", ""));
      VideoMode chosen = null;
      String chosenMime = null;
      for (VideoMode candidate : ordered) {
        for (String mime : CodecPolicy.order(settings.get("video_codec", "avc"))) {
          if (!OfflineVideoEncoder.supportsVideo(candidate.width, candidate.height, candidate.rate, mime)) continue;
          if (preparingCamera && rejectedModes.contains(id + ":" + candidate.key() + ":" + mime)) continue;
          chosen = candidate;
          chosenMime = mime;
          break;
        }
        if (chosen != null) break;
      }
      if (chosen == null) continue;
      mode = chosen;
      codecMime = chosenMime;
      feasibleModes = ordered;
      selectedMode = id + ":" + chosen.key() + ":" + chosenMime;
      size = new Size(chosen.width, chosen.height);
      int[] ae = ModeMatrixPolicy.aeRange(ranges, chosen.rate);
      fps = new Range<>(ae[0], ae[1]);
      frameRate = chosen.rate;
      if (previewTexture != null) previewTexture.setDefaultBufferSize(chosen.width, chosen.height);
      Integer sensor = c.get(CameraCharacteristics.SENSOR_ORIENTATION);
      sensorOrientation = sensor == null ? 90 : sensor;
      frontFacing = Objects.equals(c.get(CameraCharacteristics.LENS_FACING), CameraCharacteristics.LENS_FACING_FRONT);
      rotation = CameraGeometry.rotation(sensorOrientation, displayDegrees, frontFacing);
      realtimeSensor = Objects.equals(c.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE), CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME);
      activeArray = c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
      int[] stabilizationModes = c.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES);
      int[] opticalModes = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION);
      android.util.Range<Float> zoomRange =
          Build.VERSION.SDK_INT >= 30 ? c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE) : null;
      Float maxDigitalZoom = c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
      boolean zoomAvailable = zoomRange != null
          || (activeArray != null && maxDigitalZoom != null && maxDigitalZoom > 1.05f);
      float zoomMin = zoomRange != null ? zoomRange.getLower() : 1f;
      float zoomMax = zoomRange != null
          ? zoomRange.getUpper()
          : (maxDigitalZoom == null ? 1f : Math.min(maxDigitalZoom, 8f));
      Integer afRegions = c.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF);
      capabilities = new CameraCapabilities(
          StabilizationPolicy.videoStabilization(stabilizationModes),
          StabilizationPolicy.opticalStabilization(opticalModes),
          Boolean.TRUE.equals(c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)),
          zoomAvailable,
          zoomMin,
          zoomMax,
          afRegions == null ? 0 : afRegions,
          hlgCamera(c),
          OfflineVideoEncoder.supportsHlgEncode(chosen.width, chosen.height, chosen.rate));
      stabMode = capabilities.stabilizationMode;
      oisOffered = capabilities.opticalStabilization;
      zoomSupported = capabilities.zoom;
      android.util.Log.i("StereoRecorder", "Camera capabilities: " + capabilities.describe() + " mode=" + chosen.label());
      return;
    }
    throw new IOException("No camera and encoder share a supported video mode.");
  }

  private static boolean hlgCamera(CameraCharacteristics c) {
    if (Build.VERSION.SDK_INT < 33) return false;
    int[] abilities = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
    boolean tenBit = false;
    if (abilities != null)
      for (int ability : abilities)
        if (ability == CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT) tenBit = true;
    if (!tenBit) return false;
    DynamicRangeProfiles profiles =
        c.get(CameraCharacteristics.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES);
    return profiles != null && profiles.getSupportedProfiles().contains(DynamicRangeProfiles.HLG10);
  }

  private CaptureRequest.Builder baseRequest(int template) throws CameraAccessException {
    CaptureRequest.Builder request = camera.createCaptureRequest(template);
    request.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
    if (fps != null) request.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fps);
    if (stabMode != StabilizationPolicy.NONE)
      request.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, stabMode);
    // Explicit OIS only when EIS is unavailable: an EIS request already engages the HAL's fused
    // OIS+EIS on Pixel-class devices, and AOSP warns against explicitly combining both.
    if (oisOffered && stabMode == StabilizationPolicy.NONE)
      request.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON);
    request.set(CaptureRequest.CONTROL_AF_MODE,
        focusLock ? CaptureRequest.CONTROL_AF_MODE_AUTO : CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
    if (focusPoint != null && activeArray != null && capabilities.maxAfRegions > 0) {
      android.util.Size regionSize =
          new android.util.Size(Math.max(1, activeArray.width() / 10), Math.max(1, activeArray.height() / 10));
      int left = Math.round(focusPoint[0] * activeArray.width()) - regionSize.getWidth() / 2;
      int top = Math.round(focusPoint[1] * activeArray.height()) - regionSize.getHeight() / 2;
      left = Math.max(activeArray.left, Math.min(activeArray.right - regionSize.getWidth(), left));
      top = Math.max(activeArray.top, Math.min(activeArray.bottom - regionSize.getHeight(), top));
      MeteringRectangle rect = new MeteringRectangle(
          left, top, regionSize.getWidth(), regionSize.getHeight(), MeteringRectangle.METERING_WEIGHT_MAX);
      request.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[] {rect});
      request.set(CaptureRequest.CONTROL_AE_REGIONS, new MeteringRectangle[] {rect});
    }
    if (focusLock) {
      request.set(CaptureRequest.CONTROL_AE_LOCK, true);
      request.set(CaptureRequest.CONTROL_AWB_LOCK, true);
    }
    if (torchOn) request.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
    if (zoomRatio > 1f && zoomSupported) {
      if (Build.VERSION.SDK_INT >= 30) request.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoomRatio);
      else if (activeArray != null) {
        float[] region = ZoomPolicy.cropRegion(zoomRatio, activeArray.width(), activeArray.height());
        request.set(CaptureRequest.SCALER_CROP_REGION, new android.graphics.Rect(
            Math.round(region[0]), Math.round(region[1]), Math.round(region[2]), Math.round(region[3])));
      }
    }
    return request;
  }

  private void applyControls() {
    CameraCaptureSession session = cameraSession;
    if (session == null || camera == null || sessionCallback == null) return;
    try {
      CaptureRequest.Builder request =
          baseRequest(sessionRecording ? CameraDevice.TEMPLATE_RECORD : CameraDevice.TEMPLATE_PREVIEW);
      for (Surface surface : sessionSurfaces) request.addTarget(surface);
      session.setRepeatingRequest(request.build(), sessionCallback, cameraHandler);
    } catch (Exception ignored) {
      // Live control updates are best effort; the base session remains valid.
    }
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
          baseRequest(target == null ? CameraDevice.TEMPLATE_PREVIEW : CameraDevice.TEMPLATE_RECORD);
      for (Surface s : surfaces) request.addTarget(s);
      sessionSurfaces = new ArrayList<>(surfaces);
      sessionRecording = target != null;
      sessionCallback =
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
          };
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
                session.setRepeatingRequest(request.build(), sessionCallback, cameraHandler);
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
      try {
        cameraSession.stopRepeating();
        cameraSession.abortCaptures();
      } catch (CameraAccessException | IllegalStateException ignored) {
        // Closing is still required if the device already disconnected.
      }
      cameraSession.close();
      cameraSession = null;
    }
    torchOn = false;
    if (camera != null) {
      CameraDevice old = camera;
      camera = null;
      closeDevice(old);
    }
  }

  private void cameraFailure(String error) {
    if (destroyed || cameraCleanup != null) return;
    if (active.get() && preparingCamera) {
      startupCameraFailure = error;
    } else if (active.get()) {
      failure = error;
      stopRecording();
    } else state.fail(error);
    if (cameraReady != null) cameraReady.countDown();
  }

  private void deferRecovery(OfflineVideoEncoder closingEncoder, RecordingStore.Capture capture) {
    Thread deferred = new Thread(() -> {
      Uri recovered = null;
      try {
        closingEncoder.awaitRelease();
        capture.close();
        recovered = recordingStore.recover();
      } catch (Exception e) {
        android.util.Log.w("StereoRecorder", "Deferred video recovery retained", e);
      }
      final Uri result = recovered;
      main.post(() -> {
        needsRecovery = recordingStore.hasPending();
        if (result != null) state.rememberSaved(result.toString());
        if (!state.busy()) {
          if (result != null) state.saved(result.toString());
          else if (needsRecovery) state.fail("Video retained. Tap Retry save when storage is available.");
        }
      });
    }, "encoder-close-recovery");
    deferred.setDaemon(true);
    deferred.start();
  }

  private void startRecovery() {
    if (worker != null) return;
    state.saving();
    worker = new Thread(() -> {
      Uri recovered = null;
      String error = null;
      try { recovered = recordingStore.recover(); }
      catch (Exception e) { error = message(e); }
      final Uri result = recovered;
      final String problem = error;
      main.post(() -> {
        needsRecovery = recordingStore.hasPending();
        if (result != null) state.saved(result.toString());
        else if (needsRecovery)
          state.fail("Video retained. Tap Retry save when storage is available."
              + (problem == null ? "" : " " + problem));
        else state.idle();
        worker = null;
        if (destroyed) cameraHandler.post(this::maybeQuitCameraThread);
        stopSelf();
      });
    }, "video-recovery");
    worker.start();
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
