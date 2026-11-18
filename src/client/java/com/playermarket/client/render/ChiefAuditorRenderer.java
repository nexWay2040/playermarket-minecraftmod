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
 * Note: AutoGlowingGeoLayer is intentionally omitted on MC 26.3 because GeckoLib 5.5.7's
 * geckolib_emissive render type does not register OIT pipelines and crashes the client renderer.
 */
public class ChiefAuditorRenderer extends GeoEntityRenderer<ChiefAuditorBossEntity, LivingEntityRenderState> {

    public ChiefAuditorRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(PlayerMarketMod.id("chief_auditor")));
        this.shadowRadius = 1.8f;
    }

    @Override
    protected float getDeathMaxRotation(GeoRenderState renderState) {
        return 0.0F;
    }
}
