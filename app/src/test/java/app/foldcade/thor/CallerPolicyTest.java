package app.foldcade.thor;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class CallerPolicyTest {
    private final String release = CallerPolicy.PINS.get("app.foldcade").iterator().next();
    private final String debug = CallerPolicy.PINS.get("app.foldcade.debug").iterator().next();
    @Test public void pinnedPackagesWithExactUidAndSignerWork() {
        assertTrue(CallerPolicy.allows("app.foldcade", 10123, 10123, Set.of(release)));
        assertTrue(CallerPolicy.allows("app.foldcade.debug", 10124, 10124, Set.of(debug)));
    }
    @Test public void packageIsRequiredAndExact() {
        for (String pkg : new String[]{null, "", "com.android.shell", "app.foldcade.evil", "APP.FOLDCADE", "app.foldcade.thor"}) {
            assertFalse(CallerPolicy.allows(pkg, 10123, 10123, Set.of(release)));
        }
    }
    @Test public void signerIsRequiredAndExact() {
        assertFalse(CallerPolicy.allows("app.foldcade", 10123, 10123, null));
        assertFalse(CallerPolicy.allows("app.foldcade", 10123, 10123, Set.of()));
        assertFalse(CallerPolicy.allows("app.foldcade", 10123, 10123, Set.of(debug)));
        assertFalse(CallerPolicy.allows("app.foldcade.debug", 10123, 10123, Set.of(release)));
        assertFalse(CallerPolicy.allows("app.foldcade", 10123, 10123, Set.of(release.toUpperCase())));
        assertFalse(CallerPolicy.allows("app.foldcade", 10123, 10123, Set.of(release, debug)));
        Set<String> withNull = new HashSet<>(); withNull.add(release); withNull.add(null);
        assertFalse(CallerPolicy.allows("app.foldcade", 10123, 10123, withNull));
    }
    @Test public void uidMustMatchEvenForPinnedSigner() {
        assertFalse(CallerPolicy.allows("app.foldcade", 2000, 10123, Set.of(release)));
        assertFalse(CallerPolicy.allows("app.foldcade", 0, 10123, Set.of(release)));
        assertFalse(CallerPolicy.allows("app.foldcade", -1, -1, Set.of(release)));
    }
}
