package de.browars.block.entity;

import de.browars.BroWarsMod;
import de.browars.util.HologramHelper;
import de.browars.util.TeleportHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.UUID;

public class TeleportBlockEntity extends BlockEntity {
    private double targetX = 0.0;
    private double targetY = 64.0;
    private double targetZ = 0.0;
    private float targetYaw = 0.0f;
    private float targetPitch = 0.0f;
    private String targetDimension = "minecraft:overworld";
    private String name = "";
    private int colorHex = 0x8A2BE2; // Purple by default
    private UUID textDisplayUuid;
    private int tickCount = 0;

    public TeleportBlockEntity(BlockPos pos, BlockState state) {
        super(BroWarsMod.TELEPORT_BLOCK_ENTITY, pos, state);
        // Default target coordinates to the block position itself
        this.targetX = pos.getX() + 0.5;
        this.targetY = pos.getY();
        this.targetZ = pos.getZ() + 0.5;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TeleportBlockEntity blockEntity) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        blockEntity.tickCount++;

        // 1. Maintain Hologram every 20 ticks or when dirty (hovering at y + 2.35, above the 2-block beam)
        if (blockEntity.tickCount % 20 == 0) {
            if (!blockEntity.name.isEmpty()) {
                double holoX = pos.getX() + 0.5;
                double holoY = pos.getY() + 2.35;
                double holoZ = pos.getZ() + 0.5;

                UUID newUuid = HologramHelper.updateOrCreateHologram(
                        serverLevel,
                        blockEntity.textDisplayUuid,
                        holoX, holoY, holoZ,
                        blockEntity.name,
                        blockEntity.colorHex
                );
                if (newUuid != null && !newUuid.equals(blockEntity.textDisplayUuid)) {
                    blockEntity.textDisplayUuid = newUuid;
                    blockEntity.setChanged();
                }
            } else if (blockEntity.textDisplayUuid != null) {
                HologramHelper.removeHologram(serverLevel, blockEntity.textDisplayUuid);
                blockEntity.textDisplayUuid = null;
                blockEntity.setChanged();
            }
        }

        // 2. 2-Block High Collision Detection (Player enters anywhere in the 2-block column)
        AABB columnBox = new AABB(
                pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0, pos.getY() + 2.0, pos.getZ() + 1.0
        );
        for (ServerPlayer player : serverLevel.getEntitiesOfClass(ServerPlayer.class, columnBox)) {
            if (player.isSpectator()) continue;
            Identifier targetId = Identifier.tryParse(blockEntity.getTargetDimension());
            if (targetId == null) continue;
            ResourceKey<Level> targetDim = ResourceKey.create(Registries.DIMENSION, targetId);
            TeleportHelper.teleportPlayer(
                    player,
                    blockEntity.getTargetX(),
                    blockEntity.getTargetY(),
                    blockEntity.getTargetZ(),
                    blockEntity.getTargetYaw(),
                    blockEntity.getTargetPitch(),
                    targetDim,
                    blockEntity.getColorHex()
            );
        }

        // 3. Dynamic 2-Block High Double-Helix & Cosmic Vortex Particles
        DustParticleOptions dust = TeleportHelper.createDustParticle(blockEntity.colorHex, 1.0f);
        double centerX = pos.getX() + 0.5;
        double centerZ = pos.getZ() + 0.5;
        double baseY = pos.getY() + 0.05;

        // Helix strands climbing from 0.05 to 2.05
        double rotSpeed = 0.20;
        double baseAngle = blockEntity.tickCount * rotSpeed;
        double radius = 0.42;

        for (int i = 0; i < 4; i++) {
            double progress = ((blockEntity.tickCount * 0.04 + (i * 0.25)) % 1.0);
            double py = baseY + progress * 2.0;

            // Strand 1
            double angleA = baseAngle + (progress * Math.PI * 4.0);
            double pxA = centerX + Math.cos(angleA) * radius;
            double pzA = centerZ + Math.sin(angleA) * radius;
            serverLevel.sendParticles(dust, pxA, py, pzA, 1, 0, 0, 0, 0);

            // Strand 2 (opposite 180 degrees)
            double angleB = angleA + Math.PI;
            double pxB = centerX + Math.cos(angleB) * radius;
            double pzB = centerZ + Math.sin(angleB) * radius;
            serverLevel.sendParticles(dust, pxB, py, pzB, 1, 0, 0, 0, 0);
        }

