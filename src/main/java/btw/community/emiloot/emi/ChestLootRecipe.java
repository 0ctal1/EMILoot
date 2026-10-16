package btw.community.emiloot.emi;

import btw.community.emiloot.data.ChestLootScanner.ChestLootTable;
import emi.dev.emi.emi.EmiPort;
import emi.dev.emi.emi.api.recipe.EmiRecipe;
import emi.dev.emi.emi.api.recipe.EmiRecipeCategory;
import emi.dev.emi.emi.api.stack.EmiIngredient;
import emi.dev.emi.emi.api.stack.EmiStack;
import emi.dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.src.ResourceLocation;

import java.util.List;

public class ChestLootRecipe implements EmiRecipe {
    private static final int COLUMNS = 9;
    private static final int OUTPUT_Y = 22;
    private final ChestLootTable table;
    private final ResourceLocation id;
    private final EmiStack container;
    private final List<EmiStack> outputs;
    private final List<EmiStack> lookupOutputs;
    private final List<EmiIngredient> lookupCatalysts;
    public ChestLootRecipe(ChestLootTable table) {
        this.table = table;
        this.id = new ResourceLocation("emi_loot", "/chest_loot/" + table.key());
        this.container = EmiStack.of(table.icon());
        this.outputs = LootSlots.toStacks(table.entries());
        this.lookupOutputs = LootSlots.withoutHidden(outputs);
        this.lookupCatalysts = LootSlots.withoutHidden(List.<EmiIngredient>of(container));
    }

    @Override public EmiRecipeCategory getCategory() {
        return EmiLootPlugin.CHEST_LOOT;
    }
    @Override public ResourceLocation getId() {
        return id;
    }
    @Override public List<EmiIngredient> getInputs() {
        return List.of();
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
        return COLUMNS * 18;
    }
    @Override public int getDisplayHeight() {
        return OUTPUT_Y + LootSlots.rows(outputs.size(), COLUMNS) * 18;
    }
    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addText(EmiPort.ordered(EmiPort.literal(table.name())), 20, 1, 0xFFFFFF, true);
        widgets.addText(EmiPort.ordered(table.minRolls() == table.maxRolls() ? EmiPort.translatable("emi_loot.rolls", String.valueOf(table.minRolls())) : EmiPort.translatable("emi_loot.rolls_range", String.valueOf(table.minRolls()), String.valueOf(table.maxRolls()))), 20, 11, 0xFFFFFF, true);
        widgets.addSlot(container, 0, 0).drawBack(true);
        LootSlots.addOutputGrid(widgets, this, table.entries(), outputs, 0, OUTPUT_Y, COLUMNS, "emi_loot.per_roll", 0, true, (slot, e) -> slot.appendTooltip(EmiPort.translatable("emi_loot.per_chest", LootSlots.pct(table.chanceInChest(e.chance())), LootSlots.fmt(e.average() * table.averageRolls()))));
    }
}