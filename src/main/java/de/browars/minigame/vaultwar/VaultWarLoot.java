package de.browars.minigame.vaultwar;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.ArrayList;
import java.util.List;

public final class VaultWarLoot {

    private VaultWarLoot() {}

    public static List<ItemStack> generateNormalLoot(ServerLevel level, RandomSource random) {
        List<ItemStack> drops = new ArrayList<>();
        RegistryAccess access = level.registryAccess();

        // 1. Guaranteed primary tactical item
        drops.add(pickNormalPrimary(access, random));

        // 2. 65% chance for secondary tactical stack (ammo, webs, utility, food)
        if (random.nextFloat() < 0.65f) {
            drops.add(pickNormalSecondary(random));
        }

        return drops;
    }

    public static List<ItemStack> generateOminousLoot(ServerLevel level, RandomSource random) {
        List<ItemStack> drops = new ArrayList<>();
        RegistryAccess access = level.registryAccess();

        // 1. Guaranteed high-tier OP item
        drops.add(pickOminousPrimary(access, random));

        // 2. 60% chance for secondary bonus OP stack
        if (random.nextFloat() < 0.60f) {
            drops.add(pickOminousSecondary(random));
        }

        return drops;
    }

    private static ItemStack pickNormalPrimary(RegistryAccess access, RandomSource random) {
        int category = random.nextInt(4); // 0: Weapon/Shield, 1: Armor, 2: Book, 3: Potion/Apple
        switch (category) {
            case 0 -> { // Weapons & Shields
                int w = random.nextInt(10);
                return switch (w) {
                    case 0 -> enchantedItem(access, Items.IRON_SWORD, Enchantments.SHARPNESS, 1 + random.nextInt(2));
                    case 1 -> enchantedItem(access, Items.IRON_SWORD, Enchantments.FIRE_ASPECT, 1);
                    case 2 -> enchantedItem(access, Items.IRON_SWORD, Enchantments.KNOCKBACK, 1);
                    case 3 -> new ItemStack(Items.IRON_AXE);
                    case 4 -> enchantedItem(access, Items.BOW, Enchantments.POWER, 1 + random.nextInt(2));
                    case 5 -> enchantedItem(access, Items.CROSSBOW, Enchantments.QUICK_CHARGE, 1 + random.nextInt(2));
                    case 6 -> enchantedItem(access, Items.CROSSBOW, Enchantments.MULTISHOT, 1);
                    case 7 -> new ItemStack(Items.SHIELD);
                    case 8 -> new ItemStack(Items.WIND_CHARGE, 4 + random.nextInt(5));
                    case 9 -> {
                        // Trident is nerfed: rare, and max Riptide 1 or Loyalty 1
                        if (random.nextFloat() < 0.20f) {
                            yield enchantedItem(access, Items.TRIDENT, Enchantments.RIPTIDE, 1);
                        } else if (random.nextFloat() < 0.40f) {
                            yield enchantedItem(access, Items.TRIDENT, Enchantments.LOYALTY, 1);
                        } else {
                            yield new ItemStack(Items.IRON_SWORD);
                        }
                    }
                    default -> new ItemStack(Items.IRON_SWORD);
                };
            }
            case 1 -> { // Armor
                int a = random.nextInt(8);
                return switch (a) {
                    case 0 -> enchantedItem(access, Items.IRON_HELMET, Enchantments.PROTECTION, 1 + random.nextInt(2));
                    case 1 -> enchantedItem(access, Items.IRON_CHESTPLATE, Enchantments.PROTECTION, 1 + random.nextInt(2));
                    case 2 -> enchantedItem(access, Items.IRON_LEGGINGS, Enchantments.PROTECTION, 1 + random.nextInt(2));
                    case 3 -> enchantedItem(access, Items.IRON_BOOTS, Enchantments.PROTECTION, 1 + random.nextInt(2));
                    case 4 -> new ItemStack(Items.CHAINMAIL_CHESTPLATE);
                    case 5 -> new ItemStack(Items.CHAINMAIL_LEGGINGS);
                    case 6 -> random.nextBoolean()
                            ? enchantedItem(access, Items.DIAMOND_BOOTS, Enchantments.PROTECTION, 1)
                            : enchantedItem(access, Items.DIAMOND_HELMET, Enchantments.PROTECTION, 1);
                    default -> new ItemStack(Items.IRON_CHESTPLATE);
                };
            }
            case 2 -> { // Enchantment Books (with trident nerfed)
                int b = random.nextInt(11);
                return switch (b) {
                    case 0 -> enchantedBook(access, Enchantments.SHARPNESS, 1 + random.nextInt(3));
                    case 1 -> enchantedBook(access, Enchantments.PROTECTION, 1 + random.nextInt(3));
                    case 2 -> enchantedBook(access, Enchantments.POWER, 1 + random.nextInt(3));
                    case 3 -> enchantedBook(access, Enchantments.QUICK_CHARGE, 1 + random.nextInt(2));
                    case 4 -> enchantedBook(access, Enchantments.PIERCING, 1 + random.nextInt(2));
                    case 5 -> enchantedBook(access, Enchantments.MULTISHOT, 1);
                    case 6 -> enchantedBook(access, Enchantments.KNOCKBACK, 1);
                    case 7 -> enchantedBook(access, Enchantments.FIRE_ASPECT, 1);
                    case 8 -> enchantedBook(access, Enchantments.PUNCH, 1);
                    case 9 -> {
                        // Heavily nerfed trident books: max level 1, low probability
                        if (random.nextFloat() < 0.25f) {
                            yield enchantedBook(access, Enchantments.RIPTIDE, 1);
                        } else {
                            yield enchantedBook(access, Enchantments.LOYALTY, 1);
                        }
                    }
                    default -> enchantedBook(access, Enchantments.SHARPNESS, 2);
                };
            }
            default -> { // Potions & Apples
                int p = random.nextInt(6);
                return switch (p) {
                    case 0 -> potion(Items.SPLASH_POTION, Potions.HEALING);
                    case 1 -> potion(Items.SPLASH_POTION, Potions.STRONG_HEALING);
                    case 2 -> potion(Items.SPLASH_POTION, Potions.SWIFTNESS);
                    case 3 -> potion(Items.SPLASH_POTION, Potions.STRENGTH);
                    case 4 -> potion(Items.SPLASH_POTION, Potions.SLOWNESS);
                    default -> new ItemStack(Items.GOLDEN_APPLE, 1 + random.nextInt(2));
                };
            }
        }
    }

