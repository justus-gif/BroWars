package de.browars.minigame.spleef;

import de.browars.minigame.AbstractDuelGame;
import de.browars.minigame.MinigamePlayerState;
import de.browars.minigame.TeamColor;
import net.minecraft.advancements.predicates.BlockPredicate;
import net.minecraft.advancements.predicates.DataComponentMatchers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.AdventureModePredicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

public final class SpleefGame extends AbstractDuelGame<SpleefArenaData> {
    public static final int TARGET_SCORE = 7;
    public static final int SHOVEL_EFFICIENCY = 5;

    private static final AdventureModePredicate CAN_BREAK_SNOW = new AdventureModePredicate(List.of(
            new BlockPredicate(
                    Optional.of(HolderSet.direct(
                            Blocks.SNOW_BLOCK.builtInRegistryHolder(),
                            Blocks.SNOW.builtInRegistryHolder(),
                            Blocks.POWDER_SNOW.builtInRegistryHolder()
                    )),
                    Optional.empty(),
                    Optional.empty(),
                    DataComponentMatchers.ANY
            )
    ));

    private final Set<BlockPos> snowPositions = new HashSet<>();

    public SpleefGame(MinecraftServer server, ServerLevel level, SpleefArenaData arena,
                      ServerPlayer first, ServerPlayer second, TeamColor firstTeam, TeamColor secondTeam) {
        super(server, level, arena, first, second, firstTeam, secondTeam);
    }

    @Override public String getId() { return "spleef"; }
    @Override public String getDisplayName() { return "Spleef"; }
    @Override protected String startInstruction() { return ""; }
    @Override protected int targetScore() { return TARGET_SCORE; }

    @Override
    protected int phaseNumber() {
        if (playTicks < 1200) return 1;
        if (playTicks < 2400) return 2;
        return 3;
    }

    @Override
    protected String phaseTitle() {
        return switch (phaseNumber()) {
            case 2 -> "Eskalation";
            case 3 -> "Chaos";
            default -> "Aufwärmen";
        };
    }

    public static int fillSnowArea(ServerLevel level, SpleefArenaData.ArenaBox box) {
        Set<BlockPos> positions = resolveSnowPositionsForBox(level, box);
        for (BlockPos pos : positions) {
            if (!level.getBlockState(pos).is(Blocks.SNOW_BLOCK)) {
                level.setBlock(pos, Blocks.SNOW_BLOCK.defaultBlockState(), 3);
            }
        }
        return positions.size();
    }

