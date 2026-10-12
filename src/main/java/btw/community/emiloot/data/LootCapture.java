package btw.community.emiloot.data;

import net.minecraft.src.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class LootCapture {
    private static final ThreadLocal<List<ItemStack>> CAPTURED = new ThreadLocal<>();
    private LootCapture() {}
    public static void begin() {
        CAPTURED.set(new ArrayList<>());
    }
    public static List<ItemStack> end() {
        List<ItemStack> list = CAPTURED.get();
        CAPTURED.remove();
        return list == null ? new ArrayList<>() : list;
    }

    public static boolean isCapturing() {
        return CAPTURED.get() != null;
    }
    public static void record(ItemStack stack) {
        List<ItemStack> list = CAPTURED.get();
        if (list != null && stack != null && stack.stackSize > 0) { list.add(stack.copy()); }
    }
}
