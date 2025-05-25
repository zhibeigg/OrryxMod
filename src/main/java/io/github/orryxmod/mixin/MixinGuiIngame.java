package io.github.orryxmod.mixin;

import net.minecraft.client.gui.GuiIngame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiIngame.class)
public abstract class MixinGuiIngame {

    @Shadow protected String overlayMessage = "";
    @Shadow protected int overlayMessageTime;

    @Inject(method = "renderGameOverlay", at = @At("HEAD"))
    public void renderGameOverlay(float partialTicks, CallbackInfo ci) {
        overlayMessage = "";
        overlayMessageTime = 0;
    }

    @Inject(method = "setOverlayMessage", at = @At("HEAD"), cancellable = true)
    public void setOverlayMessage(String message, boolean animate, CallbackInfo ci) {
        overlayMessage = "";
        overlayMessageTime = 0;
        ci.cancel();
    }
}