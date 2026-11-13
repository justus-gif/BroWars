package de.browars.mixin;

import de.browars.minigame.AbstractDuelGame;
import de.browars.minigame.Minigame;
import de.browars.minigame.MinigameManager;
import de.browars.system.WorldSpawnRespawnHandler;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin {

    @Inject(method = "canHarmPlayer", at = @At("HEAD"), cancellable = true)
    private void browars$allowMinigamePvp(net.minecraft.world.entity.player.Player other, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        Minigame active = MinigameManager.getInstance().getActiveGame();
        if (active != null && active.isRunning()
                && active.isPlayerParticipating(self.getUUID())
                && other != null && active.isPlayerParticipating(other.getUUID())) {
            cir.setReturnValue(true);
        }
    }

    /**
     * Intercepts ServerPlayer death at HEAD before ClientboundPlayerCombatKillPacket is sent.
     * Prevents the Minecraft "You Died! / Du bist gestorben" screen from ever appearing.
     */
    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void browars$neverActuallyDieServerPlayer(DamageSource source, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        ci.cancel();
        player.setHealth(player.getMaxHealth());
        player.clearFire();
        player.removeAllEffects();
        player.setDeltaMovement(0, 0, 0);
        player.fallDistance = 0;

        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.POOF, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.4, 0.5, 0.4, 0.05);
            serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1.0f, 0.8f);
        }

        Minigame active = MinigameManager.getInstance().getActiveGame();
        if (active instanceof AbstractDuelGame<?> duel && duel.isRunning() && duel.isPlayerParticipating(player.getUUID())) {
            if (duel.isRoundActive()) {
                duel.finishRoundByElimination(player.getUUID(), "Besiegt!");
            } else {
                duel.onPlayerRespawn(player);
            }
            return;
        }
        if (de.browars.gameshow.GameshowManager.getInstance().isPlayerInShow(player.getUUID())) {
            de.browars.gameshow.GameshowManager.getInstance().onPlayerRespawn(player);
            return;
        }

        WorldSpawnRespawnHandler.teleportToWorldSpawn(player);
    }
}
