package de.browars.block.entity;

import de.browars.BroWarsMod;
import de.browars.util.HologramHelper;
import de.browars.util.TeleportHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.UUID;

public class TeleportTargetBlockEntity extends BlockEntity {
    private String name = "";
    private int colorHex = 0x00FF88; // Bright emerald green by default
    private UUID textDisplayUuid = null;
    private long tickCount = 0;

    public TeleportTargetBlockEntity(BlockPos pos, BlockState state) {
        super(BroWarsMod.TELEPORT_TARGET_BLOCK_ENTITY, pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TeleportTargetBlockEntity blockEntity) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        blockEntity.tickCount++;

        // 1. Hologram update (y + 1.2 above indicator)
        if (blockEntity.tickCount % 20 == 0) {
            double holoX = pos.getX() + 0.5;
            double holoY = pos.getY() + 1.25;
            double holoZ = pos.getZ() + 0.5;

            if (!blockEntity.name.isEmpty()) {
                String label = "§a🎯 " + blockEntity.name;
                UUID newUuid = HologramHelper.updateOrCreateHologram(
                        serverLevel,
                        blockEntity.textDisplayUuid,
                        holoX, holoY, holoZ,
                        label,
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

        // 2. Target particles (0.5 to 1.5 blocks high)
        DustParticleOptions dust = TeleportHelper.createDustParticle(blockEntity.colorHex, 1.0f);
        double centerX = pos.getX() + 0.5;
        double centerZ = pos.getZ() + 0.5;
        double baseY = pos.getY() + 0.1;

        // Ground landing ring
        double rotSpeed = 0.15;
        double baseAngle = blockEntity.tickCount * rotSpeed;
        double radius = 0.45;

        for (int i = 0; i < 3; i++) {
            double progress = ((blockEntity.tickCount * 0.05 + (i * 0.33)) % 1.0);
            double py = baseY + progress * 1.5;

            double angle = baseAngle + (progress * Math.PI * 3.0);
            double px = centerX + Math.cos(angle) * radius * (1.0 - progress * 0.3);
            double pz = centerZ + Math.sin(angle) * radius * (1.0 - progress * 0.3);
            serverLevel.sendParticles(dust, px, py, pz, 1, 0, 0, 0, 0);
        }

        // Pulse ring every 8 ticks
        if (blockEntity.tickCount % 8 == 0) {
            int ringSegments = 6;
            for (int r = 0; r < ringSegments; r++) {
                double a = (Math.PI * 2.0 / ringSegments) * r;
                double rx = centerX + Math.cos(a) * 0.4;
                double rz = centerZ + Math.sin(a) * 0.4;
                serverLevel.sendParticles(dust, rx, baseY, rz, 1, 0, 0, 0, 0);
            }
        }

        // End rod sparkle in center
        if (blockEntity.tickCount % 6 == 0) {
            serverLevel.sendParticles(ParticleTypes.END_ROD, centerX, baseY + 0.2, centerZ, 1, 0, 0.05, 0, 0.01);
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
        output.putString("name", name);
        output.putInt("colorHex", colorHex);
        if (textDisplayUuid != null) {
            output.putString("textDisplayUuid", textDisplayUuid.toString());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.name = input.getStringOr("name", "");
        this.colorHex = input.getIntOr("colorHex", 0x00FF88);
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
        tag.putString("name", name);
        tag.putInt("colorHex", colorHex);
        return tag;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; setChanged(); }

    public int getColorHex() { return colorHex; }
    public void setColorHex(int colorHex) { this.colorHex = colorHex; setChanged(); }
}
