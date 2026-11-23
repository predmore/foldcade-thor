/* SPDX-License-Identifier: Apache-2.0 */
package app.foldcade.thor;

import static app.foldcade.thor.Commands.*;

/** Serial executor owned. Durable before fan writes, so process death retains recovery data. */
public final class GameOverrides {
    public record Override(Fan tier, Fan baseline, boolean appwatchWasRunning) {}
    public interface Store {
        Override load() throws Failure;
        void save(Override value) throws Failure;
    }
    private final RootRunner root;
    private final Store store;
    private Override active;
    public GameOverrides(RootRunner root, Store store) throws Failure {
        this.root = root; this.store = store; active = store.load();
    }
    public Override active() { return active; }
    private void persist(Override value) throws Failure { store.save(value); active = value; }
    private void execute(Command command) throws Failure {
        if (root.run(command).code() != 0) throw new Failure("failed", "Command failed: " + command);
    }
    /** A module removed since the game began (exit 127) leaves nothing to restart. */
    private void startAppwatch() throws Failure {
        int code = root.run(APPWATCH_START).code();
        if (code != 0 && code != 127) throw new Failure("failed", "Command failed: " + APPWATCH_START);
    }
    public Fan readFan() throws Failure {
        RootRunner.Result r = root.run(FAN_GET);
        Fan f = r.code() == 0 ? Parsers.fan(r.out()) : null;
        if (f == null) throw new Failure("failed", "Could not read fan tier");
        return f;
    }
    public void writeFan(Fan fan) throws Failure {
        execute(Commands.fan(fan));
        if (readFan() != fan) throw new Failure("failed", "Fan read-back did not match " + fan.wire);
    }
    public void begin(Fan tier) throws Failure {
        if (tier == null || tier == Fan.DEFAULT) throw new Failure("bad_request", "Unknown game fan tier");
        Override previous = active;
        Fan baseline;
        boolean running;
        if (previous == null) {
            RootRunner.Result status = root.run(APPWATCH_STATUS);
            Boolean parsed = status.code() == 0 ? Parsers.appwatchRunning(status.out()) : null;
            // Appwatch is optional. Only a missing executable counts as absent.
            if (parsed == null && status.code() != 127) throw new Failure("failed", "Could not read appwatch status");
            running = Boolean.TRUE.equals(parsed);
            if (running) execute(APPWATCH_STOP);
            try { baseline = readFan(); }
            catch (Failure e) { if (running) execute(APPWATCH_START); throw e; }
        } else {
            baseline = previous.baseline(); running = previous.appwatchWasRunning();
        }
        Override next = new Override(tier, baseline, running);
        try { persist(next); }
        catch (Failure e) { if (previous == null && running) execute(APPWATCH_START); throw e; }
        try { writeFan(tier); }
        catch (Failure e) {
            // Keep the first game's recovery record if the second write never took effect.
            if (previous != null && readFan() == previous.tier()) persist(previous);
            throw e;
        }
    }
    public boolean end() throws Failure {
        Override old = active;
        if (old == null) return false;
        if (readFan() == old.tier()) writeFan(old.baseline());
        if (old.appwatchWasRunning()) startAppwatch();
        persist(null);
        return true;
    }
    public void setFan(Fan fan) throws Failure {
        if (fan == null || fan == Fan.DEFAULT) throw new Failure("bad_request", "Unknown fan tier");
        Override old = active;
        // Write first. appwatch's start-up pass records `fanctl get` as its baseline, so it
        // must read the user's choice, not the game's tier. If the process dies before the
        // record clears, game.end sees the fan off the game's tier, keeps it, and restarts appwatch.
        writeFan(fan);
        if (old != null) {
            if (old.appwatchWasRunning()) startAppwatch();
            persist(null);
        }
    }
}
