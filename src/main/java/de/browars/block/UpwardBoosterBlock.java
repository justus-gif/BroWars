package de.browars.block;

import de.browars.block.entity.UpwardBoosterBlockEntity;
import de.browars.network.OpenUpwardBoosterPayload;
import de.browars.util.PermissionsHelper;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class UpwardBoosterBlock extends BaseEntityBlock {
    private static final Map<UUID, Long> LAST_BOOST = new ConcurrentHashMap<>();

    public UpwardBoosterBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new UpwardBoosterBlockEntity(pos, state);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        triggerBoost(level, pos, state, entity);
        super.stepOn(level, pos, state, entity);
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier applier, boolean inside) {
        triggerBoost(level, pos, state, entity);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            if (PermissionsHelper.isOpOrCreative(serverPlayer)) {
                openGui(serverPlayer, pos);
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && placer instanceof ServerPlayer serverPlayer) {
            if (PermissionsHelper.isOpOrCreative(serverPlayer)) {
                openGui(serverPlayer, pos);
            }
        }
    }

    private void openGui(ServerPlayer player, BlockPos pos) {
        BlockEntity be = player.level().getBlockEntity(pos);
        if (be instanceof UpwardBoosterBlockEntity pad) {
            ServerPlayNetworking.send(player, new OpenUpwardBoosterPayload(pos, pad.getHeight()));
        }
    }

    private void triggerBoost(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (level.isClientSide()) return;

        long now = System.currentTimeMillis();
        long last = LAST_BOOST.getOrDefault(entity.getUUID(), 0L);
        if (now - last < 350) return;
        LAST_BOOST.put(entity.getUUID(), now);

        double height = 5.0;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof UpwardBoosterBlockEntity pad) {
            height = pad.getHeight();
        }

        // Calculate upward velocity from target height
        double vy = Math.sqrt(2.0 * 0.115 * height);
        Vec3 cur = entity.getDeltaMovement();
        Vec3 boostVec = new Vec3(cur.x * 0.25, vy, cur.z * 0.25);

        entity.setDeltaMovement(boostVec);
        entity.fallDistance = 0;

        if (entity instanceof ServerPlayer player) {
            player.connection.send(new ClientboundSetEntityMotionPacket(player.getId(), boostVec));
        }

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, pos, SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.BLOCKS, 0.9f, 1.25f);
            serverLevel.playSound(null, pos, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.BLOCKS, 0.6f, 1.4f);

            double cx = pos.getX() + 0.5;
            double cy = pos.getY() + 1.05;
            double cz = pos.getZ() + 0.5;

            serverLevel.sendParticles(ParticleTypes.GUST, cx, cy, cz, 2, 0.15, 0.1, 0.15, 0.05);
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, cx, cy, cz, 12, 0.25, 0.1, 0.25, 0.15);
            serverLevel.sendParticles(ParticleTypes.CLOUD, cx, cy, cz, 4, 0.2, 0.05, 0.2, 0.02);
        }
    }
}
