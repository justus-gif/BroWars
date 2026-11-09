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
import java.util.Map;

public final class VaultWarLoot {

    private VaultWarLoot() {}

    public static List<ItemStack> generateNormalLoot(ServerLevel level, RandomSource random) {
        List<ItemStack> drops = new ArrayList<>();
        RegistryAccess access = level.registryAccess();

        // 1. Guaranteed primary quality item (weapons, armor, tools, books, potions)
        drops.add(pickNormalPrimary(access, random));

        // 2. 80% chance for secondary combat stack (ammo, pearls, wind charges, gapples, webs)
        if (random.nextFloat() < 0.80f) {
            drops.add(pickNormalSecondary(random));
        }

        return drops;
    }

    public static List<ItemStack> generateOminousLoot(ServerLevel level, RandomSource random) {
        List<ItemStack> drops = new ArrayList<>();
        RegistryAccess access = level.registryAccess();

        // 1. Guaranteed high-tier item (Mace, Diamond Sword/Axe, High-tier Bow, Diamond Armor, Master Books)
        drops.add(pickOminousPrimary(access, random));

        // 2. 75% chance for secondary bonus OP stack (pearls, wind charges, golden apples, strong arrows)
        if (random.nextFloat() < 0.75f) {
            drops.add(pickOminousSecondary(random));
        }

        return drops;
    }

    private static ItemStack pickNormalPrimary(RegistryAccess access, RandomSource random) {
        int category = random.nextInt(5); // 0: Melee/Tools, 1: Ranged/Throwables, 2: Armor, 3: Books, 4: Potions/Food
        return switch (category) {
            case 0 -> { // Melee weapons & combat tools
                int w = random.nextInt(11);
                yield switch (w) {
                    case 0 -> enchantedItem(access, Items.IRON_SWORD, Enchantments.SHARPNESS, 2);
                    case 1 -> enchantedItem(access, Items.IRON_SWORD, Enchantments.FIRE_ASPECT, 1);
                    case 2 -> enchantedItem(access, Items.IRON_SWORD, Enchantments.KNOCKBACK, 2);
                    case 3 -> enchantedItem(access, Items.IRON_AXE, Enchantments.SHARPNESS, 2);
                    case 4 -> enchantedItem(access, Items.DIAMOND_SWORD, Enchantments.SHARPNESS, 1);
                    case 5 -> enchantedItem(access, Items.DIAMOND_AXE, Enchantments.SHARPNESS, 1);
                    case 6 -> new ItemStack(Items.SHIELD);
                    case 7 -> enchantedItem(access, Items.FISHING_ROD, Enchantments.UNBREAKING, 3);
                    case 8 -> new ItemStack(Items.WATER_BUCKET);
                    case 9 -> new ItemStack(Items.LAVA_BUCKET);
                    default -> {
                        // Trident is rare
                        if (random.nextFloat() < 0.20f) {
                            yield enchantedItem(access, Items.TRIDENT, Enchantments.LOYALTY, 1);
                        } else if (random.nextFloat() < 0.40f) {
                            yield enchantedItem(access, Items.TRIDENT, Enchantments.RIPTIDE, 1);
                        } else {
                            yield enchantedItem(access, Items.IRON_SWORD, Enchantments.SHARPNESS, 2);
                        }
                    }
                };
            }
            case 1 -> { // Ranged & Throwables
                int r = random.nextInt(7);
                yield switch (r) {
                    case 0 -> enchantedItem(access, Items.BOW, Enchantments.POWER, 2);
                    case 1 -> enchantedItem(access, Items.BOW, Enchantments.FLAME, 1);
                    case 2 -> enchantedItem(access, Items.BOW, Enchantments.PUNCH, 1);
                    case 3 -> enchantedItem(access, Items.CROSSBOW, Enchantments.QUICK_CHARGE, 2);
                    case 4 -> enchantedItem(access, Items.CROSSBOW, Enchantments.MULTISHOT, 1);
                    case 5 -> enchantedItem(access, Items.CROSSBOW, Enchantments.PIERCING, 2);
                    default -> new ItemStack(Items.WIND_CHARGE, 12);
                };
            }
            case 2 -> { // Armor pieces
                int a = random.nextInt(9);
                yield switch (a) {
                    case 0 -> enchantedItem(access, Items.DIAMOND_BOOTS, Enchantments.PROTECTION, 1);
                    case 1 -> enchantedItem(access, Items.DIAMOND_HELMET, Enchantments.PROTECTION, 1);
                    case 2 -> new ItemStack(Items.DIAMOND_CHESTPLATE);
                    case 3 -> new ItemStack(Items.DIAMOND_LEGGINGS);
                    case 4 -> enchantedItem(access, Items.IRON_CHESTPLATE, Enchantments.PROTECTION, 2);
                    case 5 -> enchantedItem(access, Items.IRON_LEGGINGS, Enchantments.PROTECTION, 2);
                    case 6 -> multiEnchantedItem(access, Items.IRON_BOOTS, Map.of(
                            Enchantments.PROTECTION, 2,
                            Enchantments.FEATHER_FALLING, 3
                    ));
                    case 7 -> multiEnchantedItem(access, Items.IRON_HELMET, Map.of(
                            Enchantments.PROTECTION, 2,
                            Enchantments.PROJECTILE_PROTECTION, 3
                    ));
                    default -> enchantedItem(access, Items.TURTLE_HELMET, Enchantments.PROTECTION, 1);
                };
            }
            case 3 -> { // Enchantment Books (to combine on the starter anvil!)
                int b = random.nextInt(15);
                yield switch (b) {
                    case 0 -> enchantedBook(access, Enchantments.SHARPNESS, 2);
                    case 1 -> enchantedBook(access, Enchantments.SHARPNESS, 3);
                    case 2 -> enchantedBook(access, Enchantments.PROTECTION, 2);
                    case 3 -> enchantedBook(access, Enchantments.PROTECTION, 3);
                    case 4 -> enchantedBook(access, Enchantments.POWER, 2);
                    case 5 -> enchantedBook(access, Enchantments.FIRE_ASPECT, 1);
                    case 6 -> enchantedBook(access, Enchantments.KNOCKBACK, 2);
                    case 7 -> enchantedBook(access, Enchantments.FEATHER_FALLING, 3);
                    case 8 -> enchantedBook(access, Enchantments.PUNCH, 1);
                    case 9 -> enchantedBook(access, Enchantments.FLAME, 1);
                    case 10 -> enchantedBook(access, Enchantments.QUICK_CHARGE, 2);
                    case 11 -> enchantedBook(access, Enchantments.MULTISHOT, 1);
                    case 12 -> enchantedBook(access, Enchantments.PIERCING, 2);
                    case 13 -> enchantedBook(access, Enchantments.DENSITY, 2);
                    default -> enchantedBook(access, Enchantments.BREACH, 1);
                };
            }
            default -> { // Potions & Consumables
                int p = random.nextInt(11);
                yield switch (p) {
                    case 0 -> potion(Items.SPLASH_POTION, Potions.STRONG_HEALING);
                    case 1 -> potion(Items.SPLASH_POTION, Potions.SWIFTNESS);
                    case 2 -> potion(Items.SPLASH_POTION, Potions.STRONG_SWIFTNESS);
                    case 3 -> potion(Items.SPLASH_POTION, Potions.LEAPING);
                    case 4 -> new ItemStack(Items.COOKED_BEEF, 12);
                    case 5 -> potion(Items.SPLASH_POTION, Potions.FIRE_RESISTANCE);
                    case 6 -> potion(Items.SPLASH_POTION, Potions.INVISIBILITY);
                    case 7 -> potion(Items.SPLASH_POTION, Potions.SLOWNESS);
                    case 8 -> potion(Items.SPLASH_POTION, Potions.POISON);
                    case 9 -> new ItemStack(Items.GOLDEN_APPLE, 2);
                    default -> new ItemStack(Items.HONEY_BOTTLE, 3);
                };
            }
        };
    }

