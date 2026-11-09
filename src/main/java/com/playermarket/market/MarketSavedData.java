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
    public static final Codec<MarketSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, PlayerMarketAccount.CODEC).fieldOf("accounts").forGetter(MarketSavedData::getSerializedAccounts),
            Codec.STRING.fieldOf("activeEventId").forGetter(d -> d.activeEvent.getId()),
            Codec.LONG.fieldOf("nextEventSwitchTick").forGetter(MarketSavedData::getNextEventSwitchTick),
            SharedCampaignData.CODEC.optionalFieldOf("campaign", new SharedCampaignData()).forGetter(MarketSavedData::getCampaignData)
    ).apply(instance, MarketSavedData::new));

    public static final SavedDataType<MarketSavedData> TYPE = new SavedDataType<>(
            PlayerMarketMod.id("market_accounts"),
            MarketSavedData::new,
            CODEC,
            DataFixTypes.LEVEL
    );

    private final Map<UUID, PlayerMarketAccount> accounts = new HashMap<>();
    private MarketEvent activeEvent = MarketEvent.STABLE_MARKET;
    private long nextEventSwitchTick = 12000L;
    private SharedCampaignData campaignData = new SharedCampaignData();

    public MarketSavedData() {
    }

    private MarketSavedData(Map<String, PlayerMarketAccount> rawAccounts, String eventId, long nextEventSwitchTick, SharedCampaignData campaignData) {
        for (Map.Entry<String, PlayerMarketAccount> entry : rawAccounts.entrySet()) {
            try {
                accounts.put(UUID.fromString(entry.getKey()), entry.getValue());
            } catch (IllegalArgumentException ignored) {
            }
        }
        this.activeEvent = MarketEvent.byId(eventId).orElse(MarketEvent.STABLE_MARKET);
        this.nextEventSwitchTick = nextEventSwitchTick;
        this.campaignData = campaignData != null ? campaignData : new SharedCampaignData();
    }

    public SharedCampaignData getCampaignData() {
        return campaignData;
    }

    public void ensureAccountRegistered(UUID playerUuid, PlayerMarketAccount account) {
        campaignData.getRegisteredParticipants().add(playerUuid);
        if (account.getStoryStage() >= 1 || account.getCrBalance() > 0 || !account.getHistory().isEmpty()) {
            campaignData.setCampaignAccepted(true);
        }
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

    public MarketEvent getActiveEvent() {
        return activeEvent;
    }

    public void setActiveEvent(MarketEvent activeEvent, long currentTick) {
        this.activeEvent = activeEvent;
        this.nextEventSwitchTick = currentTick + MarketConfig.get().marketEventDurationTicks;
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
