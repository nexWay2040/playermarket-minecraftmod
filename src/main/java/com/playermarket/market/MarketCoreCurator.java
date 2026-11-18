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
 * Единый куратор чистого ядра экономики и интерфейса PLAYER MARKET.
 * <p>
 * Принцип работы: старые enum-константы НЕ удаляются из проекта, чтобы не ломать NBT-сейвы,
 * но UI, магазины и ротация событий видят только отобранное «золотое ядро».
 */
public final class MarketCoreCurator {

    private static final Logger LOGGER = LoggerFactory.getLogger("PlayerMarket/Curator");

    public static final int MAX_CURATED_MARKET_EVENTS = 5;

    /**
     * 6 ключевых вкладок планшета вместо 12.
     */
    private static final List<String> CURATED_TAB_IDS = List.of(
            "DASHBOARD",
            "SELL_ASSETS",
            "RESOURCE_MARKET",
            "UPGRADE_STORE",
            "BLACK_MARKET",
            "STORY"
    );

    private static final Map<String, String> TAB_DISPLAY_NAMES = Map.of(
            "DASHBOARD", "Терминал",
            "SELL_ASSETS", "Биржа Тела",
            "RESOURCE_MARKET", "Биржа Ресурсов",
            "UPGRADE_STORE", "Импланты",
            "BLACK_MARKET", "Чёрный Рынок",
            "STORY", "Протокол 17.4"
    );

    /**
     * 10 понятных и ощутимых органов/статов тела вместо 30.
     */
    private static final List<String> CURATED_ASSET_IDS = List.of(
            "MAX_HEALTH",
            "NATURAL_REGENERATION",
            "ARMOR",
            "ATTACK_DAMAGE",
            "ATTACK_SPEED",
            "MOVEMENT_SPEED",
            "JUMP_STRENGTH",
            "FALL_RESISTANCE",
            "MINING_SPEED",
            "FIRE_RESISTANCE"
    );

    private static final Map<String, AssetPitch> ASSET_PITCHES = buildAssetPitches();

    /**
     * 10 ключевых апгрейдов (8 боевых/выживание + 2 экономических) вместо 28.
     */
    private static final List<String> CURATED_UPGRADE_IDS = List.of(
            "REINFORCED_HEART",
            "RAPID_RECOVERY",
            "REINFORCED_SKIN",
            "MUSCLE_OVERCLOCK",
            "FAST_REFLEXES",
            "STRONG_LEGS",
            "REINFORCED_BONES",
            "MINING_PROTOCOL",
            "BROKER_LICENSE",
            "DIAMOND_CASHBACK"
    );

    private static final Map<String, String> UPGRADE_DESCRIPTIONS = buildUpgradeDescriptions();

    /**
     * 4 самых ярких клатч-предмета Чёрного Рынка вместо 8.
     */
    private static final List<String> CURATED_BLACK_MARKET_IDS = List.of(
            "SECOND_CHANCE",
            "BERSERKER_PROTOCOL",
            "PHANTOM_STEP",
            "SYNTHETIC_HEART"
    );

    private static final Map<String, String> BLACK_MARKET_DESCRIPTIONS = buildBlackMarketDescriptions();

    /**
     * 5 курируемых рыночных событий вместо 17.
     * При инициализации мода можно переопределить через {@link #configureMarketEventIds(Collection)},
     * либо заменить эти 5 ID на точные имена констант из вашего enum MarketEvent.
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
    // Kill-switches вырезанного балласта
    // -------------------------------------------------------------------------

    public static boolean isLeasingEnabled() {
        return false;
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
        return false;
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
        return filterOrdered(allTabs, CURATED_TAB_IDS);
    }

    public static String getTabDisplayName(String tabId) {
        if (tabId == null) {
            return "";
        }
        return TAB_DISPLAY_NAMES.getOrDefault(normalizeId(tabId), tabId);
    }

    // -------------------------------------------------------------------------
    // Органы и статы тела (AssetType)
    // -------------------------------------------------------------------------

    public static List<String> getCuratedAssetIds() {
        return CURATED_ASSET_IDS;
    }

    public static boolean isAssetCurated(String assetId) {
        return assetId != null && CURATED_ASSET_IDS.contains(normalizeId(assetId));
    }

    public static boolean isAssetCurated(Enum<?> asset) {
        return asset != null && isAssetCurated(asset.name());
    }

    public static boolean canSellAsset(String assetId) {
        return isAssetCurated(assetId);
    }

    public static boolean canSellAsset(Enum<?> asset) {
        return isAssetCurated(asset);
    }

    /**
     * Выкуп разрешён для всех курируемых органов, а также для старых архивных активов,
     * если игрок уже успел продать их в старом сохранении (soldUnits > 0).
     */
    public static boolean canBuyBackAsset(String assetId, int soldUnits) {
        return isAssetCurated(assetId) || soldUnits > 0;
    }