    private static boolean isSnowState(BlockState state) {
        return state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.SNOW) || state.is(Blocks.POWDER_SNOW);
    }

    private static Set<BlockPos> resolveSnowPositionsForBox(ServerLevel level, SpleefArenaData.ArenaBox box) {
        Set<BlockPos> result = new HashSet<>();
        if (box == null) return result;

        boolean hasWalls = false;
        boolean hasSnow = false;
        for (BlockPos pos : BlockPos.betweenClosed(box.min(), box.max())) {
            BlockState state = level.getBlockState(pos);
            if (isSnowState(state)) {
                hasSnow = true;
                result.add(pos.immutable());
            } else if (!state.isAir()) {
                hasWalls = true;
            }
        }

        if (!hasWalls) {
            for (BlockPos pos : BlockPos.betweenClosed(box.min(), box.max())) {
                result.add(pos.immutable());
            }
            return result;
        }

        if (!hasSnow) {
            for (BlockPos pos : BlockPos.betweenClosed(box.min(), box.max())) {
                if (level.getBlockState(pos).isAir()) {
                    result.add(pos.immutable());
                }
            }
            return result;
        }

        // Flood-fill from existing snow across interior air holes (bounded by walls)
        Queue<BlockPos> queue = new ArrayDeque<>(result);
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            for (int[] d : dirs) {
                BlockPos next = current.offset(d[0], 0, d[1]);
                if (!box.contains(next) || result.contains(next)) continue;
                BlockState nextState = level.getBlockState(next);
                if (nextState.isAir() || isSnowState(nextState)) {
                    BlockPos imm = next.immutable();
                    result.add(imm);
                    queue.add(imm);
                }
            }
        }
        return result;
    }

    @Override
    protected void prepareArena() {
        if (snowPositions.isEmpty()) {
            for (SpleefArenaData.ArenaBox box : arena.getSnowAreas()) {
                snowPositions.addAll(resolveSnowPositionsForBox(level, box));
            }
        }
        resetSnowLayers();
    }

    private void resetSnowLayers() {
        for (BlockPos pos : snowPositions) {
            if (!level.getBlockState(pos).is(Blocks.SNOW_BLOCK)) {
                level.setBlock(pos, Blocks.SNOW_BLOCK.defaultBlockState(), 3);
            }
        }
    }

    @Override
    protected void clearRound() {
        removeDroppedEquipment();
    }

    @Override
    protected void restoreArena() {
        removeDroppedEquipment();
        resetSnowLayers();
        snowPositions.clear();
    }

    public boolean canBreakBlock(BlockPos pos, BlockState state) {
        return isRoundActive() && isSnowState(state) && arena.isInAnySnowArea(pos);
    }

    public void onSnowBlockBroken(BlockPos pos) {
        if (pos != null) {
            snowPositions.add(pos.immutable());
            level.sendParticles(ParticleTypes.SNOWFLAKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8, 0.25, 0.25, 0.25, 0.05);
            level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, net.minecraft.sounds.SoundEvents.SNOW_BREAK, net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 1.1f);
        }
    }

    @Override
    protected void tickRound(ServerPlayer first, ServerPlayer second) {
        boolean firstDead = arena.isPlayerInKillArea(first);
        boolean secondDead = second != null && arena.isPlayerInKillArea(second);
        if (finishRound(firstDead, secondDead, "In die Tiefe gefallen!")) {
            return;
        }
        if (playTicks % 20 == 0) {
            removeDroppedEquipment();
        }
    }

    @Override
    protected void equipExtra(ServerPlayer player) {
        boolean active = isRoundActive();
        ItemStack held = player.getMainHandItem();
        if (held.is(Items.DIAMOND_SHOVEL) && MinigamePlayerState.isGameItem(held)) {
            boolean hasCanBreak = held.has(DataComponents.CAN_BREAK);
            if (active && !hasCanBreak) {
                held.set(DataComponents.CAN_BREAK, CAN_BREAK_SNOW);
                player.inventoryMenu.broadcastChanges();
            } else if (!active && hasCanBreak) {
                held.remove(DataComponents.CAN_BREAK);
                player.inventoryMenu.broadcastChanges();
            }
            return;
        }

        ItemStack shovel = createSpleefShovel(level, active);
        player.setItemSlot(EquipmentSlot.MAINHAND, shovel);
    }

    public static ItemStack createSpleefShovel(ServerLevel level, boolean allowBreakingSnow) {
        ItemStack shovel = new ItemStack(Items.DIAMOND_SHOVEL);
        shovel.set(DataComponents.CUSTOM_NAME, Component.literal("§b§lSpleef-Schaufel"));
        shovel.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        if (allowBreakingSnow) {
            shovel.set(DataComponents.CAN_BREAK, CAN_BREAK_SNOW);
        }
        if (level != null) {
            level.registryAccess().lookup(Registries.ENCHANTMENT)
                    .flatMap(r -> r.get(Enchantments.EFFICIENCY))
                    .ifPresent(enchantment -> shovel.enchant(enchantment, SHOVEL_EFFICIENCY));
        }
        MinigamePlayerState.markGameItem(shovel);
        return shovel;
    }

    private void removeDroppedEquipment() {
        BlockPos min = arena.getArenaMin();
        BlockPos max = arena.getArenaMax();
        if (min == null || max == null) return;
        AABB bounds = new AABB(min).minmax(new AABB(max)).inflate(24);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, bounds)) {
            if (MinigamePlayerState.isGameItem(item.getItem()) || item.getItem().is(Items.SNOWBALL)) {
                item.discard();
            }
        }
    }
}
