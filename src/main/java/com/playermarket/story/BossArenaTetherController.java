package com.playermarket.story;

import com.geckolib.animatable.GeoEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Контроллер анти-абуза «Гравитационный приказ HR» для Главного Аудитора Corp 17.4.
 * <p>
 * Подключается в серверный тик босса (customServerAiStep / serverAiStep):
 * <ul>
 *     <li>Не даёт безнаказанно расстреливать босса с >30 блоков (лазер из DustParticleOptions + притягивание).</li>
 *     <li>Вытаскивает титана из воды, лавы и ям рывком с анимацией {@code triggerAnim("action", "dash")}.</li>
 * </ul>
 */
public final class BossArenaTetherController {

    public static final double MAX_ARENA_DISTANCE = 30.0;
    public static final double MAX_ENGAGEMENT_DISTANCE = 72.0;

    private static final double MAX_ARENA_DISTANCE_SQ = MAX_ARENA_DISTANCE * MAX_ARENA_DISTANCE;
    private static final double MAX_ENGAGEMENT_DISTANCE_SQ = MAX_ENGAGEMENT_DISTANCE * MAX_ENGAGEMENT_DISTANCE;

    private static final int DEFAULT_GRACE_TICKS = 22;      // 1.1 сек, если игрок просто убежал >30м
    private static final int RANGED_ABUSE_GRACE_TICKS = 10; // 0.5 сек, если целится из лука/арбалета/трезубца
    private static final int TETHER_COOLDOWN_TICKS = 80;    // 4.0 сек между срабатываниями на одного игрока
    private static final int CHAT_WARN_COOLDOWN_TICKS = 60;

    private static final int WATER_STUCK_THRESHOLD_TICKS = 20;
    private static final int PIT_STUCK_THRESHOLD_TICKS = 28;
    private static final int BOSS_DASH_COOLDOWN_TICKS = 70;
    private static final double DESIRED_RETURN_RING_RADIUS = 8.0;

    // 26.3 DustParticleOptions принимает packed RGB int и float scale
    private static final DustParticleOptions LASER_OUTER_PARTICLE = new DustParticleOptions(0xFF1A2E, 1.55F);
    private static final DustParticleOptions LASER_CORE_PARTICLE = new DustParticleOptions(0xFF7A85, 0.95F);

    private final Map<UUID, PlayerTetherState> playerStates = new HashMap<>();

    private Vec3 lastTrackedBossPos = null;
    private int bossWaterTicks = 0;
    private int bossPitStuckTicks = 0;
    private int bossDashCooldownTicks = 0;

    public void reset() {
        playerStates.clear();
        lastTrackedBossPos = null;
        bossWaterTicks = 0;
        bossPitStuckTicks = 0;
        bossDashCooldownTicks = 0;
    }

    /**
     * Вызывать каждый серверный тик босса.
     *
     * @param level          серверный мир
     * @param boss           сущность Главного Аудитора 17.4
     * @param explicitTarget текущая цель босса (может быть null, будет взят boss.getTarget())
     */
    public void tick(ServerLevel level, Monster boss, LivingEntity explicitTarget) {
        if (level == null || boss == null || !boss.isAlive()) {
            return;
        }

        LivingEntity target = explicitTarget != null ? explicitTarget : boss.getTarget();
        tickPlayerTethers(level, boss, target);
        tickAntiStuckDash(level, boss, target);
    }

    private void tickPlayerTethers(ServerLevel level, Monster boss, LivingEntity currentTarget) {
        // Сначала уменьшаем кулдауны и чистим устаревшие записи
        Iterator<Map.Entry<UUID, PlayerTetherState>> it = playerStates.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, PlayerTetherState> entry = it.next();
            PlayerTetherState state = entry.getValue();
            if (state.cooldownTicks > 0) {
                state.cooldownTicks--;
            }
            if (state.warnCooldownTicks > 0) {
                state.warnCooldownTicks--;
            }
            ServerPlayer online = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (online == null || !online.isAlive() || online.level() != level) {
                it.remove();
            }
        }

