package com.playermarket.market;

import com.playermarket.config.MarketConfig;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Arrays;
import java.util.Optional;

public enum UpgradeType {
    // === BODY AUGMENTATIONS (8) ===
    REINFORCED_HEART(
            "reinforced_heart", Category.BODY,
            new int[]{14, 28, 52},
            new double[]{2.0, 4.0, 6.0},
            new String[]{"+1 Сердце (+2 HP)", "+2 Сердца (+4 HP)", "+3 Сердца (+6 HP)"},
            Attributes.MAX_HEALTH, AttributeModifier.Operation.ADD_VALUE,
            null, 0
    ),
    RAPID_RECOVERY(
            "rapid_recovery", Category.BODY,
            new int[]{12, 24, 44},
            new double[]{0.20, 0.40, 0.70},
            new String[]{"+20% Естеств. реген", "+40% Естеств. реген", "+70% Естеств. реген"},
            null, null,
            REINFORCED_HEART, 1
    ),
    ADRENALINE_SYSTEM(
            "adrenaline_system", Category.BODY,
            new int[]{14, 28, 48},
            new double[]{0.08, 0.16, 0.25},
            new String[]{"+8% Скорость движения", "+16% Скорость движения", "+25% Скорость движения"},
            Attributes.MOVEMENT_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE,
            null, 0
    ),
    ENHANCED_LUNGS(
            "enhanced_lungs", Category.BODY,
            new int[]{10, 20, 36},
            new double[]{2.0, 4.0, 8.0},
            new String[]{"+2.0 Кислород и +35% под водой", "+4.0 Кислород и +70% под водой", "+8.0 Кислород и +120% под водой"},
            Attributes.OXYGEN_BONUS, AttributeModifier.Operation.ADD_VALUE,
            null, 0
    ),
    STRONG_LEGS(
            "strong_legs", Category.BODY,
            new int[]{12, 24, 42},
            new double[]{0.12, 0.24, 0.38},
            new String[]{"+12% Сила прыжка", "+24% Сила прыжка", "+38% Сила прыжка"},
            Attributes.JUMP_STRENGTH, AttributeModifier.Operation.ADD_MULTIPLIED_BASE,
            null, 0
    ),
    METABOLISM_UPGRADE(
            "metabolism_upgrade", Category.BODY,
            new int[]{12, 24, 42},
            new double[]{0.20, 0.40, 0.65},
            new String[]{"-20% Расход сытости", "-40% Расход сытости", "-65% Расход сытости"},
            null, null,
            null, 0
    ),
    REINFORCED_BONES(
            "reinforced_bones", Category.BODY,
            new int[]{12, 26, 46},
            new double[]{-0.25, -0.50, -0.75},
            new String[]{"-25% Урон от падения (+1.5м)", "-50% Урон от падения (+3м)", "-75% Урон от падения (+5м)"},
            Attributes.FALL_DAMAGE_MULTIPLIER, AttributeModifier.Operation.ADD_MULTIPLIED_BASE,
            STRONG_LEGS, 1
    ),
    THERMAL_ADAPTATION(
            "thermal_adaptation", Category.BODY,
            new int[]{12, 24, 44},
            new double[]{-0.25, -0.50, -0.75},
            new String[]{"-25% Урон от огня и горение", "-50% Урон от огня и горение", "-75% Урон от огня и горение"},
            Attributes.BURNING_TIME, AttributeModifier.Operation.ADD_MULTIPLIED_BASE,
            null, 0
    ),

