package com.playermarket.market;

import com.playermarket.PlayerMarketMod;
import com.playermarket.config.MarketConfig;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ResourceValuationEngine {
    private static final Map<Item, Long> CACHED_BASE_VALUES = new ConcurrentHashMap<>();
    private static boolean initialized = false;

    public record CatalogItem(String itemId, Item item, int bundleSize, long buyCostCr, long sellUnitCr) {
        public String displayName() {
            return new ItemStack(item).getHoverName().getString();
        }
    }

    public static void invalidateCache() {
        CACHED_BASE_VALUES.clear();
        initialized = false;
    }

    public static synchronized void ensureInitialized(MinecraftServer server) {
        if (initialized && !CACHED_BASE_VALUES.isEmpty()) return;
        rebuildCache(server != null ? server.getRecipeManager() : null);
    }

    public static synchronized void rebuildCache(RecipeManager recipeManager) {
        CACHED_BASE_VALUES.clear();
        MarketConfig cfg = MarketConfig.get();

        // 1. Seed configured base prices
        for (Map.Entry<String, Long> entry : cfg.customResourceBasePrices.entrySet()) {
            try {
                Identifier id = Identifier.parse(entry.getKey());
                Optional<Holder.Reference<Item>> holderOpt = BuiltInRegistries.ITEM.get(id);
                holderOpt.ifPresent(ref -> CACHED_BASE_VALUES.put(ref.value(), Math.max(1L, entry.getValue())));
            } catch (Exception ignored) {
            }
        }

        // Additional raw material fallbacks
        seedFallback(Items.COBBLESTONE, 2L);
        seedFallback(Items.COBBLED_DEEPSLATE, 3L);
        seedFallback(Items.DIRT, 2L);
        seedFallback(Items.NETHERRACK, 2L);
        seedFallback(Items.END_STONE, 6L);
        seedFallback(Items.SAND, 3L);
        seedFallback(Items.GRAVEL, 3L);
        seedFallback(Items.CLAY_BALL, 5L);
        seedFallback(Items.FLINT, 6L);
        seedFallback(Items.OAK_LOG, 8L);
        seedFallback(Items.SPRUCE_LOG, 8L);
        seedFallback(Items.BIRCH_LOG, 8L);
        seedFallback(Items.JUNGLE_LOG, 8L);
        seedFallback(Items.ACACIA_LOG, 8L);
        seedFallback(Items.DARK_OAK_LOG, 8L);
        seedFallback(Items.MANGROVE_LOG, 8L);
        seedFallback(Items.CHERRY_LOG, 8L);
        seedFallback(Items.BAMBOO, 2L);
        seedFallback(Items.SUGAR_CANE, 5L);
        seedFallback(Items.WHEAT, 6L);
        seedFallback(Items.CARROT, 6L);
        seedFallback(Items.POTATO, 6L);
        seedFallback(Items.BEETROOT, 6L);
        seedFallback(Items.APPLE, 12L);
        seedFallback(Items.COAL, 18L);
        seedFallback(Items.CHARCOAL, 12L);
        seedFallback(Items.RAW_COPPER, 18L);
        seedFallback(Items.COPPER_INGOT, 24L);
        seedFallback(Items.REDSTONE, 28L);
        seedFallback(Items.LAPIS_LAZULI, 32L);
        seedFallback(Items.QUARTZ, 35L);
        seedFallback(Items.AMETHYST_SHARD, 40L);
        seedFallback(Items.RAW_IRON, 48L);
        seedFallback(Items.IRON_INGOT, 60L);
        seedFallback(Items.RAW_GOLD, 88L);
        seedFallback(Items.GOLD_INGOT, 110L);
        seedFallback(Items.OBSIDIAN, 140L);
        seedFallback(Items.ENDER_PEARL, 160L);
        seedFallback(Items.BLAZE_ROD, 180L);
        seedFallback(Items.GHAST_TEAR, 260L);
        seedFallback(Items.SHULKER_SHELL, 350L);
        seedFallback(Items.EMERALD, 380L);
        seedFallback(Items.DIAMOND, cfg.diamondSellPriceCr);
        seedFallback(Items.NETHERITE_SCRAP, 950L);
        seedFallback(Items.NETHER_STAR, 5000L);
        seedFallback(Items.FEATHER, 8L);
        seedFallback(Items.STRING, 10L);
        seedFallback(Items.GUNPOWDER, 24L);
        seedFallback(Items.BONE, 12L);
        seedFallback(Items.ROTTEN_FLESH, 4L);
        seedFallback(Items.SPIDER_EYE, 14L);
        seedFallback(Items.SLIME_BALL, 30L);
        seedFallback(Items.PRISMARINE_SHARD, 22L);
        seedFallback(Items.PRISMARINE_CRYSTALS, 30L);
        seedFallback(Items.GLOWSTONE_DUST, 20L);
        seedFallback(Items.SOUL_SAND, 8L);
        seedFallback(Items.MAGMA_CREAM, 45L);
        seedFallback(Items.PHANTOM_MEMBRANE, 55L);

        // 2. Iterative relaxation over all RecipeManager recipes (Crafting, Smelting, Blasting, Smoking, Campfire, Stonecutting, Smithing)
        if (recipeManager != null) {
            Collection<RecipeHolder<?>> allRecipes = recipeManager.getRecipes();
            for (int pass = 0; pass < 6; pass++) {
                boolean changed = false;
                for (RecipeHolder<?> holder : allRecipes) {
                    Recipe<?> recipe = holder.value();
                    if (recipe.isSpecial()) continue;

                    ItemCountResult output = extractResult(recipe);
                    if (output == null || output.item == Items.AIR || output.count <= 0) continue;

                    // Do not override fixed base commodities like Diamond or Iron Ingot from block-uncrafting loops unless cheaper
                    List<Ingredient> ingredients = extractIngredients(recipe);
                    if (ingredients.isEmpty()) continue;

                    long totalIngredientsCost = 0L;
                    boolean allResolved = true;
                    for (Ingredient ing : ingredients) {
                        long ingCost = evaluateIngredientMinCost(ing);
                        if (ingCost <= 0L) {
                            allResolved = false;
                            break;
                        }
                        totalIngredientsCost += ingCost;
                    }

                    if (!allResolved || totalIngredientsCost <= 0L) continue;

                    // Processing bonus for cooking/smithing
                    if (recipe instanceof AbstractCookingRecipe) {
                        totalIngredientsCost += 4L;
                    } else if (recipe instanceof SmithingTransformRecipe) {
                        totalIngredientsCost += 25L;
                    }

                    long unitDerivedCost = Math.max(1L, totalIngredientsCost / output.count);
                    Long existing = CACHED_BASE_VALUES.get(output.item);
                    if (existing == null || (unitDerivedCost < existing && !isProtectedBaseItem(output.item))) {
                        CACHED_BASE_VALUES.put(output.item, unitDerivedCost);
                        changed = true;
                    }
                }
                if (!changed) break;
            }
        }

        initialized = true;
        PlayerMarketMod.LOGGER.info("Player Market ResourceValuationEngine cached {} item valuations.", CACHED_BASE_VALUES.size());
    }

    private static boolean isProtectedBaseItem(Item item) {
        return item == Items.DIAMOND || item == Items.EMERALD || item == Items.IRON_INGOT
                || item == Items.GOLD_INGOT || item == Items.COPPER_INGOT || item == Items.COAL
                || item == Items.REDSTONE || item == Items.LAPIS_LAZULI || item == Items.NETHERITE_SCRAP;
    }

    private static void seedFallback(Item item, long price) {
        CACHED_BASE_VALUES.putIfAbsent(item, price);
    }

    private record ItemCountResult(Item item, int count) {}

    private static ItemCountResult extractResult(Recipe<?> recipe) {
        try {
            for (RecipeDisplay display : recipe.display()) {
                SlotDisplay resultSlot = display.result();
                if (resultSlot instanceof SlotDisplay.ItemStackSlotDisplay stackSlot) {
                    return new ItemCountResult(stackSlot.stack().item().value(), Math.max(1, stackSlot.stack().count()));
                } else if (resultSlot instanceof SlotDisplay.ItemSlotDisplay itemSlot) {
                    return new ItemCountResult(itemSlot.item().value(), 1);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static List<Ingredient> extractIngredients(Recipe<?> recipe) {
        try {
            PlacementInfo info = recipe.placementInfo();
            if (info != null && !info.ingredients().isEmpty()) {
                return info.ingredients();
            }
            if (recipe instanceof SingleItemRecipe single) {
                return List.of(single.input());
            }
        } catch (Exception ignored) {
        }
        return Collections.emptyList();
    }

    private static long evaluateIngredientMinCost(Ingredient ingredient) {
        long best = Long.MAX_VALUE;
        try {
            var stream = ingredient.items();
            for (Holder<Item> holder : (Iterable<Holder<Item>>) stream::iterator) {
                Long val = CACHED_BASE_VALUES.get(holder.value());
                if (val != null && val > 0 && val < best) {
                    best = val;
                }
            }
        } catch (Exception ignored) {
        }
        return best == Long.MAX_VALUE ? -1L : best;
    }

    public static long evaluateUnitSellCr(ItemStack stack, PlayerMarketAccount account, MarketEvent activeEvent) {
        if (stack == null || stack.isEmpty()) return 0L;
        Item item = stack.getItem();
        if (item == Items.DIAMOND) {
            double mult = activeEvent != null ? activeEvent.getDiamondValueMultiplier() : 1.0;
            return Math.max(1L, Math.round(MarketConfig.get().diamondSellPriceCr * mult));
        }

        long base = CACHED_BASE_VALUES.getOrDefault(item, estimateFallbackByRarity(stack));
        double value = base * MarketConfig.get().resourceSellRatio;

        // Durability scaling
        if (stack.isDamageableItem() && stack.getMaxDamage() > 0) {
            int remaining = Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
            double durabilityRatio = (double) remaining / (double) stack.getMaxDamage();
            value *= (0.15 + 0.85 * durabilityRatio);
        }

        // Enchantment valuation bonus
        ItemEnchantments enchantments = stack.getEnchantments();
        if (enchantments != null && !enchantments.isEmpty()) {
            for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
                int lvl = entry.getIntValue();
                value += (lvl * 45.0);
            }
        }

        // Custom potion / special component bonus
        if (stack.hasNonDefault(DataComponents.POTION_CONTENTS)) {
            value += 25.0;
        }

        // Market event modifier
        if (activeEvent != null) {
            double eventMult = activeEvent.getResourcePriceMultiplier();
            if (account != null && account.getUpgradeLevel(UpgradeType.PRICE_LOCK) >= 1 && eventMult < 1.0) {
                eventMult = 1.0;
            }
            value *= eventMult;
        }

        // Corporate privilege: Premium Account bonus
        if (account != null) {
            int premLvl = account.getUpgradeLevel(UpgradeType.PREMIUM_ACCOUNT);
            if (premLvl > 0) {
                value *= (1.0 + UpgradeType.PREMIUM_ACCOUNT.getValueForLevel(premLvl));
            }
        }

        return Math.max(1L, Math.round(value));
    }

    private static long estimateFallbackByRarity(ItemStack stack) {
        return switch (stack.getRarity()) {
            case COMMON -> 4L;
            case UNCOMMON -> 35L;
            case RARE -> 120L;
            case EPIC -> 400L;
        };
    }

    public static List<CatalogItem> getCatalogOffers(PlayerMarketAccount account, MarketEvent event) {
        List<CatalogItem> list = new ArrayList<>();
        addCatalogOffer(list, Items.DIRT, 32, account, event);
        addCatalogOffer(list, Items.COBBLESTONE, 32, account, event);
        addCatalogOffer(list, Items.OAK_LOG, 16, account, event);
        addCatalogOffer(list, Items.COAL, 16, account, event);
        addCatalogOffer(list, Items.COPPER_INGOT, 8, account, event);
        addCatalogOffer(list, Items.REDSTONE, 16, account, event);
        addCatalogOffer(list, Items.LAPIS_LAZULI, 16, account, event);
        addCatalogOffer(list, Items.IRON_INGOT, 8, account, event);
        addCatalogOffer(list, Items.GOLD_INGOT, 8, account, event);
        addCatalogOffer(list, Items.OBSIDIAN, 4, account, event);
        addCatalogOffer(list, Items.EMERALD, 1, account, event);
        addCatalogOffer(list, Items.DIAMOND, 1, account, event);
        addCatalogOffer(list, Items.COOKED_BEEF, 16, account, event);
        addCatalogOffer(list, Items.QUARTZ_BLOCK, 16, account, event);
        addCatalogOffer(list, Items.ENDER_PEARL, 4, account, event);
        addCatalogOffer(list, Items.BLAZE_ROD, 2, account, event);
        if (account != null && account.getUpgradeLevel(UpgradeType.VIP_CLIENT) >= 1) {
            addCatalogOffer(list, Items.GOLDEN_APPLE, 2, account, event);
            addCatalogOffer(list, Items.NETHERITE_SCRAP, 1, account, event);
        }
        return list;
    }

    private static void addCatalogOffer(List<CatalogItem> list, Item item, int bundleSize, PlayerMarketAccount account, MarketEvent event) {
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        long unitBuy;
        if (item == Items.DIAMOND) {
            double mult = event != null ? event.getDiamondValueMultiplier() : 1.0;
            unitBuy = Math.round(MarketConfig.get().diamondBuyPriceCr * mult);
        } else {
            long base = CACHED_BASE_VALUES.getOrDefault(item, 10L);
            double mult = event != null ? event.getResourcePriceMultiplier() : 1.0;
            unitBuy = Math.max(1L, Math.round(base * MarketConfig.get().resourceBuyMarkup * mult));
        }

        if (account != null) {
            int vipLvl = account.getUpgradeLevel(UpgradeType.VIP_CLIENT);
            if (vipLvl == 2) unitBuy = Math.round(unitBuy * 0.92);
            else if (vipLvl >= 3) unitBuy = Math.round(unitBuy * 0.85);
        }

        long totalCost = Math.max(1L, unitBuy * bundleSize);
        long sellUnit = evaluateUnitSellCr(new ItemStack(item), account, event);
        list.add(new CatalogItem(id.toString(), item, bundleSize, totalCost, sellUnit));
    }
}
