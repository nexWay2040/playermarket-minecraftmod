package com.playermarket;

import com.playermarket.command.MarketCommands;
import com.playermarket.config.MarketConfig;
import com.playermarket.item.MarketAccessCardItem;
import com.playermarket.market.*;
import com.playermarket.network.MarketNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class PlayerMarketMod implements ModInitializer {
    public static final String MOD_ID = "playermarket";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final SoundEvent SCREAMER_SOUND = SoundEvent.createVariableRangeEvent(id("screamer"));

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing PLAYER MARKET — Everything Has A Price.");

        Registry.register(BuiltInRegistries.SOUND_EVENT, id("screamer"), SCREAMER_SOUND);
        MarketConfig.load();
        MarketAccessCardItem.registerItems();
        com.playermarket.item.CrumpledLetterItem.registerItems();
        com.playermarket.item.BlockedMarketCardItem.registerItems();
        com.playermarket.item.MarketPropItems.registerItems();
        MarketNetworking.registerServer();

        // Register /market commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            MarketCommands.register(dispatcher);
        });

        // Clear stale static runtime state before/after world sessions and init Valuation Engine
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            com.playermarket.story.EndingSequenceManager.resetRuntimeState();
            com.playermarket.story.CorporateChaosDirector.resetRuntimeState();
            com.playermarket.story.CorporateTaskTracker.clearAll();
            MarketAttributeApplier.clearScannerCache();
            ResourceValuationEngine.ensureInitialized(server);
        });
        ServerLifecycleEvents.SERVER_STARTED.register(ResourceValuationEngine::ensureInitialized);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            com.playermarket.story.CorporateChaosDirector.clearAllWorldEntities(server);
            com.playermarket.story.CorporateChaosDirector.resetRuntimeState();
            com.playermarket.story.EndingSequenceManager.resetRuntimeState();
            com.playermarket.story.CorporateTaskTracker.clearAll();
            MarketAttributeApplier.clearScannerCache();
        });

        // Server tick for leases, market events, and custom attribute effects
        ServerTickEvents.END_SERVER_TICK.register(MarketAttributeApplier::tickServer);

        // Sync player modifiers and grant crumpled letter / market card on join
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            server.execute(() -> {
                if (player.hasDisconnected()) return;
                try {
                    ResourceValuationEngine.ensureInitialized(server);
                    MarketSavedData data = MarketSavedData.get(server);
                    PlayerMarketAccount account = data.getOrCreateAccount(player.getUUID());
                    data.ensureAccountRegistered(player.getUUID(), account);

                    long gameTick = server.overworld().getGameTime();
                    com.playermarket.story.StoryManager.initializeAccountStory(account, gameTick);

                    // Safe 1-time delivery of crumpled introductory letter (only if campaign not yet accepted)
                    if (!data.getCampaignData().isCampaignAccepted()) {
                        grantCrumpledLetterSafely(player, account);
                    } else {
                        // Campaign already started: give card + latecomer welcome credits/mails immediately
                        if (!account.isMarketCardBlocked() && !account.isPermanentImpaired()) {
                            grantStarterCardSafely(player);
                        }
                        com.playermarket.story.StoryManager.syncLateJoiner(player, account, data.getCampaignData(), gameTick);
                    }

                    if (account.getHistory().isEmpty() && data.getCampaignData().isCampaignAccepted()) {
                        account.addTransaction(gameTick,
                                "ОТКРЫТИЕ СЧЁТА", "Стартовый капитал",
                                "Карта доступа и базовый баланс", account.getCrBalance(), 0);
                        data.setDirty();
                    }

                    MarketAttributeApplier.syncPlayerModifiers(player);
                    com.playermarket.story.StoryManager.checkAllQuests(player, account);
                    com.playermarket.story.EndingSequenceManager.onPlayerJoin(player);
                    MarketNetworking.sendSyncToPlayer(player);
                } catch (Throwable t) {
                    LOGGER.error("Failed to run join sync for player {}", player.getName().getString(), t);
                }
            });
        });

        // Re-apply modifiers after player respawn, restore card or pre-start letter, handle lease death risk
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (newPlayer.hasDisconnected()) return;
            try {
                if (newPlayer.level().getServer() != null) {
                    MarketSavedData data = MarketSavedData.get(newPlayer.level().getServer());
                    PlayerMarketAccount account = data.getOrCreateAccount(newPlayer.getUUID());
                    if (!data.getCampaignData().isCampaignAccepted()) {
                        account.setReceivedCrumpledLetter(false);
                        grantCrumpledLetterSafely(newPlayer, account);
                    } else {
                        grantStarterCardSafely(newPlayer);
                    }
                    if (!alive && !com.playermarket.story.StoryManager.isProgramTerminated(data.getCampaignData(), account)) {
                        int activeLeaseCount = account.getActiveLeases().size();
                        if (activeLeaseCount > 0) {
                            long penalty = (350L * activeLeaseCount) + Math.round(account.getCrBalance() * 0.10);
                            long deducted = account.forcePenaltyCr(penalty);
                            if (deducted > 0) {
                                account.addTransaction(newPlayer.level().getServer().overworld().getGameTime(),
                                        "РИСК ЛИЗИНГА", "Смерть оператора",
                                        "Неустойка по активным договорам аренды (+10% капитала)", -deducted, 0);
                                newPlayer.sendSystemMessage(Component.literal("§8[§cPLAYER MARKET§8] §cСтраховой сбор: Смерть при активном лизинге. Удержано §e-"
                                        + deducted + " CR §cнеустойки (§e350×" + activeLeaseCount + " +10%§c)."));
                            }
                        }
                    }
                }
                MarketAttributeApplier.syncPlayerModifiers(newPlayer);
                MarketNetworking.sendSyncToPlayer(newPlayer);
            } catch (Throwable t) {
                LOGGER.error("Failed AFTER_RESPAWN sync for {}", newPlayer.getName().getString(), t);
            }
        });

        // Track entity interactions: Courier / Piggy / Pinata / Inspector / Allay + Creeper + Merchant
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClientSide() && player instanceof ServerPlayer sp && sp.level().getServer() != null && !sp.isSpectator()) {
                ItemStack held = sp.getItemInHand(hand);
                String entityPath = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
                MarketSavedData data = MarketSavedData.get(sp.level().getServer());
                PlayerMarketAccount acc = data.getOrCreateAccount(sp.getUUID());
                var campaign = data.getCampaignData();
                if (com.playermarket.story.StoryManager.isProgramTerminated(campaign, acc)) {
                    return InteractionResult.PASS;
                }

                long gameTick = sp.level().getServer().overworld().getGameTime();
                if (com.playermarket.story.ChaosProp3DManager.handleHitboxTrigger(sp, entity, data, campaign, gameTick)) {
                    data.setDirty();
                    return InteractionResult.SUCCESS;
                }
                if (entity instanceof ServerPlayer targetSp
                        && (com.playermarket.story.CorporateChaosDirector.handleHotPotatoPass(sp, targetSp, data, gameTick)
                        || com.playermarket.story.CorporateChaosDirector.handleBountyCrownSteal(sp, targetSp, data, gameTick))) {
                    data.setDirty();
                    return InteractionResult.SUCCESS;
                }
                if (com.playermarket.story.CorporateChaosDirector.handleChaosMobInteraction(sp, entity, held, data, false, gameTick)) {
                    data.setDirty();
                    return InteractionResult.SUCCESS;
                }

                boolean updated = false;
                if (held.is(Items.FLINT_AND_STEEL) && "creeper".equals(entityPath)) {
                    campaign.addContribution("downsizing_stunt", 1);
                    com.playermarket.story.CorporateTaskTracker.advanceContract(sp, acc, "DOWNSIZE_ACT", 1);
                    updated = true;
                }
                if (entity instanceof Merchant) {
                    campaign.setObjective("villager_trade", 1);
                    updated = true;
                }
                if (updated) {
                    data.setDirty();
                    com.playermarket.story.StoryManager.checkAllQuests(sp, acc);
                    MarketNetworking.sendSyncToPlayer(sp);
                }
            }
            return InteractionResult.PASS;
        });

        // Left-click / punch support for 3D World Props (ATM Safe, Crate, Mystery Capsule) + Hot Potato / Crown steals
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClientSide() && player instanceof ServerPlayer sp && sp.level().getServer() != null && !sp.isSpectator()) {
                MarketSavedData data = MarketSavedData.get(sp.level().getServer());
                PlayerMarketAccount acc = data.getOrCreateAccount(sp.getUUID());
                var campaign = data.getCampaignData();
                if (com.playermarket.story.StoryManager.isProgramTerminated(campaign, acc)) {
                    return InteractionResult.PASS;
                }
                long gameTick = sp.level().getServer().overworld().getGameTime();
                if (com.playermarket.story.ChaosProp3DManager.handleHitboxTrigger(sp, entity, data, campaign, gameTick)) {
                    data.setDirty();
                    return InteractionResult.SUCCESS;
                }
                if (entity instanceof ServerPlayer targetSp
                        && (com.playermarket.story.CorporateChaosDirector.handleBountyCrownSteal(sp, targetSp, data, gameTick)
                        || com.playermarket.story.CorporateChaosDirector.handleHotPotatoPass(sp, targetSp, data, gameTick))) {
                    data.setDirty();
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.PASS;
        });

        // Track ore mining & deepslate work for Contracts & Active Story Quests
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!world.isClientSide() && player instanceof ServerPlayer sp && sp.level().getServer() != null && !sp.isCreative()) {
                MarketSavedData data = MarketSavedData.get(sp.level().getServer());
                PlayerMarketAccount acc = data.getOrCreateAccount(sp.getUUID());
                com.playermarket.story.SharedCampaignData campaign = data.getCampaignData();
                if (com.playermarket.story.StoryManager.isProgramTerminated(campaign, acc)) {
                    return;
                }
                boolean changed = false;

                if (pos.getY() < 0) {
                    campaign.addContribution("deepslate_work", 1);
                    changed = true;
                }

                Block b = state.getBlock();
                if (isOreBlock(b)) {
                    com.playermarket.story.CorporateTaskTracker.advanceContract(sp, acc, "MINE_ORE", 1);
                    campaign.addContribution("ore_mined", 1);

                    if (pos.getY() <= 24) {
                        campaign.addContribution("deep_ore_mined", 1);
                    }
                    if (pos.getY() < 0) {
                        campaign.addContribution("deep_cavity_mined", 1);
                    }
                    if (b == Blocks.GOLD_ORE || b == Blocks.DEEPSLATE_GOLD_ORE || b == Blocks.NETHER_GOLD_ORE) {
                        campaign.addContribution("gold_ore_mined", 1);
                    }
                    if (b == Blocks.DIAMOND_ORE || b == Blocks.DEEPSLATE_DIAMOND_ORE) {
                        campaign.addContribution("diamond_ore_mined", 1);
                    }
                    if (b == Blocks.NETHER_QUARTZ_ORE || b == Blocks.ANCIENT_DEBRIS) {
                        campaign.addContribution("nether_ore_mined", 1);
                    }
                    long curGameTick = sp.level().getServer().overworld().getGameTime();
                    if ("turbo_shift".equals(data.getActiveChaosId()) && curGameTick < data.getActiveChaosEndsTick()) {
                        long oreCr = MarketAttributeApplier.scaleBioCr(acc, 20L);
                        acc.addCr(oreCr);
                        if (world instanceof net.minecraft.server.level.ServerLevel sl && sl.getRandom().nextInt(100) < 35) {
                            sl.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
                                    sl, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new ItemStack(Items.GOLD_NUGGET, 1)));
                        }
                        sp.sendOverlayMessage(Component.literal("§6⚡ ЗОЛОТАЯ ЛИХОРАДКА: §a+" + oreCr + " CR §7за руду!"));
                    }
                    changed = true;
                }
                if (changed) {
                    data.setDirty();
                    com.playermarket.story.StoryManager.checkAllQuests(sp, acc);
                }
            }
        });

        // Track hostile kills, stunt kills & Black Market effects
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, entity, killedEntity, damageSource) -> {
            if (entity instanceof ServerPlayer sp && world.getServer() != null) {
                MarketSavedData data = MarketSavedData.get(world.getServer());
                PlayerMarketAccount acc = data.getOrCreateAccount(sp.getUUID());
                com.playermarket.story.SharedCampaignData campaign = data.getCampaignData();
                if (com.playermarket.story.StoryManager.isProgramTerminated(campaign, acc)) {
                    return;
                }
                long gameTick = world.getServer().overworld().getGameTime();

                if (killedEntity.entityTags().contains("playermarket_collector")) {
                    long colCr = MarketAttributeApplier.scaleBioCr(acc, 320L);
                    acc.addCr(colCr);
                    int diaBonus = acc.getSoldAssetCount() >= 2 ? 2 : (acc.getSoldAssetCount() >= 1 ? 1 : 0);
                    if (diaBonus > 0) {
                        ItemStack dia = new ItemStack(Items.DIAMOND, diaBonus);
                        if (!sp.getInventory().add(dia)) {
                            sp.drop(dia, false, net.minecraft.util.Prediction.SERVER_ONLY);
                        }
                    }
                    acc.addTransaction(gameTick, "КОЛЛЕКТОР ПОВЕРЖЕН", "Инкассатор Asset Corp",
                            "Трофей с корпоративного рейда", colCr, diaBonus);
                    campaign.addContribution("collectors_defeated", 1);
                    campaign.addContribution("courier_caught", 1);
                    com.playermarket.story.CorporateTaskTracker.advanceContract(sp, acc, "COURIER_LOOT", 1);
                    if (world instanceof net.minecraft.server.level.ServerLevel sl) {
                        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING,
                                killedEntity.getX(), killedEntity.getY() + 1.0, killedEntity.getZ(), 24, 0.4, 0.5, 0.4, 0.2);
                    }
                    world.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
                            "§8[§aКОЛЛЕКТОР ПОВЕРЖЕН§8] §f" + sp.getName().getString() + " §aотбился от коллектора: §e+" + colCr + " CR §8| §b+" + diaBonus + " ♦!"), false);
                    data.setDirty();
                    com.playermarket.story.StoryManager.checkAllQuests(sp, acc);
                    MarketNetworking.sendSyncToPlayer(sp);
                }

                if (com.playermarket.story.CorporateChaosDirector.handleCourierKilled(killedEntity, sp, data, gameTick)) {
                    data.setDirty();
                }

                if (killedEntity instanceof Enemy && !sp.isCreative()) {
                    com.playermarket.story.CorporateTaskTracker.advanceContract(sp, acc, "KILL_MOBS", 1);
                    campaign.addContribution("hostile_kills", 1);

                    if (data.getActiveEvent() != MarketEvent.STABLE_MARKET) {
                        campaign.addContribution("event_combat_kills", 1);
                    }
                    boolean isStunt = killedEntity.isOnFire()
                            || sp.isInWater()
                            || (sp.fallDistance > 0.0 && !sp.onGround())
                            || data.getActiveEvent() != MarketEvent.STABLE_MARKET;
                    if (isStunt) {
                        campaign.addContribution("downsizing_stunt", 1);
                        com.playermarket.story.CorporateTaskTracker.advanceContract(sp, acc, "STUNT_KILLS", 1);
                    }
                    if (killedEntity.isOnFire()) {
                        com.playermarket.story.CorporateTaskTracker.advanceContract(sp, acc, "DOWNSIZE_ACT", 1);
                    }
                    if (acc.getSoldAssetCount() >= 3) {
                        campaign.addContribution("handicapped_combat", 1);
                    }

                    String entityPath = BuiltInRegistries.ENTITY_TYPE.getKey(killedEntity.getType()).getPath();
                    if (entityPath.contains("skeleton")) {
                        campaign.addContribution("skeleton_kills", 1);
                    }
                    if (entityPath.equals("enderman") || entityPath.equals("witch") || entityPath.equals("ravager")) {
                        campaign.addContribution("apex_kills", 1);
                    }
                    data.setDirty();
                    com.playermarket.story.StoryManager.checkAllQuests(sp, acc);
                    MarketNetworking.sendSyncToPlayer(sp);
                }

                if (acc.isBlackMarketEffectActive(BlackMarketItem.SYNTHETIC_HEART, gameTick)) {
                    sp.heal(4.0F);
                    sp.setAbsorptionAmount(Math.min(sp.getMaxAbsorption(), sp.getAbsorptionAmount() + 2.0F));
                }
            }
        });
    }

    private static boolean isOreBlock(Block b) {
        return b == Blocks.COAL_ORE || b == Blocks.DEEPSLATE_COAL_ORE
                || b == Blocks.IRON_ORE || b == Blocks.DEEPSLATE_IRON_ORE
                || b == Blocks.COPPER_ORE || b == Blocks.DEEPSLATE_COPPER_ORE
                || b == Blocks.GOLD_ORE || b == Blocks.DEEPSLATE_GOLD_ORE || b == Blocks.NETHER_GOLD_ORE
                || b == Blocks.REDSTONE_ORE || b == Blocks.DEEPSLATE_REDSTONE_ORE
                || b == Blocks.LAPIS_ORE || b == Blocks.DEEPSLATE_LAPIS_ORE
                || b == Blocks.DIAMOND_ORE || b == Blocks.DEEPSLATE_DIAMOND_ORE
                || b == Blocks.EMERALD_ORE || b == Blocks.DEEPSLATE_EMERALD_ORE
                || b == Blocks.NETHER_QUARTZ_ORE || b == Blocks.ANCIENT_DEBRIS;
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public static void grantCrumpledLetterSafely(ServerPlayer player, PlayerMarketAccount account) {
        if (!account.hasReceivedCrumpledLetter()) {
            boolean hasLetter = false;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack s = player.getInventory().getItem(i);
                if (!s.isEmpty() && s.is(com.playermarket.item.CrumpledLetterItem.CRUMPLED_LETTER)) {
                    hasLetter = true;
                    break;
                }
            }
            if (!hasLetter) {
                ItemStack letter = new ItemStack(com.playermarket.item.CrumpledLetterItem.CRUMPLED_LETTER);
                if (player.getInventory().getItem(7).isEmpty()) {
                    player.getInventory().setItem(7, letter);
                } else if (!player.getInventory().add(letter)) {
                    player.drop(letter, false, net.minecraft.util.Prediction.SERVER_ONLY);
                }
            }
            account.setReceivedCrumpledLetter(true);
        }
    }

    public static void grantStarterCardSafely(ServerPlayer player) {
        if (player.level().getServer() != null) {
            MarketSavedData data = MarketSavedData.get(player.level().getServer());
            PlayerMarketAccount acc = data.getOrCreateAccount(player.getUUID());
            if (com.playermarket.story.StoryManager.isProgramTerminated(data.getCampaignData(), acc)
                    || !data.getCampaignData().isCampaignAccepted()) {
                return;
            }
        }

        // 1. Check if player has Market Access Card
        boolean hasCard = false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!s.isEmpty() && s.is(MarketAccessCardItem.MARKET_ACCESS_CARD)) {
                hasCard = true;
                break;
            }
        }
        if (!hasCard) {
            ItemStack card = new ItemStack(MarketAccessCardItem.MARKET_ACCESS_CARD);
            // Place in hotbar slot 8 (key 9) if empty, leaving slot 0 free for tools/Upgrader
            if (player.getInventory().getItem(8).isEmpty()) {
                player.getInventory().setItem(8, card);
            } else if (!player.getInventory().add(card)) {
                player.drop(card, false, net.minecraft.util.Prediction.SERVER_ONLY);
            }
        }

        // 2. Upgrader mod compatibility:
        // Ensure player has the Upgrader item (upgrader:upgrader) in slot 0 if not present
        if (FabricLoader.getInstance().isModLoaded("upgrader")) {
            try {
                Identifier upgraderId = Identifier.fromNamespaceAndPath("upgrader", "upgrader");
                Optional<net.minecraft.core.Holder.Reference<Item>> holder = BuiltInRegistries.ITEM.get(upgraderId);
                if (holder.isPresent()) {
                    Item upgraderItem = holder.get().value();
                    if (upgraderItem != null && upgraderItem != Items.AIR) {
                        boolean hasUpgrader = false;
                        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                            ItemStack s = player.getInventory().getItem(i);
                            if (!s.isEmpty() && s.is(upgraderItem)) {
                                hasUpgrader = true;
                                break;
                            }
                        }
                        if (!hasUpgrader) {
                            ItemStack upgraderStack = new ItemStack(upgraderItem);
                            if (player.getInventory().getItem(0).isEmpty()) {
                                player.getInventory().setItem(0, upgraderStack);
                            } else if (!player.getInventory().add(upgraderStack)) {
                                player.drop(upgraderStack, false, net.minecraft.util.Prediction.SERVER_ONLY);
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                LOGGER.error("Failed to check or grant upgrader item", t);
            }
        }
    }
}
