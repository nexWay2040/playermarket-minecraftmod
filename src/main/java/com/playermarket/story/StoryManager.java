package com.playermarket.story;

import com.playermarket.config.MarketConfig;
import com.playermarket.market.*;
import com.playermarket.network.MarketNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.*;

public class StoryManager {

    private static final String SENDER_LEDGER = "Mr. Ledger // Account Manager";
    private static final String SENDER_LEGAL = "Департамент Юстиции // Asset Program";
    private static final String SENDER_MEDICAL = "Медицинская Служба // BioMech";

    public static void initializeAccountStory(PlayerMarketAccount account, long gameTick) {
        StoryProgress story = account.getStoryProgress();
        if (story.getMailbox().isEmpty()) {
            story.addMail(new MailMessage(
                    "welcome_mail",
                    gameTick,
                    SENDER_LEDGER,
                    "ДОБРО ПОЖАЛОВАТЬ В ПРОГРАММУ АКТИВОВ",
                    "Уважаемый клиент!\n\n"
                            + "От лица руководства корпорации приветствую вас в Программе Активов (The Asset Program).\n"
                            + "Мы предлагаем вам уникальный шанс: монетизировать свои биологические показатели и конвертировать здоровье, ловкость и выносливость в ликвидный капитал CR.\n\n"
                            + "На первом этапе для вашей же безопасности действует испытательный регламент: открыта категория «ТЕЛО» с лимитом разовой продажи до 25%.\n"
                            + "Изучите задания во вкладке «ПРОГРАММА» на терминале. Ваше будущее выглядит крайне прибыльным.\n\n"
                            + "С уважением,\nMr. Ledger, ваш персональный аккаунт-менеджер.",
                    false,
                    "CR",
                    100L,
                    false
            ));

            if (FabricLoader.getInstance().isModLoaded("upgrader") || FabricLoader.getInstance().isModLoaded("upgrader_items")) {
                story.addMail(new MailMessage(
                        "upgrader_synergy_mail",
                        gameTick,
                        SENDER_LEDGER,
                        "СИНЕРГИЯ: КОМПЛЕКС UPGRADER ITEMS",
                        "Уважаемый клиент!\n\n"
                                + "Наши сканеры зафиксировали наличие производственного комплекса Upgrader Items в вашем секторе.\n"
                                + "Корпорация официально одобряет конвертацию низкосортных материалов в алмазы через механизмы апгрейдера.\n"
                                + "Стандартные алмазы Minecraft принимаются в нашем Магазине Улучшений без ограничений по происхождению.\n\n"
                                + "Желаем приятной добычи углерода.\nMr. Ledger.",
                        false,
                        "NONE",
                        0L,
                        true
                ));
            }
        }
    }

