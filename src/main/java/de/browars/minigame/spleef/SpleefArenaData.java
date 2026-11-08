package de.browars.minigame.spleef;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.browars.minigame.DuelArena;
import de.browars.minigame.TeamColor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SpleefArenaData implements DuelArena {

    public record ArenaSpawn(double x, double y, double z, float yaw, float pitch) {
        public static final Codec<ArenaSpawn> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.DOUBLE.fieldOf("x").forGetter(ArenaSpawn::x),
                Codec.DOUBLE.fieldOf("y").forGetter(ArenaSpawn::y),
                Codec.DOUBLE.fieldOf("z").forGetter(ArenaSpawn::z),
                Codec.FLOAT.fieldOf("yaw").forGetter(ArenaSpawn::yaw),
                Codec.FLOAT.fieldOf("pitch").forGetter(ArenaSpawn::pitch)
        ).apply(inst, ArenaSpawn::new));
    }

    public record ArenaBox(BlockPos min, BlockPos max) {
        public static final Codec<ArenaBox> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                BlockPos.CODEC.fieldOf("min").forGetter(ArenaBox::min),
                BlockPos.CODEC.fieldOf("max").forGetter(ArenaBox::max)
        ).apply(inst, ArenaBox::new));

        public static ArenaBox fromCorners(BlockPos p1, BlockPos p2) {
            return new ArenaBox(
                    new BlockPos(Math.min(p1.getX(), p2.getX()), Math.min(p1.getY(), p2.getY()), Math.min(p1.getZ(), p2.getZ())),
                    new BlockPos(Math.max(p1.getX(), p2.getX()), Math.max(p1.getY(), p2.getY()), Math.max(p1.getZ(), p2.getZ()))
            );
        }

        public boolean contains(BlockPos pos) {
            if (pos == null) return false;
            return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                    && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                    && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
        }
    }

    public static final Codec<SpleefArenaData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceKey.codec(Registries.DIMENSION).optionalFieldOf("dimension", Level.OVERWORLD).forGetter(SpleefArenaData::getDimension),
            ArenaSpawn.CODEC.optionalFieldOf("spawn1").forGetter(SpleefArenaData::getOptSpawn1),
            ArenaSpawn.CODEC.optionalFieldOf("spawn2").forGetter(SpleefArenaData::getOptSpawn2),
            ArenaBox.CODEC.optionalFieldOf("snowArea1").forGetter(d -> d.getOptSnowArea(1)),
            ArenaBox.CODEC.optionalFieldOf("snowArea2").forGetter(d -> d.getOptSnowArea(2)),
            ArenaBox.CODEC.optionalFieldOf("snowArea3").forGetter(d -> d.getOptSnowArea(3)),
            ArenaBox.CODEC.optionalFieldOf("snowArea4").forGetter(d -> d.getOptSnowArea(4)),
            ArenaBox.CODEC.optionalFieldOf("snowArea5").forGetter(d -> d.getOptSnowArea(5)),
            ArenaBox.CODEC.optionalFieldOf("snowArea6").forGetter(d -> d.getOptSnowArea(6)),
            ArenaBox.CODEC.optionalFieldOf("snowArea7").forGetter(d -> d.getOptSnowArea(7)),
            ArenaBox.CODEC.optionalFieldOf("killArea").forGetter(SpleefArenaData::getOptKillArea),
            Codec.STRING.optionalFieldOf("team1Color", "red").forGetter(SpleefArenaData::getTeam1Color),
            Codec.STRING.optionalFieldOf("team2Color", "blue").forGetter(SpleefArenaData::getTeam2Color)
    ).apply(instance, SpleefArenaData::new));

    private ResourceKey<Level> dimension = Level.OVERWORLD;
    private ArenaSpawn spawn1 = null;
    private ArenaSpawn spawn2 = null;
    private ArenaBox snowArea1 = null;
    private ArenaBox snowArea2 = null;
    private ArenaBox snowArea3 = null;
    private ArenaBox snowArea4 = null;
    private ArenaBox snowArea5 = null;
    private ArenaBox snowArea6 = null;
    private ArenaBox snowArea7 = null;
    private ArenaBox killArea = null;
    private String team1Color = "red";
    private String team2Color = "blue";

    public SpleefArenaData() {}

    public SpleefArenaData(ResourceKey<Level> dimension,
                           Optional<ArenaSpawn> spawn1, Optional<ArenaSpawn> spawn2,
                           Optional<ArenaBox> snowArea1, Optional<ArenaBox> snowArea2, Optional<ArenaBox> snowArea3,
                           Optional<ArenaBox> snowArea4, Optional<ArenaBox> snowArea5, Optional<ArenaBox> snowArea6,
                           Optional<ArenaBox> snowArea7,
                           Optional<ArenaBox> killArea,
                           String team1Color, String team2Color) {
        this.dimension = dimension != null ? dimension : Level.OVERWORLD;
        this.spawn1 = spawn1.orElse(null);
        this.spawn2 = spawn2.orElse(null);
        this.snowArea1 = snowArea1.orElse(null);
        this.snowArea2 = snowArea2.orElse(null);
        this.snowArea3 = snowArea3.orElse(null);
        this.snowArea4 = snowArea4.orElse(null);
        this.snowArea5 = snowArea5.orElse(null);
        this.snowArea6 = snowArea6.orElse(null);
        this.snowArea7 = snowArea7.orElse(null);
        this.killArea = killArea.orElse(null);
        this.team1Color = team1Color != null ? team1Color : "red";
        this.team2Color = team2Color != null ? team2Color : "blue";
    }

    private void updateDimension(ResourceKey<Level> dim) {
        if (dim != null && !this.dimension.equals(dim)) {
            this.spawn1 = null;
            this.spawn2 = null;
            this.snowArea1 = null;
            this.snowArea2 = null;
            this.snowArea3 = null;
            this.snowArea4 = null;
            this.snowArea5 = null;
            this.snowArea6 = null;
            this.snowArea7 = null;
            this.killArea = null;
            this.dimension = dim;
        }
    }

    public void setSpawn1(double x, double y, double z, float yaw, float pitch, ResourceKey<Level> dim) {
        updateDimension(dim);
        this.spawn1 = new ArenaSpawn(x, y, z, yaw, pitch);
    }

    public void setSpawn2(double x, double y, double z, float yaw, float pitch, ResourceKey<Level> dim) {
        updateDimension(dim);
        this.spawn2 = new ArenaSpawn(x, y, z, yaw, pitch);
    }

    public void setSnowArea(int index, BlockPos p1, BlockPos p2, ResourceKey<Level> dim) {
        updateDimension(dim);
        ArenaBox box = ArenaBox.fromCorners(p1, p2);
        switch (index) {
            case 1 -> this.snowArea1 = box;
            case 2 -> this.snowArea2 = box;
            case 3 -> this.snowArea3 = box;
            case 4 -> this.snowArea4 = box;
            case 5 -> this.snowArea5 = box;
            case 6 -> this.snowArea6 = box;
            case 7 -> this.snowArea7 = box;
            default -> throw new IllegalArgumentException("Ungültiger Snow-Area-Index: " + index);
        }
    }

    public void setKillArea(BlockPos p1, BlockPos p2, ResourceKey<Level> dim) {
        updateDimension(dim);
        this.killArea = ArenaBox.fromCorners(p1, p2);
    }

    public Optional<ArenaSpawn> getOptSpawn1() { return Optional.ofNullable(spawn1); }
    public Optional<ArenaSpawn> getOptSpawn2() { return Optional.ofNullable(spawn2); }

    public Optional<ArenaBox> getOptSnowArea(int index) {
        return Optional.ofNullable(getSnowArea(index));
    }

    public ArenaBox getSnowArea(int index) {
        return switch (index) {
            case 1 -> snowArea1;
            case 2 -> snowArea2;
            case 3 -> snowArea3;
            case 4 -> snowArea4;
            case 5 -> snowArea5;
            case 6 -> snowArea6;
            case 7 -> snowArea7;
            default -> null;
        };
    }

    public boolean hasSnowArea(int index) {
        return getSnowArea(index) != null;
    }

    public boolean hasAllSnowAreas() {
        return hasSnowArea(1);
    }

    public List<ArenaBox> getSnowAreas() {
        List<ArenaBox> list = new ArrayList<>(7);
        if (snowArea1 != null) list.add(snowArea1);
        if (snowArea2 != null) list.add(snowArea2);
        if (snowArea3 != null) list.add(snowArea3);
        if (snowArea4 != null) list.add(snowArea4);
        if (snowArea5 != null) list.add(snowArea5);
        if (snowArea6 != null) list.add(snowArea6);
        if (snowArea7 != null) list.add(snowArea7);
        return list;
    }

    public boolean isInAnySnowArea(BlockPos pos) {
        if (pos == null) return false;
        for (ArenaBox box : getSnowAreas()) {
            if (box.contains(pos)) return true;
        }
        return false;
    }

    public Optional<ArenaBox> getOptKillArea() { return Optional.ofNullable(killArea); }
    public ArenaBox getKillArea() { return killArea; }
    public boolean hasKillArea() { return killArea != null; }

    public boolean isPlayerInKillArea(ServerPlayer player) {
        if (player == null || !hasKillArea()) return false;
        if (!player.level().dimension().equals(dimension)) return false;
        return containsKillPosition(player.getBoundingBox(), player.level().getMinY());
    }

    public boolean containsKillPosition(AABB playerBox, int worldMinY) {
        if (!hasKillArea()) return false;
        if (playerBox.minY < worldMinY - 16) return true;
        BlockPos min = killArea.min();
        BlockPos max = killArea.max();
        return playerBox.minY <= max.getY() + 1.05
                && playerBox.maxX > min.getX() && playerBox.minX < max.getX() + 1.0
                && playerBox.maxZ > min.getZ() && playerBox.minZ < max.getZ() + 1.0;
    }

    public boolean isSpawnInKillArea(int number, int worldMinY) {
        ArenaSpawn spawn = number == 1 ? spawn1 : spawn2;
        return spawn != null && containsKillPosition(new AABB(spawn.x() - 0.3, spawn.y(), spawn.z() - 0.3,
                spawn.x() + 0.3, spawn.y() + 1.8, spawn.z() + 0.3), worldMinY);
    }

    private List<ArenaBox> getAllBoxes() {
        List<ArenaBox> boxes = getSnowAreas();
        if (killArea != null) boxes.add(killArea);
        return boxes;
    }

    @Override
    public BlockPos getArenaMin() {
        List<ArenaBox> boxes = getAllBoxes();
        if (boxes.isEmpty()) return null;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        for (ArenaBox box : boxes) {
            minX = Math.min(minX, box.min().getX());
            minY = Math.min(minY, box.min().getY());
            minZ = Math.min(minZ, box.min().getZ());
        }
        return new BlockPos(minX, minY, minZ);
    }

    @Override
    public BlockPos getArenaMax() {
        List<ArenaBox> boxes = getAllBoxes();
        if (boxes.isEmpty()) return null;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (ArenaBox box : boxes) {
            maxX = Math.max(maxX, box.max().getX());
            maxY = Math.max(maxY, box.max().getY());
            maxZ = Math.max(maxZ, box.max().getZ());
        }
        return new BlockPos(maxX, maxY, maxZ);
    }

    public ResourceKey<Level> getDimension() { return dimension; }

    public void setTeam1Color(String color) { this.team1Color = color; }
    public void setTeam2Color(String color) { this.team2Color = color; }
    public String getTeam1Color() { return team1Color; }
    public String getTeam2Color() { return team2Color; }
    @Override public TeamColor getTeam1ColorEnum() { return TeamColor.fromString(team1Color, TeamColor.RED); }
    @Override public TeamColor getTeam2ColorEnum() { return TeamColor.fromString(team2Color, TeamColor.BLUE); }

    @Override public double getSpawn1X() { return spawn1 != null ? spawn1.x() : 0; }
    @Override public double getSpawn1Y() { return spawn1 != null ? spawn1.y() : 0; }
    @Override public double getSpawn1Z() { return spawn1 != null ? spawn1.z() : 0; }
    @Override public float getSpawn1Yaw() { return spawn1 != null ? spawn1.yaw() : 0; }
    @Override public float getSpawn1Pitch() { return spawn1 != null ? spawn1.pitch() : 0; }
    public boolean hasSpawn1() { return spawn1 != null; }

    @Override public double getSpawn2X() { return spawn2 != null ? spawn2.x() : 0; }
    @Override public double getSpawn2Y() { return spawn2 != null ? spawn2.y() : 0; }
    @Override public double getSpawn2Z() { return spawn2 != null ? spawn2.z() : 0; }
    @Override public float getSpawn2Yaw() { return spawn2 != null ? spawn2.yaw() : 0; }
    @Override public float getSpawn2Pitch() { return spawn2 != null ? spawn2.pitch() : 0; }
    public boolean hasSpawn2() { return spawn2 != null; }

    public boolean isFullyReady() {
        return hasSpawn1() && hasSpawn2() && hasAllSnowAreas() && hasKillArea();
    }
}
