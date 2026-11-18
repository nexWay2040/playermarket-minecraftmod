package com.playermarket.item;

import com.playermarket.story.ChiefAuditorBossManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Предмет «Повестка на Финальный Аудит (Статья 17.4)».
 * <p>
 * Позволяет игрокам осознанно выбрать открытую арену с деревьями/рельефом
 * для Крюка-Кошки перед призывом Главного Аудитора Corp 17.4.
 */
public class AuditSummonItem extends Item {

    private static final String BOSS_ENTITY_SIMPLE_NAME = "ChiefAuditorBossEntity";
    private static final int SUMMON_COOLDOWN_TICKS = 60; // 3 секунды защиты от спама

    private static final DustParticleOptions SUMMON_PILLAR_DUST = new DustParticleOptions(0xFF1428, 2.2F);
    private static final DustParticleOptions SUMMON_RING_DUST = new DustParticleOptions(0xFF8894, 1.35F);

    private static long lastSummonServerTick = Long.MIN_VALUE;

    public AuditSummonItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }

        MinecraftServer server = serverLevel.getServer();
        long currentServerTick = server.getTickCount();

        if (isBossAlreadyActive(server, currentServerTick)) {
            serverLevel.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                    SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.0F, 0.85F);
            serverPlayer.sendOverlayMessage(Component.literal(
                    "✖ Главный Аудитор 17.4 уже активен! Сначала завершите текущий бой."
            ).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            return InteractionResult.FAIL;
        }

        lastSummonServerTick = currentServerTick;

        Vec3 lookFlat = serverPlayer.getLookAngle().multiply(1.0, 0.0, 1.0);
        Vec3 forward = lookFlat.lengthSqr() > 1.0E-4 ? lookFlat.normalize() : new Vec3(1.0, 0.0, 0.0);
        Vec3 pillarPos = serverPlayer.position().add(forward.scale(5.0));

        spawnCrimsonSummonPillar(serverLevel, pillarPos);
        playAuditSiren(serverLevel, serverPlayer, pillarPos);

        server.getPlayerList().broadcastSystemMessage(
                Component.literal("⚠ [ПРОТОКОЛ 17.4] ")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                        .append(Component.literal(serverPlayer.getScoreboardName() + " активировал Повестку на Финальный Аудит!")
                                .withStyle(ChatFormatting.GOLD)),
                false
        );

        stack.consume(1, serverPlayer);
        serverPlayer.getCooldowns().addCooldown(stack, SUMMON_COOLDOWN_TICKS);

        ChiefAuditorBossManager.startBossBattle(server, serverPlayer);
        return InteractionResult.CONSUME;
    }

    private static boolean isBossAlreadyActive(MinecraftServer server, long currentServerTick) {
        if (lastSummonServerTick != Long.MIN_VALUE && (currentServerTick - lastSummonServerTick) < SUMMON_COOLDOWN_TICKS) {
            return true;
        }
        for (ServerLevel world : server.getAllLevels()) {
            for (Entity entity : world.getAllEntities()) {
                if (entity instanceof LivingEntity living
                        && living.isAlive()
                        && BOSS_ENTITY_SIMPLE_NAME.equals(living.getClass().getSimpleName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void spawnCrimsonSummonPillar(ServerLevel level, Vec3 basePos) {
        // Вертикальный красный столб высотой 24 блока
        for (int y = 0; y <= 24; y++) {
            double py = basePos.y + y * 0.9;
            level.sendParticles(SUMMON_PILLAR_DUST,
                    basePos.x, py, basePos.z,
                    4, 0.35, 0.25, 0.35, 0.01);
            if (y % 3 == 0) {
                level.sendParticles(ParticleTypes.FLAME,
                        basePos.x, py, basePos.z,
                        2, 0.25, 0.15, 0.25, 0.02);
            }
        }

        // Расходящееся кольцо у основания столба
        for (int i = 0; i < 24; i++) {
            double angle = (Math.PI * 2.0 * i) / 24.0;
            double rx = basePos.x + Math.cos(angle) * 3.2;
            double rz = basePos.z + Math.sin(angle) * 3.2;
            level.sendParticles(SUMMON_RING_DUST,
                    rx, basePos.y + 0.35, rz,
                    2, 0.08, 0.12, 0.08, 0.01);
        }

        level.sendParticles(ParticleTypes.LARGE_SMOKE,
                basePos.x, basePos.y + 1.0, basePos.z,
                22, 1.2, 0.6, 1.2, 0.05);
    }

    private static void playAuditSiren(ServerLevel level, ServerPlayer summoner, Vec3 pillarPos) {
        level.playSound(null, pillarPos.x, pillarPos.y, pillarPos.z,
                SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 2.2F, 0.72F);
        level.playSound(null, pillarPos.x, pillarPos.y, pillarPos.z,
                SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.8F, 0.65F);
        level.playSound(null, summoner.getX(), summoner.getY(), summoner.getZ(),
                SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.85F, 1.15F);
    }
}