package de.browars.minigame.anvilrain;

import de.browars.minigame.AbstractDuelGame;
import de.browars.minigame.TeamColor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AnvilRainGame extends AbstractDuelGame<AnvilRainArenaData> {
    private final List<FallingAnvil> anvils = new ArrayList<>();
    private final Map<BlockPos, BlockState> originalFloorBlocks = new HashMap<>();
    private final RandomSource random = RandomSource.create();
    /** Ticks between the first anvil hit and the end of the round, so the hit is clearly felt. */
    public enum StrikeCause {
        NONE(""),
        ANVIL("Von einem Amboss getroffen!"),
        MAGMA("Auf Magma getreten!"),
        OUT_OF_BOUNDS("Aus der Arena gefallen!");

        public final String text;
        StrikeCause(String text) { this.text = text; }
    }

    private static final int HIT_DELAY_TICKS = 25;
    private int spawnTicks;
    private boolean firstStruck, secondStruck;
    private StrikeCause p1Cause = StrikeCause.NONE;
    private StrikeCause p2Cause = StrikeCause.NONE;
    private int hitDelay = -1;

    public AnvilRainGame(MinecraftServer server, ServerLevel level, AnvilRainArenaData arena,
                         ServerPlayer first, ServerPlayer second, TeamColor firstTeam, TeamColor secondTeam) {
        super(server, level, arena, first, second, firstTeam, secondTeam);
    }
    @Override public String getId() { return "anvil_rain"; }
    @Override public String getDisplayName() { return "Anvil Rain"; }
    @Override protected String startInstruction() { return ""; }

    @Override
    protected int phaseNumber() {
        if (playTicks < 600) return 1;
        if (playTicks < 1200) return 2;
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

    @Override protected void onRoundStarted() {
        spawnTicks = 0;
        resetHits();
        resetMagmaFloor();
    }

    @Override protected void tickRound(ServerPlayer first, ServerPlayer second) {
        boolean firstOutOfBounds = !arena.containsPlayer(first.getX(), first.getY(), first.getZ());
        boolean secondOutOfBounds = second != null && !arena.containsPlayer(second.getX(), second.getY(), second.getZ());

        if (firstOutOfBounds && p1Cause == StrikeCause.NONE) {
            p1Cause = StrikeCause.OUT_OF_BOUNDS;
        }
        if (secondOutOfBounds && p2Cause == StrikeCause.NONE) {
            p2Cause = StrikeCause.OUT_OF_BOUNDS;
        }

        // Check magma contact
        if (!firstStruck && isSteppingOnMagma(first)) {
            if (!secondStruck) hitDelay = HIT_DELAY_TICKS;
            firstStruck = true;
            p1Cause = StrikeCause.MAGMA;
            onStruck(first);
        }
        if (second != null && !secondStruck && isSteppingOnMagma(second)) {
            if (!firstStruck) hitDelay = HIT_DELAY_TICKS;
            secondStruck = true;
            p2Cause = StrikeCause.MAGMA;
            onStruck(second);
        }

        var iterator = anvils.iterator();
        while (iterator.hasNext()) {
            FallingAnvil anvil = iterator.next();
            if (anvil.removed()) { anvil.discard(); iterator.remove(); continue; }
            boolean landed = anvil.advance();
            if (!firstStruck && anvil.intersects(first.getBoundingBox())) {
                if (!secondStruck) hitDelay = HIT_DELAY_TICKS;
                firstStruck = true;
                p1Cause = StrikeCause.ANVIL;
                onStruck(first);
            }
            if (second != null && !secondStruck && anvil.intersects(second.getBoundingBox())) {
                if (!firstStruck) hitDelay = HIT_DELAY_TICKS;
                secondStruck = true;
                p2Cause = StrikeCause.ANVIL;
                onStruck(second);
            }
            if (landed) { anvil.impact(); iterator.remove(); }
        }

        boolean firstHit = firstOutOfBounds;
        boolean secondHit = secondOutOfBounds;
        if ((firstStruck || secondStruck) && --hitDelay <= 0) {
            firstHit |= firstStruck;
            secondHit |= secondStruck;
        }

        if (firstHit || secondHit) {
            String reason;
            if (firstHit && secondHit) {
                if (p1Cause == p2Cause && p1Cause != StrikeCause.NONE) {
                    reason = "Beide: " + p1Cause.text;
                } else {
                    reason = p1Name + ": " + p1Cause.text + " • " + p2Name + ": " + p2Cause.text;
                }
            } else if (firstHit) {
                reason = isSoloMode ? p1Cause.text : p1Name + " ist " + (p1Cause == StrikeCause.ANVIL ? "von einem Amboss getroffen worden!" : (p1Cause == StrikeCause.MAGMA ? "auf Magma getreten!" : "aus der Arena gefallen!"));
            } else {
                reason = p2Name + " ist " + (p2Cause == StrikeCause.ANVIL ? "von einem Amboss getroffen worden!" : (p2Cause == StrikeCause.MAGMA ? "auf Magma getreten!" : "aus der Arena gefallen!"));
            }
            if (finishRound(firstHit, secondHit, reason)) return;
        }

        // Magma spots appear starting early (tick 240) and dynamically relocate/expand every 120 ticks (6s)
        if (playTicks >= 240 && (playTicks - 240) % 120 == 0) {
            updateMagmaSpots();
        }

        if (--spawnTicks <= 0) {
            spawnTicks = AnvilRainMotion.spawnInterval(playTicks);
            int batch = switch (phaseNumber()) {
                case 2 -> 2;
                case 3 -> 3;
                default -> (playTicks > 240 ? 2 : 1);
            };
            for (int i = 0; i < batch; i++) {
                if (anvils.size() < AnvilRainMotion.MAX_ANVILS) spawn();
            }
        }
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

    private void updateMagmaSpots() {
        int currentPhase = phaseNumber();

        // Restore previously placed magma blocks so the patches dynamically shift location
        resetMagmaFloor();

        int minX = arena.getMinPos().getX();
        int maxX = arena.getMaxPos().getX();
        int minZ = arena.getMinPos().getZ();
        int maxZ = arena.getMaxPos().getZ();
        int floorY = arena.getFloorY();

        int numSpots = switch (currentPhase) {
            case 3 -> 7;
            case 2 -> 4;
            default -> 2;
        };
        int minSpotSize = switch (currentPhase) {
            case 3 -> 6;
            case 2 -> 4;
            default -> 3;
        };
        int maxSpotSize = switch (currentPhase) {
            case 3 -> Math.min(12, 8 + (playTicks - 1200) / 200);
            case 2 -> 7;
            default -> 4;
        };

        List<ServerPlayer> players = new ArrayList<>();
        ServerPlayer p1 = server.getPlayerList().getPlayer(p1Uuid);
        if (p1 != null) players.add(p1);
        if (p2Uuid != null) {
            ServerPlayer p2 = server.getPlayerList().getPlayer(p2Uuid);
            if (p2 != null) players.add(p2);
        }

        Set<BlockPos> newMagma = new HashSet<>();
        Direction[] directions = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

        for (int s = 0; s < numSpots; s++) {
            BlockPos center = null;
            for (int attempts = 0; attempts < 30; attempts++) {
                int x = minX + 1 + random.nextInt(Math.max(1, maxX - minX - 1));
                int z = minZ + 1 + random.nextInt(Math.max(1, maxZ - minZ - 1));
                BlockPos candidate = new BlockPos(x, floorY, z);
                if (newMagma.contains(candidate)) continue;
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

            List<BlockPos> candidates = new ArrayList<>();
            candidates.add(center);
            for (Direction d : directions) {
                candidates.add(center.relative(d));
                candidates.add(center.relative(d).relative(d.getClockWise()));
                candidates.add(center.relative(d, 2));
            }
            Collections.shuffle(candidates, new java.util.Random(random.nextLong()));

            int placed = 0;
            int spotSize = Math.max(minSpotSize, random.nextInt(Math.max(1, maxSpotSize - minSpotSize + 1)) + minSpotSize);
            for (BlockPos pos : candidates) {
                if (placed >= spotSize) break;
                if (pos.getX() < minX || pos.getX() > maxX || pos.getZ() < minZ || pos.getZ() > maxZ) continue;
                if (newMagma.contains(pos)) continue;
                boolean nearPlayer = false;
                for (ServerPlayer player : players) {
                    if (Math.abs(player.getX() - (pos.getX() + 0.5)) < 1.4 && Math.abs(player.getZ() - (pos.getZ() + 0.5)) < 1.4) {
                        nearPlayer = true;
                        break;
                    }
                }
                if (nearPlayer) continue;

                if (!originalFloorBlocks.containsKey(pos)) {
                    originalFloorBlocks.put(pos.immutable(), level.getBlockState(pos));
                }
                level.setBlock(pos, Blocks.MAGMA_BLOCK.defaultBlockState(), 3);
                level.sendParticles(ParticleTypes.LAVA, pos.getX() + 0.5, floorY + 1.1, pos.getZ() + 0.5, 5, 0.2, 0.1, 0.2, 0.05);
                level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, floorY + 1.05, pos.getZ() + 0.5, 3, 0.15, 0.05, 0.15, 0.02);
                newMagma.add(pos);
                placed++;
            }
        }
        level.playSound(null, (minX + maxX) / 2.0, floorY, (minZ + maxZ) / 2.0, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.7f, 0.9f);
        level.playSound(null, (minX + maxX) / 2.0, floorY, (minZ + maxZ) / 2.0, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.8f, 1.2f);
    }

    private void resetMagmaFloor() {
        for (var entry : originalFloorBlocks.entrySet()) {
            level.setBlock(entry.getKey(), entry.getValue(), 3);
        }
        originalFloorBlocks.clear();
    }

    private void resetHits() {
        firstStruck = secondStruck = false;
        hitDelay = -1;
        p1Cause = StrikeCause.NONE;
        p2Cause = StrikeCause.NONE;
    }
    /** A hit does not end the round immediately: sound and effects play first, the round ends after a short delay. */
    private void onStruck(ServerPlayer player) {
        level.broadcastEntityEvent(player, (byte) 2);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.0f, 0.7f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1.0f, 0.9f);
        level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1, player.getZ(), 25, 0.4, 0.6, 0.4, 0.3);
        level.sendParticles(ParticleTypes.POOF, player.getX(), player.getY() + 0.2, player.getZ(), 10, 0.3, 0.1, 0.3, 0.05);
    }
    private void spawn() {
        double minX = arena.getMinPos().getX() + 0.5, maxX = arena.getMaxPos().getX() + 0.5;
        double minZ = arena.getMinPos().getZ() + 0.5, maxZ = arena.getMaxPos().getZ() + 0.5;
        for (int attempt = 0; attempt < 16; attempt++) {
            double x = minX + random.nextDouble() * (maxX - minX);
            double z = minZ + random.nextDouble() * (maxZ - minZ);
            boolean occupied = false;
            for (FallingAnvil existing : anvils) if (existing.distanceToSqr(x, z) < 0.8) { occupied = true; break; }
            if (occupied) continue;
            anvils.add(new FallingAnvil(level, x, z, arena.getFloorY() + 1,
                    AnvilRainMotion.fallDuration(playTicks)));
            return;
        }
    }
    @Override protected void clearRound() {
        anvils.forEach(FallingAnvil::discard);
        anvils.clear();
        resetHits();
        resetMagmaFloor();
    }

    @Override
    protected void restoreArena() {
        resetMagmaFloor();
    }
}
