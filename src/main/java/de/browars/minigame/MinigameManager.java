package de.browars.minigame;

import de.browars.minigame.ui.MinigameHudState;
import de.browars.network.SyncMinigameHudPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class MinigameManager {
    private static final MinigameManager INSTANCE = new MinigameManager();
    private Minigame activeGame = null;

    public static MinigameManager getInstance() {
        return INSTANCE;
    }

    // Construct only after the previous game has restored its players and arena.
    public void startMinigame(java.util.function.Supplier<? extends Minigame> factory) {
        stopCurrentMinigame("Neues Minispiel gestartet");
        this.activeGame = factory.get();
        try {
            this.activeGame.start();
        } catch (RuntimeException failure) {
            stopCurrentMinigame("Spielstart fehlgeschlagen");
            throw failure;
        }
    }

    public void stopCurrentMinigame(String reason) {
        if (this.activeGame != null) {
            try {
                this.activeGame.stop(reason);
            } finally {
                this.activeGame = null;
            }
        }
    }

    public Minigame getActiveGame() {
        return activeGame;
    }

    public boolean isGameRunning() {
        return activeGame != null && activeGame.isRunning();
    }

    public boolean isPlayerInActiveGame(java.util.UUID playerUuid) {
        return activeGame != null && activeGame.isRunning() && activeGame.isPlayerParticipating(playerUuid);
    }

    public void onServerTick(MinecraftServer server) {
        if (activeGame != null) {
            if (!activeGame.isRunning()) {
                activeGame = null;
                broadcastHud(server, MinigameHudState.inactive());
                return;
            }

            activeGame.tick();
            broadcastHud(server, activeGame.isRunning() ? activeGame.getHudState() : MinigameHudState.inactive());
            if (!activeGame.isRunning()) activeGame = null;
        }
    }

    public void broadcastHud(MinecraftServer server, MinigameHudState state) {
        SyncMinigameHudPayload payload = new SyncMinigameHudPayload(state);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    public void syncPlayer(ServerPlayer player) {
        MinigameHudState state = (activeGame != null && activeGame.isRunning())
                ? activeGame.getHudState()
                : MinigameHudState.inactive();
        ServerPlayNetworking.send(player, new SyncMinigameHudPayload(state));
    }
}
