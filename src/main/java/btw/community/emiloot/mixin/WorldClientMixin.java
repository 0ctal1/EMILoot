package btw.community.emiloot.mixin;

import btw.community.emiloot.data.LootCapture;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityItem;
import net.minecraft.src.WorldClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldClient.class)
public abstract class WorldClientMixin {
    @Inject(method = "spawnEntityInWorld(Lnet/minecraft/src/Entity;)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void emiLoot$captureSpawn(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (LootCapture.isCapturing()) {
            if (entity instanceof EntityItem item) { LootCapture.record(item.getEntityItem()); }
            cir.setReturnValue(false);
        }
    }
}
