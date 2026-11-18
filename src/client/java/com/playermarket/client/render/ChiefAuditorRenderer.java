package com.playermarket.client.render;

import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.playermarket.PlayerMarketMod;
import com.playermarket.entity.ChiefAuditorBossEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * GeckoLib 5 renderer for the Chief Auditor Titan Boss.
 * Loads assets/playermarket/geckolib/models/entity/chief_auditor.geo.json,
 * assets/playermarket/geckolib/animations/entity/chief_auditor.animation.json,
 * and assets/playermarket/textures/entity/chief_auditor.png.
 */
public class ChiefAuditorRenderer extends GeoEntityRenderer<ChiefAuditorBossEntity, LivingEntityRenderState> {

    public ChiefAuditorRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(PlayerMarketMod.id("chief_auditor")));
        this.shadowRadius = 2.0f;
    }

    @Override
    protected float getDeathMaxRotation(GeoRenderState renderState) {
        return 0.0F;
    }
}
