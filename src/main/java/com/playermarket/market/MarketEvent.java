package com.playermarket.market;

import java.util.Arrays;
import java.util.Optional;

public enum MarketEvent {
    STABLE_MARKET(
            "stable_market",
            "РАВНОВЕСИЕ РЫНКА",
            "Стандартная ликвидность и базовые комиссии во всех секторах биржи.",
            1.0, 1.0, 1.0, 1.0, 1.0, 1.0, false
    ),
    HEALTH_DEMAND(
            "health_demand",
            "ДЕФИЦИТ БИОМАТЕРИАЛА",
            "Биотех-корпорации скупают здоровье и выносливость. Выплаты за ТЕЛО +35%.",
            1.35, 1.0, 1.0, 1.0, 1.0, 1.0, false
    ),
    SPEED_SHORTAGE(
            "speed_shortage",
            "КИНЕТИЧЕСКИЙ БУМ",
            "Логистическим сетям требуются курьеры. Выплаты за ДВИЖЕНИЕ +40%.",
            1.0, 1.40, 1.0, 1.0, 1.0, 1.0, false
    ),
    DIAMOND_DEMAND(
            "diamond_demand",
            "АЛМАЗНОЕ РАЛЛИ",
            "Дефицит кристаллического углерода. Курс алмазов +35%.",
            1.0, 1.0, 1.0, 1.0, 1.35, 1.0, false
    ),
    RESOURCE_OVERSUPPLY(
            "resource_oversupply",
            "ПЕРЕИЗБЫТОК СЫРЬЯ",
            "Склады переполнены. Сдача ресурсов -25%, но покупка из каталога на 25% дешевле.",
            1.0, 1.0, 0.75, 1.0, 1.0, 1.0, true
    ),
    MARKET_PANIC(
            "market_panic",
            "ПАНИКА НА БИРЖЕ",
            "Обвал котировок! Продажа статов -20%, выкуп +20% дороже, но контракты платят +35%.",
            0.80, 0.80, 0.85, 1.20, 1.15, 1.35, true
    ),
    DIVIDEND_DAY(
            "dividend_day",
            "ДЕНЬ ДИВИДЕНДОВ",
            "Квартальные выплаты акционерам! Продажа любых статов +25% CR, контракты +15%.",
            1.25, 1.25, 1.10, 1.0, 1.0, 1.15, false
    ),
    CORPORATE_AUDIT(
            "corporate_audit",
            "КОРПОРАТИВНЫЙ АУДИТ",
            "Строгая проверка счетов. Комиссия выкупа +20%, но улучшения за алмазы на 15% дешевле.",
            0.95, 0.95, 1.0, 1.20, 0.85, 1.0, true
    ),
    PREMIUM_WEEK(
            "premium_week",
            "ПРЕМИАЛЬНАЯ НЕДЕЛЯ",
            "Саммит директоров! Все выплаты в CR +20%, выкуп на 10% дешевле, улучшения -15%.",
            1.20, 1.20, 1.20, 0.90, 0.85, 1.20, false
    ),
    BUYBACK_DISCOUNT(
            "buyback_discount",
            "АМНИСТИЯ ВЫКУПА",
            "Центробанк снижает неустойку! Обратный выкуп всех характеристик на 20% дешевле.",
            1.0, 1.0, 1.0, 0.80, 1.0, 1.0, false
    ),
    MINING_BOOM(
            "mining_boom",
            "ПРОМЫШЛЕННЫЙ БУМ",
            "Тяжёлая индустрия наращивает мощности! Сдача ресурсов +30% и выплаты за ПРОФЕССИЮ +30%.",
            1.15, 1.0, 1.30, 1.0, 1.0, 1.25, false
    ),
    MARKET_CRASH(
            "market_crash",
            "КРАХ БИРЖИ: ОБВАЛ КОТИРОВОК",
            "Глобальный обвал цен! Продажа статов -35%, комиссии выкупа +30%, но антикризисные контракты платят +60% CR!",
            0.65, 0.65, 0.70, 1.30, 1.25, 1.60, true
    ),
    BULL_MARKET(
            "bull_market",
            "БЫЧИЙ ТРЕНД: АЖИОТАЖНЫЙ РОСТ",
            "Исторический максимум биржи! Все выплаты за характеристики +40%, сдача сырья +35%, контракты +30%!",
            1.40, 1.40, 1.35, 1.0, 1.20, 1.30, false
    ),
    TECH_BREAKTHROUGH(
            "tech_breakthrough",
            "ТЕХНОЛОГИЧЕСКИЙ ПРОРЫВ",
            "Революция биоинженерии! Скидка на улучшения в Магазине 30%, обратный выкуп на 25% дешевле!",
            1.10, 1.10, 1.10, 0.75, 0.70, 1.20, false
    ),
    DEBT_CRISIS(
            "debt_crisis",
            "ДОЛГОВОЙ КРИЗИС И ДЕФОЛТ",
            "Кризис ликвидности! Ставки по вкладам удвоены, выкуп статов субсидируется биржей (-30%), но ресурсы дешевеют (-20%).",
            0.90, 0.90, 0.80, 0.70, 1.15, 1.25, true
    );