    // === COMBAT AUGMENTATIONS (7) ===
    MUSCLE_OVERCLOCK(
            "muscle_overclock", Category.COMBAT,
            new int[]{16, 32, 60},
            new double[]{0.10, 0.22, 0.38},
            new String[]{"+10% Боевой урон", "+22% Боевой урон", "+38% Боевой урон"},
            Attributes.ATTACK_DAMAGE, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
            null, 0
    ),
    FAST_REFLEXES(
            "fast_reflexes", Category.COMBAT,
            new int[]{14, 28, 52},
            new double[]{0.10, 0.20, 0.35},
            new String[]{"+10% Скорость атаки", "+20% Скорость атаки", "+35% Скорость атаки"},
            Attributes.ATTACK_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE,
            null, 0
    ),
    REINFORCED_SKIN(
            "reinforced_skin", Category.COMBAT,
            new int[]{15, 30, 56},
            new double[]{2.0, 4.0, 7.0},
            new String[]{"+2.0 Базовая броня", "+4.0 Базовая броня", "+7.0 Базовая броня"},
            Attributes.ARMOR, AttributeModifier.Operation.ADD_VALUE,
            null, 0
    ),
    IMPACT_DAMPENER(
            "impact_dampener", Category.COMBAT,
            new int[]{12, 24, 44},
            new double[]{0.20, 0.40, 0.65},
            new String[]{"+20% Сопр. отбрасыванию", "+40% Сопр. отбрасыванию", "+65% Сопр. отбрасыванию"},
            Attributes.KNOCKBACK_RESISTANCE, AttributeModifier.Operation.ADD_VALUE,
            REINFORCED_SKIN, 1
    ),
    PRECISION_TARGETING(
            "precision_targeting", Category.COMBAT,
            new int[]{14, 28, 50},
            new double[]{0.4, 0.8, 1.3},
            new String[]{"+0.4м Дальность атаки", "+0.8м Дальность атаки", "+1.3м Дальность атаки"},
            Attributes.ENTITY_INTERACTION_RANGE, AttributeModifier.Operation.ADD_VALUE,
            null, 0
    ),
    PROJECTILE_CALIBRATION(
            "projectile_calibration", Category.COMBAT,
            new int[]{12, 25, 46},
            new double[]{0.15, 0.30, 0.50},
            new String[]{"+15% Урон снарядов и стрел", "+30% Урон снарядов и стрел", "+50% Урон снарядов и стрел"},
            null, null,
            null, 0
    ),
    COMBAT_FOCUS(
            "combat_focus", Category.COMBAT,
            new int[]{16, 34, 62},
            new double[]{0.18, 0.35, 0.60},
            new String[]{"+18% Критический урон", "+35% Критический урон", "+60% Критический урон"},
            null, null,
            MUSCLE_OVERCLOCK, 1
    ),

    // === PROFESSIONAL AUGMENTATIONS (7) ===
    MINING_PROTOCOL(
            "mining_protocol", Category.PROFESSIONAL,
            new int[]{14, 28, 54},
            new double[]{0.20, 0.45, 0.80},
            new String[]{"+20% Скорость копания", "+45% Скорость копания", "+80% Скорость копания"},
            Attributes.BLOCK_BREAK_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE,
            null, 0
    ),
    LUCKY_CHIP(
            "lucky_chip", Category.PROFESSIONAL,
            new int[]{16, 32, 64},
            new double[]{1.0, 2.5, 4.5},
            new String[]{"+1.0 Удача", "+2.5 Удача", "+4.5 Удача"},
            Attributes.LUCK, AttributeModifier.Operation.ADD_VALUE,
            null, 0
    ),
    XP_ACCELERATOR(
            "xp_accelerator", Category.PROFESSIONAL,
            new int[]{12, 24, 46},
            new double[]{0.20, 0.45, 0.80},
            new String[]{"+20% Получение опыта", "+45% Получение опыта", "+80% Получение опыта"},
            null, null,
            null, 0
    ),
    ORE_SCANNER(
            "ore_scanner", Category.PROFESSIONAL,
            new int[]{16, 32, 60},
            new double[]{5.0, 8.0, 12.0},
            new String[]{"Сонар руд 5м (Алмаз, Золото, Изумруд, Обломки)", "Сонар руд 8м (+Железо и Редстоун)", "Сонар руд 12м (макс. радиус сонара + HUD)"},
            null, null,
            MINING_PROTOCOL, 1
    ),
    EFFICIENT_METABOLISM(
            "efficient_metabolism", Category.PROFESSIONAL,
            new int[]{12, 24, 44},
            new double[]{0.25, 0.45, 0.70},
            new String[]{"-25% Потеря голода при работе", "-45% Потеря голода при работе", "-70% Потеря голода при работе"},
            null, null,
            null, 0
    ),
    AQUATIC_PROPULSION(
            "aquatic_propulsion", Category.PROFESSIONAL,
            new int[]{10, 22, 40},
            new double[]{0.35, 0.70, 1.20},
            new String[]{"+35% Скор. в воде и копания в воде", "+70% Скор. в воде и копания в воде", "+120% Скор. в воде и копания в воде"},
            Attributes.WATER_MOVEMENT_EFFICIENCY, AttributeModifier.Operation.ADD_VALUE,
            null, 0
    ),
    PRECISION_HANDS(
            "precision_hands", Category.PROFESSIONAL,
            new int[]{14, 28, 52},
            new double[]{0.75, 1.5, 2.5},
            new String[]{"+0.75м Дальность блоков", "+1.5м Дальность блоков", "+2.5м Дальность блоков"},
            Attributes.BLOCK_INTERACTION_RANGE, AttributeModifier.Operation.ADD_VALUE,
            null, 0
    ),

