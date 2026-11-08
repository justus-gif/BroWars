package de.browars.client.minigame;

import de.browars.BroWarsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;

import java.util.List;

public final class CrossWarClientHolder {
    private static int ammo = 5;
    private static int reloadTicks = 0;
    private static boolean reloading = false;

    private CrossWarClientHolder() {}

    public static void clientTick() {
        if (reloading && reloadTicks < 10) {
            reloadTicks++;
        }
    }

    public static void update(int newAmmo, int newReloadTicks, boolean newReloading) {
        ammo = newAmmo;
        reloadTicks = newReloadTicks;
        reloading = newReloading;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            ItemStack stack = mc.player.getMainHandItem();
            if (stack.is(BroWarsMod.CROSSWAR_CROSSBOW)) {
                if (ammo > 0 && !reloading) {
                    stack.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(List.of(new ItemStack(Items.ARROW))));
                } else {
                    stack.remove(DataComponents.CHARGED_PROJECTILES);
                }
            }
        }
    }

    public static int getAmmo() {
        return ammo;
    }

    public static int getReloadTicks() {
        return reloadTicks;
    }

    public static boolean isReloading() {
        return reloading;
    }

    public static void reset() {
        ammo = 5;
        reloadTicks = 0;
        reloading = false;
    }
}
