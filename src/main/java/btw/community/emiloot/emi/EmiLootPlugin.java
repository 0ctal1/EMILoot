package btw.community.emiloot.emi;

import btw.community.emiloot.EmiLootConfig;
import btw.community.emiloot.data.BlockDropScanner;
import btw.community.emiloot.data.ChestLootScanner;
import btw.community.emiloot.data.EmiLootLog;
import btw.community.emiloot.data.EmiLootProgress;
import btw.community.emiloot.data.MobDropScanner;
import btw.community.emiloot.data.OreDistributionScanner;
import emi.dev.emi.emi.api.EmiPlugin;
import emi.dev.emi.emi.api.EmiRegistry;
import emi.dev.emi.emi.api.recipe.EmiRecipeCategory;
import emi.dev.emi.emi.api.recipe.EmiRecipeSorting;
import emi.dev.emi.emi.api.stack.EmiStack;
import net.minecraft.src.Block;
import net.minecraft.src.Item;
import net.minecraft.src.ResourceLocation;

public class EmiLootPlugin implements EmiPlugin {
    public static EmiRecipeCategory CHEST_LOOT;
    public static EmiRecipeCategory MOB_DROPS;
    public static EmiRecipeCategory BLOCK_DROPS;
    public static EmiRecipeCategory ORE_DISTRIBUTION;
    @Override
    public void register(EmiRegistry registry) {
        long start = System.currentTimeMillis();
        if (EmiLootConfig.chestLoot) {
            EmiLootLog.info("Scanning chest loot...");
            EmiLootProgress.step("chest loot");
            try {
                CHEST_LOOT = new EmiRecipeCategory(new ResourceLocation("emi_loot", "chest_loot"), EmiStack.of(Block.chest));
                registry.addCategory(CHEST_LOOT);
                registry.addWorkstation(CHEST_LOOT, EmiStack.of(Block.chest));
                int n = 0;
                for (ChestLootScanner.ChestLootTable table : ChestLootScanner.scan()) {
                    registry.addRecipe(new ChestLootRecipe(table));
                    n++;
                }
                EmiLootLog.info("Registered " + n + " chest loot tables");
            }
            catch (Throwable t) { EmiLootLog.warn("Chest loot failed: " + t); }
        }

        if (EmiLootConfig.mobDrops) {
            EmiLootLog.info("Scanning mob drops...");
            EmiLootProgress.step("mob drops");
            try {
                MOB_DROPS = new EmiRecipeCategory(new ResourceLocation("emi_loot", "mob_drops"), EmiStack.of(Item.swordIron));
                registry.addCategory(MOB_DROPS);
                int n = 0;
                for (MobDropScanner.MobDropTable table : MobDropScanner.scan()) {
                    registry.addRecipe(new MobDropRecipe(table));
                    n++;
                }
                EmiLootLog.info("Registered " + n + " mob drop tables");
            }
            catch (Throwable t) { EmiLootLog.warn("Mob drops failed: " + t); }
        }

        if (EmiLootConfig.blockDrops) {
            EmiLootLog.info("Scanning block drops...");
            EmiLootProgress.step("block drops");
            try {
                EmiStack blockIcon = EmiStack.of(Item.pickaxeIron);
                BLOCK_DROPS = new EmiRecipeCategory(new ResourceLocation("emi_loot", "block_drops"), blockIcon, blockIcon, EmiRecipeSorting.compareInputThenOutput());
                registry.addCategory(BLOCK_DROPS);
                int n = 0;
                int hidden = 0;
                for (BlockDropScanner.BlockDropTable table : BlockDropScanner.scan()) {
                    BlockDropScanner.BlockDropTable visible = withoutHiddenItems(table);
                    if (visible == null) {
                        hidden++;
                        continue;
                    }
                    registry.addRecipe(new BlockDropRecipe(visible));
                    n++;
                }
                if (hidden > 0) { EmiLootLog.info("Left out " + hidden + " block drop tables for blocks/items BTW removes"); }
                EmiLootLog.info("Registered " + n + " block drop tables");
            }
            catch (Throwable t) { EmiLootLog.warn("Block drops failed: " + t); }
        }

        if (EmiLootConfig.oreDistribution) {
            EmiLootLog.info("Simulating ore generation...");
            EmiLootProgress.step("ore distribution");
            try {
                ORE_DISTRIBUTION = new EmiRecipeCategory(new ResourceLocation("emi_loot", "ore_distribution"), EmiStack.of(Block.oreDiamond));
                registry.addCategory(ORE_DISTRIBUTION);
                int n = 0;
                for (OreDistributionScanner.OreDistribution ore : OreDistributionScanner.scan()) {
                    registry.addRecipe(new OreDistributionRecipe(ore));
                    n++;
                }
                EmiLootLog.info("Registered " + n + " ore distributions");
            }
            catch (Throwable t) { EmiLootLog.warn("Ore distribution failed: " + t); }
        }

        EmiLootLog.info("Done in " + (System.currentTimeMillis() - start) + " ms");
    }

    private static boolean hidden(net.minecraft.src.ItemStack stack) { return LootSlots.isHiddenByBtw(EmiStack.of(stack)); }

    private static BlockDropScanner.BlockDropTable withoutHiddenItems(BlockDropScanner.BlockDropTable table) {
        java.util.List<Integer> keepVariants = new java.util.ArrayList<>();
        for (int v = 0; v < table.inputs().size(); v++) { if (!hidden(table.inputs().get(v))) { keepVariants.add(v); } }
        if (keepVariants.isEmpty()) { return null; }
        java.util.List<btw.community.emiloot.data.LootEntry> first = table.variants().get(keepVariants.get(0));
        java.util.List<Integer> keepPositions = new java.util.ArrayList<>();
        for (int i = 0; i < first.size(); i++) {
            boolean anyHidden = false;
            for (int v : keepVariants) { anyHidden |= hidden(table.variants().get(v).get(i).stack()); }
            if (!anyHidden) { keepPositions.add(i); }
        }
        if (keepPositions.isEmpty()) { return null; }
        java.util.List<net.minecraft.src.ItemStack> inputs = new java.util.ArrayList<>();
        java.util.List<java.util.List<btw.community.emiloot.data.LootEntry>> variants = new java.util.ArrayList<>();
        for (int v : keepVariants) {
            inputs.add(table.inputs().get(v));
            java.util.List<btw.community.emiloot.data.LootEntry> entries = new java.util.ArrayList<>();
            for (int i : keepPositions) { entries.add(table.variants().get(v).get(i)); }
            variants.add(entries);
        }
        java.util.List<net.minecraft.src.ItemStack> tools = new java.util.ArrayList<>();
        for (net.minecraft.src.ItemStack t : table.tools()) { if (!hidden(t)) { tools.add(t); }}
        return new BlockDropScanner.BlockDropTable(inputs, table.blockId(), table.meta(), table.kind(), variants, tools, table.noToolNeeded());
    }
}
