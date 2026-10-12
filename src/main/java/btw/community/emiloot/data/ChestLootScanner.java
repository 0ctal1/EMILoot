package btw.community.emiloot.data;

import btw.community.emiloot.mixin.WeightedRandomChestContentAccessor;
import btw.community.emiloot.mixin.WeightedRandomItemAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import btw.block.BTWBlocks;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import net.minecraft.src.StatCollector;
import net.minecraft.src.WeightedRandomChestContent;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ChestLootScanner {
    public record ChestLootTable(String key, ItemStack icon, int minRolls, int maxRolls, List<LootEntry> entries) {}
    private record Source(String key, String owner, String field, boolean hamper, int minRolls, int maxRolls) {}
    private record RawEntry(ItemStack stack, int weight, int min, int max) { RawEntry withCount(int newMin, int newMax) { return new RawEntry(stack, weight, newMin, newMax); }}
    @FunctionalInterface private interface DepthRule { RawEntry apply(RawEntry entry, int y); }
    private record DepthBand(String suffix, int sampleY) {}
    private record DepthVariant(DepthBand[] bands, DepthRule rule) {}
    private static final int DUNGEON_REDSTONE_MAX_Y = 16;
    private static final int DUNGEON_FULL_STACK_MAX_Y = 48;
    private static final DepthVariant DUNGEON = new DepthVariant(new DepthBand[]{new DepthBand("deep", DUNGEON_REDSTONE_MAX_Y), new DepthBand("mid", DUNGEON_FULL_STACK_MAX_Y), new DepthBand("shallow", DUNGEON_FULL_STACK_MAX_Y + 1),
    }, (e, y) -> {
        int id = e.stack().itemID;
        if (id == Item.redstone.itemID && y > DUNGEON_REDSTONE_MAX_Y) { return null; }
        if ((id == Item.ingotIron.itemID || id == Item.gunpowder.itemID) && y > DUNGEON_FULL_STACK_MAX_Y) { return e.withCount(1, 1); }
        return e;
    });

    private static final int MINESHAFT_REDSTONE_DIAMOND_MAX_Y = 24;
    private static final int MINESHAFT_FULL_IRON_MAX_Y = 36;
    private static final DepthVariant MINESHAFT = new DepthVariant(new DepthBand[]{ new DepthBand("deep", MINESHAFT_REDSTONE_DIAMOND_MAX_Y), new DepthBand("mid", MINESHAFT_FULL_IRON_MAX_Y), new DepthBand("shallow", MINESHAFT_FULL_IRON_MAX_Y + 1),
    }, (e, y) -> {
        int id = e.stack().itemID;
        if (id == Item.redstone.itemID || id == Item.diamond.itemID) {
            if (y > MINESHAFT_REDSTONE_DIAMOND_MAX_Y) { return null; }
            if (id == Item.diamond.itemID) { return e.withCount(1, 1); }
        }
        if (id == Item.ingotIron.itemID && y > MINESHAFT_FULL_IRON_MAX_Y) { return e.withCount(1, 1); }
        return e;
    });

    private static DepthVariant depthVariantFor(String key) {
        return switch (key) {
            case "dungeon" -> DUNGEON;
            case "mineshaft" -> MINESHAFT;
            default -> null;
        };
    }

    private static final Map<String, Integer> MANUSCRIPT_WEIGHTS = Map.of("dungeon", 30, "mineshaft", 1, "desert_temple", 1, "jungle_temple", 1, "stronghold_corridor", 1, "stronghold_library", 2, "stronghold_crossing", 1);
    private static Item manuscript;
    private static boolean manuscriptLookedUp;
    private static Item findManuscript() {
        if (manuscriptLookedUp) { return manuscript; }
        manuscriptLookedUp = true;
        Item fallback = null;
        for (Item item : Item.itemsList) {
            if (item == null) { continue; }
            String key = item.getUnlocalizedName();
            if (key == null) { continue; }
            if ("Ancient Manuscript".equals(StatCollector.translateToLocal(key + ".name"))) { return manuscript = item; }
            String lower = key.toLowerCase(Locale.ROOT);
            if (fallback == null && (lower.contains("manuscript") || lower.contains("prophecy"))) { fallback = item; }
        }
        if (fallback == null) { EmiLootLog.warn("Could not find the Ancient Manuscript item; chest tables will omit it"); }
        return manuscript = fallback;
    }

    private static List<RawEntry> withInjected(String key, List<RawEntry> raw) {
        Integer weight = MANUSCRIPT_WEIGHTS.get(key);
        Item item = weight == null ? null : findManuscript();
        if (item == null) { return raw; }
        for (RawEntry e : raw) { if (e.stack().itemID == item.itemID) { return raw; }}
        List<RawEntry> out = new ArrayList<>(raw);
        out.add(new RawEntry(new ItemStack(item), weight, 1, 1));
        return out;
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
                List<RawEntry> raw = withInjected(source.key(), toRaw(contents));
                ItemStack icon = new ItemStack(source.hamper() ? BTWBlocks.hamper : BTWBlocks.chest);
                DepthVariant variant = depthVariantFor(source.key());
                if (variant != null) {
                    for (DepthBand band : variant.bands()) { addTable(tables, source.key() + "_" + band.suffix(), icon, source, applyDepth(raw, variant.rule(), band.sampleY())); }}
                else {
                    addTable(tables, source.key(), icon, source, raw);
                }
            }
            catch (Throwable t) {
                EmiLootLog.warn("Could not read chest loot '" + source.key() + "': " + t);
            }
        }
        return tables;
    }

    private static void addTable(List<ChestLootTable> tables, String key, ItemStack icon, Source source, List<RawEntry> raw) {
        List<LootEntry> entries = toEntries(raw);
        if (!entries.isEmpty()) {
            tables.add(new ChestLootTable(key, icon, source.minRolls(), source.maxRolls(), entries));
        }
    }

    private static List<RawEntry> applyDepth(List<RawEntry> base, DepthRule rule, int y) {
        List<RawEntry> out = new ArrayList<>(base.size());
        for (RawEntry e : base) {
            RawEntry adjusted = rule.apply(e, y);
            if (adjusted != null) { out.add(adjusted); }
        }
        return out;
    }

    private static WeightedRandomChestContent[] read(MappingResolver resolver, Source source) throws Exception {
        String className = resolver.mapClassName("intermediary", source.owner());
        Class<?> cls = Class.forName(className, true, ChestLootScanner.class.getClassLoader());
        Field field;
        try {
            String fieldName = source.field().startsWith("field_") ? resolver.mapFieldName("intermediary", source.owner(), source.field(), CHEST_CONTENT_DESC) : source.field();
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

    private static List<LootEntry> toEntries(List<RawEntry> raw) {
        int total = 0;
        for (RawEntry r : raw) { total += r.weight(); }
        List<LootEntry> entries = new ArrayList<>();
        if (total <= 0) { return entries; }
        for (RawEntry r : raw) {
            float chance = (float) r.weight() / total;
            float avg = chance * (r.min() + r.max()) / 2.0F;
            entries.add(new LootEntry(r.stack(), r.min(), r.max(), chance, avg, r.weight(), total, false));
        }
        entries.sort((a, b) -> Integer.compare(b.weight(), a.weight()));
        return entries;
    }
}