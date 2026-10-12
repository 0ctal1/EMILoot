package btw.community.emiloot.mixin;

import net.minecraft.src.EntityLivingBase;
import net.minecraft.src.ModelBase;
import net.minecraft.src.RendererLivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RendererLivingEntity.class)
public interface RendererLivingEntityAccessor {
    @Accessor("mainModel") ModelBase emiLoot$getMainModel();
    @Invoker("preRenderCallback") void emiLoot$preRenderCallback(EntityLivingBase entity, float partialTicks);
}
