package de.browars.client.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Unified UI component library for all BroWars Minigames and the Game Show.
 * Ensures identical card proportions, colors, borders, typography, and animations everywhere.
 */
public final class BroWarsUiHelper {

    // Uniform 95% opacity neutral anthracite surfaces (no blue tint, consistent across all UI)
    public static final int BG_PANEL = 0xF2141416;
    public static final int BG_CARD = 0xF21E1E22;
    public static final int BG_ELEVATED = 0xF228282E;
    public static final int BG_REEL = 0xF20E0E10;

    public static final int BORDER_DARK = 0xFF0A0A0C;
    public static final int BORDER_SUBTLE = 0xFF2E2E34;

    public static final int TEXT_PRIMARY = 0xFFFFFFFF;
    public static final int TEXT_SUB = 0xFFD4D4D8;
    public static final int TEXT_MUTED = 0xFF8E8E98;

    // Signature Game Show / Start-Area accent palette (#FFBB00, #F7770F, #D61811)
    public static final int ACCENT_GOLD = 0xFFFFBB00;
    public static final int ACCENT_ORANGE = 0xFFF7770F;
    public static final int ACCENT_RED = 0xFFD61811;
    public static final int ACCENT_MUTED = 0xFF5C3D14;
    public static final int ACCENT_GREEN = 0xFF22C55E;
    public static final int ACCENT_BLUE = 0xFFF7770F;

    private BroWarsUiHelper() {}

    public static void drawPanel(
            GuiGraphicsExtractor extractor,
            int left,
            int top,
            int right,
            int bottom,
            int accentColor
    ) {
        drawPanel(extractor, left, top, right, bottom, accentColor, accentColor);
    }

    public static void drawPanel(
            GuiGraphicsExtractor extractor,
            int left,
            int top,
            int right,
            int bottom,
            int topAccent,
            int bottomAccent
    ) {
        extractor.fill(left - 1, top - 1, right + 1, bottom + 1, BORDER_DARK);
        extractor.fill(left, top, right, bottom, BG_PANEL);
        extractor.fill(left, top, left + 1, bottom, BORDER_SUBTLE);
        extractor.fill(right - 1, top, right, bottom, BORDER_SUBTLE);
        extractor.fill(left, top, right, top + 1, 0xFF000000 | (topAccent & 0x00FFFFFF));
        extractor.fill(left, bottom - 1, right, bottom, 0xFF000000 | (bottomAccent & 0x00FFFFFF));
    }

