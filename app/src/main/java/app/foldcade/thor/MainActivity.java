/* SPDX-License-Identifier: Apache-2.0 */
package app.foldcade.thor;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class MainActivity extends Activity {
    private TextView status;
    private Button refresh;
    private LinearLayout content;
    private DeviceController controller;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        controller = DeviceController.get(this);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(28); content.setPadding(padding, padding, padding, padding);
        scroll.addView(content); setContentView(scroll);
        // Targeting API 35 draws under the status and navigation bars, so the page pads itself clear of them.
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsets.CONSUMED;
        });
        scroll.setClipToPadding(false);
        text("FOLDCADE / DEVICE PLUGIN", 12, "#A3D9BA", 0);
        text("Foldcade Thor", 30, "#F0F5F1", 10);
        text("Device controls for your AYN Thor.", 16, "#B5C2BA", 8);
        status = text("Checking root access…", 16, "#F0F5F1", 28);
        refresh = new Button(this); refresh.setText("Check again"); refresh.setAllCaps(false);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, dp(52)); buttonParams.topMargin = dp(20);
        content.addView(refresh, buttonParams); refresh.setOnClickListener(v -> refresh(true));
        text("Set up once", 21, "#F0F5F1", 28);
        text("1  Open Magisk › Superuser › + and add Foldcade Thor. Magisk 31 never prompts for root access.", 16, "#B5C2BA", 12);
        text("2  Open Foldcade › Settings › Plugins › Foldcade Thor and turn on “Control this device”.", 16, "#B5C2BA", 18);
        text("Controls appear in Foldcade when the required thor-features modules are installed and active.", 14, "#8F9F94", 24);
        if (BuildConfig.DEBUG) {
            Button smoke = new Button(this); smoke.setText("Run device checks (debug)"); smoke.setAllCaps(false);
            content.addView(smoke);
            smoke.setOnClickListener(v -> {
                smoke.setEnabled(false); refresh.setEnabled(false); status.setText("Running device checks…");
                controller.submit(() -> {
                    String report = controller.smoke();
                    runOnUiThread(() -> { status.setText(report); smoke.setEnabled(true); refresh.setEnabled(true); });
                    return null;
                });
            });
        }
        refresh(false);
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size, String color, int top) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(Color.parseColor(color));
        view.setLineSpacing(dp(3), 1); view.setTextIsSelectable(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(top);
        content.addView(view, params); return view;
    }
    private void refresh(boolean force) {
        refresh.setEnabled(false);
        Bundle extras = new Bundle(); extras.putBoolean("refresh", force);
        controller.submit(() -> {
            Bundle state = controller.handle("state", null, extras);
            runOnUiThread(() -> { if (!isDestroyed()) { render(state); refresh.setEnabled(true); } });
            return null;
        });
    }
    private void render(Bundle state) {
        if (!state.getBoolean("ok")) { status.setText(state.getString("message")); return; }
        String root = state.getString("root", "denied");
        String heading = switch (root) { case "granted" -> "Root access granted"; case "absent" -> "Root unavailable · install Magisk"; default -> "Root access needed · add this app in Magisk"; };
        String[] capabilities = state.getStringArray("capabilities");
        List<String> caps = capabilities == null ? Collections.emptyList() : Arrays.asList(capabilities);
        StringBuilder lines = new StringBuilder(heading).append("\n");
        String[][] controls = {{"fan", "Fan", "thor-fanctl"}, {"lights", "Stick lights", "thor-stickfx"},
            {"brightness", "Per-screen brightness", "thor-screenbright"}, {"screens", "Screen power", "thor-screenblank"}, {"gameFan", "Per-game fan", "thor-fanctl"}};
        for (String[] control : controls) lines.append("\n").append(control[1]).append(caps.contains(control[0]) ? " · Ready" : " · Missing / needs " + control[2]);
        status.setText(lines);
    }
}
