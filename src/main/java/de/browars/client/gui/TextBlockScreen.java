package de.browars.client.gui;

import de.browars.network.OpenTextBlockPayload;
import de.browars.network.SaveTextBlockPayload;
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

public class TextBlockScreen extends Screen {
    private final OpenTextBlockPayload initialData;
    private EditBox textInput;
    private StringWidget statusLabel;

    public TextBlockScreen(OpenTextBlockPayload payload) {
        super(Component.literal("§6§l📝 Text-Block (Zwischenablage)"));
        this.initialData = payload;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = 20;

        // Title
        this.addRenderableOnly(new StringWidget(centerX - 150, startY, 300, 16, this.title, this.font));

        startY += 24;
        // Text Input Box (320px wide)
        this.addRenderableOnly(new StringWidget(centerX - 160, startY, 320, 16, Component.literal("§7Text-Inhalt (Kopieren & Einfügen):"), this.font));

        startY += 18;
        textInput = new EditBox(this.font, centerX - 160, startY, 320, 22, Component.literal("Text..."));
        textInput.setMaxLength(32767);
        textInput.setValue(initialData.text());
        this.addRenderableWidget(textInput);

        // Action buttons row
        startY += 30;
        int btnW = 104;
        int gap = 4;

        // Kopieren button
        this.addRenderableWidget(
                Button.builder(Component.literal("§b📋 Kopieren"), btn -> copyToClipboard())
                        .bounds(centerX - 160, startY, btnW, 20).build()
        );

        // Einfügen button
        this.addRenderableWidget(
                Button.builder(Component.literal("§d📥 Einfügen"), btn -> pasteFromClipboard())
                        .bounds(centerX - 160 + btnW + gap, startY, btnW, 20).build()
        );

        // Löschen button
        this.addRenderableWidget(
                Button.builder(Component.literal("§c🗑 Löschen"), btn -> {
                    textInput.setValue("");
                    setStatus("§cText geleert.");
                }).bounds(centerX - 160 + (btnW + gap) * 2, startY, btnW, 20).build()
        );

        // Status Feedback Label
        startY += 26;
        statusLabel = new StringWidget(centerX - 150, startY, 300, 16, Component.empty(), this.font);
        this.addRenderableOnly(statusLabel);

        // Save & Cancel buttons
        int bottomY = this.height - 35;
        this.addRenderableWidget(
                Button.builder(Component.literal("§a✔ Speichern"), btn -> saveAndClose())
                        .bounds(centerX - 110, bottomY, 100, 20).build()
        );
        this.addRenderableWidget(
                Button.builder(CommonComponents.GUI_CANCEL, btn -> this.onClose())
                        .bounds(centerX + 10, bottomY, 100, 20).build()
        );
    }

    private void copyToClipboard() {
        String content = textInput.getValue();
        Minecraft.getInstance().keyboardHandler.setClipboard(content);
        setStatus("§aText in Zwischenablage kopiert!");

        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.sendSystemMessage(Component.literal("§a[BroWars] Text in Zwischenablage kopiert!"));
        }
    }

    private void pasteFromClipboard() {
        String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (clip != null && !clip.isEmpty()) {
            textInput.setValue(clip);
            setStatus("§aText aus Zwischenablage eingefügt!");
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                player.sendSystemMessage(Component.literal("§a[BroWars] Text aus Zwischenablage eingefügt!"));
            }
        } else {
            setStatus("§cZwischenablage ist leer!");
        }
    }

    private void setStatus(String message) {
        if (statusLabel != null) {
            statusLabel.setMessage(Component.literal(message));
        }
    }

    private void saveAndClose() {
        SaveTextBlockPayload payload = new SaveTextBlockPayload(
                initialData.pos(),
                textInput.getValue()
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
