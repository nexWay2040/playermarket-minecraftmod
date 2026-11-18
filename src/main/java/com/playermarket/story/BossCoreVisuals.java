package com.playermarket.story;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Procedural Boss Core Visuals for Chief Auditor Corp // Liquidator 17.4.
 * Coordinates dynamic energy pulsing, orbiting electric sparks, and staggered core overload.
 * Designed following lead architect specifications for Minecraft 26.3.
 */
public final class BossCoreVisuals {

    private static final DustParticleOptions CORE_CYAN_PULSE = new DustParticleOptions(0xFF06B6D4, 1.4f);
    private static final DustParticleOptions CORE_OVERHEAT_AMBER = new DustParticleOptions(0xFFF59E0B, 2.2f);
    private static final DustParticleOptions CORE_CRITICAL_RED = new DustParticleOptions(0xFFEF4444, 2.5f);

    private BossCoreVisuals() {}

    /**
     * Ticks procedural particles and audio cues for the Auditor's chest reactor core.
     */
    public static void tick(ServerLevel level, LivingEntity boss, ChiefAuditorBossManager.State state, int stateTimerTicks, boolean phase2) {
        if (level == null || boss == null || !boss.isAlive()) return;

        // Dynamic chest height (approx 62% of entity bounding box height)
        double chestY = boss.getY() + Math.max(2.4, boss.getBbHeight() * 0.62);
        float yRotRad = (float) Math.toRadians(-boss.getYRot());
        // Forward offset vector from chest
        double fwdX = Math.sin(yRotRad) * 1.15;
        double fwdZ = Math.cos(yRotRad) * 1.15;
        double coreX = boss.getX() + fwdX;
        double coreZ = boss.getZ() + fwdZ;

        switch (state) {
            case COMBAT -> {
                // Calm corporate reactor hum (soft cyan flame and occasional spark)
                if (stateTimerTicks % 6 == 0) {
                    level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            coreX, chestY, coreZ,
                            phase2 ? 3 : 1, 0.15, 0.15, 0.15, 0.01);
                }
                if (stateTimerTicks % 12 == 0) {
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                            coreX, chestY, coreZ, 1, 0.2, 0.2, 0.2, 0.02);
                }
                if (phase2 && stateTimerTicks % 20 == 0) {
                    level.sendParticles(CORE_CYAN_PULSE,
                            coreX, chestY, coreZ, 2, 0.25, 0.25, 0.25, 0.0);
                }
            }

            case SKYFALL_TELEGRAPH -> {
                // Core superheating prior to detonation: red critical glow and violent sparks
                level.sendParticles(CORE_CRITICAL_RED, coreX, chestY, coreZ, 4, 0.3, 0.3, 0.3, 0.0);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, coreX, chestY, coreZ, 3, 0.35, 0.35, 0.35, 0.08);

                if (stateTimerTicks % 8 == 0) {
                    level.playSound(null, coreX, chestY, coreZ,
                            SoundEvents.REDSTONE_TORCH_BURNOUT, SoundSource.HOSTILE, 1.2f, 1.8f);
                }
            }

            case STAGGER -> {
                // Exposed Staggered Core: Core is thrust forward (+0.4m), 8 orbiting electric sparks, End Rod aura
                double exposedX = boss.getX() + Math.sin(yRotRad) * 1.25;
                double exposedZ = boss.getZ() + Math.cos(yRotRad) * 1.25;

                // Orbiting sparks around exposed core
                double orbitRadius = 0.95;
                int sparkPoints = 8;
                double baseAngle = (stateTimerTicks * 0.22);
                for (int i = 0; i < sparkPoints; i++) {
                    double angle = baseAngle + (2 * Math.PI / sparkPoints) * i;
                    double ox = exposedX + Math.cos(angle) * orbitRadius;
                    double oz = exposedZ + Math.sin(angle) * orbitRadius;
                    double oy = chestY + Math.sin(angle * 2.0) * 0.25;

                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, ox, oy, oz, 1, 0.0, 0.0, 0.0, 0.01);
                }

                // Core overheat amber fire and End Rod vertical radiance
                level.sendParticles(CORE_OVERHEAT_AMBER, exposedX, chestY, exposedZ, 5, 0.2, 0.2, 0.2, 0.0);
                level.sendParticles(ParticleTypes.END_ROD, exposedX, chestY, exposedZ, 2, 0.1, 0.3, 0.1, 0.02);
                level.sendParticles(ParticleTypes.FLAME, exposedX, chestY, exposedZ, 3, 0.15, 0.15, 0.15, 0.02);

                if (stateTimerTicks == 1) {
                    // Stagger breach sound cue
                    level.playSound(null, exposedX, chestY, exposedZ,
                            SoundEvents.SHIELD_BREAK, SoundSource.HOSTILE, 2.0f, 0.6f);
                    level.playSound(null, exposedX, chestY, exposedZ,
                            SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.HOSTILE, 1.6f, 1.4f);
                }
            }

            default -> {}
        }
    }
}
