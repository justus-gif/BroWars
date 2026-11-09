package de.browars.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.browars.block.entity.SkyBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public class SkyBlockRenderer implements BlockEntityRenderer<SkyBlockEntity, SkyBlockRenderState> {
    private static final double MAX_RENDER_DISTANCE = 256.0;
    private static final double MAX_RENDER_DISTANCE_SQ = MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE;

    public SkyBlockRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public int getViewDistance() {
        return (int) MAX_RENDER_DISTANCE;
    }

    @Override
    public boolean shouldRender(SkyBlockEntity blockEntity, Vec3 cameraPos) {
        // 1. Cull immediately if all 6 neighbor faces are covered by other SkyBlocks
        if (blockEntity.getVisibleFaces() == 0) {
            return false;
        }

        // 2. High-performance allocation-free squared distance check
        BlockPos pos = blockEntity.getBlockPos();
        double dx = (pos.getX() + 0.5) - cameraPos.x;
        double dy = (pos.getY() + 0.5) - cameraPos.y;
        double dz = (pos.getZ() + 0.5) - cameraPos.z;
        return (dx * dx + dy * dy + dz * dz) <= MAX_RENDER_DISTANCE_SQ;
    }

    @Override
    public SkyBlockRenderState createRenderState() {
        return new SkyBlockRenderState();
    }

    @Override
    public void extractRenderState(SkyBlockEntity entity, SkyBlockRenderState state, float partialTick, Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay overlay) {
        BlockEntityRenderState.extractBase(entity, state, overlay);
        state.visibleFacesMask = entity.getVisibleFaces();
    }

    @Override
    public void submit(SkyBlockRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
        if (state.visibleFacesMask == 0) return;
        submitNodeCollector.submitCustomGeometry(poseStack, RenderTypes.waterMask(), state);
    }

    public static void renderFace(PoseStack.Pose pose, VertexConsumer consumer, int dirIndex) {
        switch (dirIndex) {
            case 0 -> { // DOWN (Y = 0)
                consumer.addVertex(pose, 0.0f, 0.0f, 1.0f);
                consumer.addVertex(pose, 0.0f, 0.0f, 0.0f);
                consumer.addVertex(pose, 1.0f, 0.0f, 0.0f);
                consumer.addVertex(pose, 1.0f, 0.0f, 1.0f);
            }
            case 1 -> { // UP (Y = 1)
                consumer.addVertex(pose, 0.0f, 1.0f, 0.0f);
                consumer.addVertex(pose, 0.0f, 1.0f, 1.0f);
                consumer.addVertex(pose, 1.0f, 1.0f, 1.0f);
                consumer.addVertex(pose, 1.0f, 1.0f, 0.0f);
            }
            case 2 -> { // NORTH (Z = 0)
                consumer.addVertex(pose, 1.0f, 1.0f, 0.0f);
                consumer.addVertex(pose, 1.0f, 0.0f, 0.0f);
                consumer.addVertex(pose, 0.0f, 0.0f, 0.0f);
                consumer.addVertex(pose, 0.0f, 1.0f, 0.0f);
            }
            case 3 -> { // SOUTH (Z = 1)
                consumer.addVertex(pose, 0.0f, 1.0f, 1.0f);
                consumer.addVertex(pose, 0.0f, 0.0f, 1.0f);
                consumer.addVertex(pose, 1.0f, 0.0f, 1.0f);
                consumer.addVertex(pose, 1.0f, 1.0f, 1.0f);
            }
            case 4 -> { // WEST (X = 0)
                consumer.addVertex(pose, 0.0f, 1.0f, 0.0f);
                consumer.addVertex(pose, 0.0f, 0.0f, 0.0f);
                consumer.addVertex(pose, 0.0f, 0.0f, 1.0f);
                consumer.addVertex(pose, 0.0f, 1.0f, 1.0f);
            }
            case 5 -> { // EAST (X = 1)
                consumer.addVertex(pose, 1.0f, 1.0f, 1.0f);
                consumer.addVertex(pose, 1.0f, 0.0f, 1.0f);
                consumer.addVertex(pose, 1.0f, 0.0f, 0.0f);
                consumer.addVertex(pose, 1.0f, 1.0f, 0.0f);
            }
        }
    }
}
