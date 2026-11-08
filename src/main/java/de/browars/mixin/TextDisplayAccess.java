package de.browars.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Display.TextDisplay.class)
public interface TextDisplayAccess {
    @Invoker("setText") void browars$setText(Component text);
    @Invoker("setBackgroundColor") void browars$setBackgroundColor(int color);
    @Invoker("setTextOpacity") void browars$setTextOpacity(byte opacity);
    @Invoker("setFlags") void browars$setFlags(byte flags);
    @Invoker("setLineWidth") void browars$setLineWidth(int width);
}
