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
        // Keep the same layout readable when the user chooses a large GUI scale.
        float hudScale = Math.min(1f, extractor.guiWidth() / 420f);
        extractor.pose().pushMatrix();
        extractor.pose().scale(hudScale, hudScale);
        int screenWidth = (int) (extractor.guiWidth() / hudScale);
        int screenHeight = (int) (extractor.guiHeight() / hudScale);
        int centerX = screenWidth / 2;

        // Paint War evaluation happens in front of a solid black screen (only show center evaluation banner)
        boolean paintWarBlackScreen = "paintwar".equals(state.gameId()) && state.timerRemainingTicks() == 0;
        if (paintWarBlackScreen) {
            extractor.fill(0, 0, screenWidth + 2, screenHeight + 2, 0xFF000000);
        } else {
            int top = 8;
            int cardH = 28;

            String sub = state.statusText().isEmpty() ? "Best of " + (state.maxScore() * 2 - 1) : state.statusText();
            String phaseDisplay = "";
            if (state.phaseNumber() > 0 && state.phaseTitle() != null && !state.phaseTitle().isEmpty()) {
                String dotColor = switch (state.phaseNumber()) {
                    case 2 -> "§6● ";
                    case 3 -> "§c● ";
                    default -> "§a● ";
                };
                phaseDisplay = dotColor + state.phaseTitle() + " §8| ";
            }
            String fullSub = phaseDisplay + "§7" + sub;
            int subW = font.width(fullSub);
            int centerW = Math.max(Math.max(120, subW + 16), font.width("§l" + state.gameTitle() + " §r• " + state.currentRound()) + 16);
            int cLeft = centerX - (centerW / 2);
            int cRight = cLeft + centerW;
            int cBottom = top + cardH;

            int gap = 4;

            // 1. PLAYER 1 CARD (Left)
            int p1CardW = Math.max(70, Math.min(120, (screenWidth - centerW - 20) / 2));
            int p1Right = cLeft - gap;
            int p1Left = p1Right - p1CardW;
            int p1Bottom = top + cardH;

            int p1AccentColor = 0xFF000000 | (state.p1ColorHex() & 0x00FFFFFF);
            if ((p1AccentColor & 0x00FFFFFF) == 0) p1AccentColor = 0xFFDC2626;

            // P1 Background & Borders (Sleek dark glass plate)
            extractor.fill(p1Left, top, p1Right, p1Bottom, 0xCC0F172A);
            extractor.fill(p1Left, top, p1Left + 4, p1Bottom, p1AccentColor); // Left team accent
            extractor.fill(p1Left, top, p1Right, top + 1, 0x33FFFFFF);
            extractor.fill(p1Left, p1Bottom - 1, p1Right, p1Bottom, 0x44000000);

            String p1Raw = state.p1Name().isEmpty() ? "Spieler 1" : state.p1Name();
            String p1Name = font.plainSubstrByWidth(p1Raw, p1CardW - 14);
            extractor.text(font, p1Name, p1Left + 8, top + 4, 0xFFFFFFFF, true);

            // P1 Score Dots or Points
            if ("targetrush".equals(state.gameId()) || state.maxScore() > 10) {
                String p1ScoreText = state.p2Name().isEmpty() ? "§e" + state.p1Score() + " Pkt. §7(Solo)" : "§e" + state.p1Score() + " Pkt.";
                extractor.text(font, p1ScoreText, p1Left + 8, top + 15, 0xFFFFAA00, true);
            } else {
                StringBuilder p1Dots = new StringBuilder();
                for (int i = 0; i < Math.min(7, state.maxScore()); i++) {
                    p1Dots.append(state.p1Score() > i ? "§6● " : "§8○ ");
                }
                extractor.text(font, state.p2Name().isEmpty() ? "§7Übungsmodus" : p1Dots.toString().trim(), p1Left + 8, top + 15, 0xFFFFAA00, true);
            }

            // 2. CENTER ROUND CARD
            extractor.fill(cLeft, top, cRight, cBottom, 0xDD0B0F19);
            extractor.fill(cLeft, top, cRight, top + 1, 0x88FFAA00);
            extractor.fill(cLeft, cBottom - 1, cRight, cBottom, 0x88FFAA00);

            String centerTitleText;
            if ("targetrush".equals(state.gameId())) {
                centerTitleText = state.p2Name().isEmpty() ? "§6§lTarget Rush §7• §eSolo" : "§6§lTarget Rush §7• §eDuell";
            } else {
                centerTitleText = "§6§l" + state.gameTitle() + " §7• " + state.currentRound();
            }
            extractor.centeredText(font, centerTitleText, centerX, top + 4, 0xFFFFD700);
            extractor.centeredText(font, fullSub, centerX, top + 15, 0xFFCBD5E1);

            // 3. PLAYER 2 CARD (Right) - only if 2 players
            if (!state.p2Name().isEmpty()) {
                int p2CardW = p1CardW;
                int p2Left = cRight + gap;
                int p2Right = p2Left + p2CardW;
                int p2Bottom = top + cardH;

                int p2AccentColor = 0xFF000000 | (state.p2ColorHex() & 0x00FFFFFF);
                if ((p2AccentColor & 0x00FFFFFF) == 0) p2AccentColor = 0xFF2563EB;

                // P2 Background & Borders
                extractor.fill(p2Left, top, p2Right, p2Bottom, 0xCC0F172A);
                extractor.fill(p2Right - 4, top, p2Right, p2Bottom, p2AccentColor); // Right team accent
                extractor.fill(p2Left, top, p2Right, top + 1, 0x33FFFFFF);
                extractor.fill(p2Left, p2Bottom - 1, p2Right, p2Bottom, 0x44000000);

                String p2Name = font.plainSubstrByWidth(state.p2Name(), p2CardW - 14);
                int p2NameW = font.width(p2Name);
                extractor.text(font, p2Name, p2Right - 8 - p2NameW, top + 4, 0xFFFFFFFF, true);

                // P2 Score Dots or Points
                if ("targetrush".equals(state.gameId()) || state.maxScore() > 10) {
                    String p2ScoreText = "§e" + state.p2Score() + " Pkt.";
                    int p2ScoreW = font.width(p2ScoreText);
                    extractor.text(font, p2ScoreText, p2Right - 8 - p2ScoreW, top + 15, 0xFFFFAA00, true);
                } else {
                    StringBuilder p2Dots = new StringBuilder();
                    for (int i = Math.min(7, state.maxScore()) - 1; i >= 0; i--) {
                        p2Dots.append(state.p2Score() > i ? "§6● " : "§8○ ");
                    }
                    String p2DotsStr = p2Dots.toString().trim();
                    int p2DotsW = font.width(p2DotsStr);
                    extractor.text(font, p2DotsStr, p2Right - 8 - p2DotsW, top + 15, 0xFFFFAA00, true);
                }
            }

            // 4. TARGET COLOR BADGE & COUNTDOWN BAR
            if (state.targetColorHex() != 0) {
                int badgeW = 116;
                int badgeH = 24;
                int bTop = 38;
                int bLeft = centerX - (badgeW / 2);
                int bRight = bLeft + badgeW;
                int bBottom = bTop + badgeH;

                int rawHex = state.targetColorHex() & 0x00FFFFFF;
                int bgFill = 0xEE000000 | rawHex;

                // Determine optimal text contrast based on color luminance
                int r = (rawHex >> 16) & 0xFF;
                int g = (rawHex >> 8) & 0xFF;
                int b = rawHex & 0xFF;
                double luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
                int textColor = (luminance > 0.55) ? 0xFF0F172A : 0xFFFFFFFF;

                // Outer dark frame
                extractor.fill(bLeft - 1, bTop - 1, bRight + 1, bBottom + 1, 0xDD000000);
                // Color background
                extractor.fill(bLeft, bTop, bRight, bBottom, bgFill);

                // Target color text
                extractor.centeredText(font, "§l" + state.targetColorName(), centerX, bTop + 5, textColor);

                // Progress Bar (Remaining Time)
                if (state.timerRemainingTicks() >= 0 && state.timerTotalTicks() > 0) {
                    float progress = Math.max(0f, Math.min(1f, (float) state.timerRemainingTicks() / state.timerTotalTicks()));
                    int barMaxW = badgeW - 6;
                    int curBarW = (int) (barMaxW * progress);
                    extractor.fill(bLeft + 3, bBottom - 4, bRight - 3, bBottom - 2, 0x55000000);
                    extractor.fill(bLeft + 3, bBottom - 4, bLeft + 3 + curBarW, bBottom - 2, (luminance > 0.55 ? 0xFF0F172A : 0xFFFFFFFF));
                }
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
                    float intensity = 0.35f + 0.25f * pulse; // Gentle, not too overpowering
                    // VIGNETTE pipeline uses ONE_MINUS_SRC_COLOR:
                    // Red border means Red is kept and Green/Blue are multiplied away by higher values
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

                int badgeW = 164;
                int badgeH = 24;
                int bTop = 38;
                int bLeft = centerX - (badgeW / 2);
                int bRight = bLeft + badgeW;
                int bBottom = bTop + badgeH;

                if (isLocalPlayerTagger) {
                    extractor.fill(bLeft - 1, bTop - 1, bRight + 1, bBottom + 1, 0xEE7F1D1D);
                    extractor.fill(bLeft, bTop, bRight, bBottom, 0xDD991B1B);
                    extractor.centeredText(font, "§c§lDU HAST DAS TNT!", centerX, bTop + 4, 0xFFFFFFFF);
                } else if (isLocalPlayerRunner) {
                    extractor.fill(bLeft - 1, bTop - 1, bRight + 1, bBottom + 1, 0xEE14532D);
                    extractor.fill(bLeft, bTop, bRight, bBottom, 0xDD166534);
                    extractor.centeredText(font, "§a§lLAUF WEG!", centerX, bTop + 4, 0xFFFFFFFF);
                } else {
                    String tName = state.taggerIndex() == 1 ? state.p1Name() : state.p2Name();
                    extractor.fill(bLeft - 1, bTop - 1, bRight + 1, bBottom + 1, 0xDD000000);
                    extractor.fill(bLeft, bTop, bRight, bBottom, 0xCC1E293B);
                    extractor.centeredText(font, "§eTagger: §f" + tName, centerX, bTop + 4, 0xFFFFFFFF);
                }

                if (state.timerRemainingTicks() >= 0 && state.timerTotalTicks() > 0) {
                    float progress = Math.max(0f, Math.min(1f, (float) state.timerRemainingTicks() / state.timerTotalTicks()));
                    int barMaxW = badgeW - 6;
                    int curBarW = (int) (barMaxW * progress);
                    extractor.fill(bLeft + 3, bBottom - 4, bRight - 3, bBottom - 2, 0x55000000);
                    int barColor = progress < 0.25f ? 0xFFEF4444 : (progress < 0.5f ? 0xFFF59E0B : 0xFF22C55E);
                    extractor.fill(bLeft + 3, bBottom - 4, bLeft + 3 + curBarW, bBottom - 2, barColor);
                }
            } else if (state.timerRemainingTicks() >= 0 && state.timerTotalTicks() > 0 && !state.targetColorName().isEmpty()) {
                int badgeW = Math.max(76, font.width(state.targetColorName()) + 24);
                int badgeH = 24;
                int bTop = 38;
                int bLeft = centerX - (badgeW / 2);
                int bRight = bLeft + badgeW;
                int bBottom = bTop + badgeH;

                extractor.fill(bLeft - 1, bTop - 1, bRight + 1, bBottom + 1, 0xDD000000);
                extractor.fill(bLeft, bTop, bRight, bBottom, 0xDD0F172A);
                extractor.centeredText(font, state.targetColorName(), centerX, bTop + 4, 0xFFFFFFFF);

                float progress = Math.max(0f, Math.min(1f, (float) state.timerRemainingTicks() / state.timerTotalTicks()));
                int barMaxW = badgeW - 6;
                int curBarW = (int) (barMaxW * progress);
                extractor.fill(bLeft + 3, bBottom - 4, bRight - 3, bBottom - 2, 0x55000000);
                int barColor = progress < 0.20f ? 0xFFEF4444 : (progress < 0.5f ? 0xFFF59E0B : 0xFF38BDF8);
                extractor.fill(bLeft + 3, bBottom - 4, bLeft + 3 + curBarW, bBottom - 2, barColor);
            }
        }

        // 5. CENTER LARGE TITLE & ANIMATIONS
        if (state.centerTitleTicks() > 0 && !state.centerTitle().isEmpty()) {
            String title = state.centerTitle();
            String stripped = title.replaceAll("§[0-9a-fk-or]", "").trim();
            boolean isNumber = stripped.length() <= 2 && !stripped.isEmpty() && Character.isDigit(stripped.charAt(0));

            float centerY = screenHeight / 2.0f;
            int titleColor = 0xFF000000 | (state.centerTitleColorHex() & 0x00FFFFFF);
            if ((titleColor & 0x00FFFFFF) == 0) {
                titleColor = 0xFFFFFFFF;
            }

            if (isNumber) {
                // Subtle translucent glass circle plate behind countdown (NOT solid black)
                int plateW = 70;
                int plateH = 50;
                int px1 = centerX - plateW / 2;
                int py1 = (int) (centerY - plateH / 2f);
                int px2 = px1 + plateW;
                int py2 = py1 + plateH;

                extractor.fill(px1, py1, px2, py2, 0x55000000); // 33% translucent glass
                extractor.fill(px1, py1, px2, py1 + 2, titleColor); // vibrant top accent line

                float scale = 3.5f;
                extractor.pose().pushMatrix();
                extractor.pose().translate(centerX, centerY);
                extractor.pose().scale(scale, scale);
                int strW = font.width(title);
                extractor.text(font, title, -strW / 2, -4, titleColor, true);
                extractor.pose().popMatrix();

                if (!state.centerSubtitle().isEmpty()) {
                    extractor.centeredText(font, state.centerSubtitle(), centerX, py2 + 8, 0xFFFFFFFF);
                }
            } else {
                // Wide sleek banner for announcements & game start
                String sub = state.centerSubtitle();
                boolean hasSub = !sub.isEmpty();
                int titleW = (int) (font.width(title) * 1.5f);
                int subW = hasSub ? font.width(sub) : 0;
                int contentW = Math.max(titleW, subW);
                int bannerW = Math.min(screenWidth - 16, Math.max(260, contentW + 40));
                int bannerH = hasSub ? 56 : 40;
                int bx1 = centerX - bannerW / 2;
                int bx2 = centerX + bannerW / 2;
                int by1 = (int) (centerY - bannerH / 2f);
                int by2 = by1 + bannerH;

                extractor.fill(bx1, by1, bx2, by2, 0x880B0F19);
                extractor.fill(bx1, by1, bx2, by1 + 2, titleColor);
                extractor.fill(bx1, by2 - 2, bx2, by2, titleColor);

                float scale = Math.min(1.5f, (screenWidth - 48f) / Math.max(1, font.width(title)));
                extractor.pose().pushMatrix();
                if (hasSub) {
                    extractor.pose().translate(centerX, centerY - 9);
                } else {
                    extractor.pose().translate(centerX, centerY);
                }
                extractor.pose().scale(scale, scale);
                int strW = font.width(title);
                extractor.text(font, title, -strW / 2, -4, titleColor, true);
                extractor.pose().popMatrix();

                if (hasSub) {
                    extractor.centeredText(font, sub, centerX, (int) centerY + 12, 0xFFE2E8F0);
                }
            }
        }
        extractor.pose().popMatrix();
    }
}
