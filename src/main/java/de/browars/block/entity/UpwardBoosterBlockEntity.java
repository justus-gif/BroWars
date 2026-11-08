package de.browars.block.entity;

import de.browars.BroWarsMod;
import de.browars.util.InputValidation;
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

public class UpwardBoosterBlockEntity extends BlockEntity {
    private double height = 5.0;

    public UpwardBoosterBlockEntity(BlockPos pos, BlockState state) {
        super(BroWarsMod.UPWARD_BOOSTER_BLOCK_ENTITY, pos, state);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putDouble("height", height);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.height = InputValidation.upwardHeight(input.getDoubleOr("height", 5.0));
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        tag.putDouble("height", height);
        return tag;
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = InputValidation.upwardHeight(height);
        setChanged();
    }
}
