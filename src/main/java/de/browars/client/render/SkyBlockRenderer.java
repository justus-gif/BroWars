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
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public class SkyBlockRenderer implements BlockEntityRenderer<SkyBlockEntity, SkyBlockRenderState> {

    public SkyBlockRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public int getViewDistance() {
        return 1024;
    }

    @Override
    public boolean shouldRender(SkyBlockEntity blockEntity, Vec3 cameraPos) {
        return true;
    }

    @Override
    public SkyBlockRenderState createRenderState() {
        return new SkyBlockRenderState();
    }

    @Override
    public void extractRenderState(SkyBlockEntity entity, SkyBlockRenderState state, float partialTick, Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay overlay) {
        BlockEntityRenderState.extractBase(entity, state, overlay);
        state.facesToShow.clear();
        for (Direction dir : Direction.values()) {
            if (entity.shouldRenderFace(dir)) {
                state.facesToShow.add(dir);
            }
        }
    }

    @Override
    public void submit(SkyBlockRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
        if (state.facesToShow.isEmpty()) return;

        submitNodeCollector.submitCustomGeometry(poseStack, RenderTypes.waterMask(), (pose, consumer) -> {
            for (Direction dir : state.facesToShow) {
                renderFace(pose, consumer, dir);
            }
        });
    }

    private static void renderFace(PoseStack.Pose pose, VertexConsumer consumer, Direction dir) {
        float[][] vCoords = switch (dir) {
            case UP -> new float[][]{ {0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0} };
            case DOWN -> new float[][]{ {0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1} };
            case NORTH -> new float[][]{ {1, 1, 0}, {1, 0, 0}, {0, 0, 0}, {0, 1, 0} };
            case SOUTH -> new float[][]{ {0, 1, 1}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1} };
            case WEST -> new float[][]{ {0, 1, 0}, {0, 0, 0}, {0, 0, 1}, {0, 1, 1} };
            case EAST -> new float[][]{ {1, 1, 1}, {1, 0, 1}, {1, 0, 0}, {1, 1, 0} };
        };

        for (int i = 0; i < 4; i++) {
            consumer.addVertex(pose, vCoords[i][0], vCoords[i][1], vCoords[i][2]);
        }
    }
}
