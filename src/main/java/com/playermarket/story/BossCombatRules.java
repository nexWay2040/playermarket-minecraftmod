package com.playermarket.story;

import com.playermarket.entity.ChiefAuditorBossEntity;
import com.playermarket.item.MarketRelicItems;
import com.playermarket.market.MarketSavedData;
import com.playermarket.market.PlayerMarketAccount;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Единая система оружейного допуска и расчёта урона по Главному Аудитору 17.4.
 * <p>
 * Сертифицированное оружие (ТОЛЬКО 2 предмета): {@code MarketRelicItems.DOOM_HAMMER} и {@code Items.NETHERITE_SWORD}.
 * Любые стрелы, трезубцы и другое оружие блокируются корпоративной бронёй (0.0f).
 */
public final class BossCombatRules {

    private static final int CORE_HIT_COOLDOWN_TICKS = 8;      // анти-автокликер по ядру
    private static final int ACTIONBAR_HINT_COOLDOWN_TICKS = 40; // не чаще раза в 2 секунды

    private static final float BODY_DAMAGE_CORE_CLOSED = 2.0F;
    private static final float BODY_DAMAGE_CORE_OPEN_SWORD = 8.0F;
    private static final float BODY_DAMAGE_CORE_OPEN_HAMMER = 14.0F;

    private static final float CORE_DAMAGE_CLOSED = 3.0F;
    private static final float CORE_DAMAGE_OPEN_SWORD = 20.0F;
    private static final float CORE_DAMAGE_OPEN_HAMMER = 58.0F;
    private static final float CORE_DAMAGE_OPEN_HAMMER_GRAPPLE_CRIT = 85.0F;

    private static final ThreadLocal<Boolean> INTERNAL_DIRECT_DAMAGE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static final Map<UUID, Long> LAST_CORE_HIT_TICK = new HashMap<>();
    private static final Map<UUID, Long> LAST_HINT_TICK = new HashMap<>();

    private BossCombatRules() {
    }

    public static void clearCooldowns() {
        LAST_CORE_HIT_TICK.clear();
        LAST_HINT_TICK.clear();
    }

    public static boolean isBypassingArmorRules() {
        return Boolean.TRUE.equals(INTERNAL_DIRECT_DAMAGE.get());
    }

