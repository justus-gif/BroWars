package de.browars.minigame.colorfloor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.browars.minigame.TeamColor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Optional;

public class ColorFloorArenaData implements de.browars.minigame.DuelArena {

    public record ArenaSpawn(double x, double y, double z, float yaw, float pitch) {
        public static final Codec<ArenaSpawn> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.DOUBLE.fieldOf("x").forGetter(ArenaSpawn::x),
                Codec.DOUBLE.fieldOf("y").forGetter(ArenaSpawn::y),
                Codec.DOUBLE.fieldOf("z").forGetter(ArenaSpawn::z),
                Codec.FLOAT.fieldOf("yaw").forGetter(ArenaSpawn::yaw),
                Codec.FLOAT.fieldOf("pitch").forGetter(ArenaSpawn::pitch)
        ).apply(inst, ArenaSpawn::new));
    }

    public static final Codec<ColorFloorArenaData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.fieldOf("minPos").forGetter(ColorFloorArenaData::getMinPos),
            BlockPos.CODEC.fieldOf("maxPos").forGetter(ColorFloorArenaData::getMaxPos),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(ColorFloorArenaData::getDimension),
            ArenaSpawn.CODEC.optionalFieldOf("spawn1").forGetter(ColorFloorArenaData::getOptSpawn1),
            ArenaSpawn.CODEC.optionalFieldOf("spawn2").forGetter(ColorFloorArenaData::getOptSpawn2),
            Codec.BOOL.fieldOf("hasArena").forGetter(ColorFloorArenaData::hasArena),
            Codec.STRING.optionalFieldOf("team1Color", "red").forGetter(ColorFloorArenaData::getTeam1Color),
            Codec.STRING.optionalFieldOf("team2Color", "blue").forGetter(ColorFloorArenaData::getTeam2Color)
    ).apply(instance, ColorFloorArenaData::new));

    private BlockPos minPos = BlockPos.ZERO;
    private BlockPos maxPos = BlockPos.ZERO;
    private ResourceKey<Level> dimension = Level.OVERWORLD;

    private ArenaSpawn spawn1 = null;
    private ArenaSpawn spawn2 = null;
    private boolean hasArena = false;

    private String team1Color = "red";
    private String team2Color = "blue";

    public ColorFloorArenaData() {}

    public ColorFloorArenaData(BlockPos minPos, BlockPos maxPos, ResourceKey<Level> dimension,
                               Optional<ArenaSpawn> s1, Optional<ArenaSpawn> s2,
                               boolean hasArena, String team1Color, String team2Color) {
        this.minPos = minPos;
        this.maxPos = maxPos;
        this.dimension = dimension;
        this.spawn1 = s1.orElse(null);
        this.spawn2 = s2.orElse(null);
        this.hasArena = hasArena;
        this.team1Color = team1Color != null ? team1Color : "red";
        this.team2Color = team2Color != null ? team2Color : "blue";
    }

    public void setArenaBounds(BlockPos p1, BlockPos p2, ResourceKey<Level> dim) {
        int minX = Math.min(p1.getX(), p2.getX());
        int minY = Math.min(p1.getY(), p2.getY());
        int minZ = Math.min(p1.getZ(), p2.getZ());
        int maxX = Math.max(p1.getX(), p2.getX());
        int maxY = Math.max(p1.getY(), p2.getY());
        int maxZ = Math.max(p1.getZ(), p2.getZ());

        this.minPos = new BlockPos(minX, minY, minZ);
        this.maxPos = new BlockPos(maxX, maxY, maxZ);
        if (!this.dimension.equals(dim)) { spawn1 = null; spawn2 = null; }
        this.dimension = dim;
        this.hasArena = true;
    }

    public void setSpawn1(double x, double y, double z, float yaw, float pitch) {
        this.spawn1 = new ArenaSpawn(x, y, z, yaw, pitch);
    }

    public void setSpawn2(double x, double y, double z, float yaw, float pitch) {
        this.spawn2 = new ArenaSpawn(x, y, z, yaw, pitch);
    }

    public Optional<ArenaSpawn> getOptSpawn1() {
        return Optional.ofNullable(spawn1);
    }

    public Optional<ArenaSpawn> getOptSpawn2() {
        return Optional.ofNullable(spawn2);
    }

    public void setTeam1Color(String color) {
        this.team1Color = color;
    }

    public void setTeam2Color(String color) {
        this.team2Color = color;
    }

    public String getTeam1Color() {
        return team1Color;
    }

    public String getTeam2Color() {
        return team2Color;
    }

    public TeamColor getTeam1ColorEnum() {
        return TeamColor.fromString(team1Color, TeamColor.RED);
    }

    public TeamColor getTeam2ColorEnum() {
        return TeamColor.fromString(team2Color, TeamColor.BLUE);
    }

    public BlockPos getMinPos() { return minPos; }
    public BlockPos getMaxPos() { return maxPos; }
    @Override public BlockPos getArenaMin() { return minPos; }
    @Override public BlockPos getArenaMax() { return maxPos; }
    public ResourceKey<Level> getDimension() { return dimension; }

    public double getSpawn1X() { return spawn1 != null ? spawn1.x() : 0; }
    public double getSpawn1Y() { return spawn1 != null ? spawn1.y() : 0; }
    public double getSpawn1Z() { return spawn1 != null ? spawn1.z() : 0; }
    public float getSpawn1Yaw() { return spawn1 != null ? spawn1.yaw() : 0; }
    public float getSpawn1Pitch() { return spawn1 != null ? spawn1.pitch() : 0; }
    public boolean hasSpawn1() { return spawn1 != null; }

    public double getSpawn2X() { return spawn2 != null ? spawn2.x() : 0; }
    public double getSpawn2Y() { return spawn2 != null ? spawn2.y() : 0; }
    public double getSpawn2Z() { return spawn2 != null ? spawn2.z() : 0; }
    public float getSpawn2Yaw() { return spawn2 != null ? spawn2.yaw() : 0; }
    public float getSpawn2Pitch() { return spawn2 != null ? spawn2.pitch() : 0; }
    public boolean hasSpawn2() { return spawn2 != null; }

    public boolean hasArena() { return hasArena; }

    public boolean isFullyReady() {
        return hasArena && hasSpawn1() && hasSpawn2();
    }

    public boolean containsFloorSpawn(double x, double y, double z) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && x >= minPos.getX() + 0.3 && x <= maxPos.getX() + 0.7
                && z >= minPos.getZ() + 0.3 && z <= maxPos.getZ() + 0.7
                && Math.abs(y - getFloorY() - 1) <= 0.1;
    }

    public String validationError(net.minecraft.server.level.ServerLevel level) {
        long width = (long) maxPos.getX() - minPos.getX() + 1;
        long depth = (long) maxPos.getZ() - minPos.getZ() + 1;
        if (!hasArena || width < 1 || depth < 1 || width > 48 || depth > 48)
            return "Die Spielfläche muss zwischen 1×1 und 48×48 Blöcke groß sein.";
        if (!dimension.equals(level.dimension())) return "Falsche Arena-Dimension.";
        if (getFloorY() < level.getMinY() || getFloorY() + 3 >= level.getMaxY()) return "Ungültige Bodenhöhe.";
        for (int x = minPos.getX(); x <= maxPos.getX(); x++) {
            for (int z = minPos.getZ(); z <= maxPos.getZ(); z++) {
                BlockPos floor = new BlockPos(x, getFloorY(), z);
                if (!level.hasChunkAt(floor)) return "Bitte lade zuerst die gesamte Arena.";
                if (level.getBlockEntity(floor) != null) return "Entferne Kisten und andere Block-Entities aus dem Spielfeld.";
                for (int y = 1; y <= 2; y++) {
                    BlockPos headroom = floor.above(y);
                    var state = level.getBlockState(headroom);
                    if (!state.getCollisionShape(level, headroom).isEmpty() || !state.getFluidState().isEmpty())
                        return "Über der Spielfläche müssen zwei Blöcke frei sein.";
                }
            }
        }
        return null;
    }

    public int getFloorY() {
        return minPos.getY();
    }
}
