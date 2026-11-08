package de.browars.client.gui;

import de.browars.area.TeleportArea;
import de.browars.network.DeleteTeleportAreaPayload;
import de.browars.network.OpenTeleportAreaPayload;
import de.browars.network.TeleportToAreaPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class AreaManagerScreen extends Screen {
    public static final List<TeleportArea> KNOWN_AREAS = new ArrayList<>();

    private final Screen parent;
    private final net.minecraft.core.BlockPos pos1;
    private final net.minecraft.core.BlockPos pos2;
    private int page = 0;
    private static final int ITEMS_PER_PAGE = 4;

    public AreaManagerScreen(Screen parent) {
        this(parent, null, null);
    }

    public AreaManagerScreen(Screen parent, net.minecraft.core.BlockPos pos1, net.minecraft.core.BlockPos pos2) {
        super(Component.literal("§6§lTeleport-Flächen Übersicht"));
        this.parent = parent;
        this.pos1 = pos1;
        this.pos2 = pos2;
    }

    public static void updateAreas(List<TeleportArea> areas) {
        KNOWN_AREAS.clear();
        KNOWN_AREAS.addAll(areas);
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        this.addRenderableOnly(new StringWidget(centerX - 170, 10, 180, 16, this.title, this.font));

        this.addRenderableWidget(
                Button.builder(Component.literal("§a➕ Neue Fläche"), btn -> {
                    var player = Minecraft.getInstance().player;
                    net.minecraft.core.BlockPos p1 = pos1 != null ? pos1 : (player != null ? player.blockPosition() : net.minecraft.core.BlockPos.ZERO);
                    net.minecraft.core.BlockPos p2 = pos2 != null ? pos2 : p1;
                    double x = player != null ? player.getX() : 0;
                    double y = player != null ? player.getY() : 0;
                    double z = player != null ? player.getZ() : 0;
                    float yaw = player != null ? player.getYRot() : 0;
                    float pitch = player != null ? player.getXRot() : 0;
                    String dim = player != null ? player.level().dimension().identifier().toString() : "minecraft:overworld";

                    OpenTeleportAreaPayload payload = new OpenTeleportAreaPayload(
                            "", p1, p2, x, y, z, yaw, pitch, dim, "", 0x00FFFF, true, false
                    );
                    Minecraft.getInstance().gui.setScreen(new TeleportAreaScreen(payload));
                }).bounds(centerX + 30, 8, 140, 18).build()
        );

        if (KNOWN_AREAS.isEmpty()) {
            this.addRenderableOnly(new StringWidget(centerX - 120, 80, 240, 20,
                    Component.literal("§7Keine Teleport-Flächen vorhanden."), this.font));
        } else {
            int startIndex = page * ITEMS_PER_PAGE;
            int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, KNOWN_AREAS.size());

            int y = 35;
            for (int i = startIndex; i < endIndex; i++) {
                TeleportArea area = KNOWN_AREAS.get(i);

                String displayName = !area.getName().isEmpty() ? area.getName() : "§7(Ohne Name)";
                String dimName = area.getDimension().identifier().getPath();
                String details = String.format("§e%s §7[%s] (%d,%d,%d) -> (%.1f, %.1f, %.1f)",
                        displayName, dimName,
                        area.getMinPos().getX(), area.getMinPos().getY(), area.getMinPos().getZ(),
                        area.getTargetX(), area.getTargetY(), area.getTargetZ());

                this.addRenderableOnly(new StringWidget(centerX - 170, y, 340, 14, Component.literal(details), this.font));

                int by = y + 15;
                this.addRenderableWidget(
                        Button.builder(Component.literal("§bTeleport"), btn -> {
                            ClientPlayNetworking.send(new TeleportToAreaPayload(area.getId()));
                            this.onClose();
                        }).bounds(centerX - 170, by, 75, 18).build()
                );

                this.addRenderableWidget(
                        Button.builder(Component.literal("§eBearbeiten"), btn -> {
                            OpenTeleportAreaPayload payload = new OpenTeleportAreaPayload(
                                    area.getId(),
                                    area.getMinPos(),
                                    area.getMaxPos(),
                                    area.getTargetX(),
                                    area.getTargetY(),
                                    area.getTargetZ(),
                                    area.getTargetYaw(),
                                    area.getTargetPitch(),
                                    area.getTargetDimension().identifier().toString(),
                                    area.getName(),
                                    area.getColorHex(),
                                    area.isVisibleInSurvival(),
                                    area.isShowNameHologram()
                            );
                            Minecraft.getInstance().gui.setScreen(new TeleportAreaScreen(payload));
                        }).bounds(centerX - 90, by, 75, 18).build()
                );

                this.addRenderableWidget(
                        Button.builder(Component.literal("§cLöschen"), btn -> {
                            ClientPlayNetworking.send(new DeleteTeleportAreaPayload(area.getId()));
                            KNOWN_AREAS.remove(area);
                            this.rebuildWidgets();
                        }).bounds(centerX - 10, by, 65, 18).build()
                );

                y += 40;
            }

            int maxPages = (int) Math.ceil((double) KNOWN_AREAS.size() / ITEMS_PER_PAGE);
            if (page > 0) {
                this.addRenderableWidget(
                        Button.builder(Component.literal("◀ Zurück"), btn -> {
                            page--;
                            this.rebuildWidgets();
                        }).bounds(centerX - 170, this.height - 30, 70, 20).build()
                );
            }
            if (page < maxPages - 1) {
                this.addRenderableWidget(
                        Button.builder(Component.literal("Weiter ▶"), btn -> {
                            page++;
                            this.rebuildWidgets();
                        }).bounds(centerX - 95, this.height - 30, 70, 20).build()
                );
            }
        }

        this.addRenderableWidget(
                Button.builder(CommonComponents.GUI_BACK, btn -> {
                    Minecraft.getInstance().gui.setScreen(parent);
                }).bounds(centerX + 80, this.height - 30, 90, 20).build()
        );
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        this.extractTransparentBackground(extractor);
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }
}