    public static boolean canBuyBackAsset(Enum<?> asset, int soldUnits) {
        return asset != null && canBuyBackAsset(asset.name(), soldUnits);
    }

    public static <T extends Enum<T>> List<T> filterCuratedAssets(T[] allAssets) {
        return filterOrdered(allAssets, CURATED_ASSET_IDS);
    }

    /**
     * Список для вкладки «Биржа Тела»: сначала 10 курируемых органов, затем
     * архивные органы из старых сейвов, по которым у игрока ещё есть проданные единицы.
     */
    public static <T extends Enum<T>> List<T> filterDisplayableAssets(T[] allAssets, ToIntFunction<T> soldUnitsLookup) {
        List<T> curated = filterOrdered(allAssets, CURATED_ASSET_IDS);
        if (allAssets == null || soldUnitsLookup == null) {
            return curated;
        }
        List<T> result = new ArrayList<>(curated);
        for (T asset : allAssets) {
            if (asset != null && !isAssetCurated(asset) && soldUnitsLookup.applyAsInt(asset) > 0) {
                result.add(asset);
            }
        }
        return result;
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
    // Магазин апгрейдов (UpgradeType)
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
        return filterOrdered(allUpgrades, CURATED_UPGRADE_IDS);
    }

    public static String getUpgradeShortDescription(String upgradeId) {
        if (upgradeId == null) {
            return "";
        }
        return UPGRADE_DESCRIPTIONS.getOrDefault(
                normalizeId(upgradeId),
                "Архивный имплант (снят с производства Corp)"
        );
    }

    public static String getUpgradeShortDescription(Enum<?> upgrade) {
        return upgrade == null ? "" : getUpgradeShortDescription(upgrade.name());
    }

    // -------------------------------------------------------------------------
    // Чёрный рынок (BlackMarketItem)
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
        return filterOrdered(allItems, CURATED_BLACK_MARKET_IDS);
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
    // Рыночные события (MarketEvent — 5 событий вместо 17)
    // -------------------------------------------------------------------------

    /**
     * Позволяет при инициализации мода явно зарегистрировать 5 точных имён констант enum MarketEvent.
     */
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

    /**
     * Фильтрует пул из 17 MarketEvent до 5 курируемых событий.
     * Если имена констант в MarketEvent отличаются от дефолтных и ещё не настроены,
     * безопасно отдаёт первые 5 не-нейтральных событий (без падения и пустого пула).
     */
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
                    "Ни одно имя из CURATED_MARKET_EVENT_IDS {} не совпало с MarketEvent. " +
                    "Используется безопасный срез из первых {} событий. Вызовите configureMarketEventIds(...) при старте.",
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
    // Вспомогательные методы и справочники
    // -------------------------------------------------------------------------

    private static <T extends Enum<T>> List<T> filterOrdered(T[] values, List<String> curatedOrder) {
        if (values == null || values.length == 0) {
            return Collections.emptyList();
        }
        Map<String, T> byName = new LinkedHashMap<>(values.length);
        for (T value : values) {
            if (value != null) {
                byName.put(normalizeId(value.name()), value);
            }
        }
        List<T> ordered = new ArrayList<>(curatedOrder.size());
        for (String id : curatedOrder) {
            T found = byName.get(id);
            if (found != null) {
                ordered.add(found);
            }
        }
        return ordered;
    }

    private static String normalizeId(String id) {
        return id.trim().toUpperCase(Locale.ROOT);
    }

