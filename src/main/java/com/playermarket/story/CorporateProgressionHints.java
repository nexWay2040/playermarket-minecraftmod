package com.playermarket.story;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Менеджер «Понятного маршрута игрока» (5 ключевых вех без спама).
 * <p>
 * Даёт игроку чёткий вектор с первой минуты игры:
 * <ol>
 *     <li>Продать первый орган</li>
 *     <li>Сделать первый спин в казино 50/50</li>
 *     <li>Вскрыть 3D-аирдроп / банкомат с жадностью</li>
 *     <li>Получить Повестку 17.4 или Крюк-Кошку Corp</li>
 *     <li>Победить Главного Аудитора 17.4</li>
 * </ol>
 */
public final class CorporateProgressionHints {

    private static final String INTRO_TAG = "pm_intro_briefing_v2";
    private static final String MILESTONE_TAG_PREFIX = "pm_milestone_";

    private static final Map<UUID, EnumSet<Milestone>> COMPLETED_CACHE = new HashMap<>();

    private static BiConsumer<ServerPlayer, Long> crRewardHandler = (player, amount) -> { };

    private CorporateProgressionHints() {
    }

    /**
     * Подключается один раз при старте сервера для автоматического начисления CR-наград за вехи.
     */
    public static void setCrRewardHandler(BiConsumer<ServerPlayer, Long> handler) {
        crRewardHandler = handler != null ? handler : (player, amount) -> { };
    }

    public static void clearServerCache() {
        COMPLETED_CACHE.clear();
    }

    /**
     * Вызывать при входе игрока на сервер: восстанавливает пройденные вехи из тегов
     * и один раз за всё время показывает короткий вводный маршрут из 3 строк.
     */
    public static void onPlayerJoin(ServerPlayer player) {
        if (player == null) {
            return;
        }
        EnumSet<Milestone> completed = syncFromPlayerTags(player);

        if (!player.entityTags().contains(INTRO_TAG) && completed.isEmpty()) {
            player.addTag(INTRO_TAG);
            player.sendSystemMessage(Component.literal("═══ [ASSET CORP // МАРШРУТ ВЫЖИВАНИЯ] ═══")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            player.sendSystemMessage(Component.literal(
                    "1) Продай орган в планшете -> 2) Рискуй CR в Казино 50/50 и жди Аирдроп -> 3) Возьми Крюк-Кошку и сокруши Аудитора 17.4!"
            ).withStyle(ChatFormatting.GRAY));
        }
    }

    /**
     * Вызывать в ServerPlayerEvents.COPY_FROM, чтобы теги вех не терялись после смерти.
     */
    public static void copyOnRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer) {
        if (oldPlayer == null || newPlayer == null) {
            return;
        }
        EnumSet<Milestone> completed = syncFromPlayerTags(oldPlayer);
        COMPLETED_CACHE.put(newPlayer.getUUID(), EnumSet.copyOf(completed));

        if (oldPlayer.entityTags().contains(INTRO_TAG)) {
            newPlayer.addTag(INTRO_TAG);
        }
        for (Milestone milestone : completed) {
            newPlayer.addTag(milestone.tagName);
        }
    }

    public static MilestoneResult onOrganSold(ServerPlayer player) {
        return completeMilestone(player, Milestone.FIRST_ORGAN_SOLD, null);
    }

    public static MilestoneResult onCasinoSpin(ServerPlayer player) {
        return completeMilestone(player, Milestone.FIRST_CASINO_SPIN, null);
    }

    public static MilestoneResult onGreedAirdropOpened(ServerPlayer player, double greedMultiplier) {
        String note = greedMultiplier >= 1.5
                ? "Выдержка окупилась (x" + String.format(java.util.Locale.ROOT, "%.1f", greedMultiplier) + ")!"
                : "Совет: в следующий раз продержи ящик 30–90с для x1.5–x3.0 CR!";
        return completeMilestone(player, Milestone.FIRST_GREED_AIRDROP, note);
    }

    public static MilestoneResult onBossGearAcquired(ServerPlayer player) {
        return completeMilestone(player, Milestone.BOSS_GEAR_READY, null);
    }

    /**
     * Автоматически проверяет, является ли полученный предмет Повесткой 17.4 или Крюком-Кошкой.
     */
    public static MilestoneResult onItemAcquired(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) {
            return MilestoneResult.NONE;
        }
        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String path = itemId.getPath();
        boolean isBossGear = "audit_summon".equals(path)
                || "corporate_grapple".equals(path)
                || path.contains("grapple")
                || path.contains("audit_summon");

