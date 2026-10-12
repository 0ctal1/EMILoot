package btw.community.emiloot.mixin;

import net.minecraft.src.ItemStack;
import net.minecraft.src.WeightedRandomChestContent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WeightedRandomChestContent.class)
public interface WeightedRandomChestContentAccessor {
    @Accessor("theItemId") ItemStack emiLoot$getStack();
    @Accessor("theMinimumChanceToGenerateItem") int emiLoot$getMinCount();
    @Accessor("theMaximumChanceToGenerateItem") int emiLoot$getMaxCount();
}
