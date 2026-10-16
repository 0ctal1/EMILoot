package btw.community.emiloot.data;

import api.util.ForkableRandom;
import btw.community.emiloot.EmiLootConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.src.BiomeGenBase;
import net.minecraft.src.ChunkCoordinates;
import net.minecraft.src.Entity;
import net.minecraft.src.World;
import net.minecraft.src.Block;
import net.minecraft.src.Chunk;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.ItemStack;
import net.minecraft.src.Material;
import net.minecraft.src.StatCollector;
import net.minecraft.src.WorldChunkManager;
import net.minecraft.src.WorldProvider;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

public final class OreDistributionScanner {
    public static final int HEIGHT = 128;
    private static final int MIN_DIMENSION = -16;
    private static final int MAX_DIMENSION = 16;
    public record OreDistribution(String source, String label, String fullLabel, int groupId, List<ItemStack> variants, List<float[]> perY, int samples, String noteKey, int fillerId, int[] layerIds, int[] layerMetas) {
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

    @FunctionalInterface private interface ChunkGenerator { void generate(OreSandboxWorld world, Random rand) throws Exception; }
    private record Profile(int fillerId, int[] ids, int[] metas) {}
    private record Group(String key, Map<Integer, long[]> states) {}
    private OreDistributionScanner() {}

    public static List<OreDistribution> scan() {
        int samples = Math.max(1, EmiLootConfig.oreSamples);
        List<OreDistribution> out = new ArrayList<>();
        for (int dim = MIN_DIMENSION; dim <= MAX_DIMENSION; dim++) {
            WorldProvider provider;
            try { provider = WorldProvider.getProviderForDimension(dim); }
            catch (Throwable t) { continue; }
            if (provider == null) { continue; }
            try { out.addAll(dim == 0 ? scanBiomes(samples) : scanDimension(dim, samples)); }
            catch (Throwable t) { EmiLootLog.warn("Ore distribution for dimension " + dim + " failed: " + t); }
        }
        return out;
    }

    private static OreSandboxWorld worldFor(int dim) throws Exception {
        OreSandboxWorld world = OreSandboxWorld.create(dim);
        world.provider.registerWorld(world);
        return world;
    }

    private static List<OreDistribution> scanDimension(int dim, int samples) throws Exception {
        OreSandboxWorld world = worldFor(dim);
        IChunkProvider generator = world.provider.createChunkGenerator();
        Profile profile = loadedProfile(dim);
        if (profile == null) { profile = profile(List.of(generator.provideChunk(0, 0))); }
        if (profile == null) { return List.of(); }
        world.setLayers(profile.fillerId(), profile.ids(), profile.metas());
        String label = world.provider.getDimensionName();
        Map<String, Group> groups = run(world, (w, rand) -> generator.populate(generator, 0, 0), samples);
        return toDistributions("dim" + dim, label, label, groups, groups.keySet(), samples, profile);
    }

