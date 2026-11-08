package de.browars.util;

import net.minecraft.resources.Identifier;

public final class InputValidation {
    private InputValidation() {}
    public static double boostStrength(double value) { return Double.isFinite(value) ? Math.clamp(value, 0.1, 10.0) : 2.0; }
    public static double upwardHeight(double value) { return Double.isFinite(value) ? Math.clamp(value, 1.0, 30.0) : 5.0; }
    public static boolean teleportTarget(double x, double y, double z, float yaw, float pitch, String dimension) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) && Float.isFinite(yaw) && Float.isFinite(pitch)
                && Math.abs(x) <= 29_999_984 && Math.abs(z) <= 29_999_984 && Math.abs(y) <= 20_000_000
                && dimension != null && Identifier.tryParse(dimension) != null;
    }
}
