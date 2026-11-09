package de.browars.client.gui;

import de.browars.network.DeleteParkourScorePayload;
import de.browars.network.OpenParkourLeaderboardPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class ParkourLeaderboardScreen extends Screen {
    private final OpenParkourLeaderboardPayload payload;
    private final List<OpenParkourLeaderboardPayload.Entry> entries = new ArrayList<>();
    private int page = 0;
    private static final int ITEMS_PER_PAGE = 5;

    public ParkourLeaderboardScreen(OpenParkourLeaderboardPayload payload) {
        super(Component.literal("§6§lParkour Highscores (Admin)"));
        this.payload = payload;
        if (payload.entries() != null) {
            this.entries.addAll(payload.entries());
        }
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = 16;

        // Title
        this.addRenderableOnly(new StringWidget(centerX - 150, startY, 300, 16, this.title, this.font));

        startY += 20;
        this.addRenderableOnly(new StringWidget(centerX - 150, startY, 300, 14,
                Component.literal("§7Gespeicherte Bestzeiten verwalten & löschen"), this.font));

        startY += 24;

        if (entries.isEmpty()) {
            this.addRenderableOnly(new StringWidget(centerX - 120, startY + 40, 240, 20,
                    Component.literal("§7Noch keine Zeiten eingetragen."), this.font));
        } else {
            int startIndex = page * ITEMS_PER_PAGE;
            int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, entries.size());

            int y = startY;
            for (int i = startIndex; i < endIndex; i++) {
                final int scoreIndex = i;
                OpenParkourLeaderboardPayload.Entry entry = entries.get(i);

                String rankStr = switch (i) {
                    case 0 -> "§e#1";
                    case 1 -> "§f#2";
                    case 2 -> "§6#3";
                    default -> "§7#" + (i + 1);
                };

                String text = String.format("%s §f%s §7- §a%s", rankStr, entry.playerName(), entry.timeFormatted());
                this.addRenderableOnly(new StringWidget(centerX - 160, y + 2, 230, 16, Component.literal(text), this.font));

                this.addRenderableWidget(
                        Button.builder(Component.literal("§cLöschen"), btn -> {
                            ClientPlayNetworking.send(new DeleteParkourScorePayload(payload.pos(), scoreIndex));
                            entries.remove(scoreIndex);
                            int maxP = Math.max(1, (int) Math.ceil((double) entries.size() / ITEMS_PER_PAGE));
                            if (page >= maxP) page = Math.max(0, maxP - 1);
                            this.rebuildWidgets();
                        }).bounds(centerX + 80, y, 80, 18).build()
                );

                y += 24;
            }

            int maxPages = Math.max(1, (int) Math.ceil((double) entries.size() / ITEMS_PER_PAGE));
            if (page > 0) {
                this.addRenderableWidget(
                        Button.builder(Component.literal("< Zurück"), btn -> {
                            page--;
                            this.rebuildWidgets();
                        }).bounds(centerX - 160, this.height - 55, 75, 20).build()
                );
            }
            if (page < maxPages - 1) {
                this.addRenderableWidget(
                        Button.builder(Component.literal("Weiter >"), btn -> {
                            page++;
                            this.rebuildWidgets();
                        }).bounds(centerX + 85, this.height - 55, 75, 20).build()
                );
            }

            if (maxPages > 1) {
                this.addRenderableOnly(new StringWidget(centerX - 40, this.height - 53, 80, 16,
                        Component.literal(String.format("§7Seite %d / %d", page + 1, maxPages)), this.font));
            }
        }

        // Bottom Action row: Alle löschen & Schließen
        int bottomY = this.height - 28;
        if (!entries.isEmpty()) {
            this.addRenderableWidget(
                    Button.builder(Component.literal("§4Alle löschen"), btn -> {
                        ClientPlayNetworking.send(new DeleteParkourScorePayload(payload.pos(), -1));
                        entries.clear();
                        page = 0;
                        this.rebuildWidgets();
                    }).bounds(centerX - 160, bottomY, 155, 20).build()
            );

            this.addRenderableWidget(
                    Button.builder(CommonComponents.GUI_DONE, btn -> this.onClose())
                            .bounds(centerX + 5, bottomY, 155, 20).build()
            );
        } else {
            this.addRenderableWidget(
                    Button.builder(CommonComponents.GUI_DONE, btn -> this.onClose())
                            .bounds(centerX - 60, bottomY, 120, 20).build()
            );
        }
    }
}
