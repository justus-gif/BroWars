package de.browars.client.render;

import de.browars.BroWarsMod;
import de.browars.client.minigame.PaintWarRouletteClientHolder;
import de.browars.minigame.paintwar.PaintWarGame;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class PaintWarRouletteHudElement implements HudElement {

    private static ItemStack getIconForReward(PaintWarGame.BoxReward reward) {
        return switch (reward) {
            case BOMB -> new ItemStack(BroWarsMod.PAINT_BOMB);
            case CLUSTER_BOMB -> new ItemStack(BroWarsMod.CLUSTER_BOMB);
            case CROSSBOW -> new ItemStack(Items.CROSSBOW);
            case ROLLER -> new ItemStack(BroWarsMod.PAINT_ROLLER);
            case BLOOPER -> new ItemStack(BroWarsMod.PAINT_BLOOPER);
            case UPGRADE_COOLDOWN -> {
                ItemStack s = new ItemStack(Items.SUGAR);
                s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                yield s;
            }
            case UPGRADE_RADIUS -> {
                ItemStack s = new ItemStack(Items.SLIME_BALL);
                s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                yield s;
            }
        };
    }

    private static String getShortLabel(PaintWarGame.BoxReward reward) {
        return switch (reward) {
            case BOMB -> "§a§lFarb-Bombe";
            case CLUSTER_BOMB -> "§d§lCluster-Bombe";
            case CROSSBOW -> "§b§lFarb-Armbrust";
            case ROLLER -> "§e§lFarb-Walze";
            case BLOOPER -> "§8§lFarb-Blooper";
            case UPGRADE_COOLDOWN -> "§b§lWurf-Tempo+";
            case UPGRADE_RADIUS -> "§d§lKlecks-Radius+";
        };
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker) {
        if (!PaintWarRouletteClientHolder.isActive()) return;

        PaintWarGame.BoxReward[] rewards = PaintWarGame.BoxReward.values();
        int count = rewards.length;
        int targetIndex = Math.clamp(PaintWarRouletteClientHolder.getRewardOrdinal(), 0, count - 1);

        int remSpin = PaintWarRouletteClientHolder.getRemainingSpinTicks();
        int totalSpin = Math.max(1, PaintWarRouletteClientHolder.getTotalSpinTicks());
        float partial = deltaTracker.getGameTimeDeltaPartialTick(false);

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        int screenW = extractor.guiWidth();
        int cardW = 104;
        int cardH = 58;
        int right = screenW - 10;
        int left = right - cardW;
        int top = 10;
        int bottom = top + cardH;
        int centerX = left + cardW / 2;

        boolean finishedSpin = remSpin <= 0;

        // Compute smooth decelerating reel position
        float t = finishedSpin ? 1.0f : Math.clamp(1.0f - ((remSpin - partial) / (float) totalSpin), 0.0f, 1.0f);
        // Cubic ease-out from (targetIndex - 14) to targetIndex
        float easeOut = 1.0f - (float) Math.pow(1.0f - t, 3.0);
        int totalSteps = 14;
        float startSlot = targetIndex - totalSteps;
        float currentSlotFloat = startSlot + easeOut * totalSteps;

        // Card outer frame & dark glass plate
        int borderColor = finishedSpin ? 0xFF22C55E : 0xFFF59E0B;
        extractor.fill(left - 1, top - 1, right + 1, bottom + 1, 0xCC000000);
        extractor.fill(left, top, right, bottom, 0xEE0F172A);
        // Top & bottom accent bars
        extractor.fill(left, top, right, top + 2, borderColor);
        extractor.fill(left, bottom - 2, right, bottom, borderColor);
        extractor.fill(left, top, left + 2, bottom, borderColor);
        extractor.fill(right - 2, top, right, bottom, borderColor);

        // Header title
        String header = finishedSpin ? "§a§lITEM ERHALTEN" : "§6§lGLÜCKSBOX";
        extractor.centeredText(font, header, centerX, top + 5, 0xFFFFFFFF);

        // Reel window in center
        int slotSize = 24;
        int winLeft = centerX - slotSize / 2;
        int winTop = top + 16;
        int winRight = winLeft + slotSize;
        int winBottom = winTop + slotSize;

        extractor.fill(winLeft - 1, winTop - 1, winRight + 1, winBottom + 1, finishedSpin ? 0xFFFFD700 : 0xFF475569);
        extractor.fill(winLeft, winTop, winRight, winBottom, 0xFF1E293B);

        int baseIndex = (int) Math.floor(currentSlotFloat);
        float frac = currentSlotFloat - baseIndex;

        extractor.enableScissor(winLeft, winTop, winRight, winBottom);
        for (int offset = -1; offset <= 2; offset++) {
            int idx = Math.floorMod(baseIndex + offset, count);
            ItemStack icon = getIconForReward(rewards[idx]);
            int itemY = Math.round(winTop + 4 + (offset - frac) * 22.0f);
            extractor.fakeItem(icon, winLeft + 4, itemY);
        }
        extractor.disableScissor();

        // Display name of currently centered item
        int activeIdx = finishedSpin ? targetIndex : Math.floorMod(Math.round(currentSlotFloat), count);
        String itemLabel = getShortLabel(rewards[activeIdx]);
        extractor.centeredText(font, itemLabel, centerX, bottom - 12, 0xFFFFFFFF);
    }
}
