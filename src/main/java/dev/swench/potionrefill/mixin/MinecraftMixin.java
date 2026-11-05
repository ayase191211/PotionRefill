package dev.swench.potionrefill.mixin;

import dev.swench.potionrefill.Refiller;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = {MinecraftClient.class})
public class MinecraftMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    public void PotionRefill(CallbackInfo ci) {
        Refiller.tick((MinecraftClient) (Object) this);
    }
}
