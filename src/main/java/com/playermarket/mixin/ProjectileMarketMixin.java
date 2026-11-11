package com.playermarket.mixin;

import com.playermarket.market.AssetType;
import com.playermarket.market.MarketSavedData;
import com.playermarket.market.PlayerMarketAccount;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Projectile.class)
public abstract class ProjectileMarketMixin {

    @ModifyVariable(method = "shootFromRotation", at = @At("HEAD"), argsOnly = true, ordinal = 4)
    private float playermarket$modifyInaccuracy(float inaccuracy, Entity shooter) {
        if (shooter instanceof ServerPlayer sp && sp.level().getServer() != null) {
            MarketSavedData data = MarketSavedData.get(sp.level().getServer());
            PlayerMarketAccount acc = data.getOrCreateAccount(sp.getUUID());
            if (!com.playermarket.story.StoryManager.isProgramTerminated(data.getCampaignData(), acc)) {
                data.getCampaignData().addContribution("ballistics_shots", 1);
                com.playermarket.story.CorporateTaskTracker.advanceContract(sp, acc, "THROW_BALLISTICS", 1);
                data.setDirty();
                com.playermarket.story.StoryManager.checkAllQuests(sp, acc);
            }
            double soldAccuracy = acc.getTotalSoldFraction(AssetType.PROJECTILE_ACCURACY);
            double extraSpread = soldAccuracy * 14.0;
            if (acc.hasDividend("ballistic_study")) {
                extraSpread = Math.max(-0.8, extraSpread - 1.5);
            }
            return (float) Math.max(0.0, inaccuracy + extraSpread);
        }
        return inaccuracy;
    }
}
