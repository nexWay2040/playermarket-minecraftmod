package com.playermarket.market;

import com.playermarket.config.MarketConfig;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public enum AssetType {
    // === BODY ASSETS (1-8) ===
    MAX_HEALTH(
            "max_health", Category.BODY,
            20.0, 4.0, 2400L, "HP",
            Attributes.MAX_HEALTH, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, -1.0,
            new DividendOption("passive_cr_high", "Пассив: +25 CR/мин"),
            new DividendOption("adrenaline", "Бонус: +25% Скор. атаки")
    ),
    NATURAL_REGENERATION(
            "natural_regeneration", Category.BODY,
            100.0, 20.0, 1800L, "%",
            null, null, 0.0,
            new DividendOption("passive_cr_med", "Пассив: +18 CR/мин"),
            new DividendOption("potion_mastery", "Бонус: +50% Длит. зелий")
    ),
    SATURATION_EFFICIENCY(
            "saturation", Category.BODY,
            100.0, 20.0, 1500L, "%",
            null, null, 0.0,
            new DividendOption("passive_cr_low", "Пассив: +12 CR/мин"),
            new DividendOption("metabolic_focus", "Бонус: +35% Опыт (XP)")
    ),
    HUNGER_EFFICIENCY(
            "hunger_efficiency", Category.BODY,
            100.0, 20.0, 1600L, "%",
            null, null, 0.0,
            new DividendOption("passive_cr_low", "Пассив: +12 CR/мин"),
            new DividendOption("corporate_focus", "Бонус: +25% Выплаты контр.")
    ),
    OXYGEN_CAPACITY(
            "oxygen_capacity", Category.BODY,
            300.0, 60.0, 1200L, "тик",
            null, null, 0.0,
            new DividendOption("passive_cr_low", "Пассив: +10 CR/мин"),
            new DividendOption("dense_bones", "Бонус: +3.5 Броня")
    ),
    FIRE_RESISTANCE(
            "fire_resistance", Category.BODY,
            100.0, 20.0, 1500L, "%",
            Attributes.BURNING_TIME, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, 1.2,
            new DividendOption("passive_cr_med", "Пассив: +18 CR/мин"),
            new DividendOption("thermal_overload", "Бонус: +20% Урон атаки")
    ),
    FALL_RESISTANCE(
            "fall_resistance", Category.BODY,
            100.0, 20.0, 1500L, "%",
            Attributes.FALL_DAMAGE_MULTIPLIER, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, 2.0,
            new DividendOption("passive_cr_med", "Пассив: +18 CR/мин"),
            new DividendOption("kinetic_impact", "Бонус: +30% Крит. урон")
    ),
    MAX_ABSORPTION(
            "max_absorption", Category.BODY,
            20.0, 2.0, 1400L, "HP",
            Attributes.MAX_ABSORPTION, AttributeModifier.Operation.ADD_VALUE, -20.0,
            new DividendOption("passive_cr_low", "Пассив: +12 CR/мин"),
            new DividendOption("broker_instinct", "Бонус: -20% Комиссия выкупа")
    ),

    // === MOVEMENT ASSETS (9-13) ===
    MOVEMENT_SPEED(
            "movement_speed", Category.MOVEMENT,
            100.0, 25.0, 2200L, "%",
            Attributes.MOVEMENT_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, -0.90,
            new DividendOption("passive_cr_high", "Пассив: +25 CR/мин"),
            new DividendOption("reinforced_skin", "Бонус: +4.0 Броня")
    ),
    JUMP_STRENGTH(
            "jump_strength", Category.MOVEMENT,
            100.0, 35.0, 1600L, "%",
            Attributes.JUMP_STRENGTH, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, -0.40,
            new DividendOption("passive_cr_med", "Пассив: +18 CR/мин"),
            new DividendOption("grounded_stance", "Бонус: +40% Устойчивость")
    ),
    SPRINT_EFFICIENCY(
            "sprint_efficiency", Category.MOVEMENT,
            100.0, 20.0, 1500L, "%",
            null, null, 0.0,
            new DividendOption("passive_cr_med", "Пассив: +18 CR/мин"),
            new DividendOption("steady_pace", "Бонус: -35% Расход голода")
    ),
    SWIM_SPEED(
            "swim_speed", Category.MOVEMENT,
            100.0, 20.0, 1400L, "%",
            null, null, 0.0,
            new DividendOption("passive_cr_low", "Пассив: +12 CR/мин"),
            new DividendOption("terrestrial_stride", "Бонус: +20% Скор. на суше")
    ),
    STEP_HEIGHT(
            "step_height", Category.MOVEMENT,
            0.6, 0.12, 1400L, "м",
            Attributes.STEP_HEIGHT, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, -0.95,
            new DividendOption("passive_cr_low", "Пассив: +12 CR/мин"),
            new DividendOption("low_center", "Бонус: +3.0 Твёрдость брони")
    ),

    // === COMBAT ASSETS (14-23) ===
    ATTACK_DAMAGE(
            "attack_damage", Category.COMBAT,
            100.0, 20.0, 2200L, "%",
            Attributes.ATTACK_DAMAGE, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, -0.90,
            new DividendOption("passive_cr_high", "Пассив: +25 CR/мин"),
            new DividendOption("corporate_focus", "Бонус: +25% Выплаты контр.")
    ),
    ATTACK_SPEED(
            "attack_speed", Category.COMBAT,
            4.0, 1.0, 1800L, "/с",
            Attributes.ATTACK_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, -0.85,
            new DividendOption("passive_cr_med", "Пассив: +18 CR/мин"),
            new DividendOption("plated_arms", "Бонус: +4.0 Броня")
    ),
    ARMOR(
            "armor", Category.COMBAT,
            20.0, 4.0, 2000L, "ед",
            Attributes.ARMOR, AttributeModifier.Operation.ADD_VALUE, -16.0,
            new DividendOption("passive_cr_high", "Пассив: +25 CR/мин"),
            new DividendOption("unburdened", "Бонус: +20% Скорость бега")
    ),
    ARMOR_TOUGHNESS(
            "armor_toughness", Category.COMBAT,
            12.0, 2.4, 1600L, "ед",
            Attributes.ARMOR_TOUGHNESS, AttributeModifier.Operation.ADD_VALUE, -10.0,
            new DividendOption("passive_cr_med", "Пассив: +18 CR/мин"),
            new DividendOption("fortune_seeker", "Бонус: +2.5 Удача")
    ),
    KNOCKBACK_RESISTANCE(
            "knockback_resistance", Category.COMBAT,
            100.0, 20.0, 1500L, "%",
            Attributes.KNOCKBACK_RESISTANCE, AttributeModifier.Operation.ADD_VALUE, -0.8,
            new DividendOption("passive_cr_low", "Пассив: +12 CR/мин"),
            new DividendOption("broker_instinct", "Бонус: -20% Комиссия выкупа")
    ),
    ENTITY_REACH(
            "entity_reach", Category.COMBAT,
            3.0, 1.2, 2000L, "м",
            Attributes.ENTITY_INTERACTION_RANGE, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, -0.75,
            new DividendOption("passive_cr_high", "Пассив: +25 CR/мин"),
            new DividendOption("close_quarters", "Бонус: +25% Урон вблизи")
    ),
    PROJECTILE_RESISTANCE(
            "projectile_resistance", Category.COMBAT,
            100.0, 20.0, 1500L, "%",
            null, null, 0.0,
            new DividendOption("passive_cr_med", "Пассив: +18 CR/мин"),
            new DividendOption("ballistic_study", "Бонус: +30% Точность стрел")
    ),
    EXPLOSION_RESISTANCE(
            "explosion_resistance", Category.COMBAT,
            100.0, 20.0, 1500L, "%",
            Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, AttributeModifier.Operation.ADD_VALUE, -0.85,
            new DividendOption("passive_cr_med", "Пассив: +18 CR/мин"),
            new DividendOption("reinforced_skin", "Бонус: +4.0 Броня")
    ),
    CRITICAL_DAMAGE(
            "critical_damage", Category.COMBAT,
            150.0, 40.0, 1800L, "%",
            null, null, 0.0,
            new DividendOption("passive_cr_med", "Пассив: +18 CR/мин"),
            new DividendOption("experience_dividend", "Бонус: +35% Опыт (XP)")
    ),
    KNOCKBACK_POWER(
            "knockback_power", Category.COMBAT,
            100.0, 20.0, 1500L, "%",
            Attributes.ATTACK_KNOCKBACK, AttributeModifier.Operation.ADD_VALUE, -1.5,
            new DividendOption("passive_cr_low", "Пассив: +12 CR/мин"),
            new DividendOption("relentless_combo", "Бонус: +25% Скор. атаки")
    ),

    // === PROFESSIONAL ASSETS (24-30) ===
    MINING_SPEED(
            "mining_speed", Category.PROFESSIONAL,
            100.0, 15.0, 2000L, "%",
            Attributes.BLOCK_BREAK_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, -1.05,
            new DividendOption("passive_cr_high", "Пассив: +25 CR/мин"),
            new DividendOption("combat_redirect", "Бонус: +20% Урон атаки")
    ),
    UNDERWATER_MINING(
            "underwater_mining", Category.PROFESSIONAL,
            100.0, 15.0, 1400L, "%",
            Attributes.SUBMERGED_MINING_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, -1.05,
            new DividendOption("passive_cr_low", "Пассив: +12 CR/мин"),
            new DividendOption("experience_dividend", "Бонус: +35% Опыт (XP)")
    ),
    LUCK(
            "luck", Category.PROFESSIONAL,
            100.0, 20.0, 1800L, "%",
            Attributes.LUCK, AttributeModifier.Operation.ADD_VALUE, -4.0,
            new DividendOption("passive_cr_med", "Пассив: +18 CR/мин"),
            new DividendOption("broker_instinct", "Бонус: -20% Комиссия выкупа")
    ),
    XP_GAIN(
            "xp_gain", Category.PROFESSIONAL,
            100.0, 20.0, 1800L, "%",
            null, null, 0.0,
            new DividendOption("passive_cr_med", "Пассив: +18 CR/мин"),
            new DividendOption("scholar_yield", "Бонус: +30% Выплаты контр.")
    ),
    BLOCK_REACH(
            "block_reach", Category.PROFESSIONAL,
            4.5, 1.5, 2000L, "м",
            Attributes.BLOCK_INTERACTION_RANGE, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, -0.75,
            new DividendOption("passive_cr_high", "Пассив: +25 CR/мин"),
            new DividendOption("combat_reach", "Бонус: +1.2м Дальность боя")
    ),
    POTION_EFFECTIVENESS(
            "potion_effectiveness", Category.PROFESSIONAL,
            100.0, 20.0, 1600L, "%",
            null, null, 0.0,
            new DividendOption("passive_cr_med", "Пассив: +18 CR/мин"),
            new DividendOption("natural_immunity", "Бонус: +4.0 Макс. HP")
    ),
    PROJECTILE_ACCURACY(
            "projectile_accuracy", Category.PROFESSIONAL,
            100.0, 20.0, 1500L, "%",
            null, null, 0.0,
            new DividendOption("passive_cr_low", "Пассив: +12 CR/мин"),
            new DividendOption("brawler_instinct", "Бонус: +25% Урон вблизи")
    );

    public enum Category {
        BODY("category.playermarket.body", "ТЕЛО"),
        MOVEMENT("category.playermarket.movement", "ДВИЖЕНИЕ"),
        COMBAT("category.playermarket.combat", "БОЙ"),
        PROFESSIONAL("category.playermarket.professional", "ПРОФЕССИЯ");

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

        public int getRequiredStage() {
            return switch (this) {
                case BODY -> 1;
                case MOVEMENT -> 2;
                case COMBAT -> 3;
                case PROFESSIONAL -> 4;
            };
        }

        public boolean isUnlockedForStage(int stage) {
            return stage >= getRequiredStage();
        }
    }

    public static double getMaxAllowedFractionForStage(int stage) {
        return switch (stage) {
            case 1 -> 0.25;
            case 2 -> 0.50;
            case 3, 4 -> 0.75;
            default -> 0.80; // Stage 5 and 6: Full liquidation
        };
    }

    public record DividendOption(String id, String summary) {
        public String label() {
            return summary;
        }

        public String desc() {
            return "";
        }
    }

    public String getShortName() {
        return switch (this) {
            case MAX_HEALTH -> "Здоровье";
            case NATURAL_REGENERATION -> "Регенерация";
            case SATURATION_EFFICIENCY -> "Насыщение";
            case HUNGER_EFFICIENCY -> "Сытость";
            case OXYGEN_CAPACITY -> "Воздух";
            case FIRE_RESISTANCE -> "Огнестойкость";
            case FALL_RESISTANCE -> "Падение";
            case MAX_ABSORPTION -> "Поглощение";
            case MOVEMENT_SPEED -> "Скорость";
            case JUMP_STRENGTH -> "Прыжок";
            case SPRINT_EFFICIENCY -> "Спринт";
            case SWIM_SPEED -> "Плавание";
            case STEP_HEIGHT -> "Высота шага";
            case ATTACK_DAMAGE -> "Урон";
            case ATTACK_SPEED -> "Скор. атаки";
            case ARMOR -> "Броня";
            case ARMOR_TOUGHNESS -> "Твёрд. брони";
            case KNOCKBACK_RESISTANCE -> "Стойкость";
            case ENTITY_REACH -> "Дальн. атаки";
            case PROJECTILE_RESISTANCE -> "Защ. стрел";
            case EXPLOSION_RESISTANCE -> "Взрывостойкость";
            case CRITICAL_DAMAGE -> "Крит. урон";
            case KNOCKBACK_POWER -> "Отбрасывание";
            case MINING_SPEED -> "Копание";
            case UNDERWATER_MINING -> "Подвод. копание";
            case LUCK -> "Удача";
            case XP_GAIN -> "Опыт (XP)";
            case BLOCK_REACH -> "Дальн. блоков";
            case POTION_EFFECTIVENESS -> "Зелья";
            case PROJECTILE_ACCURACY -> "Точность";
        };
    }

    public String getDisplayName() {
        return switch (this) {
            case MAX_HEALTH -> "Максимальное здоровье";
            case NATURAL_REGENERATION -> "Естественная регенерация";
            case SATURATION_EFFICIENCY -> "Эффективность насыщения";
            case HUNGER_EFFICIENCY -> "Эффективность питания";
            case OXYGEN_CAPACITY -> "Время под водой";
            case FIRE_RESISTANCE -> "Сопротивление огню";
            case FALL_RESISTANCE -> "Сопротивление падению";
            case MAX_ABSORPTION -> "Максимальное поглощение";
            case MOVEMENT_SPEED -> "Скорость движения";
            case JUMP_STRENGTH -> "Сила прыжка";
            case SPRINT_EFFICIENCY -> "Эффективность спринта";
            case SWIM_SPEED -> "Скорость плавания";
            case STEP_HEIGHT -> "Высота шага";
            case ATTACK_DAMAGE -> "Базовый урон";
            case ATTACK_SPEED -> "Скорость атаки";
            case ARMOR -> "Броня";
            case ARMOR_TOUGHNESS -> "Твёрдость брони";
            case KNOCKBACK_RESISTANCE -> "Сопротивление отбрасыванию";
            case ENTITY_REACH -> "Дальность атаки";
            case PROJECTILE_RESISTANCE -> "Защита от снарядов";
            case EXPLOSION_RESISTANCE -> "Взрывоустойчивость";
            case CRITICAL_DAMAGE -> "Критический урон";
            case KNOCKBACK_POWER -> "Сила отбрасывания";
            case MINING_SPEED -> "Скорость копания";
            case UNDERWATER_MINING -> "Копание под водой";
            case LUCK -> "Удача";
            case XP_GAIN -> "Получение опыта";
            case BLOCK_REACH -> "Дальность взаимодействия";
            case POTION_EFFECTIVENESS -> "Эффективность зелий";
            case PROJECTILE_ACCURACY -> "Точность стрельбы";
        };
    }

    public String getHelpTooltip() {
        return switch (this) {
            case MAX_HEALTH -> "§aМаксимальное здоровье:§r\n"
                    + "§7Базовый запас сердец (20 HP = 10 сердец).\n"
                    + "§cЧто забирается:§7 Уменьшает кол-во сердец (до минимума в 2 сердца).\n"
                    + "§eВыгода:§7 Даёт высокий стартовый капитал CR для прокачки.";
            case NATURAL_REGENERATION -> "§aЕстественная регенерация:§r\n"
                    + "§7Скорость восстановления здоровья при полной сытости.\n"
                    + "§cЧто забирается:§7 Здоровье восстанавливается заметно медленнее.\n"
                    + "§eВыгода:§7 Компенсируется золотыми яблоками и зельями.";
            case SATURATION_EFFICIENCY -> "§aЭффективность насыщения (Saturation):§r\n"
                    + "§7Насыщение — это скрытая сытость от жирной пищи (мясо, стейк).\n"
                    + "§7Пока есть насыщение, видимые окорочка голода вообще не тратятся!\n"
                    + "§cЧто забирается:§7 Еда даёт меньше скрытой сытости. Окорочка начнут\n"
                    + "§7тратиться быстрее после еды, но голод НЕ падает до нуля.";
            case HUNGER_EFFICIENCY -> "§aЭффективность питания (Hunger):§r\n"
                    + "§7Скорость сгорания видимых окорочков голода при беге и прыжках.\n"
                    + "§cЧто забирается:§7 Окорочка голода тратятся быстрее при активности.\n"
                    + "§eБезопасность:§7 Голод никогда не упадёт ниже 3-х окорочков сам по себе.";
            case OXYGEN_CAPACITY -> "§aВремя под водой:§r\n"
                    + "§7Запас воздуха (пузырьков) при погружении в воду.\n"
                    + "§cЧто забирается:§7 Пузырьки воздуха под водой сгорают быстрее.\n"
                    + "§eВыгода:§7 Если вы на суше, это бесплатный источник капитала.";
            case FIRE_RESISTANCE -> "§aСопротивление огню:§r\n"
                    + "§7Время горения в лаве и пламени.\n"
                    + "§cЧто забирается:§7 Персонаж горит дольше и получает больше урона от огня.";
            case FALL_RESISTANCE -> "§aСопротивление падению:§r\n"
                    + "§7Амортизация ударов при приземлении с высоты.\n"
                    + "§cЧто забирается:§7 Безопасная высота падения падает с 3 до 1 блока!\n"
                    + "§7Падение с 2-3 блоков начнёт наносить ощутимый урон.";
            case MAX_ABSORPTION -> "§aМаксимальное поглощение:§r\n"
                    + "§7Золотые сердца от тотемов и золотых яблок.\n"
                    + "§cЧто забирается:§7 Ограничивает кол-во золотых сердец.";
            case MOVEMENT_SPEED -> "§aСкорость движения:§r\n"
                    + "§7Базовая скорость передвижения.\n"
                    + "§cЧто забирается:§7 Персонаж ходит и бегает медленнее.";
            case JUMP_STRENGTH -> "§aСила прыжка:§r\n"
                    + "§7Высота прыжка персонажа.\n"
                    + "§cЧто забирается:§7 Низкий прыжок (тяжелее запрыгивать на блоки).";
            case SPRINT_EFFICIENCY -> "§aЭффективность спринта:§r\n"
                    + "§7Ускорение при двойном нажатии W (бег).\n"
                    + "§cЧто забирается:§7 Снижает бонусную скорость во время спринта.";
            case SWIM_SPEED -> "§aСкорость плавания:§r\n"
                    + "§7Скорость передвижения в воде.\n"
                    + "§cЧто забирается:§7 Замедляет персонажа при плавании.";
            case STEP_HEIGHT -> "§aВысота шага:§r\n"
                    + "§7Автоматический подъём на ступеньки и блоки без прыжка.\n"
                    + "§cЧто забирается:§7 Требует прыгать на каждый блок.";
            case ATTACK_DAMAGE -> "§aБазовый урон:§r\n"
                    + "§7Физический урон рукой и оружием ближнего боя.\n"
                    + "§cЧто забирается:§7 Ваши удары наносят меньше урона.";
            case ATTACK_SPEED -> "§aСкорость атаки:§r\n"
                    + "§7Скорость перезарядки индикатора атаки (кулдаун удара).\n"
                    + "§cЧто забирается:§7 Оружие заряжается дольше перед полным ударом.";
            case ARMOR -> "§aБазовая броня:§r\n"
                    + "§7Внутренняя защита тела от физического урона.\n"
                    + "§cЧто забирается:§7 Вы получаете больше входящего урона.";
            case ARMOR_TOUGHNESS -> "§aТвёрдость брони:§r\n"
                    + "§7Защита от сильных единичных ударов (криты, взрывы, боссы).\n"
                    + "§cЧто забирается:§7 Тяжёлые удары пробивают защиту сильнее.";
            case KNOCKBACK_RESISTANCE -> "§aСопротивление отбрасыванию:§r\n"
                    + "§7Способность стоять на месте при получении удара.\n"
                    + "§cЧто забирается:§7 Вас отбрасывает дальше при ударах мобов.";
            case ENTITY_REACH -> "§aДальность атаки:§r\n"
                    + "§7Дистанция в блоках, на которой вы достаёте мечом до врага.\n"
                    + "§cЧто забирается:§7 Приходится подходить к врагам вплотную.";
            case PROJECTILE_RESISTANCE -> "§aЗащита от снарядов:§r\n"
                    + "§7Сопротивление стрелам скелетов и шарам гастов.\n"
                    + "§cЧто забирается:§7 Стрелы наносят повышенный урон.";
            case EXPLOSION_RESISTANCE -> "§aВзрывоустойчивость:§r\n"
                    + "§7Защита от криперов, динамита и взрывов визера.\n"
                    + "§cЧто забирается:§7 Взрывы наносят больше урона.";
            case CRITICAL_DAMAGE -> "§aКритический урон:§r\n"
                    + "§7Множитель урона при ударе в падении.\n"
                    + "§cЧто забирается:§7 Критические удары наносят меньше бонусного урона.";
            case KNOCKBACK_POWER -> "§aСила отбрасывания:§r\n"
                    + "§7Сила, с которой ваши удары отталкивают врагов.\n"
                    + "§cЧто забирается:§7 Враги не отлетают при ваших ударах, оставаясь вплотную.";
            case MINING_SPEED -> "§aСкорость копания:§r\n"
                    + "§7Скорость разрушения любых блоков инструментами и рукой.\n"
                    + "§cЧто забирается:§7 Блоки ломаются заметно дольше.";
            case UNDERWATER_MINING -> "§aКопание под водой:§r\n"
                    + "§7Скорость разрушения блоков при погружении в воду.\n"
                    + "§cЧто забирается:§7 Штраф к копанию под водой.";
            case LUCK -> "§aУдача:§r\n"
                    + "§7Шанс редкого лута из сундуков и на рыбалке.\n"
                    + "§cЧто забирается:§7 Снижает качество находимых сокровищ.";
            case XP_GAIN -> "§aПолучение опыта (XP):§r\n"
                    + "§7Количество сфер опыта, получаемых за убийство и плавку.\n"
                    + "§cЧто забирается:§7 Уровни опыта набираются медленнее.";
            case BLOCK_REACH -> "§aДальность взаимодействия:§r\n"
                    + "§7Дистанция установки блоков и открытия сундуков/дверей.\n"
                    + "§cЧто забирается:§7 Приходится подходить ближе к сундукам и блокам.";
            case POTION_EFFECTIVENESS -> "§aЭффективность зелий:§r\n"
                    + "§7Длительность положительных эффектов выпитых зелий.\n"
                    + "§cЧто забирается:§7 Зелья силы, скорости и регенерации длятся меньше.";
            case PROJECTILE_ACCURACY -> "§aТочность стрельбы:§r\n"
                    + "§7Кучность и убойная сила стрел из лука и арбалета.\n"
                    + "§cЧто забирается:§7 Стрелы наносят меньше урона на дистанции.";
        };
    }

    private final String id;
    private final Category category;
    private final double baseDisplayValue;
    private final double minDisplayValue;
    private final long fullValueCr;
    private final String unit;
    private final Holder<Attribute> vanillaAttribute;
    private final AttributeModifier.Operation operation;
    private final double modifierScalePer100Percent;
    private final DividendOption dividendA;
    private final DividendOption dividendB;

    AssetType(
            String id,
            Category category,
            double baseDisplayValue,
            double minDisplayValue,
            long fullValueCr,
            String unit,
            Holder<Attribute> vanillaAttribute,
            AttributeModifier.Operation operation,
            double modifierScalePer100Percent,
            DividendOption dividendA,
            DividendOption dividendB
    ) {
        this.id = id;
        this.category = category;
        this.baseDisplayValue = baseDisplayValue;
        this.minDisplayValue = minDisplayValue;
        this.fullValueCr = fullValueCr;
        this.unit = unit;
        this.vanillaAttribute = vanillaAttribute;
        this.operation = operation;
        this.modifierScalePer100Percent = modifierScalePer100Percent;
        this.dividendA = dividendA;
        this.dividendB = dividendB;
    }

    public String getId() {
        return id;
    }

    public Category getCategory() {
        return category;
    }

    public String getTranslationKey() {
        return "asset.playermarket." + id;
    }

    public String getDescriptionKey() {
        return "asset.playermarket." + id + ".desc";
    }

    public double getBaseDisplayValue() {
        return baseDisplayValue;
    }

    public double getMinDisplayValue() {
        return minDisplayValue;
    }

    public double getMaxSellableFraction() {
        double assetLimit = 1.0 - (minDisplayValue / baseDisplayValue);
        return Math.min(assetLimit, MarketConfig.get().maxAttributeSaleFraction);
    }

    public long getFullValueCr() {
        Long custom = MarketConfig.get().customAssetUnitPrices.get(id);
        long base = custom != null ? custom : fullValueCr;
        return Math.max(100L, Math.round(base * MarketConfig.get().assetPriceMultiplier));
    }

    public String getUnit() {
        return unit;
    }

    public Holder<Attribute> getVanillaAttribute() {
        return vanillaAttribute;
    }

    public AttributeModifier.Operation getOperation() {
        return operation;
    }

    public double getModifierScalePer100Percent() {
        return modifierScalePer100Percent;
    }

    public DividendOption getDividendA() {
        return dividendA;
    }

    public DividendOption getDividendB() {
        return dividendB;
    }

    public List<DividendOption> getDividends() {
        return List.of(dividendA, dividendB);
    }

    public double computeRemainingDisplayValue(double totalSoldFraction) {
        return Math.max(minDisplayValue, baseDisplayValue * (1.0 - totalSoldFraction));
    }

    public static Optional<AssetType> byId(String id) {
        return Arrays.stream(values()).filter(a -> a.id.equalsIgnoreCase(id)).findFirst();
    }
}
