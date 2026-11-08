package de.browars.minigame;

import de.browars.minigame.ui.MinigameHudState;

import java.util.UUID;

public interface Minigame {
    String getId();

    String getDisplayName();

    boolean isRunning();

    default void start() {}

    default void onPlayerRespawn(net.minecraft.server.level.ServerPlayer player) {}

    void tick();

    void stop(String reason);

    MinigameHudState getHudState();

    boolean isPlayerParticipating(UUID playerUuid);
}
