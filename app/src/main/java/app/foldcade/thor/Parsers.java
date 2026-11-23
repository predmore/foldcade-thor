/* SPDX-License-Identifier: Apache-2.0 */
package app.foldcade.thor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Screen field matching follows thor-features' thor-screenpanel (Wingdom, Apache-2.0). */
public final class Parsers {
    private Parsers() {}
    public static Commands.Fan fan(String out) { return Commands.Fan.parse(out.trim()); }
    public static Commands.Lights lights(String out) { return Commands.Lights.parse(out.trim()); }
    public static Commands.Screens screens(String out) {
        Boolean top = panel(out, "top"), bottom = panel(out, "bottom");
        if (top == null || bottom == null) return null;
        // Sleep blanks both panels outside screenblank's control. Both light on wake.
        if (!top && !bottom) return Pattern.compile("(?m)^note\\s*:\\s*device is ").matcher(out).find() ? Commands.Screens.DUAL : null;
        return top && bottom ? Commands.Screens.DUAL : top ? Commands.Screens.TOP : Commands.Screens.BOTTOM;
    }
    private static Boolean panel(String out, String name) {
        Matcher m = Pattern.compile("(?m)^" + name + "\\s+screen\\s*:\\s*(on|OFF)\\b").matcher(out);
        return m.find() ? "on".equals(m.group(1)) : null;
    }
    public static Integer bottomDisplay(String out) {
        Matcher m = Pattern.compile("(?m)^bottom\\s+screen\\s*:\\s*display\\s+(\\d+)\\s*$").matcher(out);
        if (!m.find()) return null;
        try { return Integer.valueOf(m.group(1)); } catch (NumberFormatException e) { return null; }
    }
    public static Float brightness(String out) {
        try {
            float n = Float.parseFloat(out.trim());
            return Float.isFinite(n) && n >= 0 && n <= 1 ? n : null;
        } catch (NumberFormatException e) { return null; }
    }
    public static Boolean appwatchRunning(String out) {
        Matcher m = Pattern.compile("(?m)^daemon:\\s*(running(?: \\(\\d+\\))?|not running)\\s*$").matcher(out);
        return m.find() ? m.group(1).startsWith("running") : null;
    }
}
