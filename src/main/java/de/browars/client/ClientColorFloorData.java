package de.browars.client;

import de.browars.network.SyncColorFloorDataPayload;
import net.minecraft.core.BlockPos;

public class ClientColorFloorData {
    public static boolean hasArena = false;
    public static BlockPos minPos = BlockPos.ZERO;
    public static BlockPos maxPos = BlockPos.ZERO;
    public static String dimension = "minecraft:overworld";

    public static boolean hasSpawn1 = false;
    public static double s1X = 0, s1Y = 0, s1Z = 0;

    public static boolean hasSpawn2 = false;
    public static double s2X = 0, s2Y = 0, s2Z = 0;

    public static String team1Color = "rot";
    public static String team2Color = "blau";

    public static void reset() {
        hasArena = false;
        minPos = BlockPos.ZERO;
        maxPos = BlockPos.ZERO;
        dimension = "minecraft:overworld";
        hasSpawn1 = false;
        s1X = s1Y = s1Z = 0;
        hasSpawn2 = false;
        s2X = s2Y = s2Z = 0;
        team1Color = "rot";
        team2Color = "blau";
    }

    public static void update(SyncColorFloorDataPayload payload) {
        hasArena = payload.hasArena();
        minPos = payload.minPos();
        maxPos = payload.maxPos();
        dimension = payload.dimension();

        hasSpawn1 = payload.hasSpawn1();
        s1X = payload.s1X();
        s1Y = payload.s1Y();
        s1Z = payload.s1Z();

        hasSpawn2 = payload.hasSpawn2();
        s2X = payload.s2X();
        s2Y = payload.s2Y();
        s2Z = payload.s2Z();

        team1Color = payload.team1Color();
        team2Color = payload.team2Color();
    }
}
