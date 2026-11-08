package de.browars.minigame;

import de.browars.BroWarsMod;
import de.browars.minigame.ui.MinigameHudState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One lifecycle for all duels: equipment, countdown, scores, HUD, respawn and cleanup. */
public abstract class AbstractDuelGame<A extends DuelArena> implements Minigame {
    protected enum Phase { COUNTDOWN, PLAYING, INTERMISSION, FINISHED }
    protected final MinecraftServer server;
    protected final ServerLevel level;
    protected final A arena;
    protected final UUID p1Uuid, p2Uuid;
    protected final String p1Name, p2Name;
    protected final TeamColor p1Team, p2Team;
    protected final boolean isSoloMode;
    protected final DuelScore score;
    protected Phase phase = Phase.COUNTDOWN;
    protected int phaseTicks = 100;
    protected int playTicks;
    private boolean running = true;
    private boolean started;
    private String centerTitle = "", centerSubtitle = "";
    private int titleColor = 0xFFFFFFFF, titleTicks, titleTotalTicks;
    private int lastPhaseNumber = 0;
    private final Map<UUID, MinigamePlayerState> originals = new HashMap<>();
    private final Map<UUID, ServerPlayer> lastPlayers = new HashMap<>();
    private final Map<BlockPos, BlockState> revertedTeamBlocks = new HashMap<>();
    private final Map<BlockPos, BlockState> countdownConcreteBlocks = new HashMap<>();

    protected AbstractDuelGame(MinecraftServer server, ServerLevel level, A arena,
                               ServerPlayer first, ServerPlayer second, TeamColor firstTeam, TeamColor secondTeam) {
        if (second != null && first.getUUID().equals(second.getUUID())) throw new IllegalArgumentException("Zwei verschiedene Spieler erforderlich");
        this.server = server;
        this.level = level;
        this.arena = arena;
        this.score = new DuelScore(targetScore());
        p1Uuid = first.getUUID();
        p1Name = first.getPlainTextName();
        p2Uuid = second == null ? null : second.getUUID();
        p2Name = second == null ? "" : second.getPlainTextName();
        isSoloMode = second == null;
        p1Team = firstTeam == null ? arena.getTeam1ColorEnum() : firstTeam;
        p2Team = secondTeam == null ? arena.getTeam2ColorEnum() : secondTeam;
        lastPlayers.put(p1Uuid, first);
        if (second != null) lastPlayers.put(p2Uuid, second);
    }

    @Override
    public final void start() {
        if (started) return;
        started = true;
        for (var entry : lastPlayers.entrySet()) originals.put(entry.getKey(), new MinigamePlayerState(entry.getValue()));
        transformTeamConcrete();
        prepareArena();
        setupPlayers();
        beginCountdown();
    }

    @Override public final boolean isRunning() { return running; }
    @Override public final boolean isPlayerParticipating(UUID id) { return p1Uuid.equals(id) || (p2Uuid != null && p2Uuid.equals(id)); }
    public final boolean isRoundActive() { return running && phase == Phase.PLAYING; }
    public final ServerPlayer getOpponent(ServerPlayer player) {
        if (isSoloMode || !isPlayerParticipating(player.getUUID())) return null;
        return server.getPlayerList().getPlayer(p1Uuid.equals(player.getUUID()) ? p2Uuid : p1Uuid);
    }