    private static List<OreDistribution> scanBiomes(int samples) throws Exception {
        OreSandboxWorld world = worldFor(0);
        Profile profile = loadedProfile(0);
        try { if (profile == null) { profile = profile(List.of(world.provider.createChunkGenerator().provideChunk(0, 0))); } }
        catch (Throwable t) { EmiLootLog.warn("Could not sample overworld terrain, assuming plain stone: " + t); }
        if (profile == null) { profile = flatProfile(Block.stone.blockID); }
        world.setLayers(profile.fillerId(), profile.ids(), profile.metas());
        String dimension = world.provider.getDimensionName();

        List<BiomeGenBase> biomes = generatedBiomes(world.getWorldChunkManager());
        int detect = Math.max(32, samples / 10);
        Map<BiomeGenBase, BiomeGenBase> probes = new LinkedHashMap<>();
        Map<String, Set<Integer>> biomesOf = new LinkedHashMap<>();
        for (BiomeGenBase biome : biomes) {
            try {
                BiomeGenBase probe = probeCopy(biome);
                Map<String, Group> found = run(world, (w, rand) -> probe.decorate(w, rand, 0, 0), detect);
                probes.put(biome, probe);
                for (String key : found.keySet()) { biomesOf.computeIfAbsent(key, k -> new TreeSet<>()).add(biome.biomeID); }
            }
            catch (Throwable t) { EmiLootLog.warn("Skipping ore generation for biome " + biome.biomeName + ": " + t); }
        }
        Set<Integer> everywhere = new TreeSet<>();
        for (BiomeGenBase b : probes.keySet()) { everywhere.add(b.biomeID); }

        Map<Set<Integer>, Set<String>> partitions = new LinkedHashMap<>();
        for (Map.Entry<String, Set<Integer>> e : biomesOf.entrySet()) { partitions.computeIfAbsent(e.getValue(), k -> new LinkedHashSet<>()).add(e.getKey()); }

        List<OreDistribution> out = new ArrayList<>();
        for (Map.Entry<Set<Integer>, Set<String>> part : partitions.entrySet()) {
            Set<Integer> ids = part.getKey();
            BiomeGenBase probe = null;
            for (Map.Entry<BiomeGenBase, BiomeGenBase> e : probes.entrySet()) { if (ids.contains(e.getKey().biomeID)) { probe = e.getValue(); break; } }
            if (probe == null) { continue; }
            final BiomeGenBase chosen = probe;
            boolean all = ids.equals(everywhere);
            String source = all ? "dim0" : "dim0_b" + String.join("_", ids.stream().map(String::valueOf).toList());
            String label = all ? dimension : dimension + ": " + biomeNames(ids, true);
            String fullLabel = all ? dimension : dimension + ": " + biomeNames(ids, false);
            Map<String, Group> groups = run(world, (w, rand) -> chosen.decorate(w, rand, 0, 0), samples);
            out.addAll(toDistributions(source, label, fullLabel, groups, part.getValue(), samples, profile));
        }
        return out;
    }

    private static String biomeNames(Set<Integer> ids, boolean shorten) {
        List<String> names = new ArrayList<>();
        for (int id : ids) {
            BiomeGenBase b = id >= 0 && id < BiomeGenBase.biomeList.length ? BiomeGenBase.biomeList[id] : null;
            names.add(b == null ? String.valueOf(id) : b.biomeName);
        }
        if (!shorten) { return String.join(", ", names); }
        List<String> kept = new ArrayList<>();
        for (String n : names) {
            String key = n.replace(" ", "").toLowerCase(Locale.ROOT);
            boolean variant = false;
            for (String other : names) {
                String otherKey = other.replace(" ", "").toLowerCase(Locale.ROOT);
                if (!other.equals(n) && key.startsWith(otherKey)) { variant = true; break; }
            }
            if (!variant) { kept.add(n); }
        }
        return String.join(", ", kept);
    }

    private static List<BiomeGenBase> generatedBiomes(WorldChunkManager manager) {
        Set<BiomeGenBase> seen = new LinkedHashSet<>();
        if (manager != null) {
            try {
                for (int region = 0; region < 16; region++) {
                    int x = (region % 4 - 2) * 4096;
                    int z = (region / 4 - 2) * 4096;
                    BiomeGenBase[] area = manager.getBiomesForGeneration(null, x, z, 256, 256);
                    for (BiomeGenBase b : area) { if (b != null) { seen.add(b); } }
                }
            }
            catch (Throwable t) { EmiLootLog.warn("Could not sample overworld biomes: " + t); }
        }
        if (seen.isEmpty()) { for (BiomeGenBase b : BiomeGenBase.biomeList) { if (b != null) { seen.add(b); } } }
        List<BiomeGenBase> list = new ArrayList<>(seen);
        list.sort(Comparator.comparingInt(b -> b.biomeID));
        return list;
    }

