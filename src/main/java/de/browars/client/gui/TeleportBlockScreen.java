package de.browars.client.gui;

import de.browars.network.OpenTeleportBlockPayload;
import de.browars.network.SaveTeleportBlockPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

public class TeleportBlockScreen extends Screen {
    private final OpenTeleportBlockPayload initialData;

    private EditBox xInput;
    private EditBox yInput;
    private EditBox zInput;
    private EditBox yawInput;
    private EditBox pitchInput;
    private EditBox nameInput;
    private EditBox hexInput;

    private String selectedDimension;
    private int selectedColor;

    private static final int[] PRESET_COLORS = {
            0x8A2BE2, // Purple
            0x00FFFF, // Cyan
            0xFF2222, // Red
            0x22FF22, // Green
            0xFFFF22, // Yellow
            0xFF8800, // Orange
            0xFFFFFF, // White
            0xFF00FF  // Magenta / Rainbow
    };

    private static final String[] PRESET_NAMES = {
            "Lila", "Cyan", "Rot", "Grün", "Gelb", "Orange", "Weiß", "Pink"
    };

    public TeleportBlockScreen(OpenTeleportBlockPayload data) {
        super(Component.literal("§6§lTeleport-Block Einstellungen"));
        this.initialData = data;
        this.selectedDimension = data.targetDimension();
        this.selectedColor = data.colorHex();
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = 32;

        this.addRenderableOnly(new StringWidget(centerX - 100, 10, 200, 16, this.title, this.font));

        this.addRenderableOnly(new StringWidget(centerX - 150, startY, 100, 16, Component.literal("Name / Titel:"), this.font));
        nameInput = new EditBox(this.font, centerX - 40, startY, 190, 18, Component.literal("Name"));
        nameInput.setValue(initialData.name());
        this.addRenderableWidget(nameInput);

        startY += 26;
        this.addRenderableOnly(new StringWidget(centerX - 150, startY, 80, 16, Component.literal("Ziel-Koord:"), this.font));

        xInput = new EditBox(this.font, centerX - 60, startY, 65, 18, Component.literal("X"));
        xInput.setValue(String.format(Locale.ROOT, "%.2f", initialData.targetX()));
        this.addRenderableWidget(xInput);

        yInput = new EditBox(this.font, centerX + 10, startY, 65, 18, Component.literal("Y"));
        yInput.setValue(String.format(Locale.ROOT, "%.2f", initialData.targetY()));
        this.addRenderableWidget(yInput);

        zInput = new EditBox(this.font, centerX + 80, startY, 65, 18, Component.literal("Z"));
        zInput.setValue(String.format(Locale.ROOT, "%.2f", initialData.targetZ()));
        this.addRenderableWidget(zInput);

        startY += 24;
        this.addRenderableOnly(new StringWidget(centerX - 150, startY, 80, 16, Component.literal("Blickrichtung:"), this.font));

        yawInput = new EditBox(this.font, centerX - 60, startY, 100, 18, Component.literal("Yaw"));
        yawInput.setValue(String.format(Locale.ROOT, "%.1f", initialData.targetYaw()));
        this.addRenderableWidget(yawInput);

        pitchInput = new EditBox(this.font, centerX + 45, startY, 100, 18, Component.literal("Pitch"));
        pitchInput.setValue(String.format(Locale.ROOT, "%.1f", initialData.targetPitch()));
        this.addRenderableWidget(pitchInput);

        startY += 24;
        this.addRenderableWidget(
                CycleButton.<String>builder(val -> Component.literal(val.replace("minecraft:", "").toUpperCase(Locale.ROOT)), selectedDimension)
                        .withValues(List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"))
                        .create(centerX - 150, startY, 130, 20, Component.literal("Dimension"),
                                (btn, val) -> selectedDimension = val)
        );

        this.addRenderableWidget(
                Button.builder(Component.literal("§e⟳ Eigene"), btn -> {
                    LocalPlayer player = Minecraft.getInstance().player;
                    if (player != null) {
                        xInput.setValue(String.format(Locale.ROOT, "%.2f", player.getX()));
                        yInput.setValue(String.format(Locale.ROOT, "%.2f", player.getY()));
                        zInput.setValue(String.format(Locale.ROOT, "%.2f", player.getZ()));
                        yawInput.setValue(String.format(Locale.ROOT, "%.1f", player.getYRot()));
                        pitchInput.setValue(String.format(Locale.ROOT, "%.1f", player.getXRot()));
                        selectedDimension = player.level().dimension().identifier().toString();
                    }
                }).bounds(centerX - 15, startY, 55, 20).build()
        );

        this.addRenderableWidget(
                Button.builder(Component.literal("§d📋 Ein"), btn -> {
                    de.browars.util.PlayerPositionData pos = de.browars.client.ClientClipboard.get();
                    LocalPlayer player = Minecraft.getInstance().player;
                    if (pos != null) {
                        xInput.setValue(String.format(Locale.ROOT, "%.2f", pos.x()));
                        yInput.setValue(String.format(Locale.ROOT, "%.2f", pos.y()));
                        zInput.setValue(String.format(Locale.ROOT, "%.2f", pos.z()));
                        yawInput.setValue(String.format(Locale.ROOT, "%.1f", pos.yaw()));
                        pitchInput.setValue(String.format(Locale.ROOT, "%.1f", pos.pitch()));
                        selectedDimension = pos.dimension();
                        if (player != null) {
                            player.sendSystemMessage(Component.literal("§a[BroWars] Kopierte Position eingefügt!"));
                        }
                    } else if (player != null) {
                        player.sendSystemMessage(Component.literal("§c[BroWars] Keine Position kopiert! Nutze /copypos"));
                    }
                }).bounds(centerX + 45, startY, 50, 20).build()
        );

        this.addRenderableWidget(
                Button.builder(Component.literal("§b📋 Kop"), btn -> {
                    LocalPlayer player = Minecraft.getInstance().player;
                    double tx = parseDouble(xInput.getValue(), 0);
                    double ty = parseDouble(yInput.getValue(), 0);
                    double tz = parseDouble(zInput.getValue(), 0);
                    float tyaw = parseFloat(yawInput.getValue(), 0);
                    float tpitch = parseFloat(pitchInput.getValue(), 0);
                    de.browars.client.ClientClipboard.set(tx, ty, tz, tyaw, tpitch, selectedDimension);
                    if (player != null) {
                        player.sendSystemMessage(Component.literal("§a[BroWars] Koordinaten kopiert!"));
                    }
                }).bounds(centerX + 100, startY, 50, 20).build()
        );

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
        double tx = parseDouble(xInput.getValue(), initialData.targetX());
        double ty = parseDouble(yInput.getValue(), initialData.targetY());
        double tz = parseDouble(zInput.getValue(), initialData.targetZ());
        float tyaw = parseFloat(yawInput.getValue(), initialData.targetYaw());
        float tpitch = parseFloat(pitchInput.getValue(), initialData.targetPitch());

        SaveTeleportBlockPayload payload = new SaveTeleportBlockPayload(
                initialData.pos(),
                tx, ty, tz,
                tyaw, tpitch,
                selectedDimension,
                nameInput.getValue().trim(),
                selectedColor
        );
        ClientPlayNetworking.send(payload);
        this.onClose();
    }

    private double parseDouble(String text, double fallback) {
        try { return Double.parseDouble(text.trim()); } catch (Exception e) { return fallback; }
    }

    private float parseFloat(String text, float fallback) {
        try { return Float.parseFloat(text.trim()); } catch (Exception e) { return fallback; }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        this.extractTransparentBackground(extractor);
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int swatchX = centerX + 50;
        int swatchY = 134;
        extractor.fill(swatchX - 1, swatchY - 1, swatchX + 21, swatchY + 19, 0xFFFFFFFF);
        extractor.fill(swatchX, swatchY, swatchX + 20, swatchY + 18, 0xFF000000 | selectedColor);
    }
}
