package de.browars.system;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelData;

import java.util.Set;

public class WorldSpawnRespawnHandler {
    public static void register() {
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!alive) {
                var manager = de.browars.minigame.MinigameManager.getInstance();
                if (manager.isPlayerInActiveGame(newPlayer.getUUID())) {
                    manager.getActiveGame().onPlayerRespawn(newPlayer);
                    return;
                }
                var gameshow = de.browars.gameshow.GameshowManager.getInstance();
                if (gameshow.isPlayerInShow(newPlayer.getUUID())) {
                    gameshow.onPlayerRespawn(newPlayer);
                    return;
                }
                teleportToWorldSpawn(newPlayer);
            }
        });
    }

    public static void teleportToWorldSpawn(ServerPlayer player) {
        ServerLevel overworld = player.level().getServer().overworld();
        if (overworld == null) return;

        // Zero out random spawn radius so players don't spawn scattered
        try {
            overworld.getGameRules().set(GameRules.RESPAWN_RADIUS, 0, player.level().getServer());
        } catch (Exception ignored) {}

        de.browars.gameshow.GameshowData.SpawnPoint mainSpawn =
                de.browars.gameshow.GameshowSavedData.get(overworld).getData().getMainSpawn();

        player.stopRiding();
        if (mainSpawn != null && mainSpawn.set()) {
            ServerLevel targetLevel = player.level().getServer().getLevel(mainSpawn.dimension());
            if (targetLevel == null) targetLevel = overworld;
            player.teleportTo(
                    targetLevel,
                    mainSpawn.x(),
                    mainSpawn.y(),
                    mainSpawn.z(),
                    Set.of(),
                    mainSpawn.yaw(),
                    mainSpawn.pitch(),
                    true
            );
        } else {
            LevelData.RespawnData respawnData = overworld.getRespawnData();
            BlockPos pos = respawnData.pos();
            float yaw = respawnData.yaw();
            float pitch = respawnData.pitch();

            player.teleportTo(
                    overworld,
                    pos.getX() + 0.5,
                    pos.getY(),
                    pos.getZ() + 0.5,
                    Set.of(),
                    yaw,
                    pitch,
                    true
            );
        }
        player.setDeltaMovement(0, 0, 0);
        player.fallDistance = 0;
    }
}
