package de.browars.minigame.sumo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.browars.minigame.TeamColor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.Optional;

public class SumoArenaData implements de.browars.minigame.DuelArena {

    public record ArenaSpawn(double x, double y, double z, float yaw, float pitch) {
        public static final Codec<ArenaSpawn> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.DOUBLE.fieldOf("x").forGetter(ArenaSpawn::x),
                Codec.DOUBLE.fieldOf("y").forGetter(ArenaSpawn::y),
                Codec.DOUBLE.fieldOf("z").forGetter(ArenaSpawn::z),
                Codec.FLOAT.fieldOf("yaw").forGetter(ArenaSpawn::yaw),
                Codec.FLOAT.fieldOf("pitch").forGetter(ArenaSpawn::pitch)
        ).apply(inst, ArenaSpawn::new));
    }

    public static final Codec<SumoArenaData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.fieldOf("lossZoneMin").forGetter(SumoArenaData::getLossZoneMin),
            BlockPos.CODEC.fieldOf("lossZoneMax").forGetter(SumoArenaData::getLossZoneMax),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(SumoArenaData::getDimension),
            ArenaSpawn.CODEC.optionalFieldOf("spawn1").forGetter(SumoArenaData::getOptSpawn1),
            ArenaSpawn.CODEC.optionalFieldOf("spawn2").forGetter(SumoArenaData::getOptSpawn2),
            Codec.BOOL.fieldOf("hasLossZone").forGetter(SumoArenaData::hasLossZone),
            Codec.STRING.optionalFieldOf("team1Color", "red").forGetter(SumoArenaData::getTeam1Color),
            Codec.STRING.optionalFieldOf("team2Color", "blue").forGetter(SumoArenaData::getTeam2Color)
    ).apply(instance, SumoArenaData::new));

    private BlockPos lossZoneMin = BlockPos.ZERO;
    private BlockPos lossZoneMax = BlockPos.ZERO;
    private ResourceKey<Level> dimension = Level.OVERWORLD;

    private ArenaSpawn spawn1 = null;
    private ArenaSpawn spawn2 = null;
    private boolean hasLossZone = false;

    private String team1Color = "red";
    private String team2Color = "blue";

    public SumoArenaData() {}

    public SumoArenaData(BlockPos lossZoneMin, BlockPos lossZoneMax, ResourceKey<Level> dimension,
                         Optional<ArenaSpawn> s1, Optional<ArenaSpawn> s2,
                         boolean hasLossZone, String team1Color, String team2Color) {
        this.lossZoneMin = lossZoneMin;
        this.lossZoneMax = lossZoneMax;
        this.dimension = dimension;
        this.spawn1 = s1.orElse(null);
        this.spawn2 = s2.orElse(null);
        this.hasLossZone = hasLossZone;
        this.team1Color = team1Color != null ? team1Color : "red";
        this.team2Color = team2Color != null ? team2Color : "blue";
    }

    public void setLossZone(BlockPos p1, BlockPos p2, ResourceKey<Level> dim) {
        int minX = Math.min(p1.getX(), p2.getX());
        int minY = Math.min(p1.getY(), p2.getY());
        int minZ = Math.min(p1.getZ(), p2.getZ());
        int maxX = Math.max(p1.getX(), p2.getX());
        int maxY = Math.max(p1.getY(), p2.getY());
        int maxZ = Math.max(p1.getZ(), p2.getZ());

        this.lossZoneMin = new BlockPos(minX, minY, minZ);
        this.lossZoneMax = new BlockPos(maxX, maxY, maxZ);
        if (!this.dimension.equals(dim)) {
            this.spawn1 = null;
            this.spawn2 = null;
        }
        this.dimension = dim;
        this.hasLossZone = true;
    }

    public void setSpawn1(double x, double y, double z, float yaw, float pitch) {
        this.spawn1 = new ArenaSpawn(x, y, z, yaw, pitch);
    }

    public void setSpawn2(double x, double y, double z, float yaw, float pitch) {
        this.spawn2 = new ArenaSpawn(x, y, z, yaw, pitch);
    }

    public boolean isPlayerInLossZone(ServerPlayer player) {
        if (player == null) return false;
        if (!hasLossZone) return false;
        if (!player.level().dimension().equals(dimension)) return false;

        return containsLossPosition(player.getBoundingBox(), player.level().getMinY());
    }

    // Include contact with the top surface and falls through a thin zone between ticks.
    // Physical player bounds are deliberately independent of the targeting assistance.
    public boolean containsLossPosition(AABB playerBox, int worldMinY) {
        if (!hasLossZone) return false;
        if (playerBox.minY < worldMinY - 16) return true;
        return playerBox.minY <= lossZoneMax.getY() + 1.05
                && playerBox.maxX > lossZoneMin.getX() && playerBox.minX < lossZoneMax.getX() + 1.0
                && playerBox.maxZ > lossZoneMin.getZ() && playerBox.minZ < lossZoneMax.getZ() + 1.0;
    }

    public boolean isSpawnInLossZone(int number, int worldMinY) {
        ArenaSpawn spawn = number == 1 ? spawn1 : spawn2;
        return spawn != null && containsLossPosition(new AABB(spawn.x() - 0.3, spawn.y(), spawn.z() - 0.3,
                spawn.x() + 0.3, spawn.y() + 1.8, spawn.z() + 0.3), worldMinY);
    }

    public Optional<ArenaSpawn> getOptSpawn1() { return Optional.ofNullable(spawn1); }
    public Optional<ArenaSpawn> getOptSpawn2() { return Optional.ofNullable(spawn2); }

    public void setTeam1Color(String color) { this.team1Color = color; }
    public void setTeam2Color(String color) { this.team2Color = color; }
    public String getTeam1Color() { return team1Color; }
    public String getTeam2Color() { return team2Color; }

    public TeamColor getTeam1ColorEnum() { return TeamColor.fromString(team1Color, TeamColor.RED); }
    public TeamColor getTeam2ColorEnum() { return TeamColor.fromString(team2Color, TeamColor.BLUE); }

    public BlockPos getLossZoneMin() { return lossZoneMin; }
    public BlockPos getLossZoneMax() { return lossZoneMax; }
    @Override public BlockPos getArenaMin() { return lossZoneMin; }
    @Override public BlockPos getArenaMax() { return lossZoneMax; }
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

    public boolean hasLossZone() { return hasLossZone; }

    public boolean isFullyReady() {
        return hasLossZone && hasSpawn1() && hasSpawn2();
    }
}
