package app.stereobridge;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.*;
import java.util.function.Predicate;

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
      Predicate<String> hevcAvailable,
      Listener listener) {
    LinearLayout root = new LinearLayout(activity);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(4));
    root.addView(ui.caption("Video mode"));
    RadioGroup modes = new RadioGroup(activity);
    int[] selectedIndex = {-1};
    final int[] selectedRate = {30};
    final String[] selectedModeKey = {null};
    CheckBox hdr = new CheckBox(activity);
    ui.controls(hdr);
    hdr.setText("10-bit HDR (30 fps only)");
    hdr.setEnabled(false);
    TextView hdrHint = ui.caption("10-bit HDR can't be used unless video FPS is 30.");
    RadioButton avc = new RadioButton(activity);
    ui.controls(avc);
    RadioButton hevc = new RadioButton(activity);
    ui.controls(hevc);

    boolean hdrOffered = HdrPolicy.offer(caps.hlgCamera, caps.hlgEncoder);
    java.util.List<RadioButton> buttons = new java.util.ArrayList<>();
    int firstFeasible = -1, remembered = -1;
    for (int index = 0; index < VideoMode.ALL.size(); index++) {
      VideoMode mode = VideoMode.ALL.get(index);
      RadioButton button = new RadioButton(activity);
      ui.controls(button);
      boolean feasible = modeFeasible.test(mode.key());
      button.setText(mode.label() + (feasible ? "" : "  (not supported on this camera)"));
      button.setEnabled(feasible);
      if (feasible && firstFeasible < 0) firstFeasible = index;
      if (feasible && mode.key().equals(currentKey)) remembered = index;
      final int indexFinal = index;
      button.setOnCheckedChangeListener((b, checked) -> {
        if (!checked) return;
        selectedIndex[0] = indexFinal;
        selectedRate[0] = mode.rate;
        selectedModeKey[0] = mode.key();
        boolean allowed = hdrOffered && HdrPolicy.allowedAtRate(selectedRate[0]);
        hdr.setEnabled(allowed);
        if (!allowed) hdr.setChecked(false);
        hdrHint.setVisibility(allowed ? android.view.View.GONE : android.view.View.VISIBLE);
        hevc.setEnabled(!hdr.isChecked() && hevcAvailable.test(selectedModeKey[0]));
      });
      modes.addView(button);
      buttons.add(button);
    }
    hdr.setOnCheckedChangeListener((b, checked) -> {
      if (checked) {
        hevc.setChecked(true);
        hevc.setEnabled(false);
        avc.setEnabled(false);
      } else {
        hevc.setEnabled(hevcAvailable.test(selectedModeKey[0]));
        avc.setEnabled(true);
      }
    });
    int preselected = remembered >= 0 ? remembered : firstFeasible;
    if (preselected >= 0) buttons.get(preselected).setChecked(true);
    hdr.setChecked(currentHdr && hdr.isEnabled());
    if (!hdr.isEnabled())
      hdrHint.setVisibility(android.view.View.VISIBLE);
    else if (!hdr.isChecked())
      hdrHint.setVisibility(android.view.View.GONE);
    TextView noMode = ui.caption("No supported video mode is available on this camera.");
    noMode.setVisibility(firstFeasible < 0 ? android.view.View.VISIBLE : android.view.View.GONE);
    root.addView(modes);
    root.addView(noMode);
    root.addView(hdr);
    root.addView(hdrHint);
    ui.space(root, 8);
    root.addView(ui.caption("Video codec"));
    RadioGroup codecGroup = new RadioGroup(activity);
    avc.setText("AVC · best compatibility");
    hevc.setText("HEVC · saves storage");
    if (hdr.isChecked()) {
      hevc.setChecked(true);
      hevc.setEnabled(false);
      avc.setEnabled(false);
    } else {
      hevc.setEnabled(hevcAvailable.test(selectedModeKey[0]));
      (CodecPolicy.HEVC.equals(currentCodec) && hevc.isEnabled() ? hevc : avc).setChecked(true);
    }
    codecGroup.addView(avc);
    codecGroup.addView(hevc);
    root.addView(codecGroup);
    new AlertDialog.Builder(activity)
        .setTitle("Video mode")
        .setView(root)
        .setPositiveButton(
            "Apply",
            (dialog, which) -> {
              if (selectedIndex[0] < 0) return;
              listener.onApply(
                  VideoMode.ALL.get(selectedIndex[0]).key(),
                  hdr.isChecked(),
                  hevc.isChecked() ? CodecPolicy.HEVC : CodecPolicy.AVC);
            })
        .setNegativeButton("Cancel", null)
        .show();
  }
}
