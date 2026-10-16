package btw.community.emiloot.data;

import api.item.items.AxeItem;
import api.item.items.HoeItem;
import api.item.items.PickaxeItem;
import api.item.items.ShovelItem;
import api.item.items.ToolItem;
import btw.item.items.ChiselItem;
import net.minecraft.src.Block;
import net.minecraft.src.EnumToolMaterial;
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
            List<Item> all = candidates();
            boolean[] harvests = new boolean[all.size()];
            for (int i = 0; i < all.size(); i++) {
                Item item = all.get(i);
                try { harvests[i] = item.canHarvestBlock(new ItemStack(item), world, block, SandboxWorld.X, SandboxWorld.Y, SandboxWorld.Z); }
                catch (Throwable t) { harvests[i] = false; }
            }
            Item[] best = new Item[TYPES.length];
            for (int i = 0; i < all.size(); i++) {
                if (!harvests[i] || isHybrid(all, harvests, i)) { continue; }
                Item item = all.get(i);
                int type = typeOf(item);
                if (best[type] == null || isBetterPick(item, best[type])) { best[type] = item; }
            }
            List<ItemStack> tools = new ArrayList<>();
            for (Item item : best) { if (item != null) { tools.add(new ItemStack(item)); } }
            return new ToolInfo(false, tools);
        }
        catch (Throwable t) { return NONE; }
    }

    private static boolean isHybrid(List<Item> all, boolean[] harvests, int index) {
        Item item = all.get(index);
        int type = typeOf(item);
        int level = levelOf(item);
        for (int j = 0; j < all.size(); j++) {
            if (j != index && !harvests[j] && typeOf(all.get(j)) == type && levelOf(all.get(j)) >= level) { return true; }
        }
        return false;
    }

    private static boolean isGold(Item item) {
        return item instanceof ToolItem tool && tool.toolMaterial == EnumToolMaterial.GOLD;
    }

    private static boolean isBetterPick(Item a, Item b) {
        if (isGold(a) != isGold(b)) { return isGold(b); }
        if (levelOf(a) != levelOf(b)) { return levelOf(a) < levelOf(b); }
        if (depthOf(a) != depthOf(b)) { return depthOf(a) < depthOf(b); }
        return a.itemID < b.itemID;
    }

    private static int depthOf(Item item) {
        int depth = 0;
        for (Class<?> c = item.getClass(); c != null; c = c.getSuperclass()) { depth++; }
        return depth;
    }
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
