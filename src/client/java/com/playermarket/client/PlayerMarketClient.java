package com.playermarket.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.playermarket.PlayerMarketMod;
import com.playermarket.client.gui.CrumpledLetterScreen;
import com.playermarket.client.gui.MarketMainScreen;
import com.playermarket.client.hud.MarketHudOverlay;
import com.playermarket.client.render.OreScannerRenderer;
import com.playermarket.client.render.PlayerBraceRenderLayer;
import com.playermarket.item.CrumpledLetterItem;
import com.playermarket.item.MarketAccessCardItem;
import com.playermarket.network.MarketNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

public class PlayerMarketClient implements ClientModInitializer {
    public static KeyMapping openMarketKey;

    @Override
    public void onInitializeClient() {
        openMarketKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.playermarket.open_market",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_M,
                KeyMapping.Category.MISC
        ));

        // Connect Crumpled Letter use to open Document Screen
        CrumpledLetterItem.CLIENT_OPEN_LETTER_CALLBACK = player -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == player && client.gui != null) {
                client.gui.setScreen(new CrumpledLetterScreen());
            }
        };

        // Connect item use to open Market Screen
        MarketAccessCardItem.CLIENT_OPEN_SCREEN_CALLBACK = player -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == player && client.gui != null) {
                if (ClientMarketState.getAccount().isMarketCardBlocked() || ClientMarketState.getAccount().isAccountClosed()) {
                    client.player.sendOverlayMessage(Component.literal("§cACCESS REVOKED."));
                    client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ITEM_BREAK.value(), 1.0f));
                    return;
                }
                ClientPlayNetworking.send(new MarketNetworking.MarketActionPayload("REQUEST_SYNC", "", "", 0, 0.0));
                client.gui.setScreen(new MarketMainScreen());
            }
        };

        // Register visual orthopedic brace on Player Model
        LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
            if (entityRenderer instanceof AvatarRenderer avatarRenderer) {
                helper.register(new PlayerBraceRenderLayer(avatarRenderer, context));
            }
        });

        // Network receiver for real-time market and shared campaign data
        ClientPlayNetworking.registerGlobalReceiver(MarketNetworking.MarketSyncPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> ClientMarketState.applySync(payload));
        });

        // Register In-Game HUD overlay & Ore Scanner sonar pulse renderer
        HudElementRegistry.addLast(PlayerMarketMod.id("market_hud"), MarketHudOverlay::render);
        OreScannerRenderer.register();

        // Keybinding listener for key 'M' + ending cinematic screen lock + scanner tick
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            OreScannerRenderer.tickScan(client);
            if (MarketHudOverlay.isEndingCinematicPlaying()) {
                if (client.gui != null && client.gui.screen() != null
                        && !(client.gui.screen() instanceof net.minecraft.client.gui.screens.DisconnectedScreen)
                        && !(client.gui.screen() instanceof net.minecraft.client.gui.screens.GenericWaitingScreen)) {
                    client.gui.setScreen(null);
                }
                while (openMarketKey.consumeClick()) {}
                return;
            }
            while (openMarketKey.consumeClick()) {
                if (client.player != null && client.gui != null && client.gui.screen() == null) {
                    if (ClientMarketState.getAccount().isMarketCardBlocked() || ClientMarketState.getAccount().isAccountClosed()) {
                        client.player.sendOverlayMessage(Component.literal("§cACCESS REVOKED."));
                        client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ITEM_BREAK.value(), 1.0f));
                        continue;
                    }
                    boolean hasCard = false;
                    for (int i = 0; i < client.player.getInventory().getContainerSize(); i++) {
                        ItemStack stack = client.player.getInventory().getItem(i);
                        if (!stack.isEmpty() && stack.is(MarketAccessCardItem.MARKET_ACCESS_CARD)) {
                            hasCard = true;
                            break;
                        }
                    }
                    if (!hasCard) {
                        client.player.sendOverlayMessage(
                                Component.literal("§8[§cPLAYER MARKET§8] §cТребуется Карта Доступа к бирже в инвентаре! §7(Используйте /market card)")
                        );
                        continue;
                    }
                    ClientPlayNetworking.send(new MarketNetworking.MarketActionPayload("REQUEST_SYNC", "", "", 0, 0.0));
                    client.gui.setScreen(new MarketMainScreen());
                }
            }
        });
    }
}
