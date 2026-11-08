package de.browars.minigame.targetrush;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.browars.minigame.DuelArena;
import de.browars.minigame.TeamColor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public class TargetRushArenaData implements DuelArena {

    public record ArenaSpawn(double x, double y, double z, float yaw, float pitch) {
        public static final Codec<ArenaSpawn> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.DOUBLE.fieldOf("x").forGetter(ArenaSpawn::x),
                Codec.DOUBLE.fieldOf("y").forGetter(ArenaSpawn::y),
                Codec.DOUBLE.fieldOf("z").forGetter(ArenaSpawn::z),
                Codec.FLOAT.fieldOf("yaw").forGetter(ArenaSpawn::yaw),
                Codec.FLOAT.fieldOf("pitch").forGetter(ArenaSpawn::pitch)
        ).apply(inst, ArenaSpawn::new));
    }

    public static final Codec<TargetRushArenaData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.fieldOf("targetMin").forGetter(TargetRushArenaData::getTargetMin),
            BlockPos.CODEC.fieldOf("targetMax").forGetter(TargetRushArenaData::getTargetMax),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(TargetRushArenaData::getDimension),
            ArenaSpawn.CODEC.optionalFieldOf("spawn1").forGetter(TargetRushArenaData::getOptSpawn1),
            ArenaSpawn.CODEC.optionalFieldOf("spawn2").forGetter(TargetRushArenaData::getOptSpawn2),
            Codec.BOOL.fieldOf("hasTargetArea").forGetter(TargetRushArenaData::hasTargetArea),
            Codec.STRING.optionalFieldOf("team1Color", "red").forGetter(TargetRushArenaData::getTeam1Color),
            Codec.STRING.optionalFieldOf("team2Color", "blue").forGetter(TargetRushArenaData::getTeam2Color)
    ).apply(instance, TargetRushArenaData::new));

    private BlockPos targetMin = BlockPos.ZERO;
    private BlockPos targetMax = BlockPos.ZERO;
    private ResourceKey<Level> dimension = Level.OVERWORLD;

    private ArenaSpawn spawn1 = null;
    private ArenaSpawn spawn2 = null;
    private boolean hasTargetArea = false;

    private String team1Color = "red";
    private String team2Color = "blue";

    public TargetRushArenaData() {}

    public TargetRushArenaData(BlockPos targetMin, BlockPos targetMax, ResourceKey<Level> dimension,
                               Optional<ArenaSpawn> s1, Optional<ArenaSpawn> s2,
                               boolean hasTargetArea, String team1Color, String team2Color) {
        this.targetMin = targetMin;
        this.targetMax = targetMax;
        this.dimension = dimension;
        this.spawn1 = s1.orElse(null);
        this.spawn2 = s2.orElse(null);
        this.hasTargetArea = hasTargetArea;
        this.team1Color = team1Color != null ? team1Color : "red";
        this.team2Color = team2Color != null ? team2Color : "blue";
    }

    public void setTargetBounds(BlockPos p1, BlockPos p2, ResourceKey<Level> dim) {
        int minX = Math.min(p1.getX(), p2.getX());
        int minY = Math.min(p1.getY(), p2.getY());
        int minZ = Math.min(p1.getZ(), p2.getZ());
        int maxX = Math.max(p1.getX(), p2.getX());
        int maxY = Math.max(p1.getY(), p2.getY());
        int maxZ = Math.max(p1.getZ(), p2.getZ());

        this.targetMin = new BlockPos(minX, minY, minZ);
        this.targetMax = new BlockPos(maxX, maxY, maxZ);
        this.dimension = dim;
        this.hasTargetArea = true;
    }

    public void setSpawn(int number, double x, double y, double z, float yaw, float pitch) {
        ArenaSpawn spawn = new ArenaSpawn(x, y, z, yaw, pitch);
        if (number == 1) {
            this.spawn1 = spawn;
        } else if (number == 2) {
            this.spawn2 = spawn;
        }
    }

    public void setTeam1Color(String color) { this.team1Color = color; }
    public void setTeam2Color(String color) { this.team2Color = color; }

    public BlockPos getTargetMin() { return targetMin; }
    public BlockPos getTargetMax() { return targetMax; }
    public ResourceKey<Level> getDimension() { return dimension; }
    public boolean hasTargetArea() { return hasTargetArea; }
    public Optional<ArenaSpawn> getOptSpawn1() { return Optional.ofNullable(spawn1); }
    public Optional<ArenaSpawn> getOptSpawn2() { return Optional.ofNullable(spawn2); }
    public String getTeam1Color() { return team1Color; }
    public String getTeam2Color() { return team2Color; }

    @Override public double getSpawn1X() { return spawn1 != null ? spawn1.x() : 0.0; }
    @Override public double getSpawn1Y() { return spawn1 != null ? spawn1.y() : 0.0; }
    @Override public double getSpawn1Z() { return spawn1 != null ? spawn1.z() : 0.0; }
    @Override public float getSpawn1Yaw() { return spawn1 != null ? spawn1.yaw() : 0.0f; }
    @Override public float getSpawn1Pitch() { return spawn1 != null ? spawn1.pitch() : 0.0f; }
    @Override public boolean hasSpawn1() { return spawn1 != null; }

    @Override public double getSpawn2X() { return spawn2 != null ? spawn2.x() : 0.0; }
    @Override public double getSpawn2Y() { return spawn2 != null ? spawn2.y() : 0.0; }
    @Override public double getSpawn2Z() { return spawn2 != null ? spawn2.z() : 0.0; }
    @Override public float getSpawn2Yaw() { return spawn2 != null ? spawn2.yaw() : 0.0f; }
    @Override public float getSpawn2Pitch() { return spawn2 != null ? spawn2.pitch() : 0.0f; }
    @Override public boolean hasSpawn2() { return spawn2 != null; }

    @Override public TeamColor getTeam1ColorEnum() { return TeamColor.fromString(team1Color, TeamColor.RED); }
    @Override public TeamColor getTeam2ColorEnum() { return TeamColor.fromString(team2Color, TeamColor.BLUE); }

    @Override public BlockPos getArenaMin() { return targetMin; }
    @Override public BlockPos getArenaMax() { return targetMax; }

    public Vec3 getShootingReferencePoint() {
        if (spawn1 != null && spawn2 != null) {
            return new Vec3((spawn1.x() + spawn2.x()) * 0.5, (spawn1.y() + spawn2.y()) * 0.5, (spawn1.z() + spawn2.z()) * 0.5);
        }
        if (spawn1 != null) {
            return new Vec3(spawn1.x(), spawn1.y(), spawn1.z());
        }
        if (spawn2 != null) {
            return new Vec3(spawn2.x(), spawn2.y(), spawn2.z());
        }
        return new Vec3(targetMin.getX(), targetMin.getY(), targetMin.getZ());
    }

    public ZoneTier calculateTier(BlockPos pos) {
        Vec3 ref = getShootingReferencePoint();
        double targetDist = ref.distanceTo(new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));

        // Evaluate distance extremes within the target volume
        double[] xCorners = {targetMin.getX() + 0.5, targetMax.getX() + 0.5};
        double[] yCorners = {targetMin.getY() + 0.5, targetMax.getY() + 0.5};
        double[] zCorners = {targetMin.getZ() + 0.5, targetMax.getZ() + 0.5};

        double minDist = Double.MAX_VALUE;
        double maxDist = 0.0;

        for (double cx : xCorners) {
            for (double cy : yCorners) {
                for (double cz : zCorners) {
                    double d = ref.distanceTo(new Vec3(cx, cy, cz));
                    if (d < minDist) minDist = d;
                    if (d > maxDist) maxDist = d;
                }
            }
        }

        double span = Math.max(1.0, maxDist - minDist);
        double ratio = (targetDist - minDist) / span;

        if (ratio < 0.35) {
            return ZoneTier.BRONZE;
        } else if (ratio < 0.70) {
            return ZoneTier.SILVER;
        } else {
            return ZoneTier.GOLD;
        }
    }

    public String validationError(ServerLevel level, boolean solo) {
        if (!hasTargetArea) return "Zielbereich fehlt. Nutze /browars targetrush arena.";
        if (!dimension.equals(level.dimension())) return "Falsche Arena-Dimension.";
        if (spawn1 == null) return "Spawn 1 fehlt. Nutze /browars targetrush spawn 1.";
        if (!solo && spawn2 == null) return "Spawn 2 fehlt. Nutze /browars targetrush spawn 2.";
        return null;
    }
}
