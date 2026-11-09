package de.browars.mixin;

import de.browars.minigame.Minigame;
import de.browars.minigame.MinigameManager;
import de.browars.minigame.vaultwar.VaultWarArenaData;
import de.browars.minigame.vaultwar.VaultWarGame;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ThrownEnderpearl.class)
public class ThrownEnderpearlMixin {

    @Inject(method = "onHit", at = @At("HEAD"), cancellable = true)
    private void browars$preventPearlOutsideArena(HitResult hitResult, CallbackInfo ci) {
        ThrownEnderpearl pearl = (ThrownEnderpearl) (Object) this;
        Minigame active = MinigameManager.getInstance().getActiveGame();
        if (active instanceof VaultWarGame vaultWar && vaultWar.isRunning()) {
            Vec3 loc = hitResult.getLocation();
            VaultWarArenaData arena = vaultWar.getArena();

            // 1. Never allow teleporting outside the arena bounds
            if (!arena.isInsideArena(loc.x, loc.y, loc.z)) {
                ci.cancel();
                pearl.discard();
                if (pearl.getOwner() instanceof ServerPlayer player) {
                    player.sendSystemMessage(Component.literal("§c[Vault War] Du kannst dich nicht aus der Arena teleportieren!"), true);
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_PEARL_THROW, SoundSource.PLAYERS, 0.8f, 0.5f);
                }
                return;
            }

            // 2. In looting phase, never allow teleporting across the dividing fence
            if (vaultWar.isLootingActive() && pearl.getOwner() instanceof ServerPlayer player) {
                int spawnNum = player.getUUID().equals(vaultWar.getP1Uuid()) ? 1 : 2;
                if (!vaultWar.isPositionOnPlayerSide(loc.x, loc.z, spawnNum)) {
                    ci.cancel();
                    pearl.discard();
                    player.sendSystemMessage(Component.literal("§c[Vault War] Während der Loot-Phase kannst du nicht auf die gegnerische Seite!"), true);
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_PEARL_THROW, SoundSource.PLAYERS, 0.8f, 0.5f);
                }
            }
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void browars$discardPearlOutOfBounds(CallbackInfo ci) {
        ThrownEnderpearl pearl = (ThrownEnderpearl) (Object) this;
        Minigame active = MinigameManager.getInstance().getActiveGame();
        if (active instanceof VaultWarGame vaultWar && vaultWar.isRunning()) {
            VaultWarArenaData arena = vaultWar.getArena();
            if (arena != null && arena.hasArena()) {
                if (pearl.getX() < arena.getMinPos().getX() - 2.0
                        || pearl.getX() > arena.getMaxPos().getX() + 3.0
                        || pearl.getZ() < arena.getMinPos().getZ() - 2.0
                        || pearl.getZ() > arena.getMaxPos().getZ() + 3.0
                        || pearl.getY() < arena.getMinPos().getY() - 3.0) {
                    if (pearl.getOwner() instanceof ServerPlayer player) {
                        player.sendSystemMessage(Component.literal("§c[Vault War] Enderperle außerhalb der Arena verworfen!"), true);
                    }
                    pearl.discard();
                }
            }
        }
    }
}
