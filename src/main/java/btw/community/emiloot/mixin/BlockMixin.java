package btw.community.emiloot.mixin;

import btw.community.emiloot.data.LootCapture;
import net.minecraft.src.Block;
import net.minecraft.src.ItemStack;
import net.minecraft.src.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
public abstract class BlockMixin {
    @Inject(method = "dropBlockAsItem_do(Lnet/minecraft/src/World;IIILnet/minecraft/src/ItemStack;)V", at = @At("HEAD"), cancellable = true)
    private void emiLoot$captureBlockDrop(World world, int x, int y, int z, ItemStack stack, CallbackInfo ci) {
        if (LootCapture.isCapturing()) {
            LootCapture.record(stack);
            ci.cancel();
        }
    }
}
