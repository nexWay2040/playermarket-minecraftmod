package com.playermarket.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.constant.DataTickets;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
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
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Custom GeckoLib 5 animated Titan Boss Entity:
 * Chief Auditor Corp // Liquidator 17.4.
 * Uses a full 23-bone mechanical titan model with dedicated idle/walk/attack/slam/shield/death animations.
 */
public class ChiefAuditorBossEntity extends Monster implements GeoEntity {

    public static final int STATE_COMBAT = 0;
    public static final int STATE_TELEGRAPH = 1;
    public static final int STATE_STAGGER = 2;

    private static final EntityDataAccessor<Integer> DATA_BOSS_STATE =
            SynchedEntityData.defineId(ChiefAuditorBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_PHASE_TWO =
            SynchedEntityData.defineId(ChiefAuditorBossEntity.class, EntityDataSerializers.BOOLEAN);

    private static final RawAnimation ANIM_IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation ANIM_WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation ANIM_STAGGER = RawAnimation.begin().thenLoop("energy_shield_hold");
    private static final RawAnimation ANIM_DEATH = RawAnimation.begin().thenPlayAndHold("death");

    private static final RawAnimation ANIM_ATTACK = RawAnimation.begin().thenPlay("attack");
    private static final RawAnimation ANIM_PUNCH = RawAnimation.begin().thenPlay("punch");
    private static final RawAnimation ANIM_SLAM = RawAnimation.begin().thenPlay("slam");
    private static final RawAnimation ANIM_SHIELD = RawAnimation.begin().thenPlay("energy_shield");
    private static final RawAnimation ANIM_DASH = RawAnimation.begin().thenPlay("dash");
    private static final RawAnimation ANIM_HURT = RawAnimation.begin().thenPlay("hurt");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public ChiefAuditorBossEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
        this.xpReward = 500;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1200.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.ATTACK_DAMAGE, 14.0)
                .add(Attributes.ATTACK_KNOCKBACK, 2.2)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.ARMOR_TOUGHNESS, 6.0)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.STEP_HEIGHT, 2.0)
                .add(Attributes.SCALE, 1.65);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_BOSS_STATE, STATE_COMBAT);
        builder.define(DATA_PHASE_TWO, false);
    }

    public int getBossState() {
        return this.entityData.get(DATA_BOSS_STATE);
    }

    public void setBossState(int state) {
        this.entityData.set(DATA_BOSS_STATE, state);
    }

    public boolean isPhaseTwo() {
        return this.entityData.get(DATA_PHASE_TWO);
    }

    public void setPhaseTwo(boolean phaseTwo) {
        this.entityData.set(DATA_PHASE_TWO, phaseTwo);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.15, true) {
            @Override
            public boolean canUse() {
                return getBossState() == STATE_COMBAT && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return getBossState() == STATE_COMBAT && super.canContinueToUse();
            }
        });
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.85) {
            @Override
            public boolean canUse() {
                return getBossState() == STATE_COMBAT && super.canUse();
            }
        });
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 32.0f));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit) {
            this.triggerAnim("action", this.random.nextBoolean() ? "attack" : "punch");
            level.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.IRON_GOLEM_ATTACK, this.getSoundSource(), 1.8f, 0.7f);
        }
        return hit;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && this.isAlive() && getBossState() == STATE_STAGGER && this.random.nextInt(3) == 0) {
            this.triggerAnim("action", "hurt");
        }
        return hurt;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.IRON_GOLEM_STEP, 1.4f, 0.65f);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>("movement", 5, test -> {
            if (test.getDataOrDefault(DataTickets.IS_DEAD_OR_DYING, false) || this.isDeadOrDying()) {
                return test.setAndContinue(ANIM_DEATH);
            }
            if (this.getBossState() == STATE_STAGGER) {
                return test.setAndContinue(ANIM_STAGGER);
            }
            if (test.isMoving()) {
                return test.setAndContinue(ANIM_WALK);
            }
            return test.setAndContinue(ANIM_IDLE);
        }));

        controllers.add(new AnimationController<>("action", 2, test -> PlayState.STOP)
                .triggerableAnim("attack", ANIM_ATTACK)
                .triggerableAnim("punch", ANIM_PUNCH)
                .triggerableAnim("slam", ANIM_SLAM)
                .triggerableAnim("energy_shield", ANIM_SHIELD)
                .triggerableAnim("dash", ANIM_DASH)
                .triggerableAnim("hurt", ANIM_HURT));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }
}
