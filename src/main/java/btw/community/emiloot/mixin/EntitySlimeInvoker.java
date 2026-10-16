package btw.community.emiloot.mixin;

import net.minecraft.src.EntitySlime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(EntitySlime.class)
public interface EntitySlimeInvoker {
    @Invoker("setSlimeSize") void emiLoot$setSlimeSize(int size);
}
