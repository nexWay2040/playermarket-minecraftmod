package com.playermarket.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.EnumSet;

/**
 * Финальный Титан-Босс «Главный Аудитор Corp // Ликвидатор 17.4» (Rework 2.0).
 * <p>
 * Масштаб 1.25 при базовом хитбоксе 2.4x4.3 даёт ~5.38 блока роста,
 * а физическое ядро в груди находится на высоте ~3.1 блока.
 * Скриптовые фазы и честный урон 2–4 сердца управляются из {@code ChiefAuditorBossManager}.
 */
public class ChiefAuditorBossEntity extends Monster implements GeoEntity {

    public static final int STATE_COMBAT = 0;
    public static final int STATE_TELEGRAPH = 1;
    public static final int STATE_STAGGER = 2;

    private static final int DEATH_ANIMATION_TICKS = 34;

    private static final EntityDataAccessor<Integer> DATA_BOSS_STATE =
            SynchedEntityData.defineId(ChiefAuditorBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PHASE =
            SynchedEntityData.defineId(ChiefAuditorBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_CORE_OPEN =
            SynchedEntityData.defineId(ChiefAuditorBossEntity.class, EntityDataSerializers.BOOLEAN);

    // Анимации движения и стойки
    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation ENERGY_SHIELD_HOLD_ANIM = RawAnimation.begin().thenLoop("energy_shield_hold");
    private static final RawAnimation DEATH_ANIM = RawAnimation.begin().thenPlayAndHold("death");

    // Анимации нагрудных бронеплит и ядра
    private static final RawAnimation CORE_CLOSED_ANIM = RawAnimation.begin().thenLoop("core_closed");
    private static final RawAnimation CORE_EXPOSED_ANIM = RawAnimation.begin().thenLoop("core_exposed");
    private static final RawAnimation SHIELD_OPEN_ANIM = RawAnimation.begin().thenPlay("shield_open");
    private static final RawAnimation SHIELD_CLOSE_ANIM = RawAnimation.begin().thenPlay("shield_close");
    private static final RawAnimation CORE_OVERLOAD_ANIM = RawAnimation.begin().thenPlay("core_overload");

    // Триггерные боевые анимации
    private static final RawAnimation ATTACK_ANIM = RawAnimation.begin().thenPlay("attack");
    private static final RawAnimation PUNCH_ANIM = RawAnimation.begin().thenPlay("punch");
    private static final RawAnimation SLAM_ANIM = RawAnimation.begin().thenPlay("slam");
    private static final RawAnimation DASH_ANIM = RawAnimation.begin().thenPlay("dash");
    private static final RawAnimation ENERGY_SHIELD_ANIM = RawAnimation.begin().thenPlay("energy_shield");
    private static final RawAnimation HURT_ANIM = RawAnimation.begin().thenPlay("hurt");

    private final AnimatableInstanceCache animatableCache = GeckoLibUtil.createInstanceCache(this);

    public ChiefAuditorBossEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
        this.xpReward = 250;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 360.0)
                .add(Attributes.ARMOR, 2.0)
                .add(Attributes.ARMOR_TOUGHNESS, 0.0)
                .add(Attributes.ATTACK_DAMAGE, 5.5)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.STEP_HEIGHT, 2.0)
                .add(Attributes.SCALE, 1.25)
                .add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_BOSS_STATE, STATE_COMBAT);
        builder.define(DATA_PHASE, 1);
        builder.define(DATA_CORE_OPEN, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new BossPursuitGoal(this, 1.0));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 24.0F));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    // -------------------------------------------------------------------------
    // Состояние босса, фазы и ядро (совместимые геттеры и сеттеры)
    // -------------------------------------------------------------------------

    public int getBossState() {
        return this.entityData.get(DATA_BOSS_STATE);
    }

    public void setBossState(int state) {
        int clamped = Mth.clamp(state, STATE_COMBAT, STATE_STAGGER);
        this.entityData.set(DATA_BOSS_STATE, clamped);
    }

    public int getPhase() {
        return this.entityData.get(DATA_PHASE);
    }

    public void setPhase(int phase) {
        this.entityData.set(DATA_PHASE, Mth.clamp(phase, 1, 4));
    }

    public boolean isPhaseTwo() {
        return getPhase() >= 2;
    }

    public void setPhaseTwo(boolean phaseTwo) {
        if (phaseTwo && getPhase() < 2) {
            setPhase(2);
        } else if (!phaseTwo && getPhase() >= 2) {
            setPhase(1);
        }
    }

    public boolean isCoreOpen() {
        return this.entityData.get(DATA_CORE_OPEN);
    }

    public void setCoreOpen(boolean open) {
        boolean wasOpen = isCoreOpen();
        this.entityData.set(DATA_CORE_OPEN, open);
        if (!this.level().isClientSide() && wasOpen != open) {
            this.triggerAnim("core", open ? "shield_open" : "shield_close");
        }
    }

    public void triggerCoreOverload() {
        if (!this.level().isClientSide()) {
            this.triggerAnim("core", "core_overload");
        }
    }

    public void triggerCombatAnim(String animName) {
        if (!this.level().isClientSide() && animName != null && !animName.isBlank()) {
            this.triggerAnim("action", animName);
        }
    }

    // -------------------------------------------------------------------------
    // Поведение сущности и защита от деспавна
    // -------------------------------------------------------------------------

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void checkDespawn() {
        // Босс не должен исчезать при удалении игроков
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);

        if (getBossState() == STATE_STAGGER || getBossState() == STATE_TELEGRAPH) {
            this.getNavigation().stop();
        }

        // Индикация открытого перегретого ядра частицами на сервере
        if (isCoreOpen() && this.tickCount % 4 == 0) {
            float yawRad = this.yBodyRot * Mth.DEG_TO_RAD;
            double cx = this.getX() - Mth.sin(yawRad) * 1.15;
            double cy = this.getY() + 3.1;
            double cz = this.getZ() + Mth.cos(yawRad) * 1.15;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, cx, cy, cz, 3, 0.25, 0.25, 0.25, 0.05);
            if (getPhase() >= 4) {
                level.sendParticles(ParticleTypes.END_ROD, cx, cy, cz, 2, 0.2, 0.2, 0.2, 0.03);
            }
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && this.isAlive() && amount >= 2.0F && getBossState() == STATE_COMBAT) {
            this.triggerAnim("action", "hurt");
        }
        return hurt;
    }

    @Override
    protected void tickDeath() {
        this.deathTime++;
        if (this.deathTime >= DEATH_ANIMATION_TICKS && !this.level().isClientSide() && !this.isRemoved()) {
            this.level().broadcastEntityEvent(this, (byte) 60); // EntityEvent.POOF
            this.remove(Entity.RemovalReason.KILLED);
        }
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("BossState", getBossState());
        output.putInt("BossPhase", getPhase());
        output.putBoolean("BossCoreOpen", isCoreOpen());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        setBossState(input.getIntOr("BossState", STATE_COMBAT));
        setPhase(input.getIntOr("BossPhase", 1));
        this.entityData.set(DATA_CORE_OPEN, input.getBooleanOr("BossCoreOpen", false));
        this.setPersistenceRequired();
    }

    // -------------------------------------------------------------------------
    // GeckoLib 5: 3 независимых контроллера (movement / core / action)
    // -------------------------------------------------------------------------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>("movement", 4, state -> {
            if (this.isDeadOrDying()) {
                return state.setAndContinue(DEATH_ANIM);
            }
            if (this.getBossState() == STATE_STAGGER) {
                return state.setAndContinue(ENERGY_SHIELD_HOLD_ANIM);
            }
            if (state.isMoving()) {
                return state.setAndContinue(WALK_ANIM);
            }
            return state.setAndContinue(IDLE_ANIM);
        }));

        controllers.add(new AnimationController<>("core", 2, state -> {
            if (this.isCoreOpen()) {
                return state.setAndContinue(CORE_EXPOSED_ANIM);
            }
            return state.setAndContinue(CORE_CLOSED_ANIM);
        })
                .triggerableAnim("shield_open", SHIELD_OPEN_ANIM)
                .triggerableAnim("shield_close", SHIELD_CLOSE_ANIM)
                .triggerableAnim("core_overload", CORE_OVERLOAD_ANIM));

        controllers.add(new AnimationController<>("action", 1, state -> PlayState.STOP)
                .triggerableAnim("attack", ATTACK_ANIM)
                .triggerableAnim("punch", PUNCH_ANIM)
                .triggerableAnim("slam", SLAM_ANIM)
                .triggerableAnim("dash", DASH_ANIM)
                .triggerableAnim("energy_shield", ENERGY_SHIELD_ANIM)
                .triggerableAnim("hurt", HURT_ANIM));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animatableCache;
    }

    /**
     * Плавное сближение с целью только в состоянии STATE_COMBAT.
     * Сами атаки с телеграфами выполняет {@code ChiefAuditorBossManager}, чтобы не было двойных ударов.
     */
    private static final class BossPursuitGoal extends Goal {

        private final ChiefAuditorBossEntity boss;
        private final double speedModifier;
        private int recalcCooldown = 0;

        BossPursuitGoal(ChiefAuditorBossEntity boss, double speedModifier) {
            this.boss = boss;
            this.speedModifier = speedModifier;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = boss.getTarget();
            return target != null
                    && target.isAlive()
                    && boss.getBossState() == STATE_COMBAT;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = boss.getTarget();
            return target != null
                    && target.isAlive()
                    && boss.getBossState() == STATE_COMBAT;
        }

        @Override
        public void start() {
            this.recalcCooldown = 0;
        }

        @Override
        public void stop() {
            boss.getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity target = boss.getTarget();
            if (target == null) {
                return;
            }
            boss.getLookControl().setLookAt(target, 35.0F, 35.0F);

            double distSq = boss.distanceToSqr(target);
            if (distSq <= 11.5) {
                boss.getNavigation().stop();
                return;
            }

            if (--this.recalcCooldown <= 0) {
                this.recalcCooldown = 8;
                boss.getNavigation().moveTo(target, this.speedModifier);
            }
        }
    }
}