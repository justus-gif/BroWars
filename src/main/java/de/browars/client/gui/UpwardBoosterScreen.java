package de.browars.client.gui;

import de.browars.network.OpenUpwardBoosterPayload;
import de.browars.network.SaveUpwardBoosterPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public class UpwardBoosterScreen extends Screen {
    private final OpenUpwardBoosterPayload initialData;
    private EditBox heightInput;

    public UpwardBoosterScreen(OpenUpwardBoosterPayload data) {
        super(Component.literal("§b§lAufwärts-Booster Einstellungen"));
        this.initialData = data;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = 45;

        // Title
        this.addRenderableOnly(new StringWidget(centerX - 120, 15, 240, 16, this.title, this.font));

        // Subtitle explanation
        this.addRenderableOnly(new StringWidget(centerX - 140, 32, 280, 16,
                Component.literal("§7Katapultiert Spieler senkrecht nach oben."), this.font));

        // Height Input label
        this.addRenderableOnly(new StringWidget(centerX - 120, startY + 15, 120, 16,
                Component.literal("Sprunghöhe (Blöcke):"), this.font));

        heightInput = new EditBox(this.font, centerX + 10, startY + 13, 60, 20, Component.literal("Höhe"));
        heightInput.setValue(String.format(Locale.ROOT, "%.1f", initialData.height()));
        this.addRenderableWidget(heightInput);

        // Quick Height preset buttons
        int qY = startY + 45;
        this.addRenderableOnly(new StringWidget(centerX - 120, qY - 14, 160, 14,
                Component.literal("§eSchnell-Auswahl:"), this.font));

        double[] heights = {2.0, 4.0, 6.0, 8.0, 12.0, 16.0};
        String[] labels = {"2m", "4m", "6m", "8m", "12m", "16m"};
        int btnW = 40;
        int btnH = 20;

        for (int i = 0; i < heights.length; i++) {
            double h = heights[i];
            int bx = centerX - 125 + i * 42;
            this.addRenderableWidget(
                    Button.builder(Component.literal(labels[i]), btn -> {
                        heightInput.setValue(String.format(Locale.ROOT, "%.1f", h));
                    }).bounds(bx, qY + 2, btnW, btnH).build()
            );
        }

        // Action buttons
        int bottomY = Math.max(qY + 50, this.height - 35);
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
        double height;
        try {
            height = Double.parseDouble(heightInput.getValue().trim());
        } catch (Exception e) {
            height = initialData.height();
        }

        SaveUpwardBoosterPayload payload = new SaveUpwardBoosterPayload(
                initialData.pos(),
                height
        );
        ClientPlayNetworking.send(payload);
        this.onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        this.extractTransparentBackground(extractor);
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }
}
