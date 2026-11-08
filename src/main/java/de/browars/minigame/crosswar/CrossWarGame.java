package de.browars.minigame.crosswar;

import de.browars.BroWarsMod;
import de.browars.mixin.EntityAccessor;
import de.browars.minigame.AbstractDuelGame;
import de.browars.minigame.MinigamePlayerState;
import de.browars.minigame.TeamColor;
import de.browars.network.SyncCrossWarPlayerPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CrossWarGame extends AbstractDuelGame<CrossWarArenaData> {

    public static final int MAX_AMMO = 5;
    public static final int SHOOT_COOLDOWN_TICKS = 10; // 0.5s
    public static final int TICKS_PER_ARROW = 10;      // 0.5s per arrow, total 2.5s for 5
    public static final int RELOAD_TOTAL_TICKS = MAX_AMMO * TICKS_PER_ARROW; // 50 ticks
    public static final float HIT_DAMAGE = 2.0f;       // 1 full heart per shot

    public static class CombatState {
        public int ammo = MAX_AMMO;
        public int shootCooldown = 0;
        public int reloadTicks = 0;
        public boolean holdingReload = false;
        public boolean isReloading = false;
    }

    private final Map<UUID, CombatState> combatStates = new HashMap<>();
    private final List<Arrow> activeArrows = new java.util.ArrayList<>();

    public CrossWarGame(MinecraftServer server, ServerLevel level, CrossWarArenaData arena,
                        ServerPlayer first, ServerPlayer second, TeamColor firstTeam, TeamColor secondTeam) {
        super(server, level, arena, first, second, firstTeam, secondTeam);
    }

    @Override
    public String getId() {
        return "crosswar";
    }

    @Override
    public String getDisplayName() {
        return "Cross War";
    }

    @Override
    protected String startInstruction() {
        return "Triff deinen Gegner 3-mal zum Rundensieg!";
    }

    @Override
    protected int targetScore() {
        return 3;
    }

    @Override
    protected boolean hasSpawnCampEjector() {
        return true;
    }

    @Override
    protected boolean isPlayerInSpawnChamber(ServerPlayer player, int spawnNumber) {
        return arena.isInSpawnChamber(player, spawnNumber);
    }

    @Override
    protected boolean shouldHealEveryTick() {
        return phase != Phase.PLAYING;
    }

    public CombatState getCombatState(UUID uuid) {
        return combatStates.computeIfAbsent(uuid, k -> new CombatState());
    }

    private void playPlayerSound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.connection.send(new ClientboundSoundPacket(
                BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound),
                SoundSource.PLAYERS,
                player.getX(), player.getY(), player.getZ(),
                volume, pitch, player.getRandom().nextLong()
        ));
    }

    private void playPlayerSound(ServerPlayer player, Holder<SoundEvent> sound, float volume, float pitch) {
        player.connection.send(new ClientboundSoundPacket(
                sound,
                SoundSource.PLAYERS,
                player.getX(), player.getY(), player.getZ(),
                volume, pitch, player.getRandom().nextLong()
        ));
    }

    private void updateCrossbowItem(ServerPlayer player, int ammo) {
        if (player == null || player.isDeadOrDying()) return;
        ItemStack stack = player.getMainHandItem();
        if (stack.is(BroWarsMod.CROSSWAR_CROSSBOW)) {
            if (ammo > 0) {
                stack.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(List.of(new ItemStack(Items.ARROW))));
            } else {
                stack.remove(DataComponents.CHARGED_PROJECTILES);
            }
        }
        ItemStack slot0 = player.getInventory().getItem(0);
        if (slot0.is(BroWarsMod.CROSSWAR_CROSSBOW) && slot0 != stack) {
            if (ammo > 0) {
                slot0.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(List.of(new ItemStack(Items.ARROW))));
            } else {
                slot0.remove(DataComponents.CHARGED_PROJECTILES);
            }
        }
    }

    @Override
    protected void equipExtra(ServerPlayer player) {
        // Enforce 3 hearts (6.0 HP)
        var maxHealthAttr = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttr != null && maxHealthAttr.getBaseValue() != 6.0) {
            maxHealthAttr.setBaseValue(6.0);
        }

        // Give Cross War Crossbow
        ItemStack current = player.getInventory().getItem(0);
        if (!current.is(BroWarsMod.CROSSWAR_CROSSBOW)) {
            ItemStack weapon = new ItemStack(BroWarsMod.CROSSWAR_CROSSBOW);
            weapon.set(DataComponents.CUSTOM_NAME, Component.literal("§6§lCross War Armbrust"));
            weapon.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(List.of(new ItemStack(Items.ARROW))));
            MinigamePlayerState.markGameItem(weapon);
            player.getInventory().setItem(0, weapon);
            player.getInventory().setSelectedSlot(0);
        } else {
            player.getInventory().setSelectedSlot(0);
            CombatState state = getCombatState(player.getUUID());
            updateCrossbowItem(player, state.isReloading ? 0 : state.ammo);
        }
    }

    @Override
    protected void onRoundStarted() {
        combatStates.clear();
        for (UUID id : lastPlayersKeySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                var maxHealthAttr = player.getAttribute(Attributes.MAX_HEALTH);
                if (maxHealthAttr != null) maxHealthAttr.setBaseValue(6.0);
                player.setHealth(6.0f);
                CombatState state = getCombatState(id);
                state.ammo = MAX_AMMO;
                state.shootCooldown = 0;
                state.reloadTicks = 0;
                state.holdingReload = false;
                state.isReloading = false;
                updateCrossbowItem(player, MAX_AMMO);
                syncPlayer(player);
            }
        }
    }

    private Iterable<UUID> lastPlayersKeySet() {
        return isSoloMode ? List.of(p1Uuid) : List.of(p1Uuid, p2Uuid);
    }

    public void onShootRequest(ServerPlayer shooter) {
        if (!isRoundActive() || shooter.isDeadOrDying() || shooter.level() != level) return;
        if (!isPlayerParticipating(shooter.getUUID())) return;

        CombatState state = getCombatState(shooter.getUUID());
        if (state.shootCooldown > 0) return;
        if (state.isReloading) return;

        if (state.ammo <= 0) {
            playPlayerSound(shooter, SoundEvents.DISPENSER_FAIL, 0.8f, 1.8f);
            shooter.sendSystemMessage(Component.literal("§c§lMagazin leer! Halte [R] oder Rechtsklick zum Nachladen!"), true);
            return;
        }

        state.ammo--;
        state.shootCooldown = SHOOT_COOLDOWN_TICKS;
        updateCrossbowItem(shooter, state.ammo);

        // Spawn high-velocity projectile
        Arrow arrow = new Arrow(level, shooter, new ItemStack(Items.ARROW), null);
        arrow.shootFromRotation(shooter, shooter.getXRot(), shooter.getYRot(), 0.0f, 4.2f, 0.1f);
        arrow.setBaseDamage(HIT_DAMAGE);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        arrow.addTag("crosswar_arrow");
        level.addFreshEntity(arrow);
        activeArrows.add(arrow);

        level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(),
                SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.0f, 1.2f);

        syncPlayer(shooter);
    }

    public void onReloadRequest(ServerPlayer player, boolean holding) {
        if (!isPlayerParticipating(player.getUUID())) return;
        CombatState state = getCombatState(player.getUUID());
        state.holdingReload = holding;
        if (holding) {
            if (state.ammo < MAX_AMMO && !state.isReloading) {
                state.isReloading = true;
                state.reloadTicks = 0;
                updateCrossbowItem(player, 0); // Show pulling/charging animation during reload
            }
        } else {
            if (state.isReloading) {
                state.isReloading = false;
                state.reloadTicks = 0;
                player.removeEffect(MobEffects.SLOWNESS);
                updateCrossbowItem(player, state.ammo);
                if (state.ammo < MAX_AMMO) {
                    player.sendSystemMessage(Component.literal("§7Nachladen pausiert (" + state.ammo + "/" + MAX_AMMO + ")"), true);
                }
                syncPlayer(player);
            }
        }
    }

    @Override
    protected void tickRound(ServerPlayer first, ServerPlayer second) {
        if (!arena.containsPlayer(first) || first.getY() < level.getMinY() + 1) {
            finishRound(true, false, "Aus der Arena gefallen!");
            return;
        }
        if (second != null && (!arena.containsPlayer(second) || second.getY() < level.getMinY() + 1)) {
            finishRound(false, true, "Aus der Arena gefallen!");
            return;
        }

        tickPlayerCombat(first, 1);
        if (second != null) {
            tickPlayerCombat(second, 2);
        }

        // Particle trail and cleanup for tracked active arrows (avoiding full-world scan)
        var arrowIterator = activeArrows.iterator();
        while (arrowIterator.hasNext()) {
            Arrow activeArrow = arrowIterator.next();
            if (!activeArrow.isAlive() || activeArrow.level() != level) {
                arrowIterator.remove();
                continue;
            }
            if (activeArrow.onGround() || activeArrow.tickCount > 80) {
                if (isSoloMode && activeArrow.onGround()) {
                    // Check if hit a target block
                    net.minecraft.core.BlockPos hitPos = activeArrow.blockPosition();
                    if (level.getBlockState(hitPos).is(Blocks.TARGET)) {
                        level.sendParticles(ParticleTypes.FIREWORK, activeArrow.getX(), activeArrow.getY(), activeArrow.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
                        playPlayerSound(first, SoundEvents.ARROW_HIT_PLAYER, 1.0f, 1.2f);
                        first.sendSystemMessage(Component.literal("§a§lVolltreffer auf Zielscheibe!"), true);
                    }
                }
                activeArrow.discard();
                arrowIterator.remove();
            } else {
                level.sendParticles(ParticleTypes.CRIT, activeArrow.getX(), activeArrow.getY(), activeArrow.getZ(), 2, 0.02, 0.02, 0.02, 0.01);
            }
        }
    }

    private void tickPlayerCombat(ServerPlayer player, int spawnNumber) {
        if (player == null || player.isDeadOrDying() || player.level() != level) return;
        CombatState state = getCombatState(player.getUUID());

        if (state.shootCooldown > 0) {
            state.shootCooldown--;
        }

        if (state.isReloading) {
            state.reloadTicks++;
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 10, 0, false, false, false));

            if (state.reloadTicks == 1) {
                playPlayerSound(player, SoundEvents.CROSSBOW_LOADING_START, 0.9f, 1.0f);
            }

            // Each TICKS_PER_ARROW (10 ticks = 0.5s), one arrow is loaded into the magazine!
            if (state.reloadTicks >= TICKS_PER_ARROW) {
                state.reloadTicks = 0;
                state.ammo++;

                if (state.ammo >= MAX_AMMO) {
                    state.ammo = MAX_AMMO;
                    state.isReloading = false;
                    state.holdingReload = false;
                    player.removeEffect(MobEffects.SLOWNESS);
                    updateCrossbowItem(player, MAX_AMMO);
                    playPlayerSound(player, SoundEvents.CROSSBOW_LOADING_END, 1.0f, 1.2f);
                    player.sendSystemMessage(Component.literal("§a§lMagazin voll! (5/5)"), true);
                    syncPlayer(player);
                    return;
                } else {
                    playPlayerSound(player, SoundEvents.CROSSBOW_LOADING_MIDDLE, 0.9f, 1.0f + (state.ammo * 0.1f));
                    player.sendSystemMessage(Component.literal("§e§l+1 Pfeil geladen! §7(" + state.ammo + "/" + MAX_AMMO + ")"), true);
                    syncPlayer(player);
                }
            } else if (state.reloadTicks % 3 == 0) {
                syncPlayer(player);
            }
        }
    }

    public boolean handleDamage(Player victim, DamageSource source, float amount) {
        if (!isRoundActive() || !(victim instanceof ServerPlayer serverVictim)) return false;
        if (!isPlayerParticipating(serverVictim.getUUID())) return false;

        // In spawn chamber: immune to damage ONLY during initial spawn protection (first 4 seconds / 80 ticks)
        if (playTicks <= 80) {
            int spawnNumber = serverVictim.getUUID().equals(p1Uuid) ? 1 : 2;
            if (isPlayerInSpawnChamber(serverVictim, spawnNumber)) {
                return false;
            }
        }

        if (source.getDirectEntity() instanceof AbstractArrow arrow) {
            if (arrow.getOwner() instanceof ServerPlayer shooter && isPlayerParticipating(shooter.getUUID())) {
                if (shooter.getUUID().equals(serverVictim.getUUID())) return false;

                // Valid hit!
                playPlayerSound(shooter, SoundEvents.ARROW_HIT_PLAYER, 1.0f, 1.2f);
                playPlayerSound(shooter, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f, 1.8f);

                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                        serverVictim.getX(), serverVictim.getY() + 1.0, serverVictim.getZ(),
                        6, 0.2, 0.3, 0.2, 0.1);

                float newHealth = serverVictim.getHealth() - HIT_DAMAGE;
                ((EntityAccessor) serverVictim).setInvulnerableTime(8);

                if (newHealth <= 0.05f) {
                    serverVictim.setHealth(6.0f); // Never set health to 0 to prevent death screen!
                    boolean p1Lost = serverVictim.getUUID().equals(p1Uuid);
                    finishRound(p1Lost, !p1Lost, "Abgeschossen!");
                } else {
                    serverVictim.setHealth(newHealth);
                    playPlayerSound(serverVictim, SoundEvents.PLAYER_HURT, 1.0f, 1.0f);
                }

                arrow.discard();
                return true;
            }
        }

        return false;
    }

    public void syncPlayer(ServerPlayer player) {
        CombatState state = getCombatState(player.getUUID());
        ServerPlayNetworking.send(player, new SyncCrossWarPlayerPayload(state.ammo, state.reloadTicks, state.isReloading));
    }

    @Override
    protected void clearRound() {
        for (UUID id : lastPlayersKeySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                player.removeEffect(MobEffects.SLOWNESS);
                updateCrossbowItem(player, MAX_AMMO);
                ServerPlayNetworking.send(player, new SyncCrossWarPlayerPayload(MAX_AMMO, 0, false));
            }
        }
        activeArrows.forEach(net.minecraft.world.entity.Entity::discard);
        activeArrows.clear();
        combatStates.clear();
    }

    @Override
    protected void restoreArena() {
        activeArrows.forEach(net.minecraft.world.entity.Entity::discard);
        activeArrows.clear();
    }
}
