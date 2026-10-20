package net.runelite.client.plugins.microbot.util.poh;

import net.runelite.client.plugins.microbot.shortestpath.PohPoolMode;

/** Pool eligibility only; nearest-pool selection and observed drinking stay unchanged. */
public final class PohPoolPolicy {
    private PohPoolPolicy() { }
    public static boolean shouldUse(PohPoolMode mode, boolean prayer, boolean health,
            boolean energy, boolean special, int prayerNow, int prayerMax,
            int healthNow, int healthMax, int runHundredths, int specialTenths) {
        if (mode == PohPoolMode.OFF) return false;
        if (mode != PohPoolMode.AUTO) return true;
        return prayer && prayerMax > 0 && prayerNow >= 0 && prayerNow < prayerMax
            || health && healthMax > 0 && healthNow >= 0 && (long) healthNow * 100 < (long) healthMax * 90
            || energy && runHundredths >= 0 && runHundredths < 8500
            || special && specialTenths >= 0 && specialTenths < 1000;
    }
}
