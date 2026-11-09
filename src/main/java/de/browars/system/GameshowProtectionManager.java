package de.browars.system;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GameshowProtectionManager {
    private static boolean protectionEnabled = true;
    private static final Map<UUID, Long> LAST_FEEDBACK_TIME = new ConcurrentHashMap<>();

    public static boolean isProtectionEnabled() {
        return protectionEnabled;
    }

    public static void setProtectionEnabled(boolean enabled) {
        protectionEnabled = enabled;
    }

    public static void clearPlayer(UUID id) {
        LAST_FEEDBACK_TIME.remove(id);
    }

    public static void register() {
        // 1. Prevent block break start / mining animation in survival
        AttackBlockCallback.EVENT.register((Player player, Level level, InteractionHand hand, BlockPos pos, net.minecraft.core.Direction direction) -> {
            if (de.browars.minigame.MinigameManager.getInstance().getActiveGame() instanceof de.browars.minigame.paintwar.PaintWarGame paintWar
                    && paintWar.isRunning() && paintWar.isPlayerParticipating(player.getUUID())) {
                if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                    paintWar.onLeftClickBlock(serverPlayer, pos);
                }
                return InteractionResult.FAIL;
            }

            if (de.browars.minigame.MinigameManager.getInstance().getActiveGame() instanceof de.browars.minigame.spleef.SpleefGame spleef
                    && spleef.isRunning() && spleef.isPlayerParticipating(player.getUUID())) {
                BlockState state = level.getBlockState(pos);
                if (spleef.canBreakBlock(pos, state)) {
                    if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                            && level instanceof net.minecraft.server.level.ServerLevel serverLevel
                            && serverLevel.getServer().isUnderSpawnProtection(serverLevel, pos, serverPlayer)) {
                        spleef.onSnowBlockBroken(pos);
                        serverLevel.destroyBlock(pos, false, serverPlayer);
                        return InteractionResult.SUCCESS;
                    }
                    return InteractionResult.PASS;
                }
                return InteractionResult.FAIL;
            }

            if (de.browars.minigame.MinigameManager.getInstance().getActiveGame() instanceof de.browars.minigame.vaultwar.VaultWarGame vaultWar
                    && vaultWar.isRunning() && vaultWar.isPlayerParticipating(player.getUUID())) {
                if (vaultWar.isPlayerPlacedBlock(pos)) {
                    if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                            && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                        vaultWar.removePlayerPlacedBlock(pos);
                        serverLevel.destroyBlock(pos, true, serverPlayer);
                        return InteractionResult.SUCCESS;
                    }
                    return InteractionResult.SUCCESS;
                }
                sendActionBarWarning(player);
                return InteractionResult.FAIL;
            }

            if (de.browars.minigame.MinigameManager.getInstance().getActiveGame() instanceof de.browars.minigame.lavaescape.LavaEscapeGame lavaEscape
                    && lavaEscape.isRunning() && lavaEscape.isPlayerParticipating(player.getUUID())) {
                if (lavaEscape.isPlayerPlacedBlock(pos) || lavaEscape.isChestBlock(pos)) {
                    return InteractionResult.PASS;
                }
                sendActionBarWarning(player);
                return InteractionResult.FAIL;
            }

            if (level.isClientSide() && de.browars.minigame.MinigamePlayerState.isGameItem(player.getMainHandItem())
                    && player.getMainHandItem().has(net.minecraft.core.component.DataComponents.CAN_BREAK)) {
                return InteractionResult.PASS;
            }

            if (!protectionEnabled) return InteractionResult.PASS;

            if (de.browars.util.PermissionsHelper.isOpOrCreative(player)) {
                return InteractionResult.PASS;
            }

            sendActionBarWarning(player);
            return InteractionResult.FAIL;
        });

        // 2. Prevent actual block break execution
        PlayerBlockBreakEvents.BEFORE.register((Level level, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity) -> {
            if (de.browars.minigame.MinigameManager.getInstance().getActiveGame() instanceof de.browars.minigame.paintwar.PaintWarGame paintWar
                    && paintWar.isRunning() && paintWar.isPlayerParticipating(player.getUUID())) {
                return false;
            }

            if (de.browars.minigame.MinigameManager.getInstance().getActiveGame() instanceof de.browars.minigame.spleef.SpleefGame spleef
                    && spleef.isRunning() && spleef.isPlayerParticipating(player.getUUID())) {
                if (spleef.canBreakBlock(pos, state)) {
                    spleef.onSnowBlockBroken(pos);
                    return true;
                }
                return false;
            }

            if (de.browars.minigame.MinigameManager.getInstance().getActiveGame() instanceof de.browars.minigame.vaultwar.VaultWarGame vaultWar
                    && vaultWar.isRunning() && vaultWar.isPlayerParticipating(player.getUUID())) {
                if (vaultWar.isPlayerPlacedBlock(pos)) {
                    vaultWar.removePlayerPlacedBlock(pos);
                    return true;
                }
                sendActionBarWarning(player);
                return false;
            }

            if (de.browars.minigame.MinigameManager.getInstance().getActiveGame() instanceof de.browars.minigame.lavaescape.LavaEscapeGame lavaEscape
                    && lavaEscape.isRunning() && lavaEscape.isPlayerParticipating(player.getUUID())) {
                if (lavaEscape.isPlayerPlacedBlock(pos) || lavaEscape.isChestBlock(pos)) {
                    lavaEscape.removePlayerPlacedBlock(pos);
                    return true;
                }
                sendActionBarWarning(player);
                return false;
            }

            if (!protectionEnabled) return true;

            if (de.browars.util.PermissionsHelper.isOpOrCreative(player)) {
                return true;
            }

            sendActionBarWarning(player);
            return false;
        });

        // 3. Prevent block placing & block interactions (doors, trapdoors, chests, levers, etc.)
        UseBlockCallback.EVENT.register((Player player, Level level, InteractionHand hand, BlockHitResult hitResult) -> {
            if (de.browars.minigame.MinigameManager.getInstance().getActiveGame() instanceof de.browars.minigame.paintwar.PaintWarGame paintWar
                    && paintWar.isRunning() && paintWar.isPlayerParticipating(player.getUUID())) {
                if (hand == InteractionHand.MAIN_HAND) {
                    if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                        paintWar.onUseItem(serverPlayer, hand);
                    }
                    return InteractionResult.SUCCESS;
                }
                return InteractionResult.FAIL;
            }

            if (de.browars.minigame.MinigameManager.getInstance().getActiveGame() instanceof de.browars.minigame.vaultwar.VaultWarGame vaultWar
                    && vaultWar.isRunning() && vaultWar.isPlayerParticipating(player.getUUID())) {
                BlockState state = level.getBlockState(hitResult.getBlockPos());
                ItemStack held = player.getItemInHand(hand);

                // 1. Vault interaction with keys
                if (state.is(net.minecraft.world.level.block.Blocks.VAULT)) {
                    BlockPos vaultPos = hitResult.getBlockPos();
                    boolean isTrialKey = held.is(net.minecraft.world.item.Items.TRIAL_KEY);
                    boolean isOminousKey = held.is(net.minecraft.world.item.Items.OMINOUS_TRIAL_KEY);

                    if (isTrialKey || isOminousKey) {
                        if (vaultWar.isVaultOnCooldown(vaultPos)) {
                            if (!level.isClientSide() && player instanceof ServerPlayer sPlayer) {
                                int ticksLeft = vaultWar.getVaultCooldownTicks(vaultPos);
                                sPlayer.sendSystemMessage(Component.literal("§7[Vault War] Vault lädt noch nach... (" + String.format(java.util.Locale.ROOT, "%.1fs", ticksLeft / 20.0f) + ")"), true);
                                level.playSound(null, vaultPos, net.minecraft.sounds.SoundEvents.VAULT_INSERT_ITEM_FAIL, net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 1.2f);
                            }
                            return InteractionResult.SUCCESS;
                        }

                        if (!level.isClientSide() && player instanceof ServerPlayer sPlayer) {
                            held.shrink(1);
                            vaultWar.onUseKey(sPlayer, vaultPos, isOminousKey);
                        }
                        return InteractionResult.SUCCESS;
                    } else if (held.getItem() instanceof net.minecraft.world.item.BlockItem) {
                        BlockPos placePos = hitResult.getBlockPos().relative(hitResult.getDirection());
                        if (vaultWar.getArena().isInsideArena(placePos)) {
                            vaultWar.trackPlacedBlock(placePos);
                            return InteractionResult.PASS;
                        }
                        return InteractionResult.FAIL;
                    } else {
                        if (!level.isClientSide() && player instanceof ServerPlayer sPlayer) {
                            sPlayer.sendSystemMessage(Component.literal("§e[Vault War] §7Rechtsklick mit einem §fTrial Key §7oder §5Ominous Key§7!"), true);
                            level.playSound(null, vaultPos, net.minecraft.sounds.SoundEvents.VAULT_INSERT_ITEM_FAIL, net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
                        }
                        return InteractionResult.SUCCESS;
                    }
                }

                // 2. Station blocks (Anvils, Crafting Table, Grindstone, Smithing Table)
                if (state.is(net.minecraft.world.level.block.Blocks.ANVIL)
                        || state.is(net.minecraft.world.level.block.Blocks.CHIPPED_ANVIL)
                        || state.is(net.minecraft.world.level.block.Blocks.DAMAGED_ANVIL)
                        || state.is(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE)
                        || state.is(net.minecraft.world.level.block.Blocks.GRINDSTONE)
                        || state.is(net.minecraft.world.level.block.Blocks.SMITHING_TABLE)) {
                    return InteractionResult.PASS;
                }

                // 3. Placing utility blocks (e.g. Cobwebs)
                if (held.getItem() instanceof net.minecraft.world.item.BlockItem) {
                    BlockPos placePos = hitResult.getBlockPos().relative(hitResult.getDirection());
                    if (state.canBeReplaced()) {
                        placePos = hitResult.getBlockPos();
                    }
                    if (vaultWar.getArena().isInsideArena(placePos)) {
                        vaultWar.trackPlacedBlock(placePos);
                        return InteractionResult.PASS;
                    }
                    return InteractionResult.FAIL;
                }

                // 4. Combat / utility item use (weapons, potions, pearls, wind charges, food, shield)
                return InteractionResult.PASS;
            }

            if (de.browars.minigame.MinigameManager.getInstance().getActiveGame() instanceof de.browars.minigame.lavaescape.LavaEscapeGame lavaEscape
                    && lavaEscape.isRunning() && lavaEscape.isPlayerParticipating(player.getUUID())) {
                BlockState state = level.getBlockState(hitResult.getBlockPos());
                ItemStack held = player.getItemInHand(hand);

                // 1. Opening chests / barrels in the arena
                if (state.is(net.minecraft.world.level.block.Blocks.CHEST)
                        || state.is(net.minecraft.world.level.block.Blocks.BARREL)) {
                    return InteractionResult.PASS;
                }

                // 2. Placing blocks (Cobblestone, Scaffolding, Cobwebs, etc.)
                if (held.getItem() instanceof net.minecraft.world.item.BlockItem) {
                    BlockPos placePos = hitResult.getBlockPos().relative(hitResult.getDirection());
                    if (state.canBeReplaced()) {
                        placePos = hitResult.getBlockPos();
                    }
                    if (lavaEscape.getArena().isInsideArena(placePos)) {
                        if (!level.isClientSide()) lavaEscape.trackPlacedBlock(placePos);
                        return InteractionResult.PASS;
                    }
                    return InteractionResult.FAIL;
                }

                // 3. Placing water or picking up water with bucket
                if (held.is(net.minecraft.world.item.Items.WATER_BUCKET) || held.is(net.minecraft.world.item.Items.BUCKET)) {
                    BlockPos placePos = hitResult.getBlockPos().relative(hitResult.getDirection());
                    if (state.canBeReplaced()) {
                        placePos = hitResult.getBlockPos();
                    }
                    if (lavaEscape.getArena().isInsideArena(placePos)) {
                        if (!level.isClientSide()) lavaEscape.trackPlacedBlock(placePos);
                        return InteractionResult.PASS;
                    }
                    return InteractionResult.FAIL;
                }

                // 4. Combat & utility item use (weapons, potions, wind charges, rods, food)
                return InteractionResult.PASS;
            }

            if (!protectionEnabled) return InteractionResult.PASS;

            if (de.browars.util.PermissionsHelper.isOpOrCreative(player)) {
                return InteractionResult.PASS;
            }

            ItemStack held = player.getItemInHand(hand);
            if (held.is(de.browars.BroWarsMod.CROSSWAR_CROSSBOW)
                    || held.is(net.minecraft.world.item.Items.BOW)
                    || held.is(net.minecraft.world.item.Items.CROSSBOW)) {
                return InteractionResult.PASS;
            }

            sendActionBarWarning(player);
            return InteractionResult.FAIL;
        });
    }

    private static void sendActionBarWarning(Player player) {
        long now = System.currentTimeMillis();
        long last = LAST_FEEDBACK_TIME.getOrDefault(player.getUUID(), 0L);
        if (now - last > 1000) {
            LAST_FEEDBACK_TIME.put(player.getUUID(), now);
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(Component.literal("§c[BroWars] Aktionen im Survival-Modus sind gesperrt!"), true);
            }
        }
    }
}
