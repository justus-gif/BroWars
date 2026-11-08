package de.browars.minigame.sumo;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class SumoSavedData extends SavedData {
    public static final SavedDataType<SumoSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("browars", "sumo_arena"),
            SumoSavedData::new,
            SumoArenaData.CODEC.xmap(SumoSavedData::new, SumoSavedData::getData),
            null
    );

    private final SumoArenaData data;

    public SumoSavedData() {
        this.data = new SumoArenaData();
    }

    public SumoSavedData(SumoArenaData data) {
        this.data = data != null ? data : new SumoArenaData();
    }

    public SumoArenaData getData() {
        return data;
    }

    public static SumoSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        if (overworld != null) {
            return overworld.getDataStorage().computeIfAbsent(TYPE);
        }
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public static void save(ServerLevel level) {
        SumoSavedData savedData = get(level);
        savedData.setDirty();

        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        if (overworld != null) {
            overworld.getDataStorage().scheduleSave();
        }
    }
}
