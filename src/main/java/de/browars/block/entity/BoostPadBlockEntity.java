package de.browars.block.entity;

import de.browars.BroWarsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class BoostPadBlockEntity extends BlockEntity {
    private double strength = 2.0;
    private int colorHex = 0x00FFFF;
    private boolean lookDirectionMode = true;

    public BoostPadBlockEntity(BlockPos pos, BlockState state) {
        super(BroWarsMod.BOOST_PAD_BLOCK_ENTITY, pos, state);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putDouble("strength", strength);
        output.putInt("colorHex", colorHex);
        output.putBoolean("lookDirectionMode", lookDirectionMode);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.strength = de.browars.util.InputValidation.boostStrength(input.getDoubleOr("strength", 2.0));
        this.colorHex = input.getIntOr("colorHex", 0x00FFFF);
        this.lookDirectionMode = input.getBooleanOr("lookDirectionMode", true);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        tag.putDouble("strength", strength);
        tag.putInt("colorHex", colorHex);
        tag.putBoolean("lookDirectionMode", lookDirectionMode);
        return tag;
    }

    public double getStrength() { return strength; }
    public void setStrength(double strength) { this.strength = de.browars.util.InputValidation.boostStrength(strength); setChanged(); }

    public int getColorHex() { return colorHex; }
    public void setColorHex(int colorHex) { this.colorHex = colorHex; setChanged(); }

    public boolean isLookDirectionMode() { return lookDirectionMode; }
    public void setLookDirectionMode(boolean lookDirectionMode) { this.lookDirectionMode = lookDirectionMode; setChanged(); }
}
