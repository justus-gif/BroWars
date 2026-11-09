package de.browars.parkour;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class ParkourSavedData extends SavedData {
    public static final SavedDataType<ParkourSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("browars", "parkour_data"),
            ParkourSavedData::new,
            ParkourCourseData.CODEC.xmap(ParkourSavedData::new, ParkourSavedData::getData),
            null
    );

    private final ParkourCourseData data;

    public ParkourSavedData() {
        this.data = new ParkourCourseData();
    }

    public ParkourSavedData(ParkourCourseData data) {
        this.data = data != null ? data : new ParkourCourseData();
    }

    public ParkourCourseData getData() {
        return data;
    }

    public static ParkourSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        if (overworld != null) {
            return overworld.getDataStorage().computeIfAbsent(TYPE);
        }
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public static void save(ServerLevel level) {
        ParkourSavedData savedData = get(level);
        savedData.setDirty();

        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        if (overworld != null) {
            overworld.getDataStorage().scheduleSave();
        } else {
            level.getDataStorage().scheduleSave();
        }
    }
}
