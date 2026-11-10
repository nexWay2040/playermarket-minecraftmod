package com.playermarket.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.playermarket.config.MarketConfig;
import com.playermarket.market.*;
import com.playermarket.network.MarketNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class MarketCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("market")
                // Public player subcommands
                .then(Commands.literal("card")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            boolean hasCard = false;
                            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                                net.minecraft.world.item.ItemStack s = player.getInventory().getItem(i);
                                if (!s.isEmpty() && s.is(com.playermarket.item.MarketAccessCardItem.MARKET_ACCESS_CARD)) {
                                    hasCard = true;
                                    break;
                                }
                            }
                            if (hasCard) {
                                ctx.getSource().sendFailure(Component.literal("§8[§cPLAYER MARKET§8] §cУ вас уже есть Карта Доступа в инвентаре!"));
                                return 0;
                            }
                            net.minecraft.world.item.ItemStack card = new net.minecraft.world.item.ItemStack(com.playermarket.item.MarketAccessCardItem.MARKET_ACCESS_CARD);
                            if (player.getInventory().getItem(8).isEmpty()) {
                                player.getInventory().setItem(8, card);
                            } else if (!player.getInventory().add(card)) {
                                player.drop(card, false, net.minecraft.util.Prediction.SERVER_ONLY);
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal("§8[§aPLAYER MARKET§8] §aДубликат Карты Доступа выдан."), false);
                            return 1;
                        }))
                .then(Commands.literal("balance")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            MarketSavedData data = MarketSavedData.get(player.level().getServer());
                            PlayerMarketAccount acc = data.getOrCreateAccount(player.getUUID());
                            int diamonds = MarketAttributeApplier.countVanillaDiamonds(player.getInventory());
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "§8[§aPLAYER MARKET§8] §7Account Balance: §a" + acc.getCrBalance() + " CR §8| §b" + diamonds + " Diamonds"
                            ), false);
                            return 1;
                        }))
                .then(Commands.literal("stats")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            MarketSavedData data = MarketSavedData.get(player.level().getServer());
                            PlayerMarketAccount acc = data.getOrCreateAccount(player.getUUID());
                            List<CharacterBuild> builds = CharacterBuild.evaluateUnlockedBuilds(acc);
                            String buildNames = builds.isEmpty() ? "Standard Citizen" : builds.stream().map(CharacterBuild::getTitle).reduce((a, b) -> a + ", " + b).orElse("Standard");
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "§8[§aPLAYER MARKET§8] §fPORTFOLIO TELEMETRY:\n"
                                            + " §7CR Balance: §a" + acc.getCrBalance() + " CR\n"
                                            + " §7Total Earned: §a" + acc.getTotalCrEarned() + " CR §8| §7Spent: §c" + acc.getTotalCrSpent() + " CR\n"
                                            + " §7Diamonds Invested: §b" + acc.getTotalDiamondsSpent() + " ♦\n"
                                            + " §7Sold Assets Valuation: §e" + acc.getTotalSoldAssetsValuation() + " CR\n"
                                            + " §7Black Market Access: " + (acc.isBlackMarketUnlocked() ? "§dUNLOCKED" : "§8LOCKED") + "\n"
                                            + " §7Active Archetypes: §6" + buildNames
                            ), false);
                            return 1;
                        }))

                // Administrative / Developer subcommands
                .then(Commands.literal("give")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("amount", LongArgumentType.longArg(1L))
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    long amount = LongArgumentType.getLong(ctx, "amount");
                                    MarketSavedData data = MarketSavedData.get(player.level().getServer());
                                    PlayerMarketAccount acc = data.getOrCreateAccount(player.getUUID());
                                    acc.addCr(amount);
                                    acc.addTransaction(player.level().getServer().overworld().getGameTime(),
                                            "ADMIN GRANT", "Executive Grant", "Command /market give", amount, 0);
                                    data.setDirty();
                                    MarketNetworking.sendSyncToPlayer(player, "§aAdmin granted +" + amount + " CR");
                                    ctx.getSource().sendSuccess(() -> Component.literal("§8[§aPLAYER MARKET§8] §aGranted " + amount + " CR to " + player.getName().getString()), true);
                                    return 1;
                                })))
                .then(Commands.literal("asset")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> setAssetFraction(ctx.getSource(), StringArgumentType.getString(ctx, "id"), 0.25))
                                .then(Commands.argument("fraction", DoubleArgumentType.doubleArg(0.0, 0.80))
                                        .executes(ctx -> setAssetFraction(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "id"),
                                                DoubleArgumentType.getDouble(ctx, "fraction"))))))
                .then(Commands.literal("upgrade")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> setUpgradeLvl(ctx.getSource(), StringArgumentType.getString(ctx, "id"), 1))
                                .then(Commands.argument("level", IntegerArgumentType.integer(0, 3))
                                        .executes(ctx -> setUpgradeLvl(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "id"),
                                                IntegerArgumentType.getInteger(ctx, "level"))))))
                .then(Commands.literal("contract")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            MarketSavedData data = MarketSavedData.get(player.level().getServer());
                            PlayerMarketAccount acc = data.getOrCreateAccount(player.getUUID());
                            for (int i = 0; i < acc.getContracts().size(); i++) {
                                ContractEntry c = acc.getContracts().get(i);
                                acc.getContracts().set(i, c.withProgress(c.targetProgress()));
                            }
                            data.setDirty();
                            MarketNetworking.sendSyncToPlayer(player, "§aAll active contracts marked completed.");
                            ctx.getSource().sendSuccess(() -> Component.literal("§8[§aPLAYER MARKET§8] §aCompleted all active contracts for " + player.getName().getString()), true);
                            return 1;
                        }))
                .then(Commands.literal("event")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> {
                                    String id = StringArgumentType.getString(ctx, "id");
                                    Optional<MarketEvent> evOpt = MarketEvent.byId(id);
                                    if (evOpt.isEmpty()) {
                                        ctx.getSource().sendFailure(Component.literal("§cUnknown Market Event ID: " + id));
                                        return 0;
                                    }
                                    MarketEvent ev = evOpt.get();
                                    var server = ctx.getSource().getServer();
                                    MarketSavedData data = MarketSavedData.get(server);
                                    data.setActiveEvent(ev, server.overworld().getGameTime());
                                    for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                                        MarketNetworking.sendSyncToPlayer(p, "§eMarket Event triggered: " + ev.getHeadline());
                                    }
                                    ctx.getSource().sendSuccess(() -> Component.literal("§8[§aPLAYER MARKET§8] §aActivated Market Event: §e" + ev.getHeadline()), true);
                                    return 1;
                                })))
                .then(Commands.literal("story")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("setstage")
                                .then(Commands.argument("stage", IntegerArgumentType.integer(1, 6))
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            var server = player.level().getServer();
                                            int stage = IntegerArgumentType.getInteger(ctx, "stage");
                                            MarketSavedData data = MarketSavedData.get(server);
                                            var campaign = data.getCampaignData();
                                            campaign.setCampaignAccepted(true);
                                            campaign.setCurrentStage(stage);
                                            PlayerMarketAccount acc = data.getOrCreateAccount(player.getUUID());
                                            com.playermarket.story.StoryManager.advanceStage(player, acc, stage, server.overworld().getGameTime());
                                            data.setDirty();
                                            MarketNetworking.broadcastSync(server, "§aЭтап программы установлен: " + stage);
                                            ctx.getSource().sendSuccess(() -> Component.literal("§8[§aPLAYER MARKET§8] §aStory stage set to " + stage + " (shared campaign synced)"), true);
                                            return 1;
                                        })))
                        .then(Commands.literal("reset")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    var server = player.level().getServer();
                                    MarketSavedData data = MarketSavedData.get(server);
                                    var campaign = data.getCampaignData();
                                    campaign.setCurrentStage(1);
                                    campaign.getCompletedTasks().clear();
                                    campaign.getCompletedSideQuests().clear();
                                    PlayerMarketAccount acc = data.getOrCreateAccount(player.getUUID());
                                    acc.getStoryProgress().setCurrentStage(1);
                                    acc.getStoryProgress().getCompletedTasks().clear();
                                    acc.getStoryProgress().getClaimedTaskRewards().clear();
                                    acc.getStoryProgress().setFinalChoice("NONE");
                                    data.setDirty();
                                    MarketNetworking.broadcastSync(server, "§aПрогресс сюжета сброшен на Этап 1");
                                    ctx.getSource().sendSuccess(() -> Component.literal("§8[§aPLAYER MARKET§8] §eStory reset to Stage 1"), true);
                                    return 1;
                                })))
                .then(Commands.literal("reset")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            MarketSavedData data = MarketSavedData.get(player.level().getServer());
                            PlayerMarketAccount acc = data.getOrCreateAccount(player.getUUID());
                            acc.resetAll();
                            data.setDirty();
                            MarketAttributeApplier.syncPlayerModifiers(player);
                            MarketNetworking.sendSyncToPlayer(player, "§eMarket account reset to factory defaults.");
                            ctx.getSource().sendSuccess(() -> Component.literal("§8[§aPLAYER MARKET§8] §eReset account for " + player.getName().getString()), true);
                            return 1;
                        }))
                .then(Commands.literal("reload")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> {
                            MarketConfig.load();
                            ResourceValuationEngine.invalidateCache();
                            ResourceValuationEngine.ensureInitialized(ctx.getSource().getServer());
                            for (ServerPlayer p : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                                MarketAttributeApplier.syncPlayerModifiers(p);
                                MarketNetworking.sendSyncToPlayer(p, "§aMarket Configuration & Recipe Valuations reloaded.");
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal("§8[§aPLAYER MARKET§8] §aReloaded playermarket.json & recalculated recipe valuations."), true);
                            return 1;
                        }))
                .then(Commands.literal("debug")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            MarketSavedData data = MarketSavedData.get(player.level().getServer());
                            PlayerMarketAccount acc = data.getOrCreateAccount(player.getUUID());
                            acc.setBlackMarketUnlocked(true);
                            data.setDirty();
                            MarketAttributeApplier.syncPlayerModifiers(player);
                            MarketNetworking.sendSyncToPlayer(player, "§dDebug: Black Market Unlocked & Modifiers Synced");
                            ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                                    "§8[§aPLAYER MARKET DEBUG§8]\n"
                                            + " §7Active Event: §e%s\n"
                                            + " §7MaxHealth: §f%.1f §8| §7Speed: §f%.3f §8| §7AttackDmg: §f%.1f §8| §7Armor: §f%.1f\n"
                                            + " §7BlockBreakSpeed: §f%.2f §8| §7Luck: §f%.1f §8| §7EntityReach: §f%.2f\n"
                                            + " §7Sold Assets: §f%d §8| §7Active Leases: §f%d §8| §7Upgrades: §f%d",
                                    data.getActiveEvent().getId(),
                                    player.getMaxHealth(),
                                    player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED),
                                    player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE),
                                    player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR),
                                    player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_BREAK_SPEED),
                                    player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.LUCK),
                                    player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE),
                                    acc.getPermanentSoldFractions().size(),
                                    acc.getActiveLeases().size(),
                                    acc.getUpgradeLevels().size()
                            )), false);
                            return 1;
                        }))
                .then(Commands.literal("ending")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("main")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    com.playermarket.story.EndingSequenceManager.startMainEnding(player);
                                    return 1;
                                }))
                        .then(Commands.literal("secret")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    com.playermarket.story.EndingSequenceManager.startSecretEnding(player);
                                    return 1;
                                }))
                        .then(Commands.literal("reset")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    com.playermarket.story.EndingSequenceManager.resetEnding(player);
                                    return 1;
                                })))
        );

        dispatcher.register(Commands.literal("playermarket")
                .redirect(dispatcher.getRoot().getChild("market")));
    }

    private static int setAssetFraction(CommandSourceStack source, String assetId, double fraction) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Optional<AssetType> assetOpt = AssetType.byId(assetId);
        if (assetOpt.isEmpty()) {
            source.sendFailure(Component.literal("§cUnknown Asset ID: " + assetId));
            return 0;
        }
        AssetType asset = assetOpt.get();
        MarketSavedData data = MarketSavedData.get(player.level().getServer());
        PlayerMarketAccount acc = data.getOrCreateAccount(player.getUUID());
        if (fraction <= 0.001) {
            acc.getPermanentSoldFractions().remove(asset.getId());
            acc.getPermanentSalePayoutBasis().remove(asset.getId());
        } else {
            double clamped = Math.min(fraction, asset.getMaxSellableFraction());
            acc.getPermanentSoldFractions().put(asset.getId(), clamped);
            acc.getPermanentSalePayoutBasis().put(asset.getId(), Math.round(asset.getFullValueCr() * clamped));
            acc.getSelectedDividends().putIfAbsent(asset.getId(), asset.getDividendA().id());
        }
        data.setDirty();
        MarketAttributeApplier.syncPlayerModifiers(player);
        MarketNetworking.sendSyncToPlayer(player, "§eAdmin set " + asset.getId() + " sold fraction to " + Math.round(fraction * 100) + "%");
        source.sendSuccess(() -> Component.literal("§8[§aPLAYER MARKET§8] §aSet sold fraction of " + asset.getId() + " to " + Math.round(fraction * 100) + "%"), true);
        return 1;
    }

    private static int setUpgradeLvl(CommandSourceStack source, String upgradeId, int level) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Optional<UpgradeType> upgOpt = UpgradeType.byId(upgradeId);
        if (upgOpt.isEmpty()) {
            source.sendFailure(Component.literal("§cUnknown Upgrade ID: " + upgradeId));
            return 0;
        }
        UpgradeType upgrade = upgOpt.get();
        MarketSavedData data = MarketSavedData.get(player.level().getServer());
        PlayerMarketAccount acc = data.getOrCreateAccount(player.getUUID());
        acc.setUpgradeLevel(upgrade, level);
        data.setDirty();
        MarketAttributeApplier.syncPlayerModifiers(player);
        MarketNetworking.sendSyncToPlayer(player, "§bAdmin set " + upgrade.getId() + " to Level " + level);
        source.sendSuccess(() -> Component.literal("§8[§aPLAYER MARKET§8] §bSet upgrade " + upgrade.getId() + " to level " + level), true);
        return 1;
    }
}
