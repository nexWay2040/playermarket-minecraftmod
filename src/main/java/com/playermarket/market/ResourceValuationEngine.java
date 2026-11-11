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
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
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
        // Core crafting intermediates so all tool/armor recipes resolve accurately
        seedFallback(Items.OAK_PLANKS, 2L);
        seedFallback(Items.SPRUCE_PLANKS, 2L);
        seedFallback(Items.BIRCH_PLANKS, 2L);
        seedFallback(Items.JUNGLE_PLANKS, 2L);
        seedFallback(Items.ACACIA_PLANKS, 2L);
        seedFallback(Items.DARK_OAK_PLANKS, 2L);
        seedFallback(Items.MANGROVE_PLANKS, 2L);
        seedFallback(Items.CHERRY_PLANKS, 2L);
        seedFallback(Items.PALE_OAK_PLANKS, 2L);
        seedFallback(Items.BAMBOO_PLANKS, 2L);
        seedFallback(Items.CRIMSON_PLANKS, 4L);
        seedFallback(Items.WARPED_PLANKS, 4L);
        seedFallback(Items.STICK, 2L);
        seedFallback(Items.LEATHER, 18L);
        seedFallback(Items.PAPER, 5L);
        seedFallback(Items.BOOK, 33L);
        seedFallback(Items.BRICK, 8L);
        seedFallback(Items.NETHER_BRICK, 10L);
        seedFallback(Items.BLAZE_POWDER, 95L);
        seedFallback(Items.ENDER_EYE, 260L);

        seedFallback(Items.ANCIENT_DEBRIS, 950L);
        seedFallback(Items.NETHERITE_SCRAP, 980L);
        seedFallback(Items.NETHERITE_INGOT, 4400L);
        seedFallback(Items.NETHERITE_BLOCK, 39600L);
        seedFallback(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 3600L);
        seedFallback(Items.DIAMOND_BLOCK, cfg.diamondSellPriceCr * 9L);
        seedFallback(Items.EMERALD_BLOCK, 3420L);
        seedFallback(Items.GOLD_BLOCK, 990L);
        seedFallback(Items.IRON_BLOCK, 540L);
        Items.COPPER_BLOCK.forEach(cb -> seedFallback(cb, 216L));
        seedFallback(Items.LAPIS_BLOCK, 288L);
        seedFallback(Items.REDSTONE_BLOCK, 252L);
        seedFallback(Items.COAL_BLOCK, 162L);

        seedFallback(Items.NETHER_STAR, 5500L);
        seedFallback(Items.BEACON, 6200L);
        seedFallback(Items.ELYTRA, 12000L);
        seedFallback(Items.TOTEM_OF_UNDYING, 3200L);
        seedFallback(Items.ENCHANTED_GOLDEN_APPLE, 4800L);
        seedFallback(Items.GOLDEN_APPLE, 920L);
        seedFallback(Items.TRIDENT, 4200L);
        seedFallback(Items.HEAVY_CORE, 6500L);
        seedFallback(Items.BREEZE_ROD, 240L);
        seedFallback(Items.MACE, 7200L);
        seedFallback(Items.HEART_OF_THE_SEA, 2600L);
        seedFallback(Items.CONDUIT, 3400L);
        seedFallback(Items.END_CRYSTAL, 1100L);
        seedFallback(Items.ECHO_SHARD, 650L);
        seedFallback(Items.DRAGON_HEAD, 3800L);
        seedFallback(Items.DRAGON_EGG, 15000L);
        seedFallback(Items.WITHER_SKELETON_SKULL, 1800L);
        seedFallback(Items.SHULKER_BOX, 950L);
        seedFallback(Items.EXPERIENCE_BOTTLE, 85L);

        // Explicitly seed Diamond & Netherite tier gear so smithing/upgrades never under-price
        seedFallback(Items.DIAMOND_SWORD, cfg.diamondSellPriceCr * 2L + 4L);
        seedFallback(Items.DIAMOND_SHOVEL, cfg.diamondSellPriceCr + 6L);
        seedFallback(Items.DIAMOND_PICKAXE, cfg.diamondSellPriceCr * 3L + 6L);
        seedFallback(Items.DIAMOND_AXE, cfg.diamondSellPriceCr * 3L + 6L);
        seedFallback(Items.DIAMOND_HOE, cfg.diamondSellPriceCr * 2L + 6L);
        seedFallback(Items.DIAMOND_HELMET, cfg.diamondSellPriceCr * 5L);
        seedFallback(Items.DIAMOND_CHESTPLATE, cfg.diamondSellPriceCr * 8L);
        seedFallback(Items.DIAMOND_LEGGINGS, cfg.diamondSellPriceCr * 7L);
        seedFallback(Items.DIAMOND_BOOTS, cfg.diamondSellPriceCr * 4L);
        seedFallback(Items.DIAMOND_HORSE_ARMOR, 2400L);

        seedFallback(Items.NETHERITE_SWORD, 7800L);
        seedFallback(Items.NETHERITE_SHOVEL, 6900L);
        seedFallback(Items.NETHERITE_PICKAXE, 8500L);
        seedFallback(Items.NETHERITE_AXE, 8500L);
        seedFallback(Items.NETHERITE_HOE, 7600L);
        seedFallback(Items.NETHERITE_SPEAR, 8200L);
        seedFallback(Items.NETHERITE_HELMET, 9800L);
        seedFallback(Items.NETHERITE_CHESTPLATE, 12200L);
        seedFallback(Items.NETHERITE_LEGGINGS, 11400L);
        seedFallback(Items.NETHERITE_BOOTS, 9100L);
        seedFallback(Items.NETHERITE_HORSE_ARMOR, 8800L);
        seedFallback(Items.NETHERITE_NAUTILUS_ARMOR, 9000L);

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

        // 2. Iterative relaxation over all RecipeManager recipes (Crafting, Smelting, Blasting, Smoking, Stonecutting, Smithing)
        if (recipeManager != null) {
            Collection<RecipeHolder<?>> allRecipes = recipeManager.getRecipes();
            for (int pass = 0; pass < 8; pass++) {
                boolean changed = false;
                for (RecipeHolder<?> holder : allRecipes) {
                    Recipe<?> recipe = holder.value();
                    if (recipe.isSpecial()) continue;

                    ItemCountResult output = extractResult(recipe);
                    if (output == null || output.item == Items.AIR || output.count <= 0) continue;

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
                        totalIngredientsCost += 6L;
                    } else if (recipe instanceof SmithingTransformRecipe) {
                        totalIngredientsCost += 150L;
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
                || item == Items.REDSTONE || item == Items.LAPIS_LAZULI
                || item == Items.ANCIENT_DEBRIS || item == Items.NETHERITE_SCRAP || item == Items.NETHERITE_INGOT
                || item == Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE
                || item == Items.DIAMOND_BLOCK || item == Items.NETHERITE_BLOCK || item == Items.EMERALD_BLOCK
                || item == Items.GOLD_BLOCK || item == Items.IRON_BLOCK || Items.COPPER_BLOCK.asList().contains(item)
                || item == Items.LAPIS_BLOCK || item == Items.REDSTONE_BLOCK || item == Items.COAL_BLOCK
                || item == Items.STICK;
    }

    private static void seedFallback(Item item, long price) {
        CACHED_BASE_VALUES.putIfAbsent(item, price);
    }

    private record ItemCountResult(Item item, int count) {}

    private static ItemCountResult extractResult(Recipe<?> recipe) {
        try {
            for (RecipeDisplay display : recipe.display()) {
                SlotDisplay resultSlot = null;
                if (display instanceof SmithingRecipeDisplay sd) {
                    resultSlot = sd.result();
                } else {
                    resultSlot = display.result();
                }
                ItemCountResult resolved = resolveSlotDisplay(resultSlot);
                if (resolved != null) return resolved;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static ItemCountResult resolveSlotDisplay(SlotDisplay slot) {
        if (slot instanceof SlotDisplay.ItemStackSlotDisplay stackSlot) {
            return new ItemCountResult(stackSlot.stack().item().value(), Math.max(1, stackSlot.stack().count()));
        } else if (slot instanceof SlotDisplay.ItemSlotDisplay itemSlot) {
            return new ItemCountResult(itemSlot.item().value(), 1);
        } else if (slot instanceof SlotDisplay.Composite comp) {
            for (SlotDisplay sub : comp.contents()) {
                ItemCountResult r = resolveSlotDisplay(sub);
                if (r != null) return r;
            }
        }
        return null;
    }

    private static List<Ingredient> extractIngredients(Recipe<?> recipe) {
        try {
            if (recipe instanceof SmithingTransformRecipe smith) {
                List<Ingredient> list = new ArrayList<>();
                smith.templateIngredient().ifPresent(list::add);
                list.add(smith.baseIngredient());
                smith.additionIngredient().ifPresent(list::add);
                if (!list.isEmpty()) return list;
            }
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

        // Market event modifier & Price Lock guaranteed floor
        if (activeEvent != null) {
            double eventMult = activeEvent.getResourcePriceMultiplier();
            int priceLockLvl = account != null ? account.getUpgradeLevel(UpgradeType.PRICE_LOCK) : 0;
            if (priceLockLvl >= 1 && eventMult < 1.0) {
                eventMult = 1.0;
            }
            if (priceLockLvl == 2) eventMult = Math.max(eventMult, 1.08);
            else if (priceLockLvl >= 3) eventMult = Math.max(eventMult, 1.18);
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
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        boolean isArmorOrTool = stack.hasNonDefault(DataComponents.EQUIPPABLE)
                || stack.hasNonDefault(DataComponents.TOOL)
                || stack.hasNonDefault(DataComponents.WEAPON)
                || stack.isDamageableItem();

        if (path.contains("netherite")) {
            return path.contains("block") ? 35000L : (isArmorOrTool ? 8200L : 4400L);
        }
        if (path.contains("diamond")) {
            return path.contains("block") ? 5800L : (isArmorOrTool ? 2200L : 650L);
        }
        if (path.contains("emerald")) {
            return path.contains("block") ? 3400L : 380L;
        }
        if (path.contains("heavy_core") || path.contains("mace")) {
            return 6000L;
        }
        if (path.contains("beacon") || path.contains("elytra") || path.contains("nether_star")) {
            return 6500L;
        }
        if (path.contains("shulker")) {
            return 450L;
        }
        if (path.contains("gold") || path.contains("golden")) {
            return path.contains("block") ? 950L : (isArmorOrTool ? 420L : 110L);
        }
        if (path.contains("iron")) {
            return path.contains("block") ? 520L : (isArmorOrTool ? 220L : 60L);
        }
        if (path.contains("copper")) {
            return path.contains("block") ? 200L : (isArmorOrTool ? 90L : 24L);
        }

        long rarityFloor = switch (stack.getRarity()) {
            case COMMON -> isArmorOrTool ? 45L : 8L;
            case UNCOMMON -> isArmorOrTool ? 160L : 65L;
            case RARE -> isArmorOrTool ? 650L : 260L;
            case EPIC -> isArmorOrTool ? 2500L : 1200L;
        };
        if (stack.hasNonDefault(DataComponents.FOOD)) {
            rarityFloor = Math.max(rarityFloor, 12L);
        }
        return rarityFloor;
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
