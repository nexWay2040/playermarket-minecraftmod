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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MarketHudOverlay {

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        if (client.gui.screen() instanceof MarketMainScreen) return;
        if (client.getDebugOverlay() != null && client.getDebugOverlay().showDebugScreen()) return;

        PlayerMarketAccount account = ClientMarketState.getAccount();
        SharedCampaignData campaign = ClientMarketState.getSharedCampaign();

        // Completely hide HUD during ending cinematic or when market access is permanently revoked
        if (account.isMarketCardBlocked()
                || account.isPermanentImpaired()
                || campaign.isEndingStarted()
                || campaign.isEndingApplied()
                || "FINAL_SETTLEMENT_ACTIVE".equals(campaign.getEndingState())
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
        lines.add(String.format(Locale.ROOT, "§aCR: §f§l%s §8| §b♦: §f§l%d", formatNumber(account.getCrBalance()), diamonds));

        if (event != MarketEvent.STABLE_MARKET) {
            lines.add("§6Событие: §e" + event.getHeadline());
        }

        // Active Story Quest tracker (compact single line)
        if (campaign.isCampaignAccepted() && campaign.getCurrentStage() <= 5) {
            List<StoryQuests.StageTask> tasks = StoryQuests.getTasksForStage(campaign.getCurrentStage());
            for (StoryQuests.StageTask t : tasks) {
                if (!campaign.isTaskCompleted(t.id())) {
                    lines.add(String.format(Locale.ROOT, "§6Миссия %d: §f%s", campaign.getCurrentStage(), cleanShortTitle(t.title())));
                    break;
                }
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
                    lines.add(String.format(Locale.ROOT, " §f• %s: §e%d/%d", shortTitle, c.currentProgress(), c.targetProgress()));
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
        for (String line : lines) {
            graphics.text(font, line, x + 6, lineY, 0xFFFFFFFF, true);
            lineY += 10;
        }
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
