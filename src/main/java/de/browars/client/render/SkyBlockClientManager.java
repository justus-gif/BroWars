package de.browars.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.browars.BroWarsMod;
import de.browars.block.entity.SkyBlockEntity;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * High-performance client-side batch & greedy-mesh manager for all SkyBlocks.
 * Replaces thousands of individual per-frame BlockEntityRenderer dispatches and
 * neighbor lookups with a single cached, greedy-meshed draw call per frame.
 */
public final class SkyBlockClientManager {
    private static final LongOpenHashSet SKY_BLOCK_POSITIONS = new LongOpenHashSet();
    private static final LongOpenHashSet REMAINING_FACES = new LongOpenHashSet();
    private static final LongArrayList FACE_LIST = new LongArrayList();

    private static ClientLevel trackedLevel = null;
    private static boolean dirty = true;

    private static int originX = 0;
    private static int originY = 0;
    private static int originZ = 0;
    private static float[] cachedVertices = new float[0];
    private static int cachedVertexFloatCount = 0;

    private static final SubmitNodeCollector.CustomGeometryRenderer BATCH_RENDERER = SkyBlockClientManager::renderBatchedMesh;

    private SkyBlockClientManager() {
    }

    public static void register(SkyBlockEntity entity) {
        if (entity == null || entity.isRemoved()) return;
        Level level = entity.getLevel();
        if (!(level instanceof ClientLevel clientLevel)) return;

        checkLevelChange(clientLevel);
        long packed = entity.getBlockPos().asLong();
        if (SKY_BLOCK_POSITIONS.add(packed)) {
            dirty = true;
        }
        entity.clientRegistered = true;
    }

    public static void unregister(SkyBlockEntity entity) {
        if (entity == null) return;
        entity.clientRegistered = false;
        long packed = entity.getBlockPos().asLong();
        if (SKY_BLOCK_POSITIONS.remove(packed)) {
            dirty = true;
        }
    }

    public static void markDirtyAt(Level level, BlockPos pos) {
        if (!(level instanceof ClientLevel clientLevel)) return;
        checkLevelChange(clientLevel);
        long packed = pos.asLong();
        if (clientLevel.getBlockState(pos).is(BroWarsMod.SKY_BLOCK)) {
            if (SKY_BLOCK_POSITIONS.add(packed)) {
                dirty = true;
            }
        } else {
            if (SKY_BLOCK_POSITIONS.remove(packed)) {
                dirty = true;
            }
        }
    }

    private static void checkLevelChange(ClientLevel level) {
        if (trackedLevel != level) {
            trackedLevel = level;
            SKY_BLOCK_POSITIONS.clear();
            cachedVertexFloatCount = 0;
            dirty = true;
        }
    }

    public static void submitAll(PoseStack poseStack, Vec3 cameraPos, SubmitNodeCollector collector) {
        ClientLevel currentLevel = Minecraft.getInstance().level;
        if (currentLevel == null) {
            if (trackedLevel != null) {
                trackedLevel = null;
                SKY_BLOCK_POSITIONS.clear();
                cachedVertexFloatCount = 0;
            }
            return;
        }

        checkLevelChange(currentLevel);

        if (SKY_BLOCK_POSITIONS.isEmpty()) {
            return;
        }

        if (dirty) {
            rebuildGreedyMesh(currentLevel);
            dirty = false;
        }

        if (cachedVertexFloatCount == 0) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(
                (double) originX - cameraPos.x(),
                (double) originY - cameraPos.y(),
                (double) originZ - cameraPos.z()
        );
        collector.submitCustomGeometry(poseStack, RenderTypes.waterMask(), BATCH_RENDERER);
        poseStack.popPose();
    }

    private static void renderBatchedMesh(PoseStack.Pose pose, VertexConsumer consumer) {
        final float[] verts = cachedVertices;
        final int len = cachedVertexFloatCount;
        for (int i = 0; i < len; i += 3) {
            consumer.addVertex(pose, verts[i], verts[i + 1], verts[i + 2]);
        }
    }

