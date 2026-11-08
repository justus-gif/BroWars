package de.browars.client.render;

import de.browars.BroWarsMod;
import de.browars.client.minigame.CrossWarClientHolder;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class CrossWarHudElement implements HudElement {

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (!mc.options.getCameraType().isFirstPerson()) return;
        if (!mc.player.getMainHandItem().is(BroWarsMod.CROSSWAR_CROSSBOW)) return;

        int screenWidth = extractor.guiWidth();
        int screenHeight = extractor.guiHeight();
        int cx = screenWidth / 2;
        int cy = screenHeight / 2;

        int ammo = CrossWarClientHolder.getAmmo();
        boolean reloading = CrossWarClientHolder.isReloading();
        int reloadTicks = CrossWarClientHolder.getReloadTicks();

        // 5-Slot Magazine Indicator directly under the crosshair
        int pipW = 7;
        int pipH = 4;
        int gap = 2;
        int totalW = 5 * pipW + 4 * gap;
        int startX = cx - (totalW / 2);
        int pipY = cy + 14;

        if (reloading) {
            // Incremental reload: pips < ammo are already filled,
            // pip == ammo is currently filling with amber glow based on reloadTicks (0 to 10 ticks = 0.5s),
            // and pips > ammo are empty waiting to be loaded.
            float slotProgress = Math.clamp((float) reloadTicks / 10.0f, 0.0f, 1.0f);

            for (int i = 0; i < 5; i++) {
                int px = startX + i * (pipW + gap);

                // Pip border & background
                extractor.fill(px - 1, pipY - 1, px + pipW + 1, pipY + pipH + 1, 0xAA000000);
                extractor.fill(px, pipY, px + pipW, pipY + pipH, 0x551E293B);

                if (i < ammo) {
                    // Already loaded arrow in magazine
                    extractor.fill(px, pipY, px + pipW, pipY + pipH, 0xFF38BDF8);
                    extractor.fill(px, pipY, px + pipW, pipY + 1, 0x88BAE6FD);
                } else if (i == ammo) {
                    // Currently loading arrow: smoothly fills from left to right with amber glow
                    int fillW = Math.max(1, (int) (slotProgress * pipW));
                    extractor.fill(px, pipY, px + fillW, pipY + pipH, 0xFFF59E0B);
                    extractor.fill(px, pipY, px + fillW, pipY + 1, 0x88FDE68A);
                } else {
                    // Empty slot
                    extractor.fill(px, pipY, px + pipW, pipY + pipH, 0x44334155);
                }
            }
        } else {
            // Standard ammo state
            for (int i = 0; i < 5; i++) {
                int px = startX + i * (pipW + gap);
                boolean filled = i < ammo;

                // Pip border
                extractor.fill(px - 1, pipY - 1, px + pipW + 1, pipY + pipH + 1, 0xAA000000);

                if (filled) {
                    extractor.fill(px, pipY, px + pipW, pipY + pipH, 0xFF38BDF8);
                    extractor.fill(px, pipY, px + pipW, pipY + 1, 0x88BAE6FD);
                } else {
                    extractor.fill(px, pipY, px + pipW, pipY + pipH, 0x44334155);
                }
            }
        }
    }
}
