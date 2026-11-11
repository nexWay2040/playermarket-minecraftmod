package com.playermarket.client.mixin;

import com.playermarket.client.hud.MarketHudOverlay;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputFreezeMixin extends ClientInput {

    @Inject(method = "tick", at = @At("TAIL"))
    private void playermarket$freezeMovementDuringEnding(CallbackInfo ci) {
        if (MarketHudOverlay.isEndingCinematicPlaying()) {
            this.keyPresses = Input.EMPTY;
            this.moveVector = Vec2.ZERO;
        }
    }
}