    private static ItemStack pickNormalSecondary(RandomSource random) {
        int s = random.nextInt(9);
        return switch (s) {
            case 0 -> new ItemStack(Items.ARROW, 8 + random.nextInt(9));
            case 1 -> new ItemStack(Items.SPECTRAL_ARROW, 4 + random.nextInt(5));
            case 2 -> tippedArrow(Potions.SLOWNESS, 4);
            case 3 -> tippedArrow(Potions.POISON, 3);
            case 4 -> new ItemStack(Items.COBWEB, 2 + random.nextInt(3));
            case 5 -> new ItemStack(Items.ENDER_PEARL, 1 + random.nextInt(2));
            case 6 -> new ItemStack(Items.FIREWORK_ROCKET, 3 + random.nextInt(3));
            case 7 -> new ItemStack(Items.GOLDEN_CARROT, 6 + random.nextInt(7));
            default -> new ItemStack(Items.COOKED_BEEF, 6 + random.nextInt(7));
        };
    }

    private static ItemStack pickOminousPrimary(RegistryAccess access, RandomSource random) {
        int o = random.nextInt(12);
        return switch (o) {
            case 0 -> enchantedItem(access, Items.DIAMOND_CHESTPLATE, Enchantments.PROTECTION, 2 + random.nextInt(2));
            case 1 -> enchantedItem(access, Items.DIAMOND_LEGGINGS, Enchantments.PROTECTION, 2 + random.nextInt(2));
            case 2 -> enchantedItem(access, Items.DIAMOND_SWORD, Enchantments.SHARPNESS, 3 + random.nextInt(2));
            case 3 -> new ItemStack(Items.DIAMOND_AXE);
            case 4 -> new ItemStack(Items.MACE); // Mace!
            case 5 -> enchantedBook(access, Enchantments.WIND_BURST, 1 + random.nextInt(2));
            case 6 -> enchantedBook(access, Enchantments.BREACH, 2 + random.nextInt(2));
            case 7 -> enchantedBook(access, Enchantments.DENSITY, 2 + random.nextInt(2));
            case 8 -> enchantedBook(access, Enchantments.SHARPNESS, 4);
            case 9 -> enchantedBook(access, Enchantments.PROTECTION, 4);
            case 10 -> enchantedBook(access, Enchantments.POWER, 4);
            default -> {
                // Rare God Apple (~15% among category default)
                if (random.nextFloat() < 0.35f) {
                    yield new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1);
                } else {
                    yield potion(Items.SPLASH_POTION, Potions.STRONG_STRENGTH);
                }
            }
        };
    }

    private static ItemStack pickOminousSecondary(RandomSource random) {
        int s = random.nextInt(6);
        return switch (s) {
            case 0 -> tippedArrow(Potions.STRONG_HARMING, 4);
            case 1 -> tippedArrow(Potions.STRONG_SLOWNESS, 4);
            case 2 -> new ItemStack(Items.WIND_CHARGE, 8 + random.nextInt(5));
            case 3 -> new ItemStack(Items.ENDER_PEARL, 2 + random.nextInt(2));
            case 4 -> new ItemStack(Items.GOLDEN_APPLE, 2 + random.nextInt(2));
            default -> potion(Items.SPLASH_POTION, Potions.STRONG_SWIFTNESS);
        };
    }

    private static ItemStack enchantedItem(RegistryAccess access, Item item, ResourceKey<Enchantment> enchKey, int level) {
        ItemStack stack = new ItemStack(item);
        access.lookup(Registries.ENCHANTMENT)
                .flatMap(r -> r.get(enchKey))
                .ifPresent(holder -> stack.enchant(holder, level));
        return stack;
    }

    private static ItemStack enchantedBook(RegistryAccess access, ResourceKey<Enchantment> enchKey, int level) {
        return access.lookup(Registries.ENCHANTMENT)
                .flatMap(r -> r.get(enchKey))
                .map(holder -> EnchantmentHelper.createBook(new EnchantmentInstance(holder, level)))
                .orElse(new ItemStack(Items.BOOK));
    }

    private static ItemStack potion(Item potionItem, Holder<Potion> potion) {
        return PotionContents.createItemStack(potionItem, potion);
    }

    private static ItemStack tippedArrow(Holder<Potion> potion, int count) {
        ItemStack stack = PotionContents.createItemStack(Items.TIPPED_ARROW, potion);
        stack.setCount(count);
        return stack;
    }
}
