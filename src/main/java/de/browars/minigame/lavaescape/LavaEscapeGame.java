package de.browars.minigame.lavaescape;

import de.browars.minigame.AbstractDuelGame;
import de.browars.minigame.MinigamePlayerState;
import de.browars.minigame.TeamColor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.*;

public class LavaEscapeGame extends AbstractDuelGame<LavaEscapeArenaData> {

    private static final int GRACE_DURATION_TICKS = 100; // 5 seconds grace period
    private static final int RAMP_UP_TICKS = 1200; // 60 seconds ramp up
    private static final double MIN_SPEED = 0.25; // blocks / second
    private static final double MAX_SPEED = 1.50; // blocks / second

    private final Map<BlockPos, BlockState> arenaSnapshot = new HashMap<>();
    private final Set<BlockPos> playerPlacedBlocks = new HashSet<>();
    private final Set<BlockPos> chestPositions = new HashSet<>();

    private int graceTicks = GRACE_DURATION_TICKS;
    private int risingTicks = 0;
    private double currentLavaHeight;
    private int currentLavaLayer = -999;
    private double currentSpeed = MIN_SPEED;

    public LavaEscapeGame(MinecraftServer server, ServerLevel level, LavaEscapeArenaData arena,
                          ServerPlayer first, ServerPlayer second, TeamColor firstTeam, TeamColor secondTeam) {
        super(server, level, arena, first, second, firstTeam, secondTeam);
    }

    @Override
    public String getId() {
        return "lavaescape";
    }

    @Override
    public String getDisplayName() {
        return "Lava Escape";
    }

    @Override
    protected String startInstruction() {
        return "Klettere nach oben und entkomme der tödlichen Lava!";
    }

    @Override
    protected boolean shouldEquipChestplate(ServerPlayer player) {
        return true;
    }

    @Override
    protected boolean allowMultipleGameSlots() {
        return true;
    }

    @Override
    protected boolean shouldHealEveryTick() {
        return false; // Full survival & PvP damage
    }

    private final Set<UUID> equippedPlayers = new HashSet<>();

    @Override
    protected int targetScore() {
        return 2; // Standard Best of 3 (First to 2 points)
    }

    @Override
    protected int phaseNumber() {
        if (phase == Phase.COUNTDOWN) return 1;
        if (graceTicks > 0) return 1;
        return 2;
    }

    @Override
    protected String phaseTitle() {
        if (phase == Phase.COUNTDOWN) return "Start";
        if (graceTicks > 0) return "Schonfrist";
        return "Lava steigt!";
    }

    @Override
    protected int timerRemaining() {
        if (phase == Phase.COUNTDOWN) return phaseTicks;
        if (graceTicks > 0) return graceTicks;
        return -1;
    }

    @Override
    protected int timerTotal() {
        if (phase == Phase.COUNTDOWN) return 100;
        if (graceTicks > 0) return GRACE_DURATION_TICKS;
        return -1;
    }

    public LavaEscapeArenaData getArena() {
        return arena;
    }

    public void trackPlacedBlock(BlockPos pos) {
        BlockPos immutable = pos.immutable();
        if (!playerPlacedBlocks.contains(immutable)) {
            arenaSnapshot.putIfAbsent(immutable, level.getBlockState(immutable));
            playerPlacedBlocks.add(immutable);
        }
    }

    public boolean isPlayerPlacedBlock(BlockPos pos) {
        return playerPlacedBlocks.contains(pos);
    }

    public void removePlayerPlacedBlock(BlockPos pos) {
        playerPlacedBlocks.remove(pos);
    }

    public boolean isChestBlock(BlockPos pos) {
        return chestPositions.contains(pos);
    }

    @Override
    protected void prepareArena() {
        restoreArena();
        disableFireTick();

        currentLavaHeight = arena.getMinPos().getY() - 1;
        currentLavaLayer = arena.getMinPos().getY() - 1;
        graceTicks = GRACE_DURATION_TICKS;
        risingTicks = 0;
        currentSpeed = MIN_SPEED;

        spawnLootChests();
    }