    private static void rebuildGreedyMesh(ClientLevel level) {
        BlockPos.MutableBlockPos mutPos = new BlockPos.MutableBlockPos();
        LongIterator it = SKY_BLOCK_POSITIONS.iterator();
        boolean first = true;
        int minX = 0, minY = 0, minZ = 0;

        while (it.hasNext()) {
            long packed = it.nextLong();
            int x = BlockPos.getX(packed);
            int y = BlockPos.getY(packed);
            int z = BlockPos.getZ(packed);
            mutPos.set(x, y, z);

            if (!level.hasChunkAt(mutPos) || !level.getBlockState(mutPos).is(BroWarsMod.SKY_BLOCK)) {
                it.remove();
                continue;
            }

            if (first) {
                minX = x;
                minY = y;
                minZ = z;
                first = false;
            } else {
                if (x < minX) minX = x;
                if (y < minY) minY = y;
                if (z < minZ) minZ = z;
            }
        }

        if (first || SKY_BLOCK_POSITIONS.isEmpty()) {
            cachedVertexFloatCount = 0;
            return;
        }

        originX = minX;
        originY = minY;
        originZ = minZ;

        FloatArrayList out = new FloatArrayList(Math.min(SKY_BLOCK_POSITIONS.size() * 12, 65536));

        // 0: DOWN (-Y), 1: UP (+Y), 2: NORTH (-Z), 3: SOUTH (+Z), 4: WEST (-X), 5: EAST (+X)
        for (int dir = 0; dir < 6; dir++) {
            int dx = (dir == 4) ? -1 : (dir == 5) ? 1 : 0;
            int dy = (dir == 0) ? -1 : (dir == 1) ? 1 : 0;
            int dz = (dir == 2) ? -1 : (dir == 3) ? 1 : 0;

            REMAINING_FACES.clear();
            FACE_LIST.clear();

            LongIterator posIt = SKY_BLOCK_POSITIONS.iterator();
            while (posIt.hasNext()) {
                long packed = posIt.nextLong();
                int x = BlockPos.getX(packed);
                int y = BlockPos.getY(packed);
                int z = BlockPos.getZ(packed);
                long neighborPacked = BlockPos.asLong(x + dx, y + dy, z + dz);
                if (!SKY_BLOCK_POSITIONS.contains(neighborPacked)) {
                    REMAINING_FACES.add(packed);
                    FACE_LIST.add(packed);
                }
            }

            int count = FACE_LIST.size();
            for (int i = 0; i < count; i++) {
                long seed = FACE_LIST.getLong(i);
                if (!REMAINING_FACES.contains(seed)) {
                    continue;
                }

                int sx = BlockPos.getX(seed);
                int sy = BlockPos.getY(seed);
                int sz = BlockPos.getZ(seed);

                if (dir == 0 || dir == 1) {
                    // Plane Y = sy, expand along X then Z
                    int x0 = sx;
                    while (REMAINING_FACES.contains(BlockPos.asLong(x0 - 1, sy, sz))) x0--;
                    int x1 = sx + 1;
                    while (REMAINING_FACES.contains(BlockPos.asLong(x1, sy, sz))) x1++;

                    int z0 = sz;
                    while (canExpandRowXZ(x0, x1, sy, z0 - 1)) z0--;
                    int z1 = sz + 1;
                    while (canExpandRowXZ(x0, x1, sy, z1)) z1++;

                    for (int x = x0; x < x1; x++) {
                        for (int z = z0; z < z1; z++) {
                            REMAINING_FACES.remove(BlockPos.asLong(x, sy, z));
                        }
                    }

                    float fx0 = (float) (x0 - originX);
                    float fx1 = (float) (x1 - originX);
                    float fz0 = (float) (z0 - originZ);
                    float fz1 = (float) (z1 - originZ);

                    if (dir == 0) { // DOWN (Y = sy)
                        float fy = (float) (sy - originY);
                        emitQuad(out,
                                fx0, fy, fz1,
                                fx0, fy, fz0,
                                fx1, fy, fz0,
                                fx1, fy, fz1);
                    } else { // UP (Y = sy + 1)
                        float fy = (float) (sy + 1 - originY);
                        emitQuad(out,
                                fx0, fy, fz0,
                                fx0, fy, fz1,
                                fx1, fy, fz1,
                                fx1, fy, fz0);
                    }
                } else if (dir == 2 || dir == 3) {
                    // Plane Z = sz, expand along X then Y
                    int x0 = sx;
                    while (REMAINING_FACES.contains(BlockPos.asLong(x0 - 1, sy, sz))) x0--;
                    int x1 = sx + 1;
                    while (REMAINING_FACES.contains(BlockPos.asLong(x1, sy, sz))) x1++;

                    int y0 = sy;
                    while (canExpandRowXY(x0, x1, y0 - 1, sz)) y0--;
                    int y1 = sy + 1;
                    while (canExpandRowXY(x0, x1, y1, sz)) y1++;

                    for (int x = x0; x < x1; x++) {
                        for (int y = y0; y < y1; y++) {
                            REMAINING_FACES.remove(BlockPos.asLong(x, y, sz));
                        }
                    }

                    float fx0 = (float) (x0 - originX);
                    float fx1 = (float) (x1 - originX);
                    float fy0 = (float) (y0 - originY);
                    float fy1 = (float) (y1 - originY);

                    if (dir == 2) { // NORTH (Z = sz)
                        float fz = (float) (sz - originZ);
                        emitQuad(out,
                                fx1, fy1, fz,
                                fx1, fy0, fz,
                                fx0, fy0, fz,
                                fx0, fy1, fz);
                    } else { // SOUTH (Z = sz + 1)
                        float fz = (float) (sz + 1 - originZ);
                        emitQuad(out,
                                fx0, fy1, fz,
                                fx0, fy0, fz,
                                fx1, fy0, fz,
                                fx1, fy1, fz);
                    }
                } else {
                    // Plane X = sx, expand along Z then Y
                    int z0 = sz;
                    while (REMAINING_FACES.contains(BlockPos.asLong(sx, sy, z0 - 1))) z0--;
                    int z1 = sz + 1;
                    while (REMAINING_FACES.contains(BlockPos.asLong(sx, sy, z1))) z1++;

                    int y0 = sy;
                    while (canExpandRowZY(sx, z0, z1, y0 - 1)) y0--;
                    int y1 = sy + 1;
                    while (canExpandRowZY(sx, z0, z1, y1)) y1++;

                    for (int z = z0; z < z1; z++) {
                        for (int y = y0; y < y1; y++) {
                            REMAINING_FACES.remove(BlockPos.asLong(sx, y, z));
                        }
                    }

                    float fy0 = (float) (y0 - originY);
                    float fy1 = (float) (y1 - originY);
                    float fz0 = (float) (z0 - originZ);
                    float fz1 = (float) (z1 - originZ);

                    if (dir == 4) { // WEST (X = sx)
                        float fx = (float) (sx - originX);
                        emitQuad(out,
                                fx, fy1, fz0,
                                fx, fy0, fz0,
                                fx, fy0, fz1,
                                fx, fy1, fz1);
                    } else { // EAST (X = sx + 1)
                        float fx = (float) (sx + 1 - originX);
                        emitQuad(out,
                                fx, fy1, fz1,
                                fx, fy0, fz1,
                                fx, fy0, fz0,
                                fx, fy1, fz0);
                    }
                }
            }
        }

        cachedVertexFloatCount = out.size();
        cachedVertices = out.toFloatArray();
    }

    private static boolean canExpandRowXZ(int x0, int x1, int y, int z) {
        for (int x = x0; x < x1; x++) {
            if (!REMAINING_FACES.contains(BlockPos.asLong(x, y, z))) {
                return false;
            }
        }
        return true;
    }

    private static boolean canExpandRowXY(int x0, int x1, int y, int z) {
        for (int x = x0; x < x1; x++) {
            if (!REMAINING_FACES.contains(BlockPos.asLong(x, y, z))) {
                return false;
            }
        }
        return true;
    }

    private static boolean canExpandRowZY(int x, int z0, int z1, int y) {
        for (int z = z0; z < z1; z++) {
            if (!REMAINING_FACES.contains(BlockPos.asLong(x, y, z))) {
                return false;
            }
        }
        return true;
    }

    private static void emitQuad(
            FloatArrayList out,
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3
    ) {
        out.add(x0); out.add(y0); out.add(z0);
        out.add(x1); out.add(y1); out.add(z1);
        out.add(x2); out.add(y2); out.add(z2);
        out.add(x3); out.add(y3); out.add(z3);
    }
}
