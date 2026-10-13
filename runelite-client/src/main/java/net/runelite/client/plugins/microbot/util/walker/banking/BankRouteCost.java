package net.runelite.client.plugins.microbot.util.walker.banking;

/** Interaction overhead expressed in the bank comparison's running-tile equivalents. */
public final class BankRouteCost {
    private BankRouteCost() { }

    public static int interactionOverheadTiles(int seconds, boolean bankAlreadyOpen) {
        long milliseconds = Math.min(60, Math.max(0, seconds)) * 1000L;
        // The observed seven-second sequence spent about two seconds opening the bank.
        if (bankAlreadyOpen) milliseconds = Math.max(0L, milliseconds - 2000L);
        return (int) (((milliseconds + 599L) / 600L) * 2L);
    }

    public static boolean prefersBank(int direct, int bank, int overhead, int minimumSaving,
            boolean preferTie) {
        if (bank < 0 || bank == Integer.MAX_VALUE) return false;
        if (direct < 0 || direct == Integer.MAX_VALUE) return true;
        long saving = (long) direct - bank - Math.max(0, overhead);
        return saving >= Math.max(0, minimumSaving) && (saving > 0 || preferTie);
    }

    public static boolean cannotBeatDirect(int direct, int overhead, int minimumSaving) {
        return direct >= 0 && direct != Integer.MAX_VALUE
                && (long) direct < (long) Math.max(0, overhead) + Math.max(0, minimumSaving);
    }
}
