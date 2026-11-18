package com.playermarket.entity;

import com.playermarket.PlayerMarketMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {

    public static final ResourceKey<EntityType<?>> CHIEF_AUDITOR_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, PlayerMarketMod.id("chief_auditor"));

    public static final EntityType<ChiefAuditorBossEntity> CHIEF_AUDITOR = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            CHIEF_AUDITOR_KEY,
            EntityType.Builder.of(ChiefAuditorBossEntity::new, MobCategory.MONSTER)
                    .sized(2.4f, 4.3f)
                    .eyeHeight(3.75f)
                    .fireImmune()
                    .clientTrackingRange(12)
                    .updateInterval(2)
                    .build(CHIEF_AUDITOR_KEY)
    );

    private ModEntities() {}

    public static void registerEntities() {
        FabricDefaultAttributeRegistry.register(CHIEF_AUDITOR, ChiefAuditorBossEntity.createAttributes());
    }
}
