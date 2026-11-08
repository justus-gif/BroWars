package de.browars.area;

import de.browars.util.HologramHelper;
import de.browars.util.TeleportHelper;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

public class TeleportAreaManager {
    private static int tickCounter = 0;

    public static void onServerTick(MinecraftServer server) {
        tickCounter++;
        boolean shouldTickVisuals = (tickCounter % 15 == 0); // Visual particle tick every 0.75s

        for (ServerLevel level : server.getAllLevels()) {
            ResourceKey<Level> dim = level.dimension();
            TeleportAreaSavedData savedData = TeleportAreaSavedData.get(level);
            List<TeleportArea> areas = savedData.getAreasInDimension(dim);
            if (areas.isEmpty()) continue;

            // 1. Collision detection for all players in this level
            for (ServerPlayer player : List.copyOf(level.players())) {
                if (player.isSpectator() || player.level() != level || !TeleportHelper.canTeleport(player)) continue;

                for (TeleportArea area : areas) {
                    if (area.intersects(player.getBoundingBox())) {
                        boolean teleported = TeleportHelper.teleportPlayer(
                                player,
                                area.getTargetX(),
                                area.getTargetY(),
                                area.getTargetZ(),
                                area.getTargetYaw(),
                                area.getTargetPitch(),
                                area.getTargetDimension(),
                                area.getColorHex()
                        );
                        if (teleported) break; // A broken destination must not mask an overlapping valid zone.
                    }
                }
            }

            // 2. Holograms & survival visibility
            for (TeleportArea area : areas) {
                if (shouldTickVisuals && area.isShowNameHologram() && !area.getName().isEmpty()) {
                    double centerX = (area.getMinPos().getX() + area.getMaxPos().getX() + 1.0) / 2.0;
                    double topY = area.getMaxPos().getY() + 1.25;
                    double centerZ = (area.getMinPos().getZ() + area.getMaxPos().getZ() + 1.0) / 2.0;

                    if (!level.hasChunkAt(net.minecraft.core.BlockPos.containing(centerX, topY, centerZ))) continue;
                    UUID newUuid = HologramHelper.updateOrCreateHologram(
                            level,
                            area.getTextDisplayUuid(),
                            centerX, topY, centerZ,
                            area.getName(),
                            area.getColorHex()
                    );
                    if (newUuid != null && !newUuid.equals(area.getTextDisplayUuid())) {
                        area.setTextDisplayUuid(newUuid);
                        savedData.setDirty();
                    }
                }

                if (shouldTickVisuals && (!area.isShowNameHologram() || area.getName().isEmpty()) && area.getTextDisplayUuid() != null) {
                    HologramHelper.removeHologram(level, area.getTextDisplayUuid());
                    area.setTextDisplayUuid(null);
                    savedData.setDirty();
                }
                if (shouldTickVisuals && area.isVisibleInSurvival()) {
                    spawnAreaPerimeterParticles(level, area);
                }
            }
        }
    }

    private static void spawnAreaPerimeterParticles(ServerLevel level, TeleportArea area) {
        DustParticleOptions dust = TeleportHelper.createDustParticle(area.getColorHex(), 1.1f);
        double minX = area.getMinPos().getX();
        double maxX = area.getMaxPos().getX() + 1.0;
        double minY = area.getMinPos().getY();
        double maxY = area.getMaxPos().getY() + 1.0;
        double minZ = area.getMinPos().getZ();
        double maxZ = area.getMaxPos().getZ() + 1.0;

        double horizontalStep = Math.max(1, Math.max(maxX - minX, maxZ - minZ) / 48.0);
        double verticalStep = Math.max(0.8, (maxY - minY) / 32.0);

        // Bottom perimeter
        for (double x = minX; x <= maxX; x += horizontalStep) {
            level.sendParticles(dust, x, minY + 0.05, minZ, 1, 0, 0, 0, 0);
            level.sendParticles(dust, x, minY + 0.05, maxZ, 1, 0, 0, 0, 0);
        }
        for (double z = minZ; z <= maxZ; z += horizontalStep) {
            level.sendParticles(dust, minX, minY + 0.05, z, 1, 0, 0, 0, 0);
            level.sendParticles(dust, maxX, minY + 0.05, z, 1, 0, 0, 0, 0);
        }

        // Top perimeter
        for (double x = minX; x <= maxX; x += horizontalStep) {
            level.sendParticles(dust, x, maxY, minZ, 1, 0, 0, 0, 0);
            level.sendParticles(dust, x, maxY, maxZ, 1, 0, 0, 0, 0);
        }
        for (double z = minZ; z <= maxZ; z += horizontalStep) {
            level.sendParticles(dust, minX, maxY, z, 1, 0, 0, 0, 0);
            level.sendParticles(dust, maxX, maxY, z, 1, 0, 0, 0, 0);
        }

        // Corner pillars
        double[][] corners = {{minX, minZ}, {maxX, minZ}, {minX, maxZ}, {maxX, maxZ}};
        for (double[] c : corners) {
            for (double y = minY; y <= maxY; y += verticalStep) {
                level.sendParticles(dust, c[0], y, c[1], 1, 0, 0, 0, 0);
            }
        }
    }
}
