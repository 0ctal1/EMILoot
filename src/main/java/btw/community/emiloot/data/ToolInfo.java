package btw.community.emiloot.data;

import api.item.items.AxeItem;
import api.item.items.HoeItem;
import api.item.items.PickaxeItem;
import api.item.items.ShovelItem;
import api.item.items.ToolItem;
import btw.item.items.ChiselItem;
import net.minecraft.src.Block;
import net.minecraft.src.Item;
import net.minecraft.src.ItemShears;
import net.minecraft.src.ItemStack;

import java.util.ArrayList;
import java.util.List;

public record ToolInfo(boolean noToolNeeded, List<ItemStack> tools) {
    public static final ToolInfo NONE = new ToolInfo(false, List.of());
    private static final Class<?>[] TYPES = { PickaxeItem.class, AxeItem.class, ShovelItem.class, ChiselItem.class, HoeItem.class, ItemShears.class };
    private static List<Item> candidates;
    public String signature() {
        StringBuilder sb = new StringBuilder(noToolNeeded ? "hand" : "tools");
        for (ItemStack s : tools) { sb.append(',').append(s.itemID); }
        return sb.toString();
    }

    public static void reset() {
        candidates = null;
    }
    public static ToolInfo of(SandboxWorld world, Block block, int meta) {
        try {
            if (block.blockMaterial != null && block.blockMaterial.isToolNotRequired()) { return new ToolInfo(true, List.of()); }
            if (world == null) { return NONE; }
            world.setBlockUnderTest(block.blockID, meta, 0L);
            Item[] best = new Item[TYPES.length];
            int[] bestLevel = new int[TYPES.length];
            for (Item item : candidates()) {
                int type = typeOf(item);
                if (type < 0) { continue; }
                boolean ok;
                try { ok = item.canHarvestBlock(new ItemStack(item), world, block, SandboxWorld.X, SandboxWorld.Y, SandboxWorld.Z); }
                catch (Throwable t) { ok = false; }
                if (!ok) { continue; }
                int level = levelOf(item);
                if (best[type] == null || level < bestLevel[type] || (level == bestLevel[type] && isPlainer(item, best[type]))) {
                    best[type] = item;
                    bestLevel[type] = level;
                }
            }
            List<ItemStack> tools = new ArrayList<>();
            for (Item item : best) { if (item != null) { tools.add(new ItemStack(item)); } }
            return new ToolInfo(false, tools);
        }
        catch (Throwable t) { return NONE; }
    }

    private static boolean isPlainer(Item a, Item b) { return a.getClass().getSuperclass() == ToolItem.class && b.getClass().getSuperclass() != ToolItem.class || a.itemID < b.itemID && a.getClass() == b.getClass(); }
    private static int typeOf(Item item) {
        for (int i = 0; i < TYPES.length; i++) { if (TYPES[i].isInstance(item)) { return i; }}
        return -1;
    }

    private static int levelOf(Item item) {
        if (item instanceof ToolItem tool && tool.toolMaterial != null) { return tool.toolMaterial.getHarvestLevel(); }
        return 0;
    }

    private static List<Item> candidates() {
        if (candidates == null) {
            List<Item> list = new ArrayList<>();
            for (Item item : Item.itemsList) {if (item != null && typeOf(item) >= 0 && StackValidator.isValid(new ItemStack(item)) && !hiddenByBtw(item)) { list.add(item); }}
            candidates = list;
        }
        return candidates;
    }

    private static boolean hiddenByBtw(Item item) {
        try { return emi.dev.emi.emi.runtime.EmiHidden.isDisabled(emi.dev.emi.emi.api.stack.EmiStack.of(new ItemStack(item))); }
        catch (Throwable t) { return false; }
    }
}
