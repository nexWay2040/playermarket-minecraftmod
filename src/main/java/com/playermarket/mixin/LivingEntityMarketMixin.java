package com.playermarket.mixin;

import com.playermarket.market.*;
import com.playermarket.network.MarketNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMarketMixin {

    @ModifyVariable(method = "heal", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float playermarket$modifyHealAmount(float amount) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof ServerPlayer player && player.level().getServer() != null) {
            PlayerMarketAccount acc = MarketSavedData.get(player.level().getServer()).getOrCreateAccount(player.getUUID());
            double soldRegen = acc.getTotalSoldFraction(AssetType.NATURAL_REGENERATION);
            int recoveryLvl = acc.getUpgradeLevel(UpgradeType.RAPID_RECOVERY);
            double mult = 1.0 - soldRegen;
            if (recoveryLvl > 0) {
                mult += UpgradeType.RAPID_RECOVERY.getValueForLevel(recoveryLvl);
            }
            return (float) Math.max(0.05, amount * mult);
        }
        return amount;
    }

    @Inject(method = "calculateFallDamage", at = @At("RETURN"), cancellable = true)
    private void playermarket$modifyFallDamageCalculation(double fallDistance, float damageMultiplier, CallbackInfoReturnable<Integer> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof ServerPlayer player && player.level().getServer() != null) {
            PlayerMarketAccount acc = MarketSavedData.get(player.level().getServer()).getOrCreateAccount(player.getUUID());
            long gameTick = player.level().getServer().overworld().getGameTime();
            if (acc.isBlackMarketEffectActive(BlackMarketItem.PHANTOM_STEP, gameTick)
                    || acc.isBlackMarketEffectActive(BlackMarketItem.EMERGENCY_BATTERY, gameTick)) {
                return;
            }
            double soldFall = acc.getTotalSoldFraction(AssetType.FALL_RESISTANCE);
            int bonesLvl = acc.getUpgradeLevel(UpgradeType.REINFORCED_BONES);
            if (soldFall > 0.01) {
                double safeDist = Math.max(0.45, 3.0 - (soldFall * 3.0) + (bonesLvl * 1.1));
                if (fallDistance > safeDist) {
                    double excess = fallDistance - safeDist;
                    double mult = 1.0 + (soldFall * 2.25);
                    if (bonesLvl > 0) {
                        mult *= Math.max(0.35, 1.0 + UpgradeType.REINFORCED_BONES.getValueForLevel(bonesLvl));
                    }
                    int extraDmg = Mth.ceil(excess * damageMultiplier * mult);
                    if (extraDmg > cir.getReturnValue()) {
                        cir.setReturnValue(extraDmg);
                    }
                }
            }
        }
    }

    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float playermarket$modifyIncomingAndOutgoingDamage(float amount, ServerLevel level, DamageSource source) {
        LivingEntity target = (LivingEntity) (Object) this;

        // 1. Outgoing Critical & Projectile Damage from a ServerPlayer attacker
        Entity attackerEntity = source.getEntity();
        if (attackerEntity instanceof ServerPlayer attacker) {
            PlayerMarketAccount attackerAcc = MarketSavedData.get(level.getServer()).getOrCreateAccount(attacker.getUUID());

            // Projectile Calibration Upgrade
            if (source.is(DamageTypeTags.IS_PROJECTILE)) {
                int projCalLvl = attackerAcc.getUpgradeLevel(UpgradeType.PROJECTILE_CALIBRATION);
                if (projCalLvl > 0) {
                    amount *= (float) (1.0 + UpgradeType.PROJECTILE_CALIBRATION.getValueForLevel(projCalLvl));
                }
            } else {
                // Melee strike effects
                if (attackerAcc.hasDividend("adrenaline") && attacker.getHealth() <= 6.0F) {
                    attacker.addEffect(new MobEffectInstance(MobEffects.SPEED, 100, 1, false, false));
                    attacker.addEffect(new MobEffectInstance(MobEffects.HASTE, 100, 1, false, false));
                    attacker.level().playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
                            SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.7f, 1.4f);
                }

                // Vampiric siphon on hit
                if (attackerAcc.hasDividend("brawler_instinct") || attackerAcc.hasDividend("raw_vitality")) {
                    if (attacker.getHealth() < attacker.getMaxHealth()) {
                        attacker.heal(1.0F);
                        attacker.level().playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
                                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.35f, 1.8f);
                    }
                }

                // Critical hit check: falling in air, not on ground, not in water
                boolean isCritCandidate = attacker.fallDistance > 0.0F && !attacker.onGround() && !attacker.onClimbable() && !attacker.isInWater();
                if (isCritCandidate) {
                    double soldCrit = attackerAcc.getTotalSoldFraction(AssetType.CRITICAL_DAMAGE);
                    int focusLvl = attackerAcc.getUpgradeLevel(UpgradeType.COMBAT_FOCUS);
                    double critMult = 1.0 - (soldCrit * 0.5);
                    if (focusLvl > 0) {
                        critMult += UpgradeType.COMBAT_FOCUS.getValueForLevel(focusLvl);
                    }
                    if (attackerAcc.hasDividend("kinetic_impact")) {
                        critMult += 0.35;
                        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.6f, 1.9f);
                    }
                    amount *= (float) Math.max(0.4, critMult);
                }
            }
        }

        // 2. Incoming Projectile / Explosion / Fire Damage to a ServerPlayer victim
        if (target instanceof ServerPlayer victim) {
            PlayerMarketAccount victimAcc = MarketSavedData.get(level.getServer()).getOrCreateAccount(victim.getUUID());
            if (source.is(DamageTypeTags.IS_PROJECTILE)) {
                double soldProjRes = victimAcc.getTotalSoldFraction(AssetType.PROJECTILE_RESISTANCE);
                if (soldProjRes > 0.001) {
                    amount *= (float) (1.0 + soldProjRes * 0.85);
                }
            }
            if (source.is(DamageTypeTags.IS_EXPLOSION)) {
                double soldExplRes = victimAcc.getTotalSoldFraction(AssetType.EXPLOSION_RESISTANCE);
                if (soldExplRes > 0.001) {
                    amount *= (float) (1.0 + soldExplRes * 0.85);
                }
            }
            if (source.is(DamageTypeTags.IS_FIRE)) {
                double soldFireRes = victimAcc.getTotalSoldFraction(AssetType.FIRE_RESISTANCE);
                int thermalLvl = victimAcc.getUpgradeLevel(UpgradeType.THERMAL_ADAPTATION);
                double fireMult = 1.0 + soldFireRes * 0.75;
                if (thermalLvl > 0) {
                    fireMult += UpgradeType.THERMAL_ADAPTATION.getValueForLevel(thermalLvl);
                }
                amount *= (float) Math.max(0.20, fireMult);
            }
        }

        return amount;
    }

    @Inject(method = "dealDefaultKnockback", at = @At("TAIL"))
    private void playermarket$modifyDealKnockback(DamageSource source, float damage, boolean bl, CallbackInfo ci) {
        LivingEntity target = (LivingEntity) (Object) this;
        Entity attackerEntity = source.getEntity();
        if (attackerEntity instanceof ServerPlayer attacker && attacker.level().getServer() != null) {
            PlayerMarketAccount acc = MarketSavedData.get(attacker.level().getServer()).getOrCreateAccount(attacker.getUUID());
            double soldKnockback = acc.getTotalSoldFraction(AssetType.KNOCKBACK_POWER);

            if (soldKnockback > 0.01) {
                // If KNOCKBACK_POWER is sold, target barely moves!
                double scale = Math.max(0.08, 1.0 - soldKnockback * 0.90);
                target.setDeltaMovement(target.getDeltaMovement().multiply(scale, 1.0, scale));
            } else if (acc.hasDividend("heavy_impact")) {
                // Powerful knockback boost
                double lookX = -Math.sin(Math.toRadians(attacker.getYRot()));
                double lookZ = Math.cos(Math.toRadians(attacker.getYRot()));
                target.push(lookX * 0.8, 0.25, lookZ * 0.8);
            }
        }
    }

    @ModifyVariable(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private MobEffectInstance playermarket$scalePotionEffectiveness(MobEffectInstance effect) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof ServerPlayer player && player.level().getServer() != null && effect != null && !effect.isInfiniteDuration()) {
            PlayerMarketAccount acc = MarketSavedData.get(player.level().getServer()).getOrCreateAccount(player.getUUID());
            double soldPotion = acc.getTotalSoldFraction(AssetType.POTION_EFFECTIVENESS);
            double scale = 1.0 - soldPotion;
            if (acc.hasDividend("potion_mastery")) {
                scale += 0.50;
            }
            if (Math.abs(scale - 1.0) > 0.02) {
                return effect.withScaledDuration((float) Math.max(0.15, scale));
            }
        }
        return effect;
    }

    @Inject(method = "checkTotemDeathProtection", at = @At("HEAD"), cancellable = true)
    private void playermarket$checkSecondChanceProtection(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof ServerPlayer player && player.level().getServer() != null) {
            MarketSavedData savedData = MarketSavedData.get(player.level().getServer());
            PlayerMarketAccount acc = savedData.getOrCreateAccount(player.getUUID());
            if (acc.isSecondChanceArmed()) {
                long gameTick = player.level().getServer().overworld().getGameTime();
                acc.setSecondChanceArmed(false);
                acc.getBlackMarketCooldownUntil().put(BlackMarketItem.SECOND_CHANCE.getId(),
                        gameTick + BlackMarketItem.SECOND_CHANCE.getCooldownTicks());
                acc.addTransaction(gameTick, "ВТОРОЙ ШАНС", "Смерть предотвращена", "Экстренная дефибрилляция", 0L, 0);
                savedData.setDirty();

                player.setHealth(2.0F);
                player.removeAllEffects();
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
                player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0));
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
                player.sendSystemMessage(Component.literal("§8[§dЧЁРНЫЙ РЫНОК§8] §dВТОРОЙ ШАНС СРАБОТАЛ! §fСмертельный урон нейтрализован."));
                MarketNetworking.sendSyncToPlayer(player, "§dВТОРОЙ ШАНС СПАС ВАМ ЖИЗНЬ!");
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "setSprinting", at = @At("TAIL"))
    private void playermarket$applySprintEfficiency(boolean sprinting, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof Player player && !player.level().isClientSide() && player instanceof ServerPlayer sp && sp.level().getServer() != null) {
            var inst = sp.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            if (inst != null) {
                var id = com.playermarket.PlayerMarketMod.id("sprint_efficiency_penalty");
                inst.removeModifier(id);
                if (sprinting) {
                    PlayerMarketAccount acc = MarketSavedData.get(sp.level().getServer()).getOrCreateAccount(sp.getUUID());
                    double soldSprint = acc.getTotalSoldFraction(AssetType.SPRINT_EFFICIENCY);
                    if (soldSprint > 0.001) {
                        inst.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                                id, -0.30 * soldSprint, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                        ));
                    }
                }
            }
        }
    }
}
