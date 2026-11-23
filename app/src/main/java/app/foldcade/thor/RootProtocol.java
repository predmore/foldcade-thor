/* SPDX-License-Identifier: Apache-2.0
 * Derived from thor-features' RootProtocol.java by Wingdom (Apache-2.0).
 * https://github.com/Wingdom/thor-features
 */
package app.foldcade.thor;

import java.util.UUID;

final class RootProtocol {
    private RootProtocol() {}
    static String sentinel() { return "__foldcade_" + UUID.randomUUID().toString().replace("-", "") + "__"; }
    static String wrap(Commands.Command command, String sentinel) {
        return "( " + command.shell + " ) </dev/null 2>&1; printf '\\n" + sentinel + " %s\\n' \"$?\"\n";
    }
    static Integer exitCode(String line, String sentinel) {
        if (!line.startsWith(sentinel + " ")) return null;
        try { int code = Integer.parseInt(line.substring(sentinel.length() + 1)); return code >= 0 ? code : -1; }
        catch (NumberFormatException e) { return -1; }
    }
    static String prelude() { return "export PATH=/system/bin:/system/xbin:$PATH\nexport LC_ALL=C\numask 022\n"; }
}
