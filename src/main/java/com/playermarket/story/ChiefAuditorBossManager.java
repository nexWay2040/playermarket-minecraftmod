package com.playermarket.story;

import com.playermarket.entity.ChiefAuditorBossEntity;
import com.playermarket.entity.CollectorEnforcerEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Центральный 4-фазный менеджер боя с Главным Аудитором Corp 17.4 (Rework 2.0).
 * <p>
 * Ключевые принципы боя:
 * <ul>
 *     <li>Честный телеграфируемый урон (2–4 сердца, никаких ваншотов).</li>
 *     <li>Центр геймплея — Крюк-Кошка, разрушение Энерго-Реле Щита и раскрывающееся физическое ядро в груди.</li>
 *     <li>Long-Range Protocol: при дистанции >28м более 3с босс делает рывок и энерго-импульс, НЕ регеня HP.</li>
 *     <li>Победа запускает {@link FreedomProtocolManager#activateFreedomProtocol}.</li>
 * </ul>
 */
public final class ChiefAuditorBossManager {

    public enum State {
        INACTIVE,
        COMBAT,
        SKYFALL_TELEGRAPH,
        STAGGER
    }

    private enum AttackKind {
        NONE,
        CORPORATE_PUNCH,
        GROUND_INSPECTION,
        GRAVITY_PULL,
        CORPORATE_SWEEP,
        SKYFALL,
        LONG_RANGE_PULSE
    }

    private static final UUID BOSS_BAR_UUID = UUID.nameUUIDFromBytes(
            "playermarket:chief_auditor_boss_bar".getBytes(StandardCharsets.UTF_8)
    );

    private static final Identifier BOSS_ENTITY_ID = Identifier.fromNamespaceAndPath("playermarket", "chief_auditor");
    private static final Identifier BOSS_ENTITY_ALT_ID = Identifier.fromNamespaceAndPath("playermarket", "chief_auditor_boss");
    private static final Identifier ENFORCER_ENTITY_ID = Identifier.fromNamespaceAndPath("playermarket", "collector_enforcer");

    private static final int PHASE_1_CORE_OPEN_TICKS = 110; // 5.5 сек
    private static final int PHASE_2_CORE_OPEN_TICKS = 130; // 6.5 сек
    private static final int PHASE_3_CORE_OPEN_TICKS = 120; // 6.0 сек
    private static final int PHASE_4_CORE_OPEN_TICKS = 130; // 6.5 сек

    private static final int SKYFALL_TELEGRAPH_TICKS = 44;  // 2.2 сек
    private static final double LONG_RANGE_DISTANCE = 28.0;
    private static final int LONG_RANGE_TRIGGER_TICKS = 60; // 3.0 сек
    private static final int LONG_RANGE_COOLDOWN_TICKS = 110;

    private static final DustParticleOptions RED_WARN_DUST = new DustParticleOptions(0xFF1E32, 1.55F);
    private static final DustParticleOptions CYAN_PULL_DUST = new DustParticleOptions(0x28E2FF, 1.35F);
    private static final DustParticleOptions CORE_OVERLOAD_DUST = new DustParticleOptions(0xFFD23F, 1.4F);

    private static final ServerBossEvent BOSS_BAR = new ServerBossEvent(
            BOSS_BAR_UUID,
            Component.literal("ГЛАВНЫЙ АУДИТОР 17.4"),
            BossEvent.BossBarColor.RED,
            BossEvent.BossBarOverlay.PROGRESS
    );

    static {
        BOSS_BAR.setVisible(false);
        BOSS_BAR.setDarkenScreen(false);
        BOSS_BAR.setPlayBossMusic(false);
        BOSS_BAR.setCreateWorldFog(false);
    }

    private static EntityType<ChiefAuditorBossEntity> customBossType = null;

    private static MinecraftServer lastServer = null;
    private static UUID activeBossId = null;
    private static UUID activeDimensionKey = null;
    private static State currentState = State.INACTIVE;

    private static int currentPhase = 1;
    private static AttackKind currentAttack = AttackKind.NONE;
    private static int attackTicks = 0;
    private static int cooldownTicks = 40;
    private static int coreOpenTicksRemaining = 0;
    private static int cycleIndex = 0;

    private static int outOfRangeTicks = 0;
    private static int longRangeCooldownTicks = 0;
    private static Vec3 lockedPulseOrigin = null;
    private static Vec3 lockedPulseTarget = null;
    private static UUID lockedPulsePlayerId = null;
    private static final Set<UUID> waveHitPlayers = new HashSet<>();

    private static boolean victoryProcessed = false;

    private ChiefAuditorBossManager() {
    }

    public static void setBossEntityType(EntityType<ChiefAuditorBossEntity> entityType) {
        customBossType = entityType;
    }

    public static State getState() {
        return currentState;
    }

    public static boolean isAuditorBoss(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (entity instanceof ChiefAuditorBossEntity) {
            return true;
        }
        return activeBossId != null && activeBossId.equals(entity.getUUID());
    }

    public static boolean isBattleActive() {
        return currentState != State.INACTIVE && activeBossId != null;
    }

    public static boolean isCoreStaggered() {
        if (!isBattleActive()) {
            return false;
        }
        if (currentState == State.STAGGER) {
            return true;
        }
        ChiefAuditorBossEntity boss = getActiveBoss();
        return boss != null && boss.isAlive() && boss.getBossState() == ChiefAuditorBossEntity.STATE_STAGGER;
    }

    public static boolean isCoreOpen() {
        if (!isBattleActive()) {
            return false;
        }
        ChiefAuditorBossEntity boss = getActiveBoss();
        return boss != null && boss.isAlive() && boss.isCoreOpen();
    }

    public static ChiefAuditorBossEntity getActiveBoss() {
        return getActiveBoss(lastServer);
    }

    public static ChiefAuditorBossEntity getActiveBoss(MinecraftServer server) {
        if (server == null || activeBossId == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity found = level.getEntity(activeBossId);
            if (found instanceof ChiefAuditorBossEntity boss) {
                return boss;
            }
        }
        return null;
    }

    /**
     * Запускает бой с Главным Аудитором 17.4 рядом с игроком-инициатором.
     */
    public static boolean startBossBattle(MinecraftServer server, ServerPlayer initiator) {
        if (server == null || initiator == null) {
            return false;
        }
        lastServer = server;

        ChiefAuditorBossEntity existing = getActiveBoss(server);
        if (existing != null && existing.isAlive()) {
            initiator.sendOverlayMessage(Component.literal("✖ Главный Аудитор 17.4 уже на арене!")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            return false;
        }

        // На случай зависшего состояния после нештатного удаления сущности чистим остатки
        if (currentState != State.INACTIVE || activeBossId != null) {
            clearBoss(server);
        }

        EntityType<?> bossType = resolveBossEntityType();
        if (bossType == null) {
            initiator.sendSystemMessage(Component.literal("✖ Ошибка: сущность playermarket:chief_auditor не зарегистрирована!")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        ServerLevel spawnLevel = (ServerLevel) initiator.level();
        BlockPos spawnPos = findSafeSpawnPos(spawnLevel, initiator);

        Entity spawned = bossType.spawn(spawnLevel, spawnPos, EntitySpawnReason.EVENT);
        if (!(spawned instanceof ChiefAuditorBossEntity boss)) {
            if (spawned != null) {
                spawned.discard();
            }
            initiator.sendSystemMessage(Component.literal("✖ Не удалось призвать Главного Аудитора 17.4!")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        double scaledHp = computeScaledBossHealth(server);
        AttributeInstance maxHpAttr = boss.getAttribute(Attributes.MAX_HEALTH);
        if (maxHpAttr != null) {
            maxHpAttr.setBaseValue(scaledHp);
        }
        boss.setHealth((float) scaledHp);
        boss.setPersistenceRequired();
        boss.setTarget(initiator);

        boss.setPhase(1);
        boss.setPhaseTwo(false);
        boss.setBossState(ChiefAuditorBossEntity.STATE_COMBAT);
        boss.setCoreOpen(false);

        activeBossId = boss.getUUID();
        activeDimensionKey = null;
        currentState = State.COMBAT;
        currentPhase = 1;
        currentAttack = AttackKind.NONE;
        attackTicks = 0;
        cooldownTicks = 45;
        coreOpenTicksRemaining = 0;
        cycleIndex = 0;
        outOfRangeTicks = 0;
        longRangeCooldownTicks = 0;
        lockedPulseOrigin = null;
        lockedPulseTarget = null;
        lockedPulsePlayerId = null;
        waveHitPlayers.clear();
        victoryProcessed = false;

        BOSS_BAR.setVisible(true);
        syncBossBarPlayers(server, spawnLevel, boss);
        updateBossBarVisuals(boss);

        BossCoreHitboxController.tick(spawnLevel, boss, 1, false);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ScreenAlertHelper.sendSafeBanner(
                    player,
                    "§c§l⚔ АУДИТОР",
                    "§eПервичный аудит",
                    "§c§l[ГЛАВНЫЙ АУДИТОР 17.4] §r§eПрибыл на арену (" + (int) scaledHp + " HP)! Допускаются ТОЛЬКО §6Молот Corp §eи §bНезеритовый меч§e. Бейте по §aЯДРУ В ГРУДИ§e, когда бронеплиты раскрыты!"
            );
        }

        spawnLevel.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.5F, 0.85F);
        return true;
    }

    /**
     * Принудительно раскрывает ядро босса (например, при уничтожении Энерго-Реле Щита).
     */
    public static void forceOpenCore(int durationTicks) {
        ChiefAuditorBossEntity boss = getActiveBoss(lastServer);
        if (boss != null && boss.isAlive()) {
            openCoreInternal(boss, durationTicks, true);
        }
    }

    public static void forceOpenCore(ServerLevel level, int durationTicks) {
        forceOpenCore(durationTicks);
    }

    public static void forceOpenCore(ChiefAuditorBossEntity boss, int durationTicks) {
        if (boss != null && boss.isAlive()) {
            openCoreInternal(boss, durationTicks, true);
        }
    }

    public static void forceOpenCore(ServerLevel level, ChiefAuditorBossEntity boss, int durationTicks) {
        if (boss != null && boss.isAlive()) {
            openCoreInternal(boss, durationTicks, true);
        } else {
            forceOpenCore(durationTicks);
        }
    }

    /**
     * Снимает активный бой и очищает боссбар/хитбоксы (например при остановке сервера или сбросе).
     */
    public static void clearBoss() {
        clearBoss(lastServer);
    }

    public static void clearBoss(MinecraftServer server) {
        MinecraftServer targetServer = server != null ? server : lastServer;
        if (targetServer != null) {
            ChiefAuditorBossEntity boss = getActiveBoss(targetServer);
            if (boss != null && !boss.isRemoved()) {
                boss.discard();
            }
            BossCoreHitboxController.cleanup(targetServer);
        }
        resetStateOnly();
    }

    public static void onBossDeath(LivingEntity entity) {
        if (entity == null || !(entity.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        onBossDeath(serverLevel.getServer(), serverLevel, entity);
    }

    public static void onBossDeath(ServerLevel level, LivingEntity entity) {
        if (level == null || entity == null) {
            return;
        }
        onBossDeath(level.getServer(), level, entity);
    }

    public static void onBossDeath(MinecraftServer server, ServerLevel level, LivingEntity entity) {
        if (entity == null || !isAuditorBoss(entity)) {
            return;
        }
        onBossDefeated(server, level, entity.position());
    }

    public static void onBossDeath(MinecraftServer server, ServerLevel level, Vec3 deathPos) {
        onBossDefeated(server, level, deathPos);
    }

    /**
     * Главный серверный тик боевой логики Аудитора 17.4.
     */
    public static void tickServer(MinecraftServer server) {
        if (server == null) {
            return;
        }
        lastServer = server;

        if (currentState == State.INACTIVE || activeBossId == null) {
            if (BOSS_BAR.isVisible()) {
                BOSS_BAR.setVisible(false);
                BOSS_BAR.removeAllPlayers();
            }
            return;
        }

        ChiefAuditorBossEntity boss = getActiveBoss(server);
        if (boss == null) {
            // Если сущность больше не найдена ни в одном загруженном мире — очищаем состояние
            BossCoreHitboxController.cleanup(server);
            resetStateOnly();
            return;
        }

        if (!(boss.level() instanceof ServerLevel bossLevel)) {
            return;
        }

        if (boss.isDeadOrDying() || boss.getRemovalReason() == Entity.RemovalReason.KILLED) {
            if (!victoryProcessed) {
                onBossDefeated(server, bossLevel, boss.position());
            }
            return;
        }

        if (boss.isRemoved()) {
            BossCoreHitboxController.cleanup(server);
            resetStateOnly();
            return;
        }

        // Синхронизируем фазы, боссбар и физические Interaction-хитбоксы ядра/реле
        updatePhaseProgression(server, bossLevel, boss);
        BossCoreHitboxController.tick(bossLevel, boss, currentPhase, boss.isCoreOpen());
        syncBossBarPlayers(server, bossLevel, boss);
        updateBossBarVisuals(boss);

        if (longRangeCooldownTicks > 0) {
            longRangeCooldownTicks--;
        }

        ServerPlayer nearestTarget = findNearestCombatPlayer(bossLevel, boss, 72.0);
        if (nearestTarget != null) {
            boss.setTarget(nearestTarget);
        }

        // Если сейчас открыто ядро (STAGGER) — босс перегрет, не атакует и ждёт ударов по груди
        if (currentState == State.STAGGER) {
            tickCoreStagger(bossLevel, boss);
            return;
        }

        // Проверка Long-Range Protocol (>28м более 3 секунд)
        if (currentAttack == AttackKind.NONE && nearestTarget != null) {
            double distSq = boss.distanceToSqr(nearestTarget);
            if (distSq > (LONG_RANGE_DISTANCE * LONG_RANGE_DISTANCE)) {
                outOfRangeTicks++;
                if (outOfRangeTicks >= LONG_RANGE_TRIGGER_TICKS && longRangeCooldownTicks <= 0) {
                    beginLongRangeProtocol(bossLevel, boss, nearestTarget);
                    return;
                }
            } else {
                outOfRangeTicks = Math.max(0, outOfRangeTicks - 2);
            }
        } else if (nearestTarget == null) {
            outOfRangeTicks = 0;
        }

        // Тик текущей активной атаки
        if (currentAttack != AttackKind.NONE) {
            tickActiveAttack(bossLevel, boss, nearestTarget);
            return;
        }

        // Пауза между атаками
        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }

        if (nearestTarget != null) {
            pickNextPhaseAttack(bossLevel, boss, nearestTarget);
        }
    }

    // -------------------------------------------------------------------------
    // Фазы боя и переходы
    // -------------------------------------------------------------------------

    private static void updatePhaseProgression(MinecraftServer server, ServerLevel level, ChiefAuditorBossEntity boss) {
        float maxHp = Math.max(1.0F, boss.getMaxHealth());
        float ratio = boss.getHealth() / maxHp;

        int desiredPhase;
        if (ratio <= 0.15F) {
            desiredPhase = 4;
        } else if (ratio <= 0.45F) {
            desiredPhase = 3;
        } else if (ratio <= 0.75F) {
            desiredPhase = 2;
        } else {
            desiredPhase = 1;
        }

        if (desiredPhase <= currentPhase) {
            return;
        }

        currentPhase = desiredPhase;
        boss.setPhase(desiredPhase);
        boss.setPhaseTwo(desiredPhase >= 2);

        switch (desiredPhase) {
            case 2 -> {
                spawnPhaseTwoEnforcer(level, boss);
                level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                        SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 1.6F, 0.85F);
                broadcastPhaseBanner(server,
                        "§5§l⚡ ФАЗА II",
                        "§dЭнерго-Реле и Контроль",
                        "§5§l[ФАЗА II — КОНТРОЛЬ ЗАЛОГА] §r§dАудитор активировал §bЭнерго-Реле Щита §dи вызвал Силовика! Сбивайте реле в воздухе на Крюке-Кошке, чтобы перегрузить и раскрыть ядро!"
                );
            }
            case 3 -> {
                level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                        SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.6F, 0.7F);
                broadcastPhaseBanner(server,
                        "§6§l☄ ФАЗА III",
                        "§cПротокол Ликвидации",
                        "§6§l[ФАЗА III — ЛИКВИДАЦИЯ] §r§cОстерегайтесь Skyfall: взлетайте на Крюке-Кошке при подбросе и бейте §6Молотом Corp §cс разгона в открытое ядро!"
                );
                // Сразу готовим быстрый выход на Skyfall после паузы
                cooldownTicks = Math.min(cooldownTicks, 20);
            }
            case 4 -> {
                level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                        SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 1.5F, 0.9F);
                broadcastPhaseBanner(server,
                        "§c§l☠ ФАЗА IV",
                        "§4Последнее предупреждение",
                        "§c§l[ФАЗА IV — FINAL NOTICE] §r§eЯдро Титана нестабильно и открывается чаще! Добивайте босса в грудь!"
                );
                cooldownTicks = Math.min(cooldownTicks, 15);
            }
            default -> {
            }
        }
    }

    private static void spawnPhaseTwoEnforcer(ServerLevel level, ChiefAuditorBossEntity boss) {
        BlockPos pos = boss.blockPosition().offset(3, 1, 3);
        Optional<EntityType<?>> enforcerType = BuiltInRegistries.ENTITY_TYPE.getOptional(ENFORCER_ENTITY_ID);
        Mob mob = null;
        if (enforcerType.isPresent()) {
            Entity spawned = enforcerType.get().spawn(level, pos, EntitySpawnReason.EVENT);
            if (spawned instanceof Mob m) {
                mob = m;
            }
        }
        if (mob == null) {
            mob = EntityTypes.VINDICATOR.spawn(level, pos, EntitySpawnReason.EVENT);
        }
        if (mob != null) {
            mob.setCustomName(Component.literal("Охрана Аудитора [Силовик]").withStyle(ChatFormatting.LIGHT_PURPLE));
            if (mob instanceof CollectorEnforcerEntity enforcer) {
                enforcer.markAsRaidEnforcer(1);
            } else {
                mob.setPersistenceRequired();
            }
            if (boss.getTarget() != null) {
                mob.setTarget(boss.getTarget());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Выбор и выполнение атак по 4 фазам
    // -------------------------------------------------------------------------

    private static void pickNextPhaseAttack(ServerLevel level, ChiefAuditorBossEntity boss, ServerPlayer target) {
        cycleIndex++;
        attackTicks = 0;
        waveHitPlayers.clear();

        switch (currentPhase) {
            case 1 -> {
                // PHASE I: Corporate Punch <-> Ground Inspection -> Core Open 5.5s
                currentAttack = (cycleIndex % 2 == 1)
                        ? AttackKind.CORPORATE_PUNCH
                        : AttackKind.GROUND_INSPECTION;
            }
            case 2 -> {
                // PHASE II: Gravity Pull -> Corporate Sweep (периодическое окно ядра + Энерго-Реле)
                currentAttack = (cycleIndex % 2 == 1)
                        ? AttackKind.GRAVITY_PULL
                        : AttackKind.CORPORATE_SWEEP;
            }
            case 3 -> {
                // PHASE III: чередуем Skyfall (открытие ядра 6.0s) и Corporate Sweep / Punch
                if (cycleIndex % 2 == 0) {
                    currentAttack = AttackKind.SKYFALL;
                } else {
                    currentAttack = (cycleIndex % 4 == 1) ? AttackKind.CORPORATE_SWEEP : AttackKind.CORPORATE_PUNCH;
                }
            }
            default -> {
                // PHASE IV: быстрые читаемые атаки + частое раскрытие ядра (6.5s)
                int mod = cycleIndex % 3;
                if (mod == 1) {
                    currentAttack = AttackKind.CORPORATE_SWEEP;
                } else if (mod == 2) {
                    currentAttack = AttackKind.GROUND_INSPECTION;
                } else {
                    currentAttack = AttackKind.SKYFALL;
                }
            }
        }

        if (currentAttack == AttackKind.SKYFALL) {
            currentState = State.SKYFALL_TELEGRAPH;
            boss.setBossState(ChiefAuditorBossEntity.STATE_TELEGRAPH);
            boss.triggerAnim("action", "energy_shield");
        } else {
            currentState = State.COMBAT;
            boss.setBossState(ChiefAuditorBossEntity.STATE_TELEGRAPH);
        }

        boss.getLookControl().setLookAt(target, 45.0F, 45.0F);
    }

    private static void tickActiveAttack(ServerLevel level, ChiefAuditorBossEntity boss, ServerPlayer target) {
        attackTicks++;

        switch (currentAttack) {
            case CORPORATE_PUNCH -> tickCorporatePunch(level, boss, target);
            case GROUND_INSPECTION -> tickGroundInspection(level, boss);
            case GRAVITY_PULL -> tickGravityPull(level, boss);
            case CORPORATE_SWEEP -> tickCorporateSweep(level, boss);
            case SKYFALL -> tickSkyfall(level, boss);
            case LONG_RANGE_PULSE -> tickLongRangePulse(level, boss);
            case NONE -> finishAttack(40);
        }
    }

    private static void tickCorporatePunch(ServerLevel level, ChiefAuditorBossEntity boss, ServerPlayer target) {
        if (target != null) {
            boss.getLookControl().setLookAt(target, 45.0F, 45.0F);
        }
        // Телеграф 20 тиков (1.0 сек)
        if (attackTicks < 20) {
            if (attackTicks == 6) {
                boss.triggerAnim("action", "punch");
                level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                        SoundEvents.IRON_GOLEM_ATTACK, SoundSource.HOSTILE, 1.1F, 0.85F);
            }
            if (attackTicks % 3 == 0) {
                Vec3 front = getHorizontalForward(boss).scale(2.4);
                level.sendParticles(RED_WARN_DUST,
                        boss.getX() + front.x, boss.getY() + 2.2, boss.getZ() + front.z,
                        6, 0.45, 0.45, 0.45, 0.01);
            }
            return;
        }

        // Удар на 20-м тике: честные 5.0 (P1) / 5.5 (P3+) урона
        Vec3 forward = getHorizontalForward(boss);
        Vec3 hitCenter = boss.position().add(forward.scale(2.7)).add(0.0, 1.5, 0.0);
        float damage = currentPhase >= 3 ? 5.5F : 5.0F;

        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(hitCenter, hitCenter).inflate(3.1))) {
            if (!isTargetablePlayer(player)) {
                continue;
            }
            if (player.hurtServer(level, level.damageSources().mobAttack(boss), damage)) {
                Vec3 knock = forward.scale(0.85).add(0.0, 0.35, 0.0);
                pushPlayer(player, knock);
            }
        }

        level.sendParticles(ParticleTypes.EXPLOSION, hitCenter.x, hitCenter.y, hitCenter.z, 2, 0.3, 0.3, 0.3, 0.0);
        level.playSound(null, hitCenter.x, hitCenter.y, hitCenter.z,
                SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 1.2F, 0.8F);

        finishAttack(currentPhase >= 4 ? 26 : 38);
    }

    private static void tickGroundInspection(ServerLevel level, ChiefAuditorBossEntity boss) {
        // Фаза телеграфа 24 тика (1.2 сек)
        if (attackTicks <= 24) {
            if (attackTicks == 1) {
                boss.triggerAnim("action", "slam");
                level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                        SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.9F, 0.65F);
            }
            if (attackTicks % 4 == 0) {
                drawGroundRing(level, boss.position(), 2.8, RED_WARN_DUST, 20);
            }
            return;
        }

        // Расширение ударной волны с радиуса 2.5 до 13.5 за 22 тика
        int waveTick = attackTicks - 24;
        double radius = 2.5 + (waveTick * 0.52);
        drawGroundRing(level, boss.position(), radius, RED_WARN_DUST, 28);

        if (waveTick == 1) {
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.1F, 0.75F);
        }

        float waveDamage = currentPhase >= 4 ? 5.0F : 4.0F;
        for (ServerPlayer player : level.players()) {
            if (!isTargetablePlayer(player) || waveHitPlayers.contains(player.getUUID())) {
                continue;
            }
            double horizDist = Math.hypot(player.getX() - boss.getX(), player.getZ() - boss.getZ());
            double dy = Math.abs(player.getY() - boss.getY());
            // Перепрыгнуть или уйти на Крюке-Кошке легко спасает от волны
            if (Math.abs(horizDist - radius) <= 1.35 && dy <= 1.25 && player.onGround()) {
                waveHitPlayers.add(player.getUUID());
                if (player.hurtServer(level, level.damageSources().mobAttack(boss), waveDamage)) {
                    Vec3 away = player.position().subtract(boss.position());
                    Vec3 flatAway = new Vec3(away.x, 0.0, away.z);
                    Vec3 norm = flatAway.lengthSqr() > 1.0E-4 ? flatAway.normalize() : new Vec3(1.0, 0.0, 0.0);
                    pushPlayer(player, norm.scale(0.55).add(0.0, 0.4, 0.0));
                }
            }
        }

        if (waveTick >= 22) {
            int openDuration = currentPhase >= 4 ? PHASE_4_CORE_OPEN_TICKS : PHASE_1_CORE_OPEN_TICKS;
            openCoreInternal(boss, openDuration, false);
        }
    }

    private static void tickGravityPull(ServerLevel level, ChiefAuditorBossEntity boss) {
        if (attackTicks == 1) {
            boss.triggerAnim("action", "energy_shield");
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.3F, 0.75F);
        }

        // Телеграф 20 тиков: потоки циановых частиц от игроков к боссу
        if (attackTicks < 20) {
            if (attackTicks % 4 == 0) {
                for (ServerPlayer player : level.players()) {
                    if (isTargetablePlayer(player) && boss.distanceToSqr(player) <= 484.0) {
                        spawnParticleLine(level, player.position().add(0, 1.0, 0), boss.position().add(0, 2.5, 0), CYAN_PULL_DUST, 10);
                    }
                }
            }
            return;
        }

        // На 20-м тике притягивает игроков в радиусе 22 блоков (вырываться Крюком-Кошкой!)
        for (ServerPlayer player : level.players()) {
            if (!isTargetablePlayer(player)) {
                continue;
            }
            double distSq = boss.distanceToSqr(player);
            if (distSq > 20.0 && distSq <= 484.0) {
                Vec3 toBoss = boss.position().add(0, 1.2, 0).subtract(player.position());
                Vec3 horiz = new Vec3(toBoss.x, 0.0, toBoss.z);
                double len = horiz.length();
                if (len > 1.0E-4) {
                    Vec3 pull = horiz.scale(1.15 / len).add(0.0, 0.36, 0.0);
                    pushPlayer(player, pull);
                    player.sendOverlayMessage(Component.literal("⚡ ГРАВИ-ТЯГА! Вырвись Крюком-Кошкой!")
                            .withStyle(ChatFormatting.AQUA));
                }
            }
        }

        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.1F, 0.75F);

        // Сразу готовим размах после тяги (читаемое окно ~1.1с улететь крюком)
        finishAttack(12);
    }

    private static void tickCorporateSweep(ServerLevel level, ChiefAuditorBossEntity boss) {
        if (attackTicks == 1) {
            boss.triggerAnim("action", "attack");
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.3F, 0.7F);
        }

        if (attackTicks < 22) {
            if (attackTicks % 4 == 0) {
                drawGroundRing(level, boss.position().add(0, 1.4, 0), 6.2, RED_WARN_DUST, 26);
            }
            return;
        }

        float sweepDamage = currentPhase >= 4 ? 6.5F : 6.0F;
        for (ServerPlayer player : level.players()) {
            if (!isTargetablePlayer(player)) {
                continue;
            }
            double horizDist = Math.hypot(player.getX() - boss.getX(), player.getZ() - boss.getZ());
            // Уход вверх на Крюке-Кошке спасает от размаха
            if (horizDist <= 6.5 && player.getY() <= boss.getY() + 2.8) {
                if (player.hurtServer(level, level.damageSources().mobAttack(boss), sweepDamage)) {
                    Vec3 away = player.position().subtract(boss.position());
                    Vec3 flat = new Vec3(away.x, 0.0, away.z);
                    Vec3 norm = flat.lengthSqr() > 1.0E-4 ? flat.normalize() : new Vec3(1.0, 0.0, 0.0);
                    pushPlayer(player, norm.scale(0.95).add(0.0, 0.35, 0.0));
                }
            }
        }

        level.sendParticles(ParticleTypes.SWEEP_ATTACK,
                boss.getX(), boss.getY() + 1.8, boss.getZ(),
                10, 3.2, 0.4, 3.2, 0.0);
        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.IRON_GOLEM_ATTACK, SoundSource.HOSTILE, 1.4F, 0.75F);

        // В Фазе 2 каждые 2 цикла даём гарантированное окно ядра (даже помимо Энерго-Реле)
        if (currentPhase == 2 && (cycleIndex % 4 == 0)) {
            openCoreInternal(boss, PHASE_2_CORE_OPEN_TICKS, false);
        } else {
            finishAttack(currentPhase >= 4 ? 26 : 36);
        }
    }

    private static void tickSkyfall(ServerLevel level, ChiefAuditorBossEntity boss) {
        if (attackTicks == 1) {
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.5F, 0.85F);
            for (ServerPlayer player : level.players()) {
                if (isTargetablePlayer(player) && boss.distanceToSqr(player) <= 900.0) {
                    player.sendOverlayMessage(Component.literal("⚠ SKYFALL через 2.2с — приготовь Крюк-Кошку!")
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                }
            }
        }

        if (attackTicks < SKYFALL_TELEGRAPH_TICKS) {
            if (attackTicks == SKYFALL_TELEGRAPH_TICKS - 8) {
                boss.triggerAnim("action", "slam");
            }
            if (attackTicks % 4 == 0) {
                drawGroundRing(level, boss.position(), 9.2, RED_WARN_DUST, 36);
            }
            return;
        }

        // Удар Skyfall на 44-м тике: честный урон 7.0 (P3) / 7.5 (P4) + подброс под Крюк-Кошку
        float skyfallDamage = currentPhase >= 4 ? 7.5F : 7.0F;
        for (ServerPlayer player : level.players()) {
            if (!isTargetablePlayer(player)) {
                continue;
            }
            double horizDist = Math.hypot(player.getX() - boss.getX(), player.getZ() - boss.getZ());
            if (horizDist <= 9.5 && Math.abs(player.getY() - boss.getY()) <= 5.0) {
                player.hurtServer(level, level.damageSources().mobAttack(boss), skyfallDamage);
                Vec3 away = player.position().subtract(boss.position());
                Vec3 flat = new Vec3(away.x, 0.0, away.z);
                Vec3 norm = flat.lengthSqr() > 1.0E-4 ? flat.normalize() : new Vec3(1.0, 0.0, 0.0);
                // Подброс вверх: идеальный сетап для Крюка-Кошки и крита 85 HP Молотом по ядру!
                pushPlayer(player, norm.scale(0.45).add(0.0, 1.25, 0.0));
                player.sendOverlayMessage(Component.literal("⚡ ЯДРО ПЕРЕГРЕТО — ХВАТАЙСЯ КРЮКОМ И БЕЙ В ГРУДЬ!")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            }
        }

        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                boss.getX(), boss.getY() + 0.5, boss.getZ(),
                2, 1.5, 0.3, 1.5, 0.0);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                boss.getX(), boss.getY() + 0.4, boss.getZ(),
                28, 4.2, 0.4, 4.2, 0.06);
        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.8F, 0.65F);

        int openDuration = currentPhase >= 4 ? PHASE_4_CORE_OPEN_TICKS : PHASE_3_CORE_OPEN_TICKS;
        openCoreInternal(boss, openDuration, false);
    }

    // -------------------------------------------------------------------------
    // Long-Range Protocol (>28м без регена HP)
    // -------------------------------------------------------------------------

    private static void beginLongRangeProtocol(ServerLevel level, ChiefAuditorBossEntity boss, ServerPlayer distantTarget) {
        if (boss.isCoreOpen()) {
            closeCoreInternal(boss);
        }

        currentState = State.COMBAT;
        currentAttack = AttackKind.LONG_RANGE_PULSE;
        attackTicks = 0;
        outOfRangeTicks = 0;
        longRangeCooldownTicks = LONG_RANGE_COOLDOWN_TICKS;

        boss.setBossState(ChiefAuditorBossEntity.STATE_TELEGRAPH);
        boss.triggerAnim("action", "dash");

        Vec3 delta = distantTarget.position().subtract(boss.position());
        Vec3 flat = new Vec3(delta.x, 0.0, delta.z);
        double len = flat.length();
        Vec3 dir = len > 1.0E-4 ? flat.scale(1.0 / len) : getHorizontalForward(boss);

        boss.resetFallDistance();
        boss.setDeltaMovement(dir.x * 1.45, 0.58, dir.z * 1.45);
        boss.syncVelocity = true;
        boss.getLookControl().setLookAt(distantTarget, 45.0F, 45.0F);

        lockedPulseOrigin = boss.position().add(0.0, 2.8, 0.0);
        lockedPulseTarget = distantTarget.position().add(0.0, 1.0, 0.0);
        lockedPulsePlayerId = distantTarget.getUUID();

        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.3F, 0.9F);
        distantTarget.sendOverlayMessage(Component.literal("⚡ ДАЛЬНИЙ ПРОТОКОЛ — Аудитор сокращает дистанцию!")
                .withStyle(ChatFormatting.RED));
    }

    private static void tickLongRangePulse(ServerLevel level, ChiefAuditorBossEntity boss) {
        if (attackTicks < 18) {
            if (attackTicks % 3 == 0 && lockedPulseTarget != null) {
                Vec3 origin = boss.position().add(0.0, 2.8, 0.0);
                spawnParticleLine(level, origin, lockedPulseTarget, CYAN_PULL_DUST, 18);
            }
            return;
        }

        Vec3 origin = boss.position().add(0.0, 2.8, 0.0);
        Vec3 targetPos = lockedPulseTarget != null ? lockedPulseTarget : origin.add(getHorizontalForward(boss).scale(16.0));
        spawnParticleLine(level, origin, targetPos, RED_WARN_DUST, 36);

        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.TRIDENT_THUNDER.value(), SoundSource.HOSTILE, 1.2F, 1.2F);

        if (lockedPulsePlayerId != null) {
            ServerPlayer target = level.getServer().getPlayerList().getPlayer(lockedPulsePlayerId);
            if (target != null && target.level() == level && isTargetablePlayer(target)) {
                if (target.position().distanceToSqr(targetPos) <= 9.0) {
                    target.hurtServer(level, level.damageSources().indirectMagic(boss, boss), 5.0F);
                }
            }
        }

        lockedPulseOrigin = null;
        lockedPulseTarget = null;
        lockedPulsePlayerId = null;
        finishAttack(26);
    }

    // -------------------------------------------------------------------------
    // Управление физическим ядром (Stagger)
    // -------------------------------------------------------------------------

    private static void openCoreInternal(ChiefAuditorBossEntity boss, int durationTicks, boolean fromRelayOverload) {
        currentState = State.STAGGER;
        currentAttack = AttackKind.NONE;
        attackTicks = 0;
        coreOpenTicksRemaining = Math.max(40, durationTicks);

        boss.setBossState(ChiefAuditorBossEntity.STATE_STAGGER);
        boss.setCoreOpen(true);

        if (fromRelayOverload) {
            boss.triggerAnim("core", "core_overload");
        } else {
            boss.triggerAnim("core", "shield_open");
        }

        if (boss.level() instanceof ServerLevel level) {
            level.sendParticles(CORE_OVERLOAD_DUST,
                    boss.getX(), boss.getY() + 3.1, boss.getZ(),
                    22, 0.85, 0.75, 0.85, 0.05);
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 1.5F, 1.15F);

            for (ServerPlayer player : level.players()) {
                if (boss.distanceToSqr(player) <= 1600.0) {
                    player.sendOverlayMessage(Component.literal("🔓 ЯДРО В ГРУДИ ОТКРЫТО! Бей в грудь!")
                            .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
                }
            }
        }
    }

    private static void closeCoreInternal(ChiefAuditorBossEntity boss) {
        boss.setCoreOpen(false);
        boss.setBossState(ChiefAuditorBossEntity.STATE_COMBAT);
        boss.triggerAnim("core", "shield_close");

        if (boss.level() instanceof ServerLevel level) {
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.IRON_DOOR_CLOSE, SoundSource.HOSTILE, 1.3F, 0.7F);
        }

        coreOpenTicksRemaining = 0;
        currentState = State.COMBAT;
        cooldownTicks = currentPhase >= 4 ? 22 : 34;
    }

    private static void tickCoreStagger(ServerLevel level, ChiefAuditorBossEntity boss) {
        boss.getNavigation().stop();

        if (coreOpenTicksRemaining % 6 == 0) {
            Vec3 chestPos = boss.position().add(getHorizontalForward(boss).scale(1.1)).add(0.0, 3.05, 0.0);
            level.sendParticles(currentPhase >= 4 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.END_ROD,
                    chestPos.x, chestPos.y, chestPos.z,
                    4, 0.35, 0.35, 0.35, 0.02);
        }

        coreOpenTicksRemaining--;
        if (coreOpenTicksRemaining <= 0) {
            closeCoreInternal(boss);
        }
    }

    private static void finishAttack(int nextCooldownTicks) {
        currentAttack = AttackKind.NONE;
        attackTicks = 0;
        cooldownTicks = Math.max(10, nextCooldownTicks);
        currentState = State.COMBAT;
        ChiefAuditorBossEntity boss = getActiveBoss(lastServer);
        if (boss != null && boss.isAlive()) {
            boss.setBossState(ChiefAuditorBossEntity.STATE_COMBAT);
        }
    }

    // -------------------------------------------------------------------------
    // Победа, лут и очистка
    // -------------------------------------------------------------------------

    private static void onBossDefeated(MinecraftServer server, ServerLevel level, Vec3 deathPos) {
        if (victoryProcessed) {
            return;
        }
        victoryProcessed = true;

        MinecraftServer resolvedServer = server != null ? server : (level != null ? level.getServer() : lastServer);

        if (resolvedServer != null) {
            BossCoreHitboxController.cleanup(resolvedServer);
        }
        resetStateOnly();

        if (level != null && deathPos != null) {
            ensureNetherStarDropped(level, deathPos);
        }

        if (resolvedServer != null) {
            FreedomProtocolManager.activateFreedomProtocol(resolvedServer, level, deathPos);
        }
    }

    private static void ensureNetherStarDropped(ServerLevel level, Vec3 deathPos) {
        AABB lootBox = new AABB(deathPos, deathPos).inflate(5.0);
        for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class, lootBox)) {
            if (itemEntity.getItem().is(Items.NETHER_STAR)) {
                return;
            }
        }
        ItemEntity starDrop = new ItemEntity(level, deathPos.x, deathPos.y + 0.8, deathPos.z, new ItemStack(Items.NETHER_STAR));
        starDrop.setDefaultPickUpDelay();
        level.addFreshEntity(starDrop);
    }

    private static void resetStateOnly() {
        BOSS_BAR.setVisible(false);
        BOSS_BAR.removeAllPlayers();

        activeBossId = null;
        activeDimensionKey = null;
        currentState = State.INACTIVE;
        currentPhase = 1;
        currentAttack = AttackKind.NONE;
        attackTicks = 0;
        cooldownTicks = 40;
        coreOpenTicksRemaining = 0;
        cycleIndex = 0;
        outOfRangeTicks = 0;
        longRangeCooldownTicks = 0;
        lockedPulseOrigin = null;
        lockedPulseTarget = null;
        lockedPulsePlayerId = null;
        waveHitPlayers.clear();
    }

    // -------------------------------------------------------------------------
    // Утилиты: спавн, боссбар, геометрия, частицы
    // -------------------------------------------------------------------------

    private static EntityType<?> resolveBossEntityType() {
        if (customBossType != null) {
            return customBossType;
        }
        Optional<EntityType<?>> primary = BuiltInRegistries.ENTITY_TYPE.getOptional(BOSS_ENTITY_ID);
        if (primary.isPresent()) {
            return primary.get();
        }
        return BuiltInRegistries.ENTITY_TYPE.getOptional(BOSS_ENTITY_ALT_ID).orElse(null);
    }

    private static double computeScaledBossHealth(MinecraftServer server) {
        int activePlayers = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isSpectator()) {
                activePlayers++;
            }
        }
        if (activePlayers >= 3) {
            return 600.0;
        }
        if (activePlayers == 2) {
            return 500.0;
        }
        return 360.0;
    }

    private static BlockPos findSafeSpawnPos(ServerLevel level, ServerPlayer initiator) {
        Vec3 forward = initiator.getLookAngle().multiply(1.0, 0.0, 1.0);
        Vec3 dir = forward.lengthSqr() > 1.0E-4 ? forward.normalize() : new Vec3(1.0, 0.0, 0.0);
        int targetX = Mth.floor(initiator.getX() + dir.x * 7.0);
        int targetZ = Mth.floor(initiator.getZ() + dir.z * 7.0);
        int baseY = initiator.getBlockY();

        // Поиск свободного пространства в ±5 блоках (работает и на улице, и в больших залах)
        for (int dy = -2; dy <= 5; dy++) {
            BlockPos pos = new BlockPos(targetX, baseY + dy, targetZ);
            if (!level.getBlockState(pos.below()).isAir()
                    && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                    && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) {
                return pos;
            }
        }
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(targetX, baseY, targetZ));
        if (Math.abs(surface.getY() - baseY) <= 12) {
            return surface;
        }
        return initiator.blockPosition();
    }

    private static void syncBossBarPlayers(MinecraftServer server, ServerLevel bossLevel, ChiefAuditorBossEntity boss) {
        List<ServerPlayer> candidates = new ArrayList<>();
        Set<UUID> candidateIds = new HashSet<>();

        for (ServerPlayer player : bossLevel.players()) {
            if (boss.distanceToSqr(player) <= 4096.0) {
                candidates.add(player);
                candidateIds.add(player.getUUID());
                if (!BOSS_BAR.getPlayers().contains(player)) {
                    BOSS_BAR.addPlayer(player);
                }
            }
        }

        for (ServerPlayer tracked : List.copyOf(BOSS_BAR.getPlayers())) {
            if (!candidateIds.contains(tracked.getUUID()) || tracked.level() != bossLevel) {
                BOSS_BAR.removePlayer(tracked);
            }
        }
    }

    private static void updateBossBarVisuals(ChiefAuditorBossEntity boss) {
        float maxHp = Math.max(1.0F, boss.getMaxHealth());
        float progress = Mth.clamp(boss.getHealth() / maxHp, 0.0F, 1.0F);
        BOSS_BAR.setProgress(progress);

        String phaseTag = switch (currentPhase) {
            case 1 -> "[ФАЗА I // ПЕРВИЧНЫЙ АУДИТ]";
            case 2 -> "[ФАЗА II // КОНТРОЛЬ ЗАЛОГА]";
            case 3 -> "[ФАЗА III // ЛИКВИДАЦИЯ]";
            default -> "[ФАЗА IV // FINAL NOTICE]";
        };

        if (boss.isCoreOpen()) {
            BOSS_BAR.setColor(BossEvent.BossBarColor.GREEN);
            BOSS_BAR.setOverlay(BossEvent.BossBarOverlay.PROGRESS);
            BOSS_BAR.setName(Component.literal("⚡ ГЛАВНЫЙ АУДИТОР 17.4 — ЯДРО ОТКРЫТО! ⚡")
                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        } else {
            BossEvent.BossBarColor color = switch (currentPhase) {
                case 2 -> BossEvent.BossBarColor.PURPLE;
                case 3 -> BossEvent.BossBarColor.YELLOW;
                default -> BossEvent.BossBarColor.RED;
            };
            BOSS_BAR.setColor(color);
            BOSS_BAR.setOverlay(BossEvent.BossBarOverlay.NOTCHED_10);
            BOSS_BAR.setName(Component.literal(phaseTag + " (" + (int) Math.ceil(boss.getHealth()) + " HP)")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        }
    }

    private static void broadcastPhaseBanner(MinecraftServer server, String shortTitle, String shortSub, String chatMessage) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ScreenAlertHelper.sendSafeBanner(player, shortTitle, shortSub, chatMessage);
        }
    }

    private static ServerPlayer findNearestCombatPlayer(ServerLevel level, ChiefAuditorBossEntity boss, double maxRadius) {
        double bestDistSq = maxRadius * maxRadius;
        ServerPlayer best = null;
        for (ServerPlayer player : level.players()) {
            if (!isTargetablePlayer(player)) {
                continue;
            }
            double distSq = boss.distanceToSqr(player);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = player;
            }
        }
        return best;
    }

    private static boolean isTargetablePlayer(ServerPlayer player) {
        return player != null && player.isAlive() && !player.isCreative() && !player.isSpectator();
    }

    private static Vec3 getHorizontalForward(ChiefAuditorBossEntity boss) {
        Vec3 look = boss.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        return flat.lengthSqr() > 1.0E-4 ? flat.normalize() : new Vec3(1.0, 0.0, 0.0);
    }

    private static void pushPlayer(ServerPlayer player, Vec3 velocity) {
        player.setDeltaMovement(velocity);
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        player.syncVelocity = false;
    }

    private static void drawGroundRing(ServerLevel level, Vec3 center, double radius, DustParticleOptions particle, int points) {
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0 * i) / points;
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;
            level.sendParticles(particle, x, center.y + 0.25, z, 1, 0.03, 0.05, 0.03, 0.0);
        }
    }

    private static void spawnParticleLine(ServerLevel level, Vec3 start, Vec3 end, DustParticleOptions particle, int steps) {
        Vec3 diff = end.subtract(start);
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / (double) steps;
            level.sendParticles(particle,
                    start.x + diff.x * t,
                    start.y + diff.y * t,
                    start.z + diff.z * t,
                    1, 0.02, 0.02, 0.02, 0.0);
        }
    }
}