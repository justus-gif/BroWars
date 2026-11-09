package de.browars.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SubmitNodeCollection.class)
public class SubmitNodeCollectionMixin {
    @Inject(method = "submitCustomGeometry", at = @At("HEAD"), cancellable = true)
    private void browars$routeWaterMask(
            PoseStack poseStack,
            RenderType renderType,
            SubmitNodeCollector.CustomGeometryRenderer renderer,
            CallbackInfo ci
    ) {
        if (renderType == RenderTypes.waterMask()
                || (renderType != null && renderType.toString().contains("water_mask"))) {
            ((SubmitNodeCollection) (Object) this).waterMask.submit(
                    new CustomFeatureRenderer.Submit(poseStack.last().copy(), renderType, renderer)
            );
            ci.cancel();
        }
    }
}
