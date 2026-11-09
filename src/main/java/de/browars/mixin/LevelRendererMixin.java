package de.browars.mixin;

import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.OptionsRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Shadow
    private CloudRenderer cloudRenderer;

    @Shadow
    private OptionsRenderState optionsRenderState;

    @Shadow
    private LevelRenderState levelRenderState;

    @Inject(method = "executeSolid", at = @At("HEAD"))
    private void browars$beforeSolidTerrain(
            ChunkSectionsToRender chunks,
            FeatureRenderDispatcher.PreparedFrame features,
            RenderPass renderPass,
            CallbackInfo ci
    ) {
        // Render clouds behind terrain so they are visible through depth-masked sky blocks
        if (this.cloudRenderer != null && this.optionsRenderState != null && this.levelRenderState != null) {
            CloudStatus cloudStatus = this.optionsRenderState.cloudStatus;
            if (cloudStatus != null && cloudStatus != CloudStatus.OFF && ARGB.alpha(this.levelRenderState.cloudColor) > 0) {
                this.cloudRenderer.render(cloudStatus, renderPass);
            }
        }

        if (features != null) {
            features.executeWaterMask(renderPass);
        }
    }
}
