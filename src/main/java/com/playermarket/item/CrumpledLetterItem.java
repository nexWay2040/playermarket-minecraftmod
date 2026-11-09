package com.playermarket.item;

import com.playermarket.PlayerMarketMod;
import com.playermarket.network.MarketNetworking;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

public class CrumpledLetterItem extends Item {
    public static Consumer<Player> CLIENT_OPEN_LETTER_CALLBACK = p -> {};

    public static final ResourceKey<Item> LETTER_KEY = ResourceKey.create(
            Registries.ITEM,
            PlayerMarketMod.id("crumpled_letter")
    );

    public static final Item CRUMPLED_LETTER = Registry.register(
            BuiltInRegistries.ITEM,
            LETTER_KEY,
            new CrumpledLetterItem(new Item.Properties()
                    .setId(LETTER_KEY)
                    .stacksTo(1)
                    .rarity(Rarity.UNCOMMON))
    );

    public CrumpledLetterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            CLIENT_OPEN_LETTER_CALLBACK.accept(player);
        } else if (player instanceof ServerPlayer sp) {
            // Server validates reading and delivers the contract offer
            com.playermarket.story.StoryManager.onPlayerReadLetter(sp);
            MarketNetworking.sendSyncToPlayer(sp);
        }
        return InteractionResult.SUCCESS;
    }

    public static void registerItems() {
        ResourceKey<CreativeModeTab> toolsTab = ResourceKey.create(
                Registries.CREATIVE_MODE_TAB,
                net.minecraft.resources.Identifier.withDefaultNamespace("tools_and_utilities")
        );
        CreativeModeTabEvents.modifyOutputEvent(toolsTab).register(output -> output.accept(CRUMPLED_LETTER));
    }
}
