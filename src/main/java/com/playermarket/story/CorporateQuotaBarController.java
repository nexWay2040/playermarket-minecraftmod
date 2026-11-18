package com.playermarket.story;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Серверный KPI-бар Корпоративной Квоты на базе {@link ServerBossEvent}.
 * <p>
 * Поведение:
 * <ul>
 *     <li>Обычный режим: зелёный/жёлтый бар {@code "КВОТА CORP: 640 / 1500 CR | 14:20"}.</li>
 *     <li>Аудит-Паника: мигающий красный бар {@code "⚠ АУДИТ-ПАНИКА: КОЛЛЕКТОРЫ В ГОРОДЕ ⚠"}.</li>
 *     <li>Авто-скрытие при активном бое с Главным Аудитором или после финала кампании (BOSS_DEFEATED_FREEDOM).</li>
 * </ul>
 */
public final class CorporateQuotaBarController {

    private static final UUID QUOTA_BAR_UUID = UUID.nameUUIDFromBytes(
            "playermarket:corporate_quota_kpi_bar".getBytes(StandardCharsets.UTF_8)
    );

    private static final int LOW_TIME_WARNING_SECONDS = 300; // <= 5 мин до конца часа — жёлтый сигнал
    private static final int PANIC_TOTAL_SECONDS = 180;      // стандартная 3-мин Аудит-Паника
    private static final int UPDATE_INTERVAL_TICKS = 10;     // 0.5 сек (удобно и для таймера, и для мигания сирены)

    private final ServerBossEvent bossBar = new ServerBossEvent(
            QUOTA_BAR_UUID,
            Component.literal("КВОТА CORP: 0 / 1500 CR | 60:00"),
            BossEvent.BossBarColor.GREEN,
            BossEvent.BossBarOverlay.PROGRESS
    );

    private int tickCounter = 0;
    private boolean blinkPhase = false;
    private boolean lastVisible = false;
    private boolean lastPanicState = false;
    private long lastRenderedCurrentCr = Long.MIN_VALUE;
    private long lastRenderedTargetCr = Long.MIN_VALUE;
    private int lastRenderedRemainingSec = Integer.MIN_VALUE;

    public CorporateQuotaBarController() {
        bossBar.setVisible(false);
        bossBar.setDarkenScreen(false);
        bossBar.setPlayBossMusic(false);
        bossBar.setCreateWorldFog(false);
    }

    /**
     * Обновляет KPI-бар раз в серверный тик (внутри автоматически троттлится каждые 10 тиков,
     * но мгновенно реагирует на смену CR, паники или скрытие при появлении босса).
     */
    public void tick(
            MinecraftServer server,
            long currentCr,
            long targetCr,
            int remainingSeconds,
            boolean auditPanicActive,
            int panicRemainingSeconds,
            boolean bossFightActive,
            boolean campaignCompleted
    ) {
        if (server == null) {
            return;
        }

        tickCounter++;
        boolean shouldShow = !bossFightActive && !campaignCompleted && targetCr > 0L;

        if (!shouldShow) {
            if (lastVisible || !bossBar.getPlayers().isEmpty()) {
                bossBar.setVisible(false);
                if (campaignCompleted) {
                    bossBar.removeAllPlayers();
                }
                lastVisible = false;
            }
            return;
        }

        syncConnectedPlayers(server);

        if (!lastVisible) {
            bossBar.setVisible(true);
            lastVisible = true;
        }

        boolean urgentChange = (auditPanicActive != lastPanicState)
                || (currentCr != lastRenderedCurrentCr)
                || (targetCr != lastRenderedTargetCr)
                || (remainingSeconds != lastRenderedRemainingSec);

        if (!urgentChange && (tickCounter % UPDATE_INTERVAL_TICKS != 0)) {
            return;
        }

        if (tickCounter % UPDATE_INTERVAL_TICKS == 0) {
            blinkPhase = !blinkPhase;
        }

        if (auditPanicActive) {
            applyPanicState(panicRemainingSeconds);
        } else {
            applyNormalQuotaState(currentCr, targetCr, remainingSeconds);
        }

        lastPanicState = auditPanicActive;
        lastRenderedCurrentCr = currentCr;
        lastRenderedTargetCr = targetCr;
        lastRenderedRemainingSec = remainingSeconds;
    }

