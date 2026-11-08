package de.browars.mixin;

import de.browars.BroWarsMod;
import de.browars.network.CrossWarShootPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftClientMixin {

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void browars$onStartAttack(CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = (Minecraft) (Object) this;
        if (mc.player != null && mc.player.getMainHandItem().is(BroWarsMod.CROSSWAR_CROSSBOW)) {
            ClientPlayNetworking.send(new CrossWarShootPayload());
            cir.setReturnValue(false);
        }
    }

    /**
     * In vanilla Minecraft, while a player is using an item (e.g. right-click ADS zoom),
     * handleKeybinds normally flushes keyAttack without calling startAttack().
     * This injection intercepts left clicks while aiming with Cross War Crossbow so
     * shooting while zooming functions seamlessly.
     */
    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void browars$handleCrossWarAimAttack(CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        if (mc.player != null) {
            if (mc.player.isUsingItem() && mc.player.getMainHandItem().is(BroWarsMod.CROSSWAR_CROSSBOW)) {
                while (mc.options.keyAttack.consumeClick()) {
                    ClientPlayNetworking.send(new CrossWarShootPayload());
                }
            }
            if (de.browars.minigame.MinigamePlayerState.isGameItem(mc.player.getMainHandItem())
                    || de.browars.minigame.MinigamePlayerState.isGameItem(mc.player.getOffhandItem())
                    || de.browars.client.minigame.MinigameHudClientHolder.isActive()) {
                while (mc.options.keyDrop.consumeClick()) {
                    // Prevent dropping game equipment (such as Spleef shovel)
                }
                while (mc.options.keySwapOffhand.consumeClick()) {
                    // Prevent swapping game equipment to offhand
                }
            }
        }
    }
}
