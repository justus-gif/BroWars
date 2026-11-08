package de.browars.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractArrow.class)
public abstract class AbstractArrowMixin {

    @Shadow
    protected abstract boolean canHitEntity(Entity entity);

    @Inject(method = "findHitEntity", at = @At("HEAD"), cancellable = true)
    private void browars$accurateCrossWarArrowHit(Vec3 startVec, Vec3 endVec, CallbackInfoReturnable<EntityHitResult> cir) {
        AbstractArrow arrow = (AbstractArrow) (Object) this;
        if (arrow.entityTags().contains("crosswar_arrow")) {
            // Vanilla uses margin up to 0.30 blocks (massive hitbox inflation).
            // Cross War uses an accurate, slim margin (0.04 blocks) matching arrow shaft thickness.
            AABB searchBox = arrow.getBoundingBox().expandTowards(arrow.getDeltaMovement()).inflate(0.5D);
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                    arrow.level(), arrow, startVec, endVec, searchBox, this::canHitEntity, 0.04f
            );
            cir.setReturnValue(hit);
        }
    }

    @Inject(method = "shoot", at = @At("TAIL"))
    private void browars$boostTargetRushArrow(double x, double y, double z, float velocity, float inaccuracy, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        AbstractArrow arrow = (AbstractArrow) (Object) this;
        if (arrow.getOwner() instanceof net.minecraft.server.level.ServerPlayer player) {
            de.browars.minigame.Minigame active = de.browars.minigame.MinigameManager.getInstance().getActiveGame();
            if (active instanceof de.browars.minigame.targetrush.TargetRushGame targetRush && targetRush.isRoundActive() && targetRush.isPlayerParticipating(player.getUUID())) {
                arrow.setDeltaMovement(arrow.getDeltaMovement().scale(1.45));
                arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
                arrow.addTag("targetrush_arrow");
            }
        }
    }

    @Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true)
    private void browars$targetRushIgnorePlayers(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        AbstractArrow arrow = (AbstractArrow) (Object) this;
        if (arrow.entityTags().contains("targetrush_arrow") && entity instanceof net.minecraft.world.entity.player.Player) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "onHitBlock", at = @At("HEAD"), cancellable = true)
    private void browars$targetRushOnHitBlock(net.minecraft.world.phys.BlockHitResult hitResult, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        AbstractArrow arrow = (AbstractArrow) (Object) this;
        if (arrow.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            de.browars.minigame.Minigame active = de.browars.minigame.MinigameManager.getInstance().getActiveGame();
            if (active instanceof de.browars.minigame.targetrush.TargetRushGame targetRush && targetRush.isRoundActive()) {
                if (targetRush.handleTargetHit(serverLevel, hitResult.getBlockPos(), hitResult, arrow)) {
                    ci.cancel();
                }
            }
        }
    }
}
