package btw.community.emiloot.data;

import btw.community.emiloot.mixin.WeightedRandomChestContentAccessor;
import btw.community.emiloot.mixin.WeightedRandomItemAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import net.minecraft.src.Block;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import net.minecraft.src.StatCollector;
import net.minecraft.src.WeightedRandomChestContent;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

public final class ChestLootScanner {
    public record ChestLootTable(String key, String name, ItemStack icon, int[] rolls, List<LootEntry> entries) {
        public int minRolls() {
            int m = Integer.MAX_VALUE;
            for (int r : rolls) { m = Math.min(m, r); }
            return m;
        }

        public int maxRolls() {
            int m = 0;
            for (int r : rolls) { m = Math.max(m, r); }
            return m;
        }

        public float averageRolls() {
            float sum = 0;
            for (int r : rolls) { sum += r; }
            return sum / rolls.length;
        }

        public float chanceInChest(float perRoll) {
            double miss = 0;
            for (int r : rolls) { miss += Math.pow(1.0 - perRoll, r); }
            return (float) (1.0 - miss / rolls.length);
        }
    }

    private record Extra(int item, int min, int max, int weight) {}
    private record Filter(int item, boolean remove, Integer removeAboveY, Integer removeBelowY, Integer setStack) {}
    private record Source(String key, String owner, String field, int icon, int[] rolls, List<Extra> extras, List<Filter> filters) {}
    private record RawEntry(ItemStack stack, int weight, int min, int max) {}

    private static final int CHEST = 1035;
    private static final int HAMPER = 1054;
    private static final int MINECART_CHEST = 342;
    private static final int BOOK = 403;
    private static final int IRON = 265;
    private static final int GUNPOWDER = 289;
    private static final int REDSTONE = 331;
    private static final int DIAMOND = 264;
    private static final int IRON_PICKAXE = 257;
    private static final int PUMPKIN_SEEDS = 361;
    private static final List<Source> SOURCES = List.of(
            new Source("dungeon", "net.minecraft.class_1238", "field_6231", CHEST, new int[]{8},
                    List.of(new Extra(BOOK, 1, 1, 30)),
                    List.of(new Filter(IRON, false, 36, null, 1), new Filter(GUNPOWDER, false, 36, null, 1), new Filter(REDSTONE, false, 24, null, null))),
            new Source("mineshaft", "net.minecraft.class_1253", "field_4910", MINECART_CHEST, range(3, 6),
                    List.of(new Extra(BOOK, 1, 1, 1)),
                    List.of(new Filter(IRON, false, 35, null, 1), new Filter(DIAMOND, false, 23, null, 1), new Filter(REDSTONE, false, 23, null, null),
                            new Filter(IRON_PICKAXE, true, null, null, null), new Filter(PUMPKIN_SEEDS, true, null, null, null))),
            new Source("nether_fortress", "net.minecraft.class_1274", "field_6234", CHEST, range(2, 5), List.of(), List.of()),
            new Source("desert_temple", "net.minecraft.class_5", "lootListArray", HAMPER, range(2, 6), List.of(new Extra(BOOK, 1, 1, 1)), List.of()),
            new Source("desert_temple_looted", "net.minecraft.class_5", "lootedLootListArray", HAMPER, new int[]{1, 1, 2, 2, 3}, List.of(new Extra(BOOK, 1, 1, 1)), List.of()),
            new Source("jungle_temple", "net.minecraft.class_6", "lootListArray", HAMPER, range(2, 6), List.of(new Extra(BOOK, 1, 1, 1)), List.of()),
            new Source("jungle_temple_looted", "net.minecraft.class_6", "lootedLootListArray", HAMPER, new int[]{1, 1, 2, 2, 3}, List.of(new Extra(BOOK, 1, 1, 1)), List.of()),
            new Source("stronghold_corridor", "net.minecraft.class_15", "field_27", CHEST, range(2, 3), List.of(new Extra(BOOK, 1, 1, 1)), List.of()),
            new Source("stronghold_library", "net.minecraft.class_19", "field_38", CHEST, range(1, 4), List.of(new Extra(BOOK, 1, 5, 2)), List.of()),
            new Source("stronghold_crossing", "net.minecraft.class_24", "field_48", CHEST, range(1, 4), List.of(new Extra(BOOK, 1, 1, 1)), List.of()),
            new Source("village_blacksmith", "net.minecraft.class_49", "field_89", HAMPER, range(3, 8), List.of(), List.of()));

    private static int[] range(int min, int max) {
        int[] out = new int[max - min + 1];
        for (int i = 0; i < out.length; i++) { out[i] = min + i; }
        return out;
    }

    private static ItemStack stackOf(int id) {
        return id > 0 && id < Item.itemsList.length && Item.itemsList[id] != null ? new ItemStack(id, 1, 0) : null;
    }
    private record Band(int low, int high) {}
    private static final String CHEST_CONTENT_DESC = "[Lnet/minecraft/class_847;";
    private ChestLootScanner() {}

    public static List<ChestLootTable> scan() {
        List<ChestLootTable> tables = new ArrayList<>();
        MappingResolver resolver = FabricLoader.getInstance().getMappingResolver();
        for (Source source : SOURCES) {
            try {
                WeightedRandomChestContent[] contents = read(resolver, source);
                if (contents == null || contents.length == 0) { continue; }
                List<RawEntry> raw = withExtras(source, toRaw(contents));
                ItemStack icon = stackOf(source.icon());
                if (icon == null) { icon = new ItemStack(Block.chest); }
                String name = displayName(source);
                List<Band> bands = bandsFor(source.filters());
                for (Band band : bands) {
                    String key = bands.size() == 1 ? source.key() : source.key() + "_y" + band.low();
                    String label = bands.size() == 1 ? name : bandLabel(name, band);
                    List<LootEntry> entries = toEntries(raw, source.filters(), band);
                    if (!entries.isEmpty()) { tables.add(new ChestLootTable(key, label, icon, source.rolls(), entries)); }
                }
            }
            catch (Throwable t) { EmiLootLog.warn("Could not read chest loot '" + source.key() + "': " + t); }
        }
        return tables;
    }

