package com.playermarket.market;

import com.playermarket.PlayerMarketMod;
import com.playermarket.config.MarketConfig;
import com.playermarket.network.MarketNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class MarketAttributeApplier {

    // All vanilla attributes we ever modify
    private static final List<Holder<Attribute>> MANAGED_ATTRIBUTES = List.of(
            Attributes.MAX_HEALTH,
            Attributes.BURNING_TIME,
            Attributes.FALL_DAMAGE_MULTIPLIER,
            Attributes.SAFE_FALL_DISTANCE,
            Attributes.MAX_ABSORPTION,
            Attributes.MOVEMENT_SPEED,
            Attributes.JUMP_STRENGTH,
            Attributes.STEP_HEIGHT,
            Attributes.ATTACK_DAMAGE,
            Attributes.ATTACK_SPEED,
            Attributes.ARMOR,
            Attributes.ARMOR_TOUGHNESS,
            Attributes.KNOCKBACK_RESISTANCE,
            Attributes.ENTITY_INTERACTION_RANGE,
            Attributes.EXPLOSION_KNOCKBACK_RESISTANCE,
            Attributes.ATTACK_KNOCKBACK,
            Attributes.BLOCK_BREAK_SPEED,
            Attributes.SUBMERGED_MINING_SPEED,
            Attributes.LUCK,
            Attributes.BLOCK_INTERACTION_RANGE,
            Attributes.OXYGEN_BONUS,
            Attributes.WATER_MOVEMENT_EFFICIENCY
    );

    public static void syncPlayerModifiers(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        MarketSavedData data = MarketSavedData.get(server);
        PlayerMarketAccount account = data.getOrCreateAccount(player.getUUID());
        long currentTick = server.overworld().getGameTime();

        // 1. Clean all previous playermarket modifiers on managed attributes
        for (Holder<Attribute> holder : MANAGED_ATTRIBUTES) {
            AttributeInstance inst = player.getAttribute(holder);
            if (inst != null) {
                List<Identifier> toRemove = new ArrayList<>();
                for (AttributeModifier mod : inst.getModifiers()) {
                    if (mod.id().getNamespace().equals(PlayerMarketMod.MOD_ID)) {
                        toRemove.add(mod.id());
                    }
                }
                for (Identifier id : toRemove) {
                    inst.removeModifier(id);
                }
            }
        }

        // Ensure MAX_ABSORPTION has a baseline of 20.0 so selling percentages or buying absorption works cleanly
        AttributeInstance maxAbsInst = player.getAttribute(Attributes.MAX_ABSORPTION);
        if (maxAbsInst != null && maxAbsInst.getBaseValue() < 20.0) {
            maxAbsInst.setBaseValue(20.0);
        }

        boolean emergencyBatteryActive = account.isBlackMarketEffectActive(BlackMarketItem.EMERGENCY_BATTERY, currentTick);
        boolean assetInsuranceActive = account.isBlackMarketEffectActive(BlackMarketItem.ASSET_INSURANCE, currentTick);
        int marketInsuranceLvl = account.getUpgradeLevel(UpgradeType.MARKET_INSURANCE);

        // 2. Apply Sold Asset Debuffs
        if (!emergencyBatteryActive) {
            for (AssetType asset : AssetType.values()) {
                if (asset.getVanillaAttribute() == null || asset.getOperation() == null) continue;
                double permFrac = account.getPermanentSoldFraction(asset);
                double leaseFrac = account.getLeasedFraction(asset);
                if (marketInsuranceLvl > 0 && leaseFrac > 0.0) {
                    double shield = UpgradeType.MARKET_INSURANCE.getValueForLevel(marketInsuranceLvl);
                    leaseFrac *= (1.0 - shield * 0.5);
                }
                double totalFrac = Math.min(0.90, permFrac + leaseFrac);
                if (assetInsuranceActive) {
                    totalFrac *= 0.5;
                }
                if (totalFrac > 0.001) {
                    double amount = totalFrac * asset.getModifierScalePer100Percent();
                    addModifier(player, asset.getVanillaAttribute(), "sold_" + asset.getId(), amount, asset.getOperation());

                    // Special case: selling FALL_RESISTANCE also shrinks SAFE_FALL_DISTANCE from 3.0 down to 0.5 blocks
                    // so falling from 3, 2, or even 1 block deals damage!
                    if (asset == AssetType.FALL_RESISTANCE) {
                        double safeDistReduction = -2.75 * totalFrac;
                        addModifier(player, Attributes.SAFE_FALL_DISTANCE, "sold_safe_fall_dist", safeDistReduction, AttributeModifier.Operation.ADD_VALUE);
                    }
                }
            }

            // Lease Burden Penalty: active 3d-5d leases impose a corporate debt strain
            int longLeaseCount = 0;
            for (LeaseContract lease : account.getActiveLeases()) {
                if (lease.durationDays() >= 3) longLeaseCount++;
            }
            if (longLeaseCount > 0) {
                double strain = -0.04 * longLeaseCount;
                if (marketInsuranceLvl > 0) {
                    strain *= (1.0 - UpgradeType.MARKET_INSURANCE.getValueForLevel(marketInsuranceLvl) * 0.5);
                }
                addModifier(player, Attributes.MOVEMENT_SPEED, "lease_burden_speed", strain, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
                addModifier(player, Attributes.BLOCK_BREAK_SPEED, "lease_burden_mining", strain, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            }
        } else {
            // Emergency Battery grants +15% key stats while suppressing debuffs
            addModifier(player, Attributes.MOVEMENT_SPEED, "bm_emergency_speed", 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            addModifier(player, Attributes.ATTACK_DAMAGE, "bm_emergency_damage", 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            addModifier(player, Attributes.BLOCK_BREAK_SPEED, "bm_emergency_mining", 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        }

        // 3. Apply Active Dividends (Unique per dividend ID — never cancel out a sold stat)
        Set<String> appliedDividends = new HashSet<>();
        for (Map.Entry<String, String> entry : account.getSelectedDividends().entrySet()) {
            Optional<AssetType> assetOpt = AssetType.byId(entry.getKey());
            if (assetOpt.isEmpty() || account.getTotalSoldFraction(assetOpt.get()) <= 0.001) continue;
            String divId = entry.getValue();
            if (!appliedDividends.add(divId)) continue;
            applyDividendModifier(player, account, divId);
        }

        // 4. Apply Permanent Diamond Upgrades
        for (UpgradeType upgrade : UpgradeType.values()) {
            int lvl = account.getUpgradeLevel(upgrade);
            if (lvl <= 0 || upgrade.getVanillaAttribute() == null || upgrade.getOperation() == null) continue;
            double value = upgrade.getValueForLevel(lvl);
            addModifier(player, upgrade.getVanillaAttribute(), "upgrade_" + upgrade.getId(), value, upgrade.getOperation());
            if (upgrade == UpgradeType.REINFORCED_BONES) {
                addModifier(player, Attributes.SAFE_FALL_DISTANCE, "upgrade_safe_fall", 1.2 * lvl, AttributeModifier.Operation.ADD_VALUE);
            }
        }

        // 5. Apply Black Market Active Buffs
        if (account.isBlackMarketEffectActive(BlackMarketItem.BERSERKER_PROTOCOL, currentTick)) {
            addModifier(player, Attributes.ATTACK_DAMAGE, "bm_berserker_dmg", 0.40, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            addModifier(player, Attributes.ATTACK_SPEED, "bm_berserker_spd", 0.20, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            addModifier(player, Attributes.ARMOR, "bm_berserker_armor", -0.30, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        }
        if (account.isBlackMarketEffectActive(BlackMarketItem.PHANTOM_STEP, currentTick)) {
            addModifier(player, Attributes.MOVEMENT_SPEED, "bm_phantom_spd", 0.45, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            addModifier(player, Attributes.STEP_HEIGHT, "bm_phantom_step", 0.5, AttributeModifier.Operation.ADD_VALUE);
            addModifier(player, Attributes.FALL_DAMAGE_MULTIPLIER, "bm_phantom_fall", -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        }
        if (account.isBlackMarketEffectActive(BlackMarketItem.OVERCLOCKED_MIND, currentTick)) {
            addModifier(player, Attributes.BLOCK_BREAK_SPEED, "bm_overclock_mining", 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        }

        // Clamp current health if Max Health decreased
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    private static void applyDividendModifier(ServerPlayer player, PlayerMarketAccount account, String divId) {
        switch (divId) {
            case "adrenaline" -> {
                if (account.getTotalSoldFraction(AssetType.ATTACK_SPEED) <= 0.01)
                    addModifier(player, Attributes.ATTACK_SPEED, "div_adrenaline", 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            }
            case "pain_tolerance" -> {
                if (account.getTotalSoldFraction(AssetType.KNOCKBACK_RESISTANCE) <= 0.01)
                    addModifier(player, Attributes.KNOCKBACK_RESISTANCE, "div_pain_tolerance", 0.35, AttributeModifier.Operation.ADD_VALUE);
            }
            case "dense_bones" -> {
                if (account.getTotalSoldFraction(AssetType.ARMOR) <= 0.01)
                    addModifier(player, Attributes.ARMOR, "div_dense_bones", 3.5, AttributeModifier.Operation.ADD_VALUE);
            }
            case "thermal_overload" -> {
                if (account.getTotalSoldFraction(AssetType.ATTACK_DAMAGE) <= 0.01)
                    addModifier(player, Attributes.ATTACK_DAMAGE, "div_thermal_overload", 0.20, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            }
            case "aerodynamic_step" -> {
                if (account.getTotalSoldFraction(AssetType.JUMP_STRENGTH) <= 0.01)
                    addModifier(player, Attributes.JUMP_STRENGTH, "div_aero_step", 0.20, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            }
            case "reinforced_skin" -> {
                if (account.getTotalSoldFraction(AssetType.ARMOR) <= 0.01)
                    addModifier(player, Attributes.ARMOR, "div_reinforced_skin", 4.0, AttributeModifier.Operation.ADD_VALUE);
            }
            case "grounded_stance" -> {
                if (account.getTotalSoldFraction(AssetType.KNOCKBACK_RESISTANCE) <= 0.01)
                    addModifier(player, Attributes.KNOCKBACK_RESISTANCE, "div_grounded", 0.40, AttributeModifier.Operation.ADD_VALUE);
            }
            case "tactical_grip" -> {
                if (account.getTotalSoldFraction(AssetType.ATTACK_SPEED) <= 0.01)
                    addModifier(player, Attributes.ATTACK_SPEED, "div_tactical_grip", 0.20, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            }
            case "terrestrial_stride" -> {
                if (account.getTotalSoldFraction(AssetType.MOVEMENT_SPEED) <= 0.01)
                    addModifier(player, Attributes.MOVEMENT_SPEED, "div_terrestrial", 0.20, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            }
            case "low_center" -> {
                if (account.getTotalSoldFraction(AssetType.ARMOR_TOUGHNESS) <= 0.01)
                    addModifier(player, Attributes.ARMOR_TOUGHNESS, "div_low_center", 3.0, AttributeModifier.Operation.ADD_VALUE);
            }
            case "heavy_impact" -> {
                if (account.getTotalSoldFraction(AssetType.KNOCKBACK_POWER) <= 0.01)
                    addModifier(player, Attributes.ATTACK_KNOCKBACK, "div_heavy_impact", 0.60, AttributeModifier.Operation.ADD_VALUE);
            }
            case "plated_arms" -> {
                if (account.getTotalSoldFraction(AssetType.ARMOR) <= 0.01)
                    addModifier(player, Attributes.ARMOR, "div_plated_arms", 4.0, AttributeModifier.Operation.ADD_VALUE);
            }
            case "unburdened" -> {
                if (account.getTotalSoldFraction(AssetType.MOVEMENT_SPEED) <= 0.01)
                    addModifier(player, Attributes.MOVEMENT_SPEED, "div_unburdened", 0.20, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            }
            case "berserker_edge" -> {
                if (account.getTotalSoldFraction(AssetType.ATTACK_DAMAGE) <= 0.01)
                    addModifier(player, Attributes.ATTACK_DAMAGE, "div_berserker_edge", 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            }
            case "nimble_reflexes" -> {
                if (account.getTotalSoldFraction(AssetType.ATTACK_SPEED) <= 0.01)
                    addModifier(player, Attributes.ATTACK_SPEED, "div_nimble_reflexes", 0.20, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            }
            case "fortune_seeker" -> {
                if (account.getTotalSoldFraction(AssetType.LUCK) <= 0.01)
                    addModifier(player, Attributes.LUCK, "div_fortune_seeker", 2.5, AttributeModifier.Operation.ADD_VALUE);
            }
            case "recoil_boost" -> {
                if (account.getTotalSoldFraction(AssetType.MOVEMENT_SPEED) <= 0.01)
                    addModifier(player, Attributes.MOVEMENT_SPEED, "div_recoil_boost", 0.20, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            }
            case "builder_reach" -> {
                if (account.getTotalSoldFraction(AssetType.BLOCK_REACH) <= 0.01)
                    addModifier(player, Attributes.BLOCK_INTERACTION_RANGE, "div_builder_reach", 1.25, AttributeModifier.Operation.ADD_VALUE);
            }
            case "consistent_strikes" -> {
                if (account.getTotalSoldFraction(AssetType.ATTACK_DAMAGE) <= 0.01)
                    addModifier(player, Attributes.ATTACK_DAMAGE, "div_consistent", 0.20, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            }
            case "relentless_combo" -> {
                if (account.getTotalSoldFraction(AssetType.ATTACK_SPEED) <= 0.01)
                    addModifier(player, Attributes.ATTACK_SPEED, "div_relentless", 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            }
            case "combat_redirect" -> {
                if (account.getTotalSoldFraction(AssetType.ATTACK_DAMAGE) <= 0.01)
                    addModifier(player, Attributes.ATTACK_DAMAGE, "div_combat_redirect", 0.20, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            }
            case "surface_miner" -> {
                if (account.getTotalSoldFraction(AssetType.MINING_SPEED) <= 0.01)
                    addModifier(player, Attributes.BLOCK_BREAK_SPEED, "div_surface_miner", 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            }
            case "combat_reach" -> {
                if (account.getTotalSoldFraction(AssetType.ENTITY_REACH) <= 0.01)
                    addModifier(player, Attributes.ENTITY_INTERACTION_RANGE, "div_combat_reach", 1.2, AttributeModifier.Operation.ADD_VALUE);
            }
            case "natural_immunity" -> {
                if (account.getTotalSoldFraction(AssetType.MAX_HEALTH) <= 0.01)
                    addModifier(player, Attributes.MAX_HEALTH, "div_natural_immunity", 4.0, AttributeModifier.Operation.ADD_VALUE);
            }
            case "brawler_instinct" -> {
                if (account.getTotalSoldFraction(AssetType.ATTACK_DAMAGE) <= 0.01)
                    addModifier(player, Attributes.ATTACK_DAMAGE, "div_brawler", 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            }
            default -> {}
        }
    }

    private static void addModifier(ServerPlayer player, Holder<Attribute> attribute, String name, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = player.getAttribute(attribute);
        if (inst != null) {
            Identifier id = PlayerMarketMod.id(name);
            inst.removeModifier(id);
            inst.addTransientModifier(new AttributeModifier(id, amount, op));
        }
    }

    // === ECONOMIC CALCULATIONS (Server & Client Preview Identical Formula) ===

    public static long calculateSalePayoutCr(PlayerMarketAccount account, MarketEvent event, AssetType asset, double fraction, int leaseDays) {
        double base = asset.getFullValueCr() * fraction;

        // Lease vs Permanent multiplier (1 to 5 days)
        if (leaseDays > 0) {
            double leaseFactor = switch (leaseDays) {
                case 1 -> 0.40;
                case 2 -> 0.52;
                case 3 -> 0.65;
                case 5 -> 0.85;
                case 7 -> 0.90;
                default -> 0.40;
            };
            base *= leaseFactor;
        }

        // Market Event modifier
        double eventMult = event != null ? event.getAssetMultiplier(asset) : 1.0;
        int priceLockLvl = account.getUpgradeLevel(UpgradeType.PRICE_LOCK);
        if (priceLockLvl >= 1 && eventMult < 1.0) {
            eventMult = 1.0;
        }
        if (priceLockLvl == 2) eventMult = Math.max(eventMult, 1.05);
        else if (priceLockLvl >= 3) eventMult = Math.max(eventMult, 1.12);
        base *= eventMult;

        // Premium Account bonus
        int premLvl = account.getUpgradeLevel(UpgradeType.PREMIUM_ACCOUNT);
        if (premLvl > 0) {
            base *= (1.0 + UpgradeType.PREMIUM_ACCOUNT.getValueForLevel(premLvl));
        }

        // Golden Contract doubles next deal
        if (account.isGoldenContractArmed()) {
            base *= 2.0;
        }

        return Math.max(10L, Math.round(base));
    }

    public static long calculateBuybackCostCr(PlayerMarketAccount account, MarketEvent event, AssetType asset) {
        double permSold = account.getPermanentSoldFraction(asset);
        if (permSold <= 0.001) return 0L;

        long originalBasis = account.getPermanentSalePayoutBasis().getOrDefault(asset.getId(), Math.round(asset.getFullValueCr() * permSold));
        double commission = MarketConfig.get().buybackCommission; // Default 0.30 (500 -> 650)

        // Broker License discount
        int brokerLvl = account.getUpgradeLevel(UpgradeType.BROKER_LICENSE);
        if (brokerLvl > 0) {
            commission -= UpgradeType.BROKER_LICENSE.getValueForLevel(brokerLvl);
        }
        if (account.hasDividend("broker_instinct")) {
            commission -= 0.10;
        }
        commission = Math.max(0.05, commission);

        double total = originalBasis * (1.0 + commission);
        if (event != null) {
            double eventMult = event.getBuybackCostMultiplier();
            if (account.getUpgradeLevel(UpgradeType.PRICE_LOCK) >= 1 && eventMult > 1.0) {
                eventMult = 1.0;
            }
            total *= eventMult;
        }

        // Anti-exploit floor: buyback must always cost at least +15% over the payout received
        long minAntiExploitCost = Math.round(originalBasis * 1.15);
        total = Math.max(total, minAntiExploitCost);

        return Math.max(10L, Math.round(total));
    }

    public static int calculateUpgradeDiamondCost(PlayerMarketAccount account, MarketEvent event, UpgradeType upgrade, int targetLevel) {
        int baseCost = upgrade.getDiamondCostForLevel(targetLevel);
        double mult = 1.0;
        if (event == MarketEvent.CORPORATE_AUDIT || event == MarketEvent.PREMIUM_WEEK) {
            mult *= 0.85;
        }
        int vipLvl = account.getUpgradeLevel(UpgradeType.VIP_CLIENT);
        if (vipLvl == 2) mult *= 0.92;
        else if (vipLvl >= 3) mult *= 0.85;

        return Math.max(1, (int) Math.round(baseCost * mult));
    }

    // === VANILLA DIAMOND INVENTORY HELPERS (Strictly minecraft:diamond regardless of mod origin) ===

    public static int countVanillaDiamonds(Inventory inventory) {
        int total = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.is(Items.DIAMOND)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public static boolean consumeVanillaDiamonds(Inventory inventory, int requiredAmount) {
        if (requiredAmount <= 0) return true;
        if (countVanillaDiamonds(inventory) < requiredAmount) return false;

        int remaining = requiredAmount;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.is(Items.DIAMOND)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
                if (stack.isEmpty()) {
                    inventory.setItem(i, ItemStack.EMPTY);
                }
                if (remaining <= 0) break;
            }
        }
        inventory.setChanged();
        return remaining == 0;
    }

    // === ORE SCANNER TELEMETRY ===

    public static String computeOreScannerReport(ServerPlayer player, PlayerMarketAccount account) {
        int lvl = account.getUpgradeLevel(UpgradeType.ORE_SCANNER);
        if (lvl <= 0) return "OFFLINE";
        int radius = (int) Math.round(UpgradeType.ORE_SCANNER.getValueForLevel(lvl));
        BlockPos center = player.blockPosition();
        int diamond = 0, emerald = 0, gold = 0, iron = 0, redstone = 0, coal = 0, debris = 0;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockState state = player.level().getBlockState(center.offset(dx, dy, dz));
                    Block b = state.getBlock();
                    if (b == Blocks.DIAMOND_ORE || b == Blocks.DEEPSLATE_DIAMOND_ORE) diamond++;
                    else if (b == Blocks.EMERALD_ORE || b == Blocks.DEEPSLATE_EMERALD_ORE) emerald++;
                    else if (b == Blocks.GOLD_ORE || b == Blocks.DEEPSLATE_GOLD_ORE || b == Blocks.NETHER_GOLD_ORE) gold++;
                    else if (b == Blocks.IRON_ORE || b == Blocks.DEEPSLATE_IRON_ORE) iron++;
                    else if (b == Blocks.REDSTONE_ORE || b == Blocks.DEEPSLATE_REDSTONE_ORE) redstone++;
                    else if (b == Blocks.COAL_ORE || b == Blocks.DEEPSLATE_COAL_ORE) coal++;
                    else if (b == Blocks.ANCIENT_DEBRIS) debris++;
                }
            }
        }
        return String.format(Locale.ROOT, "R:%dм | ♦:%d ИЗУМ:%d ОБЛ:%d ЗОЛ:%d ЖЕЛ:%d РЕД:%d УГ:%d",
                radius, diamond, emerald, debris, gold, iron, redstone, coal);
    }

    // === SERVER TICK HANDLER ===

    public static void tickServer(MinecraftServer server) {
        com.playermarket.story.EndingSequenceManager.tickServer(server);

        MarketSavedData data = MarketSavedData.get(server);
        long gameTime = server.overworld().getGameTime();

        // Check dynamic Market Event rotation (every 10 minutes / 12,000 ticks)
        if (gameTime >= data.getNextEventSwitchTick() || data.getActiveEvent() == MarketEvent.STABLE_MARKET) {
            MarketEvent[] events = MarketEvent.values();
            MarketEvent current = data.getActiveEvent();
            MarketEvent next = current;
            int attempts = 0;
            while ((next == current || next == MarketEvent.STABLE_MARKET) && attempts < 30) {
                next = events[server.overworld().getRandom().nextInt(events.length)];
                attempts++;
            }
            data.setActiveEvent(next, gameTime);

            boolean isCrash = next.isNegativeEvent();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (isCrash) {
                    p.sendOverlayMessage(Component.literal("§c§l⚡ ОБВАЛ РЫНКА: " + next.getHeadline() + " ⚡"));
                    p.level().playSound(null, p.getX(), p.getY(), p.getZ(),
                            SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.6f, 1.6f);
                } else {
                    p.sendOverlayMessage(Component.literal("§a§l⚡ БИРЖЕВОЙ БУМ: " + next.getHeadline() + " ⚡"));
                    p.level().playSound(null, p.getX(), p.getY(), p.getZ(),
                            SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.7f, 1.1f);
                }
                p.sendSystemMessage(Component.literal("§8[§6БИРЖЕВОЙ ВЕСТНИК§8] §eВНИМАНИЕ! Смена рыночного цикла:\n"
                        + "§f▶ §e§l" + next.getHeadline() + "\n"
                        + "§7" + next.getDescription()));
                MarketNetworking.sendSyncToPlayer(p);
            }
        }

        // Tick each online player once per second (20 ticks)
        if (gameTime % 20L == 0L) {
            boolean isMinuteTick = (gameTime % 1200L == 0L);

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                PlayerMarketAccount account = data.getOrCreateAccount(player.getUUID());
                boolean needsSync = false;

                // 1. Progress survival contract
                account.progressContracts("SURVIVE_TICKS", 20);

                // 2. Check & enforce Contract Deadlines for ACTIVE accepted contracts only (1-5 in-game days)
                List<ContractEntry> contracts = account.getContracts();
                for (int i = 0; i < contracts.size(); i++) {
                    ContractEntry c = contracts.get(i);
                    if (c.active() && !c.completed() && !c.claimed() && c.deadlineTick() > 0L && gameTime >= c.deadlineTick()) {
                        long basePenalty = c.penaltyCr();
                        long wealthTax = (long) (account.getCrBalance() * 0.05); // 5% Progressive Wealth Tax
                        long totalFine = basePenalty + Math.max(0L, wealthTax);
                        long fine = account.forcePenaltyCr(totalFine);
                        account.addTransaction(gameTime, "ШТРАФ И НАЛОГ", c.title(),
                                "Просрочен дедлайн принятого контракта (" + c.durationDays() + " дн.) [База: -" + basePenalty + " | Налог 5%: -" + wealthTax + " CR]", -fine, 0);
                        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 0));
                        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 600, 0));
                        player.sendSystemMessage(Component.literal("§8[§cPLAYER MARKET§8] §cПринятый контракт просрочен: §f" + c.title()
                                + "§c! Штраф: §e-" + basePenalty + " CR §8+ §cНалог (5%): §e-" + wealthTax + " CR §8(§cВсего: §e-" + fine + " CR§8)§c."));
                        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                                SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.45f, 0.8f);
                        contracts.set(i, c.resetForNewCycle(gameTime));
                        needsSync = true;
                    }
                }

                // 3. Check lease expirations
                Iterator<LeaseContract> it = account.getActiveLeases().iterator();
                while (it.hasNext()) {
                    LeaseContract lease = it.next();
                    if (gameTime >= lease.endTick()) {
                        it.remove();
                        needsSync = true;
                        AssetType.byId(lease.assetId()).ifPresent(asset -> {
                            account.addTransaction(gameTime, "ЛИЗИНГ ЗАВЕРШЁН", asset.getId(),
                                    String.format(Locale.ROOT, "Возвращено %.0f%% характеристики", lease.fraction() * 100.0), 0L, 0);
                            player.sendSystemMessage(Component.literal("§8[§aPLAYER MARKET§8] §aСрок аренды истёк: §f" + asset.getId() + " §aполностью возвращён вам."));
                        });
                    }
                }

                // 4. Check Black Market timed effect expirations
                Iterator<Map.Entry<String, Long>> bmIt = account.getBlackMarketActiveUntil().entrySet().iterator();
                while (bmIt.hasNext()) {
                    Map.Entry<String, Long> entry = bmIt.next();
                    if (gameTime >= entry.getValue()) {
                        bmIt.remove();
                        needsSync = true;
                    }
                }

                // 5. Passive CR/min Dividends payout every 60 seconds
                if (isMinuteTick) {
                    long divCr = account.calculatePassiveDividendPerMinuteCr();
                    if (divCr > 0L) {
                        account.addCr(divCr);
                        account.addTransaction(gameTime, "ДИВИДЕНДЫ", "Пассивный доход",
                                "Ежеминутная выплата по проданным активам", divCr, 0);
                        needsSync = true;
                    }
                }

                // 6. Active Hunger & Saturation Drain with strict safety floor (no infinite starvation!)
                if (!player.isCreative() && !player.isSpectator()) {
                    double soldSat = account.getTotalSoldFraction(AssetType.SATURATION_EFFICIENCY);
                    double soldHunger = account.getTotalSoldFraction(AssetType.HUNGER_EFFICIENCY);
                    int activeLeases = account.getActiveLeases().size();

                    if (soldSat > 0.01 || soldHunger > 0.01 || activeLeases > 0) {
                        FoodData food = player.getFoodData();
                        int currentFood = food.getFoodLevel();

                        // Safety floor: hunger will NEVER passively drain below minSafeFood (3 to 5 drumsticks)
                        // This prevents endless starvation to death, matching how hearts stop at 4 HP!
                        int minSafeFood = Math.max(6, (int) Math.round(20.0 * (1.0 - soldHunger * 0.65)));

                        if (soldSat > 0.01) {
                            float maxAllowedSat = (float) (20.0 * (1.0 - soldSat * 0.75));
                            if (food.getSaturationLevel() > maxAllowedSat) {
                                food.setSaturation(maxAllowedSat);
                            }
                        }

                        // Only apply passive drain if player is above the safety floor
                        if (currentFood > minSafeFood) {
                            double drainExhaustion = (soldHunger * 1.05) + (soldSat * 0.50) + (activeLeases * 0.20);
                            int effMetaLvl = account.getUpgradeLevel(UpgradeType.EFFICIENT_METABOLISM);
                            int metaUpgLvl = account.getUpgradeLevel(UpgradeType.METABOLISM_UPGRADE);
                            double reduction = 1.0;
                            if (effMetaLvl > 0) reduction -= UpgradeType.EFFICIENT_METABOLISM.getValueForLevel(effMetaLvl) * 0.5;
                            if (metaUpgLvl > 0) reduction -= UpgradeType.METABOLISM_UPGRADE.getValueForLevel(metaUpgLvl) * 0.4;
                            if (account.hasDividend("steady_pace")) reduction -= 0.15;

                            float finalExh = (float) (drainExhaustion * Math.max(0.25, reduction));
                            if (finalExh > 0.02F) {
                                player.causeFoodExhaustion(finalExh);
                            }
                        }
                    }
                }

                // 7. Custom non-attribute debuff for SWIM_SPEED when in water
                double swimSold = account.getTotalSoldFraction(AssetType.SWIM_SPEED);
                if (swimSold > 0.01 && player.isInWater()) {
                    double factor = Math.max(0.35, 1.0 - swimSold * 0.45);
                    player.setDeltaMovement(player.getDeltaMovement().multiply(factor, 1.0, factor));
                }

                // 8. Custom OXYGEN_CAPACITY penalty when underwater
                double oxySold = account.getTotalSoldFraction(AssetType.OXYGEN_CAPACITY);
                if (oxySold > 0.01 && player.isUnderWater()) {
                    int extraAirLoss = (int) Math.round(oxySold * 12.0);
                    player.setAirSupply(Math.max(-20, player.getAirSupply() - extraAirLoss));
                }

                // Periodically check story tasks & dynamic corporate situations
                if (gameTime % 40L == 0L) {
                    com.playermarket.story.StoryManager.checkAllQuests(player, account);
                }

                // Sync every 5 seconds for HUD timers or immediately if state changed
                if (needsSync || (gameTime % 100L == 0L)) {
                    if (needsSync) {
                        data.setDirty();
                        syncPlayerModifiers(player);
                    }
                    MarketNetworking.sendSyncToPlayer(player);
                }
            }
        }
    }
}
