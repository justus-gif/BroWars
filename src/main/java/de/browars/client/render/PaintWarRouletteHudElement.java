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
        int cardW = 98;
        int cardH = 48;
        int right = screenW - 8;
        int left = right - cardW;
        int top = 8;
        int bottom = top + cardH;
        int centerX = left + cardW / 2;

        boolean finishedSpin = remSpin <= 0;

        float t = finishedSpin ? 1.0f : Math.clamp(1.0f - ((remSpin - partial) / (float) totalSpin), 0.0f, 1.0f);
        float easeOut = 1.0f - (float) Math.pow(1.0f - t, 3.0);
        int totalSteps = 14;
        float startSlot = targetIndex - totalSteps;
        float currentSlotFloat = startSlot + easeOut * totalSteps;

        BroWarsUiHelper.drawPanel(extractor, left, top, right, bottom, BroWarsUiHelper.ACCENT_GOLD);

        int slotSize = 24;
        int winLeft = centerX - slotSize / 2;
        int winTop = top + 6;
        int winRight = winLeft + slotSize;
        int winBottom = winTop + slotSize;

        extractor.fill(winLeft - 1, winTop - 1, winRight + 1, winBottom + 1, finishedSpin ? BroWarsUiHelper.ACCENT_GOLD : BroWarsUiHelper.BORDER_SUBTLE);
        extractor.fill(winLeft, winTop, winRight, winBottom, BroWarsUiHelper.BG_REEL);

        int baseIndex = (int) Math.floor(currentSlotFloat);
        float frac = currentSlotFloat - baseIndex;

        extractor.enableScissor(winLeft, winTop, winRight, winBottom);
        for (int offset = -1; offset <= 2; offset++) {
            int idx = Math.floorMod(baseIndex + offset, count);
            ItemStack icon = getIconForReward(rewards[idx]);
            float itemY = winTop + 4.0f + (offset - frac) * 22.0f;
            extractor.pose().pushMatrix();
            extractor.pose().translate(0.0f, itemY);
            extractor.fakeItem(icon, winLeft + 4, 0);
            extractor.pose().popMatrix();
        }
        extractor.disableScissor();

        int activeIdx = finishedSpin ? targetIndex : Math.floorMod(Math.round(currentSlotFloat), count);
        String itemLabel = getShortLabel(rewards[activeIdx]);
        extractor.centeredText(font, itemLabel, centerX, bottom - 12, 0xFFFFFFFF);
    }
}
