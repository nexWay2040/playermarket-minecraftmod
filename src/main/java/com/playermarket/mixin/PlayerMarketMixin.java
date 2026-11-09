package com.playermarket.mixin;

import com.playermarket.market.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

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
            acc.progressContracts("GAIN_XP", modified);
            savedData.setDirty();
            return modified;
        }
        return experience;
    }

    @ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float playermarket$modifyFoodExhaustion(float exhaustion) {
        if (exhaustion <= 0.0F) return exhaustion;
        Player self = (Player) (Object) this;
        if (self instanceof ServerPlayer sp && sp.level().getServer() != null) {
            PlayerMarketAccount acc = MarketSavedData.get(sp.level().getServer()).getOrCreateAccount(sp.getUUID());
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
