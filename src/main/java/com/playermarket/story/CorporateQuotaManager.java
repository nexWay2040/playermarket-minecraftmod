package com.playermarket.story;

import com.playermarket.market.MarketSavedData;
import com.playermarket.market.PlayerMarketAccount;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public final class CorporateQuotaManager {

    private static final int CYCLE_DURATION_TICKS = 24000; // 1 Minecraft day (20 minutes)
    private static final int BASE_QUOTA = 1500;

    private static boolean auditPanicActive = false;
    private static long auditPanicEndsTick = 0L;
    private static final CorporateQuotaBarController QUOTA_BAR = new CorporateQuotaBarController();

    private CorporateQuotaManager() {}

    public static void resetRuntimeState() {
        auditPanicActive = false;
        auditPanicEndsTick = 0L;
        QUOTA_BAR.hideAndClear();
    }

    public static void onPlayerJoin(ServerPlayer player) {
        QUOTA_BAR.onPlayerJoin(player);
    }

    public static void onPlayerDisconnect(ServerPlayer player) {
        QUOTA_BAR.onPlayerDisconnect(player);
    }

    public static boolean isAuditPanicActive(long currentTick) {
        return auditPanicActive && currentTick < auditPanicEndsTick;
    }

    public static void contribute(MinecraftServer server, long amount) {
        if (server == null || amount <= 0) return;
        MarketSavedData data = MarketSavedData.get(server);
        SharedCampaignData campaign = data.getCampaignData();
        if (!campaign.isCampaignAccepted() || StoryManager.isProgramTerminated(campaign, null)) return;

        int current = campaign.getObjective("quota_collected");
        int target = getTargetQuota(server, campaign);

        if (current >= target) return;

        int updated = (int) Math.min((long) target, (long) current + amount);
        campaign.setObjective("quota_collected", updated);
        data.setDirty();

        // Check if just completed
        if (updated >= target) {
            onQuotaCompleted(server, data, campaign);
        }
    }

    public static int getTargetQuota(MinecraftServer server, SharedCampaignData campaign) {
        int level = campaign.getObjective("quota_cycle_level");
        int playerCount = server != null ? Math.max(1, server.getPlayerList().getPlayerCount()) : 1;
        return (BASE_QUOTA + level * 250) + (playerCount - 1) * 400;
    }

    public static int getCollectedCr(SharedCampaignData campaign) {
        return campaign.getObjective("quota_collected");
    }

    public static void tickServer(MinecraftServer server) {
        if (server == null) return;
        long gameTime = server.overworld().getGameTime();

        // Audit Panic cooldown check
        if (auditPanicActive && gameTime >= auditPanicEndsTick) {
            auditPanicActive = false;
        }

        // Only tick quota if campaign is active and running
        MarketSavedData data = MarketSavedData.get(server);
        SharedCampaignData campaign = data.getCampaignData();
        boolean terminated = StoryManager.isProgramTerminated(campaign, null);
        if (!campaign.isCampaignAccepted() || terminated) {
            QUOTA_BAR.tick(server, 0L, 0L, 0, false, 0, ChiefAuditorBossManager.isBattleActive(), true);
            return;
        }

        long deadline = campaign.getObjective("quota_deadline_tick");
        if (deadline <= 0) {
            // Init first cycle
            campaign.setObjective("quota_deadline_tick", (int) (gameTime + CYCLE_DURATION_TICKS));
            campaign.setObjective("quota_collected", 0);
            data.setDirty();
            return;
        }

        // Check cycle expiry
        if (gameTime >= deadline) {
            int collected = campaign.getObjective("quota_collected");
            int target = getTargetQuota(server, campaign);

            if (collected < target) {
                onQuotaFailed(server, data, campaign, gameTime);
            }

            // Start new cycle
            campaign.setObjective("quota_deadline_tick", (int) (gameTime + CYCLE_DURATION_TICKS));
            campaign.setObjective("quota_collected", 0);
            data.setDirty();
        }

        // Update live KPI BossBar on screen (auto-hides during Titan Boss fight or after campaign victory)
        int curCollected = campaign.getObjective("quota_collected");
        int curTarget = getTargetQuota(server, campaign);
        int remSec = (int) Math.max(0L, (campaign.getObjective("quota_deadline_tick") - gameTime) / 20L);
        int panicRemSec = auditPanicActive ? (int) Math.max(0L, (auditPanicEndsTick - gameTime) / 20L) : 0;
        QUOTA_BAR.tick(server, curCollected, curTarget, remSec, auditPanicActive, panicRemSec,
                ChiefAuditorBossManager.isBattleActive(), false);
    }

    private static void onQuotaCompleted(MinecraftServer server, MarketSavedData data, SharedCampaignData campaign) {
        int nextLevel = campaign.getObjective("quota_cycle_level") + 1;
        campaign.setObjective("quota_cycle_level", nextLevel);

        server.getPlayerList().broadcastSystemMessage(Component.literal(
                "\n§6§l========================================================\n"
                        + "§a§l  ✔ КОРПОРАТИВНАЯ КВОТА ВЫПОЛНЕНА! (Этап " + nextLevel + ")\n"
                        + "§f  Совет директоров удовлетворен вашей выработкой.\n"
                        + "§e  Бонус: +150 CR каждому и немедленный Бонусный Аирдроп!\n"
                        + "§6§l========================================================\n"), false);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerMarketAccount acc = data.getOrCreateAccount(player.getUUID());
            acc.addCr(150L);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.2f);
        }
        data.setDirty();

        // Reward: Immediately spawn a high-value supply crate
        CorporateChaosDirector.triggerDirective(server, CorporateChaosDirector.ChaosDirective.SUPPLY_OR_MIMIC);
    }

    private static void onQuotaFailed(MinecraftServer server, MarketSavedData data, SharedCampaignData campaign, long gameTime) {
        auditPanicActive = true;
        auditPanicEndsTick = gameTime + 3600L; // 3 minutes of audit panic

        server.getPlayerList().broadcastSystemMessage(Component.literal(
                "\n§4§l========================================================\n"
                        + "§c§l  ⚠ КОРПОРАТИВНАЯ КВОТА ПРОВАЛЕНА! ⚠\n"
                        + "§4  ВНИМАНИЕ: АУДИТ-ПАНИКА! СЛУЖБА БЕЗОПАСНОСТИ НАПРАВЛЯЕТ КОЛЛЕКТОРА!\n"
                        + "§e  Долги признаны просроченными. Приготовьтесь к бою!\n"
                        + "§4§l========================================================\n"), false);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 2.0f, 0.8f);
        }

        // Trigger Collector raid as consequence of failing the quota
        CorporateChaosDirector.triggerDirective(server, CorporateChaosDirector.ChaosDirective.COLLECTOR_RAID);
    }
}
