package de.browars.client.gui;

import de.browars.client.gameshow.GameshowClientHolder;
import de.browars.client.render.BroWarsUiHelper;
import de.browars.minigame.TeamColor;
import de.browars.network.SelectGameshowColorPayload;
import de.browars.network.SyncGameshowColorSelectPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class GameshowColorSelectScreen extends Screen {

    private static GameshowColorSelectScreen activeInstance = null;

    private static final TeamColor[] SELECTABLE_COLORS = {
            TeamColor.RED,
            TeamColor.BLUE,
            TeamColor.GREEN,
            TeamColor.YELLOW,
            TeamColor.ORANGE,
            TeamColor.PURPLE,
            TeamColor.PINK,
            TeamColor.LIGHT_BLUE,
            TeamColor.LIME,
            TeamColor.MAGENTA,
            TeamColor.BROWN
    };

    private static final class SilentFlatButton extends Button {
        SilentFlatButton(int x, int y, int width, int height, OnPress onPress) {
            super(x, y, width, height, Component.empty(), onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
            // Flat custom rendering is drawn in GameshowColorSelectScreen.extractRenderState
        }

        @Override
        public void playDownSound(SoundManager soundManager) {
            // Server sends a single crisp sound when selection changes
        }
    }

    private record ColorTileSpec(int x, int y, int w, int h, TeamColor color, boolean isMine, boolean isOpponent, SilentFlatButton button) {}
    private final List<ColorTileSpec> tileSpecs = new ArrayList<>();
    private SilentFlatButton readyButton = null;

    public GameshowColorSelectScreen() {
        super(Component.literal("§6§lTEAMFARBE"));
    }

    public static void openOrUpdate() {
        Minecraft mc = Minecraft.getInstance();
        if (activeInstance != null) {
            activeInstance.onSyncUpdated();
        } else {
            GameshowColorSelectScreen screen = new GameshowColorSelectScreen();
            activeInstance = screen;
            mc.gui.setScreen(screen);
        }
    }

    public static void closeIfOpen() {
        if (activeInstance != null) {
            activeInstance = null;
            Minecraft.getInstance().gui.setScreen(null);
        }
    }

    @Override
    public void removed() {
        super.removed();
        if (activeInstance == this) {
            activeInstance = null;
        }
    }

    public void onSyncUpdated() {
        if (this.minecraft != null) {
            this.rebuildWidgets();
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        tileSpecs.clear();
        readyButton = null;
        SyncGameshowColorSelectPayload state = GameshowClientHolder.getColorSelectState();
        if (state == null) return;

        int centerX = this.width / 2;
        int panelTop = Math.max(12, (this.height - 196) / 2);

        String myColorId = state.playerNumber() == 1 ? state.p1ColorId() : state.p2ColorId();
        String oppColorId = state.playerNumber() == 1 ? state.p2ColorId() : state.p1ColorId();
        boolean amReady = state.playerNumber() == 1 ? state.p1Ready() : state.p2Ready();

        int cols = 4;
        int btnW = 74;
        int btnH = 22;
        int gapX = 6;
        int gapY = 6;
        int gridW = cols * btnW + (cols - 1) * gapX;
        int startX = centerX - gridW / 2;
        int gridTop = panelTop + 66;

        for (int i = 0; i < SELECTABLE_COLORS.length; i++) {
            TeamColor tc = SELECTABLE_COLORS[i];
            int col = i % cols;
            int row = i / cols;
            int bx = startX + col * (btnW + gapX);
            int by = gridTop + row * (btnH + gapY);

            boolean isMine = tc.id.equalsIgnoreCase(myColorId);
            boolean isOpponent = tc.id.equalsIgnoreCase(oppColorId);

            SilentFlatButton btn = new SilentFlatButton(bx, by, btnW, btnH, b -> {
                ClientPlayNetworking.send(new SelectGameshowColorPayload(tc.id, false));
            });
            btn.active = !isOpponent && !isMine && !amReady;
            this.addRenderableWidget(btn);
            tileSpecs.add(new ColorTileSpec(bx, by, btnW, btnH, tc, isMine, isOpponent, btn));
        }

        int readyBtnY = gridTop + 3 * (btnH + gapY) + 10;
        readyButton = new SilentFlatButton(centerX - 84, readyBtnY, 168, 22, b -> {
            ClientPlayNetworking.send(new SelectGameshowColorPayload(myColorId, true));
        });
        this.addRenderableWidget(readyButton);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        int w = this.width;
        int h = this.height;
        extractor.fill(0, 0, w, h, 0xD808080A);

        SyncGameshowColorSelectPayload state = GameshowClientHolder.getColorSelectState();
        if (state == null) {
            super.extractRenderState(extractor, mouseX, mouseY, partialTick);
            return;
        }

        int centerX = w / 2;
        int panelW = 340;
        int panelH = 196;
        int panelLeft = centerX - panelW / 2;
        int panelRight = centerX + panelW / 2;
        int panelTop = Math.max(12, (h - panelH) / 2);
        int panelBottom = panelTop + panelH;

        BroWarsUiHelper.drawPanel(
                extractor,
                panelLeft,
                panelTop,
                panelRight,
                panelBottom,
                BroWarsUiHelper.ACCENT_GOLD
        );

        extractor.centeredText(
                this.font,
                "§6§lTEAMFARBE §8• §7" + state.remainingSeconds() + "s",
                centerX,
                panelTop + 8,
                BroWarsUiHelper.ACCENT_GOLD
        );

        TeamColor p1Col = TeamColor.fromString(state.p1ColorId(), TeamColor.RED);
        TeamColor p2Col = TeamColor.fromString(state.p2ColorId(), TeamColor.BLUE);

        int cardW = 140;
        int cardH = 28;
        int cardY = panelTop + 26;

        String p1Sub = state.p1Ready() ? "§a✔ Bereit" : (p1Col.chatPrefix + p1Col.displayName);
        String p2Sub = state.p2Ready() ? "§a✔ Bereit" : (p2Col.chatPrefix + p2Col.displayName);

        BroWarsUiHelper.drawPlayerCard(extractor, this.font, centerX - 10 - cardW, cardY, cardW, cardH, state.p1Name(), p1Col.hexColor, false, p1Sub);
        extractor.centeredText(this.font, "§8§lVS", centerX, cardY + 10, BroWarsUiHelper.TEXT_MUTED);
        BroWarsUiHelper.drawPlayerCard(extractor, this.font, centerX + 10, cardY, cardW, cardH, state.p2Name(), p2Col.hexColor, true, p2Sub);

        super.extractRenderState(extractor, mouseX, mouseY, partialTick);

        // Render Clean Color Tiles
        for (ColorTileSpec spec : tileSpecs) {
            boolean hovered = spec.button.active && spec.button.isHovered();
            int colHex = 0xFF000000 | spec.color.hexColor;

            int borderCol;
            int bgCol;
            if (spec.isMine) {
                borderCol = BroWarsUiHelper.ACCENT_GOLD;
                bgCol = BroWarsUiHelper.BG_ELEVATED;
            } else if (spec.isOpponent) {
                borderCol = BroWarsUiHelper.BORDER_DARK;
                bgCol = 0xF2101012;
            } else if (hovered) {
                borderCol = BroWarsUiHelper.ACCENT_ORANGE;
                bgCol = BroWarsUiHelper.BG_ELEVATED;
            } else {
                borderCol = BroWarsUiHelper.BORDER_SUBTLE;
                bgCol = BroWarsUiHelper.BG_CARD;
            }

            extractor.fill(spec.x - 1, spec.y - 1, spec.x + spec.w + 1, spec.y + spec.h + 1, borderCol);
            extractor.fill(spec.x, spec.y, spec.x + spec.w, spec.y + spec.h, bgCol);

            // Left Color Swatch Square (12x12)
            int swX = spec.x + 5;
            int swY = spec.y + (spec.h - 12) / 2;
            extractor.fill(swX - 1, swY - 1, swX + 13, swY + 13, BroWarsUiHelper.BORDER_DARK);
            int swatchFill = spec.isOpponent ? ((colHex & 0x00FEFEFE) >> 1 | 0xFF000000) : colHex;
            extractor.fill(swX, swY, swX + 12, swY + 12, swatchFill);

            String label;
            int textCol;
            if (spec.isMine) {
                label = "§f§l" + spec.color.displayName;
                textCol = BroWarsUiHelper.TEXT_PRIMARY;
                extractor.centeredText(this.font, "§f✔", swX + 6, swY + 2, BroWarsUiHelper.TEXT_PRIMARY);
            } else if (spec.isOpponent) {
                label = "§8" + spec.color.displayName;
                textCol = BroWarsUiHelper.TEXT_MUTED;
                extractor.centeredText(this.font, "§7✖", swX + 6, swY + 2, BroWarsUiHelper.TEXT_MUTED);
            } else {
                label = (hovered ? "§f" : "§7") + spec.color.displayName;
                textCol = hovered ? BroWarsUiHelper.TEXT_PRIMARY : BroWarsUiHelper.TEXT_SUB;
            }
            extractor.text(this.font, label, swX + 17, spec.y + 7, textCol, true);
        }

        // Render Clean Flat Ready Button
        if (readyButton != null) {
            boolean amReady = state.playerNumber() == 1 ? state.p1Ready() : state.p2Ready();
            boolean rHover = readyButton.isHovered();
            int rx = readyButton.getX();
            int ry = readyButton.getY();
            int rw = readyButton.getWidth();
            int rh = readyButton.getHeight();

            int rBorder = amReady
                    ? BroWarsUiHelper.ACCENT_GREEN
                    : (rHover ? BroWarsUiHelper.ACCENT_GOLD : BroWarsUiHelper.ACCENT_ORANGE);
            int rBg = rHover ? BroWarsUiHelper.BG_ELEVATED : BroWarsUiHelper.BG_CARD;
            int rSide = amReady ? BroWarsUiHelper.ACCENT_GREEN : BroWarsUiHelper.ACCENT_ORANGE;

            extractor.fill(rx - 1, ry - 1, rx + rw + 1, ry + rh + 1, rBorder);
            extractor.fill(rx, ry, rx + rw, ry + rh, rBg);
            extractor.fill(rx, ry, rx + 4, ry + rh, rSide);
            extractor.fill(rx + rw - 4, ry, rx + rw, ry + rh, rSide);

            String readyText = amReady ? "§a§l✔ BEREIT" : "§6§lBEREIT";
            extractor.centeredText(this.font, readyText, rx + rw / 2, ry + 7, BroWarsUiHelper.TEXT_PRIMARY);
        }
    }
}
