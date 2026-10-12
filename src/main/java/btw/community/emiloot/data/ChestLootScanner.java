package btw.community.emiloot.data;

import btw.community.emiloot.mixin.WeightedRandomChestContentAccessor;
import btw.community.emiloot.mixin.WeightedRandomItemAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import btw.block.BTWBlocks;
import net.minecraft.src.ItemStack;
import net.minecraft.src.WeightedRandomChestContent;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public final class ChestLootScanner {
    public record ChestLootTable(String key, ItemStack icon, int minRolls, int maxRolls, List<LootEntry> entries) {
    }

    private record Source(String key, String owner, String field, boolean hamper, int minRolls, int maxRolls) {
    }

    private static final String CHEST_CONTENT_DESC = "[Lnet/minecraft/class_847;";
    private static final Source[] SOURCES = {
            new Source("dungeon", "net.minecraft.class_1238", "field_6231", false, 8, 8),
            new Source("mineshaft", "net.minecraft.class_1253", "field_4910", false, 3, 6),
            new Source("nether_fortress", "net.minecraft.class_1274", "field_6234", false, 2, 5),
            new Source("desert_temple", "net.minecraft.class_5", "lootListArray", true, 2, 6),
            new Source("desert_temple_looted", "net.minecraft.class_5", "lootedLootListArray", true, 1, 3),
            new Source("jungle_temple", "net.minecraft.class_6", "lootListArray", true, 2, 6),
            new Source("jungle_temple_looted", "net.minecraft.class_6", "lootedLootListArray", true, 1, 3),
            new Source("stronghold_corridor", "net.minecraft.class_15", "field_27", false, 2, 3),
            new Source("stronghold_library", "net.minecraft.class_19", "field_38", false, 1, 4),
            new Source("stronghold_crossing", "net.minecraft.class_24", "field_48", false, 1, 4),
            new Source("village_blacksmith", "net.minecraft.class_49", "field_89", true, 3, 8),
    };

    private ChestLootScanner() {
    }

    public static List<ChestLootTable> scan() {
        List<ChestLootTable> tables = new ArrayList<>();
        MappingResolver resolver = FabricLoader.getInstance().getMappingResolver();
        for (Source source : SOURCES) {
            try {
                WeightedRandomChestContent[] contents = read(resolver, source);
                if (contents == null || contents.length == 0) {
                    continue;
                }
                List<LootEntry> entries = toEntries(contents);
                if (!entries.isEmpty()) {
                    ItemStack icon = new ItemStack(source.hamper() ? BTWBlocks.hamper : BTWBlocks.chest);
                    tables.add(new ChestLootTable(source.key(), icon, source.minRolls(), source.maxRolls(), entries));
                }
            } catch (Throwable t) {
                EmiLootLog.warn("Could not read chest loot '" + source.key() + "': " + t);
            }
        }
        return tables;
    }

    private static WeightedRandomChestContent[] read(MappingResolver resolver, Source source) throws Exception {
        String className = resolver.mapClassName("intermediary", source.owner());
        Class<?> cls = Class.forName(className, true, ChestLootScanner.class.getClassLoader());
        Field field;
        try {
            String fieldName = source.field().startsWith("field_")
                    ? resolver.mapFieldName("intermediary", source.owner(), source.field(), CHEST_CONTENT_DESC)
                    : source.field();
            field = cls.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            field = null;
            for (Field f : cls.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) && f.getType() == WeightedRandomChestContent[].class) {
                    if (field != null) {
                        throw e;
                    }
                    field = f;
                }
            }
            if (field == null) {
                throw e;
            }
        }
        field.setAccessible(true);
        return (WeightedRandomChestContent[]) field.get(null);
    }

    private static List<LootEntry> toEntries(WeightedRandomChestContent[] contents) {
        int total = 0;
        for (WeightedRandomChestContent c : contents) {
            if (c != null) {
                total += ((WeightedRandomItemAccessor) c).emiLoot$getWeight();
            }
        }
        List<LootEntry> entries = new ArrayList<>();
        if (total <= 0) { return entries; }
        for (WeightedRandomChestContent c : contents) {
            if (c == null) { continue; }
            WeightedRandomChestContentAccessor acc = (WeightedRandomChestContentAccessor) c;
            ItemStack stack = acc.emiLoot$getStack();
            if (stack == null) { continue; }
            int weight = ((WeightedRandomItemAccessor) c).emiLoot$getWeight();
            int min = Math.max(1, acc.emiLoot$getMinCount());
            int max = Math.max(min, acc.emiLoot$getMaxCount());
            float chance = (float) weight / total;
            float avg = chance * (min + max) / 2.0F;
            ItemStack copy = stack.copy();
            copy.stackSize = 1;
            entries.add(new LootEntry(copy, min, max, chance, avg, weight, total, false));
        }
        entries.sort((a, b) -> Integer.compare(b.weight(), a.weight()));
        return entries;
    }
}
