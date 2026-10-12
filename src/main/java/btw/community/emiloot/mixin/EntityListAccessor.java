package btw.community.emiloot.mixin;

import net.minecraft.src.EntityList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.HashMap;
import java.util.Map;

@Mixin(EntityList.class)
public interface EntityListAccessor {
    @SuppressWarnings("rawtypes") @Accessor("IDtoClassMapping") static Map emiLoot$getIdToClassMapping() { throw new AssertionError(); }
    @SuppressWarnings("rawtypes") @Accessor("entityEggs") static HashMap emiLoot$getEntityEggs() { throw new AssertionError(); }
}
