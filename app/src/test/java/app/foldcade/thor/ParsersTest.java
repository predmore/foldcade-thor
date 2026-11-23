package app.foldcade.thor;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

public class ParsersTest {
    private String fixture(String name) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/cli/" + name + ".txt")) {
            assertNotNull(in); return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    @Test public void actualFanAndLights() throws Exception {
        assertEquals(Commands.Fan.BALANCED, Parsers.fan(fixture("fan")));
        assertEquals(Commands.Lights.OFF, Parsers.lights(fixture("lights")));
        assertNull(Parsers.fan("fanctl not found")); assertNull(Parsers.lights("off\nunknown"));
    }
    @Test public void actualAwakeAndDozingScreenStatus() throws Exception {
        String awake = fixture("screenblank-awake"), dozing = fixture("screenblank-dozing");
        assertTrue(dozing.contains("device is Dozing"));
        assertEquals(Commands.Screens.DUAL, Parsers.screens(awake));
        assertEquals(Commands.Screens.DUAL, Parsers.screens(dozing));
        assertEquals(Commands.Screens.BOTTOM, Parsers.screens(awake.replace("top     screen : on", "top     screen : OFF")));
        assertEquals(Commands.Screens.TOP, Parsers.screens(awake.replace("bottom  screen : on", "bottom  screen : OFF")));
        assertNull(Parsers.screens("permission denied"));
        assertNull(Parsers.screens("top screen : on"));
        assertNull(Parsers.screens("top screen : OFF\nbottom screen : OFF"));
    }
    @Test public void actualDisplayIdAndBrightness() throws Exception {
        assertEquals(Integer.valueOf(1), Parsers.bottomDisplay(fixture("screenmove")));
        assertEquals(Integer.valueOf(17), Parsers.bottomDisplay(fixture("screenmove").replace("display 1", "display 17")));
        assertNull(Parsers.bottomDisplay("bottom screen : display -1"));
        assertNull(Parsers.bottomDisplay("bottom screen : display 99999999999"));
        assertEquals(.46850392f, Parsers.brightness(fixture("brightness-top")), .00000001f);
        assertEquals(.38188976f, Parsers.brightness(fixture("brightness-bottom")), .00000001f);
        for (String bad : new String[]{"NaN", "Infinity", "-1", "2", "Error", ".5\n.4"}) assertNull(Parsers.brightness(bad));
    }
    @Test public void actualAppwatchWithEnabledIndependentOfDaemon() throws Exception {
        String status = fixture("appwatch");
        assertEquals(Boolean.TRUE, Parsers.appwatchRunning(status));
        assertEquals(Boolean.FALSE, Parsers.appwatchRunning(status.replaceFirst("daemon:.*", "daemon:    not running")));
        assertNull(Parsers.appwatchRunning("enabled: yes\nprocesses: 123"));
        assertNull(Parsers.appwatchRunning("daemon: running evil"));
    }
}
