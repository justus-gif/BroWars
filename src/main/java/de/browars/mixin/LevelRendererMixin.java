package de.browars.mixin;

import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @Inject(method = "executeSolid", at = @At("HEAD"))
    private void browars$beforeSolidTerrain(
            ChunkSectionsToRender chunks,
            FeatureRenderDispatcher.PreparedFrame features,
            RenderPass renderPass,
            CallbackInfo ci
    ) {
        if (features != null) {
            features.executeWaterMask(renderPass);
        }
    }
}
