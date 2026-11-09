package com.playermarket.story;

import com.playermarket.item.MarketAccessCardItem;
import com.playermarket.market.MarketAttributeApplier;
import com.playermarket.market.MarketSavedData;
import com.playermarket.market.PlayerMarketAccount;
import com.playermarket.network.MarketNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;
import java.util.UUID;

public class EndingSequenceManager {

    public static void startSynchronizedFinalSettlement(MinecraftServer server) {
        if (server == null) return;
        MarketSavedData savedData = MarketSavedData.get(server);
        SharedCampaignData campaignData = savedData.getCampaignData();

        campaignData.setEndingState("FINAL_SETTLEMENT_ACTIVE");
        campaignData.setEndingStartedTick(server.overworld().getGameTime());

        // Process all connected players and registered participants
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            campaignData.getImpairedPlayers().add(player.getUUID());
            PlayerMarketAccount account = savedData.getOrCreateAccount(player.getUUID());

            account.addCr(100000L);
            account.setMarketCardBlocked(true);
            account.setPermanentImpaired(true);
            account.setEndingState("SETTLED");
            account.getStoryProgress().setFinalChoice("ACCOUNT_SETTLEMENT");

            // Transform all access cards in inventory into BLOCKED MARKET CARD
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty() && stack.is(MarketAccessCardItem.MARKET_ACCESS_CARD)) {
                    stack.set(DataComponents.CUSTOM_NAME, Component.literal("§c§lBLOCKED MARKET CARD"));
                    List<Component> lore = List.of(
                            Component.literal("§4[TERMINATED BY SECTION 17.4]"),
                            Component.literal("§8ID: ASSET-#000"),
                            Component.literal("§cSTATUS: ACCESS REVOKED"),
                            Component.literal("§7Biomechanical collateral claimed.")
                    );
                    stack.set(DataComponents.LORE, new ItemLore(lore));
                }
            }
            player.getInventory().setChanged();

            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 35, 10));
        }

        for (UUID uuid : campaignData.getRegisteredParticipants()) {
            campaignData.getImpairedPlayers().add(uuid);
            PlayerMarketAccount account = savedData.getOrCreateAccount(uuid);
            account.setMarketCardBlocked(true);
            account.setPermanentImpaired(true);
            account.setEndingState("SETTLED");
        }

        savedData.setDirty();
        MarketNetworking.broadcastSync(server, "§4[СИСТЕМА] ФИНАЛЬНЫЙ РАСЧЁТ УТВЕРЖДЁН. ВСТУПЛЕНИЕ В СИЛУ СТАТЬИ 17.4...");
    }

    public static void startMainEnding(ServerPlayer player) {
        if (player.level().getServer() != null) {
            startSynchronizedFinalSettlement(player.level().getServer());
        }
    }

    public static void startSecretEnding(ServerPlayer player) {
        if (player.level().getServer() != null) {
            startSynchronizedFinalSettlement(player.level().getServer());
        }
    }

    public static void resetEnding(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        MarketSavedData savedData = MarketSavedData.get(server);
        SharedCampaignData campaignData = savedData.getCampaignData();
        campaignData.setEndingState("NONE");
        campaignData.getImpairedPlayers().clear();
        campaignData.getDisconnectedPlayers().clear();

        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            PlayerMarketAccount account = savedData.getOrCreateAccount(sp.getUUID());
            account.setEndingState("NONE");
            account.setAccountClosed(false);
            account.setMarketCardBlocked(false);
            account.setPermanentImpaired(false);
            MarketAttributeApplier.syncPlayerModifiers(sp);
        }

        savedData.setDirty();
        MarketNetworking.broadcastSync(server, "§aСтатус концовки сброшен.");
    }

    public static void tickServer(MinecraftServer server) {
        MarketSavedData savedData = MarketSavedData.get(server);
        SharedCampaignData campaignData = savedData.getCampaignData();

        if ("FINAL_SETTLEMENT_ACTIVE".equals(campaignData.getEndingState())) {
            long elapsed = server.overworld().getGameTime() - campaignData.getEndingStartedTick();

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                // Freeze controls & darken vision during cinematic
                player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 255, false, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false, false));

                if (elapsed == 0) {
                    sendTitle(player, "§e§lFINAL SETTLEMENT ACCEPTED", "§6ACCOUNT SETTLEMENT APPROVED // +100,000 CR CREDITED");
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 0.8f);
                } else if (elapsed == 40) {
                    sendTitle(player, "§c§lLIQUIDATING ASSETS...", "§7COLLECTING BIOLOGICAL COLLATERAL");
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f, 0.6f);
                } else if (elapsed == 80) {
                    sendTitle(player, "§4§lREVOKING MARKET ACCESS...", "§cCLOSING ACCOUNTS AND TERMINATING PORTFOLIOS");
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 0.8f, 0.7f);
                } else if (elapsed == 120) {
                    sendTitle(player, "§4§lAPPLYING PERMANENT MODIFICATIONS...", "§7BIOMECHANICAL STABILIZATION PROTOCOL IN PROGRESS");
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1.0f, 0.5f);
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.8f, 0.6f);
                } else if (elapsed == 160) {
                    sendTitle(player, "§c§lACCOUNT ACCESS REVOKED.", "§eASSET OWNERSHIP TRANSFERRED. YOU ARE NO LONGER THE CLIENT.");
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.7f, 0.5f);
                } else if (elapsed >= 200) {
                    // Finalize sequence: mark settled, save, and cleanly disconnect
                    campaignData.setEndingState("SETTLED");
                    savedData.setDirty();

                    for (ServerPlayer target : server.getPlayerList().getPlayers()) {
                        campaignData.getDisconnectedPlayers().add(target.getUUID());
                        MarketAttributeApplier.syncPlayerModifiers(target);
                        target.connection.disconnect(Component.literal("ACCOUNT OWNERSHIP TRANSFERRED — MARKET ACCESS REVOKED."));
                    }
                    return;
                }
            }
        }
    }

    private static void sendTitle(ServerPlayer player, String title, String subtitle) {
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(subtitle)));
    }

    public static void onPlayerJoin(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        MarketSavedData savedData = MarketSavedData.get(server);
        PlayerMarketAccount account = savedData.getOrCreateAccount(player.getUUID());
        SharedCampaignData campaignData = savedData.getCampaignData();

        if (account.isPermanentImpaired() || "SETTLED".equals(campaignData.getEndingState()) || campaignData.isPlayerImpaired(player.getUUID())) {
            // Reapply modifiers cleanly on join (no sound packets or client inventory changes during handshake!)
            MarketAttributeApplier.syncPlayerModifiers(player);

            player.sendSystemMessage(Component.literal("§8===================================================="));
            player.sendSystemMessage(Component.literal("§4§lCORPORATE ASSET REGISTRY // STATUS: REVOKED"));
            player.sendSystemMessage(Component.literal("§7SUBJECT: §c" + player.getName().getString()));
            player.sendSystemMessage(Component.literal("§cMARKET ACCESS PERMANENTLY REVOKED (SECTION 17.4)"));
            player.sendSystemMessage(Component.literal("§7Biomechanical support brace active on right leg."));
            player.sendSystemMessage(Component.literal("§8===================================================="));
        }
    }
}
