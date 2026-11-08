package de.browars.minigame.crosswar;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class CrossWarSavedData extends SavedData {
    public static final SavedDataType<CrossWarSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("browars", "crosswar_arena"),
            CrossWarSavedData::new,
            CrossWarArenaData.CODEC.xmap(CrossWarSavedData::new, CrossWarSavedData::getData),
            null
    );

    private final CrossWarArenaData data;

    public CrossWarSavedData() {
        this.data = new CrossWarArenaData();
    }

    public CrossWarSavedData(CrossWarArenaData data) {
        this.data = data != null ? data : new CrossWarArenaData();
    }

    public CrossWarArenaData getData() {
        return data;
    }

    public static CrossWarSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        if (overworld != null) {
            return overworld.getDataStorage().computeIfAbsent(TYPE);
        }
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public static void save(ServerLevel level) {
        CrossWarSavedData savedData = get(level);
        savedData.setDirty();

        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        if (overworld != null) {
            overworld.getDataStorage().scheduleSave();
        }
    }
}
