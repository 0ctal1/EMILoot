package btw.community.emiloot.emi;

import btw.community.emiloot.EmiLootConfig;
import btw.community.emiloot.data.BlockDropScanner;
import btw.community.emiloot.data.BlockDropScanner.BlockDropTable;
import btw.community.emiloot.data.DropStats;
import btw.community.emiloot.data.LootEntry;
import emi.dev.emi.emi.EmiPort;
import emi.dev.emi.emi.api.recipe.EmiRecipe;
import emi.dev.emi.emi.api.recipe.EmiRecipeCategory;
import emi.dev.emi.emi.api.render.EmiTexture;
import emi.dev.emi.emi.api.stack.EmiFistStack;
import emi.dev.emi.emi.api.stack.EmiIngredient;
import emi.dev.emi.emi.api.stack.EmiStack;
import emi.dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.src.ItemStack;
import net.minecraft.src.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class BlockDropRecipe implements EmiRecipe {
    private static final int TOOL_X = 20;
    private static final int ARROW_X = 42;
    private static final int OUTPUT_X = 68;
    private static final int COLUMNS = 5;
    private static final int WIDTH = OUTPUT_X + COLUMNS * 18;
    private final BlockDropTable table;
    private final ResourceLocation id;
    private final boolean badBreak;
    private final EmiIngredient input;
    private final EmiIngredient tool;
    private final List<EmiIngredient> outputSlots = new ArrayList<>();
    private final List<EmiIngredient> lookupInputs;
    private final List<EmiIngredient> lookupCatalysts;
    private final List<EmiStack> lookupOutputs;
    public BlockDropRecipe(BlockDropTable table) {
        this.table = table;
        this.badBreak = table.kind() == BlockDropScanner.Kind.BAD_BREAK;
        this.id = new ResourceLocation("emi_loot", "/block_drops/" + table.blockId() + "/" + table.meta() + (badBreak ? "/bad_break" : ""));
        List<EmiStack> inputStacks = new ArrayList<>();
        for (ItemStack s : table.inputs()) { inputStacks.add(EmiStack.of(s)); }
        this.input = EmiIngredient.of(inputStacks);
        List<EmiStack> toolStacks = new ArrayList<>();
        for (ItemStack s : table.tools()) { toolStacks.add(EmiStack.of(s)); }
        this.tool = badBreak ? null : table.noToolNeeded() ? EmiFistStack.HAND : toolStacks.isEmpty() ? null : EmiIngredient.of(toolStacks);
        List<EmiStack> allOutputs = new ArrayList<>();
        List<List<LootEntry>> variants = table.variants();
        for (int i = 0; i < table.entries().size(); i++) {
            List<EmiStack> perVariant = new ArrayList<>();
            boolean allSame = true;
            long firstKey = DropStats.key(variants.get(0).get(i).stack());
            for (List<LootEntry> v : variants) {
                EmiStack stack = LootSlots.toStack(v.get(i));
                perVariant.add(stack);
                allOutputs.add(stack);
                allSame &= DropStats.key(v.get(i).stack()) == firstKey;
            }
            long amount = Math.max(1, table.entries().get(i).maxCount());
            outputSlots.add(allSame ? perVariant.get(0) : EmiIngredient.of(perVariant, amount));
        }

        this.lookupInputs = LootSlots.withoutHidden(new ArrayList<EmiIngredient>(inputStacks));
        this.lookupCatalysts = LootSlots.withoutHidden(new ArrayList<EmiIngredient>(tool == null ? List.of() : toolStacks));
        this.lookupOutputs = LootSlots.withoutHidden(allOutputs);
    }

    @Override public EmiRecipeCategory getCategory() {
        return EmiLootPlugin.BLOCK_DROPS;
    }
    @Override public ResourceLocation getId() {
        return id;
    }
    @Override public List<EmiIngredient> getInputs() {
        return lookupInputs;
    }
    @Override public List<EmiIngredient> getCatalysts() {
        return lookupCatalysts;
    }
    @Override public List<EmiStack> getOutputs() {
        return lookupOutputs;
    }
    @Override public boolean supportsRecipeTree() {
        return false;
    }
    @Override public int getDisplayWidth() {
        return WIDTH;
    }
    @Override public int getDisplayHeight() {
        return LootSlots.rows(outputSlots.size(), COLUMNS) * 18;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        String label = badBreak ? "emi_loot.block.bad_break" : "emi_loot.block.harvest";
        String toolKey = table.noToolNeeded() ? "emi_loot.tool.none" : tool != null ? "emi_loot.tool.min" : "emi_loot.tool.unknown";
        boolean showTool = !badBreak && tool != null;
        int inputX = showTool ? 0 : TOOL_X / 2;
        widgets.addSlot(input, inputX, 0).appendTooltip(EmiPort.translatable(label));
        if (showTool) { widgets.addSlot(tool, TOOL_X, 0).appendTooltip(EmiPort.translatable(toolKey)); }
        widgets.addTexture(EmiTexture.EMPTY_ARROW, ARROW_X, 1);
        LootSlots.addOutputGrid(widgets, this, table.entries(), outputSlots, OUTPUT_X, 0, COLUMNS, "emi_loot.per_break", EmiLootConfig.blockSamples);
    }
}