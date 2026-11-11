package com.playermarket.client.mixin;

import com.playermarket.client.hud.MarketHudOverlay;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerFreezeMixin {

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void playermarket$freezeCameraDuringEnding(double dt, CallbackInfo ci) {
        if (MarketHudOverlay.isEndingCinematicPlaying()) {
            ci.cancel();
        }
    }

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void playermarket$blockMouseClickDuringEnding(long window, MouseButtonInfo buttonInfo, int action, CallbackInfo ci) {
        if (action != 0 && MarketHudOverlay.isEndingCinematicPlaying()) {
            ci.cancel();
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void playermarket$blockMouseScrollDuringEnding(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (MarketHudOverlay.isEndingCinematicPlaying()) {
            ci.cancel();
        }
    }
}
