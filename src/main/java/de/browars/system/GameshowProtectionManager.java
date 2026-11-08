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

            if (!protectionEnabled) return InteractionResult.PASS;

            if (de.browars.util.PermissionsHelper.isOpOrCreative(player)) {
                return InteractionResult.PASS;
            }

            if (player.getItemInHand(hand).is(de.browars.BroWarsMod.CROSSWAR_CROSSBOW)) {
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
