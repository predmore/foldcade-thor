/* SPDX-License-Identifier: Apache-2.0 */
package app.foldcade.thor;

import static app.foldcade.thor.Commands.*;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** The single process-wide owner of root, capability cache and game recovery. */
final class DeviceController {
    private static DeviceController instance;
    static synchronized DeviceController get(Context context) {
        if (instance == null) instance = new DeviceController(context.getApplicationContext());
        return instance;
    }
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "thor-device"));
    final RootSession root = new RootSession();
    private GameOverrides games;
    private boolean initialized;
    private boolean fanCap, lightsCap, brightnessCap, screensCap;
    private int bottomId = -1;
    private Bundle cached;
    private DeviceController(Context context) { this.context = context; }
    <T> Future<T> submit(Callable<T> action) { return executor.submit(action); }
    private GameOverrides games() throws Failure {
        if (games == null) games = new GameOverrides(root, new FileOverrideStore(context.getNoBackupFilesDir()));
        return games;
    }
    static Bundle error(String code, String message) {
        Bundle b = new Bundle(); b.putBoolean("ok", false); b.putString("error", code); b.putString("message", message); return b;
    }
    private Bundle emptyState() {
        Bundle b = new Bundle(); b.putBoolean("ok", true); b.putInt("protocol", 1);
        b.putString("device", "ayn-thor"); b.putString("root", root.state());
        b.putStringArray("capabilities", new String[0]); return b;
    }
    private static boolean bool(Bundle b, String key, boolean fallback) throws Failure {
        if (b == null || !b.containsKey(key)) return fallback;
        Object value = b.get(key);
        if (!(value instanceof Boolean)) throw new Failure("bad_request", key + " must be a boolean");
        return (Boolean) value;
    }
    private static float level(Bundle b) throws Failure {
        Object v = b == null ? null : b.get("level");
        if (!(v instanceof Float) || !Float.isFinite((Float) v)) throw new Failure("bad_request", "level must be a finite float");
        return clamp((Float) v);
    }
    Bundle handle(String method, String arg, Bundle extras) {
        try {
            if (method == null) throw new Failure("bad_request", "Missing method");
            // Parse every argument before any probe or root command.
            Fan fan = null; Lights lights = null; Panel panel = null; Screens screens = null; float brightness = 0;
            switch (method) {
                case "state" -> { return state(bool(extras, "refresh", false)); }
                case "fan.set" -> { fan = Fan.request(arg); if (fan == null) throw new Failure("bad_request", "Unknown fan tier"); }
                case "lights.set" -> { lights = Lights.parse(arg); if (lights == null) throw new Failure("bad_request", "Unknown lights mode"); }
                case "brightness.set" -> { panel = Panel.parse(arg); if (panel == null) throw new Failure("bad_request", "Unknown panel"); brightness = level(extras); }
                case "screens.set" -> { screens = Screens.parse(arg); if (screens == null) throw new Failure("bad_request", "Unknown screen mode"); }
                case "game.begin" -> {
                    Object value = extras == null ? null : extras.get("fan");
                    fan = value instanceof String ? Fan.request((String) value) : null;
                    if (fan == null) throw new Failure("bad_request", "Unknown game fan tier");
                }
                case "game.end" -> {
                    if (games().active() == null) {
                        // A fresh process has no cached state; probe once rather than report root as denied.
                        Bundle b = initialized && cached != null ? new Bundle(cached) : state(false);
                        b.putBoolean("restored", false); return b;
                    }
                }
                default -> throw new Failure("bad_request", "Unknown method");
            }
            // A host can retain discovery across provider process death. Rebuild state lazily
            // for a valid authenticated action too, so an abandoned override can still heal.
            if (!initialized) state(false);
            if (!"granted".equals(root.state())) throw new Failure("no_root", "Open Foldcade Thor or refresh its state after granting root");
            switch (method) {
                case "fan.set" -> { require(fanCap); games().setFan(fan); }
                case "lights.set" -> {
                    require(lightsCap);
                    RootRunner.Result r = root.run(Commands.lights(lights));
                    // stickfx refuses a lit effect while every stick colour is black; say so, so the host can explain it.
                    if (r.code() != 0 && Parsers.needsColor(r.out())) throw new Failure("needs_color", "Every stick LED colour is black");
                    if (r.code() != 0) throw new Failure("failed", "Command failed: " + Commands.lights(lights));
                    verifyLights(lights);
                }
                case "brightness.set" -> {
                    require(brightnessCap);
                    execute(brightnessSet(panel == Panel.TOP ? Display.DEFAULT_DISPLAY : bottomId, brightness));
                    Bundle b = new Bundle(); b.putBoolean("ok", true); b.putFloat("level", brightness); return b;
                }
                case "screens.set" -> {
                    require(screensCap);
                    for (Command command : Commands.screens(screens)) execute(command);
                    RootRunner.Result actual = root.run(SCREEN_STATUS);
                    if (actual.code() != 0 || Parsers.screens(actual.out()) != screens) throw new Failure("failed", "Screen read-back did not match");
                }
                case "game.begin" -> { require(fanCap); games().begin(fan); }
                case "game.end" -> {
                    boolean restored = games().end(); Bundle b = state(false); b.putBoolean("restored", restored); return b;
                }
            }
            return state(false);
        } catch (Failure e) { return error(e.code, e.getMessage()); }
        catch (RuntimeException e) { Log.e("FoldcadeThor", "Device operation failed", e); return error("internal", "Device operation failed"); }
    }
    private static void require(boolean available) throws Failure {
        if (!available) throw new Failure("unsupported", "The required thor-features module is unavailable");
    }
    private void execute(Command command) throws Failure {
        if (root.run(command).code() != 0) throw new Failure("failed", "Command failed: " + command);
    }
    private void verifyLights(Lights wanted) throws Failure {
        RootRunner.Result r = root.run(LIGHTS_GET);
        if (r.code() != 0 || Parsers.lights(r.out()) != wanted) throw new Failure("failed", "Lights read-back did not match");
    }
    private Float brightness(int id) {
        RootRunner.Result r = root.run(brightnessGet(id)); return r.code() == 0 ? Parsers.brightness(r.out()) : null;
    }
    private int bottomDisplay() {
        RootRunner.Result r = root.run(DISPLAYS);
        Integer id = (r.code() == 0 || r.code() == 1) ? Parsers.bottomDisplay(r.out()) : null;
        if (id != null && id != Display.DEFAULT_DISPLAY) return id;
        DisplayManager manager = context.getSystemService(DisplayManager.class);
        if (manager != null) for (Display display : manager.getDisplays()) {
            if (display.getDisplayId() != Display.DEFAULT_DISPLAY) return display.getDisplayId();
        }
        return -1;
    }
    private Bundle state(boolean refresh) throws Failure {
        boolean probing = !initialized || refresh;
        if (probing) { root.probe(refresh); initialized = true; }
        Bundle b = emptyState();
        if (!"granted".equals(root.state())) { cached = new Bundle(b); return b; }
        games();
        if (probing) {
            bottomId = bottomDisplay();
            fanCap = lightsCap = screensCap = true;
            brightnessCap = bottomId >= 0 && root.run(BRIGHTNESS_MODULE).code() == 0;
        }
        List<String> caps = new ArrayList<>();
        if (fanCap) {
            RootRunner.Result r = root.run(FAN_GET); Fan value = r.code() == 0 ? Parsers.fan(r.out()) : null;
            if (value != null) {
                caps.add("fan"); caps.add("gameFan"); b.putString("fan", value.wire);
                GameOverrides.Override active = games().active();
                b.putString("fanBaseline", active == null ? value.wire : active.baseline().wire);
                if (active != null) b.putString("fanGame", active.tier().wire);
            } else if (probing) fanCap = false;
        }
        if (lightsCap) {
            RootRunner.Result r = root.run(LIGHTS_GET); Lights value = r.code() == 0 ? Parsers.lights(r.out()) : null;
            if (value != null) { caps.add("lights"); b.putString("lights", value.wire); }
            else if (probing) lightsCap = false;
        }
        if (brightnessCap) {
            Float top = brightness(Display.DEFAULT_DISPLAY), bottom = brightness(bottomId);
            if (top != null && bottom != null) { caps.add("brightness"); b.putFloat("brightnessTop", top); b.putFloat("brightnessBottom", bottom); }
            else if (probing) brightnessCap = false;
        }
        if (screensCap) {
            RootRunner.Result r = root.run(SCREEN_STATUS); Screens value = r.code() == 0 ? Parsers.screens(r.out()) : null;
            if (value != null) { caps.add("screens"); b.putString("screens", value.wire); }
            else if (probing) screensCap = false;
        }
        if (!"granted".equals(root.state())) b = emptyState();
        else b.putStringArray("capabilities", caps.toArray(new String[0]));
        cached = new Bundle(b); return b;
    }

    /** UI-only debug smoke hook; accepts no intents, caller strings, shell or external commands. */
    String smoke() {
        if (!BuildConfig.DEBUG) return "Unavailable";
        Bundle before = handle("state", null, null);
        if (!"granted".equals(before.getString("root"))) return "Root is required";
        List<String> result = new ArrayList<>();
        try {
            check(handle("fan.set", "quiet", null));
            Bundle game = new Bundle(); game.putString("fan", "performance");
            check(handle("game.begin", null, game));
            Bundle during = check(handle("state", null, null));
            if (!"quiet".equals(during.getString("fanBaseline"))) throw new IllegalStateException("Wrong baseline");
            check(handle("game.end", null, null));
            if (check(handle("game.end", null, null)).getBoolean("restored")) throw new IllegalStateException("End was not idempotent");
            result.add("Fan and game recovery passed");
            Bundle breathe = handle("lights.set", "breathe", null);
            check(handle("lights.set", "off", null));
            result.add(breathe.getBoolean("ok") ? "Lights passed" : "Lights off passed; breathe refused by CLI (see QA log)");
            for (String mode : new String[]{"bottom", "top", "dual"}) check(handle("screens.set", mode, null));
            result.add("Panel order and read-back passed");
            for (String panel : new String[]{"top", "bottom"}) {
                Bundle level = new Bundle(); level.putFloat("level", panel.equals("top") ? .30f : .20f);
                check(handle("brightness.set", panel, level));
                Bundle actual = check(handle("state", null, null));
                float bottom = panel.equals("top") ? before.getFloat("brightnessBottom") : .20f;
                if (Math.abs(actual.getFloat("brightnessTop") - .30f) > .01f || Math.abs(actual.getFloat("brightnessBottom") - bottom) > .01f) {
                    throw new IllegalStateException("Brightness independence/read-back failed");
                }
            }
            result.add("Independent brightness passed");
        } catch (Exception e) { result.add("FAILED: " + e.getMessage()); }
        finally {
            handle("game.end", null, null);
            try {
                Fan old = Fan.parse(before.getString("fan")); if (old != null) games().writeFan(old);
                Lights lights = Lights.parse(before.getString("lights")); if (lights != null) execute(Commands.lights(lights));
                for (Command c : Commands.screens(Screens.DUAL)) execute(c);
                if (before.containsKey("brightnessTop")) execute(brightnessSet(Display.DEFAULT_DISPLAY, before.getFloat("brightnessTop")));
                if (before.containsKey("brightnessBottom")) execute(brightnessSet(bottomId, before.getFloat("brightnessBottom")));
                result.add("Original fan, lights and brightness restored; both panels lit");
            } catch (Exception e) { result.add("RESTORE FAILED: " + e.getMessage()); }
        }
        String report = String.join("\n", result); Log.i("FoldcadeThorQA", report); return report;
    }
    private static Bundle check(Bundle result) {
        if (!result.getBoolean("ok")) throw new IllegalStateException(result.getString("error") + ": " + result.getString("message"));
        return result;
    }
}
