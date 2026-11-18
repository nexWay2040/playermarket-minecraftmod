package com.playermarket.story;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Механика «Жадность и выдержка» (Risk / Reward) для 3D Аирдропа и Банкомата.
 * <p>
 * Чем дольше игрок не забирает контейнер и отбивает волны охраны Corp, тем жирнее куш CR:
 * <ul>
 *     <li>0–29с: x1.0</li>
 *     <li>30–59с: x1.5</li>
 *     <li>60–89с: x2.0</li>
 *     <li>90с+: x3.0 (максимум)</li>
 * </ul>
 * Каждые 30 секунд (600 тиков) сигнализирует о спавне волны охранников (Vindicator + Pillager).
 */
public final class AirdropGreedManager {

    public static final int TICKS_PER_SECOND = 20;
    public static final int WAVE_INTERVAL_SECONDS = 30;
    public static final int WAVE_INTERVAL_TICKS = WAVE_INTERVAL_SECONDS * TICKS_PER_SECOND;

    private final Map<UUID, ContainerGreedState> activeContainers = new HashMap<>();

    /**
     * Явная регистрация нового 3D-контейнера (аирдропа или банкомата).
     */
    public void registerContainer(UUID containerId) {
        if (containerId == null) {
            return;
        }
        activeContainers.putIfAbsent(containerId, new ContainerGreedState());
    }

    /**
     * Тикает таймер созревания конкретного контейнера (1 раз за серверный тик).
     */
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
        int vindicators = 0;
        int pillagers = 0;

