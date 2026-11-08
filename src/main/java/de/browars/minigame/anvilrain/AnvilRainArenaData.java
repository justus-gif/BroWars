package de.browars.minigame.anvilrain;

import com.mojang.serialization.Codec;
import de.browars.minigame.colorfloor.ColorFloorArenaData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/** Flat arena using the same persisted bounds, spawns and team colors as Color Floor. */
public final class AnvilRainArenaData extends ColorFloorArenaData {
    public static final Codec<AnvilRainArenaData> CODEC = ColorFloorArenaData.CODEC.xmap(AnvilRainArenaData::new, data -> data);
    public AnvilRainArenaData() {}
    private AnvilRainArenaData(ColorFloorArenaData data) {
        super(data.getMinPos(), data.getMaxPos(), data.getDimension(), data.getOptSpawn1(), data.getOptSpawn2(),
                data.hasArena(), data.getTeam1Color(), data.getTeam2Color());
    }

    public boolean containsSpawn(int number) {
        var spawn = spawn(number);
        return (number == 1 ? hasSpawn1() : hasSpawn2()) && containsPlayer(spawn.x(), spawn.y(), spawn.z());
    }
    public boolean containsPlayer(double x, double y, double z) {
        return x >= getMinPos().getX() + 0.3 && x <= getMaxPos().getX() + 0.7
                && z >= getMinPos().getZ() + 0.3 && z <= getMaxPos().getZ() + 0.7
                && y >= getFloorY() + 0.9 && y < getFloorY() + 4;
    }
    public String validationError(ServerLevel level) {
        if (!hasArena()) return "Spielfeld fehlt. Nutze /browars anvilrain arena.";
        if (!getDimension().equals(level.dimension())) return "Falsche Arena-Dimension.";
        int width = getMaxPos().getX() - getMinPos().getX() + 1;
        int depth = getMaxPos().getZ() - getMinPos().getZ() + 1;
        if (width < 5 || depth < 5 || width > 48 || depth > 48) return "Die Arena muss zwischen 5×5 und 48×48 Blöcke groß sein.";
        if (getMinPos().getY() != getMaxPos().getY()) return "Markiere zwei Bodenblöcke auf derselben Höhe.";
        if (getFloorY() + AnvilRainMotion.DROP_HEIGHT + 3 >= level.getMaxY()) return "Über der Arena ist zu wenig Platz bis zur Baugrenze.";
        for (int x = getMinPos().getX(); x <= getMaxPos().getX(); x++) {
            for (int z = getMinPos().getZ(); z <= getMaxPos().getZ(); z++) {
                BlockPos floor = new BlockPos(x, getFloorY(), z);
                if (!level.hasChunkAt(floor)) return "Bitte lade die gesamte Arena, bevor du sie speicherst oder startest.";
                if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) return "Der Arenaboden muss durchgehend und eben sein: " + floor.toShortString();
                for (int y = getFloorY() + 1; y <= getFloorY() + AnvilRainMotion.DROP_HEIGHT + 2; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    var state = level.getBlockState(pos);
                    if (!state.getCollisionShape(level, pos).isEmpty() || !state.getFluidState().isEmpty()) return "Über dem Spielfeld muss Platz für den Ambossregen frei sein: " + pos.toShortString();
                }
            }
        }
        return null;
    }
}
