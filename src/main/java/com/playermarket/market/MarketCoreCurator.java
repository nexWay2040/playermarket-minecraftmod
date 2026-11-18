package com.playermarket.market;

import net.minecraft.util.RandomSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * Единый куратор экономики и UI для PLAYER MARKET (Rework 2.0).
 * <p>
 * Ключевые правила Rework 2.0:
 * <ul>
 *     <li>Все 30 характеристик {@code AssetType} доступны к продаже и упорядочены по 5 категориям.</li>
 *     <li>Вкладка {@code CONTRACTS} открыта с самого старта.</li>
 *     <li>Лизинг включён, дивиденды и скример-кик концовка отключены.</li>
 *     <li>14 ключевых апгрейдов, 5 предметов Чёрного Рынка и 5 рыночных событий.</li>
 * </ul>
 */
public final class MarketCoreCurator {

    private static final Logger LOGGER = LoggerFactory.getLogger("PlayerMarket/Curator");

    public static final int MAX_CURATED_MARKET_EVENTS = 5;

    /**
     * 7 видимых вкладок старта (BUY_BACK MarketMainScreen подставляет сам при наличии проданных статов).
     */
    private static final List<String> CURATED_TAB_IDS = List.of(
            "DASHBOARD",
            "SELL_ASSETS",
            "CONTRACTS",
            "RESOURCE_MARKET",
            "UPGRADE_STORE",
            "BLACK_MARKET",
            "STORY"
    );

    private static final Map<String, String> TAB_DISPLAY_NAMES = Map.of(
            "DASHBOARD", "Терминал",
            "SELL_ASSETS", "Биржа Тела",
            "BUY_BACK", "Выкуп",
            "CONTRACTS", "Контракты",
            "RESOURCE_MARKET", "Биржа Ресурсов",
            "UPGRADE_STORE", "Импланты",
            "BLACK_MARKET", "Чёрный Рынок",
            "STORY", "Протокол 17.4"
    );

    /**
     * Все 30 характеристик тела, сгруппированные по 5 понятным категориям:
     * VITALITY (5), MOBILITY (5), COMBAT (9), SURVIVAL (6), WORK & EXPLORATION (5).
     */
    private static final List<String> CURATED_ASSET_IDS = List.of(
            // 1. VITALITY
            "MAX_HEALTH",
            "MAX_ABSORPTION",
            "NATURAL_REGENERATION",
            "SATURATION_EFFICIENCY",
            "HUNGER_EFFICIENCY",
            // 2. MOBILITY
            "MOVEMENT_SPEED",
            "SPRINT_EFFICIENCY",
            "JUMP_STRENGTH",
            "SWIM_SPEED",
            "STEP_HEIGHT",
            // 3. COMBAT
            "ATTACK_DAMAGE",
            "ATTACK_SPEED",
            "CRITICAL_DAMAGE",
            "KNOCKBACK_POWER",
            "PROJECTILE_ACCURACY",
            "ENTITY_REACH",
            "ARMOR",
            "ARMOR_TOUGHNESS",
            "KNOCKBACK_RESISTANCE",
            // 4. SURVIVAL
            "FIRE_RESISTANCE",
            "FALL_RESISTANCE",
            "PROJECTILE_RESISTANCE",
            "EXPLOSION_RESISTANCE",
            "OXYGEN_CAPACITY",
            "POTION_EFFECTIVENESS",
            // 5. WORK & EXPLORATION
            "MINING_SPEED",
            "UNDERWATER_MINING",
            "BLOCK_REACH",
            "XP_GAIN",
            "LUCK"
    );

    private static final Map<String, AssetCategory> ASSET_CATEGORIES = buildAssetCategories();
    private static final Map<String, AssetPitch> ASSET_PITCHES = buildAssetPitches();

    /**
     * 14 основных апгрейдов Rework 2.0.
     */
    private static final List<String> CURATED_UPGRADE_IDS = List.of(
            "REINFORCED_HEART",
            "RAPID_RECOVERY",
            "REINFORCED_SKIN",
            "ENHANCED_LUNGS",
            "MUSCLE_OVERCLOCK",
            "FAST_REFLEXES",
            "STRONG_LEGS",
            "REINFORCED_BONES",
            "MINING_PROTOCOL",
            "ORE_SCANNER",
            "LUCKY_CHIP",
            "XP_ACCELERATOR",
            "BROKER_LICENSE",
            "DIAMOND_CASHBACK"
    );

