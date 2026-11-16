package com.playermarket.item;

import com.playermarket.PlayerMarketMod;
import com.playermarket.market.MarketSavedData;
import com.playermarket.market.PlayerMarketAccount;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

public class CorporateGrappleItem extends Item {

    public CorporateGrappleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        double reach = 30.0;
        Vec3 targetPos = eyePos.add(look.scale(reach));

        ClipContext context = new ClipContext(eyePos, targetPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player);
        BlockHitResult hit = level.clip(context);

        if (hit.getType() == HitResult.Type.BLOCK) {
            Vec3 hitVec = hit.getLocation();
            Vec3 pullVec = hitVec.subtract(player.position());
            double dist = pullVec.length();

            if (dist > 1.2) {
                Vec3 dir = pullVec.normalize();
                double speed = Math.min(2.2, 0.8 + dist * 0.07);
                Vec3 motion = new Vec3(dir.x * speed, Math.max(0.45, dir.y * speed + 0.38), dir.z * speed);

                player.setDeltaMovement(motion);
                player.resetFallDistance();

                if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.needsSync = true;
                    ServerLevel serverLevel = (ServerLevel) level;
                    int steps = (int) Math.min(32, dist * 2);
                    for (int i = 0; i <= steps; i++) {
                        double t = (double) i / steps;
                        double px = eyePos.x + (hitVec.x - eyePos.x) * t;
                        double py = eyePos.y + (hitVec.y - eyePos.y) * t;
                        double pz = eyePos.z + (hitVec.z - eyePos.z) * t;
                        serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, px, py, pz, 1, 0, 0, 0, 0);
                    }

                    serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.0F, 1.4F);
                    serverLevel.playSound(null, hitVec.x, hitVec.y, hitVec.z,
                            SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0F, 1.2F);

                    if (serverPlayer.level().getServer() != null) {
                        PlayerMarketAccount acc = MarketSavedData.get(serverPlayer.level().getServer()).getOrCreateAccount(serverPlayer.getUUID());
                        acc.setGrappleSafeTick(serverPlayer.level().getServer().overworld().getGameTime() + 75L);
                    }
                }

                player.getCooldowns().addCooldown(PlayerMarketMod.id("corporate_grapple"), 18);
                return InteractionResult.SUCCESS;
            }
        } else {
            if (!level.isClientSide()) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6F, 1.6F);
            }
        }

        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.literal("§6§l[КОРПОРАТИВНЫЙ КРЮК-КОШКА]"));
        tooltip.accept(Component.literal("§7Модификация ликвидаторов 17.4"));
        tooltip.accept(Component.literal("§eПКМ: §fВыстрелить тросом в дерево, землю или скалу."));
        tooltip.accept(Component.literal("§a✔ Полностью гасит урон от падения!"));
        tooltip.accept(Component.literal("§c⚠ Спасает от «Разрыва почвы» Главного Аудитора!"));
        tooltip.accept(Component.literal("§d★ Легендарный дроп из «Инвест-Рулетки 50/50»!"));
    }
}
