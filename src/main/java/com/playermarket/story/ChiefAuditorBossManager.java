package com.playermarket.story;

import com.playermarket.entity.ChiefAuditorBossEntity;
import com.playermarket.entity.ModEntities;
import com.playermarket.market.MarketAttributeApplier;
import com.playermarket.market.MarketSavedData;
import com.playermarket.market.PlayerMarketAccount;
import com.playermarket.network.MarketNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Server-authoritative state machine for the Final Titan Boss:
 * Chief Auditor Corp // Liquidator 17.4 (Custom GeckoLib 5 Entity).
 * Uses ServerLivingEntityEvents.AFTER_DEATH for 100% reliable victory/loot/ending resolution.
 */
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
    private static final Set<UUID> HANDLED_DEFEAT_UUIDS = new HashSet<>();
    private static final BossArenaTetherController TETHER_CONTROLLER = new BossArenaTetherController();

    private ChiefAuditorBossManager() {}

    public static boolean isAuditorBoss(Entity entity) {
        if (entity == null) return false;
        return entity instanceof ChiefAuditorBossEntity
                || entity.entityTags().contains("playermarket_chief_auditor");
    }

    public static boolean isCoreStaggered(Entity entity) {
        if (!isAuditorBoss(entity)) return false;
        if (entity instanceof ChiefAuditorBossEntity boss) {
            return boss.getBossState() == ChiefAuditorBossEntity.STATE_STAGGER || activeState == State.STAGGER;
        }
        return activeState == State.STAGGER;
    }

    public static boolean isBattleActive() {
        return activeState != State.INACTIVE && bossUuid != null;
    }

    public static void startBossBattle(MinecraftServer server, ServerPlayer triggerPlayer) {
        if (server == null || triggerPlayer == null) return;
        ServerLevel level = (ServerLevel) triggerPlayer.level();

        // Remove any previous boss cleanly
        clearBoss(server);

        BlockPos playerPos = triggerPlayer.blockPosition();
        BlockPos spawnPos = playerPos.offset(8, 0, 8);
        int topY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawnPos.getX(), spawnPos.getZ());
        spawnPos = new BlockPos(spawnPos.getX(), Math.max(playerPos.getY(), topY), spawnPos.getZ());

        ChiefAuditorBossEntity boss = ModEntities.CHIEF_AUDITOR.create(level, EntitySpawnReason.EVENT);
        if (boss == null) return;

        boss.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
        boss.setCustomName(Component.literal("§4§lГЛАВНЫЙ АУДИТОР CORP §8// §cЛИКВИДАТОР 17.4"));
        boss.setCustomNameVisible(true);
        boss.setPersistenceRequired();
        boss.addTag("playermarket_boss");
        boss.addTag("playermarket_chief_auditor");
        boss.setBossState(ChiefAuditorBossEntity.STATE_COMBAT);
        boss.setPhaseTwo(false);

        double maxHealth = 1200.0;
        var hpAttr = boss.getAttribute(Attributes.MAX_HEALTH);
        if (hpAttr != null) {
            hpAttr.setBaseValue(maxHealth);
            boss.setHealth((float) maxHealth);
        }

        boss.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 999999, 0, false, false));
        boss.setTarget(triggerPlayer);

        level.addFreshEntity(boss);
        bossUuid = boss.getUUID();
        HANDLED_DEFEAT_UUIDS.remove(bossUuid);
        activeState = State.COMBAT;
        stateTimerTicks = 0;
        skyfallCooldownTicks = 280;
        barrageCooldownTicks = 180;
        phase2Triggered = false;
        activeBarrageMarks.clear();
        barrageTimerTicks = 0;
        TETHER_CONTROLLER.reset();

        // Init Boss Bar
        bossBar = new ServerBossEvent(
                UUID.randomUUID(),
                Component.literal("§4§lГЛАВНЫЙ АУДИТОР CORP §8// §cЛИКВИДАТОР 17.4"),
                BossEvent.BossBarColor.RED,
                BossEvent.BossBarOverlay.NOTCHED_10
        );
        bossBar.setProgress(1.0f);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level() == level && player.distanceToSqr(boss) < 10000.0) {
                bossBar.addPlayer(player);
            }
        }

        // Dramatic intro broadcast and sirens
        level.playSound(null, spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(),
                SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 3.0f, 0.8f);
        level.playSound(null, spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(),
                SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 2.0f, 0.7f);

        server.getPlayerList().broadcastSystemMessage(Component.literal(
                "\n§4§l========================================================\n"
                        + "§c§l[ТРЕВОГА CORP] §4ФИНАЛЬНЫЙ АУДИТОР ПРИБЫЛ ДЛЯ ЛИКВИДАЦИИ!\n"
                        + "§eСтатья 17.4 вступила в силу. Приготовьтесь к битве!\n"
                        + "§6Используйте Крюк-Кошку для выживания при Разрывах Почвы!\n"
                        + "§4§l========================================================\n"), false);
    }

    public static void clearBoss(MinecraftServer server) {
        if (bossBar != null) {
            bossBar.removeAllPlayers();
            bossBar = null;
        }
        if (server != null && bossUuid != null) {
            for (ServerLevel sl : server.getAllLevels()) {
                Entity e = sl.getEntity(bossUuid);
                if (e != null) {
                    e.discard();
                }
            }
        }
        bossUuid = null;
        activeState = State.INACTIVE;
        activeBarrageMarks.clear();
        stateTimerTicks = 0;
        phase2Triggered = false;
        TETHER_CONTROLLER.reset();
    }

    /**
     * Guaranteed death hook invoked directly by ServerLivingEntityEvents.AFTER_DEATH.
     * Idempotent: guarantees loot, rewards, and campaign finale fire exactly once even if entity is removed before next tick.
     */
    public static void onBossDeath(ServerLevel level, LivingEntity boss, DamageSource source) {
        if (level == null || boss == null || !isAuditorBoss(boss)) return;
        UUID deadId = boss.getUUID();
        if (!HANDLED_DEFEAT_UUIDS.add(deadId)) {
            return; // Already processed this boss defeat!
        }

        MinecraftServer server = level.getServer();
        if (server == null) return;

        onBossDefeated(server, level, boss.position());
    }

    public static void tickServer(MinecraftServer server) {
        if (server == null || activeState == State.INACTIVE || bossUuid == null) return;

        // If this boss was already defeated via AFTER_DEATH, ensure state is inactive
        if (HANDLED_DEFEAT_UUIDS.contains(bossUuid)) {
            activeState = State.INACTIVE;
            bossUuid = null;
            return;
        }

        ChiefAuditorBossEntity boss = null;
        ServerLevel bossLevel = null;
        for (ServerLevel sl : server.getAllLevels()) {
            Entity e = sl.getEntity(bossUuid);
            if (e instanceof ChiefAuditorBossEntity auditor) {
                boss = auditor;
                bossLevel = sl;
                break;
            }
        }

        // CRITICAL FIX: Do NOT call clearBoss() when boss == null (could be unloaded chunk or mid-death)!
        // Death resolution is handled reliably by ServerLivingEntityEvents.AFTER_DEATH -> onBossDeath().
        if (boss == null || bossLevel == null) {
            return;
        }

        if (!boss.isAlive() || boss.isDeadOrDying()) {
            // Backup safety net in case AFTER_DEATH hasn't fired yet
            onBossDeath(bossLevel, boss, boss.damageSources().generic());
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

        // Check Phase 2 Trigger (< 50% HP)
        if (!phase2Triggered && hpRatio <= 0.50f) {
            phase2Triggered = true;
            boss.setPhaseTwo(true);
            boss.triggerAnim("action", "energy_shield");

            var spd = boss.getAttribute(Attributes.MOVEMENT_SPEED);
            if (spd != null) spd.setBaseValue(0.36);
            bossBar.setColor(BossEvent.BossBarColor.PURPLE);

            bossLevel.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 3.0f, 0.7f);
            server.getPlayerList().broadcastSystemMessage(Component.literal(
                    "§4§l[ФАЗА 2: ОВЕРДРАЙВ CORP] §cАудитор переходит на экстренное питание! Призвана элитная охрана!"), false);

            // Spawn 2 Custom 3D GeckoLib Corporate Collector Enforcers
            for (int i = 0; i < 2; i++) {
                var enforcer = ModEntities.COLLECTOR_ENFORCER.create(bossLevel, EntitySpawnReason.EVENT);
                if (enforcer != null) {
                    enforcer.setPos(boss.getX() + (i == 0 ? 3.5 : -3.5), boss.getY(), boss.getZ() + 2.5);
                    enforcer.markAsRaidEnforcer(2);
                    enforcer.setCustomName(Component.literal("§c§lСИЛОВИК ЛИКВИДАТОРА CORP"));
                    enforcer.setCustomNameVisible(true);
                    bossLevel.addFreshEntity(enforcer);
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

        // Tick dynamic Reactor Core visuals
        BossCoreVisuals.tick(bossLevel, boss, activeState, stateTimerTicks, phase2Triggered);

        switch (activeState) {
            case COMBAT -> {
                boss.setBossState(ChiefAuditorBossEntity.STATE_COMBAT);
                TETHER_CONTROLLER.tick(bossLevel, boss, nearestPlayer);
                skyfallCooldownTicks--;
                barrageCooldownTicks--;

                if (stateTimerTicks % 15 == 0) {
                    bossLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                            boss.getX(), boss.getY() + 0.5, boss.getZ(), 4, 1.2, 0.2, 1.2, 0.02);
                }

                // Trigger Skyfall telegraph
                if (skyfallCooldownTicks <= 0) {
                    activeState = State.SKYFALL_TELEGRAPH;
                    boss.setBossState(ChiefAuditorBossEntity.STATE_TELEGRAPH);
                    boss.triggerAnim("action", "slam");
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
                    barrageCooldownTicks = phase2Triggered ? 140 : 220;
                    barrageTimerTicks = 35;
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

                if (stateTimerTicks == 15 || stateTimerTicks == 30) {
                    bossLevel.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                            SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 2.0f, 0.8f);
                } else if (stateTimerTicks == 40) {
                    bossLevel.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                            SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.5f, 0.5f);
                    boss.triggerAnim("action", "slam");
                }

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

                    bossLevel.sendParticles(ParticleTypes.EXPLOSION, boss.getX(), boss.getY() + 1.0, boss.getZ(), 40, 5.0, 1.0, 5.0, 0.2);
                    bossLevel.sendParticles(ParticleTypes.CRIT, boss.getX(), boss.getY() + 1.0, boss.getZ(), 80, 8.0, 2.0, 8.0, 0.5);

                    // Flying earth & stone chunks propelled into the sky (visual only, drop disabled)
                    BlockState[] flyingBlocks = {
                            Blocks.DIRT.defaultBlockState(),
                            Blocks.COARSE_DIRT.defaultBlockState(),
                            Blocks.STONE.defaultBlockState(),
                            Blocks.COBBLESTONE.defaultBlockState()
                    };
                    for (int i = 0; i < 14; i++) {
                        double angle = (2 * Math.PI / 14) * i + (Math.random() - 0.5) * 0.35;
                        double dist = 3.5 + Math.random() * 7.5;
                        double bx = boss.getX() + Math.cos(angle) * dist;
                        double bz = boss.getZ() + Math.sin(angle) * dist;
                        BlockPos bPos = BlockPos.containing(bx, boss.getY() + 0.6, bz);
                        var debris = FallingBlockEntity.fall(bossLevel, bPos, flyingBlocks[i % flyingBlocks.length]);
                        if (debris != null) {
                            debris.dropItem = false;
                            debris.disableDrop();
                            debris.time = 1;
                            debris.setDeltaMovement(
                                    (Math.random() - 0.5) * 0.55,
                                    1.65 + Math.random() * 0.8,
                                    (Math.random() - 0.5) * 0.55
                            );
                            debris.needsSync = true;
                        }
                    }

                    // Launch players into the stratosphere (25-35 blocks high!)
                    for (ServerPlayer sp : bossLevel.players()) {
                        if (sp.isAlive() && !sp.isCreative() && !sp.isSpectator() && sp.distanceToSqr(boss) <= 1225.0) {
                            Vec3 cur = sp.getDeltaMovement();
                            sp.setDeltaMovement(cur.x * 0.2, 2.45, cur.z * 0.2);
                            sp.needsSync = true;
                            sp.sendSystemMessage(Component.literal(
                                    "§c§l[ШОКОВАЯ ВОЛНА!] §eВы в воздухе! Цепляйтесь Крюком-Кошкой за дерево/землю, иначе падение смертельно!"));
                        }
                    }

                    // Enter Stagger Phase for 5 seconds (100 ticks)
                    activeState = State.STAGGER;
                    boss.setBossState(ChiefAuditorBossEntity.STATE_STAGGER);
                    boss.triggerAnim("action", "energy_shield");
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

                bossLevel.sendParticles(AMBER_CORE_DUST, boss.getX(), boss.getY() + 3.0, boss.getZ(), 6, 0.5, 0.5, 0.5, 0.0);
                bossLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, boss.getX(), boss.getY() + 3.0, boss.getZ(), 4, 0.4, 0.4, 0.4, 0.05);

                if (stateTimerTicks % 20 == 0) {
                    bossLevel.playSound(null, boss.getX(), boss.getY() + 2.5, boss.getZ(),
                            SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 1.2f, 1.5f);
                }

                if (stateTimerTicks >= 100) {
                    activeState = State.COMBAT;
                    boss.setBossState(ChiefAuditorBossEntity.STATE_COMBAT);
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
        activeBarrageMarks.clear();
        phase2Triggered = false;
        TETHER_CONTROLLER.reset();

        if (level != null && deathPos != null) {
            // Victory fireworks & audio
            level.playSound(null, deathPos.x, deathPos.y, deathPos.z,
                    SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 2.5f, 1.0f);
            level.playSound(null, deathPos.x, deathPos.y, deathPos.z,
                    SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 2.0f, 1.2f);

            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, deathPos.x, deathPos.y + 1.5, deathPos.z, 4, 0.6, 0.6, 0.6, 0.0);
            level.sendParticles(ParticleTypes.FIREWORK, deathPos.x, deathPos.y + 2.5, deathPos.z, 80, 2.5, 2.5, 2.5, 0.25);

            // Note: Vanilla loot table data/playermarket/loot_table/entities/chief_auditor.json drops 16 Diamonds,
            // 1 Netherite Ingot, 2 Golden Apples, and 1 Totem of Undying directly on kill!
            // Plus guaranteed trophy relic drop (Doom Hammer or Titan Heart if not already dropped)
            dropItem(level, deathPos, new ItemStack(Items.NETHER_STAR, 1));

            // Grand Victory Chat Announcement
            server.getPlayerList().broadcastSystemMessage(Component.literal(
                    "\n§6§l========================================================\n"
                            + "§e§l              ПОБЕДА НАД КОРПОРАЦИЕЙ!\n"
                            + "§a  Главный Аудитор Corp (Ликвидатор 17.4) повержен!\n"
                            + "§f  Все биологические и финансовые долги аннулированы навсегда.\n"
                            + "§e  Награда: +5 000 CR каждому + сокровища Титана!\n"
                            + "§b  Вы свободны от рабского контракта Asset Corp! Игра пройдена!\n"
                            + "§6§l========================================================\n"), false);

            // Persist victory state, complete campaign, restore all sold stats and freedom for all players
            MarketSavedData data = MarketSavedData.get(server);
            SharedCampaignData campaign = data.getCampaignData();
            campaign.setCurrentStage(6);
            campaign.setEndingStarted(true);
            campaign.setEndingApplied(true);
            campaign.setCinematicCompleted(true);
            campaign.setDisconnectSent(true);
            campaign.setEndingState("BOSS_DEFEATED_FREEDOM");
            data.setActiveChaos("NONE", 0L);
            data.setChaosState("PEACE");
            CorporateChaosDirector.clearAllWorldEntities(server);

            long gameTime = server.overworld().getGameTime();
            for (PlayerMarketAccount acc : data.getAccountsView().values()) {
                acc.addCr(5000L);
                acc.addTransaction(gameTime, "ПОБЕДА НАД АУДИТОРОМ", "Контракт 17.4 расторгнут",
                        "Бонус освобождения и возврат всех органов", 5000L, 0);
                acc.getPermanentSoldFractions().clear();
                acc.getActiveLeases().clear();
                acc.setMarketCardBlocked(false);
                acc.setPermanentImpaired(false);
                acc.setEndingState("VICTORY_FREEDOM");
            }
            data.setDirty();

            // Send Victory Title + Sound + Full Sync to all players
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                MarketAttributeApplier.syncPlayerModifiers(player);
                player.removeEffect(MobEffects.BLINDNESS);
                player.removeEffect(MobEffects.SLOWNESS);
                player.removeEffect(MobEffects.DARKNESS);
                player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 100, 20));
                player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("§6§lПОБЕДА НАД КОРПОРАЦИЕЙ!")));
                player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("§aЛиквидатор 17.4 повержен — Вы свободны!")));
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.5f, 1.0f);
            }
            MarketNetworking.broadcastSync(server, "§6§lПОБЕДА! Главный Аудитор 17.4 повержен! Кампания завершена!");
            CorporateProgressionHints.onAuditorDefeated(server);
        }
    }

    private static void dropItem(ServerLevel level, Vec3 pos, ItemStack stack) {
        net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(
                level, pos.x, pos.y + 0.5, pos.z, stack);
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
    }
}
