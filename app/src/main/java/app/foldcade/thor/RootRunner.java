/* SPDX-License-Identifier: Apache-2.0 */
package app.foldcade.thor;

public interface RootRunner {
    record Result(int code, String out) {}
    Result run(Commands.Command command);
}