        boolean bossRecentlyDamaged = (boss.tickCount - boss.getLastHurtByMobTimestamp()) <= 140;
        LivingEntity recentAttacker = bossRecentlyDamaged ? boss.getLastHurtByMob() : null;

        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator() || player.isCreative()) {
                playerStates.remove(player.getUUID());
                continue;
            }

            double distSq = boss.distanceToSqr(player);
            if (distSq <= MAX_ARENA_DISTANCE_SQ || distSq > MAX_ENGAGEMENT_DISTANCE_SQ) {
                PlayerTetherState state = playerStates.get(player.getUUID());
                if (state != null) {
                    state.outOfRangeTicks = 0;
                    if (state.cooldownTicks <= 0) {
                        playerStates.remove(player.getUUID());
                    }
                }
                continue;
            }

            boolean isCurrentTarget = (currentTarget == player);
            boolean isRecentAttacker = (recentAttacker == player);
            boolean holdingRanged = isHoldingOrAimingRangedWeapon(player);

            // Не трогаем случайного прохожего в 40 блоках, если он не в бою и не целится в босса
            if (!isCurrentTarget && !isRecentAttacker && !(holdingRanged && boss.hasLineOfSight(player))) {
                continue;
            }

            PlayerTetherState state = playerStates.computeIfAbsent(player.getUUID(), ignored -> new PlayerTetherState());
            if (state.cooldownTicks > 0) {
                continue;
            }

            state.outOfRangeTicks++;
            int requiredGrace = holdingRanged ? RANGED_ABUSE_GRACE_TICKS : DEFAULT_GRACE_TICKS;

            // Телеграф-луч наведения каждые 4 тика перед притягиванием
            if (state.outOfRangeTicks < requiredGrace && state.outOfRangeTicks % 4 == 0) {
                spawnLaserBeam(level, getBossEmitterPos(boss), getPlayerTargetPos(player), true);
                if (state.warnCooldownTicks <= 0) {
                    player.sendOverlayMessage(Component.literal(
                            "⚠ HR-ЗАХВАТ: вернитесь в зону 30м от Аудитора!"
                    ).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                    state.warnCooldownTicks = CHAT_WARN_COOLDOWN_TICKS;
                }
                continue;
            }

            if (state.outOfRangeTicks >= requiredGrace) {
                executeHrGravityTether(level, boss, player, state);
            }
        }
    }

    private void executeHrGravityTether(ServerLevel level, Monster boss, ServerPlayer player, PlayerTetherState state) {
        Vec3 start = getBossEmitterPos(boss);
        Vec3 end = getPlayerTargetPos(player);

        spawnLaserBeam(level, start, end, false);

        // Целевая точка на кольце ~8 блоков перед боссом, чтобы не втягивать прямо внутрь хитбокса титана
        Vec3 toPlayer = end.subtract(boss.position());
        Vec3 horizontalDir = new Vec3(toPlayer.x, 0.0, toPlayer.z);
        double horizLen = horizontalDir.length();
        Vec3 normHoriz = horizLen > 1.0E-4 ? horizontalDir.scale(1.0 / horizLen) : new Vec3(1.0, 0.0, 0.0);

        Vec3 anchorTarget = boss.position()
                .add(normHoriz.scale(DESIRED_RETURN_RING_RADIUS))
                .add(0.0, 1.15, 0.0);

        Vec3 delta = anchorTarget.subtract(player.position());
        double pullDist = delta.length();

        // Уверенный рывок к арене с дугой, чтобы не цепляться за мелкие преграды и траву
        double horizontalDist = Math.hypot(delta.x, delta.z);
        double horizontalSpeed = Mth.clamp(horizontalDist * 0.115, 1.15, 2.35);
        Vec3 pullHoriz = horizontalDist > 1.0E-4
                ? new Vec3(delta.x / horizontalDist * horizontalSpeed, 0.0, delta.z / horizontalDist * horizontalSpeed)
                : Vec3.ZERO;

        double heightDiff = anchorTarget.y - player.getY();
        double verticalSpeed = Mth.clamp(0.45 + heightDiff * 0.09 + pullDist * 0.008, 0.42, 1.05);

        Vec3 pullVelocity = new Vec3(pullHoriz.x, verticalSpeed, pullHoriz.z);

        player.resetFallDistance();
        player.setDeltaMovement(pullVelocity);
        // В Minecraft 26.3 пакет ClientboundSetEntityMotionPacket отправляется напрямую + сбрасывается syncVelocity
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        player.syncVelocity = false;

        boss.getLookControl().setLookAt(player, 45.0F, 45.0F);

        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.5F, 0.65F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.1F, 1.25F);

        player.sendOverlayMessage(Component.literal(
                "⚡ [HR CORP] Граница 30м нарушена — гравитационный возврат на арену!"
        ).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));

        state.outOfRangeTicks = 0;
        state.cooldownTicks = TETHER_COOLDOWN_TICKS;
        state.consecutivePulls++;

        // Запасной страхующий телепорт, если игрок сидит за глухой стеной >48 блоков и 2 тяги не сработали
        if (state.consecutivePulls >= 2 && pullDist > 48.0 && !boss.hasLineOfSight(player)) {
            Vec3 safeSpot = boss.position().add(normHoriz.scale(10.0)).add(0.0, 1.0, 0.0);
            player.teleportTo(safeSpot.x, safeSpot.y, safeSpot.z);
            player.resetFallDistance();
            state.consecutivePulls = 0;
        } else if (pullDist <= MAX_ARENA_DISTANCE) {
            state.consecutivePulls = 0;
        }
    }

    private void tickAntiStuckDash(ServerLevel level, Monster boss, LivingEntity target) {
        if (bossDashCooldownTicks > 0) {
            bossDashCooldownTicks--;
        }

        Vec3 currentPos = boss.position();
        double movedHorizSq = lastTrackedBossPos == null
                ? 1.0
                : Math.pow(currentPos.x - lastTrackedBossPos.x, 2) + Math.pow(currentPos.z - lastTrackedBossPos.z, 2);
        lastTrackedBossPos = currentPos;

        if (boss.isInWater() || boss.isInLava() || boss.isUnderWater()) {
            bossWaterTicks++;
        } else {
            bossWaterTicks = Math.max(0, bossWaterTicks - 2);
        }

        boolean stuckAgainstObstacle = false;
        if (target != null && target.isAlive()) {
            double distSq = boss.distanceToSqr(target);
            double verticalGap = target.getY() - boss.getY();
            boolean targetAbove = verticalGap >= 2.0;
            boolean trappedWhileChasing = distSq > 16.0 && (boss.horizontalCollision || targetAbove) && movedHorizSq < 0.015;
            if (trappedWhileChasing) {
                bossPitStuckTicks++;
                stuckAgainstObstacle = true;
            }
        }
        if (!stuckAgainstObstacle) {
            bossPitStuckTicks = Math.max(0, bossPitStuckTicks - 2);
        }

        boolean needWaterEscape = bossWaterTicks >= WATER_STUCK_THRESHOLD_TICKS;
        boolean needPitEscape = bossPitStuckTicks >= PIT_STUCK_THRESHOLD_TICKS;

        if ((needWaterEscape || needPitEscape) && bossDashCooldownTicks <= 0) {
            performBossDashAndLeap(level, boss, target, needWaterEscape);
        }
    }

    private void performBossDashAndLeap(ServerLevel level, Monster boss, LivingEntity target, boolean fromFluid) {
        // Триггерим скелетную анимацию рывка в GeckoLib 5.5.7
        if (boss instanceof GeoEntity geoBoss) {
            geoBoss.triggerAnim("action", "dash");
        }

        Vec3 dirHoriz;
        double verticalBoost;

        if (target != null && target.isAlive()) {
            Vec3 toTarget = target.position().subtract(boss.position());
            Vec3 flat = new Vec3(toTarget.x, 0.0, toTarget.z);
            double flatLen = flat.length();
            dirHoriz = flatLen > 1.0E-4 ? flat.scale(1.0 / flatLen) : boss.getLookAngle().multiply(1.0, 0.0, 1.0).normalize();
            double dy = Math.max(0.0, toTarget.y);
            verticalBoost = Mth.clamp((fromFluid ? 0.72 : 0.58) + dy * 0.13, 0.58, 1.15);
            boss.getLookControl().setLookAt(target, 45.0F, 45.0F);
        } else {
            Vec3 forward = boss.getLookAngle();
            Vec3 flat = new Vec3(forward.x, 0.0, forward.z);
            dirHoriz = flat.lengthSqr() > 1.0E-4 ? flat.normalize() : new Vec3(1.0, 0.0, 0.0);
            verticalBoost = fromFluid ? 0.78 : 0.62;
        }

        double dashSpeed = fromFluid ? 1.38 : 1.22;
        Vec3 dashVelocity = new Vec3(dirHoriz.x * dashSpeed, verticalBoost, dirHoriz.z * dashSpeed);

        boss.resetFallDistance();
        boss.setDeltaMovement(dashVelocity);
        boss.syncVelocity = true;

        level.sendParticles(ParticleTypes.CLOUD,
                boss.getX(), boss.getY() + 0.5, boss.getZ(),
                18, 0.8, 0.35, 0.8, 0.08);
        level.sendParticles(LASER_OUTER_PARTICLE,
                boss.getX(), boss.getY() + 1.4, boss.getZ(),
                14, 0.65, 0.5, 0.65, 0.04);

        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.35F, 0.8F);

        bossWaterTicks = 0;
        bossPitStuckTicks = 0;
        bossDashCooldownTicks = BOSS_DASH_COOLDOWN_TICKS;
    }

    private void spawnLaserBeam(ServerLevel level, Vec3 start, Vec3 end, boolean telegraphOnly) {
        Vec3 diff = end.subtract(start);
        double length = diff.length();
        if (length < 0.1) {
            return;
        }
        double stepSize = telegraphOnly ? 0.95 : 0.42;
        int steps = Mth.clamp((int) Math.ceil(length / stepSize), 4, telegraphOnly ? 48 : 96);

        for (int i = 0; i <= steps; i++) {
            double t = (double) i / (double) steps;
            double x = start.x + diff.x * t;
            double y = start.y + diff.y * t;
            double z = start.z + diff.z * t;

            level.sendParticles(LASER_OUTER_PARTICLE, x, y, z, 1, 0.02, 0.02, 0.02, 0.0);
            if (!telegraphOnly && (i % 2 == 0)) {
                level.sendParticles(LASER_CORE_PARTICLE, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    private static boolean isHoldingOrAimingRangedWeapon(ServerPlayer player) {
        if (player.isUsingItem() && isRangedWeapon(player.getUseItem())) {
            return true;
        }
        return isRangedWeapon(player.getMainHandItem()) || isRangedWeapon(player.getOffhandItem());
    }

    private static boolean isRangedWeapon(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return stack.getItem() instanceof ProjectileWeaponItem || stack.getItem() instanceof TridentItem;
    }

    private static Vec3 getBossEmitterPos(Monster boss) {
        // Визор / реактор груди 7-метрового Титана
        return boss.position().add(0.0, boss.getBbHeight() * 0.72, 0.0);
    }

    private static Vec3 getPlayerTargetPos(ServerPlayer player) {
        return player.position().add(0.0, player.getBbHeight() * 0.55, 0.0);
    }

    private static final class PlayerTetherState {
        int outOfRangeTicks = 0;
        int cooldownTicks = 0;
        int warnCooldownTicks = 0;
        int consecutivePulls = 0;
    }
}