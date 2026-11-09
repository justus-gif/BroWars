package de.browars.util;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TeleportHelper {
    public static final long COOLDOWN_MS = 1500;
    private static final Map<UUID, Long> LAST_TELEPORT = new ConcurrentHashMap<>();

    public static boolean canTeleport(ServerPlayer player) {
        if (de.browars.minigame.MinigameManager.getInstance().isPlayerInActiveGame(player.getUUID())) return false;
        long now = System.currentTimeMillis();
        long last = LAST_TELEPORT.getOrDefault(player.getUUID(), 0L);
        return (now - last) >= COOLDOWN_MS;
    }

    public static void setCooldown(ServerPlayer player) {
        LAST_TELEPORT.put(player.getUUID(), System.currentTimeMillis());
    }

    public static boolean teleportPlayer(ServerPlayer player, double targetX, double targetY, double targetZ,
                                         float targetYaw, float targetPitch, ResourceKey<Level> targetDim, int colorHex) {
        if (!canTeleport(player)) {
            return false;
        }

        ServerLevel currentLevel = player.level();
        ServerLevel destLevel = currentLevel.getServer().getLevel(targetDim);
        if (destLevel == null || !Double.isFinite(targetX) || !Double.isFinite(targetY) || !Double.isFinite(targetZ) || !Float.isFinite(targetYaw) || !Float.isFinite(targetPitch)) {
            return false;
        }

        // Particle & sound at origin
        spawnTeleportBurst(currentLevel, player.getX(), player.getY() + 1.0, player.getZ(), colorHex);
        currentLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.1f);
        currentLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.7f, 1.3f);

        // Perform teleport
        boolean success = player.teleportTo(destLevel, targetX, targetY, targetZ, Set.of(), targetYaw, targetPitch, true);

        if (success) {
            setCooldown(player);
            de.browars.parkour.ParkourManager.getInstance().cancelRun(player);

            // Particle & sound at destination delayed by 3 ticks for client loading
            DELAYED_EFFECTS.add(new DelayedEffect(destLevel, targetX, targetY, targetZ, colorHex, 3));
        }

        return success;
    }

    public static class DelayedEffect {
        public final ServerLevel level;
        public final double x, y, z;
        public final int colorHex;
        public int ticksLeft;

        public DelayedEffect(ServerLevel level, double x, double y, double z, int colorHex, int ticksLeft) {
            this.level = level;
            this.x = x;
            this.y = y;
            this.z = z;
            this.colorHex = colorHex;
            this.ticksLeft = ticksLeft;
        }
    }

    public static final java.util.List<DelayedEffect> DELAYED_EFFECTS = new java.util.ArrayList<>();

    public static void onServerTick() {
        var iterator = DELAYED_EFFECTS.iterator();
        while (iterator.hasNext()) {
            DelayedEffect effect = iterator.next();
            effect.ticksLeft--;
            if (effect.ticksLeft <= 0) {
                spawnTeleportBurst(effect.level, effect.x, effect.y + 1.0, effect.z, effect.colorHex);
                effect.level.playSound(null, effect.x, effect.y, effect.z,
                        SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.1f);
                effect.level.playSound(null, effect.x, effect.y, effect.z,
                        SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.7f, 1.3f);
                iterator.remove();
            }
        }
    }

    public static void clearPlayer(UUID id) {
        LAST_TELEPORT.remove(id);
    }

    public static void resetRuntimeState() {
        LAST_TELEPORT.clear();
        DELAYED_EFFECTS.clear();
    }

    public static void spawnTeleportBurst(ServerLevel level, double x, double y, double z, int colorHex) {
        DustParticleOptions dust = createDustParticle(colorHex, 1.4f);

        // Expanding horizontal shockwave ring
        int ringCount = 16;
        for (int i = 0; i < ringCount; i++) {
            double angle = (Math.PI * 2.0 / ringCount) * i;
            double vx = Math.cos(angle) * 0.25;
            double vz = Math.sin(angle) * 0.25;
            level.sendParticles(dust, x, y - 0.8, z, 0, vx, 0.05, vz, 1.0);
        }

        // Full 2-block column burst
        level.sendParticles(dust, x, y, z, 35, 0.35, 0.9, 0.35, 0.15);
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, 15, 0.3, 0.7, 0.3, 0.12);
        level.sendParticles(ParticleTypes.PORTAL, x, y, z, 35, 0.4, 0.8, 0.4, 0.3);
    }

    public static DustParticleOptions createDustParticle(int colorHex, float scale) {
        return new DustParticleOptions(colorHex, scale);
    }
}
