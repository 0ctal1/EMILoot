package btw.community.emiloot.data;

import api.util.ForkableRandom;
import btw.block.BTWBlocks;
import btw.community.emiloot.EmiLootConfig;
import net.minecraft.src.BiomeGenBase;
import net.minecraft.src.BiomeGenHills;
import net.minecraft.src.Block;
import net.minecraft.src.ItemStack;
import net.minecraft.src.WorldGenMinable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class OreDistributionScanner {
    public static final int HEIGHT = 128;
    public record OreDistribution(String source, int groupId, List<ItemStack> variants, List<float[]> perY, int samples, String noteKey) {
        public float total() {
            float t = 0;
            for (float[] a : perY) { for (float v : a) { t += v; }}
            return t;
        }

        public float totalAt(int y) {
            float t = 0;
            for (float[] a : perY) { t += a[y]; }
            return t;
        }

        public int minY() {
            for (int y = 0; y < HEIGHT; y++) { if (totalAt(y) > 0) { return y; }}
            return 0;
        }

        public int maxY() {
            for (int y = HEIGHT - 1; y >= 0; y--) { if (totalAt(y) > 0) { return y; }}
            return 0;
        }

        public static float meanOf(float[] a) {
            double sum = 0;
            double weight = 0;
            for (int y = 0; y < a.length; y++) {
                sum += (double) y * a[y];
                weight += a[y];
            }
            return weight <= 0 ? 0 : (float) (sum / weight);
        }

        public float meanY() {
            float[] all = new float[HEIGHT];
            for (float[] a : perY) { for (int y = 0; y < HEIGHT; y++) { all[y] += a[y]; }}
            return meanOf(all);
        }
    }

    @FunctionalInterface private interface ChunkGenerator { void generate(OreSandboxWorld world, Random rand); }
    private OreDistributionScanner() {}
    public static List<OreDistribution> scan() {
        int samples = Math.max(1, EmiLootConfig.oreSamples);
        List<OreDistribution> out = new ArrayList<>();

        try {
            OreProbeDecorator decorator = new OreProbeDecorator(BiomeGenBase.plains);
            out.addAll(run("overworld", 0, Block.stone.blockID, samples, (world, rand) -> decorator.decorate(world, rand, 0, 0)));
        }
        catch (Throwable t) { EmiLootLog.warn("Ore distribution (overworld) failed: " + t); }
        try {
            if (BiomeGenBase.extremeHills instanceof BiomeGenHills hills) {
                if (!hills.hasSilverfishBeenInit) { hills.initSilverfish(); }
                out.addAll(run("extreme_hills", 0, Block.stone.blockID, samples, (world, rand) -> {
                    hills.addEmeralds(world, rand, 0, 0);
                    hills.addSilverfishBlocks(world, rand, 0, 0);
                }));
            }
        }
        catch (Throwable t) { EmiLootLog.warn("Ore distribution (extreme hills) failed: " + t); }
        try {
            WorldGenMinable quartz = new WorldGenMinable(Block.oreNetherQuartz.blockID, 13, Block.netherrack.blockID);
            out.addAll(run("nether", -1, Block.netherrack.blockID, samples, (world, rand) -> {
                for (int i = 0; i < 16; i++) {
                    int x = rand.nextInt(16);
                    int y = rand.nextInt(108) + 10;
                    int z = rand.nextInt(16);
                    quartz.generate(world, rand, x, y, z);
                }
            }));
        }
        catch (Throwable t) { EmiLootLog.warn("Ore distribution (nether) failed: " + t); }
        return out;
    }

    private static List<OreDistribution> run(String source, int dimension, int fillerId, int samples, ChunkGenerator generator) throws Exception {
        OreSandboxWorld world = OreSandboxWorld.create(dimension);
        Random seeds = new Random(0x0E5D15L ^ source.hashCode());
        Map<Integer, Map<Integer, long[]>> counts = new LinkedHashMap<>();
        for (int i = 0; i < samples; i++) {
            long seed = seeds.nextLong();
            world.reset(fillerId, seed ^ 0x5DEECE66DL);
            generator.generate(world, ForkableRandom.createForkableRandom(seed));
            for (Map.Entry<Long, Integer> e : world.placed().entrySet()) {
                int packed = e.getValue();
                int id = packed >> 4;
                int y = OreSandboxWorld.yOf(e.getKey());
                if (id <= 0 || id == fillerId || y >= HEIGHT || id == Block.dirt.blockID || id == Block.gravel.blockID) { continue; }
                counts.computeIfAbsent(groupOf(id), k -> new HashMap<>()).computeIfAbsent(packed, k -> new long[HEIGHT])[y]++;
            }
        }

        List<OreDistribution> out = new ArrayList<>();
        for (Map.Entry<Integer, Map<Integer, long[]>> group : counts.entrySet()) {
            OreDistribution d = toDistribution(source, group.getValue(), samples);
            if (d != null) { out.add(d); }
        }
        out.sort(Comparator.comparingDouble(OreDistribution::meanY).reversed());
        EmiLootLog.info("Ore distribution (" + source + "): " + out.size() + " ores from " + samples + " simulated chunks");
        return out;
    }

    private static OreDistribution toDistribution(String source, Map<Integer, long[]> states, int samples) {
        Map<String, ItemStack> stacks = new LinkedHashMap<>();
        Map<String, float[]> perY = new HashMap<>();
        for (Map.Entry<Integer, long[]> e : states.entrySet()) {
            int id = e.getKey() >> 4;
            int meta = e.getKey() & 15;
            ItemStack stack = stackFor(id, meta);
            if (stack == null) { continue; }
            String key = stack.itemID + ":" + stack.getItemDamage();
            stacks.putIfAbsent(key, stack);
            float[] avg = perY.computeIfAbsent(key, k -> new float[HEIGHT]);
            long[] c = e.getValue();
            for (int y = 0; y < HEIGHT; y++) { avg[y] += (float) c[y] / samples; }
        }
        if (stacks.isEmpty()) { return null; }
        List<String> keys = new ArrayList<>(stacks.keySet());
        keys.sort(Comparator.comparingDouble((String k) -> OreDistribution.meanOf(perY.get(k))).reversed());
        List<ItemStack> variants = new ArrayList<>();
        List<float[]> heights = new ArrayList<>();
        for (String k : keys) {
            variants.add(stacks.get(k));
            heights.add(perY.get(k));
        }
        int groupId = variants.get(0).itemID;
        return new OreDistribution(source, groupId, variants, heights, samples, noteFor(source, groupId));
    }

    private static ItemStack stackFor(int id, int meta) {
        if (id >= Block.blocksList.length || Block.blocksList[id] == null) { return null; }
        ItemStack exact = new ItemStack(id, 1, meta);
        if (StackValidator.isValid(exact)) { return exact; }
        ItemStack plain = new ItemStack(id, 1, 0);
        return StackValidator.isValid(plain) ? plain : null;
    }

    private static int groupOf(int id) {
        try {
            if (id == BTWBlocks.infestedMidStrataStone.blockID || id == BTWBlocks.infestedDeepStrataStone.blockID) { return BTWBlocks.infestedStone.blockID; }
        } catch (Throwable ignored) {}
        return id;
    }

    private static String noteFor(String source, int blockId) {
        if (source.equals("overworld") && blockId == Block.oreDiamond.blockID) { return "emi_loot.ore.note.diamond_caves"; }
        if (source.equals("extreme_hills")) { return "emi_loot.ore.note.extreme_hills"; }
        return null;
    }
}
