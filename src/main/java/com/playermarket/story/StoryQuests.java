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
    // 20 ACTIVE GAMEPLAY QUESTS (4 per Stage)
    // ==========================================

    // Stage 1: Field Operations & Initial Liquidation
    public static final List<StageTask> STAGE_1_TASKS = List.of(
            new StageTask("s1_field_inspection", 1, "Полевая инспекция", "Исследуйте не менее 2 различных биомов в игровом мире.", 200, 6),
            new StageTask("s1_underground_audit", 1, "Подземный аудит", "Лично добудьте в пещерах не менее 16 железных или медных руд.", 250, 8),
            new StageTask("s1_first_sale", 1, "Первая ликвидация", "Продайте любую биологическую характеристику на бирже.", 300, 10),
            new StageTask("s1_asset_stress", 1, "Стресс-тест тела", "Переживите игровую ночь под открытым небом с дебаффом продажи.", 350, 10)
    );

    // Stage 2: Combat, Procurement & First Recovery
    public static final List<StageTask> STAGE_2_TASKS = List.of(
            new StageTask("s2_combat_insurance", 2, "Боевое страхование", "Победите 10 враждебных мобов при изменённых боевых параметрах.", 400, 12),
            new StageTask("s2_diamond_procure", 2, "Алмазный фонд", "Добудьте от 3 алмазов и приобретите улучшение в Магазине.", 450, 15),
            new StageTask("s2_recovery_op", 2, "План выкупа", "Добудьте ресурсы, заработайте капитал и выкупите характеристику.", 500, 15),
            new StageTask("s2_emergency_delivery", 2, "Срочная поставка", "Выполните принятый контракт на поставку сырья с биржи.", 450, 12)
    );

    // Stage 3: Deep Subterranean & Night Shift
    public static final List<StageTask> STAGE_3_TASKS = List.of(
            new StageTask("s3_deep_mining", 3, "Глубинная разведка", "Спуститесь ниже глубины Y=0 и добудьте 8 глубинных руд.", 550, 18),
            new StageTask("s3_terrain_survey", 3, "Маркшейдерский обход", "Преодолейте дистанцию в 1000 блоков и посетите 3 биома.", 550, 18),
            new StageTask("s3_equipment_test", 3, "Полевые испытания", "Экипируйте комплект брони и нейтрализуйте 5 мобов в ближнем бою.", 500, 15),
            new StageTask("s3_night_shift", 3, "Ночная смена", "Проведите ночное время под открытым небом, отражая нападения.", 600, 20)
    );

    // Stage 4: High-Risk Contracts & Co-op Field Testing
    public static final List<StageTask> STAGE_4_TASKS = List.of(
            new StageTask("s4_corporate_salvage", 4, "Сбор образцов", "Добудьте и соберите 32 единицы полезных ископаемых в недрах.", 650, 20),
            new StageTask("s4_high_risk_contract", 4, "Контракт высокого риска", "Завершите сделку или контракт во время биржевого события.", 700, 25),
            new StageTask("s4_personal_recovery", 4, "Физическая реабилитация", "Совершите выкуп характеристик не менее 2 раз.", 650, 20),
            new StageTask("s4_cooperative_test", 4, "Кооперативный тест", "Совместно выполните нормы добычи или боевых побед.", 750, 25)
    );

    // Stage 5: Underwater Inspection, Mobility & Final Evaluation
    public static final List<StageTask> STAGE_5_TASKS = List.of(
            new StageTask("s5_underwater_audit", 5, "Подводная инспекция", "Исследуйте подводные глубины и проведите под водой от 30 секунд.", 800, 25),
            new StageTask("s5_mobility_test", 5, "Тест мобильности", "Преодолейте 500 блоков в условиях сниженной скорости или прыжка.", 800, 25),
            new StageTask("s5_resource_recovery", 5, "Металлургический базис", "Самостоятельно выплавьте 32 слитка металлов в печах.", 850, 30),
            new StageTask("s5_final_field_eval", 5, "Финальная аттестация", "Победите 15 мобов, имейте от 5 алмазов и завершите этап.", 1000, 40)
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
            new SideQuest("sq_diamond_rush", "Алмазная лихорадка", "Накопите в инвентаре не менее 16 натуральных алмазов.", 1500, 20),
            new SideQuest("sq_cave_diver", "Глубинная впадина", "Спуститесь в глубокие пещеры и добудьте руду ниже Y=-32.", 800, 15),
            new SideQuest("sq_hunter", "Ночной охотник", "Лично уничтожьте 25 враждебных монстров в ночном мире.", 1000, 18),
            new SideQuest("sq_long_march", "Трансконтинентальный рейс", "Преодолейте дистанцию не менее 2500 блоков по миру.", 1200, 15),
            new SideQuest("sq_iron_baron", "Стальной магнат", "Выплавьте не менее 64 железных слитков в печах.", 900, 12),
            new SideQuest("sq_heavy_investor", "Крупный акционер", "Потратьте не менее 15 алмазов на аугментации в Магазине.", 2000, 25),
            new SideQuest("sq_resilience", "Мастер выживания", "Проживите 3 полных игровых дня без единой гибели оператора.", 1500, 20),
            new SideQuest("sq_trader", "Вексельный дилер", "Успешно закройте не менее 5 контрактов с биржевой доски.", 1800, 25),
            new SideQuest("sq_gold_rush", "Золотой стандарт", "Добудьте не менее 24 блоков золотой руды в пещерах или Незере.", 1100, 16),
            new SideQuest("sq_nether_recon", "Адская экспедиция", "Посетите Незер и добудьте не менее 4 единиц кварца или дебриса.", 2500, 30),
            new SideQuest("sq_sniper", "Меткий стрелок", "Уничтожьте 10 скелетов стрелковым оружием на дистанции.", 800, 12),
            new SideQuest("sq_apex", "Опасная биомасса", "Сразите опасного противника (Эндермен, Ведьма или Разоритель).", 1500, 20)
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
