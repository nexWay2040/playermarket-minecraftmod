package com.playermarket.item;

import com.playermarket.PlayerMarketMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * Custom 3D prop item models rendered inside synchronized Display entities for corporate events.
 */
public final class MarketPropItems {

    public static final Item PROP_HR_BOMB = registerProp("prop_hr_bomb", Rarity.EPIC);
    public static final Item PROP_SUPPLY_CRATE = registerProp("prop_supply_crate", Rarity.RARE);
    public static final Item PROP_ATM_SAFE = registerProp("prop_atm_safe", Rarity.EPIC);
    public static final Item PROP_AUDIT_DRONE_BODY = registerProp("prop_audit_drone_body", Rarity.RARE);
    public static final Item PROP_AUDIT_DRONE_ROTORS = registerProp("prop_audit_drone_rotors", Rarity.UNCOMMON);
    public static final Item PROP_BOUNTY_CROWN = registerProp("prop_bounty_crown", Rarity.EPIC);
    public static final Item PROP_MYSTERY_CAPSULE = registerProp("prop_mystery_capsule", Rarity.EPIC);
    public static final Item PROP_CCTV_POD = registerProp("prop_cctv_pod", Rarity.UNCOMMON);
    public static final Item PROP_VERITY_KOLOBOK = registerProp("prop_verity_kolobok", Rarity.EPIC);

    private MarketPropItems() {
    }

    private static Item registerProp(String id, Rarity rarity) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, PlayerMarketMod.id(id));
        return Registry.register(
                BuiltInRegistries.ITEM,
                key,
                new Item(new Item.Properties().setId(key).stacksTo(1).rarity(rarity))
        );
    }

    public static void registerItems() {
        // Static class initialization triggers item registrations in BuiltInRegistries.ITEM.
    }
}
