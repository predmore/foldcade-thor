/* SPDX-License-Identifier: Apache-2.0 */
package app.foldcade.thor;

final class Failure extends Exception {
    final String code;
    Failure(String code, String message) { super(message); this.code = code; }
}
