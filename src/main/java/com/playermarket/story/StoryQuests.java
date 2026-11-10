package com.playermarket.story;

import java.util.List;

public class StoryQuests {

    public record StageTask(
            String id,
            int stage,
            String title,
            String description,
            long rewardCr,
            int rewardDiamonds
    ) {}

    public record StageInfo(
            int stageNumber,
            String name,
            String subtitle,
            String description,
            String corporateStanding,
            int themeColor
    ) {}

    public record SideQuest(
            String id,
            String title,
            String description,
            long rewardCr,
            int rewardDiamonds
    ) {}

    public record FinalChoiceOption(
            String id,
            String title,
            String subtitle,
            String requirementText,
            String rewardText,
            String corporatePhilosophy,
            int themeColor
    ) {}

    public static final List<StageInfo> STAGES = List.of(
            new StageInfo(
                    1,
                    "НОВЫЙ КЛИЕНТ",
                    "STAGE 1: NEW CUSTOMER",
                    "Первые шаги в Программе Активов. Исследуйте мир, разведайте пещеры и оцените условия контракта.",
                    "Испытательный клиент (Доступно: ТЕЛО до 25%)",
                    0xFF10B981
            ),
            new StageInfo(
                    2,
                    "АКТИВНЫЙ ИНВЕСТОР",
                    "STAGE 2: ACTIVE INVESTOR",
                    "Боевые испытания и добыча сырья. Корпорация требует доказать эффективность и опробовать аугментации.",
                    "Активный клиент (Доступно: ТЕЛО, ДВИЖЕНИЕ до 50%)",
                    0xFF38BDF8
            ),
            new StageInfo(
                    3,
                    "ПОБОЧНЫЕ ЭФФЕКТЫ",
                    "STAGE 3: SIDE EFFECTS",
                    "Глубинные недра и ночные вахты. Вы начинаете ощущать последствия сделок в реальном выживании.",
                    "Привилегированный клиент (Доступно: БОЙ, ЛИЗИНГ)",
                    0xFFF59E0B
            ),
            new StageInfo(
                    4,
                    "ПРЕМИУМ-КЛИЕНТ",
                    "STAGE 4: PREMIUM CLIENT",
                    "Высокорисковые операции и закрытый Чёрный Рынок. Совместные нормативы для операторов сектора.",
                    "VIP Инвестор (Доступен ЧЁРНЫЙ РЫНОК и ПРОФЕССИИ)",
                    0xFFC084FC
            ),
            new StageInfo(
                    5,
                    "ФИНАНСОВЫЙ АУДИТ",
                    "STAGE 5: FINAL AUDIT",
                    "Комплексная аттестация тела и ресурсов. Финальная проверка оператора перед итоговым расчётом.",
                    "Корпоративный партнёр (Ликвидация до 80-90%)",
                    0xFFEF4444
            ),
            new StageInfo(
                    6,
                    "ФИНАЛЬНЫЙ РАСЧЁТ",
                    "STAGE 6: FINAL SETTLEMENT",
                    "Решающая резолюция корпорации: окончательный расчет, выплата 100 000 CR и вступление в силу Статьи 17.4.",
                    "Финальная Резолюция Совета Директоров (Статья 17.4)",
                    0xFFFBBF24
            )
    );

    // ==========================================
    // 20 ACTIVE GAMEPLAY QUESTS (4 per Stage, paced for dynamic ~15-20 min runs)
    // ==========================================

    // Stage 1: Field Operations & Initial Liquidation
    public static final List<StageTask> STAGE_1_TASKS = List.of(
            new StageTask("s1_field_inspection", 1, "Полевая разведка", "Посетите не менее 2 различных биомов в игровом мире.", 200, 6),
            new StageTask("s1_underground_audit", 1, "Первые недра", "Лично добудьте 4 любых рудных блока (уголь, медь, железо и др.).", 250, 8),
            new StageTask("s1_first_sale", 1, "Первая ликвидация", "Продайте любую долю характеристики во вкладке «ПРОДАЖА».", 300, 10),
            new StageTask("s1_asset_stress", 1, "Стресс-тест тела", "Спуститесь в пещеру (ниже Y=56) или встретьте ночь на 5 сек после продажи стата.", 350, 10)
    );

    // Stage 2: Combat, Procurement & First Recovery
    public static final List<StageTask> STAGE_2_TASKS = List.of(
            new StageTask("s2_combat_insurance", 2, "Боевой тест", "Победите 3 враждебных мобов (зомби, скелеты, пауки и т.д.).", 400, 12),
            new StageTask("s2_diamond_procure", 2, "Первая аугментация", "Приобретите любое 1 улучшение за алмазы во вкладке «МАГАЗИН».", 450, 12),
            new StageTask("s2_recovery_op", 2, "Опыт выкупа", "Выкупите любую проданную характеристику обратно на бирже.", 500, 15),
            new StageTask("s2_emergency_delivery", 2, "Биржевой подряд", "Примите и выполните 1 любой контракт во вкладке «КОНТРАКТЫ».", 450, 12)
    );

