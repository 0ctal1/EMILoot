package btw.community.emiloot.data;

import net.minecraft.src.ItemStack;

public record LootEntry(ItemStack stack, int minCount, int maxCount, float chance, float average, int weight, int totalWeight, boolean rare, float[] lootingChance, float[] lootingAverage, String noteKey, int[] lootingMax) {
    public LootEntry(ItemStack stack, int minCount, int maxCount, float chance, float average, int weight, int totalWeight, boolean rare, float[] lootingChance, float[] lootingAverage, String noteKey) {
        this(stack, minCount, maxCount, chance, average, weight, totalWeight, rare, lootingChance, lootingAverage, noteKey, null);
    }

    public LootEntry(ItemStack stack, int minCount, int maxCount, float chance, float average, int weight, int totalWeight, boolean rare) {
        this(stack, minCount, maxCount, chance, average, weight, totalWeight, rare, null, null, null, null);
    }

    public boolean lootingMatters() {
        if (lootingChance == null || lootingAverage == null) { return false; }
        for (int i = 1; i < lootingChance.length; i++) {
            if (Math.abs(lootingChance[i] - lootingChance[0]) > 0.005F || Math.abs(lootingAverage[i] - lootingAverage[0]) > 0.02F || (lootingMax != null && lootingMax[i] != lootingMax[0])) {
                return true;
            }
        }
        return false;
    }
}
