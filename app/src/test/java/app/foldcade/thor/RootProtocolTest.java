package app.foldcade.thor;

import org.junit.Test;
import static org.junit.Assert.*;

public class RootProtocolTest {
    @Test public void framingIsUniqueAndDoesNotConfuseStatusOutput() {
        String marker = RootProtocol.sentinel();
        assertNotEquals(marker, RootProtocol.sentinel());
        assertNull(RootProtocol.exitCode("balanced", marker));
        assertEquals(Integer.valueOf(127), RootProtocol.exitCode(marker + " 127", marker));
        assertEquals(Integer.valueOf(-1), RootProtocol.exitCode(marker + " garbled", marker));
        assertEquals(Integer.valueOf(-1), RootProtocol.exitCode(marker + " -1", marker));
        assertTrue(RootProtocol.wrap(Commands.FAN_GET, marker).contains("</dev/null"));
        assertTrue(RootProtocol.wrap(Commands.FAN_GET, marker).contains("$?"));
    }
}
