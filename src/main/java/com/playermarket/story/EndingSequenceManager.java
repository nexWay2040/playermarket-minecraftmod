package com.playermarket.story;

import com.playermarket.item.BlockedMarketCardItem;
import com.playermarket.item.MarketAccessCardItem;
import com.playermarket.market.MarketAttributeApplier;
import com.playermarket.market.MarketSavedData;
import com.playermarket.market.PlayerMarketAccount;
import com.playermarket.network.MarketNetworking;
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

import java.util.UUID;

public class EndingSequenceManager {

    public static void startSynchronizedFinalSettlement(MinecraftServer server) {
        if (server == null) return;
        MarketSavedData savedData = MarketSavedData.get(server);
        SharedCampaignData campaignData = savedData.getCampaignData();

        // 1. Set independent ending lifecycle flags
        campaignData.setEndingStarted(true);
        campaignData.setEndingApplied(true);
        campaignData.setEndingState("FINAL_SETTLEMENT_ACTIVE");
        campaignData.setEndingStartedTick(server.overworld().getGameTime());

        // 2. Process all connected players and registered participants
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            campaignData.getImpairedPlayers().add(player.getUUID());
            PlayerMarketAccount account = savedData.getOrCreateAccount(player.getUUID());

            account.addCr(100000L);
            account.setMarketCardBlocked(true);
            account.setPermanentImpaired(true);
            account.setEndingState("SETTLED");
            account.getStoryProgress().setFinalChoice("ACCOUNT_SETTLEMENT");

            // Transform all access cards in inventory into BLOCKED MARKET CARD item
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty() && stack.is(MarketAccessCardItem.MARKET_ACCESS_CARD)) {
                    player.getInventory().setItem(i, new ItemStack(BlockedMarketCardItem.BLOCKED_MARKET_CARD));
                }
            }
            player.getInventory().setChanged();

            // Set Title Animation Timing (fade-in 10, stay 50, fade-out 10)
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 10));
        }

        for (UUID uuid : campaignData.getRegisteredParticipants()) {
            campaignData.getImpairedPlayers().add(uuid);
            PlayerMarketAccount account = savedData.getOrCreateAccount(uuid);
            account.setMarketCardBlocked(true);
            account.setPermanentImpaired(true);
            account.setEndingState("SETTLED");
        }

        // 3. Persist to disk BEFORE sending any network sequences
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
        campaignData.setEndingStarted(false);
        campaignData.setEndingApplied(false);
        campaignData.setCinematicCompleted(false);
        campaignData.setDisconnectSent(false);
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

        // If disconnect was already sent, never run cinematic or disconnect logic again!
        if (campaignData.isDisconnectSent() || "SETTLED".equals(campaignData.getEndingState())) {
            return;
        }

        if ("FINAL_SETTLEMENT_ACTIVE".equals(campaignData.getEndingState())) {
            long elapsed = server.overworld().getGameTime() - campaignData.getEndingStartedTick();

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                // Freeze controls & darken vision during cinematic
                player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 255, false, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false, false));

                if (elapsed == 0) {
                    sendTitle(player, "§6§lFINAL SETTLEMENT", "§eAccount Approved // +100,000 CR");
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 0.8f);
                } else if (elapsed == 40) {
                    sendTitle(player, "§c§lLIQUIDATING ASSETS...", "§7Collecting Biological Collateral");
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f, 0.6f);
                } else if (elapsed == 80) {
                    sendTitle(player, "§4§lREVOKING ACCESS...", "§cClosing Portfolios & Accounts");
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 0.8f, 0.7f);
                } else if (elapsed == 120) {
                    sendTitle(player, "§4§lPERMANENT MODIFICATIONS", "§7Section 17.4 Protocol Applied");
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1.0f, 0.5f);
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.8f, 0.6f);
                } else if (elapsed == 160) {
                    sendTitle(player, "§c§lACCOUNT ACCESS REVOKED.", "§4Asset Ownership Transferred.");
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.7f, 0.5f);
                } else if (elapsed >= 200) {
                    // Finalize sequence: mark completed, save, and disconnect
                    campaignData.setEndingState("SETTLED");
                    campaignData.setCinematicCompleted(true);
                    campaignData.setDisconnectSent(true);
                    savedData.setDirty();

                    for (ServerPlayer target : server.getPlayerList().getPlayers()) {
                        campaignData.getDisconnectedPlayers().add(target.getUUID());
                        MarketAttributeApplier.syncPlayerModifiers(target);
                        target.connection.disconnect(Component.literal("§c§lACCOUNT OWNERSHIP TRANSFERRED\n§7Market Access Revoked (Section 17.4)\n§8Your biological assets have been liquidated."));
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
            // Ensure UUID is in impaired set for client-side leg brace rendering
            campaignData.getImpairedPlayers().add(player.getUUID());
            savedData.setDirty();

            // Transform any remaining market access cards in inventory to BLOCKED cards
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty() && stack.is(MarketAccessCardItem.MARKET_ACCESS_CARD)) {
                    player.getInventory().setItem(i, new ItemStack(BlockedMarketCardItem.BLOCKED_MARKET_CARD));
                }
            }

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
