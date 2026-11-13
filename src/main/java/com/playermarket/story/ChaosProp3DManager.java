package com.playermarket.story;

import com.mojang.math.Transformation;
import com.playermarket.item.MarketPropItems;
import com.playermarket.market.MarketSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

/**
 * Manages synchronized animated 3D Display & Interaction props for Corporate Chaos events.
 * Uses multi-part Blockbench-style cuboid item models rendered via ItemDisplay entities.
 */
public final class ChaosProp3DManager {

    public static final String PROP_TAG = "playermarket_3d_prop";
    private static final DustParticleOptions DRONE_RING_DUST = new DustParticleOptions(0xFFFF2438, 1.25f);
    private static final DustParticleOptions CROWN_AURA_DUST = new DustParticleOptions(0xFFFFD83B, 1.0f);
    private static final DustParticleOptions VERITY_AURA_DUST = new DustParticleOptions(0xFFFFEA38, 1.15f);
    private static final DustParticleOptions CRATE_BEACON_DUST = new DustParticleOptions(0xFF22D3EE, 1.35f);
    private static final DustParticleOptions ATM_BEACON_DUST = new DustParticleOptions(0xFF34D399, 1.35f);
    private static final DustParticleOptions CAPSULE_BEACON_DUST = new DustParticleOptions(0xFFE879F9, 1.35f);

    public enum PropKind {
        HR_BOMB("playermarket_prop_bomb"),
        SUPPLY_CRATE("playermarket_prop_crate"),
        ATM_SAFE("playermarket_prop_atm"),
        AUDIT_DRONE("playermarket_prop_drone"),
        BOUNTY_CROWN("playermarket_prop_crown"),
        MYSTERY_CAPSULE("playermarket_prop_capsule"),
        CCTV_POD("playermarket_prop_cctv"),
        VERITY_KOLOBOK("playermarket_prop_verity");

        public final String tag;

        PropKind(String tag) {
            this.tag = tag;
        }
    }

    private static final class PropRig {
        final PropKind kind;
        final UUID mainDisplayId;
        UUID secondaryDisplayId; // e.g., Drone spinning rotor blades
        UUID textDisplayId;
        UUID hitboxId;
        UUID ownerPlayerId;
        double x;
        double y;
        double z;
        float yawRad;
        float secondarySpinRad;
        float shakePower;
        int stateValue;
        long createdTick;
        long nextHopTick;

