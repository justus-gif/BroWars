package de.browars.mixin;

import de.browars.minigame.Minigame;
import de.browars.minigame.MinigameManager;
import de.browars.minigame.sumo.SumoGame;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public class PlayerDamageMixin {

    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void browars$preventPlayerPvpAndMagmaDamage(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Player victim = (Player) (Object) this;
        Minigame active = MinigameManager.getInstance().getActiveGame();
        boolean inSumo = (active instanceof SumoGame sumo && sumo.isRunning() && sumo.isPlayerParticipating(victim.getUUID()));

        if (inSumo) {
            SumoGame sumo = (SumoGame) active;
            // Only the actual opponent may cause knockback; environmental damage is ignored.
            if (!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer attacker)
                    || sumo.getOpponent(attacker) != victim || !sumo.registerHit(attacker.getUUID())) {
                cir.setReturnValue(false);
                return;
            }
            ((EntityAccessor) victim).setInvulnerableTime(0);
            return;
        }

        if (active instanceof de.browars.minigame.tnttag.TntTagGame tntTag
                && tntTag.isRunning() && tntTag.isPlayerParticipating(victim.getUUID())) {
            if (source.getEntity() instanceof net.minecraft.server.level.ServerPlayer attacker && victim instanceof net.minecraft.server.level.ServerPlayer serverVictim) {
                tntTag.onPlayerAttack(attacker, serverVictim);
            }
            ((EntityAccessor) victim).setInvulnerableTime(0);
            cir.setReturnValue(false);
            return;
        }

        if (active instanceof de.browars.minigame.crosswar.CrossWarGame crossWar
                && crossWar.isRoundActive() && crossWar.isPlayerParticipating(victim.getUUID())) {
            crossWar.handleDamage(victim, source, amount);
            cir.setReturnValue(false);
            return;
        }

        if (active instanceof de.browars.minigame.AbstractDuelGame<?> game
                && game.isRunning() && game.isPlayerParticipating(victim.getUUID())) {
            cir.setReturnValue(false);
            return;
        }

        // Outside Sumo: players can NEVER hurt each other (no PvP in lobby / color floor)
        if (source.getEntity() instanceof Player || source.getDirectEntity() instanceof Player) {
            cir.setReturnValue(false);
            return;
        }

        // Magma blocks deal 0 damage
        if (source.is(DamageTypes.HOT_FLOOR)) {
            cir.setReturnValue(false);
            return;
        }

        // Guarantee players never actually die from any lethal environmental damage
        if (amount >= victim.getHealth()) {
            victim.setHealth(victim.getMaxHealth());
            victim.clearFire();
            victim.removeAllEffects();
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "hurtServer", at = @At("RETURN"))
    private void browars$handleSumoAfterHurt(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Player victim = (Player) (Object) this;
        Minigame active = MinigameManager.getInstance().getActiveGame();
        if (active instanceof SumoGame sumo && sumo.isRunning() && sumo.isPlayerParticipating(victim.getUUID())) {
            // Keep victim at full health (no hearts loss in Sumo) and immediately ready for next hit
            victim.setHealth(victim.getMaxHealth());
            ((EntityAccessor) victim).setInvulnerableTime(0);
        }
    }

    @Inject(method = "getAttackStrengthScale", at = @At("HEAD"), cancellable = true)
    private void browars$alwaysFullAttackStrengthInSumo(float adjustTicks, CallbackInfoReturnable<Float> cir) {
        Player player = (Player) (Object) this;
        Minigame active = MinigameManager.getInstance().getActiveGame();
        if (active instanceof SumoGame sumo && sumo.isRunning() && sumo.isPlayerParticipating(player.getUUID())) {
            // Permanent 100% attack cooldown recharge so spamming hits never suffers cooldown penalties!
            cir.setReturnValue(1.0f);
        }
    }

    /**
     * Attacking normally resets this ticker. Suppressing both reset variants makes
     * Sumo's game stick genuinely cooldown-free on the server, including rapid
     * consecutive clicks.
     */
    @Inject(method = {"resetAttackStrengthTicker", "resetOnlyAttackStrengthTicker"}, at = @At("HEAD"), cancellable = true)
    private void browars$keepSumoAttackReady(CallbackInfo ci) {
        Player player = (Player) (Object) this;
        Minigame active = MinigameManager.getInstance().getActiveGame();
        if (active instanceof SumoGame sumo && sumo.isFightActive() && sumo.isPlayerParticipating(player.getUUID())) {
            ci.cancel();
        }
    }

    @Inject(method = "preventsBlockDrops", at = @At("HEAD"), cancellable = true)
    private void browars$preventSpleefSnowballDrops(CallbackInfoReturnable<Boolean> cir) {
        Player player = (Player) (Object) this;
        Minigame active = MinigameManager.getInstance().getActiveGame();
        if (active instanceof de.browars.minigame.spleef.SpleefGame spleef
                && spleef.isRunning() && spleef.isPlayerParticipating(player.getUUID())) {
            cir.setReturnValue(true);
        }
    }
}
