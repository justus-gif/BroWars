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

    public TargetRushTarget(BlockPos pos, net.minecraft.world.level.block.state.BlockState previousState, int spawnTick, int lifetimeTicks, ZoneTier tier) {
        this.pos = pos.immutable();
        this.previousState = previousState;
        this.spawnTick = spawnTick;
        this.lifetimeTicks = lifetimeTicks;
        this.tier = tier;
    }

    public BlockPos getPos() { return pos; }
    public net.minecraft.world.level.block.state.BlockState getPreviousState() { return previousState; }
    public int getSpawnTick() { return spawnTick; }
    public int getLifetimeTicks() { return lifetimeTicks; }
    public ZoneTier getTier() { return tier; }

    public boolean isExpired(int currentPlayTicks) {
        return (currentPlayTicks - spawnTick) >= lifetimeTicks;
    }

    public void tickParticles(ServerLevel level, int tick) {
        // Pulse particles every 4 ticks around the block outline
        if (tick % 4 != 0) return;

        double minX = pos.getX();
        double minY = pos.getY();
        double minZ = pos.getZ();
        double maxX = minX + 1.0;
        double maxY = minY + 1.0;
        double maxZ = minZ + 1.0;

        // Sample points along the 12 edges
        double midX = minX + 0.5;
        double midY = minY + 0.5;
        double midZ = minZ + 0.5;

        switch (tier) {
            case GOLD -> {
                // Gold glowing / electric spark particles
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, midX, maxY + 0.05, midZ, 2, 0.35, 0.0, 0.35, 0.01);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, midX, minY - 0.05, midZ, 2, 0.35, 0.0, 0.35, 0.01);
                level.sendParticles(ParticleTypes.WAX_ON, minX - 0.05, midY, midZ, 1, 0.0, 0.35, 0.35, 0.0);
                level.sendParticles(ParticleTypes.WAX_ON, maxX + 0.05, midY, midZ, 1, 0.0, 0.35, 0.35, 0.0);
                level.sendParticles(ParticleTypes.WAX_ON, midX, midY, minZ - 0.05, 1, 0.35, 0.35, 0.0, 0.0);
                level.sendParticles(ParticleTypes.WAX_ON, midX, midY, maxZ + 0.05, 1, 0.35, 0.35, 0.0, 0.0);
            }
            case SILVER -> {
                // White glitter / end rod particles
                level.sendParticles(ParticleTypes.END_ROD, midX, maxY + 0.05, midZ, 1, 0.3, 0.0, 0.3, 0.01);
                level.sendParticles(ParticleTypes.END_ROD, midX, minY - 0.05, midZ, 1, 0.3, 0.0, 0.3, 0.01);
                level.sendParticles(ParticleTypes.SNOWFLAKE, minX - 0.05, midY, midZ, 1, 0.0, 0.3, 0.3, 0.005);
                level.sendParticles(ParticleTypes.SNOWFLAKE, maxX + 0.05, midY, midZ, 1, 0.0, 0.3, 0.3, 0.005);
                level.sendParticles(ParticleTypes.END_ROD, midX, midY, minZ - 0.05, 1, 0.3, 0.3, 0.0, 0.005);
                level.sendParticles(ParticleTypes.END_ROD, midX, midY, maxZ + 0.05, 1, 0.3, 0.3, 0.0, 0.005);
            }
            case BRONZE -> {
                // Bronze / copper particles
                level.sendParticles(ParticleTypes.WAX_OFF, midX, maxY + 0.05, midZ, 2, 0.3, 0.0, 0.3, 0.01);
                level.sendParticles(ParticleTypes.WAX_OFF, midX, minY - 0.05, midZ, 2, 0.3, 0.0, 0.3, 0.01);
                level.sendParticles(ParticleTypes.SMALL_FLAME, minX - 0.02, midY, midZ, 1, 0.0, 0.25, 0.25, 0.0);
                level.sendParticles(ParticleTypes.SMALL_FLAME, maxX + 0.02, midY, midZ, 1, 0.0, 0.25, 0.25, 0.0);
                level.sendParticles(ParticleTypes.SMALL_FLAME, midX, midY, minZ - 0.02, 1, 0.25, 0.25, 0.0, 0.0);
                level.sendParticles(ParticleTypes.SMALL_FLAME, midX, midY, maxZ + 0.02, 1, 0.25, 0.25, 0.0, 0.0);
            }
        }
    }
}
