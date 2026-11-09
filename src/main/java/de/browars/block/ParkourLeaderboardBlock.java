package de.browars.block;

import de.browars.BroWarsMod;
import de.browars.block.entity.ParkourLeaderboardBlockEntity;
import de.browars.network.OpenParkourLeaderboardPayload;
import de.browars.parkour.ParkourSavedData;
import de.browars.parkour.ParkourScore;
import de.browars.util.PermissionsHelper;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.BlockGetter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ParkourLeaderboardBlock extends BaseEntityBlock {
    public ParkourLeaderboardBlock(Properties properties) {
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
        return new ParkourLeaderboardBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return createTickerHelper(blockEntityType, BroWarsMod.PARKOUR_LEADERBOARD_BLOCK_ENTITY, ParkourLeaderboardBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            if (PermissionsHelper.isOpOrCreative(serverPlayer)) {
                openLeaderboardGui(serverPlayer, pos);
                return InteractionResult.SUCCESS;
            } else {
                serverPlayer.sendSystemMessage(Component.literal("§c[BroWars] Das Parkour-Menü ist nur für Administratoren verfügbar."), true);
                return InteractionResult.CONSUME;
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
            ParkourSavedData savedData = ParkourSavedData.get(serverLevel);
            savedData.getData().addLeaderboardBlock(pos);
            ParkourSavedData.save(serverLevel);

            BlockEntity be = serverLevel.getBlockEntity(pos);
            if (be instanceof ParkourLeaderboardBlockEntity leaderboardBe) {
                leaderboardBe.updateHologram(serverLevel);
            }
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
            BlockEntity be = serverLevel.getBlockEntity(pos);
            if (be instanceof ParkourLeaderboardBlockEntity leaderboardBe) {
                leaderboardBe.onRemoved(serverLevel);
            }
            ParkourSavedData savedData = ParkourSavedData.get(serverLevel);
            savedData.getData().removeLeaderboardBlock(pos);
            ParkourSavedData.save(serverLevel);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    public static void openLeaderboardGui(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.level();
        ParkourSavedData savedData = ParkourSavedData.get(level);
        List<ParkourScore> scores = savedData.getData().getScores();

        List<OpenParkourLeaderboardPayload.Entry> entries = new ArrayList<>();
        for (int i = 0; i < scores.size(); i++) {
            ParkourScore s = scores.get(i);
            entries.add(new OpenParkourLeaderboardPayload.Entry(i, s.playerName(), ParkourScore.formatTime(s.timeMillis()), s.timestamp()));
        }

        ServerPlayNetworking.send(player, new OpenParkourLeaderboardPayload(pos, entries));
    }
}
