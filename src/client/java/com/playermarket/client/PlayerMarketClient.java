package com.playermarket.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.playermarket.PlayerMarketMod;
import com.playermarket.client.gui.MarketMainScreen;
import com.playermarket.client.hud.MarketHudOverlay;
import com.playermarket.item.MarketAccessCardItem;
import com.playermarket.network.MarketNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
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

        // Connect item use to open Market Screen
        MarketAccessCardItem.CLIENT_OPEN_SCREEN_CALLBACK = player -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == player && client.gui != null) {
                if (ClientMarketState.getAccount().isAccountClosed()) {
                    client.player.sendOverlayMessage(
                            Component.literal("§8[§cPLAYER MARKET§8] §cУчётная запись закрыта по требованию клиента. Вход заблокирован.")
                    );
                    return;
                }
                ClientPlayNetworking.send(new MarketNetworking.MarketActionPayload("REQUEST_SYNC", "", "", 0, 0.0));
                client.gui.setScreen(new MarketMainScreen());
            }
        };

        // Network receiver for real-time market data
        ClientPlayNetworking.registerGlobalReceiver(MarketNetworking.MarketSyncPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> ClientMarketState.applySync(payload));
        });

        // Register In-Game HUD overlay
        HudElementRegistry.addLast(PlayerMarketMod.id("market_hud"), MarketHudOverlay::render);

        // Keybinding listener for key 'M' (Only opens terminal if player actually carries the Access Card)
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMarketKey.consumeClick()) {
                if (client.player != null && client.gui != null && client.gui.screen() == null) {
                    if (ClientMarketState.getAccount().isAccountClosed()) {
                        client.player.sendOverlayMessage(
                                Component.literal("§8[§cPLAYER MARKET§8] §cУчётная запись закрыта по требованию клиента. Вход заблокирован.")
                        );
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
