package com.playermarket.item;

import com.playermarket.PlayerMarketMod;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

public class BlockedMarketCardItem extends Item {

    public static final ResourceKey<Item> KEY = ResourceKey.create(
            Registries.ITEM,
            PlayerMarketMod.id("blocked_market_card")
    );

    public static final Item BLOCKED_MARKET_CARD = Registry.register(
            BuiltInRegistries.ITEM,
            KEY,
            new BlockedMarketCardItem(new Item.Properties()
                    .setId(KEY)
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)
                    .fireResistant())
    );

    public BlockedMarketCardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.level().playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                    SoundEvents.CHEST_LOCKED, SoundSource.PLAYERS, 1.0f, 0.6f);
            serverPlayer.sendSystemMessage(Component.translatable("message.playermarket.access_revoked"));
        } else if (level.isClientSide()) {
            player.sendOverlayMessage(Component.translatable("message.playermarket.access_revoked_overlay"));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.literal("§4[TERMINATED BY SECTION 17.4]"));
        tooltip.accept(Component.literal("§8ID: ASSET-#000"));
        tooltip.accept(Component.literal("§cSTATUS: ACCESS REVOKED"));
        tooltip.accept(Component.literal("§7Biomechanical collateral claimed."));
    }

    public static void registerItems() {
        ResourceKey<CreativeModeTab> toolsTab = ResourceKey.create(
                Registries.CREATIVE_MODE_TAB,
                net.minecraft.resources.Identifier.withDefaultNamespace("tools_and_utilities")
        );
        CreativeModeTabEvents.modifyOutputEvent(toolsTab).register(output -> output.accept(BLOCKED_MARKET_CARD));
    }
}
