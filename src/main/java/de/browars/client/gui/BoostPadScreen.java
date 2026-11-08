package de.browars.client.gui;

import de.browars.network.OpenBoostPadPayload;
import de.browars.network.SaveBoostPadPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public class BoostPadScreen extends Screen {
    private final OpenBoostPadPayload initialData;

    private EditBox strengthInput;
    private EditBox hexInput;
    private boolean lookDirectionMode;
    private int selectedColor;

    private int swatchX = 0;
    private int swatchY = 0;

    private static final int[] PRESET_COLORS = {
            0x00FFFF, // Cyan
            0xFACC15, // Yellow
            0xFF8800, // Orange
            0xFF2222, // Red
            0x22FF22, // Green
            0xFF00FF, // Magenta
            0x8A2BE2, // Purple
            0xFFFFFF  // White
    };

    private static final String[] PRESET_NAMES = {
            "Cyan", "Gelb", "Orange", "Rot", "Grün", "Pink", "Lila", "Weiß"
    };

    public BoostPadScreen(OpenBoostPadPayload data) {
        super(Component.literal("§e§lBoost-Pad Einstellungen"));
        this.initialData = data;
        this.lookDirectionMode = data.lookDirectionMode();
        this.selectedColor = data.colorHex();
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = 30;

        // Title
        this.addRenderableOnly(new StringWidget(centerX - 100, 10, 200, 16, this.title, this.font));

        // 1. Boost-Modus (Blickrichtung vs Pad-Richtung)
        this.addRenderableWidget(
                CycleButton.booleanBuilder(Component.literal("§aWo man schaut (Blick)"), Component.literal("§6Pad-Richtung (Pfeil)"), lookDirectionMode)
                        .create(centerX - 120, startY, 240, 20, Component.literal("Richtung"),
                                (btn, val) -> lookDirectionMode = val)
        );

        // 2. Boost-Stärke
        startY += 26;
        this.addRenderableOnly(new StringWidget(centerX - 120, startY, 100, 16, Component.literal("Boost-Stärke:"), this.font));

        strengthInput = new EditBox(this.font, centerX - 10, startY, 50, 18, Component.literal("Stärke"));
        strengthInput.setValue(String.format(Locale.ROOT, "%.1f", initialData.strength()));
        this.addRenderableWidget(strengthInput);

        // Quick Strength presets
        int qY = startY + 22;
        double[] qVals = {1.5, 2.0, 3.0, 5.0};
        String[] qLabels = {"1.5x", "2.0x", "3.0x", "5.0x"};
        for (int i = 0; i < 4; i++) {
            double v = qVals[i];
            this.addRenderableWidget(
                    Button.builder(Component.literal(qLabels[i]), btn -> {
                        strengthInput.setValue(String.format(Locale.ROOT, "%.1f", v));
                    }).bounds(centerX - 120 + i * 62, qY, 56, 18).build()
            );
        }

        // 3. Zweitfarbe (Schimmer & Pfeile)
        startY = qY + 26;
        this.addRenderableOnly(new StringWidget(centerX - 120, startY, 120, 16, Component.literal("§bZweitfarbe (Effekt/Pfeil):"), this.font));

        hexInput = new EditBox(this.font, centerX + 10, startY, 65, 18, Component.literal("Hex"));
        hexInput.setValue(String.format("#%06X", selectedColor & 0xFFFFFF));
        hexInput.setResponder(val -> {
            try {
                String clean = val.replace("#", "").trim();
                selectedColor = (int) Long.parseLong(clean, 16);
            } catch (Exception ignored) {}
        });
        this.addRenderableWidget(hexInput);

        this.swatchX = centerX + 85;
        this.swatchY = startY;

        // Color presets
        startY += 24;
        int btnW = 56;
        int btnH = 18;
        for (int i = 0; i < PRESET_COLORS.length; i++) {
            final int col = PRESET_COLORS[i];
            int colIndex = i % 4;
            int rowIndex = i / 4;
            int bx = centerX - 120 + colIndex * (btnW + 6);
            int by = startY + rowIndex * (btnH + 4);

            this.addRenderableWidget(
                    Button.builder(Component.literal(PRESET_NAMES[i]), btn -> {
                        selectedColor = col;
                        hexInput.setValue(String.format("#%06X", col & 0xFFFFFF));
                    }).bounds(bx, by, btnW, btnH).build()
            );
        }

        // Bottom action buttons
        int bottomY = Math.max(startY + 46, this.height - 28);
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
        double strength;
        try {
            strength = Double.parseDouble(strengthInput.getValue().trim());
        } catch (Exception e) {
            strength = initialData.strength();
        }

        SaveBoostPadPayload payload = new SaveBoostPadPayload(
                initialData.pos(),
                strength,
                selectedColor,
                lookDirectionMode
        );
        ClientPlayNetworking.send(payload);
        this.onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        this.extractTransparentBackground(extractor);
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);

        if (swatchX > 0 && swatchY > 0) {
            extractor.fill(swatchX - 1, swatchY - 1, swatchX + 26, swatchY + 19, 0xFFFFFFFF);
            extractor.fill(swatchX, swatchY, swatchX + 25, swatchY + 18, 0xFF000000 | selectedColor);
        }
    }
}
