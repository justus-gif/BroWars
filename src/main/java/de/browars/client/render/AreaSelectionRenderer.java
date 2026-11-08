package de.browars.client.render;

import de.browars.BroWarsMod;
import de.browars.area.TeleportArea;
import de.browars.client.ClientArenaSelection;
import de.browars.client.gui.AreaManagerScreen;
import de.browars.item.ArenaWandItem;
import de.browars.item.TeleportWandItem;

import de.browars.util.TeleportHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.level.Level;

public class AreaSelectionRenderer {
    private static int clientTickCount = 0;

    public static void onClientTick(Minecraft client) {
        LocalPlayer player = client.player;
        Level level = client.level;
        if (player == null || level == null) return;

        clientTickCount++;
        if (clientTickCount % 6 != 0) return;

        boolean holdingTeleportWand = player.getMainHandItem().is(BroWarsMod.TELEPORT_WAND) ||
                player.getOffhandItem().is(BroWarsMod.TELEPORT_WAND);

        boolean holdingArenaWand = player.getMainHandItem().is(BroWarsMod.ARENA_WAND) ||
                player.getOffhandItem().is(BroWarsMod.ARENA_WAND);

        if (!holdingTeleportWand && !holdingArenaWand) return;

        if (holdingTeleportWand) {
            // 1. Render active Teleport Wand selection
            BlockPos p1 = TeleportWandItem.POS1_MAP.get(player.getUUID());
            BlockPos p2 = TeleportWandItem.POS2_MAP.get(player.getUUID());

            if (p1 != null && p2 != null) {
                int minX = Math.min(p1.getX(), p2.getX());
                int minY = Math.min(p1.getY(), p2.getY());
                int minZ = Math.min(p1.getZ(), p2.getZ());
                int maxX = Math.max(p1.getX(), p2.getX()) + 1;
                int maxY = Math.max(p1.getY(), p2.getY()) + 1;
                int maxZ = Math.max(p1.getZ(), p2.getZ()) + 1;

                DustParticleOptions yellowDust = TeleportHelper.createDustParticle(0xFFFF00, 1.0f);
                drawBoxOutline(level, minX, minY, minZ, maxX, maxY, maxZ, yellowDust);
            }

            // 2. Render known Teleport Areas in current dimension
            for (TeleportArea area : AreaManagerScreen.KNOWN_AREAS) {
                if (area.getDimension().equals(level.dimension())) {
                    DustParticleOptions areaDust = TeleportHelper.createDustParticle(area.getColorHex(), 0.8f);
                    drawBoxOutline(level,
                            area.getMinPos().getX(), area.getMinPos().getY(), area.getMinPos().getZ(),
                            area.getMaxPos().getX() + 1, area.getMaxPos().getY() + 1, area.getMaxPos().getZ() + 1,
                            areaDust
                    );
                }
            }
        }

        if (holdingArenaWand) {
            // Render active temporary Arena Wand selection (Magenta/Purple)
            BlockPos ap1 = ClientArenaSelection.getPos1();
            if (ap1 == null) ap1 = ArenaWandItem.POS1_MAP.get(player.getUUID());

            BlockPos ap2 = ClientArenaSelection.getPos2();
            if (ap2 == null) ap2 = ArenaWandItem.POS2_MAP.get(player.getUUID());

            if (ap1 != null && ap2 != null) {
                int minX = Math.min(ap1.getX(), ap2.getX());
                int minY = Math.min(ap1.getY(), ap2.getY());
                int minZ = Math.min(ap1.getZ(), ap2.getZ());
                int maxX = Math.max(ap1.getX(), ap2.getX()) + 1;
                int maxY = Math.max(ap1.getY(), ap2.getY()) + 1;
                int maxZ = Math.max(ap1.getZ(), ap2.getZ()) + 1;

                DustParticleOptions magentaDust = TeleportHelper.createDustParticle(0xD946EF, 1.0f);
                drawBoxOutline(level, minX, minY, minZ, maxX, maxY, maxZ, magentaDust);
            }
        }
    }

    private static void drawBoxOutline(Level level, double minX, double minY, double minZ,
                                       double maxX, double maxY, double maxZ, DustParticleOptions dust) {
        double step = 1.0;
        for (double x = minX; x <= maxX; x += step) {
            level.addParticle(dust, x, minY, minZ, 0, 0, 0);
            level.addParticle(dust, x, maxY, minZ, 0, 0, 0);
            level.addParticle(dust, x, minY, maxZ, 0, 0, 0);
            level.addParticle(dust, x, maxY, maxZ, 0, 0, 0);
        }
        for (double y = minY; y <= maxY; y += step) {
            level.addParticle(dust, minX, y, minZ, 0, 0, 0);
            level.addParticle(dust, maxX, y, minZ, 0, 0, 0);
            level.addParticle(dust, minX, y, maxZ, 0, 0, 0);
            level.addParticle(dust, maxX, y, maxZ, 0, 0, 0);
        }
        for (double z = minZ; z <= maxZ; z += step) {
            level.addParticle(dust, minX, minY, z, 0, 0, 0);
            level.addParticle(dust, maxX, minY, z, 0, 0, 0);
            level.addParticle(dust, minX, maxY, z, 0, 0, 0);
            level.addParticle(dust, maxX, maxY, z, 0, 0, 0);
        }
    }
}
