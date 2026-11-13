package de.browars.client.gameshow;

import de.browars.network.SyncGameshowColorSelectPayload;
import de.browars.network.SyncGameshowHudPayload;

public final class GameshowClientHolder {
    private static SyncGameshowHudPayload hudState = SyncGameshowHudPayload.INACTIVE;
    private static SyncGameshowColorSelectPayload colorSelectState = null;

    // Client-side tick counters for 100% smooth sub-frame interpolation without network packet jitter
    private static String lastPhase = "INACTIVE";
    private static int lastGameNumber = -1;
    private static int clientElapsedTicks = 0;
    private static int clientRemainingTicks = 0;

    private GameshowClientHolder() {}

    public static void setHudState(SyncGameshowHudPayload state) {
        SyncGameshowHudPayload next = state != null ? state : SyncGameshowHudPayload.INACTIVE;
        if (!next.active()) {
            hudState = SyncGameshowHudPayload.INACTIVE;
            lastPhase = "INACTIVE";
            lastGameNumber = -1;
            clientElapsedTicks = 0;
            clientRemainingTicks = 0;
            return;
        }

        boolean phaseChanged = !next.phase().equals(lastPhase) || next.currentGameNumber() != lastGameNumber;
        hudState = next;
        if (phaseChanged) {
            lastPhase = next.phase();
            lastGameNumber = next.currentGameNumber();
            clientElapsedTicks = Math.max(0, next.phaseTotalTicks() - next.phaseTicksRemaining());
            clientRemainingTicks = next.phaseTicksRemaining();
        }
    }

    public static void clientTick() {
        if (!isActive()) return;
        clientElapsedTicks++;
        if (clientRemainingTicks > 0) {
            clientRemainingTicks--;
        }
    }

    public static float getSmoothElapsedTicks(float partialTick) {
        return clientElapsedTicks + partialTick;
    }

    public static float getSmoothRemainingTicks(float partialTick) {
        return Math.max(0.0f, clientRemainingTicks - partialTick);
    }

    public static SyncGameshowHudPayload getHudState() {
        return hudState;
    }

    public static boolean isActive() {
        return hudState != null && hudState.active();
    }

    public static boolean isInBlackRoomCutscene() {
        if (!isActive()) return false;
        String p = hudState.phase();
        return "COLOR_SELECT".equals(p)
                || "INTRO".equals(p)
                || "ROULETTE".equals(p)
                || "EVALUATION".equals(p)
                || "FINALE_EVALUATION".equals(p);
    }

    public static void setColorSelectState(SyncGameshowColorSelectPayload state) {
        colorSelectState = state;
    }

    public static SyncGameshowColorSelectPayload getColorSelectState() {
        return colorSelectState;
    }

    public static void reset() {
        hudState = SyncGameshowHudPayload.INACTIVE;
        colorSelectState = null;
        lastPhase = "INACTIVE";
        lastGameNumber = -1;
        clientElapsedTicks = 0;
        clientRemainingTicks = 0;
        de.browars.client.gui.GameshowColorSelectScreen.closeIfOpen();
    }
}
