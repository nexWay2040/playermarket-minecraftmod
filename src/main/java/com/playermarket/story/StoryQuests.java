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
                    "Решающая резолюция корпорации: окончательный расчет, выплата 1 000 000 CR и вступление в силу Статьи 17.4.",
                    "Финальная Резолюция Совета Директоров (Статья 17.4)",
                    0xFFFBBF24
            )
    );

    // ==========================================
    // 20 CREATIVE GAMEPLAY QUESTS (4 per Stage, paced for fun ~15-20 min videos)
    // ==========================================

    // Stage 1: Corporate Onboarding & Office Absurdity
    public static final List<StageTask> STAGE_1_TASKS = List.of(
            new StageTask("s1_first_sale", 1, "Мелкий шрифт в деле", "Продайте любую первую характеристику (10–25%) во вкладке «ПРОДАЖА».", 300, 10),
            new StageTask("s1_icebreaker", 1, "Тимбилдинг (HR Icebreaker)", "Постойте рядом (≤5.5 бл.) сразу с 2 любыми мобами разных видов в течение 5 сек.", 250, 8),
            new StageTask("s1_ficsit_sink", 1, "Утилизация FICSIT", "Сдайте 8 любых вещей (грязь, плоть, руда и т.п.) на «Рынке Ресурсов».", 250, 8),
            new StageTask("s1_drg_dance", 1, "Перекур вопреки уставу", "Нарушьте дисциплину DRG: присядьте (Shift) 10 раз, пока начальство не видит.", 250, 8)
    );

    // Stage 2: Corporate Insurance Stunts & Augmentations
    public static final List<StageTask> STAGE_2_TASKS = List.of(
            new StageTask("s2_gravity_test", 2, "Гравитация — актив фирмы", "Спрыгните с высоты 10+ блоков (в воду, сено, слизь или на ноги) и выживите!", 450, 12),
            new StageTask("s2_no_armor_dare", 2, "Оптимизация спецодежды", "При проданном стате постойте 5 сек без единой брони рядом с врагом (≤6 бл.).", 450, 12),
            new StageTask("s2_diamond_procure", 2, "Внедрение углерода", "Приобретите любой 1 алмазный чип в «МАГАЗИНЕ УЛУЧШЕНИЙ».", 450, 12),
            new StageTask("s2_emergency_delivery", 2, "Тестовый подряд", "Примите и выполните 1 любой корпоративный контракт во вкладке «КОНТРАКТЫ».", 500, 14)
    );

    // Stage 3: Side Effects, Paranoia & Leasing
    public static final List<StageTask> STAGE_3_TASKS = List.of(
            new StageTask("s3_paranoid_audit", 3, "Симулятор паранойи", "Накрутите камерой 1800° (5 оборотов) ночью или под землёй (Y < 56).", 550, 15),
            new StageTask("s3_miracle_clutch", 3, "Страховка на грани", "Поешьте/полечитесь при ≤3 сердцах ИЛИ переживите мощный удар от 6+ урона.", 550, 15),
            new StageTask("s3_dark_kitchen", 3, "Дегустация бизнес-ланча", "Съешьте гнилую плоть, глаз паука, сырое мясо, яд. картофель или выпейте молоко.", 500, 14),
            new StageTask("s3_night_shift", 3, "Игра в долгую", "Оформите любой Лизинг стата ИЛИ выкупите проданную характеристику обратно.", 600, 18)
    );

    // Stage 4: Extreme Restructuring & Dark Sector
    public static final List<StageTask> STAGE_4_TASKS = List.of(
            new StageTask("s4_downsizing", 4, "Управляемый даунсайзинг", "Подожгите Крипера огнивом, убейте моба в огне или победите врага критом.", 700, 18),
            new StageTask("s4_high_risk_contract", 4, "Нелегальный софт", "Купите чип на Чёрном Рынке, закройте 2 контракта или победите врага в событии.", 700, 20),
            new StageTask("s4_cloud_migration", 4, "Миграция данных в Облако", "Взберитесь на высоту Y ≥ 170 (столб или гора) для «синхронизации с облаком».", 650, 18),
            new StageTask("s4_handicapped_dare", 4, "Испытание без запчастей", "Победите 1 врага или закройте 5 сделок, имея минимум 3 проданных характеристики.", 750, 22)
    );

    // Stage 5: Final Extreme Audit Before Section 17.4
    public static final List<StageTask> STAGE_5_TASKS = List.of(
            new StageTask("s5_fire_and_water", 5, "Крио-сауна корпорации", "Искупайтесь в воде или котле рядом с лавой (≤5 бл.) либо внутри Незера!", 850, 24),
            new StageTask("s5_no_hands_brawl", 5, "Переговоры голыми руками", "Нанесите враждебным мобам 4 удара абсолютно пустыми руками.", 800, 22),
            new StageTask("s5_resource_recovery", 5, "Олигарх на диете", "Заработайте всего 3 500+ CR, вложите 3+ алмаза или проведите 3 сделки в событиях.", 850, 24),
            new StageTask("s5_final_field_eval", 5, "Аттестация под землёй", "Спуститесь ниже Y=20 (или выйдите под ночной дождь) и закройте сделку с 2+ алмазами.", 1000, 30)
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
    // 12 CREATIVE CORPORATE SATIRE SIDE QUESTS
    // ==========================================
    public static final List<SideQuest> SIDE_QUESTS = List.of(
            new SideQuest("sq_alterra_debt", "Долг корпорации Alterra", "Накопите 4 натуральных алмаза в инвентаре (вы уже должны за них корпорации 3M CR).", 600, 10),
            new SideQuest("sq_phishing_meal", "Премия Annual_Bonus.exe", "Переименуйте любую еду на наковальне и съешьте её (или съешьте сомнительный паёк).", 650, 12),
            new SideQuest("sq_trampoline", "Прыжок веры", "Упадите минимум с 15 блоков высоты (в воду, сено, слизь или пером) и выживите!", 750, 14),
            new SideQuest("sq_creeper_stare", "Собеседование с Крипером", "Постойте рядом с Крипером (≤4.5 бл.) 3 сек или переживите взрыв Крипера.", 700, 12),
            new SideQuest("sq_rooftop_dealer", "Оффшор на краю неба", "Заключите любую сделку в терминале [M], находясь на высоте Y ≥ 150.", 650, 12),
            new SideQuest("sq_bare_knuckle", "Кулачный аргумент", "Нанесите враждебным мобам 6 ударов абсолютно голыми руками.", 700, 12),
            new SideQuest("sq_deepslate_work", "Глубинный субботник", "Спуститесь на глубинный сланец (ниже Y=0) и разбейте там 3 любых блока.", 650, 12),
            new SideQuest("sq_lightning_rod", "Искры энтузиазма", "Получите и переживите урон от огня, молнии или взрыва без смерти.", 750, 14),
            new SideQuest("sq_merchant_shady", "Серый импорт", "Откройте торговлю с Жителем/Бродячим торговцем ИЛИ купите 2 партии в каталоге.", 700, 12),
            new SideQuest("sq_broken_asset", "На 30% меньше человек", "Продайте сразу 3+ характеристики или снизьте общую био-норму на 30%.", 850, 15),
            new SideQuest("sq_nether_spa", "Горячая путёвка в пекло", "Посетите Незер ИЛИ проведите 10 секунд рядом с открытой лавой (≤4 бл.).", 950, 18),
            new SideQuest("sq_red_tape", "Мастер бюрократии", "Закройте 2 корпоративных контракта и оформите минимум 1 выкуп или лизинг.", 950, 18)
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
                    "Мгновенная выплата +1 000 000 CR каждому участнику, бессрочный ортопедический фиксатор",
                    "«Вы больше не клиент. Корпорация забирает биологический залог. Сделка окончательна.»",
                    0xFFEF4444
            )
    );
}