    public static boolean isCertifiedWeapon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return stack.is(MarketRelicItems.DOOM_HAMMER) || stack.is(Items.NETHERITE_SWORD);
    }

    /**
     * Вызывается при ударе по физическому хитбоксу ядра в груди Титана ({@code hitCore = true}).
     *
     * @return {@code true}, если взаимодействие/удар обработаны.
     */
    public static boolean applyCoreDirectHit(ServerLevel level, ChiefAuditorBossEntity boss, ServerPlayer attacker) {
        if (level == null || boss == null || !boss.isAlive() || attacker == null) {
            return false;
        }

        long gameTime = level.getGameTime();
        UUID attackerId = attacker.getUUID();

        Long lastCoreTick = LAST_CORE_HIT_TICK.get(attackerId);
        if (lastCoreTick != null && gameTime >= lastCoreTick && (gameTime - lastCoreTick) < CORE_HIT_COOLDOWN_TICKS) {
            return true;
        }

        ItemStack weapon = attacker.getMainHandItem();
        Vec3 coreFxPos = getCoreVisualPos(boss);

        if (!isCertifiedWeapon(weapon)) {
            LAST_CORE_HIT_TICK.put(attackerId, gameTime);
            playArmorBlockFeedback(level, attacker, coreFxPos, gameTime);
            return true;
        }

        LAST_CORE_HIT_TICK.put(attackerId, gameTime);
        attacker.resetAttackStrengthTicker();

        boolean coreOpen = boss.isCoreOpen();
        if (!coreOpen) {
            // Ядро закрыто бронеплитами: 3.0f урона + искры + подсказка
            applyBypassDirectDamage(level, boss, attacker, CORE_DAMAGE_CLOSED);
            spawnArmorSparks(level, coreFxPos, 10);
            level.playSound(null, coreFxPos.x, coreFxPos.y, coreFxPos.z,
                    SoundEvents.ANVIL_PLACE, SoundSource.HOSTILE, 1.0F, 1.35F);
            sendThrottledHint(attacker, gameTime, "§cБронеплиты ядра закрыты!");
            return true;
        }

        boolean isHammer = weapon.is(MarketRelicItems.DOOM_HAMMER);
        if (!isHammer) {
            // Незеритовый меч в открытое ядро: 20.0f чистого урона
            applyBypassDirectDamage(level, boss, attacker, CORE_DAMAGE_OPEN_SWORD);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    coreFxPos.x, coreFxPos.y, coreFxPos.z,
                    16, 0.35, 0.35, 0.35, 0.14);
            level.sendParticles(ParticleTypes.CRIT,
                    coreFxPos.x, coreFxPos.y, coreFxPos.z,
                    12, 0.3, 0.3, 0.3, 0.12);
            level.playSound(null, coreFxPos.x, coreFxPos.y, coreFxPos.z,
                    SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1.2F, 1.15F);
            attacker.sendOverlayMessage(Component.literal("§b⚡ УДАР В ЯДРО! (-20 HP)"));
            return true;
        }

        // Doom Hammer: проверяем импульс Крюка-Кошки (в воздухе / падение / высокая скорость / недавний крюк)
        boolean grappleMomentum = hasGrappleMomentum(level.getServer(), attacker, gameTime);
        if (grappleMomentum) {
            applyBypassDirectDamage(level, boss, attacker, CORE_DAMAGE_OPEN_HAMMER_GRAPPLE_CRIT);

            level.sendParticles(ParticleTypes.EXPLOSION,
                    coreFxPos.x, coreFxPos.y, coreFxPos.z,
                    3, 0.35, 0.35, 0.35, 0.02);
            level.sendParticles(ParticleTypes.END_ROD,
                    coreFxPos.x, coreFxPos.y, coreFxPos.z,
                    28, 0.55, 0.55, 0.55, 0.22);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    coreFxPos.x, coreFxPos.y, coreFxPos.z,
                    24, 0.5, 0.5, 0.5, 0.2);

            level.playSound(null, coreFxPos.x, coreFxPos.y, coreFxPos.z,
                    SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.4F, 0.75F);
            level.playSound(null, coreFxPos.x, coreFxPos.y, coreFxPos.z,
                    SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.2F, 1.1F);

            attacker.sendOverlayMessage(Component.literal("§6§l⚡ КРИТ С КРЮКА ПО ЯДРУ! (-85 HP)"));
        } else {
            applyBypassDirectDamage(level, boss, attacker, CORE_DAMAGE_OPEN_HAMMER);

            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    coreFxPos.x, coreFxPos.y, coreFxPos.z,
                    22, 0.45, 0.45, 0.45, 0.16);
            level.sendParticles(ParticleTypes.END_ROD,
                    coreFxPos.x, coreFxPos.y, coreFxPos.z,
                    12, 0.35, 0.35, 0.35, 0.1);
            level.playSound(null, coreFxPos.x, coreFxPos.y, coreFxPos.z,
                    SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.1F, 1.05F);

            attacker.sendOverlayMessage(Component.literal("§e⚡ МОЛОТОМ В ЯДРО! (-58 HP)"));
        }

        return true;
    }

    /**
     * Вызывается из {@code LivingEntityMarketMixin} при обычном ударе по телу/ногам босса ({@code hitCore = false}).
     *
     * @return скорректированный урон, либо {@code 0.0f} при блокировке корпоративной бронёй.
     */
    public static float filterBodyDamage(
            ServerLevel level,
            ChiefAuditorBossEntity boss,
            DamageSource source,
            float incomingAmount
    ) {
        if (Boolean.TRUE.equals(INTERNAL_DIRECT_DAMAGE.get())) {
            return incomingAmount;
        }
        if (source == null || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return incomingAmount;
        }
        if (level == null || boss == null) {
            return 0.0F;
        }

        long gameTime = level.getGameTime();
        Vec3 hitFxPos = boss.position().add(0.0, boss.getBbHeight() * 0.4, 0.0);

        // Любые стрелы / трезубцы / метательные снаряды блокируются корпоративной бронёй
        Entity directEntity = source.getDirectEntity();
        if (directEntity instanceof Projectile projectile) {
            spawnArmorSparks(level, projectile.position(), 10);
            level.playSound(null, projectile.getX(), projectile.getY(), projectile.getZ(),
                    SoundEvents.ANVIL_PLACE, SoundSource.HOSTILE, 0.9F, 1.45F);
            if (projectile.getOwner() instanceof ServerPlayer shooter) {
                sendThrottledHint(shooter, gameTime, "§c§lCORPORATE ARMOR §7— нужен Молот Corp или Незеритовый меч!");
            }
            return 0.0F;
        }

        if (!(source.getEntity() instanceof ServerPlayer attacker)) {
            spawnArmorSparks(level, hitFxPos, 6);
            return 0.0F;
        }

        ItemStack weapon = attacker.getMainHandItem();
        if (!isCertifiedWeapon(weapon)) {
            playArmorBlockFeedback(level, attacker, hitFxPos, gameTime);
            return 0.0F;
        }

        if (!boss.isCoreOpen()) {
            spawnArmorSparks(level, hitFxPos, 8);
            level.playSound(null, hitFxPos.x, hitFxPos.y, hitFxPos.z,
                    SoundEvents.ANVIL_PLACE, SoundSource.HOSTILE, 0.85F, 1.35F);
            sendThrottledHint(attacker, gameTime, "§eБронеплиты закрыты — ждите раскрытия ядра в груди!");
            return BODY_DAMAGE_CORE_CLOSED;
        }

        sendThrottledHint(attacker, gameTime, "§aЦельтесь выше — бейте в ЯДРО В ГРУДИ!");
        return weapon.is(MarketRelicItems.DOOM_HAMMER)
                ? BODY_DAMAGE_CORE_OPEN_HAMMER
                : BODY_DAMAGE_CORE_OPEN_SWORD;
    }

    private static void applyBypassDirectDamage(
            ServerLevel level,
            ChiefAuditorBossEntity boss,
            ServerPlayer attacker,
            float exactPureDamage
    ) {
        INTERNAL_DIRECT_DAMAGE.set(Boolean.TRUE);
        try {
            // sonicBoom атрибутирует убийцу attacker и наносит 100% чистый урон без срезки бронёй
            boss.hurtServer(level, level.damageSources().sonicBoom(attacker), exactPureDamage);
            if (exactPureDamage >= CORE_DAMAGE_OPEN_HAMMER && boss.isAlive()) {
                boss.triggerCoreOverload();
            }
        } finally {
            INTERNAL_DIRECT_DAMAGE.set(Boolean.FALSE);
        }
    }

    private static boolean hasGrappleMomentum(MinecraftServer server, ServerPlayer attacker, long gameTime) {
        if (!attacker.onGround()
                || attacker.fallDistance > 0.35F
                || attacker.getDeltaMovement().length() > 0.40) {
            return true;
        }
        if (server != null) {
            MarketSavedData data = MarketSavedData.get(server);
            if (data != null) {
                PlayerMarketAccount acc = data.getOrCreateAccount(attacker.getUUID());
                if (acc != null && acc.getGrappleSafeTick() > gameTime) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void playArmorBlockFeedback(ServerLevel level, ServerPlayer attacker, Vec3 pos, long gameTime) {
        spawnArmorSparks(level, pos, 12);
        level.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ANVIL_PLACE, SoundSource.HOSTILE, 0.95F, 1.4F);
        sendThrottledHint(attacker, gameTime, "§c§lCORPORATE ARMOR §7— нужен Молот Corp или Незеритовый меч!");
    }

    private static void spawnArmorSparks(ServerLevel level, Vec3 pos, int count) {
        level.sendParticles(ParticleTypes.CRIT,
                pos.x, pos.y, pos.z,
                count, 0.45, 0.45, 0.45, 0.14);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                pos.x, pos.y, pos.z,
                Math.max(4, count / 2), 0.35, 0.35, 0.35, 0.1);
    }

    private static void sendThrottledHint(ServerPlayer player, long gameTime, String message) {
        UUID id = player.getUUID();
        Long last = LAST_HINT_TICK.get(id);
        if (last == null || gameTime < last || (gameTime - last) >= ACTIONBAR_HINT_COOLDOWN_TICKS) {
            LAST_HINT_TICK.put(id, gameTime);
            player.sendOverlayMessage(Component.literal(message));
        }
    }

    private static Vec3 getCoreVisualPos(ChiefAuditorBossEntity boss) {
        float yawRad = boss.yBodyRot * ((float) Math.PI / 180.0F);
        double fx = -Math.sin(yawRad) * 1.15;
        double fz = Math.cos(yawRad) * 1.15;
        return new Vec3(boss.getX() + fx, boss.getY() + 3.1, boss.getZ() + fz);
    }
}