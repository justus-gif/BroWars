package de.browars.block.entity;

import de.browars.BroWarsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class SkyBlockEntity extends BlockEntity {
    public SkyBlockEntity(BlockPos pos, BlockState state) {
        super(BroWarsMod.SKY_BLOCK_ENTITY, pos, state);
    }

    public boolean shouldRenderFace(Direction direction) {
        if (this.level == null) return true;
        BlockPos neighborPos = this.worldPosition.relative(direction);
        BlockState neighborState = this.level.getBlockState(neighborPos);
        return !neighborState.is(BroWarsMod.SKY_BLOCK);
    }
}

