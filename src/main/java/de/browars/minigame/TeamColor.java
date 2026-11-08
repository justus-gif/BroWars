package de.browars.minigame;

import java.util.Locale;

public enum TeamColor {
    BROWN("brown", "Brown", 0x835432, "§6", "braun"),
    RED("red", "Red", 0xDC2626, "§c", "rot"),
    ORANGE("orange", "Orange", 0xEA580C, "§6", "orange"),
    YELLOW("yellow", "Yellow", 0xEAB308, "§e", "gelb"),
    LIME("lime", "Lime", 0x84CC16, "§a", "lime"),
    GREEN("green", "Green", 0x16A34A, "§2", "gruen", "grün"),
    LIGHT_BLUE("light_blue", "Light Blue", 0x0EA5E9, "§b", "hellblau"),
    BLUE("blue", "Blue", 0x2563EB, "§9", "blau"),
    PURPLE("purple", "Purple", 0x9333EA, "§5", "purple"),
    MAGENTA("magenta", "Magenta", 0xD946EF, "§d", "magenta"),
    PINK("pink", "Pink", 0xFB7185, "§d", "rosa");

    public final String id;
    public final String displayName;
    public final int hexColor;
    public final String chatPrefix;
    public final String[] aliases;

    // Compatibility references
    public static final TeamColor ROT = RED;
    public static final TeamColor BLAU = BLUE;

    TeamColor(String id, String displayName, int hexColor, String chatPrefix, String... aliases) {
        this.id = id;
        this.displayName = displayName;
        this.hexColor = hexColor;
        this.chatPrefix = chatPrefix;
        this.aliases = aliases;
    }

    public static TeamColor fromString(String str, TeamColor fallback) {
        if (str == null) return fallback;
        String s = str.trim().toLowerCase(Locale.ROOT)
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue")
                .replace("-", "_").replace(" ", "_");
        for (TeamColor c : values()) {
            if (c.id.equalsIgnoreCase(s) || c.name().equalsIgnoreCase(s)) {
                return c;
            }
            if (c.aliases != null) {
                for (String alias : c.aliases) {
                    if (alias.equalsIgnoreCase(s)) {
                        return c;
                    }
                }
            }
        }
        return fallback;
    }

    public net.minecraft.world.level.block.Block getConcreteBlock() {
        return switch (this) {
            case BROWN -> net.minecraft.world.level.block.Blocks.CONCRETE.brown();
            case RED -> net.minecraft.world.level.block.Blocks.CONCRETE.red();
            case ORANGE -> net.minecraft.world.level.block.Blocks.CONCRETE.orange();
            case YELLOW -> net.minecraft.world.level.block.Blocks.CONCRETE.yellow();
            case LIME -> net.minecraft.world.level.block.Blocks.CONCRETE.lime();
            case GREEN -> net.minecraft.world.level.block.Blocks.CONCRETE.green();
            case LIGHT_BLUE -> net.minecraft.world.level.block.Blocks.CONCRETE.lightBlue();
            case BLUE -> net.minecraft.world.level.block.Blocks.CONCRETE.blue();
            case PURPLE -> net.minecraft.world.level.block.Blocks.CONCRETE.purple();
            case MAGENTA -> net.minecraft.world.level.block.Blocks.CONCRETE.magenta();
            case PINK -> net.minecraft.world.level.block.Blocks.CONCRETE.pink();
        };
    }

    public net.minecraft.world.level.block.Block getConcreteSlabBlock() {
        return switch (this) {
            case BROWN -> net.minecraft.world.level.block.Blocks.CONCRETE_SLAB.brown();
            case RED -> net.minecraft.world.level.block.Blocks.CONCRETE_SLAB.red();
            case ORANGE -> net.minecraft.world.level.block.Blocks.CONCRETE_SLAB.orange();
            case YELLOW -> net.minecraft.world.level.block.Blocks.CONCRETE_SLAB.yellow();
            case LIME -> net.minecraft.world.level.block.Blocks.CONCRETE_SLAB.lime();
            case GREEN -> net.minecraft.world.level.block.Blocks.CONCRETE_SLAB.green();
            case LIGHT_BLUE -> net.minecraft.world.level.block.Blocks.CONCRETE_SLAB.lightBlue();
            case BLUE -> net.minecraft.world.level.block.Blocks.CONCRETE_SLAB.blue();
            case PURPLE -> net.minecraft.world.level.block.Blocks.CONCRETE_SLAB.purple();
            case MAGENTA -> net.minecraft.world.level.block.Blocks.CONCRETE_SLAB.magenta();
            case PINK -> net.minecraft.world.level.block.Blocks.CONCRETE_SLAB.pink();
        };
    }

    public net.minecraft.world.level.block.Block getConcreteStairsBlock() {
        return switch (this) {
            case BROWN -> net.minecraft.world.level.block.Blocks.CONCRETE_STAIRS.brown();
            case RED -> net.minecraft.world.level.block.Blocks.CONCRETE_STAIRS.red();
            case ORANGE -> net.minecraft.world.level.block.Blocks.CONCRETE_STAIRS.orange();
            case YELLOW -> net.minecraft.world.level.block.Blocks.CONCRETE_STAIRS.yellow();
            case LIME -> net.minecraft.world.level.block.Blocks.CONCRETE_STAIRS.lime();
            case GREEN -> net.minecraft.world.level.block.Blocks.CONCRETE_STAIRS.green();
            case LIGHT_BLUE -> net.minecraft.world.level.block.Blocks.CONCRETE_STAIRS.lightBlue();
            case BLUE -> net.minecraft.world.level.block.Blocks.CONCRETE_STAIRS.blue();
            case PURPLE -> net.minecraft.world.level.block.Blocks.CONCRETE_STAIRS.purple();
            case MAGENTA -> net.minecraft.world.level.block.Blocks.CONCRETE_STAIRS.magenta();
            case PINK -> net.minecraft.world.level.block.Blocks.CONCRETE_STAIRS.pink();
        };
    }

    public boolean matchesTeamConcrete(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(getConcreteBlock()) || state.is(getConcreteSlabBlock()) || state.is(getConcreteStairsBlock());
    }

    public net.minecraft.world.level.block.state.BlockState applyTeamColor(net.minecraft.world.level.block.state.BlockState currentState) {
        if (currentState.getBlock() instanceof net.minecraft.world.level.block.SlabBlock) {
            return getConcreteSlabBlock().withPropertiesOf(currentState);
        }
        if (currentState.getBlock() instanceof net.minecraft.world.level.block.StairBlock) {
            return getConcreteStairsBlock().withPropertiesOf(currentState);
        }
        return getConcreteBlock().defaultBlockState();
    }
}
