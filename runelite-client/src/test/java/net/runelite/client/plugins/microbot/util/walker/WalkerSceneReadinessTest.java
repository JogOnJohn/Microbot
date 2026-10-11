package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.GameState;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class WalkerSceneReadinessTest {
    @Test public void loadingResumesWithoutTreatingItAsLogout() {
        long[] now = {0};
        assertEquals(WalkerSceneReadiness.Result.READY, WalkerSceneReadiness.await(
            () -> now[0] == 0 ? GameState.LOADING : GameState.LOGGED_IN,
            () -> true, () -> false, () -> now[0], ms -> now[0] += ms * 1_000_000L, 8000));
    }

    @Test public void loggedInMissingPlayerIsBounded() {
        long[] now = {0};
        assertEquals(WalkerSceneReadiness.Result.TIMED_OUT, WalkerSceneReadiness.await(
            () -> GameState.LOGGED_IN, () -> false, () -> false, () -> now[0],
            ms -> now[0] += ms * 1_000_000L, 8000));
        assertEquals(8_000_000_000L, now[0]);
    }

    @Test public void actualLogoutIsNotRetried() {
        assertEquals(WalkerSceneReadiness.Result.LOGGED_OUT, WalkerSceneReadiness.await(
            () -> GameState.LOGIN_SCREEN, () -> false, () -> false, () -> 0L,
            ms -> { throw new AssertionError("Logout must not wait"); }, 8000));
    }

    @Test public void cancellationWinsEvenWhenSceneReady() {
        assertEquals(WalkerSceneReadiness.Result.CANCELLED, WalkerSceneReadiness.await(
            () -> GameState.LOGGED_IN, () -> true, () -> true, () -> 0L,
            ms -> { throw new AssertionError("Cancellation must not wait"); }, 8000));
    }

    @Test public void playerSnapshotCanRecoverAfterLoading() {
        long[] now = {0};
        assertEquals(WalkerSceneReadiness.Result.READY, WalkerSceneReadiness.await(
            () -> GameState.LOGGED_IN, () -> now[0] >= 200_000_000L,
            () -> false, () -> now[0], ms -> now[0] += ms * 1_000_000L, 8000));
    }
}
