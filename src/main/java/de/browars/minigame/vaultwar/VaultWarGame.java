package de.browars.minigame.vaultwar;

import de.browars.minigame.AbstractDuelGame;
import de.browars.minigame.MinigameManager;
import de.browars.minigame.TeamColor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class VaultWarGame extends AbstractDuelGame<VaultWarArenaData> {

    public enum VaultPhase {
        LOOTING,
        COMBAT,
        SUDDEN_DEATH
    }

    private static final int LOOT_DURATION_TICKS = 1800; // 90 seconds (1:30 minutes)
    private static final int COMBAT_LIMIT_TICKS = 12000;  // 10 minutes
    private static final int NORMAL_KEYS = 25;
    private static final int OMINOUS_KEYS = 5;
    private static final int START_XP_LEVELS = 60;
    private static final int VAULT_COOLDOWN_TICKS = 25; // 1.25s cooldown per vault

    private VaultPhase vaultPhase = VaultPhase.LOOTING;
    private int lootingTicksLeft = LOOT_DURATION_TICKS;
    private int combatTicks = 0;

    private final Map<BlockPos, BlockState> originalFenceBlocks = new HashMap<>();
    private final Set<BlockPos> playerPlacedBlocks = new HashSet<>();
    private final Map<BlockPos, BlockState> pristineAnvils = new HashMap<>();
    private final Set<UUID> spawnedItemEntities = new HashSet<>();
    private final Map<BlockPos, Integer> vaultReadyAtTick = new HashMap<>();

    public VaultWarGame(MinecraftServer server, ServerLevel level, VaultWarArenaData arena,
                        ServerPlayer first, ServerPlayer second, TeamColor firstTeam, TeamColor secondTeam) {
        super(server, level, arena, first, second, firstTeam, secondTeam);
    }

    @Override
    public String getId() {
        return "vaultwar";
    }

    @Override
    public String getDisplayName() {
        return "Vault War";
    }

    @Override
    protected String startInstruction() {
        return "Öffne Vaults mit deinen Schlüsseln und schmiede Ausrüstung!";
    }

    @Override
    protected boolean shouldEquipChestplate(ServerPlayer player) {
        return false;
    }

    @Override
    protected boolean allowMultipleGameSlots() {
        return true;
    }

    @Override
    protected boolean shouldHealEveryTick() {
        return false;
    }

    @Override
    protected boolean shouldMaintainHunger() {
        return false;
    }

    @Override
    protected int targetScore() {
        return 2; // Best of 3: first to 2 points
    }

    @Override
    protected String matchStatus() {
        return "Best of 3 • Erster zu 2";
    }

    @Override
    protected int phaseNumber() {
        if (phase == Phase.COUNTDOWN) return 1;
        if (vaultPhase == VaultPhase.LOOTING) return 1;
        if (vaultPhase == VaultPhase.COMBAT) return 2;
        return 3;
    }

    @Override
    protected String phaseTitle() {
        if (phase == Phase.COUNTDOWN) return "Start";
        if (vaultPhase == VaultPhase.LOOTING) return "Loot-Phase";
        if (vaultPhase == VaultPhase.COMBAT) return "Kampf";
        return "Sudden Death";
    }

    @Override
    protected int timerRemaining() {
        if (phase == Phase.COUNTDOWN) return phaseTicks;
        if (vaultPhase == VaultPhase.LOOTING) return lootingTicksLeft;
        if (vaultPhase == VaultPhase.COMBAT) return Math.max(0, COMBAT_LIMIT_TICKS - combatTicks);
        return -1;
    }

    @Override
    protected int timerTotal() {
        if (phase == Phase.COUNTDOWN) return 100;
        if (vaultPhase == VaultPhase.LOOTING) return LOOT_DURATION_TICKS;
        if (vaultPhase == VaultPhase.COMBAT) return COMBAT_LIMIT_TICKS;
        return -1;
    }

    public boolean isCombatActive() {
        return isRoundActive() && (vaultPhase == VaultPhase.COMBAT || vaultPhase == VaultPhase.SUDDEN_DEATH);
    }

    public boolean isLootingActive() {
        return isRoundActive() && vaultPhase == VaultPhase.LOOTING;
    }

    public VaultWarArenaData getArena() {
        return arena;
    }

    public void trackPlacedBlock(BlockPos pos) {
        playerPlacedBlocks.add(pos.immutable());
    }

    public boolean isPlayerPlacedBlock(BlockPos pos) {
        return playerPlacedBlocks.contains(pos);
    }

    public void removePlayerPlacedBlock(BlockPos pos) {
        playerPlacedBlocks.remove(pos);
    }

    public boolean canBreak(BlockPos pos, BlockState state) {
        if (!isRunning() || !arena.isInsideArena(pos)) return false;
        // Never allow breaking the dividing fence
        if (arena.hasFence()) {
            BlockPos fMin = arena.getFenceMin();
            BlockPos fMax = arena.getFenceMax();
            int minX = Math.min(fMin.getX(), fMax.getX());
            int maxX = Math.max(fMin.getX(), fMax.getX());
            int minY = Math.min(fMin.getY(), fMax.getY());
            int maxY = Math.max(fMin.getY(), fMax.getY());
            int minZ = Math.min(fMin.getZ(), fMax.getZ());
            int maxZ = Math.max(fMin.getZ(), fMax.getZ());
            if (pos.getX() >= minX && pos.getX() <= maxX
                    && pos.getY() >= minY && pos.getY() <= maxY
                    && pos.getZ() >= minZ && pos.getZ() <= maxZ) {
                return false;
            }
        }
        // Always allow breaking vaults, utility stations, cobwebs, and player placed blocks
        if (state.is(Blocks.VAULT)
                || state.is(Blocks.ANVIL)
                || state.is(Blocks.CHIPPED_ANVIL)
                || state.is(Blocks.DAMAGED_ANVIL)
                || state.is(Blocks.CRAFTING_TABLE)
                || state.is(Blocks.COBWEB)) {
            return true;
        }
        return isPlayerPlacedBlock(pos);
    }

    public boolean isVaultOnCooldown(BlockPos pos) {
        return getVaultCooldownTicks(pos) > 0;
    }

    public int getVaultCooldownTicks(BlockPos pos) {
        Integer readyAt = vaultReadyAtTick.get(pos);
        if (readyAt == null) return 0;
        return Math.max(0, readyAt - playTicks);
    }

    public void onUseKey(ServerPlayer player, BlockPos pos, boolean isOminous) {
        if (!isRoundActive() || vaultPhase != VaultPhase.LOOTING) {
            player.sendSystemMessage(Component.literal("§c[Vault War] Die Loot-Phase ist nicht aktiv!"), true);
            return;
        }

        vaultReadyAtTick.put(pos.immutable(), playTicks + VAULT_COOLDOWN_TICKS);

        List<ItemStack> drops = isOminous
                ? VaultWarLoot.generateOminousLoot(level, player.getRandom())
                : VaultWarLoot.generateNormalLoot(level, player.getRandom());

        if (isOminous) {
            level.playSound(null, pos, SoundEvents.VAULT_INSERT_ITEM, SoundSource.BLOCKS, 1.0f, 0.8f);
            level.playSound(null, pos, SoundEvents.VAULT_OPEN_SHUTTER, SoundSource.BLOCKS, 1.0f, 0.8f);
            level.playSound(null, pos, SoundEvents.TRIAL_SPAWNER_OMINOUS_ACTIVATE, SoundSource.BLOCKS, 1.2f, 1.0f);
            level.sendParticles(ParticleTypes.TRIAL_SPAWNER_DETECTED_PLAYER_OMINOUS, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 15, 0.25, 0.25, 0.25, 0.05);
            level.sendParticles(ParticleTypes.OMINOUS_SPAWNING, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 8, 0.2, 0.2, 0.2, 0.02);
        } else {
            level.playSound(null, pos, SoundEvents.VAULT_INSERT_ITEM, SoundSource.BLOCKS, 1.0f, 1.0f);
            level.playSound(null, pos, SoundEvents.VAULT_OPEN_SHUTTER, SoundSource.BLOCKS, 1.0f, 1.0f);
            level.playSound(null, pos, SoundEvents.VAULT_EJECT_ITEM, SoundSource.BLOCKS, 1.0f, 1.0f);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 10, 0.25, 0.25, 0.25, 0.05);
        }

        for (ItemStack stack : drops) {
            if (stack == null || stack.isEmpty()) continue;
            ItemEntity itemEntity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, stack);
            double dx = (player.getRandom().nextDouble() - 0.5) * 0.16;
            double dy = 0.32 + player.getRandom().nextDouble() * 0.12;
            double dz = (player.getRandom().nextDouble() - 0.5) * 0.16;
            itemEntity.setDeltaMovement(dx, dy, dz);
            itemEntity.setPickUpDelay(3);
            level.addFreshEntity(itemEntity);
            spawnedItemEntities.add(itemEntity.getUUID());
        }
    }

    @Override
    protected void onRoundStarted() {
        vaultPhase = VaultPhase.LOOTING;
        lootingTicksLeft = LOOT_DURATION_TICKS;
        combatTicks = 0;

        restoreFence();

        ServerPlayer p1 = server.getPlayerList().getPlayer(p1Uuid);
        if (p1 != null) setupPlayerForLooting(p1);

        if (p2Uuid != null) {
            ServerPlayer p2 = server.getPlayerList().getPlayer(p2Uuid);
            if (p2 != null) setupPlayerForLooting(p2);
        }

        message("§eDie 1:30-minütige Loot-Phase hat begonnen! Öffne Vaults & nutze den Amboss!");
        setCenterTitle("§6§lLOOT-PHASE!", "1:30 Minuten Zeit!", 0xFFF59E0B, 40);
        playSound(SoundEvents.NOTE_BLOCK_BELL, 1.0f);
        playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f);
    }

    private void setupPlayerForLooting(ServerPlayer player) {
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.removeAllEffects();
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0f);
        player.setExperienceLevels(START_XP_LEVELS);
        player.experienceProgress = 0.0f;

        // Give starter stations & keys
        player.getInventory().add(new ItemStack(Items.VAULT, 5));
        player.getInventory().add(new ItemStack(Items.ANVIL, 1));
        player.getInventory().add(new ItemStack(Items.CRAFTING_TABLE, 1));
        player.getInventory().add(new ItemStack(Items.TRIAL_KEY, NORMAL_KEYS));
        player.getInventory().add(new ItemStack(Items.OMINOUS_TRIAL_KEY, OMINOUS_KEYS));

        player.inventoryMenu.sendAllDataToRemote();
    }

    @Override
    protected void tickRound(ServerPlayer first, ServerPlayer second) {
        if (first.gameMode.getGameModeForPlayer() != GameType.SURVIVAL) {
            first.setGameMode(GameType.SURVIVAL);
        }
        if (second != null && second.gameMode.getGameModeForPlayer() != GameType.SURVIVAL) {
            second.setGameMode(GameType.SURVIVAL);
        }

        if (vaultPhase == VaultPhase.LOOTING) {
            lootingTicksLeft--;

            if (lootingTicksLeft <= 100 && lootingTicksLeft > 0 && lootingTicksLeft % 20 == 0) {
                int secondsLeft = lootingTicksLeft / 20;
                setCenterTitle(Integer.toString(secondsLeft), "Zaun fällt gleich!", secondsLeft > 2 ? 0xFFFBBF24 : 0xFFEF4444, 20);
                playSound(SoundEvents.NOTE_BLOCK_PLING, new float[]{0, 2.0f, 1.6f, 1.3f, 1.1f, 0.9f}[secondsLeft]);
            }

            if (lootingTicksLeft <= 0) {
                startCombatPhase();
            }
        } else if (vaultPhase == VaultPhase.COMBAT || vaultPhase == VaultPhase.SUDDEN_DEATH) {
            combatTicks++;

            if (combatTicks >= COMBAT_LIMIT_TICKS && vaultPhase == VaultPhase.COMBAT) {
                startSuddenDeath();
            }

            if (vaultPhase == VaultPhase.SUDDEN_DEATH) {
                applySuddenDeathEffects(first, second);
            }
        }
    }

    private void startCombatPhase() {
        vaultPhase = VaultPhase.COMBAT;
        removeFence();

        setCenterTitle("§c§lKAMPF!", "Der Zaun ist gefallen – besiege deinen Gegner!", 0xFFEF4444, 50);
        playSound(SoundEvents.NOTE_BLOCK_PLING, 2.0f);
        playSound(SoundEvents.IRON_DOOR_OPEN, 1.0f);
        message("§c§lKAMPF! §eDer Zaun ist gefallen! PvP ist jetzt aktiviert!");
        MinigameManager.getInstance().broadcastHud(server, getHudState());
    }

    private void startSuddenDeath() {
        vaultPhase = VaultPhase.SUDDEN_DEATH;
        setCenterTitle("§4§lSUDDEN DEATH!", "Zeit abgelaufen (-1 ❤ alle 2s)", 0xFFDC2626, 60);
        playSound(SoundEvents.NOTE_BLOCK_BASS, 0.8f);
        message("§4§l[Vault War] SUDDEN DEATH! Zeitlimit erreicht!");
        MinigameManager.getInstance().broadcastHud(server, getHudState());
    }

    private void applySuddenDeathEffects(ServerPlayer first, ServerPlayer second) {
        List<ServerPlayer> players = (second == null) ? List.of(first) : List.of(first, second);
        for (ServerPlayer player : players) {
            if (player == null || player.isDeadOrDying() || player.level() != level) continue;
            player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, false, false, false));
            if (combatTicks % 40 == 0) {
                if (player.getHealth() <= 2.0f) {
                    player.setHealth(player.getMaxHealth());
                    finishRoundByElimination(player.getUUID(), "Sudden Death!");
                    return;
                }
                player.hurtServer(level, level.damageSources().wither(), 2.0f);
            }
        }
    }

    @Override
    protected void clearRound() {
        restoreFence();
        cleanupPlayerPlacedBlocks();
        restorePristineAnvils();
        clearArenaDrops();
        vaultReadyAtTick.clear();

        for (UUID id : List.of(p1Uuid, p2Uuid != null ? p2Uuid : p1Uuid)) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                player.getInventory().clearContent();
                player.removeAllEffects();
            }
        }
    }

    @Override
    protected void restoreArena() {
        restoreFence();
        cleanupPlayerPlacedBlocks();
        restorePristineAnvils();
        clearArenaDrops();
        vaultReadyAtTick.clear();
    }

    @Override
    protected void prepareArena() {
        if (arena.hasFence()) {
            BlockPos min = arena.getFenceMin();
            BlockPos max = arena.getFenceMax();
            int minX = Math.min(min.getX(), max.getX());
            int minY = Math.min(min.getY(), max.getY());
            int minZ = Math.min(min.getZ(), max.getZ());
            int maxX = Math.max(min.getX(), max.getX());
            int maxY = Math.max(min.getY(), max.getY());
            int maxZ = Math.max(min.getZ(), max.getZ());

            originalFenceBlocks.clear();

            List<VaultWarArenaData.SavedFenceBlock> currentWorldBlocks = new ArrayList<>();
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        BlockPos p = new BlockPos(x, y, z);
                        BlockState st = level.getBlockState(p);
                        if (!st.isAir()) {
                            currentWorldBlocks.add(new VaultWarArenaData.SavedFenceBlock(p.immutable(), st));
                            originalFenceBlocks.put(p.immutable(), st);
                        }
                    }
                }
            }

            if (!currentWorldBlocks.isEmpty()) {
                // User has built/modified the fence in the world! Persist these exact blocks to saved data.
                arena.setSavedFenceBlocks(currentWorldBlocks);
                VaultWarSavedData.save(level);
            } else if (!arena.getSavedFenceBlocks().isEmpty()) {
                // World blocks were air (e.g. game stopped/interrupted during combat) -> restore from persistent saved data
                for (VaultWarArenaData.SavedFenceBlock savedBlock : arena.getSavedFenceBlocks()) {
                    originalFenceBlocks.put(savedBlock.pos(), savedBlock.state());
                }
                restoreFence();
            }
        }

        if (arena.getArenaMin() != null && arena.getArenaMax() != null) {
            BlockPos min = arena.getArenaMin();
            BlockPos max = arena.getArenaMax();
            int minX = Math.min(min.getX(), max.getX());
            int minY = Math.min(min.getY(), max.getY());
            int minZ = Math.min(min.getZ(), max.getZ());
            int maxX = Math.max(min.getX(), max.getX());
            int maxY = Math.max(min.getY(), max.getY());
            int maxZ = Math.max(min.getZ(), max.getZ());

            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        BlockPos p = new BlockPos(x, y, z);
                        BlockState st = level.getBlockState(p);
                        if (st.is(Blocks.ANVIL) || st.is(Blocks.CHIPPED_ANVIL) || st.is(Blocks.DAMAGED_ANVIL)) {
                            pristineAnvils.putIfAbsent(p, Blocks.ANVIL.defaultBlockState());
                        }
                    }
                }
            }
        }
    }

    private void removeFence() {
        for (Map.Entry<BlockPos, BlockState> entry : originalFenceBlocks.entrySet()) {
            BlockPos p = entry.getKey();
            BlockState st = entry.getValue();
            level.levelEvent(2001, p, Block.getId(st));
            level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    public void restoreFence() {
        if (!arena.hasFence()) return;
        if (originalFenceBlocks.isEmpty() && !arena.getSavedFenceBlocks().isEmpty()) {
            for (VaultWarArenaData.SavedFenceBlock savedBlock : arena.getSavedFenceBlocks()) {
                originalFenceBlocks.put(savedBlock.pos(), savedBlock.state());
            }
        }
        for (Map.Entry<BlockPos, BlockState> entry : originalFenceBlocks.entrySet()) {
            level.setBlock(entry.getKey(), entry.getValue(), 3);
        }
    }

    public boolean isPositionOnPlayerSide(double x, double z, int spawnNumber) {
        if (!arena.hasFence()) return true;
        BlockPos min = arena.getFenceMin();
        BlockPos max = arena.getFenceMax();
        var spawn = arena.spawn(spawnNumber);
        if (spawn == null) return true;

        int dx = max.getX() - min.getX();
        int dz = max.getZ() - min.getZ();

        if (dx <= dz) {
            double fenceX = (min.getX() + max.getX() + 1) / 2.0;
            return (spawn.x() < fenceX) == (x < fenceX);
        } else {
            double fenceZ = (min.getZ() + max.getZ() + 1) / 2.0;
            return (spawn.z() < fenceZ) == (z < fenceZ);
        }
    }

    private void cleanupPlayerPlacedBlocks() {
        for (BlockPos pos : playerPlacedBlocks) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
        playerPlacedBlocks.clear();
    }

    private void restorePristineAnvils() {
        for (Map.Entry<BlockPos, BlockState> entry : pristineAnvils.entrySet()) {
            level.setBlock(entry.getKey(), entry.getValue(), 3);
        }
    }

    private void clearArenaDrops() {
        if (arena.getArenaMin() != null && arena.getArenaMax() != null) {
            AABB box = arena.toAABB();
            List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, box);
            for (ItemEntity item : items) {
                item.discard();
            }
        }
        for (UUID uuid : spawnedItemEntities) {
            Entity e = level.getEntity(uuid);
            if (e != null && e.isAlive()) {
                e.discard();
            }
        }
        spawnedItemEntities.clear();
    }
}
