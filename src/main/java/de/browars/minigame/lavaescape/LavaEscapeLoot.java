package de.browars.minigame.lavaescape;

import de.browars.minigame.MinigamePlayerState;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.ArrayList;
import java.util.List;

public class LavaEscapeLoot {

    public enum Category {
        BLOCK,
        ATTACK,
        UTILITY
    }

    public record LootOption(int score, int weight, Category category, java.util.function.Function<RegistryAccess, ItemStack> factory) {}

    private static final List<LootOption> POOL = new ArrayList<>();

    static {
        // --- BLOCKS (Moderate stack sizes: mainly blocks, but not overflowing) ---
        POOL.add(new LootOption(1, 18, Category.BLOCK, a -> new ItemStack(Items.COBBLESTONE, 12)));
        POOL.add(new LootOption(1, 16, Category.BLOCK, a -> new ItemStack(Items.OAK_PLANKS, 12)));
        POOL.add(new LootOption(1, 15, Category.BLOCK, a -> new ItemStack(Items.SCAFFOLDING, 8)));
        POOL.add(new LootOption(1, 14, Category.BLOCK, a -> new ItemStack(Items.LADDER, 6)));
        POOL.add(new LootOption(1, 12, Category.BLOCK, a -> new ItemStack(Items.MOSS_BLOCK, 8)));
        POOL.add(new LootOption(2, 10, Category.BLOCK, a -> new ItemStack(Items.TWISTING_VINES, 6)));
        POOL.add(new LootOption(2, 9, Category.BLOCK, a -> new ItemStack(Items.SLIME_BLOCK, 2)));
        POOL.add(new LootOption(2, 8, Category.BLOCK, a -> new ItemStack(Items.HAY_BLOCK, 4)));
        POOL.add(new LootOption(2, 8, Category.BLOCK, a -> new ItemStack(Items.COBWEB, 2)));

        // --- ATTACK / COMBAT (Varied, but not too strong) ---
        POOL.add(new LootOption(2, 14, Category.ATTACK, a -> new ItemStack(Items.STONE_SWORD)));
        POOL.add(new LootOption(2, 10, Category.ATTACK, a -> new ItemStack(Items.WOODEN_AXE)));
        POOL.add(new LootOption(3, 8, Category.ATTACK, a -> new ItemStack(Items.STONE_AXE)));
        POOL.add(new LootOption(1, 14, Category.ATTACK, a -> new ItemStack(Items.SNOWBALL, 8)));
        POOL.add(new LootOption(1, 10, Category.ATTACK, a -> new ItemStack(Items.EGG, 6)));
        POOL.add(new LootOption(2, 11, Category.ATTACK, a -> enchantedItem(a, Items.STICK, Enchantments.KNOCKBACK, 1, "§eRückstoß-Stock")));
        POOL.add(new LootOption(3, 8, Category.ATTACK, a -> new ItemStack(Items.BOW)));
        POOL.add(new LootOption(1, 12, Category.ATTACK, a -> new ItemStack(Items.ARROW, 5)));
        POOL.add(new LootOption(3, 8, Category.ATTACK, a -> enchantedItem(a, Items.FISHING_ROD, Enchantments.UNBREAKING, 2, "§bTaktische Angel")));
        POOL.add(new LootOption(3, 8, Category.ATTACK, a -> new ItemStack(Items.WIND_CHARGE, 3)));
        POOL.add(new LootOption(3, 6, Category.ATTACK, a -> splashPotion(Potions.SLOWNESS, "§eWurftrank der Langsamkeit")));
        POOL.add(new LootOption(3, 5, Category.ATTACK, a -> splashPotion(Potions.WEAKNESS, "§7Wurftrank der Schwäche")));

        // --- UTILITY / MOBILITY / FOOD ---
        POOL.add(new LootOption(3, 7, Category.UTILITY, a -> new ItemStack(Items.SHIELD)));
        POOL.add(new LootOption(2, 7, Category.UTILITY, a -> new ItemStack(Items.LEATHER_BOOTS)));
        POOL.add(new LootOption(3, 5, Category.UTILITY, a -> new ItemStack(Items.CHAINMAIL_BOOTS)));
        POOL.add(new LootOption(2, 9, Category.UTILITY, a -> new ItemStack(Items.COOKED_BEEF, 4)));
        POOL.add(new LootOption(2, 8, Category.UTILITY, a -> new ItemStack(Items.BREAD, 4)));
        POOL.add(new LootOption(2, 6, Category.UTILITY, a -> new ItemStack(Items.GOLDEN_CARROT, 2)));
        POOL.add(new LootOption(3, 8, Category.UTILITY, a -> drinkPotion(Potions.SWIFTNESS, "§bTrank der Schnelligkeit")));
        POOL.add(new LootOption(3, 8, Category.UTILITY, a -> drinkPotion(Potions.LEAPING, "§aTrank der Sprungkraft")));

        // --- Rare Clutch (Max 1 per chest) ---
        POOL.add(new LootOption(5, 3, Category.UTILITY, a -> new ItemStack(Items.ENDER_PEARL, 1)));
        POOL.add(new LootOption(5, 3, Category.UTILITY, a -> new ItemStack(Items.GOLDEN_APPLE, 1)));
    }

