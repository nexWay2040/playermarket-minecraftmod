package com.playermarket.client;

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
