package com.playermarket.story;

import com.playermarket.entity.ChiefAuditorBossEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Управляет реальными физическими {@code EntityTypes.INTERACTION}-хитбоксами босса:
 * <ul>
 *     <li>Хитбокс грудного Ядра ({@code playermarket_boss_core_hitbox}, 1.45x1.35) на высоте ~3.1 блока.</li>
 *     <li>Парящие Энерго-Реле Щита в Фазе 2 ({@code playermarket_boss_relay}, 1.2x1.2) на высоте y+3.2..4.8.</li>
 * </ul>
 */
public final class BossCoreHitboxController {

    public static final String CORE_HITBOX_TAG = "playermarket_boss_core_hitbox";
    public static final String RELAY_HITBOX_TAG = "playermarket_boss_relay";

    private static final float CORE_WIDTH = 1.45F;
    private static final float CORE_HEIGHT = 1.35F;
    private static final double CORE_BASE_Y_OFFSET = 2.45; // центр 2.45 + 1.35/2 = ~3.125 блока
    private static final double CORE_FORWARD_OFFSET = 1.15;

    private static final float RELAY_WIDTH = 1.2F;
    private static final float RELAY_HEIGHT = 1.2F;
    private static final int RELAY_OVERLOAD_OPEN_TICKS = 130; // 6.5 секунд раскрытия ядра

    private static final DustParticleOptions RELAY_BEAM_DUST = new DustParticleOptions(0x28F0FF, 1.0F);

    private static UUID coreHitboxUuid = null;
    private static final List<RelayAnchor> ACTIVE_RELAYS = new ArrayList<>();
    private static final Map<UUID, Long> LAST_PLAYER_RELAY_ACTION_TICK = new HashMap<>();

    private BossCoreHitboxController() {
    }

    /**
     * Каждый тик синхронизирует хитбокс грудного ядра и обслуживает активные Энерго-Реле Фазы 2.
     */
    public static void tick(ServerLevel level, ChiefAuditorBossEntity boss, int phase, boolean coreOpen) {
        if (level == null) {
            return;
        }

        if (boss == null || !boss.isAlive()) {
            cleanupLevel(level);
            return;
        }

        syncCoreHitbox(level, boss);
        tickRelays(level, boss, phase, coreOpen);
    }

    /**
     * Спавнит {@code count} (2–3) парящих Энерго-Реле Щита на высоте y+3.2..4.8 в радиусе 9–12 блоков.
     */
    public static void spawnShieldRelays(ServerLevel level, ChiefAuditorBossEntity boss, int count) {
        if (level == null || boss == null || !boss.isAlive()) {
            return;
        }

        clearRelaysOnly(level);

        int clampedCount = Mth.clamp(count, 2, 3);
        RandomSource random = level.getRandom();
        double baseAngle = random.nextDouble() * (Math.PI * 2.0);

        for (int i = 0; i < clampedCount; i++) {
            double angle = baseAngle + (Math.PI * 2.0 * i) / clampedCount + (random.nextDouble() - 0.5) * 0.35;
            double radius = 9.0 + random.nextDouble() * 3.0; // 9..12 блоков
            double heightOffset = 3.2 + random.nextDouble() * 1.6; // y + 3.2..4.8

            Vec3 anchorPos = new Vec3(
                    boss.getX() + Math.cos(angle) * radius,
                    boss.getY() + heightOffset,
                    boss.getZ() + Math.sin(angle) * radius
            );

            Interaction relay = createInteractionEntity(level, RELAY_WIDTH, RELAY_HEIGHT, anchorPos, RELAY_HITBOX_TAG);
            if (relay != null) {
                relay.setCustomName(Component.literal("⚡ Энерго-Реле Щита").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
                relay.setCustomNameVisible(true);
                level.addFreshEntity(relay);

                ACTIVE_RELAYS.add(new RelayAnchor(relay.getUUID(), anchorPos, random.nextDouble() * Math.PI * 2.0));

                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        anchorPos.x, anchorPos.y + 0.6, anchorPos.z,
                        14, 0.35, 0.35, 0.35, 0.05);
            }
        }
    }

    public static int getActiveRelayCount(ServerLevel level) {
        if (level == null) {
            return 0;
        }
        int alive = 0;
        Iterator<RelayAnchor> it = ACTIVE_RELAYS.iterator();
        while (it.hasNext()) {
            Entity entity = level.getEntity(it.next().uuid);
            if (entity != null && entity.isAlive()) {
                alive++;
            } else {
                it.remove();
            }
        }
        return alive;
    }

