package de.browars.minigame.anvilrain;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class AnvilRainSavedData extends SavedData {
    public static final SavedDataType<AnvilRainSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("browars", "anvil_rain_arena"), AnvilRainSavedData::new,
            AnvilRainArenaData.CODEC.xmap(AnvilRainSavedData::new, AnvilRainSavedData::getData), null);
    private AnvilRainArenaData data;
    public AnvilRainSavedData() { this(new AnvilRainArenaData()); }
    public AnvilRainSavedData(AnvilRainArenaData data) { this.data = data; }
    public AnvilRainArenaData getData() { return data; }
    public void setData(AnvilRainArenaData data) { this.data = data; setDirty(); }
    public static AnvilRainSavedData get(ServerLevel level) { return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE); }
    public static void save(ServerLevel level) {
        get(level).setDirty();
        level.getServer().overworld().getDataStorage().scheduleSave();
    }
}
