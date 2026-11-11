package com.playermarket.client.mixin;

import com.playermarket.client.hud.MarketHudOverlay;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftFreezeMixin {

    @Inject(method = "handleKeybinds", at = @At("HEAD"), cancellable = true)
    private void playermarket$blockKeybindsDuringEnding(CallbackInfo ci) {
        if (MarketHudOverlay.isEndingCinematicPlaying()) {
            ci.cancel();
        }
    }

    @Inject(method = "pauseGame", at = @At("HEAD"), cancellable = true)
    private void playermarket$blockPauseDuringEnding(boolean bl, CallbackInfo ci) {
        if (MarketHudOverlay.isEndingCinematicPlaying()) {
            ci.cancel();
        }
    }
}
