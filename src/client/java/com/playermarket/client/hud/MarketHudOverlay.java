package com.playermarket.client.hud;

import com.playermarket.PlayerMarketMod;
import com.playermarket.client.ClientMarketState;
import com.playermarket.client.gui.MarketMainScreen;
import com.playermarket.market.*;
import com.playermarket.story.SharedCampaignData;
import com.playermarket.story.StoryQuests;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MarketHudOverlay {

    private static final Identifier ICON_CR = PlayerMarketMod.id("textures/gui/icons/currency_cr.png");
    private static final Identifier ICON_DIAMOND = PlayerMarketMod.id("textures/gui/icons/currency_diamond.png");

    public static boolean isEndingCinematicPlaying() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null || mc.getConnection() == null) {
            return false;
        }
        if (mc.gui != null) {
            var screen = mc.gui.screen();
            if (screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen
                    || screen instanceof net.minecraft.client.gui.screens.GenericWaitingScreen
                    || screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
                return false;
            }
        }
        SharedCampaignData campaign = ClientMarketState.getSharedCampaign();
        boolean active = "FINAL_SETTLEMENT_ACTIVE".equals(campaign.getEndingState())
                || (campaign.isEndingStarted() && !campaign.isDisconnectSent() && !"SETTLED".equals(campaign.getEndingState()));
        if (!active) return false;
        long elapsed = ClientMarketState.getLocalEndingElapsedMs();
        return elapsed < 20000L;
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        if (client.getDebugOverlay() != null && client.getDebugOverlay().showDebugScreen()) return;

        PlayerMarketAccount account = ClientMarketState.getAccount();
        SharedCampaignData campaign = ClientMarketState.getSharedCampaign();

        // Fullscreen responsive ending cinematic renders even while screen closes / card is blocked
        if (isEndingCinematicPlaying()) {
            renderEndingCinematic(graphics, client, campaign);
            return;
        }

        if (client.gui.screen() instanceof MarketMainScreen) return;

        // Completely hide HUD after ending settlement or when market access is permanently revoked
        if (account.isMarketCardBlocked()
                || account.isPermanentImpaired()
                || campaign.isEndingStarted()
                || campaign.isEndingApplied()
                || "SETTLED".equals(campaign.getEndingState())) {
            return;
        }

        long gameTick = ClientMarketState.getCurrentGameTick();
        int diamonds = MarketAttributeApplier.countVanillaDiamonds(client.player.getInventory());
        MarketEvent event = ClientMarketState.getActiveEvent();

        Font font = client.font;
        int x = 6;
        int y = 6;

        // Support both SDL3 scancodes (KEY_LALT=226, KEY_RALT=230) and legacy GLFW keycodes (342, 346)
        boolean showAll = com.mojang.blaze3d.platform.InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_LALT)
                || com.mojang.blaze3d.platform.InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_RALT)
                || com.mojang.blaze3d.platform.InputConstants.isKeyDown(226)
                || com.mojang.blaze3d.platform.InputConstants.isKeyDown(230)
                || com.mojang.blaze3d.platform.InputConstants.isKeyDown(342)
                || com.mojang.blaze3d.platform.InputConstants.isKeyDown(346);

        List<String> lines = new ArrayList<>();
        lines.add("§a§lPLAYER MARKET §8// §7[M]");
        String crDisplay = formatNumber(account.getCrBalance());
        String currencyPrefix = "   §f§l" + crDisplay + " §aCR §8| ";
        lines.add(currencyPrefix + "   §b§l" + diamonds);

        if (event != MarketEvent.STABLE_MARKET) {
            lines.add("§6Событие: §e" + event.getHeadline());
        }

        String scannerReport = ClientMarketState.getOreScannerReport();
        if (account.getUpgradeLevel(UpgradeType.ORE_SCANNER) > 0 && scannerReport != null && !scannerReport.equals("OFFLINE")) {
            lines.add("§bСонар: §7" + scannerReport);
        }

        // Active Story Quest tracker (compact single line)
        if (campaign.isCampaignAccepted() && campaign.getCurrentStage() <= 5) {
            List<StoryQuests.StageTask> tasks = StoryQuests.getTasksForStage(campaign.getCurrentStage());
            int doneInStage = 0;
            StoryQuests.StageTask nextTask = null;
            for (StoryQuests.StageTask t : tasks) {
                if (campaign.isTaskCompleted(t.id())) {
                    doneInStage++;
                } else if (nextTask == null) {
                    nextTask = t;
                }
            }
            if (nextTask != null) {
                lines.add(String.format(Locale.ROOT, "§6Миссия %d (%d/%d): §f%s",
                        campaign.getCurrentStage(), doneInStage, tasks.size(), cleanShortTitle(nextTask.title())));
            }
        }

        // Group Active Leases by Asset to avoid cluttering HUD
        List<LeaseContract> leases = account.getActiveLeases();
        if (!leases.isEmpty()) {
            lines.add("§d§lАКТИВНЫЙ ЛИЗИНГ (ВКЛАДЫ):");
            java.util.Map<String, LeasedGroup> grouped = new java.util.LinkedHashMap<>();
            for (LeaseContract lease : leases) {
                double daysLeft = Math.max(0.0, (lease.endTick() - gameTick) / 24000.0);
                grouped.compute(lease.assetId(), (k, v) -> {
                    if (v == null) return new LeasedGroup(lease.fraction(), daysLeft);
                    return new LeasedGroup(v.fraction + lease.fraction(), Math.max(v.daysLeft, daysLeft));
                });
            }

            int count = 0;
            for (java.util.Map.Entry<String, LeasedGroup> entry : grouped.entrySet()) {
                if (!showAll && count >= 3) {
                    lines.add(String.format(Locale.ROOT, " §8+ ещё %d позиций... §e[ALT: раскрыть]", grouped.size() - count));
                    break;
                }
                String shortName = AssetType.byId(entry.getKey()).map(AssetType::getShortName).orElse(entry.getKey());
                LeasedGroup g = entry.getValue();
                lines.add(String.format(Locale.ROOT, " §7• §f%s: §c-%.0f%% §8(§d%.1f дн.§8)", shortName, g.fraction * 100.0, g.daysLeft));
                count++;
            }
        }

        // Active Contracts (Up to 3 active unless ALT is held)
        List<ContractEntry> contracts = account.getContracts();
        List<ContractEntry> activeContracts = new ArrayList<>();
        for (ContractEntry c : contracts) {
            if (c.active() && !c.claimed()) {
                activeContracts.add(c);
            }
        }

        if (!activeContracts.isEmpty()) {
            lines.add("§e§lПРИНЯТЫЕ КОНТРАКТЫ:");
            int activeContractCount = 0;
            for (ContractEntry c : activeContracts) {
                if (!showAll && activeContractCount >= 3) {
                    lines.add(String.format(Locale.ROOT, " §8+ ещё %d контрактов... §e[ALT: раскрыть]", activeContracts.size() - activeContractCount));
                    break;
                }
                String shortTitle = cleanShortTitle(c.title());
                if (c.completed()) {
                    lines.add(" §a✔ " + shortTitle + " §2[Готов]");
                } else {
                    double daysLeft = (c.deadlineTick() > gameTick)
                            ? Math.max(0.0, (c.deadlineTick() - gameTick) / 24000.0)
                            : (double) c.durationDays();
                    lines.add(String.format(Locale.ROOT, " §f• %s: §e%s", shortTitle, ContractBoardCatalog.formatProgress(c)));
                    lines.add(String.format(Locale.ROOT, "   §eОсталось: %.1f дн. §8| §cШтраф: -%d CR", daysLeft, c.penaltyCr()));
                }
                activeContractCount++;
            }
        }

        if (showAll && (leases.size() > 3 || activeContracts.size() > 3)) {
            lines.add("§8[ALT: Реестр развёрнут]");
        }

        int maxLineW = 190;
        for (String line : lines) {
            maxLineW = Math.max(maxLineW, font.width(line) + 14);
        }
        int width = maxLineW;
        int height = 12 + lines.size() * 10;

        // Render Background
        try {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PlayerMarketMod.id("hud_bg"), x, y, width, height);
        } catch (Throwable t) {
            graphics.fill(x, y, x + width, y + height, 0xCC090C12);
            graphics.fill(x, y, x + width, y + 1, 0xFF10B981);
            graphics.fill(x, y + height - 1, x + width, y + height, 0xFF1E293B);
            graphics.fill(x, y, x + 1, y + height, 0xFF10B981);
            graphics.fill(x + width - 1, y, x + width, y + height, 0xFF1E293B);
        }

        // Render Lines
        int lineY = y + 5;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            graphics.text(font, line, x + 6, lineY, 0xFFFFFFFF, true);
            if (i == 1) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, ICON_CR, x + 5, lineY - 1, 0.0f, 0.0f, 9, 9, 16, 16, 16, 16);
                int diaX = x + 6 + font.width(currencyPrefix);
                graphics.blit(RenderPipelines.GUI_TEXTURED, ICON_DIAMOND, diaX + 1, lineY - 1, 0.0f, 0.0f, 9, 9, 16, 16, 16, 16);
            }
            lineY += 10;
        }
    }

    private static void renderEndingCinematic(GuiGraphicsExtractor graphics, Minecraft client, SharedCampaignData campaign) {
        Font font = client.font;
        int sw = client.getWindow().getGuiScaledWidth();
        int sh = client.getWindow().getGuiScaledHeight();
        long elapsedMs = ClientMarketState.getLocalEndingElapsedMs();
        float progress = Math.min(1.0f, elapsedMs / 8000.0f);

        // Smooth dark vignette intensifying as liquidation proceeds
        int bgAlpha = (int) (0x48 + progress * 0x70);
        int bgTint = (bgAlpha << 24) | (elapsedMs >= 4800L ? 0x160408 : 0x05080F);
        graphics.fill(0, 0, sw, sh, bgTint);

        // Responsive top & bottom cinematic letterbox bars (slide in smoothly over first 500ms)
        float slide = Math.min(1.0f, elapsedMs / 500.0f);
        int targetBarH = Math.max(24, Math.min(48, sh / 7));
        int barH = Math.max(4, Math.round(targetBarH * slide));
        int accent = elapsedMs >= 3200L ? 0xFFEF4444 : 0xFF10B981;
        graphics.fill(0, 0, sw, barH, 0xF205070B);
        graphics.fill(0, barH - 1, sw, barH, accent);
        graphics.fill(0, sh - barH, sw, sh, 0xF205070B);
        graphics.fill(0, sh - barH, sw, sh - barH + 1, accent);

        // Timeline: 0..8000ms (5 phases) -> 8000..9200ms (blackout) -> 9200..11150ms (1.95s screamer) -> 11150ms+ (cut black until disconnect)
        String tag;
        String title;
        if (elapsedMs >= 8000L) {
            // Pure pitch-black blackout before & immediately after the 1.95s screamer
            graphics.fill(0, 0, sw, sh, 0xFF000000);

            if (elapsedMs < 9200L) {
                com.playermarket.client.PlayerMarketClient.resetEndingScreamer();
                if (elapsedMs < 8320L && ((elapsedMs / 60L) % 2L) == 0L) {
                    String msg = "SIGNAL TERMINATED // DISCONNECTING...";
                    graphics.text(font, msg, (sw - font.width(msg)) / 2, sh / 2 - 4, 0xFF7F1D1D, false);
                }
                return;
            }

            // Cut immediately back to black void once the 1.95s screamer sound completes (never linger silently)
            if (elapsedMs >= 11150L) {
                return;
            }

            // SUDDEN FULLSCREEN SCREAMER WITH SOUND (exact 1950ms window synced to screamer.ogg)
            com.playermarket.client.PlayerMarketClient.triggerEndingScreamerOnce();
            long scareMs = elapsedMs - 9200L;
            boolean whiteStrobe = scareMs < 85L || (scareMs > 760L && scareMs < 820L);
            boolean redStrobe = (scareMs >= 85L && scareMs < 170L) || ((scareMs / 55L) % 8L == 0L);

            if (redStrobe) {
                graphics.fill(0, 0, sw, sh, 0x3AD00000);
            }

            boolean useAlt = ((scareMs / 60L) % 7L == 0L) || (scareMs > 880L && scareMs < 1060L);
            net.minecraft.resources.Identifier scareTex = PlayerMarketMod.id(
                    useAlt ? "textures/gui/screamer_alt.png" : "textures/gui/screamer.png");

            float lunge = Math.min(1.0f, scareMs / 145.0f);
            int size = Math.round(Math.max(sw * 0.68f, sh * 1.12f) * (0.74f + 0.34f * lunge));
            long rng = (System.nanoTime() >>> 14) ^ (scareMs * 2654435761L);
            int maxJ = scareMs < 550L ? 15 : 8;
            int jx = (int) (Math.floorMod(rng, maxJ * 2L + 1L) - maxJ);
            int jy = (int) (Math.floorMod(rng >> 8, maxJ * 2L + 1L) - maxJ);
            int drawX = (sw - size) / 2 + jx;
            int drawY = (sh - size) / 2 + jy;
            int tint = whiteStrobe ? 0xFFFFFFFF : (redStrobe ? 0xFFFF7A7A : 0xFFECECEC);

            graphics.blit(RenderPipelines.GUI_TEXTURED, scareTex, drawX, drawY,
                    0.0f, 0.0f, size, size, 512, 512, 512, 512, tint);

            // CRT scanline flicker & torn red horror bands
            int lineStart = (int) ((scareMs / 24L) % 4L);
            for (int sy = lineStart; sy < sh; sy += 4) {
                graphics.fill(0, sy, sw, sy + 1, 0x3E000000);
            }
            if (((scareMs / 48L) % 3L) == 0L) {
                int tearY = (int) Math.floorMod(rng >> 16, Math.max(1, sh - 12));
                graphics.fill(0, tearY, sw, tearY + 3, 0x66DC2626);
            }

            if (whiteStrobe) {
                graphics.fill(0, 0, sw, sh, 0x45FFFFFF);
            }

            if (((scareMs / 75L) % 4L) != 1L) {
                String scareCaption = "ВЫ БОЛЬШЕ НЕ КЛИЕНТ.";
                int capCol = ((scareMs / 50L) % 2L) == 0L ? 0xFFDC2626 : 0xFFFCA5A5;
                graphics.text(font, scareCaption, (sw - font.width(scareCaption)) / 2 + (jx / 2), sh - 24 + (jy / 3), capCol, true);
            }
            return;
        }

        com.playermarket.client.PlayerMarketClient.resetEndingScreamer();

        String subtitle;
        int titleColor;
        int step;
        if (elapsedMs < 1600L) {
            step = 1;
            tag = "[ CONTRACT EXECUTED // +1,000,000 CR ]";
            title = Component.translatableWithFallback("ending.playermarket.phase1.title", "FINAL SETTLEMENT").getString();
            subtitle = Component.translatableWithFallback("ending.playermarket.phase1.sub",
                    "Расчёт одобрен. Средства зачислены на корпоративный счёт.").getString();
            titleColor = 0xFFFBBF24;
        } else if (elapsedMs < 3200L) {
            step = 2;
            tag = "[ BIOMETRIC COLLATERAL RECOVERY ]";
            title = Component.translatableWithFallback("ending.playermarket.phase2.title", "ЛИКВИДАЦИЯ АКТИВОВ").getString();
            subtitle = Component.translatableWithFallback("ending.playermarket.phase2.sub",
                    "Сбор биологического залога согласно условиям сделки...").getString();
            titleColor = 0xFFF59E0B;
        } else if (elapsedMs < 4800L) {
            step = 3;
            tag = "[ CORPORATE REGISTRY OVERRIDE ]";
            title = Component.translatableWithFallback("ending.playermarket.phase3.title", "САНКЦИИ АКТИВИРОВАНЫ").getString();
            subtitle = Component.translatableWithFallback("ending.playermarket.phase3.sub",
                    "Аннулирование доступа к терминалу и конфискация портфеля...").getString();
            titleColor = 0xFFEF4444;
        } else if (elapsedMs < 6400L) {
            step = 4;
            tag = "[ SECTION 17.4 ENFORCEMENT ]";
            title = Component.translatableWithFallback("ending.playermarket.phase4.title", "СТАТЬЯ 17.4").getString();
            subtitle = Component.translatableWithFallback("ending.playermarket.phase4.sub",
                    "Установка постоянного ограничителя. Возврат невозможен.").getString();
            titleColor = 0xFFEF4444;
        } else {
            step = 5;
            tag = "[ SUBJECT STATUS: EXPIRED ]";
            title = Component.translatableWithFallback("ending.playermarket.phase5.title", "ДОСТУП АННУЛИРОВАН").getString();
            subtitle = Component.translatableWithFallback("ending.playermarket.phase5.sub",
                    "Права собственности переданы корпорации. Вы больше не клиент.").getString();
            titleColor = 0xFFDC2626;
        }

        int centerY = sh / 2;

        // Center card panel for crystal readability over any world lighting
        int cardW = Math.min(Math.max(240, sw - 48), 420);
        int wrapW = Math.max(120, cardW - 28);
        List<FormattedCharSequence> subLines = font.split(Component.literal(subtitle), wrapW);
        int cardH = 74 + subLines.size() * 11;
        int cardX = (sw - cardW) / 2;
        int cardY = centerY - cardH / 2;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PlayerMarketMod.id("hud_bg"), cardX, cardY, cardW, cardH);

        // Phase tag header inside top of card
        int tagW = font.width(tag);
        if (tagW <= cardW - 16) {
            graphics.text(font, tag, (sw - tagW) / 2, cardY + 10, accent, true);
        } else {
            graphics.text(font, font.plainSubstrByWidth(tag, cardW - 20), cardX + 10, cardY + 10, accent, true);
        }

        // Scaled responsive title — automatically steps down scale so it NEVER overflows screen width
        int rawTitleW = Math.max(1, font.width(title));
        float maxFitW = Math.max(100.0f, cardW - 24.0f);
        float titleScale = rawTitleW * 2.0f <= maxFitW ? 2.0f : (rawTitleW * 1.5f <= maxFitW ? 1.5f : Math.min(1.0f, maxFitW / rawTitleW));
        graphics.pose().pushMatrix();
        graphics.pose().translate(sw / 2.0f, cardY + 32.0f);
        graphics.pose().scale(titleScale, titleScale);
        graphics.text(font, title, -rawTitleW / 2, -4, titleColor, true);
        graphics.pose().popMatrix();

        // Wrapped multi-line subtitle guaranteed within card bounds
        int subY = cardY + 46;
        for (FormattedCharSequence seq : subLines) {
            graphics.text(font, seq, (sw - font.width(seq)) / 2, subY, 0xFFE2E8F0, true);
            subY += 11;
        }

        // Animated transfer progress bar
        float phaseProgress = Math.min(1.0f, Math.max(0.0f, elapsedMs / 8000.0f));
        int barW = Math.min(240, cardW - 32);
        int barX = (sw - barW) / 2;
        int progY = cardY + cardH - 18;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PlayerMarketMod.id("bar_bg"), barX, progY, barW, 6);
        int fillW = Math.max(4, Math.round(barW * phaseProgress));
        String fillSprite = elapsedMs < 3200L ? "bar_green" : "bar_red";
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PlayerMarketMod.id(fillSprite), barX, progY, fillW, 6);

        // Telemetry counter inside bottom letterbox bar
        String footerLeft = String.format(Locale.ROOT, "PROTOCOL // STEP %d/5", step);
        String footerRight = Math.min(100, Math.round(phaseProgress * 100)) + "%";
        int footerY = sh - (barH / 2) - 4;
        graphics.text(font, footerLeft, 12, footerY, 0xFF94A3B8, false);
        graphics.text(font, footerRight, sw - font.width(footerRight) - 12, footerY, accent, false);
    }

    private static String cleanShortTitle(String title) {
        if (title == null) return "";
        if (title.contains(":")) {
            return title.substring(0, title.indexOf(':')).trim();
        }
        if (title.contains("//")) {
            return title.substring(0, title.indexOf("//")).trim();
        }
        return title.trim();
    }

    private static String formatNumber(long num) {
        if (num >= 1_000_000L) {
            return String.format(Locale.ROOT, "%.1fM", num / 1_000_000.0);
        } else if (num >= 10_000L) {
            return String.format(Locale.ROOT, "%.1fK", num / 1_000.0);
        } else {
            return String.format(Locale.ROOT, "%,d", num);
        }
    }

    private static class LeasedGroup {
        double fraction;
        double daysLeft;

        LeasedGroup(double fraction, double daysLeft) {
            this.fraction = fraction;
            this.daysLeft = daysLeft;
        }
    }
}
