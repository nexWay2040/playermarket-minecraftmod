package com.playermarket.story;

import com.playermarket.entity.CollectorEnforcerEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Плавная и честная механика «Жадность и выдержка» (Risk / Reward) для 3D-аирдропов и банкоматов.
 * <p>
 * Тиры созревания (Rework 2.0):
 * <ul>
 *     <li>0–44с: {@code x1.0} (SECURE — базовая награда гарантирована)</li>
 *     <li>45–89с: {@code x1.5} (WARM)</li>
 *     <li>90–134с: {@code x2.0} (GOLDEN)</li>
 *     <li>135с+: {@code x3.0} (OVERDRIVE)</li>
 * </ul>
 * Новая волна охраны приходит каждые 45 секунд (900 тиков).
 */
public final class AirdropGreedManager {

    public static final int TICKS_PER_SECOND = 20;
    public static final int WAVE_INTERVAL_SECONDS = 45;
    public static final int WAVE_INTERVAL_TICKS = WAVE_INTERVAL_SECONDS * TICKS_PER_SECOND;

    private static final Identifier COLLECTOR_ENFORCER_ID = Identifier.fromNamespaceAndPath("playermarket", "collector_enforcer");

    private static EntityType<? extends Mob> customEnforcerType = null;

    private final Map<UUID, ContainerGreedState> activeContainers = new HashMap<>();

    public static void setEnforcerEntityType(EntityType<? extends Mob> entityType) {
        customEnforcerType = entityType;
    }

    public void registerContainer(UUID containerId) {
        if (containerId == null) {
            return;
        }
        activeContainers.putIfAbsent(containerId, new ContainerGreedState());
    }

    public GreedTickResult tickContainer(UUID containerId) {
        if (containerId == null) {
            return GreedTickResult.EMPTY;
        }
        ContainerGreedState state = activeContainers.computeIfAbsent(containerId, ignored -> new ContainerGreedState());
        GreedTier previousTier = resolveTier(state.elapsedTicks / TICKS_PER_SECOND);

        state.elapsedTicks++;
        int elapsedSeconds = state.elapsedTicks / TICKS_PER_SECOND;
        GreedTier currentTier = resolveTier(elapsedSeconds);
        boolean tierUpgraded = currentTier.ordinal() > previousTier.ordinal();

        boolean spawnWave = false;
        int waveNumber = state.spawnedWaves;
        int enforcers = 0;
        int pillagers = 0;

        // Волна охраны каждые 45 секунд (на 45с, 90с, 135с...)
        if (state.elapsedTicks > 0 && state.elapsedTicks % WAVE_INTERVAL_TICKS == 0) {
            state.spawnedWaves++;
            waveNumber = state.spawnedWaves;
            spawnWave = true;
            enforcers = computeEnforcerCount(waveNumber);
            pillagers = computePillagerCount(waveNumber);
        }

        return new GreedTickResult(
                state.elapsedTicks,
                elapsedSeconds,
                currentTier.multiplier,
                currentTier,
                tierUpgraded,
                spawnWave,
                waveNumber,
                enforcers,
                pillagers,
                computeSecondsToNextTier(elapsedSeconds)
        );
    }

    public double getMultiplier(UUID containerId) {
        return getTier(containerId).multiplier;
    }

    public long applyMultiplier(UUID containerId, long baseCr) {
        if (baseCr <= 0L) {
            return 0L;
        }
        return Math.round(baseCr * getMultiplier(containerId));
    }

    public GreedTier getTier(UUID containerId) {
        if (containerId == null) {
            return GreedTier.SECURE;
        }
        ContainerGreedState state = activeContainers.get(containerId);
        if (state == null) {
            return GreedTier.SECURE;
        }
        return resolveTier(state.elapsedTicks / TICKS_PER_SECOND);
    }

    public int getElapsedSeconds(UUID containerId) {
        ContainerGreedState state = activeContainers.get(containerId);
        return state == null ? 0 : state.elapsedTicks / TICKS_PER_SECOND;
    }

    public String getTierTitle(UUID containerId) {
        return getTier(containerId).displayTitle;
    }

