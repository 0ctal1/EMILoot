package btw.community.emiloot.data;

import net.minecraft.src.BiomeDecorator;
import net.minecraft.src.BiomeGenBase;

final class OreProbeDecorator extends BiomeDecorator {
    OreProbeDecorator(BiomeGenBase biome) {
        super(biome);
    }
    @Override protected void decorate() {
        generateOres();
    }
}
