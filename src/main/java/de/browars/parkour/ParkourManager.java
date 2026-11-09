package de.browars.parkour;

import de.browars.block.entity.ParkourLeaderboardBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ParkourManager {
    private static final ParkourManager INSTANCE = new ParkourManager();

    public static ParkourManager getInstance() {
        return INSTANCE;
    }

    public static class PlayerParkourState {
        public boolean readyInStart = false;
        public boolean running = false;
        public long startTimeMillis = 0L;
        public double lastX = 0;
        public double lastY = 0;
        public double lastZ = 0;
        public boolean hasLastPos = false;
    }

    private final Map<UUID, PlayerParkourState> states = new ConcurrentHashMap<>();

    private ParkourManager() {}

    public void onServerTick(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;

        ParkourSavedData savedData = ParkourSavedData.get(overworld);
        ParkourCourseData course = savedData.getData();
        if (!course.hasStart() || !course.hasEnd()) return;

        AABB startBox = course.getStartAABB();
        AABB endBox = course.getEndAABB();
        ResourceKey<Level> startDim = course.getStartDimension();
        ResourceKey<Level> endDim = course.getEndDimension();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();
            PlayerParkourState state = states.computeIfAbsent(uuid, k -> new PlayerParkourState());

            // 1. Check dimension match
            if (!player.level().dimension().equals(startDim)) {
                if (state.running || state.readyInStart) {
                    state.running = false;
                    state.readyInStart = false;
                    player.sendSystemMessage(Component.literal("§c❌ Parkour abgebrochen (Dimensionswechsel)!"), true);
                }
                state.hasLastPos = false;
                continue;
            }

            // 2. Teleport detection (> 8 blocks in 1 tick)
            if (state.running && state.hasLastPos) {
                double distSq = player.distanceToSqr(state.lastX, state.lastY, state.lastZ);
                if (distSq > 64.0) { // Teleport or respawn
                    state.running = false;
                    state.readyInStart = false;
                    player.sendSystemMessage(Component.literal("§cParkour abgebrochen (Teleport erkannt)!"), true);
                }
            }
            state.lastX = player.getX();
            state.lastY = player.getY();
            state.lastZ = player.getZ();
            state.hasLastPos = true;

            // 3. Flight or Spectator anti-cheat
            if (state.running && (player.isSpectator() || player.getAbilities().flying)) {
                state.running = false;
                state.readyInStart = false;
                player.sendSystemMessage(Component.literal("§cParkour abgebrochen (Fliegen ist verboten)!"), true);
                continue;
            }

            // 4. Start Area Collision Check
            boolean inStart = startBox != null && startBox.intersects(player.getBoundingBox());
            if (inStart) {
                if (state.running) {
                    state.running = false;
                }
                state.readyInStart = true;
                continue;
            }

            // 5. Leaving Start Area -> Start Timer!
            if (state.readyInStart) {
                double distToCenterSq = player.distanceToSqr(startBox.getCenter());
                if (distToCenterSq <= 36.0 && !player.getAbilities().flying && !player.isSpectator()) {
                    state.readyInStart = false;
                    state.running = true;
                    state.startTimeMillis = System.currentTimeMillis();
                    player.sendSystemMessage(Component.literal("§6Zeit: §e00:00:00"), true);
                } else {
                    state.readyInStart = false;
                }
                continue;
            }

            // 6. Running -> Check Finish or Update Actionbar
            if (state.running) {
                boolean reachedFinish = player.level().dimension().equals(endDim)
                        && endBox != null && endBox.intersects(player.getBoundingBox());

                if (reachedFinish) {
                    long totalTime = System.currentTimeMillis() - state.startTimeMillis;
                    state.running = false;
                    state.readyInStart = false;

                    // Play pleasant sound only on finish
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.3f);

                    ParkourScore previousPb = course.getPersonalBest(player.getUUID());
                    boolean isNewPb = course.addOrUpdateScore(player.getUUID(), player.getName().getString(), totalTime);
                    ParkourSavedData.save(player.level());

                    updateAllHolograms(server);

                    String timeStr = ParkourScore.formatTime(totalTime);
                    if (isNewPb) {
                        if (previousPb == null) {
                            player.sendSystemMessage(Component.literal("§aZiel! §fZeit: §e" + timeStr + " §a(Erster Rekord!)"), true);
                            server.getPlayerList().broadcastSystemMessage(
                                    Component.literal("§6[Parkour] §e" + player.getName().getString() + " §ahat den Parkour in §e" + timeStr + " §agemeistert!"),
                                    false
                            );
                        } else {
                            player.sendSystemMessage(Component.literal("§aNeue Bestzeit! §e" + timeStr + " §7(Vorher: " + ParkourScore.formatTime(previousPb.timeMillis()) + ")"), true);
                            server.getPlayerList().broadcastSystemMessage(
                                    Component.literal("§6[Parkour] §e" + player.getName().getString() + " §ahat eine neue Bestzeit aufgestellt: §e" + timeStr + "!"),
                                    false
                            );
                        }
                    } else {
                        player.sendSystemMessage(Component.literal("§aZiel! §fZeit: §e" + timeStr + " §7(Bestzeit: " + ParkourScore.formatTime(previousPb.timeMillis()) + ")"), true);
                    }
                } else {
                    long elapsed = System.currentTimeMillis() - state.startTimeMillis;
                    ParkourScore pb = course.getPersonalBest(player.getUUID());
                    String pbStr = pb != null ? " §7| §bBestzeit: §a" + ParkourScore.formatTime(pb.timeMillis()) : "";
                    player.sendSystemMessage(Component.literal("§6Zeit: §e" + ParkourScore.formatTime(elapsed) + pbStr), true);
                }
            }
        }
    }

    public void cancelRun(ServerPlayer player) {
        PlayerParkourState state = states.get(player.getUUID());
        if (state != null && state.running) {
            state.running = false;
            state.readyInStart = false;
            player.sendSystemMessage(Component.literal("§cParkour abgebrochen!"), true);
        }
    }

    public void updateAllHolograms(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;
        ParkourSavedData savedData = ParkourSavedData.get(overworld);
        for (BlockPos pos : savedData.getData().getLeaderboardBlocks()) {
            for (ServerLevel lvl : server.getAllLevels()) {
                if (lvl.hasChunkAt(pos)) {
                    BlockEntity be = lvl.getBlockEntity(pos);
                    if (be instanceof ParkourLeaderboardBlockEntity lbBe) {
                        lbBe.updateHologram(lvl);
                    }
                }
            }
        }
    }
}