        PropRig(PropKind kind, UUID mainDisplayId, long createdTick, double x, double y, double z) {
            this.kind = kind;
            this.mainDisplayId = mainDisplayId;
            this.createdTick = createdTick;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final Map<PropKind, PropRig> ACTIVE_RIGS = new EnumMap<>(PropKind.class);
    private static final Map<UUID, Long> HITBOX_DEBOUNCE = new HashMap<>();

    private ChaosProp3DManager() {
    }

    public static void resetRuntimeState() {
        ACTIVE_RIGS.clear();
        HITBOX_DEBOUNCE.clear();
    }

    public static void clearAll(MinecraftServer server) {
        if (server != null) {
            for (PropRig rig : new ArrayList<>(ACTIVE_RIGS.values())) {
                discardRigEntities(server, rig);
            }
            // Failsafe sweep removes any residual tagged props across loaded dimensions (e.g., after crash/restart)
            for (ServerLevel sl : server.getAllLevels()) {
                discardTagged(sl, EntityTypes.ITEM_DISPLAY);
                discardTagged(sl, EntityTypes.TEXT_DISPLAY);
                discardTagged(sl, EntityTypes.INTERACTION);
            }
        }
        resetRuntimeState();
    }

    public static void removeKind(MinecraftServer server, PropKind kind) {
        PropRig rig = ACTIVE_RIGS.remove(kind);
        if (rig != null && server != null) {
            discardRigEntities(server, rig);
        }
    }

    private static <T extends Entity> void discardTagged(ServerLevel sl, EntityType<T> type) {
        for (T entity : sl.getEntities(type, e -> e.entityTags().contains(PROP_TAG))) {
            entity.discard();
        }
    }

    private static void discardRigEntities(MinecraftServer server, PropRig rig) {
        discardUuid(server, rig.mainDisplayId);
        discardUuid(server, rig.secondaryDisplayId);
        discardUuid(server, rig.textDisplayId);
        discardUuid(server, rig.hitboxId);
    }

    private static void discardUuid(MinecraftServer server, UUID id) {
        if (id == null) return;
        for (ServerLevel sl : server.getAllLevels()) {
            Entity e = sl.getEntity(id);
            if (e != null) {
                e.discard();
                return;
            }
        }
    }

    private static Entity findEntity(MinecraftServer server, UUID id) {
        if (server == null || id == null) return null;
        for (ServerLevel sl : server.getAllLevels()) {
            Entity e = sl.getEntity(id);
            if (e != null) return e;
        }
        return null;
    }

    // === SPAWNERS ===

    public static void spawnOrUpdateBomb(MinecraftServer server, ServerPlayer holder, int secRemaining, long gameTime) {
        if (server == null || holder == null || !(holder.level() instanceof ServerLevel sl)) return;
        PropRig rig = ACTIVE_RIGS.get(PropKind.HR_BOMB);
        double tx = holder.getX();
        double ty = holder.getY() + 2.45;
        double tz = holder.getZ();

        if (rig == null || findEntity(server, rig.mainDisplayId) == null) {
            removeKind(server, PropKind.HR_BOMB);
            Display.ItemDisplay itemDisp = createItemDisplay(sl, tx, ty, tz, MarketPropItems.PROP_HR_BOMB, 0xFFEF4444, PropKind.HR_BOMB.tag);
            Display.ItemDisplay orbitDisp = createItemDisplay(sl, tx + 1.55, holder.getY() + 1.25, tz, MarketPropItems.PROP_HR_BOMB, 0xFFEF4444, PropKind.HR_BOMB.tag);
            Display.TextDisplay textDisp = createTextDisplay(sl, tx, ty + 0.85, tz, "§c§l💣 " + secRemaining + "с!", PropKind.HR_BOMB.tag);
            if (itemDisp == null) return;
            rig = new PropRig(PropKind.HR_BOMB, itemDisp.getUUID(), gameTime, tx, ty, tz);
            if (orbitDisp != null) {
                orbitDisp.setShadowRadius(0.0f);
                applyTransform(orbitDisp, 0f, 0f, 0f, 0f, 0f, 0f, 0.65f);
                rig.secondaryDisplayId = orbitDisp.getUUID();
            }
            if (textDisp != null) rig.textDisplayId = textDisp.getUUID();
            ACTIVE_RIGS.put(PropKind.HR_BOMB, rig);
        }
        rig.ownerPlayerId = holder.getUUID();
        rig.stateValue = Math.max(1, secRemaining);
    }

    public static void spawnOrUpdateBountyCrown(MinecraftServer server, ServerPlayer holder, int secRemaining, long gameTime) {
        if (server == null || holder == null || !(holder.level() instanceof ServerLevel sl)) return;
        PropRig rig = ACTIVE_RIGS.get(PropKind.BOUNTY_CROWN);
        double tx = holder.getX();
        double ty = holder.getY() + 2.52;
        double tz = holder.getZ();

        if (rig == null || findEntity(server, rig.mainDisplayId) == null) {
            removeKind(server, PropKind.BOUNTY_CROWN);
            Display.ItemDisplay crown = createItemDisplay(sl, tx, ty, tz, MarketPropItems.PROP_BOUNTY_CROWN, 0xFFFACC15, PropKind.BOUNTY_CROWN.tag);
            Display.TextDisplay label = createTextDisplay(sl, tx, ty + 0.80, tz,
                    "§e§l👑 КОРОНА: §f" + holder.getName().getString() + " §7(" + secRemaining + "с)", PropKind.BOUNTY_CROWN.tag);
            if (crown == null) return;
            rig = new PropRig(PropKind.BOUNTY_CROWN, crown.getUUID(), gameTime, tx, ty, tz);
            if (label != null) rig.textDisplayId = label.getUUID();
            ACTIVE_RIGS.put(PropKind.BOUNTY_CROWN, rig);
        }
        rig.ownerPlayerId = holder.getUUID();
        rig.stateValue = Math.max(1, secRemaining);
    }

    public static void spawnCctvFollower(MinecraftServer server, ServerPlayer target, boolean compliant, long gameTime) {
        if (server == null || target == null || !(target.level() instanceof ServerLevel sl)) return;
        removeKind(server, PropKind.CCTV_POD);
        double tx = target.getX() + 1.3;
        double ty = target.getY() + 2.5;
        double tz = target.getZ() + 1.3;
        int color = compliant ? 0xFF22C55E : 0xFFEF4444;
        String labelText = compliant ? "§a§l📹 БИО-ЗАЛОГ: НОРМА" : "§c§l📹 НАРУШЕНИЕ: 0 СТАТОВ!";
        Display.ItemDisplay cam = createItemDisplay(sl, tx, ty, tz, MarketPropItems.PROP_CCTV_POD, color, PropKind.CCTV_POD.tag);
        Display.TextDisplay label = createTextDisplay(sl, tx, ty + 0.68, tz, labelText, PropKind.CCTV_POD.tag);
        if (cam == null) return;
        PropRig rig = new PropRig(PropKind.CCTV_POD, cam.getUUID(), gameTime, tx, ty, tz);
        if (label != null) rig.textDisplayId = label.getUUID();
        rig.ownerPlayerId = target.getUUID();
        rig.stateValue = compliant ? 1 : 0;
        ACTIVE_RIGS.put(PropKind.CCTV_POD, rig);
    }

    public static void spawnAuditDrone(MinecraftServer server, ServerPlayer target, long gameTime) {
        if (server == null || target == null || !(target.level() instanceof ServerLevel sl)) return;
        removeKind(server, PropKind.AUDIT_DRONE);
        double tx = target.getX();
        double ty = target.getY() + 3.85;
        double tz = target.getZ();

        Display.ItemDisplay body = createItemDisplay(sl, tx, ty, tz, MarketPropItems.PROP_AUDIT_DRONE_BODY, 0xFFEF4444, PropKind.AUDIT_DRONE.tag);
        Display.ItemDisplay rotors = createItemDisplay(sl, tx, ty + 0.08, tz, MarketPropItems.PROP_AUDIT_DRONE_ROTORS, 0xFF38BDF8, PropKind.AUDIT_DRONE.tag);
        Display.TextDisplay label = createTextDisplay(sl, tx, ty + 0.92, tz, "§c§l🛸 ДРОН HR — БЕГИ ИЗ КРУГА!", PropKind.AUDIT_DRONE.tag);
        if (body == null) return;
        PropRig rig = new PropRig(PropKind.AUDIT_DRONE, body.getUUID(), gameTime, tx, ty, tz);
        if (rotors != null) rig.secondaryDisplayId = rotors.getUUID();
        if (label != null) rig.textDisplayId = label.getUUID();
        rig.ownerPlayerId = target.getUUID();
        ACTIVE_RIGS.put(PropKind.AUDIT_DRONE, rig);
    }

    public static void spawnSupplyCrate(ServerLevel sl, BlockPos pos, long gameTime) {
        removeKind(sl.getServer(), PropKind.SUPPLY_CRATE);
        double tx = pos.getX() + 0.5;
        double ty = pos.getY() + 0.65;
        double tz = pos.getZ() + 0.5;

        Display.ItemDisplay crate = createItemDisplay(sl, tx, ty, tz, MarketPropItems.PROP_SUPPLY_CRATE, 0xFF22D3EE, PropKind.SUPPLY_CRATE.tag);
        Display.TextDisplay label = createTextDisplay(sl, tx, ty + 0.95, tz, "§b§l[📦 ГРУЗ ASSET CORP — ПКМ/УДАР]", PropKind.SUPPLY_CRATE.tag);
        Interaction hitbox = createHitbox(sl, tx, pos.getY() + 0.05, tz, 1.45f, 1.35f, PropKind.SUPPLY_CRATE.tag);
        if (crate == null || hitbox == null) return;

        PropRig rig = new PropRig(PropKind.SUPPLY_CRATE, crate.getUUID(), gameTime, tx, ty, tz);
        if (label != null) rig.textDisplayId = label.getUUID();
        rig.hitboxId = hitbox.getUUID();
        ACTIVE_RIGS.put(PropKind.SUPPLY_CRATE, rig);
    }

    public static void spawnAtmSafe(ServerLevel sl, BlockPos groundPos, long gameTime) {
        removeKind(sl.getServer(), PropKind.ATM_SAFE);
        double tx = groundPos.getX() + 0.5;
        double ty = groundPos.getY() + 0.70;
        double tz = groundPos.getZ() + 0.5;

        Display.ItemDisplay atm = createItemDisplay(sl, tx, ty + 4.0, tz, MarketPropItems.PROP_ATM_SAFE, 0xFF34D399, PropKind.ATM_SAFE.tag);
        Display.TextDisplay label = createTextDisplay(sl, tx, ty + 1.25, tz, "§a§l[🏧 БАНКОМАТ HR: 0/6 — БЕЙ/ПКМ!]", PropKind.ATM_SAFE.tag);
        Interaction hitbox = createHitbox(sl, tx, groundPos.getY(), tz, 1.45f, 1.55f, PropKind.ATM_SAFE.tag);
        if (atm == null || hitbox == null) return;

        PropRig rig = new PropRig(PropKind.ATM_SAFE, atm.getUUID(), gameTime, tx, ty, tz);
        if (label != null) rig.textDisplayId = label.getUUID();
        rig.hitboxId = hitbox.getUUID();
        rig.stateValue = 0; // hits landed 0..6
        ACTIVE_RIGS.put(PropKind.ATM_SAFE, rig);
    }

    public static void spawnMysteryCapsule(ServerLevel sl, BlockPos pos, long gameTime) {
        removeKind(sl.getServer(), PropKind.MYSTERY_CAPSULE);
        double tx = pos.getX() + 0.5;
        double ty = pos.getY() + 0.72;
        double tz = pos.getZ() + 0.5;

        Display.ItemDisplay capsule = createItemDisplay(sl, tx, ty, tz, MarketPropItems.PROP_MYSTERY_CAPSULE, 0xFFE879F9, PropKind.MYSTERY_CAPSULE.tag);
        Display.TextDisplay label = createTextDisplay(sl, tx, ty + 1.05, tz, "§d§l[🎁 КОТ В МЕШКЕ 0/2 — ЛОВИ!]", PropKind.MYSTERY_CAPSULE.tag);
        Interaction hitbox = createHitbox(sl, tx, pos.getY(), tz, 1.35f, 1.45f, PropKind.MYSTERY_CAPSULE.tag);
        if (capsule == null || hitbox == null) return;

        PropRig rig = new PropRig(PropKind.MYSTERY_CAPSULE, capsule.getUUID(), gameTime, tx, ty, tz);
        if (label != null) rig.textDisplayId = label.getUUID();
        rig.hitboxId = hitbox.getUUID();
        rig.stateValue = 0;
        rig.nextHopTick = gameTime + 65L;
        ACTIVE_RIGS.put(PropKind.MYSTERY_CAPSULE, rig);
    }

    public static void spawnVerityKolobok(ServerLevel sl, BlockPos groundPos, long gameTime) {
        removeKind(sl.getServer(), PropKind.VERITY_KOLOBOK);
        double tx = groundPos.getX() + 0.5;
        double baseGroundY = groundPos.getY();
        double tz = groundPos.getZ() + 0.5;

        Display.ItemDisplay verity = createItemDisplay(sl, tx, baseGroundY + 0.50, tz, MarketPropItems.PROP_VERITY_KOLOBOK, 0xFFFACC15, PropKind.VERITY_KOLOBOK.tag);
        Display.TextDisplay label = createTextDisplay(sl, tx, baseGroundY + 1.38, tz, "§e§l🟡 ВЕРИТИ-КОЛОБОК [ПКМ/УДАР]", PropKind.VERITY_KOLOBOK.tag);
        Interaction hitbox = createHitbox(sl, tx, baseGroundY, tz, 1.35f, 1.35f, PropKind.VERITY_KOLOBOK.tag);
        if (verity == null || hitbox == null) return;

        verity.setShadowRadius(0.32f);
        PropRig rig = new PropRig(PropKind.VERITY_KOLOBOK, verity.getUUID(), gameTime, tx, baseGroundY, tz);
        if (label != null) rig.textDisplayId = label.getUUID();
        rig.hitboxId = hitbox.getUUID();
        rig.yawRad = sl.getRandom().nextFloat() * 6.283f;
        ACTIVE_RIGS.put(PropKind.VERITY_KOLOBOK, rig);
    }

    public static Vec3 getDronePos() {
        PropRig rig = ACTIVE_RIGS.get(PropKind.AUDIT_DRONE);
        return rig == null ? null : new Vec3(rig.x, rig.y, rig.z);
    }

    public static int getAtmHits() {
        PropRig rig = ACTIVE_RIGS.get(PropKind.ATM_SAFE);
        return rig == null ? 0 : rig.stateValue;
    }

    public static void setAtmShake(int hits) {
        PropRig rig = ACTIVE_RIGS.get(PropKind.ATM_SAFE);
        if (rig != null) {
            rig.stateValue = hits;
            rig.shakePower = 0.28f;
        }
    }

    public static void updateTextLabel(MinecraftServer server, PropKind kind, String text) {
        PropRig rig = ACTIVE_RIGS.get(kind);
        if (rig == null || rig.textDisplayId == null) return;
        Entity e = findEntity(server, rig.textDisplayId);
        if (e instanceof Display.TextDisplay td) {
            td.setText(Component.literal(text));
        }
    }

    // === ANIMATION & INTERACTION POLL TICK ===

    private static boolean dronePositionLocked = false;
    private static double droneLockedGroundY = 0.0;

    public static void setDroneLocked(boolean locked, double groundY) {
        dronePositionLocked = locked;
        if (locked) {
            droneLockedGroundY = groundY;
        }
    }

    public static boolean isDroneLocked() {
        return dronePositionLocked;
    }

    public static boolean hasActiveEncounterProp() {
        return ACTIVE_RIGS.containsKey(PropKind.VERITY_KOLOBOK)
                || ACTIVE_RIGS.containsKey(PropKind.SUPPLY_CRATE)
                || ACTIVE_RIGS.containsKey(PropKind.ATM_SAFE)
                || ACTIVE_RIGS.containsKey(PropKind.MYSTERY_CAPSULE)
                || ACTIVE_RIGS.containsKey(PropKind.HR_BOMB)
                || ACTIVE_RIGS.containsKey(PropKind.AUDIT_DRONE);
    }

    public static void tickAll(MinecraftServer server, MarketSavedData data, SharedCampaignData campaign, long gameTime) {
        if (server == null) return;

        // Clean chaos-exclusive rigs when no directive is active, while letting standalone
        // world encounters (Verity, Crates, ATMs, Capsules) live out their 34s timer cleanly.
        if ("NONE".equals(data.getActiveChaosId())) {
            dronePositionLocked = false;
            removeKind(server, PropKind.HR_BOMB);
            removeKind(server, PropKind.AUDIT_DRONE);
            removeKind(server, PropKind.BOUNTY_CROWN);
            removeKind(server, PropKind.CCTV_POD);
        }
        if (!ACTIVE_RIGS.containsKey(PropKind.AUDIT_DRONE)) {
            dronePositionLocked = false;
        }
        for (PropKind standalone : new PropKind[]{PropKind.VERITY_KOLOBOK, PropKind.SUPPLY_CRATE, PropKind.ATM_SAFE, PropKind.MYSTERY_CAPSULE}) {
            PropRig sRig = ACTIVE_RIGS.get(standalone);
            if (sRig != null && "NONE".equals(data.getActiveChaosId()) && gameTime - sRig.createdTick >= 680L) {
                removeKind(server, standalone);
            }
        }
        if (ACTIVE_RIGS.isEmpty()) return;

        for (var entry : new ArrayList<>(ACTIVE_RIGS.entrySet())) {
            PropKind kind = entry.getKey();
            PropRig rig = entry.getValue();
            if (!ACTIVE_RIGS.containsKey(kind)) continue;

            Entity mainEnt = findEntity(server, rig.mainDisplayId);
            if (!(mainEnt instanceof Display.ItemDisplay itemDisplay) || !mainEnt.isAlive()) {
                ACTIVE_RIGS.remove(kind);
                discardRigEntities(server, rig);
                continue;
            }

            switch (kind) {
                case HR_BOMB -> animateBomb(server, rig, itemDisplay, gameTime);
                case BOUNTY_CROWN -> animateCrown(server, rig, itemDisplay, gameTime);
                case CCTV_POD -> animateCctv(server, rig, itemDisplay, gameTime);
                case AUDIT_DRONE -> animateDrone(server, rig, itemDisplay, gameTime);
                case SUPPLY_CRATE -> animateSupplyCrate(server, rig, itemDisplay, gameTime);
                case ATM_SAFE -> animateAtm(server, rig, itemDisplay, gameTime);
                case MYSTERY_CAPSULE -> animateMysteryCapsule(server, rig, itemDisplay, gameTime);
                case VERITY_KOLOBOK -> animateVerityKolobok(server, rig, itemDisplay, gameTime);
            }
        }
    }

    private static void animateBomb(MinecraftServer server, PropRig rig, Display.ItemDisplay item, long gameTime) {
        ServerPlayer holder = rig.ownerPlayerId != null ? server.getPlayerList().getPlayer(rig.ownerPlayerId) : null;
        if (holder == null || !holder.isAlive() || !(holder.level() instanceof ServerLevel sl)) return;

        rig.x = holder.getX();
        rig.y = holder.getY() + 2.48 + Math.sin(gameTime * 0.25) * 0.12;
        rig.z = holder.getZ();

        moveEntity(item, sl, rig.x, rig.y, rig.z);
        float spinStep = rig.stateValue <= 5 ? 0.36f : 0.16f;
        rig.yawRad += spinStep;
        float pulse = rig.stateValue <= 5
                ? 1.0f + (float) Math.abs(Math.sin(gameTime * 0.45)) * 0.32f
                : 0.92f + (float) Math.sin(gameTime * 0.20) * 0.08f;

        if (gameTime % 2L == 0L) {
            applyTransform(item, 0f, 0f, 0f, 0f, rig.yawRad, 0f, pulse);
        }

        // Orbiting eye-level miniature 3D bomb indicator so solo 1st-person players clearly see it!
        Entity orbitEnt = findEntity(server, rig.secondaryDisplayId);
        if (orbitEnt instanceof Display.ItemDisplay orbitBomb && orbitBomb.level() instanceof ServerLevel osl) {
            double ang = gameTime * 0.14;
            double ox = holder.getX() + Math.cos(ang) * 1.55;
            double oy = holder.getY() + 1.25 + Math.sin(gameTime * 0.30) * 0.10;
            double oz = holder.getZ() + Math.sin(ang) * 1.55;
            moveEntity(orbitBomb, osl, ox, oy, oz);
            rig.secondarySpinRad += spinStep * 1.3f;
            if (gameTime % 2L == 0L) {
                float miniScale = rig.stateValue <= 5 ? 0.56f : 0.44f;
                applyTransform(orbitBomb, 0f, 0f, 0f, 0f, rig.secondarySpinRad, 0f, miniScale);
            }
            if (gameTime % 4L == 0L) {
                osl.sendParticles(ParticleTypes.FLAME, ox, oy + 0.25, oz, 1, 0.04, 0.04, 0.04, 0.01);
            }
        }

        Entity label = findEntity(server, rig.textDisplayId);
        if (label instanceof Display.TextDisplay td && td.level() instanceof ServerLevel tsl) {
            moveEntity(td, tsl, rig.x, rig.y + 0.85, rig.z);
            if (gameTime % 5L == 0L) {
                td.setText(Component.literal((rig.stateValue <= 5 ? "§4§l" : "§c§l") + "💣 " + rig.stateValue + "с — СБРОСЬ!"));
            }
        }
        if (gameTime % 3L == 0L) {
            sl.sendParticles(ParticleTypes.FLAME, rig.x, rig.y + 0.48, rig.z, 2, 0.08, 0.08, 0.08, 0.01);
            sl.sendParticles(ParticleTypes.SMOKE, rig.x, rig.y + 0.52, rig.z, 1, 0.06, 0.06, 0.06, 0.01);
        }
    }

    private static void animateCrown(MinecraftServer server, PropRig rig, Display.ItemDisplay item, long gameTime) {
        ServerPlayer holder = rig.ownerPlayerId != null ? server.getPlayerList().getPlayer(rig.ownerPlayerId) : null;
        if (holder == null || !holder.isAlive() || !(holder.level() instanceof ServerLevel sl)) return;

        rig.x = holder.getX();
        rig.y = holder.getY() + 2.48 + Math.sin(gameTime * 0.15) * 0.10;
        rig.z = holder.getZ();

        moveEntity(item, sl, rig.x, rig.y, rig.z);
        rig.yawRad += 0.11f;
        if (gameTime % 2L == 0L) {
            applyTransform(item, 0f, 0f, 0f, 0.08f * (float) Math.sin(gameTime * 0.12), rig.yawRad, 0f, 1.15f);
        }

        Entity label = findEntity(server, rig.textDisplayId);
        if (label instanceof Display.TextDisplay td && td.level() instanceof ServerLevel tsl) {
            moveEntity(td, tsl, rig.x, rig.y + 0.78, rig.z);
            if (gameTime % 10L == 0L) {
                td.setText(Component.literal("§e§l👑 ЦЕЛЬ: §f" + holder.getName().getString() + " §7(" + rig.stateValue + "с)"));
            }
        }
        if (gameTime % 5L == 0L) {
            sl.sendParticles(CROWN_AURA_DUST, holder.getX(), holder.getY() + 1.2, holder.getZ(), 4, 0.35, 0.45, 0.35, 0.01);
        }
    }

    private static void animateCctv(MinecraftServer server, PropRig rig, Display.ItemDisplay item, long gameTime) {
        ServerPlayer target = rig.ownerPlayerId != null ? server.getPlayerList().getPlayer(rig.ownerPlayerId) : null;
        if (target == null || !target.isAlive() || !(target.level() instanceof ServerLevel sl)) return;

        double angle = gameTime * 0.05;
        rig.x = target.getX() + Math.cos(angle) * 1.45;
        rig.y = target.getY() + 2.45 + Math.sin(gameTime * 0.12) * 0.08;
        rig.z = target.getZ() + Math.sin(angle) * 1.45;

        moveEntity(item, sl, rig.x, rig.y, rig.z);
        float lookYaw = (float) Math.atan2(target.getX() - rig.x, target.getZ() - rig.z);
        if (gameTime % 2L == 0L) {
            applyTransform(item, 0f, 0f, 0f, -0.15f, lookYaw, 0f, 1.0f);
        }

        Entity label = findEntity(server, rig.textDisplayId);
        if (label instanceof Display.TextDisplay td && td.level() instanceof ServerLevel tsl) {
            moveEntity(td, tsl, rig.x, rig.y + 0.65, rig.z);
        }
    }

    private static void animateDrone(MinecraftServer server, PropRig rig, Display.ItemDisplay body, long gameTime) {
        ServerPlayer target = rig.ownerPlayerId != null ? server.getPlayerList().getPlayer(rig.ownerPlayerId) : null;
        if (target == null || !target.isAlive() || !(target.level() instanceof ServerLevel sl)) return;

        double baseGroundY = dronePositionLocked ? droneLockedGroundY : target.getY();
        double hoverY = baseGroundY + 2.65 + Math.sin(gameTime * (dronePositionLocked ? 0.45 : 0.22)) * 0.16;
        double dx = target.getX() - rig.x;
        double dz = target.getZ() - rig.z;
        if (!dronePositionLocked) {
            // Approach smoothly with capped horizontal step so players can easily reposition
            double stepX = Math.max(-0.14, Math.min(0.14, dx * 0.065));
            double stepZ = Math.max(-0.14, Math.min(0.14, dz * 0.065));
            rig.x += stepX;
            rig.z += stepZ;
        }
        rig.y += (hoverY - rig.y) * 0.22;

        moveEntity(body, sl, rig.x, rig.y, rig.z);
        rig.yawRad = (float) Math.atan2(dx, dz);
        float pitchTilt = (float) Math.min(0.28, Math.hypot(dx, dz) * 0.06);
        if (gameTime % 2L == 0L) {
            applyTransform(body, 0f, 0f, 0f, pitchTilt, rig.yawRad, 0f, 1.15f);
        }

        Entity rotorsEnt = findEntity(server, rig.secondaryDisplayId);
        if (rotorsEnt instanceof Display.ItemDisplay rotors && rotors.level() instanceof ServerLevel rsl) {
            moveEntity(rotors, rsl, rig.x, rig.y + 0.10, rig.z);
            rig.secondarySpinRad += 0.65f;
            if (gameTime % 2L == 0L) {
                applyTransform(rotors, 0f, 0f, 0f, pitchTilt * 0.5f, rig.secondarySpinRad, 0f, 1.2f);
            }
        }

        Entity label = findEntity(server, rig.textDisplayId);
        if (label instanceof Display.TextDisplay td && td.level() instanceof ServerLevel tsl) {
            moveEntity(td, tsl, rig.x, rig.y + 0.90, rig.z);
        }

        // Draw crisp perimeter strike ring at the drone's target X/Z (freezes during lock-on!)
        if (gameTime % 2L == 0L) {
            double groundY = (dronePositionLocked ? droneLockedGroundY : target.getY()) + 0.15;
            double radius = 3.0;
            int pts = 16;
            double phase = (gameTime % 40L) * 0.05;
            for (int i = 0; i < pts; i++) {
                double a = (i * (Math.PI * 2.0) / pts) + phase;
                sl.sendParticles(DRONE_RING_DUST,
                        rig.x + Math.cos(a) * radius, groundY, rig.z + Math.sin(a) * radius,
                        1, 0.0, 0.0, 0.0, 0.0);
            }
            if (gameTime % 6L == 0L) {
                sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, rig.x, rig.y - 0.3, rig.z, 2, 0.1, 0.2, 0.1, 0.02);
            }
        }
    }

