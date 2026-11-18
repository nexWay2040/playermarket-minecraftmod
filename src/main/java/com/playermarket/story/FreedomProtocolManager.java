package com.playermarket.story;

import com.playermarket.PlayerMarketMod;
import com.playermarket.item.BlockedMarketCardItem;
import com.playermarket.item.CorporateGrappleItem;
import com.playermarket.item.MarketAccessCardItem;
import com.playermarket.item.MarketRelicItems;
import com.playermarket.market.MarketAttributeApplier;
import com.playermarket.market.MarketEvent;
import com.playermarket.market.MarketSavedData;
import com.playermarket.market.PlayerMarketAccount;
import com.playermarket.network.MarketNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Пост-победная концовка THE FREEDOM PROTOCOL после ликвидации Главного Аудитора 17.4.
 * <p>
 * Полностью заменяет старую скример-концовку с киком:
 * <ul>
 *     <li>Все состояния хранятся внутри {@code PlayerMarketAccount#getEndingState()} без расширения DFU CODEC.</li>
 *     <li>Возвращает 100% проданных органов и начисляет +5000 CR.</li>
 *     <li>Навсегда блокирует карту доступа (заменяет её на BlockedMarketCard) и отключает квоты/хаос.</li>
 *     <li>Делает Крюк-Кошку «Liberated» (бесплатный, без износа) и даёт выбрать 1 из 3 вечных специализаций.</li>
 * </ul>
 */
public final class FreedomProtocolManager {

    public static final String FREEDOM_PREFIX = "VICTORY_FREEDOM";
    public static final long FREEDOM_TROPHY_CR = 5000L;
    public static final long SURVIVOR_SECOND_WIND_COOLDOWN_TICKS = 6000L; // 5 минут

    private static final Identifier MOD_TITAN_HEALTH = PlayerMarketMod.id("freedom_titan_health");
    private static final Identifier MOD_MOVEMENT = PlayerMarketMod.id("freedom_movement");
    private static final Identifier MOD_EXPLORER_OXYGEN = PlayerMarketMod.id("freedom_explorer_oxygen");
    private static final Identifier MOD_EXPLORER_WATER_MOVE = PlayerMarketMod.id("freedom_explorer_water_move");
    private static final Identifier MOD_CRAFTSMAN_BREAK = PlayerMarketMod.id("freedom_craftsman_break_speed");
    private static final Identifier MOD_SURVIVOR_ARMOR = PlayerMarketMod.id("freedom_survivor_armor");
    private static final Identifier MOD_SURVIVOR_EXPLOSION_KB = PlayerMarketMod.id("freedom_survivor_explosion_kb");

    private static final Map<UUID, Long> SURVIVOR_SECOND_WIND_COOLDOWNS = new HashMap<>();

    private FreedomProtocolManager() {
    }

    public enum LegacySpec {
        NONE("Без специализации"),
        EXPLORER("Исследователь"),
        CRAFTSMAN("Мастер"),
        SURVIVOR("Выживший");

        private final String displayName;

        LegacySpec(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public static boolean isFreedomActive(PlayerMarketAccount acc) {
        if (acc == null) {
            return false;
        }
        String state = acc.getEndingState();
        return state != null && state.startsWith(FREEDOM_PREFIX);
    }

    public static LegacySpec getLegacySpec(PlayerMarketAccount acc) {
        if (!isFreedomActive(acc)) {
            return LegacySpec.NONE;
        }
        String state = acc.getEndingState();
        int sep = state.indexOf(':');
        if (sep < 0 || sep + 1 >= state.length()) {
            return LegacySpec.NONE;
        }
        String suffix = state.substring(sep + 1).trim().toUpperCase(Locale.ROOT);
        try {
            return LegacySpec.valueOf(suffix);
        } catch (IllegalArgumentException ignored) {
            return LegacySpec.NONE;
        }
    }

    public static boolean isGrappleLiberated(PlayerMarketAccount acc) {
        return isFreedomActive(acc);
    }

    public static double getFreedomFallDamageMultiplier(PlayerMarketAccount acc) {
        return isFreedomActive(acc) ? 0.85 : 1.0;
    }

    public static void clearCooldowns() {
        SURVIVOR_SECOND_WIND_COOLDOWNS.clear();
    }

    /**
     * Активирует THE FREEDOM PROTOCOL для всего сервера сразу после смерти Главного Аудитора 17.4.
     */
    public static void activateFreedomProtocol(MinecraftServer server, ServerLevel level, Vec3 deathPos) {
        if (server == null) {
            return;
        }

        MarketSavedData data = MarketSavedData.get(server);
        if (data == null) {
            return;
        }

        // 1. Останавливаем квоты, биржу и корпоративный хаос навсегда
        var campaign = data.getCampaignData();
        if (campaign != null) {
            campaign.setCurrentStage(6);
            campaign.setEndingStarted(true);
            campaign.setEndingApplied(true);
            campaign.setCinematicCompleted(true);
            campaign.setDisconnectSent(true);
            campaign.setEndingState(FREEDOM_PREFIX);
        }
        long gameTime = server.overworld().getGameTime();
        data.setActiveEvent(MarketEvent.STABLE_MARKET, gameTime);
        data.setActiveChaos("NONE", 0L);
        data.setChaosState("PEACE");
        CorporateChaosDirector.clearAllWorldEntities(server);

        // 2. Обновляем все аккаунты игроков (даже оффлайн)
        for (PlayerMarketAccount acc : data.getAccountsView().values()) {
            if (acc == null) {
                continue;
            }
            if (!isFreedomActive(acc)) {
                acc.getPermanentSoldFractions().clear();
                acc.getActiveLeases().clear();
                acc.getContracts().clear();
                acc.addCr(FREEDOM_TROPHY_CR);
                acc.addTransaction(gameTime, "FREEDOM PROTOCOL", "Контракт 17.4 расторгнут",
                        "Полный возврат всех 30 характеристик + вечные перки", FREEDOM_TROPHY_CR, 0);
                acc.setEndingState(FREEDOM_PREFIX);
            }
            acc.setMarketCardBlocked(true);
            acc.setAccountClosed(true);
            acc.setPermanentImpaired(false);
        }

        data.setDirty();

        // Праздничные эффекты на точке падения Титана
        if (level != null && deathPos != null) {
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                    deathPos.x, deathPos.y + 2.0, deathPos.z,
                    80, 2.2, 2.0, 2.2, 0.35);
            level.playSound(null, deathPos.x, deathPos.y, deathPos.z,
                    SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1.8F, 1.0F);
        }

        // 3. Обновляем инвентарь, модификаторы и уведомляем всех онлайн-игроков без кика
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerMarketAccount acc = data.getOrCreateAccount(player.getUUID());
            if (!isFreedomActive(acc)) {
                acc.getPermanentSoldFractions().clear();
                acc.getActiveLeases().clear();
                acc.getContracts().clear();
                acc.addCr(FREEDOM_TROPHY_CR);
                acc.setEndingState(FREEDOM_PREFIX);
            }
            acc.setMarketCardBlocked(true);
            acc.setAccountClosed(true);
            acc.setPermanentImpaired(false);

            replaceMarketCardsWithBlocked(player);
            grantLiberatedGrappleIfMissing(player);
            player.removeEffect(MobEffects.BLINDNESS);
            player.removeEffect(MobEffects.SLOWNESS);
            player.removeEffect(MobEffects.DARKNESS);
            MarketAttributeApplier.syncPlayerModifiers(player);

            // Восстанавливаем здоровье до нового максимума как знак полного исцеления тела
            player.setHealth(player.getMaxHealth());
            player.getFoodData().setFoodLevel(20);

            ScreenAlertHelper.sendSafeBanner(
                    player,
                    "§a§l🕊 СВОБОДА!",
                    "§eПротокол 17.4 завершён",
                    "§a§l[FREEDOM PROTOCOL] §r§eГлавный Аудитор повержен! Все органы восстановлены (100%), +5000 CR, Крюк-Кошка освобождён от износа и КД, а карта Corp заблокирована навсегда."
            );

            if (getLegacySpec(acc) == LegacySpec.NONE) {
                sendLegacySpecChoiceMessage(player);
            }
        }
        data.setDirty();
        MarketNetworking.broadcastSync(server, "§a§lFREEDOM PROTOCOL АКТИВИРОВАН! Карта Corp аннулирована, вечные бонусы активны!");
        CorporateProgressionHints.onAuditorDefeated(server);
    }

    /**
     * Сохраняет выбор 1 из 3 вечных специализаций внутри {@code endingState} и применяет атрибуты.
     */
    public static boolean selectLegacySpec(ServerPlayer player, LegacySpec spec) {
        if (player == null || spec == null || spec == LegacySpec.NONE) {
            return false;
        }

        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return false;
        }

        MarketSavedData data = MarketSavedData.get(server);
        if (data == null) {
            return false;
        }

        PlayerMarketAccount acc = data.getOrCreateAccount(player.getUUID());
        if (acc == null || !isFreedomActive(acc)) {
            player.sendSystemMessage(Component.literal("✖ Протокол Свободы ещё не активирован!")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        acc.setEndingState(FREEDOM_PREFIX + ":" + spec.name());
        data.setDirty();

        MarketAttributeApplier.syncPlayerModifiers(player);
        MarketNetworking.sendSyncToPlayer(player, "§6Наследие Свободы: " + spec.getDisplayName());

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.15F);
        player.sendOverlayMessage(Component.literal("★ НАСЛЕДИЕ СВОБОДЫ: " + spec.getDisplayName() + " ★")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal("✔ Вечная специализация выбрана: ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(spec.getDisplayName()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
        return true;
    }

    /**
     * Применяет или полностью снимает постоянные AttributeModifier Протокола Свободы.
     */
    public static void applyFreedomModifiers(ServerPlayer player, PlayerMarketAccount acc) {
        if (player == null) {
            return;
        }

        removeAllFreedomModifiers(player);
        if (!isFreedomActive(acc)) {
            return;
        }

        // Общие бонусы Свободы для всех победителей
        applyPermanentModifier(player, Attributes.MAX_HEALTH,
                new AttributeModifier(MOD_TITAN_HEALTH, 2.0, AttributeModifier.Operation.ADD_VALUE));
        applyPermanentModifier(player, Attributes.MOVEMENT_SPEED,
                new AttributeModifier(MOD_MOVEMENT, 0.08, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

        // Уникальные бонусы выбранной специализации
        LegacySpec spec = getLegacySpec(acc);
        switch (spec) {
            case EXPLORER -> {
                applyPermanentModifier(player, Attributes.OXYGEN_BONUS,
                        new AttributeModifier(MOD_EXPLORER_OXYGEN, 4.0, AttributeModifier.Operation.ADD_VALUE));
                applyPermanentModifier(player, Attributes.WATER_MOVEMENT_EFFICIENCY,
                        new AttributeModifier(MOD_EXPLORER_WATER_MOVE, 0.45, AttributeModifier.Operation.ADD_VALUE));
            }
            case CRAFTSMAN -> applyPermanentModifier(player, Attributes.BLOCK_BREAK_SPEED,
                    new AttributeModifier(MOD_CRAFTSMAN_BREAK, 0.20, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            case SURVIVOR -> {
                applyPermanentModifier(player, Attributes.ARMOR,
                        new AttributeModifier(MOD_SURVIVOR_ARMOR, 2.0, AttributeModifier.Operation.ADD_VALUE));
                applyPermanentModifier(player, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE,
                        new AttributeModifier(MOD_SURVIVOR_EXPLOSION_KB, 0.35, AttributeModifier.Operation.ADD_VALUE));
            }
            case NONE -> {
            }
        }

        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    /**
     * Раз в 5 минут (6000 тиков) спасает игрока со специализацией SURVIVOR при падении HP <= 4.0.
     */
    public static boolean tryTriggerSurvivorSecondWind(ServerPlayer player, PlayerMarketAccount acc, long gameTime) {
        if (player == null || acc == null || !isFreedomActive(acc)) {
            return false;
        }
        if (getLegacySpec(acc) != LegacySpec.SURVIVOR) {
            return false;
        }
        if (player.isCreative() || player.isSpectator()) {
            return false;
        }
        if (player.getHealth() > 4.0F) {
            return false;
        }

        UUID uuid = player.getUUID();
        Long lastTrigger = SURVIVOR_SECOND_WIND_COOLDOWNS.get(uuid);
        if (lastTrigger != null && gameTime >= lastTrigger && (gameTime - lastTrigger) < SURVIVOR_SECOND_WIND_COOLDOWN_TICKS) {
            return false;
        }

        SURVIVOR_SECOND_WIND_COOLDOWNS.put(uuid, gameTime);

        if (player.getHealth() <= 0.0F) {
            player.setHealth(Math.min(4.0F, player.getMaxHealth()));
        }

        // Regeneration II на 4с (80 тиков) + Absorption I на 6с (120 тиков)
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 80, 1, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 120, 0, false, true, true));

        ServerLevel level = (ServerLevel) player.level();
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                player.getX(), player.getY() + 1.0, player.getZ(),
                24, 0.45, 0.55, 0.45, 0.15);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8F, 1.15F);

        player.sendOverlayMessage(Component.literal("🛡 [ВТОРОЕ ДЫХАНИЕ] Выживший активирован! (КД 5 мин)")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        return true;
    }

    private static void replaceMarketCardsWithBlocked(ServerPlayer player) {
        Inventory inv = player.getInventory();
        int size = inv.getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.is(MarketAccessCardItem.MARKET_ACCESS_CARD)) {
                inv.setItem(i, new ItemStack(BlockedMarketCardItem.BLOCKED_MARKET_CARD, stack.getCount()));
            }
        }
        ItemStack carried = player.containerMenu.getCarried();
        if (!carried.isEmpty() && carried.is(MarketAccessCardItem.MARKET_ACCESS_CARD)) {
            player.containerMenu.setCarried(new ItemStack(BlockedMarketCardItem.BLOCKED_MARKET_CARD, carried.getCount()));
        }
    }

    private static void grantLiberatedGrappleIfMissing(ServerPlayer player) {
        Inventory inv = player.getInventory();
        int size = inv.getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof CorporateGrappleItem) {
                return;
            }
        }
        ItemStack grapple = new ItemStack(MarketRelicItems.CORPORATE_GRAPPLE);
        if (!inv.add(grapple) && player.level() instanceof ServerLevel sl) {
            player.spawnAtLocation(sl, grapple);
        }
    }

    private static void sendLegacySpecChoiceMessage(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("═══ [ВЫБЕРИ ВЕЧНОЕ НАСЛЕДИЕ СВОБОДЫ] ═══")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        MutableComponent buttons = Component.empty()
                .append(makeSpecButton(
                        "[Исследователь]",
                        "/market legacy explorer",
                        "Бонус: +4.0 запас воздуха под водой и +0.45 скорость плавания",
                        ChatFormatting.AQUA
                ))
                .append(Component.literal("  "))
                .append(makeSpecButton(
                        "[Мастер]",
                        "/market legacy craftsman",
                        "Бонус: +20% к скорости добычи блоков навсегда",
                        ChatFormatting.YELLOW
                ))
                .append(Component.literal("  "))
                .append(makeSpecButton(
                        "[Выживший]",
                        "/market legacy survivor",
                        "Бонус: +2 брони, +0.35 антиотброс взрывов и Второе Дыхание при HP <= 4 (КД 5 мин)",
                        ChatFormatting.GREEN
                ));

        player.sendSystemMessage(buttons);
        player.sendSystemMessage(Component.literal("Или введите вручную: /market legacy explorer | craftsman | survivor")
                .withStyle(ChatFormatting.GRAY));
    }

    private static MutableComponent makeSpecButton(String label, String command, String hoverDesc, ChatFormatting color) {
        Style style = Style.EMPTY
                .withColor(color)
                .withBold(true)
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(hoverDesc).withStyle(color)));
        return Component.literal(label).withStyle(style);
    }

    private static void removeAllFreedomModifiers(ServerPlayer player) {
        removeModifier(player, Attributes.MAX_HEALTH, MOD_TITAN_HEALTH);
        removeModifier(player, Attributes.MOVEMENT_SPEED, MOD_MOVEMENT);
        removeModifier(player, Attributes.OXYGEN_BONUS, MOD_EXPLORER_OXYGEN);
        removeModifier(player, Attributes.WATER_MOVEMENT_EFFICIENCY, MOD_EXPLORER_WATER_MOVE);
        removeModifier(player, Attributes.BLOCK_BREAK_SPEED, MOD_CRAFTSMAN_BREAK);
        removeModifier(player, Attributes.ARMOR, MOD_SURVIVOR_ARMOR);
        removeModifier(player, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, MOD_SURVIVOR_EXPLOSION_KB);
    }

    private static void removeModifier(
            ServerPlayer player,
            net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
            Identifier modifierId
    ) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(modifierId);
        }
    }

    private static void applyPermanentModifier(
            ServerPlayer player,
            net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
            AttributeModifier modifier
    ) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(modifier.id());
            instance.addPermanentModifier(modifier);
        }
    }
}