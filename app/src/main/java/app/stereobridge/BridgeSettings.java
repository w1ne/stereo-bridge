package app.stereobridge;

import android.content.*;

/** User-selected devices and mixer controls; never contains microphone samples. */
public final class BridgeSettings {
  private final android.content.SharedPreferences preferences;

  public BridgeSettings(Context context) {
    preferences = context.getSharedPreferences("bridge", Context.MODE_PRIVATE);
  }

  public String get(String key, String fallback) {
    return preferences.getString(key, fallback);
  }

  public void set(String key, String value) {
    preferences.edit().putString(key, value).apply();
  }

  public float gain(boolean left) {
    return preferences.getFloat(left ? "gain_left" : "gain_right", 1f);
  }

  public void gain(boolean left, float value) {
    preferences
        .edit()
        .putFloat(left ? "gain_left" : "gain_right", Math.max(0, Math.min(2, value)))
        .apply();
  }

  public boolean flag(String key) {
    return preferences.getBoolean(key, false);
  }

  public void flag(String key, boolean value) {
    preferences.edit().putBoolean(key, value).apply();
  }

  public String address(boolean left) {
    return get(left ? "mic_left" : "mic_right", "");
  }

  public String name(boolean left) {
    return get(
        left ? "name_left" : "name_right", left ? "Choose a microphone" : "Choose a microphone");
  }

  public void microphone(boolean left, String address, String name) {
    preferences
        .edit()
        .putString(left ? "mic_left" : "mic_right", address)
        .putString(left ? "name_left" : "name_right", name)
        .apply();
  }

  public String camera() {
    return get("camera", "com.google.android.GoogleCamera");
  }
}
