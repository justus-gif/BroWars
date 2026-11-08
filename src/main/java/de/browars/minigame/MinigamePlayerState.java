package de.browars.minigame;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.List;

/** Keeps temporary game equipment separate from a participant's own belongings. */
public final class MinigamePlayerState {
    private static final String GAME_ITEM = "browars_game_item";
    private final List<ItemStack> inventory = new ArrayList<>();
    private final List<MobEffectInstance> effects = new ArrayList<>();
    private final int selectedSlot;
    private final GameType gameMode;
    private final double attackSpeed;
    private final double maxHealth;
    private final double sneakingSpeed;
    private final float health;
    private final int food;
    private final float saturation;
    private final int expLevel;
    private final float expProgress;
    private final int totalExp;

    public MinigamePlayerState(ServerPlayer player) {
        player.closeContainer();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            inventory.add(player.getInventory().getItem(i).copy());
        }
        for (var effect : player.getActiveEffects()) effects.add(new MobEffectInstance(effect));
        selectedSlot = player.getInventory().getSelectedSlot();
        gameMode = player.gameMode.getGameModeForPlayer();
        var attribute = player.getAttribute(Attributes.ATTACK_SPEED);
        attackSpeed = attribute == null ? 4.0 : attribute.getBaseValue();
        var maxHealthAttr = player.getAttribute(Attributes.MAX_HEALTH);
        maxHealth = maxHealthAttr == null ? 20.0 : maxHealthAttr.getBaseValue();
        var sneakAttr = player.getAttribute(Attributes.SNEAKING_SPEED);
        sneakingSpeed = sneakAttr == null ? 0.3 : sneakAttr.getBaseValue();
        health = player.getHealth();
        food = player.getFoodData().getFoodLevel();
        saturation = player.getFoodData().getSaturationLevel();
        expLevel = player.experienceLevel;
        expProgress = player.experienceProgress;
        totalExp = player.totalExperience;
        player.getInventory().clearContent();
        player.removeAllEffects();
        player.setExperienceLevels(0);
        player.setExperiencePoints(0);
        player.experienceProgress = 0f;
    }

    public void restore(ServerPlayer player) {
        player.closeContainer();
        player.getInventory().clearContent();
        for (int i = 0; i < inventory.size(); i++) player.getInventory().setItem(i, inventory.get(i).copy());
        player.getInventory().setSelectedSlot(selectedSlot);
        player.removeAllEffects();
        for (var effect : effects) player.addEffect(new MobEffectInstance(effect));
        var attribute = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attribute != null) attribute.setBaseValue(attackSpeed);
        var maxHealthAttr = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttr != null) maxHealthAttr.setBaseValue(maxHealth);
        var sneakAttr = player.getAttribute(Attributes.SNEAKING_SPEED);
        if (sneakAttr != null) sneakAttr.setBaseValue(sneakingSpeed);
        player.setGameMode(gameMode);
        player.setHealth(Math.min(health, player.getMaxHealth()));
        player.getFoodData().setFoodLevel(food);
        player.getFoodData().setSaturation(saturation);
        player.setExperienceLevels(expLevel);
        player.totalExperience = totalExp;
        player.experienceProgress = expProgress;
        player.setDeltaMovement(0, 0, 0);
        player.fallDistance = 0;
        player.clearFire();
        player.inventoryMenu.broadcastChanges();
    }

    /**
     * Removes leftovers of an interrupted game (e.g. server crash): game-owned items and a
     * modified attack speed, both of which would otherwise be saved permanently with the player.
     */
    public static void cleanupLeftovers(ServerPlayer player) {
        boolean changed = false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (isGameItem(player.getInventory().getItem(i))) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
                changed = true;
            }
        }
        var attribute = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attribute != null && attribute.getBaseValue() != 4.0) attribute.setBaseValue(4.0);
        var maxHealthAttr = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttr != null && maxHealthAttr.getBaseValue() != 20.0) maxHealthAttr.setBaseValue(20.0);
        var sneakAttr = player.getAttribute(Attributes.SNEAKING_SPEED);
        if (sneakAttr != null && sneakAttr.getBaseValue() != 0.3) sneakAttr.setBaseValue(0.3);
        if (changed) player.inventoryMenu.broadcastChanges();
    }

    public static void markGameItem(ItemStack item) {
        CustomData existing = item.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = existing != null ? existing.copyTag() : new CompoundTag();
        tag.putBoolean(GAME_ITEM, true);
        item.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static boolean isGameItem(ItemStack item) {
        CustomData data = item.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBooleanOr(GAME_ITEM, false);
    }
}
