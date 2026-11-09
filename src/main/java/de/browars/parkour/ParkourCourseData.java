package de.browars.parkour;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class ParkourCourseData {
    public static final Codec<ParkourCourseData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.fieldOf("startMin").forGetter(ParkourCourseData::getStartMin),
            BlockPos.CODEC.fieldOf("startMax").forGetter(ParkourCourseData::getStartMax),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("startDimension").forGetter(ParkourCourseData::getStartDimension),
            Codec.BOOL.fieldOf("hasStart").forGetter(ParkourCourseData::hasStart),
            BlockPos.CODEC.fieldOf("endMin").forGetter(ParkourCourseData::getEndMin),
            BlockPos.CODEC.fieldOf("endMax").forGetter(ParkourCourseData::getEndMax),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("endDimension").forGetter(ParkourCourseData::getEndDimension),
            Codec.BOOL.fieldOf("hasEnd").forGetter(ParkourCourseData::hasEnd),
            ParkourScore.CODEC.listOf().fieldOf("scores").forGetter(ParkourCourseData::getScores),
            BlockPos.CODEC.listOf().fieldOf("leaderboardBlocks").forGetter(ParkourCourseData::getLeaderboardBlocks)
    ).apply(instance, ParkourCourseData::new));

    private BlockPos startMin = BlockPos.ZERO;
    private BlockPos startMax = BlockPos.ZERO;
    private ResourceKey<Level> startDimension = Level.OVERWORLD;
    private boolean hasStart = false;

    private BlockPos endMin = BlockPos.ZERO;
    private BlockPos endMax = BlockPos.ZERO;
    private ResourceKey<Level> endDimension = Level.OVERWORLD;
    private boolean hasEnd = false;

    private final List<ParkourScore> scores = new ArrayList<>();
    private final List<BlockPos> leaderboardBlocks = new ArrayList<>();

    public ParkourCourseData() {}

    public ParkourCourseData(BlockPos startMin, BlockPos startMax, ResourceKey<Level> startDimension, boolean hasStart,
                             BlockPos endMin, BlockPos endMax, ResourceKey<Level> endDimension, boolean hasEnd,
                             List<ParkourScore> scores, List<BlockPos> leaderboardBlocks) {
        this.startMin = startMin;
        this.startMax = startMax;
        this.startDimension = startDimension;
        this.hasStart = hasStart;
        this.endMin = endMin;
        this.endMax = endMax;
        this.endDimension = endDimension;
        this.hasEnd = hasEnd;
        if (scores != null) this.scores.addAll(scores);
        if (leaderboardBlocks != null) this.leaderboardBlocks.addAll(leaderboardBlocks);
    }

    public void setStartBounds(BlockPos p1, BlockPos p2, ResourceKey<Level> dim) {
        int minX = Math.min(p1.getX(), p2.getX());
        int minY = Math.min(p1.getY(), p2.getY());
        int minZ = Math.min(p1.getZ(), p2.getZ());
        int maxX = Math.max(p1.getX(), p2.getX());
        int maxY = Math.max(p1.getY(), p2.getY());
        int maxZ = Math.max(p1.getZ(), p2.getZ());
        this.startMin = new BlockPos(minX, minY, minZ);
        this.startMax = new BlockPos(maxX, maxY, maxZ);
        this.startDimension = dim;
        this.hasStart = true;
    }

    public void setEndBounds(BlockPos p1, BlockPos p2, ResourceKey<Level> dim) {
        int minX = Math.min(p1.getX(), p2.getX());
        int minY = Math.min(p1.getY(), p2.getY());
        int minZ = Math.min(p1.getZ(), p2.getZ());
        int maxX = Math.max(p1.getX(), p2.getX());
        int maxY = Math.max(p1.getY(), p2.getY());
        int maxZ = Math.max(p1.getZ(), p2.getZ());
        this.endMin = new BlockPos(minX, minY, minZ);
        this.endMax = new BlockPos(maxX, maxY, maxZ);
        this.endDimension = dim;
        this.hasEnd = true;
    }

    public AABB getStartAABB() {
        if (!hasStart) return null;
        return AABB.encapsulatingFullBlocks(startMin, startMax);
    }

    public AABB getEndAABB() {
        if (!hasEnd) return null;
        return AABB.encapsulatingFullBlocks(endMin, endMax);
    }

    public boolean addOrUpdateScore(UUID uuid, String name, long timeMillis) {
        long now = System.currentTimeMillis();
        int existingIndex = -1;
        for (int i = 0; i < scores.size(); i++) {
            if (scores.get(i).playerUuid().equals(uuid)) {
                existingIndex = i;
                break;
            }
        }

        if (existingIndex >= 0) {
            ParkourScore existing = scores.get(existingIndex);
            if (timeMillis < existing.timeMillis()) {
                // New personal best!
                scores.set(existingIndex, new ParkourScore(uuid, name, timeMillis, now));
                sortScores();
                return true;
            }
            return false; // Not a personal best
        } else {
            // First time completed
            scores.add(new ParkourScore(uuid, name, timeMillis, now));
            sortScores();
            return true;
        }
    }

    public void sortScores() {
        scores.sort(Comparator.comparingLong(ParkourScore::timeMillis));
    }

    public ParkourScore getPersonalBest(UUID uuid) {
        for (ParkourScore score : scores) {
            if (score.playerUuid().equals(uuid)) {
                return score;
            }
        }
        return null;
    }

    public boolean deleteScore(int index) {
        if (index >= 0 && index < scores.size()) {
            scores.remove(index);
            return true;
        }
        return false;
    }

    public void clearScores() {
        scores.clear();
    }

    public void addLeaderboardBlock(BlockPos pos) {
        if (!leaderboardBlocks.contains(pos)) {
            leaderboardBlocks.add(pos);
        }
    }

    public void removeLeaderboardBlock(BlockPos pos) {
        leaderboardBlocks.remove(pos);
    }

    public BlockPos getStartMin() { return startMin; }
    public BlockPos getStartMax() { return startMax; }
    public ResourceKey<Level> getStartDimension() { return startDimension; }
    public boolean hasStart() { return hasStart; }

    public BlockPos getEndMin() { return endMin; }
    public BlockPos getEndMax() { return endMax; }
    public ResourceKey<Level> getEndDimension() { return endDimension; }
    public boolean hasEnd() { return hasEnd; }

    public List<ParkourScore> getScores() { return scores; }
    public List<BlockPos> getLeaderboardBlocks() { return leaderboardBlocks; }
}
