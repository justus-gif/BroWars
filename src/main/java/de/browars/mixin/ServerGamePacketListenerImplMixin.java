package de.browars.mixin;

import de.browars.minigame.Minigame;
import de.browars.minigame.MinigameManager;
import de.browars.minigame.sumo.SumoGame;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImplMixin {

    @Shadow
    public ServerPlayer player;

    @Inject(method = "handlePunch", at = @At("RETURN"))
    private void browars$assistSumoPunch(ServerboundPunchPacket packet, CallbackInfo ci) {
        if (player == null || player.level() == null) return;

        Minigame active = MinigameManager.getInstance().getActiveGame();
        if (active instanceof de.browars.minigame.paintwar.PaintWarGame paintWar && paintWar.isRoundActive() && paintWar.isPlayerParticipating(player.getUUID())) {
            paintWar.onPlayerPunch(player);
            return;
        }
        if (active instanceof SumoGame sumo && sumo.isFightActive() && sumo.isPlayerParticipating(player.getUUID())) {
            ItemStack held = player.getMainHandItem();
            if (held.is(Items.STICK)) {
                ServerPlayer opponent = sumo.getOpponent(player);
                if (opponent != null && opponent.isAlive() && !opponent.isSpectator()) {
                    if (opponent.level() != player.level() || !player.hasLineOfSight(opponent)) return;
                    Vec3 eye = player.getEyePosition();
                    double reach = player.entityInteractionRange();
                    Vec3 end = eye.add(player.getViewVector(1.0F).scale(reach));
                    // Fixed margin lets a slightly missed crosshair connect without a wide auto-aim cone.
                    var targetBox = opponent.getBoundingBox().inflate(0.55);
                    var hit = targetBox.contains(eye) ? java.util.Optional.of(eye) : targetBox.clip(eye, end);
                    if (hit.isPresent()) {
                        var wall = player.level().clip(new net.minecraft.world.level.ClipContext(
                                eye, hit.get(), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                                net.minecraft.world.level.ClipContext.Fluid.NONE, player));
                        if (wall.getType() == net.minecraft.world.phys.HitResult.Type.MISS) player.attack(opponent);
                    }
                }
            }
        }
    }

    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void browars$preventDroppingGameItems(net.minecraft.network.protocol.game.ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        if (player == null) return;
        var action = packet.getAction();
        if (action == net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.DROP_ITEM
                || action == net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS
                || action == net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND) {
            if (MinigameManager.getInstance().isPlayerInActiveGame(player.getUUID())
                    || de.browars.minigame.MinigamePlayerState.isGameItem(player.getMainHandItem())
                    || de.browars.minigame.MinigamePlayerState.isGameItem(player.getOffhandItem())) {
                player.inventoryMenu.sendAllDataToRemote();
                ci.cancel();
            }
        }
    }
}
