package de.browars.client.gui;

import de.browars.network.OpenTeleportTargetPayload;
import de.browars.network.SaveTeleportTargetPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public class TeleportTargetScreen extends Screen {
    private final OpenTeleportTargetPayload initialData;

    private EditBox nameInput;
    private EditBox hexInput;
    private int selectedColor;

    private static final int[] PRESET_COLORS = {
            0x00FF88, 0x00FFFF, 0x9900FF, 0xFF0055,
            0xFFCC00, 0x33FF00, 0xFFFFFF, 0x222222
    };
    private static final String[] PRESET_NAMES = {
            "Smaragd", "Aqua", "Violett", "Rubin",
            "Gold", "Neon", "Weiß", "Dunkel"
    };

    public TeleportTargetScreen(OpenTeleportTargetPayload payload) {
        super(Component.literal("§a🎯 Teleport Ziel-Indikator"));
        this.initialData = payload;
        this.selectedColor = payload.colorHex();
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = 32;

        this.addRenderableOnly(new StringWidget(centerX - 120, 10, 240, 16, this.title, this.font));

        // Name input
        this.addRenderableOnly(new StringWidget(centerX - 150, startY, 100, 16, Component.literal("Ziel Name:"), this.font));
        nameInput = new EditBox(this.font, centerX - 40, startY, 190, 18, Component.literal("Name"));
        nameInput.setValue(initialData.name());
        this.addRenderableWidget(nameInput);

        // Copy position button
        startY += 30;
        this.addRenderableWidget(
                Button.builder(Component.literal("§b📋 Spot-Position kopieren"), btn -> {
                    LocalPlayer player = Minecraft.getInstance().player;
                    double x = initialData.pos().getX() + 0.5;
                    double y = initialData.pos().getY();
                    double z = initialData.pos().getZ() + 0.5;
                    float yaw = player != null ? player.getYRot() : 0.0f;
                    float pitch = player != null ? player.getXRot() : 0.0f;
                    String dim = player != null ? player.level().dimension().identifier().toString() : "minecraft:overworld";

                    de.browars.client.ClientClipboard.set(x, y, z, yaw, pitch, dim);
                    if (player != null) {
                        player.sendSystemMessage(Component.literal("§a[BroWars] Ziel-Position kopiert! (X: " + String.format(Locale.ROOT, "%.2f", x) + " Y: " + String.format(Locale.ROOT, "%.2f", y) + " Z: " + String.format(Locale.ROOT, "%.2f", z) + ")"));
                    }
                }).bounds(centerX - 150, startY, 300, 20).build()
        );

        // Color Section
        startY += 28;
        this.addRenderableOnly(new StringWidget(centerX - 150, startY, 100, 16, Component.literal("§bPartikelfarbe:"), this.font));

        hexInput = new EditBox(this.font, centerX - 40, startY, 80, 18, Component.literal("Hex"));
        hexInput.setValue(String.format("#%06X", selectedColor & 0xFFFFFF));
        hexInput.setResponder(val -> {
            try {
                String clean = val.replace("#", "").trim();
                selectedColor = (int) Long.parseLong(clean, 16);
            } catch (Exception ignored) {}
        });
        this.addRenderableWidget(hexInput);

        // Preset color buttons
        startY += 24;
        int btnW = 72;
        int btnH = 18;
        for (int i = 0; i < PRESET_COLORS.length; i++) {
            final int col = PRESET_COLORS[i];
            int colIndex = i % 4;
            int rowIndex = i / 4;
            int bx = centerX - 150 + colIndex * (btnW + 4);
            int by = startY + rowIndex * (btnH + 2);

            this.addRenderableWidget(
                    Button.builder(Component.literal(PRESET_NAMES[i]), btn -> {
                        selectedColor = col;
                        hexInput.setValue(String.format("#%06X", col & 0xFFFFFF));
                    }).bounds(bx, by, btnW, btnH).build()
            );
        }

        // Bottom action buttons
        int bottomY = this.height - 30;
        this.addRenderableWidget(
                Button.builder(Component.literal("§a✔ Speichern"), btn -> saveAndClose())
                        .bounds(centerX - 110, bottomY, 100, 20).build()
        );
        this.addRenderableWidget(
                Button.builder(CommonComponents.GUI_CANCEL, btn -> this.onClose())
                        .bounds(centerX + 10, bottomY, 100, 20).build()
        );
    }

    private void saveAndClose() {
        SaveTeleportTargetPayload payload = new SaveTeleportTargetPayload(
                initialData.pos(),
                nameInput.getValue().trim(),
                selectedColor
        );
        ClientPlayNetworking.send(payload);
        this.onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        this.extractTransparentBackground(extractor);
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int swatchX = centerX + 50;
        int swatchY = 106;
        extractor.fill(swatchX - 1, swatchY - 1, swatchX + 21, swatchY + 19, 0xFFFFFFFF);
        extractor.fill(swatchX, swatchY, swatchX + 20, swatchY + 18, 0xFF000000 | selectedColor);
    }
}
