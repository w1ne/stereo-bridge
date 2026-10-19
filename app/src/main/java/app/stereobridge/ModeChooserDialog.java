package app.stereobridge;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Mode, HDR and codec chooser with GCam-derived enablement rules. */
final class ModeChooserDialog {
  interface Listener {
    void onApply(String modeKey, boolean hdr, String codec);
  }

  static void show(
      Activity activity,
      Ui ui,
      CameraCapabilities caps,
      String currentKey,
      boolean currentHdr,
      String currentCodec,
      Predicate<String> modeFeasible,
      Supplier<Boolean> hevcAvailable,
      Listener listener) {
    LinearLayout root = new LinearLayout(activity);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(4));
    root.addView(ui.caption("Video mode"));
    RadioGroup modes = new RadioGroup(activity);
    int[] selectedIndex = {0};
    final boolean[] hdrChosen = {currentHdr};
    final int[] selectedRate = {30};
    CheckBox hdr = new CheckBox(activity);
    ui.controls(hdr);
    hdr.setText("10-bit HDR (30 fps only)");
    TextView hdrHint = ui.caption("10-bit HDR can't be used unless video FPS is 30.");
    hdrHint.setVisibility(hdrChosen[0] ? android.view.View.GONE : android.view.View.VISIBLE);

    java.util.List<RadioButton> buttons = new java.util.ArrayList<>();
    String feasibleDefault = null;
    for (int index = 0; index < VideoMode.ALL.size(); index++) {
      VideoMode mode = VideoMode.ALL.get(index);
      RadioButton button = new RadioButton(activity);
      ui.controls(button);
      boolean feasible = modeFeasible.test(mode.key());
      button.setText(mode.label() + (feasible ? "" : "  (not supported on this camera)"));
      button.setEnabled(feasible);
      if (feasible && feasibleDefault == null) feasibleDefault = mode.key();
      if (mode.key().equals(currentKey) && feasible) {
        button.setChecked(true);
        selectedIndex[0] = index;
        selectedRate[0] = mode.rate;
      }
      final int indexFinal = index;
      button.setOnClickListener(v -> {
        selectedIndex[0] = indexFinal;
        selectedRate[0] = mode.rate;
        hdrChosen[0] = hdr.isChecked();
      });
      modes.addView(button);
      buttons.add(button);
    }
    if (feasibleDefault != null && !buttons.get(selectedIndex[0]).isChecked()) {
      for (int index = 0; index < VideoMode.ALL.size(); index++)
        if (VideoMode.ALL.get(index).key().equals(feasibleDefault)) {
          buttons.get(index).setChecked(true);
          selectedIndex[0] = index;
          selectedRate[0] = VideoMode.ALL.get(index).rate;
        }
    }
    boolean hdrOffered = HdrPolicy.offer(caps.hlgCamera, caps.hlgEncoder);
    hdr.setEnabled(hdrOffered && HdrPolicy.allowedAtRate(selectedRate[0]));
    hdr.setChecked(hdrChosen[0] && hdr.isEnabled());
    for (RadioButton button : buttons)
      button.setOnCheckedChangeListener((b, checked) -> {
        if (!checked) return;
        boolean allowed = hdrOffered && HdrPolicy.allowedAtRate(selectedRate[0]);
        hdr.setEnabled(allowed);
        if (!allowed) hdr.setChecked(false);
        hdrHint.setVisibility(allowed ? android.view.View.GONE : android.view.View.VISIBLE);
      });
    root.addView(modes);
    root.addView(hdr);
    root.addView(hdrHint);
    ui.space(root, 8);
    root.addView(ui.caption("Video codec"));
    RadioGroup codecGroup = new RadioGroup(activity);
    RadioButton avc = new RadioButton(activity);
    ui.controls(avc);
    avc.setText("AVC · best compatibility");
    RadioButton hevc = new RadioButton(activity);
    ui.controls(hevc);
    hevc.setText("HEVC · saves storage");
    hevc.setEnabled(hevcAvailable.get());
    (CodecPolicy.HEVC.equals(currentCodec) && hevc.isEnabled() ? hevc : avc).setChecked(true);
    codecGroup.addView(avc);
    codecGroup.addView(hevc);
    root.addView(codecGroup);
    new AlertDialog.Builder(activity)
        .setTitle("Video mode")
        .setView(root)
        .setPositiveButton(
            "Apply",
            (dialog, which) ->
                listener.onApply(
                    VideoMode.ALL.get(selectedIndex[0]).key(),
                    hdr.isChecked(),
                    hevc.isChecked() ? CodecPolicy.HEVC : CodecPolicy.AVC))
        .setNegativeButton("Cancel", null)
        .show();
  }
}
