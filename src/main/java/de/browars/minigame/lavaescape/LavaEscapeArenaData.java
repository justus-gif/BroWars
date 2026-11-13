package de.browars.minigame.lavaescape;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.browars.minigame.DuelArena;
import de.browars.minigame.TeamColor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.Optional;

public class LavaEscapeArenaData implements DuelArena {

    public record ArenaSpawn(double x, double y, double z, float yaw, float pitch) {
        public static final Codec<ArenaSpawn> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.DOUBLE.fieldOf("x").forGetter(ArenaSpawn::x),
                Codec.DOUBLE.fieldOf("y").forGetter(ArenaSpawn::y),
                Codec.DOUBLE.fieldOf("z").forGetter(ArenaSpawn::z),
                Codec.FLOAT.fieldOf("yaw").forGetter(ArenaSpawn::yaw),
                Codec.FLOAT.fieldOf("pitch").forGetter(ArenaSpawn::pitch)
        ).apply(inst, ArenaSpawn::new));
    }

    public static final Codec<LavaEscapeArenaData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.fieldOf("minPos").forGetter(LavaEscapeArenaData::getMinPos),
            BlockPos.CODEC.fieldOf("maxPos").forGetter(LavaEscapeArenaData::getMaxPos),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(LavaEscapeArenaData::getDimension),
            ArenaSpawn.CODEC.optionalFieldOf("spawn1").forGetter(LavaEscapeArenaData::getOptSpawn1),
            ArenaSpawn.CODEC.optionalFieldOf("spawn2").forGetter(LavaEscapeArenaData::getOptSpawn2),
            Codec.BOOL.fieldOf("hasArena").forGetter(LavaEscapeArenaData::hasArena),
            Codec.STRING.optionalFieldOf("team1Color", "red").forGetter(LavaEscapeArenaData::getTeam1Color),
            Codec.STRING.optionalFieldOf("team2Color", "blue").forGetter(LavaEscapeArenaData::getTeam2Color),
            BlockPos.CODEC.optionalFieldOf("goalMin").forGetter(d -> Optional.ofNullable(d.goalMin)),
            BlockPos.CODEC.optionalFieldOf("goalMax").forGetter(d -> Optional.ofNullable(d.goalMax)),
            BlockPos.CODEC.optionalFieldOf("chestAreaMin").forGetter(d -> Optional.ofNullable(d.chestAreaMin)),
            BlockPos.CODEC.optionalFieldOf("chestAreaMax").forGetter(d -> Optional.ofNullable(d.chestAreaMax)),
            Codec.INT.optionalFieldOf("targetScore", 2).forGetter(LavaEscapeArenaData::getTargetScore)
    ).apply(instance, LavaEscapeArenaData::new));

    private BlockPos minPos = BlockPos.ZERO;
    private BlockPos maxPos = BlockPos.ZERO;
    private ResourceKey<Level> dimension = Level.OVERWORLD;

    private ArenaSpawn spawn1 = null;
    private ArenaSpawn spawn2 = null;
    private boolean hasArena = false;

    private String team1Color = "red";
    private String team2Color = "blue";

    private BlockPos goalMin = null;
    private BlockPos goalMax = null;

    private BlockPos chestAreaMin = null;
    private BlockPos chestAreaMax = null;

    private int targetScore = 2;

    public LavaEscapeArenaData() {}

    public LavaEscapeArenaData(BlockPos minPos, BlockPos maxPos, ResourceKey<Level> dimension,
                               Optional<ArenaSpawn> s1, Optional<ArenaSpawn> s2,
                               boolean hasArena, String team1Color, String team2Color,
                               Optional<BlockPos> goalMin, Optional<BlockPos> goalMax,
                               Optional<BlockPos> chestAreaMin, Optional<BlockPos> chestAreaMax,
                               int targetScore) {
        this.minPos = minPos;
        this.maxPos = maxPos;
        this.dimension = dimension;
        this.spawn1 = s1.orElse(null);
        this.spawn2 = s2.orElse(null);
        this.hasArena = hasArena;
        this.team1Color = team1Color != null ? team1Color : "red";
        this.team2Color = team2Color != null ? team2Color : "blue";
        this.goalMin = goalMin.orElse(null);
        this.goalMax = goalMax.orElse(null);
        this.chestAreaMin = chestAreaMin.orElse(null);
        this.chestAreaMax = chestAreaMax.orElse(null);
        this.targetScore = targetScore > 0 ? targetScore : 2;
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
        if (!this.dimension.equals(dim)) {
            this.spawn1 = null;
            this.spawn2 = null;
            this.goalMin = null;
            this.goalMax = null;
            this.chestAreaMin = null;
            this.chestAreaMax = null;
        }
        this.dimension = dim;
        this.hasArena = true;
    }

    public void setSpawn1(double x, double y, double z, float yaw, float pitch, ResourceKey<Level> dim) {
        this.spawn1 = new ArenaSpawn(x, y, z, yaw, pitch);
        this.dimension = dim;
    }

    public void setSpawn2(double x, double y, double z, float yaw, float pitch, ResourceKey<Level> dim) {
        this.spawn2 = new ArenaSpawn(x, y, z, yaw, pitch);
        this.dimension = dim;
    }

    public void setGoalBounds(BlockPos p1, BlockPos p2) {
        int minX = Math.min(p1.getX(), p2.getX());
        int minY = Math.min(p1.getY(), p2.getY());
        int minZ = Math.min(p1.getZ(), p2.getZ());
        int maxX = Math.max(p1.getX(), p2.getX());
        int maxY = Math.max(p1.getY(), p2.getY());
        int maxZ = Math.max(p1.getZ(), p2.getZ());

        this.goalMin = new BlockPos(minX, minY, minZ);
        this.goalMax = new BlockPos(maxX, maxY, maxZ);
    }

    public void setChestAreaBounds(BlockPos p1, BlockPos p2) {
        int minX = Math.min(p1.getX(), p2.getX());
        int minY = Math.min(p1.getY(), p2.getY());
        int minZ = Math.min(p1.getZ(), p2.getZ());
        int maxX = Math.max(p1.getX(), p2.getX());
        int maxY = Math.max(p1.getY(), p2.getY());
        int maxZ = Math.max(p1.getZ(), p2.getZ());

        this.chestAreaMin = new BlockPos(minX, minY, minZ);
        this.chestAreaMax = new BlockPos(maxX, maxY, maxZ);
    }

    public boolean hasGoal() {
        return goalMin != null && goalMax != null;
    }

    public boolean hasChestArea() {
        return chestAreaMin != null && chestAreaMax != null;
    }

    public boolean isInsideArena(BlockPos pos) {
        if (!hasArena) return false;
        return pos.getX() >= minPos.getX() && pos.getX() <= maxPos.getX()
                && pos.getY() >= minPos.getY() && pos.getY() <= maxPos.getY()
                && pos.getZ() >= minPos.getZ() && pos.getZ() <= maxPos.getZ();
    }

    public boolean isInsideGoal(BlockPos pos) {
        if (!hasGoal()) return false;
        return pos.getX() >= goalMin.getX() && pos.getX() <= goalMax.getX()
                && pos.getY() >= goalMin.getY() && pos.getY() <= goalMax.getY()
                && pos.getZ() >= goalMin.getZ() && pos.getZ() <= goalMax.getZ();
    }

    public boolean isInsideGoal(AABB aabb) {
        if (!hasGoal()) return false;
        AABB goalBox = new AABB(goalMin.getX(), goalMin.getY(), goalMin.getZ(),
                goalMax.getX() + 1.0, goalMax.getY() + 1.0, goalMax.getZ() + 1.0);
        return goalBox.intersects(aabb);
    }

    public BlockPos getEffectiveChestMin() {
        if (hasChestArea()) {
            return chestAreaMin;
        }
        return minPos;
    }

    public BlockPos getEffectiveChestMax() {
        if (hasChestArea()) {
            return chestAreaMax;
        }
        return maxPos;
    }

    public boolean isReady() {
        return hasArena && spawn1 != null;
    }

    public boolean isReadyForDuel() {
        return hasArena && spawn1 != null && spawn2 != null;
    }

    public BlockPos getMinPos() { return minPos; }
    public BlockPos getMaxPos() { return maxPos; }
    public ResourceKey<Level> getDimension() { return dimension; }
    public ArenaSpawn getSpawn1() { return spawn1; }
    public ArenaSpawn getSpawn2() { return spawn2; }
    public Optional<ArenaSpawn> getOptSpawn1() { return Optional.ofNullable(spawn1); }
    public Optional<ArenaSpawn> getOptSpawn2() { return Optional.ofNullable(spawn2); }
    public boolean hasArena() { return hasArena; }
    public String getTeam1Color() { return team1Color; }
    public void setTeam1Color(String team1Color) { this.team1Color = team1Color; }
    public String getTeam2Color() { return team2Color; }
    public void setTeam2Color(String team2Color) { this.team2Color = team2Color; }
    public BlockPos getGoalMin() { return goalMin; }
    public BlockPos getGoalMax() { return goalMax; }
    public BlockPos getChestAreaMin() { return chestAreaMin; }
    public BlockPos getChestAreaMax() { return chestAreaMax; }
    public int getTargetScore() { return targetScore; }
    public void setTargetScore(int targetScore) { this.targetScore = targetScore > 0 ? targetScore : 2; }

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
    @Override public BlockPos getArenaMin() { return minPos; }
    @Override public BlockPos getArenaMax() { return maxPos; }
}