    @Override
    public final void tick() {
        if (!running || !started) return;
        if (titleTicks > 0) titleTicks--;
        ServerPlayer first = server.getPlayerList().getPlayer(p1Uuid);
        ServerPlayer second = p2Uuid == null ? null : server.getPlayerList().getPlayer(p2Uuid);
        if (first == null || (!isSoloMode && second == null)) {
            stop("Spieler hat das Spiel verlassen");
            return;
        }
        lastPlayers.put(p1Uuid, first);
        if (second != null) lastPlayers.put(p2Uuid, second);
        if (phase == Phase.PLAYING) {
            boolean firstOut = first.level() != level || first.isDeadOrDying();
            boolean secondOut = second != null && (second.level() != level || second.isDeadOrDying());
            if (finishRound(firstOut, secondOut, "Arena verlassen")) return;
        }
        maintainPlayer(first, p1Team);
        if (second != null) maintainPlayer(second, p2Team);
        if (phase != Phase.PLAYING) {
            freeze(first, 1);
            if (second != null) freeze(second, 2);
            phaseTicks--;
        }
        switch (phase) {
            case COUNTDOWN -> {
                if (phaseTicks <= 0) {
                    phase = Phase.PLAYING;
                    playTicks = 0;
                    lastPhaseNumber = phaseNumber();
                    removeCountdownConcrete();
                    onRoundStarted();
                    setCenterTitle("§a§lLOS!", startInstruction(), 0xFF22C55E, 30);
                    playSound(SoundEvents.NOTE_BLOCK_PLING, 2.0f);
                    for (UUID id : lastPlayers.keySet()) {
                        ServerPlayer p = server.getPlayerList().getPlayer(id);
                        if (p != null) {
                            level.sendParticles(ParticleTypes.POOF, p.getX(), p.getY() + 0.2, p.getZ(), 8, 0.3, 0.1, 0.3, 0.04);
                        }
                    }
                } else if (!onCountdownTick(phaseTicks) && phaseTicks % 20 == 0) {
                    int number = phaseTicks / 20;
                    setCenterTitle(Integer.toString(number), "Bereit machen!", number > 2 ? 0xFFFBBF24 : 0xFFEF4444, 25);
                    playSound(SoundEvents.NOTE_BLOCK_PLING, new float[]{0, 1.7f, 1.4f, 1.2f, 1.0f}[number]);
                }
            }
            case PLAYING -> {
                playTicks++;
                int currentPhase = phaseNumber();
                if (lastPhaseNumber != 0 && currentPhase != lastPhaseNumber) {
                    onPhaseChanged(lastPhaseNumber, currentPhase);
                }
                lastPhaseNumber = currentPhase;
                if (hasSpawnCampEjector()) {
                    ejectSpawnCampers(first, second);
                }
                tickRound(first, second);
            }
            case INTERMISSION -> { if (phaseTicks <= 0) beginCountdown(); }
            case FINISHED -> { if (phaseTicks <= 0) stop("Match regulär beendet"); }
        }
    }

