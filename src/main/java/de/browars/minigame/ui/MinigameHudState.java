package de.browars.minigame.ui;

public record MinigameHudState(
        boolean active,
        String gameId,
        String gameTitle,
        String p1Name,
        int p1Score,
        int p1ColorHex,
        String p2Name,
        int p2Score,
        int p2ColorHex,
        int maxScore,
        int currentRound,
        String statusText,
        int timerRemainingTicks,
        int timerTotalTicks,
        int targetColorHex,
        String targetColorName,
        String centerTitle,
        String centerSubtitle,
        int centerTitleColorHex,
        int centerTitleTicks,
        int centerTitleTotalTicks,
        int phaseNumber,
        String phaseTitle,
        int taggerIndex
) {
    public static MinigameHudState inactive() {
        return new MinigameHudState(
                false, "", "",
                "", 0, 0xFF2222,
                "", 0, 0x2266FF,
                2, 1, "",
                -1, -1,
                0, "",
                "", "", 0xFFFFFF, 0, 0,
                1, "Aufwärmen",
                0
        );
    }
}
