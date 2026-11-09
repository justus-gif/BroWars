package de.browars.block.entity;

import de.browars.BroWarsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;

public class SkyBlockEntity extends BlockEntity {
    private static final Direction[] DIRECTIONS = Direction.values();
    private final BlockPos.MutableBlockPos scratchPos = new BlockPos.MutableBlockPos();
    private byte lastComputedMask = 0x3F;

    public SkyBlockEntity(BlockPos pos, BlockState state) {
        super(BroWarsMod.SKY_BLOCK_ENTITY, pos, state);
    }

    public byte getLastVisibleMask() {
        return this.lastComputedMask;
    }

    public void setLastVisibleMask(byte mask) {
        this.lastComputedMask = mask;
    }

    /**
     * Dynamically computes the visible faces of this SkyBlock for the current frame.
     * Evaluated on demand each frame so changes (placed/broken blocks, chunk loads)
     * are reflected immediately with zero stale cache bugs.
     */
    public byte computeVisibleFaces() {
        if (this.level == null) {
            return 0x3F;
        }
        byte mask = 0;
        BlockPos pos = this.worldPosition;
        for (int i = 0; i < 6; i++) {
            Direction dir = DIRECTIONS[i];
            this.scratchPos.set(pos.getX() + dir.getStepX(), pos.getY() + dir.getStepY(), pos.getZ() + dir.getStepZ());
            BlockState neighbor = this.level.getBlockState(this.scratchPos);

            // 1. Skip internal faces between adjacent SkyBlocks so multi-block structures connect seamlessly
            if (neighbor.is(BroWarsMod.SKY_BLOCK)) {
                continue;
            }

            // 2. Skip faces touching solid full blocks that completely occlude this face
            // This prevents Z-fighting and flickering seams with adjacent terrain
            if (neighbor.getFaceOcclusionShape(dir.getOpposite()) == Shapes.block()) {
                continue;
            }

            mask |= (byte) (1 << i);
        }
        this.lastComputedMask = mask;
        return mask;
    }
}

