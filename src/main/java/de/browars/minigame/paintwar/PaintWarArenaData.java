package de.browars.minigame.paintwar;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.browars.minigame.DuelArena;
import de.browars.minigame.TeamColor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Random;

public class PaintWarArenaData implements DuelArena {

    public static final Codec<PaintWarArenaData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.fieldOf("minPos").forGetter(PaintWarArenaData::getMinPos),
            BlockPos.CODEC.fieldOf("maxPos").forGetter(PaintWarArenaData::getMaxPos),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(PaintWarArenaData::getDimension),
            Codec.BOOL.fieldOf("hasArena").forGetter(PaintWarArenaData::hasArena),
            Codec.STRING.optionalFieldOf("team1Color", "red").forGetter(PaintWarArenaData::getTeam1Color),
            Codec.STRING.optionalFieldOf("team2Color", "blue").forGetter(PaintWarArenaData::getTeam2Color)
    ).apply(instance, PaintWarArenaData::new));

    private BlockPos minPos = BlockPos.ZERO;
    private BlockPos maxPos = BlockPos.ZERO;
    private ResourceKey<Level> dimension = Level.OVERWORLD;
    private boolean hasArena = false;

    private String team1Color = "red";
    private String team2Color = "blue";

    // Dynamic random spawns chosen per round across the arena (including Y levels)
    private double spawn1X, spawn1Y, spawn1Z;
    private float spawn1Yaw;
    private double spawn2X, spawn2Y, spawn2Z;
    private float spawn2Yaw;

    public PaintWarArenaData() {}

    public PaintWarArenaData(BlockPos minPos, BlockPos maxPos, ResourceKey<Level> dimension,
                             boolean hasArena, String team1Color, String team2Color) {
        this.minPos = minPos;
        this.maxPos = maxPos;
        this.dimension = dimension;
        this.hasArena = hasArena;
        this.team1Color = team1Color != null ? team1Color : "red";
        this.team2Color = team2Color != null ? team2Color : "blue";
        initDefaultSpawns();
    }

    private void initDefaultSpawns() {
        double cx = (minPos.getX() + maxPos.getX()) * 0.5 + 0.5;
        double cy = Math.max(minPos.getY(), maxPos.getY()) + 1.0;
        double cz = (minPos.getZ() + maxPos.getZ()) * 0.5 + 0.5;
        this.spawn1X = cx;
        this.spawn1Y = cy;
        this.spawn1Z = cz;
        this.spawn1Yaw = 0f;
        this.spawn2X = cx;
        this.spawn2Y = cy;
        this.spawn2Z = cz;
        this.spawn2Yaw = 180f;
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
        this.dimension = dim;
        this.hasArena = true;
        initDefaultSpawns();
    }

    public void assignRandomRoundSpawns(List<BlockPos> candidates, Random random) {
        if (candidates == null || candidates.isEmpty()) {
            initDefaultSpawns();
            return;
        }
        double centerX = (minPos.getX() + maxPos.getX()) * 0.5 + 0.5;
        double centerZ = (minPos.getZ() + maxPos.getZ()) * 0.5 + 0.5;

        BlockPos p1 = candidates.get(random.nextInt(candidates.size()));
        BlockPos p2 = p1;
        double bestDistSq = -1;
        for (int i = 0; i < 24; i++) {
            BlockPos candidate = candidates.get(random.nextInt(candidates.size()));
            double dSq = candidate.distSqr(p1);
            if (dSq > bestDistSq) {
                bestDistSq = dSq;
                p2 = candidate;
            }
        }

        this.spawn1X = p1.getX() + 0.5;
        this.spawn1Y = p1.getY() + 1.0;
        this.spawn1Z = p1.getZ() + 0.5;
        this.spawn1Yaw = (float) Math.toDegrees(Math.atan2(-(centerX - spawn1X), centerZ - spawn1Z));

        this.spawn2X = p2.getX() + 0.5;
        this.spawn2Y = p2.getY() + 1.0;
        this.spawn2Z = p2.getZ() + 0.5;
        this.spawn2Yaw = (float) Math.toDegrees(Math.atan2(-(centerX - spawn2X), centerZ - spawn2Z));
    }

    public boolean containsPlayer(ServerPlayer player) {
        if (player == null || !hasArena) return true;
        if (!player.level().dimension().equals(dimension)) return false;
        AABB box = player.getBoundingBox();
        return box.maxX >= minPos.getX() - 5.0 && box.minX <= maxPos.getX() + 6.0
                && box.maxZ >= minPos.getZ() - 5.0 && box.minZ <= maxPos.getZ() + 6.0
                && player.getY() >= player.level().getMinY() + 2;
    }

    public String validationError(ServerLevel level) {
        if (!hasArena) return "Arena-Bereich fehlt. Nutze /browars paintwar arena.";
        if (!dimension.equals(level.dimension())) return "Falsche Arena-Dimension.";
        int width = maxPos.getX() - minPos.getX() + 1;
        int depth = maxPos.getZ() - minPos.getZ() + 1;
        if (width < 5 || depth < 5) return "Die markierte Arena muss mindestens 5×5 Blöcke (X/Z) groß sein.";
        return null;
    }

    public void setTeam1Color(String color) { this.team1Color = color; }
    public void setTeam2Color(String color) { this.team2Color = color; }
    public String getTeam1Color() { return team1Color; }
    public String getTeam2Color() { return team2Color; }

    @Override public TeamColor getTeam1ColorEnum() { return TeamColor.fromString(team1Color, TeamColor.RED); }
    @Override public TeamColor getTeam2ColorEnum() { return TeamColor.fromString(team2Color, TeamColor.BLUE); }

    public BlockPos getMinPos() { return minPos; }
    public BlockPos getMaxPos() { return maxPos; }
    @Override public BlockPos getArenaMin() { return hasArena ? minPos : null; }
    @Override public BlockPos getArenaMax() { return hasArena ? maxPos : null; }
    public ResourceKey<Level> getDimension() { return dimension; }

    @Override public double getSpawn1X() { return spawn1X; }
    @Override public double getSpawn1Y() { return spawn1Y; }
    @Override public double getSpawn1Z() { return spawn1Z; }
    @Override public float getSpawn1Yaw() { return spawn1Yaw; }
    @Override public float getSpawn1Pitch() { return 0f; }
    @Override public boolean hasSpawn1() { return hasArena; }

    @Override public double getSpawn2X() { return spawn2X; }
    @Override public double getSpawn2Y() { return spawn2Y; }
    @Override public double getSpawn2Z() { return spawn2Z; }
    @Override public float getSpawn2Yaw() { return spawn2Yaw; }
    @Override public float getSpawn2Pitch() { return 0f; }
    @Override public boolean hasSpawn2() { return hasArena; }

    public boolean hasArena() { return hasArena; }
}
