/* SPDX-License-Identifier: Apache-2.0
 * Derived from thor-features' RootSession.java by Wingdom (Apache-2.0).
 * https://github.com/Wingdom/thor-features
 * Uses queue deadlines so a blocked pipe cannot bypass the command timeout.
 */
package app.foldcade.thor;

import android.util.Log;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

final class RootSession implements RootRunner, AutoCloseable {
    private static final String TAG = "FoldcadeThor";
    private static final long TIMEOUT_MS = 10_000;
    private static final int MAX_OUTPUT = 131_072;
    private static final String EOF = "\u0000EOF";
    private Process process;
    private Writer input;
    private BlockingQueue<String> output;
    private String sentinel;
    private boolean retired;
    private String su;
    private String state = "denied";

    String state() { return state; }

    String probe(boolean refresh) {
        if (refresh) { close(); retired = false; }
        su = findSu();
        if (su == null) { state = "absent"; return state; }
        Result r = run(Commands.ID);
        state = r.code() == 0 && r.out().trim().equals("0") ? "granted" : "denied";
        return state;
    }
    private static String findSu() {
        String path = System.getenv("PATH");
        for (String dir : ("/system/bin:/system/xbin:/sbin:" + (path == null ? "" : path)).split(":")) {
            if (dir.isEmpty()) continue;
            File file = new File(dir, "su");
            if (file.isFile() && file.canExecute()) return file.getAbsolutePath();
        }
        return null;
    }
    private static BlockingQueue<String> pump(Process p) {
        BlockingQueue<String> queue = new ArrayBlockingQueue<>(1024);
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.length() > MAX_OUTPUT || !queue.offer(line)) { p.destroy(); break; }
                }
            } catch (IOException ignored) { }
            finally { queue.offer(EOF); }
        }, "thor-root-output");
        thread.setDaemon(true);
        thread.start();
        return queue;
    }
    private boolean open() {
        if (retired) return false;
        if (process != null && process.isAlive()) return true;
        close();
        try {
            sentinel = RootProtocol.sentinel();
            process = new ProcessBuilder(su).redirectErrorStream(true).start();
            output = pump(process);
            input = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
            input.write(RootProtocol.prelude()); input.flush();
            Result proof = exchange(Commands.ID);
            if (proof.code() != 0 || !proof.out().trim().equals("0")) throw new IOException("Session is not uid 0");
            Log.i(TAG, "root session open as uid 0");
            return true;
        } catch (Exception e) { retire(e); return false; }
    }
    private Result exchange(Commands.Command command) throws Exception {
        input.write(RootProtocol.wrap(command, sentinel)); input.flush();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MS);
        StringBuilder text = new StringBuilder();
        while (true) {
            long remaining = deadline - System.nanoTime();
            String line = remaining > 0 ? output.poll(remaining, TimeUnit.NANOSECONDS) : null;
            if (line == null) throw new IOException("Root command timed out");
            if (line.equals(EOF)) throw new IOException("Root shell closed");
            Integer code = RootProtocol.exitCode(line, sentinel);
            if (code != null) return new Result(code, text.toString());
            text.append(line).append('\n');
            if (text.length() > MAX_OUTPUT) throw new IOException("Root output exceeded limit");
        }
    }
    private void retire(Exception e) {
        Log.w(TAG, "Root session retired; using one-shot su", e);
        close(); retired = true;
    }
    @Override public Result run(Commands.Command command) {
        if (su == null) return new Result(-1, "Root has not been probed");
        if (open()) {
            try { return trace(command, exchange(command)); }
            catch (Exception e) { retire(e); }
        }
        return trace(command, oneShot(command));
    }
    private Result trace(Commands.Command command, Result result) {
        if (BuildConfig.DEBUG) Log.d("FoldcadeThorQA", command + " => " + result.code() + ": " + result.out().trim());
        return result;
    }
    private Result oneShot(Commands.Command command) {
        Process child = null;
        try {
            // Every fallback independently proves uid 0 before the fixed command runs.
            String proof = RootProtocol.sentinel();
            String script = "export PATH=/system/bin:/system/xbin:$PATH; export LC_ALL=C; "
                + "if [ \"$(id -u)\" != 0 ]; then exit 126; fi; printf '" + proof + "\\n'; " + command.shell;
            child = new ProcessBuilder(su, "-c", script).redirectErrorStream(true).start();
            BlockingQueue<String> queue = pump(child);
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MS);
            StringBuilder text = new StringBuilder();
            boolean proven = false;
            while (true) {
                long remaining = deadline - System.nanoTime();
                String line = remaining > 0 ? queue.poll(remaining, TimeUnit.NANOSECONDS) : null;
                if (line == null) throw new IOException("One-shot root command timed out");
                if (line.equals(EOF)) break;
                if (!proven && line.equals(proof)) { proven = true; continue; }
                text.append(line).append('\n');
                if (text.length() > MAX_OUTPUT) throw new IOException("Root output exceeded limit");
            }
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0 || !child.waitFor(remaining, TimeUnit.NANOSECONDS)) throw new IOException("Root command did not exit");
            int code = child.exitValue();
            if (!proven) { state = "denied"; return new Result(-1, "su did not grant uid 0"); }
            return new Result(code, text.toString());
        } catch (Exception e) {
            Log.w(TAG, "One-shot root failed", e);
            return new Result(-1, "Root command failed or timed out");
        } finally { if (child != null) child.destroyForcibly(); }
    }
    @Override public void close() {
        // Destroy before closing streams; closing a live reader may block on a read lock.
        if (process != null) process.destroyForcibly();
        try { if (input != null) input.close(); } catch (IOException ignored) { }
        process = null; input = null; output = null;
    }
}
