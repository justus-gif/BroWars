package de.browars.minigame.lavaescape;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class LavaEscapeSavedData extends SavedData {
    public static final SavedDataType<LavaEscapeSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("browars", "lavaescape_arena"),
            LavaEscapeSavedData::new,
            LavaEscapeArenaData.CODEC.xmap(LavaEscapeSavedData::new, LavaEscapeSavedData::getData),
            null
    );

    private final LavaEscapeArenaData data;

    public LavaEscapeSavedData() {
        this.data = new LavaEscapeArenaData();
    }

    public LavaEscapeSavedData(LavaEscapeArenaData data) {
        this.data = data != null ? data : new LavaEscapeArenaData();
    }

    public LavaEscapeArenaData getData() {
        return data;
    }

    public static LavaEscapeSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        if (overworld != null) {
            return overworld.getDataStorage().computeIfAbsent(TYPE);
        }
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public static void save(ServerLevel level) {
        LavaEscapeSavedData savedData = get(level);
        savedData.setDirty();

        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        if (overworld != null) {
            overworld.getDataStorage().scheduleSave();
        }
    }
}
