/* SPDX-License-Identifier: Apache-2.0 */
package app.foldcade.thor;

import java.util.List;
import java.util.Locale;

/** The only shell command factory. No request text is ever concatenated into a shell. */
public final class Commands {
    private Commands() {}
    public enum Fan {
        QUIET("quiet"), BALANCED("balanced"), PERFORMANCE("performance"), DEFAULT("default");
        public final String wire;
        Fan(String wire) { this.wire = wire; }
        public static Fan parse(String v) {
            for (Fan f : values()) if (f.wire.equals(v)) return f;
            return null;
        }
        public static Fan request(String v) { Fan f = parse(v); return f == DEFAULT ? null : f; }
    }
    public enum Lights {
        OFF("off"), CHARGE("charge"), BREATHE("breathe"), USB("usb");
        public final String wire;
        Lights(String wire) { this.wire = wire; }
        public static Lights parse(String v) {
            for (Lights m : values()) if (m.wire.equals(v)) return m;
            return null;
        }
    }
    public enum Panel {
        TOP("top"), BOTTOM("bottom");
        final String wire;
        Panel(String wire) { this.wire = wire; }
        public static Panel parse(String v) {
            for (Panel p : values()) if (p.wire.equals(v)) return p;
            return null;
        }
    }
    public enum Screens {
        DUAL("dual"), TOP("top"), BOTTOM("bottom");
        public final String wire;
        Screens(String wire) { this.wire = wire; }
        public static Screens parse(String v) {
            for (Screens s : values()) if (s.wire.equals(v)) return s;
            return null;
        }
    }
    public static final class Command {
        final String shell;
        private Command(String shell) { this.shell = shell; }
        @Override public String toString() { return shell; }
        @Override public boolean equals(Object o) { return o instanceof Command && shell.equals(((Command) o).shell); }
        @Override public int hashCode() { return shell.hashCode(); }
    }
    public static final Command ID = new Command("id -u");
    public static final Command FAN_GET = new Command("/system/bin/fanctl get");
    public static final Command LIGHTS_GET = new Command("/system/bin/stickfx get");
    public static final Command SCREEN_STATUS = new Command("/system/bin/screenblank status");
    public static final Command DISPLAYS = new Command("/system/bin/screenmove status");
    public static final Command APPWATCH_STATUS = new Command("/system/bin/appwatch status");
    public static final Command APPWATCH_STOP = new Command("/system/bin/appwatch stop");
    public static final Command APPWATCH_START = new Command("/system/bin/appwatch start");
    public static final Command BRIGHTNESS_MODULE = new Command(
        "test -d /data/adb/modules/thor-screenbright && test ! -e /data/adb/modules/thor-screenbright/disable && test ! -e /data/adb/modules/thor-screenbright/remove");
    public static Command fan(Fan v) { return new Command("/system/bin/fanctl " + java.util.Objects.requireNonNull(v).wire); }
    public static Command lights(Lights v) { return new Command("/system/bin/stickfx " + java.util.Objects.requireNonNull(v).wire); }
    public static Command panel(Panel p, boolean lit) {
        return new Command("/system/bin/screenblank " + (lit ? "on " : "off ") + java.util.Objects.requireNonNull(p).wire);
    }
    /** Unblank the destination before blanking the other panel. Never move an app. */
    public static List<Command> screens(Screens mode) {
        return switch (mode) {
            case DUAL -> List.of(panel(Panel.TOP, true), panel(Panel.BOTTOM, true));
            case TOP -> List.of(panel(Panel.TOP, true), panel(Panel.BOTTOM, false));
            case BOTTOM -> List.of(panel(Panel.BOTTOM, true), panel(Panel.TOP, false));
        };
    }
    private static int display(int id) {
        if (id < 0) throw new IllegalArgumentException("Invalid display ID");
        return id;
    }
    public static Command brightnessGet(int id) { return new Command("cmd display get-brightness --id " + display(id)); }
    public static float clamp(float level) {
        if (!Float.isFinite(level)) throw new IllegalArgumentException("Brightness must be finite");
        return Math.max(0.01f, Math.min(1f, level));
    }
    public static Command brightnessSet(int id, float level) {
        return new Command("cmd display set-brightness " + String.format(Locale.ROOT, "%.8f", clamp(level)) + " --id " + display(id));
    }
}
