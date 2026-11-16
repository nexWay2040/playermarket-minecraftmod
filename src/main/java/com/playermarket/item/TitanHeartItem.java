package com.playermarket.item;

import com.playermarket.PlayerMarketMod;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

public class TitanHeartItem extends Item {

    public TitanHeartItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && level instanceof ServerLevel sl) {
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 6000, 3, false, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 1200, 1, false, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 400, 1, false, true, true));

            sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.0, player.getZ(), 35, 0.5, 0.7, 0.5, 0.15);
            sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0f, 1.1f);
            player.sendSystemMessage(Component.literal("§6§l[СЕРДЦЕ ТИТАНА] §eБио-щит активирован: +8 золотых сердец и защита от летального урона!"));
        }

        player.getCooldowns().addCooldown(PlayerMarketMod.id("titan_heart"), 600);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.literal("§6§l[СЕРДЦЕ ТИТАНА // БИО-ЯДРО]"));
        tooltip.accept(Component.literal("§7Имплант высшей категории защиты"));
        tooltip.accept(Component.literal("§eПКМ: §f+8 золотых сердец (Поглощение IV) и Сопротивление II"));
        tooltip.accept(Component.literal("§bЭффект: §aСпасает от верной гибели в бою с Аудитором!"));
        tooltip.accept(Component.literal("§d★ Легендарный джекпот из «Инвест-Рулетки 50/50»!"));
    }
}
