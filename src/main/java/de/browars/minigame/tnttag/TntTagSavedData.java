package de.browars.minigame.tnttag;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class TntTagSavedData extends SavedData {
    public static final SavedDataType<TntTagSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("browars", "tnttag_arena"),
            TntTagSavedData::new,
            TntTagArenaData.CODEC.xmap(TntTagSavedData::new, TntTagSavedData::getData),
            null
    );

    private final TntTagArenaData data;

    public TntTagSavedData() {
        this.data = new TntTagArenaData();
    }

    public TntTagSavedData(TntTagArenaData data) {
        this.data = data != null ? data : new TntTagArenaData();
    }

    public TntTagArenaData getData() {
        return data;
    }

    public static TntTagSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        if (overworld != null) {
            return overworld.getDataStorage().computeIfAbsent(TYPE);
        }
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public static void save(ServerLevel level) {
        TntTagSavedData savedData = get(level);
        savedData.setDirty();

        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        if (overworld != null) {
            overworld.getDataStorage().scheduleSave();
        }
    }
}
