package com.playermarket.client.mixin;

import com.playermarket.client.hud.MarketHudOverlay;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.GenericWaitingScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiFreezeMixin {

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void playermarket$blockScreensDuringEnding(Screen screen, CallbackInfo ci) {
        if (MarketHudOverlay.isEndingCinematicPlaying()
                && screen != null
                && !(screen instanceof DisconnectedScreen)
                && !(screen instanceof GenericWaitingScreen)) {
            ci.cancel();
        }
    }
}
