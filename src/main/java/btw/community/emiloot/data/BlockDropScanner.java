package btw.community.emiloot.data;

import api.item.items.PlaceAsBlockItem;
import net.minecraft.src.BlockHalfSlab;
import btw.community.emiloot.EmiLootConfig;
import net.minecraft.src.Block;
import net.minecraft.src.Item;
import net.minecraft.src.ItemBlock;
import net.minecraft.src.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class BlockDropScanner {
    public enum Kind { HARVEST, BAD_BREAK }
    public record BlockDropTable(List<ItemStack> inputs, int blockId, int meta, Kind kind, List<List<LootEntry>> variants, List<ItemStack> tools, boolean noToolNeeded) { public List<LootEntry> entries() { return variants.get(0); }}
    private record Found(ItemStack input, int meta, Kind kind, List<LootEntry> entries, ToolInfo tools) {}
    private static final int MIN_SAMPLES = 16;
    private static final int DETERMINISTIC_AFTER = 24;
    private static final long TIME_BUDGET_MS = 15_000L;
    private static final java.util.Map<Long, Boolean> VALID_CACHE = new java.util.HashMap<>();
    private BlockDropScanner() {}
    public static List<BlockDropTable> scan() {
        List<BlockDropTable> tables = new ArrayList<>();
        int samples = Math.max(MIN_SAMPLES, EmiLootConfig.blockSamples);
        VALID_CACHE.clear();
        ToolInfo.reset();
        SandboxWorld world;
        try {
            world = SandboxWorld.create();
        } catch (Throwable t) {
            EmiLootLog.warn("Could not create the sandbox world, falling back to basic block drops: " + t);
            world = null;
        }

        List<Block> blocks = new ArrayList<>();
        for (Block block : Block.blocksList) { if (block != null && block.blockID > 0 && block.blockID < Item.itemsList.length && Item.itemsList[block.blockID] != null) { blocks.add(block); } }

        long start = System.currentTimeMillis();
        int failedBlocks = 0;
        int done = 0;
        for (Block block : blocks) {
            if (done % 25 == 0) { EmiLootProgress.step("block drops " + done + "/" + blocks.size()); }
            done++;
            int n = System.currentTimeMillis() - start > TIME_BUDGET_MS ? MIN_SAMPLES : samples;
            try { scanBlock(world, block, Item.itemsList[block.blockID], n, tables); }
            catch (Throwable t) {
                failedBlocks++;
                EmiLootLog.warn("Skipping block drops for block id " + block.blockID + ": " + t);
            }
        }
        EmiLootLog.info("Scanned " + blocks.size() + " blocks in " + (System.currentTimeMillis() - start) + " ms" + (failedBlocks > 0 ? " (" + failedBlocks + " skipped)" : ""));
        return withoutDuplicates(tables);
    }

    private static List<BlockDropTable> withoutDuplicates(List<BlockDropTable> tables) {
        Set<String> seen = new HashSet<>();
        List<BlockDropTable> out = new ArrayList<>();
        int dupes = 0;
        for (BlockDropTable t : tables) {
            StringBuilder sig = new StringBuilder().append(t.kind());
            for (ItemStack in : t.inputs()) { sig.append('|').append(in.itemID).append(':').append(in.getItemDamage()); }
            sig.append("|tools");
            for (ItemStack tool : t.tools()) { sig.append(',').append(tool.itemID); }
            sig.append(t.noToolNeeded() ? "|hand" : "|nohand");
            for (List<LootEntry> variant : t.variants()) {
                sig.append("|v");
                for (LootEntry e : variant) {sig.append(',').append(DropStats.key(e.stack())).append('x').append(e.minCount()).append('-').append(e.maxCount()).append('@').append(Math.round(e.chance() * 20));}
            }
            if (seen.add(sig.toString())) { out.add(t);} else { dupes++; }
        }
        if (dupes > 0) { EmiLootLog.info("Merged " + dupes + " duplicate block drop tables"); }
        return out;
    }

    private static Set<Integer> candidateMetas(Block block, Item blockItem) {
        Set<Integer> metas = new LinkedHashSet<>();
        if (blockItem.getHasSubtypes()) {
            try {
                List<ItemStack> variants = new ArrayList<>();
                blockItem.getSubItems(blockItem.itemID, blockItem.getCreativeTab(), variants);
                for (ItemStack s : variants) { if (s != null && s.itemID == blockItem.itemID && s.getItemDamage() >= 0 && s.getItemDamage() < 16) { metas.add(s.getItemDamage()); }}}
            catch (Throwable ignored) {}
        }
        if (metas.isEmpty()) { for (int m = 0; m < 16; m++) { metas.add(m); }}
        return metas;
    }

    private static void scanBlock(SandboxWorld world, Block block, Item blockItem, int samples, List<BlockDropTable> out) {
        if (block instanceof BlockHalfSlab && block.isOpaqueCube()) { return; }
        boolean subtypes = blockItem.getHasSubtypes();
        Set<String> seenHarvest = new HashSet<>();
        Set<String> seenBad = new HashSet<>();
        List<Found> found = new ArrayList<>();
        int harvestFailures = 0;
        int badFailures = 0;
        for (int meta : candidateMetas(block, blockItem)) {
            ItemStack picked = pickedStack(world, block, meta, subtypes);
            if (picked == null) { continue; }
            ToolInfo tools = ToolInfo.of(world, block, meta);
            boolean harvestable = tools == ToolInfo.NONE || tools.noToolNeeded() || !tools.tools().isEmpty();
            boolean badBreakPossible = !tools.noToolNeeded();
            DropStats harvest = !harvestable ? null : world == null || harvestFailures >= 2 ? null : simulate(world, block, meta, samples, Kind.HARVEST);
            if (harvest == null && harvestable) {
                harvestFailures++;
                harvest = simulateFormula(block, meta, samples);
            }
            DropStats bad = !badBreakPossible || world == null || badFailures >= 2 ? null : simulate(world, block, meta, samples, Kind.BAD_BREAK);
            if (bad == null && badBreakPossible && world != null) { badFailures++; }
            List<LootEntry> harvestEntries = harvest == null || harvest.isEmpty() ? null : harvest.toEntries(1.0F, false);
            List<LootEntry> badEntries = bad == null || bad.isEmpty() ? null : bad.toEntries(1.0F, false);
            if (harvestEntries != null && sameDrops(harvestEntries, badEntries)) {
                tools = new ToolInfo(true, List.of());
                badEntries = null;
            }
            ItemStack input = placerInput(block, picked, harvestEntries);
            Set<Long> harvestKeys = harvest == null ? Set.of() : new HashSet<>(harvest.keys());
            if (harvestEntries != null && seenHarvest.add(input.itemID + ":" + input.getItemDamage() + ":" + harvestKeys)) {
                if (!(EmiLootConfig.hideSelfDrops && isSelfDrop(input, harvestEntries))) { found.add(new Found(input, meta, Kind.HARVEST, harvestEntries, tools)); }
            }
            if (badEntries != null) {
                Set<Long> badKeys = new HashSet<>(bad.keys());
                if (!badKeys.equals(harvestKeys) && seenBad.add(input.itemID + ":" + input.getItemDamage() + ":" + badKeys)) { found.add(new Found(input, meta, Kind.BAD_BREAK, badEntries, ToolInfo.NONE)); }
            }
        }

        java.util.Map<String, List<Found>> groups = new java.util.LinkedHashMap<>();
        for (Found f : found) {
            List<LootEntry> sorted = new ArrayList<>(f.entries());
            sorted.sort(java.util.Comparator.comparingInt((LootEntry e) -> e.stack().itemID).thenComparingInt(LootEntry::minCount).thenComparingInt(LootEntry::maxCount));
            StringBuilder sig = new StringBuilder().append(f.kind()).append('|').append(f.input().itemID).append('|').append(f.tools().signature());
            for (LootEntry e : sorted) { sig.append('|').append(e.stack().itemID).append('x').append(e.minCount()).append('-').append(e.maxCount()).append('@').append(Math.round(e.chance() * 50));}
            groups.computeIfAbsent(sig.toString(), k -> new ArrayList<>()).add(new Found(f.input(), f.meta(), f.kind(), sorted, f.tools()));
        }
        for (List<Found> group : groups.values()) {
            List<ItemStack> inputs = new ArrayList<>();
            List<List<LootEntry>> variants = new ArrayList<>();
            Set<Long> seenInputs = new HashSet<>();
            for (Found f : group) {
                if (!seenInputs.add(DropStats.key(f.input()))) { continue; }
                inputs.add(f.input());
                variants.add(f.entries());
            }
            Found first = group.get(0);
            out.add(new BlockDropTable(inputs, block.blockID, first.meta(), first.kind(), variants, first.tools().tools(), first.tools().noToolNeeded()));
        }
    }

    private static boolean sameDrops(List<LootEntry> a, List<LootEntry> b) {
        if (a == null || b == null || a.size() != b.size()) { return false; }
        java.util.Map<Long, LootEntry> byKey = new java.util.HashMap<>();
        for (LootEntry e : b) { byKey.put(DropStats.key(e.stack()), e); }
        for (LootEntry e : a) {
            LootEntry o = byKey.get(DropStats.key(e.stack()));
            if (o == null || o.minCount() != e.minCount() || o.maxCount() != e.maxCount() || Math.abs(o.chance() - e.chance()) > 0.02F) { return false; }
        }
        return true;
    }

    private static int placedBlockId(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof ItemBlock itemBlock) { return itemBlock.getBlockID(); }
        if (item instanceof PlaceAsBlockItem placer) { return placer.getBlockID(); }
        return -1;
    }

    private static ItemStack placerInput(Block block, ItemStack picked, List<LootEntry> drops) {
        if (drops == null || placedBlockId(picked) == block.blockID) { return picked; }
        for (LootEntry e : drops) {
            if (placedBlockId(e.stack()) == block.blockID && StackValidator.isValid(e.stack())) {
                ItemStack placer = e.stack().copy();
                placer.stackSize = 1;
                return placer;
            }
        }
        return picked;
    }

    private static ItemStack pickedStack(SandboxWorld world, Block block, int meta, boolean subtypes) {
        ItemStack fallback = new ItemStack(block.blockID, 1, subtypes ? meta : 0);
        if (world != null) {
            try {
                world.setBlockUnderTest(block.blockID, meta, 0L);
                int id = block.idPicked(world, SandboxWorld.X, SandboxWorld.Y, SandboxWorld.Z);
                if (subtypes && id == block.blockID) {
                    ItemStack own = new ItemStack(block.blockID, 1, block.getDamageValue(world, SandboxWorld.X, SandboxWorld.Y, SandboxWorld.Z));
                    if (StackValidator.isValid(own)) { return own; }
                    if (StackValidator.isValid(fallback)) { return fallback; }
                }
                if (id != block.blockID && block.renderAsNormalBlock()) {
                    ItemStack own = new ItemStack(block.blockID, 1, meta);
                    if (StackValidator.isValid(own)) { return own; }
                }
                if (id > 0 && id < Item.itemsList.length && Item.itemsList[id] != null) {
                    ItemStack picked = normalized(new ItemStack(id, 1, block.getDamageValue(world, SandboxWorld.X, SandboxWorld.Y, SandboxWorld.Z)));
                    if (StackValidator.isValid(picked)) { return picked; }
                }
            }
            catch (Throwable ignored) {}
        }
        return StackValidator.isValid(fallback) ? fallback : null;
    }

    private static DropStats simulate(SandboxWorld world, Block block, int meta, int samples, Kind kind) {
        DropStats stats = new DropStats();
        world.setBlockUnderTest(block.blockID, meta, block.blockID * 4099L + meta * 31L + kind.ordinal());
        java.util.Map<Long, Integer> first = null;
        boolean allSame = true;
        for (int i = 0; i < samples; i++) {
            List<ItemStack> drops;
            boolean ok = true;
            world.restoreBlockUnderTest(block.blockID, meta);
            LootCapture.begin();
            try {
                if (kind == Kind.HARVEST) { block.dropBlockAsItemWithChance(world, SandboxWorld.X, SandboxWorld.Y, SandboxWorld.Z, meta, 1.0F, 0); }
                else { breakByHand(world, block); }
            }
            catch (Throwable t) { ok = false; }
            finally { drops = LootCapture.end(); }
            if (!ok) { return null; }
            List<ItemStack> valid = validOnly(drops);
            stats.addTrial(valid);
            java.util.Map<Long, Integer> signature = signature(valid);
            if (first == null) { first = signature; }
            else if (!first.equals(signature)) { allSame = false; }
            if (allSame && i + 1 >= DETERMINISTIC_AFTER) { break; }
        }
        return stats;
    }

    private static final int MAX_HAND_HITS = 64;

    private static void breakByHand(SandboxWorld world, Block block) {
        int x = SandboxWorld.X;
        int y = SandboxWorld.Y;
        int z = SandboxWorld.Z;
        for (int hit = 0; hit < MAX_HAND_HITS && world.getBlockId(x, y, z) == block.blockID; hit++) {
            int meta = world.getBlockMetadata(x, y, z);
            boolean convertible;
            try { convertible = block.canConvertBlock(null, world, x, y, z); }
            catch (Throwable t) { convertible = false; }
            if (convertible) {
                if (!block.convertBlock(null, world, x, y, z, 1)) { return; }
                continue;
            }
            try { block.onBlockDestroyedWithImproperTool(world, null, x, y, z, meta); }
            catch (NullPointerException npe) {
                LootCapture.end();
                LootCapture.begin();
                block.dropComponentItemsOnBadBreak(world, x, y, z, meta, 1.0F);
            }
            return;
        }
    }

    private static java.util.Map<Long, Integer> signature(List<ItemStack> drops) {
        java.util.Map<Long, Integer> map = new java.util.HashMap<>();
        for (ItemStack s : drops) { map.merge(DropStats.key(s), s.stackSize, Integer::sum); }
        return map;
    }

    private static DropStats simulateFormula(Block block, int meta, int samples) {
        DropStats stats = new DropStats();
        Random rand = new Random(block.blockID * 31L + meta);
        for (int i = 0; i < samples; i++) {
            List<ItemStack> drops = new ArrayList<>(2);
            try {
                int count = block.quantityDroppedWithBonus(0, rand);
                for (int j = 0; j < count; j++) {
                    int id = block.idDropped(meta, rand, 0);
                    if (id > 0 && id < Item.itemsList.length && Item.itemsList[id] != null) { drops.add(new ItemStack(id, 1, block.damageDropped(meta))); }
                }
            }
            catch (Throwable t) { return null; }
            stats.addTrial(validOnly(drops));
        }
        return stats;
    }

    private static List<ItemStack> validOnly(List<ItemStack> drops) {
        List<ItemStack> list = new ArrayList<>(drops.size());
        for (ItemStack raw : drops) {
            if (raw == null) { continue; }
            ItemStack s = normalized(raw);
            if (VALID_CACHE.computeIfAbsent(DropStats.key(s), k -> StackValidator.isValid(s))) { list.add(s); }
        }
        return list;
    }

    private static ItemStack normalized(ItemStack stack) {
        Item item = stack.getItem();
        if (item == null || item.getHasSubtypes() || item.isDamageable() || stack.getItemDamage() == 0) { return stack; }
        ItemStack copy = stack.copy();
        copy.setItemDamage(0);
        return copy;
    }

    private static boolean isSelfDrop(ItemStack input, List<LootEntry> entries) {
        if (entries.size() != 1) { return false; }
        LootEntry e = entries.get(0);
        return e.stack().itemID == input.itemID && e.minCount() == 1 && e.maxCount() == 1 && e.chance() >= 0.995F;
    }
}
