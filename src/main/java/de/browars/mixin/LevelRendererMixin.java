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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Shadow
    private CloudRenderer cloudRenderer;

    @Shadow
    private OptionsRenderState optionsRenderState;

    @Shadow
    private LevelRenderState levelRenderState;

    @Unique
    private boolean browars$cloudsRenderedThisFrame = false;

    @Inject(method = "executeSolid", at = @At("HEAD"))
    private void browars$beforeSolidTerrain(
            ChunkSectionsToRender chunks,
            FeatureRenderDispatcher.PreparedFrame features,
            RenderPass renderPass,
            CallbackInfo ci
    ) {
        this.browars$cloudsRenderedThisFrame = false;

        boolean hasWaterMask = features != null && features.hasAnyWaterMask();
        if (hasWaterMask) {
            // Render clouds exactly once before solid terrain so they appear behind SkyBlocks and terrain
            if (this.cloudRenderer != null && this.optionsRenderState != null && this.levelRenderState != null) {
                CloudStatus cloudStatus = this.optionsRenderState.cloudStatus;
                if (cloudStatus != null && cloudStatus != CloudStatus.OFF && ARGB.alpha(this.levelRenderState.cloudColor) > 0) {
                    this.cloudRenderer.render(cloudStatus, renderPass);
                    this.browars$cloudsRenderedThisFrame = true;
                }
            }

            features.executeWaterMask(renderPass);
        }
    }

    @Redirect(
            method = "executeClassicTransparency",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/CloudRenderer;render(Lnet/minecraft/client/CloudStatus;Lcom/mojang/renderpearl/api/commands/RenderPass;)V"
            )
    )
    private void browars$skipSecondCloudRender(CloudRenderer instance, CloudStatus status, RenderPass pass) {
        if (!this.browars$cloudsRenderedThisFrame) {
            instance.render(status, pass);
        }
    }

    @Redirect(
            method = "executeOit",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/CloudRenderer;renderOit(Lnet/minecraft/client/CloudStatus;Lnet/minecraft/client/renderer/oit/OitStage;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Lnet/minecraft/client/renderer/oit/OitRenderPassProvider$Parameters;)V"
            ),
            require = 0
    )
    private void browars$skipSecondOitCloudRender(
            CloudRenderer instance,
            CloudStatus status,
            net.minecraft.client.renderer.oit.OitStage stage,
            com.mojang.renderpearl.api.textures.GpuTextureView textureView,
            net.minecraft.client.renderer.oit.OitRenderPassProvider.Parameters params
    ) {
        if (!this.browars$cloudsRenderedThisFrame) {
            instance.renderOit(status, stage, textureView, params);
        }
    }
}
