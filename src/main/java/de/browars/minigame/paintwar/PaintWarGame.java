package de.browars.minigame.paintwar;

import com.mojang.math.Transformation;
import de.browars.BroWarsMod;
import de.browars.mixin.BlockDisplayAccess;
import de.browars.mixin.DisplayAccess;
import de.browars.minigame.AbstractDuelGame;
import de.browars.minigame.MinigameManager;
import de.browars.minigame.TeamColor;
import de.browars.network.SyncPaintWarRoulettePayload;
import de.browars.util.HologramHelper;
import de.browars.util.TeleportHelper;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

public class PaintWarGame extends AbstractDuelGame<PaintWarArenaData> {

    public static final int ROUND_DURATION_TICKS = 3 * 60 * 20; // 3 minutes (3600 ticks)
    public static final int REVEAL_DURATION_TICKS = 60;         // 3 seconds drumroll reveal
    public static final int SPECIAL_BLOCKS_REQUIRED = 750;      // 750 painted blocks = 100% XP Ulti (persists across rounds!)
    public static final int ROULETTE_SPIN_TICKS = 32;           // 1.6s top-right UI spin
    public static final String ITEM_TYPE_KEY = "paintwar_item_type";
    private static final String TEMPORARY_TAG = "browars_minigame_temporary";

    public enum ProjectileKind {
        SNOWBALL,
        BOMB,
        CLUSTER_MAIN,
        CLUSTER_MINI,
        CROSSBOW_ARROW
    }

    public enum BoxReward {
        BOMB("§a§lFarb-Bombe (2x)"),
        CLUSTER_BOMB("§d§lCluster-Bombe"),
        CROSSBOW("§b§lFarb-Armbrust (3 Salven)"),
        ROLLER("§e§lFarb-Walze (5 Sek.)"),
        BLOOPER("§8§lFarb-Blooper"),
        UPGRADE_COOLDOWN("§b§lUpgrade: Schneller Werfen!"),
        UPGRADE_RADIUS("§d§lUpgrade: Groesserer Klecks!");

        public final String displayName;
        BoxReward(String displayName) {
            this.displayName = displayName;
        }
    }

    public static class PlayerPaintState {
        public int snowballCooldown = 0;
        public int weaponCooldown = 0;
        public int cooldownUpgradeLevel = 0; // max 3 (resets per round)
        public int radiusUpgradeLevel = 0;   // max 2 (resets per round)

        // Ulti / Special bar state: persists across round restarts!
        public int specialCharge = 0;
        public boolean hasSpecialItem = false;
        public String specialItemType = "";

        public String activeWeaponType = "";
        public int activeWeaponAmmo = 0;
        public int rollerTicksRemaining = 0;
        public int blooperInkTicks = 0;
        public boolean submerged = false;

        public int rouletteTicks = 0;
        public BoxReward pendingReward = null;
    }

    private static class TrackedProjectile {
        final Projectile entity;
        final UUID ownerUuid;
        final TeamColor team;
        final ProjectileKind kind;
        final double radius;
        Vec3 lastPos;
        Vec3 lastVel;

        TrackedProjectile(Projectile entity, UUID ownerUuid, TeamColor team, ProjectileKind kind, double radius) {
            this.entity = entity;
            this.ownerUuid = ownerUuid;
            this.team = team;
            this.kind = kind;
            this.radius = radius;
            this.lastPos = entity.position();
            this.lastVel = entity.getDeltaMovement();
        }
    }

    private static class LuckyBox {
        final BlockPos floorPos;
        final double x, baseY, z;
        Display.BlockDisplay blockDisplay;
        UUID hologramUuid;
        int age = 0;

        LuckyBox(ServerLevel level, BlockPos floorPos) {
            this.floorPos = floorPos;
            this.x = floorPos.getX() + 0.5;
            this.baseY = floorPos.getY() + 1.35;
            this.z = floorPos.getZ() + 0.5;

            blockDisplay = EntityTypes.BLOCK_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
            if (blockDisplay != null) {
                blockDisplay.setPermanentlyInvulnerable(true);
                blockDisplay.addTag(TEMPORARY_TAG);
                ((BlockDisplayAccess) blockDisplay).browars$setBlockState(BroWarsMod.PAINT_LUCKY_BOX.defaultBlockState());
                ((DisplayAccess) blockDisplay).browars$setTeleportDuration(1);
                ((DisplayAccess) blockDisplay).browars$setViewRange(2.0f);
                ((DisplayAccess) blockDisplay).browars$setTransformation(new Transformation(
                        new Vector3f(-0.35f, 0.0f, -0.35f),
                        new Quaternionf(),
                        new Vector3f(0.7f, 0.7f, 0.7f),
                        new Quaternionf()
                ));
                blockDisplay.setPos(x, baseY, z);
                level.addFreshEntity(blockDisplay);
            }
            hologramUuid = HologramHelper.updateOrCreateHologram(level, null, x, baseY + 0.95, z, "§6§l[ ? ]", 0xFFD700);
            if (hologramUuid != null) {
                Entity holo = level.getEntity(hologramUuid);
                if (holo != null) holo.addTag(TEMPORARY_TAG);
            }
        }

        void tick(ServerLevel level) {
            age++;
            double bobOffset = Math.sin(age * 0.12) * 0.18;
            double curY = baseY + bobOffset;
            if (blockDisplay != null && blockDisplay.isAlive()) {
                blockDisplay.setPos(x, curY, z);
                blockDisplay.setYRot((age * 7.0f) % 360.0f);
            }
            if (hologramUuid != null) {
                Entity holo = level.getEntity(hologramUuid);
                if (holo != null && holo.isAlive()) {
                    holo.setPos(x, curY + 0.95, z);
                }
            }
            if (age % 5 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, x, curY + 0.35, z, 1, 0.25, 0.25, 0.25, 0.01);
            }
        }

        AABB getHitbox() {
            return new AABB(x - 0.65, baseY - 0.35, z - 0.65, x + 0.65, baseY + 1.15, z + 0.65);
        }