    private static final Map<String, String> UPGRADE_DESCRIPTIONS = buildUpgradeDescriptions();

    /**
     * 5 предметов Чёрного Рынка Rework 2.0.
     */
    private static final List<String> CURATED_BLACK_MARKET_IDS = List.of(
            "SECOND_CHANCE",
            "BERSERKER_PROTOCOL",
            "PHANTOM_STEP",
            "EMERGENCY_BATTERY",
            "GOLDEN_CONTRACT"
    );

    private static final Map<String, String> BLACK_MARKET_DESCRIPTIONS = buildBlackMarketDescriptions();

    /**
     * 5 курируемых рыночных событий.
     */
    private static final Set<String> CURATED_MARKET_EVENT_IDS = new LinkedHashSet<>(List.of(
            "ORGAN_BOOM",
            "BUYBACK_CRASH",
            "RESOURCE_RUSH",
            "BLACK_FRIDAY",
            "AUDIT_SURGE"
    ));

    private static boolean loggedMissingEventWarning = false;

    private MarketCoreCurator() {
    }

    // -------------------------------------------------------------------------
    // Переключатели фич Rework 2.0
    // -------------------------------------------------------------------------

    public static boolean isLeasingEnabled() {
        return true;
    }

    public static boolean areDividendsEnabled() {
        return false;
    }

    public static boolean isMailTabEnabled() {
        return false;
    }

    public static boolean isLedgerTabEnabled() {
        return false;
    }

    public static boolean isContractsTabEnabled() {
        return true;
    }

    public static boolean isScreamerFreezeEndingEnabled() {
        return false;
    }

    // -------------------------------------------------------------------------
    // Вкладки GUI (MarketMainScreen)
    // -------------------------------------------------------------------------

    public static List<String> getCuratedTabIds() {
        return CURATED_TAB_IDS;
    }

    public static boolean isTabVisible(String tabId) {
        if (tabId == null || tabId.isBlank()) {
            return false;
        }
        return CURATED_TAB_IDS.contains(normalizeId(tabId));
    }

    public static boolean isTabVisible(Enum<?> tab) {
        return tab != null && isTabVisible(tab.name());
    }

