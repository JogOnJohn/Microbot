package net.runelite.client.plugins.microbot.accountselector;

/**
 * Scripts post this event before a terminal logout to prevent automatic re-login.
 * AutoLogin resumes when another script starts, after a manual login, or on AutoLogin restart.
 * A request that does not reach the login screen within {@link #PENDING_LOGOUT_MILLIS} expires.
 * A renewal restarts that deadline only while the earlier request is still pending, so it never
 * re-blocks AutoLogin after a restart or a manual login cleared it.
 * Ordinary logouts must not post this event.
 */
public final class AutoLoginSuppressionRequest {
    public static final long PENDING_LOGOUT_MILLIS = 15_000L;

    private final boolean renewal;

    public AutoLoginSuppressionRequest() {
        this(false);
    }

    private AutoLoginSuppressionRequest(boolean renewal) {
        this.renewal = renewal;
    }

    public static AutoLoginSuppressionRequest renewal() {
        return new AutoLoginSuppressionRequest(true);
    }

    public boolean isRenewal() {
        return renewal;
    }
}
