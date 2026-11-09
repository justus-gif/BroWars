package de.browars.minigame.vaultwar;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class VaultWarSavedData extends SavedData {
    public static final SavedDataType<VaultWarSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("browars", "vaultwar_arena"),
            VaultWarSavedData::new,
            VaultWarArenaData.CODEC.xmap(VaultWarSavedData::new, VaultWarSavedData::getData),
            null
    );

    private final VaultWarArenaData data;

    public VaultWarSavedData() {
        this.data = new VaultWarArenaData();
    }

    public VaultWarSavedData(VaultWarArenaData data) {
        this.data = data != null ? data : new VaultWarArenaData();
    }

    public VaultWarArenaData getData() {
        return data;
    }

    public static VaultWarSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        if (overworld != null) {
            return overworld.getDataStorage().computeIfAbsent(TYPE);
        }
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public static void save(ServerLevel level) {
        VaultWarSavedData savedData = get(level);
        savedData.setDirty();

        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        if (overworld != null) {
            overworld.getDataStorage().scheduleSave();
        }
    }
}
