package de.browars.minigame.spleef;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class SpleefSavedData extends SavedData {
    public static final SavedDataType<SpleefSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("browars", "spleef_arena"),
            SpleefSavedData::new,
            SpleefArenaData.CODEC.xmap(SpleefSavedData::new, SpleefSavedData::getData),
            null
    );

    private final SpleefArenaData data;

    public SpleefSavedData() {
        this.data = new SpleefArenaData();
    }

    public SpleefSavedData(SpleefArenaData data) {
        this.data = data != null ? data : new SpleefArenaData();
    }

    public SpleefArenaData getData() {
        return data;
    }

    public static SpleefSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        if (overworld != null) {
            return overworld.getDataStorage().computeIfAbsent(TYPE);
        }
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public static void save(ServerLevel level) {
        SpleefSavedData savedData = get(level);
        savedData.setDirty();

        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        if (overworld != null) {
            overworld.getDataStorage().scheduleSave();
        }
    }
}