    public Component getFloatingLabel(UUID containerId) {
        int elapsedSeconds = getElapsedSeconds(containerId);
        GreedTier tier = resolveTier(elapsedSeconds);
        int toNext = computeSecondsToNextTier(elapsedSeconds);

        if (toNext <= 0) {
            return Component.literal(tier.displayTitle + " [x3.0 OVERDRIVE]")
                    .withStyle(tier.color, ChatFormatting.BOLD);
        }
        return Component.literal(tier.displayTitle + " [" + formatMultiplier(tier.multiplier) + "] • до апгрейда " + toNext + "с")
                .withStyle(tier.color, ChatFormatting.BOLD);
    }

    public GreedSnapshot claimAndRemove(UUID containerId) {
        if (containerId == null) {
            return new GreedSnapshot(0, GreedTier.SECURE, 0);
        }
        ContainerGreedState removed = activeContainers.remove(containerId);
        if (removed == null) {
            return new GreedSnapshot(0, GreedTier.SECURE, 0);
        }
        int seconds = removed.elapsedTicks / TICKS_PER_SECOND;
        return new GreedSnapshot(seconds, resolveTier(seconds), removed.spawnedWaves);
    }

    public void removeContainer(UUID containerId) {
        if (containerId != null) {
            activeContainers.remove(containerId);
        }
    }

    public void clearAll() {
        activeContainers.clear();
    }

    /**
     * Спавнит ослабленную честную волну охраны (CollectorEnforcerEntity + Pillager) вокруг контейнера.
     */
    public List<Mob> spawnGuardWave(
            ServerLevel level,
            BlockPos centerPos,
            int enforcerCount,
            int pillagerCount,
            LivingEntity initialTarget
    ) {
        List<Mob> spawned = new ArrayList<>(Math.max(0, enforcerCount) + Math.max(0, pillagerCount));
        if (level == null || centerPos == null) {
            return spawned;
        }

        RandomSource random = level.getRandom();
        EntityType<?> resolvedEnforcerType = resolveEnforcerEntityType();
        int estimatedWaveTier = enforcerCount >= 2 ? 3 : (pillagerCount >= 2 ? 2 : 1);

        for (int i = 0; i < enforcerCount; i++) {
            BlockPos spawnPos = findSafeGuardSpawnPos(level, centerPos, random);
            Mob guard = null;
            if (resolvedEnforcerType != null) {
                var spawnedEntity = resolvedEnforcerType.spawn(level, spawnPos, EntitySpawnReason.EVENT);
                if (spawnedEntity instanceof Mob mob) {
                    guard = mob;
                }
            }
            if (guard == null) {
                guard = EntityTypes.VINDICATOR.spawn(level, spawnPos, EntitySpawnReason.EVENT);
            }
            if (guard != null) {
                guard.setCustomName(Component.literal("Охрана Corp [Силовик]").withStyle(ChatFormatting.RED));
                if (guard instanceof CollectorEnforcerEntity enforcer) {
                    enforcer.markAsRaidEnforcer(estimatedWaveTier);
                } else {
                    guard.setPersistenceRequired();
                }
                if (initialTarget != null && initialTarget.isAlive()) {
                    guard.setTarget(initialTarget);
                }
                spawned.add(guard);
            }
        }

        for (int i = 0; i < pillagerCount; i++) {
            BlockPos spawnPos = findSafeGuardSpawnPos(level, centerPos, random);
            Pillager pillager = EntityTypes.PILLAGER.spawn(level, spawnPos, EntitySpawnReason.EVENT);
            if (pillager != null) {
                pillager.setCustomName(Component.literal("Охрана Corp [Стрелок]").withStyle(ChatFormatting.GOLD));
                pillager.setPersistenceRequired();
                if (initialTarget != null && initialTarget.isAlive()) {
                    pillager.setTarget(initialTarget);
                }
                spawned.add(pillager);
            }
        }

        if (!spawned.isEmpty()) {
            level.playSound(null, centerPos, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.3F, 1.0F);
        }
        return spawned;
    }

    public static GreedTier resolveTier(int elapsedSeconds) {
        if (elapsedSeconds >= 135) {
            return GreedTier.OVERDRIVE;
        }
        if (elapsedSeconds >= 90) {
            return GreedTier.GOLDEN;
        }
        if (elapsedSeconds >= 45) {
            return GreedTier.WARM;
        }
        return GreedTier.SECURE;
    }

