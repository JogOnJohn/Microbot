package net.runelite.client.plugins.microbot.util.walker;

/** Exception assertions compatible with this tree's JUnit 4.12 dependency. */
public final class WalkerApiAssertions {
    private WalkerApiAssertions() { }

    @FunctionalInterface
    public interface ThrowingAction { void run() throws Throwable; }

    public static <T extends Throwable> T assertThrows(Class<T> expected, ThrowingAction action) {
        try {
            action.run();
        } catch (Throwable failure) {
            if (expected.isInstance(failure)) return expected.cast(failure);
            throw new AssertionError("Expected " + expected.getName() + ", got " + failure, failure);
        }
        throw new AssertionError("Expected " + expected.getName() + " but no exception was thrown");
    }
}