    private static Map<String, AssetPitch> buildAssetPitches() {
        Map<String, AssetPitch> map = new LinkedHashMap<>();
        map.put("MAX_HEALTH", new AssetPitch(
                "Сердце",
                "Быстрый крупный куш CR за запас HP — идеально для старта в стиле стеклопушки.",
                "-Макс. HP"
        ));
        map.put("NATURAL_REGENERATION", new AssetPitch(
                "Печень",
                "Бесплатные CR, если готов хилиться золотыми яблоками и зельями вместо авто-регена.",
                "-Автореген"
        ));
        map.put("ARMOR", new AssetPitch(
                "Панцирь",
                "Выгодная продажа базовой кожи для шахты и мирного фарма до выхода на босса.",
                "-Броня"
        ));
        map.put("ATTACK_DAMAGE", new AssetPitch(
                "Мускулатура",
                "Слей силу удара в мирное время ради квоты или ставки в казино 50/50.",
                "-Урон вблизи"
        ));
        map.put("ATTACK_SPEED", new AssetPitch(
                "Рефлексы",
                "Дорогой боевой актив: удары реже, зато кошелёк сразу тяжелее.",
                "-Скорость атаки"
        ));
        map.put("MOVEMENT_SPEED", new AssetPitch(
                "Сухожилия",
                "Лёгкие CR на базе или в узких пещерах — главное не попасть под рейд коллекторов.",
                "-Скорость бега"
        ));
        map.put("JUMP_STRENGTH", new AssetPitch(
                "Икроножные",
                "Жирная выплата за прыгучесть — компенсируй лестницами или Крюком-Кошкой!",
                "-Высота прыжка"
        ));
        map.put("FALL_RESISTANCE", new AssetPitch(
                "Кости",
                "Безопасные CR на равнине, но перед Skyfall Титана лучше выкупить обратно.",
                "+Урон падения"
        ));
        map.put("MINING_SPEED", new AssetPitch(
                "Кисти рук",
                "Когда кирка уже не нужна перед боем с Аудитором — преврати скорость копания в CR.",
                "-Скорость копания"
        ));
        map.put("FIRE_RESISTANCE", new AssetPitch(
                "Терможелезы",
                "Свободные деньги в обычном мире, пока держишься подальше от лавы и Незера.",
                "+Урон от огня"
        ));
        return Collections.unmodifiableMap(map);
    }

    private static Map<String, String> buildUpgradeDescriptions() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("REINFORCED_HEART", "Титановое сердце: +макс. HP для выживания против Аудитора.");
        map.put("RAPID_RECOVERY", "Нано-реген: ускоряет восстановление здоровья в бою.");
        map.put("REINFORCED_SKIN", "Подкожный кевлар: постоянная прибавка к броне.");
        map.put("MUSCLE_OVERCLOCK", "Экзо-мышцы: усиливает урон в ближнем бою.");
        map.put("FAST_REFLEXES", "Нейро-ускоритель: повышает скорость атак.");
        map.put("STRONG_LEGS", "Сервоприводы: повышает скорость бега и уклонения.");
        map.put("REINFORCED_BONES", "Демпфер падений: режет урон от высоты и Skyfall.");
        map.put("MINING_PROTOCOL", "Бур-протокол: ускоряет добычу руд для квоты.");
        map.put("BROKER_LICENSE", "Брокерская лицензия: улучшает курс продажи на бирже.");
        map.put("DIAMOND_CASHBACK", "Корп-Кэшбэк: возвращает часть CR с каждой покупки.");
        return Collections.unmodifiableMap(map);
    }

    private static Map<String, String> buildBlackMarketDescriptions() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("SECOND_CHANCE", "Второй Шанс: спасает от смертельного удара на краю гибели.");
        map.put("BERSERKER_PROTOCOL", "Протокол Берсерка: дикий прирост урона ценой риска.");
        map.put("PHANTOM_STEP", "Фантомный Шаг: экстренный рывок и разрыв дистанции.");
        map.put("SYNTHETIC_HEART", "Синтетическое Сердце: временный буст HP перед схваткой.");
        return Collections.unmodifiableMap(map);
    }

    public record AssetPitch(String displayName, String whySell, String tradeOffTag) {
        public static final AssetPitch LEGACY_FALLBACK = new AssetPitch(
                "Архивный актив",
                "Устаревший орган: новая продажа закрыта, доступен только выкуп старого долга.",
                "Legacy"
        );
    }
}