package de.browars.block.entity;

import de.browars.BroWarsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Consumer;

public class SkyBlockEntity extends BlockEntity {
    public static Consumer<SkyBlockEntity> clientOnAdd = null;
    public static Consumer<SkyBlockEntity> clientOnRemove = null;

    public boolean clientRegistered = false;

    public SkyBlockEntity(BlockPos pos, BlockState state) {
        super(BroWarsMod.SKY_BLOCK_ENTITY, pos, state);
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (level != null && level.isClientSide() && clientOnAdd != null) {
            clientOnAdd.accept(this);
        }
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        if (this.level != null && this.level.isClientSide() && clientOnAdd != null) {
            clientOnAdd.accept(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (clientOnRemove != null) {
            clientOnRemove.accept(this);
        }
    }

    public boolean shouldRenderFace(Direction direction) {
        if (this.level == null) return true;
        BlockPos neighborPos = this.worldPosition.relative(direction);
        BlockState neighborState = this.level.getBlockState(neighborPos);
        return !neighborState.is(BroWarsMod.SKY_BLOCK);
    }
}

