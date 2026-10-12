package btw.community.emiloot.data;

import net.minecraft.src.Block;
import net.minecraft.src.Item;
import net.minecraft.src.ItemBlock;
import net.minecraft.src.ItemStack;

public final class StackValidator {
    private StackValidator() {}

    public static boolean isValid(ItemStack stack) {
        if (stack == null || stack.itemID <= 0 || stack.itemID >= Item.itemsList.length) { return false; }
        Item item = Item.itemsList[stack.itemID];
        if (item == null) { return false; }
        try {
            int damage = stack.getItemDamage();
            item.getIconFromDamage(damage);
            stack.getIconIndex();
            if (item instanceof ItemBlock itemBlock) {
                int id = itemBlock.getBlockID();
                Block block = id > 0 && id < Block.blocksList.length ? Block.blocksList[id] : null;
                if (block == null) { return false; }
                for (int side = 0; side < 6; side++) { block.getIcon(side, damage); }
            }
            String name = stack.getDisplayName();
            return name != null;
        }
        catch (Throwable t) { return false; }
    }
}
