package de.browars.minigame.paintwar;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class PaintWarSavedData extends SavedData {
    public static final SavedDataType<PaintWarSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("browars", "paintwar_arena"),
            PaintWarSavedData::new,
            PaintWarArenaData.CODEC.xmap(PaintWarSavedData::new, PaintWarSavedData::getData),
            null
    );

    private final PaintWarArenaData data;

    public PaintWarSavedData() {
        this.data = new PaintWarArenaData();
    }

    public PaintWarSavedData(PaintWarArenaData data) {
        this.data = data != null ? data : new PaintWarArenaData();
    }

    public PaintWarArenaData getData() {
        return data;
    }

    public static PaintWarSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        if (overworld != null) {
            return overworld.getDataStorage().computeIfAbsent(TYPE);
        }
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public static void save(ServerLevel level) {
        PaintWarSavedData savedData = get(level);
        savedData.setDirty();

        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        if (overworld != null) {
            overworld.getDataStorage().scheduleSave();
        }
    }
}
