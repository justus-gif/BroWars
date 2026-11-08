package de.browars.minigame.anvilrain;

import net.minecraft.world.phys.AABB;

/** Deterministic motion and difficulty, shared by gameplay and regression checks. */
public final class AnvilRainMotion {
    public static final int DROP_HEIGHT = 20;
    public static final int MAX_ANVILS = 100;
    // The rain reaches full intensity after 90 seconds: noticeably sooner than
    // before, but still long enough for players to learn the rhythm.
    private static final int DIFFICULTY_RAMP_TICKS = 1800;
    private AnvilRainMotion() {}
    private static double difficulty(int ticks) { return Math.clamp(ticks / (double) DIFFICULTY_RAMP_TICKS, 0, 1); }
    /** Starts as a controlled glide, then accelerates sharply near the floor. */
    public static int fallDuration(int ticks) { return (int) Math.round(140 - 80 * difficulty(ticks)); }
    public static int spawnInterval(int ticks) { return (int) Math.round(16 - 12 * difficulty(ticks)); }
    public static double remainingHeight(double progress) {
        double t = Math.clamp(progress, 0, 1);
        return DROP_HEIGHT * (1 - 0.16 * t - 0.84 * t * t * t);
    }
    public static AABB sweptHitbox(double x, double z, double previousY, double nextY) {
        return new AABB(x - 0.43, Math.min(previousY, nextY), z - 0.43,
                x + 0.43, Math.max(previousY, nextY) + 1, z + 0.43);
    }
}