    public static void checkAllQuests(ServerPlayer player, PlayerMarketAccount account) {
        if (!MarketConfig.get().enableStoryProgram) return;

        StoryProgress story = account.getStoryProgress();
        long gameTick = player.level().getServer() != null ? player.level().getServer().overworld().getGameTime() : 0L;
        int diamondsInInventory = MarketAttributeApplier.countVanillaDiamonds(player.getInventory());

        // 1. Check Stage Tasks
        int stage = story.getCurrentStage();
        boolean anyTaskCompleted = false;

        switch (stage) {
            case 1 -> {
                // S1 Tasks
                if (account.getTotalCrEarned() >= 500L) {
                    if (story.completeTask("s1_first_payment")) anyTaskCompleted = true;
                }
                if (hasAnySoldStats(account)) {
                    if (story.completeTask("s1_first_sale")) anyTaskCompleted = true;
                }
            }
            case 2 -> {
                // S2 Tasks
                if (account.getTotalDealsCount() >= 3) {
                    if (story.completeTask("s2_sell_more")) anyTaskCompleted = true;
                }
                if (diamondsInInventory >= 1) {
                    if (story.completeTask("s2_diamonds")) anyTaskCompleted = true;
                }
                if (account.getTotalDiamondsSpent() >= 1) {
                    if (story.completeTask("s2_first_invest")) anyTaskCompleted = true;
                }
                if (countCategoriesWithSoldStats(account) >= 2) {
                    if (story.completeTask("s2_portfolio_exp")) anyTaskCompleted = true;
                }
            }
            case 3 -> {
                // S3 Tasks
                if (hasAnyBuyback(account)) {
                    if (story.completeTask("s3_asset_recovery")) anyTaskCompleted = true;
                }
                if (!account.getActiveLeases().isEmpty()) {
                    if (story.completeTask("s3_lease_contract")) anyTaskCompleted = true;
                }
                if (gameTick >= 24000L || account.getTotalCrEarned() >= 1500L) {
                    if (story.completeTask("s3_survival_check")) anyTaskCompleted = true;
                }
                if (hasUpgradeLevelAtLeast(account, 2)) {
                    if (story.completeTask("s3_upgrade_comp")) anyTaskCompleted = true;
                }
            }
            case 4 -> {
                // S4 Tasks
                if (account.getTotalDealsCount() >= 10) {
                    if (story.completeTask("s4_premium_status")) anyTaskCompleted = true;
                }
                if (account.getTotalDiamondsSpent() >= 8) {
                    if (story.completeTask("s4_investor")) anyTaskCompleted = true;
                }
                if (diamondsInInventory >= 10) {
                    if (story.completeTask("s4_diamond_reserve")) anyTaskCompleted = true;
                }
                if (countStatsSoldAboveFraction(account, 0.25) >= 3) {
                    if (story.completeTask("s4_asset_specialist")) anyTaskCompleted = true;
                }
                if (countClaimedContracts(account) >= 3) {
                    if (story.completeTask("s4_contract_completion")) anyTaskCompleted = true;
                }
            }
            case 5 -> {
                // S5 Tasks
                if (countTotalBuybacks(account) >= 2) {
                    if (story.completeTask("s5_recovery_plan")) anyTaskCompleted = true;
                }
            }
        }

        // Check if all 5 stage tasks are completed -> Advance Stage
        List<StoryQuests.StageTask> stageTasks = StoryQuests.getTasksForStage(stage);
        if (!stageTasks.isEmpty()) {
            boolean allDone = true;
            for (StoryQuests.StageTask task : stageTasks) {
                if (!story.isTaskCompleted(task.id())) {
                    allDone = false;
                    break;
                }
            }

            if (allDone && stage < 6) {
                advanceStage(player, account, stage + 1, gameTick);
            }
        }

        // 2. Check Side Quests
        checkSideQuests(player, account, diamondsInInventory);

        // 3. Check Unexpected Corporate Situations
        checkUnexpectedSituations(player, account, gameTick);

        if (anyTaskCompleted && MarketConfig.get().enableStoryToasts) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
        }
    }

    private static void checkSideQuests(ServerPlayer player, PlayerMarketAccount account, int diamondsInInventory) {
        if (!MarketConfig.get().enableSideQuests) return;
        StoryProgress story = account.getStoryProgress();

        if (account.getTotalCrEarned() >= 10000L) story.completeSideQuest("sq_poor_decisions");
        if (diamondsInInventory >= 32) story.completeSideQuest("sq_diamond_fever");
        if (countTotalBuybacks(account) >= 3) story.completeSideQuest("sq_refund_please");
        if (countClaimedContracts(account) >= 10) story.completeSideQuest("sq_corporate_slave");
        if (countCategoriesWithSoldStats(account) >= 4) story.completeSideQuest("sq_asset_collector");
        if (account.getCrBalance() >= 100000L) story.completeSideQuest("sq_millionaire");
        if (isFullyRecovered(account)) story.completeSideQuest("sq_full_recovery");
        if (hasUpgradesInAllCategories(account)) story.completeSideQuest("sq_investor_all");
        if (countStatsSoldAboveFraction(account, 0.50) >= 3) story.completeSideQuest("sq_bad_investment");
        if (account.getTotalDiamondsSpent() >= 12) story.completeSideQuest("sq_diamond_hunt");
        if (account.isSecondChanceArmed()) story.completeSideQuest("sq_second_chance");
    }

    private static void checkUnexpectedSituations(ServerPlayer player, PlayerMarketAccount account, long gameTick) {
        StoryProgress story = account.getStoryProgress();
        if (gameTick - story.getLastEventTick() < 12000L) return; // Cooldown between dynamic letters

        // 1. CONGRATULATIONS, YOU ARE POOR
        if (!story.hasTriggeredEvent("CONGRATS_POOR") && account.getTotalCrSpent() >= 2000L && account.getCrBalance() < 50L) {
            story.recordTriggeredEvent("CONGRATS_POOR");
            story.setLastEventTick(gameTick);
            story.addMail(new MailMessage(
                    "poor_congrats_" + gameTick,
                    gameTick,
                    SENDER_LEDGER,
                    "ПОЗДРАВЛЯЕМ! ВЫ НА МЕЛИ",
                    "Уважаемый клиент!\n\n"
                            + "Наши финансовые сенсоры зафиксировали, что ваш текущий баланс приблизился к эмоционально тяжёлой цифре (менее 50 CR).\n"
                            + "Мы искренне восхищаемся вашей готовностью тратить всё до последнего кредита.\n"
                            + "В качестве знака корпоративной поддержки высылаем вам скромную гуманитарную субсидию.\n\n"
                            + "С наилучшими пожеланиями,\nMr. Ledger.",
                    false,
                    "CR",
                    150L,
                    false
            ));
            notifyNewMail(player, "Поздравляем! Вы на мели");
            return;
        }

        // 2. 30% LESS HUMAN
        double totalMutilation = computeTotalMutilationFraction(account);
        if (!story.hasTriggeredEvent("LESS_HUMAN_30") && totalMutilation >= 0.30) {
            story.recordTriggeredEvent("LESS_HUMAN_30");
            story.setLastEventTick(gameTick);
            story.addMail(new MailMessage(
                    "less_human_" + gameTick,
                    gameTick,
                    SENDER_MEDICAL,
                    "МЕДИЦИНСКАЯ СПРАВКА: ВЫ НА 30% МЕНЬШЕ ЧЕЛОВЕК",
                    "Уважаемый клиент!\n\n"
                            + "Плановая биометрическая телеметрия подтверждает: от вашего изначального биологического состава осталось менее 70%.\n"
                            + "Оставшиеся органические узлы функционируют в пределах допустимой нормы.\n"
                            + "Поздравляем с постепенным переходом на прогрессивную синтетическую основу!\n\n"
                            + "Медицинский отдел BioMech Corp.",
                    false,
                    "CR",
                    300L,
                    false
            ));
            notifyNewMail(player, "Медицинская справка: Вы на 30% меньше человек");
            return;
        }

        // 3. BODY INSPECTION (Stage 3+)
        if (story.getCurrentStage() >= 3 && !story.hasTriggeredEvent("BODY_INSPECTION") && gameTick > 36000L) {
            story.recordTriggeredEvent("BODY_INSPECTION");
            story.setLastEventTick(gameTick);

            double hpSold = account.getTotalSoldFraction(AssetType.MAX_HEALTH);
            double speedSold = account.getTotalSoldFraction(AssetType.MOVEMENT_SPEED);
            double dmgSold = account.getTotalSoldFraction(AssetType.ATTACK_DAMAGE);

            String hpStatus = hpSold > 0.4 ? "КРИТИЧЕСКИ ОБЕСЦЕНЕНО" : (hpSold > 0.1 ? "НИЖЕ ОЖИДАНИЙ" : "В ПРЕДЕЛАХ НОРМЫ");
            String speedStatus = speedSold > 0.4 ? "СНЯТО С ПРОИЗВОДСТВА" : (speedSold > 0.1 ? "ОГРАНИЧЕННАЯ ПОДВИЖНОСТЬ" : "АКТИВНО");
            String combatStatus = dmgSold > 0.3 ? "ОТДЕЛ СОКРАЩЁН" : "ОПЕРАТИВНО";

            story.addMail(new MailMessage(
                    "inspection_" + gameTick,
                    gameTick,
                    SENDER_LEDGER,
                    "РЕЗУЛЬТАТЫ РЕВИЗИИ ВАШЕГО ТЕЛА",
                    "ОФИЦИАЛЬНЫЙ СРЕЗ ТЕЛЕМЕТРИИ ПЕРСОНАЖА:\n\n"
                            + "• Запас здоровья: " + hpStatus + "\n"
                            + "• Мобильность и бег: " + speedStatus + "\n"
                            + "• Боевая эффективность: " + combatStatus + "\n\n"
                            + "Заключение комиссии: Финансовые решения превосходны. Физическое состояние внушает лёгкую тревогу.\n"
                            + "Рекомендуем продолжать инвестировать в алмазные приводы.\n\n"
                            + "Mr. Ledger.",
                    false,
                    "NONE",
                    0L,
                    true
            ));
            notifyNewMail(player, "Результаты ревизии вашего тела");
        }
    }

    public static void advanceStage(ServerPlayer player, PlayerMarketAccount account, int newStage, long gameTick) {
        StoryProgress story = account.getStoryProgress();
        story.setCurrentStage(newStage);

        switch (newStage) {
            case 2 -> {
                story.addMail(new MailMessage(
                        "stage2_congrats",
                        gameTick,
                        SENDER_LEDGER,
                        "ЭТАП 2: ВАШ АККАУНТ ДЕМОНСТРИРУЕТ РОСТ",
                        "Поздравляем с переходом на 2-й этап Программы Активов!\n\n"
                                + "Ваш баланс стремительно растёт. Ваше физическое тело, впрочем, начинает проявлять следы амортизации.\n"
                                + "Корпорация утвердила расширение лимитов: теперь вам открыта категория «ДВИЖЕНИЕ» и продажи до 50%.\n"
                                + "Напоминаем, что алмазы Minecraft являются универсальным платёжным средством в нашем магазине.\n\n"
                                + "Mr. Ledger.",
                        false,
                        "CR",
                        350L,
                        false
                ));
            }
            case 3 -> {
                story.addMail(new MailMessage(
                        "stage3_congrats",
                        gameTick,
                        SENDER_LEDGER,
                        "ЭТАП 3: О ПОБОЧНЫХ ЭФФЕКТАХ И ВЫКУПЕ",
                        "Уважаемый клиент!\n\n"
                                + "Мы заметили, что вы начали обращать внимание на ослабление характеристик и интересоваться выкупом.\n"
                                + "Напоминаем: владение собственным телом — это премиальная услуга, облагаемая сервисной комиссией (30%).\n"
                                + "Вам одобрен доступ к категории «БОЙ» и финансовым вкладам (лизингу).\n\n"
                                + "Mr. Ledger.",
                        false,
                        "CR",
                        500L,
                        false
                ));
            }
            case 4 -> {
                account.setBlackMarketUnlocked(true);
                story.addMail(new MailMessage(
                        "stage4_congrats",
                        gameTick,
                        SENDER_LEDGER,
                        "ЭТАП 4: СТАТУС PREMIUM И ДОСТУП В ТЁМНЫЙ СЕКТОР",
                        "Поздравляем! Вы больше не рядовой клиент.\n\n"
                                + "Совет директоров повысил ваш аккаунт до статуса PREMIUM!\n"
                                + "Вам предоставлен полный допуск к категории «ПРОФЕССИЯ», а также снята блокировка с ЧЁРНОГО РЫНКА (Dark Sector).\n"
                                + "Среди нелегальных разработок вы найдёте чип «Второй Шанс» и квантовую батарею.\n\n"
                                + "Mr. Ledger.",
                        false,
                        "DIAMOND",
                        1L,
                        false
                ));
            }
            case 5 -> {
                story.addMail(new MailMessage(
                        "stage5_congrats",
                        gameTick,
                        SENDER_LEDGER,
                        "ЭТАП 5: ВНЕПЛАНОВАЯ ПРОВЕРКА И ШТРАФЫ",
                        "Внимание! Юридический отдел инициировал внеплановый аудит вашего аккаунта.\n\n"
                                + "Предупреждаем: в силу вступил прогрессивный налог на богатство (5% от баланса CR за срыв дедлайнов).\n"
                                + "Следите за доской корпоративных заказов. Срыв контракта обойдётся дорого.\n\n"
                                + "Mr. Ledger.",
                        false,
                        "CR",
                        800L,
                        false
                ));
            }
            case 6 -> {
                story.addMail(new MailMessage(
                        "stage6_final_offer",
                        gameTick,
                        SENDER_LEDGER,
                        "ФИНАЛЬНОЕ ПРЕДЛОЖЕНИЕ: THE FINAL TRANSACTION",
                        "Уважаемый клиент!\n\n"
                                + "Мы подошли к кульминации нашего сотрудничества.\n"
                                + "Совет директоров подготовил Финальную Резолюцию (The Final Transaction).\n"
                                + "Вам доступны три взаимно исключающих пути:\n"
                                + "  A. ПРЕМИУМ-АКЦИОНЕР — пожизненный статус директора (+50% к дивидендам).\n"
                                + "  B. СУВЕРЕНИЗАЦИЯ — выкуп своих прав и разрыв контракта (+4 Max HP навсегда).\n"
                                + "  C. ТОТАЛЬНАЯ ЛИКВИДАЦИЯ — полная продажа оставшихся активов (+50 000 CR сразу).\n\n"
                                + "Сделайте выбор во вкладке «ПРОГРАММА» на терминале.\n\n"
                                + "Mr. Ledger.",
                        false,
                        "DIAMOND",
                        2L,
                        false
                ));
            }
        }

        player.sendSystemMessage(Component.literal("§8[§6MR. LEDGER§8] §6ПРОГРАММА АКТИВОВ: §fОткрыт §e"
                + StoryQuests.STAGES.get(newStage - 1).name() + "§f! Проверьте почту на терминале [M]."));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1.0f);
    }

    public static void onTerminalTabOpened(ServerPlayer player, PlayerMarketAccount account, String tabName) {
        StoryProgress story = account.getStoryProgress();
        boolean changed = false;

        if (story.completeTask("s1_register")) changed = true;

        if (tabName.equals("SELL_ASSETS")) {
            if (story.completeTask("s1_eval")) changed = true;
        } else if (tabName.equals("UPGRADE_STORE")) {
            if (story.completeTask("s1_welcome_pkg")) changed = true;
        } else if (tabName.equals("BUY_BACK")) {
            if (story.getCurrentStage() >= 5) {
                if (story.completeTask("s5_hidden_commission")) changed = true;
            }
        } else if (tabName.equals("PORTFOLIO")) {
            if (story.getCurrentStage() >= 3) {
                if (story.completeTask("s3_audit")) changed = true;
            }
            if (story.getCurrentStage() >= 5) {
                if (story.completeTask("s5_account_review")) changed = true;
            }
        }

        if (changed) {
            checkAllQuests(player, account);
        }
    }

    public static void onStatSold(ServerPlayer player, PlayerMarketAccount account, AssetType asset, double fraction, boolean isLease) {
        StoryProgress story = account.getStoryProgress();
        long gameTick = player.level().getServer() != null ? player.level().getServer().overworld().getGameTime() : 0L;

        story.completeTask("s1_first_sale");
        if (isLease && story.getCurrentStage() >= 3) {
            story.completeTask("s3_lease_contract");
        }

        // Contextual Speech from Mr. Ledger
        if (asset == AssetType.MAX_HEALTH) {
            player.sendSystemMessage(Component.literal("§8[§6Mr. Ledger§8] §7«Ваши будущие медицинские расходы теперь проблема кого-то другого.»"));
        } else if (asset == AssetType.MOVEMENT_SPEED) {
            player.sendSystemMessage(Component.literal("§8[§6Mr. Ledger§8] §7«Искренне надеемся, что вам понравится ваш новый опыт пеших прогулок.»"));
        } else if (asset == AssetType.ATTACK_DAMAGE) {
            player.sendSystemMessage(Component.literal("§8[§6Mr. Ledger§8] §7«Ваш отдел физического насилия подвергся корпоративной оптимизации.»"));
        } else if (isLease) {
            player.sendSystemMessage(Component.literal("§8[§6Mr. Ledger§8] §7«Превосходный выбор! Аренда актива оформлена как депозитный вклад под процент.»"));
        }

        checkAllQuests(player, account);
    }

    public static void onBuyback(ServerPlayer player, PlayerMarketAccount account, AssetType asset) {
        StoryProgress story = account.getStoryProgress();
        long buybacks = story.getTaskProgress().getOrDefault("buyback_count", 0L) + 1;
        story.getTaskProgress().put("buyback_count", buybacks);

        if (story.getCurrentStage() >= 3) story.completeTask("s3_asset_recovery");
        if (story.getCurrentStage() >= 5 && buybacks >= 2) story.completeTask("s5_recovery_plan");

        player.sendSystemMessage(Component.literal("§8[§6Mr. Ledger§8] §7«Ваши активы возвращаются домой. К сожалению, сервисная комиссия остаётся у нас.»"));

        checkAllQuests(player, account);
    }

    public static void onDiamondsSpent(ServerPlayer player, PlayerMarketAccount account, int count) {
        StoryProgress story = account.getStoryProgress();
        if (story.getCurrentStage() >= 2) story.completeTask("s2_first_invest");
        if (story.getCurrentStage() >= 4 && account.getTotalDiamondsSpent() >= 8) story.completeTask("s4_investor");

        player.sendSystemMessage(Component.literal("§8[§6Mr. Ledger§8] §7«Рады видеть, что вы реинвестируете чистый углерод в биологическую инфраструктуру.»"));

        checkAllQuests(player, account);
    }

    public static void onResourceSold(ServerPlayer player, PlayerMarketAccount account, long payoutCr) {
        StoryProgress story = account.getStoryProgress();
        long resEarned = story.getTaskProgress().getOrDefault("resource_cr_earned", 0L) + payoutCr;
        story.getTaskProgress().put("resource_cr_earned", resEarned);

        if (story.getCurrentStage() >= 2) story.completeTask("s2_resource_ex");
        if (resEarned >= 5000L) story.completeSideQuest("sq_resource_tycoon");

        checkAllQuests(player, account);
    }

    public static void onContractClaimed(ServerPlayer player, PlayerMarketAccount account, boolean duringCrisis) {
        StoryProgress story = account.getStoryProgress();
        long contractsClaimed = story.getTaskProgress().getOrDefault("contracts_claimed_count", 0L) + 1;
        story.getTaskProgress().put("contracts_claimed_count", contractsClaimed);

        if (story.getCurrentStage() >= 4 && contractsClaimed >= 3) story.completeTask("s4_contract_completion");
        if (story.getCurrentStage() >= 5 && duringCrisis) story.completeTask("s5_market_crisis");
        if (contractsClaimed >= 10) story.completeSideQuest("sq_corporate_slave");

        checkAllQuests(player, account);
    }

    public static void onDealMade(ServerPlayer player, PlayerMarketAccount account, MarketEvent event) {
        StoryProgress story = account.getStoryProgress();
        if (story.getCurrentStage() >= 5 && event != null && event != MarketEvent.STABLE_MARKET) {
            story.completeTask("s5_undervalued");
        }
        checkAllQuests(player, account);
    }

    public static boolean chooseFinalTransaction(ServerPlayer player, PlayerMarketAccount account, String choiceId) {
        StoryProgress story = account.getStoryProgress();
        if (story.getCurrentStage() < 6 || !story.getFinalChoice().equals("NONE")) return false;

        long gameTick = player.level().getServer() != null ? player.level().getServer().overworld().getGameTime() : 0L;

        switch (choiceId) {
            case "MAIN_ENDING", "TRANSFER_OF_OWNERSHIP" -> {
                EndingSequenceManager.startMainEnding(player);
                return true;
            }
            case "SECRET_ENDING", "REVOKE_CONSENT" -> {
                EndingSequenceManager.startSecretEnding(player);
                return true;
            }
            case "PREMIUM_SHAREHOLDER" -> {
                if (account.getCrBalance() < 25000L || account.getUpgradeLevel(UpgradeType.VIP_CLIENT) < 2) {
                    player.sendSystemMessage(Component.literal("§c[ОШИБКА] Требуется 25 000 CR и статус VIP-Клиент (Ур. 2) для вступления в Совет Директоров."));
                    return false;
                }
                story.setFinalChoice("PREMIUM_SHAREHOLDER");
                account.addTransaction(gameTick, "РЕЗОЛЮЦИЯ СОВЕТА", "Премиум-Акционер", "Вступление в совет директоров", 0L, 0);

                story.addMail(new MailMessage(
                        "shareholder_contract",
                        gameTick,
                        SENDER_LEDGER,
                        "ДОБРО ПОЖАЛОВАТЬ В СОВЕТ ДИРЕКТОРОВ!",
                        "Уважаемый коллега!\n\n"
                                + "Решением совета директоров вы утверждены в статусе Пожизненного Акционера Asset Program!\n"
                                + "Ваши дивиденды повышены на +50%, а комиссия на любые операции снижена вдвое.\n"
                                + "Вы доказали, что деньги решают всё.\n\n"
                                + "С почтением,\nMr. Ledger и Совет Директоров.",
                        false,
                        "CR",
                        5000L,
                        false
                ));
            }
            case "COMPLETE_RECOVERY" -> {
                double maxSold = 0.0;
                for (AssetType a : AssetType.values()) {
                    maxSold = Math.max(maxSold, account.getPermanentSoldFraction(a));
                }
                if (maxSold > 0.10) {
                    player.sendSystemMessage(Component.literal("§c[ОШИБКА] Для Суверенизации Личности необходимо выкупить все характеристики до доли ≤ 10%."));
                    return false;
                }
                story.setFinalChoice("COMPLETE_RECOVERY");
                account.addTransaction(gameTick, "РЕЗОЛЮЦИЯ СОВЕТА", "Суверенизация", "Полный разрыв корпоративного контракта", 0L, 0);

                // Apply Permanent +4 Max HP vigor boost!
                var maxHpInst = player.getAttribute(Attributes.MAX_HEALTH);
                if (maxHpInst != null) {
                    var vigId = com.playermarket.PlayerMarketMod.id("sovereign_vigor_bonus");
                    maxHpInst.removeModifier(vigId);
                    maxHpInst.addPermanentModifier(new AttributeModifier(vigId, 4.0, AttributeModifier.Operation.ADD_VALUE));
                }

                story.addMail(new MailMessage(
                        "sovereignty_contract",
                        gameTick,
                        SENDER_LEGAL,
                        "СВИДЕТЕЛЬСТВО О СУВЕРЕНИЗАЦИИ",
                        "ОФИЦИАЛЬНЫЙ АКТ ДЕПАРТАМЕНТА ЮСТИЦИИ:\n\n"
                                + "Настоящим подтверждается, что гражданин " + player.getName().getString() + " полностью погасил свои биологические обязательства.\n"
                                + "Контракт Программы Активов расторгнут. Вам присвоен пожизненный иммунитет к налогу на богатство и постоянная биологическая прибавка +4 HP.\n"
                                + "Вы свободны. (Хотя мы всё ещё готовы купить вашу почку, если передумаете).\n\n"
                                + "Департамент Юстиции.",
                        false,
                        "DIAMOND",
                        5L,
                        false
                ));
            }
            case "TOTAL_LIQUIDATION" -> {
                if (countStatsSoldAboveFraction(account, 0.70) < 5) {
                    player.sendSystemMessage(Component.literal("§c[ОШИБКА] Требуется продать не менее 5 характеристик на 70% и более для Тотальной Ликвидации."));
                    return false;
                }
                story.setFinalChoice("TOTAL_LIQUIDATION");
                long buyoutPayout = 50000L;
                account.addCr(buyoutPayout);
                account.addTransaction(gameTick, "РЕЗОЛЮЦИЯ СОВЕТА", "Тотальная Ликвидация", "Полная продажа органических прав", buyoutPayout, 0);

                story.addMail(new MailMessage(
                        "liquidation_contract",
                        gameTick,
                        SENDER_LEDGER,
                        "АКТ ПОЛНОЙ ЛИКВИДАЦИИ ПЕРСОНАЖА",
                        "ПОЗДРАВЛЯЕМ СИНТЕТИЧЕСКУЮ ЕДИНИЦУ #" + player.getUUID().toString().substring(0, 8) + "!\n\n"
                                + "Вы передали 100% биологических прав корпорации за рекордную выплату +50 000 CR.\n"
                                + "Вам предоставлен максимальный дисконт на Чёрном Рынке (-35%) и статус Синтетического Субъекта.\n"
                                + "Тело мертво — капитал вечен!\n\n"
                                + "Mr. Ledger.",
                        false,
                        "CR",
                        buyoutPayout,
                        true
                ));
            }
            default -> {
                return false;
            }
        }

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 0.9f);
        MarketAttributeApplier.syncPlayerModifiers(player);
        MarketNetworking.sendSyncToPlayer(player, "§6ФИНАЛЬНАЯ СДЕЛКА ЗАКЛЮЧЕНА!");
        return true;
    }

    public static boolean claimTaskReward(ServerPlayer player, PlayerMarketAccount account, int stage, String taskId, long gameTick) {
        StoryProgress story = account.getStoryProgress();
        for (StoryQuests.StageTask task : StoryQuests.getTasksForStage(stage)) {
            if (task.id().equals(taskId)) {
                if (story.claimTaskReward(taskId)) {
                    if (task.rewardCr() > 0) {
                        account.addCr(task.rewardCr());
                        account.addTransaction(gameTick, "НАГРАДА ЗА ЭТАП", task.title(), "Программа Активов (Этап " + stage + ")", task.rewardCr(), 0);
                    }
                    if (task.rewardDiamonds() > 0) {
                        ItemStack dia = new ItemStack(Items.DIAMOND, task.rewardDiamonds());
                        if (!player.getInventory().add(dia)) {
                            player.drop(dia, false, net.minecraft.util.Prediction.SERVER_ONLY);
                        }
                        player.getInventory().setChanged();
                    }
                    checkAllQuests(player, account);
                    return true;
                }
                break;
            }
        }
        return false;
    }

    public static boolean claimSideQuestReward(ServerPlayer player, PlayerMarketAccount account, String questId, long gameTick) {
        StoryProgress story = account.getStoryProgress();
        for (StoryQuests.SideQuest sq : StoryQuests.SIDE_QUESTS) {
            if (sq.id().equals(questId)) {
                if (story.claimSideQuestReward(questId)) {
                    if (sq.rewardCr() > 0) {
                        account.addCr(sq.rewardCr());
                        account.addTransaction(gameTick, "НАГРАДА ЗА ПОРУЧЕНИЕ", sq.title(), "Корпоративный контракт", sq.rewardCr(), 0);
                    }
                    if (sq.rewardDiamonds() > 0) {
                        ItemStack dia = new ItemStack(Items.DIAMOND, sq.rewardDiamonds());
                        if (!player.getInventory().add(dia)) {
                            player.drop(dia, false, net.minecraft.util.Prediction.SERVER_ONLY);
                        }
                        player.getInventory().setChanged();
                    }
                    checkAllQuests(player, account);
                    return true;
                }
                break;
            }
        }
        return false;
    }

    public static boolean claimMailReward(ServerPlayer player, PlayerMarketAccount account, String mailId, long gameTick) {
        StoryProgress story = account.getStoryProgress();
        MailMessage claimed = story.claimMailReward(mailId);
        if (claimed != null) {
            if ("CR".equalsIgnoreCase(claimed.rewardType()) && claimed.rewardAmount() > 0) {
                account.addCr(claimed.rewardAmount());
                account.addTransaction(gameTick, "ВЛОЖЕНИЕ ИЗ ПИСЬМА", claimed.subject(), "От: " + claimed.sender(), claimed.rewardAmount(), 0);
            } else if ("DIAMOND".equalsIgnoreCase(claimed.rewardType()) && claimed.rewardAmount() > 0) {
                ItemStack dia = new ItemStack(Items.DIAMOND, (int) claimed.rewardAmount());
                if (!player.getInventory().add(dia)) {
                    player.drop(dia, false, net.minecraft.util.Prediction.SERVER_ONLY);
                }
                player.getInventory().setChanged();
                account.addTransaction(gameTick, "ВЛОЖЕНИЕ ИЗ ПИСЬМА", claimed.subject(), "От: " + claimed.sender(), 0L, (int) claimed.rewardAmount());
            }
            checkAllQuests(player, account);
            return true;
        }
        return false;
    }

    public static void markMailRead(PlayerMarketAccount account, String mailId) {
        account.getStoryProgress().markMailRead(mailId);
    }

    private static void notifyNewMail(ServerPlayer player, String subject) {
        player.sendSystemMessage(Component.literal("§8[§6MR. LEDGER§8] §eВам пришло новое письмо: §f«" + subject + "»§7. Откройте терминал [M]."));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.7f, 1.2f);
    }

    // Helper checks
    private static boolean hasAnySoldStats(PlayerMarketAccount account) {
        for (AssetType a : AssetType.values()) {
            if (account.getTotalSoldFraction(a) > 0.001) return true;
        }
        return false;
    }

    private static int countCategoriesWithSoldStats(PlayerMarketAccount account) {
        Set<AssetType.Category> cats = new HashSet<>();
        for (AssetType a : AssetType.values()) {
            if (account.getTotalSoldFraction(a) > 0.001) cats.add(a.getCategory());
        }
        return cats.size();
    }

    private static boolean hasAnyBuyback(PlayerMarketAccount account) {
        for (TransactionRecord rec : account.getHistory()) {
            if (rec.category().contains("ВЫКУП") || rec.category().contains("BUYBACK")) return true;
        }
        return false;
    }

    private static int countTotalBuybacks(PlayerMarketAccount account) {
        int count = 0;
        for (TransactionRecord rec : account.getHistory()) {
            if (rec.category().contains("ВЫКУП") || rec.category().contains("BUYBACK")) count++;
        }
        return count;
    }

    private static boolean hasUpgradeLevelAtLeast(PlayerMarketAccount account, int minLvl) {
        for (UpgradeType u : UpgradeType.values()) {
            if (account.getUpgradeLevel(u) >= minLvl) return true;
        }
        return false;
    }

    private static int countStatsSoldAboveFraction(PlayerMarketAccount account, double minFraction) {
        int count = 0;
        for (AssetType a : AssetType.values()) {
            if (account.getTotalSoldFraction(a) >= minFraction - 0.001) count++;
        }
        return count;
    }

    private static int countClaimedContracts(PlayerMarketAccount account) {
        int count = 0;
        for (ContractEntry c : account.getContracts()) {
            if (c.claimed()) count++;
        }
        return count;
    }

    private static boolean isFullyRecovered(PlayerMarketAccount account) {
        if (countTotalBuybacks(account) < 3) return false;
        if (!account.getActiveLeases().isEmpty()) return false;
        for (AssetType a : AssetType.values()) {
            if (account.getTotalSoldFraction(a) > 0.001) return false;
        }
        return true;
    }

    private static boolean hasUpgradesInAllCategories(PlayerMarketAccount account) {
        Set<UpgradeType.Category> cats = new HashSet<>();
        for (UpgradeType u : UpgradeType.values()) {
            if (account.getUpgradeLevel(u) > 0) cats.add(u.getCategory());
        }
        return cats.size() >= 4;
    }

    private static double computeTotalMutilationFraction(PlayerMarketAccount account) {
        double sum = 0.0;
        for (AssetType a : AssetType.values()) {
            sum += account.getTotalSoldFraction(a);
        }
        return sum / AssetType.values().length;
    }
}
