package com.playermarket.network;

import com.playermarket.PlayerMarketMod;
import com.playermarket.config.MarketConfig;
import com.playermarket.market.*;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Optional;
import java.util.UUID;

public class MarketNetworking {

    public enum ActionType {
        SELL_ASSET,
        LEASE_ASSET,
        BUYBACK_ASSET,
        SELECT_DIVIDEND,
        SELL_INVENTORY_SLOT,
        SELL_ALL_MATCHING_ITEM,
        BUY_CATALOG_RESOURCE,
        BUY_DIAMOND_UPGRADE,
        BUY_BLACK_MARKET,
        CLAIM_CONTRACT,
        ACCEPT_CONTRACT,
        REFRESH_CONTRACTS,
        CANCEL_LEASE,
        REQUEST_SYNC,
        TAB_OPENED,
        CLAIM_TASK_REWARD,
        CLAIM_SIDE_QUEST_REWARD,
        CLAIM_MAIL_REWARD,
        MARK_MAIL_READ,
        CHOOSE_FINAL_TRANSACTION,
        READ_CRUMPLED_LETTER,
        ACCEPT_SHARED_CONTRACT,
        DECLINE_SHARED_CONTRACT
    }

    public record MarketActionPayload(
            String action,
            String targetId,
            String secondaryId,
            int intValue,
            double doubleValue
    ) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<MarketActionPayload> TYPE =
                new CustomPacketPayload.Type<>(PlayerMarketMod.id("market_action"));

        public static final StreamCodec<RegistryFriendlyByteBuf, MarketActionPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, MarketActionPayload::action,
                ByteBufCodecs.STRING_UTF8, MarketActionPayload::targetId,
                ByteBufCodecs.STRING_UTF8, MarketActionPayload::secondaryId,
                ByteBufCodecs.VAR_INT, MarketActionPayload::intValue,
                ByteBufCodecs.DOUBLE, MarketActionPayload::doubleValue,
                MarketActionPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record MarketSyncPayload(
            CompoundTag accountTag,
            CompoundTag campaignTag,
            String activeEventId,
            long currentGameTick,
            long nextEventSwitchTick,
            String oreScannerReport,
            String statusMessage
    ) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<MarketSyncPayload> TYPE =
                new CustomPacketPayload.Type<>(PlayerMarketMod.id("market_sync"));

        public static final StreamCodec<RegistryFriendlyByteBuf, MarketSyncPayload> STREAM_CODEC = StreamCodec.of(
                (buf, val) -> {
                    ByteBufCodecs.COMPOUND_TAG.encode(buf, val.accountTag());
                    ByteBufCodecs.COMPOUND_TAG.encode(buf, val.campaignTag());
                    ByteBufCodecs.STRING_UTF8.encode(buf, val.activeEventId());
                    ByteBufCodecs.VAR_LONG.encode(buf, val.currentGameTick());
                    ByteBufCodecs.VAR_LONG.encode(buf, val.nextEventSwitchTick());
                    ByteBufCodecs.STRING_UTF8.encode(buf, val.oreScannerReport());
                    ByteBufCodecs.STRING_UTF8.encode(buf, val.statusMessage());
                },
                buf -> new MarketSyncPayload(
                        ByteBufCodecs.COMPOUND_TAG.decode(buf),
                        ByteBufCodecs.COMPOUND_TAG.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.VAR_LONG.decode(buf),
                        ByteBufCodecs.VAR_LONG.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf)
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void registerServer() {
        PayloadTypeRegistry.serverboundPlay().register(MarketActionPayload.TYPE, MarketActionPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MarketSyncPayload.TYPE, MarketSyncPayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(MarketActionPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            MinecraftServer server = player.level().getServer();
            if (server == null) return;
            server.execute(() -> handleAction(player, server, payload));
        });
    }

    public static void sendSyncToPlayer(ServerPlayer player) {
        sendSyncToPlayer(player, "");
    }

    public static void sendSyncToPlayer(ServerPlayer player, String statusMessage) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        ResourceValuationEngine.ensureInitialized(server);
        MarketSavedData savedData = MarketSavedData.get(server);
        PlayerMarketAccount account = savedData.getOrCreateAccount(player.getUUID());
        savedData.ensureAccountRegistered(player.getUUID(), account);
        long gameTick = server.overworld().getGameTime();

        Tag encodedAccount = PlayerMarketAccount.CODEC.encodeStart(NbtOps.INSTANCE, account).result().orElse(new CompoundTag());
        CompoundTag compound = encodedAccount instanceof CompoundTag ct ? ct : new CompoundTag();

        Tag encodedCampaign = com.playermarket.story.SharedCampaignData.CODEC.encodeStart(NbtOps.INSTANCE, savedData.getCampaignData()).result().orElse(new CompoundTag());
        CompoundTag campaignCompound = encodedCampaign instanceof CompoundTag cct ? cct : new CompoundTag();

        String scannerReport = MarketAttributeApplier.computeOreScannerReport(player, account);

        ServerPlayNetworking.send(player, new MarketSyncPayload(
                compound,
                campaignCompound,
                savedData.getActiveEvent().getId(),
                gameTick,
                savedData.getNextEventSwitchTick(),
                scannerReport,
                statusMessage != null ? statusMessage : ""
        ));
    }