    // === CORPORATE PRIVILEGES (6) — High-Value Executive Diamond Investments ===
    PREMIUM_ACCOUNT(
            "premium_account", Category.CORPORATE,
            new int[]{20, 40, 72},
            new double[]{0.12, 0.25, 0.40},
            new String[]{"+12% к выплатам CR", "+25% к выплатам CR", "+40% к выплатам CR"},
            null, null,
            null, 0
    ),
    BROKER_LICENSE(
            "broker_license", Category.CORPORATE,
            new int[]{18, 36, 64},
            new double[]{0.10, 0.18, 0.25},
            new String[]{"-10% Комиссия обратного выкупа", "-18% Комиссия обратного выкупа", "-25% Комиссия обратного выкупа"},
            null, null,
            null, 0
    ),
    MARKET_INSURANCE(
            "market_insurance", Category.CORPORATE,
            new int[]{16, 32, 56},
            new double[]{0.30, 0.55, 0.80},
            new String[]{"-30% Штраф и нагрузка лизинга", "-55% Штраф и нагрузка лизинга", "-80% Штраф и нагрузка лизинга"},
            null, null,
            BROKER_LICENSE, 1
    ),
    VIP_CLIENT(
            "vip_client", Category.CORPORATE,
            new int[]{24, 48, 92},
            new double[]{1.0, 2.0, 3.0},
            new String[]{
                    "Лимит +5 ♦/дн. (15), +15% дивиденды, Каталог",
                    "Лимит +12 ♦/дн. (22), скидка -10% ♦ на чипы, +30% див.",
                    "Лимит +25 ♦/дн. (35), скидка -20% ♦, +50% див., Чёрный рынок"
            },
            null, null,
            PREMIUM_ACCOUNT, 1
    ),
    PRICE_LOCK(
            "price_lock", Category.CORPORATE,
            new int[]{20, 42, 75},
            new double[]{1.0, 2.0, 3.0},
            new String[]{"Иммунитет к обвалам рынка", "Гарант мин. курса +8%", "Гарант мин. курса +18%"},
            null, null,
            null, 0
    ),
    DIAMOND_CASHBACK(
            "diamond_cashback", Category.CORPORATE,
            new int[]{24, 48, 84},
            new double[]{0.20, 0.35, 0.50},
            new String[]{"20% шанс возврата 1-2 алмазов", "35% шанс возврата 1-3 алмазов", "50% шанс возврата 2-4 алмазов"},
            null, null,
            VIP_CLIENT, 1
    );

    public enum Category {
        BODY("upgrade.category.body", "ТЕЛЕСНЫЕ"),
        COMBAT("upgrade.category.combat", "БОЕВЫЕ"),
        PROFESSIONAL("upgrade.category.professional", "ПРОФЕССИЯ"),
        CORPORATE("upgrade.category.corporate", "VIP И КОРП.");

