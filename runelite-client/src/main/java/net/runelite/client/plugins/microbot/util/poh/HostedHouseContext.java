package net.runelite.client.plugins.microbot.util.poh;

/** House identity changes on observed departure/entry, even when template coordinates repeat. */
public final class HostedHouseContext {
    private long generation;
    private long rejectedGeneration = -1;
    private boolean present;
    private boolean recovering;

    public synchronized void observe(boolean inHouse) {
        if (present != inHouse) {
            present = inHouse;
            generation++;
            rejectedGeneration = -1;
        }
    }
    public synchronized void beginRecovery() {
        reject();
        recovering = true;
    }
    public synchronized void reject() { rejectedGeneration = generation; }
    public synchronized void endRecovery() { recovering = false; }
    public synchronized boolean facilitiesEligible() {
        return !recovering && rejectedGeneration != generation;
    }
    public synchronized long generation() { return generation; }
}