        void discard(ServerLevel level) {
            if (blockDisplay != null) blockDisplay.discard();
            HologramHelper.removeHologram(level, hologramUuid);
        }
    }

    private static class InkStormCloud {
        final UUID ownerUuid;
        final TeamColor team;
        double x, y, z;
        final double vx, vz;
        int ticksRemaining = 110; // 5.5 seconds balanced storm

        InkStormCloud(UUID ownerUuid, TeamColor team, double x, double y, double z, double vx, double vz) {
            this.ownerUuid = ownerUuid;
            this.team = team;
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vz = vz;
        }
    }

    private static class PendingInkStrike {
        final UUID ownerUuid;
        final TeamColor team;
        final BlockPos targetPos;
        int ticksRemaining = 30; // 1.5 seconds warning

        PendingInkStrike(UUID ownerUuid, TeamColor team, BlockPos targetPos) {
            this.ownerUuid = ownerUuid;
            this.team = team;
            this.targetPos = targetPos;
        }
    }

    /** Preserves exact original white BlockState (full block, slab with top/bottom/double, stairs with facing/half/shape). */
    private final Map<BlockPos, BlockState> originalArenaBlocks = new LinkedHashMap<>();
    private final List<BlockPos> boxCandidatePositions = new ArrayList<>();
    private final Map<UUID, PlayerPaintState> playerStates = new HashMap<>();
    private final List<TrackedProjectile> activeProjectiles = new ArrayList<>();
    private final List<LuckyBox> luckyBoxes = new ArrayList<>();
    private final List<InkStormCloud> activeStorms = new ArrayList<>();
    private final List<PendingInkStrike> pendingStrikes = new ArrayList<>();
    private final Random random = new Random();

    private boolean inReveal = false;
    private int revealTicks = 0;

    public PaintWarGame(MinecraftServer server, ServerLevel level, PaintWarArenaData arena,
                        ServerPlayer first, ServerPlayer second, TeamColor firstTeam, TeamColor secondTeam) {
        super(server, level, arena, first, second, firstTeam, secondTeam);
    }

    @Override
    public String getId() {
        return "paintwar";
    }

    @Override
    public String getDisplayName() {
        return "Paint War";
    }

    @Override
    protected String startInstruction() {
        return "Färbe so viel weißen Concrete (Blöcke, Slabs & Treppen) wie möglich!";
    }

    @Override
    protected int targetScore() {
        return 3; // Best of 5 / First to 3
    }

    @Override
    protected boolean hasSpawnCampEjector() {
        return false; // Random spawns across the map, no spawn chambers
    }

    @Override
    protected boolean allowMultipleGameSlots() {
        return true;
    }

    @Override
    protected boolean shouldEquipChestplate(ServerPlayer player) {
        PlayerPaintState state = playerStates.get(player.getUUID());
        return state == null || !state.submerged;
    }

    @Override
    protected int timerRemaining() {
        if (inReveal || phase == Phase.INTERMISSION || phase == Phase.FINISHED) {
            return 0;
        }
        if (phase == Phase.COUNTDOWN) {
            return ROUND_DURATION_TICKS;
        }
        return Math.max(1, ROUND_DURATION_TICKS - playTicks);
    }

    @Override
    protected int timerTotal() {
        return ROUND_DURATION_TICKS;
    }

    @Override
    protected String targetName() {
        if (inReveal || phase == Phase.INTERMISSION || phase == Phase.FINISHED) {
            return "";
        }
        int remSeconds = Math.max(0, (ROUND_DURATION_TICKS - playTicks + 19) / 20);
        int mins = remSeconds / 60;
        int secs = remSeconds % 60;
        return String.format(Locale.ROOT, "%02d:%02d", mins, secs);
    }

    @Override
    protected int phaseNumber() {
        if (playTicks < 1200) return 1;      // First minute
        if (playTicks < 2400) return 2;      // Second minute
        return 3;                            // Final minute
    }

    @Override
    protected String phaseTitle() {
        return switch (phaseNumber()) {
            case 2 -> "Item-Fieber";
            case 3 -> "Schluss-Spurt";
            default -> "Farb-Start";
        };
    }

    public PlayerPaintState getState(UUID uuid) {
        return playerStates.computeIfAbsent(uuid, k -> new PlayerPaintState());
    }

    public TeamColor getTeamForPlayer(ServerPlayer player) {
        return p1Uuid.equals(player.getUUID()) ? p1Team : p2Team;
    }

    @Override
    protected void prepareArena() {
        clearActiveEntities();
        inReveal = false;
        revealTicks = 0;

        if (originalArenaBlocks.isEmpty()) {
            scanArenaForPaintableBlocks();
        } else {
            resetPaintedBlocksToWhite();
        }

        // Pick fresh random spawns across all levels of the arena for this round!
        arena.assignRandomRoundSpawns(boxCandidatePositions, random);
    }

    private boolean isPaintableBlockState(BlockState state) {
        return state.is(Blocks.CONCRETE.white())
                || state.is(Blocks.CONCRETE_SLAB.white())
                || state.is(Blocks.CONCRETE_STAIRS.white());
    }

    private BlockState toWhiteEquivalent(BlockState state) {
        if (state.getBlock() instanceof SlabBlock) {
            return Blocks.CONCRETE_SLAB.white().withPropertiesOf(state);
        }
        if (state.getBlock() instanceof StairBlock) {
            return Blocks.CONCRETE_STAIRS.white().withPropertiesOf(state);
        }
        return Blocks.CONCRETE.white().defaultBlockState();
    }

    private void scanArenaForPaintableBlocks() {
        originalArenaBlocks.clear();
        boxCandidatePositions.clear();
        if (!arena.hasArena()) return;

        BlockPos min = arena.getMinPos();
        BlockPos max = arena.getMaxPos();
        int minX = Math.min(min.getX(), max.getX());
        int maxX = Math.max(min.getX(), max.getX());
        int minZ = Math.min(min.getZ(), max.getZ());
        int maxZ = Math.max(min.getZ(), max.getZ());
        int minY = level.getMinY();
        int maxY = level.getMaxY() - 1;

        int minChunkX = minX >> 4;
        int maxChunkX = maxX >> 4;
        int minChunkZ = minZ >> 4;
        int maxChunkZ = maxZ >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                level.getChunk(cx, cz);
            }
        }

        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!level.hasChunkAt(mpos.set(x, min.getY(), z))) continue;
                for (int y = minY; y <= maxY; y++) {
                    mpos.set(x, y, z);
                    BlockState state = level.getBlockState(mpos);
                    if (isPaintableBlockState(state)) {
                        BlockPos imm = mpos.immutable();
                        BlockState whiteState = toWhiteEquivalent(state);
                        originalArenaBlocks.put(imm, whiteState);
                        if (!state.equals(whiteState)) {
                            level.setBlock(imm, whiteState, 3);
                        }
                        BlockState above1 = level.getBlockState(imm.above());
                        BlockState above2 = level.getBlockState(imm.above(2));
                        if (above1.getCollisionShape(level, imm.above()).isEmpty()
                                && above2.getCollisionShape(level, imm.above(2)).isEmpty()) {
                            boxCandidatePositions.add(imm);
                        }
                    }
                }
            }
        }
    }

    private void resetPaintedBlocksToWhite() {
        for (Map.Entry<BlockPos, BlockState> entry : originalArenaBlocks.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState whiteState = entry.getValue();
            if (!level.getBlockState(pos).equals(whiteState)) {
                level.setBlock(pos, whiteState, 3);
            }
        }
    }

    @Override
    protected void clearRound() {
        clearActiveEntities();
        inReveal = false;
        revealTicks = 0;
        for (UUID id : getParticipantIds()) {
            PlayerPaintState st = getState(id);
            st.snowballCooldown = 0;
            st.weaponCooldown = 0;
            st.cooldownUpgradeLevel = 0;
            st.radiusUpgradeLevel = 0;
            // NOTE: st.specialCharge, st.hasSpecialItem, and st.specialItemType intentionally persist across rounds!
            st.activeWeaponType = "";
            st.activeWeaponAmmo = 0;
            st.rollerTicksRemaining = 0;
            st.blooperInkTicks = 0;
            st.submerged = false;
            st.rouletteTicks = 0;
            st.pendingReward = null;
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                ServerPlayNetworking.send(p, new SyncPaintWarRoulettePayload(-1, 0));
            }
        }
    }

    @Override
    protected void restoreArena() {
        clearActiveEntities();
        resetPaintedBlocksToWhite();
        originalArenaBlocks.clear();
        boxCandidatePositions.clear();
    }

    private void clearActiveEntities() {
        for (TrackedProjectile tp : activeProjectiles) {
            if (tp.entity != null && tp.entity.isAlive()) {
                tp.entity.discard();
            }
        }
        activeProjectiles.clear();

        for (LuckyBox box : luckyBoxes) {
            box.discard(level);
        }
        luckyBoxes.clear();
        activeStorms.clear();
        pendingStrikes.clear();
    }

    private List<UUID> getParticipantIds() {
        return isSoloMode ? List.of(p1Uuid) : List.of(p1Uuid, p2Uuid);
    }

    @Override
    protected void onRoundStarted() {
        for (UUID id : getParticipantIds()) {
            PlayerPaintState st = getState(id);
            st.snowballCooldown = 0;
            st.weaponCooldown = 0;
            st.cooldownUpgradeLevel = 0;
            st.radiusUpgradeLevel = 0;
            st.activeWeaponType = "";
            st.activeWeaponAmmo = 0;
            st.rollerTicksRemaining = 0;
            st.blooperInkTicks = 0;
            st.submerged = false;
            st.rouletteTicks = 0;
            st.pendingReward = null;

            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                p.getInventory().setSelectedSlot(0);
                p.connection.send(new ClientboundSetHeldSlotPacket(0));
                syncSpecialBarToPlayer(p, st);
                syncPlayerInventory(p);
            }
        }
    }

    private void syncSpecialBarToPlayer(ServerPlayer player, PlayerPaintState st) {
        if (st.hasSpecialItem) {
            player.experienceProgress = 1.0f;
            player.totalExperience = SPECIAL_BLOCKS_REQUIRED;
            player.setExperienceLevels(1);
        } else {
            player.experienceProgress = Math.min(1.0f, (float) st.specialCharge / SPECIAL_BLOCKS_REQUIRED);
            player.totalExperience = st.specialCharge;
            player.setExperienceLevels(0);
        }
    }

    @Override
    protected void equipExtra(ServerPlayer player) {
        syncPlayerInventory(player);
    }

    private ItemStack createTaggedGameItem(Item item, int count, String customName, String itemType, boolean glint) {
        ItemStack stack = new ItemStack(item, Math.max(1, count));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(customName));
        if (glint) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("browars_game_item", true);
        tag.putString(ITEM_TYPE_KEY, itemType);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        if (item == Items.CROSSBOW) {
            stack.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(List.of(new ItemStack(Items.ARROW))));
        }
        return stack;
    }

    public static String getPaintWarItemType(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return "";
        return data.copyTag().getStringOr(ITEM_TYPE_KEY, "");
    }

    private void syncPlayerInventory(ServerPlayer player) {
        if (player == null || player.isDeadOrDying()) return;
        PlayerPaintState st = getState(player.getUUID());
        TeamColor team = getTeamForPlayer(player);

        syncSpecialBarToPlayer(player, st);

        // Slot 0: Base Paint Ball (custom texture item!)
        String lvSuffix = (st.cooldownUpgradeLevel > 0 || st.radiusUpgradeLevel > 0)
                ? " §7[Tempo " + st.cooldownUpgradeLevel + " | Radius " + st.radiusUpgradeLevel + "]"
                : "";
        ItemStack slot0 = player.getInventory().getItem(0);
        if (!"snowball".equals(getPaintWarItemType(slot0))) {
            ItemStack ball = createTaggedGameItem(
                    BroWarsMod.PAINT_BALL, 1,
                    team.chatPrefix + "§lFarb-Ball" + lvSuffix + " §7(Rechtsklick)",
                    "snowball",
                    st.cooldownUpgradeLevel > 0 || st.radiusUpgradeLevel > 0
            );
            player.getInventory().setItem(0, ball);
        } else {
            slot0.setCount(1);
            slot0.set(DataComponents.CUSTOM_NAME, Component.literal(team.chatPrefix + "§lFarb-Ball" + lvSuffix + " §7(Rechtsklick)"));
            slot0.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, st.cooldownUpgradeLevel > 0 || st.radiusUpgradeLevel > 0);
        }

        // Slot 1: Lucky Box Active Weapon / Power-Up
        if (st.rollerTicksRemaining > 0) {
            int sec = (st.rollerTicksRemaining + 19) / 20;
            ItemStack roller = createTaggedGameItem(
                    BroWarsMod.PAINT_ROLLER, 1,
                    "§e§lFarb-Walze §7(Aktiv: " + sec + "s)",
                    "roller", true
            );
            player.getInventory().setItem(1, roller);
        } else if (!st.activeWeaponType.isEmpty() && st.activeWeaponAmmo > 0) {
            ItemStack weapon = switch (st.activeWeaponType) {
                case "bomb" -> createTaggedGameItem(BroWarsMod.PAINT_BOMB, st.activeWeaponAmmo, "§a§lFarb-Bombe §7(" + st.activeWeaponAmmo + "x)", "bomb", false);
                case "cluster_bomb" -> createTaggedGameItem(BroWarsMod.CLUSTER_BOMB, st.activeWeaponAmmo, "§d§lCluster-Bombe §7(" + st.activeWeaponAmmo + "x)", "cluster_bomb", false);
                case "crossbow" -> createTaggedGameItem(Items.CROSSBOW, 1, "§b§lFarb-Armbrust §7(" + st.activeWeaponAmmo + " Salven)", "crossbow", true);
                case "blooper" -> createTaggedGameItem(BroWarsMod.PAINT_BLOOPER, st.activeWeaponAmmo, "§8§lFarb-Blooper §7(Rechtsklick)", "blooper", false);
                default -> ItemStack.EMPTY;
            };
            player.getInventory().setItem(1, weapon);
        } else {
            st.activeWeaponType = "";
            st.activeWeaponAmmo = 0;
            if (!player.getInventory().getItem(1).isEmpty()) {
                player.getInventory().setItem(1, ItemStack.EMPTY);
            }
        }

        // Slot 2: Single Ulti Item (Mega-Farbschlag, persists across rounds!)
        if (st.hasSpecialItem) {
            ItemStack slot2 = player.getInventory().getItem(2);
            if (!"special_strike".equals(getPaintWarItemType(slot2))) {
                ItemStack special = createTaggedGameItem(
                        BroWarsMod.MEGA_STRIKE, 1,
                        "§d§lULTI: Mega-Farbschlag §7(Rechtsklick)",
                        "special_strike", true
                );
                player.getInventory().setItem(2, special);
            }
        } else if (!player.getInventory().getItem(2).isEmpty()) {
            player.getInventory().setItem(2, ItemStack.EMPTY);
        }

        // Clear slots 3..8
        for (int i = 3; i < 9; i++) {
            if (!player.getInventory().getItem(i).isEmpty()) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
    }

    private int getMaxSnowballCooldown(PlayerPaintState st) {
        return switch (st.cooldownUpgradeLevel) {
            case 1 -> 11; // 0.55s
            case 2 -> 8;  // 0.40s
            case 3 -> 6;  // 0.30s
            default -> 14; // 0.70s
        };
    }

    private double getSnowballRadius(PlayerPaintState st) {
        return switch (st.radiusUpgradeLevel) {
            case 1 -> 1.60;
            case 2 -> 1.90;
            default -> 1.30;
        };
    }

    @Override
    protected void tickRound(ServerPlayer first, ServerPlayer second) {
        if (inReveal) {
            tickRevealPhase(first, second);
            return;
        }

        if (playTicks >= ROUND_DURATION_TICKS) {
            startRevealPhase(first, second);
            return;
        }

        // Keep players inside the arena (teleport to a random surface block if fallen out)
        keepPlayerInBounds(first);
        if (second != null) keepPlayerInBounds(second);

        // Tick player states, Splatoon floor effects, roller trails & roulette
        tickPlayerState(first, p1Team);
        if (second != null) tickPlayerState(second, p2Team);

        // Tick tracked projectiles
        tickProjectiles(first, second);

        // Tick Lucky Boxes & spawn new ones periodically
        tickLuckyBoxes(first, second);

        // Tick Special effects (Ink Storm clouds & Ink Strikes)
        tickSpecials(first);

        // Broadcast HUD once per second so MM:SS timer updates smoothly
        if (playTicks % 20 == 0) {
            MinigameManager.getInstance().broadcastHud(server, getHudState());
        }
    }

    private void keepPlayerInBounds(ServerPlayer player) {
        if (player == null || player.isDeadOrDying()) return;
        if (!arena.containsPlayer(player)) {
            if (!boxCandidatePositions.isEmpty()) {
                BlockPos target = boxCandidatePositions.get(random.nextInt(boxCandidatePositions.size()));
                player.teleportTo(level, target.getX() + 0.5, target.getY() + 1.0, target.getZ() + 0.5,
                        Set.of(), player.getYRot(), 0f, true);
            } else {
                var spawn = arena.spawn(1);
                player.teleportTo(level, spawn.x(), spawn.y(), spawn.z(), Set.of(), spawn.yaw(), spawn.pitch(), true);
            }
            player.setDeltaMovement(0, 0, 0);
            player.fallDistance = 0;
            player.sendSystemMessage(Component.literal("§eZurück in die Arena!"), true);
        }
    }

    /**
     * Accurately finds the concrete block, slab, or stairs the player is standing on.
     * Bottom slabs and stairs occupy player.blockPosition(), while full blocks and top slabs
     * are at player.getBlockPosBelowThatAffectsMyMovement() or player.blockPosition().below().
     */
    private BlockPos findSurfaceBlockUnderPlayer(ServerPlayer player) {
        BlockPos feetPos = player.blockPosition();
        if (originalArenaBlocks.containsKey(feetPos)) {
            return feetPos;
        }
        BlockPos slightlyBelow = BlockPos.containing(player.getX(), player.getY() - 0.08, player.getZ());
        if (originalArenaBlocks.containsKey(slightlyBelow)) {
            return slightlyBelow;
        }
        BlockPos affectPos = player.getBlockPosBelowThatAffectsMyMovement();
        if (originalArenaBlocks.containsKey(affectPos)) {
            return affectPos;
        }
        BlockPos belowPos = feetPos.below();
        if (originalArenaBlocks.containsKey(belowPos)) {
            return belowPos;
        }
        return slightlyBelow;
    }

    private void tickPlayerState(ServerPlayer player, TeamColor team) {
        if (player == null || player.isDeadOrDying() || player.level() != level) return;
        PlayerPaintState st = getState(player.getUUID());

        BlockPos floorPos = findSurfaceBlockUnderPlayer(player);
        BlockState floorState = level.getBlockState(floorPos);

        TeamColor opponentTeam = (team == p1Team) ? p2Team : p1Team;
        boolean onOwnInk = team.matchesTeamConcrete(floorState);
        boolean onEnemyInk = !isSoloMode && opponentTeam.matchesTeamConcrete(floorState);

        // Splatoon Squid Dive ("Abtauchen") when sneaking on own ink -> fast sneaking speed!
        if (onOwnInk && player.isShiftKeyDown() && st.rollerTicksRemaining <= 0) {
            st.submerged = true;
            var sneakAttr = player.getAttribute(Attributes.SNEAKING_SPEED);
            if (sneakAttr != null && sneakAttr.getBaseValue() != 1.0) {
                sneakAttr.setBaseValue(1.0);
            }
            player.removeEffect(MobEffects.SLOWNESS);
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 10, 0, false, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, 10, 2, false, false, true));
            if (playTicks % 3 == 0) {
                DustParticleOptions dust = TeleportHelper.createDustParticle(team.hexColor, 1.1f);
                level.sendParticles(dust, player.getX(), player.getY() + 0.15, player.getZ(), 4, 0.25, 0.08, 0.25, 0.02);
                level.sendParticles(ParticleTypes.BUBBLE_POP, player.getX(), player.getY() + 0.15, player.getZ(), 2, 0.2, 0.05, 0.2, 0.01);
            }
            if (playTicks % 10 == 0) {
                playPlayerSound(player, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, 0.5f, 1.5f);
            }
        } else {
            var sneakAttr = player.getAttribute(Attributes.SNEAKING_SPEED);
            if (sneakAttr != null && sneakAttr.getBaseValue() != 0.3) {
                sneakAttr.setBaseValue(0.3);
            }
            if (st.submerged) {
                st.submerged = false;
                player.removeEffect(MobEffects.INVISIBILITY);
                player.removeEffect(MobEffects.SPEED);
            }
            if (onOwnInk) {
                player.removeEffect(MobEffects.SLOWNESS);
                player.addEffect(new MobEffectInstance(MobEffects.SPEED, 10, 0, false, false, false));
            } else if (onEnemyInk) {
                player.removeEffect(MobEffects.SPEED);
                player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 15, 2, false, false, true));
                if (playTicks % 6 == 0) {
                    DustParticleOptions enemyDust = TeleportHelper.createDustParticle(opponentTeam.hexColor, 0.9f);
                    level.sendParticles(enemyDust, player.getX(), player.getY() + 0.1, player.getZ(), 2, 0.2, 0.05, 0.2, 0.01);
                }
            }
        }

        // Cooldown recovery (2x faster while submerged in own ink!)
        int cdStep = st.submerged ? 2 : 1;
        if (st.snowballCooldown > 0) st.snowballCooldown = Math.max(0, st.snowballCooldown - cdStep);
        if (st.weaponCooldown > 0) st.weaponCooldown = Math.max(0, st.weaponCooldown - cdStep);

        // Ongoing Blooper ink screen splatter effect on victim
        if (st.blooperInkTicks > 0) {
            st.blooperInkTicks--;
            if (st.blooperInkTicks % 6 == 0) {
                DustParticleOptions inkDust = TeleportHelper.createDustParticle(0x1E1B4B, 1.4f);
                Vec3 look = player.getViewVector(1.0f);
                level.sendParticles(inkDust,
                        player.getX() + look.x * 0.45,
                        player.getEyeY() - 0.05,
                        player.getZ() + look.z * 0.45,
                        6, 0.25, 0.18, 0.25, 0.01);
            }
        }

        // Active Splatoon Paint Roller trail (supports slabs, stairs & NetBlock bridges across heights!)
        if (st.rollerTicksRemaining > 0) {
            st.rollerTicksRemaining--;
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, 10, 0, false, false, false));
            paintSphere(floorPos, 1.4, player, team, false, 1.0f);
            if (floorState.is(BroWarsMod.NET_BLOCK)) {
                dripPaintThroughNet(floorPos, 1.25, player, team, false, 0.9f);
            }
            if (st.rollerTicksRemaining % 6 == 0) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.5f, 1.3f);
            }
            if (st.rollerTicksRemaining == 0) {
                player.sendSystemMessage(Component.literal("§7Farb-Walze ist abgelaufen!"), true);
                playPlayerSound(player, SoundEvents.ITEM_BREAK, 0.7f, 1.2f);
            }
        }

        // Mario Kart Lucky Box Roulette (visuals rendered in top-right HUD UI, sound ticked here)
        if (st.rouletteTicks > 0) {
            st.rouletteTicks--;
            int stepInterval = st.rouletteTicks > 16 ? 2 : (st.rouletteTicks > 8 ? 3 : 4);
            if (st.rouletteTicks > 0 && st.rouletteTicks % stepInterval == 0) {
                float progress = 1.0f - (st.rouletteTicks / (float) ROULETTE_SPIN_TICKS);
                playPlayerSound(player, SoundEvents.NOTE_BLOCK_HAT, 0.65f, 1.3f + progress * 0.6f);
            } else if (st.rouletteTicks == 0 && st.pendingReward != null) {
                applyBoxReward(player, st, st.pendingReward);
                st.pendingReward = null;
            }
        }

        syncPlayerInventory(player);
    }

    private void applyBoxReward(ServerPlayer player, PlayerPaintState st, BoxReward reward) {
        playPlayerSound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.75f, 1.3f);
        switch (reward) {
            case BOMB -> {
                st.activeWeaponType = "bomb";
                st.activeWeaponAmmo = 2;
            }
            case CLUSTER_BOMB -> {
                st.activeWeaponType = "cluster_bomb";
                st.activeWeaponAmmo = 1;
            }
            case CROSSBOW -> {
                st.activeWeaponType = "crossbow";
                st.activeWeaponAmmo = 3;
            }
            case ROLLER -> {
                st.rollerTicksRemaining = 100; // 5 seconds balanced trail
            }
            case BLOOPER -> {
                st.activeWeaponType = "blooper";
                st.activeWeaponAmmo = 1;
            }
            case UPGRADE_COOLDOWN -> {
                st.cooldownUpgradeLevel = Math.min(3, st.cooldownUpgradeLevel + 1);
            }
            case UPGRADE_RADIUS -> {
                st.radiusUpgradeLevel = Math.min(2, st.radiusUpgradeLevel + 1);
            }
        }
        syncPlayerInventory(player);
    }

    private BoxReward rollBalancedReward(PlayerPaintState st) {
        List<BoxReward> pool = new ArrayList<>(List.of(
                BoxReward.BOMB, BoxReward.BOMB,
                BoxReward.CLUSTER_BOMB,
                BoxReward.CROSSBOW, BoxReward.CROSSBOW,
                BoxReward.ROLLER
        ));
        if (!isSoloMode) {
            pool.add(BoxReward.BLOOPER);
            pool.add(BoxReward.BLOOPER);
        }
        if (st.cooldownUpgradeLevel < 3) {
            pool.add(BoxReward.UPGRADE_COOLDOWN);
        }
        if (st.radiusUpgradeLevel < 2) {
            pool.add(BoxReward.UPGRADE_RADIUS);
        }
        return pool.get(random.nextInt(pool.size()));
    }

    private void tickLuckyBoxes(ServerPlayer first, ServerPlayer second) {
        // Fewer Lucky Box spawns: max 1 in phase 1 & 2 (every 21s / 17s), max 2 in phase 3
        int maxBoxes = phaseNumber() >= 3 ? 2 : 1;
        int spawnInterval = phaseNumber() >= 2 ? 340 : 420;
        if (playTicks > 0 && playTicks % spawnInterval == 0 && luckyBoxes.size() < maxBoxes) {
            spawnLuckyBox();
        }

        Iterator<LuckyBox> it = luckyBoxes.iterator();
        while (it.hasNext()) {
            LuckyBox box = it.next();
            box.tick(level);

            ServerPlayer opener = null;
            if (first != null && isPlayerTouchingBox(first, box)) {
                opener = first;
            } else if (second != null && isPlayerTouchingBox(second, box)) {
                opener = second;
            }

            if (opener != null) {
                triggerLuckyBoxOpen(opener, box);
                box.discard(level);
                it.remove();
            }
        }
    }

    private boolean isPlayerTouchingBox(ServerPlayer player, LuckyBox box) {
        double dx = player.getX() - box.x;
        double dz = player.getZ() - box.z;
        double dy = (player.getY() + 0.9) - box.baseY;
        return (dx * dx + dz * dz <= 1.35 * 1.35) && Math.abs(dy) <= 1.75;
    }

    private boolean tryOpenBoxByRay(ServerPlayer player) {
        if (luckyBoxes.isEmpty()) return false;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(4.5));

        Iterator<LuckyBox> it = luckyBoxes.iterator();
        while (it.hasNext()) {
            LuckyBox box = it.next();
            AABB hitbox = box.getHitbox().inflate(0.25);
            if (hitbox.contains(eye) || hitbox.clip(eye, end).isPresent()) {
                triggerLuckyBoxOpen(player, box);
                box.discard(level);
                it.remove();
                return true;
            }
        }
        return false;
    }

    private void triggerLuckyBoxOpen(ServerPlayer player, LuckyBox box) {
        level.sendParticles(ParticleTypes.FIREWORK, box.x, box.baseY + 0.4, box.z, 18, 0.3, 0.3, 0.3, 0.08);
        level.playSound(null, box.x, box.baseY, box.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.2f, 1.4f);
        playPlayerSound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.9f, 1.3f);

        PlayerPaintState st = getState(player.getUUID());
        st.pendingReward = rollBalancedReward(st);
        st.rouletteTicks = ROULETTE_SPIN_TICKS;
        ServerPlayNetworking.send(player, new SyncPaintWarRoulettePayload(st.pendingReward.ordinal(), ROULETTE_SPIN_TICKS));
    }

    private void spawnLuckyBox() {
        if (boxCandidatePositions.isEmpty()) return;
        for (int attempt = 0; attempt < 25; attempt++) {
            BlockPos candidate = boxCandidatePositions.get(random.nextInt(boxCandidatePositions.size()));
            boolean tooClose = false;
            for (LuckyBox existing : luckyBoxes) {
                if (existing.floorPos.distSqr(candidate) < 36) {
                    tooClose = true;
                    break;
                }
            }
            if (!tooClose) {
                luckyBoxes.add(new LuckyBox(level, candidate));
                return;
            }
        }
    }

    public void onPlayerPunch(ServerPlayer player) {
        if (!isRoundActive() || inReveal || player == null || player.isDeadOrDying() || player.level() != level) return;
        if (!isPlayerParticipating(player.getUUID())) return;

        if (tryOpenBoxByRay(player)) return;
        tryHandPaint(player);
    }

    public void onLeftClickBlock(ServerPlayer player, BlockPos pos) {
        if (!isRoundActive() || inReveal || player == null || player.isDeadOrDying() || player.level() != level) return;
        if (!isPlayerParticipating(player.getUUID())) return;

        if (tryOpenBoxByRay(player)) return;
        paintWithHandAt(player, pos);
    }

    public boolean onUseItem(ServerPlayer player, InteractionHand hand) {
        if (!isRoundActive() || inReveal || player == null || player.isDeadOrDying() || player.level() != level) return false;
        if (!isPlayerParticipating(player.getUUID())) return false;

        if (tryOpenBoxByRay(player)) return true;

        ItemStack held = player.getItemInHand(hand);
        String type = getPaintWarItemType(held);

        if (type.isEmpty()) {
            // Do NOT paint on right-click with an empty hand (prevents right-click hold/spam exploit)
            return false;
        }

        PlayerPaintState st = getState(player.getUUID());
        TeamColor team = getTeamForPlayer(player);

        switch (type) {
            case "snowball" -> {
                if (st.snowballCooldown > 0) return true;
                int cd = getMaxSnowballCooldown(st);
                st.snowballCooldown = cd;
                player.getCooldowns().addCooldown(held, cd);

                Snowball snowball = new Snowball(level, player, new ItemStack(BroWarsMod.PAINT_BALL));
                snowball.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, 1.45f, 0.5f);
                snowball.addTag(TEMPORARY_TAG);
                level.addFreshEntity(snowball);
                activeProjectiles.add(new TrackedProjectile(snowball, player.getUUID(), team, ProjectileKind.SNOWBALL, getSnowballRadius(st)));

                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.7f, 1.2f);
                syncPlayerInventory(player);
                return true;
            }
            case "bomb" -> {
                if (st.weaponCooldown > 0 || st.activeWeaponAmmo <= 0) return true;
                st.weaponCooldown = 16;
                st.activeWeaponAmmo--;
                player.getCooldowns().addCooldown(held, 16);

                Snowball bomb = new Snowball(level, player, new ItemStack(BroWarsMod.PAINT_BOMB));
                bomb.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, 1.15f, 0.3f);
                bomb.addTag(TEMPORARY_TAG);
                level.addFreshEntity(bomb);
                activeProjectiles.add(new TrackedProjectile(bomb, player.getUUID(), team, ProjectileKind.BOMB, 2.05));

                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.WITCH_THROW, SoundSource.PLAYERS, 1.0f, 0.9f);
                syncPlayerInventory(player);
                return true;
            }
            case "cluster_bomb" -> {
                if (st.weaponCooldown > 0 || st.activeWeaponAmmo <= 0) return true;
                st.weaponCooldown = 18;
                st.activeWeaponAmmo--;
                player.getCooldowns().addCooldown(held, 18);

                Snowball cluster = new Snowball(level, player, new ItemStack(BroWarsMod.CLUSTER_BOMB));
                cluster.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, 1.2f, 0.3f);
                cluster.addTag(TEMPORARY_TAG);
                level.addFreshEntity(cluster);
                activeProjectiles.add(new TrackedProjectile(cluster, player.getUUID(), team, ProjectileKind.CLUSTER_MAIN, 1.5));

                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0f, 1.1f);
                syncPlayerInventory(player);
                return true;
            }
            case "crossbow" -> {
                if (st.weaponCooldown > 0 || st.activeWeaponAmmo <= 0) return true;
                st.weaponCooldown = 18;
                st.activeWeaponAmmo--;
                player.getCooldowns().addCooldown(held, 18);

                float[] yawOffsets = new float[]{-8.0f, 0.0f, 8.0f};
                for (float offset : yawOffsets) {
                    Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), null);
                    arrow.shootFromRotation(player, player.getXRot(), player.getYRot() + offset, 0.0f, 2.5f, 0.2f);
                    arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
                    arrow.addTag(TEMPORARY_TAG);
                    level.addFreshEntity(arrow);
                    activeProjectiles.add(new TrackedProjectile(arrow, player.getUUID(), team, ProjectileKind.CROSSBOW_ARROW, 1.65));
                }

                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.0f, 1.1f);
                syncPlayerInventory(player);
                return true;
            }
            case "blooper" -> {
                if (st.activeWeaponAmmo <= 0) return true;
                st.activeWeaponAmmo = 0;
                ServerPlayer opponent = getOpponent(player);
                if (opponent != null && opponent.isAlive()) {
                    // Long 8-second (160 ticks) Blooper ink blindness + darkness + 7-second slowness!
                    opponent.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 160, 0, false, false, true));
                    opponent.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 160, 0, false, false, true));
                    opponent.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 140, 0, false, false, true));
                    getState(opponent.getUUID()).blooperInkTicks = 160;
                    playPlayerSound(opponent, SoundEvents.SQUID_SQUIRT, 1.2f, 0.75f);
                    opponent.sendSystemMessage(Component.literal("§8§lFARB-BLOOPER! §7Deine Sicht wurde für 8 Sek. vollgespritzt!"), true);
                    DustParticleOptions inkDust = TeleportHelper.createDustParticle(team.hexColor, 1.6f);
                    level.sendParticles(inkDust, opponent.getX(), opponent.getEyeY(), opponent.getZ(), 32, 0.4, 0.4, 0.4, 0.05);
                    paintSphere(findSurfaceBlockUnderPlayer(opponent), 1.8, player, team, false, 0.9f);
                    player.sendSystemMessage(Component.literal("§aFarb-Blooper hat " + opponent.getPlainTextName() + " für 8s geblendet!"), true);
                } else {
                    paintSphere(findSurfaceBlockUnderPlayer(player), 2.2, player, team, false, 0.9f);
                }
                playPlayerSound(player, SoundEvents.SQUID_SQUIRT, 1.0f, 1.2f);
                syncPlayerInventory(player);
                return true;
            }
            case "special_strike" -> {
                st.hasSpecialItem = false;
                st.specialItemType = "";
                st.specialCharge = 0;
                player.getInventory().setItem(2, ItemStack.EMPTY);
                syncSpecialBarToPlayer(player, st);

                Vec3 eye = player.getEyePosition();
                Vec3 end = eye.add(player.getViewVector(1.0f).scale(22.0));
                BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
                BlockPos target = hit.getType() != HitResult.Type.MISS ? hit.getBlockPos() : findSurfaceBlockUnderPlayer(player);
                pendingStrikes.add(new PendingInkStrike(player.getUUID(), team, target));
                level.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.9f, 0.8f);
                message(team.chatPrefix + "§l" + player.getPlainTextName() + " §fhat die Ulti §d§lMega-Farbschlag §fgezündet!");
                return true;
            }
            default -> {
                return true;
            }
        }
    }

    private boolean tryHandPaint(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0f).scale(3.5));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.BLOCK) {
            return paintWithHandAt(player, hit.getBlockPos());
        }
        return false;
    }

    private boolean paintWithHandAt(ServerPlayer player, BlockPos pos) {
        // Single-block left-click hand painting without cooldown
        if (!originalArenaBlocks.containsKey(pos)) return false;

        TeamColor team = getTeamForPlayer(player);
        if (paintBlock(pos, player, team, true)) {
            level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.45f, 1.5f);
            return true;
        }
        return false;
    }

    private void tickProjectiles(ServerPlayer first, ServerPlayer second) {
        Iterator<TrackedProjectile> it = activeProjectiles.iterator();
        List<TrackedProjectile> newlySpawned = new ArrayList<>();

        while (it.hasNext()) {
            TrackedProjectile tp = it.next();
            Projectile ent = tp.entity;
            boolean impacted = !ent.isAlive() || ent.level() != level || ent.onGround() || ent.tickCount > 100;

            if (!impacted) {
                tp.lastPos = ent.position();
                tp.lastVel = ent.getDeltaMovement();
                DustParticleOptions dust = TeleportHelper.createDustParticle(tp.team.hexColor, 0.9f);
                level.sendParticles(dust, ent.getX(), ent.getY(), ent.getZ(), 2, 0.05, 0.05, 0.05, 0.01);
                continue;
            }

            Vec3 impactVec = ent.isAlive() ? ent.position() : tp.lastPos.add(tp.lastVel);
            BlockPos centerPos = BlockPos.containing(impactVec);
            if (tp.lastVel.lengthSqr() > 0.0001) {
                BlockHitResult rayHit = level.clip(new ClipContext(
                        tp.lastPos,
                        tp.lastPos.add(tp.lastVel.scale(1.6)),
                        ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE,
                        ent
                ));
                if (rayHit.getType() == HitResult.Type.BLOCK) {
                    centerPos = rayHit.getBlockPos();
                    impactVec = rayHit.getLocation();
                } else if (!originalArenaBlocks.containsKey(centerPos)) {
                    BlockPos forwardPos = BlockPos.containing(impactVec.add(tp.lastVel.normalize().scale(0.65)));
                    if (originalArenaBlocks.containsKey(forwardPos)) {
                        centerPos = forwardPos;
                    } else if (originalArenaBlocks.containsKey(centerPos.below())) {
                        centerPos = centerPos.below();
                    }
                }
            }

            ServerPlayer owner = server.getPlayerList().getPlayer(tp.ownerUuid);
            paintSphere(centerPos, tp.radius, owner, tp.team, true, 0.82f);
            if (level.getBlockState(centerPos).is(BroWarsMod.NET_BLOCK)) {
                dripPaintThroughNet(centerPos, tp.radius * 0.9, owner, tp.team, true, 0.82f);
            }

            DustParticleOptions splash = TeleportHelper.createDustParticle(tp.team.hexColor, 1.3f);
            level.sendParticles(splash, impactVec.x, impactVec.y + 0.2, impactVec.z,
                    tp.kind == ProjectileKind.BOMB ? 24 : 10, 0.4, 0.3, 0.4, 0.05);

            SoundEvent impactSound = (tp.kind == ProjectileKind.BOMB || tp.kind == ProjectileKind.CLUSTER_MAIN)
                    ? SoundEvents.GENERIC_SPLASH
                    : SoundEvents.SLIME_SQUISH;
            level.playSound(null, impactVec.x, impactVec.y, impactVec.z, impactSound, SoundSource.PLAYERS, 0.8f, 1.25f);

            // Check non-lethal opponent hit ("Stör-Effekt")
            ServerPlayer opponent = tp.ownerUuid.equals(p1Uuid) ? second : first;
            if (opponent != null && opponent.isAlive()) {
                double hitDist = tp.radius + 0.85;
                if (opponent.position().distanceToSqr(impactVec) <= hitDist * hitDist) {
                    applyOpponentPaintHit(opponent, impactVec, tp.team);
                }
            }

            // Cluster Bomb spawns 4 mini droplets on impact
            if (tp.kind == ProjectileKind.CLUSTER_MAIN && owner != null) {
                double[][] dirs = new double[][]{{0.30, 0}, {-0.30, 0}, {0, 0.30}, {0, -0.30}};
                for (double[] d : dirs) {
                    Snowball mini = new Snowball(level, impactVec.x, impactVec.y + 0.4, impactVec.z, new ItemStack(BroWarsMod.PAINT_BALL));
                    mini.setOwner(owner);
                    mini.setDeltaMovement(d[0], 0.34, d[1]);
                    mini.addTag(TEMPORARY_TAG);
                    level.addFreshEntity(mini);
                    newlySpawned.add(new TrackedProjectile(mini, tp.ownerUuid, tp.team, ProjectileKind.CLUSTER_MINI, 1.2));
                }
            }

            if (ent.isAlive()) ent.discard();
            it.remove();
        }

        activeProjectiles.addAll(newlySpawned);
    }

    private void applyOpponentPaintHit(ServerPlayer victim, Vec3 impactVec, TeamColor attackerTeam) {
        Vec3 diff = victim.position().subtract(impactVec);
        double horiz = Math.hypot(diff.x, diff.z);
        double kx = horiz > 0.001 ? (diff.x / horiz) * 0.42 : 0.0;
        double kz = horiz > 0.001 ? (diff.z / horiz) * 0.42 : 0.0;
        Vec3 motion = new Vec3(kx, 0.24, kz);
        victim.setDeltaMovement(motion);
        victim.connection.send(new ClientboundSetEntityMotionPacket(victim.getId(), motion));

        victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1, false, false, true));
        DustParticleOptions hitDust = TeleportHelper.createDustParticle(attackerTeam.hexColor, 1.4f);
        level.sendParticles(hitDust, victim.getX(), victim.getY() + 1.0, victim.getZ(), 14, 0.3, 0.4, 0.3, 0.04);
        playPlayerSound(victim, SoundEvents.SLIME_ATTACK, 0.9f, 1.1f);
        victim.sendSystemMessage(Component.literal(attackerTeam.chatPrefix + "Von gegnerischer Farbe getroffen!"), true);
    }

    private void tickSpecials(ServerPlayer first) {
        // 1. Ink Storm Clouds (multi-level downward search, passes through NetBlock!)
        Iterator<InkStormCloud> stormIt = activeStorms.iterator();
        while (stormIt.hasNext()) {
            InkStormCloud storm = stormIt.next();
            storm.ticksRemaining--;
            storm.x += storm.vx;
            storm.z += storm.vz;

            DustParticleOptions cloudDust = TeleportHelper.createDustParticle(storm.team.hexColor, 1.6f);
            level.sendParticles(cloudDust, storm.x, storm.y, storm.z, 8, 1.6, 0.3, 1.6, 0.02);
            level.sendParticles(ParticleTypes.CLOUD, storm.x, storm.y + 0.3, storm.z, 3, 1.4, 0.2, 1.4, 0.01);

            if (storm.ticksRemaining % 4 == 0) {
                int dropX = (int) Math.floor(storm.x + (random.nextDouble() - 0.5) * 3.2);
                int dropZ = (int) Math.floor(storm.z + (random.nextDouble() - 0.5) * 3.2);
                int startY = (int) Math.floor(storm.y);
                BlockPos.MutableBlockPos scan = new BlockPos.MutableBlockPos();
                for (int y = startY; y >= Math.max(level.getMinY(), startY - 24); y--) {
                    scan.set(dropX, y, dropZ);
                    if (originalArenaBlocks.containsKey(scan)) {
                        ServerPlayer owner = server.getPlayerList().getPlayer(storm.ownerUuid);
                        paintSphere(scan.immutable(), 1.25, owner, storm.team, false, 0.9f);
                        break;
                    }
                    BlockState obstacle = level.getBlockState(scan);
                    if (!obstacle.isAir() && !obstacle.is(BroWarsMod.NET_BLOCK) && !obstacle.getCollisionShape(level, scan).isEmpty()) {
                        break;
                    }
                }
            }

            if (storm.ticksRemaining <= 0) {
                stormIt.remove();
            }
        }

        // 2. Pending Mega Ink Strikes (balanced 3.6 radius)
        Iterator<PendingInkStrike> strikeIt = pendingStrikes.iterator();
        while (strikeIt.hasNext()) {
            PendingInkStrike strike = strikeIt.next();
            strike.ticksRemaining--;

            double cx = strike.targetPos.getX() + 0.5;
            double cy = strike.targetPos.getY() + 1.1;
            double cz = strike.targetPos.getZ() + 0.5;
            DustParticleOptions ringDust = TeleportHelper.createDustParticle(strike.team.hexColor, 1.4f);

            for (int i = 0; i < 12; i++) {
                double angle = (i * Math.PI * 2.0) / 12.0 + (strike.ticksRemaining * 0.15);
                double rx = cx + Math.cos(angle) * 3.5;
                double rz = cz + Math.sin(angle) * 3.5;
                level.sendParticles(ringDust, rx, cy, rz, 1, 0, 0.1, 0, 0);
            }

            if (strike.ticksRemaining % 5 == 0) {
                level.playSound(null, cx, cy, cz, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.9f, 1.0f + (30 - strike.ticksRemaining) * 0.03f);
            }

            if (strike.ticksRemaining <= 0) {
                ServerPlayer owner = server.getPlayerList().getPlayer(strike.ownerUuid);
                paintSphere(strike.targetPos, 3.6, owner, strike.team, false, 0.85f);
                if (level.getBlockState(strike.targetPos).is(BroWarsMod.NET_BLOCK)) {
                    dripPaintThroughNet(strike.targetPos, 3.2, owner, strike.team, false, 0.85f);
                }
                level.sendParticles(ringDust, cx, cy + 0.5, cz, 40, 1.6, 0.8, 1.6, 0.08);
                level.playSound(null, cx, cy, cz, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.9f, 1.0f);
                strikeIt.remove();
            }
        }
    }

    private void dripPaintThroughNet(BlockPos netPos, double radius, ServerPlayer painter, TeamColor team, boolean countForSpecial, float edgeChance) {
        BlockPos.MutableBlockPos scan = new BlockPos.MutableBlockPos();
        int startY = netPos.getY() - 1;
        for (int y = startY; y >= Math.max(level.getMinY(), startY - 16); y--) {
            scan.set(netPos.getX(), y, netPos.getZ());
            if (originalArenaBlocks.containsKey(scan)) {
                paintSphere(scan.immutable(), radius, painter, team, countForSpecial, edgeChance);
                break;
            }
            BlockState obstacle = level.getBlockState(scan);
            if (!obstacle.isAir() && !obstacle.is(BroWarsMod.NET_BLOCK) && !obstacle.getCollisionShape(level, scan).isEmpty()) {
                break;
            }
        }
    }

    private int paintSphere(BlockPos center, double radius, ServerPlayer painter, TeamColor team, boolean countForSpecial, float edgeChance) {
        int rCeil = (int) Math.ceil(radius);
        double rSq = radius * radius;
        double innerSq = Math.max(0, (radius - 0.75) * (radius - 0.75));
        int paintedCount = 0;

        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        for (int dx = -rCeil; dx <= rCeil; dx++) {
            for (int dy = -rCeil; dy <= rCeil; dy++) {
                for (int dz = -rCeil; dz <= rCeil; dz++) {
                    double distSq = dx * dx + dy * dy + dz * dz;
                    if (distSq > rSq) continue;
                    if (distSq > innerSq && edgeChance < 1.0f && random.nextFloat() > edgeChance) continue;

                    mpos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (paintBlock(mpos, painter, team, countForSpecial)) {
                        paintedCount++;
                    }
                }
            }
        }
        return paintedCount;
    }

    private boolean paintBlock(BlockPos pos, ServerPlayer painter, TeamColor team, boolean countForSpecial) {
        if (!originalArenaBlocks.containsKey(pos)) return false;
        BlockState current = level.getBlockState(pos);
        if (team.matchesTeamConcrete(current)) return false;

        BlockState paintedState = team.applyTeamColor(current);
        level.setBlock(pos, paintedState, 3);

        if (countForSpecial && painter != null) {
            PlayerPaintState st = getState(painter.getUUID());
            if (!st.hasSpecialItem) {
                st.specialCharge++;
                if (st.specialCharge >= SPECIAL_BLOCKS_REQUIRED) {
                    st.specialCharge = SPECIAL_BLOCKS_REQUIRED;
                    st.hasSpecialItem = true;
                    st.specialItemType = "special_strike";
                    syncSpecialBarToPlayer(painter, st);
                    syncPlayerInventory(painter);
                    playPlayerSound(painter, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
                    painter.sendSystemMessage(Component.literal("§d§lULTI BEREIT! §fNutze Slot 3 für deinen Mega-Farbschlag!"), true);
                } else {
                    syncSpecialBarToPlayer(painter, st);
                }
            }
        }
        return true;
    }

    private void startRevealPhase(ServerPlayer first, ServerPlayer second) {
        inReveal = true;
        revealTicks = REVEAL_DURATION_TICKS;
        clearActiveEntities();
        if (first != null && first.getAttribute(Attributes.SNEAKING_SPEED) != null) {
            first.getAttribute(Attributes.SNEAKING_SPEED).setBaseValue(0.3);
        }
        if (second != null && second.getAttribute(Attributes.SNEAKING_SPEED) != null) {
            second.getAttribute(Attributes.SNEAKING_SPEED).setBaseValue(0.3);
        }
        setCenterTitle("§e§lAUSWERTUNG", "", 0xFFFBBF24, REVEAL_DURATION_TICKS + 10);
        playSound(SoundEvents.NOTE_BLOCK_PLING, 1.0f);
        MinigameManager.getInstance().broadcastHud(server, getHudState());
    }

    private void tickRevealPhase(ServerPlayer first, ServerPlayer second) {
        if (first != null) first.setDeltaMovement(0, 0, 0);
        if (second != null) second.setDeltaMovement(0, 0, 0);

        if (revealTicks % 4 == 0 && revealTicks > 10) {
            float pitch = 0.8f + ((REVEAL_DURATION_TICKS - revealTicks) / (float) REVEAL_DURATION_TICKS) * 1.1f;
            playSound(SoundEvents.NOTE_BLOCK_SNARE, pitch);
        }

        revealTicks--;
        if (revealTicks <= 0) {
            inReveal = false;
            concludeRoundByPaintPercentage();
        }
    }

    private void concludeRoundByPaintPercentage() {
        int total = Math.max(1, originalArenaBlocks.size());
        int p1Count = 0;
        int p2Count = 0;

        for (BlockPos pos : originalArenaBlocks.keySet()) {
            BlockState st = level.getBlockState(pos);
            if (p1Team.matchesTeamConcrete(st)) p1Count++;
            else if (p2Team.matchesTeamConcrete(st)) p2Count++;
        }

        double p1Pct = (p1Count * 100.0) / total;
        double p2Pct = (p2Count * 100.0) / total;

        if (isSoloMode) {
            finishRound(true, false, String.format(Locale.US, "%.1f%%", p1Pct));
            setCenterTitle("§6§l" + String.format(Locale.US, "%.1f%%", p1Pct),
                    "§7" + p1Count + " / " + total, 0xFFFFD700, 90);
            return;
        }

        String pctComparison = String.format(Locale.US, "%s%.1f%% §8: %s%.1f%%",
                p1Team.chatPrefix, p1Pct, p2Team.chatPrefix, p2Pct);

        boolean p1Lost;
        boolean p2Lost;
        if (p1Count > p2Count) {
            p1Lost = false;
            p2Lost = true;
        } else if (p2Count > p1Count) {
            p1Lost = true;
            p2Lost = false;
        } else {
            p1Lost = true;
            p2Lost = true;
        }

        String reason = String.format(Locale.US, "%.1f%% : %.1f%%", Math.max(p1Pct, p2Pct), Math.min(p1Pct, p2Pct));
        finishRound(p1Lost, p2Lost, reason);

        if (p1Count != p2Count) {
            String winner = p1Count > p2Count ? p1Name : p2Name;
            TeamColor winTeam = p1Count > p2Count ? p1Team : p2Team;
            String title = score.isFinished()
                    ? winTeam.chatPrefix + "§l👑 " + winner
                    : winTeam.chatPrefix + "§l" + winner;
            setCenterTitle(title, pctComparison,
                    score.isFinished() ? 0xFFFFD700 : winTeam.hexColor, score.isFinished() ? 110 : 70);
        } else {
            setCenterTitle("§e§lGLEICHSTAND", pctComparison, 0xFFFBBF24, 60);
        }
    }

    private void playPlayerSound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        if (player == null) return;
        player.connection.send(new ClientboundSoundPacket(
                BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound),
                SoundSource.PLAYERS,
                player.getX(), player.getY(), player.getZ(),
                volume, pitch, player.getRandom().nextLong()
        ));
    }

    private void playPlayerSound(ServerPlayer player, Holder<SoundEvent> sound, float volume, float pitch) {
        if (player == null) return;
        player.connection.send(new ClientboundSoundPacket(
                sound,
                SoundSource.PLAYERS,
                player.getX(), player.getY(), player.getZ(),
                volume, pitch, player.getRandom().nextLong()
        ));
    }
}
