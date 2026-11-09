package com.playermarket.client;

import com.playermarket.market.MarketEvent;
import com.playermarket.market.PlayerMarketAccount;
import com.playermarket.network.MarketNetworking;
import net.minecraft.nbt.NbtOps;

public class ClientMarketState {
    private static PlayerMarketAccount account = new PlayerMarketAccount();
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
