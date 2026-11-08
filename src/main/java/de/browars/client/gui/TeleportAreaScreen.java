package de.browars.client.gui;

import de.browars.network.OpenTeleportAreaPayload;
import de.browars.network.SaveTeleportAreaPayload;
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

public class TeleportAreaScreen extends Screen {
    private final OpenTeleportAreaPayload initialData;

    private EditBox xInput;
    private EditBox yInput;
    private EditBox zInput;
    private EditBox yawInput;
    private EditBox pitchInput;
    private EditBox nameInput;
    private EditBox hexInput;

    private String selectedDimension;
    private int selectedColor;
    private boolean visibleInSurvival;
    private boolean showNameHologram;

    private int swatchX = 0;
    private int swatchY = 0;

    private static final int[] PRESET_COLORS = {
            0x00FFFF, // Cyan
            0x8A2BE2, // Purple
            0xFF2222, // Red
            0x22FF22, // Green
            0xFFFF22, // Yellow
            0xFF8800, // Orange
            0xFFFFFF, // White
            0xFF00FF  // Pink
    };

    private static final String[] PRESET_NAMES = {
            "Cyan", "Lila", "Rot", "Grün", "Gelb", "Orange", "Weiß", "Pink"
    };

    public TeleportAreaScreen(OpenTeleportAreaPayload data) {
        super(Component.literal("§b§lTeleport-Fläche Konfiguration"));
        this.initialData = data;
        this.selectedDimension = data.targetDimension();
        this.selectedColor = data.colorHex();
        this.visibleInSurvival = data.visibleInSurvival();
        this.showNameHologram = data.showNameHologram();
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;

        // Title
        this.addRenderableOnly(new StringWidget(centerX - 150, 10, 300, 16, this.title, this.font));

        // Area Bounds Info
        String boundsText = String.format("Bereich: [%d, %d, %d] bis [%d, %d, %d]",
                initialData.pos1().getX(), initialData.pos1().getY(), initialData.pos1().getZ(),
                initialData.pos2().getX(), initialData.pos2().getY(), initialData.pos2().getZ());
        this.addRenderableOnly(new StringWidget(centerX - 165, 26, 330, 14, Component.literal("§7" + boundsText), this.font));

        // ================= LEFT COLUMN (Coordinates & Destination) =================
        int leftX = centerX - 165;
        int leftW = 150;

        // Name
        this.addRenderableOnly(new StringWidget(leftX, 42, leftW, 14, Component.literal("Flächen-Name:"), this.font));
        nameInput = new EditBox(this.font, leftX, 56, leftW, 18, Component.literal("Name"));
        nameInput.setValue(initialData.name());
        this.addRenderableWidget(nameInput);

        // Coordinates (X, Y, Z)
        this.addRenderableOnly(new StringWidget(leftX, 76, leftW, 14, Component.literal("Ziel X / Y / Z:"), this.font));
        int coordW = 48;
        xInput = new EditBox(this.font, leftX, 90, coordW, 18, Component.literal("X"));
        xInput.setValue(String.format(Locale.ROOT, "%.2f", initialData.targetX()));
        this.addRenderableWidget(xInput);

        yInput = new EditBox(this.font, leftX + 51, 90, coordW, 18, Component.literal("Y"));
        yInput.setValue(String.format(Locale.ROOT, "%.2f", initialData.targetY()));
        this.addRenderableWidget(yInput);

        zInput = new EditBox(this.font, leftX + 102, 90, coordW, 18, Component.literal("Z"));
        zInput.setValue(String.format(Locale.ROOT, "%.2f", initialData.targetZ()));
        this.addRenderableWidget(zInput);

        // Yaw & Pitch
        this.addRenderableOnly(new StringWidget(leftX, 110, leftW, 14, Component.literal("Blickrichtung Yaw / Pitch:"), this.font));
        yawInput = new EditBox(this.font, leftX, 124, 72, 18, Component.literal("Yaw"));
        yawInput.setValue(String.format(Locale.ROOT, "%.1f", initialData.targetYaw()));
        this.addRenderableWidget(yawInput);

        pitchInput = new EditBox(this.font, leftX + 78, 124, 72, 18, Component.literal("Pitch"));
        pitchInput.setValue(String.format(Locale.ROOT, "%.1f", initialData.targetPitch()));
        this.addRenderableWidget(pitchInput);

        // Dimension
        this.addRenderableWidget(
                CycleButton.<String>builder(val -> Component.literal(val.replace("minecraft:", "").toUpperCase(Locale.ROOT)), selectedDimension)
                        .withValues(List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"))
                        .create(leftX, 145, leftW, 20, Component.literal("Dimension"),
                                (btn, val) -> selectedDimension = val)
        );

        // Fast Position buttons: [⟳ Eigene], [📋 Ein], and [📋 Kop]
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
                }).bounds(leftX, 168, 50, 20).build()
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
                }).bounds(leftX + 52, 168, 46, 20).build()
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
                }).bounds(leftX + 100, 168, 50, 20).build()
        );

        // ================= RIGHT COLUMN (Settings & Appearance) =================
        int rightX = centerX + 15;
        int rightW = 150;

        // Toggles
        this.addRenderableWidget(
                CycleButton.booleanBuilder(Component.literal("§aSichtbar"), Component.literal("§cUnsichtbar"), visibleInSurvival)
                        .create(rightX, 42, rightW, 20, Component.literal("Survival-Partikel"),
                                (btn, val) -> visibleInSurvival = val)
        );

        this.addRenderableWidget(
                CycleButton.booleanBuilder(Component.literal("§aAktiv"), Component.literal("§cAus"), showNameHologram)
                        .create(rightX, 66, rightW, 20, Component.literal("Schwebender Name"),
                                (btn, val) -> showNameHologram = val)
        );

        // Color Section
        this.addRenderableOnly(new StringWidget(rightX, 90, 50, 18, Component.literal("§bFarbe:"), this.font));

        hexInput = new EditBox(this.font, rightX + 45, 90, 65, 18, Component.literal("Hex"));
        hexInput.setValue(String.format("#%06X", selectedColor & 0xFFFFFF));
        hexInput.setResponder(val -> {
            try {
                String clean = val.replace("#", "").trim();
                selectedColor = (int) Long.parseLong(clean, 16);
            } catch (Exception ignored) {}
        });
        this.addRenderableWidget(hexInput);

        this.swatchX = rightX + 115;
        this.swatchY = 90;

        // 8 Color Presets in 2 columns
        int pColW = 72;
        int pRowH = 18;
        int pStartY = 112;
        for (int i = 0; i < PRESET_COLORS.length; i++) {
            final int col = PRESET_COLORS[i];
            int cIndex = i % 2;
            int rIndex = i / 2;
            int bx = rightX + cIndex * (pColW + 6);
            int by = pStartY + rIndex * (pRowH + 2);

            this.addRenderableWidget(
                    Button.builder(Component.literal(PRESET_NAMES[i]), btn -> {
                        selectedColor = col;
                        hexInput.setValue(String.format("#%06X", col & 0xFFFFFF));
                    }).bounds(bx, by, pColW, pRowH).build()
            );
        }

        // ================= BOTTOM ACTIONS =================
        int bottomY = Math.max(196, this.height - 28);
        this.addRenderableWidget(
                Button.builder(Component.literal("§6📋 Zonen-Manager"), btn -> {
                    Minecraft.getInstance().gui.setScreen(new AreaManagerScreen(this));
                }).bounds(centerX - 165, bottomY, 105, 20).build()
        );

        this.addRenderableWidget(
                Button.builder(Component.literal("§a✔ Speichern"), btn -> saveAndClose())
                        .bounds(centerX - 50, bottomY, 100, 20).build()
        );

        this.addRenderableWidget(
                Button.builder(CommonComponents.GUI_CANCEL, btn -> this.onClose())
                        .bounds(centerX + 60, bottomY, 105, 20).build()
        );
    }

    private void saveAndClose() {
        double tx = parseDouble(xInput.getValue(), initialData.targetX());
        double ty = parseDouble(yInput.getValue(), initialData.targetY());
        double tz = parseDouble(zInput.getValue(), initialData.targetZ());
        float tyaw = parseFloat(yawInput.getValue(), initialData.targetYaw());
        float tpitch = parseFloat(pitchInput.getValue(), initialData.targetPitch());

        SaveTeleportAreaPayload payload = new SaveTeleportAreaPayload(
                initialData.areaId(),
                initialData.pos1(),
                initialData.pos2(),
                tx, ty, tz,
                tyaw, tpitch,
                selectedDimension,
                nameInput.getValue().trim(),
                selectedColor,
                visibleInSurvival,
                showNameHologram
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

        // Draw preview swatch right next to hexInput
        if (swatchX > 0 && swatchY > 0) {
            extractor.fill(swatchX - 1, swatchY - 1, swatchX + 26, swatchY + 19, 0xFFFFFFFF);
            extractor.fill(swatchX, swatchY, swatchX + 25, swatchY + 18, 0xFF000000 | selectedColor);
        }
    }
}
