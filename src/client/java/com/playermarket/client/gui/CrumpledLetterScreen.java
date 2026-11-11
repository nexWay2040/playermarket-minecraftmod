package com.playermarket.client.gui;

import com.playermarket.PlayerMarketMod;
import com.playermarket.network.MarketNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

import java.util.List;

public class CrumpledLetterScreen extends Screen {

    private static final Identifier PARCHMENT_TEXTURE = PlayerMarketMod.id("textures/gui/parchment_contract.png");

    private boolean clause17Open = false;
    private int clauseMinX = 0, clauseMinY = 0, clauseMaxX = 0, clauseMaxY = 0;

    public CrumpledLetterScreen() {
        super(Component.literal("МЯТОЕ ПИСЬМО // ДОГОВОР"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Darkened atmospheric backdrop
        graphics.fill(0, 0, width, height, 0xCC05070B);

        int docW = Math.min(width - 16, 396);
        int docH = Math.min(height - 16, 276);
        int dx = (width - docW) / 2;
        int dy = (height - docH) / 2;

        // Render high-resolution procedural aged parchment background texture with wax seal & border
        graphics.blit(RenderPipelines.GUI_TEXTURED, PARCHMENT_TEXTURE, dx, dy, 0.0f, 0.0f, docW, docH, 400, 280, 400, 280);

        // Header Title & Stamp
        graphics.text(font, "§4§lКОРПОРАТИВНЫЙ РЕЕСТР §8// §0ASSET PROGRAM", dx + 20, dy + 18, 0xFF24150D, false);
        graphics.text(font, "§8REF #00-B", dx + docW - 85, dy + 18, 0xFF634D35, false);

        // Official Contract Body Text (Russian)
        int textY = dy + 44;
        int textMaxW = docW - 80; // Leave room on right for the wax seal

        String[] contractLines = {
                "§0§lУважаемый кандидат!",
                "",
                "§8Совет Директоров предлагает вам принять участие в совместной Программе Активов (The Asset Program).",
                "",
                "§4§lУСЛОВИЯ СОГЛАШЕНИЯ:",
                "§0• Монетизация биологических показателей (здоровье, мобильность) в валюту CR.",
                "§0• Кооперативное участие: общий прогресс сюжета для всех операторов сектора.",
                "§0• При подписании выдаётся Карта Доступа к бирже [M] и стартовый бонус +100 CR.",
                "§0• Все сделки до наступления финального расчёта являются добровольными.",
                "",
                "§8Подписывая настоящий документ, вы соглашаетесь на внесение в корпоративный реестр."
        };

        for (String line : contractLines) {
            if (line.isEmpty()) {
                textY += 4;
                continue;
            }
            List<net.minecraft.network.chat.FormattedText> wrapped =
                    font.getSplitter().splitLines(Component.literal(line), textMaxW, net.minecraft.network.chat.Style.EMPTY);
            for (var wl : wrapped) {
                graphics.text(font, wl.getString(), dx + 20, textY, 0xFF1C1309, false);
                textY += 10;
            }
        }

        // Hidden tiny legal boilerplate tucked subtly above the action buttons
        int btnY = dy + docH - 42;
        int fineY = Math.min(btnY - 22, textY + 4);
        String b1 = "Рег. форма #00-B/АУД: сторона подтверждает обработку сессионной телеметрии, курсов CR/♦, архивное хранение";
        String b2 = "учётных логов и принятие сопутствующих положений регламента, вкл. ";
        String link17 = "п. 17.4 (протокол отчуждения)";
        String b3 = " без оговорок.";
        float fScale = 0.60f;
        int line1W = Math.round(font.width(b1) * fScale);
        int b2W = Math.round(font.width(b2) * fScale);
        int l17W = Math.round(font.width(link17) * fScale);
        clauseMinX = dx + 20 + b2W - 1;
        clauseMinY = fineY + 6;
        clauseMaxX = clauseMinX + l17W + 2;
        clauseMaxY = clauseMinY + 8;
        boolean clauseHover = !clause17Open && (mouseX >= clauseMinX && mouseX <= clauseMaxX && mouseY >= clauseMinY && mouseY <= clauseMaxY);

        graphics.pose().pushMatrix();
        graphics.pose().translate(dx + 20.0f, (float) fineY);
        graphics.pose().scale(fScale, fScale);
        graphics.text(font, b1, 0, 0, 0x77735B40, false);
        graphics.text(font, b2, 0, 11, 0x77735B40, false);
        graphics.text(font, link17, font.width(b2), 11, clauseHover ? 0xFF7F1D1D : 0x77735B40, false);
        if (clauseHover) {
            graphics.fill(font.width(b2), 20, font.width(b2) + font.width(link17), 21, 0xFF7F1D1D);
        }
        graphics.text(font, b3, font.width(b2) + font.width(link17), 11, 0x77735B40, false);
        graphics.pose().popMatrix();

        // Two prominent interactive buttons at the bottom of the parchment
        int signBtnW = 180;
        int signBtnH = 24;
        int signBtnX = dx + 22;

        int declineBtnW = 110;
        int declineBtnH = 24;
        int declineBtnX = dx + docW - declineBtnW - 22;

        boolean signHovered = !clause17Open && (mouseX >= signBtnX && mouseX <= signBtnX + signBtnW && mouseY >= btnY && mouseY <= btnY + signBtnH);
        boolean declineHovered = !clause17Open && (mouseX >= declineBtnX && mouseX <= declineBtnX + declineBtnW && mouseY >= btnY && mouseY <= btnY + declineBtnH);

        // Sign Button (Dark Forest / Emerald styling with gold border)
        graphics.fill(signBtnX, btnY, signBtnX + signBtnW, btnY + signBtnH, signHovered ? 0xFF14532D : 0xFF166534);
        graphics.fill(signBtnX + 1, btnY + 1, signBtnX + signBtnW - 1, btnY + signBtnH - 1, signHovered ? 0xFF052E16 : 0xFF14532D);
        String signText = signHovered ? "§a§l✔ ПОДПИСАТЬ ДОГОВОР" : "§f§l✔ ПОДПИСАТЬ ДОГОВОР";
        int stw = font.width(signText);
        graphics.text(font, signText, signBtnX + (signBtnW - stw) / 2, btnY + 7, 0xFFFFFFFF, false);

        // Decline Button (Muted Dark Slate)
        graphics.fill(declineBtnX, btnY, declineBtnX + declineBtnW, btnY + declineBtnH, declineHovered ? 0xFF334155 : 0xFF475569);
        graphics.fill(declineBtnX + 1, btnY + 1, declineBtnX + declineBtnW - 1, btnY + declineBtnH - 1, declineHovered ? 0xFF0F172A : 0xFF1E293B);
        String decText = declineHovered ? "§c✖ ОТКЛОНИТЬ" : "§7✖ ОТКЛОНИТЬ";
        int dtw = font.width(decText);
        graphics.text(font, decText, declineBtnX + (declineBtnW - dtw) / 2, btnY + 7, 0xFFFFFFFF, false);

        if (clause17Open) {
            renderClause17Overlay(graphics, mouseX, mouseY);
        }
    }

    private void renderClause17Overlay(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.fill(0, 0, width, height, 0xAA04060A);
        int mw = Math.min(340, width - 24);
        int mh = Math.min(188, height - 24);
        int mx = (width - mw) / 2;
        int my = (height - mh) / 2;

        graphics.fill(mx, my, mx + mw, my + mh, 0xFF0B0F18);
        graphics.fill(mx, my, mx + mw, my + 2, 0xFFEF4444);
        graphics.fill(mx, my + mh - 1, mx + mw, my + mh, 0xFF1E293B);
        graphics.fill(mx, my, mx + 1, my + mh, 0xFF1E293B);
        graphics.fill(mx + mw - 1, my, mx + mw, my + mh, 0xFF1E293B);

        graphics.text(font, "§c§lПРИЛОЖЕНИЕ // ПУНКТ 17.4", mx + 12, my + 10, 0xFFEF4444, false);
        int cy = my + 26;
        String[] notes = {
                "§fПосле завершения 6-го этапа и подписания финального расчёта:",
                "§7• На счёт начисляется +100 000 CR и счёт немедленно закрывается.",
                "§c• Все биологические активы переходят в собственность корпорации.",
                "§c• Карта Доступа [M] и терминал блокируются навсегда.",
                "§e• На правую ногу устанавливается несъёмный ортез (снижение",
                "§e  скорости и прыжка, постоянный визуальный фиксатор на скине).",
                "§4• Происходит принудительное отключение от сервера без отката."
        };
        for (String line : notes) {
            for (var seq : font.split(Component.literal(line), mw - 24)) {
                if (cy + 9 <= my + mh - 32) {
                    graphics.text(font, seq, mx + 12, cy, 0xFFE2E8F0, false);
                    cy += 10;
                }
            }
            cy += 2;
        }

        int cw = 110, ch = 18;
        int cx = mx + (mw - cw) / 2;
        int cyBtn = my + mh - 26;
        boolean chov = mouseX >= cx && mouseX <= cx + cw && mouseY >= cyBtn && mouseY <= cyBtn + ch;
        graphics.fill(cx, cyBtn, cx + cw, cyBtn + ch, chov ? 0xFF334155 : 0xFF1E293B);
        String cTxt = "Закрыть";
        graphics.text(font, cTxt, cx + (cw - font.width(cTxt)) / 2, cyBtn + 5, 0xFFFFFFFF, false);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        int mx = (int) event.x();
        int my = (int) event.y();

        if (clause17Open) {
            clause17Open = false;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            return true;
        }

        if (mx >= clauseMinX && mx <= clauseMaxX && my >= clauseMinY && my <= clauseMaxY) {
            clause17Open = true;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.1f));
            return true;
        }

        int docW = Math.min(width - 16, 396);
        int docH = Math.min(height - 16, 276);
        int dx = (width - docW) / 2;
        int dy = (height - docH) / 2;

        int btnY = dy + docH - 42;
        int signBtnW = 180;
        int signBtnH = 24;
        int signBtnX = dx + 22;

        int declineBtnW = 110;
        int declineBtnH = 24;
        int declineBtnX = dx + docW - declineBtnW - 22;

        // Click on [✔ ПОДПИСАТЬ ДОГОВОР]
        if (mx >= signBtnX && mx <= signBtnX + signBtnW && my >= btnY && my <= btnY + signBtnH) {
            ClientPlayNetworking.send(new MarketNetworking.MarketActionPayload("ACCEPT_SHARED_CONTRACT", "", "", 0, 0.0));
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f));
            super.onClose();
            return true;
        }

        // Click on [✖ ОТКЛОНИТЬ]
        if (mx >= declineBtnX && mx <= declineBtnX + declineBtnW && my >= btnY && my <= btnY + declineBtnH) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            super.onClose();
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void onClose() {
        // Just close cleanly without forcing contract acceptance
        super.onClose();
    }
}
