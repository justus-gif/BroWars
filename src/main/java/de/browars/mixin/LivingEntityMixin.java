package de.browars.mixin;

import de.browars.minigame.AbstractDuelGame;
import de.browars.minigame.Minigame;
import de.browars.minigame.MinigameManager;
import de.browars.minigame.crosswar.CrossWarGame;
import de.browars.minigame.sumo.SumoGame;
import de.browars.system.WorldSpawnRespawnHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    @ModifyVariable(method = "knockback", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private double browars$modifySumoKnockbackStrength(double strength) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (entity instanceof Player victim) {
            Minigame active = MinigameManager.getInstance().getActiveGame();
            if (active instanceof SumoGame sumo && sumo.isFightActive() && sumo.isPlayerParticipating(victim.getUUID())) {
                double kb = sumo.getCurrentKnockback();
                if (kb > SumoGame.HIT_KNOCKBACK && victim.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, victim.getX(), victim.getY() + 1.0, victim.getZ(), 8, 0.3, 0.4, 0.3, 0.15);
                }
                return kb;
            }
        }
        return strength;
    }

    /**
     * Prevents any player from actually dying (no death screen, no inventory drop).
     * If lethal damage occurs in a minigame, the round resolves cleanly.
     * Outside minigames, the player is safely recovered to world spawn.
     */
    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void browars$neverActuallyDie(DamageSource source, CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (entity instanceof ServerPlayer player) {
            ci.cancel();
            player.setHealth(player.getMaxHealth());
            player.clearFire();
            player.removeAllEffects();
            player.setDeltaMovement(0, 0, 0);
            player.fallDistance = 0;

            Minigame active = MinigameManager.getInstance().getActiveGame();
            if (active instanceof AbstractDuelGame<?> duel && duel.isRoundActive() && duel.isPlayerParticipating(player.getUUID())) {
                duel.finishRoundByElimination(player.getUUID(), "Ausgeschieden");
                return;
            }

            WorldSpawnRespawnHandler.teleportToWorldSpawn(player);
        }
    }

    /**
     * Disables health regeneration in Cross War so damage remains permanent across the round.
     */
    @Inject(method = "heal", at = @At("HEAD"), cancellable = true)
    private void browars$preventCrossWarRegen(float amount, CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (entity instanceof Player player) {
            Minigame active = MinigameManager.getInstance().getActiveGame();
            if (active instanceof CrossWarGame crossWar && crossWar.isRoundActive() && crossWar.isPlayerParticipating(player.getUUID())) {
                ci.cancel();
            }
        }
    }
}
