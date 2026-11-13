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
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
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
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.Merchant;

import java.util.*;

/**
 * Synchronized Corporate Chaos Director tailored for dynamic co-op & solo sessions.
 * Features varied gameplay directives, strict single-creature pacing, and true evasion AI.
 */
public final class CorporateChaosDirector {

    public enum ChaosDirective {
        HOT_POTATO("hot_potato", "ГОРЯЧАЯ КАРТОШКА HR",
                "Бомба-контракт тикает! Передай другу (ПКМ/вплотную) или потушись в воде / пробеги 32м!", 300, false),
        SWAP_ROULETTE("swap_roulette", "ТЕЛЕПОРТ-РОКИРОВКА",
                "Сбой HR-навигации: через 3 секунды все поменяются местами (в соло — рывок)!", 120, false),
        FLOOR_LAVA_PANIC("floor_lava", "ПОЛ — ЭТО АУДИТ-ЛАВА!",
                "Не стой на месте дольше 2 сек! Каждый ожог — штраф -5% (от 120 CR)!", 360, false),
        MAGNET_PULSE("magnet_pulse", "МАГНИТНЫЙ ПЫЛЕСОС",
                "Полярность изменена: притягивай весь лут в радиусе 14м и отталкивай врагов!", 400, false),
        TAX_AUDIT_FREEZE("tax_audit", "НАЛОГОВЫЙ РЕЙД HR",
                "Камера HR следит! 0 статов = штраф -18% (от 450 CR)! Продай стат ради кэшбэка!", 260, false),
        SUPPLY_OR_MIMIC("supply_crate", "ГРУЗ С НЕБА 3D",
                "Сброшен 3D Контейнер Asset Corp! Открой ПКМ/ударом — алмазы или пранк HR!", 520, false),
        VAULT_HEIST("vault_heist", "ВЗЛОМ БАНКОМАТА 3D",
                "Сброшен 3D Банкомат! Пробей 6 раз (ПКМ/удар) за 22 сек — иначе штраф -10%!", 440, false),
        DRONE_STRIKE("drone_strike", "ОРБИТАЛЬНЫЙ ДРОН HR",
                "3D Дрон с вращающимся винтом навёл красный лазер! Выбеги из круга до разряда (x3)!", 340, false),
        BOUNTY_HUNT("bounty_hunt", "ОХОТА ЗА КОРОНОЙ 3D",
                "3D Корона на лидере (отбери ПКМ/ударом!) или лови Кот в Мешке в соло!", 380, false),
        SCHIZO_AUDIT("schizo_audit", "ПАРАНОЙЯ ШРЕДИНГЕРА",
                "Крипер сзади?! Не дёргайся — это тест нервов HR (+140 CR, без взрывов)!", 280, false),
        CONFISCATION_SALE("confiscation_sale", "ОПИСЬ ИМУЩЕСТВА",
                "Физ-аудит приставов! Подпрыгни 6 раз за 13 сек — иначе изъятие вещей и пошлина!", 260, false),
        BLACKOUT_PARANOIA("blackout_paranoia", "ОТКЛЮЧЕНИЕ СВЕТА",
                "Авария на подстанции: мрак и шорохи в тени. Выдержите смену ради премии!", 340, false),
        MINI_AUDIT("mini_audit", "МИНИ-ФОРМАТ (x0.48)",
                "Урезаны хитбоксы: вы вдвое меньше, но носитесь на 30% быстрее!", 660, false),
        GIANT_EGO("giant_ego", "ГИГАНТ БЮДЖЕТА (x1.65)",
                "Повышение: рост x1.65, шаг 1.5 блока, урон +30%, анти-откидывание!", 660, false),
        ZERO_GRAVITY("zero_gravity", "ЛУННАЯ ГРАВИТАЦИЯ",
                "Гравитация отключена за долги! Прыгайте в стратосферу без урона!", 640, false),
        BOUNCY_FLOOR("bouncy_floor", "ОФИСНЫЙ БАТУТ HR",
                "Пружинный пол активирован: отскакивайте от земли до небес!", 560, false),
        HR_WORKOUT("hr_workout", "АВРАЛ: ЗАРЯДКА 8x SHIFT!",
                "Тимбилдинг! Присядьте (Shift) 8 раз за 22 сек — или штраф -12% (от 250 CR)!", 440, false),
        TURBO_SHIFT("turbo_shift", "ЗОЛОТАЯ ЛИХОРАДКА",
                "Добыча +85%, бег +40%, бонус CR и самородки за каждую выкопанную руду!", 760, false),
        COLLECTOR_RAID("collector_raid", "ВИЗИТ КОЛЛЕКТОРА",
                "Одиночный рейд Коллектора Asset Corp! Победите ради CR, иначе штраф -10%!", 460, true),
        SKY_PARCEL("sky_parcel", "КУРИНЫЙ АВИАДРОП",
                "Одиночная курица-курьер падает с посылкой! Перехватите груз!", 420, true),
        PIGGY_BANK("piggy_bank", "БЕГЛАЯ СВИНКА-КОПИЛКА",
                "Пугливая Свинка-Копилка убегает! Догоните (ПКМ/удар) до побега!", 520, true),
        RAINBOW_PINATA("rainbow_pinata", "РАДУЖНАЯ ОВЦА-ПИНЬЯТА",
                "Шустрая овца jeb_! Стукните 3 раза за печенье, конфетти и награду!", 600, true),
        BRIBE_INSPECTOR("bribe_inspector", "ГОЛОДНЫЙ ИНСПЕКТОР",
                "Ревизор просит перекус! Кликните ПКМ с ЛЮБОЙ едой или изумрудом!", 640, true),
        ALLAY_BONUS("allay_bonus", "ВЕРИТИ-КОЛОБОК!",
                "Жёлтый Колобок Верити катится по земле! Догони (ПКМ/удар) ради награды!", 520, false);

        private final String id;
        private final String title;
        private final String description;
        private final int durationTicks;
        private final boolean spawnsEntity;

        ChaosDirective(String id, String title, String description, int durationTicks, boolean spawnsEntity) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.durationTicks = durationTicks;
            this.spawnsEntity = spawnsEntity;
        }

        public String getId() { return id; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public int getDurationTicks() { return durationTicks; }
        public boolean isSpawnsEntity() { return spawnsEntity; }

        public static Optional<ChaosDirective> byId(String id) {
            if (id == null) return Optional.empty();
            if ("verity_kolobok".equalsIgnoreCase(id)) return Optional.of(ALLAY_BONUS);
            return Arrays.stream(values()).filter(d -> d.id.equalsIgnoreCase(id)).findFirst();
        }
    }

    public static final int WORKOUT_TARGET_SQUATS = 8;
    public static final int CONFISCATION_TARGET_JUMPS = 6;
    public static final int VAULT_TARGET_HITS = 6;
    public static final int CAPSULE_TARGET_HITS = 2;

    private enum MobKind {
        COURIER("playermarket_courier"),
        PIGGY("playermarket_piggy"),
        PINATA("playermarket_pinata"),
        INSPECTOR("playermarket_inspector"),
        COLLECTOR("playermarket_collector"),
        CRATE("playermarket_crate");

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
    private static final java.util.Deque<String> RECENT_DIRECTIVES = new java.util.ArrayDeque<>();
    private static int lastCompanionStage = 0;
    private static long nextStageCompanionTick = 0L;
    private static long mobSpawnCooldownUntil = 0L;
    private static ServerPlayer priorityTargetOverride = null;
    private static long manualSpawnReadyTick = 0L;
    private static int manualSpawnCycle = 0;

    // Runtime state for interactive non-mob Chaos Directives (reset cleanly between directives & worlds)
    private static long directiveStartTick = 0L;
    private static UUID hotPotatoHolderId = null;
    private static long hotPotatoPassCooldownUntil = 0L;
    private static long hotPotatoLastBeepTick = 0L;
    private static double hotPotatoRunDist = 0.0;
    private static double hotPotatoLastX = 0.0;
    private static double hotPotatoLastZ = 0.0;
    private static boolean swapExecuted = false;
    private static final Map<UUID, Integer> FLOOR_IDLE_TICKS = new HashMap<>();
    private static final Map<UUID, BlockPos> FLOOR_LAST_POS = new HashMap<>();
    private static final Set<UUID> FLOOR_PENALIZED = new HashSet<>();
    private static UUID bountyCrownHolderId = null;
    private static long bountyTransferCooldownUntil = 0L;
    private static UUID droneTargetId = null;
    private static long droneNextStrikeTick = 0L;
    private static boolean droneWarnedCurrentWave = false;
    private static long schizoNextCueTick = 0L;
    private static int atmHitsLanded = 0;
    private static int capsuleHitsLanded = 0;

    private CorporateChaosDirector() {
    }

    public static void resetRuntimeState() {
        TRACKED_MOBS.clear();
        LAST_TAP_TICK.clear();
        lastCompletedDirectiveId = "";
        RECENT_DIRECTIVES.clear();
        lastCompanionStage = 0;
        nextStageCompanionTick = 0L;
        mobSpawnCooldownUntil = 0L;
        priorityTargetOverride = null;
        manualSpawnReadyTick = 0L;
        manualSpawnCycle = 0;
        clearDirectiveRuntimeState();
    }

    private static void clearDirectiveRuntimeState() {
        directiveStartTick = 0L;
        hotPotatoHolderId = null;
        hotPotatoPassCooldownUntil = 0L;
        hotPotatoLastBeepTick = 0L;
        hotPotatoRunDist = 0.0;
        hotPotatoLastX = 0.0;
        hotPotatoLastZ = 0.0;
        swapExecuted = false;
        FLOOR_IDLE_TICKS.clear();
        FLOOR_LAST_POS.clear();
        FLOOR_PENALIZED.clear();
        bountyCrownHolderId = null;
        bountyTransferCooldownUntil = 0L;
        droneTargetId = null;
        droneNextStrikeTick = 0L;
        droneWarnedCurrentWave = false;
        schizoNextCueTick = 0L;
        atmHitsLanded = 0;
        capsuleHitsLanded = 0;
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
        ChaosProp3DManager.clearAll(server);
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

        // 1. Tick active courier/piggy/pinata/inspector/allay special entities with real flee AI
        tickTrackedMobs(server, data, campaign, gameTime);

        // 2. Tick animated 3D Display & Interaction props
        ChaosProp3DManager.tickAll(server, data, campaign, gameTime);

        // 3. Tick active interactive Chaos mechanics (Hot Potato, 3D ATM, Drone, Crown, Lava, Magnet, etc.)
        String curId = data.getActiveChaosId();
        if (!"NONE".equals(curId)) {
            if (gameTime >= data.getActiveChaosEndsTick()) {
                endActiveDirective(server, data, campaign, gameTime, true);
            } else {
                tickActiveDirectiveMechanics(server, data, campaign, curId, gameTime);
            }
        }

        // 3. Periodic next directive spawn when none is active (clamp stale timers from older saves)
        if ("NONE".equals(data.getActiveChaosId())) {
            long maxAllowedNext = gameTime + Math.max(400L, MarketConfig.get().chaosIntervalMaxTicks);
            if (data.getNextChaosTick() > maxAllowedNext) {
                data.setNextChaosTick(gameTime + 120L);
            }
            if (gameTime >= data.getNextChaosTick() && !server.getPlayerList().getPlayers().isEmpty()) {
                ChaosDirective next = pickNextDirective(server, gameTime);
                triggerDirective(server, next);
            }
        }

        // 4. Stage companion encounters (never stacks if any chaos/companion mob is already active!)
        tickStageCompanionEvents(server, data, campaign, gameTime);
    }

    private static void tickStageCompanionEvents(MinecraftServer server, MarketSavedData data, SharedCampaignData campaign, long gameTime) {
        int stage = campaign.getCurrentStage();
        if (stage < 1 || stage > 5 || server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }

        if (stage != lastCompanionStage) {
            lastCompanionStage = stage;
            nextStageCompanionTick = gameTime + 200L; // ~10s after entering a stage
            return;
        }
        if (nextStageCompanionTick <= 0L || gameTime < nextStageCompanionTick) {
            return;
        }
        // Strictly prevent stacked animals/entities in the world
        if (hasAnyLiveTrackedMob(server) || gameTime < mobSpawnCooldownUntil) {
            nextStageCompanionTick = gameTime + 200L;
            return;
        }

        boolean spawned = false;
        switch (stage) {
            case 1 -> {
                if (!campaign.isTaskCompleted("s1_piggy_catch")) {
                    spawnPiggyBank(server, gameTime);
                    spawned = true;
                }
            }
            case 2 -> {
                if (!campaign.isTaskCompleted("s2_pinata_audit")) {
                    spawnRainbowPinata(server, gameTime);
                    spawned = true;
                }
            }
            case 3 -> {
                if (!campaign.isTaskCompleted("s3_inspector_bribe")) {
                    spawnBribeInspector(server, gameTime);
                    spawned = true;
                }
            }
            case 4 -> {
                if (!campaign.isTaskCompleted("s4_sky_courier")) {
                    spawnSkyParcelCourier(server, gameTime);
                    spawned = true;
                }
            }
            case 5 -> {
                if (!campaign.isTaskCompleted("s5_collector_clash")) {
                    if (server.overworld().getDifficulty() == Difficulty.PEACEFUL) {
                        spawnVerityKolobok(server, gameTime);
                    } else {
                        spawnCollectorRaid(server, data, gameTime);
                    }
                    spawned = true;
                } else if (!campaign.isTaskCompleted("s5_ballistics_check")) {
                    spawnVerityKolobok(server, gameTime);
                    spawned = true;
                }
            }
        }

        nextStageCompanionTick = gameTime + (spawned ? 1500L : 400L);
    }

