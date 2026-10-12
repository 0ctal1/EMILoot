package btw.community.emiloot.mixin;

import net.minecraft.src.WeightedRandomItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WeightedRandomItem.class)
public interface WeightedRandomItemAccessor {
    @Accessor("itemWeight") int emiLoot$getWeight();
}
