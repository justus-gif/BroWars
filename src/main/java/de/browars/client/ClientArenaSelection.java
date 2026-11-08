package de.browars.client;

import net.minecraft.core.BlockPos;

public class ClientArenaSelection {
    private static BlockPos pos1 = null;
    private static BlockPos pos2 = null;

    public static void setPos1(BlockPos p) {
        pos1 = p;
    }

    public static void setPos2(BlockPos p) {
        pos2 = p;
    }

    public static void set(BlockPos p1, BlockPos p2) {
        pos1 = p1;
        pos2 = p2;
    }

    public static BlockPos getPos1() {
        return pos1;
    }

    public static BlockPos getPos2() {
        return pos2;
    }
}
