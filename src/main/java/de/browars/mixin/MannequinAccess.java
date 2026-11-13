package de.browars.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.component.ResolvableProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Mannequin.class)
public interface MannequinAccess {
    @Invoker("setProfile")
    void browars$setProfile(ResolvableProfile profile);

    @Invoker("setImmovable")
    void browars$setImmovable(boolean immovable);

    @Invoker("setDescription")
    void browars$setDescription(Component description);

    @Invoker("setHideDescription")
    void browars$setHideDescription(boolean hide);
}
