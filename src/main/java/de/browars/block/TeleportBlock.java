package de.browars.block;

import de.browars.BroWarsMod;
import de.browars.block.entity.TeleportBlockEntity;
import de.browars.network.OpenTeleportBlockPayload;
import de.browars.util.TeleportHelper;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
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

public class TeleportBlock extends BaseEntityBlock {
    public TeleportBlock(Properties properties) {
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

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
                             InsideBlockEffectApplier applier, boolean inside) {
        if (!level.isClientSide() && entity instanceof ServerPlayer player) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TeleportBlockEntity teleporter) {
                Identifier dimId = Identifier.tryParse(teleporter.getTargetDimension());
                if (dimId == null) return;
                ResourceKey<Level> targetDim = ResourceKey.create(Registries.DIMENSION, dimId);
                TeleportHelper.teleportPlayer(
                        player,
                        teleporter.getTargetX(),
                        teleporter.getTargetY(),
                        teleporter.getTargetZ(),
                        teleporter.getTargetYaw(),
                        teleporter.getTargetPitch(),
                        targetDim,
                        teleporter.getColorHex()
                );
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            if (de.browars.util.PermissionsHelper.isOpOrCreative(serverPlayer)) {
                openTeleportGui(serverPlayer, pos);
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && placer instanceof ServerPlayer serverPlayer) {
            if (de.browars.util.PermissionsHelper.isOpOrCreative(serverPlayer)) {
                openTeleportGui(serverPlayer, pos);
            }
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!level.isClientSide() && be instanceof TeleportBlockEntity teleporter) {
            teleporter.onRemoved((net.minecraft.server.level.ServerLevel) level);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    private void openTeleportGui(ServerPlayer player, BlockPos pos) {
        BlockEntity be = player.level().getBlockEntity(pos);
        if (be instanceof TeleportBlockEntity teleporter) {
            OpenTeleportBlockPayload payload = new OpenTeleportBlockPayload(
                    pos,
                    teleporter.getTargetX(),
                    teleporter.getTargetY(),
                    teleporter.getTargetZ(),
                    teleporter.getTargetYaw(),
                    teleporter.getTargetPitch(),
                    teleporter.getTargetDimension(),
                    teleporter.getName(),
                    teleporter.getColorHex()
            );
            ServerPlayNetworking.send(player, payload);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TeleportBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return createTickerHelper(blockEntityType, BroWarsMod.TELEPORT_BLOCK_ENTITY, TeleportBlockEntity::serverTick);
    }
}
