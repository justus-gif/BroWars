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
        if (inSuddenDeath || phase == Phase.FINISHED) return 0;
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
    protected int phaseNumber() {
        return 0; // Kein "● Aufwärmen" Label
    }

    @Override
    protected String phaseTitle() {
        return "";
    }

    @Override
    protected String matchStatus() {
        if (phase == Phase.FINISHED) {
            int s1 = points.getOrDefault(p1Uuid, 0);
            int s2 = p2Uuid == null ? 0 : points.getOrDefault(p2Uuid, 0);
            if (s1 == s2) return "§eUnentschieden!";
            String winner = s1 > s2 ? p1Name : p2Name;
            return "§aSieger: §e" + winner;
        }
        if (inSuddenDeath) return "§c§lSUDDEN DEATH! §eNächster Treffer gewinnt!";
        int remSec = Math.max(0, (ROUND_DURATION_TICKS - playTicks + 19) / 20);
        int m = remSec / 60;
        int s = remSec % 60;
        return String.format(Locale.ROOT, "%02d:%02d • %d Targets", m, s, activeTargets.size());
    }

    @Override
    protected String soloStatus() {
        if (phase == Phase.FINISHED) {
            return "§aHighscore: §e" + points.getOrDefault(p1Uuid, 0) + " Pkt.";
        }
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
        purgeOrphanBadges();

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
                target.remove(level);
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
                endSoloMatch(finalScore);
                return;
            }

            int s1 = points.getOrDefault(p1Uuid, 0);
            int s2 = points.getOrDefault(p2Uuid, 0);

            if (s1 == s2) {
                if (!inSuddenDeath) {
                    inSuddenDeath = true;
                    playSound(SoundEvents.END_PORTAL_SPAWN, 1.2f);
                    playSound(SoundEvents.NOTE_BLOCK_BELL, 1.5f);
                    setCenterTitle("§c§lSUDDEN DEATH!", "§eNächster Treffer entscheidet das Match!", 0xFFEF4444, 80);
                    message("§c§lSUDDEN DEATH! §eGleichstand (" + s1 + " : " + s2 + ")! Der nächste Treffer gewinnt!");
                }
            } else if (s1 > s2) {
                endDuelMatch(p1Uuid, p1Name, s1, s2, "Zeit abgelaufen!");
            } else {
                endDuelMatch(p2Uuid, p2Name, s2, s1, "Zeit abgelaufen!");
            }
        }
    }

    private void endSoloMatch(int finalScore) {
        clearRound();
        phase = Phase.FINISHED;
        phaseTicks = 120; // 6 seconds end screen

        setCenterTitle("§6§lZEIT ABGELAUFEN!", "§eHighscore: §a§l" + finalScore + " §ePunkte", 0xFFFFD700, 110);
        message("§aZeit abgelaufen! Highscore von §e" + p1Name + "§a: §6§l" + finalScore + " Punkte§a!");

        ServerPlayer p = server.getPlayerList().getPlayer(p1Uuid);
        if (p != null) {
            level.sendParticles(ParticleTypes.FIREWORK, p.getX(), p.getY() + 1.5, p.getZ(), 35, 0.6, 0.8, 0.6, 0.15);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1.2, p.getZ(), 15, 0.4, 0.4, 0.4, 0.1);
        }
        de.browars.minigame.MinigameSounds.playWin(server, true, p1Uuid);
    }

    private void endDuelMatch(UUID winnerUuid, String winnerName, int winScore, int loseScore, String reason) {
        clearRound();
        phase = Phase.FINISHED;
        phaseTicks = 140; // 7 seconds end screen

        setCenterTitle("§6§l" + winnerName + " GEWINNT!", "§eEndstand: §a" + winScore + " §7zu §c" + loseScore + " §ePunkten", 0xFFFFD700, 120);
        String reasonSuffix = (reason != null && !reason.isBlank()) ? " §8- §e" + reason : "";
        message("§a" + winnerName + " gewinnt das Match! (§e" + winScore + " : " + loseScore + " Punkte§a)" + reasonSuffix);

        ServerPlayer winnerPlayer = server.getPlayerList().getPlayer(winnerUuid);
        if (winnerPlayer != null) {
            level.sendParticles(ParticleTypes.FIREWORK, winnerPlayer.getX(), winnerPlayer.getY() + 1.5, winnerPlayer.getZ(), 40, 0.6, 0.8, 0.6, 0.15);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, winnerPlayer.getX(), winnerPlayer.getY() + 1.2, winnerPlayer.getZ(), 20, 0.4, 0.4, 0.4, 0.1);
        }
        de.browars.minigame.MinigameSounds.playWin(server, true, p1Uuid, p2Uuid);
    }

    private void spawnNewTarget() {
        BlockPos min = arena.getTargetMin();
        BlockPos max = arena.getTargetMax();

        int width = max.getX() - min.getX() + 1;
        int height = max.getY() - min.getY() + 1;
        int depth = max.getZ() - min.getZ() + 1;

        if (width <= 0 || height <= 0 || depth <= 0) return;

        // Try up to 60 random positions
        for (int attempt = 0; attempt < 60; attempt++) {
            int rx = min.getX() + random.nextInt(width);
            int ry = min.getY() + random.nextInt(height);
            int rz = min.getZ() + random.nextInt(depth);
            BlockPos candidate = new BlockPos(rx, ry, rz);

            var state = level.getBlockState(candidate);
            // Must strictly be air and no fluid, plus space directly above for badge
            if (!state.isAir() || !level.getFluidState(candidate).isEmpty() || !level.getBlockState(candidate.above()).isAir()) continue;

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

            // Create compact 3D floating text badge directly on top of the block (no wallhack see-through)
            Component badgeText = switch (tier) {
                case GOLD -> Component.literal("§6§l★ GOLD [2×] ★");
                case SILVER -> Component.literal("§f§l✦ SILBER [1.5×] ✦");
                case BRONZE -> Component.literal("§c§l◆ BRONZE [1×] ◆");
            };
            int badgeBg = switch (tier) {
                case GOLD -> 0xCC3A2000;
                case SILVER -> 0xCC182838;
                case BRONZE -> 0xCC301408;
            };
            UUID hologramUuid = de.browars.util.HologramHelper.createTargetBadge(
                    level,
                    candidate.getX() + 0.5,
                    candidate.getY() + 1.18,
                    candidate.getZ() + 0.5,
                    badgeText,
                    badgeBg
            );

            // Spawn visual spawn particles and chime for gold targets
            if (tier == ZoneTier.GOLD) {
                level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, true, true, candidate.getX() + 0.5, candidate.getY() + 0.5, candidate.getZ() + 0.5, 18, 0.35, 0.35, 0.35, 0.1);
                level.playSound(null, candidate, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.4f, 1.3f);
            } else {
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, true, true, candidate.getX() + 0.5, candidate.getY() + 0.5, candidate.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.05);
            }

            activeTargets.add(new TargetRushTarget(candidate, state, playTicks, lifetime, tier, hologramUuid));
            break;
        }
    }

    public boolean checkArrowTrajectoryHit(ServerLevel hitLevel, Vec3 start, Vec3 end, Projectile projectile) {
        if (!isRoundActive() || hitLevel != level) return false;
        for (TargetRushTarget t : new ArrayList<>(activeTargets)) {
            BlockPos pos = t.getPos();
            net.minecraft.world.phys.AABB hitBox = new net.minecraft.world.phys.AABB(
                    pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1.0, pos.getY() + 1.35, pos.getZ() + 1.0
            );
            var clip = hitBox.clip(start, end);
            if (clip.isPresent()) {
                return registerHit(hitLevel, t, clip.get(), projectile);
            }
        }
        return false;
    }

    public boolean handleTargetHit(ServerLevel hitLevel, BlockPos hitPos, BlockHitResult hitResult, Projectile projectile) {
        if (!isRoundActive() || hitLevel != level) return false;
        for (TargetRushTarget t : new ArrayList<>(activeTargets)) {
            if (t.getPos().equals(hitPos)) {
                return registerHit(hitLevel, t, hitResult.getLocation(), projectile);
            }
        }
        return false;
    }

    private boolean registerHit(ServerLevel hitLevel, TargetRushTarget hitTarget, Vec3 hitLoc, Projectile projectile) {
        if (!isRoundActive() || hitLevel != level) return false;
        if (!activeTargets.contains(hitTarget)) return false;

        ServerPlayer shooter = null;
        if (projectile.getOwner() instanceof ServerPlayer p && isPlayerParticipating(p.getUUID())) {
            shooter = p;
        }
        if (shooter == null) return false;

        BlockPos hitPos = hitTarget.getPos();
        double cx = hitPos.getX() + 0.5;
        double cy = hitPos.getY() + 0.5;
        double cz = hitPos.getZ() + 0.5;
        double dx = Math.abs(hitLoc.x - cx);
        double dy = Math.abs(hitLoc.y - cy);
        double dz = Math.abs(hitLoc.z - cz);
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double ratio = Math.max(0.0, 1.0 - (dist / 0.70));
        int precision = Math.max(1, (int) Math.ceil(15.0 * ratio));

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

        // Destroy target block, remove badge hologram and restore previous block
        hitTarget.remove(level);
        activeTargets.remove(hitTarget);
        projectile.discard();

        // Sudden death win condition
        if (inSuddenDeath && !isSoloMode) {
            int finalS1 = points.getOrDefault(p1Uuid, 0);
            int finalS2 = points.getOrDefault(p2Uuid, 0);
            if (shooter.getUUID().equals(p1Uuid)) {
                endDuelMatch(p1Uuid, p1Name, finalS1, finalS2, "Sudden Death Treffer von " + p1Name + "!");
            } else {
                endDuelMatch(p2Uuid, p2Name, finalS2, finalS1, "Sudden Death Treffer von " + p2Name + "!");
            }
        }

        return true;
    }

    private void clearStrayArrows() {
        if (arena.getTargetMin() == null || arena.getTargetMax() == null) return;
        BlockPos min = arena.getTargetMin();
        BlockPos max = arena.getTargetMax();
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(min).minmax(new net.minecraft.world.phys.AABB(max)).inflate(60);
        for (net.minecraft.world.entity.projectile.arrow.AbstractArrow arrow : level.getEntitiesOfClass(net.minecraft.world.entity.projectile.arrow.AbstractArrow.class, box)) {
            if (arrow.entityTags().contains("targetrush_arrow")) {
                arrow.discard();
            }
        }
    }

    private void purgeOrphanBadges() {
        if (arena.getTargetMin() == null || arena.getTargetMax() == null) return;
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(arena.getTargetMin())
                .minmax(new net.minecraft.world.phys.AABB(arena.getTargetMax())).inflate(15);
        for (net.minecraft.world.entity.Display.TextDisplay display : level.getEntitiesOfClass(net.minecraft.world.entity.Display.TextDisplay.class, box)) {
            if (display.entityTags().contains("browars_target_badge")) {
                display.discard();
            }
        }
    }

    private void clearActiveTargets() {
        for (TargetRushTarget t : activeTargets) {
            t.remove(level);
        }
        activeTargets.clear();
    }

    @Override
    protected void prepareArena() {
        purgeOrphanBadges();
    }

    @Override
    protected void restoreArena() {
        clearActiveTargets();
        clearStrayArrows();
        purgeOrphanBadges();
    }

    @Override
    protected void clearRound() {
        clearActiveTargets();
        clearStrayArrows();
        purgeOrphanBadges();
        inSuddenDeath = false;
    }

    @Override
    protected boolean allowMultipleGameSlots() {
        return true;
    }

    @Override
    protected void equipExtra(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        if (!held.is(Items.BOW)) {
            ItemStack bow = new ItemStack(Items.BOW);
            bow.set(DataComponents.CUSTOM_NAME, Component.literal("§6§lTarget Rush Bogen"));
            level.registryAccess().lookup(Registries.ENCHANTMENT).flatMap(r -> r.get(Enchantments.INFINITY))
                    .ifPresent(ench -> bow.enchant(ench, 1));
            MinigamePlayerState.markGameItem(bow);
            player.setItemSlot(EquipmentSlot.MAINHAND, bow);
        }

        // Keep 1 arrow in inventory slot 9 (so offhand stays free for clean vision)
        boolean hasArrow = false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(Items.ARROW)) {
                hasArrow = true;
                break;
            }
        }
        if (!hasArrow) {
            ItemStack arrow = new ItemStack(Items.ARROW);
            MinigamePlayerState.markGameItem(arrow);
            player.getInventory().setItem(9, arrow);
        }
        if (player.getOffhandItem().is(Items.ARROW) && MinigamePlayerState.isGameItem(player.getOffhandItem())) {
            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        }
    }
}