        return isBossGear ? onBossGearAcquired(player) : MilestoneResult.NONE;
    }

    public static void onAuditorDefeated(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            onAuditorDefeated(player);
        }
    }

    public static MilestoneResult onAuditorDefeated(ServerPlayer player) {
        return completeMilestone(player, Milestone.AUDITOR_DEFEATED, null);
    }

    public static boolean isCompleted(ServerPlayer player, Milestone milestone) {
        if (player == null || milestone == null) {
            return false;
        }
        return syncFromPlayerTags(player).contains(milestone);
    }

    public static int getCompletedCount(ServerPlayer player) {
        return player == null ? 0 : syncFromPlayerTags(player).size();
    }

    /**
     * Отдаёт короткую понятную строку следующей цели для виджета DASHBOARD / STORY.
     */
    public static String getNextObjective(ServerPlayer player) {
        if (player == null) {
            return Milestone.FIRST_ORGAN_SOLD.nextHint;
        }
        EnumSet<Milestone> completed = syncFromPlayerTags(player);
        for (Milestone milestone : Milestone.values()) {
            if (!completed.contains(milestone)) {
                return milestone.hudObjective;
            }
        }
        return "Свобода достигнута: выкупай органы или фарми CR в своё удовольствие!";
    }

    public static MilestoneResult completeMilestone(ServerPlayer player, Milestone milestone, String extraNote) {
        if (player == null || milestone == null) {
            return MilestoneResult.NONE;
        }
        EnumSet<Milestone> completed = syncFromPlayerTags(player);
        if (completed.contains(milestone)) {
            return MilestoneResult.ALREADY_DONE;
        }

        completed.add(milestone);
        player.addTag(milestone.tagName);

        if (milestone.rewardCr > 0L) {
            crRewardHandler.accept(player, milestone.rewardCr);
        }

        sendGoldenToast(player, milestone, completed.size(), extraNote);
        return new MilestoneResult(true, milestone, milestone.rewardCr);
    }

    private static void sendGoldenToast(ServerPlayer player, Milestone milestone, int totalCompleted, String extraNote) {
        // Звуковой отклик без назойливой громкости
        player.level().playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                milestone == Milestone.AUDITOR_DEFEATED
                        ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE
                        : SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS,
                0.85F,
                milestone == Milestone.AUDITOR_DEFEATED ? 1.0F : 1.25F
        );

        String rewardSuffix = milestone.rewardCr > 0L ? " (+" + milestone.rewardCr + " CR)" : "";
        player.sendOverlayMessage(Component.literal(
                "★ ВЕХА [" + totalCompleted + "/5]: " + milestone.title + rewardSuffix + " ★"
        ).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        player.sendSystemMessage(Component.literal("★ [МАРШРУТ " + totalCompleted + "/5] ")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal(milestone.title + rewardSuffix)
                        .withStyle(ChatFormatting.YELLOW)));

        String hintLine = (extraNote != null && !extraNote.isBlank()) ? extraNote : milestone.nextHint;
        if (hintLine != null && !hintLine.isBlank()) {
            player.sendSystemMessage(Component.literal("   ↳ " + hintLine)
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private static EnumSet<Milestone> syncFromPlayerTags(ServerPlayer player) {
        EnumSet<Milestone> cached = COMPLETED_CACHE.computeIfAbsent(
                player.getUUID(),
                ignored -> EnumSet.noneOf(Milestone.class)
        );

        Set<String> tags = player.entityTags();
        for (Milestone milestone : Milestone.values()) {
            if (tags.contains(milestone.tagName)) {
                cached.add(milestone);
            }
        }
        return cached;
    }

    public enum Milestone {
        FIRST_ORGAN_SOLD(
                "first_organ",
                "Первая сделка с телом",
                150L,
                "Продай первый орган в планшете («Биржа Тела»)",
                "Дальше: испытай удачу в Казино 50/50 — там падают легендарные реликвии!"
        ),
        FIRST_CASINO_SPIN(
                "first_casino",
                "Ставка в Казино 50/50",
                200L,
                "Сделай первый спин в Казино 50/50",
                "Дальше: найди 3D-Аирдроп/Банкомат и удерживай его ради множителя x1.5–x3.0!"
        ),
        FIRST_GREED_AIRDROP(
                "first_airdrop",
                "Вскрытие Груза Жадности",
                350L,
                "Вскрой 3D-Аирдроп или Банкомат",
                "Дальше: добудь Крюк-Кошку и Повестку 17.4 — пригодится арена с рельефом/деревьями!"
        ),
        BOSS_GEAR_READY(
                "boss_gear",
                "Готовность к Протоколу 17.4",
                400L,
                "Добудь Крюк-Кошку Corp или Повестку 17.4",
                "Дальше: найди просторную арену с точками для крюка и примени Повестку 17.4!"
        ),
        AUDITOR_DEFEATED(
                "auditor_defeated",
                "Ликвидация Главного Аудитора 17.4",
                1000L,
                "Сокруши Главного Аудитора Corp 17.4",
                "Контракт разорван! Ты свободен — можешь выкупить остатки тела и забрать трофеи."
        );

        private final String id;
        private final String tagName;
        private final String title;
        private final long rewardCr;
        private final String hudObjective;
        private final String nextHint;

        Milestone(String id, String title, long rewardCr, String hudObjective, String nextHint) {
            this.id = id;
            this.tagName = MILESTONE_TAG_PREFIX + id;
            this.title = title;
            this.rewardCr = rewardCr;
            this.hudObjective = hudObjective;
            this.nextHint = nextHint;
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public long getRewardCr() {
            return rewardCr;
        }

        public String getHudObjective() {
            return hudObjective;
        }
    }

    public record MilestoneResult(boolean newlyCompleted, Milestone milestone, long rewardCr) {
        public static final MilestoneResult NONE = new MilestoneResult(false, null, 0L);
        public static final MilestoneResult ALREADY_DONE = new MilestoneResult(false, null, 0L);
    }
}