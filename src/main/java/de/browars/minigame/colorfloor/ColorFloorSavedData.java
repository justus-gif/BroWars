package de.browars.minigame.colorfloor;

import de.browars.network.SyncColorFloorDataPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class ColorFloorSavedData extends SavedData {
    public static final SavedDataType<ColorFloorSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("browars", "color_floor_arena"),
            ColorFloorSavedData::new,
            ColorFloorArenaData.CODEC.xmap(ColorFloorSavedData::new, ColorFloorSavedData::getData),
            null
    );

    private final ColorFloorArenaData data;

    public ColorFloorSavedData() {
        this.data = new ColorFloorArenaData();
    }

    public ColorFloorSavedData(ColorFloorArenaData data) {
        this.data = data != null ? data : new ColorFloorArenaData();
    }

    public ColorFloorArenaData getData() {
        return data;
    }

    public static ColorFloorSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        if (overworld != null) {
            return overworld.getDataStorage().computeIfAbsent(TYPE);
        }
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public static void saveAndSync(ServerLevel level) {
        ColorFloorSavedData savedData = get(level);
        savedData.setDirty();

        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        if (overworld != null) {
            overworld.getDataStorage().scheduleSave();
        }

        syncToAll(level);
    }

    public static void syncToAll(ServerLevel level) {
        ColorFloorSavedData savedData = get(level);
        ColorFloorArenaData d = savedData.getData();
        SyncColorFloorDataPayload payload = new SyncColorFloorDataPayload(
                d.hasArena(), d.getMinPos(), d.getMaxPos(), d.getDimension().identifier().toString(),
                d.hasSpawn1(), d.getSpawn1X(), d.getSpawn1Y(), d.getSpawn1Z(),
                d.hasSpawn2(), d.getSpawn2X(), d.getSpawn2Y(), d.getSpawn2Z(),
                d.getTeam1Color(), d.getTeam2Color()
        );
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    public static void syncToPlayer(ServerPlayer player) {
        ColorFloorSavedData savedData = get(player.level());
        ColorFloorArenaData d = savedData.getData();
        SyncColorFloorDataPayload payload = new SyncColorFloorDataPayload(
                d.hasArena(), d.getMinPos(), d.getMaxPos(), d.getDimension().identifier().toString(),
                d.hasSpawn1(), d.getSpawn1X(), d.getSpawn1Y(), d.getSpawn1Z(),
                d.hasSpawn2(), d.getSpawn2X(), d.getSpawn2Y(), d.getSpawn2Z(),
                d.getTeam1Color(), d.getTeam2Color()
        );
        ServerPlayNetworking.send(player, payload);
    }
}
