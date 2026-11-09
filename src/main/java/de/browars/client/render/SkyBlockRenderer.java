package de.browars.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.browars.BroWarsMod;
import de.browars.block.entity.SkyBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import org.joml.Vector4fc;

public class SkyBlockRenderer implements BlockEntityRenderer<SkyBlockEntity, SkyBlockRenderState> {
    private static final Identifier SKY_VIEW_TEXTURE = Identifier.fromNamespaceAndPath(BroWarsMod.MOD_ID, "textures/block/sky_block_view.png");

    public SkyBlockRenderer(BlockEntityRendererProvider.Context context) {
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

        BlockPos blockPos = state.blockPos;
        if (blockPos == null) return;

        Minecraft mc = Minecraft.getInstance();
        float partialTick = cameraRenderState.cameraEntityPartialTicks;
        Vec3 camPos = cameraRenderState.pos != null ? cameraRenderState.pos : (mc.player != null ? mc.player.getEyePosition(partialTick) : Vec3.ZERO);

        Vector3fc skyColorVec = null;
        Vector3fc fogColorVec = null;
        Vector4fc sunsetVec = null;
        if (mc.gameRenderer != null && mc.gameRenderer.mainCamera() != null) {
            try {
                EnvironmentAttributeProbe probe = mc.gameRenderer.mainCamera().attributeProbe();
                if (probe != null) {
                    skyColorVec = probe.getValue(EnvironmentAttributes.SKY_COLOR, partialTick);
                    fogColorVec = probe.getValue(EnvironmentAttributes.FOG_COLOR, partialTick);
                    sunsetVec = probe.getValue(EnvironmentAttributes.SUNRISE_SUNSET_COLOR, partialTick);
                }
            } catch (Exception ignored) {
            }
        }

        final float skyR = skyColorVec != null ? skyColorVec.x() : 0.47f;
        final float skyG = skyColorVec != null ? skyColorVec.y() : 0.65f;
        final float skyB = skyColorVec != null ? skyColorVec.z() : 1.00f;

        final float fogR = fogColorVec != null ? fogColorVec.x() : 0.75f;
        final float fogG = fogColorVec != null ? fogColorVec.y() : 0.84f;
        final float fogB = fogColorVec != null ? fogColorVec.z() : 1.00f;

        final Vector4fc finalSunset = sunsetVec;
        final double camX = camPos.x;
        final double camY = camPos.y;
        final double camZ = camPos.z;
        final int bpX = blockPos.getX();
        final int bpY = blockPos.getY();
        final int bpZ = blockPos.getZ();

        submitNodeCollector.submitCustomGeometry(poseStack, RenderTypes.entitySolid(SKY_VIEW_TEXTURE), (pose, consumer) -> {
            for (Direction dir : state.facesToShow) {
                renderFace(pose, consumer, dir, bpX, bpY, bpZ, camX, camY, camZ, skyR, skyG, skyB, fogR, fogG, fogB, finalSunset);
            }
        });
    }

    private static void renderFace(PoseStack.Pose pose, VertexConsumer consumer, Direction dir,
                                   int bpX, int bpY, int bpZ, double camX, double camY, double camZ,
                                   float skyR, float skyG, float skyB, float fogR, float fogG, float fogB,
                                   Vector4fc sunsetVec) {
        float[][] vCoords;
        float nx = dir.getStepX();
        float ny = dir.getStepY();
        float nz = dir.getStepZ();

        switch (dir) {
            case UP -> vCoords = new float[][]{
                    {0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}
            };
            case DOWN -> vCoords = new float[][]{
                    {0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}
            };
            case NORTH -> vCoords = new float[][]{
                    {1, 0, 0}, {0, 0, 0}, {0, 1, 0}, {1, 1, 0}
            };
            case SOUTH -> vCoords = new float[][]{
                    {0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}
            };
            case WEST -> vCoords = new float[][]{
                    {0, 0, 0}, {0, 0, 1}, {0, 1, 1}, {0, 1, 0}
            };
            case EAST -> vCoords = new float[][]{
                    {1, 0, 1}, {1, 0, 0}, {1, 1, 0}, {1, 1, 1}
            };
            default -> { return; }
        }

        float[] u = new float[4];
        float[] v = new float[4];
        int[] red = new int[4];
        int[] green = new int[4];
        int[] blue = new int[4];

        for (int i = 0; i < 4; i++) {
            double wx = bpX + vCoords[i][0];
            double wy = bpY + vCoords[i][1];
            double wz = bpZ + vCoords[i][2];

            double dx = wx - camX;
            double dy = wy - camY;
            double dz = wz - camZ;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 0.0001) len = 0.0001;

            double ndx = dx / len;
            double ndy = dy / len;
            double ndz = dz / len;

            double phi = Math.atan2(ndz, ndx);
            double theta = Math.asin(Math.max(-1.0, Math.min(1.0, ndy)));

            u[i] = (float) (phi / (2.0 * Math.PI) + 0.5);
            v[i] = (float) (0.5 - theta / Math.PI);

            float r, g, b;
            if (ndy >= 0.0) {
                float t = (float) Math.pow(Math.min(1.0, ndy), 0.75);
                r = fogR + (skyR - fogR) * t;
                g = fogG + (skyG - fogG) * t;
                b = fogB + (skyB - fogB) * t;

                if (sunsetVec != null && sunsetVec.w() > 0.01f) {
                    float sunAlpha = sunsetVec.w() * (1.0f - (float) ndy);
                    r = r + (sunsetVec.x() - r) * sunAlpha;
                    g = g + (sunsetVec.y() - g) * sunAlpha;
                    b = b + (sunsetVec.z() - b) * sunAlpha;
                }
            } else {
                float t = (float) Math.min(1.0, -ndy * 2.0);
                float voidR = 0.03f, voidG = 0.03f, voidB = 0.05f;
                r = fogR + (voidR - fogR) * t;
                g = fogG + (voidG - fogG) * t;
                b = fogB + (voidB - fogB) * t;
            }

            red[i] = Math.max(0, Math.min(255, (int)(r * 255.0f)));
            green[i] = Math.max(0, Math.min(255, (int)(g * 255.0f)));
            blue[i] = Math.max(0, Math.min(255, (int)(b * 255.0f)));
        }

        // Fix UV seam wrapping around the +/-PI boundary
        for (int i = 1; i < 4; i++) {
            float diff = u[i] - u[0];
            if (diff > 0.5f) {
                u[i] -= 1.0f;
            } else if (diff < -0.5f) {
                u[i] += 1.0f;
            }
        }

        for (int i = 0; i < 4; i++) {
            consumer.addVertex(pose, vCoords[i][0], vCoords[i][1], vCoords[i][2])
                    .setColor(red[i], green[i], blue[i], 255)
                    .setUv(u[i], v[i])
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(LightCoordsUtil.FULL_BRIGHT)
                    .setNormal(pose, nx, ny, nz);
        }
    }
}
