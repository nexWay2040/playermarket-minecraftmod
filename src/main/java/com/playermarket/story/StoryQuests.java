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

    // Stage 1: Corporate Onboarding & Piggy Bank Cash-In
    public static final List<StageTask> STAGE_1_TASKS = List.of(
            new StageTask("s1_first_sale", 1, "Мелкий шрифт в деле", "Продайте любую первую характеристику (10–25%) во вкладке «ПРОДАЖА».", 220, 3),
            new StageTask("s1_piggy_catch", 1, "Беглая Свинка-Копилка", "Догоните светящуюся Свинку-Копилку рядом (ПКМ/удар) ИЛИ поймайте Курьера!", 160, 2),
            new StageTask("s1_ficsit_sink", 1, "Утилизация FICSIT", "Сдайте 8 любых вещей (грязь, плоть, руда и т.п.) на «Рынке Ресурсов».", 140, 1),
            new StageTask("s1_drg_dance", 1, "Перекур вопреки уставу", "Нарушьте дисциплину DRG: присядьте (Shift) 10 раз рядом с другом или на базе.", 120, 1)
    );

    // Stage 2: Stunts, Rainbow Pinata & Upgrades
    public static final List<StageTask> STAGE_2_TASKS = List.of(
            new StageTask("s2_gravity_test", 2, "Гравитация — актив фирмы", "Спрыгните с высоты 8+ блоков (в воду, сено, слизь или на ноги) и выживите!", 200, 2),
            new StageTask("s2_pinata_audit", 2, "Радужная Овца-Пиньята", "Стукните или подстригите 3 раза радужную Овцу jeb_ (появится рядом с конфетти)!", 240, 3),
            new StageTask("s2_diamond_procure", 2, "Внедрение углерода", "Приобретите любой 1 алмазный чип в «МАГАЗИНЕ УЛУЧШЕНИЙ».", 200, 2),
            new StageTask("s2_emergency_delivery", 2, "Тестовый подряд", "Примите и выполните 1 любой корпоративный контракт во вкладке «КОНТРАКТЫ».", 240, 3)
    );

    // Stage 3: Side Effects, Inspector Bribe & Leasing
    public static final List<StageTask> STAGE_3_TASKS = List.of(
            new StageTask("s3_paranoid_audit", 3, "Симулятор паранойи", "Покрутите камерой на 1080° (3 оборота) где угодно — проверьте, не следит ли HR!", 220, 2),
            new StageTask("s3_inspector_bribe", 3, "Взятка голодному инспектору", "Угостите Инспектора (или любого Жителя) едой или изумрудом через ПКМ!", 260, 3),
            new StageTask("s3_dark_kitchen", 3, "Дегустация бизнес-ланча", "Съешьте гнилую плоть, сырое мясо, печенье, ягоды, суп или выпейте молоко.", 200, 2),
            new StageTask("s3_night_shift", 3, "Игра в долгую", "Оформите любой Лизинг стата ИЛИ выкупите проданную характеристику обратно.", 280, 4)
    );

    // Stage 4: Sky Chicken Courier, Roulette 50/50 & Dark Sector
    public static final List<StageTask> STAGE_4_TASKS = List.of(
            new StageTask("s4_sky_courier", 4, "Куриный десант с неба", "Перехватите Курицу-Курьера на парашюте (ПКМ/удар) или заберите её аирдроп!", 300, 4),
            new StageTask("s4_casino_rush", 4, "Казик 50/50 не отпускает", "Сыграйте 2 раза в «Инвест-Рулетку 50/50» (в Обзоре или на Чёрном рынке)!", 260, 3),
            new StageTask("s4_high_risk_contract", 4, "Нелегальный софт", "Купите чип на Чёрном Рынке, закройте 2 контракта или победите врага в Директиву.", 320, 4),
            new StageTask("s4_handicapped_dare", 4, "Управляемый даунсайзинг", "Имея ≥2 проданных стата, убейте 1 врага ИЛИ закройте 5 сделок на бирже.", 350, 5)
    );

    // Stage 5: Collector Raid, Verity Kolobok & Final Preparation
    public static final List<StageTask> STAGE_5_TASKS = List.of(
            new StageTask("s5_collector_clash", 5, "Визит Коллектора", "Отбейтесь от Коллектора Asset Corp (появится рядом) ИЛИ одолейте 3 врагов!", 400, 5),
            new StageTask("s5_ballistics_check", 5, "Верити-Колобок и салют", "Поймайте жёлтого Верити-Колобка (ПКМ/удар) ИЛИ запустите 5 снежков/фейерверков!", 350, 4),
            new StageTask("s5_resource_recovery", 5, "Олигарх на диете", "Заработайте всего 2 500+ CR ИЛИ вложите 2+ алмаза в улучшения/рулетку.", 380, 4),
            new StageTask("s5_final_field_eval", 5, "Финальный аудит портфеля", "Закройте суммарно 6+ сделок на бирже (рулетка, статы, сырьё или контракты).", 450, 6)
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
            new SideQuest("sq_alterra_debt", "Долг корпорации Alterra", "Накопите 4 натуральных алмаза в инвентаре (вы уже должны за них корпорации 3M CR).", 160, 1),
            new SideQuest("sq_phishing_meal", "Премия Annual_Bonus.exe", "Переименуйте любую еду на наковальне и съешьте её (или съешьте сомнительный паёк).", 180, 1),
            new SideQuest("sq_trampoline", "Прыжок веры", "Упадите минимум с 10 блоков высоты (в воду, сено, слизь или на ноги) и выживите!", 200, 2),
            new SideQuest("sq_sky_parcel_catch", "Авиапочта и пиньята", "Поймайте Свинку-Копилку, Радужную Пиньяту, Курицу или Верити-Колобка (1 раз).", 220, 2),
            new SideQuest("sq_roulette_player", "Ставка на 50/50", "Крутаните «Инвест-Рулетку 50/50» хотя бы 1 раз (в Обзоре или Чёрном рынке).", 160, 1),
            new SideQuest("sq_bribe_bread", "Откат хлебушком", "Угостите едой или изумрудом Инспектора, Жителя или Торговца (ПКМ).", 180, 2),
            new SideQuest("sq_first_chip", "Свежая прошивка", "Купите хотя бы 1 постоянное улучшение за алмазы во вкладке «МАГАЗИН».", 180, 1),
            new SideQuest("sq_ballistics_fun", "Артподготовка", "Запустите 5 любых снарядов: снежки, яйца, стрелы, жемчуг или фейерверк.", 180, 2),
            new SideQuest("sq_merchant_shady", "Серый импорт", "Откройте торговлю с Жителем/Торговцем ИЛИ купите 2 партии в «Каталоге сырья».", 180, 1),
            new SideQuest("sq_broken_asset", "На 30% меньше человек", "Продайте сразу 3+ характеристики или снизьте общую био-норму на 30%.", 280, 3),
            new SideQuest("sq_collector_hunter", "Антиколлектор", "Отбейтесь от Коллектора, победите 3 враждебных мобов или поймайте Верити-Колобка.", 260, 3),
            new SideQuest("sq_red_tape", "Мастер бюрократии", "Закройте 2 корпоративных контракта во вкладке «КОНТРАКТЫ».", 260, 3)
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
