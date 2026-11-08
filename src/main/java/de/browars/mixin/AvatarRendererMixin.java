package de.browars.mixin;

import de.browars.BroWarsMod;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {

    @Inject(
        method = "getArmPose(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void browars$crossWarCrossbowArmPose(Avatar avatar, ItemStack stack, InteractionHand hand, CallbackInfoReturnable<HumanoidModel.ArmPose> cir) {
        if (stack.is(BroWarsMod.CROSSWAR_CROSSBOW)) {
            if (!avatar.isSwinging() && CrossbowItem.isCharged(stack)) {
                cir.setReturnValue(HumanoidModel.ArmPose.CROSSBOW_HOLD);
                return;
            }
            if (avatar.getUsedItemHand() == hand && avatar.getUseItemRemainingTicks() > 0) {
                cir.setReturnValue(HumanoidModel.ArmPose.CROSSBOW_CHARGE);
            }
        }
    }

    @Inject(
        method = "shouldShowName(Lnet/minecraft/world/entity/Avatar;D)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void browars$crossWarNametagVisibility(Avatar avatar, double distanceToCameraSq, CallbackInfoReturnable<Boolean> cir) {
        if (de.browars.client.minigame.MinigameHudClientHolder.isActive()) {
            var state = de.browars.client.minigame.MinigameHudClientHolder.getState();
            if ("crosswar".equals(state.gameId())) {
                // In Cross War, nametags are hidden unless very close (within 6 blocks, 36.0 sq)
                if (distanceToCameraSq > 36.0) {
                    cir.setReturnValue(false);
                }
            }
        }
    }
}