    private static boolean hasAnyLiveTrackedMob(MinecraftServer server) {
        Iterator<Map.Entry<UUID, TrackedMob>> it = TRACKED_MOBS.entrySet().iterator();
        while (it.hasNext()) {
            TrackedMob tm = it.next().getValue();
            Entity e = findEntity(server, tm.entityId);
            if (e != null && e.isAlive()) {
                return true;
            }
            it.remove();
        }
        return false;
    }

    private static long scaleFine(PlayerMarketAccount acc, long minBase, double pct) {
        long bal = acc != null ? Math.max(0L, acc.getCrBalance()) : 0L;
        return Math.max(minBase, Math.round(bal * pct));
    }

    private static ChaosDirective pickNextDirective(MinecraftServer server, long gameTime) {
        var rng = server.overworld().getRandom();
        boolean canSpawnMob = !hasAnyLiveTrackedMob(server) && gameTime >= mobSpawnCooldownUntil;

        // First showcase directive guarantees a vivid 3D prop / bomb / drone Chaos event!
        if (lastCompletedDirectiveId == null || lastCompletedDirectiveId.isEmpty()) {
            ChaosDirective[] starters = {
                    ChaosDirective.SUPPLY_OR_MIMIC,
                    ChaosDirective.HOT_POTATO,
                    ChaosDirective.VAULT_HEIST,
                    ChaosDirective.DRONE_STRIKE
            };
            return starters[rng.nextInt(starters.length)];
        }

        // Streamer-Chaos weighted pool: 3D Bombs, Crates, ATMs, Drones & Verity dominate (~73%)
        // while Entropy-style recency memory prevents any of the last 3 directives from repeating.
        List<ChaosDirective> pool = new ArrayList<>();
        for (ChaosDirective d : ChaosDirective.values()) {
            if (d.getId().equals(lastCompletedDirectiveId) || RECENT_DIRECTIVES.contains(d.getId())) continue;
            if (d == ChaosDirective.COLLECTOR_RAID && server.overworld().getDifficulty() == Difficulty.PEACEFUL) continue;
            if (d.isSpawnsEntity() && !canSpawnMob) continue;

            int weight = switch (d) {
                case SUPPLY_OR_MIMIC, HOT_POTATO, DRONE_STRIKE, VAULT_HEIST -> 10;
                case ALLAY_BONUS, BOUNTY_HUNT -> 7;
                case HR_WORKOUT, FLOOR_LAVA_PANIC, TAX_AUDIT_FREEZE, CONFISCATION_SALE,
                        PIGGY_BANK, RAINBOW_PINATA, SKY_PARCEL -> 3;
                default -> 1;
            };
            for (int w = 0; w < weight; w++) {
                pool.add(d);
            }
        }
        if (pool.isEmpty()) {
            return ChaosDirective.HOT_POTATO;
        }
        return pool.get(rng.nextInt(pool.size()));
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

        clearDirectiveRuntimeState();
        ChaosProp3DManager.clearAll(server);
        directiveStartTick = gameTime;
        long endsAt = gameTime + directive.getDurationTicks();
        data.setActiveChaos(directive.getId(), endsAt);
        lastCompletedDirectiveId = directive.getId();
        RECENT_DIRECTIVES.addLast(directive.getId());
        while (RECENT_DIRECTIVES.size() > 3) {
            RECENT_DIRECTIVES.removeFirst();
        }

        int minInt = Math.max(160, MarketConfig.get().chaosIntervalMinTicks);
        int maxInt = Math.max(minInt + 80, MarketConfig.get().chaosIntervalMaxTicks);
        long nextStart = endsAt + minInt + server.overworld().getRandom().nextInt(maxInt - minInt + 1);
        data.setNextChaosTick(nextStart);

        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            campaign.setObjective("chaos_sq_" + sp.getUUID(), 0);
            campaign.setObjective("chaos_jmp_" + sp.getUUID(), 0);
        }

