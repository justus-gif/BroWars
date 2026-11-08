package de.browars.util;

import de.browars.minigame.Minigame;
import de.browars.minigame.MinigameManager;
import de.browars.minigame.sumo.SumoGame;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public class SumoHitboxHelper {

    public static boolean isSumoActiveForPlayer(Entity entity) {
        if (!(entity instanceof Player player)) {
            return false;
        }

        // Server-side check
        if (!player.level().isClientSide()) {
            Minigame active = MinigameManager.getInstance().getActiveGame();
            return active instanceof SumoGame sumo && sumo.isRunning() && sumo.isPlayerParticipating(player.getUUID());
        }

        // Client-side check (safely isolated to avoid classloading issues on dedicated servers)
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            return isClientSumoActive(player);
        }

        return false;
    }

    private static boolean isClientSumoActive(Player player) {
        try {
            return de.browars.client.minigame.MinigameHudClientHolder.isActive()
                    && "sumo".equals(de.browars.client.minigame.MinigameHudClientHolder.getState().gameId())
                    && (player.getPlainTextName().equals(de.browars.client.minigame.MinigameHudClientHolder.getState().p1Name())
                    || player.getPlainTextName().equals(de.browars.client.minigame.MinigameHudClientHolder.getState().p2Name()));
        } catch (Throwable ignored) {
            return false;
        }
    }
}
