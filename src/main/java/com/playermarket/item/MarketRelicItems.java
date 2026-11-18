package com.playermarket.item;

import com.playermarket.PlayerMarketMod;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

public final class MarketRelicItems {

    public static final ResourceKey<Item> GRAPPLE_KEY = ResourceKey.create(
            Registries.ITEM, PlayerMarketMod.id("corporate_grapple"));
    public static final Item CORPORATE_GRAPPLE = Registry.register(
            BuiltInRegistries.ITEM, GRAPPLE_KEY,
            new CorporateGrappleItem(new Item.Properties().setId(GRAPPLE_KEY).stacksTo(1).rarity(Rarity.EPIC).durability(250))
    );

    public static final ResourceKey<Item> HAMMER_KEY = ResourceKey.create(
            Registries.ITEM, PlayerMarketMod.id("doom_hammer"));
    public static final Item DOOM_HAMMER = Registry.register(
            BuiltInRegistries.ITEM, HAMMER_KEY,
            new DoomHammerItem(new Item.Properties().setId(HAMMER_KEY).stacksTo(1).rarity(Rarity.EPIC).durability(1200))
    );

    public static final ResourceKey<Item> TITAN_HEART_KEY = ResourceKey.create(
            Registries.ITEM, PlayerMarketMod.id("titan_heart"));
    public static final Item TITAN_HEART = Registry.register(
            BuiltInRegistries.ITEM, TITAN_HEART_KEY,
            new TitanHeartItem(new Item.Properties().setId(TITAN_HEART_KEY).stacksTo(4).rarity(Rarity.EPIC))
    );

    public static final ResourceKey<Item> GOLDEN_EXPLOIT_KEY = ResourceKey.create(
            Registries.ITEM, PlayerMarketMod.id("golden_exploit"));
    public static final Item GOLDEN_EXPLOIT = Registry.register(
            BuiltInRegistries.ITEM, GOLDEN_EXPLOIT_KEY,
            new GoldenExploitItem(new Item.Properties().setId(GOLDEN_EXPLOIT_KEY).stacksTo(16).rarity(Rarity.RARE))
    );

    public static final ResourceKey<Item> AUDIT_SUMMON_KEY = ResourceKey.create(
            Registries.ITEM, PlayerMarketMod.id("audit_summon"));
    public static final Item AUDIT_SUMMON = Registry.register(
            BuiltInRegistries.ITEM, AUDIT_SUMMON_KEY,
            new AuditSummonItem(new Item.Properties().setId(AUDIT_SUMMON_KEY).stacksTo(1).rarity(Rarity.EPIC))
    );

    private MarketRelicItems() {}

    public static void registerItems() {
        ResourceKey<CreativeModeTab> toolsTab = ResourceKey.create(
                Registries.CREATIVE_MODE_TAB,
                net.minecraft.resources.Identifier.withDefaultNamespace("tools_and_utilities")
        );
        CreativeModeTabEvents.modifyOutputEvent(toolsTab).register(output -> {
            output.accept(CORPORATE_GRAPPLE);
            output.accept(DOOM_HAMMER);
            output.accept(TITAN_HEART);
            output.accept(GOLDEN_EXPLOIT);
            output.accept(AUDIT_SUMMON);
        });
    }
}
