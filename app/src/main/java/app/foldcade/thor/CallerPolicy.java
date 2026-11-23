/* SPDX-License-Identifier: Apache-2.0 */
package app.foldcade.thor;

import java.util.Map;
import java.util.Set;

public final class CallerPolicy {
    private CallerPolicy() {}
    public static final Map<String, Set<String>> PINS = Map.of(
        "app.foldcade", Set.of("36f6883086e694e0a8b245421543063e6b96450970bd4030198543a59e6581ff"),
        "app.foldcade.debug", Set.of("b8069324c56de0db74f96afc444763e97b71dfae33440a95755b380b74f36bd2"));

    public static boolean allows(String pkg, int callingUid, int packageUid, Set<String> signers) {
        return pkg != null && callingUid >= 0 && callingUid == packageUid
            && PINS.containsKey(pkg) && PINS.get(pkg).equals(signers);
    }
}
