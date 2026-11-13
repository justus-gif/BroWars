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
    private static final double MIN_SPEED = 0.2125; // blocks / second (15% slower than 0.25)
    private static final double MAX_SPEED = 1.275;  // blocks / second (15% slower than 1.50)

    private final Map<BlockPos, BlockState> arenaSnapshot = new HashMap<>();
    private final Set<BlockPos> playerPlacedBlocks = new HashSet<>();
    private final Set<BlockPos> chestPositions = new HashSet<>();
    private boolean baseArenaCaptured = false;

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

    @Override
    protected net.minecraft.world.level.GameType defaultGameType() {
        return net.minecraft.world.level.GameType.SURVIVAL;
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
        // If scaffolding or twisting vines are stacked, they can grow upwards or horizontally from the clicked block!
        // Mark vertical column and horizontal neighbors within scaffolding range as playerPlacedBlocks so players can break them.
        int minY = arena.getMinPos().getY();
        int maxY = arena.getMaxPos().getY();
        for (int y = Math.max(minY, immutable.getY() - 1); y <= Math.min(maxY, immutable.getY() + 32); y++) {
            BlockPos columnPos = new BlockPos(immutable.getX(), y, immutable.getZ());
            BlockState st = level.getBlockState(columnPos);
            if (st.isAir() || st.is(Blocks.SCAFFOLDING) || st.is(Blocks.TWISTING_VINES) || st.is(Blocks.TWISTING_VINES_PLANT)) {
                arenaSnapshot.putIfAbsent(columnPos, Blocks.AIR.defaultBlockState());
                playerPlacedBlocks.add(columnPos);
            }
        }
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos horiz = immutable.offset(dx, 0, dz);
                if (arena.isInsideArena(horiz)) {
                    BlockState st = level.getBlockState(horiz);
                    if (st.isAir() || st.is(Blocks.SCAFFOLDING)) {
                        arenaSnapshot.putIfAbsent(horiz, Blocks.AIR.defaultBlockState());
                        playerPlacedBlocks.add(horiz);
                    }
                }
            }
        }
    }

    public boolean isPlayerPlacedBlock(BlockPos pos) {
        return playerPlacedBlocks.contains(pos);
    }

    public boolean canBreakBlock(BlockPos pos) {
        if (playerPlacedBlocks.contains(pos) || chestPositions.contains(pos)) {
            return true;
        }
        BlockState base = arenaSnapshot.get(pos);
        return base != null && !level.getBlockState(pos).equals(base);
    }

    public boolean isInsideBuildZone(BlockPos pos) {
        if (!arena.hasArena()) return false;
        BlockPos min = arena.getMinPos();
        BlockPos max = arena.getMaxPos();
        return pos.getX() >= min.getX() - 2 && pos.getX() <= max.getX() + 2
                && pos.getY() >= min.getY() - 2 && pos.getY() <= max.getY() + 10
                && pos.getZ() >= min.getZ() - 2 && pos.getZ() <= max.getZ() + 2;
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
        if (!baseArenaCaptured) {
            cleanupPreviousGeneratedChests();
            captureBaseArenaSnapshot();
            baseArenaCaptured = true;
        }
        disableFireTick();

        currentLavaHeight = arena.getMinPos().getY() - 1;
        currentLavaLayer = arena.getMinPos().getY() - 1;
        graceTicks = GRACE_DURATION_TICKS;
        risingTicks = 0;
        currentSpeed = MIN_SPEED;

        spawnLootChests();
    }

    /**
     * Removes any leftover chests generated by a previous `/browars lavaescape chests` command
     * so that every match starts with a clean arena and generates fresh random chests.
     */
    private void cleanupPreviousGeneratedChests() {
        if (!arena.hasArena()) return;
        BlockPos min = arena.getMinPos();
        BlockPos max = arena.getMaxPos();
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        for (int y = min.getY(); y <= max.getY(); y++) {
            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    mpos.set(x, y, z);
                    BlockState st = level.getBlockState(mpos);
                    if (st.is(Blocks.CHEST)) {
                        if (level.getBlockEntity(mpos) instanceof ChestBlockEntity chest) {
                            chest.clearContent();
                        }
                        level.setBlock(mpos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    private void captureBaseArenaSnapshot() {
        if (!arena.hasArena()) return;
        BlockPos min = arena.getMinPos();
        BlockPos max = arena.getMaxPos();
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        for (int y = min.getY(); y <= max.getY(); y++) {
            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    mpos.set(x, y, z);
                    BlockState st = level.getBlockState(mpos);
                    // If there is any leftover lava or cobweb from an interrupted match, treat base as AIR
                    if (st.is(Blocks.LAVA) || st.is(Blocks.COBWEB) || st.is(Blocks.SCAFFOLDING)) {
                        level.setBlock(mpos, Blocks.AIR.defaultBlockState(), 3);
                        arenaSnapshot.put(mpos.immutable(), Blocks.AIR.defaultBlockState());
                    } else {
                        arenaSnapshot.put(mpos.immutable(), st);
                    }
                }
            }
        }
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
                setCenterTitle("§c§lLAVA STEIGT", "", 0xFFEF4444, 30);
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

        int minX = Math.min(min.getX(), max.getX());
        int maxX = Math.max(min.getX(), max.getX());
        int minZ = Math.min(min.getZ(), max.getZ());
        int maxZ = Math.max(min.getZ(), max.getZ());

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState lavaState = Blocks.LAVA.defaultBlockState();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                pos.set(x, y, z);
                BlockState current = level.getBlockState(pos);
                if (current.is(Blocks.LAVA)) continue;

                // Clear chest contents before lava overwrites a chest so items don't pop out
                if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
                    chest.clearContent();
                }

                // Snapshot original block to restore later
                arenaSnapshot.putIfAbsent(pos.immutable(), current);
                // Place lava across the entire arena area (flag 2 = UPDATE_CLIENTS without triggering fluid flow outside bounds)
                level.setBlock(pos, lavaState, 2);
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

        // 1. First: Check for any pre-placed chests/barrels anywhere in the chest area
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

        int area = Math.max(1, (maxX - minX + 1) * (maxZ - minZ + 1));
        int height = Math.max(1, maxY - minY + 1);
        int desiredChests = Math.max(8, Math.min(32, (area / 16) + (height / 6)));

        if (chestPositions.size() >= desiredChests) {
            de.browars.BroWarsMod.LOGGER.info("[Lava Escape] {} vorplatzierte Kisten gefunden und befüllt.", chestPositions.size());
            return;
        }

        // 2. Collect valid 3D chest spots randomly across ALL heights (in mid-air or on platforms)
        List<BlockPos> validSpots = collectValidChestSpots(level, minX, maxX, minY, maxY, minZ, maxZ, true);

        if (validSpots.isEmpty()) {
            de.browars.BroWarsMod.LOGGER.warn("[Lava Escape] Keine freien Positionen für Kistengenerierung gefunden im Bereich {} bis {}", min.toShortString(), max.toShortString());
            return;
        }

        int needed = desiredChests - chestPositions.size();
        List<BlockPos> chosen = selectDistributedChests(validSpots, chestPositions, needed, random);
        for (BlockPos cPos : chosen) {
            arenaSnapshot.putIfAbsent(cPos, level.getBlockState(cPos));
            level.setBlock(cPos, Blocks.CHEST.defaultBlockState(), 3);
            chestPositions.add(cPos);
            populateChestStatic(level, cPos, random);
        }

        de.browars.BroWarsMod.LOGGER.info("[Lava Escape] Insgesamt {} zufällige Kisten in der Arena bereitgestellt.", chestPositions.size());
    }

    public static int generateAndPlaceChests(ServerLevel level, BlockPos p1, BlockPos p2) {
        int minX = Math.min(p1.getX(), p2.getX());
        int maxX = Math.max(p1.getX(), p2.getX());
        int minY = Math.min(p1.getY(), p2.getY());
        int maxY = Math.max(p1.getY(), p2.getY());
        int minZ = Math.min(p1.getZ(), p2.getZ());
        int maxZ = Math.max(p1.getZ(), p2.getZ());

        // First remove any old chests in this region so re-running `/browars lavaescape chests` doesn't stack old chests
        BlockPos.MutableBlockPos cleanupPos = new BlockPos.MutableBlockPos();
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cleanupPos.set(x, y, z);
                    if (level.getBlockState(cleanupPos).is(Blocks.CHEST)) {
                        if (level.getBlockEntity(cleanupPos) instanceof ChestBlockEntity chest) {
                            chest.clearContent();
                        }
                        level.setBlock(cleanupPos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }

        RandomSource random = level.getRandom();
        int area = Math.max(1, (maxX - minX + 1) * (maxZ - minZ + 1));
        int height = Math.max(1, maxY - minY + 1);
        int desiredChests = Math.max(8, Math.min(32, (area / 16) + (height / 6)));

        List<BlockPos> validSpots = new ArrayList<>();
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        int searchMinY = Math.min(maxY, minY + 1);
        int searchMaxY = Math.max(searchMinY, maxY - 1);

        for (int y = searchMinY; y <= searchMaxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    mpos.set(x, y, z);
                    if (isValidChestSpot(level, mpos)) {
                        validSpots.add(mpos.immutable());
                    }
                }
            }
        }

        if (validSpots.isEmpty()) return 0;

        List<BlockPos> chosen = selectDistributedChests(validSpots, Set.of(), desiredChests, random);
        int placed = 0;
        for (BlockPos cPos : chosen) {
            level.setBlock(cPos, Blocks.CHEST.defaultBlockState(), 3);
            populateChestStatic(level, cPos, random);
            placed++;
        }

        return placed;
    }

    private static boolean isValidChestSpot(ServerLevel level, BlockPos chestPos) {
        BlockState chestSpace = level.getBlockState(chestPos);
        if (chestSpace.is(Blocks.LAVA) || chestSpace.is(Blocks.WATER) || chestSpace.is(Blocks.CHEST) || chestSpace.is(Blocks.BARREL)) {
            return false;
        }
        if (!chestSpace.isAir() && !chestSpace.canBeReplaced()) {
            return false;
        }

        BlockPos abovePos = chestPos.above();
        BlockState aboveChest = level.getBlockState(abovePos);
        if (!aboveChest.isAir() && !aboveChest.canBeReplaced()) {
            return false;
        }
        if (aboveChest.isRedstoneConductor(level, abovePos)) {
            return false;
        }
        return true;
    }

    private List<BlockPos> collectValidChestSpots(ServerLevel level, int startX, int endX, int minY, int maxY, int startZ, int endZ, boolean checkSpawns) {
        List<BlockPos> validSpots = new ArrayList<>();
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        int searchMinY = Math.min(maxY, minY + 1);
        int searchMaxY = Math.max(searchMinY, maxY - 1);

        for (int y = searchMinY; y <= searchMaxY; y++) {
            for (int x = startX; x <= endX; x++) {
                for (int z = startZ; z <= endZ; z++) {
                    mpos.set(x, y, z);
                    if (!isValidChestSpot(level, mpos)) continue;

                    if (chestPositions.contains(mpos)) continue;
                    if (checkSpawns && (isNearSpawn(mpos, 1) || isNearSpawn(mpos, 2))) continue;

                    validSpots.add(mpos.immutable());
                }
            }
        }
        return validSpots;
    }

    private static List<BlockPos> selectDistributedChests(List<BlockPos> candidates, Set<BlockPos> existing, int count, RandomSource random) {
        if (candidates.isEmpty() || count <= 0) return List.of();

        List<BlockPos> pool = new ArrayList<>(candidates);
        Collections.shuffle(pool, new java.util.Random(random.nextLong()));

        List<BlockPos> selected = new ArrayList<>();
        List<BlockPos> allOccupied = new ArrayList<>(existing);

        // Pass 1: Wide 3D spacing (at least 4.0 blocks apart = 16.0 distSqr)
        for (BlockPos p : pool) {
            if (selected.size() >= count) break;
            if (isFarEnough(p, allOccupied, 16.0)) {
                selected.add(p);
                allOccupied.add(p);
            }
        }

        // Pass 2: Medium 3D spacing (at least 2.5 blocks apart = 6.25 distSqr) if more needed
        if (selected.size() < count) {
            for (BlockPos p : pool) {
                if (selected.size() >= count) break;
                if (selected.contains(p)) continue;
                if (isFarEnough(p, allOccupied, 6.25)) {
                    selected.add(p);
                    allOccupied.add(p);
                }
            }
        }

        // Pass 3: Close 3D spacing (at least 1.5 blocks apart = 2.25 distSqr) if still needed
        if (selected.size() < count) {
            for (BlockPos p : pool) {
                if (selected.size() >= count) break;
                if (selected.contains(p)) continue;
                if (isFarEnough(p, allOccupied, 2.25)) {
                    selected.add(p);
                    allOccupied.add(p);
                }
            }
        }

        return selected;
    }

    private static boolean isFarEnough(BlockPos candidate, List<BlockPos> occupied, double minDistSqr) {
        if (minDistSqr <= 0.0) return true;
        for (BlockPos existing : occupied) {
            if (candidate.distSqr(existing) < minDistSqr) {
                return false;
            }
        }
        return true;
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
        if (spawnNumber == 1 && !arena.hasSpawn1()) return false;
        if (spawnNumber == 2 && !arena.hasSpawn2()) return false;
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
        // 1. Clear contents of all chests before removing blocks so items don't spill onto the ground
        for (BlockPos cPos : chestPositions) {
            if (level.getBlockEntity(cPos) instanceof ChestBlockEntity chest) {
                chest.clearContent();
            }
        }

        // 2. Clear any remaining placed blocks first
        for (BlockPos pos : playerPlacedBlocks) {
            if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
                chest.clearContent();
            }
            BlockState orig = arenaSnapshot.getOrDefault(pos, Blocks.AIR.defaultBlockState());
            level.setBlock(pos, orig, 3);
        }

        // 3. Restore all blocks modified by lava, chests or players back to the pristine base snapshot
        for (Map.Entry<BlockPos, BlockState> entry : arenaSnapshot.entrySet()) {
            BlockPos p = entry.getKey();
            BlockState originalState = entry.getValue();
            if (!level.getBlockState(p).equals(originalState)) {
                if (level.getBlockEntity(p) instanceof ChestBlockEntity chest) {
                    chest.clearContent();
                }
                level.setBlock(p, originalState, 3);
            }
        }

        // 4. Remove all items/projectiles dropped inside arena (including any container drops)
        if (arena.hasArena()) {
            AABB arenaBox = new AABB(arena.getMinPos().getX() - 2, arena.getMinPos().getY() - 2, arena.getMinPos().getZ() - 2,
                    arena.getMaxPos().getX() + 3, arena.getMaxPos().getY() + 3, arena.getMaxPos().getZ() + 3);
            List<ItemEntity> droppedItems = level.getEntitiesOfClass(ItemEntity.class, arenaBox);
            for (ItemEntity item : droppedItems) {
                item.discard();
            }
        }

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
            return "";
        } else if (graceTicks > 0) {
            return String.format(java.util.Locale.ROOT, "§e%.1fs", graceTicks / 20.0f);
        } else {
            return "§cY = " + currentLavaLayer;
        }
    }

    @Override
    protected String soloStatus() {
        return matchStatus();
    }
}
