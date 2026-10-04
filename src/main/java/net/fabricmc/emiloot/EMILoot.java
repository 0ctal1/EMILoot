package net.fabricmc.emiloot;

import btw.item.BTWItems;
import emi.dev.emi.emi.api.EmiEntrypoint;
import emi.dev.emi.emi.api.EmiPlugin;
import emi.dev.emi.emi.api.EmiRegistry;
import emi.dev.emi.emi.api.recipe.EmiRecipeCategory;
import emi.dev.emi.emi.api.stack.EmiFistStack;
import emi.dev.emi.emi.api.stack.EmiStack;
import net.minecraft.src.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@EmiEntrypoint
public class EMILoot implements EmiPlugin {

    public static final EmiRecipeCategory BLOCK_DROPS = new EmiRecipeCategory(new ResourceLocation("emiloot", "block_drops"), EmiStack.of(Item.pickaxeIron));

    private List<EmiStack> getTools(Item[] tools, int harvestLevel) {
        List<EmiStack> stacks = new ArrayList<EmiStack>();
        if (harvestLevel < 0) { harvestLevel = 0; }
        if (harvestLevel >= tools.length) { harvestLevel = tools.length - 1; }
        for (int i = harvestLevel; i < tools.length; i++) { stacks.add(EmiStack.of(new ItemStack(tools[i], 1, 0))); }
        return stacks;
    }

    private record HarvestBlockAccess(Block block, int metadata) implements IBlockAccess {
        @Override public int getBlockId(int x, int y, int z) { return x == 0 && y == 0 && z == 0 ? block.blockID : 0; }
        @Override public TileEntity getBlockTileEntity(int x, int y, int z) { return null; }
        @Override public int getLightBrightnessForSkyBlocks(int x, int y, int z, int lightValue) { return 0; }
        @Override public float getBrightness(int x, int y, int z, int lightValue) { return 0.0F; }
        @Override public float getLightBrightness(int x, int y, int z) { return 0.0F; }
        @Override public int getBlockMetadata(int x, int y, int z) { return x == 0 && y == 0 && z == 0 ? metadata : 0; }
        @Override public Material getBlockMaterial(int x, int y, int z) { int id = getBlockId(x, y, z); return id <= 0 || Block.blocksList[id] == null ? Material.air : Block.blocksList[id].blockMaterial; }
        @Override public boolean isBlockOpaqueCube(int x, int y, int z) { return false; }
        @Override public boolean isBlockNormalCube(int x, int y, int z) { return false; }
        @Override public boolean isAirBlock(int x, int y, int z) { return getBlockId(x, y, z) == 0; }
        @Override public BiomeGenBase getBiomeGenForCoords(int x, int z) { return null; }
        @Override public int getHeight() { return 256; }
        @Override public boolean extendedLevelsInChunkCache() { return false; }
        @Override public boolean doesBlockHaveSolidTopSurface(int x, int y, int z) { return false; }
        @Override public Vec3Pool getWorldVec3Pool() { return null; }
        @Override public int isBlockProvidingPowerTo(int x, int y, int z, int direction) { return 0; }
    }

    private int getHarvestLevel(Item[] tools, Block block, int metadata) {
        World world = Minecraft.getMinecraft().theWorld;
        for (int i = 0; i < tools.length; i++) {
            Item tool = tools[i];
            if (tool != null && tool.canHarvestBlock(new ItemStack(tool, 1, 0), world, block, 0, 0, 0)) {
                return i;
            }
        }
        return 0;
    }

    @Override
    public void register(EmiRegistry reg) {
        reg.addCategory(BLOCK_DROPS);

        Random random = new Random(0);
        Item[] pickaxes = new Item[]{Item.pickaxeWood, Item.pickaxeStone, Item.pickaxeIron, Item.pickaxeDiamond, BTWItems.steelPickaxe};
        Item[] axes = new Item[]{Item.axeWood, Item.axeStone, Item.axeIron, Item.axeDiamond};
        Item[] shovels = new Item[]{Item.shovelWood, Item.shovelStone, Item.shovelIron, Item.shovelDiamond};
        Item[] hoes = new Item[]{Item.hoeWood, Item.hoeStone, Item.hoeIron, Item.hoeDiamond};

        for (Block block : Block.blocksList) {
            if (block == null) { continue; }

            List<ItemStack> variants = new ArrayList<ItemStack>();
            block.getSubBlocks(block.blockID, block.getCreativeTabToDisplayOn(), variants);

            if (block instanceof btw.block.blocks.WoodSidingAndCornerAndDecorativeBlock) {
                variants.add(new ItemStack(block.blockID, 1, 12));
                variants.add(new ItemStack(block.blockID, 1, 14));
            }

            if (variants.isEmpty()) { variants.add(new ItemStack(block.blockID, 1, 0)); }

            for (ItemStack variant : variants) {
                int metadata = variant.getItemDamage();

                random.setSeed(0);
                int dropId = block.idDropped(metadata, random, 0);
                if (dropId <= 0 || Item.itemsList[dropId] == null) { continue; }

                random.setSeed(0);
                int count = block.quantityDropped(random);
                if (count <= 0) { continue; }

                int dropMetadata = block.damageDropped(metadata);
                EmiStack blockStack = EmiStack.of(variant);
                List<EmiStack> drops = Collections.singletonList(EmiStack.of(new ItemStack(dropId, count, dropMetadata)));

                if (block.blockMaterial.isToolNotRequired()) {
                    reg.addRecipe(new EmiBlockLootRecipe(
                            new ResourceLocation("emiloot", "block_" + block.blockID + "_" + metadata + "_fist"),
                            blockStack,
                            Collections.singletonList(new EmiFistStack()),
                            drops
                    ));
                } else {
                    if (block.arechiselseffectiveon() || block.canChiselsHarvest()) {
                        reg.addRecipe(new EmiBlockLootRecipe(
                                new ResourceLocation("emiloot", "block_" + block.blockID + "_" + metadata + "_chisel"),
                                blockStack,
                                Collections.singletonList(EmiStack.of(new ItemStack(BTWItems.ironChisel, 1, 0))),
                                drops
                        ));
                    }

                    if (block.areAxesEffectiveOn()) {
                        reg.addRecipe(new EmiBlockLootRecipe(
                                new ResourceLocation("emiloot", "block_" + block.blockID + "_" + metadata + "_axe"),
                                blockStack,
                                getTools(axes, getHarvestLevel(axes, block, metadata)),
                                drops
                        ));
                    }

                    if (block.arePicksEffectiveOn()) {
                        reg.addRecipe(new EmiBlockLootRecipe(
                                new ResourceLocation("emiloot", "block_" + block.blockID + "_" + metadata + "_pickaxe"),
                                blockStack,
                                getTools(pickaxes, getHarvestLevel(pickaxes, block, metadata)),
                                drops
                        ));
                    }

                    if (block.areShovelsEffectiveOn()) {
                        reg.addRecipe(new EmiBlockLootRecipe(
                                new ResourceLocation("emiloot", "block_" + block.blockID + "_" + metadata + "_shovel"),
                                blockStack,
                                getTools(shovels, getHarvestLevel(shovels, block, metadata)),
                                drops
                        ));
                    }

                    if (block.areHoesEffectiveOn()) {
                        reg.addRecipe(new EmiBlockLootRecipe(
                                new ResourceLocation("emiloot", "block_" + block.blockID + "_" + metadata + "_hoe"),
                                blockStack,
                                getTools(hoes, getHarvestLevel(hoes, block, metadata)),
                                drops
                        ));
                    }
                }
            }
        }
    }
}