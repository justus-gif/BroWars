package de.browars.client.minigame;

public final class PaintWarRouletteClientHolder {
    private static int rewardOrdinal = -1;
    private static int totalSpinTicks = 0;
    private static int remainingSpinTicks = 0;
    private static int holdTicks = 0;

    private PaintWarRouletteClientHolder() {}

    public static void startOrUpdate(int ordinal, int spinTicks) {
        if (ordinal < 0 || spinTicks <= 0) {
            reset();
            return;
        }
        rewardOrdinal = ordinal;
        totalSpinTicks = spinTicks;
        remainingSpinTicks = spinTicks;
        holdTicks = 44; // Hold won item for 2.2 seconds after spin ends
    }

    public static void clientTick() {
        if (remainingSpinTicks > 0) {
            remainingSpinTicks--;
        } else if (holdTicks > 0) {
            holdTicks--;
            if (holdTicks == 0) {
                rewardOrdinal = -1;
            }
        }
    }

    public static boolean isActive() {
        return rewardOrdinal >= 0 && (remainingSpinTicks > 0 || holdTicks > 0);
    }

    public static int getRewardOrdinal() {
        return rewardOrdinal;
    }

    public static int getTotalSpinTicks() {
        return totalSpinTicks;
    }

    public static int getRemainingSpinTicks() {
        return remainingSpinTicks;
    }

    public static int getHoldTicks() {
        return holdTicks;
    }

    public static void reset() {
        rewardOrdinal = -1;
        totalSpinTicks = 0;
        remainingSpinTicks = 0;
        holdTicks = 0;
    }
}