    public static List<String> filterVisibleTabIds(Collection<String> rawTabIds) {
        if (rawTabIds == null || rawTabIds.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, String> availableByNormalized = new LinkedHashMap<>();
        for (String raw : rawTabIds) {
            if (raw != null && !raw.isBlank()) {
                availableByNormalized.putIfAbsent(normalizeId(raw), raw);
            }
        }
        List<String> result = new ArrayList<>(CURATED_TAB_IDS.size());
        for (String curatedId : CURATED_TAB_IDS) {
            String original = availableByNormalized.get(curatedId);
            if (original != null) {
                result.add(original);
            }
        }
        return result;
    }

    public static <T extends Enum<T>> List<T> filterVisibleTabs(T[] allTabs) {
        return filterOrderedStrict(allTabs, CURATED_TAB_IDS);
    }

    public static String getTabDisplayName(String tabId) {
        if (tabId == null) {
            return "";
        }
        return TAB_DISPLAY_NAMES.getOrDefault(normalizeId(tabId), tabId);
    }

    // -------------------------------------------------------------------------
    // Все 30 характеристик (AssetType) в порядке 5 категорий
    // -------------------------------------------------------------------------

    public static List<String> getCuratedAssetIds() {
        return CURATED_ASSET_IDS;
    }

    public static boolean isAssetCurated(String assetId) {
        return assetId != null && !assetId.isBlank();
    }

    public static boolean isAssetCurated(Enum<?> asset) {
        return asset != null;
    }

    public static boolean canSellAsset(String assetId) {
        return isAssetCurated(assetId);
    }

    public static boolean canSellAsset(Enum<?> asset) {
        return isAssetCurated(asset);
    }

    public static boolean canBuyBackAsset(String assetId, int soldUnits) {
        return isAssetCurated(assetId) || soldUnits > 0;
    }

    public static boolean canBuyBackAsset(Enum<?> asset, int soldUnits) {
        return asset != null && (isAssetCurated(asset) || soldUnits > 0);
    }

    /**
     * Возвращает ВСЕ 30 характеристик AssetType, упорядоченные по 5 категориям:
     * VITALITY, MOBILITY, COMBAT, SURVIVAL, WORK & EXPLORATION.
     * Любые дополнительные значения enum (если появятся) безопасно добавляются в конец.
     */
    public static <T extends Enum<T>> List<T> filterCuratedAssets(T[] allAssets) {
        if (allAssets == null || allAssets.length == 0) {
            return Collections.emptyList();
        }
        List<T> ordered = new ArrayList<>(allAssets.length);
        Set<T> added = new LinkedHashSet<>();

        Map<String, T> byName = new LinkedHashMap<>(allAssets.length);
        for (T asset : allAssets) {
            if (asset != null) {
                byName.put(normalizeId(asset.name()), asset);
            }
        }

        for (String id : CURATED_ASSET_IDS) {
            T found = byName.get(id);
            if (found != null && added.add(found)) {
                ordered.add(found);
            }
        }
        for (T asset : allAssets) {
            if (asset != null && added.add(asset)) {
                ordered.add(asset);
            }
        }
        return ordered;
    }

    public static <T extends Enum<T>> List<T> filterDisplayableAssets(T[] allAssets, ToIntFunction<T> soldUnitsLookup) {
        return filterCuratedAssets(allAssets);
    }

    public static AssetCategory getAssetCategory(String assetId) {
        if (assetId == null) {
            return AssetCategory.WORK_AND_EXPLORATION;
        }
        return ASSET_CATEGORIES.getOrDefault(normalizeId(assetId), AssetCategory.WORK_AND_EXPLORATION);
    }

    public static AssetCategory getAssetCategory(Enum<?> asset) {
        return asset == null ? AssetCategory.WORK_AND_EXPLORATION : getAssetCategory(asset.name());
    }

    public static AssetPitch getAssetPitch(String assetId) {
        if (assetId == null) {
            return AssetPitch.LEGACY_FALLBACK;
        }
        return ASSET_PITCHES.getOrDefault(normalizeId(assetId), AssetPitch.LEGACY_FALLBACK);
    }

    public static AssetPitch getAssetPitch(Enum<?> asset) {
        return asset == null ? AssetPitch.LEGACY_FALLBACK : getAssetPitch(asset.name());
    }

    public static String getAssetShortDescription(String assetId) {
        return getAssetPitch(assetId).whySell();
    }

    public static String getAssetShortDescription(Enum<?> asset) {
        return getAssetPitch(asset).whySell();
    }

    // -------------------------------------------------------------------------
    // Магазин апгрейдов (14 курируемых позиций UpgradeType)
    // -------------------------------------------------------------------------

    public static List<String> getCuratedUpgradeIds() {
        return CURATED_UPGRADE_IDS;
    }

    public static boolean isUpgradeCurated(String upgradeId) {
        return upgradeId != null && CURATED_UPGRADE_IDS.contains(normalizeId(upgradeId));
    }

    public static boolean isUpgradeCurated(Enum<?> upgrade) {
        return upgrade != null && isUpgradeCurated(upgrade.name());
    }

    public static <T extends Enum<T>> List<T> filterCuratedUpgrades(T[] allUpgrades) {
        return filterOrderedWithFallback(allUpgrades, CURATED_UPGRADE_IDS);
    }

    public static String getUpgradeShortDescription(String upgradeId) {
        if (upgradeId == null) {
            return "";
        }
        return UPGRADE_DESCRIPTIONS.getOrDefault(
                normalizeId(upgradeId),
                "Корпоративный биоимплант"
        );
    }

    public static String getUpgradeShortDescription(Enum<?> upgrade) {
        return upgrade == null ? "" : getUpgradeShortDescription(upgrade.name());
    }

    // -------------------------------------------------------------------------
    // Чёрный рынок (5 курируемых предметов BlackMarketItem)
    // -------------------------------------------------------------------------

    public static List<String> getCuratedBlackMarketIds() {
        return CURATED_BLACK_MARKET_IDS;
    }

    public static boolean isBlackMarketItemCurated(String itemId) {
        return itemId != null && CURATED_BLACK_MARKET_IDS.contains(normalizeId(itemId));
    }

    public static boolean isBlackMarketItemCurated(Enum<?> item) {
        return item != null && isBlackMarketItemCurated(item.name());
    }

    public static <T extends Enum<T>> List<T> filterCuratedBlackMarketItems(T[] allItems) {
        return filterOrderedWithFallback(allItems, CURATED_BLACK_MARKET_IDS);
    }

    public static String getBlackMarketShortDescription(String itemId) {
        if (itemId == null) {
            return "";
        }
        return BLACK_MARKET_DESCRIPTIONS.getOrDefault(
                normalizeId(itemId),
                "Нелегальный контрабандный протокол"
        );
    }

    public static String getBlackMarketShortDescription(Enum<?> item) {
        return item == null ? "" : getBlackMarketShortDescription(item.name());
    }

    // -------------------------------------------------------------------------
    // Рыночные события (5 курируемых MarketEvent)
    // -------------------------------------------------------------------------

    public static void configureMarketEventIds(Collection<String> exactEnumNames) {
        if (exactEnumNames == null || exactEnumNames.isEmpty()) {
            return;
        }
        CURATED_MARKET_EVENT_IDS.clear();
        for (String name : exactEnumNames) {
            if (name != null && !name.isBlank()) {
                CURATED_MARKET_EVENT_IDS.add(normalizeId(name));
                if (CURATED_MARKET_EVENT_IDS.size() >= MAX_CURATED_MARKET_EVENTS) {
                    break;
                }
            }
        }
        loggedMissingEventWarning = false;
    }

    public static Set<String> getCuratedMarketEventIds() {
        return Collections.unmodifiableSet(CURATED_MARKET_EVENT_IDS);
    }

    public static boolean isMarketEventCurated(String eventId) {
        return eventId != null && CURATED_MARKET_EVENT_IDS.contains(normalizeId(eventId));
    }

    public static boolean isMarketEventCurated(Enum<?> event) {
        return event != null && isMarketEventCurated(event.name());
    }

    public static <T extends Enum<T>> List<T> filterCuratedMarketEvents(T[] allEvents) {
        if (allEvents == null || allEvents.length == 0) {
            return Collections.emptyList();
        }
        List<T> matched = new ArrayList<>(MAX_CURATED_MARKET_EVENTS);
        for (T event : allEvents) {
            if (event != null && isMarketEventCurated(event.name())) {
                matched.add(event);
                if (matched.size() >= MAX_CURATED_MARKET_EVENTS) {
                    return matched;
                }
            }
        }
        if (!matched.isEmpty()) {
            return matched;
        }

        if (!loggedMissingEventWarning) {
            loggedMissingEventWarning = true;
            LOGGER.warn(
                    "Ни одно имя из CURATED_MARKET_EVENT_IDS {} не совпало с MarketEvent. Используется срез из первых {} событий.",
                    CURATED_MARKET_EVENT_IDS, MAX_CURATED_MARKET_EVENTS
            );
        }

        List<T> fallback = new ArrayList<>(MAX_CURATED_MARKET_EVENTS);
        for (T event : allEvents) {
            if (event == null) {
                continue;
            }
            String upper = normalizeId(event.name());
            if ("NONE".equals(upper) || "NORMAL".equals(upper) || "CALM".equals(upper)) {
                continue;
            }
            fallback.add(event);
            if (fallback.size() >= MAX_CURATED_MARKET_EVENTS) {
                break;
            }
        }
        if (fallback.isEmpty()) {
            fallback.addAll(Arrays.stream(allEvents).filter(Objects::nonNull).limit(MAX_CURATED_MARKET_EVENTS).toList());
        }
        return fallback;
    }

    public static <T extends Enum<T>> T pickRandomCuratedMarketEvent(T[] allEvents, RandomSource random, T currentEvent) {
        List<T> pool = filterCuratedMarketEvents(allEvents);
        if (pool.isEmpty()) {
            return currentEvent;
        }
        if (pool.size() == 1) {
            return pool.getFirst();
        }
        List<T> candidates = new ArrayList<>(pool.size());
        for (T candidate : pool) {
            if (candidate != currentEvent) {
                candidates.add(candidate);
            }
        }
        if (candidates.isEmpty()) {
            return pool.get(random.nextInt(pool.size()));
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    // -------------------------------------------------------------------------
    // Вспомогательная фильтрация и справочники
    // -------------------------------------------------------------------------

    private static <T extends Enum<T>> List<T> filterOrderedStrict(T[] values, List<String> curatedOrder) {
        if (values == null || values.length == 0) {
            return Collections.emptyList();
        }
        Map<String, T> byName = buildByNameMap(values);
        List<T> ordered = new ArrayList<>(curatedOrder.size());
        for (String id : curatedOrder) {
            T found = byName.get(id);
            if (found != null) {
                ordered.add(found);
            }
        }
        return ordered;
    }

    private static <T extends Enum<T>> List<T> filterOrderedWithFallback(T[] values, List<String> curatedOrder) {
        if (values == null || values.length == 0) {
            return Collections.emptyList();
        }
        Map<String, T> byName = buildByNameMap(values);
        List<T> ordered = new ArrayList<>(curatedOrder.size());
        for (String id : curatedOrder) {
            T found = byName.get(id);
            if (found != null) {
                ordered.add(found);
            }
        }
        if (ordered.isEmpty()) {
            for (T value : values) {
                if (value != null) {
                    ordered.add(value);
                    if (ordered.size() >= curatedOrder.size()) {
                        break;
                    }
                }
            }
        }
        return ordered;
    }

    private static <T extends Enum<T>> Map<String, T> buildByNameMap(T[] values) {
        Map<String, T> byName = new LinkedHashMap<>(values.length);
        for (T value : values) {
            if (value != null) {
                byName.put(normalizeId(value.name()), value);
            }
        }
        return byName;
    }

    private static String normalizeId(String id) {
        return id.trim().toUpperCase(Locale.ROOT);
    }

    private static Map<String, AssetCategory> buildAssetCategories() {
        Map<String, AssetCategory> map = new LinkedHashMap<>();
        // VITALITY
        map.put("MAX_HEALTH", AssetCategory.VITALITY);
        map.put("MAX_ABSORPTION", AssetCategory.VITALITY);
        map.put("NATURAL_REGENERATION", AssetCategory.VITALITY);
        map.put("SATURATION_EFFICIENCY", AssetCategory.VITALITY);
        map.put("HUNGER_EFFICIENCY", AssetCategory.VITALITY);
        // MOBILITY
        map.put("MOVEMENT_SPEED", AssetCategory.MOBILITY);
        map.put("SPRINT_EFFICIENCY", AssetCategory.MOBILITY);
        map.put("JUMP_STRENGTH", AssetCategory.MOBILITY);
        map.put("SWIM_SPEED", AssetCategory.MOBILITY);
        map.put("STEP_HEIGHT", AssetCategory.MOBILITY);
        // COMBAT
        map.put("ATTACK_DAMAGE", AssetCategory.COMBAT);
        map.put("ATTACK_SPEED", AssetCategory.COMBAT);
        map.put("CRITICAL_DAMAGE", AssetCategory.COMBAT);
        map.put("KNOCKBACK_POWER", AssetCategory.COMBAT);
        map.put("PROJECTILE_ACCURACY", AssetCategory.COMBAT);
        map.put("ENTITY_REACH", AssetCategory.COMBAT);
        map.put("ARMOR", AssetCategory.COMBAT);
        map.put("ARMOR_TOUGHNESS", AssetCategory.COMBAT);
        map.put("KNOCKBACK_RESISTANCE", AssetCategory.COMBAT);
        // SURVIVAL
        map.put("FIRE_RESISTANCE", AssetCategory.SURVIVAL);
        map.put("FALL_RESISTANCE", AssetCategory.SURVIVAL);
        map.put("PROJECTILE_RESISTANCE", AssetCategory.SURVIVAL);
        map.put("EXPLOSION_RESISTANCE", AssetCategory.SURVIVAL);
        map.put("OXYGEN_CAPACITY", AssetCategory.SURVIVAL);
        map.put("POTION_EFFECTIVENESS", AssetCategory.SURVIVAL);
        // WORK & EXPLORATION
        map.put("MINING_SPEED", AssetCategory.WORK_AND_EXPLORATION);
        map.put("UNDERWATER_MINING", AssetCategory.WORK_AND_EXPLORATION);
        map.put("BLOCK_REACH", AssetCategory.WORK_AND_EXPLORATION);
        map.put("XP_GAIN", AssetCategory.WORK_AND_EXPLORATION);
        map.put("LUCK", AssetCategory.WORK_AND_EXPLORATION);
        return Collections.unmodifiableMap(map);
    }

    private static Map<String, AssetPitch> buildAssetPitches() {
        Map<String, AssetPitch> map = new LinkedHashMap<>();
        // 1. VITALITY
        map.put("MAX_HEALTH", new AssetPitch("Сердце", "Крупная выплата CR за запас сердец — классический старт.", "-Макс. HP"));
        map.put("MAX_ABSORPTION", new AssetPitch("Щитовые железы", "Уменьшает предел золотых сердец поглощения.", "-Макс. Absorption"));
        map.put("NATURAL_REGENERATION", new AssetPitch("Печень", "Отключает пассивный реген от еды: живи на яблоках и зельях.", "-Автореген"));
        map.put("SATURATION_EFFICIENCY", new AssetPitch("Метаболизм", "Еда хуже держит сытость — бери больше провианта.", "-Насыщение"));
        map.put("HUNGER_EFFICIENCY", new AssetPitch("Желудок", "Голод тратится быстрее при спринте и добыче.", "+Расход голода"));

        // 2. MOBILITY
        map.put("MOVEMENT_SPEED", new AssetPitch("Сухожилия", "Лёгкие CR на базе, но сложнее убегать от коллекторов.", "-Скорость бега"));
        map.put("SPRINT_EFFICIENCY", new AssetPitch("Выносливость", "Снижает рывок при беге — компенсируется Крюком-Кошкой.", "-Спринт"));
        map.put("JUMP_STRENGTH", new AssetPitch("Икроножные", "Ниже прыжок — ставь полублоки или цепляйся Крюком-Кошкой.", "-Прыжок"));
        map.put("SWIM_SPEED", new AssetPitch("Плавниковые мышцы", "Медленнее плавание: безвредный куш CR на суше.", "-Скорость в воде"));
        map.put("STEP_HEIGHT", new AssetPitch("Коленные суставы", "Снижает высоту авто-подъёма на уступы.", "-Шаг"));

        // 3. COMBAT
        map.put("ATTACK_DAMAGE", new AssetPitch("Мускулатура", "Слей базовый урон в шахте ради квоты или казино.", "-Урон вблизи"));
        map.put("ATTACK_SPEED", new AssetPitch("Нервные рефлексы", "Удары чуть реже, зато моментально закрывает квоту.", "-Скорость атаки"));
        map.put("CRITICAL_DAMAGE", new AssetPitch("Ударный импульс", "Слабее криты в прыжке — выкупай перед Титаном.", "-Крит. урон"));
        map.put("KNOCKBACK_POWER", new AssetPitch("Толчковые связки", "Меньше откидывание врагов от твоих атак.", "-Откидывание"));
        map.put("PROJECTILE_ACCURACY", new AssetPitch("Глазомер", "Больше разброс луков и арбалетов: играй через ближний бой.", "-Точность"));
        map.put("ENTITY_REACH", new AssetPitch("Размах рук", "Укорачивает дистанцию удара по мобам.", "-Дальность удара"));
        map.put("ARMOR", new AssetPitch("Панцирь", "Продай кожу для быстрого старта до крафта алмазной брони.", "-Броня"));
        map.put("ARMOR_TOUGHNESS", new AssetPitch("Пластины дермы", "Сложнее танковать тяжёлые удары силовиков и босса.", "-Твёрдость"));
        map.put("KNOCKBACK_RESISTANCE", new AssetPitch("Стабилизатор веса", "Атаки босса сильнее отбрасывают назад.", "-Антиотброс"));

        // 4. SURVIVAL
        map.put("FIRE_RESISTANCE", new AssetPitch("Терможелезы", "Бесплатные CR в обычном мире вдали от лавы и Незера.", "+Урон огнём"));
        map.put("FALL_RESISTANCE", new AssetPitch("Плотность костей", "Больно падать: перед Skyfall босса выкупи или держи крюк!", "+Урон падения"));
        map.put("PROJECTILE_RESISTANCE", new AssetPitch("Кевлар кожи", "Выстрелы Pillager-ов и скелетов пробивают больнее.", "+Урон стрел"));
        map.put("EXPLOSION_RESISTANCE", new AssetPitch("Амортизация", "Криперы и взрывы бьют ощутимее — следи за спиной.", "+Урон взрыва"));
        map.put("OXYGEN_CAPACITY", new AssetPitch("Лёгкие", "Идеальная продажа, если не ныряешь в океанические храмы.", "-Кислород"));
        map.put("POTION_EFFECTIVENESS", new AssetPitch("Кровоток", "Зелья действуют слабее и короче — для хардкорщиков.", "-Эффекты зелий"));

        // 5. WORK & EXPLORATION
        map.put("MINING_SPEED", new AssetPitch("Моторика кистей", "Медленнее копаешь блоки — выгодно слить перед финальным боем.", "-Скорость копания"));
        map.put("UNDERWATER_MINING", new AssetPitch("Аква-моторика", "Почти безболезненно продаётся для игры на поверхности.", "-Копка в воде"));
        map.put("BLOCK_REACH", new AssetPitch("Хватка", "Короче дистанция стройки и добычи блоков.", "-Дальность блоков"));
        map.put("XP_GAIN", new AssetPitch("Память нейросети", "Меньше опыта с руд и мобов, если уже зачаровал экипировку.", "-Опыт (XP)"));
        map.put("LUCK", new AssetPitch("Чип удачи", "Чуть скромнее лут и рыбалка ради живых CR здесь и сейчас.", "-Удача"));
        return Collections.unmodifiableMap(map);
    }

    private static Map<String, String> buildUpgradeDescriptions() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("REINFORCED_HEART", "Титановое сердце: +макс. HP перед рейдами и Аудитором.");
        map.put("RAPID_RECOVERY", "Нано-регенерация: быстрее восстанавливает здоровье.");
        map.put("REINFORCED_SKIN", "Подкожный кевлар: постоянная прибавка к броне.");
        map.put("ENHANCED_LUNGS", "Аква-лёгкие: дольше запас дыхания и выносливости.");
        map.put("MUSCLE_OVERCLOCK", "Экзо-мышцы: усиливает урон в ближнем бою.");
        map.put("FAST_REFLEXES", "Нейро-ускоритель: повышает скорость атак.");
        map.put("STRONG_LEGS", "Сервоприводы ног: ускоряет бег и манёвренность.");
        map.put("REINFORCED_BONES", "Демпфер падений: режет урон от высоты и Skyfall.");
        map.put("MINING_PROTOCOL", "Бур-протокол: ускоряет добычу руд для квоты.");
        map.put("ORE_SCANNER", "Рудный сканер: эффективнее добыча и поиск ценных жил.");
        map.put("LUCKY_CHIP", "Чип фортуны: повышает удачу и шанс редкого дропа.");
        map.put("XP_ACCELERATOR", "Ускоритель XP: больше опыта с руд и врагов.");
        map.put("BROKER_LICENSE", "Лицензия брокера: улучшает курс продаж на бирже.");
        map.put("DIAMOND_CASHBACK", "Корп-Кэшбэк: возвращает часть CR с покупок.");
        return Collections.unmodifiableMap(map);
    }

    private static Map<String, String> buildBlackMarketDescriptions() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("SECOND_CHANCE", "Второй Шанс: страхует от смертельного удара на краю гибели.");
        map.put("BERSERKER_PROTOCOL", "Протокол Берсерка: мощный всплеск урона ценой риска.");
        map.put("PHANTOM_STEP", "Фантомный Шаг: экстренный рывок и разрыв дистанции.");
        map.put("EMERGENCY_BATTERY", "Аварийная Батарея: экстренный щит и подзарядка в бою.");
        map.put("GOLDEN_CONTRACT", "Золотой Контракт: крупный нелегальный куш под особым риском.");
        return Collections.unmodifiableMap(map);
    }

    public enum AssetCategory {
        VITALITY("Живучесть"),
        MOBILITY("Мобильность"),
        COMBAT("Бой"),
        SURVIVAL("Выживание"),
        WORK_AND_EXPLORATION("Добыча и Разведка");

        private final String displayName;

        AssetCategory(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public record AssetPitch(String displayName, String whySell, String tradeOffTag) {
        public static final AssetPitch LEGACY_FALLBACK = new AssetPitch(
                "Био-актив",
                "Характеристика тела: доступна к продаже и выкупу на Бирже Тела.",
                "Asset"
        );
    }
}