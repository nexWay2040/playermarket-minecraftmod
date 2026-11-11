package com.playermarket.story;

import com.playermarket.PlayerMarketMod;
import com.playermarket.config.MarketConfig;
import com.playermarket.market.AssetType;
import com.playermarket.market.MarketAttributeApplier;
import com.playermarket.market.MarketSavedData;
import com.playermarket.market.PlayerMarketAccount;
import com.playermarket.network.MarketNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.Merchant;

import java.util.*;

/**
 * Synchronized Corporate Chaos Director tailored for dynamic co-op sessions.
 * Fires shared world directives every ~75-95s plus interactive stage companion encounters.
 */
public final class CorporateChaosDirector {

    public enum ChaosDirective {
        MINI_AUDIT("mini_audit", "МИНИ-ФОРМАТ (x0.48)",
                "Корпорация урезала расходы на хитбоксы: ваш рост уменьшен, но бегаете на 25% быстрее!", 700),
        GIANT_EGO("giant_ego", "РАЗДУТЫЙ БЮДЖЕТ (x1.55)",
                "Повышение по должности: рост x1.55, урон +25%, отбрасывание снижено!", 700),
        ZERO_GRAVITY("zero_gravity", "ОТМЕНА ГРАВИТАЦИИ",
                "Гравитация отключена за долги! Вы почти не падаете и прыгаете до небес!", 700),
        BOUNCY_FLOOR("bouncy_floor", "ПРУЖИННЫЙ ПОЛ HR",
                "Офисный батут: вы отскакиваете от земли (защита от падения активна)!", 600),
        HR_WORKOUT("hr_workout", "АВРАЛ: ЗАРЯДКА 8x SHIFT!",
                "Тимбилдинг! Присядьте (Shift) 8 раз за 22 сек — иначе удар током и штраф -75 CR!", 440),
        TURBO_SHIFT("turbo_shift", "ТУРБО-СМЕНА (+75% ДОБЫЧА)",
                "Крайний дедлайн! Добыча +75%, бег +40% и +15 CR за каждую выкопанную руду!", 800),
        COLLECTOR_RAID("collector_raid", "ВИЗИТ КОЛЛЕКТОРА",
                "На пороге Коллектор Asset Corp! Победите его ради алмазов и +450 CR!", 400),
        SKY_PARCEL("sky_parcel", "ПОСЫЛКА С НЕБА (КУРЬЕР)",
                "Корпоративный куриный десант! Поймайте посылку с алмазами и яблоками!", 400),
        PIGGY_BANK("piggy_bank", "БЕГЛАЯ СВИНКА-КОПИЛКА",
                "Срочная инкассация! Догоните светящуюся Свинку (ПКМ/удар) ради +300 CR и алмазов!", 500),
        RAINBOW_PINATA("rainbow_pinata", "РАДУЖНАЯ ОВЦА-ПИНЬЯТА",
                "Аудит веселья! Стукните овцу jeb_ 3 раза за печенье, конфетти и алмазы!", 600),
        BRIBE_INSPECTOR("bribe_inspector", "ГОЛОДНЫЙ ИНСПЕКТОР",
                "Ревизор просит перекус! Кликните по Инспектору ПКМ с ЛЮБОЙ едой или изумрудом!", 700),
        ALLAY_BONUS("allay_bonus", "СФЕРА ПРЕМИИ (КУРЬЕР)",
                "Летающий курьер-Аллей принёс бонус! Кликните ПКМ ради алмазов и яблок!", 500);

        private final String id;
        private final String title;
        private final String description;
        private final int durationTicks;

        ChaosDirective(String id, String title, String description, int durationTicks) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.durationTicks = durationTicks;
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }

        public int getDurationTicks() {
            return durationTicks;
        }

