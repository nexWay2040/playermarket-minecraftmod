package com.playermarket.market;

import java.util.Arrays;
import java.util.Optional;

public enum BlackMarketItem {
    SECOND_CHANCE(
            "second_chance",
            1600L, 6,
            0, 12000, // Заряд до срабатывания, откат 10 мин
            "Спасает от смертельного удара (оставляет 2 HP + Регенерация и Поглощение)"
    ),
    BERSERKER_PROTOCOL(
            "berserker_protocol",
            900L, 4,
            1200, 3600, // 60 сек, откат 3 мин
            "+40% к урону и +20% к скор. атаки, но -30% брони на 60 сек."
    ),
    PHANTOM_STEP(
            "phantom_step",
            650L, 3,
            400, 1800, // 20 сек, откат 90 сек
            "+45% к скорости, шаг +0.5м и защита от падения на 20 сек."
    ),
    SYNTHETIC_HEART(
            "synthetic_heart",
            1250L, 5,
            3600, 4800, // 3 мин, откат 4 мин
            "Восстанавливает 4 HP и +2 поглощения за каждое убийство (3 мин.)"
    ),
    OVERCLOCKED_MIND(
            "overclocked_mind",
            800L, 3,
            2400, 4800, // 2 мин, откат 4 мин
            "+100% к опыту (XP) и +35% к скорости копания на 2 мин."
    ),
    EMERGENCY_BATTERY(
            "emergency_battery",
            1200L, 5,
            1200, 3600, // 60 сек, откат 3 мин
            "Отключает штрафы от проданных статов и даёт +15% ко всем статам на 60 сек."
    ),
    GOLDEN_CONTRACT(
            "golden_contract",
            1000L, 4,
            0, 2400, // На следующую сделку, откат 2 мин
            "Удваивает (x2) выплату CR за следующую продажу стата, ресурса или контракт"
    ),
    ASSET_INSURANCE(
            "asset_insurance",
            1100L, 4,
            6000, 7200, // 5 мин, откат 6 мин
            "Снижает штрафы проданных характеристик и лизинга на 50% на 5 мин."
    );

    private final String id;
    private final long costCr;
    private final int costDiamonds;
    private final int durationTicks;
    private final int cooldownTicks;
    private final String summary;

    BlackMarketItem(String id, long costCr, int costDiamonds, int durationTicks, int cooldownTicks, String summary) {
        this.id = id;
        this.costCr = costCr;
        this.costDiamonds = costDiamonds;
        this.durationTicks = durationTicks;
        this.cooldownTicks = cooldownTicks;
        this.summary = summary;
    }

    public String getId() {
        return id;
    }

    public String getTranslationKey() {
        return "blackmarket.playermarket." + id;
    }

    public String getDescriptionKey() {
        return "blackmarket.playermarket." + id + ".desc";
    }

    public long getCostCr() {
        return costCr;
    }

    public int getCostDiamonds() {
        return costDiamonds;
    }

    public int getDurationTicks() {
        return durationTicks;
    }

    public int getCooldownTicks() {
        return cooldownTicks;
    }

    public String getSummary() {
        return summary;
    }

    public String getTitle() {
        return switch (this) {
            case SECOND_CHANCE -> "Второй Шанс";
            case BERSERKER_PROTOCOL -> "Протокол «Берсерк»";
            case PHANTOM_STEP -> "Фантомный Шаг";
            case SYNTHETIC_HEART -> "Синтетическое Сердце";
            case OVERCLOCKED_MIND -> "Разогнанный Разум";
            case EMERGENCY_BATTERY -> "Аварийная Батарея";
            case GOLDEN_CONTRACT -> "Золотой Контракт";
            case ASSET_INSURANCE -> "Страховка Активов";
        };
    }

    public static Optional<BlackMarketItem> byId(String id) {
        return Arrays.stream(values()).filter(b -> b.id.equalsIgnoreCase(id)).findFirst();
    }
}
