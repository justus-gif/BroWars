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

    public record LootOption(int score, int weight, java.util.function.Function<RegistryAccess, ItemStack> factory) {}

    private static final List<LootOption> POOL = new ArrayList<>();

    static {
        // --- Score 1 (Low cost, utility / ammo) ---
        POOL.add(new LootOption(1, 14, a -> new ItemStack(Items.COBBLESTONE, 8)));
        POOL.add(new LootOption(1, 12, a -> new ItemStack(Items.SCAFFOLDING, 6)));
        POOL.add(new LootOption(1, 12, a -> new ItemStack(Items.SNOWBALL, 8)));
        POOL.add(new LootOption(1, 10, a -> new ItemStack(Items.EGG, 8)));
        POOL.add(new LootOption(1, 10, a -> new ItemStack(Items.ARROW, 4)));
        POOL.add(new LootOption(1, 8, a -> new ItemStack(Items.SLIME_BALL, 4)));

        // --- Score 2 (Small traps & mobility) ---
        POOL.add(new LootOption(2, 10, a -> new ItemStack(Items.COBWEB, 2)));
        POOL.add(new LootOption(2, 8, a -> new ItemStack(Items.SWEET_BERRIES, 6)));
        POOL.add(new LootOption(2, 8, a -> new ItemStack(Items.COOKED_BEEF, 4)));

        // --- Score 3 (Tactical sabotage & mobility) ---
        POOL.add(new LootOption(3, 9, a -> new ItemStack(Items.WIND_CHARGE, 2)));
        POOL.add(new LootOption(3, 8, a -> enchantedItem(a, Items.FISHING_ROD, Enchantments.UNBREAKING, 2, "§bTaktische Angel")));
        POOL.add(new LootOption(3, 8, a -> enchantedItem(a, Items.STICK, Enchantments.KNOCKBACK, 1, "§6Schock-Stock")));
        POOL.add(new LootOption(3, 7, a -> splashPotion(Potions.SLOWNESS, "§eWurftrank der Langsamkeit")));
        POOL.add(new LootOption(3, 8, a -> drinkPotion(Potions.SWIFTNESS, "§bTrank der Schnelligkeit")));
        POOL.add(new LootOption(3, 8, a -> drinkPotion(Potions.LEAPING, "§aTrank der Sprungkraft")));

        // --- Score 4 (Strong tools) ---
        POOL.add(new LootOption(4, 6, a -> new ItemStack(Items.BOW)));
        POOL.add(new LootOption(4, 6, a -> new ItemStack(Items.WATER_BUCKET)));
        POOL.add(new LootOption(4, 5, a -> new ItemStack(Items.SHIELD)));

        // --- Score 5 (Clutch / High impact, max 1 per chest) ---
        POOL.add(new LootOption(5, 4, a -> new ItemStack(Items.WIND_CHARGE, 4)));
        POOL.add(new LootOption(5, 4, a -> enchantedItem(a, Items.STICK, Enchantments.KNOCKBACK, 2, "§cMega-Rückstoß-Stock")));
        POOL.add(new LootOption(5, 4, a -> new ItemStack(Items.GOLDEN_APPLE, 1)));
        POOL.add(new LootOption(5, 4, a -> new ItemStack(Items.CROSSBOW)));
        POOL.add(new LootOption(5, 3, a -> splashPotion(Potions.WEAKNESS, "§7Wurftrank der Schwäche")));
    }

    public static List<ItemStack> generateChestLoot(RegistryAccess access, RandomSource random) {
        int budget = 8 + random.nextInt(4); // 8 to 11 points total budget
        List<ItemStack> items = new ArrayList<>();
        int currentBudget = budget;
        boolean hasHighTier = false;

        // Try up to 10 iterations to fill the chest budget
        for (int attempt = 0; attempt < 10 && currentBudget > 0; attempt++) {
            List<LootOption> candidates = new ArrayList<>();
            int totalWeight = 0;
            for (LootOption opt : POOL) {
                if (opt.score() <= currentBudget) {
                    if (opt.score() >= 5 && hasHighTier) {
                        continue; // Maximum 1 high tier item per chest!
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
            currentBudget -= selected.score();
            if (selected.score() >= 5) {
                hasHighTier = true;
            }
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
