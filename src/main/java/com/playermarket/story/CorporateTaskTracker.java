package com.playermarket.story;

import com.playermarket.market.AssetType;
import com.playermarket.market.MarketSavedData;
import com.playermarket.market.PlayerMarketAccount;
import com.playermarket.network.MarketNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Tracks real-time creative stunts, corporate dares, and contract progress server-side.
 */
public final class CorporateTaskTracker {

    private static final class PlayerStuntState {
        boolean initialized = false;
        boolean wasCrouching = false;
        float lastYaw = 0.0F;
        double yawAccumulator = 0.0;
        double peakFall = 0.0;
        double lastX = 0.0;
        double lastZ = 0.0;
        double sprintAccumulator = 0.0;
    }

    private static final Map<UUID, PlayerStuntState> STATES = new HashMap<>();

    private CorporateTaskTracker() {
    }

    public static void clearPlayer(UUID uuid) {
        STATES.remove(uuid);
    }

    /**
     * Advances matching active contracts and announces completion with audio/visual feedback.
     */
    public static boolean advanceContract(ServerPlayer player, PlayerMarketAccount account, String typeId, int amount) {
        List<String> newlyCompleted = account.progressContracts(typeId, amount);
        if (!newlyCompleted.isEmpty()) {
            for (String title : newlyCompleted) {
                player.sendSystemMessage(Component.literal("§8[§eКОНТРАКТЫ§8] §a✔ Контракт выполнен: §f«" + title + "»§7. Заберите награду в [M] -> КОНТРАКТЫ!"));
                player.sendOverlayMessage(Component.literal("§a✔ Контракт выполнен: §f" + title));
            }
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.85F, 1.35F);
            return true;
        }
        return false;
    }

    /**
     * Runs every 2 ticks to capture fast actions (crouch squats, 360 spins, fall clutches, sprinting).
     */
    public static boolean tickFast(ServerPlayer player, PlayerMarketAccount account, SharedCampaignData campaign, MarketSavedData data, long gameTime) {
        if (player.isSpectator() || account.isPermanentImpaired()) return false;

        PlayerStuntState state = STATES.computeIfAbsent(player.getUUID(), id -> new PlayerStuntState());
        if (!state.initialized) {
            state.initialized = true;
            state.wasCrouching = player.isCrouching();
            state.lastYaw = player.getYRot();
            state.lastX = player.getX();
            state.lastZ = player.getZ();
            return false;
        }

        boolean changed = false;
        boolean contractDone = false;

        // 1. Crouch transitions ("DRG Перекур" dance squats)
        boolean crouching = player.isCrouching();
        if (crouching && !state.wasCrouching) {
            campaign.addContribution("dance_squats", 1);
            contractDone |= advanceContract(player, account, "DANCE_SQUATS", 1);
            changed = true;
        }
        state.wasCrouching = crouching;

        // 2. Camera rotation ("Датчик паранойи")
        float currentYaw = player.getYRot();
        float yawDelta = Math.abs(Mth.wrapDegrees(currentYaw - state.lastYaw));
        state.lastYaw = currentYaw;
        if (yawDelta > 1.0F && yawDelta < 160.0F) {
            state.yawAccumulator += yawDelta;
            if (state.yawAccumulator >= 30.0) {
                int deg = (int) Math.floor(state.yawAccumulator);
                state.yawAccumulator -= deg;
                contractDone |= advanceContract(player, account, "PARANOID_SPIN", deg);
                long dayTime = player.level().getOverworldClockTime() % 24000L;
                boolean isNight = dayTime >= 13000L && dayTime <= 23000L;
                boolean inCave = player.blockPosition().getY() < 56 || !player.level().canSeeSky(player.blockPosition());
                if (inCave || isNight) {
                    campaign.addContribution("paranoid_degrees", deg);
                }
                changed = true;
            }
        }

        // 3. Fall & clutch landing ("Гравитация — актив фирмы" / "Прыжок веры")
        if (!player.onGround() && !player.isInWater() && player.fallDistance > state.peakFall) {
            state.peakFall = player.fallDistance;
        } else if ((player.onGround() || player.isInWater()) && state.peakFall >= 8.0) {
            int survivedHeight = (int) Math.round(state.peakFall);
            state.peakFall = 0.0;
            if (player.isAlive() && survivedHeight >= 10) {
                int prevMax = campaign.getObjective("max_survived_fall");
                if (survivedHeight > prevMax) {
                    campaign.setObjective("max_survived_fall", survivedHeight);
                }
                contractDone |= advanceContract(player, account, "PARKOUR_CLUTCH", 1);
                changed = true;
            }
        } else if (player.onGround() || player.isInWater()) {
            state.peakFall = 0.0;
        }

        // 4. Sprint distance ("Сверхсрочный курьер")
        double dx = player.getX() - state.lastX;
        double dz = player.getZ() - state.lastZ;
        state.lastX = player.getX();
        state.lastZ = player.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (player.isSprinting() && horiz > 0.05 && horiz < 4.0) {
            state.sprintAccumulator += horiz;
            if (state.sprintAccumulator >= 2.0) {
                int meters = (int) Math.floor(state.sprintAccumulator);
                state.sprintAccumulator -= meters;
                contractDone |= advanceContract(player, account, "SPRINT_COURIER", meters);
                changed = true;
            }
        }

        if (changed) {
            data.setDirty();
        }
        if (contractDone) {
            MarketNetworking.sendSyncToPlayer(player);
        }
        return contractDone;
    }

    /**
     * Runs once every second (20 ticks) for environmental & proximity stunts.
     */
    public static boolean tickOneSecond(ServerPlayer player, PlayerMarketAccount account, SharedCampaignData campaign, MarketSavedData data) {
        if (player.isSpectator() || account.isPermanentImpaired()) return false;

        boolean completedAny = false;
        completedAny |= advanceContract(player, account, "SURVIVE_SEC", 1);

        BlockPos pos = player.blockPosition();
        int y = pos.getY();

        // Sky / Cloud Migration checks
        if (y >= 160) {
            completedAny |= advanceContract(player, account, "ALTITUDE_CLOUD", 1);
        }
        if (y >= 170) {
            campaign.setObjective("cloud_reached", 1);
        }

        // Nether visit & Lava / Cryo-sauna check
        boolean inNether = player.level().dimension() == Level.NETHER;
        if (inNether) {
            campaign.setObjective("nether_visited", 1);
        }

        boolean nearLava = false;
        for (int ox = -4; ox <= 4 && !nearLava; ox++) {
            for (int oy = -2; oy <= 2 && !nearLava; oy++) {
                for (int oz = -4; oz <= 4; oz++) {
                    BlockState bs = player.level().getBlockState(pos.offset(ox, oy, oz));
                    if (bs.is(Blocks.LAVA) || bs.is(Blocks.MAGMA_BLOCK)) {
                        nearLava = true;
                        break;
                    }
                }
            }
        }

        if (nearLava) {
            campaign.addContribution("lava_tanning_sec", 1);
            completedAny |= advanceContract(player, account, "HOT_SPRING", 1);
        }

        boolean inWaterOrCauldron = player.isInWater() || player.level().getBlockState(pos).is(Blocks.WATER_CAULDRON);
        if (inWaterOrCauldron && (inNether || nearLava)) {
            campaign.setObjective("cryo_sauna", 1);
        }

        // Merchant / Villager trade open check
        if (player.containerMenu instanceof MerchantMenu) {
            campaign.setObjective("villager_trade", 1);
        }

        // Proximity scan for HR Icebreaker, Armorless Hazard & Creeper Interview
        List<Entity> nearbyMobs = player.level().getEntities(player, player.getBoundingBox().inflate(8.0), e -> e instanceof Mob && e.isAlive());
        Set<String> species5m = new HashSet<>();
        boolean hostileNear8 = false;
        boolean hostileNear6 = false;
        boolean creeperClose = false;

        for (Entity e : nearbyMobs) {
            double distSq = e.distanceToSqr(player);
            if (distSq <= 5.5 * 5.5) {
                species5m.add(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString());
            }
            if (e instanceof Enemy) {
                if (distSq <= 8.0 * 8.0) hostileNear8 = true;
                if (distSq <= 6.2 * 6.2) hostileNear6 = true;
            }
            if (distSq <= 4.5 * 4.5 && "creeper".equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath())) {
                creeperClose = true;
            }
        }

        if (species5m.size() >= 2) {
            campaign.addContribution("icebreaker_seconds", 1);
        }
        if (creeperClose) {
            campaign.addContribution("creeper_close_sec", 1);
        }

        if (hostileNear8 && isArmorEmpty(player)) {
            completedAny |= advanceContract(player, account, "RISK_SURVIVAL", 1);
            if (hostileNear6 && hasAnySoldStat(account)) {
                campaign.addContribution("no_armor_hazard_sec", 1);
            }
        }

        data.setDirty();
        return completedAny;
    }

    private static boolean isArmorEmpty(ServerPlayer player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                && player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
                && player.getItemBySlot(EquipmentSlot.LEGS).isEmpty()
                && player.getItemBySlot(EquipmentSlot.FEET).isEmpty();
    }

    private static boolean hasAnySoldStat(PlayerMarketAccount account) {
        for (AssetType type : AssetType.values()) {
            if (account.getTotalSoldFraction(type) > 0.001) return true;
        }
        return false;
    }
}
