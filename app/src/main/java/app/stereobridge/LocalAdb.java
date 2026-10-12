package app.stereobridge;

import android.content.*;
import android.os.Build;
import android.security.keystore.*;
import io.github.muntashirakon.adb.*;
import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.*;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

final class LocalAdb extends AbsAdbConnectionManager {
  private static LocalAdb instance;
  private PrivateKey key;
  private Certificate certificate;

  static synchronized LocalAdb get(Context c) throws Exception {
    if (instance == null) instance = new LocalAdb(c.getApplicationContext());
    return instance;
  }

  private LocalAdb(Context context) throws Exception {
    load(context);
    setApi(Build.VERSION.SDK_INT);
    setHostAddress("127.0.0.1");
    setTimeout(15, TimeUnit.SECONDS);
  }

  protected PrivateKey getPrivateKey() {
    return key;
  }

  protected Certificate getCertificate() {
    return certificate;
  }

  protected String getDeviceName() {
    return "Stereo Bridge";
  }

  private void load(Context c) throws Exception {
    KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
    ks.load(null);
    String alias = "stereo_bridge_adb_storage";
    if (!ks.containsAlias(alias)) {
      KeyGenerator g = KeyGenerator.getInstance("AES", "AndroidKeyStore");
      g.init(
          new KeyGenParameterSpec.Builder(
                  alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
              .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
              .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
              .build());
      g.generateKey();
    }
    Key encryption = ks.getKey(alias, null);
    File path = new File(c.getFilesDir(), "adb-identity.enc");
    if (path.exists()) {
      try (DataInputStream in = new DataInputStream(new FileInputStream(path))) {
        int n = in.readUnsignedByte();
        byte[] iv = new byte[n];
        in.readFully(iv);
        long size = path.length() - 1 - n;
        if (n != 12 || size < 16 || size > 32768) throw new IOException("Invalid identity storage");
        byte[] enc = new byte[(int) size];
        in.readFully(enc);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, encryption, new GCMParameterSpec(128, iv));
        try (DataInputStream data =
            new DataInputStream(new ByteArrayInputStream(cipher.doFinal(enc)))) {
          int len = data.readInt();
          if (len < 1 || len > 16384) throw new IOException("Invalid identity");
          byte[] encoded = new byte[len];
          data.readFully(encoded);
          key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded));
          certificate =
              java.security.cert.CertificateFactory.getInstance("X.509").generateCertificate(data);
        }
      }
      return;
    }
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair pair = generator.generateKeyPair();
    key = pair.getPrivate();
    X500Name name = new X500Name("CN=Stereo Bridge");
    long now = System.currentTimeMillis();
    certificate =
        new JcaX509CertificateConverter()
            .getCertificate(
                new JcaX509v3CertificateBuilder(
                        name,
                        new BigInteger(128, new SecureRandom()),
                        new Date(now - 86400000),
                        new Date(now + 315360000000L),
                        name,
                        pair.getPublic())
                    .build(new JcaContentSignerBuilder("SHA256withRSA").build(key)));
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream data = new DataOutputStream(bytes)) {
      data.writeInt(key.getEncoded().length);
      data.write(key.getEncoded());
      data.write(certificate.getEncoded());
    }
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.ENCRYPT_MODE, encryption);
    try (DataOutputStream out = new DataOutputStream(new FileOutputStream(path))) {
      out.writeByte(cipher.getIV().length);
      out.write(cipher.getIV());
      out.write(cipher.doFinal(bytes.toByteArray()));
    }
  }

  @Override
  public synchronized boolean connect(String host, int port)
      throws IOException, InterruptedException, AdbPairingRequiredException {
    if (!"127.0.0.1".equals(host)) throw new IOException("Only this phone is supported");
    return isConnected() || super.connect(host, port);
  }

  synchronized AdbStream shell(String command) throws Exception {
    AdbConnection connection = getAdbConnection();
    if (connection == null || !connection.isConnected())
      throw new IOException("Not connected to ADB.");
    return connection.open("shell:" + command, 15, TimeUnit.SECONDS);
  }
}