    private static void animateSupplyCrate(MinecraftServer server, PropRig rig, Display.ItemDisplay crate, long gameTime) {
        if (!(crate.level() instanceof ServerLevel sl)) return;
        double bobY = rig.y + Math.sin(gameTime * 0.12) * 0.12;
        moveEntity(crate, sl, rig.x, bobY, rig.z);
        rig.yawRad += 0.055f;
        if (gameTime % 2L == 0L) {
            applyTransform(crate, 0f, 0f, 0f, 0f, rig.yawRad, 0f, 1.12f);
        }
        Entity label = findEntity(server, rig.textDisplayId);
        if (label instanceof Display.TextDisplay td && td.level() instanceof ServerLevel tsl) {
            moveEntity(td, tsl, rig.x, bobY + 0.92, rig.z);
        }
        if (gameTime % 6L == 0L) {
            sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, rig.x, bobY + 0.2, rig.z, 2, 0.35, 0.35, 0.35, 0.04);
            emitBeaconColumn(sl, rig.x, bobY + 0.5, rig.z, CRATE_BEACON_DUST);
        }
    }

    private static void animateAtm(MinecraftServer server, PropRig rig, Display.ItemDisplay atm, long gameTime) {
        if (!(atm.level() instanceof ServerLevel sl)) return;
        double animY = rig.y + Math.sin(gameTime * 0.08) * 0.03;
        moveEntity(atm, sl, rig.x, animY, rig.z);
        float shake = rig.shakePower;
        rig.shakePower *= 0.72f;
        float tiltZ = shake > 0.01f ? (float) Math.sin(gameTime * 2.1) * shake : 0f;
        float scale = 1.18f + shake * 0.45f;
        rig.yawRad += 0.02f;
        if (gameTime % 2L == 0L || shake > 0.05f) {
            applyTransform(atm, 0f, 0f, 0f, 0f, rig.yawRad, tiltZ, scale);
        }
        Entity label = findEntity(server, rig.textDisplayId);
        if (label instanceof Display.TextDisplay td && td.level() instanceof ServerLevel tsl) {
            moveEntity(td, tsl, rig.x, animY + 1.25, rig.z);
        }
        if (gameTime % 6L == 0L) {
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, rig.x, rig.y + 0.3, rig.z, 2, 0.35, 0.45, 0.35, 0.02);
            emitBeaconColumn(sl, rig.x, rig.y + 0.5, rig.z, ATM_BEACON_DUST);
        }
    }

    private static void animateMysteryCapsule(MinecraftServer server, PropRig rig, Display.ItemDisplay capsule, long gameTime) {
        if (!(capsule.level() instanceof ServerLevel sl)) return;

        // Every ~3.2s, playfully teleport-hop near an online player!
        if (gameTime >= rig.nextHopTick && !sl.players().isEmpty()) {
            rig.nextHopTick = gameTime + 68L;
            ServerPlayer near = sl.players().get(sl.getRandom().nextInt(sl.players().size()));
            BlockPos dest = CorporateChaosDirector.findForwardGroundPosPublic(sl, near, 3, 6);
            rig.x = dest.getX() + 0.5;
            rig.y = dest.getY() + 0.72;
            rig.z = dest.getZ() + 0.5;
            Entity hb = findEntity(server, rig.hitboxId);
            if (hb != null) {
                hb.setPos(rig.x, dest.getY(), rig.z);
                hb.needsSync = true;
            }
            sl.sendParticles(ParticleTypes.PORTAL, rig.x, rig.y + 0.2, rig.z, 18, 0.35, 0.45, 0.35, 0.2);
        }

        double bobY = rig.y + Math.sin(gameTime * 0.22) * 0.16;
        moveEntity(capsule, sl, rig.x, bobY, rig.z);
        rig.yawRad += 0.12f;
        if (gameTime % 2L == 0L) {
            applyTransform(capsule, 0f, 0f, 0f, 0.12f * (float) Math.sin(gameTime * 0.15), rig.yawRad, 0f, 1.05f);
        }
        Entity label = findEntity(server, rig.textDisplayId);
        if (label instanceof Display.TextDisplay td && td.level() instanceof ServerLevel tsl) {
            moveEntity(td, tsl, rig.x, bobY + 0.95, rig.z);
        }
        if (gameTime % 5L == 0L) {
            sl.sendParticles(ParticleTypes.END_ROD, rig.x, bobY + 0.15, rig.z, 2, 0.25, 0.3, 0.25, 0.02);
            emitBeaconColumn(sl, rig.x, bobY + 0.4, rig.z, CAPSULE_BEACON_DUST);
        }
    }

    private static void animateVerityKolobok(MinecraftServer server, PropRig rig, Display.ItemDisplay verity, long gameTime) {
        if (!(verity.level() instanceof ServerLevel sl)) return;

        ServerPlayer nearest = null;
        double bestDistSq = 22.0 * 22.0;
        for (ServerPlayer sp : sl.players()) {
            if (sp.isSpectator() || !sp.isAlive()) continue;
            double dSq = (sp.getX() - rig.x) * (sp.getX() - rig.x) + (sp.getZ() - rig.z) * (sp.getZ() - rig.z);
            if (dSq < bestDistSq) {
                bestDistSq = dSq;
                nearest = sp;
            }
        }

        // Grounded sphere movement: slow, playful, easy to catch even after selling speed!
        double speed = bestDistSq < 6.0 * 6.0 ? 0.085 : 0.055;
        if (nearest != null) {
            double dx = rig.x - nearest.getX();
            double dz = rig.z - nearest.getZ();
            double len = Math.max(0.05, Math.hypot(dx, dz));
            double strafe = Math.sin(gameTime * 0.16) * 0.55;
            double fx = (dx / len) - (dz / len) * strafe;
            double fz = (dz / len) + (dx / len) * strafe;
            double fLen = Math.max(0.05, Math.hypot(fx, fz));
            double nextX = rig.x + (fx / fLen) * speed;
            double nextZ = rig.z + (fz / fLen) * speed;
            Double nextGroundY = resolveGroundedSurfaceY(sl, nextX, rig.y, nextZ);
            if (nextGroundY != null) {
                rig.x = nextX;
                rig.y = nextGroundY;
                rig.z = nextZ;
                rig.yawRad = (float) Math.atan2(fx, fz);
            } else {
                // Smoothly turn away from walls/cliffs without ever flying up
                double altFx = -(dz / len);
                double altFz = (dx / len);
                double ax = rig.x + altFx * speed;
                double az = rig.z + altFz * speed;
                Double altGroundY = resolveGroundedSurfaceY(sl, ax, rig.y, az);
                if (altGroundY != null) {
                    rig.x = ax;
                    rig.y = altGroundY;
                    rig.z = az;
                    rig.yawRad = (float) Math.atan2(altFx, altFz);
                }
            }
        }

        // Grounded roll + bouncy hop arc (max ~0.42 blocks off ground, never airborne flight)
        double hopArc = Math.abs(Math.sin(gameTime * 0.24)) * 0.42;
        double visualY = rig.y + 0.50 + hopArc;
        rig.secondarySpinRad += (float) (speed * 2.1);

        moveEntity(verity, sl, rig.x, visualY, rig.z);
        if (gameTime % 2L == 0L) {
            applyTransform(verity, 0f, 0f, 0f, rig.secondarySpinRad, rig.yawRad, 0f, 0.92f);
        }

        Entity hb = findEntity(server, rig.hitboxId);
        if (hb != null) {
            hb.setPos(rig.x, rig.y, rig.z);
            hb.needsSync = true;
        }
        Entity label = findEntity(server, rig.textDisplayId);
        if (label instanceof Display.TextDisplay td && td.level() instanceof ServerLevel tsl) {
            moveEntity(td, tsl, rig.x, visualY + 0.85, rig.z);
        }
        if (gameTime % 4L == 0L) {
            sl.sendParticles(VERITY_AURA_DUST, rig.x, visualY, rig.z, 2, 0.20, 0.15, 0.20, 0.0);
            emitBeaconColumn(sl, rig.x, visualY + 0.35, rig.z, VERITY_AURA_DUST);
        }
    }

    private static Double resolveGroundedSurfaceY(ServerLevel sl, double x, double curY, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int baseY = (int) Math.round(curY);
        for (int dy = 1; dy >= -2; dy--) {
            BlockPos feet = new BlockPos(bx, baseY + dy, bz);
            BlockPos below = feet.below();
            BlockPos head = feet.above();
            if (!sl.getBlockState(below).getCollisionShape(sl, below).isEmpty()
                    && sl.getBlockState(feet).getCollisionShape(sl, feet).isEmpty()
                    && sl.getBlockState(head).getCollisionShape(sl, head).isEmpty()) {
                return (double) feet.getY();
            }
        }
        return null;
    }

    private static void emitBeaconColumn(ServerLevel sl, double x, double startY, double z, DustParticleOptions color) {
        for (int i = 0; i < 6; i++) {
            sl.sendParticles(color, x, startY + 0.8 + i * 1.15, z, 1, 0.04, 0.04, 0.04, 0.0);
        }
    }

    /**
     * Checks if an interacted/attacked entity belongs to one of our 3D Interaction hitboxes.
     */
    public static boolean handleHitboxTrigger(ServerPlayer player, Entity target, MarketSavedData data,
                                              SharedCampaignData campaign, long gameTime) {
        if (player == null || target == null) return false;
        Set<String> tags = target.entityTags();
        if (!tags.contains(PROP_TAG)) return false;

        Long last = HITBOX_DEBOUNCE.get(target.getUUID());
        if (last != null && (gameTime - last) < 4L) {
            return true;
        }

        for (PropKind kind : List.of(PropKind.SUPPLY_CRATE, PropKind.ATM_SAFE, PropKind.MYSTERY_CAPSULE, PropKind.VERITY_KOLOBOK)) {
            if (tags.contains(kind.tag)) {
                HITBOX_DEBOUNCE.put(target.getUUID(), gameTime);
                PropRig rig = ACTIVE_RIGS.get(kind);
                double px = rig != null ? rig.x : target.getX();
                double py = rig != null ? rig.y : target.getY() + 0.5;
                double pz = rig != null ? rig.z : target.getZ();
                CorporateChaosDirector.onWorldPropClicked(player, kind, px, py, pz, data, campaign, gameTime);
                return true;
            }
        }
        return false;
    }

    // === LOW-LEVEL ENTITY BUILDERS ===

    private static Display.ItemDisplay createItemDisplay(ServerLevel sl, double x, double y, double z,
                                                         Item modelItem, int glowColorRgb, String extraTag) {
        Display.ItemDisplay disp = EntityTypes.ITEM_DISPLAY.create(sl, EntitySpawnReason.EVENT);
        if (disp == null) return null;
        disp.setPos(x, y, z);
        disp.addTag(PROP_TAG);
        disp.addTag(extraTag);
        disp.setItemStack(new ItemStack(modelItem));
        disp.setItemTransform(ItemDisplayContext.FIXED);
        disp.setBillboardConstraints(Display.BillboardConstraints.FIXED);
        disp.setBrightnessOverride(Brightness.FULL_BRIGHT);
        disp.setViewRange(2.0f);
        disp.setShadowRadius(0.45f);
        disp.setShadowStrength(0.45f);
        disp.setGlowingTag(true);
        disp.setGlowColorOverride(glowColorRgb);
        disp.setPosRotInterpolationDuration(3);
        disp.setTransformationInterpolationDuration(3);
        disp.setTransformationInterpolationDelay(0);
        sl.addFreshEntity(disp);
        return disp;
    }

    private static Display.TextDisplay createTextDisplay(ServerLevel sl, double x, double y, double z,
                                                         String initialText, String extraTag) {
        Display.TextDisplay text = EntityTypes.TEXT_DISPLAY.create(sl, EntitySpawnReason.EVENT);
        if (text == null) return null;
        text.setPos(x, y, z);
        text.addTag(PROP_TAG);
        text.addTag(extraTag);
        text.setText(Component.literal(initialText));
        text.setBillboardConstraints(Display.BillboardConstraints.CENTER);
        text.setBrightnessOverride(Brightness.FULL_BRIGHT);
        text.setBackgroundColor(0xA0080D1A);
        text.setLineWidth(240);
        text.setFlags((byte) (Display.TextDisplay.FLAG_SHADOW | Display.TextDisplay.FLAG_SEE_THROUGH));
        text.setViewRange(2.0f);
        text.setPosRotInterpolationDuration(3);
        sl.addFreshEntity(text);
        return text;
    }

    private static Interaction createHitbox(ServerLevel sl, double x, double y, double z,
                                            float width, float height, String extraTag) {
        Interaction hb = EntityTypes.INTERACTION.create(sl, EntitySpawnReason.EVENT);
        if (hb == null) return null;
        hb.setPos(x, y, z);
        hb.addTag(PROP_TAG);
        hb.addTag(extraTag);
        hb.setWidth(width);
        hb.setHeight(height);
        hb.setResponse(true);
        sl.addFreshEntity(hb);
        return hb;
    }

    private static void moveEntity(Entity entity, ServerLevel targetLevel, double x, double y, double z) {
        if (entity.level() != targetLevel) {
            entity.teleportTo(targetLevel, x, y, z, Set.of(), 0f, 0f, false);
        } else {
            entity.setPos(x, y, z);
            entity.needsSync = true;
        }
    }

    private static void applyTransform(Display display, float tx, float ty, float tz,
                                       float rx, float ry, float rz, float scale) {
        display.setTransformationInterpolationDuration(2);
        display.setTransformation(new Transformation(
                new Vector3f(tx, ty, tz),
                new Quaternionf().rotateXYZ(rx, ry, rz),
                new Vector3f(scale, scale, scale),
                new Quaternionf()
        ));
    }
}