    public static void broadcastSync(MinecraftServer server, String statusMessage) {
        if (server == null) return;
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            sendSyncToPlayer(sp, statusMessage);
        }
    }

    private static synchronized void handleAction(ServerPlayer player, MinecraftServer server, MarketActionPayload payload) {
        ResourceValuationEngine.ensureInitialized(server);
        MarketSavedData savedData = MarketSavedData.get(server);
        PlayerMarketAccount account = savedData.getOrCreateAccount(player.getUUID());
        MarketEvent event = savedData.getActiveEvent();
        long gameTick = server.overworld().getGameTime();

        ActionType actionType;
        try {
            actionType = ActionType.valueOf(payload.action());
        } catch (IllegalArgumentException e) {
            return;
        }

        String status = "";

        switch (actionType) {
            case REQUEST_SYNC -> {
                MarketAttributeApplier.syncPlayerModifiers(player);
                sendSyncToPlayer(player, "");
                return;
            }

            case SELL_ASSET -> {
                Optional<AssetType> assetOpt = AssetType.byId(payload.targetId());
                if (assetOpt.isEmpty()) return;
                AssetType asset = assetOpt.get();

                int stage = account.getStoryStage();
                if (!asset.getCategory().isUnlockedForStage(stage)) {
                    sendSyncToPlayer(player, "§cКатегория " + asset.getCategory().getDisplayName() + " ещё не разблокирована в Программе (нужен Этап " + asset.getCategory().getRequiredStage() + ")!");
                    playErrorSound(player);
                    return;
                }

                double requestedFraction = validateSaleTier(payload.doubleValue());
                if (requestedFraction <= 0.0) {
                    sendSyncToPlayer(player, "§cНедопустимая доля продажи.");
                    return;
                }

                double currentTotalSold = account.getTotalSoldFraction(asset);
                double maxAllowed = asset.getMaxAllowedFractionForStage(stage);
                if (currentTotalSold + requestedFraction > maxAllowed + 0.0001) {
                    sendSyncToPlayer(player, "§cНа вашем этапе (" + stage + ") лимит ликвидации составляет " + Math.round(maxAllowed * 100) + "%!");
                    playErrorSound(player);
                    return;
                }

                long payout = MarketAttributeApplier.calculateSalePayoutCr(account, event, asset, requestedFraction, 0);
                if (account.isGoldenContractArmed()) {
                    account.setGoldenContractArmed(false);
                }

                double newPermSold = account.getPermanentSoldFraction(asset) + requestedFraction;
                account.getPermanentSoldFractions().put(asset.getId(), newPermSold);

                long prevBasis = account.getPermanentSalePayoutBasis().getOrDefault(asset.getId(), 0L);
                account.getPermanentSalePayoutBasis().put(asset.getId(), prevBasis + payout);

                if (!account.getSelectedDividends().containsKey(asset.getId())) {
                    String divChoice = (payload.secondaryId() != null && !payload.secondaryId().isEmpty())
                            ? payload.secondaryId()
                            : asset.getDividendA().id();
                    account.getSelectedDividends().put(asset.getId(), divChoice);
                } else if (payload.secondaryId() != null && !payload.secondaryId().isEmpty()) {
                    account.getSelectedDividends().put(asset.getId(), payload.secondaryId());
                }

                account.addCr(payout);
                recordCompletedDeal(player, account, event);
                account.progressContracts("MARKET_TRADES", 1);

                String divId = account.getSelectedDividends().get(asset.getId());
                account.addTransaction(gameTick, "ПРОДАЖА АКТИВА",
                        asset.getDisplayName() + " (" + Math.round(requestedFraction * 100) + "%)",
                        "Дивидент: " + divId, payout, 0);

                com.playermarket.story.StoryManager.onStatSold(player, account, asset, requestedFraction, false);

                status = "§aПРОДАНО " + Math.round(requestedFraction * 100) + "% " + asset.getDisplayName() + " за +" + payout + " CR";
                playSuccessSound(player);
            }

            case LEASE_ASSET -> {
                Optional<AssetType> assetOpt = AssetType.byId(payload.targetId());
                if (assetOpt.isEmpty()) return;
                AssetType asset = assetOpt.get();

                int stage = account.getStoryStage();
                if (!asset.getCategory().isUnlockedForStage(stage)) {
                    sendSyncToPlayer(player, "§cКатегория " + asset.getCategory().getDisplayName() + " ещё не разблокирована в Программе (нужен Этап " + asset.getCategory().getRequiredStage() + ")!");
                    playErrorSound(player);
                    return;
                }

                double requestedFraction = validateSaleTier(payload.doubleValue());
                int days = payload.intValue();
                if (days != 1 && days != 2 && days != 3 && days != 5 && days != 7) {
                    days = 1;
                }
                if (requestedFraction <= 0.0) return;

                double currentTotalSold = account.getTotalSoldFraction(asset);
                double maxAllowed = asset.getMaxAllowedFractionForStage(stage);
                if (currentTotalSold + requestedFraction > maxAllowed + 0.0001) {
                    sendSyncToPlayer(player, "§cНа вашем этапе (" + stage + ") лимит ликвидации составляет " + Math.round(maxAllowed * 100) + "%!");
                    playErrorSound(player);
                    return;
                }

                long payout = MarketAttributeApplier.calculateSalePayoutCr(account, event, asset, requestedFraction, days);
                if (account.isGoldenContractArmed()) {
                    account.setGoldenContractArmed(false);
                }

                long durationTicks = days * 24000L;
                LeaseContract lease = new LeaseContract(
                        UUID.randomUUID().toString().substring(0, 8),
                        asset.getId(),
                        requestedFraction,
                        payout,
                        gameTick,
                        gameTick + durationTicks,
                        days
                );
                account.getActiveLeases().add(lease);
                if (!account.getSelectedDividends().containsKey(asset.getId())) {
                    account.getSelectedDividends().put(asset.getId(), asset.getDividendA().id());
                }

                account.addCr(payout);
                recordCompletedDeal(player, account, event);
                account.progressContracts("MARKET_TRADES", 1);
                account.addTransaction(gameTick, "ЛИЗИНГ АКТИВА",
                        asset.getDisplayName() + " (" + Math.round(requestedFraction * 100) + "% / " + days + " дн.)",
                        "Временный контракт лизинга", payout, 0);

                com.playermarket.story.StoryManager.onStatSold(player, account, asset, requestedFraction, true);

                status = "§aЛИЗИНГ " + Math.round(requestedFraction * 100) + "% " + asset.getDisplayName() + " (" + days + " дн.) за +" + payout + " CR";
                playSuccessSound(player);
            }

            case BUYBACK_ASSET -> {
                Optional<AssetType> assetOpt = AssetType.byId(payload.targetId());
                if (assetOpt.isEmpty()) return;
                AssetType asset = assetOpt.get();

                double permSold = account.getPermanentSoldFraction(asset);
                if (permSold <= 0.001) {
                    sendSyncToPlayer(player, "§cНет постоянных продаж для выкупа этой характеристики.");
                    playErrorSound(player);
                    return;
                }

                long cost = MarketAttributeApplier.calculateBuybackCostCr(account, event, asset);
                if (!account.spendCr(cost)) {
                    sendSyncToPlayer(player, "§cНедостаточно CR! Требуется " + cost + " CR для выкупа.");
                    playErrorSound(player);
                    return;
                }

                account.getPermanentSoldFractions().remove(asset.getId());
                account.getPermanentSalePayoutBasis().remove(asset.getId());
                if (account.getTotalSoldFraction(asset) <= 0.001) {
                    account.getSelectedDividends().remove(asset.getId());
                }

                recordCompletedDeal(player, account, event);
                account.progressContracts("MARKET_TRADES", 1);
                account.progressContracts("BUYBACK_ASSET", 1);
                account.addTransaction(gameTick, "ОБРАТНЫЙ ВЫКУП",
                        asset.getDisplayName() + " (" + Math.round(permSold * 100) + "%)",
                        "Характеристика полностью восстановлена", -cost, 0);

                com.playermarket.story.StoryManager.onBuyback(player, account, asset);

                status = "§aВОССТАНОВЛЕНО " + Math.round(permSold * 100) + "% " + asset.getDisplayName() + " (-" + cost + " CR)";
                playSuccessSound(player);
            }

            case SELECT_DIVIDEND -> {
                Optional<AssetType> assetOpt = AssetType.byId(payload.targetId());
                if (assetOpt.isEmpty()) return;
                AssetType asset = assetOpt.get();
                String divId = payload.secondaryId();
                boolean valid = asset.getDividendA().id().equals(divId) || asset.getDividendB().id().equals(divId);
                if (!valid) return;

                account.getSelectedDividends().put(asset.getId(), divId);
                status = "§aВыбран дивиденд: §f" + (divId.equals(asset.getDividendA().id()) ? asset.getDividendA().label() : asset.getDividendB().label());
                playSuccessSound(player);
            }

            case SELL_INVENTORY_SLOT -> {
                int slot = payload.intValue();
                Inventory inv = player.getInventory();
                if (slot < 0 || slot >= 36) return;
                ItemStack stack = inv.getItem(slot);
                if (stack.isEmpty() || isUnsellableMarketCard(stack)) {
                    sendSyncToPlayer(player, "§cЭтот предмет нельзя продать.");
                    playErrorSound(player);
                    return;
                }

                int countToSell = stack.getCount();
                long unitPrice = ResourceValuationEngine.evaluateUnitSellCr(stack, account, event);
                long totalPayout = unitPrice * countToSell;
                if (account.hasDividend("corporate_focus") || account.hasDividend("scholar_yield")) {
                    totalPayout = Math.round(totalPayout * 1.05);
                }
                if (account.isGoldenContractArmed()) {
                    totalPayout *= 2L;
                    account.setGoldenContractArmed(false);
                }

                String itemName = stack.getHoverName().getString();
                inv.setItem(slot, ItemStack.EMPTY);
                inv.setChanged();

                account.addCr(totalPayout);
                recordCompletedDeal(player, account, event);
                account.progressContracts("SELL_RESOURCES", countToSell);
                account.progressContracts("MARKET_TRADES", 1);
                account.addTransaction(gameTick, "ПРОДАЖА РЕСУРСА",
                        countToSell + "x " + itemName,
                        "Цена: " + unitPrice + " CR/шт", totalPayout, 0);

                com.playermarket.story.StoryManager.onResourceSold(player, account, totalPayout);

                status = "§aПРОДАНО " + countToSell + "x " + itemName + " за +" + totalPayout + " CR";
                playSuccessSound(player);
            }

            case SELL_ALL_MATCHING_ITEM -> {
                int slot = payload.intValue();
                Inventory inv = player.getInventory();
                if (slot < 0 || slot >= 36) return;
                ItemStack targetStack = inv.getItem(slot);
                if (targetStack.isEmpty() || isUnsellableMarketCard(targetStack)) return;

                Item targetItem = targetStack.getItem();
                String itemName = targetStack.getHoverName().getString();
                int totalCount = 0;
                long totalPayout = 0L;

                for (int i = 0; i < 36; i++) {
                    ItemStack s = inv.getItem(i);
                    if (!s.isEmpty() && s.is(targetItem) && !isUnsellableMarketCard(s)) {
                        long unitVal = ResourceValuationEngine.evaluateUnitSellCr(s, account, event);
                        totalCount += s.getCount();
                        totalPayout += unitVal * s.getCount();
                        inv.setItem(i, ItemStack.EMPTY);
                    }
                }

                if (totalCount <= 0) return;
                inv.setChanged();

                if (account.isGoldenContractArmed()) {
                    totalPayout *= 2L;
                    account.setGoldenContractArmed(false);
                }

                account.addCr(totalPayout);
                recordCompletedDeal(player, account, event);
                account.progressContracts("SELL_RESOURCES", totalCount);
                account.progressContracts("MARKET_TRADES", 1);
                account.addTransaction(gameTick, "ОПТОВАЯ ПРОДАЖА",
                        totalCount + "x " + itemName,
                        "Партия ресурсов", totalPayout, 0);

                com.playermarket.story.StoryManager.onResourceSold(player, account, totalPayout);

                status = "§aОПТОМ ПРОДАНО " + totalCount + "x " + itemName + " за +" + totalPayout + " CR";
                playSuccessSound(player);
            }

            case BUY_CATALOG_RESOURCE -> {
                String itemIdStr = payload.targetId();
                ResourceValuationEngine.CatalogItem match = null;
                for (ResourceValuationEngine.CatalogItem offer : ResourceValuationEngine.getCatalogOffers(account, event)) {
                    if (offer.itemId().equals(itemIdStr)) {
                        match = offer;
                        break;
                    }
                }
                if (match == null) return;

                if (!account.spendCr(match.buyCostCr())) {
                    sendSyncToPlayer(player, "§cНедостаточно CR! Требуется " + match.buyCostCr() + " CR.");
                    playErrorSound(player);
                    return;
                }

                ItemStack purchased = new ItemStack(match.item(), match.bundleSize());
                String itemName = purchased.getHoverName().getString();
                if (!player.getInventory().add(purchased)) {
                    player.drop(purchased, false, Prediction.SERVER_ONLY);
                }
                player.getInventory().setChanged();

                recordCompletedDeal(player, account, event);
                account.progressContracts("MARKET_TRADES", 1);
                account.addTransaction(gameTick, "ПОКУПКА РЕСУРСА",
                        match.bundleSize() + "x " + itemName,
                        "Каталог сырья", -match.buyCostCr(), 0);

                status = "§aКУПЛЕНО " + match.bundleSize() + "x " + itemName + " (-" + match.buyCostCr() + " CR)";
                playSuccessSound(player);
            }

            case BUY_DIAMOND_UPGRADE -> {
                Optional<UpgradeType> upgradeOpt = UpgradeType.byId(payload.targetId());
                if (upgradeOpt.isEmpty()) return;
                UpgradeType upgrade = upgradeOpt.get();

                int currentLvl = account.getUpgradeLevel(upgrade);
                int targetLvl = currentLvl + 1;
                if (targetLvl > upgrade.getMaxLevel()) {
                    sendSyncToPlayer(player, "§cУлучшение уже прокачано на максимум!");
                    playErrorSound(player);
                    return;
                }

                if (upgrade.getPrerequisite() != null) {
                    int prereqLvl = account.getUpgradeLevel(upgrade.getPrerequisite());
                    if (prereqLvl < upgrade.getPrerequisiteLevel()) {
                        sendSyncToPlayer(player, "§cТребуется " + upgrade.getPrerequisite().getTitle() + " Ур. " + upgrade.getPrerequisiteLevel() + "!");
                        playErrorSound(player);
                        return;
                    }
                }

                int requiredDiamonds = MarketAttributeApplier.calculateUpgradeDiamondCost(account, event, upgrade, targetLvl);
                Inventory inv = player.getInventory();
                int playerDiamonds = MarketAttributeApplier.countVanillaDiamonds(inv);
                if (playerDiamonds < requiredDiamonds) {
                    sendSyncToPlayer(player, "§cНедостаточно алмазов! Требуется " + requiredDiamonds + " ♦ (В наличии " + playerDiamonds + ").");
                    playErrorSound(player);
                    return;
                }

                if (!MarketAttributeApplier.consumeVanillaDiamonds(inv, requiredDiamonds)) {
                    sendSyncToPlayer(player, "§cОшибка списания алмазов из инвентаря.");
                    playErrorSound(player);
                    return;
                }

                account.setUpgradeLevel(upgrade, targetLvl);
                account.recordDiamondsSpent(requiredDiamonds);
                recordCompletedDeal(player, account, event);
                account.progressContracts("MARKET_TRADES", 1);

                int cashbackLvl = account.getUpgradeLevel(UpgradeType.DIAMOND_CASHBACK);
                int refundedDiamonds = 0;
                if (cashbackLvl > 0 && upgrade != UpgradeType.DIAMOND_CASHBACK && requiredDiamonds >= 4) {
                    double chance = UpgradeType.DIAMOND_CASHBACK.getValueForLevel(cashbackLvl);
                    if (server.overworld().getRandom().nextDouble() < chance) {
                        refundedDiamonds = Math.min(requiredDiamonds - 1, cashbackLvl);
                        if (refundedDiamonds > 0) {
                            ItemStack refundStack = new ItemStack(Items.DIAMOND, refundedDiamonds);
                            if (!inv.add(refundStack)) {
                                player.drop(refundStack, false, Prediction.SERVER_ONLY);
                            }
                            inv.setChanged();
                        }
                    }
                }

                account.addTransaction(gameTick, "МОДИФИКАЦИЯ",
                        upgrade.getTitle() + " Ур. " + targetLvl,
                        upgrade.getSummaryForLevel(targetLvl) + (refundedDiamonds > 0 ? " (+" + refundedDiamonds + " кэшбэк)" : ""),
                        0L, -(requiredDiamonds - refundedDiamonds));

                com.playermarket.story.StoryManager.onDiamondsSpent(player, account, requiredDiamonds);

                status = "§bУСТАНОВЛЕНО: " + upgrade.getTitle() + " УР. " + targetLvl + " (-" + requiredDiamonds + " ♦)"
                        + (refundedDiamonds > 0 ? " §a[+" + refundedDiamonds + " ♦ Кэшбэк!]" : "");
                playSuccessSound(player);
            }

            case BUY_BLACK_MARKET -> {
                if (!account.isBlackMarketUnlocked()) {
                    sendSyncToPlayer(player, "§cДоступ к Чёрному Рынку заблокирован. Заключите больше сделок или получите VIP III.");
                    playErrorSound(player);
                    return;
                }

                Optional<BlackMarketItem> bmOpt = BlackMarketItem.byId(payload.targetId());
                if (bmOpt.isEmpty()) return;
                BlackMarketItem bm = bmOpt.get();

                long cooldownRem = account.getBlackMarketCooldownRemainingTicks(bm, gameTick);
                if (cooldownRem > 0L) {
                    sendSyncToPlayer(player, "§cПротокол на перезарядке (" + (cooldownRem / 20L) + " сек. осталось).");
                    playErrorSound(player);
                    return;
                }

                if (bm == BlackMarketItem.SECOND_CHANCE && account.isSecondChanceArmed()) {
                    sendSyncToPlayer(player, "§cПротокол «Второй Шанс» уже активен в вашей системе.");
                    playErrorSound(player);
                    return;
                }
                if (bm == BlackMarketItem.GOLDEN_CONTRACT && account.isGoldenContractArmed()) {
                    sendSyncToPlayer(player, "§c«Золотой Контракт» уже активирован на следующую сделку.");
                    playErrorSound(player);
                    return;
                }

                long crCost = bm.getCostCr();
                int diaCost = bm.getCostDiamonds();
                Inventory inv = player.getInventory();

                if (account.getCrBalance() < crCost || MarketAttributeApplier.countVanillaDiamonds(inv) < diaCost) {
                    sendSyncToPlayer(player, "§cТребуется " + crCost + " CR + " + diaCost + " ♦!");
                    playErrorSound(player);
                    return;
                }

                if (!MarketAttributeApplier.consumeVanillaDiamonds(inv, diaCost)) {
                    return;
                }
                account.spendCr(crCost);
                account.recordDiamondsSpent(diaCost);
                recordCompletedDeal(player, account, event);

                if (bm == BlackMarketItem.SECOND_CHANCE) {
                    account.setSecondChanceArmed(true);
                } else if (bm == BlackMarketItem.GOLDEN_CONTRACT) {
                    account.setGoldenContractArmed(true);
                    account.getBlackMarketCooldownUntil().put(bm.getId(), gameTick + bm.getCooldownTicks());
                } else {
                    account.getBlackMarketActiveUntil().put(bm.getId(), gameTick + bm.getDurationTicks());
                    account.getBlackMarketCooldownUntil().put(bm.getId(), gameTick + bm.getCooldownTicks());
                }

                account.addTransaction(gameTick, "ЧЁРНЫЙ РЫНОК",
                        bm.getTitle(),
                        bm.getSummary(), -crCost, -diaCost);

                status = "§dАКТИВИРОВАН: " + bm.getTitle();
                playSuccessSound(player);
            }

            case CLAIM_CONTRACT -> {
                String contractId = payload.targetId();
                for (int i = 0; i < account.getContracts().size(); i++) {
                    ContractEntry c = account.getContracts().get(i);
                    if (c.id().equals(contractId)) {
                        if (!c.completed() || c.claimed()) {
                            sendSyncToPlayer(player, "§cКонтракт ещё не выполнен или награда уже получена.");
                            playErrorSound(player);
                            return;
                        }
                        double mult = event != null ? event.getContractRewardMultiplier() : 1.0;
                        if (account.hasDividend("corporate_focus")) mult += 0.10;
                        if (account.hasDividend("scholar_yield")) mult += 0.12;
                        int premLvl = account.getUpgradeLevel(UpgradeType.PREMIUM_ACCOUNT);
                        if (premLvl > 0) mult += UpgradeType.PREMIUM_ACCOUNT.getValueForLevel(premLvl);

                        long payout = Math.round(c.rewardCr() * mult);
                        if (account.isGoldenContractArmed()) {
                            payout *= 2L;
                            account.setGoldenContractArmed(false);
                        }

                        account.getContracts().set(i, c.markClaimed());
                        account.addCr(payout);
                        int diaReward = c.rewardDiamonds();
                        if (diaReward > 0) {
                            ItemStack diaStack = new ItemStack(Items.DIAMOND, diaReward);
                            if (!player.getInventory().add(diaStack)) {
                                player.drop(diaStack, false, Prediction.SERVER_ONLY);
                            }
                            player.getInventory().setChanged();
                        }
                        recordCompletedDeal(player, account, event);
                        account.addTransaction(gameTick, "НАГРАДА ПО КОНТРАКТУ",
                                c.title(), "Выполнение корпоративного заказа", payout, diaReward);

                        com.playermarket.story.StoryManager.onContractClaimed(player, account, event != MarketEvent.STABLE_MARKET);

                        status = "§aКОНТРАКТ ВЫПОЛНЕН! +" + payout + " CR" + (diaReward > 0 ? " §8| §b+" + diaReward + " ♦" : "");
                        playSuccessSound(player);
                        break;
                    }
                }
            }

            case REFRESH_CONTRACTS -> {
                long refreshCost = 150L;
                if (!account.spendCr(refreshCost)) {
                    sendSyncToPlayer(player, "§cТребуется " + refreshCost + " CR для обновления доски контрактов.");
                    playErrorSound(player);
                    return;
                }
                account.refreshContracts(gameTick);
                account.addTransaction(gameTick, "ДОСКА КОНТРАКТОВ", "Обновление списка", "Комиссия за запрос", -refreshCost, 0);
                status = "§aДоска контрактов обновлена (-" + refreshCost + " CR)";
                playSuccessSound(player);
            }

            case ACCEPT_CONTRACT -> {
                String contractId = payload.targetId();
                for (int i = 0; i < account.getContracts().size(); i++) {
                    ContractEntry c = account.getContracts().get(i);
                    if (c.id().equals(contractId)) {
                        if (c.active() || c.claimed()) {
                            sendSyncToPlayer(player, "§cКонтракт уже активен или завершён.");
                            playErrorSound(player);
                            return;
                        }
                        account.getContracts().set(i, c.accept(gameTick));
                        status = "§aКонтракт принят: §f" + c.title() + " §7(Срок: " + c.durationDays() + " дн.)";
                        playSuccessSound(player);
                        break;
                    }
                }
            }

            case CANCEL_LEASE -> {
                String leaseId = payload.targetId();
                LeaseContract foundLease = null;
                for (LeaseContract lc : account.getActiveLeases()) {
                    if (lc.id().equals(leaseId) || lc.assetId().equals(leaseId)) {
                        foundLease = lc;
                        break;
                    }
                }
                if (foundLease == null) {
                    sendSyncToPlayer(player, "§cАктивный договор лизинга не найден.");
                    playErrorSound(player);
                    return;
                }
                long earlyPenaltyFee = Math.max(50L, Math.round(foundLease.payoutCr() * 1.25));
                if (!account.spendCr(earlyPenaltyFee)) {
                    sendSyncToPlayer(player, "§cНедостаточно CR! Требуется: " + earlyPenaltyFee + " CR (выплата + штраф 25%).");
                    playErrorSound(player);
                    return;
                }
                account.getActiveLeases().remove(foundLease);
                AssetType.byId(foundLease.assetId()).ifPresent(asset -> {
                    if (account.getTotalSoldFraction(asset) <= 0.001) {
                        account.getSelectedDividends().remove(asset.getId());
                    }
                    account.addTransaction(gameTick, "ДОСРОЧНОЕ РАСТОРЖЕНИЕ", "Лизинг: " + asset.getDisplayName(),
                            "Возврат стата (штраф 25% за раннее прекращение)", -earlyPenaltyFee, 0);
                });
                MarketAttributeApplier.syncPlayerModifiers(player);
                status = "§aЛизинг досрочно расторгнут! Характеристика возвращена (-" + earlyPenaltyFee + " CR)";
                playSuccessSound(player);
            }

            case TAB_OPENED -> {
                com.playermarket.story.StoryManager.onTerminalTabOpened(player, account, payload.targetId());
                savedData.setDirty();
                return;
            }

            case CLAIM_TASK_REWARD -> {
                int stageIdx = payload.intValue();
                String taskId = payload.targetId();
                if (com.playermarket.story.StoryManager.claimTaskReward(player, account, stageIdx, taskId, gameTick)) {
                    status = "§aНаграда за задачу получена!";
                    playSuccessSound(player);
                } else {
                    sendSyncToPlayer(player, "§cНаграда уже получена или задача не выполнена.");
                    playErrorSound(player);
                    return;
                }
            }

            case CLAIM_SIDE_QUEST_REWARD -> {
                String questId = payload.targetId();
                if (com.playermarket.story.StoryManager.claimSideQuestReward(player, account, questId, gameTick)) {
                    status = "§aНаграда за поручение получена!";
                    playSuccessSound(player);
                } else {
                    sendSyncToPlayer(player, "§cНаграда уже получена или поручение не завершено.");
                    playErrorSound(player);
                    return;
                }
            }

            case CLAIM_MAIL_REWARD -> {
                String mailId = payload.targetId();
                if (com.playermarket.story.StoryManager.claimMailReward(player, account, mailId, gameTick)) {
                    status = "§aВложение из письма получено!";
                    playSuccessSound(player);
                } else {
                    sendSyncToPlayer(player, "§cВложение уже получено или отсутствует.");
                    playErrorSound(player);
                    return;
                }
            }

            case MARK_MAIL_READ -> {
                com.playermarket.story.StoryManager.markMailRead(account, payload.targetId());
                savedData.setDirty();
                return;
            }

            case CHOOSE_FINAL_TRANSACTION -> {
                String choiceId = payload.targetId();
                if (com.playermarket.story.StoryManager.chooseFinalTransaction(player, account, choiceId)) {
                    status = "§6ФИНАЛЬНЫЙ ВЫБОР ЗАФИКСИРОВАН: " + choiceId;
                    playSuccessSound(player);
                } else {
                    playErrorSound(player);
                    return;
                }
            }

            case READ_CRUMPLED_LETTER -> {
                account.setReadCrumpledLetter(true);
                com.playermarket.story.StoryManager.onPlayerReadLetter(player, account);
                savedData.setDirty();
                sendSyncToPlayer(player, "");
                return;
            }

            case ACCEPT_SHARED_CONTRACT -> {
                com.playermarket.story.StoryManager.acceptSharedContract(player, account, server);
                savedData.setDirty();
                broadcastSync(server, "§aУчастник подписал Программу Активов!");
                playSuccessSound(player);
                return;
            }

            case DECLINE_SHARED_CONTRACT -> {
                com.playermarket.story.StoryManager.declineSharedContract(player, account);
                savedData.setDirty();
                sendSyncToPlayer(player, "§7Вы отклонили предложение Программы Активов.");
                return;
            }
        }

        savedData.setDirty();
        MarketAttributeApplier.syncPlayerModifiers(player);
        sendSyncToPlayer(player, status);
    }

    private static double validateSaleTier(double raw) {
        if (raw >= 0.05 && raw <= 0.90) {
            return Math.round(raw * 100.0) / 100.0;
        }
        return -1.0;
    }

    private static boolean isUnsellableMarketCard(ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace().equals(PlayerMarketMod.MOD_ID);
    }

    private static void recordCompletedDeal(ServerPlayer player, PlayerMarketAccount account, MarketEvent event) {
        boolean wasLocked = !account.isBlackMarketUnlocked();
        account.incrementDealsCount();
        account.checkBlackMarketUnlock();
        if (wasLocked && account.isBlackMarketUnlocked()) {
            player.sendSystemMessage(Component.literal("§8[§cPLAYER MARKET§8] §aДОСТУП ОТКРЫТ: §dЧёрный Рынок разблокирован! Нажмите [M] для перехода."));
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.9f, 1.2f);
        }
        com.playermarket.story.StoryManager.onDealMade(player, account, event);
    }

    private static void playSuccessSound(ServerPlayer player) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7f, 1.2f);
    }

    private static void playErrorSound(ServerPlayer player) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.7f, 1.0f);
    }
}
