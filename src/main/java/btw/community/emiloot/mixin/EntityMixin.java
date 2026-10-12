package btw.community.emiloot.mixin;

import btw.community.emiloot.data.LootCapture;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityItem;
import net.minecraft.src.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "entityDropItem(Lnet/minecraft/src/ItemStack;F)Lnet/minecraft/src/EntityItem;", at = @At("HEAD"), cancellable = true)
    private void emiLoot$captureDrop(ItemStack stack, float offsetY, CallbackInfoReturnable<EntityItem> cir) {
        if (LootCapture.isCapturing()) {
            LootCapture.record(stack);
            cir.setReturnValue(null);
        }
    }
}