        switch (directive) {
            case COLLECTOR_RAID -> spawnCollectorRaid(server, data, gameTime);
            case SKY_PARCEL -> spawnSkyParcelCourier(server, gameTime);
            case PIGGY_BANK -> spawnPiggyBank(server, gameTime);
            case RAINBOW_PINATA -> spawnRainbowPinata(server, gameTime);
            case BRIBE_INSPECTOR -> spawnBribeInspector(server, gameTime);
            case ALLAY_BONUS -> spawnVerityKolobok(server, gameTime);
            case SUPPLY_OR_MIMIC -> spawnSupplyCrate(server, data, gameTime);
            case VAULT_HEIST -> initVaultHeist(server, data, gameTime);
            case DRONE_STRIKE -> initDroneStrike(server, data, gameTime);
            case BOUNTY_HUNT -> initBountyHunt(server, data, gameTime);
            case SCHIZO_AUDIT -> initSchizoAudit(server, gameTime);
            case HOT_POTATO -> initHotPotato(server, data, gameTime);
            case TAX_AUDIT_FREEZE -> initTaxAudit(server, data, gameTime);
            default -> {}
        }

        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            MarketAttributeApplier.syncPlayerModifiers(sp);
            sp.sendSystemMessage(Component.literal("§8[§6ДИРЕКТИВА ASSET CORP§8] §e§l" + directive.getTitle() + "\n§7" + directive.getDescription()));
            sp.sendOverlayMessage(Component.literal("§6§l⚡ " + directive.getTitle() + " ⚡"));
            sendDirectiveTitle(sp, "§6§l⚡ " + directive.getTitle(), "§f" + truncateSubtitle(directive.getDescription(), 54));
            sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                    SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.85f, 1.15f);
        }
        MarketNetworking.broadcastSync(server, "§6Директива: " + directive.getTitle());
    }

    private static void sendDirectiveTitle(ServerPlayer sp, String title, String subtitle) {
        if (sp == null || sp.connection == null) return;
        sp.connection.send(new ClientboundSetTitlesAnimationPacket(4, 34, 8));
        sp.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title)));
        sp.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(subtitle)));
    }

    private static String truncateSubtitle(String s, int maxLen) {
        if (s == null || s.length() <= maxLen) return s == null ? "" : s;
        return s.substring(0, maxLen - 1) + "…";
    }

    private static void initHotPotato(MinecraftServer server, MarketSavedData data, long gameTime) {
        ServerPlayer holder = pickTargetPlayer(server, data);
        if (holder == null) return;
        hotPotatoHolderId = holder.getUUID();
        hotPotatoLastX = holder.getX();
        hotPotatoLastZ = holder.getZ();
        hotPotatoRunDist = 0.0;
        hotPotatoPassCooldownUntil = gameTime + 20L;
        hotPotatoLastBeepTick = gameTime;
        ChaosProp3DManager.spawnOrUpdateBomb(server, holder, 15, gameTime);
        holder.sendSystemMessage(Component.literal("§c§l💣 НА ВАС ПОВЕШЕНА 3D БОМБА HR! §eПрыгните в воду, пробегите 32м или передайте другу (ПКМ)!"));
        holder.level().playSound(null, holder.getX(), holder.getY(), holder.getZ(),
                SoundEvents.TNT_PRIMED, SoundSource.PLAYERS, 1.0f, 1.1f);
    }

    private static void initTaxAudit(MinecraftServer server, MarketSavedData data, long gameTime) {
        ServerPlayer cameraSubject = null;
        boolean subjectCompliant = true;
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            PlayerMarketAccount acc = data.getOrCreateAccount(sp.getUUID());
            if (sp.isSpectator() || StoryManager.isProgramTerminated(data.getCampaignData(), acc)) continue;
            if (acc.getSoldAssetCount() <= 0) {
                cameraSubject = sp;
                subjectCompliant = false;
                sp.addEffect(new MobEffectInstance(MobEffects.GLOWING, 240, 0, false, false));
                sp.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 160, 0, false, false));
                long estFine = scaleFine(acc, 450L, 0.18);
                sp.sendSystemMessage(Component.literal("§c§l⚠ НАЛОГОВЫЙ РЕЙД: §7У вас §c0 проданных статов§7! Откройте [M]->ПРОДАЖА за 13 сек или спишут §e-" + estFine + " CR §7(18%)!"));
            } else {
                if (cameraSubject == null) cameraSubject = sp;
                sp.addEffect(new MobEffectInstance(MobEffects.SPEED, 200, 0, false, false));
                sp.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, 0, false, false));
                sp.sendSystemMessage(Component.literal("§a§l✔ НАЛОГОВЫЙ РЕЙД: §7Ваш био-залог подтверждён — ждите кэшбэк в конце проверки!"));
            }
        }
        if (cameraSubject != null) {
            ChaosProp3DManager.spawnCctvFollower(server, cameraSubject, subjectCompliant, gameTime);
        }
    }

    private static void initVaultHeist(MinecraftServer server, MarketSavedData data, long gameTime) {
        ServerPlayer anchor = pickTargetPlayer(server, data);
        if (anchor == null || !(anchor.level() instanceof ServerLevel sl)) return;
        BlockPos pos = findForwardGroundPos(sl, anchor, 3, 6);
        atmHitsLanded = 0;
        ChaosProp3DManager.spawnAtmSafe(sl, pos, gameTime);
        sl.playSound(null, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.9f, 0.9f);
        for (ServerPlayer sp : sl.players()) {
            sp.sendSystemMessage(Component.literal("§8[§aВЗЛОМ 3D§8] §aСброшен Банкомат HR у §fX:" + pos.getX() + " Z:" + pos.getZ() + "§a! Пробей 6 раз (ПКМ/удар)!"));
        }
    }

    private static void initDroneStrike(MinecraftServer server, MarketSavedData data, long gameTime) {
        ServerPlayer target = pickTargetPlayer(server, data);
        if (target == null) return;
        droneTargetId = target.getUUID();
        droneNextStrikeTick = gameTime + 90L; // 4.5s per strike wave (3 waves total inside 340t)
        droneWarnedCurrentWave = false;
        ChaosProp3DManager.spawnAuditDrone(server, target, gameTime);
        target.level().playSound(null, target.getX(), target.getY() + 3.0, target.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.9f, 1.5f);
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            sp.sendSystemMessage(Component.literal("§8[§cДРОН HR§8] §c3D Дрон-коллектор повис над §f" + target.getName().getString() + "§c! Не стойте в красном кольце на земле!"));
        }
    }

    private static void initBountyHunt(MinecraftServer server, MarketSavedData data, long gameTime) {
        List<ServerPlayer> active = new ArrayList<>();
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            PlayerMarketAccount acc = data.getOrCreateAccount(sp.getUUID());
            if (!sp.isSpectator() && sp.isAlive() && !StoryManager.isProgramTerminated(data.getCampaignData(), acc)) {
                active.add(sp);
            }
        }
        if (active.size() >= 2) {
            // Co-op mode: crown the wealthiest player -> others steal with ПКМ / punch!
            ServerPlayer richest = active.getFirst();
            long bestCr = -1L;
            for (ServerPlayer sp : active) {
                long bal = data.getOrCreateAccount(sp.getUUID()).getCrBalance();
                if (bal > bestCr) {
                    bestCr = bal;
                    richest = sp;
                }
            }
            bountyCrownHolderId = richest.getUUID();
            bountyTransferCooldownUntil = gameTime + 20L;
            ChaosProp3DManager.spawnOrUpdateBountyCrown(server, richest, 19, gameTime);
            for (ServerPlayer sp : active) {
                sp.sendSystemMessage(Component.literal("§8[§eОХОТА 3D§8] §eЗолотая 3D Корона на игроке §f" + richest.getName().getString()
                        + "§e! Коснись его (ПКМ/удар), чтобы перехватить +240 CR!"));
            }
        } else if (!active.isEmpty()) {
            // Solo mode: spawn jumping Contraband Mystery Capsule ("Кот в мешке")!
            ServerPlayer solo = active.getFirst();
            if (solo.level() instanceof ServerLevel sl) {
                BlockPos pos = findForwardGroundPos(sl, solo, 4, 6);
                capsuleHitsLanded = 0;
                ChaosProp3DManager.spawnMysteryCapsule(sl, pos, gameTime);
                sl.playSound(null, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                        SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.3f);
                solo.sendSystemMessage(Component.literal("§8[§dКОТ В МЕШКЕ 3D§8] §dКапсула удачи скачет у §fX:" + pos.getX() + " Z:" + pos.getZ()
                        + "§d! Поймай её 2 раза (ПКМ/удар)!"));
            }
        }
    }

    private static void initSchizoAudit(MinecraftServer server, long gameTime) {
        schizoNextCueTick = gameTime + 25L;
    }

    private static void tickActiveDirectiveMechanics(MinecraftServer server, MarketSavedData data, SharedCampaignData campaign,
                                                     String curId, long gameTime) {
        Optional<ChaosDirective> dirOpt = ChaosDirective.byId(curId);
        if (dirOpt.isEmpty()) return;
        ChaosDirective dir = dirOpt.get();
        long elapsed = gameTime - directiveStartTick;
        long remaining = Math.max(0L, data.getActiveChaosEndsTick() - gameTime);

        switch (dir) {
            case HOT_POTATO -> tickHotPotato(server, data, campaign, gameTime, remaining);
            case SWAP_ROULETTE -> tickSwapRoulette(server, data, elapsed, gameTime);
            case FLOOR_LAVA_PANIC -> tickFloorLava(server, data, gameTime);
            case MAGNET_PULSE -> tickMagnetPulse(server, gameTime);
            case BLACKOUT_PARANOIA -> tickBlackoutParanoia(server, gameTime);
            case DRONE_STRIKE -> tickDroneStrike(server, data, gameTime);
            case BOUNTY_HUNT -> tickBountyHunt(server, data, gameTime, remaining);
            case SCHIZO_AUDIT -> tickSchizoAudit(server, gameTime);
            default -> {}
        }
    }

    private static void tickDroneStrike(MinecraftServer server, MarketSavedData data, long gameTime) {
        if (droneTargetId == null) return;
        ServerPlayer target = server.getPlayerList().getPlayer(droneTargetId);
        if (target == null || !target.isAlive() || !(target.level() instanceof ServerLevel sl)) return;

        long ticksUntil = droneNextStrikeTick - gameTime;
        if (!droneWarnedCurrentWave && ticksUntil <= 38L && ticksUntil > 0L) {
            droneWarnedCurrentWave = true;
            ChaosProp3DManager.setDroneLocked(true, target.getY());
            sl.playSound(null, target.getX(), target.getY() + 2.0, target.getZ(),
                    SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 1.0f, 1.8f);
            target.sendOverlayMessage(Component.literal("§c§l🔒 ЗОНА ЗАФИКСИРОВАНА! ВЫБЕГИ ИЗ КРУГА!"));
        }

        if (gameTime >= droneNextStrikeTick) {
            droneNextStrikeTick = gameTime + 90L;
            droneWarnedCurrentWave = false;
            var dronePos = ChaosProp3DManager.getDronePos();
            ChaosProp3DManager.setDroneLocked(false, target.getY());
            if (dronePos == null) return;

            sl.sendParticles(ParticleTypes.EXPLOSION, dronePos.x, target.getY() + 0.5, dronePos.z, 2, 0.3, 0.2, 0.3, 0.05);
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, dronePos.x, target.getY() + 0.8, dronePos.z, 24, 1.6, 0.8, 1.6, 0.12);
            sl.playSound(null, dronePos.x, target.getY(), dronePos.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.8f, 1.6f);

            double distSq = (target.getX() - dronePos.x) * (target.getX() - dronePos.x)
                    + (target.getZ() - dronePos.z) * (target.getZ() - dronePos.z);
            PlayerMarketAccount acc = data.getOrCreateAccount(target.getUUID());
            if (distSq <= 3.0 * 3.0) {
                long fine = acc.forcePenaltyCr(scaleFine(acc, 240L, 0.08));
                acc.addTransaction(gameTime, "УДАР ДРОНА HR", "Попадание разряда", "Не успел выбежать из зафиксированного круга", -fine, 0);
                if (target.getHealth() > 4.0f) {
                    target.hurtServer(sl, sl.damageSources().lightningBolt(), 3.0f);
                }
                target.setDeltaMovement(target.getDeltaMovement().x, 0.45, target.getDeltaMovement().z);
                target.needsSync = true;
                target.sendOverlayMessage(Component.literal("§c⚡ ПОДСТРЕЛЕН ДРОНОМ HR! §e-" + fine + " CR"));
            } else {
                long bonus = MarketAttributeApplier.scaleBioCr(acc, 120L);
                acc.addCr(bonus);
                target.sendOverlayMessage(Component.literal("§a✔ УКЛОНЕНИЕ ОТ ДРОНА! §e+" + bonus + " CR"));
            }
            data.setDirty();
        }
    }

    private static void tickBountyHunt(MinecraftServer server, MarketSavedData data, long gameTime, long remaining) {
        if (bountyCrownHolderId == null) return;
        ServerPlayer holder = server.getPlayerList().getPlayer(bountyCrownHolderId);
        if (holder == null || !holder.isAlive() || holder.isSpectator()) {
            ServerPlayer replacement = pickTargetPlayer(server, data);
            if (replacement != null) {
                bountyCrownHolderId = replacement.getUUID();
                holder = replacement;
            } else {
                bountyCrownHolderId = null;
                return;
            }
        }
        int sec = (int) Math.max(1L, (remaining + 19L) / 20L);
        ChaosProp3DManager.spawnOrUpdateBountyCrown(server, holder, sec, gameTime);
    }

    private static void tickSchizoAudit(MinecraftServer server, long gameTime) {
        if (gameTime < schizoNextCueTick) return;
        var rng = server.overworld().getRandom();
        schizoNextCueTick = gameTime + 35L + rng.nextInt(35);

        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (sp.isSpectator() || !sp.isAlive() || !(sp.level() instanceof ServerLevel sl)) continue;
            var back = sp.getLookAngle().scale(-1.9);
            double sx = sp.getX() + back.x;
            double sy = sp.getY();
            double sz = sp.getZ() + back.z;
            int roll = rng.nextInt(4);
            switch (roll) {
                case 0 -> {
                    sl.playSound(null, sx, sy, sz, SoundEvents.CREEPER_PRIMED, SoundSource.HOSTILE, 1.0f, 1.0f);
                    sp.sendOverlayMessage(Component.literal("§c§lТССССС... СЗАДИ?!"));
                }
                case 1 -> sl.playSound(null, sx, sy, sz, SoundEvents.SKELETON_SHOOT, SoundSource.HOSTILE, 0.9f, 1.1f);
                case 2 -> sl.playSound(null, sx, sy + 4.0, sz, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.65f, 0.9f);
                default -> sl.playSound(null, sx, sy, sz, SoundEvents.ENDERMAN_SCREAM, SoundSource.HOSTILE, 0.7f, 1.2f);
            }
        }
    }

    private static void tickHotPotato(MinecraftServer server, MarketSavedData data, SharedCampaignData campaign,
                                      long gameTime, long remaining) {
        if (hotPotatoHolderId == null) return;
        ServerPlayer holder = server.getPlayerList().getPlayer(hotPotatoHolderId);
        if (holder == null || !holder.isAlive() || holder.isSpectator()) {
            ServerPlayer replacement = pickTargetPlayer(server, data);
            if (replacement != null) {
                hotPotatoHolderId = replacement.getUUID();
                hotPotatoLastX = replacement.getX();
                hotPotatoLastZ = replacement.getZ();
                hotPotatoRunDist = 0.0;
            } else {
                hotPotatoHolderId = null;
            }
            return;
        }

        // Defuse condition 1: water bath
        if (holder.isInWater()) {
            defuseHotPotato(server, holder, data, campaign, gameTime, "Обезврежено в воде!");
            return;
        }

        // Defuse condition 2: sprint/run 32 blocks total
        double dx = holder.getX() - hotPotatoLastX;
        double dz = holder.getZ() - hotPotatoLastZ;
        double step = Math.hypot(dx, dz);
        if (step > 0.05 && step < 12.0) {
            hotPotatoRunDist += step;
        }
        hotPotatoLastX = holder.getX();
        hotPotatoLastZ = holder.getZ();
        if (hotPotatoRunDist >= 32.0) {
            defuseHotPotato(server, holder, data, campaign, gameTime, "Сброшено на бегу (32м)!");
            return;
        }

        // Proximity auto-tag pass in co-op (within 2.1 blocks after cooldown)
        if (gameTime >= hotPotatoPassCooldownUntil) {
            for (ServerPlayer other : server.getPlayerList().getPlayers()) {
                if (other == holder || !other.isAlive() || other.isSpectator()) continue;
                if (other.level() == holder.level() && other.distanceToSqr(holder) <= 4.41) {
                    transferHotPotato(holder, other, gameTime);
                    return;
                }
            }
        }

        long beepInterval = remaining <= 80L ? 5L : 10L;
        int sec = (int) Math.max(1L, (remaining + 19L) / 20L);
        ChaosProp3DManager.spawnOrUpdateBomb(server, holder, sec, gameTime);
        if (gameTime - hotPotatoLastBeepTick >= beepInterval) {
            hotPotatoLastBeepTick = gameTime;
            if (holder.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.FLAME, holder.getX(), holder.getY() + 1.2, holder.getZ(), 5, 0.25, 0.25, 0.25, 0.02);
                float pitch = remaining <= 80L ? 1.55f : 0.95f;
                sl.playSound(null, holder.getX(), holder.getY(), holder.getZ(),
                        SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.9f, pitch);
            }
            holder.sendOverlayMessage(Component.literal(String.format(Locale.ROOT,
                    "§c§l💣 БОМБА У ТЕБЯ! §e%dс §8| §7Бег: §f%.0f/32м §7или §bВода / Друг", sec, hotPotatoRunDist)));
        }
    }

    public static boolean handleHotPotatoPass(ServerPlayer passer, ServerPlayer receiver, MarketSavedData data, long gameTime) {
        if (passer == null || receiver == null || passer == receiver || hotPotatoHolderId == null) return false;
        if (!ChaosDirective.HOT_POTATO.getId().equals(data.getActiveChaosId())) return false;
        if (!hotPotatoHolderId.equals(passer.getUUID()) || gameTime < hotPotatoPassCooldownUntil) return false;
        transferHotPotato(passer, receiver, gameTime);
        return true;
    }

    private static void transferHotPotato(ServerPlayer from, ServerPlayer to, long gameTime) {
        hotPotatoHolderId = to.getUUID();
        hotPotatoPassCooldownUntil = gameTime + 24L;
        hotPotatoRunDist = 0.0;
        hotPotatoLastX = to.getX();
        hotPotatoLastZ = to.getZ();
        from.sendOverlayMessage(Component.literal("§a✔ Бомба передана игроку §e" + to.getName().getString() + "§a!"));
        to.sendOverlayMessage(Component.literal("§c§l💣 " + from.getName().getString() + " ПЕРЕДАЛ ТЕБЕ БОМБУ!"));
        to.sendSystemMessage(Component.literal("§c§l💣 ВАМ ПЕРЕДАЛИ БОМБУ HR! §eБыстро в воду, бегите 32м или верните другу!"));
        to.level().playSound(null, to.getX(), to.getY(), to.getZ(),
                SoundEvents.TNT_PRIMED, SoundSource.PLAYERS, 1.0f, 1.3f);
        if (to.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SMOKE, to.getX(), to.getY() + 1.0, to.getZ(), 14, 0.3, 0.3, 0.3, 0.06);
        }
    }

    private static void defuseHotPotato(MinecraftServer server, ServerPlayer hero, MarketSavedData data,
                                        SharedCampaignData campaign, long gameTime, String reason) {
        hotPotatoHolderId = null;
        PlayerMarketAccount acc = data.getOrCreateAccount(hero.getUUID());
        long cr = MarketAttributeApplier.scaleBioCr(acc, 140L);
        acc.addCr(cr);
        int dia = acc.getSoldAssetCount() >= 1 ? 1 : 0;
        if (dia > 0) {
            giveOrDrop(hero, new ItemStack(Items.DIAMOND, dia));
        }
        acc.addTransaction(gameTime, "БОМБА HR", "Контракт обезврежен!", reason, cr, dia);
        if (hero.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, hero.getX(), hero.getY() + 1.0, hero.getZ(), 24, 0.4, 0.5, 0.4, 0.2);
            sl.playSound(null, hero.getX(), hero.getY(), hero.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.9f, 1.3f);
        }
        hero.sendSystemMessage(Component.literal("§a§l✔ БОМБА ОБЕЗВРЕЖЕНА (" + reason + "): §e+" + cr + " CR"
                + (dia > 0 ? " §b+1 ♦" : " §7(продай стат ради ♦)")));
        endActiveDirective(server, data, campaign, gameTime, false);
    }

    private static void tickSwapRoulette(MinecraftServer server, MarketSavedData data, long elapsed, long gameTime) {
        if (swapExecuted) return;
        if (elapsed == 20L || elapsed == 40L) {
            int countDown = elapsed == 20L ? 2 : 1;
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                sp.sendOverlayMessage(Component.literal("§d§l⚡ РОКИРОВКА ЧЕРЕЗ " + countDown + "..."));
                sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                        SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.8f, countDown == 1 ? 1.5f : 1.0f);
            }
            return;
        }
        if (elapsed < 60L) return;

        swapExecuted = true;
        List<ServerPlayer> candidates = new ArrayList<>();
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            PlayerMarketAccount acc = data.getOrCreateAccount(sp.getUUID());
            if (!sp.isSpectator() && sp.isAlive() && !StoryManager.isProgramTerminated(data.getCampaignData(), acc)) {
                candidates.add(sp);
            }
        }
        if (candidates.size() >= 2) {
            record Snapshot(ServerLevel level, double x, double y, double z, float yaw, float pitch) {}
            List<Snapshot> snaps = new ArrayList<>();
            for (ServerPlayer sp : candidates) {
                snaps.add(new Snapshot((ServerLevel) sp.level(), sp.getX(), sp.getY(), sp.getZ(), sp.getYRot(), sp.getXRot()));
            }
            for (int i = 0; i < candidates.size(); i++) {
                ServerPlayer mover = candidates.get(i);
                Snapshot dst = snaps.get((i + 1) % candidates.size());
                mover.teleportTo(dst.level(), dst.x(), dst.y(), dst.z(), Set.of(), dst.yaw(), dst.pitch(), false);
                dst.level().sendParticles(ParticleTypes.PORTAL, dst.x(), dst.y() + 1.0, dst.z(), 26, 0.4, 0.6, 0.4, 0.25);
                dst.level().playSound(null, dst.x(), dst.y(), dst.z(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.9f, 1.05f);
                PlayerMarketAccount acc = data.getOrCreateAccount(mover.getUUID());
                long cr = MarketAttributeApplier.scaleBioCr(acc, 90L);
                acc.addCr(cr);
                mover.sendOverlayMessage(Component.literal("§d✦ ТЕЛЕПОРТ-РОКИРОВКА! §a+" + cr + " CR"));
            }
        } else if (candidates.size() == 1) {
            ServerPlayer solo = candidates.getFirst();
            ServerLevel sl = (ServerLevel) solo.level();
            BlockPos dest = findSafeForwardBlink(sl, solo, 7);
            solo.teleportTo(sl, dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5, Set.of(), solo.getYRot(), solo.getXRot(), false);
            sl.sendParticles(ParticleTypes.PORTAL, dest.getX() + 0.5, dest.getY() + 1.0, dest.getZ() + 0.5, 24, 0.4, 0.6, 0.4, 0.25);
            sl.playSound(null, dest.getX(), dest.getY(), dest.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.9f, 1.15f);
            PlayerMarketAccount acc = data.getOrCreateAccount(solo.getUUID());
            long cr = MarketAttributeApplier.scaleBioCr(acc, 90L);
            acc.addCr(cr);
            solo.sendOverlayMessage(Component.literal("§d✦ СОЛО-РЫВОК! §a+" + cr + " CR"));
        }
        data.setDirty();
    }

    private static void tickFloorLava(MinecraftServer server, MarketSavedData data, long gameTime) {
        if (gameTime % 5L != 0L) return;
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (sp.isSpectator() || sp.isCreative() || !sp.isAlive()) continue;
            PlayerMarketAccount acc = data.getOrCreateAccount(sp.getUUID());
            if (StoryManager.isProgramTerminated(data.getCampaignData(), acc)) continue;

            BlockPos cur = sp.blockPosition();
            BlockPos prev = FLOOR_LAST_POS.put(sp.getUUID(), cur);
            boolean standingStill = sp.onGround() && !sp.isInWater() && prev != null && prev.distSqr(cur) == 0.0
                    && sp.getDeltaMovement().horizontalDistanceSqr() < 0.002;
            int idle = standingStill ? FLOOR_IDLE_TICKS.getOrDefault(sp.getUUID(), 0) + 5 : 0;
            FLOOR_IDLE_TICKS.put(sp.getUUID(), idle);

            if (idle >= 20 && idle < 40) {
                sp.sendOverlayMessage(Component.literal("§c🔥 ПОЛ НАКАЛЯЕТСЯ! ШЕВЕЛИСЬ!"));
            } else if (idle >= 40) {
                FLOOR_IDLE_TICKS.put(sp.getUUID(), 20);
                FLOOR_PENALIZED.add(sp.getUUID());
                long fine = acc.forcePenaltyCr(scaleFine(acc, 120L, 0.05));
                if (fine > 0L) {
                    acc.addTransaction(gameTime, "ШТРАФ: ЛАВА-ПОЛ", "Простой на месте", "Штраф за бездействие (5%)", -fine, 0);
                    data.setDirty();
                }
                if (sp.level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.FLAME, sp.getX(), sp.getY() + 0.1, sp.getZ(), 14, 0.3, 0.1, 0.3, 0.05);
                    if (sp.getHealth() > 4.0f) {
                        sp.hurtServer(sl, sl.damageSources().onFire(), 2.0f);
                    }
                    sp.setDeltaMovement(sp.getDeltaMovement().x, 0.38, sp.getDeltaMovement().z);
                    sp.needsSync = true;
                    sl.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.7f, 1.3f);
                }
                sp.sendOverlayMessage(Component.literal("§4🔥 АУДИТ-ЛАВА! §e-" + fine + " CR §c— Беги / Прыгай!"));
            }
        }
    }

    private static void tickMagnetPulse(MinecraftServer server, long gameTime) {
        if (gameTime % 8L != 0L) return;
        boolean pushEnemies = (gameTime % 40L == 0L);
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (sp.isSpectator() || !sp.isAlive() || !(sp.level() instanceof ServerLevel sl)) continue;
            var box = sp.getBoundingBox().inflate(14.0);
            for (ItemEntity item : sl.getEntitiesOfClass(ItemEntity.class, box)) {
                double dx = sp.getX() - item.getX();
                double dy = (sp.getY() + 0.7) - item.getY();
                double dz = sp.getZ() - item.getZ();
                double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (d > 0.6) {
                    item.setDeltaMovement(dx / d * 0.55, dy / d * 0.40 + 0.05, dz / d * 0.55);
                    item.needsSync = true;
                }
            }
            for (ExperienceOrb orb : sl.getEntitiesOfClass(ExperienceOrb.class, box)) {
                double dx = sp.getX() - orb.getX();
                double dy = (sp.getY() + 0.7) - orb.getY();
                double dz = sp.getZ() - orb.getZ();
                double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (d > 0.6) {
                    orb.setDeltaMovement(dx / d * 0.55, dy / d * 0.40, dz / d * 0.55);
                    orb.needsSync = true;
                }
            }
            if (pushEnemies) {
                var nearBox = sp.getBoundingBox().inflate(7.5);
                for (LivingEntity mob : sl.getEntitiesOfClass(LivingEntity.class, nearBox)) {
                    if (!(mob instanceof Enemy) || mob.entityTags().contains(MobKind.COLLECTOR.tag)) continue;
                    double dx = mob.getX() - sp.getX();
                    double dz = mob.getZ() - sp.getZ();
                    double d = Math.max(0.5, Math.hypot(dx, dz));
                    mob.setDeltaMovement(dx / d * 0.75, 0.35, dz / d * 0.75);
                    mob.needsSync = true;
                }
                sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, sp.getX(), sp.getY() + 1.0, sp.getZ(), 10, 0.7, 0.4, 0.7, 0.08);
            }
        }
    }

    private static void tickBlackoutParanoia(MinecraftServer server, long gameTime) {
        if (gameTime % 60L != 0L) return;
        var rng = server.overworld().getRandom();
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (sp.isSpectator() || !sp.isAlive() || !(sp.level() instanceof ServerLevel sl)) continue;
            if (rng.nextBoolean()) {
                sp.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 70, 0, false, false));
            }
            double ox = (rng.nextDouble() - 0.5) * 6.0;
            double oz = (rng.nextDouble() - 0.5) * 6.0;
            int pick = rng.nextInt(3);
            if (pick == 0) {
                sl.playSound(null, sp.getX() + ox, sp.getY(), sp.getZ() + oz, SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 0.85f, 0.9f);
            } else if (pick == 1) {
                sl.playSound(null, sp.getX() + ox, sp.getY(), sp.getZ() + oz, SoundEvents.ENDERMAN_STARE, SoundSource.AMBIENT, 0.55f, 0.85f);
            } else {
                sl.playSound(null, sp.getX() + ox, sp.getY(), sp.getZ() + oz, SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 0.75f, 0.8f);
            }
        }
    }

    private static void endActiveDirective(MinecraftServer server, MarketSavedData data, SharedCampaignData campaign, long gameTime, boolean evaluateChallenge) {
        String curId = data.getActiveChaosId();
        Optional<ChaosDirective> dirOpt = ChaosDirective.byId(curId);

        if (evaluateChallenge && dirOpt.isPresent()) {
            switch (dirOpt.get()) {
                case HR_WORKOUT -> {
                    for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                        PlayerMarketAccount acc = data.getOrCreateAccount(sp.getUUID());
                        if (sp.isSpectator() || StoryManager.isProgramTerminated(campaign, acc)) continue;
                        int squats = campaign.getObjective("chaos_sq_" + sp.getUUID());
                        if (squats >= WORKOUT_TARGET_SQUATS) {
                            long cr = MarketAttributeApplier.scaleBioCr(acc, 220L);
                            acc.addCr(cr);
                            int dia = acc.getSoldAssetCount() >= 1 ? 1 : 0;
                            if (dia > 0) giveOrDrop(sp, new ItemStack(Items.DIAMOND, dia));
                            sp.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 160, 1, false, false));
                            acc.addTransaction(gameTime, "ДИРЕКТИВА HR", "Зарядка выполнена!", "8 приседаний по уставу", cr, dia);
                            sp.sendSystemMessage(Component.literal("§8[§aHR-ОТДЕЛ§8] §a✔ Зарядка выполнена: §e+" + cr + " CR" + (dia > 0 ? " §8| §b+1 ♦" : "") + "§a!"));
                            sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.3f);
                        } else {
                            long fine = acc.forcePenaltyCr(scaleFine(acc, 250L, 0.12));
                            acc.addTransaction(gameTime, "ШТРАФ HR", "Прогул зарядки", "Выполнено " + squats + "/" + WORKOUT_TARGET_SQUATS + " (-12%)", -fine, 0);
                            if (sp.getHealth() > 4.0f && sp.level() instanceof ServerLevel sl) {
                                sp.hurtServer(sl, sl.damageSources().lightningBolt(), 2.5f);
                            }
                            sp.sendSystemMessage(Component.literal("§8[§cHR-ОТДЕЛ§8] §c✖ Прогул гимнастики (" + squats + "/" + WORKOUT_TARGET_SQUATS + ")! Штраф §e-" + fine + " CR§c!"));
                            sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.65f, 1.5f);
                        }
                    }
                }
                case HOT_POTATO -> {
                    if (hotPotatoHolderId != null) {
                        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                            PlayerMarketAccount acc = data.getOrCreateAccount(sp.getUUID());
                            if (sp.isSpectator() || StoryManager.isProgramTerminated(campaign, acc)) continue;
                            if (sp.getUUID().equals(hotPotatoHolderId)) {
                                long fine = acc.forcePenaltyCr(scaleFine(acc, 320L, 0.15));
                                acc.addTransaction(gameTime, "ВЗРЫВ БОМБЫ HR", "Бомба не сброшена", "Штраф за удержание (15%)", -fine, 0);
                                if (sp.level() instanceof ServerLevel sl) {
                                    sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, sp.getX(), sp.getY() + 0.8, sp.getZ(), 1, 0, 0, 0, 0);
                                    sl.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.9f, 1.1f);
                                    if (sp.getHealth() > 5.0f) {
                                        sp.hurtServer(sl, sl.damageSources().explosion(null), 5.0f);
                                    }
                                    sp.setDeltaMovement(0.0, 0.65, 0.0);
                                    sp.needsSync = true;
                                }
                                sp.sendSystemMessage(Component.literal("§c💥 БУМ! Вы не успели сбросить бомбу HR: §e-" + fine + " CR §7(15%)§c!"));
                            } else {
                                long cr = MarketAttributeApplier.scaleBioCr(acc, 90L);
                                acc.addCr(cr);
                                sp.sendSystemMessage(Component.literal("§a✔ Бомба миновала вас! Премия: §e+" + cr + " CR"));
                            }
                        }
                    }
                }
                case TAX_AUDIT_FREEZE -> {
                    for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                        PlayerMarketAccount acc = data.getOrCreateAccount(sp.getUUID());
                        if (sp.isSpectator() || StoryManager.isProgramTerminated(campaign, acc)) continue;
                        if (acc.getSoldAssetCount() <= 0) {
                            long fine = acc.forcePenaltyCr(scaleFine(acc, 450L, 0.18));
                            acc.addTransaction(gameTime, "НАЛОГОВЫЙ ШТРАФ", "Отказ от био-взноса", "0 статов при проверке (18%)", -fine, 0);
                            sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.7f, 0.8f);
                            sp.sendSystemMessage(Component.literal("§c✖ АУДИТ ЗАВЕРШЁН: У вас всё ещё 0 статов! Штраф §e-" + fine + " CR §7(18%)§c. Продайте стат в [M]->ПРОДАЖА!"));
                        } else {
                            long cashback = MarketAttributeApplier.scaleBioCr(acc, 150L);
                            acc.addCr(cashback);
                            acc.addTransaction(gameTime, "КЭШБЭК HR", "Аудит пройден", "Подтверждены проданные статы", cashback, 0);
                            sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 1.2f);
                            sp.sendSystemMessage(Component.literal("§a✔ Налоговый вычет за проданные статы: §e+" + cashback + " CR§a!"));
                        }
                    }
                }
                case FLOOR_LAVA_PANIC -> {
                    for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                        PlayerMarketAccount acc = data.getOrCreateAccount(sp.getUUID());
                        if (sp.isSpectator() || StoryManager.isProgramTerminated(campaign, acc)) continue;
                        if (!FLOOR_PENALIZED.contains(sp.getUUID())) {
                            long cr = MarketAttributeApplier.scaleBioCr(acc, 110L);
                            acc.addCr(cr);
                            sp.sendSystemMessage(Component.literal("§a✔ Лава не обожгла! Награда за движение: §e+" + cr + " CR"));
                        }
                    }
                }
                case BLACKOUT_PARANOIA -> {
                    for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                        PlayerMarketAccount acc = data.getOrCreateAccount(sp.getUUID());
                        if (sp.isSpectator() || StoryManager.isProgramTerminated(campaign, acc)) continue;
                        sp.removeEffect(MobEffects.DARKNESS);
                        long cr = MarketAttributeApplier.scaleBioCr(acc, 120L);
                        acc.addCr(cr);
                        sp.sendSystemMessage(Component.literal("§a⚡ Свет восстановлен! Премия за смелость: §e+" + cr + " CR"));
                    }
                }
                case BOUNTY_HUNT -> {
                    if (bountyCrownHolderId != null) {
                        ServerPlayer winner = server.getPlayerList().getPlayer(bountyCrownHolderId);
                        if (winner != null && winner.isAlive() && !winner.isSpectator()) {
                            PlayerMarketAccount acc = data.getOrCreateAccount(winner.getUUID());
                            if (!StoryManager.isProgramTerminated(campaign, acc)) {
                                long cr = MarketAttributeApplier.scaleBioCr(acc, 240L);
                                acc.addCr(cr);
                                int dia = acc.getSoldAssetCount() >= 1 ? 1 : 0;
                                if (dia > 0) giveOrDrop(winner, new ItemStack(Items.DIAMOND, dia));
                                acc.addTransaction(gameTime, "КОРОНА ДОЛЖНИКА", "Победа в охоте!", "Удержана 3D Корона Asset Corp", cr, dia);
                                if (winner.level() instanceof ServerLevel sl) {
                                    sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, winner.getX(), winner.getY() + 1.1, winner.getZ(), 32, 0.5, 0.6, 0.5, 0.2);
                                    sl.playSound(null, winner.getX(), winner.getY(), winner.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.25f);
                                }
                                server.getPlayerList().broadcastSystemMessage(Component.literal(
                                        "§8[§eКОРОНА 3D§8] §e👑 §f" + winner.getName().getString() + " §aудержал 3D Корону и сорвал §e+" + cr + " CR"
                                                + (dia > 0 ? " §b+1 ♦" : "") + "§a!"), false);
                            }
                        }
                    }
                }
                case SCHIZO_AUDIT -> {
                    for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                        PlayerMarketAccount acc = data.getOrCreateAccount(sp.getUUID());
                        if (sp.isSpectator() || StoryManager.isProgramTerminated(campaign, acc)) continue;
                        long cr = MarketAttributeApplier.scaleBioCr(acc, 140L);
                        acc.addCr(cr);
                        acc.addTransaction(gameTime, "ТЕСТ НЕРВОВ HR", "Психика в пределах нормы", "Фальшивые криперы пережиты", cr, 0);
                        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.PLAYERS, 0.8f, 1.2f);
                        sp.sendSystemMessage(Component.literal("§a✔ Псих-тест HR пройден (криперы были фейком ха-ха): §e+" + cr + " CR"));
                    }
                }
                case CONFISCATION_SALE -> {
                    for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                        PlayerMarketAccount acc = data.getOrCreateAccount(sp.getUUID());
                        if (sp.isSpectator() || StoryManager.isProgramTerminated(campaign, acc)) continue;
                        int jumps = campaign.getObjective("chaos_jmp_" + sp.getUUID());
                        if (jumps >= CONFISCATION_TARGET_JUMPS) {
                            long cr = MarketAttributeApplier.scaleBioCr(acc, 160L);
                            acc.addCr(cr);
                            sp.addEffect(new MobEffectInstance(MobEffects.SPEED, 160, 1, false, false));
                            acc.addTransaction(gameTime, "ОПИСЬ ИМУЩЕСТВА", "Отпрыгались от пристава!", "6/6 прыжков", cr, 0);
                            sp.sendSystemMessage(Component.literal("§a✔ Приставы отстали (" + jumps + "/" + CONFISCATION_TARGET_JUMPS + "): §e+" + cr + " CR §aи Скорость II!"));
                        } else {
                            seizeRandomJunkOrFine(sp, acc, gameTime, jumps);
                        }
                    }
                }
                case VAULT_HEIST -> {
                    for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                        PlayerMarketAccount acc = data.getOrCreateAccount(sp.getUUID());
                        if (sp.isSpectator() || StoryManager.isProgramTerminated(campaign, acc)) continue;
                        long fine = acc.forcePenaltyCr(scaleFine(acc, 220L, 0.10));
                        acc.addTransaction(gameTime, "СРЫВ ВЗЛОМА HR", "Банкомат заблокирован", "Успели только " + atmHitsLanded + "/" + VAULT_TARGET_HITS, -fine, 0);
                        sp.sendSystemMessage(Component.literal("§c🏧 Банкомат HR заблокировался (" + atmHitsLanded + "/" + VAULT_TARGET_HITS + ")! Штраф тревоги: §e-" + fine + " CR§c!"));
                    }
                }
                default -> {}
            }
        }

        clearDirectiveRuntimeState();
        ChaosProp3DManager.clearAll(server);
        data.setActiveChaos("NONE", 0L);
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            campaign.setObjective("chaos_sq_" + sp.getUUID(), 0);
            campaign.setObjective("chaos_jmp_" + sp.getUUID(), 0);
            MarketAttributeApplier.syncPlayerModifiers(sp);
        }
        MarketNetworking.broadcastSync(server, "");
    }

    private static void seizeRandomJunkOrFine(ServerPlayer sp, PlayerMarketAccount acc, long gameTime, int jumps) {
        var inv = sp.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack st = inv.getItem(i);
            if (!st.isEmpty() && !st.is(com.playermarket.item.MarketAccessCardItem.MARKET_ACCESS_CARD)
                    && !st.is(com.playermarket.item.CrumpledLetterItem.CRUMPLED_LETTER)
                    && !st.isDamageableItem() && !st.is(Items.DIAMOND)) {
                int take = Math.min(8, st.getCount());
                String itemName = st.getHoverName().getString();
                st.shrink(take);
                inv.setChanged();
                long bailiffFee = acc.forcePenaltyCr(scaleFine(acc, 150L, 0.08));
                acc.addTransaction(gameTime, "ПРИСТАВЫ HR", "Изъято и пошлина: " + itemName, "Недопрыгнуто (" + jumps + "/" + CONFISCATION_TARGET_JUMPS + ")", -bailiffFee, 0);
                sp.sendSystemMessage(Component.literal("§c🔨 ОПИСЬ: §7Приставы конфисковали §f" + take + "x " + itemName
                        + " §cи списали пошлину §e-" + bailiffFee + " CR§c!"));
                return;
            }
        }
        long fine = acc.forcePenaltyCr(scaleFine(acc, 260L, 0.12));
        acc.addTransaction(gameTime, "ШТРАФ ПРИСТАВОВ", "Карманы пусты", "Прыжки " + jumps + "/" + CONFISCATION_TARGET_JUMPS + " (-12%)", -fine, 0);
        sp.sendSystemMessage(Component.literal("§c🔨 Прыжки " + jumps + "/" + CONFISCATION_TARGET_JUMPS + ": списана пошлина §e-" + fine + " CR §7(12%)§c!"));
    }

    public static boolean onPlayerJump(ServerPlayer player, MarketSavedData data, SharedCampaignData campaign) {
        if (!ChaosDirective.CONFISCATION_SALE.getId().equals(data.getActiveChaosId()) || StoryManager.isProgramTerminated(campaign, null)) {
            return false;
        }
        String key = "chaos_jmp_" + player.getUUID();
        int cur = campaign.getObjective(key);
        if (cur >= CONFISCATION_TARGET_JUMPS) return false;
        int next = cur + 1;
        campaign.setObjective(key, next);
        data.setDirty();
        if (next == CONFISCATION_TARGET_JUMPS) {
            player.sendOverlayMessage(Component.literal("§a✔ Прыжки засчитаны (" + next + "/" + CONFISCATION_TARGET_JUMPS + ")! Приставы уходят!"));
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.9f, 1.45f);
        } else {
            player.sendOverlayMessage(Component.literal("§eПрыжки от приставов: §f" + next + " / " + CONFISCATION_TARGET_JUMPS));
        }
        MarketNetworking.sendSyncToPlayer(player);
        return true;
    }

    public static boolean handleBountyCrownSteal(ServerPlayer stealer, ServerPlayer victim, MarketSavedData data, long gameTime) {
        if (stealer == null || victim == null || stealer == victim || bountyCrownHolderId == null) return false;
        if (!ChaosDirective.BOUNTY_HUNT.getId().equals(data.getActiveChaosId())) return false;
        if (!bountyCrownHolderId.equals(victim.getUUID()) || gameTime < bountyTransferCooldownUntil) return false;
        bountyCrownHolderId = stealer.getUUID();
        bountyTransferCooldownUntil = gameTime + 20L;
        long rem = Math.max(20L, data.getActiveChaosEndsTick() - gameTime);
        ChaosProp3DManager.spawnOrUpdateBountyCrown(stealer.level().getServer(), stealer, (int) (rem / 20L), gameTime);
        if (stealer.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.FIREWORK, stealer.getX(), stealer.getY() + 1.5, stealer.getZ(), 18, 0.4, 0.4, 0.4, 0.15);
            sl.playSound(null, stealer.getX(), stealer.getY(), stealer.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.35f);
        }
        stealer.sendOverlayMessage(Component.literal("§e§l👑 ТЫ ОТОБРАЛ 3D КОРОНУ у " + victim.getName().getString() + "!"));
        victim.sendOverlayMessage(Component.literal("§c§l👑 " + stealer.getName().getString() + " УКРАЛ ТВОЮ КОРОНУ!"));
        return true;
    }

    public static void onWorldPropClicked(ServerPlayer player, ChaosProp3DManager.PropKind kind,
                                          double px, double py, double pz,
                                          MarketSavedData data, SharedCampaignData campaign, long gameTime) {
        if (player == null || data == null || StoryManager.isProgramTerminated(campaign, null)) return;
        if (!(player.level() instanceof ServerLevel sl)) return;
        PlayerMarketAccount acc = data.getOrCreateAccount(player.getUUID());
        MinecraftServer server = sl.getServer();

        switch (kind) {
            case SUPPLY_CRATE -> {
                ChaosProp3DManager.removeKind(server, ChaosProp3DManager.PropKind.SUPPLY_CRATE);
                boolean isPrank = sl.getRandom().nextInt(10) < 3;
                sl.playSound(null, px, py, pz, SoundEvents.CHEST_OPEN, SoundSource.PLAYERS, 1.0f, 1.15f);
                if (isPrank) {
                    spawnDrop(sl, px, py, pz, new ItemStack(Items.COOKIE, 8));
                    spawnDrop(sl, px, py, pz, new ItemStack(Items.EXPERIENCE_BOTTLE, 4));
                    sl.sendParticles(ParticleTypes.FIREWORK, px, py + 0.5, pz, 28, 0.5, 0.5, 0.5, 0.22);
                    player.setDeltaMovement(0.0, 0.45, 0.0);
                    player.needsSync = true;
                    long cr = MarketAttributeApplier.scaleBioCr(acc, 130L);
                    acc.addCr(cr);
                    acc.addTransaction(gameTime, "ГРУЗ-ПРАНК 3D", "Мимик HR!", "Печеньки, опыт и откат", cr, 0);
                    player.sendSystemMessage(Component.literal("§d🎉 ХА-ХА! В 3D контейнере был пранк-мимик HR! Приз: §e+" + cr + " CR §dи печенье!"));
                } else {
                    if (acc.getSoldAssetCount() >= 1) {
                        spawnDrop(sl, px, py, pz, new ItemStack(Items.DIAMOND, 1));
                    }
                    spawnDrop(sl, px, py, pz, new ItemStack(Items.GOLDEN_APPLE, 1));
                    spawnDrop(sl, px, py, pz, new ItemStack(Items.IRON_INGOT, 5));
                    spawnDrop(sl, px, py, pz, new ItemStack(Items.COOKED_BEEF, 6));
                    sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, px, py + 0.5, pz, 28, 0.5, 0.5, 0.5, 0.2);
                    long cr = MarketAttributeApplier.scaleBioCr(acc, 190L);
                    acc.addCr(cr);
                    acc.addTransaction(gameTime, "СНАБЖЕНИЕ 3D", "3D Груз Asset Corp вскрыт!", "Провизия и алмазы", cr, 0);
                    player.sendSystemMessage(Component.literal("§a📦 3D ГРУЗ ВСКРЫТ: §e+" + cr + " CR§a, Яблоко и ресурсы!"));
                }
                CorporateTaskTracker.advanceContract(player, acc, "COURIER_LOOT", 1);
                StoryManager.checkAllQuests(player, acc);
                data.setDirty();
                endActiveDirective(server, data, campaign, gameTime, false);
            }
            case ATM_SAFE -> {
                atmHitsLanded++;
                ChaosProp3DManager.setAtmShake(atmHitsLanded);
                sl.playSound(null, px, py, pz, SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.85f, 1.1f + atmHitsLanded * 0.12f);
                sl.sendParticles(ParticleTypes.CRIT, px, py + 0.4, pz, 12, 0.3, 0.4, 0.3, 0.12);
                spawnDrop(sl, px, py + 0.5, pz, new ItemStack(Items.GOLD_NUGGET, 2));

                if (atmHitsLanded < VAULT_TARGET_HITS) {
                    ChaosProp3DManager.updateTextLabel(server, ChaosProp3DManager.PropKind.ATM_SAFE,
                            "§a§l[🏧 БАНКОМАТ HR: " + atmHitsLanded + "/" + VAULT_TARGET_HITS + " — БЕЙ!]");
                    player.sendOverlayMessage(Component.literal("§a🏧 Взлом Банкомата: §e" + atmHitsLanded + " / " + VAULT_TARGET_HITS));
                } else {
                    ChaosProp3DManager.removeKind(server, ChaosProp3DManager.PropKind.ATM_SAFE);
                    int dia = acc.getSoldAssetCount() >= 1 ? 1 : 0;
                    if (dia > 0) spawnDrop(sl, px, py + 0.6, pz, new ItemStack(Items.DIAMOND, dia));
                    spawnDrop(sl, px, py + 0.6, pz, new ItemStack(Items.EMERALD, 4));
                    spawnDrop(sl, px, py + 0.6, pz, new ItemStack(Items.GOLD_INGOT, 4));
                    sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, px, py + 0.7, pz, 38, 0.6, 0.6, 0.6, 0.25);
                    sl.playSound(null, px, py, pz, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.15f);

                    long cr = MarketAttributeApplier.scaleBioCr(acc, 240L);
                    acc.addCr(cr);
                    acc.addTransaction(gameTime, "ДЖЕКПОТ БАНКОМАТА 3D", "Сейф вскрыт 6/6!", "Кэш, золото и изумруды", cr, dia);
                    CorporateTaskTracker.advanceContract(player, acc, "COURIER_LOOT", 1);
                    StoryManager.checkAllQuests(player, acc);
                    for (ServerPlayer sp : sl.players()) {
                        if (sp != player && sp.distanceToSqr(px, py, pz) <= 18.0 * 18.0) {
                            PlayerMarketAccount pAcc = data.getOrCreateAccount(sp.getUUID());
                            long share = MarketAttributeApplier.scaleBioCr(pAcc, 120L);
                            pAcc.addCr(share);
                            sp.sendOverlayMessage(Component.literal("§a🏧 Банкомат взломан! Доля: §e+" + share + " CR"));
                        }
                    }
                    player.sendSystemMessage(Component.literal("§a§l🏧 БАНКОМАТ HR ВСКРЫТ: §e+" + cr + " CR§a, изумруды и золото!"
                            + (dia > 0 ? " §b+1 ♦" : "")));
                    data.setDirty();
                    endActiveDirective(server, data, campaign, gameTime, false);
                }
            }
            case MYSTERY_CAPSULE -> {
                capsuleHitsLanded++;
                sl.playSound(null, px, py, pz, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.2f + capsuleHitsLanded * 0.25f);
                sl.sendParticles(ParticleTypes.FIREWORK, px, py + 0.4, pz, 18, 0.4, 0.4, 0.4, 0.15);
                if (capsuleHitsLanded < CAPSULE_TARGET_HITS) {
                    ChaosProp3DManager.updateTextLabel(server, ChaosProp3DManager.PropKind.MYSTERY_CAPSULE,
                            "§d§l[🎁 КОТ В МЕШКЕ " + capsuleHitsLanded + "/" + CAPSULE_TARGET_HITS + " — ЕЩЁ РАЗ!]");
                    player.sendOverlayMessage(Component.literal("§d🎁 Кот в Мешке схвачен (" + capsuleHitsLanded + "/" + CAPSULE_TARGET_HITS + ")!"));
                } else {
                    ChaosProp3DManager.removeKind(server, ChaosProp3DManager.PropKind.MYSTERY_CAPSULE);
                    int prize = sl.getRandom().nextInt(3);
                    long cr = MarketAttributeApplier.scaleBioCr(acc, prize == 0 ? 280L : (prize == 1 ? 210L : 150L));
                    acc.addCr(cr);
                    if (prize == 0) {
                        int dia = acc.getSoldAssetCount() >= 1 ? 1 : 0;
                        if (dia > 0) spawnDrop(sl, px, py + 0.5, pz, new ItemStack(Items.DIAMOND, dia));
                        spawnDrop(sl, px, py + 0.5, pz, new ItemStack(Items.GOLDEN_APPLE, 1));
                        player.sendSystemMessage(Component.literal("§d🎰 КОТ В МЕШКЕ [ДЖЕКПОТ]: §e+" + cr + " CR §dи Золотое Яблоко!"));
                    } else if (prize == 1) {
                        spawnDrop(sl, px, py + 0.5, pz, new ItemStack(Items.GOLD_INGOT, 5));
                        spawnDrop(sl, px, py + 0.5, pz, new ItemStack(Items.EMERALD, 3));
                        player.sendSystemMessage(Component.literal("§d🎰 КОТ В МЕШКЕ [ЗОЛОТОЙ ПРИИСК]: §e+" + cr + " CR §dи слитки!"));
                    } else {
                        spawnDrop(sl, px, py + 0.5, pz, new ItemStack(Items.COOKIE, 12));
                        player.addEffect(new MobEffectInstance(MobEffects.SPEED, 300, 1, false, false));
                        player.sendSystemMessage(Component.literal("§d🎰 КОТ В МЕШКЕ [ПЕЧЕНЬЕ ОТ HR]: §e+" + cr + " CR §d+ 12 печенек + Скорость II!"));
                    }
                    acc.addTransaction(gameTime, "КОТ В МЕШКЕ 3D", "Рулетка капсулы", "Приз корпорации", cr, 0);
                    sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, px, py + 0.5, pz, 30, 0.5, 0.5, 0.5, 0.22);
                    sl.playSound(null, px, py, pz, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.3f);
                    CorporateTaskTracker.advanceContract(player, acc, "COURIER_LOOT", 1);
                    StoryManager.checkAllQuests(player, acc);
                    data.setDirty();
                    endActiveDirective(server, data, campaign, gameTime, false);
                }
            }
            case VERITY_KOLOBOK -> {
                ChaosProp3DManager.removeKind(server, ChaosProp3DManager.PropKind.VERITY_KOLOBOK);
                if (acc.getSoldAssetCount() >= 1) {
                    spawnDrop(sl, px, py + 0.5, pz, new ItemStack(Items.DIAMOND, 1));
                }
                spawnDrop(sl, px, py + 0.5, pz, new ItemStack(Items.GOLDEN_APPLE, 1));
                spawnDrop(sl, px, py + 0.5, pz, new ItemStack(Items.EMERALD, 3));
                sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, px, py + 0.6, pz, 28, 0.5, 0.5, 0.5, 0.18);
                sl.playSound(null, px, py, pz, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.4f);

                long cr = MarketAttributeApplier.scaleBioCr(acc, 180L);
                acc.addCr(cr);
                acc.addTransaction(gameTime, "ВЕРИТИ-КОЛОБОК", "Верити пойман!", "Бонус корпорации", cr, 0);
                campaign.addContribution("allay_caught", 1);
                campaign.addContribution("courier_caught", 1);
                CorporateTaskTracker.advanceContract(player, acc, "COURIER_LOOT", 1);
                player.sendSystemMessage(Component.literal("§8[§eВЕРИТИ 3D§8] §e✔ Верити-Колобок вручил награду: §e+" + cr + " CR §bи ресурсы!"));
                StoryManager.checkAllQuests(player, acc);
                data.setDirty();
                if (ChaosDirective.ALLAY_BONUS.getId().equals(data.getActiveChaosId())) {
                    endActiveDirective(server, data, campaign, gameTime, false);
                } else {
                    MarketNetworking.sendSyncToPlayer(player);
                }
            }
            default -> {}
        }
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
        if (priorityTargetOverride != null && priorityTargetOverride.isAlive() && !priorityTargetOverride.isSpectator()) {
            return priorityTargetOverride;
        }
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

    public static BlockPos findSafeGroundPosPublic(ServerLevel sl, BlockPos center, int minDist, int maxDist) {
        return findSafeGroundPos(sl, center, minDist, maxDist);
    }

    public static BlockPos findForwardGroundPosPublic(ServerLevel sl, ServerPlayer sp, int minDist, int maxDist) {
        return findForwardGroundPos(sl, sp, minDist, maxDist);
    }

    private static BlockPos findForwardGroundPos(ServerLevel sl, ServerPlayer sp, int minDist, int maxDist) {
        if (sp == null) return BlockPos.ZERO;
        var look = sp.getLookAngle();
        double baseAngle = Math.atan2(look.z, look.x);
        var rng = sl.getRandom();
        BlockPos center = sp.blockPosition();
        for (int attempt = 0; attempt < 18; attempt++) {
            double spread = attempt < 6 ? (attempt - 2.5) * 0.18 : (rng.nextDouble() - 0.5) * 1.4;
            double angle = baseAngle + spread;
            double dist = minDist + rng.nextDouble() * Math.max(1, maxDist - minDist);
            int tx = (int) Math.round(sp.getX() + Math.cos(angle) * dist);
            int tz = (int) Math.round(sp.getZ() + Math.sin(angle) * dist);
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
        return findSafeGroundPos(sl, center, minDist, maxDist);
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

    private static BlockPos findSafeForwardBlink(ServerLevel sl, ServerPlayer sp, int maxBlocks) {
        var look = sp.getLookAngle();
        double lx = look.x;
        double lz = look.z;
        double len = Math.max(0.01, Math.hypot(lx, lz));
        lx /= len;
        lz /= len;
        for (int dist = maxBlocks; dist >= 2; dist--) {
            int tx = (int) Math.round(sp.getX() + lx * dist);
            int tz = (int) Math.round(sp.getZ() + lz * dist);
            for (int dy = 2; dy >= -3; dy--) {
                BlockPos feet = new BlockPos(tx, sp.blockPosition().getY() + dy, tz);
                BlockPos below = feet.below();
                BlockPos head = feet.above();
                if (!sl.getBlockState(below).getCollisionShape(sl, below).isEmpty()
                        && sl.getBlockState(feet).getCollisionShape(sl, feet).isEmpty()
                        && sl.getBlockState(head).getCollisionShape(sl, head).isEmpty()) {
                    return feet;
                }
            }
        }
        return sp.blockPosition();
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

    // === INTERACTIVE WORLD ENCOUNTERS (Strictly single active + 45s cooldown) ===

    private static void spawnSupplyCrate(MinecraftServer server, MarketSavedData data, long gameTime) {
        ServerPlayer anchor = pickTargetPlayer(server, data);
        if (anchor == null || !(anchor.level() instanceof ServerLevel sl)) return;

        BlockPos pos = findForwardGroundPos(sl, anchor, 3, 6);
        ChaosProp3DManager.spawnSupplyCrate(sl, pos, gameTime);
        sl.sendParticles(ParticleTypes.FIREWORK, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 22, 0.4, 0.5, 0.4, 0.12);
        sl.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.65f, 1.3f);

        for (ServerPlayer sp : sl.players()) {
            sp.sendSystemMessage(Component.literal("§8[§6ЛОГИСТИКА 3D§8] §eСброшен 3D Груз Asset Corp у §fX:" + pos.getX() + " Z:" + pos.getZ() + "§e! Нажмите ПКМ или ударьте!"));
        }
    }

    private static void spawnCollectorRaid(MinecraftServer server, MarketSavedData data, long gameTime) {
        ServerPlayer target = pickTargetPlayer(server, data);
        if (target == null || !(target.level() instanceof ServerLevel sl)) return;
        if (sl.getDifficulty() == Difficulty.PEACEFUL) {
            spawnVerityKolobok(server, gameTime);
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
        TRACKED_MOBS.put(collector.getUUID(), new TrackedMob(collector.getUUID(), MobKind.COLLECTOR, gameTime + 1100L));
        mobSpawnCooldownUntil = gameTime + 900L;
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 18, 0.5, 0.6, 0.5, 0.08);
        sl.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.8f, 1.3f);
    }

    private static void spawnSkyParcelCourier(MinecraftServer server, long gameTime) {
        MarketSavedData data = MarketSavedData.get(server);
        ServerPlayer anchor = pickTargetPlayer(server, data);
        if (anchor == null || !(anchor.level() instanceof ServerLevel sl)) return;

        BlockPos ground = findSafeGroundPos(sl, anchor.blockPosition(), 3, 8);
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
        mobSpawnCooldownUntil = gameTime + 900L;

        for (ServerPlayer sp : sl.players()) {
            sp.sendSystemMessage(Component.literal("§8[§6АВИАПОЧТА§8] §eКурица-курьер сброшена над §fX:" + ground.getX() + " Z:" + ground.getZ() + "§e! Поймай её (ПКМ/удар)!"));
        }
        sl.playSound(null, ground.getX(), ground.getY(), ground.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0f, 1.1f);
    }

    private static void spawnPiggyBank(MinecraftServer server, long gameTime) {
        MarketSavedData data = MarketSavedData.get(server);
        ServerPlayer anchor = pickTargetPlayer(server, data);
        if (anchor == null || !(anchor.level() instanceof ServerLevel sl)) return;

        BlockPos pos = findSafeGroundPos(sl, anchor.blockPosition(), 4, 8);
        Pig pig = EntityTypes.PIG.create(sl, EntitySpawnReason.EVENT);
        if (pig == null) return;

        pig.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        pig.addTag(MobKind.PIGGY.tag);
        pig.setCustomName(Component.literal("§6§lСВИНКА-КОПИЛКА"));
        pig.setCustomNameVisible(true);
        pig.setPersistenceRequired();
        pig.addEffect(new MobEffectInstance(MobEffects.GLOWING, 720, 0, false, false));

        sl.addFreshEntity(pig);
        TRACKED_MOBS.put(pig.getUUID(), new TrackedMob(pig.getUUID(), MobKind.PIGGY, gameTime + 680L));
        mobSpawnCooldownUntil = gameTime + 900L;
        sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 22, 0.4, 0.4, 0.4, 0.15);
        sl.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.PIG_AMBIENT_BABY.value(), SoundSource.NEUTRAL, 1.0f, 1.1f);

        for (ServerPlayer sp : sl.players()) {
            sp.sendSystemMessage(Component.literal("§8[§6ИНКАССАЦИЯ§8] §eШустрая Свинка-Копилка убегает у §fX:" + pos.getX() + " Z:" + pos.getZ() + "§e! Догоните (ПКМ/удар)!"));
        }
    }

    private static void spawnRainbowPinata(MinecraftServer server, long gameTime) {
        MarketSavedData data = MarketSavedData.get(server);
        ServerPlayer anchor = pickTargetPlayer(server, data);
        if (anchor == null || !(anchor.level() instanceof ServerLevel sl)) return;

        BlockPos pos = findSafeGroundPos(sl, anchor.blockPosition(), 4, 7);
        Sheep sheep = EntityTypes.SHEEP.create(sl, EntitySpawnReason.EVENT);
        if (sheep == null) return;

        sheep.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        sheep.addTag(MobKind.PINATA.tag);
        sheep.setCustomName(Component.literal("jeb_"));
        sheep.setCustomNameVisible(true);
        sheep.setPersistenceRequired();
        sheep.addEffect(new MobEffectInstance(MobEffects.GLOWING, 700, 0, false, false));

        sl.addFreshEntity(sheep);
        TRACKED_MOBS.put(sheep.getUUID(), new TrackedMob(sheep.getUUID(), MobKind.PINATA, gameTime + 600L));
        mobSpawnCooldownUntil = gameTime + 900L;
        sl.sendParticles(ParticleTypes.FIREWORK, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 26, 0.5, 0.5, 0.5, 0.18);
        sl.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.NEUTRAL, 1.0f, 1.2f);

        for (ServerPlayer sp : sl.players()) {
            sp.sendSystemMessage(Component.literal("§8[§dПИНЬЯТА§8] §dРадужная Овца jeb_ заспавнилась у §fX:" + pos.getX() + " Z:" + pos.getZ() + "§d! Стукните 3 раза!"));
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
        TRACKED_MOBS.put(trader.getUUID(), new TrackedMob(trader.getUUID(), MobKind.INSPECTOR, gameTime + 640L));
        mobSpawnCooldownUntil = gameTime + 900L;
        sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 20, 0.5, 0.6, 0.5, 0.1);
        sl.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.WANDERING_TRADER_YES, SoundSource.NEUTRAL, 1.0f, 1.0f);

        for (ServerPlayer sp : sl.players()) {
            sp.sendSystemMessage(Component.literal("§8[§eРЕВИЗОР§8] §fПришёл голодный Инспектор у §eX:" + pos.getX() + " Z:" + pos.getZ() + "§f! Суй ему ЛЮБУЮ еду или изумруд (ПКМ)!"));
        }
    }

    private static void spawnVerityKolobok(MinecraftServer server, long gameTime) {
        MarketSavedData data = MarketSavedData.get(server);
        ServerPlayer anchor = pickTargetPlayer(server, data);
        if (anchor == null || !(anchor.level() instanceof ServerLevel sl)) return;

        BlockPos ground = findForwardGroundPos(sl, anchor, 3, 6);
        ChaosProp3DManager.spawnVerityKolobok(sl, ground, gameTime);
        sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, ground.getX() + 0.5, ground.getY() + 0.6, ground.getZ() + 0.5, 22, 0.4, 0.4, 0.4, 0.12);
        sl.playSound(null, ground.getX(), ground.getY(), ground.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.0f, 1.25f);

        for (ServerPlayer sp : sl.players()) {
            sp.sendSystemMessage(Component.literal("§8[§eВЕРИТИ 3D§8] §eПоявился жёлтый Верити-Колобок у §fX:" + ground.getX() + " Z:" + ground.getZ() + "§e! Поймай (ПКМ/удар)!"));
        }
    }

    // === FLEE AI, TICK & INTERACTION HANDLERS ===

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
                    case COURIER -> ParticleTypes.CLOUD;
                    case COLLECTOR -> ParticleTypes.ELECTRIC_SPARK;
                    case CRATE -> ParticleTypes.TOTEM_OF_UNDYING;
                };
                sl.sendParticles(pType, found.getX(), found.getY() + 0.9, found.getZ(), 2, 0.25, 0.25, 0.25, 0.02);
            }

            // Active fleeing & evasion AI so chase mobs genuinely run away & zig-zag!
            if (!tm.launching && gameTime % 4L == 0L
                    && (tm.kind == MobKind.PIGGY || (tm.kind == MobKind.PINATA && tm.taps > 0))) {
                applyEvasiveFlee(found, tm.kind, gameTime);
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
                if (tm.kind == MobKind.COLLECTOR && found.level() instanceof ServerLevel sl) {
                    for (ServerPlayer sp : sl.players()) {
                        PlayerMarketAccount acc = data.getOrCreateAccount(sp.getUUID());
                        if (sp.isSpectator() || StoryManager.isProgramTerminated(campaign, acc)) continue;
                        if (sp.distanceToSqr(found) <= 28.0 * 28.0) {
                            long fine = acc.forcePenaltyCr(scaleFine(acc, 200L, 0.10));
                            acc.addTransaction(gameTime, "ВЗЫСКАНИЕ КОЛЛЕКТОРА", "Коллектор ушёл с деньгами", "Рейд не отбит (10%)", -fine, 0);
                            sp.sendSystemMessage(Component.literal("§c⚖ КОЛЛЕКТОР УШЁЛ И ВЗЫСКАЛ ДОЛГ: §e-" + fine + " CR §7(10%)§c!"));
                        }
                    }
                    data.setDirty();
                }
                if (found.level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.CLOUD, found.getX(), found.getY() + 0.5, found.getZ(), 14, 0.3, 0.3, 0.3, 0.05);
                }
                found.discard();
                it.remove();
            }
        }
    }

    private static void applyEvasiveFlee(Entity mobEntity, MobKind kind, long gameTime) {
        if (!(mobEntity.level() instanceof ServerLevel sl)) return;
        ServerPlayer nearest = null;
        double bestDistSq = 18.0 * 18.0;
        for (ServerPlayer sp : sl.players()) {
            if (sp.isSpectator() || !sp.isAlive()) continue;
            double dSq = sp.distanceToSqr(mobEntity);
            if (dSq < bestDistSq) {
                bestDistSq = dSq;
                nearest = sp;
            }
        }
        if (nearest == null) return;

        double dx = mobEntity.getX() - nearest.getX();
        double dz = mobEntity.getZ() - nearest.getZ();
        double len = Math.max(0.01, Math.hypot(dx, dz));
        double nx = dx / len;
        double nz = dz / len;

        // Lateral zig-zag perpendicular to escape vector so players can't run in a straight line easily
        double strafe = Math.sin(gameTime * 0.22 + mobEntity.getId()) * 0.42;
        double fx = nx - nz * strafe;
        double fz = nz + nx * strafe;
        double fLen = Math.max(0.01, Math.hypot(fx, fz));
        fx /= fLen;
        fz /= fLen;

        double speed = kind == MobKind.PIGGY ? 0.12 : 0.10;

        if (mobEntity instanceof Mob mob) {
            mob.getNavigation().moveTo(
                    mobEntity.getX() + fx * 6.0,
                    mobEntity.getY(),
                    mobEntity.getZ() + fz * 6.0,
                    kind == MobKind.PINATA ? 0.90 : 0.96
            );
        }

        double vy = mobEntity.getDeltaMovement().y;
        if (mobEntity.horizontalCollision || (mobEntity.onGround() && bestDistSq < 16.0)) {
            vy = 0.34;
        }

        mobEntity.setDeltaMovement(fx * speed, vy, fz * speed);
        mobEntity.needsSync = true;
        if (bestDistSq < 9.0 && gameTime % 12L == 0L) {
            sl.sendParticles(ParticleTypes.CLOUD, mobEntity.getX(), mobEntity.getY() + 0.3, mobEntity.getZ(), 4, 0.15, 0.1, 0.15, 0.03);
        }
    }

    /**
     * Unified handler for right-clicking OR punching custom Chaos/Companion entities.
     */
    public static boolean handleChaosMobInteraction(ServerPlayer player, Entity target, ItemStack held,
                                                    MarketSavedData data, boolean isAttack, long gameTime) {
        if (player == null || target == null || data == null) return false;
        SharedCampaignData campaign = data.getCampaignData();
        PlayerMarketAccount acc = data.getOrCreateAccount(player.getUUID());
        if (StoryManager.isProgramTerminated(campaign, acc)) return false;

        Set<String> tags = target.entityTags();

        if (target instanceof Merchant && !tags.contains(MobKind.INSPECTOR.tag)) {
            if (!isAttack && isBribeItem(held)) {
                campaign.setObjective("villager_trade", 1);
                campaign.addContribution("inspector_bribed", 1);
                CorporateTaskTracker.advanceContract(player, acc, "BRIBE_PET", 1);
                return false;
            }
            return false;
        }

        Long lastTick = LAST_TAP_TICK.get(target.getUUID());
        if (lastTick != null && (gameTime - lastTick) < 4L) {
            return isSpecialInteractiveMob(tags);
        }

        if (tags.contains(MobKind.CRATE.tag)) {
            LAST_TAP_TICK.put(target.getUUID(), gameTime);
            TRACKED_MOBS.remove(target.getUUID());
            handleCrateOpened(player, target, data, gameTime);
            target.discard();
            return true;
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
                || tags.contains(MobKind.INSPECTOR.tag)
                || tags.contains(MobKind.CRATE.tag);
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

    private static void handleCrateOpened(ServerPlayer opener, Entity crate, MarketSavedData data, long gameTime) {
        if (!(crate.level() instanceof ServerLevel sl)) return;
        PlayerMarketAccount acc = data.getOrCreateAccount(opener.getUUID());
        boolean isPrank = sl.getRandom().nextInt(10) < 3;

        sl.playSound(null, crate.getX(), crate.getY(), crate.getZ(), SoundEvents.CHEST_OPEN, SoundSource.PLAYERS, 0.9f, 1.1f);
        if (isPrank) {
            spawnDrop(sl, crate.getX(), crate.getY() + 0.5, crate.getZ(), new ItemStack(Items.COOKIE, 8));
            spawnDrop(sl, crate.getX(), crate.getY() + 0.5, crate.getZ(), new ItemStack(Items.EXPERIENCE_BOTTLE, 4));
            sl.sendParticles(ParticleTypes.FIREWORK, crate.getX(), crate.getY() + 0.7, crate.getZ(), 28, 0.5, 0.5, 0.5, 0.22);
            opener.setDeltaMovement(0.0, 0.45, 0.0);
            opener.needsSync = true;
            long cr = MarketAttributeApplier.scaleBioCr(acc, 120L);
            acc.addCr(cr);
            acc.addTransaction(gameTime, "ГРУЗ-ПРАНК", "Мимик HR!", "Печеньки, опыт и откат", cr, 0);
            opener.sendSystemMessage(Component.literal("§d🎉 ХА-ХА! В контейнере был пранк-мимик отдела HR! Утешительный приз: §e+" + cr + " CR §dи печенье!"));
        } else {
            if (acc.getSoldAssetCount() >= 1) {
                spawnDrop(sl, crate.getX(), crate.getY() + 0.5, crate.getZ(), new ItemStack(Items.DIAMOND, 1));
            }
            spawnDrop(sl, crate.getX(), crate.getY() + 0.5, crate.getZ(), new ItemStack(Items.GOLDEN_APPLE, 1));
            spawnDrop(sl, crate.getX(), crate.getY() + 0.5, crate.getZ(), new ItemStack(Items.IRON_INGOT, 5));
            spawnDrop(sl, crate.getX(), crate.getY() + 0.5, crate.getZ(), new ItemStack(Items.COOKED_BEEF, 6));
            sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, crate.getX(), crate.getY() + 0.6, crate.getZ(), 26, 0.5, 0.5, 0.5, 0.2);
            long cr = MarketAttributeApplier.scaleBioCr(acc, 180L);
            acc.addCr(cr);
            acc.addTransaction(gameTime, "СНАБЖЕНИЕ", "Груз Asset Corp вскрыт!", "Провизия и ресурсы", cr, 0);
            opener.sendSystemMessage(Component.literal("§a📦 ГРУЗ ВСКРЫТ: §e+" + cr + " CR§a, Яблоко, железо и провизия!"));
        }
        CorporateTaskTracker.advanceContract(opener, acc, "COURIER_LOOT", 1);
        StoryManager.checkAllQuests(opener, acc);
        MarketNetworking.sendSyncToPlayer(opener);
    }

    private static void handlePiggyCaught(ServerPlayer catcher, Entity pig, MarketSavedData data, SharedCampaignData campaign, long gameTime) {
        if (!(pig.level() instanceof ServerLevel sl)) return;
        PlayerMarketAccount acc = data.getOrCreateAccount(catcher.getUUID());
        if (acc.getSoldAssetCount() >= 1) {
            spawnDrop(sl, pig.getX(), pig.getY() + 0.4, pig.getZ(), new ItemStack(Items.DIAMOND, 1));
        }
        spawnDrop(sl, pig.getX(), pig.getY() + 0.4, pig.getZ(), new ItemStack(Items.EMERALD, 3));
        spawnDrop(sl, pig.getX(), pig.getY() + 0.4, pig.getZ(), new ItemStack(Items.GOLD_INGOT, 3));
        sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pig.getX(), pig.getY() + 0.6, pig.getZ(), 28, 0.5, 0.5, 0.5, 0.2);
        sl.playSound(null, pig.getX(), pig.getY(), pig.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.9f, 1.35f);

        long cr = MarketAttributeApplier.scaleBioCr(acc, 180L);
        acc.addCr(cr);
        acc.addTransaction(gameTime, "ИНКАССАЦИЯ", "Свинка-Копилка поймана!", "Трофей по ставке био-вклада", cr, 0);
        campaign.addContribution("piggy_caught", 1);
        campaign.addContribution("courier_caught", 1);
        CorporateTaskTracker.advanceContract(catcher, acc, "COURIER_LOOT", 1);
        CorporateTaskTracker.advanceContract(catcher, acc, "BRIBE_PET", 1);
        catcher.sendSystemMessage(Component.literal("§8[§aИНКАССАЦИЯ§8] §a✔ Свинка-Копилка поймана: §e+" + cr + " CR§a, золото и изумруды!"
                + (acc.getSoldAssetCount() == 0 ? " §c(0 статов: без алмаза!)" : "")));
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
            long cr = MarketAttributeApplier.scaleBioCr(acc, 60L);
            acc.addCr(cr);
            // Panic hop away after getting hit!
            applyEvasiveFlee(sheepEntity, MobKind.PINATA, gameTime);
            striker.sendOverlayMessage(Component.literal("§d✦ ПИНЬЯТА! Удар " + tm.taps + "/3 §e(+" + cr + " CR) — догоняй!"));
        } else {
            tm.launching = true;
            tm.expireTick = gameTime + 28L;
            int dia = acc.getSoldAssetCount() >= 2 ? 2 : (acc.getSoldAssetCount() >= 1 ? 1 : 0);
            if (dia > 0) {
                spawnDrop(sl, sheepEntity.getX(), sheepEntity.getY() + 0.7, sheepEntity.getZ(), new ItemStack(Items.DIAMOND, dia));
            }
            spawnDrop(sl, sheepEntity.getX(), sheepEntity.getY() + 0.7, sheepEntity.getZ(), new ItemStack(Items.GOLDEN_APPLE, 1));
            long cr = MarketAttributeApplier.scaleBioCr(acc, 160L);
            acc.addCr(cr);
            acc.addTransaction(gameTime, "ПИНЬЯТА jeb_", "Радужный взрыв призов!", "Финал пиньяты (3/3)", cr, 0);
            campaign.addContribution("pinata_done", 1);
            campaign.addContribution("courier_caught", 1);
            CorporateTaskTracker.advanceContract(striker, acc, "COURIER_LOOT", 1);
            if (sheepEntity instanceof LivingEntity le) {
                le.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 40, 2, false, false));
            }
            sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, sheepEntity.getX(), sheepEntity.getY() + 0.9, sheepEntity.getZ(), 32, 0.6, 0.6, 0.6, 0.25);
            sl.playSound(null, sheepEntity.getX(), sheepEntity.getY(), sheepEntity.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.4f);
            striker.sendSystemMessage(Component.literal("§8[§dПИНЬЯТА§8] §d✔ Радужная Пиньята разбита: §e+" + cr + " CR§d"
                    + (dia > 0 ? " + " + dia + " ♦" : "") + " и Золотое Яблоко!"));
        }
        data.setDirty();
        StoryManager.checkAllQuests(striker, acc);
        MarketNetworking.sendSyncToPlayer(striker);
    }

    private static void handleInspectorBribed(ServerPlayer briber, Entity trader, MarketSavedData data, SharedCampaignData campaign, long gameTime) {
        if (!(trader.level() instanceof ServerLevel sl)) return;
        PlayerMarketAccount acc = data.getOrCreateAccount(briber.getUUID());
        int dia = acc.getSoldAssetCount() >= 3 ? 2 : 1;
        spawnDrop(sl, trader.getX(), trader.getY() + 0.7, trader.getZ(), new ItemStack(Items.DIAMOND, dia));
        spawnDrop(sl, trader.getX(), trader.getY() + 0.7, trader.getZ(), new ItemStack(Items.GOLDEN_CARROT, 4));
        sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, trader.getX(), trader.getY() + 1.0, trader.getZ(), 28, 0.5, 0.6, 0.5, 0.2);
        sl.playSound(null, trader.getX(), trader.getY(), trader.getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0f, 1.15f);

        long cr = MarketAttributeApplier.scaleBioCr(acc, 220L);
        acc.addCr(cr);
        acc.addTransaction(gameTime, "ВЗЯТКА РЕВИЗОРУ", "Акт проверки подписан!", "Инспектор доволен", cr, 0);
        campaign.addContribution("inspector_bribed", 1);
        campaign.setObjective("villager_trade", 1);
        campaign.addContribution("courier_caught", 1);
        CorporateTaskTracker.advanceContract(briber, acc, "BRIBE_PET", 1);
        CorporateTaskTracker.advanceContract(briber, acc, "COURIER_LOOT", 1);
        briber.sendSystemMessage(Component.literal("§8[§aРЕВИЗОР§8] §a«Хмм, отличный бутерброд! Отчёт чист!» §e(+" + cr + " CR и " + dia + " ♦)"));
        StoryManager.checkAllQuests(briber, acc);
        MarketNetworking.sendSyncToPlayer(briber);
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

        int catcherSold = directCatcher != null ? data.getOrCreateAccount(directCatcher.getUUID()).getSoldAssetCount() : 1;
        if (catcherSold >= 1) {
            spawnDrop(sl, x, y, z, new ItemStack(Items.DIAMOND, 1));
        }
        spawnDrop(sl, x, y, z, new ItemStack(Items.GOLDEN_APPLE, 1));
        spawnDrop(sl, x, y, z, new ItemStack(Items.EMERALD, 3));

        sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y + 0.5, z, 28, 0.5, 0.5, 0.5, 0.25);
        sl.playSound(null, x, y, z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 0.9f, 1.2f);

        for (ServerPlayer sp : sl.players()) {
            boolean isCatcher = directCatcher != null && directCatcher.getUUID().equals(sp.getUUID());
            if (isCatcher || sp.distanceToSqr(source) <= 18.0 * 18.0) {
                PlayerMarketAccount acc = data.getOrCreateAccount(sp.getUUID());
                if (StoryManager.isProgramTerminated(campaign, acc)) continue;
                long bonus = MarketAttributeApplier.scaleBioCr(acc, isCatcher ? 200L : 110L);
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

    public static String triggerStageManualSpawn(MinecraftServer server, ServerPlayer player, MarketSavedData data, long gameTime) {
        if (server == null || player == null || data == null) return "§cОшибка сервера.";
        PlayerMarketAccount acc = data.getOrCreateAccount(player.getUUID());
        SharedCampaignData campaign = data.getCampaignData();
        if (StoryManager.isProgramTerminated(campaign, acc)) {
            return "§cПрограмма закрыта навсегда (Статья 17.4).";
        }
        if (!campaign.isCampaignAccepted()) {
            return "§eСначала прочитайте и подпишите Мятое письмо в инвентаре!";
        }
        if (!"NONE".equals(data.getActiveChaosId())) {
            return "§eДождитесь окончания текущей Директивы Хаоса!";
        }
        if (hasAnyLiveTrackedMob(server)) {
            return "§eВ мире уже бегает ивент-моб! Сначала поймайте его.";
        }
        if (ChaosProp3DManager.hasActiveEncounterProp()) {
            return "§eРядом уже активен 3D-груз / Верити! Сначала заберите его.";
        }
        if (gameTime < manualSpawnReadyTick) {
            long secLeft = Math.max(1L, (manualSpawnReadyTick - gameTime + 19L) / 20L);
            return "§eПерезарядка вызова: подождите ещё " + secLeft + " сек.";
        }

        manualSpawnReadyTick = gameTime + 700L; // 35s strict anti-farm cooldown
        int stage = campaign.getCurrentStage();
        String spawnedLabel;

        try {
            priorityTargetOverride = player;
            if (stage <= 1 && !campaign.isTaskCompleted("s1_piggy_catch")) {
                spawnPiggyBank(server, gameTime);
                spawnedLabel = "Свинка-Копилка";
            } else if (stage == 2 && !campaign.isTaskCompleted("s2_pinata_audit")) {
                spawnRainbowPinata(server, gameTime);
                spawnedLabel = "Радужная Пиньята (jeb_)";
            } else if (stage == 3 && !campaign.isTaskCompleted("s3_inspector_bribe")) {
                spawnBribeInspector(server, gameTime);
                spawnedLabel = "Голодный Инспектор";
            } else if (stage == 4 && !campaign.isTaskCompleted("s4_sky_courier")) {
                spawnSkyParcelCourier(server, gameTime);
                spawnedLabel = "Курица-Курьер";
            } else if (stage >= 5 && !campaign.isTaskCompleted("s5_ballistics_check")) {
                spawnVerityKolobok(server, gameTime);
                spawnedLabel = "Верити-Колобок 3D";
            } else if (stage >= 5 && !campaign.isTaskCompleted("s5_collector_clash")
                    && server.overworld().getDifficulty() != Difficulty.PEACEFUL) {
                spawnCollectorRaid(server, data, gameTime);
                spawnedLabel = "Коллектор Asset Corp";
            } else {
                int slot = Math.floorMod(manualSpawnCycle++, 4);
                switch (slot) {
                    case 0 -> {
                        spawnPiggyBank(server, gameTime);
                        spawnedLabel = "Свинка-Копилка";
                    }
                    case 1 -> {
                        spawnSupplyCrate(server, data, gameTime);
                        spawnedLabel = "3D Груз Снабжения";
                    }
                    case 2 -> {
                        spawnVerityKolobok(server, gameTime);
                        spawnedLabel = "Верити-Колобок 3D";
                    }
                    default -> {
                        spawnRainbowPinata(server, gameTime);
                        spawnedLabel = "Радужная Пиньята (jeb_)";
                    }
                }
            }
        } finally {
            priorityTargetOverride = null;
        }

        return "§a✦ Вызвано событие: §e" + spawnedLabel + " §7(КД 35 сек.)";
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
                addChaosMod(player, Attributes.MOVEMENT_SPEED, "chaos_speed", 0.30, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            }
            case GIANT_EGO -> {
                addChaosMod(player, Attributes.SCALE, "chaos_scale", 0.65, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
                addChaosMod(player, Attributes.STEP_HEIGHT, "chaos_step", 0.90, AttributeModifier.Operation.ADD_VALUE);
                addChaosMod(player, Attributes.ATTACK_DAMAGE, "chaos_dmg", 0.30, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
                addChaosMod(player, Attributes.KNOCKBACK_RESISTANCE, "chaos_kb", 0.60, AttributeModifier.Operation.ADD_VALUE);
            }
            case ZERO_GRAVITY -> {
                addChaosMod(player, Attributes.GRAVITY, "chaos_gravity", -0.64, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
                addChaosMod(player, Attributes.SAFE_FALL_DISTANCE, "chaos_safe_fall", 12.0, AttributeModifier.Operation.ADD_VALUE);
                addChaosMod(player, Attributes.JUMP_STRENGTH, "chaos_jump", 0.26, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            }
            case BOUNCY_FLOOR -> {
                addChaosMod(player, Attributes.BOUNCINESS, "chaos_bounce", 0.85, AttributeModifier.Operation.ADD_VALUE);
                addChaosMod(player, Attributes.SAFE_FALL_DISTANCE, "chaos_safe_fall", 14.0, AttributeModifier.Operation.ADD_VALUE);
            }
            case TURBO_SHIFT -> {
                addChaosMod(player, Attributes.BLOCK_BREAK_SPEED, "chaos_mine", 0.85, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
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
