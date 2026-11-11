package com.playermarket.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.playermarket.PlayerMarketMod;
import com.playermarket.client.ClientMarketState;
import com.playermarket.client.gui.MarketMainScreen;
import com.playermarket.market.AssetType;
import com.playermarket.market.PlayerMarketAccount;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public class PlayerBraceRenderLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static final Identifier BRACE_TEXTURE = PlayerMarketMod.id("textures/entity/leg_brace.png");
    private static final Identifier BIO_HIGHLIGHT_TEXTURE = PlayerMarketMod.id("textures/entity/bio_highlight.png");
    private static final int FULL_BRIGHT = 15728880;

    // Separate dedicated models prevent deferred SubmitNodeCollector visibility overwrite
    private final HumanoidModel<AvatarRenderState> braceModel;
    private final HumanoidModel<AvatarRenderState> headOverlayModel;
    private final HumanoidModel<AvatarRenderState> torsoOverlayModel;
    private final HumanoidModel<AvatarRenderState> armsOverlayModel;
    private final HumanoidModel<AvatarRenderState> legsOverlayModel;

    public PlayerBraceRenderLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, EntityRendererProvider.Context context) {
        super(parent);
        this.braceModel = createZoneModel(0.68f, false, false, false, true);
        this.headOverlayModel = createZoneModel(0.42f, true, false, false, false);
        this.torsoOverlayModel = createZoneModel(0.42f, false, true, false, false);
        this.armsOverlayModel = createZoneModel(0.42f, false, false, true, false);
        this.legsOverlayModel = createZoneModel(0.42f, false, false, false, true);
    }

    private static HumanoidModel<AvatarRenderState> createZoneModel(float inflation,
                                                                     boolean head, boolean torso,
                                                                     boolean arms, boolean rightLegOnlyOrLegs) {
        var mesh = HumanoidModel.createMesh(new CubeDeformation(inflation), 0.0f);
        HumanoidModel<AvatarRenderState> model = new HumanoidModel<>(LayerDefinition.create(mesh, 64, 64).bakeRoot());
        model.head.visible = head;
        model.hat.visible = false;
        model.body.visible = torso;
        model.rightArm.visible = arms;
        model.leftArm.visible = arms;
        model.rightLeg.visible = rightLegOnlyOrLegs;
        model.leftLeg.visible = (inflation < 0.60f) && rightLegOnlyOrLegs;
        return model;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float f, float g) {
        if (state.isSpectator) return;

        boolean impaired = false;
        boolean isLocalInTerminal = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            Entity e = mc.level.getEntity(state.id);
            if (e instanceof Player p) {
                impaired = ClientMarketState.isPlayerImpaired(p.getUUID());
                isLocalInTerminal = (mc.player != null && p == mc.player
                        && mc.gui != null && mc.gui.screen() instanceof MarketMainScreen);
            }
        }

        // 1. Live 3D Bio-Monitor limb highlight overlay directly on the 3D player model
        if (isLocalInTerminal) {
            PlayerMarketAccount account = ClientMarketState.getAccount();
            double headSold = MarketMainScreen.getZoneLoss(account,
                    AssetType.OXYGEN_CAPACITY, AssetType.SATURATION_EFFICIENCY, AssetType.HUNGER_EFFICIENCY, AssetType.XP_GAIN, AssetType.LUCK);
            double torsoSold = MarketMainScreen.getZoneLoss(account,
                    AssetType.MAX_HEALTH, AssetType.NATURAL_REGENERATION, AssetType.ARMOR, AssetType.ARMOR_TOUGHNESS, AssetType.FIRE_RESISTANCE, AssetType.MAX_ABSORPTION, AssetType.POTION_EFFECTIVENESS);
            double armsSold = MarketMainScreen.getZoneLoss(account,
                    AssetType.ATTACK_DAMAGE, AssetType.ATTACK_SPEED, AssetType.MINING_SPEED, AssetType.UNDERWATER_MINING, AssetType.BLOCK_REACH, AssetType.ENTITY_REACH, AssetType.KNOCKBACK_POWER, AssetType.CRITICAL_DAMAGE);
            double legsSold = MarketMainScreen.getZoneLoss(account,
                    AssetType.MOVEMENT_SPEED, AssetType.JUMP_STRENGTH, AssetType.STEP_HEIGHT, AssetType.SPRINT_EFFICIENCY, AssetType.SWIM_SPEED, AssetType.FALL_RESISTANCE, AssetType.KNOCKBACK_RESISTANCE);

            String hovered = MarketMainScreen.getHoveredBioZone();
            submitZoneOverlay(this.headOverlayModel, "HEAD".equals(hovered), headSold, poseStack, collector, state);
            submitZoneOverlay(this.torsoOverlayModel, "TORSO".equals(hovered), torsoSold, poseStack, collector, state);
            submitZoneOverlay(this.armsOverlayModel, "ARMS".equals(hovered), armsSold, poseStack, collector, state);
            submitZoneOverlay(this.legsOverlayModel, "LEGS".equals(hovered), legsSold, poseStack, collector, state);
        }

        // 2. Permanent Section 17.4 exoskeleton right-leg brace
        if (impaired) {
            this.braceModel.setupAnim(state);
            poseStack.pushPose();
            collector.order(2).submitModel(
                    this.braceModel, state, poseStack,
                    RenderTypes.armorCutoutNoCull(BRACE_TEXTURE),
                    light, OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor
            );
            poseStack.popPose();
        }
    }

    private static void submitZoneOverlay(HumanoidModel<AvatarRenderState> zoneModel, boolean hovered, double soldFrac,
                                          PoseStack poseStack, SubmitNodeCollector collector, AvatarRenderState state) {
        if (!hovered && soldFrac <= 0.005) return;

        int rgb;
        int alpha;
        double wave = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 240.0);
        if (soldFrac > 0.005) {
            rgb = MarketMainScreen.getZoneRgb(soldFrac) & 0x00FFFFFF;
            alpha = hovered ? 0xB8 : (int) (0x58 + wave * 0x40);
        } else {
            rgb = 0x38BDF8;
            alpha = 0x85;
        }
        int tintColor = (alpha << 24) | rgb;

        zoneModel.setupAnim(state);
        poseStack.pushPose();
        collector.order(1).submitModel(
                zoneModel, state, poseStack,
                RenderTypes.entityTranslucentEmissive(BIO_HIGHLIGHT_TEXTURE),
                FULL_BRIGHT, OverlayTexture.NO_OVERLAY, tintColor, null, 0
        );
        poseStack.popPose();
    }
}
