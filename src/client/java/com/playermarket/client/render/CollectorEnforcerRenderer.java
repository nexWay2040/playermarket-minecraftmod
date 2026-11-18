package com.playermarket.client.render;

import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.playermarket.entity.CollectorEnforcerEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/**
 * Клиентский 3D-рендерер Корпоративного Силовика-Коллектора на GeckoLib 5.5.7.
 * <p>
 * Подгружает:
 * <ul>
 *     <li>assets/playermarket/geckolib/models/entity/collector_enforcer.geo.json</li>
 *     <li>assets/playermarket/geckolib/animations/entity/collector_enforcer.animation.json</li>
 *     <li>assets/playermarket/textures/entity/collector_enforcer.png</li>
 * </ul>
 */
public class CollectorEnforcerRenderer extends GeoEntityRenderer<CollectorEnforcerEntity, LivingEntityRenderState> {

    private static final float MODEL_SCALE = 1.15F;

    public CollectorEnforcerRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(
                Identifier.fromNamespaceAndPath("playermarket", "collector_enforcer")
        ));
        this.withScale(MODEL_SCALE);
        this.shadowRadius = 0.62F;
    }

    @Override
    protected float getDeathMaxRotation(GeoRenderState renderState) {
        return 0.0F;
    }
}