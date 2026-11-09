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
                    "Первые шаги в Программе Активов. Корпорация вежлива, щедра и предлагает монетизировать избыточные биологические ресурсы.",
                    "Испытательный клиент (Доступно: ТЕЛО до 25%)",
                    0xFF10B981
            ),
            new StageInfo(
                    2,
                    "ЛЁГКИЕ ДЕНЬГИ",
                    "STAGE 2: EASY MONEY",
                    "Ликвидность растёт быстрее, чем вы ожидали. Корпорация предлагает диверсифицировать продажи и освоить инвестиции.",
                    "Активный клиент (Доступно: ТЕЛО, ДВИЖЕНИЕ до 50%)",
                    0xFF38BDF8
            ),
            new StageInfo(
                    3,
                    "ПОБОЧНЫЕ ЭФФЕКТЫ",
                    "STAGE 3: SIDE EFFECTS",
                    "Вы начинаете ощущать последствия сделок. Оказывается, обратный выкуп стоит дороже, а владение телом — платная услуга.",
                    "Привилегированный клиент (Доступно: БОЙ, ЛИЗИНГ)",
                    0xFFF59E0B
            ),
            new StageInfo(
                    4,
                    "ПРЕМИУМ-КЛИЕНТ",
                    "STAGE 4: PREMIUM CLIENT",
                    "Поздравляем! Ваш статус повышен до PREMIUM. Корпорация открывает закрытый Чёрный Рынок и полный перечень профессий.",
                    "VIP Инвестор (Доступен ЧЁРНЫЙ РЫНОК и ПРОФЕССИИ)",
                    0xFFC084FC
            ),
            new StageInfo(
                    5,
                    "ФИНАНСОВЫЙ АУДИТ",
                    "STAGE 5: FINANCIAL TROUBLE",
                    "Внеплановая проверка портфеля. Скрытые комиссии, налоги на богатство и стресс-тесты вашего модифицированного тела.",
                    "Корпоративный партнёр (Ликвидация до 80-90%)",
                    0xFFEF4444
            ),
            new StageInfo(
                    6,
                    "ФИНАЛЬНАЯ СДЕЛКА",
                    "STAGE 6: THE FINAL TRANSACTION",
                    "Решающий выбор жизненного пути: остаться вечным акционером корпорации, выкупить свою свободу или продаться целиком.",
                    "Финальная Резолюция Совета Директоров",
                    0xFFFBBF24
            )
    );

    public static final List<StageTask> STAGE_1_TASKS = List.of(
            new StageTask("s1_register", 1, "Регистрация в Программе", "Откройте терминал PLAYER MARKET и активируйте карту доступа.", 50, 5),
            new StageTask("s1_eval", 1, "Оценка активов", "Изучите котировки собственных характеристик во вкладке «ПРОДАЖА».", 50, 5),
            new StageTask("s1_first_sale", 1, "Первая ликвидация", "Продайте любую биологическую характеристику на бирже.", 150, 10),
            new StageTask("s1_first_payment", 1, "Первый капитал", "Заработайте суммарно не менее 500 CR на любых операциях.", 100, 8),
            new StageTask("s1_welcome_pkg", 1, "Приветственный пакет", "Ознакомьтесь с ассортиментом во вкладке «МАГАЗИН УЛУЧШЕНИЙ».", 100, 8)
    );

    public static final List<StageTask> STAGE_2_TASKS = List.of(
            new StageTask("s2_sell_more", 2, "Масштабирование портфеля", "Совершите не менее 3 продаж характеристик.", 200, 12),
            new StageTask("s2_diamonds", 2, "Алмазный стандарт", "Получите и сохраните в инвентаре хотя бы 1 настоящий алмаз.", 150, 10),
            new StageTask("s2_first_invest", 2, "Первая инвестиция", "Приобретите любое постоянное улучшение за алмазы в Магазине.", 200, 15),
            new StageTask("s2_portfolio_exp", 2, "Диверсификация активов", "Продайте характеристики минимум из 2 разных категорий (Тело и Движение).", 250, 15),
            new StageTask("s2_resource_ex", 2, "Товарный обмен", "Продайте добытые в шахтах ресурсы на Рынке Ресурсов.", 150, 12)
    );

    public static final List<StageTask> STAGE_3_TASKS = List.of(
            new StageTask("s3_asset_recovery", 3, "Попытка возврата", "Выкупите обратно хотя бы часть любой проданной характеристики.", 300, 15),
            new StageTask("s3_lease_contract", 3, "Депозитный вклад", "Оформите временный лизинг (вклад под процент) во вкладке «ПРОДАЖА».", 250, 15),
            new StageTask("s3_survival_check", 3, "Биологическая стойкость", "Проживите 1 полный игровой день (20 минут) с изменёнными статами.", 350, 20),
            new StageTask("s3_upgrade_comp", 3, "Техническая компенсация", "Приобретите улучшение 2-го уровня в Магазине для компенсации слабостей.", 300, 20),
            new StageTask("s3_audit", 3, "Финансовый аудит", "Изучите отчёт о санкциях, налогах и дивидендах во вкладке «ПОРТФЕЛЬ».", 200, 15)
    );

    public static final List<StageTask> STAGE_4_TASKS = List.of(
            new StageTask("s4_premium_status", 4, "Акула рынка", "Совершите не менее 10 успешных финансовых сделок на терминале.", 400, 20),
            new StageTask("s4_investor", 4, "Крупный инвестор", "Потратьте не менее 8 алмазов на аугментации в Магазине Улучшений.", 500, 25),
            new StageTask("s4_diamond_reserve", 4, "Алмазный фонд", "Соберите резерв из не менее 10 алмазов в своём инвентаре.", 450, 20),
            new StageTask("s4_asset_specialist", 4, "Узкая специализация", "Имейте не менее 3 проданных характеристик с долей от 25% и выше.", 500, 25),
            new StageTask("s4_contract_completion", 4, "Корпоративный подряд", "Успешно завершите и заберите награду за 3 корпоративных контракта.", 600, 30)
    );

    public static final List<StageTask> STAGE_5_TASKS = List.of(
            new StageTask("s5_hidden_commission", 5, "Особенности контракта", "Ознакомьтесь с комиссией выкупа (30%) во вкладке «ВЫКУП».", 400, 25),
            new StageTask("s5_undervalued", 5, "Трейдинг в кризис", "Совершите сделку во время макроэкономического события биржи.", 600, 25),
            new StageTask("s5_recovery_plan", 5, "План санации", "Успешно совершите обратный выкуп не менее 2 характеристик.", 750, 30),
            new StageTask("s5_market_crisis", 5, "Стресс-тест контрактов", "Завершите корпоративный контракт во время активного кризиса рынка.", 800, 30),
            new StageTask("s5_account_review", 5, "Генеральная ревизия", "Пройдите комплексную оценку здоровья и долгов во вкладке «ПОРТФЕЛЬ».", 500, 25)
    );

    public static final List<StageTask> getTasksForStage(int stage) {
        return switch (stage) {
            case 1 -> STAGE_1_TASKS;
            case 2 -> STAGE_2_TASKS;
            case 3 -> STAGE_3_TASKS;
            case 4 -> STAGE_4_TASKS;
            case 5 -> STAGE_5_TASKS;
            default -> List.of();
        };
    }

    public static final List<SideQuest> SIDE_QUESTS = List.of(
            new SideQuest("sq_poor_decisions", "Плохие решения", "Продайте биологические характеристики на общую сумму от 10 000 CR.", 1500, 15),
            new SideQuest("sq_diamond_fever", "Алмазная лихорадка", "Накопите в инвентаре 32 натуральных алмаза Minecraft.", 2500, 30),
            new SideQuest("sq_refund_please", "Оформите возврат", "Выкупите обратно любые проданные характеристики не менее 3 раз.", 1000, 15),
            new SideQuest("sq_corporate_slave", "Корпоративный раб", "Выполните 10 корпоративных заказов с доски контрактов.", 3000, 30),
            new SideQuest("sq_asset_collector", "Коллекционер активов", "Продайте хотя бы по одной характеристике из всех 4-х категорий.", 1200, 15),
            new SideQuest("sq_millionaire", "Миллионер", "Достигните баланса счёта в 100 000 CR (остерегайтесь налога на богатство!).", 0, 50),
            new SideQuest("sq_full_recovery", "Полное выздоровление", "Выкупите обратно характеристики не менее 3 раз и восстановите тело до 0% долгов.", 450, 15),
            new SideQuest("sq_investor_all", "Венчурный инвестор", "Приобретите постоянные улучшения за алмазы из всех 4 категорий.", 2000, 25),
            new SideQuest("sq_resource_tycoon", "Сырьевой магнат", "Заработайте не менее 5 000 CR на продаже обычных ресурсов и руд.", 1500, 15),
            new SideQuest("sq_bad_investment", "Рискованный портфель", "Имейте одновременно 3 характеристики, проданные на 50% и выше.", 2000, 20),
            new SideQuest("sq_diamond_hunt", "Охота за алмазами", "Потратьте не менее 12 алмазов на высокоуровневые аугментации.", 2500, 25),
            new SideQuest("sq_second_chance", "Жизнь взаймы", "Активируйте спасительный чип «Второй Шанс» на закрытом Чёрном Рынке.", 1800, 20)
    );

    public static final List<FinalChoiceOption> FINAL_CHOICES = List.of(
            new FinalChoiceOption(
                    "PREMIUM_SHAREHOLDER",
                    "ПРЕМИУМ-АКЦИОНЕР",
                    "ПУТЬ А: ВЕЧНЫЙ ДИРЕКТОР КОРПОРАЦИИ",
                    "Баланс счета ≥ 25 000 CR и статус VIP-Клиент (Ур. 2+)",
                    "Дивиденды +50% CR/мин навсегда, скидка выкупа -50%, титул [СОВЕТ ДИРЕКТОРОВ]",
                    "«Вы поняли правила игры. Зачем быть человеком, если можно быть корпорацией?»",
                    0xFFFBBF24
            ),
            new FinalChoiceOption(
                    "COMPLETE_RECOVERY",
                    "СУВЕРЕНИЗАЦИЯ ЛИЧНОСТИ",
                    "ПУТЬ B: РАЗРЫВ КОРПОРАТИВНОГО КОНТРАКТА",
                    "Выкупить обратно характеристики (все продажи ≤ 10%)",
                    "Постоянный бонус +4.0 Max HP (2 сердца), иммунитет к налогу на богатство, титул [СВОБОДНЫЙ ГРАЖДАНИН]",
                    "«Вы вернули каждую клетку своего тела и доказали, что свободу нельзя купить за кредиты.»",
                    0xFF10B981
            ),
            new FinalChoiceOption(
                    "TOTAL_LIQUIDATION",
                    "ТОТАЛЬНАЯ ЛИКВИДАЦИЯ",
                    "ПУТЬ C: ПОЛНАЯ ПРОДАЖА ЧЕЛОВЕЧНОСТИ",
                    "Продать минимум 5 характеристик на 70% и более",
                    "Мгновенная выплата +50 000 CR, цены Чёрного Рынка -35%, титул [СИНТЕТИЧЕСКИЙ СУБЪЕКТ]",
                    "«Органическое тело — пережиток эволюции. Зато банковский счёт выглядит божественно.»",
                    0xFFC084FC
            )
    );
}
