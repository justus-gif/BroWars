package de.browars.mixin;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.KillCommand;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

@Mixin(KillCommand.class)
public class KillCommandMixin {

    @Inject(method = "kill", at = @At("HEAD"), cancellable = true)
    private static void browars$disableKillCommand(CommandSourceStack source, Collection<? extends Entity> targets, CallbackInfoReturnable<Integer> cir) {
        source.sendFailure(Component.literal("§cDer /kill-Befehl ist auf dieser Welt deaktiviert!"));
        cir.setReturnValue(0);
    }
}
