package com.playermarket.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.playermarket.PlayerMarketMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class MarketConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static MarketConfig INSTANCE = new MarketConfig();

    public int configVersion = 5;
    public long startingCr = 250L;
    public double assetPriceMultiplier = 1.0;
    public double resourceSellRatio = 0.65;
    public double resourceBuyMarkup = 1.40;
    public long diamondBuyPriceCr = 1200L;
    public long diamondSellPriceCr = 650L;
    public double buybackCommission = 1.35; // 135% corporate buyback markup: base * 2.35
    public double upgradePriceMultiplier = 1.0;
    public double maxAttributeSaleFraction = 0.80; // Max 80% of any attribute can be sold
    public int maxUpgradeLevelCap = 3;
    public int contractRefreshTicks = 24000; // 1 Minecraft day
    public int marketEventDurationTicks = 2400; // 2.0 minutes dynamic cycle
    public double premiumAccountBonus = 0.10; // +10% CR payout
    public double brokerLicenseDiscount = 0.15; // -15% buyback commission
    public double diamondCashbackChance = 0.25; // 25% chance to refund 1 diamond on L2/L3 purchases
    public int blackMarketMinDeals = 3;
    public long blackMarketMinNetTurnover = 1000L;
    public boolean enableStoryProgram = true;
    public boolean enableStoryToasts = true;
    public boolean enableSideQuests = true;
    public boolean enableChaosDirectives = true;
    public int chaosIntervalMinTicks = 240; // 12s
    public int chaosIntervalMaxTicks = 400; // 20s

    public Map<String, Long> customResourceBasePrices = new LinkedHashMap<>();
    public Map<String, Long> customAssetUnitPrices = new LinkedHashMap<>();
    public Map<String, Integer> customUpgradeBaseDiamondCost = new LinkedHashMap<>();

    public MarketConfig() {
        initDefaults();
    }

    private void initDefaults() {
        customResourceBasePrices.put("minecraft:dirt", 2L);
        customResourceBasePrices.put("minecraft:cobblestone", 2L);
        customResourceBasePrices.put("minecraft:stone", 4L);
        customResourceBasePrices.put("minecraft:oak_log", 8L);
        customResourceBasePrices.put("minecraft:spruce_log", 8L);
        customResourceBasePrices.put("minecraft:birch_log", 8L);
        customResourceBasePrices.put("minecraft:jungle_log", 8L);
        customResourceBasePrices.put("minecraft:acacia_log", 8L);
        customResourceBasePrices.put("minecraft:dark_oak_log", 8L);
        customResourceBasePrices.put("minecraft:sand", 3L);
        customResourceBasePrices.put("minecraft:gravel", 3L);
        customResourceBasePrices.put("minecraft:clay_ball", 5L);
        customResourceBasePrices.put("minecraft:coal", 18L);
        customResourceBasePrices.put("minecraft:charcoal", 12L);
        customResourceBasePrices.put("minecraft:copper_ingot", 24L);
        customResourceBasePrices.put("minecraft:raw_copper", 18L);
        customResourceBasePrices.put("minecraft:redstone", 28L);
        customResourceBasePrices.put("minecraft:lapis_lazuli", 32L);
        customResourceBasePrices.put("minecraft:quartz", 35L);
        customResourceBasePrices.put("minecraft:amethyst_shard", 40L);
        customResourceBasePrices.put("minecraft:iron_ingot", 60L);
        customResourceBasePrices.put("minecraft:raw_iron", 48L);
        customResourceBasePrices.put("minecraft:gold_ingot", 110L);
        customResourceBasePrices.put("minecraft:raw_gold", 88L);
        customResourceBasePrices.put("minecraft:obsidian", 140L);
        customResourceBasePrices.put("minecraft:ender_pearl", 160L);
        customResourceBasePrices.put("minecraft:blaze_rod", 180L);
        customResourceBasePrices.put("minecraft:ghast_tear", 260L);
        customResourceBasePrices.put("minecraft:emerald", 380L);
        customResourceBasePrices.put("minecraft:diamond", 650L);
        customResourceBasePrices.put("minecraft:netherite_scrap", 950L);
        customResourceBasePrices.put("minecraft:nether_star", 5000L);
        customResourceBasePrices.put("minecraft:wheat", 6L);
        customResourceBasePrices.put("minecraft:carrot", 6L);
        customResourceBasePrices.put("minecraft:potato", 6L);
        customResourceBasePrices.put("minecraft:apple", 12L);
        customResourceBasePrices.put("minecraft:beef", 14L);
        customResourceBasePrices.put("minecraft:cooked_beef", 22L);
        customResourceBasePrices.put("minecraft:leather", 18L);
        customResourceBasePrices.put("minecraft:string", 10L);
        customResourceBasePrices.put("minecraft:bone", 12L);
        customResourceBasePrices.put("minecraft:gunpowder", 24L);
        customResourceBasePrices.put("minecraft:slime_ball", 30L);
    }

    public static MarketConfig get() {
        return INSTANCE;
    }

    public static void load() {
        Path path = getConfigPath();
        if (Files.exists(path)) {
            try {
                String rawJson = Files.readString(path, StandardCharsets.UTF_8);
                int rawVersion = 0;
                try {
                    JsonObject obj = JsonParser.parseString(rawJson).getAsJsonObject();
                    if (obj.has("configVersion") && obj.get("configVersion").isJsonPrimitive()) {
                        rawVersion = obj.get("configVersion").getAsInt();
                    }
                } catch (Exception ignored) {
                }

                MarketConfig loaded = GSON.fromJson(rawJson, MarketConfig.class);
                if (loaded != null) {
                    if (loaded.customResourceBasePrices == null || loaded.customResourceBasePrices.isEmpty()) {
                        loaded.initDefaults();
                    }
                    boolean upgraded = false;
                    if (loaded.buybackCommission < 1.0) {
                        loaded.buybackCommission = 1.35;
                        upgraded = true;
                    }
                    if (rawVersion < 3) {
                        if (loaded.marketEventDurationTicks >= 6000) {
                            loaded.marketEventDurationTicks = 2400;
                        }
                        if (loaded.blackMarketMinDeals > 3) {
                            loaded.blackMarketMinDeals = 3;
                        }
                        if (loaded.blackMarketMinNetTurnover > 1000L) {
                            loaded.blackMarketMinNetTurnover = 1000L;
                        }
                        if (loaded.startingCr > 300L) {
                            loaded.startingCr = 250L;
                        }
                        loaded.enableStoryProgram = true;
                        loaded.enableSideQuests = true;
                        loaded.enableChaosDirectives = true;
                        upgraded = true;
                    }
                    if (rawVersion < 5) {
                        if (loaded.marketEventDurationTicks >= 3000 || loaded.marketEventDurationTicks <= 0) {
                            loaded.marketEventDurationTicks = 2400;
                        }
                        if (loaded.chaosIntervalMinTicks <= 0 || loaded.chaosIntervalMinTicks >= 300) {
                            loaded.chaosIntervalMinTicks = 240;
                        }
                        if (loaded.chaosIntervalMaxTicks <= loaded.chaosIntervalMinTicks || loaded.chaosIntervalMaxTicks >= 520) {
                            loaded.chaosIntervalMaxTicks = 400;
                        }
                        loaded.configVersion = 5;
                        upgraded = true;
                    }
                    INSTANCE = loaded;
                    if (upgraded) {
                        save();
                    }
                }
            } catch (Exception e) {
                PlayerMarketMod.LOGGER.error("Failed to load playermarket.json config, using defaults", e);
            }
        } else {
            save();
        }
    }

    public static void save() {
        Path path = getConfigPath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (IOException e) {
            PlayerMarketMod.LOGGER.error("Failed to save playermarket.json config", e);
        }
    }

    private static Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("playermarket.json");
    }
}
