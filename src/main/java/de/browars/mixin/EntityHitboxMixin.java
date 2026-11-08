package de.browars.mixin;

import de.browars.util.SumoHitboxHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityHitboxMixin {

    @Inject(method = "getPickRadius", at = @At("HEAD"), cancellable = true)
    private void browars$expandSumoPickRadius(CallbackInfoReturnable<Float> cir) {
        Entity self = (Entity) (Object) this;
        if (SumoHitboxHelper.isSumoActiveForPlayer(self)) {
            // Expand crosshair targeting raycast by 0.55 blocks in all directions
            cir.setReturnValue(0.55F);
        }
    }

    @Inject(method = "makeBoundingBox(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/AABB;", at = @At("HEAD"), cancellable = true)
    private void browars$slimCrossWarArrowBox(Vec3 pos, CallbackInfoReturnable<AABB> cir) {
        Entity self = (Entity) (Object) this;
        if (self.entityTags().contains("crosswar_arrow")) {
            // Realistic arrow geometry: slim 0.12m width and height instead of default 0.5m cube
            double hw = 0.06;
            double hh = 0.06;
            cir.setReturnValue(new AABB(pos.x - hw, pos.y - hh, pos.z - hw, pos.x + hw, pos.y + hh, pos.z + hw));
        }
    }

    @Inject(method = "shouldBeSaved", at = @At("HEAD"), cancellable = true)
    private void browars$skipTemporaryGameDisplays(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (self.entityTags().contains("browars_minigame_temporary")) cir.setReturnValue(false);
    }
}
