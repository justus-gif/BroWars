package de.browars.minigame.colorfloor;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

import java.util.*;

public class ColorFloorPatternGenerator {

    public enum PatternType {
        CHAOS_1X1("1x1 Chaos-Gitter"),
        CHECKERBOARD_1X1("1x1 Schachbrett"),
        THIN_STRIPES_H("1x1 Horizontale Streifen"),
        THIN_STRIPES_V("1x1 Vertikale Streifen"),
        DIAGONAL_STRIPES("1x1 Diagonale Streifen"),
        CONCENTRIC_RINGS_THIN("Dünne Ringe"),
        SPIRAL("Farb-Spirale"),
        SPARSE_ISLANDS("Verstreute Einzelblöcke"),
        LABYRINTH("Farb-Labyrinth");

        public final String name;

        PatternType(String name) {
            this.name = name;
        }

        public static PatternType pick(RandomSource rand, PatternType lastPattern) {
            PatternType[] all = values();
            if (all.length <= 1) return all[0];
            PatternType picked;
            do {
                picked = all[rand.nextInt(all.length)];
            } while (picked == lastPattern);
            return picked;
        }
    }

    public static PatternType generateFloor(ServerLevel level, int minX, int maxX, int minZ, int maxZ, int floorY,
                                            RandomSource rand, PatternType lastPattern) {
        PatternType pattern = PatternType.pick(rand, lastPattern);
        ColorFloorGame.FloorColor[] colors = ColorFloorGame.FloorColor.values();

        double centerX = (minX + maxX) / 2.0;
        double centerZ = (minZ + maxZ) / 2.0;
        int colorOffset = rand.nextInt(colors.length);

        switch (pattern) {
            case CHAOS_1X1 -> {
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        setFloorBlock(level, x, floorY, z, ColorFloorGame.FloorColor.random(rand));
                    }
                }
            }
            case CHECKERBOARD_1X1 -> {
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        int index = (Math.abs(x) % 2 * 2 + Math.abs(z) % 2 + colorOffset) % colors.length;
                        setFloorBlock(level, x, floorY, z, colors[index]);
                    }
                }
            }
            case THIN_STRIPES_H -> {
                for (int z = minZ; z <= maxZ; z++) {
                    ColorFloorGame.FloorColor c = colors[Math.abs(z - minZ + colorOffset) % colors.length];
                    for (int x = minX; x <= maxX; x++) {
                        setFloorBlock(level, x, floorY, z, c);
                    }
                }
            }
            case THIN_STRIPES_V -> {
                for (int x = minX; x <= maxX; x++) {
                    ColorFloorGame.FloorColor c = colors[Math.abs(x - minX + colorOffset) % colors.length];
                    for (int z = minZ; z <= maxZ; z++) {
                        setFloorBlock(level, x, floorY, z, c);
                    }
                }
            }
            case DIAGONAL_STRIPES -> {
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        int index = Math.abs((x - minX) + (z - minZ) + colorOffset) % colors.length;
                        setFloorBlock(level, x, floorY, z, colors[index]);
                    }
                }
            }
            case CONCENTRIC_RINGS_THIN -> {
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        double dist = Math.hypot(x - centerX, z - centerZ);
                        int ring = (int) (dist / 1.3);
                        ColorFloorGame.FloorColor c = colors[Math.abs(ring + colorOffset) % colors.length];
                        setFloorBlock(level, x, floorY, z, c);
                    }
                }
            }
            case SPIRAL -> {
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        double dx = x - centerX;
                        double dz = z - centerZ;
                        double angle = Math.atan2(dz, dx);
                        double dist = Math.hypot(dx, dz);
                        int val = (int) Math.round((angle / (2 * Math.PI) * colors.length) + (dist / 1.5) + colorOffset);
                        int index = ((val % colors.length) + colors.length) % colors.length;
                        setFloorBlock(level, x, floorY, z, colors[index]);
                    }
                }
            }
            case SPARSE_ISLANDS -> {
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        if (rand.nextInt(3) == 0) {
                            setFloorBlock(level, x, floorY, z, ColorFloorGame.FloorColor.random(rand));
                        } else {
                            int idx = ((x * 7 + z * 13 + colorOffset) % colors.length + colors.length) % colors.length;
                            setFloorBlock(level, x, floorY, z, colors[idx]);
                        }
                    }
                }
            }
            case LABYRINTH -> {
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        int h = (x * 19 + z * 37 + (x ^ z) * 11 + colorOffset);
                        int index = ((h % colors.length) + colors.length) % colors.length;
                        setFloorBlock(level, x, floorY, z, colors[index]);
                    }
                }
            }
        }

        // Guarantee fair & reachable layout for all 4 colors in every quadrant
        ensureFairColorDistribution(level, minX, maxX, minZ, maxZ, floorY, rand);

        return pattern;
    }

    private static void ensureFairColorDistribution(ServerLevel level, int minX, int maxX, int minZ, int maxZ, int floorY, RandomSource rand) {
        ColorFloorGame.FloorColor[] colors = ColorFloorGame.FloorColor.values();
        int midX = (minX + maxX) / 2;
        int midZ = (minZ + maxZ) / 2;

        // Check each of the 4 quadrants (NW, NE, SW, SE)
        int[][] quadrantBounds = {
                {minX, midX, minZ, midZ},
                {midX + 1, maxX, minZ, midZ},
                {minX, midX, midZ + 1, maxZ},
                {midX + 1, maxX, midZ + 1, maxZ}
        };

        for (int[] bounds : quadrantBounds) {
            int qMinX = bounds[0];
            int qMaxX = bounds[1];
            int qMinZ = bounds[2];
            int qMaxZ = bounds[3];

            if (qMinX > qMaxX || qMinZ > qMaxZ) continue;

            Map<ColorFloorGame.FloorColor, List<BlockPos>> colorPositions = new EnumMap<>(ColorFloorGame.FloorColor.class);
            for (ColorFloorGame.FloorColor c : colors) {
                colorPositions.put(c, new ArrayList<>());
            }

            for (int x = qMinX; x <= qMaxX; x++) {
                for (int z = qMinZ; z <= qMaxZ; z++) {
                    BlockPos pos = new BlockPos(x, floorY, z);
                    for (ColorFloorGame.FloorColor c : colors) {
                        if (level.getBlockState(pos).is(c.block)) {
                            colorPositions.get(c).add(pos);
                            break;
                        }
                    }
                }
            }

            // Find missing or underrepresented colors (< 2 blocks in this quadrant)
            for (ColorFloorGame.FloorColor targetColor : colors) {
                List<BlockPos> posList = colorPositions.get(targetColor);
                if (posList.size() < 2) {
                    // Need to insert targetColor by replacing a block from the most frequent color in this quadrant
                    ColorFloorGame.FloorColor maxColor = null;
                    int maxCount = -1;
                    for (Map.Entry<ColorFloorGame.FloorColor, List<BlockPos>> entry : colorPositions.entrySet()) {
                        if (entry.getValue().size() > maxCount) {
                            maxCount = entry.getValue().size();
                            maxColor = entry.getKey();
                        }
                    }

                    if (maxColor != null && maxCount > 2) {
                        List<BlockPos> donorList = colorPositions.get(maxColor);
                        int needed = 2 - posList.size();
                        for (int i = 0; i < needed && donorList.size() > 2; i++) {
                            BlockPos donorPos = donorList.remove(rand.nextInt(donorList.size()));
                            level.setBlock(donorPos, targetColor.block.defaultBlockState(), 2);
                            posList.add(donorPos);
                        }
                    }
                }
            }
        }
    }

    private static void setFloorBlock(ServerLevel level, int x, int y, int z, ColorFloorGame.FloorColor color) {
        level.setBlock(new BlockPos(x, y, z), color.block.defaultBlockState(), 2);
    }
}