        private final String translationKey;
        private final String defaultName;

        Category(String translationKey, String defaultName) {
            this.translationKey = translationKey;
            this.defaultName = defaultName;
        }

        public String getTranslationKey() {
            return translationKey;
        }

        public String getDefaultName() {
            return defaultName;
        }

        public String getDisplayName() {
            return defaultName;
        }
    }

    public String getTitle() {
        return switch (this) {
            case REINFORCED_HEART -> "Усиленное сердце";
            case RAPID_RECOVERY -> "Быстрое восстановление";
            case ADRENALINE_SYSTEM -> "Адреналиновая система";
            case ENHANCED_LUNGS -> "Улучшенные лёгкие";
            case STRONG_LEGS -> "Мощные ноги";
            case METABOLISM_UPGRADE -> "Метаболический регулятор";
            case REINFORCED_BONES -> "Укреплённый скелет";
            case THERMAL_ADAPTATION -> "Термоадаптация";
            case MUSCLE_OVERCLOCK -> "Разгон мускулатуры";
            case FAST_REFLEXES -> "Быстрые рефлексы";
            case REINFORCED_SKIN -> "Укреплённая кожа";
            case IMPACT_DAMPENER -> "Гаситель ударов";
            case PRECISION_TARGETING -> "Точное наведение";
            case PROJECTILE_CALIBRATION -> "Калибровка снарядов";
            case COMBAT_FOCUS -> "Боевой фокус";
            case MINING_PROTOCOL -> "Протокол добычи";
            case LUCKY_CHIP -> "Чип удачи";
            case XP_ACCELERATOR -> "XP-Акселератор";
            case ORE_SCANNER -> "Сканер руд";
            case EFFICIENT_METABOLISM -> "Эффективный метаболизм";
            case AQUATIC_PROPULSION -> "Водный двигатель";
            case PRECISION_HANDS -> "Точные манипуляторы";
            case PREMIUM_ACCOUNT -> "Премиум Аккаунт";
            case BROKER_LICENSE -> "Лицензия брокера";
            case MARKET_INSURANCE -> "Рыночная страховка";
            case VIP_CLIENT -> "VIP Клиент";
            case PRICE_LOCK -> "Фиксация цен";
            case DIAMOND_CASHBACK -> "Алмазный кэшбэк";
        };
    }

    private final String id;
    private final Category category;
    private final int[] diamondCosts;
    private final double[] levelValues;
    private final String[] levelSummaries;
    private final Holder<Attribute> vanillaAttribute;
    private final AttributeModifier.Operation operation;
    private final UpgradeType prerequisite;
    private final int prerequisiteLevel;

    UpgradeType(
            String id,
            Category category,
            int[] diamondCosts,
            double[] levelValues,
            String[] levelSummaries,
            Holder<Attribute> vanillaAttribute,
            AttributeModifier.Operation operation,
            UpgradeType prerequisite,
            int prerequisiteLevel
    ) {
        this.id = id;
        this.category = category;
        this.diamondCosts = diamondCosts;
        this.levelValues = levelValues;
        this.levelSummaries = levelSummaries;
        this.vanillaAttribute = vanillaAttribute;
        this.operation = operation;
        this.prerequisite = prerequisite;
        this.prerequisiteLevel = prerequisiteLevel;
    }

    public String getId() {
        return id;
    }

    public Category getCategory() {
        return category;
    }

    public String getTranslationKey() {
        return "upgrade.playermarket." + id;
    }

    public String getDescriptionKey() {
        return "upgrade.playermarket." + id + ".desc";
    }

    public int getMaxLevel() {
        return Math.min(diamondCosts.length, MarketConfig.get().maxUpgradeLevelCap);
    }

    public int getDiamondCostForLevel(int targetLevel) {
        if (targetLevel < 1 || targetLevel > diamondCosts.length) return 999;
        Integer customBase = MarketConfig.get().customUpgradeBaseDiamondCost.get(id);
        int raw = customBase != null ? customBase * targetLevel : diamondCosts[targetLevel - 1];
        return Math.max(1, (int) Math.round(raw * MarketConfig.get().upgradePriceMultiplier));
    }

