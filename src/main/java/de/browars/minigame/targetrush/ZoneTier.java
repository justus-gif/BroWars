package de.browars.minigame.targetrush;

public enum ZoneTier {
    BRONZE("Bronze", 1.0, 0xCD7F32, "§6"),
    SILVER("Silber", 1.5, 0xC0C0C0, "§f"),
    GOLD("Gold", 2.0, 0xFFD700, "§e");

    public final String displayName;
    public final double multiplier;
    public final int colorHex;
    public final String colorCode;

    ZoneTier(String displayName, double multiplier, int colorHex, String colorCode) {
        this.displayName = displayName;
        this.multiplier = multiplier;
        this.colorHex = colorHex;
        this.colorCode = colorCode;
    }
}