    private static String displayName(Source source) {
        String langKey = "emi_loot.chest." + source.key();
        return StatCollector.func_94522_b(langKey) ? StatCollector.translateToLocal(langKey) : source.key();
    }

    private static String bandLabel(String name, Band band) {
        if (band.high() == Integer.MAX_VALUE) { return StatCollector.translateToLocalFormatted("emi_loot.chest.band_above", name, band.low()); }
        return StatCollector.translateToLocalFormatted("emi_loot.chest.band_range", name, Math.max(0, band.low()), band.high());
    }

    private static List<Band> bandsFor(List<Filter> filters) {
        TreeSet<Integer> cuts = new TreeSet<>();
        for (Filter f : filters) {
            if (f.removeAboveY() != null) { cuts.add(f.removeAboveY()); }
            if (f.removeBelowY() != null) { cuts.add(f.removeBelowY() - 1); }
        }
        List<Band> bands = new ArrayList<>();
        int low = 0;
        for (int cut : cuts) {
            if (cut < low) { continue; }
            bands.add(new Band(low, cut));
            low = cut + 1;
        }
        bands.add(new Band(low, Integer.MAX_VALUE));
        return bands;
    }

    private static Filter filterFor(List<Filter> filters, ItemStack stack) {
        for (Filter f : filters) { if (f.item() == stack.itemID) { return f; } }
        return null;
    }

    private static List<RawEntry> withExtras(Source source, List<RawEntry> raw) {
        List<RawEntry> out = new ArrayList<>(raw);
        for (Extra extra : source.extras()) {
            ItemStack stack = stackOf(extra.item());
            if (stack == null) {
                EmiLootLog.warn("Chest loot '" + source.key() + "': unknown extra item '" + extra.item() + "'");
                continue;
            }
            boolean present = false;
            for (RawEntry e : raw) { present |= e.stack().itemID == stack.itemID && e.stack().getItemDamage() == stack.getItemDamage(); }
            if (!present) { out.add(new RawEntry(stack, extra.weight(), Math.max(1, extra.min()), Math.max(extra.min(), extra.max()))); }
        }
        return out;
    }

    private static WeightedRandomChestContent[] read(MappingResolver resolver, Source source) throws Exception {
        if (source.owner() == null) { throw new IllegalArgumentException("no class given"); }
        String className = resolver.mapClassName("intermediary", source.owner());
        Class<?> cls = Class.forName(className, true, ChestLootScanner.class.getClassLoader());
        Field field = null;
        if (source.field() != null) {
            String fieldName = source.field().startsWith("field_") ? resolver.mapFieldName("intermediary", source.owner(), source.field(), CHEST_CONTENT_DESC) : source.field();
            try { field = cls.getDeclaredField(fieldName); }
            catch (NoSuchFieldException ignored) {}
        }
        if (field == null) {
            for (Field f : cls.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) || f.getType() != WeightedRandomChestContent[].class) { continue; }
                if (field != null) { throw new NoSuchFieldException("several loot arrays in " + className + "; name the field"); }
                field = f;
            }
        }
        if (field == null) { throw new NoSuchFieldException("no loot array in " + className); }
        field.setAccessible(true);
        return (WeightedRandomChestContent[]) field.get(null);
    }

    private static List<RawEntry> toRaw(WeightedRandomChestContent[] contents) {
        List<RawEntry> raw = new ArrayList<>(contents.length);
        for (WeightedRandomChestContent c : contents) {
            if (c == null) { continue; }
            WeightedRandomChestContentAccessor acc = (WeightedRandomChestContentAccessor) c;
            ItemStack stack = acc.emiLoot$getStack();
            if (stack == null) { continue; }
            int weight = ((WeightedRandomItemAccessor) c).emiLoot$getWeight();
            int min = Math.max(1, acc.emiLoot$getMinCount());
            int max = Math.max(min, acc.emiLoot$getMaxCount());
            ItemStack copy = stack.copy();
            copy.stackSize = 1;
            raw.add(new RawEntry(copy, weight, min, max));
        }
        return raw;
    }

    private static boolean removedIn(Filter f, Band band) {
        if (f == null) { return false; }
        if (f.remove()) { return true; }
        if (f.removeAboveY() != null && band.low() > f.removeAboveY()) { return true; }
        return f.removeBelowY() != null && band.high() < f.removeBelowY();
    }

    private static List<LootEntry> toEntries(List<RawEntry> raw, List<Filter> filters, Band band) {
        int total = 0;
        for (RawEntry r : raw) { total += r.weight(); }
        List<LootEntry> entries = new ArrayList<>();
        if (total <= 0) { return entries; }
        for (RawEntry r : raw) {
            Filter f = filterFor(filters, r.stack());
            if (removedIn(f, band)) { continue; }
            int min = r.min();
            int max = r.max();
            if (f != null && f.setStack() != null) { min = max = Math.max(1, f.setStack()); }
            float chance = (float) r.weight() / total;
            float avg = chance * (min + max) / 2.0F;
            entries.add(new LootEntry(r.stack(), min, max, chance, avg, r.weight(), total, false));
        }
        entries.sort((a, b) -> Integer.compare(b.weight(), a.weight()));
        return entries;
    }
}