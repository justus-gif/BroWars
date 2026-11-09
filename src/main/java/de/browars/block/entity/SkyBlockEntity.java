package de.browars.block.entity;

import de.browars.BroWarsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class SkyBlockEntity extends BlockEntity {
    private static final Direction[] DIRECTIONS = Direction.values();
    // Bitmask: Bit 0 = DOWN, 1 = UP, 2 = NORTH, 3 = SOUTH, 4 = WEST, 5 = EAST
    // -1 = dirty / not computed
    private byte cachedVisibleFaces = -1;

    public SkyBlockEntity(BlockPos pos, BlockState state) {
        super(BroWarsMod.SKY_BLOCK_ENTITY, pos, state);
    }

    public void clearFaceCache() {
        this.cachedVisibleFaces = -1;
    }

    public byte getVisibleFaces() {
        if (this.cachedVisibleFaces == -1) {
            updateVisibleFaces();
        }
        return this.cachedVisibleFaces;
    }

    private void updateVisibleFaces() {
        if (this.level == null) {
            this.cachedVisibleFaces = 0x3F; // All 6 faces visible by default
            return;
        }
        byte mask = 0;
        BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 6; i++) {
            mut.setWithOffset(this.worldPosition, DIRECTIONS[i]);
            BlockState neighbor = this.level.getBlockState(mut);
            if (!neighbor.is(BroWarsMod.SKY_BLOCK)) {
                mask |= (byte) (1 << i);
            }
        }
        this.cachedVisibleFaces = mask;
    }
}
