package de.browars.minigame.crosswar;

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

import java.util.Optional;

public class CrossWarArenaData implements DuelArena {

    public record ArenaSpawn(double x, double y, double z, float yaw, float pitch) {
        public static final Codec<ArenaSpawn> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.DOUBLE.fieldOf("x").forGetter(ArenaSpawn::x),
                Codec.DOUBLE.fieldOf("y").forGetter(ArenaSpawn::y),
                Codec.DOUBLE.fieldOf("z").forGetter(ArenaSpawn::z),
                Codec.FLOAT.fieldOf("yaw").forGetter(ArenaSpawn::yaw),
                Codec.FLOAT.fieldOf("pitch").forGetter(ArenaSpawn::pitch)
        ).apply(inst, ArenaSpawn::new));
    }

    public static final Codec<CrossWarArenaData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.fieldOf("minPos").forGetter(CrossWarArenaData::getMinPos),
            BlockPos.CODEC.fieldOf("maxPos").forGetter(CrossWarArenaData::getMaxPos),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(CrossWarArenaData::getDimension),
            ArenaSpawn.CODEC.optionalFieldOf("spawn1").forGetter(CrossWarArenaData::getOptSpawn1),
            ArenaSpawn.CODEC.optionalFieldOf("spawn2").forGetter(CrossWarArenaData::getOptSpawn2),
            Codec.BOOL.fieldOf("hasArena").forGetter(CrossWarArenaData::hasArena),
            Codec.STRING.optionalFieldOf("team1Color", "red").forGetter(CrossWarArenaData::getTeam1Color),
            Codec.STRING.optionalFieldOf("team2Color", "blue").forGetter(CrossWarArenaData::getTeam2Color),
            BlockPos.CODEC.optionalFieldOf("sc1Min").forGetter(d -> Optional.ofNullable(d.spawnChamber1Min)),
            BlockPos.CODEC.optionalFieldOf("sc1Max").forGetter(d -> Optional.ofNullable(d.spawnChamber1Max)),
            BlockPos.CODEC.optionalFieldOf("sc2Min").forGetter(d -> Optional.ofNullable(d.spawnChamber2Min)),
            BlockPos.CODEC.optionalFieldOf("sc2Max").forGetter(d -> Optional.ofNullable(d.spawnChamber2Max))
    ).apply(instance, CrossWarArenaData::new));

    private BlockPos minPos = BlockPos.ZERO;
    private BlockPos maxPos = BlockPos.ZERO;
    private ResourceKey<Level> dimension = Level.OVERWORLD;

    private ArenaSpawn spawn1 = null;
    private ArenaSpawn spawn2 = null;
    private boolean hasArena = false;

    private String team1Color = "red";
    private String team2Color = "blue";

    private BlockPos spawnChamber1Min = null;
    private BlockPos spawnChamber1Max = null;
    private BlockPos spawnChamber2Min = null;
    private BlockPos spawnChamber2Max = null;

    public CrossWarArenaData() {}

    public CrossWarArenaData(BlockPos minPos, BlockPos maxPos, ResourceKey<Level> dimension,
                             Optional<ArenaSpawn> s1, Optional<ArenaSpawn> s2,
                             boolean hasArena, String team1Color, String team2Color,
                             Optional<BlockPos> sc1Min, Optional<BlockPos> sc1Max,
                             Optional<BlockPos> sc2Min, Optional<BlockPos> sc2Max) {
        this.minPos = minPos;
        this.maxPos = maxPos;
        this.dimension = dimension;
        this.spawn1 = s1.orElse(null);
        this.spawn2 = s2.orElse(null);
        this.hasArena = hasArena;
        this.team1Color = team1Color != null ? team1Color : "red";
        this.team2Color = team2Color != null ? team2Color : "blue";
        this.spawnChamber1Min = sc1Min.orElse(null);
        this.spawnChamber1Max = sc1Max.orElse(null);
        this.spawnChamber2Min = sc2Min.orElse(null);
        this.spawnChamber2Max = sc2Max.orElse(null);
    }

    public CrossWarArenaData(BlockPos minPos, BlockPos maxPos, ResourceKey<Level> dimension,
                             Optional<ArenaSpawn> s1, Optional<ArenaSpawn> s2,
                             boolean hasArena, String team1Color, String team2Color) {
        this(minPos, maxPos, dimension, s1, s2, hasArena, team1Color, team2Color,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
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
        }
        this.dimension = dim;
        this.hasArena = true;
    }

    public void setSpawn1(double x, double y, double z, float yaw, float pitch) {
        setSpawn1(x, y, z, yaw, pitch, Level.OVERWORLD);
    }

    public void setSpawn1(double x, double y, double z, float yaw, float pitch, ResourceKey<Level> dim) {
        this.spawn1 = new ArenaSpawn(x, y, z, yaw, pitch);
        if (!hasArena && dim != null) this.dimension = dim;
    }

    public void setSpawn2(double x, double y, double z, float yaw, float pitch) {
        setSpawn2(x, y, z, yaw, pitch, Level.OVERWORLD);
    }

    public void setSpawn2(double x, double y, double z, float yaw, float pitch, ResourceKey<Level> dim) {
        this.spawn2 = new ArenaSpawn(x, y, z, yaw, pitch);
        if (!hasArena && dim != null) this.dimension = dim;
    }

    public boolean containsPlayer(ServerPlayer player) {
        if (player == null || !hasArena) return true;
        if (!player.level().dimension().equals(dimension)) return false;
        AABB box = player.getBoundingBox();
        return box.maxX >= minPos.getX() && box.minX <= maxPos.getX() + 1.0
                && box.maxY >= minPos.getY() && box.minY <= maxPos.getY() + 1.0
                && box.maxZ >= minPos.getZ() && box.minZ <= maxPos.getZ() + 1.0;
    }

    public String validationError(ServerLevel level) {
        if (!hasSpawn1()) return "Spawn 1 fehlt. Nutze /browars crosswar spawn 1.";
        if (!hasSpawn2()) return "Spawn 2 fehlt. Nutze /browars crosswar spawn 2.";
        if (hasArena) {
            if (!dimension.equals(level.dimension())) return "Falsche Arena-Dimension.";
            int width = maxPos.getX() - minPos.getX() + 1;
            int height = maxPos.getY() - minPos.getY() + 1;
            int depth = maxPos.getZ() - minPos.getZ() + 1;
            if (width < 5 || depth < 5 || height < 3) return "Die markierte Arena muss mindestens 5×3×5 Blöcke groß sein.";
        }
        return null;
    }

    public Optional<ArenaSpawn> getOptSpawn1() { return Optional.ofNullable(spawn1); }
    public Optional<ArenaSpawn> getOptSpawn2() { return Optional.ofNullable(spawn2); }

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

    @Override public double getSpawn1X() { return spawn1 != null ? spawn1.x() : 0; }
    @Override public double getSpawn1Y() { return spawn1 != null ? spawn1.y() : 0; }
    @Override public double getSpawn1Z() { return spawn1 != null ? spawn1.z() : 0; }
    @Override public float getSpawn1Yaw() { return spawn1 != null ? spawn1.yaw() : 0; }
    @Override public float getSpawn1Pitch() { return spawn1 != null ? spawn1.pitch() : 0; }
    @Override public boolean hasSpawn1() { return spawn1 != null; }

    @Override public double getSpawn2X() { return spawn2 != null ? spawn2.x() : 0; }
    @Override public double getSpawn2Y() { return spawn2 != null ? spawn2.y() : 0; }
    @Override public double getSpawn2Z() { return spawn2 != null ? spawn2.z() : 0; }
    @Override public float getSpawn2Yaw() { return spawn2 != null ? spawn2.yaw() : 0; }
    @Override public float getSpawn2Pitch() { return spawn2 != null ? spawn2.pitch() : 0; }
    @Override public boolean hasSpawn2() { return spawn2 != null; }

    public void setSpawnChamber1(BlockPos p1, BlockPos p2) {
        int minX = Math.min(p1.getX(), p2.getX());
        int minY = Math.min(p1.getY(), p2.getY());
        int minZ = Math.min(p1.getZ(), p2.getZ());
        int maxX = Math.max(p1.getX(), p2.getX());
        int maxY = Math.max(p1.getY(), p2.getY());
        int maxZ = Math.max(p1.getZ(), p2.getZ());
        this.spawnChamber1Min = new BlockPos(minX, minY, minZ);
        this.spawnChamber1Max = new BlockPos(maxX, maxY, maxZ);
    }

    public void setSpawnChamber2(BlockPos p1, BlockPos p2) {
        int minX = Math.min(p1.getX(), p2.getX());
        int minY = Math.min(p1.getY(), p2.getY());
        int minZ = Math.min(p1.getZ(), p2.getZ());
        int maxX = Math.max(p1.getX(), p2.getX());
        int maxY = Math.max(p1.getY(), p2.getY());
        int maxZ = Math.max(p1.getZ(), p2.getZ());
        this.spawnChamber2Min = new BlockPos(minX, minY, minZ);
        this.spawnChamber2Max = new BlockPos(maxX, maxY, maxZ);
    }

    public boolean hasSpawnChamber1() {
        return spawnChamber1Min != null && spawnChamber1Max != null;
    }

    public boolean hasSpawnChamber2() {
        return spawnChamber2Min != null && spawnChamber2Max != null;
    }

    public BlockPos getSpawnChamber1Min() { return spawnChamber1Min; }
    public BlockPos getSpawnChamber1Max() { return spawnChamber1Max; }
    public BlockPos getSpawnChamber2Min() { return spawnChamber2Min; }
    public BlockPos getSpawnChamber2Max() { return spawnChamber2Max; }

    public boolean isInSpawnChamber(ServerPlayer player, int spawnNumber) {
        if (player == null) return false;
        BlockPos scMin = spawnNumber == 1 ? spawnChamber1Min : spawnChamber2Min;
        BlockPos scMax = spawnNumber == 1 ? spawnChamber1Max : spawnChamber2Max;

        if (scMin != null && scMax != null) {
            AABB chamber = new AABB(
                    scMin.getX(), scMin.getY(), scMin.getZ(),
                    scMax.getX() + 1.0, scMax.getY() + 1.0, scMax.getZ() + 1.0
            );
            return chamber.intersects(player.getBoundingBox());
        }

        // Fallback: 2.5 blocks radius around spawn coordinate
        DuelArena.Spawn spawn = spawn(spawnNumber);
        double dx = player.getX() - spawn.x();
        double dy = player.getY() - spawn.y();
        double dz = player.getZ() - spawn.z();
        return (dx * dx + dz * dz < 2.5 * 2.5 && Math.abs(dy) < 3.0);
    }

    public boolean hasArena() { return hasArena; }
}
