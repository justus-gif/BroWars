package de.browars.minigame.targetrush;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;

public class TargetRushTarget {
    private final BlockPos pos;
    private final net.minecraft.world.level.block.state.BlockState previousState;
    private final int spawnTick;
    private final int lifetimeTicks;
    private final ZoneTier tier;
    private final java.util.UUID hologramUuid;

    public TargetRushTarget(BlockPos pos, net.minecraft.world.level.block.state.BlockState previousState, int spawnTick, int lifetimeTicks, ZoneTier tier, java.util.UUID hologramUuid) {
        this.pos = pos.immutable();
        this.previousState = previousState;
        this.spawnTick = spawnTick;
        this.lifetimeTicks = lifetimeTicks;
        this.tier = tier;
        this.hologramUuid = hologramUuid;
    }

    public BlockPos getPos() { return pos; }
    public net.minecraft.world.level.block.state.BlockState getPreviousState() { return previousState; }
    public int getSpawnTick() { return spawnTick; }
    public int getLifetimeTicks() { return lifetimeTicks; }
    public ZoneTier getTier() { return tier; }
    public java.util.UUID getHologramUuid() { return hologramUuid; }

    public void remove(ServerLevel level) {
        if (hologramUuid != null) {
            de.browars.util.HologramHelper.removeHologram(level, hologramUuid);
        }
        level.setBlockAndUpdate(pos, previousState);
    }

    public boolean isExpired(int currentPlayTicks) {
        return (currentPlayTicks - spawnTick) >= lifetimeTicks;
    }

    private static final net.minecraft.core.particles.DustParticleOptions GOLD_DUST = new net.minecraft.core.particles.DustParticleOptions(0xFFD700, 2.0f);
    private static final net.minecraft.core.particles.DustParticleOptions SILVER_DUST = new net.minecraft.core.particles.DustParticleOptions(0xE2E8F0, 2.0f);
    private static final net.minecraft.core.particles.DustParticleOptions BRONZE_DUST = new net.minecraft.core.particles.DustParticleOptions(0xB45309, 2.0f);

    public void tickParticles(ServerLevel level, int tick) {
        // Emit particles every 3 ticks around the block outline
        if (tick % 3 != 0) return;

        double x = pos.getX();
        double y = pos.getY();
        double z = pos.getZ();

        net.minecraft.core.particles.DustParticleOptions dust;
        net.minecraft.core.particles.SimpleParticleType extra;

        switch (tier) {
            case GOLD -> {
                dust = GOLD_DUST;
                extra = ParticleTypes.FLAME;
            }
            case SILVER -> {
                dust = SILVER_DUST;
                extra = ParticleTypes.END_ROD;
            }
            default -> {
                dust = BRONZE_DUST;
                extra = ParticleTypes.SMALL_FLAME;
            }
        }

        // 8 Corners of the block with large, bold dust particles (force=true, visible across whole arena)
        double[][] corners = {
                {x - 0.02, y - 0.02, z - 0.02}, {x + 1.02, y - 0.02, z - 0.02},
                {x - 0.02, y + 1.02, z - 0.02}, {x + 1.02, y + 1.02, z - 0.02},
                {x - 0.02, y - 0.02, z + 1.02}, {x + 1.02, y - 0.02, z + 1.02},
                {x - 0.02, y + 1.02, z + 1.02}, {x + 1.02, y + 1.02, z + 1.02}
        };

        for (double[] c : corners) {
            level.sendParticles(dust, true, true, c[0], c[1], c[2], 1, 0.0, 0.0, 0.0, 0.0);
        }

        // Edge midpoints for a clear wireframe outline
        double[][] edgeMids = {
                {x + 0.5, y - 0.02, z - 0.02}, {x + 0.5, y + 1.02, z - 0.02},
                {x + 0.5, y - 0.02, z + 1.02}, {x + 0.5, y + 1.02, z + 1.02},
                {x - 0.02, y + 0.5, z - 0.02}, {x + 1.02, y + 0.5, z - 0.02},
                {x - 0.02, y + 0.5, z + 1.02}, {x + 1.02, y + 0.5, z + 1.02},
                {x - 0.02, y - 0.02, z + 0.5}, {x + 1.02, y - 0.02, z + 0.5},
                {x - 0.02, y + 1.02, z + 0.5}, {x + 1.02, y + 1.02, z + 0.5}
        };

        for (double[] em : edgeMids) {
            level.sendParticles(dust, true, true, em[0], em[1], em[2], 1, 0.0, 0.0, 0.0, 0.0);
        }

        // Extra glowing accent particles on top and sides
        level.sendParticles(extra, true, true, x + 0.5, y + 1.05, z + 0.5, 2, 0.25, 0.0, 0.25, 0.01);

        // High vertical sparkle beacon for high-value targets (Gold & Silver)
        if (tier == ZoneTier.GOLD) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, true, x + 0.5, y + 2.0, z + 0.5, 4, 0.15, 0.6, 0.15, 0.02);
            level.sendParticles(ParticleTypes.FLAME, true, true, x + 0.5, y + 1.6, z + 0.5, 2, 0.08, 0.4, 0.08, 0.01);
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, true, true, x + 0.5, y + 1.2, z + 0.5, 3, 0.2, 0.3, 0.2, 0.05);
        } else if (tier == ZoneTier.SILVER) {
            level.sendParticles(ParticleTypes.END_ROD, true, true, x + 0.5, y + 1.8, z + 0.5, 3, 0.1, 0.5, 0.1, 0.01);
        }
    }
}
