package com.playermarket.client;

import com.playermarket.client.gui.CrumpledLetterScreen;
import com.playermarket.client.gui.MarketMainScreen;
import com.playermarket.market.MarketEvent;
import com.playermarket.market.PlayerMarketAccount;
import com.playermarket.network.MarketNetworking;
import com.playermarket.story.SharedCampaignData;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NbtOps;

import java.util.UUID;

public class ClientMarketState {
    private static PlayerMarketAccount account = new PlayerMarketAccount();
    private static SharedCampaignData sharedCampaign = new SharedCampaignData();
    private static MarketEvent activeEvent = MarketEvent.STABLE_MARKET;
    private static long currentGameTick = 0L;
    private static long nextEventSwitchTick = 0L;
    private static String oreScannerReport = "OFFLINE";
    private static String lastStatusMessage = "";
    private static long lastStatusTimestampMs = 0L;
    private static long localEndingStartMs = 0L;

    public static void applySync(MarketNetworking.MarketSyncPayload payload) {
        PlayerMarketAccount.CODEC.parse(NbtOps.INSTANCE, payload.accountTag())
                .result()
                .ifPresent(parsed -> account = parsed);
        if (payload.campaignTag() != null && !payload.campaignTag().isEmpty()) {
            SharedCampaignData.CODEC.parse(NbtOps.INSTANCE, payload.campaignTag())
                    .result()
                    .ifPresent(parsed -> sharedCampaign = parsed);
        }
        activeEvent = MarketEvent.byId(payload.activeEventId()).orElse(MarketEvent.STABLE_MARKET);
        currentGameTick = payload.currentGameTick();
        nextEventSwitchTick = payload.nextEventSwitchTick();
        oreScannerReport = payload.oreScannerReport() != null ? payload.oreScannerReport() : "OFFLINE";
        if (payload.statusMessage() != null && !payload.statusMessage().isEmpty()) {
            lastStatusMessage = payload.statusMessage();
            lastStatusTimestampMs = System.currentTimeMillis();
        }

        // Automatically close market screens on all co-op clients the instant final ending starts
        boolean endingActive = "FINAL_SETTLEMENT_ACTIVE".equals(sharedCampaign.getEndingState())
                || (sharedCampaign.isEndingStarted() && !sharedCampaign.isCinematicCompleted() && !sharedCampaign.isDisconnectSent());
        if (endingActive) {
            if (localEndingStartMs == 0L) {
                localEndingStartMs = System.currentTimeMillis();
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui != null && (mc.gui.screen() instanceof MarketMainScreen || mc.gui.screen() instanceof CrumpledLetterScreen)) {
                mc.gui.setScreen(null);
            }
        } else {
            localEndingStartMs = 0L;
        }
    }

    public static long getLocalEndingElapsedMs() {
        return localEndingStartMs > 0L ? Math.max(0L, System.currentTimeMillis() - localEndingStartMs) : 0L;
    }

    public static void reset() {
        account = new PlayerMarketAccount();
        sharedCampaign = new SharedCampaignData();
        activeEvent = MarketEvent.STABLE_MARKET;
        currentGameTick = 0L;
        nextEventSwitchTick = 0L;
        oreScannerReport = "OFFLINE";
        lastStatusMessage = "";
        lastStatusTimestampMs = 0L;
        localEndingStartMs = 0L;
    }

    public static PlayerMarketAccount getAccount() {
        return account;
    }

    public static SharedCampaignData getSharedCampaign() {
        return sharedCampaign;
    }

    public static boolean isPlayerImpaired(UUID uuid) {
        if (uuid == null) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && uuid.equals(mc.player.getUUID())) {
            if (account.isPermanentImpaired()) return true;
        }
        return sharedCampaign.isPlayerImpaired(uuid);
    }

    public static MarketEvent getActiveEvent() {
        return activeEvent;
    }

    public static long getCurrentGameTick() {
        return currentGameTick;
    }

    public static long getNextEventSwitchTick() {
        return nextEventSwitchTick;
    }

    public static String getOreScannerReport() {
        return oreScannerReport;
    }

    public static String getActiveStatusMessage() {
        if (System.currentTimeMillis() - lastStatusTimestampMs > 6500L) {
            return "";
        }
        return lastStatusMessage;
    }
}
