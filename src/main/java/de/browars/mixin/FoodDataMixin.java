package de.browars.mixin;

import de.browars.minigame.Minigame;
import de.browars.minigame.MinigameManager;
import de.browars.minigame.crosswar.CrossWarGame;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FoodData.class)
public class FoodDataMixin {

    @Inject(method = "addExhaustion", at = @At("HEAD"), cancellable = true)
    private void browars$preventExhaustion(float exhaustion, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void browars$keepAlwaysSaturated(ServerPlayer player, CallbackInfo ci) {
        Minigame active = MinigameManager.getInstance().getActiveGame();
        if (active instanceof CrossWarGame crossWar && crossWar.isRoundActive() && crossWar.isPlayerParticipating(player.getUUID())) {
            ci.cancel();
            return;
        }

        FoodData self = (FoodData) (Object) this;
        if (self.getFoodLevel() < 20) {
            self.setFoodLevel(20);
        }
        if (self.getSaturationLevel() < 20.0f) {
            self.setSaturation(20.0f);
        }
    }
}
