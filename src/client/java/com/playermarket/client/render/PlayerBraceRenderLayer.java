package com.playermarket.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.playermarket.PlayerMarketMod;
import com.playermarket.client.ClientMarketState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public class PlayerBraceRenderLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static final Identifier BRACE_TEXTURE = PlayerMarketMod.id("textures/entity/leg_brace.png");
    private final HumanoidModel<AvatarRenderState> braceModel;

    public PlayerBraceRenderLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, EntityRendererProvider.Context context) {
        super(parent);
        this.braceModel = new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER));
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float f, float g) {
        if (state.isSpectator) return;

        boolean impaired = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            Entity e = mc.level.getEntity(state.id);
            if (e instanceof Player p) {
                impaired = ClientMarketState.isPlayerImpaired(p.getUUID());
            }
        }

        if (impaired) {
            // Configure model to display only the right leg support brace
            this.braceModel.head.visible = false;
            this.braceModel.hat.visible = false;
            this.braceModel.body.visible = false;
            this.braceModel.rightArm.visible = false;
            this.braceModel.leftArm.visible = false;
            this.braceModel.leftLeg.visible = false;
            this.braceModel.rightLeg.visible = true;
            this.braceModel.setupAnim(state);

            // Slightly scale outward around knee and shin to overlay cleanly on trousers/armor
            poseStack.pushPose();
            renderColoredCutoutModel(this.braceModel, BRACE_TEXTURE, poseStack, collector, light, state, OverlayTexture.NO_OVERLAY, -1);
            poseStack.popPose();
        }
    }
}