    protected abstract void tickRound(ServerPlayer first, ServerPlayer second);
    protected abstract String startInstruction();
    protected void prepareArena() {}
    protected void onRoundStarted() {}
    protected boolean onCountdownTick(int phaseTicks) { return false; }
    protected void clearRound() {}
    protected void restoreArena() {}
    protected void equipExtra(ServerPlayer player) {}
    protected int targetColor() { return 0; }
    protected String targetName() { return ""; }
    protected int timerRemaining() { return -1; }
    protected int timerTotal() { return -1; }
    protected int soloProgress() { return playTicks / 20; }
    protected String soloStatus() { return "Solo • " + soloProgress() + " s"; }
    protected int targetScore() { return DuelScore.TARGET; }
    protected boolean shouldHealEveryTick() { return true; }
    protected String matchStatus() { return "Erster zu " + score.target() + " Punkten"; }
    protected int phaseNumber() { return 1; }
    protected String phaseTitle() { return "Aufwärmen"; }
    protected int taggerIndex() { return 0; }
    protected boolean hasSpawnCampEjector() { return false; }
    protected boolean allowMultipleGameSlots() { return false; }
    protected boolean shouldEquipChestplate(ServerPlayer player) { return true; }
    protected void onPhaseChanged(int oldPhase, int newPhase) {
        String title = phaseTitle();
        String color = switch (newPhase) {
            case 2 -> "§6";
            case 3 -> "§c";
            default -> "§a";
        };
        message(color + "Phase " + newPhase + ": " + title + "!");
        playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, newPhase == 3 ? 1.5f : 1.2f);
        for (UUID id : lastPlayers.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                player.sendSystemMessage(Component.literal(color + "§lPhase " + newPhase + ": " + title), true);
            }
        }
        MinigameManager.getInstance().broadcastHud(server, getHudState());
    }

    protected final boolean finishRound(boolean firstLost, boolean secondLost, String reason) {
        if (!isRoundActive()) return false;
        DuelScore.Result result = score.resolve(firstLost, secondLost, isSoloMode);
        if (result == DuelScore.Result.NONE) return false;
        clearRound();
        if (result == DuelScore.Result.SOLO_OUT) {
            phase = Phase.FINISHED;
            phaseTicks = 80;
            setCenterTitle("§c§lAUSGESCHIEDEN!", reason, 0xFFEF4444, 70);
        } else if (result == DuelScore.Result.DRAW) {
            phase = Phase.INTERMISSION;
            phaseTicks = 50;
            setCenterTitle("§e§lGLEICHSTAND!", "Runde wird wiederholt", 0xFFFBBF24, 45);
            message("§eBeide Spieler sind ausgeschieden. Runde wird wiederholt.");
        } else {
            String winner = result == DuelScore.Result.PLAYER_ONE ? p1Name : p2Name;
            UUID winnerUuid = result == DuelScore.Result.PLAYER_ONE ? p1Uuid : p2Uuid;
            ServerPlayer winnerPlayer = server.getPlayerList().getPlayer(winnerUuid);
            if (winnerPlayer != null) {
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, winnerPlayer.getX(), winnerPlayer.getY() + 1.2, winnerPlayer.getZ(), 15, 0.4, 0.4, 0.4, 0.1);
                if (score.isFinished()) {
                    level.sendParticles(ParticleTypes.FIREWORK, winnerPlayer.getX(), winnerPlayer.getY() + 1.5, winnerPlayer.getZ(), 35, 0.6, 0.8, 0.6, 0.15);
                }
            }
            MinigameSounds.playWin(server, score.isFinished(), p1Uuid, p2Uuid);
            if (score.isFinished()) {
                phase = Phase.FINISHED;
                phaseTicks = 120;
                setCenterTitle("§6§l" + winner + " GEWINNT!", "Endstand: " + score.first() + " - " + score.second(), 0xFFFFD700, 110);
            } else {
                phase = Phase.INTERMISSION;
                phaseTicks = 50;
                setCenterTitle("§a§lPUNKT FÜR " + winner + "!", "Spielstand: " + score.first() + " - " + score.second(), 0xFF22C55E, 45);
            }
            String reasonSuffix = (reason != null && !reason.isBlank()) ? " §8- §e" + reason : "";
            message("§a" + winner + " gewinnt Runde " + score.round() + "! (" + score.first() + ":" + score.second() + ")" + reasonSuffix);
            if (!score.isFinished()) score.nextRound();
        }
        placeCountdownConcrete();
        prepareArena();
        setupPlayers();
        return true;
    }

    private void beginCountdown() {
        phase = Phase.COUNTDOWN;
        phaseTicks = 100;
        placeCountdownConcrete();
        setCenterTitle("5", isSoloMode ? "Solo-Übungsmodus" : "Macht euch bereit!", 0xFF38BDF8, 25);
        playSound(SoundEvents.NOTE_BLOCK_PLING, 0.8f);
    }

    private void setupPlayers() {
        setupPlayer(server.getPlayerList().getPlayer(p1Uuid), 1, p1Team);
        if (p2Uuid != null) setupPlayer(server.getPlayerList().getPlayer(p2Uuid), 2, p2Team);
    }

    private void setupPlayer(ServerPlayer player, int number, TeamColor team) {
        if (player == null || player.isDeadOrDying()) return;
        var spawn = arena.spawn(number);
        player.stopRiding();
        player.teleportTo(level, spawn.x(), spawn.y(), spawn.z(), Set.of(), spawn.yaw(), spawn.pitch(), true);
        player.setDeltaMovement(0, 0, 0);
        player.fallDistance = 0;
        player.clearFire();
        player.removeAllEffects();
        player.setGameMode(GameType.ADVENTURE);
        player.setHealth(player.getMaxHealth());
        equipArmor(player, team);
        maintainPlayer(player, team);
        // Mask chunk rendering and block packet arrival so player never sees unreplaced team blocks
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.BLINDNESS, 20, 0, false, false, false));
    }

    private void freeze(ServerPlayer player, int number) {
        if (player.isDeadOrDying()) return;
        var spawn = arena.spawn(number);
        if (player.level() != level || player.distanceToSqr(spawn.x(), spawn.y(), spawn.z()) > 0.0001) {
            player.teleportTo(level, spawn.x(), spawn.y(), spawn.z(), Relative.ROTATION, 0f, 0f, false);
        }
        player.setDeltaMovement(0, 0, 0);
        player.fallDistance = 0;
    }

    private void maintainPlayer(ServerPlayer player, TeamColor team) {
        if (player.isDeadOrDying()) return;
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(20);
        if (shouldHealEveryTick()) player.setHealth(player.getMaxHealth());
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (shouldEquipChestplate(player)) {
            if (!MinigamePlayerState.isGameItem(chest) || !chest.is(Items.LEATHER_CHESTPLATE)) equipArmor(player, team);
        } else if (MinigamePlayerState.isGameItem(chest)) {
            player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        }
        // Only clean game-owned equipment; personal items are secured in the original state.
        if (!allowMultipleGameSlots()) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                if (i == 38 || i == player.getInventory().getSelectedSlot()) continue;
                if (MinigamePlayerState.isGameItem(player.getInventory().getItem(i))) player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
        equipExtra(player);
    }

    private void equipArmor(ServerPlayer player, TeamColor color) {
        ItemStack armor = new ItemStack(Items.LEATHER_CHESTPLATE);
        armor.set(DataComponents.DYED_COLOR, new DyedItemColor(color.hexColor));
        armor.set(DataComponents.CUSTOM_NAME, Component.literal(color.chatPrefix + "Team " + color.displayName));
        armor.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
        level.registryAccess().lookup(Registries.ENCHANTMENT).flatMap(r -> r.get(Enchantments.BINDING_CURSE))
                .ifPresent(enchantment -> armor.enchant(enchantment, 1));
        MinigamePlayerState.markGameItem(armor);
        player.setItemSlot(EquipmentSlot.CHEST, armor);
    }

    @Override public final void onPlayerRespawn(ServerPlayer player) {
        if (!isPlayerParticipating(player.getUUID())) return;
        lastPlayers.put(player.getUUID(), player);
        if (isRoundActive()) finishRound(p1Uuid.equals(player.getUUID()), !p1Uuid.equals(player.getUUID()), "Ausgeschieden");
        setupPlayer(player, p1Uuid.equals(player.getUUID()) ? 1 : 2, p1Uuid.equals(player.getUUID()) ? p1Team : p2Team);
    }

    @Override public final void stop(String reason) {
        if (!running) return;
        running = false;
        try {
            clearRound();
            restoreArena();
            for (var entry : originals.entrySet()) {
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player == null) player = lastPlayers.get(entry.getKey());
                if (player != null) entry.getValue().restore(player);
            }
            originals.clear();
            restoreTeamConcrete();
        } finally {
            message("§7Minispiel beendet: " + reason);
            MinigameManager.getInstance().broadcastHud(server, MinigameHudState.inactive());
        }
    }

    private void ejectSpawnCampers(ServerPlayer first, ServerPlayer second) {
        if (playTicks <= 80) return;
        checkAndEject(first, 1);
        if (second != null) checkAndEject(second, 2);
    }

    protected boolean isPlayerInSpawnChamber(ServerPlayer player, int spawnNumber) {
        var spawn = arena.spawn(spawnNumber);
        double dx = player.getX() - spawn.x();
        double dy = player.getY() - spawn.y();
        double dz = player.getZ() - spawn.z();
        return (dx * dx + dz * dz < 2.5 * 2.5 && Math.abs(dy) < 3.0);
    }

    private void checkAndEject(ServerPlayer player, int spawnNumber) {
        if (player == null || player.isDeadOrDying() || player.level() != level) return;
        if (isPlayerInSpawnChamber(player, spawnNumber)) {
            if (playTicks % 15 == 0) {
                var spawn = arena.spawn(spawnNumber);
                double rad = Math.toRadians(spawn.yaw());
                double pushX = -Math.sin(rad) * 1.3;
                double pushZ = Math.cos(rad) * 1.3;
                player.setDeltaMovement(pushX, 0.42, pushZ);
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player.getId(), new net.minecraft.world.phys.Vec3(pushX, 0.42, pushZ)));
                level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, player.getX(), player.getY() + 0.5, player.getZ(), 4, 0.2, 0.2, 0.2, 0.05);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.0f, 1.2f);
                player.sendSystemMessage(Component.literal("§c§lRaus aus der Spawnkammer!"), true);
            }
        }
    }

    public UUID getP1Uuid() { return p1Uuid; }
    public UUID getP2Uuid() { return p2Uuid; }

    public boolean finishRoundByElimination(UUID eliminatedPlayer, String reason) {
        if (!isRoundActive()) return false;
        boolean p1Lost = p1Uuid.equals(eliminatedPlayer);
        boolean p2Lost = p2Uuid != null && p2Uuid.equals(eliminatedPlayer);
        return finishRound(p1Lost, p2Lost, reason);
    }

    private record ScanRegion(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {}

    private void transformTeamConcrete() {
        List<ScanRegion> regions = new java.util.ArrayList<>();

        if (arena.getArenaMin() != null && arena.getArenaMax() != null) {
            boolean fullY = arena instanceof de.browars.minigame.paintwar.PaintWarArenaData;
            regions.add(new ScanRegion(
                    Math.min(arena.getArenaMin().getX(), arena.getArenaMax().getX()) - 16,
                    fullY ? level.getMinY() : Math.min(arena.getArenaMin().getY(), arena.getArenaMax().getY()) - 8,
                    Math.min(arena.getArenaMin().getZ(), arena.getArenaMax().getZ()) - 16,
                    Math.max(arena.getArenaMin().getX(), arena.getArenaMax().getX()) + 16,
                    fullY ? level.getMaxY() - 1 : Math.max(arena.getArenaMin().getY(), arena.getArenaMax().getY()) + 16,
                    Math.max(arena.getArenaMin().getZ(), arena.getArenaMax().getZ()) + 16
            ));
        }

        // Spawn Chamber 1
        if (arena instanceof de.browars.minigame.crosswar.CrossWarArenaData crossWarArena && crossWarArena.hasSpawnChamber1()) {
            regions.add(new ScanRegion(
                    crossWarArena.getSpawnChamber1Min().getX() - 12, crossWarArena.getSpawnChamber1Min().getY() - 6, crossWarArena.getSpawnChamber1Min().getZ() - 12,
                    crossWarArena.getSpawnChamber1Max().getX() + 12, crossWarArena.getSpawnChamber1Max().getY() + 12, crossWarArena.getSpawnChamber1Max().getZ() + 12
            ));
        } else if (arena instanceof de.browars.minigame.tnttag.TntTagArenaData tntArena && tntArena.hasSpawnChamber1()) {
            regions.add(new ScanRegion(
                    tntArena.getSpawnChamber1Min().getX() - 12, tntArena.getSpawnChamber1Min().getY() - 6, tntArena.getSpawnChamber1Min().getZ() - 12,
                    tntArena.getSpawnChamber1Max().getX() + 12, tntArena.getSpawnChamber1Max().getY() + 12, tntArena.getSpawnChamber1Max().getZ() + 12
            ));
        }
        if (arena.hasSpawn1()) {
            int sx = (int) Math.floor(arena.getSpawn1X());
            int sy = (int) Math.floor(arena.getSpawn1Y());
            int sz = (int) Math.floor(arena.getSpawn1Z());
            regions.add(new ScanRegion(sx - 24, sy - 10, sz - 24, sx + 24, sy + 16, sz + 24));
        }

        // Spawn Chamber 2
        if (arena instanceof de.browars.minigame.crosswar.CrossWarArenaData crossWarArena && crossWarArena.hasSpawnChamber2()) {
            regions.add(new ScanRegion(
                    crossWarArena.getSpawnChamber2Min().getX() - 12, crossWarArena.getSpawnChamber2Min().getY() - 6, crossWarArena.getSpawnChamber2Min().getZ() - 12,
                    crossWarArena.getSpawnChamber2Max().getX() + 12, crossWarArena.getSpawnChamber2Max().getY() + 12, crossWarArena.getSpawnChamber2Max().getZ() + 12
            ));
        } else if (arena instanceof de.browars.minigame.tnttag.TntTagArenaData tntArena && tntArena.hasSpawnChamber2()) {
            regions.add(new ScanRegion(
                    tntArena.getSpawnChamber2Min().getX() - 12, tntArena.getSpawnChamber2Min().getY() - 6, tntArena.getSpawnChamber2Min().getZ() - 12,
                    tntArena.getSpawnChamber2Max().getX() + 12, tntArena.getSpawnChamber2Max().getY() + 12, tntArena.getSpawnChamber2Max().getZ() + 12
            ));
        }
        if (arena.hasSpawn2()) {
            int sx = (int) Math.floor(arena.getSpawn2X());
            int sy = (int) Math.floor(arena.getSpawn2Y());
            int sz = (int) Math.floor(arena.getSpawn2Z());
            regions.add(new ScanRegion(sx - 24, sy - 10, sz - 24, sx + 24, sy + 16, sz + 24));
        }

        List<BlockPos> team1Positions = new java.util.ArrayList<>();
        List<BlockPos> team2Positions = new java.util.ArrayList<>();
        List<BlockPos> countdownPositions = new java.util.ArrayList<>();

        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        for (ScanRegion reg : regions) {
            int minX = reg.minX;
            int maxX = reg.maxX;
            int minY = Math.max(level.getMinY(), reg.minY);
            int maxY = Math.min(level.getMaxY() - 1, reg.maxY);
            int minZ = reg.minZ;
            int maxZ = reg.maxZ;

            int minChunkX = minX >> 4;
            int maxChunkX = maxX >> 4;
            int minChunkZ = minZ >> 4;
            int maxChunkZ = maxZ >> 4;
            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                    level.getChunk(cx, cz);
                }
            }

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!level.hasChunkAt(mpos.set(x, minY, z))) continue;
                    for (int y = minY; y <= maxY; y++) {
                        mpos.set(x, y, z);
                        BlockState state = level.getBlockState(mpos);
                        if (state.is(BroWarsMod.TEAM1_CONCRETE)) {
                            BlockPos imm = mpos.immutable();
                            team1Positions.add(imm);
                            revertedTeamBlocks.putIfAbsent(imm, state);
                            level.setBlock(mpos, p1Team.getConcreteBlock().defaultBlockState(), 3);
                        } else if (state.is(BroWarsMod.TEAM2_CONCRETE)) {
                            BlockPos imm = mpos.immutable();
                            team2Positions.add(imm);
                            revertedTeamBlocks.putIfAbsent(imm, state);
                            level.setBlock(mpos, p2Team.getConcreteBlock().defaultBlockState(), 3);
                        } else if (state.is(BroWarsMod.TEAM_COUNTDOWN_CONCRETE)) {
                            BlockPos imm = mpos.immutable();
                            if (revertedTeamBlocks.putIfAbsent(imm, state) == null) {
                                countdownPositions.add(imm);
                            }
                        }
                    }
                }
            }
        }

        for (BlockPos pos : countdownPositions) {
            TeamColor team = resolveCountdownBlockTeam(pos, team1Positions, team2Positions);
            BlockState teamConcrete = team.getConcreteBlock().defaultBlockState();
            countdownConcreteBlocks.put(pos, teamConcrete);
            level.setBlock(pos, teamConcrete, 3);
        }
    }

    private TeamColor resolveCountdownBlockTeam(BlockPos pos, List<BlockPos> team1Positions, List<BlockPos> team2Positions) {
        double minDist1 = Double.MAX_VALUE;
        for (BlockPos p1 : team1Positions) {
            minDist1 = Math.min(minDist1, pos.distSqr(p1));
        }
        double minDist2 = Double.MAX_VALUE;
        for (BlockPos p2 : team2Positions) {
            minDist2 = Math.min(minDist2, pos.distSqr(p2));
        }
        if (minDist1 <= 256.0 || minDist2 <= 256.0) {
            return minDist1 <= minDist2 ? p1Team : p2Team;
        }
        if (arena.hasSpawn1() && arena.hasSpawn2()) {
            double d1 = pos.distToCenterSqr(arena.getSpawn1X(), arena.getSpawn1Y(), arena.getSpawn1Z());
            double d2 = pos.distToCenterSqr(arena.getSpawn2X(), arena.getSpawn2Y(), arena.getSpawn2Z());
            return d1 <= d2 ? p1Team : p2Team;
        }
        return p1Team;
    }

    private void placeCountdownConcrete() {
        for (Map.Entry<BlockPos, BlockState> entry : countdownConcreteBlocks.entrySet()) {
            level.setBlock(entry.getKey(), entry.getValue(), 3);
        }
    }

    private void removeCountdownConcrete() {
        for (BlockPos pos : countdownConcreteBlocks.keySet()) {
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private void restoreTeamConcrete() {
        for (Map.Entry<BlockPos, BlockState> entry : revertedTeamBlocks.entrySet()) {
            level.setBlock(entry.getKey(), entry.getValue(), 3);
        }
        revertedTeamBlocks.clear();
        countdownConcreteBlocks.clear();
    }

    protected int p1Score() { return isSoloMode ? soloProgress() : score.first(); }
    protected int p2Score() { return score.second(); }

    @Override public final MinigameHudState getHudState() {
        return new MinigameHudState(running, getId(), getDisplayName(), p1Name, p1Score(),
                p1Team.hexColor, p2Name, p2Score(), p2Team.hexColor, score.target(), score.round(),
                isSoloMode ? soloStatus() : matchStatus(), timerRemaining(), timerTotal(), targetColor(), targetName(),
                centerTitle, centerSubtitle, titleColor, titleTicks, titleTotalTicks,
                phaseNumber(), phaseTitle(), taggerIndex());
    }

    protected final void setCenterTitle(String title, String subtitle, int color, int ticks) {
        centerTitle = title; centerSubtitle = subtitle; titleColor = color; titleTicks = titleTotalTicks = ticks;
        MinigameManager.getInstance().broadcastHud(server, getHudState());
    }
    protected final void playSound(Holder<SoundEvent> sound, float pitch) { MinigameSounds.play(server, sound, pitch, p1Uuid, p2Uuid); }
    protected final void playSound(SoundEvent sound, float pitch) { playSound(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), pitch); }
    protected final void message(String text) {
        var component = Component.literal("[" + getDisplayName() + "] " + text);
        for (UUID id : lastPlayers.keySet()) {
            var player = server.getPlayerList().getPlayer(id);
            if (player != null) player.sendSystemMessage(component);
        }
    }
}