    private final String id;
    private final String headline;
    private final String description;
    private final double bodyAssetMultiplier;
    private final double movementAssetMultiplier;
    private final double resourcePriceMultiplier;
    private final double buybackCostMultiplier;
    private final double diamondValueMultiplier;
    private final double contractRewardMultiplier;
    private final boolean negativeEvent;

    MarketEvent(
            String id,
            String headline,
            String description,
            double bodyAssetMultiplier,
            double movementAssetMultiplier,
            double resourcePriceMultiplier,
            double buybackCostMultiplier,
            double diamondValueMultiplier,
            double contractRewardMultiplier,
            boolean negativeEvent
    ) {
        this.id = id;
        this.headline = headline;
        this.description = description;
        this.bodyAssetMultiplier = bodyAssetMultiplier;
        this.movementAssetMultiplier = movementAssetMultiplier;
        this.resourcePriceMultiplier = resourcePriceMultiplier;
        this.buybackCostMultiplier = buybackCostMultiplier;
        this.diamondValueMultiplier = diamondValueMultiplier;
        this.contractRewardMultiplier = contractRewardMultiplier;
        this.negativeEvent = negativeEvent;
    }

    public String getId() {
        return id;
    }

    public String getTranslationKey() {
        return "event.playermarket." + id;
    }

    public String getDescriptionKey() {
        return "event.playermarket." + id + ".desc";
    }

    public String getHeadline() {
        return headline;
    }

    public String getDescription() {
        return description;
    }

    public double getAssetMultiplier(AssetType type) {
        return switch (type.getCategory()) {
            case BODY -> bodyAssetMultiplier;
            case MOVEMENT -> movementAssetMultiplier;
            case COMBAT -> (bodyAssetMultiplier + movementAssetMultiplier) * 0.5;
            case PROFESSIONAL -> (this == MINING_BOOM) ? 1.30 : bodyAssetMultiplier;
        };
    }

    public double getBodyMultiplier() {
        return bodyAssetMultiplier;
    }

    public double getMovementMultiplier() {
        return movementAssetMultiplier;
    }

    public double getResourceBuyMultiplier() {
        return resourcePriceMultiplier;
    }

    public double getResourcePriceMultiplier() {
        return resourcePriceMultiplier;
    }

    public double getBuybackCostMultiplier() {
        return buybackCostMultiplier;
    }

    public double getDiamondValueMultiplier() {
        return diamondValueMultiplier;
    }

    public double getContractRewardMultiplier() {
        return contractRewardMultiplier;
    }

    public boolean isNegativeEvent() {
        return negativeEvent;
    }

    public static Optional<MarketEvent> byId(String id) {
        return Arrays.stream(values()).filter(e -> e.id.equalsIgnoreCase(id)).findFirst();
    }
}
