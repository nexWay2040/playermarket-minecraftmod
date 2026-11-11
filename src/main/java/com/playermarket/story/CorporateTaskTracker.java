package com.playermarket.story;

import com.playermarket.market.MarketSavedData;
import com.playermarket.market.PlayerMarketAccount;
import com.playermarket.network.MarketNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.MerchantMenu;

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
        if (player == null || account == null || player.isSpectator()
                || account.isPermanentImpaired() || account.isMarketCardBlocked() || account.isAccountClosed()) {
            return false;
        }
        if (player.level().getServer() != null) {
            var campaign = MarketSavedData.get(player.level().getServer()).getCampaignData();
            if (StoryManager.isProgramTerminated(campaign, account)) {
                return false;
            }
        }
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
        if (player.isSpectator() || StoryManager.isProgramTerminated(campaign, account)) return false;

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

        // 1. Crouch transitions ("DRG Перекур" dance squats + Chaos HR Workout directive)
        boolean crouching = player.isCrouching();
        if (crouching && !state.wasCrouching) {
            campaign.addContribution("dance_squats", 1);
            contractDone |= advanceContract(player, account, "DANCE_SQUATS", 1);
            CorporateChaosDirector.onPlayerCrouch(player, data, campaign);
            changed = true;
        }
        state.wasCrouching = crouching;

        // 2. Camera rotation ("Датчик паранойи" — works anywhere!)
        float currentYaw = player.getYRot();
        float yawDelta = Math.abs(Mth.wrapDegrees(currentYaw - state.lastYaw));
        state.lastYaw = currentYaw;
        if (yawDelta > 1.0F && yawDelta < 160.0F) {
            state.yawAccumulator += yawDelta;
            if (state.yawAccumulator >= 30.0) {
                int deg = (int) Math.floor(state.yawAccumulator);
                state.yawAccumulator -= deg;
                contractDone |= advanceContract(player, account, "PARANOID_SPIN", deg);
                campaign.addContribution("paranoid_degrees", deg);
                changed = true;
            }
        }

        // 3. Fall & clutch landing ("Гравитация — актив фирмы" / "Прыжок веры")
        if (!player.onGround() && !player.isInWater() && player.fallDistance > state.peakFall) {
            state.peakFall = player.fallDistance;
        } else if ((player.onGround() || player.isInWater()) && state.peakFall >= 7.5) {
            int survivedHeight = (int) Math.round(state.peakFall);
            state.peakFall = 0.0;
            if (player.isAlive() && survivedHeight >= 8) {
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
     * Runs once every second (20 ticks) for non-tedious active contracts & merchant state.
     */
    public static boolean tickOneSecond(ServerPlayer player, PlayerMarketAccount account, SharedCampaignData campaign, MarketSavedData data) {
        if (player.isSpectator() || StoryManager.isProgramTerminated(campaign, account)) return false;

        boolean completedAny = false;
        completedAny |= advanceContract(player, account, "SURVIVE_SEC", 1);

        if (player.containerMenu instanceof MerchantMenu) {
            campaign.setObjective("villager_trade", 1);
        }

        data.setDirty();
        return completedAny;
    }
}
