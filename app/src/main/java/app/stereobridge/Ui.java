package app.stereobridge;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.view.WindowInsets;
import android.widget.*;

/** Shared system-theme styling for dashboard and setup. */
final class Ui {
  final Activity activity;
  final boolean dark;
  final int background, surface, text, secondary, accent, blue, border, danger;

  Ui(Activity activity) {
    this.activity = activity;
    dark = isDark(activity);
    background = Color.parseColor(dark ? "#0C1118" : "#F3F5F8");
    surface = Color.parseColor(dark ? "#171F2B" : "#FFFFFF");
    text = Color.parseColor(dark ? "#F1F5FA" : "#142033");
    secondary = Color.parseColor(dark ? "#A5B2C4" : "#536479");
    accent = Color.parseColor(dark ? "#75E1C5" : "#087962");
    blue = Color.parseColor(dark ? "#9CBFFF" : "#315EB3");
    border = Color.parseColor(dark ? "#2C3747" : "#DEE5ED");
    danger = Color.parseColor(dark ? "#FFB0AA" : "#AF3035");
  }

  static boolean isDark(Context c) {
    return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
        == Configuration.UI_MODE_NIGHT_YES;
  }

  static void theme(Activity a) {
    a.setTheme(
        isDark(a)
            ? android.R.style.Theme_Material_NoActionBar
            : android.R.style.Theme_Material_Light_NoActionBar);
  }

  int dp(int value) {
    return Math.round(value * activity.getResources().getDisplayMetrics().density);
  }

  TextView text(String label, int size, boolean strong) {
    TextView view = new TextView(activity);
    view.setText(label);
    view.setTextSize(size);
    view.setTextColor(text);
    if (strong) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    view.setLineSpacing(dp(3), 1f);
    return view;
  }

  TextView caption(String label) {
    TextView v = text(label, 14, false);
    v.setTextColor(secondary);
    return v;
  }

  GradientDrawable shape(int fill, int radius, boolean stroke) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(fill);
    d.setCornerRadius(dp(radius));
    if (stroke) d.setStroke(dp(1), border);
    return d;
  }

  LinearLayout card(LinearLayout parent) {
    LinearLayout box = new LinearLayout(activity);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(dp(18), dp(18), dp(18), dp(18));
    box.setBackground(shape(surface, 22, true));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.bottomMargin = dp(12);
    parent.addView(box, p);
    return box;
  }

  Button button(LinearLayout parent, String label, boolean primary, Runnable action) {
    Button b = new Button(activity);
    b.setText(label);
    b.setAllCaps(false);
    b.setTextSize(primary ? 19 : 16);
    b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    b.setMinHeight(dp(primary ? 64 : 50));
    b.setMinimumHeight(dp(primary ? 64 : 50));
    b.setPadding(dp(18), dp(10), dp(18), dp(10));
    int foreground = primary ? background : text;
    b.setTextColor(
        new ColorStateList(
            new int[][] {new int[] {-android.R.attr.state_enabled}, new int[] {}},
            new int[] {secondary, foreground}));
    b.setBackground(
        new RippleDrawable(
            ColorStateList.valueOf(dark ? 0x33FFFFFF : 0x22000000),
            shape(primary ? accent : surface, primary ? 20 : 14, !primary),
            null));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.topMargin = dp(8);
    p.bottomMargin = dp(4);
    parent.addView(b, p);
    b.setOnClickListener(v -> action.run());
    return b;
  }

  void controls(CompoundButton b) {
    b.setTextColor(text);
    b.setTextSize(15);
    b.setMinHeight(dp(48));
    b.setButtonTintList(ColorStateList.valueOf(accent));
  }

  void screen(ScrollView scroll, LinearLayout root, LinearLayout footer) {
    activity
        .getWindow()
        .setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(background));
    LinearLayout container = new LinearLayout(activity);
    container.setOrientation(LinearLayout.VERTICAL);
    container.setBackgroundColor(background);
    scroll.setFillViewport(true);
    scroll.setBackgroundColor(background);
    container.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    footer.setPadding(dp(22), dp(8), dp(22), dp(12));
    footer.setBackgroundColor(background);
    container.addView(footer, new LinearLayout.LayoutParams(-1, -2));
    container.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          if (android.os.Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars()
                | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            container.setPadding(bars.left, bars.top, bars.right, bars.bottom);
          } else {
            android.view.DisplayCutout cutout = insets.getDisplayCutout();
            container.setPadding(
                Math.max(insets.getSystemWindowInsetLeft(), cutout == null ? 0 : cutout.getSafeInsetLeft()),
                Math.max(insets.getSystemWindowInsetTop(), cutout == null ? 0 : cutout.getSafeInsetTop()),
                Math.max(insets.getSystemWindowInsetRight(), cutout == null ? 0 : cutout.getSafeInsetRight()),
                Math.max(insets.getSystemWindowInsetBottom(), cutout == null ? 0 : cutout.getSafeInsetBottom()));
          }
          return insets;
        });
    root.setPadding(dp(22), dp(24), dp(22), dp(12));
    activity.setContentView(container);
    if (android.os.Build.VERSION.SDK_INT >= 30) {
    android.view.WindowInsetsController controller = activity.getWindow().getInsetsController();
    if (controller != null) {
      int mask =
          android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
              | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
      controller.setSystemBarsAppearance(dark ? 0 : mask, mask);
    }
    } else {
      View decor = activity.getWindow().getDecorView();
      int mask = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
      decor.setSystemUiVisibility(dark ? decor.getSystemUiVisibility() & ~mask
          : decor.getSystemUiVisibility() | mask);
    }
    container.requestApplyInsets();
  }

  void space(LinearLayout parent, int size) {
    View v = new View(activity);
    parent.addView(v, new LinearLayout.LayoutParams(1, dp(size)));
  }
}
