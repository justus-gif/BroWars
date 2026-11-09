package de.browars.minigame.vaultwar;

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

public class VaultWarArenaData implements DuelArena {

    public record ArenaSpawn(double x, double y, double z, float yaw, float pitch) {
        public static final Codec<ArenaSpawn> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.DOUBLE.fieldOf("x").forGetter(ArenaSpawn::x),
                Codec.DOUBLE.fieldOf("y").forGetter(ArenaSpawn::y),
                Codec.DOUBLE.fieldOf("z").forGetter(ArenaSpawn::z),
                Codec.FLOAT.fieldOf("yaw").forGetter(ArenaSpawn::yaw),
                Codec.FLOAT.fieldOf("pitch").forGetter(ArenaSpawn::pitch)
        ).apply(inst, ArenaSpawn::new));
    }

    public static final Codec<VaultWarArenaData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.fieldOf("minPos").forGetter(VaultWarArenaData::getMinPos),
            BlockPos.CODEC.fieldOf("maxPos").forGetter(VaultWarArenaData::getMaxPos),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(VaultWarArenaData::getDimension),
            ArenaSpawn.CODEC.optionalFieldOf("spawn1").forGetter(VaultWarArenaData::getOptSpawn1),
            ArenaSpawn.CODEC.optionalFieldOf("spawn2").forGetter(VaultWarArenaData::getOptSpawn2),
            Codec.BOOL.fieldOf("hasArena").forGetter(VaultWarArenaData::hasArena),
            Codec.STRING.optionalFieldOf("team1Color", "red").forGetter(VaultWarArenaData::getTeam1Color),
            Codec.STRING.optionalFieldOf("team2Color", "blue").forGetter(VaultWarArenaData::getTeam2Color),
            BlockPos.CODEC.optionalFieldOf("fenceMin").forGetter(d -> Optional.ofNullable(d.fenceMin)),
            BlockPos.CODEC.optionalFieldOf("fenceMax").forGetter(d -> Optional.ofNullable(d.fenceMax)),
            Codec.STRING.optionalFieldOf("fenceBlock", "minecraft:iron_bars").forGetter(VaultWarArenaData::getFenceBlock)
    ).apply(instance, VaultWarArenaData::new));

    private BlockPos minPos = BlockPos.ZERO;
    private BlockPos maxPos = BlockPos.ZERO;
    private ResourceKey<Level> dimension = Level.OVERWORLD;

    private ArenaSpawn spawn1 = null;
    private ArenaSpawn spawn2 = null;
    private boolean hasArena = false;

    private String team1Color = "red";
    private String team2Color = "blue";

    private BlockPos fenceMin = null;
    private BlockPos fenceMax = null;
    private String fenceBlock = "minecraft:iron_bars";

    public VaultWarArenaData() {}

    public VaultWarArenaData(BlockPos minPos, BlockPos maxPos, ResourceKey<Level> dimension,
                             Optional<ArenaSpawn> s1, Optional<ArenaSpawn> s2,
                             boolean hasArena, String team1Color, String team2Color,
                             Optional<BlockPos> fenceMin, Optional<BlockPos> fenceMax,
                             String fenceBlock) {
        this.minPos = minPos;
        this.maxPos = maxPos;
        this.dimension = dimension;
        this.spawn1 = s1.orElse(null);
        this.spawn2 = s2.orElse(null);
        this.hasArena = hasArena;
        this.team1Color = team1Color != null ? team1Color : "red";
        this.team2Color = team2Color != null ? team2Color : "blue";
        this.fenceMin = fenceMin.orElse(null);
        this.fenceMax = fenceMax.orElse(null);
        this.fenceBlock = fenceBlock != null && !fenceBlock.isBlank() ? fenceBlock : "minecraft:iron_bars";
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
            this.fenceMin = null;
            this.fenceMax = null;
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

    public void setFenceBounds(BlockPos p1, BlockPos p2) {
        int minX = Math.min(p1.getX(), p2.getX());
        int minY = Math.min(p1.getY(), p2.getY());
        int minZ = Math.min(p1.getZ(), p2.getZ());
        int maxX = Math.max(p1.getX(), p2.getX());
        int maxY = Math.max(p1.getY(), p2.getY());
        int maxZ = Math.max(p1.getZ(), p2.getZ());

        this.fenceMin = new BlockPos(minX, minY, minZ);
        this.fenceMax = new BlockPos(maxX, maxY, maxZ);
    }

    public boolean hasFence() {
        return fenceMin != null && fenceMax != null;
    }

    public BlockPos getFenceMin() {
        return fenceMin;
    }

    public BlockPos getFenceMax() {
        return fenceMax;
    }

    public String getFenceBlock() {
        return fenceBlock != null && !fenceBlock.isBlank() ? fenceBlock : "minecraft:iron_bars";
    }

    public void setFenceBlock(String fenceBlock) {
        this.fenceBlock = fenceBlock != null && !fenceBlock.isBlank() ? fenceBlock : "minecraft:iron_bars";
    }

    public boolean isInsideArena(BlockPos pos) {
        if (!hasArena) return false;
        return pos.getX() >= minPos.getX() && pos.getX() <= maxPos.getX()
                && pos.getY() >= minPos.getY() && pos.getY() <= maxPos.getY()
                && pos.getZ() >= minPos.getZ() && pos.getZ() <= maxPos.getZ();
    }

    public boolean isInsideArena(double x, double y, double z) {
        if (!hasArena) return false;
        return x >= minPos.getX() && x <= maxPos.getX() + 1.0
                && y >= minPos.getY() && y <= maxPos.getY() + 1.0
                && z >= minPos.getZ() && z <= maxPos.getZ() + 1.0;
    }

    public AABB toAABB() {
        if (!hasArena) return new AABB(0, 0, 0, 0, 0, 0);
        return new AABB(
                minPos.getX(), minPos.getY(), minPos.getZ(),
                maxPos.getX() + 1.0, maxPos.getY() + 1.0, maxPos.getZ() + 1.0
        );
    }

    public String validationError(ServerLevel level) {
        if (!hasArena) return "Arena wurde noch nicht festgelegt (/browars vaultwar arena).";
        if (spawn1 == null) return "Spawn 1 wurde noch nicht gesetzt (/browars vaultwar spawn 1).";
        if (spawn2 == null) return "Spawn 2 wurde noch nicht gesetzt (/browars vaultwar spawn 2).";
        if (!hasFence()) return "Trennzaun wurde noch nicht gesetzt (/browars vaultwar fence).";
        if (!level.dimension().equals(dimension)) {
            return "Arena befindet sich in einer anderen Dimension (" + dimension.identifier() + ").";
        }
        return null;
    }

    public BlockPos getMinPos() { return minPos; }
    public BlockPos getMaxPos() { return maxPos; }
    public ResourceKey<Level> getDimension() { return dimension; }
    public boolean hasArena() { return hasArena; }

    public Optional<ArenaSpawn> getOptSpawn1() { return Optional.ofNullable(spawn1); }
    public Optional<ArenaSpawn> getOptSpawn2() { return Optional.ofNullable(spawn2); }

    public String getTeam1Color() { return team1Color; }
    public void setTeam1Color(String team1Color) { this.team1Color = team1Color; }

    public String getTeam2Color() { return team2Color; }
    public void setTeam2Color(String team2Color) { this.team2Color = team2Color; }

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
