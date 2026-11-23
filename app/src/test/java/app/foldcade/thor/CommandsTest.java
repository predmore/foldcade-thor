package app.foldcade.thor;

import static app.foldcade.thor.Commands.*;
import java.util.List;
import java.util.Locale;
import org.junit.Test;
import static org.junit.Assert.*;

public class CommandsTest {
    @Test public void requestsOnlyAcceptExactAllowlists() {
        for (String bad : new String[]{null, "", "default", " quiet", "QUIET", "quiet; id", "$(id)", "quiet\n"}) assertNull(Fan.request(bad));
        assertEquals(Fan.DEFAULT, Fan.parse("default"));
        for (String bad : new String[]{null, "ON", "off;id", "breathe ", "usb\n"}) assertNull(Lights.parse(bad));
        assertNull(Panel.parse("1")); assertNull(Screens.parse("dual;id"));
        assertEquals(3, java.util.Arrays.stream(Fan.values()).filter(f -> Fan.request(f.wire) != null).count());
        for (Fan f : Fan.values()) assertEquals("/system/bin/fanctl " + f.wire, fan(f).toString());
        for (Lights l : Lights.values()) assertEquals("/system/bin/stickfx " + l.wire, lights(l).toString());
    }
    @Test public void unblankAlwaysPrecedesBlankAndNeverMovesApps() {
        assertEquals(List.of(panel(Panel.TOP, true), panel(Panel.BOTTOM, true)), screens(Screens.DUAL));
        assertEquals(List.of(panel(Panel.TOP, true), panel(Panel.BOTTOM, false)), screens(Screens.TOP));
        assertEquals(List.of(panel(Panel.BOTTOM, true), panel(Panel.TOP, false)), screens(Screens.BOTTOM));
        // These plans work from every current state, including reapplication after wake.
        for (Screens from : Screens.values()) for (Screens to : Screens.values()) {
            boolean top = from != Screens.BOTTOM, bottom = from != Screens.TOP;
            for (Command op : screens(to)) {
                if (op.equals(panel(Panel.TOP, true))) top = true;
                if (op.equals(panel(Panel.BOTTOM, true))) bottom = true;
                if (op.equals(panel(Panel.TOP, false))) top = false;
                if (op.equals(panel(Panel.BOTTOM, false))) bottom = false;
                assertTrue(top || bottom); assertFalse(op.toString().contains("screenmove"));
            }
            assertEquals(to != Screens.BOTTOM, top); assertEquals(to != Screens.TOP, bottom);
        }
    }
    @Test public void brightnessIsClampedFiniteLocaleIndependentAndIdsAreNumeric() {
        Locale old = Locale.getDefault();
        try {
            Locale.setDefault(Locale.FRANCE);
            assertEquals("cmd display set-brightness 0.25000000 --id 7", brightnessSet(7, .25f).toString());
            assertEquals("cmd display set-brightness 0.01000000 --id 0", brightnessSet(0, -10).toString());
            assertEquals("cmd display set-brightness 1.00000000 --id 2", brightnessSet(2, 10).toString());
        } finally { Locale.setDefault(old); }
        for (float bad : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> brightnessSet(0, bad));
        }
        assertThrows(IllegalArgumentException.class, () -> brightnessGet(-1));
    }
    @Test public void brightnessModuleChecksBothDisableMarkers() {
        assertTrue(BRIGHTNESS_MODULE.toString().contains("test -d /data/adb/modules/thor-screenbright"));
        assertTrue(BRIGHTNESS_MODULE.toString().contains("test ! -e /data/adb/modules/thor-screenbright/disable"));
        assertTrue(BRIGHTNESS_MODULE.toString().contains("test ! -e /data/adb/modules/thor-screenbright/remove"));
    }
}