        public static Optional<ChaosDirective> byId(String id) {
            if (id == null) return Optional.empty();
            return Arrays.stream(values()).filter(d -> d.id.equalsIgnoreCase(id)).findFirst();
        }
    }

    public static final int WORKOUT_TARGET_SQUATS = 8;

    private enum MobKind {
        COURIER("playermarket_courier"),
        PIGGY("playermarket_piggy"),
        PINATA("playermarket_pinata"),
        INSPECTOR("playermarket_inspector"),
        ALLAY("playermarket_allay"),
        COLLECTOR("playermarket_collector");

        final String tag;

        MobKind(String tag) {
            this.tag = tag;
        }
    }

    private static final class TrackedMob {
        final UUID entityId;
        final MobKind kind;
        long expireTick;
        int taps;
        boolean launching;

        TrackedMob(UUID entityId, MobKind kind, long expireTick) {
            this.entityId = entityId;
            this.kind = kind;
            this.expireTick = expireTick;
        }
    }

    private static final Map<UUID, TrackedMob> TRACKED_MOBS = new HashMap<>();
    private static final Map<UUID, Long> LAST_TAP_TICK = new HashMap<>();
    private static String lastCompletedDirectiveId = "";
    private static int lastCompanionStage = 0;
    private static long nextStageCompanionTick = 0L;

    private CorporateChaosDirector() {
    }

    public static void resetRuntimeState() {
        TRACKED_MOBS.clear();
        LAST_TAP_TICK.clear();
        lastCompletedDirectiveId = "";
        lastCompanionStage = 0;
        nextStageCompanionTick = 0L;
    }

    public static void clearAllWorldEntities(MinecraftServer server) {
        if (server != null) {
            for (UUID id : new ArrayList<>(TRACKED_MOBS.keySet())) {
                Entity found = findEntity(server, id);
                if (found != null) {
                    found.discard();
                }
            }
        }
        resetRuntimeState();
    }

    public static void tickServer(MinecraftServer server, MarketSavedData data, SharedCampaignData campaign, long gameTime) {
        boolean worldSettled = StoryManager.isProgramTerminated(campaign, null);

        if (worldSettled || !campaign.isCampaignAccepted() || !MarketConfig.get().enableChaosDirectives || !data.isChaosEnabled()) {
            if (!"NONE".equals(data.getActiveChaosId())) {
                endActiveDirective(server, data, campaign, gameTime, false);
            }
            if (worldSettled) {
                clearAllWorldEntities(server);
            }
            return;
        }

        // 1. Tick active courier/piggy/pinata/inspector/allay special entities
        tickTrackedMobs(server, data, campaign, gameTime);

        // 2. Check expiration of active chaos directive
        String curId = data.getActiveChaosId();
        if (!"NONE".equals(curId) && gameTime >= data.getActiveChaosEndsTick()) {
            endActiveDirective(server, data, campaign, gameTime, true);
        }

        // 3. Periodic next directive spawn when none is active
        if ("NONE".equals(data.getActiveChaosId()) && gameTime >= data.getNextChaosTick()) {
            if (!server.getPlayerList().getPlayers().isEmpty()) {
                ChaosDirective next = pickNextDirective(server);
                triggerDirective(server, next);
            }
        }

        // 4. Stage companion encounters (ensures current stage world quest always has a live event!)
        tickStageCompanionEvents(server, data, campaign, gameTime);
    }

    private static void tickStageCompanionEvents(MinecraftServer server, MarketSavedData data, SharedCampaignData campaign, long gameTime) {
        int stage = campaign.getCurrentStage();
        if (stage < 1 || stage > 5 || server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }

        if (stage != lastCompanionStage) {
            lastCompanionStage = stage;
            nextStageCompanionTick = gameTime + 180L; // ~9 seconds after entering stage
            return;
        }
        if (nextStageCompanionTick <= 0L || gameTime < nextStageCompanionTick) {
            return;
        }

        boolean spawned = false;
        switch (stage) {
            case 1 -> {
                if (!campaign.isTaskCompleted("s1_piggy_catch") && !hasLiveMobKind(server, MobKind.PIGGY)) {
                    spawnPiggyBank(server, gameTime);
                    spawned = true;
                }
            }
            case 2 -> {
                if (!campaign.isTaskCompleted("s2_pinata_audit") && !hasLiveMobKind(server, MobKind.PINATA)) {
                    spawnRainbowPinata(server, gameTime);
                    spawned = true;
                }
            }
            case 3 -> {
                if (!campaign.isTaskCompleted("s3_inspector_bribe") && !hasLiveMobKind(server, MobKind.INSPECTOR)) {
                    spawnBribeInspector(server, gameTime);
                    spawned = true;
                }
            }
            case 4 -> {
                if (!campaign.isTaskCompleted("s4_sky_courier") && !hasLiveMobKind(server, MobKind.COURIER)) {
                    spawnSkyParcelCourier(server, gameTime);
                    spawned = true;
                }
            }
            case 5 -> {
                if (!campaign.isTaskCompleted("s5_collector_clash")
                        && !hasLiveMobKind(server, MobKind.COLLECTOR)
                        && !hasLiveMobKind(server, MobKind.ALLAY)) {
                    if (server.overworld().getDifficulty() == Difficulty.PEACEFUL) {
                        spawnAllayCourier(server, gameTime);
                    } else {
                        spawnCollectorRaid(server, data, gameTime);
                    }
                    spawned = true;
                } else if (!campaign.isTaskCompleted("s5_ballistics_check") && !hasLiveMobKind(server, MobKind.ALLAY)) {
                    spawnAllayCourier(server, gameTime);
                    spawned = true;
                }
            }
        }

        nextStageCompanionTick = gameTime + (spawned ? 1100L : 400L);
    }

    private static boolean hasLiveMobKind(MinecraftServer server, MobKind kind) {
        for (TrackedMob tm : TRACKED_MOBS.values()) {
            if (tm.kind == kind && findEntity(server, tm.entityId) != null) {
                return true;
            }
        }
        return false;
    }

    private static ChaosDirective pickNextDirective(MinecraftServer server) {
        ChaosDirective[] all = ChaosDirective.values();
        var rng = server.overworld().getRandom();
        ChaosDirective chosen = all[rng.nextInt(all.length)];
        for (int i = 0; i < 8 && chosen.getId().equals(lastCompletedDirectiveId); i++) {
            chosen = all[rng.nextInt(all.length)];
        }
        if (chosen == ChaosDirective.COLLECTOR_RAID && server.overworld().getDifficulty() == Difficulty.PEACEFUL) {
            chosen = ChaosDirective.ALLAY_BONUS;
        }
        return chosen;
    }

    public static void triggerDirective(MinecraftServer server, ChaosDirective directive) {
        if (server == null || directive == null) return;
        MarketSavedData data = MarketSavedData.get(server);
        SharedCampaignData campaign = data.getCampaignData();
        if (StoryManager.isProgramTerminated(campaign, null)) return;
        long gameTime = server.overworld().getGameTime();

        if (!"NONE".equals(data.getActiveChaosId())) {
            endActiveDirective(server, data, campaign, gameTime, false);
        }

        long endsAt = gameTime + directive.getDurationTicks();
        data.setActiveChaos(directive.getId(), endsAt);
        lastCompletedDirectiveId = directive.getId();

        int minInt = Math.max(600, MarketConfig.get().chaosIntervalMinTicks);
        int maxInt = Math.max(minInt + 100, MarketConfig.get().chaosIntervalMaxTicks);
        long nextStart = endsAt + minInt + server.overworld().getRandom().nextInt(maxInt - minInt + 1);
        data.setNextChaosTick(nextStart);

        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            campaign.setObjective("chaos_sq_" + sp.getUUID(), 0);
        }

        switch (directive) {
            case COLLECTOR_RAID -> spawnCollectorRaid(server, data, gameTime);
            case SKY_PARCEL -> spawnSkyParcelCourier(server, gameTime);
            case PIGGY_BANK -> spawnPiggyBank(server, gameTime);
            case RAINBOW_PINATA -> spawnRainbowPinata(server, gameTime);
            case BRIBE_INSPECTOR -> spawnBribeInspector(server, gameTime);
            case ALLAY_BONUS -> spawnAllayCourier(server, gameTime);
            default -> {}
        }

        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            MarketAttributeApplier.syncPlayerModifiers(sp);
            sp.sendSystemMessage(Component.literal("§8[§6ДИРЕКТИВА ASSET CORP§8] §e§l" + directive.getTitle() + "\n§7" + directive.getDescription()));
            sp.sendOverlayMessage(Component.literal("§6§l⚡ " + directive.getTitle() + " ⚡"));
            sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                    SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.85f, 1.15f);
        }
        MarketNetworking.broadcastSync(server, "§6Директива: " + directive.getTitle());
    }

    private static void endActiveDirective(MinecraftServer server, MarketSavedData data, SharedCampaignData campaign, long gameTime, boolean evaluateChallenge) {
        String curId = data.getActiveChaosId();
        Optional<ChaosDirective> dirOpt = ChaosDirective.byId(curId);

        if (evaluateChallenge && dirOpt.isPresent() && dirOpt.get() == ChaosDirective.HR_WORKOUT) {
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                PlayerMarketAccount acc = data.getOrCreateAccount(sp.getUUID());
                if (sp.isSpectator() || StoryManager.isProgramTerminated(campaign, acc)) continue;
                int squats = campaign.getObjective("chaos_sq_" + sp.getUUID());
                if (squats >= WORKOUT_TARGET_SQUATS) {
                    acc.addCr(350L);
                    giveOrDrop(sp, new ItemStack(Items.DIAMOND, 4));
                    sp.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 160, 1, false, false));
                    acc.addTransaction(gameTime, "ДИРЕКТИВА HR", "Зарядка выполнена!", "8 приседаний по уставу", 350L, 4);
                    sp.sendSystemMessage(Component.literal("§8[§aHR-ОТДЕЛ§8] §a✔ Молодец, актив! Зарядка выполнена: §e+350 CR §8| §b+4 ♦§a!"));
                    sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                            SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.3f);
                } else {
                    long fine = acc.forcePenaltyCr(75L);
                    acc.addTransaction(gameTime, "ШТРАФ HR", "Прогул зарядки", "Выполнено " + squats + "/" + WORKOUT_TARGET_SQUATS, -fine, 0);
                    if (sp.getHealth() > 4.0f && sp.level() instanceof ServerLevel sl) {
                        sp.hurtServer(sl, sl.damageSources().lightningBolt(), 2.0f);
                    }
                    sp.sendSystemMessage(Component.literal("§8[§cHR-ОТДЕЛ§8] §c✖ Прогул гимнастики (" + squats + "/" + WORKOUT_TARGET_SQUATS + ")! Штраф §e-" + fine + " CR§c и разряд тока!"));
                    sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                            SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.65f, 1.5f);
                }
            }
        }

        data.setActiveChaos("NONE", 0L);
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            campaign.setObjective("chaos_sq_" + sp.getUUID(), 0);
            MarketAttributeApplier.syncPlayerModifiers(sp);
        }
        MarketNetworking.broadcastSync(server, "");
    }

    public static boolean onPlayerCrouch(ServerPlayer player, MarketSavedData data, SharedCampaignData campaign) {
        if (!ChaosDirective.HR_WORKOUT.getId().equals(data.getActiveChaosId()) || StoryManager.isProgramTerminated(campaign, null)) {
            return false;
        }
        String key = "chaos_sq_" + player.getUUID();
        int cur = campaign.getObjective(key);
        if (cur >= WORKOUT_TARGET_SQUATS) {
            return false;
        }
        int next = cur + 1;
        campaign.setObjective(key, next);
        data.setDirty();

        if (next == WORKOUT_TARGET_SQUATS) {
            player.sendOverlayMessage(Component.literal("§a✔ Зарядка засчитана (" + next + "/" + WORKOUT_TARGET_SQUATS + ")! Ждите премию!"));
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.9f, 1.5f);
        } else {
            player.sendOverlayMessage(Component.literal("§eЗарядка: §f" + next + " / " + WORKOUT_TARGET_SQUATS + " §7(Shift)"));
        }
        MarketNetworking.sendSyncToPlayer(player);
        return true;
    }

    // === SAFE WORLD SPAWN HELPERS (Works both outdoors and inside caves/bases!) ===

    private static ServerPlayer pickTargetPlayer(MinecraftServer server, MarketSavedData data) {
        List<ServerPlayer> valid = new ArrayList<>();
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            PlayerMarketAccount acc = data.getOrCreateAccount(sp.getUUID());
            if (!sp.isSpectator() && !StoryManager.isProgramTerminated(data.getCampaignData(), acc)) {
                valid.add(sp);
            }
        }
        if (valid.isEmpty()) return null;
        return valid.get(server.overworld().getRandom().nextInt(valid.size()));
    }

    private static BlockPos findSafeGroundPos(ServerLevel sl, BlockPos center, int minDist, int maxDist) {
        var rng = sl.getRandom();
        for (int attempt = 0; attempt < 18; attempt++) {
            double angle = rng.nextDouble() * Math.PI * 2.0;
            double dist = minDist + rng.nextDouble() * Math.max(1, maxDist - minDist);
            int tx = (int) Math.round(center.getX() + Math.cos(angle) * dist);
            int tz = (int) Math.round(center.getZ() + Math.sin(angle) * dist);
            for (int dy = 3; dy >= -4; dy--) {
                BlockPos feet = new BlockPos(tx, center.getY() + dy, tz);
                BlockPos below = feet.below();
                BlockPos head = feet.above();
                if (!sl.getBlockState(below).getCollisionShape(sl, below).isEmpty()
                        && sl.getBlockState(feet).getCollisionShape(sl, feet).isEmpty()
                        && sl.getBlockState(head).getCollisionShape(sl, head).isEmpty()) {
                    return feet;
                }
            }
        }
        return center.above();
    }

    private static BlockPos findSafeAirDropPos(ServerLevel sl, BlockPos groundSpot, int desiredHeight) {
        int clearAbove = 1;
        for (int h = 1; h <= desiredHeight; h++) {
            BlockPos check = groundSpot.above(h);
            if (!sl.getBlockState(check).getCollisionShape(sl, check).isEmpty()) {
                break;
            }
            clearAbove = h;
        }
        int spawnH = Math.max(2, clearAbove - 1);
        return groundSpot.above(spawnH);
    }

    // === 6 INTERACTIVE WORLD ENCOUNTERS ===

    private static void spawnCollectorRaid(MinecraftServer server, MarketSavedData data, long gameTime) {
        ServerPlayer target = pickTargetPlayer(server, data);
        if (target == null || !(target.level() instanceof ServerLevel sl)) return;
        if (sl.getDifficulty() == Difficulty.PEACEFUL) {
            spawnAllayCourier(server, gameTime);
            return;
        }

        Vindicator collector = EntityTypes.VINDICATOR.create(sl, EntitySpawnReason.EVENT);
        if (collector == null) return;

        BlockPos pos = findSafeGroundPos(sl, target.blockPosition(), 5, 9);
        collector.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        collector.addTag(MobKind.COLLECTOR.tag);
        collector.setCustomName(Component.literal("§c§lКОЛЛЕКТОР ASSET CORP"));
        collector.setCustomNameVisible(true);
        collector.setPersistenceRequired();
        collector.setTarget(target);
        collector.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        collector.addEffect(new MobEffectInstance(MobEffects.GLOWING, 1200, 0, false, false));
        collector.addEffect(new MobEffectInstance(MobEffects.SPEED, 1200, 0, false, false));
        var hpAttr = collector.getAttribute(Attributes.MAX_HEALTH);
        if (hpAttr != null) {
            hpAttr.setBaseValue(24.0);
            collector.setHealth(24.0f);
        }

        sl.addFreshEntity(collector);
        TRACKED_MOBS.put(collector.getUUID(), new TrackedMob(collector.getUUID(), MobKind.COLLECTOR, gameTime + 1200L));
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 18, 0.5, 0.6, 0.5, 0.08);
        sl.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.8f, 1.3f);
    }

    private static void spawnSkyParcelCourier(MinecraftServer server, long gameTime) {
        MarketSavedData data = MarketSavedData.get(server);
        ServerPlayer anchor = pickTargetPlayer(server, data);
        if (anchor == null || !(anchor.level() instanceof ServerLevel sl)) return;

        BlockPos ground = findSafeGroundPos(sl, anchor.blockPosition(), 2, 7);
        BlockPos dropPos = findSafeAirDropPos(sl, ground, 14);

        Chicken chicken = EntityTypes.CHICKEN.create(sl, EntitySpawnReason.EVENT);
        if (chicken == null) return;

        chicken.setPos(dropPos.getX() + 0.5, dropPos.getY(), dropPos.getZ() + 0.5);
        chicken.addTag(MobKind.COURIER.tag);
        chicken.setCustomName(Component.literal("§6§lКУРЬЕР ASSET CORP"));
        chicken.setCustomNameVisible(true);
        chicken.addEffect(new MobEffectInstance(MobEffects.GLOWING, 440, 0, false, false));
        chicken.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 440, 0, false, false));

        sl.addFreshEntity(chicken);
        TRACKED_MOBS.put(chicken.getUUID(), new TrackedMob(chicken.getUUID(), MobKind.COURIER, gameTime + 220L));

        for (ServerPlayer sp : sl.players()) {
            sp.sendSystemMessage(Component.literal("§8[§6АВИАПОЧТА§8] §eКурица-курьер сброшена над §fX:" + ground.getX() + " Z:" + ground.getZ() + "§e! Поймай её (ПКМ/удар)!"));
        }
        sl.playSound(null, ground.getX(), ground.getY(), ground.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0f, 1.1f);
    }

    private static void spawnPiggyBank(MinecraftServer server, long gameTime) {
        MarketSavedData data = MarketSavedData.get(server);
        ServerPlayer anchor = pickTargetPlayer(server, data);
        if (anchor == null || !(anchor.level() instanceof ServerLevel sl)) return;

        BlockPos pos = findSafeGroundPos(sl, anchor.blockPosition(), 3, 7);
        Pig pig = EntityTypes.PIG.create(sl, EntitySpawnReason.EVENT);
        if (pig == null) return;

        pig.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        pig.addTag(MobKind.PIGGY.tag);
        pig.setCustomName(Component.literal("§6§lСВИНКА-КОПИЛКА"));
        pig.setCustomNameVisible(true);
        pig.setPersistenceRequired();
        pig.addEffect(new MobEffectInstance(MobEffects.GLOWING, 600, 0, false, false));
        pig.addEffect(new MobEffectInstance(MobEffects.SPEED, 600, 1, false, false));

        sl.addFreshEntity(pig);
        TRACKED_MOBS.put(pig.getUUID(), new TrackedMob(pig.getUUID(), MobKind.PIGGY, gameTime + 560L));
        sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 22, 0.4, 0.4, 0.4, 0.15);
        sl.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.PIG_AMBIENT_BABY.value(), SoundSource.NEUTRAL, 1.0f, 1.1f);

        for (ServerPlayer sp : sl.players()) {
            sp.sendSystemMessage(Component.literal("§8[§6ИНКАССАЦИЯ§8] §eСвинка-Копилка убегает у §fX:" + pos.getX() + " Z:" + pos.getZ() + "§e! Догоните (ПКМ или удар)!"));
        }
    }

    private static void spawnRainbowPinata(MinecraftServer server, long gameTime) {
        MarketSavedData data = MarketSavedData.get(server);
        ServerPlayer anchor = pickTargetPlayer(server, data);
        if (anchor == null || !(anchor.level() instanceof ServerLevel sl)) return;

        BlockPos pos = findSafeGroundPos(sl, anchor.blockPosition(), 3, 6);
        Sheep sheep = EntityTypes.SHEEP.create(sl, EntitySpawnReason.EVENT);
        if (sheep == null) return;

        sheep.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        sheep.addTag(MobKind.PINATA.tag);
        sheep.setCustomName(Component.literal("jeb_"));
        sheep.setCustomNameVisible(true);
        sheep.setPersistenceRequired();
        sheep.addEffect(new MobEffectInstance(MobEffects.GLOWING, 700, 0, false, false));

        sl.addFreshEntity(sheep);
        TRACKED_MOBS.put(sheep.getUUID(), new TrackedMob(sheep.getUUID(), MobKind.PINATA, gameTime + 640L));
        sl.sendParticles(ParticleTypes.FIREWORK, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 26, 0.5, 0.5, 0.5, 0.18);
        sl.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.NEUTRAL, 1.0f, 1.2f);

        for (ServerPlayer sp : sl.players()) {
            sp.sendSystemMessage(Component.literal("§8[§dПИНЬЯТА§8] §dРадужная Овца jeb_ заспавнилась у §fX:" + pos.getX() + " Z:" + pos.getZ() + "§d! Стукните 3 раза за призы!"));
        }
    }

    private static void spawnBribeInspector(MinecraftServer server, long gameTime) {
        MarketSavedData data = MarketSavedData.get(server);
        ServerPlayer anchor = pickTargetPlayer(server, data);
        if (anchor == null || !(anchor.level() instanceof ServerLevel sl)) return;

        BlockPos pos = findSafeGroundPos(sl, anchor.blockPosition(), 3, 6);
        WanderingTrader trader = EntityTypes.WANDERING_TRADER.create(sl, EntitySpawnReason.EVENT);
        if (trader == null) return;

        trader.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        trader.addTag(MobKind.INSPECTOR.tag);
        trader.setCustomName(Component.literal("§e§lИНСПЕКТОР [ДАЙ ЕДУ / ♦]"));
        trader.setCustomNameVisible(true);
        trader.setPersistenceRequired();
        trader.addEffect(new MobEffectInstance(MobEffects.GLOWING, 800, 0, false, false));

        sl.addFreshEntity(trader);
        TRACKED_MOBS.put(trader.getUUID(), new TrackedMob(trader.getUUID(), MobKind.INSPECTOR, gameTime + 720L));
        sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 20, 0.5, 0.6, 0.5, 0.1);
        sl.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.WANDERING_TRADER_YES, SoundSource.NEUTRAL, 1.0f, 1.0f);

        for (ServerPlayer sp : sl.players()) {
            sp.sendSystemMessage(Component.literal("§8[§eРЕВИЗОР§8] §fПришёл голодный Инспектор у §eX:" + pos.getX() + " Z:" + pos.getZ() + "§f! Суй ему ЛЮБУЮ еду или изумруд (ПКМ)!"));
        }
    }

    private static void spawnAllayCourier(MinecraftServer server, long gameTime) {
        MarketSavedData data = MarketSavedData.get(server);
        ServerPlayer anchor = pickTargetPlayer(server, data);
        if (anchor == null || !(anchor.level() instanceof ServerLevel sl)) return;

        BlockPos ground = findSafeGroundPos(sl, anchor.blockPosition(), 2, 5);
        BlockPos air = findSafeAirDropPos(sl, ground, 4);
        Allay allay = EntityTypes.ALLAY.create(sl, EntitySpawnReason.EVENT);
        if (allay == null) return;

        allay.setPos(air.getX() + 0.5, air.getY(), air.getZ() + 0.5);
        allay.addTag(MobKind.ALLAY.tag);
        allay.setCustomName(Component.literal("§b§lСФЕРА ПРЕМИИ [КЛИКНИ]"));
        allay.setCustomNameVisible(true);
        allay.setPersistenceRequired();
        allay.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND));
        allay.addEffect(new MobEffectInstance(MobEffects.GLOWING, 600, 0, false, false));

        sl.addFreshEntity(allay);
        TRACKED_MOBS.put(allay.getUUID(), new TrackedMob(allay.getUUID(), MobKind.ALLAY, gameTime + 560L));
        sl.sendParticles(ParticleTypes.END_ROD, air.getX() + 0.5, air.getY() + 0.5, air.getZ() + 0.5, 20, 0.4, 0.4, 0.4, 0.08);
        sl.playSound(null, air.getX(), air.getY(), air.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.0f, 1.2f);

        for (ServerPlayer sp : sl.players()) {
            sp.sendSystemMessage(Component.literal("§8[§bПРЕМИЯ§8] §bПрилетела светящаяся Сфера-Аллей у §fX:" + air.getX() + " Z:" + air.getZ() + "§b! Кликните ПКМ за наградой!"));
        }
    }

    // === TICK & INTERACTION HANDLERS ===

    private static void tickTrackedMobs(MinecraftServer server, MarketSavedData data, SharedCampaignData campaign, long gameTime) {
        if (TRACKED_MOBS.isEmpty()) return;
        Iterator<Map.Entry<UUID, TrackedMob>> it = TRACKED_MOBS.entrySet().iterator();
        while (it.hasNext()) {
            TrackedMob tm = it.next().getValue();
            Entity found = findEntity(server, tm.entityId);
            if (found == null || !found.isAlive()) {
                it.remove();
                continue;
            }
            if (found.level() instanceof ServerLevel sl && gameTime % 10L == 0L) {
                var pType = switch (tm.kind) {
                    case PIGGY -> ParticleTypes.HAPPY_VILLAGER;
                    case PINATA -> ParticleTypes.FIREWORK;
                    case INSPECTOR -> ParticleTypes.NOTE;
                    case ALLAY -> ParticleTypes.END_ROD;
                    case COURIER -> ParticleTypes.CLOUD;
                    case COLLECTOR -> ParticleTypes.ELECTRIC_SPARK;
                };
                sl.sendParticles(pType, found.getX(), found.getY() + 0.9, found.getZ(), 2, 0.25, 0.25, 0.25, 0.02);
            }

            if (tm.kind == MobKind.COURIER && (found.onGround() || gameTime >= tm.expireTick)) {
                deliverCourierLoot(found, data, campaign, null, gameTime);
                found.discard();
                it.remove();
            } else if (tm.launching && gameTime >= tm.expireTick) {
                if (found.level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.FIREWORK, found.getX(), found.getY() + 0.5, found.getZ(), 32, 0.5, 0.5, 0.5, 0.2);
                    sl.playSound(null, found.getX(), found.getY(), found.getZ(), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 0.9f, 1.3f);
                }
                found.discard();
                it.remove();
            } else if (!tm.launching && gameTime >= tm.expireTick) {
                if (found.level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.CLOUD, found.getX(), found.getY() + 0.5, found.getZ(), 14, 0.3, 0.3, 0.3, 0.05);
                }
                found.discard();
                it.remove();
            }
        }
    }

    /**
     * Unified handler for right-clicking OR punching custom Chaos/Companion entities.
     * Returns true if the target was one of our special interactive mobs and the click/punch was handled.
     */
    public static boolean handleChaosMobInteraction(ServerPlayer player, Entity target, ItemStack held,
                                                    MarketSavedData data, boolean isAttack, long gameTime) {
        if (player == null || target == null || data == null) return false;
        SharedCampaignData campaign = data.getCampaignData();
        PlayerMarketAccount acc = data.getOrCreateAccount(player.getUUID());
        if (StoryManager.isProgramTerminated(campaign, acc)) return false;

        Set<String> tags = target.entityTags();

        // Check normal Villagers/Wandering Traders for food/emerald bribery too
        if (target instanceof Merchant && !tags.contains(MobKind.INSPECTOR.tag)) {
            if (!isAttack && isBribeItem(held)) {
                campaign.setObjective("villager_trade", 1);
                campaign.addContribution("inspector_bribed", 1);
                CorporateTaskTracker.advanceContract(player, acc, "BRIBE_PET", 1);
                return false; // Let vanilla open merchant trade normally after recording progress
            }
            return false;
        }

        // Prevent duplicate trigger between attack/interact within 4 ticks on the same entity
        Long lastTick = LAST_TAP_TICK.get(target.getUUID());
        if (lastTick != null && (gameTime - lastTick) < 4L) {
            return isSpecialInteractiveMob(tags);
        }

        if (tags.contains(MobKind.COURIER.tag)) {
            LAST_TAP_TICK.put(target.getUUID(), gameTime);
            TRACKED_MOBS.remove(target.getUUID());
            deliverCourierLoot(target, data, campaign, player, gameTime);
            target.discard();
            return true;
        }

        if (tags.contains(MobKind.PIGGY.tag)) {
            LAST_TAP_TICK.put(target.getUUID(), gameTime);
            TRACKED_MOBS.remove(target.getUUID());
            handlePiggyCaught(player, target, data, campaign, gameTime);
            target.discard();
            return true;
        }

        if (tags.contains(MobKind.PINATA.tag)) {
            LAST_TAP_TICK.put(target.getUUID(), gameTime);
            handlePinataTap(player, target, data, campaign, gameTime);
            return true;
        }

        if (tags.contains(MobKind.ALLAY.tag)) {
            LAST_TAP_TICK.put(target.getUUID(), gameTime);
            TRACKED_MOBS.remove(target.getUUID());
            handleAllayCaught(player, target, data, campaign, gameTime);
            target.discard();
            return true;
        }

        if (tags.contains(MobKind.INSPECTOR.tag)) {
            if (isAttack) {
                player.sendOverlayMessage(Component.literal("§eНе бей ревизора! Угости его ЕДОЙ или изумрудом через ПКМ!"));
                return true;
            }
            LAST_TAP_TICK.put(target.getUUID(), gameTime);
            if (!isBribeItem(held)) {
                player.sendOverlayMessage(Component.literal("§eИнспектор шепчет: «Дай в руку ЛЮБУЮ ЕДУ или изумруд (ПКМ)!»"));
                player.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 0.7f, 0.9f);
                return true;
            }
            if (!player.isCreative()) {
                held.shrink(1);
            }
            TRACKED_MOBS.remove(target.getUUID());
            handleInspectorBribed(player, target, data, campaign, gameTime);
            target.discard();
            return true;
        }

        return false;
    }

    private static boolean isSpecialInteractiveMob(Set<String> tags) {
        return tags.contains(MobKind.COURIER.tag)
                || tags.contains(MobKind.PIGGY.tag)
                || tags.contains(MobKind.PINATA.tag)
                || tags.contains(MobKind.ALLAY.tag)
                || tags.contains(MobKind.INSPECTOR.tag);
    }

    private static boolean isBribeItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.has(DataComponents.FOOD)
                || stack.is(Items.EMERALD)
                || stack.is(Items.GOLD_INGOT)
                || stack.is(Items.GOLDEN_APPLE)
                || stack.is(Items.COOKIE)
                || stack.is(Items.MILK_BUCKET);
    }

    private static void handlePiggyCaught(ServerPlayer catcher, Entity pig, MarketSavedData data, SharedCampaignData campaign, long gameTime) {
        if (!(pig.level() instanceof ServerLevel sl)) return;
        spawnDrop(sl, pig.getX(), pig.getY() + 0.4, pig.getZ(), new ItemStack(Items.DIAMOND, 2));
        spawnDrop(sl, pig.getX(), pig.getY() + 0.4, pig.getZ(), new ItemStack(Items.EMERALD, 3));
        spawnDrop(sl, pig.getX(), pig.getY() + 0.4, pig.getZ(), new ItemStack(Items.GOLD_INGOT, 3));
        sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pig.getX(), pig.getY() + 0.6, pig.getZ(), 28, 0.5, 0.5, 0.5, 0.2);
        sl.playSound(null, pig.getX(), pig.getY(), pig.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.9f, 1.35f);

        PlayerMarketAccount acc = data.getOrCreateAccount(catcher.getUUID());
        acc.addCr(300L);
        acc.addTransaction(gameTime, "ИНКАССАЦИЯ", "Свинка-Копилка поймана!", "Трофей + алмазы", 300L, 0);
        campaign.addContribution("piggy_caught", 1);
        campaign.addContribution("courier_caught", 1);
        CorporateTaskTracker.advanceContract(catcher, acc, "COURIER_LOOT", 1);
        CorporateTaskTracker.advanceContract(catcher, acc, "BRIBE_PET", 1);
        catcher.sendSystemMessage(Component.literal("§8[§aИНКАССАЦИЯ§8] §a✔ Свинка-Копилка поймана: §e+300 CR§a, алмазы и золото!"));
        StoryManager.checkAllQuests(catcher, acc);
        MarketNetworking.sendSyncToPlayer(catcher);
    }

    private static void handlePinataTap(ServerPlayer striker, Entity sheepEntity, MarketSavedData data, SharedCampaignData campaign, long gameTime) {
        if (!(sheepEntity.level() instanceof ServerLevel sl)) return;
        TrackedMob tm = TRACKED_MOBS.get(sheepEntity.getUUID());
        if (tm == null) {
            tm = new TrackedMob(sheepEntity.getUUID(), MobKind.PINATA, gameTime + 300L);
            TRACKED_MOBS.put(sheepEntity.getUUID(), tm);
        }
        if (tm.launching) return;

        tm.taps++;
        PlayerMarketAccount acc = data.getOrCreateAccount(striker.getUUID());
        sl.sendParticles(ParticleTypes.FIREWORK, sheepEntity.getX(), sheepEntity.getY() + 0.8, sheepEntity.getZ(), 18, 0.4, 0.4, 0.4, 0.15);
        sl.playSound(null, sheepEntity.getX(), sheepEntity.getY(), sheepEntity.getZ(), SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 0.9f, 1.1f + tm.taps * 0.15f);

        if (tm.taps < 3) {
            spawnDrop(sl, sheepEntity.getX(), sheepEntity.getY() + 0.6, sheepEntity.getZ(), new ItemStack(Items.COOKIE, 2));
            spawnDrop(sl, sheepEntity.getX(), sheepEntity.getY() + 0.6, sheepEntity.getZ(), new ItemStack(Items.EMERALD, 1));
            acc.addCr(100L);
            striker.sendOverlayMessage(Component.literal("§d✦ ПИНЬЯТА! Удар " + tm.taps + "/3 §e(+100 CR и печеньки!)"));
        } else {
            tm.launching = true;
            tm.expireTick = gameTime + 28L;
            spawnDrop(sl, sheepEntity.getX(), sheepEntity.getY() + 0.7, sheepEntity.getZ(), new ItemStack(Items.DIAMOND, 2));
            spawnDrop(sl, sheepEntity.getX(), sheepEntity.getY() + 0.7, sheepEntity.getZ(), new ItemStack(Items.GOLDEN_APPLE, 1));
            acc.addCr(250L);
            acc.addTransaction(gameTime, "ПИНЬЯТА jeb_", "Радужный взрыв призов!", "Финал пиньяты (3/3)", 250L, 0);
            campaign.addContribution("pinata_done", 1);
            campaign.addContribution("courier_caught", 1);
            CorporateTaskTracker.advanceContract(striker, acc, "COURIER_LOOT", 1);
            if (sheepEntity instanceof LivingEntity le) {
                le.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 40, 2, false, false));
            }
            sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, sheepEntity.getX(), sheepEntity.getY() + 0.9, sheepEntity.getZ(), 32, 0.6, 0.6, 0.6, 0.25);
            sl.playSound(null, sheepEntity.getX(), sheepEntity.getY(), sheepEntity.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.4f);
            striker.sendSystemMessage(Component.literal("§8[§dПИНЬЯТА§8] §d✔ Радужная Пиньята разбита: §e+250 CR§d, §b+2 ♦ §dи Золотое Яблоко!"));
        }
        data.setDirty();
        StoryManager.checkAllQuests(striker, acc);
        MarketNetworking.sendSyncToPlayer(striker);
    }

    private static void handleInspectorBribed(ServerPlayer briber, Entity trader, MarketSavedData data, SharedCampaignData campaign, long gameTime) {
        if (!(trader.level() instanceof ServerLevel sl)) return;
        spawnDrop(sl, trader.getX(), trader.getY() + 0.7, trader.getZ(), new ItemStack(Items.DIAMOND, 3));
        spawnDrop(sl, trader.getX(), trader.getY() + 0.7, trader.getZ(), new ItemStack(Items.GOLDEN_CARROT, 4));
        sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, trader.getX(), trader.getY() + 1.0, trader.getZ(), 28, 0.5, 0.6, 0.5, 0.2);
        sl.playSound(null, trader.getX(), trader.getY(), trader.getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0f, 1.15f);

        PlayerMarketAccount acc = data.getOrCreateAccount(briber.getUUID());
        acc.addCr(350L);
        acc.addTransaction(gameTime, "ВЗЯТКА РЕВИЗОРУ", "Акт проверки подписан!", "Инспектор доволен", 350L, 0);
        campaign.addContribution("inspector_bribed", 1);
        campaign.setObjective("villager_trade", 1);
        campaign.addContribution("courier_caught", 1);
        CorporateTaskTracker.advanceContract(briber, acc, "BRIBE_PET", 1);
        CorporateTaskTracker.advanceContract(briber, acc, "COURIER_LOOT", 1);
        briber.sendSystemMessage(Component.literal("§8[§aРЕВИЗОР§8] §a«Хмм, отличный бутерброд! Отчёт чист!» §e(+350 CR и 3 ♦)"));
        StoryManager.checkAllQuests(briber, acc);
        MarketNetworking.sendSyncToPlayer(briber);
    }

    private static void handleAllayCaught(ServerPlayer catcher, Entity allay, MarketSavedData data, SharedCampaignData campaign, long gameTime) {
        if (!(allay.level() instanceof ServerLevel sl)) return;
        spawnDrop(sl, allay.getX(), allay.getY() + 0.5, allay.getZ(), new ItemStack(Items.DIAMOND, 3));
        spawnDrop(sl, allay.getX(), allay.getY() + 0.5, allay.getZ(), new ItemStack(Items.GOLDEN_APPLE, 2));
        sl.sendParticles(ParticleTypes.END_ROD, allay.getX(), allay.getY() + 0.6, allay.getZ(), 28, 0.5, 0.5, 0.5, 0.15);
        sl.playSound(null, allay.getX(), allay.getY(), allay.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.4f);

        PlayerMarketAccount acc = data.getOrCreateAccount(catcher.getUUID());
        acc.addCr(300L);
        acc.addTransaction(gameTime, "СФЕРА ПРЕМИИ", "Аллей-курьер пойман!", "Бонус корпорации", 300L, 0);
        campaign.addContribution("allay_caught", 1);
        campaign.addContribution("courier_caught", 1);
        CorporateTaskTracker.advanceContract(catcher, acc, "COURIER_LOOT", 1);
        catcher.sendSystemMessage(Component.literal("§8[§bПРЕМИЯ§8] §b✔ Сфера-Аллей вручила награду: §e+300 CR§b, §f3 ♦ §bи Золотые Яблоки!"));
        StoryManager.checkAllQuests(catcher, acc);
        MarketNetworking.sendSyncToPlayer(catcher);
    }

    public static boolean handleCourierKilled(Entity courierEntity, ServerPlayer killer, MarketSavedData data, long gameTime) {
        if (courierEntity == null || !courierEntity.entityTags().contains(MobKind.COURIER.tag)) {
            return false;
        }
        TRACKED_MOBS.remove(courierEntity.getUUID());
        SharedCampaignData campaign = data.getCampaignData();
        deliverCourierLoot(courierEntity, data, campaign, killer, gameTime);
        return true;
    }

    private static void deliverCourierLoot(Entity source, MarketSavedData data, SharedCampaignData campaign, ServerPlayer directCatcher, long gameTime) {
        if (!(source.level() instanceof ServerLevel sl)) return;
        double x = source.getX();
        double y = source.getY() + 0.3;
        double z = source.getZ();

        spawnDrop(sl, x, y, z, new ItemStack(Items.DIAMOND, 3));
        spawnDrop(sl, x, y, z, new ItemStack(Items.GOLDEN_APPLE, 2));
        spawnDrop(sl, x, y, z, new ItemStack(Items.EMERALD, 4));

        sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y + 0.5, z, 28, 0.5, 0.5, 0.5, 0.25);
        sl.playSound(null, x, y, z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 0.9f, 1.2f);

        for (ServerPlayer sp : sl.players()) {
            boolean isCatcher = directCatcher != null && directCatcher.getUUID().equals(sp.getUUID());
            if (isCatcher || sp.distanceToSqr(source) <= 18.0 * 18.0) {
                PlayerMarketAccount acc = data.getOrCreateAccount(sp.getUUID());
                if (StoryManager.isProgramTerminated(campaign, acc)) continue;
                long bonus = isCatcher ? 350L : 200L;
                acc.addCr(bonus);
                acc.addTransaction(gameTime, "АВИАПОЧТА", isCatcher ? "Курьер перехвачен!" : "Посылка с неба", "Награда за курьера", bonus, 0);
                campaign.addContribution("sky_parcel_caught", 1);
                campaign.addContribution("courier_caught", 1);
                CorporateTaskTracker.advanceContract(sp, acc, "COURIER_LOOT", 1);
                sp.sendOverlayMessage(Component.literal("§a✈ Курьер перехвачен! +" + bonus + " CR и лут!"));
                StoryManager.checkAllQuests(sp, acc);
                MarketNetworking.sendSyncToPlayer(sp);
            }
        }
    }

    private static void giveOrDrop(ServerPlayer sp, ItemStack stack) {
        if (!sp.getInventory().add(stack)) {
            sp.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
        sp.getInventory().setChanged();
    }

    private static void spawnDrop(ServerLevel sl, double x, double y, double z, ItemStack stack) {
        ItemEntity item = new ItemEntity(sl, x, y, z, stack);
        item.setGlowingTag(true);
        sl.addFreshEntity(item);
    }

    private static Entity findEntity(MinecraftServer server, UUID uuid) {
        for (ServerLevel sl : server.getAllLevels()) {
            Entity e = sl.getEntity(uuid);
            if (e != null) return e;
        }
        return null;
    }

    public static void applyDirectiveModifiers(ServerPlayer player, MarketSavedData data, long gameTime) {
        if (player == null || data == null) return;
        String activeId = data.getActiveChaosId();
        if ("NONE".equals(activeId) || gameTime >= data.getActiveChaosEndsTick()) return;

        Optional<ChaosDirective> dirOpt = ChaosDirective.byId(activeId);
        if (dirOpt.isEmpty()) return;

        switch (dirOpt.get()) {
            case MINI_AUDIT -> {
                addChaosMod(player, Attributes.SCALE, "chaos_scale", -0.52, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
                addChaosMod(player, Attributes.MOVEMENT_SPEED, "chaos_speed", 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            }
            case GIANT_EGO -> {
                addChaosMod(player, Attributes.SCALE, "chaos_scale", 0.55, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
                addChaosMod(player, Attributes.ATTACK_DAMAGE, "chaos_dmg", 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
                addChaosMod(player, Attributes.KNOCKBACK_RESISTANCE, "chaos_kb", 0.50, AttributeModifier.Operation.ADD_VALUE);
            }
            case ZERO_GRAVITY -> {
                addChaosMod(player, Attributes.GRAVITY, "chaos_gravity", -0.62, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
                addChaosMod(player, Attributes.SAFE_FALL_DISTANCE, "chaos_safe_fall", 10.0, AttributeModifier.Operation.ADD_VALUE);
                addChaosMod(player, Attributes.JUMP_STRENGTH, "chaos_jump", 0.22, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            }
            case BOUNCY_FLOOR -> {
                addChaosMod(player, Attributes.BOUNCINESS, "chaos_bounce", 0.85, AttributeModifier.Operation.ADD_VALUE);
                addChaosMod(player, Attributes.SAFE_FALL_DISTANCE, "chaos_safe_fall", 12.0, AttributeModifier.Operation.ADD_VALUE);
            }
            case TURBO_SHIFT -> {
                addChaosMod(player, Attributes.BLOCK_BREAK_SPEED, "chaos_mine", 0.75, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
                addChaosMod(player, Attributes.MOVEMENT_SPEED, "chaos_speed", 0.40, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            }
            default -> {}
        }
    }

    private static void addChaosMod(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> holder,
                                    String suffix, double amount, AttributeModifier.Operation op) {
        var inst = player.getAttribute(holder);
        if (inst == null) return;
        inst.addTransientModifier(new AttributeModifier(PlayerMarketMod.id(suffix), amount, op));
    }

    public static String buildCoopSummary(MinecraftServer server, MarketSavedData data) {
        if (server == null || data == null) return "";
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (count >= 4) break;
            PlayerMarketAccount acc = data.getOrCreateAccount(sp.getUUID());
            double soldSum = 0.0;
            for (AssetType type : AssetType.values()) {
                soldSum += acc.getTotalSoldFraction(type);
            }
            double bioNorm = Math.max(5.0, Math.round((1.0 - Math.min(0.95, soldSum / AssetType.values().length)) * 100.0));
            if (count > 0) sb.append('|');
            sb.append(sp.getName().getString().replace("|", "").replace(":", ""))
                    .append(':')
                    .append((int) bioNorm)
                    .append(':')
                    .append(acc.getCrBalance());
            count++;
        }
        return sb.toString();
    }
}