        // 4. Shimmering core portal sparks & End Rod motes
        if (blockEntity.tickCount % 3 == 0) {
            double ex = centerX + (serverLevel.getRandom().nextDouble() - 0.5) * 0.3;
            double ey = baseY + 0.1;
            double ez = centerZ + (serverLevel.getRandom().nextDouble() - 0.5) * 0.3;
            serverLevel.sendParticles(ParticleTypes.END_ROD, ex, ey, ez, 1, 0, 0.08, 0, 0.01);

            double pox = centerX + (serverLevel.getRandom().nextDouble() - 0.5) * 0.8;
            double poy = baseY + serverLevel.getRandom().nextDouble() * 1.8;
            double poz = centerZ + (serverLevel.getRandom().nextDouble() - 0.5) * 0.8;
            serverLevel.sendParticles(ParticleTypes.PORTAL, pox, poy, poz, 1,
                    (centerX - pox), 0.1, (centerZ - poz), 0.5);
        }

        // 5. Floor Pad & Crown Rings every 10 ticks
        if (blockEntity.tickCount % 10 == 0) {
            int ringSegments = 8;
            for (int r = 0; r < ringSegments; r++) {
                double a = (Math.PI * 2.0 / ringSegments) * r;
                double rx = centerX + Math.cos(a) * 0.45;
                double rz = centerZ + Math.sin(a) * 0.45;
                serverLevel.sendParticles(dust, rx, baseY, rz, 1, 0, 0, 0, 0);
                serverLevel.sendParticles(dust, rx, baseY + 2.0, rz, 1, 0, 0, 0, 0);
            }
        }
    }

    public void onRemoved(ServerLevel level) {
        if (this.textDisplayUuid != null) {
            HologramHelper.removeHologram(level, this.textDisplayUuid);
            this.textDisplayUuid = null;
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putDouble("targetX", targetX);
        output.putDouble("targetY", targetY);
        output.putDouble("targetZ", targetZ);
        output.putFloat("targetYaw", targetYaw);
        output.putFloat("targetPitch", targetPitch);
        output.putString("targetDimension", targetDimension);
        output.putString("name", name);
        output.putInt("colorHex", colorHex);
        if (textDisplayUuid != null) {
            output.putString("textDisplayUuid", textDisplayUuid.toString());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.targetX = input.getDoubleOr("targetX", this.worldPosition.getX() + 0.5);
        this.targetY = input.getDoubleOr("targetY", this.worldPosition.getY());
        this.targetZ = input.getDoubleOr("targetZ", this.worldPosition.getZ() + 0.5);
        this.targetYaw = input.getFloatOr("targetYaw", 0.0f);
        this.targetPitch = input.getFloatOr("targetPitch", 0.0f);
        this.targetDimension = input.getStringOr("targetDimension", "minecraft:overworld");
        this.name = input.getStringOr("name", "");
        this.colorHex = input.getIntOr("colorHex", 0x8A2BE2);
        String uuidStr = input.getStringOr("textDisplayUuid", "");
        this.textDisplayUuid = !uuidStr.isEmpty() ? UUID.fromString(uuidStr) : null;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        tag.putDouble("targetX", targetX);
        tag.putDouble("targetY", targetY);
        tag.putDouble("targetZ", targetZ);
        tag.putFloat("targetYaw", targetYaw);
        tag.putFloat("targetPitch", targetPitch);
        tag.putString("targetDimension", targetDimension);
        tag.putString("name", name);
        tag.putInt("colorHex", colorHex);
        return tag;
    }

    // Getters and Setters
    public double getTargetX() { return targetX; }
    public void setTargetX(double targetX) { this.targetX = targetX; setChanged(); }
    public double getTargetY() { return targetY; }
    public void setTargetY(double targetY) { this.targetY = targetY; setChanged(); }
    public double getTargetZ() { return targetZ; }
    public void setTargetZ(double targetZ) { this.targetZ = targetZ; setChanged(); }
    public float getTargetYaw() { return targetYaw; }
    public void setTargetYaw(float targetYaw) { this.targetYaw = targetYaw; setChanged(); }
    public float getTargetPitch() { return targetPitch; }
    public void setTargetPitch(float targetPitch) { this.targetPitch = targetPitch; setChanged(); }
    public String getTargetDimension() { return targetDimension; }
    public void setTargetDimension(String targetDimension) { this.targetDimension = targetDimension; setChanged(); }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; setChanged(); }
    public int getColorHex() { return colorHex; }
    public void setColorHex(int colorHex) { this.colorHex = colorHex; setChanged(); }
}