    private static ItemStack pickNormalSecondary(RandomSource random) {
        int s = random.nextInt(12);
        return switch (s) {
            case 0 -> new ItemStack(Items.ARROW, 32);
            case 1 -> new ItemStack(Items.SPECTRAL_ARROW, 16);
            case 2 -> tippedArrow(Potions.SLOWNESS, 6);
            case 3 -> tippedArrow(Potions.POISON, 6);
            case 4 -> tippedArrow(Potions.WEAKNESS, 6);
            case 5 -> new ItemStack(Items.ENDER_PEARL, 2);
            case 6 -> new ItemStack(Items.WIND_CHARGE, 8);
            case 7 -> new ItemStack(Items.GOLDEN_APPLE, 1);
            case 8 -> new ItemStack(Items.COBWEB, 4);
            case 9 -> new ItemStack(Items.GOLDEN_CARROT, 16);
            case 10 -> new ItemStack(Items.IRON_INGOT, 6);
            default -> new ItemStack(Items.DIAMOND, 2);
        };
    }

    private static ItemStack pickOminousPrimary(RegistryAccess access, RandomSource random) {
        int category = random.nextInt(4); // 0: God Weapons, 1: God Armor, 2: Master Books, 3: God Consumables
        return switch (category) {
            case 0 -> { // God Weapons (Mace, Diamond Sword/Axe, High-tier Bows)
                int w = random.nextInt(8);
                yield switch (w) {
                    case 0 -> enchantedItem(access, Items.MACE, Enchantments.DENSITY, 2);
                    case 1 -> enchantedItem(access, Items.MACE, Enchantments.BREACH, 1);
                    case 2 -> enchantedItem(access, Items.MACE, Enchantments.WIND_BURST, 1);
                    case 3 -> multiEnchantedItem(access, Items.DIAMOND_SWORD, Map.of(
                            Enchantments.SHARPNESS, 2,
                            Enchantments.FIRE_ASPECT, 1
                    ));
                    case 4 -> multiEnchantedItem(access, Items.DIAMOND_SWORD, Map.of(
                            Enchantments.SHARPNESS, 3,
                            Enchantments.KNOCKBACK, 1
                    ));
                    case 5 -> enchantedItem(access, Items.DIAMOND_AXE, Enchantments.SHARPNESS, 2);
                    case 6 -> multiEnchantedItem(access, Items.CROSSBOW, Map.of(
                            Enchantments.QUICK_CHARGE, 3,
                            Enchantments.MULTISHOT, 1
                    ));
                    default -> multiEnchantedItem(access, Items.BOW, Map.of(
                            Enchantments.POWER, 3,
                            Enchantments.FLAME, 1,
                            Enchantments.PUNCH, 1
                    ));
                };
            }
            case 1 -> { // God Armor (Diamond Protection II)
                int a = random.nextInt(5);
                yield switch (a) {
                    case 0 -> enchantedItem(access, Items.DIAMOND_CHESTPLATE, Enchantments.PROTECTION, 2);
                    case 1 -> enchantedItem(access, Items.DIAMOND_LEGGINGS, Enchantments.PROTECTION, 2);
                    case 2 -> multiEnchantedItem(access, Items.DIAMOND_BOOTS, Map.of(
                            Enchantments.PROTECTION, 2,
                            Enchantments.FEATHER_FALLING, 4
                    ));
                    case 3 -> multiEnchantedItem(access, Items.DIAMOND_HELMET, Map.of(
                            Enchantments.PROTECTION, 2,
                            Enchantments.RESPIRATION, 3
                    ));
                    default -> enchantedItem(access, Items.IRON_CHESTPLATE, Enchantments.PROTECTION, 3);
                };
            }
            case 2 -> { // Master Books
                int b = random.nextInt(10);
                yield switch (b) {
                    case 0 -> enchantedBook(access, Enchantments.SHARPNESS, 3);
                    case 1 -> enchantedBook(access, Enchantments.PROTECTION, 3);
                    case 2 -> enchantedBook(access, Enchantments.POWER, 3);
                    case 3 -> enchantedBook(access, Enchantments.DENSITY, 2);
                    case 4 -> enchantedBook(access, Enchantments.DENSITY, 3);
                    case 5 -> enchantedBook(access, Enchantments.BREACH, 2);
                    case 6 -> enchantedBook(access, Enchantments.WIND_BURST, 1);
                    case 7 -> enchantedBook(access, Enchantments.FEATHER_FALLING, 4);
                    case 8 -> enchantedBook(access, Enchantments.FIRE_ASPECT, 1);
                    default -> enchantedBook(access, Enchantments.PUNCH, 2);
                };
            }
            default -> { // God Consumables
                int c = random.nextInt(7);
                yield switch (c) {
                    case 0 -> new ItemStack(Items.GOLDEN_APPLE, 4);
                    case 1 -> potion(Items.SPLASH_POTION, Potions.STRONG_SWIFTNESS);
                    case 2 -> potion(Items.SPLASH_POTION, Potions.STRONG_LEAPING);
                    case 3 -> potion(Items.SPLASH_POTION, Potions.STRONG_HEALING);
                    case 4 -> new ItemStack(Items.GOLDEN_APPLE, 4);
                    case 5 -> potion(Items.SPLASH_POTION, Potions.TURTLE_MASTER);
                    default -> potion(Items.SPLASH_POTION, Potions.WIND_CHARGED);
                };
            }
        };
    }

