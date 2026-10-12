package btw.community.emiloot.emi;

import btw.community.emiloot.data.LootEntry;
import emi.dev.emi.emi.api.recipe.EmiRecipe;
import emi.dev.emi.emi.api.stack.EmiIngredient;
import emi.dev.emi.emi.api.stack.EmiStack;
import emi.dev.emi.emi.runtime.EmiHidden;
import emi.dev.emi.emi.api.widget.SlotWidget;
import emi.dev.emi.emi.api.widget.WidgetHolder;
import emi.dev.emi.emi.EmiPort;
import net.minecraft.src.ItemStack;
import net.minecraft.src.StatCollector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class LootSlots {
    private LootSlots() {}
    static EmiStack toStack(LootEntry entry) {
        ItemStack stack = entry.stack().copy();
        stack.stackSize = Math.max(1, entry.maxCount());
        EmiStack emi = EmiStack.of(stack).setAmount(stack.stackSize);
        if (entry.chance() < 0.995F) { emi = emi.setChance(roundChance(entry.chance())); }
        return emi;
    }

    static List<EmiStack> toStacks(List<LootEntry> entries) {
        List<EmiStack> list = new ArrayList<>(entries.size());
        for (LootEntry e : entries) { list.add(toStack(e)); }
        return list;
    }

    static boolean isHiddenByBtw(EmiIngredient ingredient) {
        try { return EmiHidden.isDisabled(ingredient); }
        catch (Throwable t) { return false; }
    }

    static <T extends EmiIngredient> List<T> withoutHidden(List<T> list) {
        List<T> out = new ArrayList<>(list.size());
        for (T t : list) { if (t != null && !isHiddenByBtw(t)) { out.add(t); }}
        return out;
    }

    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V"};
    static String fmt(float value) {
        if (value >= 10.0F) { return String.format(Locale.ROOT, "%.0f", value); }
        if (value >= 0.995F) { return String.format(Locale.ROOT, "%.2f", value).replaceAll("\\.?0+$", ""); }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    static float roundChance(float chance) {
        float p = chance * 100.0F;
        if (p >= 0.95F) { return Math.round(p) / 100.0F; }
        return Math.max(0.1F, Math.round(p * 10.0F) / 10.0F) / 100.0F;
    }

    static String pct(float chance) {
        float p = roundChance(chance) * 100.0F;
        return p >= 0.95F ? String.valueOf(Math.round(p)) : String.format(Locale.ROOT, "%.1f", p);
    }

    static void addOutputGrid(WidgetHolder widgets, EmiRecipe recipe, List<LootEntry> entries, List<? extends EmiIngredient> stacks, int x0, int y0, int columns, String perKey, int samples) {
        addOutputGrid(widgets, recipe, entries, stacks, x0, y0, columns, perKey, samples, true);
    }

    static void addOutputGrid(WidgetHolder widgets, EmiRecipe recipe, List<LootEntry> entries, List<? extends EmiIngredient> stacks, int x0, int y0, int columns, String perKey, int samples, boolean details) {
        String per = perKey == null ? null : StatCollector.translateToLocal(perKey);
        for (int i = 0; i < entries.size(); i++) {
            LootEntry e = entries.get(i);
            int x = x0 + (i % columns) * 18;
            int y = y0 + (i / columns) * 18;
            SlotWidget slot = widgets.addSlot(stacks.get(i), x, y);
            if (!details) {
                slot.recipeContext(recipe);
                continue;
            }
            if (e.minCount() == e.maxCount()) { slot.appendTooltip(EmiPort.translatable("emi_loot.count", String.valueOf(e.minCount()))); }
            else { slot.appendTooltip(EmiPort.translatable("emi_loot.count_range", String.valueOf(e.minCount()), String.valueOf(e.maxCount()))); }
            if (e.totalWeight() > 0) { slot.appendTooltip(EmiPort.translatable("emi_loot.weight", String.valueOf(e.weight()), String.valueOf(e.totalWeight()))); }
            if (per != null && (e.chance() < 0.995F || e.minCount() != e.maxCount())) { slot.appendTooltip(EmiPort.translatable("emi_loot.average", fmt(e.average()), per)); }
            if (e.noteKey() != null) { slot.appendTooltip(EmiPort.translatable(e.noteKey())); }
            if (e.lootingMatters()) {
                for (int l = 1; l < e.lootingChance().length; l++) {
                    if (e.lootingMax() != null) { slot.appendTooltip(EmiPort.translatable("emi_loot.looting_max", ROMAN[l], pct(e.lootingChance()[l]), String.valueOf(e.lootingMax()[l]), fmt(e.lootingAverage()[l]))); }
                    else { slot.appendTooltip(EmiPort.translatable("emi_loot.looting", ROMAN[l], pct(e.lootingChance()[l]), fmt(e.lootingAverage()[l]))); }
                }
            } else if (e.lootingChance() != null && !e.rare()) { slot.appendTooltip(EmiPort.translatable("emi_loot.looting_none")); }
            if (samples > 0) { slot.appendTooltip(EmiPort.translatable("emi_loot.estimated", String.valueOf(samples))); }
            slot.recipeContext(recipe);
        }
    }
    static int rows(int count, int columns) {
        return Math.max(1, (count + columns - 1) / columns);
    }
}
