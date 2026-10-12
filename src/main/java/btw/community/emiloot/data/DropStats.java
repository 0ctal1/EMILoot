package btw.community.emiloot.data;

import net.minecraft.src.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DropStats {
    private static final class Acc {
        final ItemStack proto;
        int hits;
        long sum;
        int min = Integer.MAX_VALUE;
        int max;
        Acc(ItemStack proto) {
            this.proto = proto;
        }
    }

    private final Map<Long, Acc> results = new LinkedHashMap<>();
    private int trials;
    public static long key(ItemStack stack) { return ((long) stack.itemID << 32) | (stack.getItemDamage() & 0xFFFFFFFFL); }
    public void addTrial(List<ItemStack> drops) {
        trials++;
        Map<Long, Integer> counts = new HashMap<>();
        Map<Long, ItemStack> protos = new HashMap<>();
        for (ItemStack stack : drops) {
            if (stack == null || stack.stackSize <= 0) { continue; }
            long k = key(stack);
            counts.merge(k, stack.stackSize, Integer::sum);
            protos.putIfAbsent(k, stack);
        }
        for (Map.Entry<Long, Integer> e : counts.entrySet()) {
            Acc acc = results.computeIfAbsent(e.getKey(), k -> {
                ItemStack p = protos.get(k).copy();
                p.stackSize = 1;
                return new Acc(p);
            });
            int c = e.getValue();
            acc.hits++;
            acc.sum += c;
            acc.min = Math.min(acc.min, c);
            acc.max = Math.max(acc.max, c);
        }
    }

    public int trials() {
        return trials;
    }
    public boolean isEmpty() {
        return results.isEmpty();
    }
    public Set<Long> keys() {
        return results.keySet();
    }
    public Map<Long, LootEntry> byKey(float probabilityScale) {
        Map<Long, LootEntry> map = new LinkedHashMap<>();
        for (LootEntry e : toEntries(probabilityScale, false)) { map.put(key(e.stack()), e); }
        return map;
    }

    public List<LootEntry> toEntries(float probabilityScale, boolean rare) {
        List<LootEntry> list = new ArrayList<>();
        if (trials == 0) { return list; }
        for (Acc acc : results.values()) {
            float chance = (float) acc.hits / trials * probabilityScale;
            float avg = (float) acc.sum / trials * probabilityScale;
            list.add(new LootEntry(acc.proto, acc.min, acc.max, chance, avg, 0, 0, rare));
        }
        list.sort(Comparator.comparingDouble((LootEntry e) -> -e.chance()));
        return list;
    }
}
