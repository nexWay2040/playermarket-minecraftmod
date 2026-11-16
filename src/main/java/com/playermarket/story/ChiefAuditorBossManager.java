package com.playermarket.story;

import com.playermarket.PlayerMarketMod;
import com.playermarket.item.MarketAccessCardItem;
import com.playermarket.market.MarketAttributeApplier;
import com.playermarket.market.MarketSavedData;
import com.playermarket.market.PlayerMarketAccount;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public final class ChiefAuditorBossManager {

    public enum State {
        INACTIVE,
        COMBAT,
        SKYFALL_TELEGRAPH,
        STAGGER
    }

    private static final DustParticleOptions RED_TELEGRAPH_DUST = new DustParticleOptions(0xFFFF1111, 2.2f);
    private static final DustParticleOptions AMBER_CORE_DUST = new DustParticleOptions(0xFFFBBF24, 2.0f);

    private static State activeState = State.INACTIVE;
    private static UUID bossUuid = null;
    private static ServerBossEvent bossBar = null;
    private static int stateTimerTicks = 0;
    private static int skyfallCooldownTicks = 320; // Every 16 seconds
    private static int barrageCooldownTicks = 200; // Every 10 seconds
    private static boolean phase2Triggered = false;
    private static final List<Vec3> activeBarrageMarks = new ArrayList<>();
    private static int barrageTimerTicks = 0;

    private ChiefAuditorBossManager() {}

    public static boolean isAuditorBoss(net.minecraft.world.entity.Entity entity) {
        return entity != null && entity.entityTags().contains("playermarket_chief_auditor");
    }

    public static boolean isCoreStaggered(net.minecraft.world.entity.Entity entity) {
        return isAuditorBoss(entity) && activeState == State.STAGGER;
    }

    public static boolean isBattleActive() {
        return activeState != State.INACTIVE && bossUuid != null;
    }

    public static void startBossBattle(MinecraftServer server, ServerPlayer triggerPlayer) {
        if (server == null || triggerPlayer == null) return;
        ServerLevel level = (ServerLevel) triggerPlayer.level();

        // Remove old boss if any exists
        clearBoss(server);

        BlockPos playerPos = triggerPlayer.blockPosition();
        BlockPos spawnPos = playerPos.offset(8, 0, 8);
        int topY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawnPos.getX(), spawnPos.getZ());
        spawnPos = new BlockPos(spawnPos.getX(), Math.max(playerPos.getY(), topY), spawnPos.getZ());

        IronGolem golem = EntityTypes.IRON_GOLEM.create(level, EntitySpawnReason.EVENT);
        if (golem == null) return;

        golem.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
        golem.setCustomName(Component.literal("§4§lГЛАВНЫЙ АУДИТОР CORP §8// §cЛИКВИДАТОР 17.4"));
        golem.setCustomNameVisible(true);
        golem.setPersistenceRequired();
        golem.addTag("playermarket_boss");
        golem.addTag("playermarket_chief_auditor");

        // Scale up to 2.8x (Titan size ~8 blocks tall!)
        var scaleAttr = golem.getAttribute(Attributes.SCALE);
        if (scaleAttr != null) {
            scaleAttr.setBaseValue(2.8);
        }

        double maxHealth = 1200.0;
        var hpAttr = golem.getAttribute(Attributes.MAX_HEALTH);
        if (hpAttr != null) {
            hpAttr.setBaseValue(maxHealth);
            golem.setHealth((float) maxHealth);
        }

        var dmgAttr = golem.getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmgAttr != null) dmgAttr.setBaseValue(22.0);

        var speedAttr = golem.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) speedAttr.setBaseValue(0.28);

        var kbAttr = golem.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kbAttr != null) kbAttr.setBaseValue(1.0);

        var followAttr = golem.getAttribute(Attributes.FOLLOW_RANGE);
        if (followAttr != null) followAttr.setBaseValue(64.0);

        golem.addEffect(new MobEffectInstance(MobEffects.GLOWING, 999999, 0, false, false));
        golem.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 999999, 0, false, false));
        golem.setTarget(triggerPlayer);

        level.addFreshEntity(golem);
        bossUuid = golem.getUUID();
        activeState = State.COMBAT;
        stateTimerTicks = 0;
        skyfallCooldownTicks = 280;
        barrageCooldownTicks = 180;
        phase2Triggered = false;
        activeBarrageMarks.clear();
        barrageTimerTicks = 0;

        // Init Boss Bar
        bossBar = new ServerBossEvent(
                UUID.randomUUID(),
                Component.literal("§4§lГЛАВНЫЙ АУДИТОР CORP §8// §cЛИКВИДАТОР 17.4"),
                BossEvent.BossBarColor.RED,
                BossEvent.BossBarOverlay.NOTCHED_10
        );
        bossBar.setProgress(1.0f);

        // Dramatic intro broadcast and sirens
        level.playSound(null, spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(),
                SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 3.0f, 0.8f);
        level.playSound(null, spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(),
                SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 2.0f, 0.7f);

        server.getPlayerList().broadcastSystemMessage(Component.literal(
                "\n§4§l========================================================\n"
                        + "§c§l[ТРЕВОГА CORP] §4ФИНАЛЬНЫЙ АУДИТОР ПРИБЫЛ ДЛЯ ЛИКВИДАЦИИ!\n"
                        + "§eСтатья 17.4 вступила в силу. Все биологические залоги аннулируются.\n"
                        + "§6Используйте Крюк-Кошку для выживания при Разрывах Почвы!\n"
                        + "§4§l========================================================\n"), false);
    }

    public static void clearBoss(MinecraftServer server) {
        if (bossBar != null) {
            bossBar.removeAllPlayers();
            bossBar = null;
        }
        if (bossUuid != null && server != null) {
            for (ServerLevel sl : server.getAllLevels()) {
                net.minecraft.world.entity.Entity e = sl.getEntity(bossUuid);
                if (e != null) {
                    e.discard();
                }
            }
        }
        bossUuid = null;
        activeState = State.INACTIVE;
        stateTimerTicks = 0;
        phase2Triggered = false;
        activeBarrageMarks.clear();
    }

    public static void tickServer(MinecraftServer server) {
        if (activeState == State.INACTIVE || bossUuid == null || server == null) return;

        IronGolem boss = null;
        ServerLevel bossLevel = null;
        for (ServerLevel sl : server.getAllLevels()) {
            net.minecraft.world.entity.Entity e = sl.getEntity(bossUuid);
            if (e instanceof IronGolem ig) {
                boss = ig;
                bossLevel = sl;
                break;
            }
        }

        if (boss == null || !boss.isAlive()) {
            // Boss died or removed
            if (boss != null && !boss.isAlive()) {
                onBossDefeated(server, bossLevel, boss.position());
            } else {
                clearBoss(server);
            }
            return;
        }

        // Update BossBar
        float hpRatio = Math.max(0.0f, Math.min(1.0f, boss.getHealth() / boss.getMaxHealth()));
        if (bossBar != null) {
            bossBar.setProgress(hpRatio);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.level() == bossLevel && player.distanceToSqr(boss) < 10000.0) {
                    bossBar.addPlayer(player);
                } else {
                    bossBar.removePlayer(player);
                }
            }
        }

        // Phase 2 check (< 50% HP)
        if (!phase2Triggered && hpRatio <= 0.50f) {
            phase2Triggered = true;
            bossLevel.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 2.5f, 0.7f);
            server.getPlayerList().broadcastSystemMessage(Component.literal(
                    "§4§l[ПРОТОКОЛ БАНКРОТСТВА] §cАудитор вошёл в режим Overdrive! Скорость повышена, вызвана охрана!"), false);
            boss.addEffect(new MobEffectInstance(MobEffects.SPEED, 999999, 1, false, false));

            // Spawn 2 Elite Vindicators
            for (int i = 0; i < 2; i++) {
                Vindicator v = EntityTypes.VINDICATOR.create(bossLevel, EntitySpawnReason.EVENT);
                if (v != null) {
                    v.setPos(boss.getX() + (i == 0 ? 3.0 : -3.0), boss.getY(), boss.getZ() + 2.0);
                    v.setCustomName(Component.literal("§c§lОХРАНА ЛИКВИДАТОРА CORP"));
                    v.setCustomNameVisible(true);
                    v.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
                    v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_AXE));
                    bossLevel.addFreshEntity(v);
                }
            }
        }

        // Combat target selection
        ServerPlayer nearestPlayer = null;
        double nearestDistSq = Double.MAX_VALUE;
        for (ServerPlayer p : bossLevel.players()) {
            if (p.isAlive() && !p.isCreative() && !p.isSpectator()) {
                double d = p.distanceToSqr(boss);
                if (d < nearestDistSq) {
                    nearestDistSq = d;
                    nearestPlayer = p;
                }
            }
        }
        if (nearestPlayer != null && (boss.getTarget() == null || !boss.getTarget().isAlive())) {
            boss.setTarget(nearestPlayer);
        }

        stateTimerTicks++;

        switch (activeState) {
            case COMBAT -> {
                skyfallCooldownTicks--;
                barrageCooldownTicks--;

                // Ambient boss steps & particles
                if (stateTimerTicks % 15 == 0) {
                    bossLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                            boss.getX(), boss.getY() + 0.5, boss.getZ(), 4, 1.2, 0.2, 1.2, 0.02);
                }

                // Check Skyfall trigger
                if (skyfallCooldownTicks <= 0) {
                    activeState = State.SKYFALL_TELEGRAPH;
                    stateTimerTicks = 0;
                    boss.setDeltaMovement(0, 0, 0);

                    bossLevel.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                            SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 2.0f, 0.7f);

                    for (ServerPlayer sp : bossLevel.players()) {
                        if (sp.distanceToSqr(boss) < 3600.0) {
                            sp.sendSystemMessage(Component.literal(
                                    "§4§l⚠ РАЗРЫВ ПОЧВЫ ЧЕРЕЗ 2 СЕК! ЦЕПЛЯЙТЕСЬ КРЮК-КОШКОЙ ЗА ДЕРЕВЬЯ/ЗЕМЛЮ! ⚠"));
                        }
                    }
                } else if (barrageCooldownTicks <= 0 && activeBarrageMarks.isEmpty()) {
                    // Trigger Orbital Barrage
                    barrageCooldownTicks = phase2Triggered ? 140 : 220;
                    barrageTimerTicks = 35; // 1.75 sec telegraph
                    activeBarrageMarks.clear();
                    for (ServerPlayer p : bossLevel.players()) {
                        if (p.distanceToSqr(boss) < 2500.0) {
                            activeBarrageMarks.add(p.position());
                            activeBarrageMarks.add(p.position().add((Math.random() - 0.5) * 8.0, 0.0, (Math.random() - 0.5) * 8.0));
                        }
                    }
                }

                // Tick Active Barrage
                if (!activeBarrageMarks.isEmpty()) {
                    barrageTimerTicks--;
                    for (Vec3 mark : activeBarrageMarks) {
                        bossLevel.sendParticles(RED_TELEGRAPH_DUST, mark.x, mark.y + 0.2, mark.z, 6, 0.8, 0.1, 0.8, 0.0);
                        bossLevel.sendParticles(ParticleTypes.SMOKE, mark.x, mark.y + 0.2, mark.z, 2, 0.4, 0.1, 0.4, 0.02);
                    }
                    if (barrageTimerTicks <= 0) {
                        for (Vec3 mark : activeBarrageMarks) {
                            bossLevel.playSound(null, mark.x, mark.y, mark.z,
                                    SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.5f, 1.2f);
                            bossLevel.sendParticles(ParticleTypes.EXPLOSION, mark.x, mark.y + 0.5, mark.z, 5, 0.3, 0.3, 0.3, 0.0);

                            for (ServerPlayer sp : bossLevel.players()) {
                                if (sp.position().distanceToSqr(mark) <= 16.0) {
                                    sp.hurtServer(bossLevel, bossLevel.damageSources().mobAttack(boss), 9.0f);
                                }
                            }
                        }
                        activeBarrageMarks.clear();
                    }
                }
            }

            case SKYFALL_TELEGRAPH -> {
                boss.setDeltaMovement(0, 0, 0);

                // Sound telegraphs
                if (stateTimerTicks == 15 || stateTimerTicks == 30) {
                    bossLevel.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                            SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 2.0f, 0.8f);
                } else if (stateTimerTicks == 40) {
                    bossLevel.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                            SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.5f, 0.5f);
                }

                // Telegraph ring on ground (radius 18 blocks)
                double radius = 18.0;
                int points = 32;
                for (int i = 0; i < points; i++) {
                    double angle = (2 * Math.PI / points) * i;
                    double px = boss.getX() + Math.cos(angle) * radius;
                    double pz = boss.getZ() + Math.sin(angle) * radius;
                    bossLevel.sendParticles(RED_TELEGRAPH_DUST, px, boss.getY() + 0.2, pz, 1, 0, 0, 0, 0);
                }
                bossLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        boss.getX(), boss.getY() + 0.2, boss.getZ(), 8, 2.5, 0.5, 2.5, 0.05);

                // DETONATION at tick 50 (2.5 seconds)
                if (stateTimerTicks >= 50) {
                    bossLevel.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                            SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3.0f, 0.6f);
                    bossLevel.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                            SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 2.5f, 0.8f);

                    // Explosion shockwave particles
                    bossLevel.sendParticles(ParticleTypes.EXPLOSION, boss.getX(), boss.getY() + 1.0, boss.getZ(), 40, 5.0, 1.0, 5.0, 0.2);

                    // Debris particles
                    bossLevel.sendParticles(ParticleTypes.CRIT, boss.getX(), boss.getY() + 1.0, boss.getZ(), 80, 8.0, 2.0, 8.0, 0.5);

                    // Launch players into the stratosphere (25-35 blocks high!)
                    for (ServerPlayer sp : bossLevel.players()) {
                        if (sp.isAlive() && !sp.isCreative() && !sp.isSpectator() && sp.distanceToSqr(boss) <= 1225.0) { // within 35 blocks
                            Vec3 cur = sp.getDeltaMovement();
                            sp.setDeltaMovement(cur.x * 0.2, 2.45, cur.z * 0.2);
                            sp.needsSync = true;
                            sp.sendSystemMessage(Component.literal(
                                    "§c§l[ШОКОВАЯ ВОЛНА!] §eВы в воздухе! Цепляйтесь Крюком-Кошкой за дерево/землю, иначе падение смертельно!"));
                        }
                    }

                    // Enter Stagger Phase for 5 seconds (100 ticks)
                    activeState = State.STAGGER;
                    stateTimerTicks = 0;
                    for (ServerPlayer sp : bossLevel.players()) {
                        if (sp.distanceToSqr(boss) < 3600.0) {
                            sp.sendSystemMessage(Component.literal(
                                    "§a§l[ЯДРО АУДИТОРА ПЕРЕГРЕТО!] §eУязвимость! Наносите сокрушающий урон по ядру (+200%)!"));
                        }
                    }
                }
            }

            case STAGGER -> {
                boss.setDeltaMovement(0, 0, 0);

                // Core sparks & scrape particles
                bossLevel.sendParticles(AMBER_CORE_DUST, boss.getX(), boss.getY() + 2.5, boss.getZ(), 6, 0.5, 0.5, 0.5, 0.0);
                bossLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, boss.getX(), boss.getY() + 2.5, boss.getZ(), 4, 0.4, 0.4, 0.4, 0.05);

                if (stateTimerTicks % 20 == 0) {
                    bossLevel.playSound(null, boss.getX(), boss.getY() + 2.0, boss.getZ(),
                            SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 1.2f, 1.5f);
                }

                // Stagger ends after 100 ticks (5 seconds)
                if (stateTimerTicks >= 100) {
                    activeState = State.COMBAT;
                    stateTimerTicks = 0;
                    skyfallCooldownTicks = phase2Triggered ? 240 : 320;
                    bossLevel.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                            SoundEvents.IRON_GOLEM_REPAIR, SoundSource.HOSTILE, 1.5f, 0.8f);
                    for (ServerPlayer sp : bossLevel.players()) {
                        if (sp.distanceToSqr(boss) < 3600.0) {
                            sp.sendSystemMessage(Component.literal("§c§l[ЯДРО ЗАЩИЩЕНО] §7Бронеплиты Аудитора сомкнулись!"));
                        }
                    }
                }
            }
        }
    }

    private static void onBossDefeated(MinecraftServer server, ServerLevel level, Vec3 deathPos) {
        if (bossBar != null) {
            bossBar.removeAllPlayers();
            bossBar = null;
        }
        activeState = State.INACTIVE;
        bossUuid = null;

        if (level != null && deathPos != null) {
            // Victory fireworks & sound
            level.playSound(null, deathPos.x, deathPos.y, deathPos.z,
                    SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 2.0f, 1.0f);
            level.playSound(null, deathPos.x, deathPos.y, deathPos.z,
                    SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 2.0f, 1.2f);

            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, deathPos.x, deathPos.y + 1.0, deathPos.z, 3, 0.5, 0.5, 0.5, 0.0);
            level.sendParticles(ParticleTypes.FIREWORK, deathPos.x, deathPos.y + 2.0, deathPos.z, 60, 2.0, 2.0, 2.0, 0.2);

            // Ultimate drops: 16 Diamonds, Netherite Ingot, 2 Golden Apples
            dropItem(level, deathPos, new ItemStack(Items.DIAMOND, 16));
            dropItem(level, deathPos, new ItemStack(Items.NETHERITE_INGOT, 1));
            dropItem(level, deathPos, new ItemStack(Items.GOLDEN_APPLE, 2));

            // Grand Victory Chat Announcement
            server.getPlayerList().broadcastSystemMessage(Component.literal(
                    "\n§6§l========================================================\n"
                            + "§e§l              ПОБЕДА НАД КОРПОРАЦИЕЙ!\n"
                            + "§a  Главный Аудитор Corp (Ликвидатор 17.4) повержен!\n"
                            + "§f  Все биологические и финансовые долги аннулированы навсегда.\n"
                            + "§b  Вы свободны от рабского контракта!\n"
                            + "§6§l========================================================\n"), false);

            // Restore all sold stats and freedom for all players
            MarketSavedData data = MarketSavedData.get(server);
            for (PlayerMarketAccount acc : data.getAccountsView().values()) {
                acc.getPermanentSoldFractions().clear();
                acc.getActiveLeases().clear();
                acc.setMarketCardBlocked(false);
                acc.setPermanentImpaired(false);
            }
            data.setDirty();

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                MarketAttributeApplier.syncPlayerModifiers(player);
                player.removeEffect(MobEffects.BLINDNESS);
                player.removeEffect(MobEffects.SLOWNESS);
            }
        }
    }

    private static void dropItem(ServerLevel level, Vec3 pos, ItemStack stack) {
        net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(
                level, pos.x, pos.y + 0.5, pos.z, stack);
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
    }
}