    /**
     * Обрабатывает ЛКМ/ПКМ игрока по {@code Interaction}-хитбоксу ядра или Энерго-Реле.
     *
     * @return {@code true}, если целью был хитбокс ядра или реле.
     */
    public static boolean handleEntityInteraction(ServerPlayer attacker, Entity target) {
        if (attacker == null || target == null) {
            return false;
        }
        if (!(attacker.level() instanceof ServerLevel level)) {
            return false;
        }

        if (target.entityTags().contains(CORE_HITBOX_TAG)) {
            ChiefAuditorBossEntity boss = ChiefAuditorBossManager.getActiveBoss(level.getServer());
            if (boss == null || !boss.isAlive()) {
                target.discard();
                return true;
            }
            return BossCombatRules.applyCoreDirectHit(level, boss, attacker);
        }

        if (target.entityTags().contains(RELAY_HITBOX_TAG)) {
            long gameTime = level.getGameTime();
            Long lastHit = LAST_PLAYER_RELAY_ACTION_TICK.get(attacker.getUUID());
            if (lastHit != null && gameTime >= lastHit && (gameTime - lastHit) < 4L) {
                return true;
            }
            LAST_PLAYER_RELAY_ACTION_TICK.put(attacker.getUUID(), gameTime);
            destroyRelay(level, target, attacker);
            return true;
        }

        return false;
    }

