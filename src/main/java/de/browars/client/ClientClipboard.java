package de.browars.client;

import de.browars.util.PlayerPositionData;
import net.minecraft.client.Minecraft;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClientClipboard {
    private static PlayerPositionData cachedPosition = null;

    private static final Pattern PATTERN_FULL = Pattern.compile(
            "X:\\s*([-+]?\\d*\\.?\\d+),\\s*Y:\\s*([-+]?\\d*\\.?\\d+),\\s*Z:\\s*([-+]?\\d*\\.?\\d+)(?:\\s*\\(Yaw:\\s*([-+]?\\d*\\.?\\d+),\\s*Pitch:\\s*([-+]?\\d*\\.?\\d+)\\))?(?:\\s*\\[([^\\]]+)\\])?"
    );

    private static final Pattern PATTERN_TP = Pattern.compile(
            "(?:/tp|/teleport)?\\s*([-+]?\\d*\\.?\\d+)\\s+([-+]?\\d*\\.?\\d+)\\s+([-+]?\\d*\\.?\\d+)(?:\\s+([-+]?\\d*\\.?\\d+)\\s+([-+]?\\d*\\.?\\d+))?"
    );

    public static void set(double x, double y, double z, float yaw, float pitch, String dimension) {
        cachedPosition = new PlayerPositionData(x, y, z, yaw, pitch, dimension);

        // Also copy formatted text to player's OS system clipboard
        String clipText = String.format(Locale.ROOT, "X: %.2f, Y: %.2f, Z: %.2f (Yaw: %.1f, Pitch: %.1f) [%s]",
                x, y, z, yaw, pitch, dimension);
        try {
            Minecraft.getInstance().keyboardHandler.setClipboard(clipText);
        } catch (Exception ignored) {}
    }

    public static PlayerPositionData get() {
        if (cachedPosition != null) {
            return cachedPosition;
        }

        // Fallback: try parsing from OS clipboard
        try {
            String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (clip != null && !clip.isBlank()) {
                Matcher mFull = PATTERN_FULL.matcher(clip.trim());
                if (mFull.find()) {
                    double x = Double.parseDouble(mFull.group(1));
                    double y = Double.parseDouble(mFull.group(2));
                    double z = Double.parseDouble(mFull.group(3));
                    float yaw = mFull.group(4) != null ? Float.parseFloat(mFull.group(4)) : 0f;
                    float pitch = mFull.group(5) != null ? Float.parseFloat(mFull.group(5)) : 0f;
                    String dim = mFull.group(6) != null ? mFull.group(6).trim() : "minecraft:overworld";
                    return new PlayerPositionData(x, y, z, yaw, pitch, dim);
                }

                Matcher mTp = PATTERN_TP.matcher(clip.trim());
                if (mTp.find()) {
                    double x = Double.parseDouble(mTp.group(1));
                    double y = Double.parseDouble(mTp.group(2));
                    double z = Double.parseDouble(mTp.group(3));
                    float yaw = mTp.group(4) != null ? Float.parseFloat(mTp.group(4)) : 0f;
                    float pitch = mTp.group(5) != null ? Float.parseFloat(mTp.group(5)) : 0f;
                    return new PlayerPositionData(x, y, z, yaw, pitch, "minecraft:overworld");
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    public static boolean hasData() {
        return get() != null;
    }
}
