package com.playermarket.client.gui;

import com.playermarket.network.MarketNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class CrumpledLetterScreen extends Screen {

    public CrumpledLetterScreen() {
        super(Component.literal("МЯТОЕ ПИСЬМО"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Darkened atmospheric backdrop
        graphics.fill(0, 0, width, height, 0xAA0A0D14);

        int docW = Math.min(360, width - 20);
        int docH = Math.min(220, height - 20);
        int dx = (width - docW) / 2;
        int dy = (height - docH) / 2;

        // Vintage folded corporate document styling
        graphics.fill(dx - 2, dy - 2, dx + docW + 2, dy + docH + 2, 0xFF3E3123);
        graphics.fill(dx, dy, dx + docW, dy + docH, 0xFFF5EBD7);

        // Header crease / corporate stamp
        graphics.fill(dx + 12, dy + 28, dx + docW - 12, dy + 29, 0xFFC8B79B);
        graphics.text(font, "§8[§4CONFIDENTIAL DISPATCH§8]", dx + 16, dy + 14, 0xFF3E3123, false);
        graphics.text(font, "§7REF #00-B", dx + docW - 75, dy + 14, 0xFF7A6B58, false);

        // Official letter body text
        int textY = dy + 38;
        String[] paragraphs = {
                "To the person reading this,",
                "",
                "You have been selected for a private financial evaluation.",
                "",
                "Your physical capabilities may have greater market value than you realize.",
                "",
                "A formal offer will be delivered shortly.",
                "",
                "Please keep this letter."
        };

        for (String p : paragraphs) {
            if (p.isEmpty()) {
                textY += 6;
                continue;
            }
            List<net.minecraft.network.chat.FormattedText> lines =
                    font.getSplitter().splitLines(Component.literal("§0" + p), docW - 32, net.minecraft.network.chat.Style.EMPTY);
            for (var line : lines) {
                graphics.text(font, line.getString(), dx + 16, textY, 0xFF1C1917, false);
                textY += 10;
            }
        }

        // Close button at bottom
        int btnW = 140;
        int btnH = 20;
        int btnX = dx + (docW - btnW) / 2;
        int btnY = dy + docH - 28;
        boolean hovered = (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH);

        graphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, hovered ? 0xFF1E293B : 0xFF334155);
        graphics.fill(btnX + 1, btnY + 1, btnX + btnW - 1, btnY + btnH - 1, hovered ? 0xFF0F172A : 0xFF1E293B);
        String closeStr = hovered ? "§fЗАКРЫТЬ [ESC]" : "§7ЗАКРЫТЬ [ESC]";
        int csw = font.width(closeStr);
        graphics.text(font, closeStr, btnX + (btnW - csw) / 2, btnY + 6, 0xFFFFFFFF, false);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        int mx = (int) event.x();
        int my = (int) event.y();

        int docW = Math.min(360, width - 20);
        int docH = Math.min(220, height - 20);
        int dx = (width - docW) / 2;
        int dy = (height - docH) / 2;
        int btnW = 140;
        int btnH = 20;
        int btnX = dx + (docW - btnW) / 2;
        int btnY = dy + docH - 28;

        if (mx >= btnX && mx <= btnX + btnW && my >= btnY && my <= btnY + btnH) {
            onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void onClose() {
        // Send reading confirmation to server
        ClientPlayNetworking.send(new MarketNetworking.MarketActionPayload("READ_CRUMPLED_LETTER", "", "", 0, 0.0));
        super.onClose();
    }
}
