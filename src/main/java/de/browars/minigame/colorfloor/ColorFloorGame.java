package de.browars.minigame.colorfloor;

import de.browars.minigame.AbstractDuelGame;
import de.browars.minigame.MinigameManager;
import de.browars.minigame.TeamColor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class ColorFloorGame extends AbstractDuelGame<ColorFloorArenaData> {
    public enum FloorColor {
        RED("ROT", 0xFF2222, Blocks.CONCRETE_POWDER.red()),
        YELLOW("GELB", 0xFFDD00, Blocks.CONCRETE_POWDER.yellow()),
        LIGHT_BLUE("HELLBLAU", 0x00D4FF, Blocks.CONCRETE_POWDER.lightBlue()),
        LIME("HELLGRÜN", 0x44FF22, Blocks.CONCRETE_POWDER.lime());
        public final String displayName;
        public final int hexColor;
        public final Block block;
        FloorColor(String name, int color, Block block) { displayName = name; hexColor = color; this.block = block; }
        public static FloorColor random(RandomSource random) { return values()[random.nextInt(values().length)]; }
    }
    private final RandomSource random = RandomSource.create();
    private final Map<BlockPos, BlockState> originalFloor = new HashMap<>();
    private ColorFloorPatternGenerator.PatternType lastPattern;
    private FloorColor target, lastTarget;
    private int cycle, cycleTicks, cycleDuration, survivedCycles;
    private boolean dropping;

    public ColorFloorGame(MinecraftServer server, ServerLevel level, ColorFloorArenaData arena,
                          ServerPlayer first, ServerPlayer second, TeamColor firstTeam, TeamColor secondTeam) {
        super(server, level, arena, first, second, firstTeam, secondTeam);
    }
    @Override public String getId() { return "color_floor"; }
    @Override public String getDisplayName() { return "Color Floor"; }
    @Override protected String startInstruction() { return "Finde die richtige Farbe!"; }
    @Override protected int targetColor() { return isRoundActive() && target != null ? target.hexColor : 0; }
    @Override protected String targetName() { return target == null ? "" : target.displayName; }
    @Override protected int timerRemaining() { return isRoundActive() && !dropping ? cycleTicks : -1; }
    @Override protected int timerTotal() { return cycleDuration; }
    @Override protected int soloProgress() { return survivedCycles; }
    @Override protected String soloStatus() { return "Solo • " + survivedCycles + " Runden"; }

    @Override
    protected int phaseNumber() {
        if (cycle <= 6) return 1;
        if (cycle <= 12) return 2;
        return 3;
    }

    @Override
    protected String phaseTitle() {
        return switch (phaseNumber()) {
            case 2 -> "Eskalation";
            case 3 -> "Chaos";
            default -> "Aufwärmen";
        };
    }

    private int calculateCycleDuration() {
        if (cycle <= 6) {
            return 80 - (cycle - 1) * 7;
        }
        if (cycle <= 12) {
            return 45 - (cycle - 6) * 3;
        }
        return Math.max(24, 27 - (cycle - 12));
    }

    @Override protected void prepareArena() {
        if (originalFloor.isEmpty()) {
            for (BlockPos pos : BlockPos.betweenClosed(arena.getMinPos().getX(), arena.getFloorY(), arena.getMinPos().getZ(),
                    arena.getMaxPos().getX(), arena.getFloorY(), arena.getMaxPos().getZ())) {
                if (level.getBlockEntity(pos) != null) throw new IllegalArgumentException("Spielfeld enthält einen Block mit Inventar/Daten: " + pos.toShortString());
                originalFloor.put(pos.immutable(), level.getBlockState(pos));
            }
        }
        cycle = 1;
        survivedCycles = 0;
        target = null;
        dropping = false;
        generateFloor();
    }
    @Override protected void onRoundStarted() { chooseTarget(); }
    @Override protected void restoreArena() {
        originalFloor.forEach((pos, state) -> level.setBlock(pos, state, 3));
        originalFloor.clear();
    }

    @Override protected void tickRound(ServerPlayer first, ServerPlayer second) {
        boolean p1Magma = isSteppingOnMagma(first);
        boolean p2Magma = second != null && isSteppingOnMagma(second);
        if (p1Magma || p2Magma) {
            if (finishRound(p1Magma, p2Magma, "Auf Magmablock getreten!")) return;
        }
        boolean p1Off = isOffArena(first);
        boolean p2Off = second != null && isOffArena(second);
        if (p1Off || p2Off) {
            if (finishRound(p1Off, p2Off, "Vom Farbfeld gefallen!")) return;
        }
        cycleTicks--;
        if (!dropping) {
            if (cycleTicks <= 20 && cycleTicks > 0 && cycleTicks % 5 == 0) {
                float pitch = 1.0f + (20 - cycleTicks) / 20.0f * 0.8f;
                playSound(SoundEvents.NOTE_BLOCK_HAT, pitch);
            }
            if (cycleTicks <= 0) {
                for (BlockPos pos : originalFloor.keySet()) {
                    if (!level.getBlockState(pos).is(target.block)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
                dropping = true;
                cycleTicks = 25;
                playSound(SoundEvents.BEACON_DEACTIVATE, 1.5f);
            }
        } else if (cycleTicks <= 0) {
            // Resolve all falling players before a new floor can rescue them.
            if (!first.onGround() || (second != null && !second.onGround())) return;
            cycle++;
            if (isSoloMode) survivedCycles++;
            generateFloor();
            chooseTarget();
            setCenterTitle("§a§lÜBERLEBT!", "Nächste Runde wird schneller...", 0xFF22C55E, 25);
        }
    }

    private boolean isOffArena(ServerPlayer player) {
        var box = player.getBoundingBox();
        return box.minY < arena.getFloorY() + 0.9 || box.maxX <= arena.getMinPos().getX()
                || box.minX >= arena.getMaxPos().getX() + 1 || box.maxZ <= arena.getMinPos().getZ()
                || box.minZ >= arena.getMaxPos().getZ() + 1;
    }

    private boolean isSteppingOnMagma(ServerPlayer player) {
        if (player == null || player.isDeadOrDying()) return false;
        var box = player.getBoundingBox();
        if (box.minY > arena.getFloorY() + 1.5 || box.minY < arena.getFloorY() + 0.8) return false;
        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(box.minX + 0.05, arena.getFloorY(), box.minZ + 0.05),
                BlockPos.containing(box.maxX - 0.05, arena.getFloorY(), box.maxZ - 0.05))) {
            if (level.getBlockState(pos).is(Blocks.MAGMA_BLOCK)) return true;
        }
        return false;
    }

    private void generateFloor() {
        lastPattern = ColorFloorPatternGenerator.generateFloor(level, arena.getMinPos().getX(), arena.getMaxPos().getX(),
                arena.getMinPos().getZ(), arena.getMaxPos().getZ(), arena.getFloorY(), random, lastPattern);
        if (cycle >= 7) {
            placeMagmaPatches();
        }
    }

    private void placeMagmaPatches() {
        int minX = arena.getMinPos().getX();
        int maxX = arena.getMaxPos().getX();
        int minZ = arena.getMinPos().getZ();
        int maxZ = arena.getMaxPos().getZ();
        int floorY = arena.getFloorY();

        Map<FloorColor, Integer> colorCounts = new EnumMap<>(FloorColor.class);
        for (FloorColor c : FloorColor.values()) colorCounts.put(c, 0);
        for (BlockPos pos : originalFloor.keySet()) {
            BlockState state = level.getBlockState(pos);
            for (FloorColor c : FloorColor.values()) {
                if (state.is(c.block)) {
                    colorCounts.put(c, colorCounts.get(c) + 1);
                    break;
                }
            }
        }

        int numPatches = cycle <= 12 ? (cycle >= 10 ? 3 : (2 + random.nextInt(2))) : 3;
        int maxPatchSize = cycle <= 12 ? (cycle >= 9 ? 2 : 1) : Math.min(4, 2 + (cycle - 13) / 2);

        java.util.List<ServerPlayer> players = new java.util.ArrayList<>();
        ServerPlayer p1 = server.getPlayerList().getPlayer(p1Uuid);
        if (p1 != null) players.add(p1);
        if (p2Uuid != null) {
            ServerPlayer p2 = server.getPlayerList().getPlayer(p2Uuid);
            if (p2 != null) players.add(p2);
        }

        Set<BlockPos> magmaPositions = new java.util.HashSet<>();
        net.minecraft.core.Direction[] directions = {
                net.minecraft.core.Direction.NORTH,
                net.minecraft.core.Direction.SOUTH,
                net.minecraft.core.Direction.EAST,
                net.minecraft.core.Direction.WEST
        };

        for (int p = 0; p < numPatches; p++) {
            BlockPos center = null;
            for (int attempts = 0; attempts < 30; attempts++) {
                int x = minX + 1 + random.nextInt(Math.max(1, maxX - minX - 1));
                int z = minZ + 1 + random.nextInt(Math.max(1, maxZ - minZ - 1));
                BlockPos candidate = new BlockPos(x, floorY, z);
                if (magmaPositions.contains(candidate)) continue;
                boolean nearPlayer = false;
                for (ServerPlayer player : players) {
                    if (Math.abs(player.getX() - (x + 0.5)) < 1.4 && Math.abs(player.getZ() - (z + 0.5)) < 1.4) {
                        nearPlayer = true;
                        break;
                    }
                }
                if (!nearPlayer) {
                    center = candidate;
                    break;
                }
            }

            if (center == null) continue;

            java.util.List<BlockPos> patchCandidates = new java.util.ArrayList<>();
            patchCandidates.add(center);
            for (net.minecraft.core.Direction d : directions) {
                patchCandidates.add(center.relative(d));
            }
            java.util.Collections.shuffle(patchCandidates, new java.util.Random(random.nextLong()));

            int placedInPatch = 0;
            int targetPatchSize = 1 + random.nextInt(maxPatchSize);

            for (BlockPos pos : patchCandidates) {
                if (placedInPatch >= targetPatchSize) break;
                if (pos.getX() < minX || pos.getX() > maxX || pos.getZ() < minZ || pos.getZ() > maxZ) continue;
                if (magmaPositions.contains(pos)) continue;

                boolean nearPlayer = false;
                for (ServerPlayer player : players) {
                    if (Math.abs(player.getX() - (pos.getX() + 0.5)) < 1.4 && Math.abs(player.getZ() - (pos.getZ() + 0.5)) < 1.4) {
                        nearPlayer = true;
                        break;
                    }
                }
                if (nearPlayer) continue;

                BlockState curState = level.getBlockState(pos);
                FloorColor currentColor = null;
                for (FloorColor c : FloorColor.values()) {
                    if (curState.is(c.block)) {
                        currentColor = c;
                        break;
                    }
                }
                if (currentColor != null && colorCounts.get(currentColor) <= 3) {
                    continue;
                }

                level.setBlock(pos, Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
                magmaPositions.add(pos);
                if (currentColor != null) {
                    colorCounts.put(currentColor, colorCounts.get(currentColor) - 1);
                }
                placedInPatch++;
            }
        }
    }

    private void chooseTarget() {
        // Small arenas and some patterns do not contain every color.
        var available = EnumSet.noneOf(FloorColor.class);
        for (BlockPos pos : originalFloor.keySet()) {
            BlockState state = level.getBlockState(pos);
            for (FloorColor color : FloorColor.values()) if (state.is(color.block)) available.add(color);
        }
        if (available.size() > 1) available.remove(lastTarget);
        if (available.isEmpty()) throw new IllegalStateException("Kein Farbfeld vorhanden");
        var choices = available.toArray(FloorColor[]::new);
        target = lastTarget = choices[random.nextInt(choices.length)];
        cycleDuration = calculateCycleDuration();
        cycleTicks = cycleDuration;
        dropping = false;
        playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1f);
    }
    public boolean isFloorBlock(ServerLevel world, BlockPos pos) {
        return world == level && pos.getY() == arena.getFloorY()
                && pos.getX() >= arena.getMinPos().getX() && pos.getX() <= arena.getMaxPos().getX()
                && pos.getZ() >= arena.getMinPos().getZ() && pos.getZ() <= arena.getMaxPos().getZ();
    }
    public static boolean isArenaFloorPos(ServerLevel level, BlockPos pos) {
        return MinigameManager.getInstance().getActiveGame() instanceof ColorFloorGame game
                && game.isRunning() && game.isFloorBlock(level, pos);
    }
}
