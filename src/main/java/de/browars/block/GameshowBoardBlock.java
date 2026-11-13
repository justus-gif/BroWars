package de.browars.block;

import de.browars.BroWarsMod;
import de.browars.block.entity.GameshowBoardBlockEntity;
import de.browars.gameshow.GameshowData;
import de.browars.gameshow.GameshowSavedData;
import de.browars.util.PermissionsHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class GameshowBoardBlock extends BaseEntityBlock {
    public GameshowBoardBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GameshowBoardBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return createTickerHelper(blockEntityType, BroWarsMod.GAMESHOW_BOARD_BLOCK_ENTITY, GameshowBoardBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
            if (PermissionsHelper.isOpOrCreative(serverPlayer) && serverPlayer.isShiftKeyDown()) {
                GameshowSavedData saved = GameshowSavedData.get(serverLevel);
                saved.getData().setLastShow(GameshowData.ShowRecord.EMPTY);
                GameshowSavedData.save(serverLevel);
                BlockEntity be = serverLevel.getBlockEntity(pos);
                if (be instanceof GameshowBoardBlockEntity boardBe) {
                    boardBe.updateHologram(serverLevel);
                }
                serverPlayer.sendSystemMessage(Component.literal("§a[Game Show] Board-Anzeige zurückgesetzt."), true);
                return InteractionResult.SUCCESS;
            }
            BlockEntity be = serverLevel.getBlockEntity(pos);
            if (be instanceof GameshowBoardBlockEntity boardBe) {
                boardBe.updateHologram(serverLevel);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
            GameshowSavedData savedData = GameshowSavedData.get(serverLevel);
            savedData.getData().addBoardBlock(pos);
            GameshowSavedData.save(serverLevel);

            BlockEntity be = serverLevel.getBlockEntity(pos);
            if (be instanceof GameshowBoardBlockEntity boardBe) {
                boardBe.updateHologram(serverLevel);
            }
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
            BlockEntity be = serverLevel.getBlockEntity(pos);
            if (be instanceof GameshowBoardBlockEntity boardBe) {
                boardBe.onRemoved(serverLevel);
            }
            GameshowSavedData savedData = GameshowSavedData.get(serverLevel);
            savedData.getData().removeBoardBlock(pos);
            GameshowSavedData.save(serverLevel);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
