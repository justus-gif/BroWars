package de.browars.client.render;

import de.browars.client.gameshow.GameshowClientHolder;
import de.browars.gameshow.GameshowData;
import de.browars.gameshow.GameshowGameType;
import de.browars.minigame.TeamColor;
import de.browars.network.SyncGameshowHudPayload;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

public class GameshowHudElement implements HudElement {

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker) {
        if (!GameshowClientHolder.isActive()) return;

        SyncGameshowHudPayload state = GameshowClientHolder.getHudState();
        if (state == null || !state.active()) return;

        String phase = state.phase();
        if ("IN_GAME".equals(phase) || "COLOR_SELECT".equals(phase) || "INACTIVE".equals(phase)) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
        float elapsedTicks = GameshowClientHolder.getSmoothElapsedTicks(partial);
        float remainingTicks = GameshowClientHolder.getSmoothRemainingTicks(partial);

        float hudScale = Math.min(1f, extractor.guiWidth() / 420f);
        extractor.pose().pushMatrix();
        extractor.pose().scale(hudScale, hudScale);
        int screenW = (int) (extractor.guiWidth() / hudScale);
        int screenH = (int) (extractor.guiHeight() / hudScale);
        int centerX = screenW / 2;
        int centerY = screenH / 2;

        TeamColor p1Team = TeamColor.fromString(state.p1ColorId(), TeamColor.RED);
        TeamColor p2Team = TeamColor.fromString(state.p2ColorId(), TeamColor.BLUE);

        boolean scoreIncrementPending = "EVALUATION".equals(phase) && elapsedTicks < 40.0f;
        int shownP1Wins = state.p1Wins();
        int shownP2Wins = state.p2Wins();
        if (scoreIncrementPending) {
            if (state.lastWinnerName().equalsIgnoreCase(state.p1Name()) && shownP1Wins > 0) {
                shownP1Wins--;
            } else if (state.lastWinnerName().equalsIgnoreCase(state.p2Name()) && shownP2Wins > 0) {
                shownP2Wins--;
            }
        }

        // 1. Unified Top Scorebar (identical to MinigameHudElement)
        int maxDots = Math.clamp((state.totalGames() + 1) / 2, 4, 7);
        String p1Dots = BroWarsUiHelper.formatDots(shownP1Wins, maxDots, false);
        String p2Dots = BroWarsUiHelper.formatDots(shownP2Wins, maxDots, true);

        String subStatus = switch (phase) {
            case "INTRO" -> "§7" + state.totalGames() + " Spiele";
            case "ROULETTE", "EVALUATION", "LOBBY" -> state.currentGameNumber() > state.totalGames()
                    ? "§cEntscheidungsspiel"
                    : "§7Spiel " + Math.max(1, state.currentGameNumber()) + " / " + state.totalGames();
            case "FINALE_EVALUATION" -> "§6Finale";
            case "PODIUM" -> "§e" + Math.max(0, (int) Math.ceil(remainingTicks / 20.0f)) + "s";
            default -> "";
        };

        BroWarsUiHelper.drawTopScorebar(
                extractor,
                font,
                screenW,
                centerX,
                "§6§lGAME SHOW",
                subStatus,
                state.p1Name(),
                p1Team.hexColor,
                p1Dots,
                state.p2Name(),
                p2Team.hexColor,
                p2Dots
        );

        // 2. Phase-Specific Center Overlays
        switch (phase) {
            case "INTRO" -> renderIntroOverlay(extractor, font, centerX, centerY, state, p1Team, p2Team, elapsedTicks);
            case "ROULETTE" -> renderRouletteOverlay(extractor, font, centerX, centerY, state, elapsedTicks);
            case "EVALUATION" -> renderEvaluationOverlay(extractor, font, centerX, centerY, state, p1Team, p2Team, shownP1Wins, shownP2Wins, elapsedTicks);
            case "FINALE_EVALUATION" -> renderFinaleOverlay(extractor, font, centerX, centerY, state, p1Team, p2Team, elapsedTicks);
            default -> {}
        }

        extractor.pose().popMatrix();
    }

    private void renderIntroOverlay(
            GuiGraphicsExtractor extractor,
            Font font,
            int centerX,
            int centerY,
            SyncGameshowHudPayload state,
            TeamColor p1Team,
            TeamColor p2Team,
            float elapsedTicks
    ) {
        int boxW = 284;
        int boxH = 92;
        int left = centerX - boxW / 2;
        int right = centerX + boxW / 2;
        int top = centerY - boxH / 2 + 6;
        int bottom = top + boxH;

        BroWarsUiHelper.drawPanel(extractor, left, top, right, bottom, BroWarsUiHelper.ACCENT_GOLD);

        extractor.centeredText(font, "§6§lGAME SHOW", centerX, top + 9, BroWarsUiHelper.ACCENT_GOLD);

        float slideProgress = Math.clamp(elapsedTicks / 16.0f, 0.0f, 1.0f);
        float ease = 1.0f - (float) Math.pow(1.0f - slideProgress, 3.0);
        float slideOffset = (1.0f - ease) * 18.0f;

        int cardW = 112;
        int cardH = 28;
        int cardY = top + 28;

        int maxDots = Math.clamp((state.totalGames() + 1) / 2, 4, 7);
        String p1Dots = BroWarsUiHelper.formatDots(0, maxDots, false);
        String p2Dots = BroWarsUiHelper.formatDots(0, maxDots, true);

        // P1 Card
        extractor.pose().pushMatrix();
        extractor.pose().translate(-slideOffset, 0.0f);
        BroWarsUiHelper.drawPlayerCard(extractor, font, centerX - 14 - cardW, cardY, cardW, cardH, state.p1Name(), p1Team.hexColor, false, p1Dots);
        extractor.pose().popMatrix();

        extractor.centeredText(font, "§8§lVS", centerX, cardY + 10, BroWarsUiHelper.TEXT_MUTED);

        // P2 Card
        extractor.pose().pushMatrix();
        extractor.pose().translate(slideOffset, 0.0f);
        BroWarsUiHelper.drawPlayerCard(extractor, font, centerX + 14, cardY, cardW, cardH, state.p2Name(), p2Team.hexColor, true, p2Dots);
        extractor.pose().popMatrix();

        extractor.centeredText(font, "§7" + state.totalGames() + " Spiele", centerX, bottom - 16, BroWarsUiHelper.TEXT_SUB);
    }

    private void renderRouletteOverlay(
            GuiGraphicsExtractor extractor,
            Font font,
            int centerX,
            int centerY,
            SyncGameshowHudPayload state,
            float elapsedTicks
    ) {
        List<String> poolIds = state.roulettePoolIds();
        if (poolIds == null || poolIds.isEmpty()) return;

        int count = poolIds.size();
        int targetIdx = Math.clamp(state.rouletteTargetIndex(), 0, count - 1);

        int revealHoldTicks = 40;
        int spinDuration = Math.max(1, state.phaseTotalTicks() - revealHoldTicks);
        boolean finishedSpin = elapsedTicks >= spinDuration;

        float t = finishedSpin ? 1.0f : Math.clamp(elapsedTicks / (float) spinDuration, 0.0f, 1.0f);
        // Smooth ease-out curve matching GameshowManager sound ticks
        float easeOut = 1.0f - (float) Math.pow(1.0f - t, 3.8);
        int totalSteps = 22;
        float startSlot = targetIdx - totalSteps;
        float currentSlotFloat = startSlot + easeOut * totalSteps;

        // Constant panel dimensions so there is zero layout jump when spin finishes
        int boxW = 268;
        int boxH = 120;
        int left = centerX - boxW / 2;
        int right = centerX + boxW / 2;
        int top = centerY - boxH / 2 + 8;
        int bottom = top + boxH;

        BroWarsUiHelper.drawPanel(extractor, left, top, right, bottom, BroWarsUiHelper.ACCENT_GOLD);

        boolean isTiebreaker = state.currentGameNumber() > state.totalGames();
        String header = isTiebreaker
                ? "§c§lENTSCHEIDUNGSSPIEL"
                : ("§6§lSPIEL " + state.currentGameNumber() + " / " + state.totalGames());
        extractor.centeredText(font, header, centerX, top + 8, BroWarsUiHelper.TEXT_PRIMARY);

        // Reel Viewport (Vertical 3-card slot window, sub-pixel matrix translation)
        int winW = 228;
        int winH = 84;
        int winLeft = centerX - winW / 2;
        int winRight = centerX + winW / 2;
        int winTop = top + 24;
        int winBottom = winTop + winH;
        int winCenterY = winTop + winH / 2;

        extractor.fill(winLeft - 1, winTop - 1, winRight + 1, winBottom + 1, finishedSpin ? BroWarsUiHelper.ACCENT_GOLD : BroWarsUiHelper.BORDER_SUBTLE);
        extractor.fill(winLeft, winTop, winRight, winBottom, BroWarsUiHelper.BG_REEL);

        int baseIndex = (int) Math.floor(currentSlotFloat);
        float frac = currentSlotFloat - baseIndex;
        float rowStep = 27.0f;

        extractor.enableScissor(winLeft, winTop, winRight, winBottom);
        for (int offset = -2; offset <= 2; offset++) {
            int idx = Math.floorMod(baseIndex + offset, count);
            GameshowGameType game = GameshowGameType.fromId(poolIds.get(idx));
            String gName = game != null ? game.displayName : poolIds.get(idx);

            float deltaFromCenter = offset - frac;
            boolean isCenterCard = Math.abs(deltaFromCenter) < 0.45f;

            int cardH = 22;
            int cLeft = winLeft + 8;
            int cRight = winRight - 8;
            int halfH = cardH / 2;

            extractor.pose().pushMatrix();
            extractor.pose().translate(0.0f, winCenterY + deltaFromCenter * rowStep);

            int bgCol = isCenterCard ? BroWarsUiHelper.BG_ELEVATED : BroWarsUiHelper.BG_CARD;
            int barCol = isCenterCard
                    ? (finishedSpin ? BroWarsUiHelper.ACCENT_GOLD : BroWarsUiHelper.ACCENT_ORANGE)
                    : BroWarsUiHelper.ACCENT_MUTED;

            extractor.fill(cLeft, -halfH, cRight, halfH, bgCol);
            extractor.fill(cLeft, -halfH, cLeft + 4, halfH, barCol);
            extractor.fill(cRight - 4, -halfH, cRight, halfH, barCol);

            if (isCenterCard) {
                int frameCol = finishedSpin ? BroWarsUiHelper.ACCENT_GOLD : BroWarsUiHelper.ACCENT_ORANGE;
                extractor.fill(cLeft, -halfH, cRight, -halfH + 1, frameCol);
                extractor.fill(cLeft, halfH - 1, cRight, halfH, frameCol);
            }

            int textColor = isCenterCard ? BroWarsUiHelper.TEXT_PRIMARY : BroWarsUiHelper.TEXT_MUTED;
            String label = isCenterCard ? ("§l" + gName.toUpperCase()) : gName;
            extractor.centeredText(font, label, centerX, -4, textColor);
            extractor.pose().popMatrix();
        }
        extractor.disableScissor();

        // Side pointers
        extractor.text(font, "§6§l▶", winLeft - 9, winCenterY - 4, BroWarsUiHelper.ACCENT_GOLD, true);
        extractor.text(font, "§6§l◀", winRight + 3, winCenterY - 4, BroWarsUiHelper.ACCENT_GOLD, true);
    }

    private void renderEvaluationOverlay(
            GuiGraphicsExtractor extractor,
            Font font,
            int centerX,
            int centerY,
            SyncGameshowHudPayload state,
            TeamColor p1Team,
            TeamColor p2Team,
            int shownP1Wins,
            int shownP2Wins,
            float elapsedTicks
    ) {
        int boxW = 296;
        int boxH = 94;
        int left = centerX - boxW / 2;
        int right = centerX + boxW / 2;
        int top = centerY - boxH / 2 + 8;
        int bottom = top + boxH;

        TeamColor winTeam = TeamColor.fromString(state.lastWinnerColorId(), TeamColor.YELLOW);
        BroWarsUiHelper.drawPanel(extractor, left, top, right, bottom, BroWarsUiHelper.ACCENT_GOLD);

        GameshowGameType gameType = GameshowGameType.fromId(state.activeGameId());
        String gameTitle = gameType != null ? gameType.displayName : state.activeGameId();
        extractor.centeredText(font, "§6§l" + gameTitle.toUpperCase(), centerX, top + 8, BroWarsUiHelper.ACCENT_GOLD);

        String summaryPart = (state.lastScoreSummary() != null && !state.lastScoreSummary().isEmpty())
                ? (" §8• §7" + state.lastScoreSummary())
                : "";
        String winBanner = winTeam.chatPrefix + "§l" + state.lastWinnerName() + summaryPart;
        extractor.centeredText(font, winBanner, centerX, top + 21, BroWarsUiHelper.TEXT_PRIMARY);

        // 3-Column Scoreboard Row: [P1 Card] [Center Score Box] [P2 Card]
        int scoreBoxW = 54;
        int cardW = 104;
        int cardH = 36;
        int cardY = top + 40;

        boolean p1WonLast = state.lastWinnerName().equalsIgnoreCase(state.p1Name());
        boolean p2WonLast = state.lastWinnerName().equalsIgnoreCase(state.p2Name());
        boolean tallied = elapsedTicks >= 40.0f;

        float popScale = 1.0f;
        if (tallied && elapsedTicks < 56.0f) {
            float p = (elapsedTicks - 40.0f) / 16.0f;
            popScale = 1.0f + 0.35f * (float) Math.sin(p * Math.PI);
        }

        int maxDots = Math.clamp((state.totalGames() + 1) / 2, 4, 7);
        String p1Dots = BroWarsUiHelper.formatDots(shownP1Wins, maxDots, false);
        String p2Dots = BroWarsUiHelper.formatDots(shownP2Wins, maxDots, true);

        // Left: P1 Card
        int p1L = centerX - (scoreBoxW / 2) - 6 - cardW;
        BroWarsUiHelper.drawPlayerCard(extractor, font, p1L, cardY, cardW, cardH, state.p1Name(), p1Team.hexColor, false, p1Dots);
        if (p1WonLast && tallied) {
            extractor.fill(p1L, cardY, p1L + cardW, cardY + 1, BroWarsUiHelper.ACCENT_GOLD);
            extractor.fill(p1L, cardY + cardH - 1, p1L + cardW, cardY + cardH, BroWarsUiHelper.ACCENT_GOLD);
        }

        // Center: Score Box
        int sL = centerX - scoreBoxW / 2;
        int sR = centerX + scoreBoxW / 2;
        extractor.fill(sL - 1, cardY - 1, sR + 1, cardY + cardH + 1, tallied ? BroWarsUiHelper.ACCENT_GOLD : BroWarsUiHelper.BORDER_SUBTLE);
        extractor.fill(sL, cardY, sR, cardY + cardH, BroWarsUiHelper.BG_ELEVATED);

        extractor.pose().pushMatrix();
        extractor.pose().translate(centerX - 13.0f, cardY + 14.0f);
        if (p1WonLast) extractor.pose().scale(popScale, popScale);
        String s1Str = "§f§l" + shownP1Wins;
        extractor.text(font, s1Str, -font.width(s1Str) / 2, -4, BroWarsUiHelper.ACCENT_GOLD, true);
        extractor.pose().popMatrix();

        extractor.centeredText(font, "§8§l:", centerX, cardY + 10, BroWarsUiHelper.TEXT_MUTED);

        extractor.pose().pushMatrix();
        extractor.pose().translate(centerX + 13.0f, cardY + 14.0f);
        if (p2WonLast) extractor.pose().scale(popScale, popScale);
        String s2Str = "§f§l" + shownP2Wins;
        extractor.text(font, s2Str, -font.width(s2Str) / 2, -4, BroWarsUiHelper.ACCENT_GOLD, true);
        extractor.pose().popMatrix();

        if (tallied && (p1WonLast || p2WonLast)) {
            int plusX = p1WonLast ? (centerX - 13) : (centerX + 13);
            extractor.centeredText(font, "§a§l+1", plusX, cardY + 24, BroWarsUiHelper.ACCENT_GREEN);
        }

        // Right: P2 Card
        int p2L = centerX + (scoreBoxW / 2) + 6;
        BroWarsUiHelper.drawPlayerCard(extractor, font, p2L, cardY, cardW, cardH, state.p2Name(), p2Team.hexColor, true, p2Dots);
        if (p2WonLast && tallied) {
            extractor.fill(p2L, cardY, p2L + cardW, cardY + 1, BroWarsUiHelper.ACCENT_GOLD);
            extractor.fill(p2L, cardY + cardH - 1, p2L + cardW, cardY + cardH, BroWarsUiHelper.ACCENT_GOLD);
        }
    }

    private void renderFinaleOverlay(
            GuiGraphicsExtractor extractor,
            Font font,
            int centerX,
            int centerY,
            SyncGameshowHudPayload state,
            TeamColor p1Team,
            TeamColor p2Team,
            float elapsedTicks
    ) {
        boolean revealed = elapsedTicks >= 40.0f;

        List<GameshowData.PlayedGameEntry> played = state.playedGames();
        int maxRows = Math.min(played.size(), 8);

        int boxW = 296;
        int boxH = 74 + maxRows * 11;
        int left = centerX - boxW / 2;
        int right = centerX + boxW / 2;
        int top = centerY - boxH / 2 + 8;
        int bottom = top + boxH;

        boolean p1Champion = state.p1Wins() >= state.p2Wins();
        TeamColor champTeam = p1Champion ? p1Team : p2Team;
        String champName = p1Champion ? state.p1Name() : state.p2Name();

        BroWarsUiHelper.drawPanel(extractor, left, top, right, bottom, BroWarsUiHelper.ACCENT_GOLD);

        extractor.centeredText(font, "§6§lENDERGEBNIS", centerX, top + 8, BroWarsUiHelper.ACCENT_GOLD);

        // Winner Crown Box
        int crownTop = top + 22;
        int crownBottom = crownTop + 34;
        extractor.fill(left + 12, crownTop, right - 12, crownBottom, BroWarsUiHelper.BG_CARD);
        extractor.fill(left + 12, crownTop, right - 12, crownTop + 1, revealed ? BroWarsUiHelper.ACCENT_GOLD : BroWarsUiHelper.BORDER_SUBTLE);

        String finalStanding = p1Team.chatPrefix + state.p1Name() + "  §f§l" + state.p1Wins()
                + " §8: §f§l" + state.p2Wins() + "  " + p2Team.chatPrefix + state.p2Name();

        if (!revealed) {
            extractor.centeredText(font, finalStanding, centerX, crownTop + 13, BroWarsUiHelper.TEXT_PRIMARY);
        } else {
            String champTitle = "§6§l👑 " + champTeam.chatPrefix + "§l" + champName.toUpperCase();
            extractor.centeredText(font, champTitle, centerX, crownTop + 6, BroWarsUiHelper.ACCENT_GOLD);
            extractor.centeredText(font, finalStanding, centerX, crownTop + 19, BroWarsUiHelper.TEXT_SUB);
        }

        int rowY = crownBottom + 8;
        for (int i = 0; i < maxRows; i++) {
            GameshowData.PlayedGameEntry entry = played.get(i);
            TeamColor wCol = TeamColor.fromString(entry.winnerColorId(), TeamColor.YELLOW);
            String scorePart = (entry.scoreSummary() != null && !entry.scoreSummary().isEmpty())
                    ? (" §8(" + entry.scoreSummary() + ")")
                    : "";
            String line = "§7#" + entry.gameNumber() + " §f" + entry.gameTitle()
                    + " §8• " + wCol.chatPrefix + entry.winnerName() + scorePart;
            extractor.centeredText(font, line, centerX, rowY, BroWarsUiHelper.TEXT_SUB);
            rowY += 11;
        }
    }
}
