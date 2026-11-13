package de.browars.gameshow;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class GameshowSavedData extends SavedData {
    public static final SavedDataType<GameshowSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("browars", "gameshow_data"),
            GameshowSavedData::new,
            GameshowData.CODEC.xmap(GameshowSavedData::new, GameshowSavedData::getData),
            null
    );

    private final GameshowData data;

    public GameshowSavedData() {
        this.data = new GameshowData();
    }

    public GameshowSavedData(GameshowData data) {
        this.data = data != null ? data : new GameshowData();
    }

    public GameshowData getData() {
        return data;
    }

    public static GameshowSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        if (overworld != null) {
            return overworld.getDataStorage().computeIfAbsent(TYPE);
        }
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public static void save(ServerLevel level) {
        GameshowSavedData savedData = get(level);
        savedData.setDirty();

        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        if (overworld != null) {
            overworld.getDataStorage().scheduleSave();
        } else {
            level.getDataStorage().scheduleSave();
        }
    }
}
