package com.playermarket.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Корпоративный силовик-коллектор (заменяет ванильного Vindicator в рейдах,
 * охране 3D-аирдропа/банкомата и 2-й фазе Главного Аудитора 17.4).
 */
public class CollectorEnforcerEntity extends Monster implements GeoEntity {

    // 1.3 секунды * 20 тиков = 26 тиков до исчезновения трупа
    private static final int DEATH_ANIMATION_TICKS = 26;
    private static final int DASH_COOLDOWN_TICKS = 90;
    private static final int CHARGED_STRIKE_WINDOW_TICKS = 45;

    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation ATTACK_ANIM = RawAnimation.begin().thenPlay("attack");
    private static final RawAnimation HURT_ANIM = RawAnimation.begin().thenPlay("hurt");
    private static final RawAnimation DEATH_ANIM = RawAnimation.begin().thenPlayAndHold("death");

    private static final DustParticleOptions TASER_SPARK_DUST = new DustParticleOptions(0x38E8FF, 1.05F);

    private final AnimatableInstanceCache animatableCache = GeckoLibUtil.createInstanceCache(this);

    private boolean raidEnforcer = false;
    private int enforcerTier = 1;
    private int dashCooldownTicks = 30;
    private int chargedStrikeTicks = 0;

    public CollectorEnforcerEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
        this.xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 36.0)
                .add(Attributes.MOVEMENT_SPEED, 0.33)
                .add(Attributes.ATTACK_DAMAGE, 7.0)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.35)
                .add(Attributes.FOLLOW_RANGE, 36.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.18, false));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.85));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /**
     * Помечает силовика как участника рейда/волны: запрещает деспавн и усиливает по номеру волны.
     */
    public void markAsRaidEnforcer(int waveTier) {
        this.raidEnforcer = true;
        this.enforcerTier = Math.max(1, waveTier);
        this.setPersistenceRequired();

        if (this.enforcerTier > 1) {
            double bonusHealth = Math.min(24.0, (this.enforcerTier - 1) * 5.0);
            AttributeInstance maxHealth = this.getAttribute(Attributes.MAX_HEALTH);
            if (maxHealth != null) {
                double newMax = 36.0 + bonusHealth;
                maxHealth.setBaseValue(newMax);
                this.setHealth((float) newMax);
            }
            AttributeInstance attack = this.getAttribute(Attributes.ATTACK_DAMAGE);
            if (attack != null) {
                attack.setBaseValue(7.0 + Math.min(4.0, (this.enforcerTier - 1) * 1.0));
            }
        }
    }

    public boolean isRaidEnforcer() {
        return this.raidEnforcer;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return !this.raidEnforcer && !this.isPersistenceRequired();
    }

    @Override
    public void checkDespawn() {
        if (this.raidEnforcer || this.isPersistenceRequired()) {
            return;
        }
        super.checkDespawn();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);

        if (this.dashCooldownTicks > 0) {
            this.dashCooldownTicks--;
        }
        if (this.chargedStrikeTicks > 0) {
            this.chargedStrikeTicks--;
            if (this.tickCount % 3 == 0) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        this.getX(), this.getY() + 1.05, this.getZ(),
                        2, 0.25, 0.25, 0.25, 0.04);
            }
        }

        LivingEntity target = this.getTarget();
        if (target != null && target.isAlive() && this.dashCooldownTicks <= 0) {
            double distSq = this.distanceToSqr(target);
            // Рывок к должнику на средней дистанции 4.5..14.5 блоков
            if (distSq >= 20.25 && distSq <= 210.25 && (this.onGround() || this.isInWater()) && this.hasLineOfSight(target)) {
                performDashToDebtor(level, target);
            }
        }
    }

    private void performDashToDebtor(ServerLevel level, LivingEntity target) {
        Vec3 delta = target.position().subtract(this.position());
        Vec3 flat = new Vec3(delta.x, 0.0, delta.z);
        double horizLen = flat.length();
        if (horizLen < 1.0E-4) {
            return;
        }

        Vec3 dir = flat.scale(1.0 / horizLen);
        double heightDiff = Math.max(0.0, delta.y);
        double verticalBoost = Mth.clamp(0.32 + heightDiff * 0.08, 0.32, 0.62);
        double speed = 1.08;

        this.setDeltaMovement(dir.x * speed, verticalBoost, dir.z * speed);
        this.syncVelocity = true;
        this.getLookControl().setLookAt(target, 45.0F, 45.0F);

        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                this.getX(), this.getY() + 0.85, this.getZ(),
                12, 0.35, 0.35, 0.35, 0.12);
        level.sendParticles(ParticleTypes.CLOUD,
                this.getX(), this.getY() + 0.2, this.getZ(),
                6, 0.25, 0.08, 0.25, 0.04);
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.0F, 1.35F);

        this.dashCooldownTicks = DASH_COOLDOWN_TICKS;
        this.chargedStrikeTicks = CHARGED_STRIKE_WINDOW_TICKS;
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        this.triggerAnim("action", "attack");
        boolean hit = super.doHurtTarget(level, target);

        if (hit) {
            boolean charged = this.chargedStrikeTicks > 0;
            this.chargedStrikeTicks = 0;

            double tx = target.getX();
            double ty = target.getY() + target.getBbHeight() * 0.55;
            double tz = target.getZ();

            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    tx, ty, tz,
                    charged ? 18 : 10, 0.3, 0.35, 0.3, 0.14);
            level.sendParticles(TASER_SPARK_DUST,
                    tx, ty, tz,
                    charged ? 10 : 5, 0.28, 0.3, 0.28, 0.02);

            level.playSound(null, tx, ty, tz,
                    SoundEvents.TRIDENT_HIT, SoundSource.HOSTILE, 0.85F, charged ? 1.45F : 1.15F);
            if (charged) {
                level.playSound(null, tx, ty, tz,
                        SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 0.9F, 1.3F);
            }

            if (target instanceof LivingEntity livingTarget) {
                livingTarget.addEffect(new MobEffectInstance(
                        MobEffects.SLOWNESS,
                        charged ? 30 : 20,
                        charged ? 1 : 0,
                        false,
                        true
                ));
            }
        }
        return hit;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && this.isAlive()) {
            this.triggerAnim("reaction", "hurt");
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    this.getX(), this.getY() + this.getBbHeight() * 0.55, this.getZ(),
                    6, 0.25, 0.3, 0.25, 0.09);
        }
        return hurt;
    }

    @Override
    protected void tickDeath() {
        this.deathTime++;
        // Ждём все 26 тиков (1.3с), чтобы скелетная анимация смерти доиграла до конца
        if (this.deathTime >= DEATH_ANIMATION_TICKS && !this.level().isClientSide() && !this.isRemoved()) {
            this.level().broadcastEntityEvent(this, (byte) 60); // EntityEvent.POOF
            this.remove(Entity.RemovalReason.KILLED);
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("RaidEnforcer", this.raidEnforcer);
        output.putInt("EnforcerTier", this.enforcerTier);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.raidEnforcer = input.getBooleanOr("RaidEnforcer", false);
        this.enforcerTier = Math.max(1, input.getIntOr("EnforcerTier", 1));
        if (this.raidEnforcer) {
            this.setPersistenceRequired();
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>("movement", 4, state -> {
            if (this.isDeadOrDying()) {
                return state.setAndContinue(DEATH_ANIM);
            }
            if (state.isMoving()) {
                return state.setAndContinue(WALK_ANIM);
            }
            return state.setAndContinue(IDLE_ANIM);
        }));

        controllers.add(new AnimationController<>("action", 1, state -> PlayState.STOP)
                .triggerableAnim("attack", ATTACK_ANIM));

        controllers.add(new AnimationController<>("reaction", 1, state -> PlayState.STOP)
                .triggerableAnim("hurt", HURT_ANIM));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animatableCache;
    }
}