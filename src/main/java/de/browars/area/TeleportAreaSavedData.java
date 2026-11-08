package de.browars.area;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;

public class TeleportAreaSavedData extends SavedData {
    private final Map<String, TeleportArea> areas = new LinkedHashMap<>();

    public static final Codec<TeleportAreaSavedData> CODEC = TeleportArea.CODEC.listOf().xmap(
            TeleportAreaSavedData::new,
            TeleportAreaSavedData::getAreasList
    );

    public static final SavedDataType<TeleportAreaSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("browars", "teleport_areas"),
            TeleportAreaSavedData::new,
            CODEC,
            null
    );

    public TeleportAreaSavedData() {
        super();
    }

    public TeleportAreaSavedData(List<TeleportArea> list) {
        super();
        for (TeleportArea area : list) {
            this.areas.put(area.getId(), area);
        }
    }

    public List<TeleportArea> getAreasList() {
        return new ArrayList<>(areas.values());
    }

    public Collection<TeleportArea> getAllAreas() {
        return areas.values();
    }

    public TeleportArea getArea(String id) {
        return areas.get(id);
    }

    public void putArea(TeleportArea area) {
        areas.put(area.getId(), area);
        setDirty();
    }

    public boolean removeArea(String id) {
        TeleportArea removed = areas.remove(id);
        if (removed != null) {
            setDirty();
            return true;
        }
        return false;
    }

    public List<TeleportArea> getAreasInDimension(ResourceKey<Level> dim) {
        List<TeleportArea> result = new ArrayList<>();
        for (TeleportArea area : areas.values()) {
            if (area.getDimension().equals(dim)) {
                result.add(area);
            }
        }
        return result;
    }

    public static TeleportAreaSavedData get(ServerLevel level) {
        // Saved data is stored on overworld server level to be globally accessible
        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        if (overworld != null) {
            return overworld.getDataStorage().computeIfAbsent(TYPE);
        }
        return level.getDataStorage().computeIfAbsent(TYPE);
    }
}
