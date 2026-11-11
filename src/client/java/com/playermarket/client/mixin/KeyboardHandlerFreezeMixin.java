package com.playermarket.client.mixin;

import com.playermarket.client.hud.MarketHudOverlay;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerFreezeMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void playermarket$blockKeysDuringEnding(long window, int action, KeyEvent keyEvent, CallbackInfo ci) {
        // Block key presses & repeats during the 10-second ending cinematic (allow key release action == 0 so no stuck keys remain)
        if (action != 0 && MarketHudOverlay.isEndingCinematicPlaying()) {
            ci.cancel();
        }
    }
}
