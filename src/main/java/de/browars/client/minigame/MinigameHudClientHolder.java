package de.browars.client.minigame;

import de.browars.minigame.ui.MinigameHudState;

public class MinigameHudClientHolder {
    private static MinigameHudState state = MinigameHudState.inactive();

    public static void setState(MinigameHudState newState) {
        state = newState != null ? newState : MinigameHudState.inactive();
    }

    public static MinigameHudState getState() {
        return state;
    }

    public static boolean isActive() {
        return state != null && state.active();
    }
}