        // Волна каждые 30 секунд (на 30с, 60с, 90с, 120с...)
        if (state.elapsedTicks > 0 && state.elapsedTicks % WAVE_INTERVAL_TICKS == 0) {
            state.spawnedWaves++;
            spawnWave = true;
            waveNumber = state.spawnedWaves;
            vindicators = computeVindicatorCount(waveNumber);
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
                vindicators,
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
            return GreedTier.BASE;
        }
        ContainerGreedState state = activeContainers.get(containerId);
        if (state == null) {
            return GreedTier.BASE;
        }
        return resolveTier(state.elapsedTicks / TICKS_PER_SECOND);
    }

    public int getElapsedSeconds(UUID containerId) {
        ContainerGreedState state = activeContainers.get(containerId);
        return state == null ? 0 : state.elapsedTicks / TICKS_PER_SECOND;
    }

    /**
     * Короткое название тира для 3D-подписи над ящиком / банкоматом.
     */
    public String getTierTitle(UUID containerId) {
        return getTier(containerId).displayTitle;
    }

    /**
     * Готовая цветная строка Component для рендера 3D-голограммы над контейнером.
     */
    public Component getFloatingLabel(UUID containerId) {
        int elapsedSeconds = getElapsedSeconds(containerId);
        GreedTier tier = resolveTier(elapsedSeconds);
        int toNext = computeSecondsToNextTier(elapsedSeconds);

        if (toNext <= 0) {
            return Component.literal(tier.displayTitle + " [x3.0 МАКС]")
                    .withStyle(tier.color, ChatFormatting.BOLD);
        }
        return Component.literal(tier.displayTitle + " [" + formatMultiplier(tier.multiplier) + "] • до апгрейда " + toNext + "с")
                .withStyle(tier.color, ChatFormatting.BOLD);
    }

    /**
     * Забирает финальный снимок жадности при вскрытии ящика/банкомата и удаляет трекер из памяти.
     */
    public GreedSnapshot claimAndRemove(UUID containerId) {
        if (containerId == null) {
            return new GreedSnapshot(0, GreedTier.BASE, 0);
        }
        ContainerGreedState removed = activeContainers.remove(containerId);
        if (removed == null) {
            return new GreedSnapshot(0, GreedTier.BASE, 0);
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
     * Готовый спавнер волны охранников Corp (Vindicator + Pillager) вокруг контейнера.
     * Можно вызывать напрямую, когда {@link GreedTickResult#shouldSpawnGuardWave()} == true.
     */
    public List<Mob> spawnGuardWave(
            ServerLevel level,
            BlockPos centerPos,
            int vindicatorCount,
            int pillagerCount,
            LivingEntity initialTarget
    ) {
        List<Mob> spawned = new ArrayList<>(vindicatorCount + pillagerCount);
        if (level == null || centerPos == null) {
            return spawned;
        }

        RandomSource random = level.getRandom();
        for (int i = 0; i < vindicatorCount; i++) {
            BlockPos spawnPos = findSafeGuardSpawnPos(level, centerPos, random);
            var enforcer = com.playermarket.entity.ModEntities.COLLECTOR_ENFORCER.spawn(level, spawnPos, EntitySpawnReason.EVENT);
            if (enforcer != null) {
                enforcer.markAsRaidEnforcer(Math.max(1, vindicatorCount));
                enforcer.setCustomName(Component.literal("Силовик Corp [Штурмовик]").withStyle(ChatFormatting.RED));
                enforcer.setCustomNameVisible(true);
                if (initialTarget != null && initialTarget.isAlive()) {
                    enforcer.setTarget(initialTarget);
                }
                spawned.add(enforcer);
            }
        }

        for (int i = 0; i < pillagerCount; i++) {
            BlockPos spawnPos = findSafeGuardSpawnPos(level, centerPos, random);
            Pillager pillager = EntityTypes.PILLAGER.spawn(level, spawnPos, EntitySpawnReason.EVENT);
            if (pillager != null) {
                pillager.setCustomName(Component.literal("Охрана Corp [Стрелок]").withStyle(ChatFormatting.GOLD));
                if (initialTarget != null && initialTarget.isAlive()) {
                    pillager.setTarget(initialTarget);
                }
                spawned.add(pillager);
            }
        }

        if (!spawned.isEmpty()) {
            level.playSound(null, centerPos, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.4F, 1.0F);
        }
        return spawned;
    }

    public static GreedTier resolveTier(int elapsedSeconds) {
        if (elapsedSeconds >= 90) {
            return GreedTier.MAX_GREED;
        }
        if (elapsedSeconds >= 60) {
            return GreedTier.GOLDEN;
        }
        if (elapsedSeconds >= 30) {
            return GreedTier.WARM;
        }
        return GreedTier.BASE;
    }

    private static int computeSecondsToNextTier(int elapsedSeconds) {
        if (elapsedSeconds < 30) {
            return 30 - elapsedSeconds;
        }
        if (elapsedSeconds < 60) {
            return 60 - elapsedSeconds;
        }
        if (elapsedSeconds < 90) {
            return 90 - elapsedSeconds;
        }
        return 0;
    }

    private static int computeVindicatorCount(int waveNumber) {
        return switch (waveNumber) {
            case 1 -> 1;
            case 2 -> 2;
            default -> Math.min(4, 1 + waveNumber);
        };
    }

    private static int computePillagerCount(int waveNumber) {
        return switch (waveNumber) {
            case 1 -> 2;
            case 2 -> 2;
            default -> Math.min(4, waveNumber);
        };
    }

    private static BlockPos findSafeGuardSpawnPos(ServerLevel level, BlockPos center, RandomSource random) {
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = random.nextDouble() * (Math.PI * 2.0);
            double radius = 4.5 + random.nextDouble() * 4.5;
            int x = center.getX() + (int) Math.round(Math.cos(angle) * radius);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * radius);

            // Сначала ищем свободное место на высоте контейнера (для подземелий/зданий)
            for (int dy = -2; dy <= 4; dy++) {
                BlockPos candidate = new BlockPos(x, center.getY() + dy, z);
                if (isTwoBlockHeadroom(level, candidate)) {
                    return candidate;
                }
            }

            // Иначе берём поверхность по heightmap
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
        return String.format(java.util.Locale.ROOT, "x%.1f", multiplier);
    }

    public enum GreedTier {
        BASE(1.0, "БАЗОВЫЙ КУШ", ChatFormatting.GRAY),
        WARM(1.5, "ОХРАНА В ПУТИ", ChatFormatting.YELLOW),
        GOLDEN(2.0, "ЗОЛОТОЙ СЕЙФ", ChatFormatting.GOLD),
        MAX_GREED(3.0, "ДЖЕКПОТ ЖАДНОСТИ", ChatFormatting.RED);

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
            int vindicatorCount,
            int pillagerCount,
            int secondsToNextTier
    ) {
        public static final GreedTickResult EMPTY = new GreedTickResult(
                0, 0, 1.0, GreedTier.BASE, false, false, 0, 0, 0, 30
        );
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