    @Override
    protected void onRoundStarted() {
        graceTicks = GRACE_DURATION_TICKS;
        risingTicks = 0;
        currentLavaHeight = arena.getMinPos().getY() - 1;
        currentLavaLayer = arena.getMinPos().getY() - 1;
        currentSpeed = MIN_SPEED;

        ServerPlayer p1 = server.getPlayerList().getPlayer(p1Uuid);
        if (p1 != null && !p1.isCreative() && !p1.isSpectator()) {
            p1.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        }
        if (p2Uuid != null) {
            ServerPlayer p2 = server.getPlayerList().getPlayer(p2Uuid);
            if (p2 != null && !p2.isCreative() && !p2.isSpectator()) {
                p2.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            }
        }

        message("§6[Lava Escape] Die Schonfrist läuft! In §e5 Sekunden §6beginnt die Lava zu steigen!");
    }

    @Override
    protected void equipExtra(ServerPlayer player) {
        if (!player.isCreative() && !player.isSpectator() && player.gameMode.getGameModeForPlayer() == net.minecraft.world.level.GameType.ADVENTURE) {
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        }

        // Give starter gear EXACTLY ONCE per round: 32 Cobblestone, 1 Wooden Sword, 8 Beef
        if (equippedPlayers.add(player.getUUID())) {
            for (int i = 0; i < 36; i++) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }

            ItemStack sword = new ItemStack(Items.WOODEN_SWORD);
            MinigamePlayerState.markGameItem(sword);
            player.getInventory().setItem(0, sword);

            ItemStack blocks = new ItemStack(Items.COBBLESTONE, 32);
            MinigamePlayerState.markGameItem(blocks);
            player.getInventory().setItem(1, blocks);

            ItemStack food = new ItemStack(Items.COOKED_BEEF, 8);
            MinigamePlayerState.markGameItem(food);
            player.getInventory().setItem(8, food);
        }
    }

    @Override
    protected void tickRound(ServerPlayer first, ServerPlayer second) {
        if (!isRoundActive()) return;

        // 1. Check goal reached
        boolean firstAtGoal = isPlayerAtGoal(first);
        boolean secondAtGoal = second != null && isPlayerAtGoal(second);

        if (firstAtGoal && secondAtGoal) {
            finishRound(false, false, "Beide gleichzeitig im Ziel!");
            return;
        } else if (firstAtGoal) {
            finishRound(false, true, "Ziel zuerst erreicht!");
            return;
        } else if (secondAtGoal) {
            finishRound(true, false, "Ziel zuerst erreicht!");
            return;
        }

        // 2. Handle lava rise timing
        if (graceTicks > 0) {
            graceTicks--;
            if (graceTicks == 0) {
                message("§c§lDIE LAVA STEIGT! Rette dich nach oben!");
                playSound(SoundEvents.LAVA_EXTINGUISH, 1.2f);
            }
        } else {
            risingTicks++;
            // Smooth linear acceleration from MIN_SPEED (0.25) to MAX_SPEED (1.50) over 60s (1200 ticks)
            double progress = Math.min(1.0, (double) risingTicks / RAMP_UP_TICKS);
            currentSpeed = MIN_SPEED + (MAX_SPEED - MIN_SPEED) * progress;

            double blocksPerTick = currentSpeed / 20.0;
            currentLavaHeight += blocksPerTick;

            int targetLayer = (int) Math.floor(currentLavaHeight);
            int maxArenaY = arena.getMaxPos().getY();

            if (targetLayer > currentLavaLayer && targetLayer <= maxArenaY) {
                while (currentLavaLayer < targetLayer && currentLavaLayer < maxArenaY) {
                    currentLavaLayer++;
                    fillLavaLayer(currentLavaLayer);
                }
                // Ambient rising sound
                if (playTicks % 10 == 0) {
                    level.playSound(null, first.blockPosition(), SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.8f, 1.0f);
                }
            }
        }

        // 3. Check lava instant kill
        boolean firstInLava = isPlayerInLava(first);
        boolean secondInLava = second != null && isPlayerInLava(second);

        if (firstInLava || secondInLava) {
            if (firstInLava) triggerDeathEffect(first);
            if (secondInLava) triggerDeathEffect(second);

            if (firstInLava && secondInLava) {
                finishRound(true, true, "Beide in der Lava verbrannt");
            } else if (firstInLava) {
                finishRound(true, false, "In der Lava verbrannt");
            } else {
                finishRound(false, true, "In der Lava verbrannt");
            }
        }
    }

    private boolean isPlayerAtGoal(ServerPlayer player) {
        if (player == null || player.isDeadOrDying() || player.level() != level) return false;
        if (player.isCreative() || player.isSpectator()) return false;
        if (!arena.hasGoal()) {
            // Default goal: reaching topmost layer of the arena
            return player.getY() >= arena.getMaxPos().getY() - 0.5 && arena.isInsideArena(player.blockPosition());
        }
        // Protect against huge goal selections encompassing the lower arena/spawns
        int minGoalThresholdY = Math.max(arena.getGoalMin().getY(), arena.getGoalMax().getY() - 5);
        if (player.getY() < minGoalThresholdY) return false;

        return arena.isInsideGoal(player.getBoundingBox()) || arena.isInsideGoal(player.blockPosition());
    }

    private boolean isPlayerInLava(ServerPlayer player) {
        if (player == null || player.isDeadOrDying() || player.level() != level) return false;
        if (player.isCreative() || player.isSpectator()) return false;
        if (player.isInLava()) return true;

        BlockPos feetPos = player.blockPosition();
        if (level.getBlockState(feetPos).is(Blocks.LAVA)) return true;

        // Strict boundary check: If player's feet touch or dip below current lava level
        if (currentLavaLayer >= arena.getMinPos().getY()) {
            if (player.getY() <= currentLavaLayer + 0.5 && arena.isInsideArena(feetPos)) {
                return true;
            }
        }

        return false;
    }

    private void triggerDeathEffect(ServerPlayer player) {
        level.sendParticles(ParticleTypes.LAVA, player.getX(), player.getY() + 0.5, player.getZ(), 25, 0.4, 0.4, 0.4, 0.2);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 0.8, player.getZ(), 15, 0.3, 0.3, 0.3, 0.05);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.PLAYERS, 1.5f, 0.8f);
    }

    private void fillLavaLayer(int y) {
        BlockPos min = arena.getMinPos();
        BlockPos max = arena.getMaxPos();

        // Preserve outer walls! Only fill inner volume so lava doesn't leak or destroy perimeter
        int minX = min.getX() + 1;
        int maxX = max.getX() - 1;
        int minZ = min.getZ() + 1;
        int maxZ = max.getZ() - 1;

        if (minX > maxX || minZ > maxZ) {
            minX = min.getX();
            maxX = max.getX();
            minZ = min.getZ();
            maxZ = max.getZ();
        }

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState lavaState = Blocks.LAVA.defaultBlockState();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                pos.set(x, y, z);
                BlockState current = level.getBlockState(pos);
                if (current.is(Blocks.LAVA)) continue;

                // Snapshot original block to restore later
                arenaSnapshot.putIfAbsent(pos.immutable(), current);
                // Destroy block and place lava
                level.setBlock(pos, lavaState, 3);
            }
        }
    }

    private void spawnLootChests() {
        BlockPos min = arena.getEffectiveChestMin();
        BlockPos max = arena.getEffectiveChestMax();

        int minX = Math.min(min.getX(), max.getX());
        int maxX = Math.max(min.getX(), max.getX());
        int minY = Math.min(min.getY(), max.getY());
        int maxY = Math.max(min.getY(), max.getY());
        int minZ = Math.min(min.getZ(), max.getZ());
        int maxZ = Math.max(min.getZ(), max.getZ());

        RandomSource random = level.getRandom();
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();

        // 1. First: Check for any pre-placed chests/barrels in the arena/chest-area
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    mpos.set(x, y, z);
                    BlockState state = level.getBlockState(mpos);
                    if (state.is(Blocks.CHEST) || state.is(Blocks.BARREL)) {
                        BlockPos imm = mpos.immutable();
                        chestPositions.add(imm);
                        populateChestStatic(level, imm, random);
                    }
                }
            }
        }

        int height = maxY - minY + 1;
        int desiredChests = Math.max(3, Math.min(20, height / 4));

        // If pre-placed chests already satisfy the count, we don't need to generate more
        if (chestPositions.size() >= desiredChests) {
            de.browars.BroWarsMod.LOGGER.info("[Lava Escape] {} vorplatzierte Kisten gefunden und befüllt.", chestPositions.size());
            return;
        }

        // 2. Procedural chest platform finder
        // If not using a separate chestarea, stay 1 block inside outer walls if arena is wide enough
        int startX = (arena.hasChestArea() || maxX - minX <= 3) ? minX : minX + 1;
        int endX = (arena.hasChestArea() || maxX - minX <= 3) ? maxX : maxX - 1;
        int startZ = (arena.hasChestArea() || maxZ - minZ <= 3) ? minZ : minZ + 1;
        int endZ = (arena.hasChestArea() || maxZ - minZ <= 3) ? maxZ : maxZ - 1;

        List<BlockPos> validPlatforms = new ArrayList<>();

        for (int y = minY; y <= maxY - 1; y++) {
            for (int x = startX; x <= endX; x++) {
                for (int z = startZ; z <= endZ; z++) {
                    mpos.set(x, y, z);
                    BlockState floor = level.getBlockState(mpos);
                    if (floor.isAir() || floor.is(Blocks.LAVA) || floor.is(Blocks.WATER) || floor.is(Blocks.CHEST) || floor.is(Blocks.BARREL)) continue;
                    if (!floor.isSolid() && floor.getCollisionShape(level, mpos).isEmpty()) continue;

                    BlockPos chestPos = mpos.above();
                    if (chestPositions.contains(chestPos)) continue;

                    BlockState chestSpace = level.getBlockState(chestPos);
                    if (!chestSpace.isAir() && !chestSpace.canBeReplaced() && !chestSpace.is(Blocks.CHEST)) continue;

                    // Avoid spawning directly on top of player spawns
                    if (isNearSpawn(chestPos, 1) || isNearSpawn(chestPos, 2)) continue;

                    validPlatforms.add(chestPos.immutable());
                }
            }
        }

        if (validPlatforms.isEmpty()) {
            int cx = (startX + endX) / 2;
            int cz = (startZ + endZ) / 2;
            for (int y = minY + 4; y < maxY - 2; y += 12) {
                BlockPos floorPos = new BlockPos(cx, y, cz);
                arenaSnapshot.putIfAbsent(floorPos, level.getBlockState(floorPos));
                level.setBlock(floorPos, Blocks.COBBLESTONE.defaultBlockState(), 3);

                BlockPos chestPos = floorPos.above();
                arenaSnapshot.putIfAbsent(chestPos, level.getBlockState(chestPos));
                validPlatforms.add(chestPos);
            }
        }

        if (validPlatforms.isEmpty()) {
            de.browars.BroWarsMod.LOGGER.warn("[Lava Escape] Keine Plattformen für Kistengenerierung gefunden im Bereich {} bis {}", min.toShortString(), max.toShortString());
            return;
        }

        // Distribute chests vertically
        Collections.shuffle(validPlatforms, new java.util.Random(random.nextLong()));
        validPlatforms.sort(Comparator.comparingInt(BlockPos::getY));

        int needed = desiredChests - chestPositions.size();
        int step = Math.max(1, validPlatforms.size() / Math.max(1, needed));

        for (int i = 0; i < validPlatforms.size() && chestPositions.size() < desiredChests; i += step) {
            BlockPos cPos = validPlatforms.get(i);
            arenaSnapshot.putIfAbsent(cPos, level.getBlockState(cPos));

            level.setBlock(cPos, Blocks.CHEST.defaultBlockState(), 3);
            chestPositions.add(cPos);
            populateChestStatic(level, cPos, random);
        }

        de.browars.BroWarsMod.LOGGER.info("[Lava Escape] Insgesamt {} Kisten bereitgestellt.", chestPositions.size());
    }

    public static int generateAndPlaceChests(ServerLevel level, BlockPos p1, BlockPos p2) {
        int minX = Math.min(p1.getX(), p2.getX());
        int maxX = Math.max(p1.getX(), p2.getX());
        int minY = Math.min(p1.getY(), p2.getY());
        int maxY = Math.max(p1.getY(), p2.getY());
        int minZ = Math.min(p1.getZ(), p2.getZ());
        int maxZ = Math.max(p1.getZ(), p2.getZ());

        RandomSource random = level.getRandom();
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();

        List<BlockPos> validPlatforms = new ArrayList<>();

        for (int y = minY; y <= maxY - 1; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    mpos.set(x, y, z);
                    BlockState floor = level.getBlockState(mpos);
                    if (floor.isAir() || floor.is(Blocks.LAVA) || floor.is(Blocks.WATER) || floor.is(Blocks.CHEST) || floor.is(Blocks.BARREL)) continue;
                    if (!floor.isSolid() && floor.getCollisionShape(level, mpos).isEmpty()) continue;

                    BlockPos chestPos = mpos.above();
                    BlockState chestSpace = level.getBlockState(chestPos);
                    if (!chestSpace.isAir() && !chestSpace.canBeReplaced() && !chestSpace.is(Blocks.CHEST)) continue;

                    validPlatforms.add(chestPos.immutable());
                }
            }
        }

        if (validPlatforms.isEmpty()) {
            int cx = (minX + maxX) / 2;
            int cz = (minZ + maxZ) / 2;
            for (int y = minY + 4; y < maxY - 2; y += 12) {
                BlockPos floorPos = new BlockPos(cx, y, cz);
                level.setBlock(floorPos, Blocks.COBBLESTONE.defaultBlockState(), 3);
                validPlatforms.add(floorPos.above());
            }
        }

        if (validPlatforms.isEmpty()) return 0;

        Collections.shuffle(validPlatforms, new java.util.Random(random.nextLong()));
        validPlatforms.sort(Comparator.comparingInt(BlockPos::getY));

        int height = maxY - minY + 1;
        int desiredChests = Math.max(4, Math.min(25, height / 3));
        int step = Math.max(1, validPlatforms.size() / Math.max(1, desiredChests));

        int placed = 0;
        for (int i = 0; i < validPlatforms.size() && placed < desiredChests; i += step) {
            BlockPos cPos = validPlatforms.get(i);
            level.setBlock(cPos, Blocks.CHEST.defaultBlockState(), 3);
            populateChestStatic(level, cPos, random);
            placed++;
        }

        return placed;
    }

    public static void populateChestStatic(ServerLevel level, BlockPos pos, RandomSource random) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ChestBlockEntity chest) {
            chest.clearContent();
            List<ItemStack> loot = LavaEscapeLoot.generateChestLoot(level.registryAccess(), random);
            List<Integer> slots = new ArrayList<>();
            for (int s = 0; s < chest.getContainerSize(); s++) slots.add(s);
            Collections.shuffle(slots, new java.util.Random(random.nextLong()));

            for (int idx = 0; idx < loot.size() && idx < slots.size(); idx++) {
                chest.setItem(slots.get(idx), loot.get(idx));
            }
            chest.setChanged();
        }
    }

    private boolean isNearSpawn(BlockPos pos, int spawnNumber) {
        var sp = arena.spawn(spawnNumber);
        double dx = pos.getX() + 0.5 - sp.x();
        double dy = pos.getY() + 0.5 - sp.y();
        double dz = pos.getZ() + 0.5 - sp.z();
        return (dx * dx + dz * dz < 1.5 * 1.5 && Math.abs(dy) < 2.5);
    }

    @Override
    protected void clearRound() {
        restoreArena();
        equippedPlayers.clear();
        ServerPlayer p1 = server.getPlayerList().getPlayer(p1Uuid);
        if (p1 != null) p1.clearFire();
        if (p2Uuid != null) {
            ServerPlayer p2 = server.getPlayerList().getPlayer(p2Uuid);
            if (p2 != null) p2.clearFire();
        }
    }

    @Override
    protected void restoreArena() {
        // 1. Remove all items/projectiles dropped inside arena
        if (arena.hasArena()) {
            AABB arenaBox = new AABB(arena.getMinPos().getX() - 1, arena.getMinPos().getY() - 1, arena.getMinPos().getZ() - 1,
                    arena.getMaxPos().getX() + 2, arena.getMaxPos().getY() + 2, arena.getMaxPos().getZ() + 2);
            List<ItemEntity> droppedItems = level.getEntitiesOfClass(ItemEntity.class, arenaBox);
            for (ItemEntity item : droppedItems) {
                item.discard();
            }
        }

        // 2. Restore all blocks modified by lava, chests or players
        for (Map.Entry<BlockPos, BlockState> entry : arenaSnapshot.entrySet()) {
            BlockPos p = entry.getKey();
            BlockState originalState = entry.getValue();
            level.setBlock(p, originalState, 3);
        }

        // 3. Clear any remaining placed blocks
        for (BlockPos pos : playerPlacedBlocks) {
            if (!arenaSnapshot.containsKey(pos)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }

        arenaSnapshot.clear();
        playerPlacedBlocks.clear();
        chestPositions.clear();

        enableFireTick();
    }

    private void disableFireTick() {
        try {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "gamerule doFireTick false");
        } catch (Exception ignored) {}
    }

    private void enableFireTick() {
        try {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "gamerule doFireTick true");
        } catch (Exception ignored) {}
    }

    @Override
    protected String matchStatus() {
        if (phase == Phase.COUNTDOWN) {
            return "Startet gleich...";
        } else if (graceTicks > 0) {
            return String.format(java.util.Locale.ROOT, "§6Lava in §e%.1fs", graceTicks / 20.0f);
        } else {
            return String.format(java.util.Locale.ROOT, "§cLava: Y=%d §7(§e%.1f b/s§7)", currentLavaLayer, currentSpeed);
        }
    }

    @Override
    protected String soloStatus() {
        return matchStatus();
    }
}
