package de.browars.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;

import java.util.UUID;

public class HologramHelper {
    public static Component formatHologramText(String rawText, int colorHex) {
        if (rawText == null || rawText.trim().isEmpty()) {
            return Component.empty();
        }
        TextColor textColor = TextColor.fromRgb(colorHex);
        return Component.literal(rawText).withStyle(Style.EMPTY.withColor(textColor).withBold(true));
    }

    public static UUID updateOrCreateHologram(ServerLevel level, UUID existingUuid, double x, double y, double z,
                                             String name, int colorHex) {
        if (name == null || name.trim().isEmpty()) {
            removeHologram(level, existingUuid);
            return null;
        }

        Component text = formatHologramText(name, colorHex);

        if (existingUuid != null) {
            Entity existing = level.getEntity(existingUuid);
            if (existing instanceof Display.TextDisplay textDisplay && existing.isAlive()) {
                applyTextDisplayProps(textDisplay, text);
                textDisplay.setPos(x, y, z);
                return existingUuid;
            }
        }

        Display.TextDisplay display = net.minecraft.world.entity.EntityTypes.TEXT_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
        if (display != null) {
            display.setPos(x, y, z);
            applyTextDisplayProps(display, text);
            level.addFreshEntity(display);
            return display.getUUID();
        }

        return null;
    }

    private static void applyTextDisplayProps(Display.TextDisplay display, Component text) {
        ((de.browars.mixin.TextDisplayAccess) display).browars$setText(text);
        ((de.browars.mixin.DisplayAccess) display).browars$setBillboard(Display.BillboardConstraints.CENTER);
    }

    public static void removeHologram(ServerLevel level, UUID uuid) {
        if (uuid == null) return;
        Entity entity = level.getEntity(uuid);
        if (entity != null) {
            entity.discard();
        }
    }
}