    public static String formatDots(int score, int maxDots, boolean reverse) {
        int count = Math.clamp(maxDots, 1, 7);
        StringBuilder sb = new StringBuilder();
        if (reverse) {
            for (int i = count - 1; i >= 0; i--) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(score > i ? "§6●" : "§8○");
            }
        } else {
            for (int i = 0; i < count; i++) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(score > i ? "§6●" : "§8○");
            }
        }
        return sb.toString();
    }

    public static void drawPlayerCard(
            GuiGraphicsExtractor extractor,
            Font font,
            int left,
            int top,
            int width,
            int height,
            String rawName,
            int teamColorHex,
            boolean rightAligned,
            String bottomText
    ) {
        int right = left + width;
        int bottom = top + height;
        int accent = 0xFF000000 | (teamColorHex & 0x00FFFFFF);
        if ((accent & 0x00FFFFFF) == 0) {
            accent = rightAligned ? 0xFF2563EB : 0xFFDC2626;
        }

        extractor.fill(left - 1, top - 1, right + 1, bottom + 1, BORDER_DARK);
        extractor.fill(left, top, right, bottom, BG_CARD);
        extractor.fill(left, top, right, top + 1, BORDER_SUBTLE);
        extractor.fill(left, bottom - 1, right, bottom, BORDER_SUBTLE);

        if (rightAligned) {
            extractor.fill(right - 3, top, right, bottom, accent);
        } else {
            extractor.fill(left, top, left + 3, bottom, accent);
        }

        String fallbackName = rightAligned ? "Spieler 2" : "Spieler 1";
        String cleanName = (rawName == null || rawName.isEmpty()) ? fallbackName : rawName;
        String truncated = font.plainSubstrByWidth(cleanName, width - 12);

        if (rightAligned) {
            int nameW = font.width(truncated);
            extractor.text(font, truncated, right - 7 - nameW, top + 4, 0xFFFFFFFF, true);
            if (bottomText != null && !bottomText.isEmpty()) {
                int subW = font.width(bottomText);
                extractor.text(font, bottomText, right - 7 - subW, top + 15, 0xFFFFBB00, true);
            }
        } else {
            extractor.text(font, truncated, left + 7, top + 4, 0xFFFFFFFF, true);
            if (bottomText != null && !bottomText.isEmpty()) {
                extractor.text(font, bottomText, left + 7, top + 15, 0xFFFFBB00, true);
            }
        }
    }

    public static void drawTopScorebar(
            GuiGraphicsExtractor extractor,
            Font font,
            int screenWidth,
            int centerX,
            String centerTitle,
            String centerSubtitle,
            String p1Name,
            int p1ColorHex,
            String p1BottomText,
            String p2Name,
            int p2ColorHex,
            String p2BottomText
    ) {
        int top = 8;
        int cardH = 28;
        boolean hasSub = centerSubtitle != null && !centerSubtitle.isEmpty();

        int titleW = font.width(centerTitle);
        int subW = hasSub ? font.width(centerSubtitle) : 0;
        int centerW = Math.max(106, Math.max(titleW, subW) + 18);
        int cLeft = centerX - centerW / 2;
        int cRight = cLeft + centerW;
        int cBottom = top + cardH;
        int gap = 4;

        int pCardW = Math.max(82, Math.min(126, (screenWidth - centerW - 20) / 2));

        // Left Player 1 Card
        int p1Right = cLeft - gap;
        int p1Left = p1Right - pCardW;
        drawPlayerCard(extractor, font, p1Left, top, pCardW, cardH, p1Name, p1ColorHex, false, p1BottomText);

        // Center Info Card
        drawPanel(extractor, cLeft, top, cRight, cBottom, ACCENT_GOLD);

        if (hasSub) {
            extractor.centeredText(font, centerTitle, centerX, top + 4, ACCENT_GOLD);
            extractor.centeredText(font, centerSubtitle, centerX, top + 15, 0xFFD4D4D8);
        } else {
            extractor.centeredText(font, centerTitle, centerX, top + 10, ACCENT_GOLD);
        }

        // Right Player 2 Card (only when 2nd player exists)
        if (p2Name != null && !p2Name.isEmpty()) {
            int p2Left = cRight + gap;
            drawPlayerCard(extractor, font, p2Left, top, pCardW, cardH, p2Name, p2ColorHex, true, p2BottomText);
        }
    }

    public static void drawBadge(
            GuiGraphicsExtractor extractor,
            Font font,
            int centerX,
            int top,
            int minWidth,
            String text,
            int bgFill,
            int borderFill,
            int textColor,
            float progress,
            int barColor
    ) {
        int badgeW = Math.max(minWidth, font.width(text) + 20);
        int badgeH = 22;
        int bLeft = centerX - badgeW / 2;
        int bRight = bLeft + badgeW;
        int bBottom = top + badgeH;

        extractor.fill(bLeft - 1, top - 1, bRight + 1, bBottom + 1, BORDER_DARK);
        extractor.fill(bLeft, top, bRight, bBottom, bgFill);
        extractor.fill(bLeft, top, bRight, top + 1, borderFill);
        extractor.centeredText(font, text, centerX, progress >= 0f ? top + 4 : top + 7, textColor);

        if (progress >= 0f) {
            float clamped = Math.clamp(progress, 0f, 1f);
            int barMaxW = badgeW - 6;
            int curBarW = (int) (barMaxW * clamped);
            extractor.fill(bLeft + 3, bBottom - 4, bRight - 3, bBottom - 2, BG_REEL);
            extractor.fill(bLeft + 3, bBottom - 4, bLeft + 3 + curBarW, bBottom - 2, barColor);
        }
    }

    public static void drawCenterBanner(
            GuiGraphicsExtractor extractor,
            Font font,
            int screenWidth,
            int centerX,
            float centerY,
            String title,
            String subtitle,
            int colorHex
    ) {
        String stripped = title.replaceAll("§[0-9a-fk-or]", "").trim();
        boolean isNumber = stripped.length() <= 2 && !stripped.isEmpty() && Character.isDigit(stripped.charAt(0));

        int accentColor = 0xFF000000 | (colorHex & 0x00FFFFFF);
        if ((accentColor & 0x00FFFFFF) == 0) accentColor = ACCENT_GOLD;

        if (isNumber) {
            int plateW = 56;
            int plateH = 42;
            int px1 = centerX - plateW / 2;
            int py1 = (int) (centerY - plateH / 2f);
            int px2 = px1 + plateW;
            int py2 = py1 + plateH;

            drawPanel(extractor, px1, py1, px2, py2, accentColor);

            float scale = 3.0f;
            extractor.pose().pushMatrix();
            extractor.pose().translate(centerX, centerY);
            extractor.pose().scale(scale, scale);
            int strW = font.width(title);
            extractor.text(font, title, -strW / 2, -4, accentColor, true);
            extractor.pose().popMatrix();
        } else {
            boolean hasSub = subtitle != null && !subtitle.isEmpty();
            int titleW = (int) (font.width(title) * 1.4f);
            int subW = hasSub ? font.width(subtitle) : 0;
            int contentW = Math.max(titleW, subW);
            int bannerW = Math.min(screenWidth - 20, Math.max(180, contentW + 32));
            int bannerH = hasSub ? 48 : 34;
            int bx1 = centerX - bannerW / 2;
            int bx2 = centerX + bannerW / 2;
            int by1 = (int) (centerY - bannerH / 2f);
            int by2 = by1 + bannerH;

            drawPanel(extractor, bx1, by1, bx2, by2, accentColor);

            float scale = Math.min(1.4f, (screenWidth - 48f) / Math.max(1, font.width(title)));
            extractor.pose().pushMatrix();
            extractor.pose().translate(centerX, hasSub ? (centerY - 7) : centerY);
            extractor.pose().scale(scale, scale);
            int strW = font.width(title);
            extractor.text(font, title, -strW / 2, -4, accentColor, true);
            extractor.pose().popMatrix();

            if (hasSub) {
                extractor.centeredText(font, subtitle, centerX, (int) centerY + 10, 0xFFD4D4D8);
            }
        }
    }
}
