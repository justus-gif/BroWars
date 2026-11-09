package de.browars.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

public class SkyBlockRenderState extends BlockEntityRenderState implements SubmitNodeCollector.CustomGeometryRenderer {
    public byte visibleFacesMask;

    @Override
    public void render(PoseStack.Pose pose, VertexConsumer consumer) {
        for (int i = 0; i < 6; i++) {
            if ((this.visibleFacesMask & (1 << i)) != 0) {
                SkyBlockRenderer.renderFace(pose, consumer, i);
            }
        }
    }
}