    private static ItemStack pickOminousSecondary(RandomSource random) {
        int s = random.nextInt(7);
        return switch (s) {
            case 0 -> new ItemStack(Items.ENDER_PEARL, 3);
            case 1 -> new ItemStack(Items.WIND_CHARGE, 16);
            case 2 -> new ItemStack(Items.GOLDEN_APPLE, 2);
            case 3 -> tippedArrow(Potions.STRONG_SLOWNESS, 8);
            case 4 -> tippedArrow(Potions.POISON, 8);
            case 5 -> new ItemStack(Items.DIAMOND, 3);
            default -> new ItemStack(Items.COBWEB, 6);
        };
    }

    private static ItemStack enchantedItem(RegistryAccess access, Item item, ResourceKey<Enchantment> enchKey, int level) {
        ItemStack stack = new ItemStack(item);
        access.lookup(Registries.ENCHANTMENT)
                .flatMap(r -> r.get(enchKey))
                .ifPresent(holder -> stack.enchant(holder, level));
        return stack;
    }

    private static ItemStack multiEnchantedItem(RegistryAccess access, Item item, Map<ResourceKey<Enchantment>, Integer> enchants) {
        ItemStack stack = new ItemStack(item);
        access.lookup(Registries.ENCHANTMENT).ifPresent(reg -> {
            for (Map.Entry<ResourceKey<Enchantment>, Integer> entry : enchants.entrySet()) {
                reg.get(entry.getKey()).ifPresent(holder -> stack.enchant(holder, entry.getValue()));
            }
        });
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
