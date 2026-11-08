package de.browars.minigame.targetrush;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class TargetRushSavedData extends SavedData {
    public static final SavedDataType<TargetRushSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("browars", "target_rush_arena"),
            TargetRushSavedData::new,
            TargetRushArenaData.CODEC.xmap(TargetRushSavedData::new, TargetRushSavedData::getData),
            null
    );

    private final TargetRushArenaData data;

    public TargetRushSavedData() {
        this.data = new TargetRushArenaData();
    }

    public TargetRushSavedData(TargetRushArenaData data) {
        this.data = data != null ? data : new TargetRushArenaData();
    }

    public TargetRushArenaData getData() {
        return data;
    }

    public static TargetRushSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        if (overworld != null) {
            return overworld.getDataStorage().computeIfAbsent(TYPE);
        }
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public static void save(ServerLevel level) {
        TargetRushSavedData savedData = get(level);
        savedData.setDirty();

        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        if (overworld != null) {
            overworld.getDataStorage().scheduleSave();
        } else {
            level.getDataStorage().scheduleSave();
        }
    }
}
