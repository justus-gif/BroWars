package de.browars.mixin;

import de.browars.minigame.MinigameManager;
import de.browars.minigame.MinigamePlayerState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public class SlotMixin {

    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void browars$preventGameItemRemoval(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (MinigameManager.getInstance().getActiveGame() instanceof de.browars.minigame.vaultwar.VaultWarGame) {
            return;
        }
        Slot self = (Slot) (Object) this;
        ItemStack stack = self.getItem();
        if (!stack.isEmpty()) {
            if (MinigamePlayerState.isGameItem(stack) || MinigameManager.getInstance().isPlayerInActiveGame(player.getUUID())) {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void browars$lockMinigameSlots(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (MinigameManager.getInstance().getActiveGame() instanceof de.browars.minigame.vaultwar.VaultWarGame) {
            return;
        }
        Slot self = (Slot) (Object) this;
        ItemStack current = self.getItem();
        if (!current.isEmpty() && MinigamePlayerState.isGameItem(current)) {
            cir.setReturnValue(false);
        }
    }
}
