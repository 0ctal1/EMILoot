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
import net.minecraft.src.Gui;
import net.minecraft.src.Minecraft;
import net.minecraft.src.RenderHelper;
import net.minecraft.src.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Locale;

public class MobDropRecipe implements EmiRecipe {
    private static final int COLUMNS = 6;
    private static final int STAGE_W = 44;
    private static final int STAGE_H = 56;
    private static final int GAP = 6;
    private static final int RIGHT_X = STAGE_W + GAP;
    private static final int XP_Y = 11;
    private static final int OUTPUT_Y = 23;
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
        return RIGHT_X + COLUMNS * 18;
    }
    @Override public int getDisplayHeight() {
        return Math.max(STAGE_H, OUTPUT_Y + LootSlots.rows(outputs.size(), COLUMNS) * 18);
    }

    private String xpText() {
        if (table.xpMax() < 0) { return null; }
        if (table.xpMax() == 0) { return EmiPort.translatable("emi_loot.xp_none").getString(); }
        if (table.xpMin() == table.xpMax()) { return EmiPort.translatable("emi_loot.xp", String.valueOf(table.xpMax())).getString(); }
        return EmiPort.translatable("emi_loot.xp_range", String.valueOf(table.xpMin()), String.valueOf(table.xpMax())).getString();
    }

    private static void addStage(WidgetHolder widgets, int w, int h) {
        widgets.addDrawable(0, 0, w, h, (draw, mouseX, mouseY, delta) -> {
            Gui.drawRect(0, 0, w - 1, 1, 0xFF373737);
            Gui.drawRect(0, 0, 1, h - 1, 0xFF373737);
            Gui.drawRect(1, h - 1, w, h, 0xFFFFFFFF);
            Gui.drawRect(w - 1, 1, w, h, 0xFFFFFFFF);
            Gui.drawRect(1, 1, w - 1, h - 1, 0xFF8B8B8B);
            Gui.drawRect(w - 1, 0, w, 1, 0xFF8B8B8B);
            Gui.drawRect(0, h - 1, 1, h, 0xFF8B8B8B);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        });
    }

    private static void addXpText(WidgetHolder widgets, int x, int y, String text) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        widgets.addDrawable(x - 1, y - 1, font.getStringWidth(text) + 2, 10, (draw, mouseX, mouseY, delta) -> {
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
        int stageH = getDisplayHeight();
        addStage(widgets, STAGE_W, stageH);
        if (EntityWidget.canRender(table.entity())) { widgets.add(new EntityWidget(table.entity(), table.entityName(), 2, 3, STAGE_W - 4, stageH - 6)); }
        else if (egg != null) { widgets.addSlot(egg, (STAGE_W - 18) / 2, (stageH - 18) / 2).drawBack(false); }
        widgets.addText(EmiPort.ordered(EmiPort.translatable("entity." + table.entityName() + ".name")), RIGHT_X, 1, 0xFFFFFF, true);
        String xp = xpText();
        if (xp != null) { addXpText(widgets, RIGHT_X, XP_Y, xp); }
        LootSlots.addOutputGrid(widgets, this, table.entries(), outputs, RIGHT_X, OUTPUT_Y, COLUMNS, "emi_loot.per_kill", table.samples());
    }
}
