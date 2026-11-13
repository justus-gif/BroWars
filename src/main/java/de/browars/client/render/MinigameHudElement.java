package de.browars.client.render;

import de.browars.client.minigame.MinigameHudClientHolder;
import de.browars.minigame.ui.MinigameHudState;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class MinigameHudElement implements HudElement {

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker) {
        if (!MinigameHudClientHolder.isActive()) return;

        MinigameHudState state = MinigameHudClientHolder.getState();
        if (state == null || !state.active()) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        float hudScale = Math.min(1f, extractor.guiWidth() / 420f);
        extractor.pose().pushMatrix();
        extractor.pose().scale(hudScale, hudScale);
        int screenWidth = (int) (extractor.guiWidth() / hudScale);
        int screenHeight = (int) (extractor.guiHeight() / hudScale);
        int centerX = screenWidth / 2;

        // Paint War evaluation happens in front of a solid black screen
        boolean paintWarBlackScreen = "paintwar".equals(state.gameId()) && state.timerRemainingTicks() == 0;
        if (paintWarBlackScreen) {
            extractor.fill(0, 0, screenWidth + 2, screenHeight + 2, 0xFF000000);
        } else {
            boolean numericScore = "targetrush".equals(state.gameId()) || state.maxScore() > 10;
            String p1Bottom = numericScore
                    ? ("§e" + state.p1Score())
                    : (state.p2Name().isEmpty() ? "§7Solo" : BroWarsUiHelper.formatDots(state.p1Score(), state.maxScore(), false));
            String p2Bottom = numericScore
                    ? ("§e" + state.p2Score())
                    : BroWarsUiHelper.formatDots(state.p2Score(), state.maxScore(), true);

            String centerTitle = "§6§l" + state.gameTitle().toUpperCase();
            String centerSub = buildCleanSubtitle(state);

            BroWarsUiHelper.drawTopScorebar(
                    extractor,
                    font,
                    screenWidth,
                    centerX,
                    centerTitle,
                    centerSub,
                    state.p1Name(),
                    state.p1ColorHex(),
                    p1Bottom,
                    state.p2Name(),
                    state.p2ColorHex(),
                    p2Bottom
            );

            // Status / Timer / Color / Tagger Badge below top scorebar
            int badgeTop = 39;
            float progress = (state.timerRemainingTicks() >= 0 && state.timerTotalTicks() > 0)
                    ? Math.clamp((float) state.timerRemainingTicks() / state.timerTotalTicks(), 0f, 1f)
                    : -1f;

            if (state.targetColorHex() != 0) {
                int rawHex = state.targetColorHex() & 0x00FFFFFF;
                int bgFill = 0xF2000000 | rawHex;
                int r = (rawHex >> 16) & 0xFF;
                int g = (rawHex >> 8) & 0xFF;
                int b = rawHex & 0xFF;
                double luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
                int textColor = (luminance > 0.55) ? BroWarsUiHelper.BORDER_DARK : BroWarsUiHelper.TEXT_PRIMARY;

                BroWarsUiHelper.drawBadge(
                        extractor, font, centerX, badgeTop, 108,
                        "§l" + state.targetColorName(),
                        bgFill, BroWarsUiHelper.BORDER_DARK, textColor,
                        progress, textColor
                );
            } else if (state.taggerIndex() > 0) {
                String localPlayerName = mc.player != null ? mc.player.getScoreboardName() : "";
                boolean isLocalPlayerTagger = false;
                boolean isLocalPlayerRunner = false;
                if (state.taggerIndex() == 1) {
                    if (localPlayerName.equalsIgnoreCase(state.p1Name())) isLocalPlayerTagger = true;
                    else if (localPlayerName.equalsIgnoreCase(state.p2Name())) isLocalPlayerRunner = true;
                } else if (state.taggerIndex() == 2) {
                    if (localPlayerName.equalsIgnoreCase(state.p2Name())) isLocalPlayerTagger = true;
                    else if (localPlayerName.equalsIgnoreCase(state.p1Name())) isLocalPlayerRunner = true;
                }

                if (isLocalPlayerTagger) {
                    long millis = System.currentTimeMillis();
                    float pulse = (float) (Math.sin(millis / 200.0) * 0.5 + 0.5);
                    float intensity = 0.35f + 0.25f * pulse;
                    int vignetteColor = net.minecraft.util.ARGB.colorFromFloat(1.0f, 1.0f - intensity, 1.0f, 1.0f);
                    extractor.blit(
                            net.minecraft.client.renderer.RenderPipelines.VIGNETTE,
                            net.minecraft.resources.Identifier.withDefaultNamespace("textures/misc/vignette.png"),
                            0, 0, 0.0f, 0.0f,
                            screenWidth, screenHeight,
                            screenWidth, screenHeight,
                            vignetteColor
                    );
                }

                String badgeText;
                int bgFill;
                int borderFill;
                if (isLocalPlayerTagger) {
                    badgeText = "§c§lTNT";
                    bgFill = 0xF2991B1B;
                    borderFill = BroWarsUiHelper.BORDER_DARK;
                } else if (isLocalPlayerRunner) {
                    badgeText = "§a§lLAUF";
                    bgFill = 0xF2166534;
                    borderFill = BroWarsUiHelper.BORDER_DARK;
                } else {
                    badgeText = "§e" + (state.taggerIndex() == 1 ? state.p1Name() : state.p2Name());
                    bgFill = BroWarsUiHelper.BG_CARD;
                    borderFill = BroWarsUiHelper.BORDER_DARK;
                }

                int barColor = progress < 0.25f ? BroWarsUiHelper.ACCENT_RED : (progress < 0.55f ? BroWarsUiHelper.ACCENT_ORANGE : BroWarsUiHelper.ACCENT_GOLD);
                BroWarsUiHelper.drawBadge(
                        extractor, font, centerX, badgeTop, 108,
                        badgeText, bgFill, borderFill, BroWarsUiHelper.TEXT_PRIMARY,
                        progress, barColor
                );
            } else if (progress >= 0f && !state.targetColorName().isEmpty()) {
                int barColor = progress < 0.20f ? BroWarsUiHelper.ACCENT_RED : (progress < 0.50f ? BroWarsUiHelper.ACCENT_ORANGE : BroWarsUiHelper.ACCENT_GOLD);
                BroWarsUiHelper.drawBadge(
                        extractor, font, centerX, badgeTop, 76,
                        state.targetColorName(), BroWarsUiHelper.BG_CARD, BroWarsUiHelper.BORDER_DARK, BroWarsUiHelper.TEXT_PRIMARY,
                        progress, barColor
                );
            }
        }

        // Center Banner / Countdown
        if (state.centerTitleTicks() > 0 && !state.centerTitle().isEmpty()) {
            BroWarsUiHelper.drawCenterBanner(
                    extractor,
                    font,
                    screenWidth,
                    centerX,
                    screenHeight / 2.0f,
                    state.centerTitle(),
                    state.centerSubtitle(),
                    state.centerTitleColorHex()
            );
        }

        extractor.pose().popMatrix();
    }

    private static String buildCleanSubtitle(MinigameHudState state) {
        String status = state.statusText() != null ? state.statusText().trim() : "";
        boolean hasCustomStatus = !status.isEmpty()
                && !status.startsWith("Erster zu")
                && !status.startsWith("Best of")
                && !status.startsWith("Startet");

        String phasePart = "";
        if (state.phaseNumber() > 0 && state.phaseTitle() != null && !state.phaseTitle().isEmpty()) {
            String dot = switch (state.phaseNumber()) {
                case 2 -> "§6● ";
                case 3 -> "§c● ";
                default -> "§a● ";
            };
            phasePart = dot + state.phaseTitle();
        }

        if (hasCustomStatus) {
            return "§7" + status;
        }
        if (!phasePart.isEmpty()) {
            return phasePart;
        }
        return "§7Runde " + state.currentRound();
    }
}