    private static int computeSecondsToNextTier(int elapsedSeconds) {
        if (elapsedSeconds < 45) {
            return 45 - elapsedSeconds;
        }
        if (elapsedSeconds < 90) {
            return 90 - elapsedSeconds;
        }
        if (elapsedSeconds < 135) {
            return 135 - elapsedSeconds;
        }
        return 0;
    }

    private static int computeEnforcerCount(int waveNumber) {
        // Волна 1: 1 Enforcer, Волна 2: 1 Enforcer, Волна 3+: макс 2 Enforcer
        return waveNumber >= 3 ? 2 : 1;
    }

    private static int computePillagerCount(int waveNumber) {
        // Волна 1: 1 Pillager, Волна 2: 2 Pillager, Волна 3+: макс 2 Pillager
        return waveNumber <= 1 ? 1 : 2;
    }

    private static EntityType<?> resolveEnforcerEntityType() {
        if (customEnforcerType != null) {
            return customEnforcerType;
        }
        Optional<EntityType<?>> found = BuiltInRegistries.ENTITY_TYPE.getOptional(COLLECTOR_ENFORCER_ID);
        return found.orElse(null);
    }

    private static BlockPos findSafeGuardSpawnPos(ServerLevel level, BlockPos center, RandomSource random) {
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = random.nextDouble() * (Math.PI * 2.0);
            double radius = 4.5 + random.nextDouble() * 4.5;
            int x = center.getX() + (int) Math.round(Math.cos(angle) * radius);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * radius);

            for (int dy = -2; dy <= 4; dy++) {
                BlockPos candidate = new BlockPos(x, center.getY() + dy, z);
                if (isTwoBlockHeadroom(level, candidate)) {
                    return candidate;
                }
            }

            BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, center.getY(), z));
            if (Math.abs(surface.getY() - center.getY()) <= 12) {
                return surface;
            }
        }
        return center.above();
    }

    private static boolean isTwoBlockHeadroom(ServerLevel level, BlockPos pos) {
        return !level.getBlockState(pos.below()).isAir()
                && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
    }

    private static String formatMultiplier(double multiplier) {
        if (Math.abs(multiplier - Math.rint(multiplier)) < 0.001) {
            return "x" + (int) Math.rint(multiplier) + ".0";
        }
        return String.format(Locale.ROOT, "x%.1f", multiplier);
    }

    public enum GreedTier {
        SECURE(1.0, "SECURE // ГАРАНТ", ChatFormatting.GREEN),
        WARM(1.5, "ОХРАНА В ПУТИ", ChatFormatting.YELLOW),
        GOLDEN(2.0, "ЗОЛОТОЙ КУШ", ChatFormatting.GOLD),
        OVERDRIVE(3.0, "OVERDRIVE", ChatFormatting.RED);

        private final double multiplier;
        private final String displayTitle;
        private final ChatFormatting color;

        GreedTier(double multiplier, String displayTitle, ChatFormatting color) {
            this.multiplier = multiplier;
            this.displayTitle = displayTitle;
            this.color = color;
        }

        public double getMultiplier() {
            return multiplier;
        }

        public String getDisplayTitle() {
            return displayTitle;
        }

        public ChatFormatting getColor() {
            return color;
        }
    }

    public record GreedTickResult(
            int elapsedTicks,
            int elapsedSeconds,
            double multiplier,
            GreedTier tier,
            boolean tierUpgraded,
            boolean shouldSpawnGuardWave,
            int waveNumber,
            int enforcerCount,
            int pillagerCount,
            int secondsToNextTier
    ) {
        public static final GreedTickResult EMPTY = new GreedTickResult(
                0, 0, 1.0, GreedTier.SECURE, false, false, 0, 0, 0, 45
        );

        public int vindicatorCount() {
            return enforcerCount;
        }
    }

    public record GreedSnapshot(int elapsedSeconds, GreedTier finalTier, int wavesSurvived) {
        public double multiplier() {
            return finalTier.getMultiplier();
        }
    }

    private static final class ContainerGreedState {
        int elapsedTicks = 0;
        int spawnedWaves = 0;
    }
}