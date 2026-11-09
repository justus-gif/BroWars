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

        // 1. Guaranteed primary tactical item
        drops.add(pickNormalPrimary(access, random));

        // 2. 75% chance for secondary tactical stack (ammo, throwables, building blocks, food, crafting supplies)
        if (random.nextFloat() < 0.75f) {
            drops.add(pickNormalSecondary(random));
        }

        return drops;
    }

    public static List<ItemStack> generateOminousLoot(ServerLevel level, RandomSource random) {
        List<ItemStack> drops = new ArrayList<>();
        RegistryAccess access = level.registryAccess();

        // 1. Guaranteed high-tier item (well-balanced, no one-shots, no god apples)
        drops.add(pickOminousPrimary(access, random));

        // 2. 70% chance for secondary bonus tactical stack
        if (random.nextFloat() < 0.70f) {
            drops.add(pickOminousSecondary(random));
        }

        return drops;
    }

    private static ItemStack pickNormalPrimary(RegistryAccess access, RandomSource random) {
        int category = random.nextInt(5); // 0: Melee/Tools, 1: Ranged/Throwables, 2: Armor, 3: Books, 4: Potions/Food
        return switch (category) {
            case 0 -> { // Melee weapons & tactical utilities
                int w = random.nextInt(12);
                yield switch (w) {
                    case 0 -> enchantedItem(access, Items.IRON_SWORD, Enchantments.SHARPNESS, 1);
                    case 1 -> enchantedItem(access, Items.IRON_SWORD, Enchantments.FIRE_ASPECT, 1);
                    case 2 -> enchantedItem(access, Items.IRON_SWORD, Enchantments.KNOCKBACK, 1);
                    case 3 -> new ItemStack(Items.IRON_AXE);
                    case 4 -> multiEnchantedItem(access, Items.STONE_SWORD, Map.of(
                            Enchantments.SHARPNESS, 2,
                            Enchantments.KNOCKBACK, 1
                    ));
                    case 5 -> multiEnchantedItem(access, Items.GOLDEN_SWORD, Map.of(
                            Enchantments.FIRE_ASPECT, 1,
                            Enchantments.KNOCKBACK, 1
                    ));
                    case 6 -> new ItemStack(Items.SHIELD);
                    case 7 -> enchantedItem(access, Items.FISHING_ROD, Enchantments.UNBREAKING, 2);
                    case 8 -> new ItemStack(Items.FLINT_AND_STEEL);
                    case 9 -> new ItemStack(Items.WATER_BUCKET);
                    case 10 -> new ItemStack(Items.LAVA_BUCKET);
                    default -> {
                        // Trident is heavily nerfed: very rare, max level 1
                        if (random.nextFloat() < 0.15f) {
                            yield enchantedItem(access, Items.TRIDENT, Enchantments.LOYALTY, 1);
                        } else if (random.nextFloat() < 0.30f) {
                            yield enchantedItem(access, Items.TRIDENT, Enchantments.RIPTIDE, 1);
                        } else {
                            yield new ItemStack(Items.IRON_SWORD);
                        }
                    }
                };
            }
            case 1 -> { // Ranged & Throwables
                int r = random.nextInt(8);
                yield switch (r) {
                    case 0 -> enchantedItem(access, Items.BOW, Enchantments.POWER, 1);
                    case 1 -> enchantedItem(access, Items.BOW, Enchantments.FLAME, 1);
                    case 2 -> enchantedItem(access, Items.BOW, Enchantments.PUNCH, 1);
                    case 3 -> enchantedItem(access, Items.CROSSBOW, Enchantments.QUICK_CHARGE, 1);
                    case 4 -> enchantedItem(access, Items.CROSSBOW, Enchantments.MULTISHOT, 1);
                    case 5 -> enchantedItem(access, Items.CROSSBOW, Enchantments.PIERCING, 1);
                    case 6 -> new ItemStack(Items.WIND_CHARGE, 6 + random.nextInt(5));
                    default -> new ItemStack(Items.SNOWBALL, 16);
                };
            }
            case 2 -> { // Armor pieces (balanced protection, specialized defenses)
                int a = random.nextInt(12);
                yield switch (a) {
                    case 0 -> new ItemStack(Items.TURTLE_HELMET);
                    case 1 -> enchantedItem(access, Items.CHAINMAIL_CHESTPLATE, Enchantments.PROTECTION, 1 + random.nextInt(2));
                    case 2 -> enchantedItem(access, Items.CHAINMAIL_LEGGINGS, Enchantments.PROTECTION, 1 + random.nextInt(2));
                    case 3 -> multiEnchantedItem(access, Items.CHAINMAIL_BOOTS, Map.of(
                            Enchantments.PROTECTION, 1,
                            Enchantments.FEATHER_FALLING, 2
                    ));
                    case 4 -> enchantedItem(access, Items.IRON_HELMET, Enchantments.PROTECTION, 1);
                    case 5 -> enchantedItem(access, Items.IRON_HELMET, Enchantments.PROJECTILE_PROTECTION, 2);
                    case 6 -> enchantedItem(access, Items.IRON_CHESTPLATE, Enchantments.PROTECTION, 1);
                    case 7 -> enchantedItem(access, Items.IRON_CHESTPLATE, Enchantments.BLAST_PROTECTION, 2);
                    case 8 -> enchantedItem(access, Items.IRON_LEGGINGS, Enchantments.PROTECTION, 1);
                    case 9 -> enchantedItem(access, Items.IRON_LEGGINGS, Enchantments.FIRE_PROTECTION, 2);
                    case 10 -> enchantedItem(access, Items.IRON_BOOTS, Enchantments.FEATHER_FALLING, 2);
                    default -> enchantedItem(access, Items.IRON_BOOTS, Enchantments.DEPTH_STRIDER, 2);
                };
            }
            case 3 -> { // Enchantment Books (to combine on the starter anvil!)
                int b = random.nextInt(17);
                yield switch (b) {
                    case 0 -> enchantedBook(access, Enchantments.SHARPNESS, 1 + random.nextInt(2));
                    case 1 -> enchantedBook(access, Enchantments.PROTECTION, 1 + random.nextInt(2));
                    case 2 -> enchantedBook(access, Enchantments.PROJECTILE_PROTECTION, 2);
                    case 3 -> enchantedBook(access, Enchantments.FIRE_PROTECTION, 2);
                    case 4 -> enchantedBook(access, Enchantments.BLAST_PROTECTION, 2);
                    case 5 -> enchantedBook(access, Enchantments.FEATHER_FALLING, 2);
                    case 6 -> enchantedBook(access, Enchantments.DEPTH_STRIDER, 2);
                    case 7 -> enchantedBook(access, Enchantments.KNOCKBACK, 1);
                    case 8 -> enchantedBook(access, Enchantments.FIRE_ASPECT, 1);
                    case 9 -> enchantedBook(access, Enchantments.POWER, 1 + random.nextInt(2));
                    case 10 -> enchantedBook(access, Enchantments.PUNCH, 1);
                    case 11 -> enchantedBook(access, Enchantments.FLAME, 1);
                    case 12 -> enchantedBook(access, Enchantments.QUICK_CHARGE, 1 + random.nextInt(2));
                    case 13 -> enchantedBook(access, Enchantments.PIERCING, 1 + random.nextInt(2));
                    case 14 -> enchantedBook(access, Enchantments.MULTISHOT, 1);
                    case 15 -> enchantedBook(access, Enchantments.DENSITY, 1);
                    default -> {
                        // Heavily nerfed trident books
                        if (random.nextFloat() < 0.20f) {
                            yield enchantedBook(access, Enchantments.RIPTIDE, 1);
                        } else {
                            yield enchantedBook(access, Enchantments.LOYALTY, 1);
                        }
                    }
                };
            }
            default -> { // Potions & Consumables (utility & tactical advantages)
                int p = random.nextInt(13);
                yield switch (p) {
                    case 0 -> potion(Items.SPLASH_POTION, Potions.HEALING);
                    case 1 -> potion(Items.SPLASH_POTION, Potions.SWIFTNESS);
                    case 2 -> potion(Items.SPLASH_POTION, Potions.LEAPING);
                    case 3 -> potion(Items.SPLASH_POTION, Potions.SLOWNESS);
                    case 4 -> potion(Items.SPLASH_POTION, Potions.WEAKNESS);
                    case 5 -> potion(Items.SPLASH_POTION, Potions.POISON);
                    case 6 -> potion(Items.SPLASH_POTION, Potions.FIRE_RESISTANCE);
                    case 7 -> potion(Items.SPLASH_POTION, Potions.INVISIBILITY);
                    case 8 -> potion(Items.SPLASH_POTION, Potions.SLOW_FALLING);
                    case 9 -> new ItemStack(Items.GOLDEN_APPLE, 1);
                    case 10 -> new ItemStack(Items.HONEY_BOTTLE, 2); // Antidote against poison!
                    case 11 -> new ItemStack(Items.CHORUS_FRUIT, 4 + random.nextInt(3));
                    default -> new ItemStack(Items.GOLDEN_APPLE, 1);
                };
            }
        };
    }

    private static ItemStack pickNormalSecondary(RandomSource random) {
        int s = random.nextInt(11);
        return switch (s) {
            case 0 -> new ItemStack(Items.ARROW, 12 + random.nextInt(9));
            case 1 -> new ItemStack(Items.SPECTRAL_ARROW, 6 + random.nextInt(5));
            case 2 -> tippedArrow(Potions.SLOWNESS, 4);
            case 3 -> tippedArrow(Potions.POISON, 3);
            case 4 -> tippedArrow(Potions.WEAKNESS, 4);
            case 5 -> new ItemStack(Items.COBWEB, 3 + random.nextInt(3));
            case 6 -> new ItemStack(Items.ENDER_PEARL, 1);
            case 7 -> new ItemStack(Items.FIREWORK_ROCKET, 4 + random.nextInt(3));
            case 8 -> new ItemStack(Items.GOLDEN_CARROT, 8 + random.nextInt(5));
            case 9 -> new ItemStack(Items.IRON_INGOT, 3 + random.nextInt(3)); // Crafting/repairing on anvil
            default -> new ItemStack(Items.OAK_PLANKS, 6 + random.nextInt(5)); // Barricades
        };
    }

    private static ItemStack pickOminousPrimary(RegistryAccess access, RandomSource random) {
        int category = random.nextInt(4); // 0: High-tier Weapons, 1: High-tier Armor, 2: Master Books, 3: Tactical Potions & Gapples
        return switch (category) {
            case 0 -> { // High-tier Weapons (Mace, Diamond Sword/Axe, Crossbow/Bow)
                int w = random.nextInt(6);
                yield switch (w) {
                    case 0 -> new ItemStack(Items.MACE);
                    case 1 -> enchantedItem(access, Items.MACE, Enchantments.DENSITY, 1);
                    case 2 -> enchantedItem(access, Items.DIAMOND_SWORD, Enchantments.SHARPNESS, 1 + random.nextInt(2));
                    case 3 -> enchantedItem(access, Items.DIAMOND_AXE, Enchantments.SHARPNESS, 1 + random.nextInt(2));
                    case 4 -> multiEnchantedItem(access, Items.CROSSBOW, Map.of(
                            Enchantments.QUICK_CHARGE, 2,
                            Enchantments.MULTISHOT, 1
                    ));
                    default -> multiEnchantedItem(access, Items.BOW, Map.of(
                            Enchantments.POWER, 2,
                            Enchantments.PUNCH, 1
                    ));
                };
            }
            case 1 -> { // High-tier Armor (Diamond pieces, iron with strong defense)
                int a = random.nextInt(6);
                yield switch (a) {
                    case 0 -> enchantedItem(access, Items.DIAMOND_CHESTPLATE, Enchantments.PROTECTION, 1);
                    case 1 -> enchantedItem(access, Items.DIAMOND_LEGGINGS, Enchantments.PROTECTION, 1);
                    case 2 -> multiEnchantedItem(access, Items.DIAMOND_BOOTS, Map.of(
                            Enchantments.PROTECTION, 1,
                            Enchantments.FEATHER_FALLING, 3
                    ));
                    case 3 -> enchantedItem(access, Items.DIAMOND_HELMET, Enchantments.PROTECTION, 1);
                    case 4 -> enchantedItem(access, Items.IRON_CHESTPLATE, Enchantments.PROTECTION, 2 + random.nextInt(2));
                    default -> new ItemStack(Items.DIAMOND_CHESTPLATE);
                };
            }
            case 2 -> { // Master Books (valuable combining options)
                int b = random.nextInt(8);
                yield switch (b) {
                    case 0 -> enchantedBook(access, Enchantments.SHARPNESS, 2 + random.nextInt(2));
                    case 1 -> enchantedBook(access, Enchantments.PROTECTION, 2);
                    case 2 -> enchantedBook(access, Enchantments.POWER, 2);
                    case 3 -> enchantedBook(access, Enchantments.DENSITY, 1 + random.nextInt(2));
                    case 4 -> enchantedBook(access, Enchantments.BREACH, 1 + random.nextInt(2));
                    case 5 -> enchantedBook(access, Enchantments.WIND_BURST, 1);
                    case 6 -> enchantedBook(access, Enchantments.FEATHER_FALLING, 3);
                    default -> enchantedBook(access, Enchantments.FIRE_ASPECT, 1);
                };
            }
            default -> { // Tactical Potions & Gapples (no god apples, no strength 2)
                int c = random.nextInt(6);
                yield switch (c) {
                    case 0 -> new ItemStack(Items.GOLDEN_APPLE, 2 + random.nextInt(2));
                    case 1 -> potion(Items.SPLASH_POTION, Potions.STRONG_SWIFTNESS);
                    case 2 -> potion(Items.SPLASH_POTION, Potions.STRONG_LEAPING);
                    case 3 -> potion(Items.SPLASH_POTION, Potions.REGENERATION);
                    case 4 -> potion(Items.SPLASH_POTION, Potions.STRONG_HEALING);
                    default -> potion(Items.SPLASH_POTION, Potions.TURTLE_MASTER);
                };
            }
        };
    }

    private static ItemStack pickOminousSecondary(RandomSource random) {
        int s = random.nextInt(7);
        return switch (s) {
            case 0 -> new ItemStack(Items.ENDER_PEARL, 2);
            case 1 -> tippedArrow(Potions.STRONG_SLOWNESS, 4);
            case 2 -> tippedArrow(Potions.POISON, 4);
            case 3 -> new ItemStack(Items.WIND_CHARGE, 10 + random.nextInt(7));
            case 4 -> new ItemStack(Items.GOLDEN_APPLE, 1 + random.nextInt(2));
            case 5 -> new ItemStack(Items.FIREWORK_ROCKET, 6 + random.nextInt(5));
            default -> new ItemStack(Items.DIAMOND, 1 + random.nextInt(2));
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
