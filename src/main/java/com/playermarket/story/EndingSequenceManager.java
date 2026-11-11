package com.playermarket.story;

import com.playermarket.item.BlockedMarketCardItem;
import com.playermarket.item.MarketAccessCardItem;
import com.playermarket.market.MarketAttributeApplier;
import com.playermarket.market.MarketSavedData;
import com.playermarket.market.PlayerMarketAccount;
import com.playermarket.network.MarketNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public class EndingSequenceManager {
    private static long endingLastTickWallMs = 0L;
    private static long endingAccumulatedActiveMs = 0L;
    private static int lastCueStep = 0;

    public static void startSynchronizedFinalSettlement(MinecraftServer server) {
        if (server == null) return;
        MarketSavedData savedData = MarketSavedData.get(server);
        SharedCampaignData campaignData = savedData.getCampaignData();

        // 1. Set independent ending lifecycle flags & clear prior completion flags so it ALWAYS runs & kicks
        campaignData.setEndingStarted(true);
        campaignData.setEndingApplied(true);
        campaignData.setCinematicCompleted(false);
        campaignData.setDisconnectSent(false);
        campaignData.setEndingState("FINAL_SETTLEMENT_ACTIVE");
        campaignData.setEndingStartedTick(server.overworld().getGameTime());
        endingLastTickWallMs = System.currentTimeMillis();
        endingAccumulatedActiveMs = 0L;
        lastCueStep = 0;
        savedData.setActiveEvent(com.playermarket.market.MarketEvent.STABLE_MARKET, server.overworld().getGameTime());

        // 2. Process all connected players and registered participants
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            campaignData.getImpairedPlayers().add(player.getUUID());
            PlayerMarketAccount account = savedData.getOrCreateAccount(player.getUUID());

            account.addCr(1_000_000L);
            account.setMarketCardBlocked(true);
            account.setPermanentImpaired(true);
            account.setEndingState("SETTLED");
            account.getActiveLeases().clear();
            account.getContracts().clear();
            account.getStoryProgress().setFinalChoice("ACCOUNT_SETTLEMENT");

            player.closeContainer();
            player.setDeltaMovement(0.0, 0.0, 0.0);
            player.needsSync = true;
            MarketAttributeApplier.syncPlayerModifiers(player);

            // Transform all access cards in inventory into BLOCKED MARKET CARD item
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty() && stack.is(MarketAccessCardItem.MARKET_ACCESS_CARD)) {
                    player.getInventory().setItem(i, new ItemStack(BlockedMarketCardItem.BLOCKED_MARKET_CARD));
                }
            }
            player.getInventory().setChanged();

            // Clear any huge vanilla titles; client HUD renders responsive wrapped fullscreen takeover
            player.connection.send(new ClientboundClearTitlesPacket(true));
        }

        for (UUID uuid : campaignData.getRegisteredParticipants()) {
            campaignData.getImpairedPlayers().add(uuid);
            PlayerMarketAccount account = savedData.getOrCreateAccount(uuid);
            account.setMarketCardBlocked(true);
            account.setPermanentImpaired(true);
            account.setEndingState("SETTLED");
            account.getActiveLeases().clear();
            account.getContracts().clear();
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
        endingLastTickWallMs = 0L;
        endingAccumulatedActiveMs = 0L;
        lastCueStep = 0;

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

        if (!"FINAL_SETTLEMENT_ACTIVE".equals(campaignData.getEndingState())) {
            return;
        }

        long nowMs = System.currentTimeMillis();
        if (endingLastTickWallMs <= 0L) {
            endingLastTickWallMs = nowMs;
        }
        long dtMs = Math.max(0L, Math.min(250L, nowMs - endingLastTickWallMs));
        endingLastTickWallMs = nowMs;
        endingAccumulatedActiveMs += dtMs;

        long tickElapsed = Math.max(0L, server.overworld().getGameTime() - campaignData.getEndingStartedTick());
        long activeMs = Math.max(endingAccumulatedActiveMs, tickElapsed * 50L);

        int cueToFire = 0;
        if (activeMs < 1600L && lastCueStep < 1) {
            lastCueStep = 1;
            cueToFire = 1;
        } else if (activeMs >= 1600L && lastCueStep < 2) {
            lastCueStep = 2;
            cueToFire = 2;
        } else if (activeMs >= 3200L && lastCueStep < 3) {
            lastCueStep = 3;
            cueToFire = 3;
        } else if (activeMs >= 4800L && lastCueStep < 4) {
            lastCueStep = 4;
            cueToFire = 4;
        } else if (activeMs >= 6400L && lastCueStep < 5) {
            lastCueStep = 5;
            cueToFire = 5;
        }

        for (ServerPlayer player : new java.util.ArrayList<>(server.getPlayerList().getPlayers())) {
            // Lock movement, rotation, containers & jump completely during the sequence
            player.closeContainer();
            player.setDeltaMovement(0.0, Math.min(0.0, player.getDeltaMovement().y), 0.0);
            player.needsSync = true;
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 255, false, false, false));
            if (activeMs < 8000L) {
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false, false));
            }

            if (cueToFire == 1) {
                player.sendSystemMessage(Component.literal("§6§l[FINAL SETTLEMENT] §eСчёт одобрен // +1,000,000 CR начислено."));
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 0.8f);
            } else if (cueToFire == 2) {
                player.sendSystemMessage(Component.literal("§c§l[ЛИКВИДАЦИЯ] §7Сбор биологического залога..."));
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f, 0.6f);
            } else if (cueToFire == 3) {
                player.sendSystemMessage(Component.literal("§4§l[САНКЦИИ] §cАннулирование доступа и портфеля..."));
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 0.8f, 0.7f);
            } else if (cueToFire == 4) {
                player.sendSystemMessage(Component.literal("§4§l[СТАТЬЯ 17.4] §7Применение постоянных модификаций..."));
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1.0f, 0.5f);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.8f, 0.6f);
            } else if (cueToFire == 5) {
                player.sendSystemMessage(Component.literal("§c§l[ДОСТУП АННУЛИРОВАН] §4Права собственности переданы корпорации."));
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.7f, 0.5f);
            }
        }

        if (activeMs >= 11250L) {
            // Finalize sequence immediately after the 1.95s blackout screamer (9.2s -> 11.15s)
            campaignData.setEndingState("SETTLED");
            campaignData.setCinematicCompleted(true);
            campaignData.setDisconnectSent(true);
            endingLastTickWallMs = 0L;
            endingAccumulatedActiveMs = 0L;
            lastCueStep = 0;
            savedData.setDirty();

            for (ServerPlayer target : new java.util.ArrayList<>(server.getPlayerList().getPlayers())) {
                target.removeEffect(MobEffects.BLINDNESS);
                target.removeEffect(MobEffects.SLOWNESS);
                campaignData.getDisconnectedPlayers().add(target.getUUID());
                MarketAttributeApplier.syncPlayerModifiers(target);
                com.playermarket.network.MarketNetworking.sendSyncToPlayer(target, "");
                target.connection.disconnect(Component.literal("§c§lДОСТУП ЗАБЛОКИРОВАН // СТАТЬЯ 17.4\n§7Права на активы переданы корпорации.\n§8Вы больше не клиент."));
            }
        }
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
