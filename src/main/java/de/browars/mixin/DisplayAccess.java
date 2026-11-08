package de.browars.mixin;

import com.mojang.math.Transformation;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Display.class)
public interface DisplayAccess {
    @Invoker("setTransformation") void browars$setTransformation(Transformation value);
    @Invoker("setTransformationInterpolationDuration") void browars$setTransformationDuration(int ticks);
    @Invoker("setTransformationInterpolationDelay") void browars$setTransformationDelay(int ticks);
    @Invoker("setPosRotInterpolationDuration") void browars$setTeleportDuration(int ticks);
    @Invoker("setBillboardConstraints") void browars$setBillboard(Display.BillboardConstraints value);
    @Invoker("setViewRange") void browars$setViewRange(float value);
}
