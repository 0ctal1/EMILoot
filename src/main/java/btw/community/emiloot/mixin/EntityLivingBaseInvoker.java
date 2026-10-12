package btw.community.emiloot.mixin;

import net.minecraft.src.EntityLivingBase;
import net.minecraft.src.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(EntityLivingBase.class)
public interface EntityLivingBaseInvoker {
    @Invoker("dropFewItems") void emiLoot$dropFewItems(boolean killedByPlayer, int lootingLevel);
    @Invoker("dropRareDrop") void emiLoot$dropRareDrop(int rareDropRoll);
    @Invoker("dropHead") void emiLoot$dropHead();
    @Invoker("getExperiencePoints") int emiLoot$getExperiencePoints(EntityPlayer player);
}
