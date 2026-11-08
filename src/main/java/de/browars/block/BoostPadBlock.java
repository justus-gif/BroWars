package de.browars.block;

import de.browars.block.entity.BoostPadBlockEntity;
import de.browars.network.OpenBoostPadPayload;
import de.browars.util.TeleportHelper;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
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
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BoostPadBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    protected static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 2.0, 16.0);
    private static final Map<UUID, Long> LAST_BOOST = new ConcurrentHashMap<>();

    public BoostPadBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BoostPadBlockEntity(pos, state);
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
            if (de.browars.util.PermissionsHelper.isOpOrCreative(serverPlayer)) {
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
            if (de.browars.util.PermissionsHelper.isOpOrCreative(serverPlayer)) {
                openGui(serverPlayer, pos);
            }
        }
    }

    private void openGui(ServerPlayer player, BlockPos pos) {
        BlockEntity be = player.level().getBlockEntity(pos);
        if (be instanceof BoostPadBlockEntity pad) {
            ServerPlayNetworking.send(player, new OpenBoostPadPayload(
                    pos,
                    pad.getStrength(),
                    pad.getColorHex(),
                    pad.isLookDirectionMode()
            ));
        }
    }

    private void triggerBoost(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (level.isClientSide()) return;

        long now = System.currentTimeMillis();
        long last = LAST_BOOST.getOrDefault(entity.getUUID(), 0L);
        if (now - last < 350) return; // 350ms cooldown
        LAST_BOOST.put(entity.getUUID(), now);

        double strength = 2.0;
        int colorHex = 0x00FFFF;
        boolean lookMode = true;

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof BoostPadBlockEntity pad) {
            strength = pad.getStrength();
            colorHex = pad.getColorHex();
            lookMode = pad.isLookDirectionMode();
        }

        Vec3 boostVec;
        if (lookMode) {
            // "es soll dahin boosten wo man schaut"
            Vec3 look = entity.getLookAngle();
            double vx = look.x * strength;
            double vy = Math.max(0.28, look.y * strength); // Mindest-Aufwärtsschwung für flüssiges Gleiten
            double vz = look.z * strength;
            boostVec = new Vec3(vx, vy, vz);
        } else {
            // Fester Richtungsboost des Pads
            Direction facing = state.getValue(FACING);
            boostVec = new Vec3(facing.getStepX() * strength, 0.35, facing.getStepZ() * strength);
        }

        entity.setDeltaMovement(boostVec);

        if (entity instanceof ServerPlayer player) {
            player.connection.send(new ClientboundSetEntityMotionPacket(player.getId(), boostVec));
        }

        if (level instanceof ServerLevel serverLevel) {
            // Sounds
            serverLevel.playSound(null, pos, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.BLOCKS, 0.85f, 1.35f);
            if (SoundEvents.WIND_CHARGE_BURST != null) {
                serverLevel.playSound(null, pos, SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.BLOCKS, 0.9f, 1.2f);
            }

            // Particles in custom Zweitfarbe
            DustParticleOptions dust = TeleportHelper.createDustParticle(colorHex, 1.3f);
            double cx = pos.getX() + 0.5;
            double cy = pos.getY() + 0.15;
            double cz = pos.getZ() + 0.5;

            serverLevel.sendParticles(dust, cx, cy, cz, 18, 0.3, 0.1, 0.3, 0.1);
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, cx, cy, cz, 12, 0.2, 0.1, 0.2, 0.15);
            serverLevel.sendParticles(ParticleTypes.GUST, cx, cy, cz, 1, 0, 0, 0, 0);
        }
    }
}
