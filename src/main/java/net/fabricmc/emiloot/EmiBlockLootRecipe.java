package net.fabricmc.emiloot;

import emi.dev.emi.emi.api.recipe.EmiRecipe;
import emi.dev.emi.emi.api.recipe.EmiRecipeCategory;
import emi.dev.emi.emi.api.stack.EmiIngredient;
import emi.dev.emi.emi.api.stack.EmiStack;
import emi.dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.src.ResourceLocation;

import java.util.Collections;
import java.util.List;

public class EmiBlockLootRecipe implements EmiRecipe {
    private final ResourceLocation id;
    private final EmiStack block;
    private final List<EmiStack> tools;
    private final List<EmiStack> drops;

    public EmiBlockLootRecipe(ResourceLocation id, EmiStack block, List<EmiStack> tools, List<EmiStack> drops) {
        this.id = id;
        this.block = block;
        this.tools = tools;
        this.drops = drops;
    }

    @Override public EmiRecipeCategory getCategory() {
        return EMILoot.BLOCK_DROPS;
    }
    @Override public ResourceLocation getId() {
        return id;
    }
    @Override public List<EmiIngredient> getInputs() {
        return Collections.singletonList(block);
    }
    @Override public List<EmiStack> getOutputs() {return drops;}
    @Override public int getDisplayWidth() {
        return 67;
    }
    @Override public int getDisplayHeight() {
        return 18;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addSlot(block, 0, 0).recipeContext(this);
        if (!tools.isEmpty()) {widgets.addSlot(tools.get(0), 25, 0).recipeContext(this);}
        for (int i = 0; i < drops.size(); i++) {widgets.addSlot(drops.get(i), 49 + i * 18, 0).recipeContext(this);}
    }
}