    public void onPlayerJoin(ServerPlayer player) {
        if (player != null && lastVisible) {
            bossBar.addPlayer(player);
        }
    }

    public void onPlayerDisconnect(ServerPlayer player) {
        if (player != null) {
            bossBar.removePlayer(player);
        }
    }

    public void hideAndClear() {
        bossBar.setVisible(false);
        bossBar.removeAllPlayers();
        lastVisible = false;
        lastRenderedCurrentCr = Long.MIN_VALUE;
        lastRenderedTargetCr = Long.MIN_VALUE;
        lastRenderedRemainingSec = Integer.MIN_VALUE;
    }

    private void applyNormalQuotaState(long currentCr, long targetCr, int remainingSeconds) {
        long safeCurrent = Math.max(0L, currentCr);
        int safeRemaining = Math.max(0, remainingSeconds);
        float progress = Mth.clamp((float) safeCurrent / (float) targetCr, 0.0F, 1.0F);

        boolean quotaMet = safeCurrent >= targetCr;
        boolean lowTime = !quotaMet && safeRemaining <= LOW_TIME_WARNING_SECONDS;
        boolean behindPace = !quotaMet && progress < 0.5F && safeRemaining <= 900;

        BossEvent.BossBarColor barColor = (quotaMet || (!lowTime && !behindPace))
                ? BossEvent.BossBarColor.GREEN
                : BossEvent.BossBarColor.YELLOW;

        ChatFormatting textColor = (barColor == BossEvent.BossBarColor.GREEN)
                ? ChatFormatting.GREEN
                : ChatFormatting.YELLOW;

        String title = "КВОТА CORP: " + safeCurrent + " / " + targetCr + " CR | " + formatMmSs(safeRemaining);
        if (quotaMet) {
            title = title + " ✓";
        }

        bossBar.setColor(barColor);
        bossBar.setOverlay(BossEvent.BossBarOverlay.PROGRESS);
        bossBar.setProgress(progress);
        bossBar.setName(Component.literal(title).withStyle(textColor, ChatFormatting.BOLD));
    }

    private void applyPanicState(int panicRemainingSeconds) {
        BossEvent.BossBarColor barColor = blinkPhase
                ? BossEvent.BossBarColor.RED
                : BossEvent.BossBarColor.WHITE;

        ChatFormatting textColor = blinkPhase
                ? ChatFormatting.RED
                : ChatFormatting.WHITE;

        String baseTitle = "⚠ АУДИТ-ПАНИКА: КОЛЛЕКТОРЫ В ГОРОДЕ ⚠";
        if (panicRemainingSeconds > 0) {
            baseTitle = baseTitle + " | " + formatMmSs(panicRemainingSeconds);
        }

        float progress = panicRemainingSeconds > 0
                ? Mth.clamp((float) panicRemainingSeconds / (float) PANIC_TOTAL_SECONDS, 0.0F, 1.0F)
                : 1.0F;

        bossBar.setColor(barColor);
        bossBar.setOverlay(BossEvent.BossBarOverlay.PROGRESS);
        bossBar.setProgress(progress);
        bossBar.setName(Component.literal(baseTitle).withStyle(textColor, ChatFormatting.BOLD));
    }

    private void syncConnectedPlayers(MinecraftServer server) {
        List<ServerPlayer> onlinePlayers = server.getPlayerList().getPlayers();
        Set<UUID> onlineIds = new HashSet<>(onlinePlayers.size());
        for (ServerPlayer player : onlinePlayers) {
            onlineIds.add(player.getUUID());
            if (!bossBar.getPlayers().contains(player)) {
                bossBar.addPlayer(player);
            }
        }
        // Копируем список во избежание ConcurrentModificationException при удалении
        List<ServerPlayer> tracked = List.copyOf(bossBar.getPlayers());
        for (ServerPlayer player : tracked) {
            if (!onlineIds.contains(player.getUUID())) {
                bossBar.removePlayer(player);
            }
        }
    }

    private static String formatMmSs(int totalSeconds) {
        int clamped = Math.max(0, totalSeconds);
        int minutes = clamped / 60;
        int seconds = clamped % 60;
        return String.format(java.util.Locale.ROOT, "%02d:%02d", minutes, seconds);
    }
}