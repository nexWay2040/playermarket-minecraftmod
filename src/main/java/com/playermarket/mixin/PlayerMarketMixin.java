package com.playermarket.mixin;

import com.playermarket.item.BlockedMarketCardItem;
import com.playermarket.item.CrumpledLetterItem;
import com.playermarket.item.MarketAccessCardItem;
import com.playermarket.market.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerMarketMixin {

    @ModifyVariable(method = "giveExperiencePoints", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int playermarket$modifyExperienceGain(int experience) {
        if (experience <= 0) return experience;
        Player self = (Player) (Object) this;
        if (self instanceof ServerPlayer sp && sp.level().getServer() != null) {
            MarketSavedData savedData = MarketSavedData.get(sp.level().getServer());
            PlayerMarketAccount acc = savedData.getOrCreateAccount(sp.getUUID());
            long gameTick = sp.level().getServer().overworld().getGameTime();

            double soldXp = acc.getTotalSoldFraction(AssetType.XP_GAIN);
            double mult = 1.0 - soldXp;

            int xpAccLvl = acc.getUpgradeLevel(UpgradeType.XP_ACCELERATOR);
            if (xpAccLvl > 0) {
                mult += UpgradeType.XP_ACCELERATOR.getValueForLevel(xpAccLvl);
            }
            if (acc.hasDividend("experience_dividend")) {
                mult += 0.15;
            }
            if (acc.hasDividend("metabolic_focus")) {
                mult += 0.10;
            }
            if (acc.isBlackMarketEffectActive(BlackMarketItem.OVERCLOCKED_MIND, gameTick)) {
                mult += 1.00;
            }

            int modified = Math.max(1, (int) Math.round(experience * Math.max(0.15, mult)));
            if (!com.playermarket.story.StoryManager.isProgramTerminated(savedData.getCampaignData(), acc)) {
                com.playermarket.story.CorporateTaskTracker.advanceContract(sp, acc, "GAIN_XP", modified);
                savedData.setDirty();
            }
            return modified;
        }
        return experience;
    }

    @Inject(method = "dropEquipment", at = @At("HEAD"))
    private void playermarket$vanishCardsOnDeath(ServerLevel level, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        Inventory inv = self.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (isVanishingCard(s)) {
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
        if (self.containerMenu != null && isVanishingCard(self.containerMenu.getCarried())) {
            self.containerMenu.setCarried(ItemStack.EMPTY);
        }
    }

    private static boolean isVanishingCard(ItemStack s) {
        return s != null && !s.isEmpty() && (s.is(MarketAccessCardItem.MARKET_ACCESS_CARD)
                || s.is(BlockedMarketCardItem.BLOCKED_MARKET_CARD)
                || s.is(CrumpledLetterItem.CRUMPLED_LETTER));
    }

    @ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float playermarket$modifyFoodExhaustion(float exhaustion) {
        if (exhaustion <= 0.0F) return exhaustion;
        Player self = (Player) (Object) this;
        if (self instanceof ServerPlayer sp && sp.level().getServer() != null) {
            PlayerMarketAccount acc = MarketSavedData.get(sp.level().getServer()).getOrCreateAccount(sp.getUUID());
            if (acc.isPermanentImpaired()) {
                return exhaustion;
            }
            double soldSat = acc.getTotalSoldFraction(AssetType.SATURATION_EFFICIENCY);
            double soldHunger = acc.getTotalSoldFraction(AssetType.HUNGER_EFFICIENCY);
            int activeLeases = acc.getActiveLeases().size();
            int effMetaLvl = acc.getUpgradeLevel(UpgradeType.EFFICIENT_METABOLISM);
            int metaUpgLvl = acc.getUpgradeLevel(UpgradeType.METABOLISM_UPGRADE);

            double mult = 1.0 + (soldSat * 2.20) + (soldHunger * 2.80) + (activeLeases * 0.25);
            if (effMetaLvl > 0) {
                mult -= UpgradeType.EFFICIENT_METABOLISM.getValueForLevel(effMetaLvl);
            }
            if (metaUpgLvl > 0) {
                mult -= UpgradeType.METABOLISM_UPGRADE.getValueForLevel(metaUpgLvl) * 0.5;
            }
            if (acc.hasDividend("steady_pace")) {
                mult -= 0.15;
            }

            return (float) (exhaustion * Math.max(0.20, mult));
        }
        return exhaustion;
    }
}
