package app.foldcade.thor;

import static app.foldcade.thor.Commands.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class GameOverridesTest {
    static class Memory implements GameOverrides.Store {
        GameOverrides.Override value;
        boolean fail;
        @Override public GameOverrides.Override load() { return value; }
        @Override public void save(GameOverrides.Override v) throws Failure {
            if (fail) throw new Failure("internal", "disk full"); value = v;
        }
    }
    static class Fake implements RootRunner {
        Fan fan = Fan.BALANCED, appwatchBaseline;
        boolean running = true, missing, ignoreFanWrites, startMissing;
        Fan fanWhenAppwatchStarted;
        Command fail;
        Memory store;
        final List<Command> calls = new ArrayList<>();
        @Override public Result run(Command c) {
            calls.add(c);
            if (c.equals(fail)) return new Result(1, "failed");
            if (c.equals(FAN_GET)) return new Result(0, fan.wire + "\n");
            if (c.equals(APPWATCH_STATUS)) return missing ? new Result(127, "not found") : new Result(0, "daemon: " + (running ? "running (3210)" : "not running") + "\nenabled: yes\n");
            if (c.equals(APPWATCH_STOP)) { running = false; if (appwatchBaseline != null) fan = appwatchBaseline; return new Result(0, ""); }
            if (c.equals(APPWATCH_START)) {
                if (startMissing) return new Result(127, "not found");
                running = true; fanWhenAppwatchStarted = fan; return new Result(0, "");
            }
            for (Fan f : Fan.values()) if (c.equals(Commands.fan(f))) {
                // Recovery must be saved before the game is applied.
                if (f == Fan.PERFORMANCE && store != null) assertNotNull(store.value);
                if (!ignoreFanWrites) fan = f;
                return new Result(0, "");
            }
            throw new AssertionError("Unexpected root command " + c);
        }
    }
    private Memory store = new Memory();
    private Fake root = new Fake();
    private GameOverrides game() throws Failure { root.store = store; return new GameOverrides(root, store); }
    @Test public void beginStopsAppwatchBeforeBaselineAndRestoresOnEnd() throws Exception {
        root.appwatchBaseline = Fan.QUIET;
        GameOverrides g = game(); g.begin(Fan.PERFORMANCE);
        assertEquals(List.of(APPWATCH_STATUS, APPWATCH_STOP, FAN_GET, fan(Fan.PERFORMANCE), FAN_GET), root.calls);
        assertEquals(Fan.QUIET, store.value.baseline()); assertFalse(root.running);
        assertTrue(g.end()); assertEquals(Fan.QUIET, root.fan); assertTrue(root.running); assertNull(store.value);
        root.calls.clear(); assertFalse(g.end()); assertTrue(root.calls.isEmpty());
    }
    @Test public void secondBeginKeepsFirstBaselineAndSurvivesProcessDeath() throws Exception {
        GameOverrides g = game(); g.begin(Fan.QUIET); g.begin(Fan.PERFORMANCE);
        assertEquals(Fan.BALANCED, store.value.baseline());
        assertEquals(1, root.calls.stream().filter(APPWATCH_STOP::equals).count());
        GameOverrides reloaded = game(); assertEquals(Fan.PERFORMANCE, reloaded.active().tier());
        reloaded.end(); assertEquals(Fan.BALANCED, root.fan); assertTrue(root.running);
    }
    @Test public void externalUserChangeSurvivesEnd() throws Exception {
        GameOverrides g = game(); g.begin(Fan.PERFORMANCE); root.fan = Fan.QUIET;
        root.calls.clear(); g.end();
        assertEquals(Fan.QUIET, root.fan); assertEquals(List.of(FAN_GET, APPWATCH_START), root.calls);
    }
    @Test public void fanSetCancelsWithoutBaselineRestoreAndRestartsAppwatch() throws Exception {
        GameOverrides g = game(); g.begin(Fan.PERFORMANCE); root.calls.clear();
        g.setFan(Fan.QUIET); assertEquals(Fan.QUIET, root.fan); assertNull(g.active()); assertTrue(root.running);
        assertFalse(root.calls.contains(fan(Fan.BALANCED)));
        root.calls.clear(); assertFalse(g.end()); assertTrue(root.calls.isEmpty());
    }
    @Test public void stoppedAndMissingAppwatchAreNotStarted() throws Exception {
        for (boolean missing : new boolean[]{false, true}) {
            root = new Fake(); root.running = false; root.missing = missing; store = new Memory();
            GameOverrides g = game(); g.begin(Fan.QUIET); g.end();
            assertFalse(root.running); assertFalse(root.calls.contains(APPWATCH_STOP)); assertFalse(root.calls.contains(APPWATCH_START));
        }
    }
    @Test public void stockDefaultBaselineIsRestored() throws Exception {
        root.fan = Fan.DEFAULT; GameOverrides g = game(); g.begin(Fan.PERFORMANCE); g.end();
        assertEquals(Fan.DEFAULT, root.fan); assertTrue(root.calls.contains(fan(Fan.DEFAULT)));
    }
    @Test public void failedPersistenceCannotChangeFanOrLeaveAppwatchStopped() throws Exception {
        GameOverrides g = game(); store.fail = true;
        assertThrows(Failure.class, () -> g.begin(Fan.PERFORMANCE));
        assertEquals(Fan.BALANCED, root.fan); assertTrue(root.running); assertNull(g.active());
    }
    @Test public void failedFanReadbackIsReportedAndRecoveryRemains() throws Exception {
        GameOverrides g = game(); root.ignoreFanWrites = true;
        assertThrows(Failure.class, () -> g.begin(Fan.PERFORMANCE)); assertNotNull(store.value);
        root.ignoreFanWrites = false; g.end(); assertTrue(root.running);
    }
    @Test public void failedAppwatchStartRetainsRecordForRetry() throws Exception {
        GameOverrides g = game(); g.begin(Fan.PERFORMANCE); root.fail = APPWATCH_START;
        assertThrows(Failure.class, g::end); assertNotNull(g.active());
        root.fail = null; assertTrue(g.end()); assertNull(store.value); assertTrue(root.running);
    }
    @Test public void invalidTierNeverRunsRoot() throws Exception {
        GameOverrides g = game();
        assertThrows(Failure.class, () -> g.begin(Fan.DEFAULT));
        assertThrows(Failure.class, () -> g.setFan(null)); assertTrue(root.calls.isEmpty());
    }
    @Test public void secondBeginKeepsFirstBaselineAndDoesNotStopTwice() throws Exception {
        GameOverrides g = game(); root.appwatchBaseline = Fan.QUIET;
        g.begin(Fan.PERFORMANCE); g.begin(Fan.BALANCED);
        assertEquals(Fan.QUIET, g.active().baseline());
        assertEquals(1, root.calls.stream().filter(APPWATCH_STOP::equals).count());
        g.end(); assertEquals(Fan.QUIET, root.fan); assertTrue(root.running);
    }
    @Test public void userChangeDuringGameIsNeverRestoredOver() throws Exception {
        GameOverrides g = game(); g.begin(Fan.PERFORMANCE); root.fan = Fan.QUIET;
        int start = root.calls.size(); g.end();
        assertEquals(Fan.QUIET, root.fan); assertTrue(root.running);
        assertFalse(root.calls.subList(start, root.calls.size()).contains(Commands.fan(Fan.BALANCED)));
    }
    @Test public void manualFanEndsOverrideWithoutRestoreAndResumesAppwatch() throws Exception {
        GameOverrides g = game(); g.begin(Fan.PERFORMANCE);
        int start = root.calls.size(); g.setFan(Fan.QUIET);
        assertEquals(Fan.QUIET, root.fan); assertNull(g.active()); assertTrue(root.running);
        assertFalse(root.calls.subList(start, root.calls.size()).contains(Commands.fan(Fan.BALANCED)));
        root.calls.clear(); assertFalse(g.end()); assertTrue(root.calls.isEmpty());
    }
    @Test public void manualFanIsWrittenBeforeAppwatchRecordsItsBaseline() throws Exception {
        GameOverrides g = game(); g.begin(Fan.PERFORMANCE); g.setFan(Fan.BALANCED);
        assertEquals(Fan.BALANCED, root.fanWhenAppwatchStarted); assertNull(store.value);
    }
    @Test public void appwatchRemovedDuringGameStillEndsTheOverride() throws Exception {
        GameOverrides g = game(); root.appwatchBaseline = Fan.QUIET; g.begin(Fan.PERFORMANCE); root.startMissing = true;
        assertTrue(g.end()); assertNull(store.value); assertEquals(Fan.QUIET, root.fan);
        g.begin(Fan.PERFORMANCE); g.setFan(Fan.BALANCED); assertNull(store.value);
    }
    @Test public void manualFanWithoutAppwatchLeavesDaemonOff() throws Exception {
        root.running = false; GameOverrides g = game(); g.begin(Fan.PERFORMANCE); g.setFan(Fan.QUIET);
        assertFalse(root.running); assertEquals(Fan.QUIET, root.fan); assertNull(g.active());
        assertFalse(root.calls.contains(APPWATCH_START));
    }
    @Test public void reloadedOverrideSurvivesProcessDeathAndEmptyEndRunsNothing() throws Exception {
        GameOverrides g = game(); assertFalse(g.end()); assertTrue(root.calls.isEmpty());
        g.begin(Fan.PERFORMANCE); GameOverrides reloaded = game();
        assertEquals(Fan.BALANCED, reloaded.active().baseline()); assertTrue(reloaded.end());
        assertEquals(Fan.BALANCED, root.fan); assertNull(store.value);
    }
}