    private static BiomeGenBase probeCopy(BiomeGenBase biome) throws Exception {
        Field unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object unsafe = unsafeField.get(null);
        BiomeGenBase copy = (BiomeGenBase) unsafe.getClass().getMethod("allocateInstance", Class.class).invoke(unsafe, biome.getClass());
        for (Class<?> c = biome.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) { continue; }
                f.setAccessible(true);
                f.set(copy, f.get(biome));
            }
        }
        copy.theBiomeDecorator = new OreProbeDecorator(copy);
        return copy;
    }

    private static Profile flatProfile(int fillerId) {
        int[] ids = new int[HEIGHT];
        int[] metas = new int[HEIGHT];
        for (int y = 0; y < HEIGHT; y++) { ids[y] = y == 0 ? Block.bedrock.blockID : fillerId; }
        return new Profile(fillerId, ids, metas);
    }

    private static final int MAX_PROFILE_CHUNKS = 32;
    private static final int PROFILE_RADIUS = 2;

    private static Profile loadedProfile(int dim) {
        try {
            MinecraftServer server = MinecraftServer.getServer();
            World world = server == null ? null : server.worldServerForDimension(dim);
            if (world == null || world.provider == null || world.provider.dimensionId != dim) { return null; }
            List<int[]> centers = new ArrayList<>();
            for (Object o : new ArrayList<>(world.playerEntities)) {
                if (o instanceof Entity e) { centers.add(new int[]{(int) Math.floor(e.posX) >> 4, (int) Math.floor(e.posZ) >> 4}); }
            }
            ChunkCoordinates spawn = world.getSpawnPoint();
            if (spawn != null) { centers.add(new int[]{spawn.posX >> 4, spawn.posZ >> 4}); }
            Set<Long> seen = new LinkedHashSet<>();
            List<Chunk> chunks = new ArrayList<>();
            for (int[] c : centers) {
                for (int dx = -PROFILE_RADIUS; dx <= PROFILE_RADIUS && chunks.size() < MAX_PROFILE_CHUNKS; dx++) {
                    for (int dz = -PROFILE_RADIUS; dz <= PROFILE_RADIUS && chunks.size() < MAX_PROFILE_CHUNKS; dz++) {
                        int cx = c[0] + dx;
                        int cz = c[1] + dz;
                        if (!seen.add(((long) cx << 32) ^ (cz & 0xFFFFFFFFL))) { continue; }
                        if (world.getChunkProvider().chunkExists(cx, cz)) { chunks.add(world.getChunkFromChunkCoords(cx, cz)); }
                    }
                }
            }
            return chunks.isEmpty() ? null : profile(chunks);
        }
        catch (Throwable t) {
            EmiLootLog.warn("Could not read loaded terrain for dimension " + dim + ": " + t);
            return null;
        }
    }

    private static Profile profile(List<Chunk> chunks) {
        Map<Integer, Integer> overall = new HashMap<>();
        List<Map<Integer, Integer>> perY = new ArrayList<>();
        for (int y = 0; y < HEIGHT; y++) {
            Map<Integer, Integer> counts = new HashMap<>();
            for (Chunk chunk : chunks) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        int id = chunk.getBlockID(x, y, z);
                        if (id <= 0) { continue; }
                        counts.merge((id << 4) | (chunk.getBlockMetadata(x, y, z) & 15), 1, Integer::sum);
                        overall.merge(id, 1, Integer::sum);
                    }
                }
            }
            perY.add(counts);
        }
        int columns = chunks.size() * 256;
        int filler = 0;
        int best = 0;
        for (Map.Entry<Integer, Integer> e : overall.entrySet()) {
            Block b = Block.blocksList[e.getKey()];
            if (b == null || b.blockMaterial.isLiquid() || b.blockHardness < 0) { continue; }
            if (e.getValue() > best) { best = e.getValue(); filler = e.getKey(); }
        }
        if (filler == 0) { return null; }
        int[] ids = new int[HEIGHT];
        int[] metas = new int[HEIGHT];
        int lastMeta = 0;
        for (int y = 0; y < HEIGHT; y++) {
            int top = 0;
            int topCount = 0;
            int fillerMeta = -1;
            int fillerCount = 0;
            for (Map.Entry<Integer, Integer> e : perY.get(y).entrySet()) {
                if (e.getValue() > topCount) { topCount = e.getValue(); top = e.getKey(); }
                if (e.getKey() >> 4 == filler && e.getValue() > fillerCount) { fillerCount = e.getValue(); fillerMeta = e.getKey() & 15; }
            }
            Block topBlock = top == 0 ? null : Block.blocksList[top >> 4];
            if (topBlock != null && top >> 4 != filler && topBlock.blockHardness < 0 && topCount > columns / 2) {
                ids[y] = top >> 4;
                metas[y] = top & 15;
                continue;
            }
            if (fillerMeta >= 0) { lastMeta = fillerMeta; }
            ids[y] = filler;
            metas[y] = lastMeta;
        }
        return new Profile(filler, ids, metas);
    }

    private static String groupKey(Block block) {
        String name;
        try { name = block.getUnlocalizedName(); }
        catch (Throwable t) { name = ""; }
        if (name == null) { name = ""; }
        if (name.startsWith("tile.")) { name = name.substring(5); }
        int dot = name.indexOf('.');
        if (dot > 0) { name = name.substring(0, dot); }
        return block.getClass().getName() + "|" + name;
    }

    private static boolean isOre(Block block, int fillerId) {
        if (block == null || block.blockID == fillerId) { return false; }
        Material m = block.blockMaterial;
        return m != null && m.isSolid() && !m.isLiquid() && m != Material.ground && m != Material.sand;
    }

    private static Map<String, Group> run(OreSandboxWorld world, ChunkGenerator generator, int samples) throws Exception {
        Random seeds = new Random(0x0E5D15L ^ world.fillerId());
        Map<String, Group> groups = new LinkedHashMap<>();
        int failures = 0;
        Throwable firstError = null;
        for (int i = 0; i < samples; i++) {
            long seed = seeds.nextLong();
            world.reset(seed ^ 0x5DEECE66DL);
            try { generator.generate(world, ForkableRandom.createForkableRandom(seed)); }
            catch (Throwable t) {
                failures++;
                if (firstError == null) { firstError = t; }
            }
            for (Map.Entry<Long, Integer> e : world.placed().entrySet()) {
                if (!world.replacedFiller(e.getKey())) { continue; }
                int packed = e.getValue();
                int id = packed >> 4;
                int y = OreSandboxWorld.yOf(e.getKey());
                if (id <= 0 || id >= Block.blocksList.length || y >= HEIGHT) { continue; }
                Block block = Block.blocksList[id];
                if (!isOre(block, world.fillerId())) { continue; }
                String key = groupKey(block);
                groups.computeIfAbsent(key, k -> new Group(k, new HashMap<>())).states().computeIfAbsent(packed, k -> new long[HEIGHT])[y]++;
            }
        }
        if (failures == samples && groups.isEmpty() && firstError != null) { throw new RuntimeException("generation failed every time", firstError); }
        return groups;
    }

    private static List<OreDistribution> toDistributions(String source, String label, String fullLabel, Map<String, Group> groups, Set<String> keep, int samples, Profile profile) {
        List<OreDistribution> out = new ArrayList<>();
        for (Group g : groups.values()) {
            if (!keep.contains(g.key())) { continue; }
            OreDistribution d = toDistribution(source, label, fullLabel, g.states(), samples, profile);
            if (d != null) { out.add(d); }
        }
        out.sort(Comparator.comparingDouble(OreDistribution::meanY).reversed());
        EmiLootLog.info("Ore distribution (" + fullLabel + "): " + out.size() + " ores from " + samples + " simulated chunks");
        return out;
    }

    private static OreDistribution toDistribution(String source, String label, String fullLabel, Map<Integer, long[]> states, int samples, Profile profile) {
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
        int groupId = Integer.MAX_VALUE;
        for (ItemStack s : variants) { groupId = Math.min(groupId, s.itemID); }
        return new OreDistribution(source, label, fullLabel, groupId, variants, heights, samples, noteFor(groupId), profile.fillerId(), profile.ids(), profile.metas());
    }

    private static ItemStack stackFor(int id, int meta) {
        if (id >= Block.blocksList.length || Block.blocksList[id] == null) { return null; }
        ItemStack exact = new ItemStack(id, 1, meta);
        if (StackValidator.isValid(exact)) { return exact; }
        ItemStack plain = new ItemStack(id, 1, 0);
        return StackValidator.isValid(plain) ? plain : null;
    }

    private static String noteFor(int blockId) {
        Block block = blockId < Block.blocksList.length ? Block.blocksList[blockId] : null;
        if (block == null) { return null; }
        String key = "emi_loot.ore.note." + block.getUnlocalizedName().toLowerCase(Locale.ROOT);
        return StatCollector.func_94522_b(key) ? key : null;
    }
}
