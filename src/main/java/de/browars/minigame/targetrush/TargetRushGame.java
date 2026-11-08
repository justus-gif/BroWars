package de.browars.minigame.targetrush;

import de.browars.minigame.AbstractDuelGame;
import de.browars.minigame.MinigamePlayerState;
import de.browars.minigame.TeamColor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public final class TargetRushGame extends AbstractDuelGame<TargetRushArenaData> {

    public static final int ROUND_DURATION_TICKS = 2 * 60 * 20; // 2 minutes (2400 ticks)
    public static final int MAX_TARGETS = 5;
    public static final int MIN_SPAWN_INTERVAL = 30; // 1.5s
    public static final int MAX_SPAWN_INTERVAL = 60; // 3.0s

    private final List<TargetRushTarget> activeTargets = new ArrayList<>();
    private final Map<UUID, Integer> points = new HashMap<>();
    private int nextSpawnCountdown = 10;
    private boolean inSuddenDeath = false;
    private final Random random = new Random();

    public TargetRushGame(MinecraftServer server, ServerLevel level, TargetRushArenaData arena,
                          ServerPlayer first, ServerPlayer second, TeamColor firstTeam, TeamColor secondTeam) {
        super(server, level, arena, first, second, firstTeam, secondTeam);
    }

    @Override public String getId() { return "targetrush"; }
    @Override public String getDisplayName() { return "Target Rush"; }
    @Override protected String startInstruction() { return "Triff die Zielscheiben mit dem Bogen!"; }
    @Override protected int targetScore() { return 1; }

    @Override
    protected int p1Score() {
        return points.getOrDefault(p1Uuid, 0);
    }

    @Override
    protected int p2Score() {
        return isSoloMode ? 0 : (p2Uuid != null ? points.getOrDefault(p2Uuid, 0) : 0);
    }

    @Override
    protected int timerRemaining() {
        if (inSuddenDeath) return 0;
        if (phase == Phase.PLAYING) {
            return Math.max(0, ROUND_DURATION_TICKS - playTicks);
        }
        return ROUND_DURATION_TICKS;
    }

    @Override
    protected int timerTotal() {
        return ROUND_DURATION_TICKS;
    }

    @Override
    protected String matchStatus() {
        if (inSuddenDeath) return "§c§lSUDDEN DEATH! §eNächster Treffer gewinnt!";
        int remSec = Math.max(0, (ROUND_DURATION_TICKS - playTicks + 19) / 20);
        int m = remSec / 60;
        int s = remSec % 60;
        return String.format(Locale.ROOT, "%02d:%02d • %d Targets", m, s, activeTargets.size());
    }

    @Override
    protected String soloStatus() {
        int remSec = Math.max(0, (ROUND_DURATION_TICKS - playTicks + 19) / 20);
        int m = remSec / 60;
        int s = remSec % 60;
        return String.format(Locale.ROOT, "%02d:%02d • %d Targets", m, s, activeTargets.size());
    }

    @Override
    protected void onRoundStarted() {
        points.clear();
        points.put(p1Uuid, 0);
        if (p2Uuid != null) points.put(p2Uuid, 0);
        inSuddenDeath = false;
        clearActiveTargets();

        // Spawn initial 3 to 4 targets immediately
        int initialCount = 3 + random.nextInt(2);
        for (int i = 0; i < initialCount; i++) {
            spawnNewTarget();
        }
        nextSpawnCountdown = MIN_SPAWN_INTERVAL + random.nextInt(MAX_SPAWN_INTERVAL - MIN_SPAWN_INTERVAL + 1);
    }

    @Override
    protected void tickRound(ServerPlayer first, ServerPlayer second) {
        // 1. Tick particles and check expiration for active targets
        Iterator<TargetRushTarget> it = activeTargets.iterator();
        while (it.hasNext()) {
            TargetRushTarget target = it.next();
            target.tickParticles(level, playTicks);

            if (target.isExpired(playTicks)) {
                // Despawn unhit target with poof particles & sound
                BlockPos pos = target.getPos();
                level.sendParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0.05);
                level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6f, 1.4f);
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                it.remove();
            }
        }

        // 2. Spawn new targets periodically up to MAX_TARGETS
        if (activeTargets.size() < MAX_TARGETS) {
            nextSpawnCountdown--;
            if (nextSpawnCountdown <= 0) {
                spawnNewTarget();
                nextSpawnCountdown = MIN_SPAWN_INTERVAL + random.nextInt(MAX_SPAWN_INTERVAL - MIN_SPAWN_INTERVAL + 1);
            }
        }

        if (playTicks % 80 == 0) {
            clearStrayArrows();
        }

        // 3. Match time check
        if (playTicks >= ROUND_DURATION_TICKS) {
            if (isSoloMode) {
                int finalScore = points.getOrDefault(p1Uuid, 0);
                finishRound(false, false, "Zeit abgelaufen! Highscore: " + finalScore + " Punkte!");
                return;
            }

            int s1 = points.getOrDefault(p1Uuid, 0);
            int s2 = points.getOrDefault(p2Uuid, 0);

            if (s1 == s2) {
                if (!inSuddenDeath) {
                    inSuddenDeath = true;
                    playSound(SoundEvents.END_PORTAL_SPAWN, 1.2f);
                    playSound(SoundEvents.NOTE_BLOCK_BELL, 1.5f);
                    for (UUID id : List.of(p1Uuid, p2Uuid)) {
                        ServerPlayer p = server.getPlayerList().getPlayer(id);
                        if (p != null) {
                            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("§c§lSUDDEN DEATH!")));
                            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("§eNächster Treffer entscheidet das Match!")));
                        }
                    }
                }
            } else if (s1 > s2) {
                finishRound(false, true, "Zeit abgelaufen! " + s1 + " zu " + s2 + " Punkte");
            } else {
                finishRound(true, false, "Zeit abgelaufen! " + s2 + " zu " + s1 + " Punkte");
            }
        }
    }

    private void spawnNewTarget() {
        BlockPos min = arena.getTargetMin();
        BlockPos max = arena.getTargetMax();

        int width = max.getX() - min.getX() + 1;
        int height = max.getY() - min.getY() + 1;
        int depth = max.getZ() - min.getZ() + 1;

        if (width <= 0 || height <= 0 || depth <= 0) return;

        // Try up to 25 random positions
        for (int attempt = 0; attempt < 25; attempt++) {
            int rx = min.getX() + random.nextInt(width);
            int ry = min.getY() + random.nextInt(height);
            int rz = min.getZ() + random.nextInt(depth);
            BlockPos candidate = new BlockPos(rx, ry, rz);

            if (!level.getBlockState(candidate).isAir()) continue;

            // Check spacing: at least 2.5 blocks distance from other active targets
            boolean tooClose = false;
            for (TargetRushTarget existing : activeTargets) {
                if (existing.getPos().distSqr(candidate) < (2.5 * 2.5)) {
                    tooClose = true;
                    break;
                }
            }
            if (tooClose) continue;

            // Determine depth tier
            ZoneTier tier = arena.calculateTier(candidate);

            // Random lifetime between 15 and 20 seconds (300 to 400 ticks)
            int lifetime = 300 + random.nextInt(101);

            // Place target block
            level.setBlockAndUpdate(candidate, Blocks.TARGET.defaultBlockState());

            // Spawn visual spawn particles
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, candidate.getX() + 0.5, candidate.getY() + 0.5, candidate.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.05);

            activeTargets.add(new TargetRushTarget(candidate, playTicks, lifetime, tier));
            break;
        }
    }

    public boolean handleTargetHit(ServerLevel hitLevel, BlockPos hitPos, BlockHitResult hitResult, Projectile projectile) {
        if (!isRoundActive() || hitLevel != level) return false;

        TargetRushTarget hitTarget = null;
        for (TargetRushTarget t : activeTargets) {
            if (t.getPos().equals(hitPos)) {
                hitTarget = t;
                break;
            }
        }
        if (hitTarget == null) return false;

        ServerPlayer shooter = null;
        if (projectile.getOwner() instanceof ServerPlayer p && isPlayerParticipating(p.getUUID())) {
            shooter = p;
        }
        if (shooter == null) return false;

        // Calculate precision power (1 to 15) using hit position relative to block face center
        Vec3 hitLoc = hitResult.getLocation();
        Direction direction = hitResult.getDirection();
        Direction.Axis axis = direction.getAxis();
        double dx = Math.abs(Mth.frac(hitLoc.x) - 0.5);
        double dy = Math.abs(Mth.frac(hitLoc.y) - 0.5);
        double dz = Math.abs(Mth.frac(hitLoc.z) - 0.5);
        double offset = switch (axis) {
            case Y -> Math.max(dx, dz);
            case Z -> Math.max(dx, dy);
            case X -> Math.max(dy, dz);
        };
        int precision = Math.max(1, (int) Math.ceil(15.0 * Math.max(0.0, 1.0 - (offset / 0.5))));

        // Multiply by tier
        ZoneTier tier = hitTarget.getTier();
        int pointsAwarded = Math.max(1, (int) Math.round(precision * tier.multiplier));

        // Add points
        points.put(shooter.getUUID(), points.getOrDefault(shooter.getUUID(), 0) + pointsAwarded);

        // Feedback to shooter via Actionbar (NO chat spam)
        String bullseyeNote = precision >= 15 ? " §e★ Bullseye! ★" : "";
        shooter.sendSystemMessage(Component.literal(tier.colorCode + "+" + pointsAwarded + " Punkte §7(" + tier.displayName + " • Präzision " + precision + "/15)" + bullseyeNote), true);

        // Satisfying hit sound & particles
        level.playSound(null, hitPos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.2f, 1.3f);
        level.playSound(null, hitPos, SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 1.0f, 1.2f);
        if (precision >= 15) {
            level.playSound(null, hitPos, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.8f);
        }
        level.sendParticles(ParticleTypes.CRIT, hitPos.getX() + 0.5, hitPos.getY() + 0.5, hitPos.getZ() + 0.5, 20, 0.35, 0.35, 0.35, 0.15);

        // Destroy target block and remove
        level.setBlockAndUpdate(hitPos, Blocks.AIR.defaultBlockState());
        activeTargets.remove(hitTarget);
        projectile.discard();

        // Sudden death win condition
        if (inSuddenDeath && !isSoloMode) {
            if (shooter.getUUID().equals(p1Uuid)) {
                finishRound(false, true, "Sudden Death Treffer von " + p1Name + "!");
            } else {
                finishRound(true, false, "Sudden Death Treffer von " + p2Name + "!");
            }
        }

        return true;
    }

    private void clearStrayArrows() {
        BlockPos min = arena.getTargetMin();
        BlockPos max = arena.getTargetMax();
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(min).minmax(new net.minecraft.world.phys.AABB(max)).inflate(25);
        for (net.minecraft.world.entity.projectile.arrow.AbstractArrow arrow : level.getEntitiesOfClass(net.minecraft.world.entity.projectile.arrow.AbstractArrow.class, box)) {
            if (arrow.entityTags().contains("targetrush_arrow")) {
                arrow.discard();
            }
        }
    }

    private void clearActiveTargets() {
        for (TargetRushTarget t : activeTargets) {
            level.setBlockAndUpdate(t.getPos(), Blocks.AIR.defaultBlockState());
        }
        activeTargets.clear();
    }

    @Override
    protected void clearRound() {
        clearActiveTargets();
        clearStrayArrows();
        inSuddenDeath = false;
    }

    @Override
    protected void equipExtra(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        if (held.is(Items.BOW) && MinigamePlayerState.isGameItem(held)) return;

        ItemStack bow = new ItemStack(Items.BOW);
        bow.set(DataComponents.CUSTOM_NAME, Component.literal("§6§lTarget Rush Bogen"));
        level.registryAccess().lookup(Registries.ENCHANTMENT).flatMap(r -> r.get(Enchantments.INFINITY))
                .ifPresent(ench -> bow.enchant(ench, 1));
        MinigamePlayerState.markGameItem(bow);

        ItemStack arrow = new ItemStack(Items.ARROW);
        MinigamePlayerState.markGameItem(arrow);

        player.setItemSlot(EquipmentSlot.MAINHAND, bow);
        player.getInventory().setItem(9, arrow); // Put single arrow in upper inventory
    }
}