    // Stage 3: Deep Subterranean & Leasing
    public static final List<StageTask> STAGE_3_TASKS = List.of(
            new StageTask("s3_deep_mining", 3, "Глубинная разведка", "Спуститесь ниже Y=24 и добудьте 3 рудных блока (или 8 руд всего).", 550, 15),
            new StageTask("s3_terrain_survey", 3, "Полевой маршрут", "Преодолейте дистанцию 250 блоков по миру.", 550, 15),
            new StageTask("s3_equipment_test", 3, "Зачистка сектора", "Доведите счётчик побед над враждебными мобами до 6.", 500, 15),
            new StageTask("s3_night_shift", 3, "Арендный депозит", "Оформите лизинг характеристики или проведите 15 сек под землёй/ночью.", 600, 18)
    );

    // Stage 4: High-Risk Operations & Black Market
    public static final List<StageTask> STAGE_4_TASKS = List.of(
            new StageTask("s4_corporate_salvage", 4, "Сбор образцов", "Добудьте суммарно не менее 10 рудных блоков в мире.", 650, 18),
            new StageTask("s4_high_risk_contract", 4, "Тёмный сектор", "Купите 1 чип на Чёрном Рынке, либо выполните контракт/бой в событии.", 700, 20),
            new StageTask("s4_personal_recovery", 4, "Биржевой оборот", "Совершите не менее 5 сделок на бирже или 2 выкупа характеристик.", 650, 20),
            new StageTask("s4_cooperative_test", 4, "Совместный норматив", "Добудьте 14 руд суммарно или победите 8 врагов в секторе.", 750, 22)
    );

    // Stage 5: Underwater Inspection, Mobility & Final Evaluation
    public static final List<StageTask> STAGE_5_TASKS = List.of(
            new StageTask("s5_underwater_audit", 5, "Водное погружение", "Проведите под водой не менее 5 секунд (река, озеро или океан).", 800, 25),
            new StageTask("s5_mobility_test", 5, "Тест мобильности", "Пройдите суммарно 400 блоков по миру в рамках программы.", 800, 25),
            new StageTask("s5_resource_recovery", 5, "Оборот капитала", "Заработайте всего от 3 000 CR или добудьте 16 рудных блоков.", 850, 25),
            new StageTask("s5_final_field_eval", 5, "Финальная аттестация", "Доведите победы до 10 мобов и вложите от 3 алмазов (или держите 2+ в инвентаре).", 1000, 30)
    );

    public static List<StageTask> getTasksForStage(int stage) {
        return switch (stage) {
            case 1 -> STAGE_1_TASKS;
            case 2 -> STAGE_2_TASKS;
            case 3 -> STAGE_3_TASKS;
            case 4 -> STAGE_4_TASKS;
            case 5 -> STAGE_5_TASKS;
            default -> List.of();
        };
    }

    // ==========================================
    // 12 ACTIVE GAMEPLAY SIDE QUESTS
    // ==========================================
    public static final List<SideQuest> SIDE_QUESTS = List.of(
            new SideQuest("sq_diamond_rush", "Алмазный резерв", "Накопите в инвентаре 4 натуральных алмаза Minecraft.", 600, 10),
            new SideQuest("sq_cave_diver", "Глубинный горизонт", "Спуститесь ниже Y=0 и добудьте не менее 2 блоков руды.", 650, 12),
            new SideQuest("sq_hunter", "Охотник сектора", "Победите 8 враждебных монстров в мире.", 700, 12),
            new SideQuest("sq_long_march", "Дальний рейд", "Преодолейте суммарную дистанцию в 600 блоков.", 650, 12),
            new SideQuest("sq_iron_baron", "Рудный магнат", "Добудьте 16 руд или заработайте от 2 000 CR.", 750, 12),
            new SideQuest("sq_heavy_investor", "Крупный акционер", "Потратьте не менее 4 алмазов на улучшения в Магазине.", 900, 15),
            new SideQuest("sq_resilience", "Стабильный профиль", "Проведите 3 финансовые операции и заработайте от 1 200 CR.", 700, 12),
            new SideQuest("sq_trader", "Контрактный дилер", "Выполните и заберите награду по 2 биржевым контрактам.", 850, 15),
            new SideQuest("sq_gold_rush", "Золотой стандарт", "Добудьте не менее 4 блоков золотой руды.", 750, 12),
            new SideQuest("sq_nether_recon", "Незер-разведка", "Добудьте хотя бы 1 блок руды кварца или обломков в Незере.", 1200, 20),
            new SideQuest("sq_sniper", "Костяной отчёт", "Победите 2 скелетов в игровом мире.", 600, 10),
            new SideQuest("sq_apex", "Опасная цель", "Победите редкого врага (Эндермен, Ведьма или Разоритель).", 1000, 16)
    );

    // ==========================================
    // CANONICAL SINGLE ENDING: FINAL SETTLEMENT
    // ==========================================
    public static final List<FinalChoiceOption> FINAL_CHOICES = List.of(
            new FinalChoiceOption(
                    "FINAL_SETTLEMENT",
                    "ФИНАНСОВЫЙ РАСЧЁТ",
                    "СТАТЬЯ 17.4: ПОЛНАЯ ПЕРЕДАЧА БИОЛОГИЧЕСКИХ АКТИВОВ",
                    "Завершение всех 5 этапов Программы Активов корпорации",
                    "Мгновенная выплата +100 000 CR каждому участнику, бессрочный ортопедический фиксатор",
                    "«Вы больше не клиент. Корпорация забирает биологический залог. Сделка окончательна.»",
                    0xFFEF4444
            )
    );
}
