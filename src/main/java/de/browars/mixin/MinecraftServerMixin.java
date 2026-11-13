package de.browars.mixin;

import de.browars.minigame.MinigameManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {

    @Inject(method = "isUnderSpawnProtection", at = @At("HEAD"), cancellable = true)
    private void browars$bypassSpawnProtectionInMinigames(ServerLevel level, BlockPos pos, Player player, CallbackInfoReturnable<Boolean> cir) {
        if (player != null && MinigameManager.getInstance().isPlayerInActiveGame(player.getUUID())) {
            cir.setReturnValue(false);
        }
    }
}
