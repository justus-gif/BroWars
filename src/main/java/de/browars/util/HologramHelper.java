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

    public static UUID createTargetBadge(ServerLevel level, double x, double y, double z, Component text, int bgColor) {
        Display.TextDisplay display = net.minecraft.world.entity.EntityTypes.TEXT_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
        if (display != null) {
            display.setPos(x, y, z);
            ((de.browars.mixin.TextDisplayAccess) display).browars$setText(text);
            ((de.browars.mixin.TextDisplayAccess) display).browars$setBackgroundColor(bgColor);
            ((de.browars.mixin.TextDisplayAccess) display).browars$setFlags((byte) 1);
            ((de.browars.mixin.DisplayAccess) display).browars$setBillboard(Display.BillboardConstraints.CENTER);
            ((de.browars.mixin.DisplayAccess) display).browars$setViewRange(4.0f);
            ((de.browars.mixin.DisplayAccess) display).browars$setTransformation(
                    new com.mojang.math.Transformation(
                            new org.joml.Vector3f(),
                            new org.joml.Quaternionf(),
                            new org.joml.Vector3f(1.25f, 1.25f, 1.25f),
                            new org.joml.Quaternionf()
                    )
            );
            display.addTag("browars_target_badge");
            level.addFreshEntity(display);
            return display.getUUID();
        }
        return null;
    }

    public static void removeHologram(ServerLevel level, UUID uuid) {
        if (uuid == null) return;
        Entity entity = level.getEntity(uuid);
        if (entity != null) {
            entity.discard();
        }
    }
}
