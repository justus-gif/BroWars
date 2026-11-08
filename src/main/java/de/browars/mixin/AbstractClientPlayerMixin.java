package de.browars.mixin;

import de.browars.BroWarsMod;
import de.browars.client.minigame.CrossWarClientHolder;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public class AbstractClientPlayerMixin {

    @Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
    private void browars$applyCrossWarAimFov(boolean isFirstPerson, float partialTick, CallbackInfoReturnable<Float> cir) {
        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
        if (player.isUsingItem() && player.getUseItem().is(BroWarsMod.CROSSWAR_CROSSBOW)
                && CrossWarClientHolder.getAmmo() > 0 && !CrossWarClientHolder.isReloading()) {
            cir.setReturnValue(cir.getReturnValue() * 0.40f);
        }
    }
}
