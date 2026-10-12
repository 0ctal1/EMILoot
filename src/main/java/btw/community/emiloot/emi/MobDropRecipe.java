package btw.community.emiloot.emi;

import btw.community.emiloot.data.MobDropScanner.MobDropTable;
import emi.dev.emi.emi.EmiPort;
import emi.dev.emi.emi.api.recipe.EmiRecipe;
import emi.dev.emi.emi.api.recipe.EmiRecipeCategory;
import emi.dev.emi.emi.api.stack.EmiIngredient;
import emi.dev.emi.emi.api.stack.EmiStack;
import emi.dev.emi.emi.api.widget.WidgetHolder;
import emi.dev.emi.emi.runtime.EmiDrawContext;
import net.minecraft.src.FontRenderer;
import net.minecraft.src.Minecraft;
import net.minecraft.src.RenderHelper;
import net.minecraft.src.ResourceLocation;

import java.util.List;
import java.util.Locale;

public class MobDropRecipe implements EmiRecipe {
    private static final int COLUMNS = 6;
    private static final int OUTPUT_X = 44;
    private static final int OUTPUT_Y = 22;
    private final MobDropTable table;
    private final ResourceLocation id;
    private final EmiStack egg;
    private final List<EmiStack> outputs;
    private final List<EmiStack> lookupOutputs;
    private final List<EmiIngredient> lookupCatalysts;

    public MobDropRecipe(MobDropTable table) {
        this.table = table;
        this.id = new ResourceLocation("emi_loot", "/mob_drops/" + table.entityName().toLowerCase(Locale.ROOT));
        this.egg = table.spawnEgg() == null ? null : EmiStack.of(table.spawnEgg());
        this.outputs = LootSlots.toStacks(table.entries());
        this.lookupOutputs = LootSlots.withoutHidden(outputs);
        this.lookupCatalysts = egg == null ? List.of() : LootSlots.withoutHidden(List.<EmiIngredient>of(egg));
    }

    @Override public EmiRecipeCategory getCategory() {
        return EmiLootPlugin.MOB_DROPS;
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
        return OUTPUT_X + COLUMNS * 18;
    }
    @Override public int getDisplayHeight() {
        return Math.max(66, OUTPUT_Y + LootSlots.rows(outputs.size(), COLUMNS) * 18);
    }
    private static void addXpText(WidgetHolder widgets, String text) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        int width = font.getStringWidth(text) + 2;
        widgets.addDrawable(MobDropRecipe.OUTPUT_X - 1, 11 - 1, width, 10, (draw, mouseX, mouseY, delta) -> {
            RenderHelper.disableStandardItemLighting();
            FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
            fr.drawString(text, 2, 1, 0);
            fr.drawString(text, 0, 1, 0);
            fr.drawString(text, 1, 2, 0);
            fr.drawString(text, 1, 0, 0);
            fr.drawString(text, 1, 1, 0x80FF20);
            EmiDrawContext.wrap(draw).resetColor();
        });
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addText(EmiPort.ordered(EmiPort.translatable("entity." + table.entityName() + ".name")), 0, 0, 0xFFFFFF, true);
        if (table.xpMax() >= 0) {
            String xp = table.xpMax() == 0 ? EmiPort.translatable("emi_loot.xp_none").getString() : table.xpMin() == table.xpMax() ? EmiPort.translatable("emi_loot.xp", String.valueOf(table.xpMax())).getString() : EmiPort.translatable("emi_loot.xp_range", String.valueOf(table.xpMin()), String.valueOf(table.xpMax())).getString();
            addXpText(widgets, xp);
        }
        int boxTop = 10;
        int boxHeight = getDisplayHeight() - boxTop;
        if (EntityWidget.canRender(table.entity())) { widgets.add(new EntityWidget(table.entity(), table.entityName(), 0, boxTop, OUTPUT_X - 4, boxHeight)); }
        else if (egg != null) { widgets.addSlot(egg, (OUTPUT_X - 4 - 18) / 2, boxTop + (boxHeight - 18) / 2); }
        LootSlots.addOutputGrid(widgets, this, table.entries(), outputs, OUTPUT_X, OUTPUT_Y, COLUMNS, "emi_loot.per_kill", table.samples());
    }
}