    public static List<ItemStack> generateChestLoot(RegistryAccess access, RandomSource random) {
        int budget = 6 + random.nextInt(4); // 6 to 9 points budget (tighter, balanced loot)
        List<ItemStack> items = new ArrayList<>();

        // 1. Guaranteed 1 moderate stack of creative building/climbing blocks per chest
        int guaranteedRoll = random.nextInt(5);
        ItemStack guaranteedBlocks = switch (guaranteedRoll) {
            case 0 -> new ItemStack(Items.COBBLESTONE, 12);
            case 1 -> new ItemStack(Items.OAK_PLANKS, 12);
            case 2 -> new ItemStack(Items.SCAFFOLDING, 8);
            case 3 -> new ItemStack(Items.LADDER, 6);
            default -> new ItemStack(Items.MOSS_BLOCK, 8);
        };
        MinigamePlayerState.markGameItem(guaranteedBlocks);
        items.add(guaranteedBlocks);

        int currentBudget = budget;
        int blockCount = 1; // already have guaranteed blocks, cap at max 2 block stacks
        int attackCount = 0; // cap at max 1 attack item
        boolean hasHighTier = false;

        // Try up to 8 iterations to fill the chest budget
        for (int attempt = 0; attempt < 8 && currentBudget > 0; attempt++) {
            List<LootOption> candidates = new ArrayList<>();
            int totalWeight = 0;
            for (LootOption opt : POOL) {
                if (opt.score() <= currentBudget) {
                    if (opt.score() >= 5 && hasHighTier) {
                        continue;
                    }
                    if (opt.category() == Category.BLOCK && blockCount >= 2) {
                        continue; // No more than 2 block stacks per chest
                    }
                    if (opt.category() == Category.ATTACK && attackCount >= 1) {
                        continue; // No more than 1 attack item per chest
                    }
                    candidates.add(opt);
                    totalWeight += opt.weight();
                }
            }

            if (candidates.isEmpty() || totalWeight <= 0) break;

            int roll = random.nextInt(totalWeight);
            LootOption selected = null;
            int running = 0;
            for (LootOption opt : candidates) {
                running += opt.weight();
                if (roll < running) {
                    selected = opt;
                    break;
                }
            }

            if (selected == null) selected = candidates.getFirst();

            ItemStack stack = selected.factory().apply(access);
            MinigamePlayerState.markGameItem(stack);
            items.add(stack);

            // If a bow was rolled, bundle 4 arrows with it so it's directly usable
            if (stack.is(Items.BOW)) {
                ItemStack arrows = new ItemStack(Items.ARROW, 4);
                MinigamePlayerState.markGameItem(arrows);
                items.add(arrows);
            }

            if (selected.category() == Category.BLOCK) blockCount++;
            if (selected.category() == Category.ATTACK) attackCount++;
            if (selected.score() >= 5) hasHighTier = true;

            currentBudget -= selected.score();
        }

        return items;
    }

    private static ItemStack enchantedItem(RegistryAccess access, Item item, ResourceKey<Enchantment> enchKey, int level, String name) {
        ItemStack stack = new ItemStack(item);
        access.lookup(Registries.ENCHANTMENT)
                .flatMap(r -> r.get(enchKey))
                .ifPresent(holder -> stack.enchant(holder, level));
        if (name != null) {
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        }
        return stack;
    }

    private static ItemStack drinkPotion(Holder<Potion> potion, String name) {
        ItemStack stack = PotionContents.createItemStack(Items.POTION, potion);
        if (name != null) {
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        }
        return stack;
    }

    private static ItemStack splashPotion(Holder<Potion> potion, String name) {
        ItemStack stack = PotionContents.createItemStack(Items.SPLASH_POTION, potion);
        if (name != null) {
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        }
        return stack;
    }
}