    /**
     * Полная очистка всех хитбоксов ядра и реле на сервере.
     */
    public static void cleanup(MinecraftServer server) {
        coreHitboxUuid = null;
        ACTIVE_RELAYS.clear();
        LAST_PLAYER_RELAY_ACTION_TICK.clear();

        if (server == null) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            removeAllTaggedInLevel(level);
        }
    }

    private static void syncCoreHitbox(ServerLevel level, ChiefAuditorBossEntity boss) {
        Vec3 corePos = computeCoreInteractionPos(boss);

        Interaction core = null;
        if (coreHitboxUuid != null) {
            Entity found = level.getEntity(coreHitboxUuid);
            if (found instanceof Interaction existing && existing.isAlive()) {
                core = existing;
            }
        }

        // Если сущность перезагружалась с чанком — ищем по тегу прежде чем создавать дубликат
        if (core == null) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof Interaction candidate && candidate.isAlive()
                        && candidate.entityTags().contains(CORE_HITBOX_TAG)) {
                    if (core == null) {
                        core = candidate;
                        coreHitboxUuid = candidate.getUUID();
                    } else {
                        candidate.discard();
                    }
                }
            }
        }

        if (core == null) {
            core = createInteractionEntity(level, CORE_WIDTH, CORE_HEIGHT, corePos, CORE_HITBOX_TAG);
            if (core != null) {
                level.addFreshEntity(core);
                coreHitboxUuid = core.getUUID();
            }
        } else {
            core.setPos(corePos.x, corePos.y, corePos.z);
        }
    }

    private static void tickRelays(ServerLevel level, ChiefAuditorBossEntity boss, int phase, boolean coreOpen) {
        if (ACTIVE_RELAYS.isEmpty()) {
            return;
        }

        // В фазах 3–4 (или при перегрузке/раскрытии ядра) старые реле рассеиваются
        if (phase < 2 || phase >= 3) {
            clearRelaysOnly(level);
            return;
        }

        Vec3 bossChest = computeCoreInteractionPos(boss).add(0.0, 0.65, 0.0);
        int tick = boss.tickCount;

        List<RelayAnchor> snapshot = List.copyOf(ACTIVE_RELAYS);
        for (RelayAnchor anchor : snapshot) {
            Entity entity = level.getEntity(anchor.uuid);
            if (!(entity instanceof Interaction relay) || !relay.isAlive()) {
                ACTIVE_RELAYS.remove(anchor);
                continue;
            }

            // Плавное парение
            double hoverY = anchor.basePos.y + Math.sin(tick * 0.12 + anchor.phaseOffset) * 0.28;
            relay.setPos(anchor.basePos.x, hoverY, anchor.basePos.z);

            Vec3 relayCenter = new Vec3(anchor.basePos.x, hoverY + RELAY_HEIGHT * 0.5, anchor.basePos.z);

            // Подсветка реле + луч энергии к груди Титана
            if (tick % 3 == 0) {
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        relayCenter.x, relayCenter.y, relayCenter.z,
                        2, 0.25, 0.25, 0.25, 0.01);
                level.sendParticles(ParticleTypes.END_ROD,
                        relayCenter.x, relayCenter.y, relayCenter.z,
                        1, 0.18, 0.18, 0.18, 0.02);
                spawnEnergyBeam(level, relayCenter, bossChest);
            }

            // Позволяем ломать реле также стрелами / трезубцами / крюком-кошкой при касании
            AABB hitBox = relay.getBoundingBox().inflate(0.55);
            List<Projectile> projectiles = level.getEntitiesOfClass(Projectile.class, hitBox);
            for (Projectile projectile : projectiles) {
                if (!projectile.isAlive()) {
                    continue;
                }
                ServerPlayer ownerPlayer = projectile.getOwner() instanceof ServerPlayer sp ? sp : null;
                if (projectile instanceof AbstractArrow) {
                    projectile.discard();
                }
                destroyRelay(level, relay, ownerPlayer);
                break;
            }
        }
    }

    private static void destroyRelay(ServerLevel level, Entity relayEntity, ServerPlayer breaker) {
        Vec3 pos = relayEntity.position().add(0.0, RELAY_HEIGHT * 0.5, 0.0);
        UUID brokenId = relayEntity.getUUID();
        relayEntity.discard();
        ACTIVE_RELAYS.removeIf(anchor -> anchor.uuid.equals(brokenId));

        level.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 2, 0.2, 0.2, 0.2, 0.01);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y, pos.z, 18, 0.45, 0.45, 0.45, 0.12);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.3F, 0.85F);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 1.1F, 1.35F);

        int remaining = getActiveRelayCount(level);
        ChiefAuditorBossEntity boss = ChiefAuditorBossManager.getActiveBoss(level.getServer());

        if (remaining > 0) {
            if (breaker != null) {
                breaker.sendOverlayMessage(Component.literal(
                        "⚡ Энерго-Реле уничтожено! Осталось: " + remaining
                ).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
            }
            return;
        }

        // Все реле уничтожены -> перегрузка щита и принудительное раскрытие ядра на 6.5 сек!
        if (boss != null && boss.isAlive()) {
            boss.triggerCoreOverload();
            Vec3 chest = computeCoreInteractionPos(boss).add(0.0, 0.65, 0.0);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, chest.x, chest.y, chest.z, 32, 0.7, 0.7, 0.7, 0.2);
            level.playSound(null, chest.x, chest.y, chest.z,
                    SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.3F, 1.2F);

            ChiefAuditorBossManager.forceOpenCore(level, boss, RELAY_OVERLOAD_OPEN_TICKS);
        }

        if (breaker != null) {
            breaker.sendOverlayMessage(Component.literal(
                    "⚡ ЩИТ ПЕРЕГРУЖЕН! Ядро открыто на 6.5с — бейте в грудь!"
            ).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        }
    }

    private static void spawnEnergyBeam(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 diff = to.subtract(from);
        double len = diff.length();
        if (len < 0.2) {
            return;
        }
        int steps = Mth.clamp((int) Math.ceil(len / 0.85), 4, 24);
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / (double) steps;
            level.sendParticles(RELAY_BEAM_DUST,
                    from.x + diff.x * t,
                    from.y + diff.y * t,
                    from.z + diff.z * t,
                    1, 0.01, 0.01, 0.01, 0.0);
        }
    }

    private static Vec3 computeCoreInteractionPos(ChiefAuditorBossEntity boss) {
        float yawRad = boss.yBodyRot * Mth.DEG_TO_RAD;
        double forwardX = -Mth.sin(yawRad) * CORE_FORWARD_OFFSET;
        double forwardZ = Mth.cos(yawRad) * CORE_FORWARD_OFFSET;
        return new Vec3(
                boss.getX() + forwardX,
                boss.getY() + CORE_BASE_Y_OFFSET,
                boss.getZ() + forwardZ
        );
    }

    private static Interaction createInteractionEntity(
            ServerLevel level,
            float width,
            float height,
            Vec3 pos,
            String tagName
    ) {
        Interaction interaction = EntityTypes.INTERACTION.create(level, EntitySpawnReason.EVENT);
        if (interaction == null) {
            return null;
        }

        CompoundTag nbt = new CompoundTag();
        nbt.putFloat("width", width);
        nbt.putFloat("height", height);
        nbt.putBoolean("response", true);

        ValueInput valueInput = TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), nbt);
        interaction.load(valueInput);
        interaction.setPos(pos.x, pos.y, pos.z);
        interaction.addTag(tagName);
        return interaction;
    }

    private static void clearRelaysOnly(ServerLevel level) {
        ACTIVE_RELAYS.clear();
        if (level == null) {
            return;
        }
        for (Entity entity : level.getAllEntities()) {
            if (entity.entityTags().contains(RELAY_HITBOX_TAG)) {
                entity.discard();
            }
        }
    }

    private static void cleanupLevel(ServerLevel level) {
        coreHitboxUuid = null;
        ACTIVE_RELAYS.clear();
        removeAllTaggedInLevel(level);
    }

    private static void removeAllTaggedInLevel(ServerLevel level) {
        if (level == null) {
            return;
        }
        for (Entity entity : level.getAllEntities()) {
            if (entity.entityTags().contains(CORE_HITBOX_TAG) || entity.entityTags().contains(RELAY_HITBOX_TAG)) {
                entity.discard();
            }
        }
    }

    private record RelayAnchor(UUID uuid, Vec3 basePos, double phaseOffset) {
    }
}