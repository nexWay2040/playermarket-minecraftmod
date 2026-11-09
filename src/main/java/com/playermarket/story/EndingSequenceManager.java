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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EndingSequenceManager {

    private record ActiveSequence(UUID playerId, String type, int ticksElapsed) {}
    private static final Map<UUID, ActiveSequence> ACTIVE_SEQUENCES = new ConcurrentHashMap<>();

    public static void startMainEnding(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        MarketSavedData savedData = MarketSavedData.get(server);
        PlayerMarketAccount account = savedData.getOrCreateAccount(player.getUUID());

        account.setEndingState("MAIN_TRIGGERED");
        savedData.setDirty();
        MarketNetworking.sendSyncToPlayer(player, "§c[СИСТЕМА] ИНИЦИАЛИЗАЦИЯ ФИНАЛЬНОГО РАСЧЁТА...");

        ACTIVE_SEQUENCES.put(player.getUUID(), new ActiveSequence(player.getUUID(), "MAIN", 0));
    }

    public static void startSecretEnding(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        MarketSavedData savedData = MarketSavedData.get(server);
        PlayerMarketAccount account = savedData.getOrCreateAccount(player.getUUID());

        account.setEndingState("SECRET_TRIGGERED");
        savedData.setDirty();
        MarketNetworking.sendSyncToPlayer(player, "§4[СИСТЕМА] РАСТОРЖЕНИЕ ДОГОВОРА...");

        ACTIVE_SEQUENCES.put(player.getUUID(), new ActiveSequence(player.getUUID(), "SECRET", 0));
    }

    public static void resetEnding(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        MarketSavedData savedData = MarketSavedData.get(server);
        PlayerMarketAccount account = savedData.getOrCreateAccount(player.getUUID());

        account.setEndingState("NONE");
        account.setAccountClosed(false);
        savedData.setDirty();
        ACTIVE_SEQUENCES.remove(player.getUUID());
        MarketAttributeApplier.syncPlayerModifiers(player);
        MarketNetworking.sendSyncToPlayer(player, "§aСтатус концовки сброшен.");
    }

    public static void tickServer(MinecraftServer server) {
        if (ACTIVE_SEQUENCES.isEmpty()) return;

        List<UUID> toRemove = new ArrayList<>();

        for (Map.Entry<UUID, ActiveSequence> entry : ACTIVE_SEQUENCES.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || player.hasDisconnected()) {
                toRemove.add(entry.getKey());
                continue;
            }

            ActiveSequence seq = entry.getValue();
            int tick = seq.ticksElapsed;
            MarketSavedData savedData = MarketSavedData.get(server);
            PlayerMarketAccount account = savedData.getOrCreateAccount(player.getUUID());

            if ("MAIN".equals(seq.type)) {
                // Freeze controls & darken vision safely
                player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 255, false, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false, false));

                if (tick == 0) {
                    player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 45, 10));
                    sendTitle(player, "§c§lLIQUIDATING ASSETS...", "§7Оценка и списание зарегистрированных прав");
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f, 0.6f);
                } else if (tick == 45) {
                    sendTitle(player, "§c§lTRANSFERRING OWNERSHIP...", "§7Передача права собственности корпорации");
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 0.8f, 0.7f);
                } else if (tick == 90) {
                    sendTitle(player, "§c§lASSET #000 SUCCESSFULLY ACQUIRED.", "§eСубъект переведён в реестр корпоративного имущества");
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.6f, 0.5f);
                } else if (tick >= 130) {
                    // Finalize Main Ending
                    account.setEndingState("MAIN_COMPLETED");
                    account.getStoryProgress().setFinalChoice("TOTAL_LIQUIDATION");
                    savedData.setDirty();

                    toRemove.add(entry.getKey());

                    player.connection.disconnect(Component.literal("ACCOUNT OWNERSHIP TRANSFERRED. YOU ARE NO LONGER THE CLIENT."));
                    continue;
                }
            } else if ("SECRET".equals(seq.type)) {
                player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 255, false, false, false));

                if (tick == 0) {
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1.0f, 0.5f);
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.ENDERMAN_SCREAM, SoundSource.PLAYERS, 0.7f, 0.6f);
                    sendTitle(player, "§4§lACCOUNT TERMINATED BY CLIENT REQUEST.", "§cРазрыв договора в соответствии с п. 17.4");
                } else if (tick >= 55) {
                    // Finalize Secret Ending:
                    // 1. All sold stats restored to original 100% baseline!
                    account.getPermanentSoldFractions().clear();
                    account.getPermanentSalePayoutBasis().clear();
                    account.getActiveLeases().clear();
                    // 2. CR account closed
                    account.setCrBalance(0L);
                    account.setAccountClosed(true);
                    account.setEndingState("SECRET_COMPLETED");
                    account.getStoryProgress().setFinalChoice("COMPLETE_RECOVERY");

                    // 3. Add Final Chilling Mail
                    account.getStoryProgress().addMail(new MailMessage(
                            "secret_revocation_letter",
                            server.overworld().getGameTime(),
                            "MR. LEDGER",
                            "РАСТОРЖЕНИЕ ДОГОВОРА",
                            "We respect your decision.\n\nWe do not forgive it.",
                            false,
                            "",
                            0L,
                            true
                    ));

                    // 4. Restore attributes
                    MarketAttributeApplier.syncPlayerModifiers(player);

                    savedData.setDirty();

                    toRemove.add(entry.getKey());

                    player.connection.disconnect(Component.literal("ACCOUNT TERMINATED BY CLIENT REQUEST."));
                    continue;
                }
            }

            ACTIVE_SEQUENCES.put(entry.getKey(), new ActiveSequence(seq.playerId, seq.type, tick + 1));
        }

        for (UUID u : toRemove) {
            ACTIVE_SEQUENCES.remove(u);
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

        if ("MAIN_COMPLETED".equals(account.getEndingState())) {
            // Transform Market Access Cards into EMPLOYEE ACCESS CARD
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty() && stack.is(MarketAccessCardItem.MARKET_ACCESS_CARD)) {
                    stack.set(DataComponents.CUSTOM_NAME, Component.literal("§6§lEMPLOYEE ACCESS CARD"));
                    List<Component> lore = List.of(
                            Component.literal("§7[CORPORATE ASSET DIVISION]"),
                            Component.literal("§8ID: ASSET-#000"),
                            Component.literal("§eСотрудник: §f" + player.getName().getString()),
                            Component.literal("§cСтатус: СОБСТВЕННОСТЬ КОРПОРАЦИИ")
                    );
                    stack.set(DataComponents.LORE, new ItemLore(lore));
                }
            }
            player.getInventory().setChanged();

            // Display employee re-login broadcast
            player.sendSystemMessage(Component.literal("§8===================================================="));
            player.sendSystemMessage(Component.literal("§6§lCORPORATE INTERNAL SYSTEM // RE-LOGIN"));
            player.sendSystemMessage(Component.literal("§7CUSTOMER: §c" + player.getName().getString()));
            player.sendSystemMessage(Component.literal("§7ACCOUNT MANAGER: §e" + player.getName().getString()));
            player.sendSystemMessage(Component.literal("§c§lFIRST ASSIGNMENT: EVALUATE YOUR REPLACEMENT."));
            player.sendSystemMessage(Component.literal("§8===================================================="));

            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.9f, 1.0f);
        } else if ("SECRET_COMPLETED".equals(account.getEndingState())) {
            // Secret Ending re-login notification
            MarketAttributeApplier.syncPlayerModifiers(player);

            player.sendSystemMessage(Component.literal("§8===================================================="));
            player.sendSystemMessage(Component.literal("§7[PLAYER MARKET // ACCOUNT STATUS]"));
            player.sendSystemMessage(Component.literal("§cУчётная запись закрыта по требованию клиента."));
            player.sendSystemMessage(Component.literal("§aБиологические характеристики полностью восстановлены до 100%."));
            player.sendSystemMessage(Component.literal("§7Вам пришло 1 новое письмо в почтовый ящик."));
            player.sendSystemMessage(Component.literal("§8===================================================="));
        }
    }
}
