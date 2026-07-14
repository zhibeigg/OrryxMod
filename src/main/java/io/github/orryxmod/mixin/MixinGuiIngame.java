package io.github.orryxmod.mixin;

import net.minecraft.client.gui.GuiIngame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiIngame.class)
public abstract class MixinGuiIngame {

    @Shadow(remap = false)
    protected String field_73838_g;

    @Shadow(remap = false)
    protected int field_73845_h;

    @Inject(method = "renderGameOverlay", at = @At("HEAD"))
    public void renderGameOverlay(float partialTicks, CallbackInfo ci) {
        field_73838_g = "";
        field_73845_h = 0;
    }

    @Inject(method = "setOverlayMessage", at = @At("HEAD"), cancellable = true)
    public void setOverlayMessage(String message, boolean animate, CallbackInfo ci) {
        field_73838_g = "";
        field_73845_h = 0;
        ci.cancel();
    }
}