    public double getValueForLevel(int level) {
        if (level <= 0) return 0.0;
        int idx = Math.clamp(level - 1, 0, levelValues.length - 1);
        return levelValues[idx];
    }

    public String getSummaryForLevel(int level) {
        int idx = Math.clamp(level - 1, 0, levelSummaries.length - 1);
        return levelSummaries[idx];
    }

    public String getMechanicDescription() {
        return switch (this) {
            case REINFORCED_HEART -> "Постоянно увеличивает максимум здоровья персонажа.";
            case RAPID_RECOVERY -> "Усиливает любое лечение и регенерацию от сытости.";
            case ADRENALINE_SYSTEM -> "Постоянно ускоряет базовый бег и передвижение.";
            case ENHANCED_LUNGS -> "Продлевает запас воздуха и ускоряет добычу под водой.";
            case STRONG_LEGS -> "Увеличивает высоту прыжка — легче перепрыгивать блоки.";
            case METABOLISM_UPGRADE -> "Сокращает пассивный расход сытости и штрафы голода.";
            case REINFORCED_BONES -> "Смягчает урон от падения и повышает безопасную высоту.";
            case THERMAL_ADAPTATION -> "Снижает урон от огня и сокращает длительность горения.";
            case MUSCLE_OVERCLOCK -> "Мультипликативно усиливает любой урон в ближнем бою.";
            case FAST_REFLEXES -> "Ускоряет перезарядку ударов оружием и инструментами.";
            case REINFORCED_SKIN -> "Даёт постоянную встроенную броню поверх экипировки.";
            case IMPACT_DAMPENER -> "Снижает отбрасывание от атак монстров и взрывов.";
            case PRECISION_TARGETING -> "Увеличивает дистанцию атак по мобам в ближнем бою.";
            case PROJECTILE_CALIBRATION -> "Усиливает урон луков, арбалетов, трезубцев и снежков.";
            case COMBAT_FOCUS -> "Увеличивает множитель урона критических атак в прыжке.";
            case MINING_PROTOCOL -> "Ускоряет добычу любых блоков всеми инструментами.";
            case LUCKY_CHIP -> "Повышает параметр Удачи для редкого лута и рыбалки.";
            case XP_ACCELERATOR -> "Умножает весь получаемый опыт из сфер, мобов и руд.";
            case ORE_SCANNER -> "Каждые 3с подсвечивает руды контуром сквозь стены + счётчик HUD.";
            case EFFICIENT_METABOLISM -> "Снижает истощение от спринта, копания и лизинга.";
            case AQUATIC_PROPULSION -> "Ускоряет плавание и работу под водой почти без штрафа.";
            case PRECISION_HANDS -> "Позволяет ставить и ломать блоки с большего расстояния.";
            case PREMIUM_ACCOUNT -> "Увеличивает выплаты CR за продажу статов, сырья и контракты.";
            case BROKER_LICENSE -> "Снижает комиссию корпорации при обратном выкупе активов.";
            case MARKET_INSURANCE -> "Смягчает дебаффы и риск лизинга характеристик.";
            case VIP_CLIENT -> "Повышает суточный лимит продажи ♦, дивиденды и даёт скидку ♦.";
            case PRICE_LOCK -> "Защищает от падения курса при кризисах и держит мин. планку CR.";
            case DIAMOND_CASHBACK -> "При покупке чипов от 4 ♦ даёт шанс мгновенно вернуть часть ♦.";
        };
    }

    public Holder<Attribute> getVanillaAttribute() {
        return vanillaAttribute;
    }

    public AttributeModifier.Operation getOperation() {
        return operation;
    }

    public UpgradeType getPrerequisite() {
        return prerequisite;
    }

    public int getPrerequisiteLevel() {
        return prerequisiteLevel;
    }

    public static Optional<UpgradeType> byId(String id) {
        return Arrays.stream(values()).filter(u -> u.id.equalsIgnoreCase(id)).findFirst();
    }
}
