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
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class PlayerMarketMod implements ModInitializer {
    public static final String MOD_ID = "playermarket";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing PLAYER MARKET — Everything Has A Price.");

        MarketConfig.load();
        MarketAccessCardItem.registerItems();
        MarketNetworking.registerServer();

        // Register /market commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            MarketCommands.register(dispatcher);
        });

        // Initialize Recipe Valuation Engine on server start
        ServerLifecycleEvents.SERVER_STARTED.register(ResourceValuationEngine::ensureInitialized);

        // Server tick for leases, market events, and custom attribute effects
        ServerTickEvents.END_SERVER_TICK.register(MarketAttributeApplier::tickServer);

        // Sync player modifiers and grant initial Market Access Card on first join
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            server.execute(() -> {
                MarketSavedData data = MarketSavedData.get(server);
                PlayerMarketAccount account = data.getOrCreateAccount(player.getUUID());
                long gameTick = server.overworld().getGameTime();
                com.playermarket.story.StoryManager.initializeAccountStory(account, gameTick);
                if (account.getHistory().isEmpty()) {
                    // Grant starter Market Access Card on first ever join
                    grantStarterCardSafely(player);
                    account.addTransaction(server.overworld().getGameTime(),
                            "ОТКРЫТИЕ СЧЁТА", "Стартовый капитал",
                            "Карта доступа и базовый баланс", account.getCrBalance(), 0);
                    data.setDirty();
                }
                MarketAttributeApplier.syncPlayerModifiers(player);
                com.playermarket.story.StoryManager.checkAllQuests(player, account);
                com.playermarket.story.EndingSequenceManager.onPlayerJoin(player);
                MarketNetworking.sendSyncToPlayer(player);
            });
        });

        // Re-apply modifiers after player respawn, grant card and handle lease death risk
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            // Always guarantee player has their Market Access Card (and Upgrader) on respawn
            grantStarterCardSafely(newPlayer);

            if (!alive && newPlayer.level().getServer() != null) {
                MarketSavedData data = MarketSavedData.get(newPlayer.level().getServer());
                PlayerMarketAccount account = data.getOrCreateAccount(newPlayer.getUUID());
                int activeLeaseCount = account.getActiveLeases().size();
                if (activeLeaseCount > 0) {
                    long penalty = 150L * activeLeaseCount;
                    long deducted = account.forcePenaltyCr(penalty);
                    if (deducted > 0) {
                        account.addTransaction(newPlayer.level().getServer().overworld().getGameTime(),
                                "РИСК ЛИЗИНГА", "Смерть оператора",
                                "Неустойка по активным договорам аренды", -deducted, 0);
                        newPlayer.sendSystemMessage(Component.literal("§8[§cPLAYER MARKET§8] §cСтраховой сбор: Смерть при активном лизинге. Удержано §e-"
                                + deducted + " CR §cнеустойки."));
                    }
                }
            }
            MarketAttributeApplier.syncPlayerModifiers(newPlayer);
            MarketNetworking.sendSyncToPlayer(newPlayer);
        });

        // Track ore mining for Mining Contract
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!world.isClientSide() && player instanceof ServerPlayer sp && sp.level().getServer() != null) {
                Block b = state.getBlock();
                if (isOreBlock(b)) {
                    MarketSavedData data = MarketSavedData.get(sp.level().getServer());
                    PlayerMarketAccount acc = data.getOrCreateAccount(sp.getUUID());
                    acc.progressContracts("MINE_ORE", 1);
                    data.setDirty();
                }
            }
        });

        // Track hostile kills for Combat Contract & Synthetic Heart Black Market item
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, entity, killedEntity, damageSource) -> {
            if (entity instanceof ServerPlayer sp && world.getServer() != null) {
                MarketSavedData data = MarketSavedData.get(world.getServer());
                PlayerMarketAccount acc = data.getOrCreateAccount(sp.getUUID());
                long gameTick = world.getServer().overworld().getGameTime();

                if (killedEntity instanceof Enemy) {
                    acc.progressContracts("KILL_MOBS", 1);
                    data.setDirty();
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

    public static void grantStarterCardSafely(ServerPlayer player) {
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
