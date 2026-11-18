package com.playermarket.item;

import com.playermarket.story.ChiefAuditorBossManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

public class DoomHammerItem extends Item {

    public DoomHammerItem(Properties properties) {
        super(properties);
    }

    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!attacker.level().isClientSide() && attacker.level() instanceof ServerLevel sl) {
            boolean isAuditor = ChiefAuditorBossManager.isAuditorBoss(target);
            boolean staggered = ChiefAuditorBossManager.isCoreStaggered() || ChiefAuditorBossManager.isCoreOpen();

            if (isAuditor && staggered) {
                sl.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY() + 1.5, target.getZ(), 8, 0.5, 0.5, 0.5, 0.1);
                sl.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.2f, 0.6f);
                sl.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.0f, 1.2f);
            } else {
                for (LivingEntity nearby : sl.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(3.5))) {
                    if (nearby != attacker && nearby != target && !nearby.isAlliedTo(attacker)) {
                        nearby.hurtServer(sl, sl.damageSources().mobAttack(attacker), 7.0f);
                        sl.sendParticles(ParticleTypes.CRIT, nearby.getX(), nearby.getY() + 1.0, nearby.getZ(), 5, 0.2, 0.2, 0.2, 0.05);
                    }
                }
                sl.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.IRON_GOLEM_ATTACK, SoundSource.PLAYERS, 1.0f, 0.8f);
            }
        }
        super.hurtEnemy(stack, target, attacker);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.literal("§4§l[КРЕДИТНЫЙ МОЛОТ СУДНОГО ДНЯ]"));
        tooltip.accept(Component.literal("§7Тяжелейшее гидравлическое орудие Corp"));
        tooltip.accept(Component.literal("§cУрон: §fТяжёлый сокрушающий сплэш по области"));
        tooltip.accept(Component.literal("§6Спец-свойство: §e+200% урона по открытому ядру Аудитора!"));
        tooltip.accept(Component.literal("§d★ Легендарный джекпот из «Инвест-Рулетки 50/50»!"));
    }
}
