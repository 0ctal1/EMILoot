package btw.community.emiloot.emi;

import btw.community.emiloot.data.OreDistributionScanner.OreDistribution;
import emi.dev.emi.emi.EmiPort;
import emi.dev.emi.emi.api.recipe.EmiRecipe;
import emi.dev.emi.emi.api.recipe.EmiRecipeCategory;
import emi.dev.emi.emi.api.stack.EmiIngredient;
import emi.dev.emi.emi.api.stack.EmiStack;
import emi.dev.emi.emi.api.widget.SlotWidget;
import emi.dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.src.ItemStack;
import net.minecraft.src.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class OreDistributionRecipe implements EmiRecipe {
    private static final int WIDTH = 160;
    private static final int GRAPH_Y = 22;
    private static final int GRAPH_X = 22;
    private final OreDistribution data;
    private final ResourceLocation id;
    private final List<EmiStack> variants = new ArrayList<>();
    private final List<EmiStack> lookupOutputs;
    public OreDistributionRecipe(OreDistribution data) {
        this.data = data;
        this.id = new ResourceLocation("emi_loot", "/ore_distribution/" + data.source() + "/" + data.groupId());
        for (ItemStack s : data.variants()) { variants.add(EmiStack.of(s)); }
        this.lookupOutputs = LootSlots.withoutHidden(variants);
    }

    @Override public EmiRecipeCategory getCategory() {
        return EmiLootPlugin.ORE_DISTRIBUTION;
    }
    @Override public ResourceLocation getId() {
        return id;
    }
    @Override public List<EmiIngredient> getInputs() {
        return List.of();
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
        return GRAPH_Y + Math.max(OreGraphWidget.HEIGHT, variants.size() * 18);
    }
    @Override
    public void addWidgets(WidgetHolder widgets) {
        boolean multi = variants.size() > 1;
        float total = data.total();
        widgets.add(new ScrollingTextWidget(data.fullLabel(), 0, 1, WIDTH, 0xFFFFFF));
        widgets.addText(EmiPort.ordered(EmiPort.translatable("emi_loot.ore.range", String.valueOf(data.minY()), String.valueOf(data.maxY()), LootSlots.fmt(total))), 0, 11, 0xFFFFFF, true);
        for (int i = 0; i < variants.size(); i++) {
            float[] perY = data.perY().get(i);
            float sum = 0;
            int min = -1;
            int max = -1;
            for (int y = 0; y < perY.length; y++) {
                if (perY[y] > 0) {
                    sum += perY[y];
                    if (min < 0) { min = y; }
                    max = y;
                }
            }
            SlotWidget slot = widgets.addSlot(variants.get(i), 0, GRAPH_Y + i * 18);
            slot.appendTooltip(EmiPort.translatable("emi_loot.ore.variant_range", String.valueOf(Math.max(min, 0)), String.valueOf(Math.max(max, 0))));
            if (multi && total > 0) { slot.appendTooltip(EmiPort.translatable("emi_loot.ore.variant_share", LootSlots.pct(sum / total))); }
            slot.recipeContext(this);
        }

        widgets.add(new OreGraphWidget(data, GRAPH_X, GRAPH_Y));
    }
}
