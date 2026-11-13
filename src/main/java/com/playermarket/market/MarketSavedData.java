package com.playermarket.market;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.playermarket.PlayerMarketMod;
import com.playermarket.config.MarketConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import com.playermarket.story.SharedCampaignData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MarketSavedData extends SavedData {
    public record ChaosData(
            String activeChaosId,
            long activeChaosEndsTick,
            long nextChaosTick,
            boolean chaosEnabled
    ) {
        public static final Codec<ChaosData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.optionalFieldOf("activeChaosId", "NONE").forGetter(ChaosData::activeChaosId),
                Codec.LONG.optionalFieldOf("activeChaosEndsTick", 0L).forGetter(ChaosData::activeChaosEndsTick),
                Codec.LONG.optionalFieldOf("nextChaosTick", 240L).forGetter(ChaosData::nextChaosTick),
                Codec.BOOL.optionalFieldOf("chaosEnabled", true).forGetter(ChaosData::chaosEnabled)
        ).apply(instance, ChaosData::new));

        public ChaosData() {
            this("NONE", 0L, 240L, true);
        }
    }

    public static final Codec<MarketSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, PlayerMarketAccount.CODEC).fieldOf("accounts").forGetter(MarketSavedData::getSerializedAccounts),
            Codec.STRING.fieldOf("activeEventId").forGetter(d -> d.activeEvent.getId()),
            Codec.LONG.fieldOf("nextEventSwitchTick").forGetter(MarketSavedData::getNextEventSwitchTick),
            SharedCampaignData.CODEC.optionalFieldOf("campaign", new SharedCampaignData()).forGetter(MarketSavedData::getCampaignData),
            ChaosData.CODEC.optionalFieldOf("chaosData", new ChaosData()).forGetter(MarketSavedData::getChaosData)
    ).apply(instance, MarketSavedData::new));

    public static final SavedDataType<MarketSavedData> TYPE = new SavedDataType<>(
            PlayerMarketMod.id("market_accounts"),
            MarketSavedData::new,
            CODEC,
            DataFixTypes.LEVEL
    );

    private final Map<UUID, PlayerMarketAccount> accounts = new HashMap<>();
    private MarketEvent activeEvent = MarketEvent.STABLE_MARKET;
    private long nextEventSwitchTick = 2400L;
    private SharedCampaignData campaignData = new SharedCampaignData();
    private String activeChaosId = "NONE";
    private long activeChaosEndsTick = 0L;
    private long nextChaosTick = 240L;
    private boolean chaosEnabled = true;

    public MarketSavedData() {
    }

    private MarketSavedData(Map<String, PlayerMarketAccount> rawAccounts, String eventId, long nextEventSwitchTick, SharedCampaignData campaignData, ChaosData chaosData) {
        for (Map.Entry<String, PlayerMarketAccount> entry : rawAccounts.entrySet()) {
            try {
                accounts.put(UUID.fromString(entry.getKey()), entry.getValue());
            } catch (IllegalArgumentException ignored) {
            }
        }
        this.activeEvent = MarketEvent.byId(eventId).orElse(MarketEvent.STABLE_MARKET);
        this.nextEventSwitchTick = nextEventSwitchTick;
        this.campaignData = campaignData != null ? campaignData : new SharedCampaignData();
        ChaosData cd = chaosData != null ? chaosData : new ChaosData();
        this.activeChaosId = cd.activeChaosId();
        this.activeChaosEndsTick = cd.activeChaosEndsTick();
        this.nextChaosTick = cd.nextChaosTick();
        this.chaosEnabled = cd.chaosEnabled();
    }

    public ChaosData getChaosData() {
        return new ChaosData(activeChaosId, activeChaosEndsTick, nextChaosTick, chaosEnabled);
    }

    public String getActiveChaosId() {
        return activeChaosId != null ? activeChaosId : "NONE";
    }

    public void setActiveChaos(String id, long endsTick) {
        this.activeChaosId = id != null ? id : "NONE";
        this.activeChaosEndsTick = endsTick;
        setDirty();
    }

    public long getActiveChaosEndsTick() {
        return activeChaosEndsTick;
    }

    public long getNextChaosTick() {
        return nextChaosTick;
    }

    public void setNextChaosTick(long nextChaosTick) {
        this.nextChaosTick = nextChaosTick;
        setDirty();
    }

    public boolean isChaosEnabled() {
        return chaosEnabled;
    }

    public void setChaosEnabled(boolean chaosEnabled) {
        this.chaosEnabled = chaosEnabled;
        setDirty();
    }

    public SharedCampaignData getCampaignData() {
        return campaignData;
    }

    public void ensureAccountRegistered(UUID playerUuid, PlayerMarketAccount account) {
        campaignData.getRegisteredParticipants().add(playerUuid);
    }

    private Map<String, PlayerMarketAccount> getSerializedAccounts() {
        Map<String, PlayerMarketAccount> out = new HashMap<>();
        for (Map.Entry<UUID, PlayerMarketAccount> entry : accounts.entrySet()) {
            out.put(entry.getKey().toString(), entry.getValue());
        }
        return out;
    }

    public PlayerMarketAccount getOrCreateAccount(UUID playerUuid) {
        return accounts.computeIfAbsent(playerUuid, id -> {
            setDirty();
            return new PlayerMarketAccount();
        });
    }

    public Map<UUID, PlayerMarketAccount> getAccountsView() {
        return java.util.Collections.unmodifiableMap(accounts);
    }

    public void resetAllAccounts() {
        for (PlayerMarketAccount acc : accounts.values()) {
            acc.resetAll();
        }
        setDirty();
    }

    public MarketEvent getActiveEvent() {
        return activeEvent;
    }

    public void setActiveEvent(MarketEvent activeEvent, long currentTick) {
        this.activeEvent = activeEvent;
        long duration = Math.clamp(MarketConfig.get().marketEventDurationTicks, 2400L, 3600L);
        this.nextEventSwitchTick = currentTick + duration;
        setDirty();
    }

    public void setNextEventSwitchTick(long tick) {
        this.nextEventSwitchTick = tick;
        setDirty();
    }

    public long getNextEventSwitchTick() {
        return nextEventSwitchTick;
    }

    public static MarketSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(TYPE);
    }
}
