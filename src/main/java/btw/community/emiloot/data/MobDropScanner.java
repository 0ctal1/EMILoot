package btw.community.emiloot.data;

import btw.community.emiloot.EmiLootConfig;
import btw.community.emiloot.mixin.EntityListAccessor;
import btw.community.emiloot.mixin.EntityLivingBaseInvoker;
import btw.community.emiloot.mixin.EntitySlimeInvoker;
import api.entity.component.VariantComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.src.EntityEggInfo;
import net.minecraft.src.EntityList;
import net.minecraft.src.EntityVillager;
import net.minecraft.src.EntityLiving;
import net.minecraft.src.EntityLivingBase;
import net.minecraft.src.EntitySlime;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import net.minecraft.src.Minecraft;
import net.minecraft.src.World;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class MobDropScanner {
    public static final int MAX_LOOTING = 3;
    public record MobDropTable(int entityId, String entityName, EntityLivingBase entity, ItemStack spawnEgg, List<LootEntry> entries, int samples, int xpMin, int xpMax, float xpAvg) {}
    private MobDropScanner() {}
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List<MobDropTable> scan() {
        List<MobDropTable> tables = new ArrayList<>();
        World world = Minecraft.getMinecraft().theWorld;
        World samplingWorld = serverWorld(world);
        int samples = Math.max(16, EmiLootConfig.mobSamples);
        Map<Integer, Class> idToClass = new TreeMap<>((Map<Integer, Class>) EntityListAccessor.emiLoot$getIdToClassMapping());
        Map eggs = EntityListAccessor.emiLoot$getEntityEggs();
        Map classToName = EntityListAccessor.emiLoot$getClassToStringMapping();
        Set<String> hidden = hiddenMobs();
        for (Map.Entry<Integer, Class> e : idToClass.entrySet()) {
            Class<?> cls = e.getValue();
            if (cls == null || !EntityLiving.class.isAssignableFrom(cls) || Modifier.isAbstract(cls.getModifiers())) { continue; }
            String name = EntityList.getStringFromID(e.getKey());
            if (name == null) { name = cls.getSimpleName(); }
            if (hidden.contains(name.toLowerCase(java.util.Locale.ROOT))) { continue; }
            Object eggInfo = eggs == null ? null : eggs.get(e.getKey());
            EntityLivingBase entity;
            EntityLivingBase sampler;
            try {
                entity = create(cls, world, eggInfo);
                sampler = samplingWorld == world ? entity : create(cls, samplingWorld, eggInfo);
            }
            catch (Throwable t) {
                EmiLootLog.warn("Could not create " + cls.getName() + " for mob drops: " + t);
                continue;
            }
            EmiLootProgress.step("mob drops: " + name);
            try {
                List<LootEntry> entries = simulateAnySize(sampler, samples);
                int[] xp = experience(sampler, samples);
                if (sampler != entity && sampler instanceof EntitySlime slime && entity instanceof EntitySlime) { ((EntitySlimeInvoker) entity).emiLoot$setSlimeSize(slime.getSlimeSize()); }
                if (entries.isEmpty() && (xp == null || xp[1] <= 0)) { continue; }
                ItemStack egg = eggs != null && eggs.containsKey(e.getKey())
                        ? new ItemStack(Item.monsterPlacer.itemID, 1, e.getKey())
                        : null;
                tables.add(new MobDropTable(e.getKey(), name, entity, egg, entries, Math.max(samples, 4000),
                        xp == null ? -1 : xp[0], xp == null ? -1 : xp[1], xp == null ? -1 : xp[2] / 1000.0F));
            }
            catch (Throwable t) { EmiLootLog.warn("Skipping mob drops for " + name + ": " + t); }
        }
        tables = mergeVariants(tables, classToName);
        if (world == null) {
            EmiLootLog.info("No world was loaded while scanning mob drops; mob's drops depend on the world may be missing until EMI reloads in-game.");
        }
        return tables;
    }

    private static World serverWorld(World fallback) {
        try {
            MinecraftServer server = MinecraftServer.getServer();
            World w = server == null ? null : server.worldServerForDimension(0);
            if (w != null) { return w; }
        }
        catch (Throwable ignored) {}
        EmiLootLog.info("No integrated server world; mob drops that only happen server-side may be missing");
        return fallback;
    }

    private static EntityLivingBase create(Class<?> cls, World world, Object eggInfo) throws Exception {
        EntityLivingBase entity = (EntityLivingBase) cls.getConstructor(World.class).newInstance(world);
        if (eggInfo instanceof EntityEggInfo info && info.variant != 0 && entity.hasComponent(VariantComponent.class)) {
            entity.getComponent(VariantComponent.class).get().set(entity, info.variant);
        }
        return entity;
    }

    private static Set<String> hiddenMobs() {
        Set<String> out = new java.util.HashSet<>();
        String raw = EmiLootConfig.hiddenMobs == null ? "" : EmiLootConfig.hiddenMobs;
        for (String part : raw.split(",")) {
            String name = part.trim().toLowerCase(java.util.Locale.ROOT);
            if (!name.isEmpty()) { out.add(name); }
        }
        return out;
    }

    @SuppressWarnings("rawtypes")
    private static Class<?> variantParent(Class<?> cls, Map classToName) {
        Class<?> parent = cls.getSuperclass();
        if (parent == null || classToName == null || !Modifier.isAbstract(parent.getModifiers())) { return null; }
        return classToName.containsKey(parent) ? parent : null;
    }

    private static String dropSignature(MobDropTable table) {
        List<String> parts = new ArrayList<>();
        for (LootEntry e : table.entries()) { parts.add(DropStats.key(e.stack()) + "x" + e.minCount() + "-" + e.maxCount() + "@" + Math.round(e.chance() * 50)); }
        java.util.Collections.sort(parts);
        return String.join(",", parts) + "|xp" + table.xpMin() + "-" + table.xpMax();
    }

    @SuppressWarnings("rawtypes")
    private static List<MobDropTable> mergeVariants(List<MobDropTable> tables, Map classToName) {
        Map<Class<?>, List<MobDropTable>> byParent = new java.util.LinkedHashMap<>();
        for (MobDropTable t : tables) {
            Class<?> parent = variantParent(t.entity().getClass(), classToName);
            if (parent != null) { byParent.computeIfAbsent(parent, k -> new ArrayList<>()).add(t); }
        }
        Set<MobDropTable> drop = new java.util.HashSet<>();
        Map<MobDropTable, MobDropTable> replace = new java.util.HashMap<>();
        for (Map.Entry<Class<?>, List<MobDropTable>> group : byParent.entrySet()) {
            List<MobDropTable> members = group.getValue();
            if (members.size() < 2) { continue; }
            String sig = dropSignature(members.get(0));
            boolean identical = true;
            for (MobDropTable m : members) { identical &= dropSignature(m).equals(sig); }
            if (!identical) { continue; }
            MobDropTable first = members.get(0);
            if (first.entity() instanceof EntityVillager villager) { villager.setDirtyPeasant(1); }
            String parentName = String.valueOf(classToName.get(group.getKey()));
            replace.put(first, new MobDropTable(first.entityId(), parentName, first.entity(), first.spawnEgg(), first.entries(), first.samples(), first.xpMin(), first.xpMax(), first.xpAvg()));
            for (int i = 1; i < members.size(); i++) { drop.add(members.get(i)); }
        }
        List<MobDropTable> out = new ArrayList<>();
        for (MobDropTable t : tables) {
            if (drop.contains(t)) { continue; }
            out.add(replace.getOrDefault(t, t));
        }
        return out;
    }

    private static DropStats sample(int samples, Runnable hook) {
        DropStats stats = new DropStats();
        int failures = 0;
        Throwable lastError = null;
        for (int i = 0; i < samples; i++) {
            LootCapture.begin();
            boolean ok = true;
            try { hook.run(); }
            catch (Throwable t) {
                ok = false;
                lastError = t;
            }
            finally {
                List<ItemStack> drops = LootCapture.end();
                if (ok) { stats.addTrial(drops); }
            }
            if (!ok && ++failures > 2) { throw new RuntimeException("drop code keeps failing", lastError); }
        }
        return stats;
    }

    private static DropStats sampleQuietly(int samples, Runnable hook) {
        try { return sample(samples, hook); }
        catch (Throwable t) { return new DropStats(); }
    }

    private static List<LootEntry> simulateAnySize(EntityLivingBase entity, int samples) {
        if (!(entity instanceof EntitySlime)) { return simulate(entity, samples); }
        List<LootEntry> entries = List.of();
        for (int size : new int[]{1, 2, 4}) {
            ((EntitySlimeInvoker) entity).emiLoot$setSlimeSize(size);
            entries = simulate(entity, samples);
            if (!entries.isEmpty()) { break; }
        }
        return entries;
    }

    private static List<LootEntry> simulate(EntityLivingBase entity, int samples) {
        EntityLivingBaseInvoker invoker = (EntityLivingBaseInvoker) entity;
        List<LootEntry> entries = new ArrayList<>();
        List<Map<Long, LootEntry>> normal = new ArrayList<>();
        int lootSamples = Math.max(samples, 4000);
        for (int looting = 0; looting <= MAX_LOOTING; looting++) {
            final int l = looting;
            int n = lootSamples;
            entity.rand.setSeed(0x5EEDL + entity.getClass().getName().hashCode());
            DropStats stats = looting == 0 ? sample(n, () -> invoker.emiLoot$dropFewItems(true, l)) : sampleQuietly(n, () -> invoker.emiLoot$dropFewItems(true, l));
            normal.add(stats.byKey(1.0F));
        }
        java.util.Set<Long> keys = new java.util.LinkedHashSet<>();
        for (Map<Long, LootEntry> m : normal) { keys.addAll(m.keySet()); }
        for (long key : keys) {
            float[] chance = new float[MAX_LOOTING + 1];
            float[] avg = new float[MAX_LOOTING + 1];
            int[] maxPerLevel = new int[MAX_LOOTING + 1];
            LootEntry proto = null;
            int min = Integer.MAX_VALUE;
            int max = 0;
            for (int l = 0; l <= MAX_LOOTING; l++) {
                LootEntry e = normal.get(l).get(key);
                if (e != null) {
                    chance[l] = e.chance();
                    avg[l] = e.average();
                    maxPerLevel[l] = e.maxCount();
                    min = Math.min(min, e.minCount());
                    max = Math.max(max, e.maxCount());
                    if (proto == null || l == 0) { proto = e; }
                }
            }
            LootEntry base = normal.get(0).get(key);
            int bMin = base != null ? base.minCount() : min;
            int bMax = base != null ? base.maxCount() : max;
            entries.add(new LootEntry(proto.stack(), bMin, bMax, chance[0], avg[0], 0, 0, false, chance, avg, base == null ? "emi_loot.looting_only" : null, maxPerLevel));
        }
        entries.sort((a, b) -> Float.compare(b.chance(), a.chance()));
        int condSamples = Math.max(16, samples / 4);
        Map<Long, LootEntry> rare1 = sampleQuietly(condSamples, () -> invoker.emiLoot$dropRareDrop(1)).byKey(1.0F);
        Map<Long, LootEntry> rare0 = sampleQuietly(condSamples, () -> invoker.emiLoot$dropRareDrop(0)).byKey(1.0F);
        java.util.Set<Long> rareKeys = new java.util.LinkedHashSet<>(rare1.keySet());
        rareKeys.addAll(rare0.keySet());
        for (long key : rareKeys) {
            LootEntry e1 = rare1.get(key);
            LootEntry e0 = rare0.get(key);
            float[] chance = new float[MAX_LOOTING + 1];
            float[] avg = new float[MAX_LOOTING + 1];
            for (int l = 0; l <= MAX_LOOTING; l++) {
                float p1 = (4 * l + 1) / 800.0F;
                float p0 = 4 / 800.0F;
                chance[l] = (e1 == null ? 0 : p1 * e1.chance()) + (e0 == null ? 0 : p0 * e0.chance());
                avg[l] = (e1 == null ? 0 : p1 * e1.average()) + (e0 == null ? 0 : p0 * e0.average());
            }
            LootEntry proto = e1 != null ? e1 : e0;
            int min = Math.min(e1 == null ? Integer.MAX_VALUE : e1.minCount(), e0 == null ? Integer.MAX_VALUE : e0.minCount());
            int max = Math.max(e1 == null ? 0 : e1.maxCount(), e0 == null ? 0 : e0.maxCount());
            entries.add(new LootEntry(proto.stack(), min, max, chance[0], avg[0], 0, 0, true, chance, avg, "emi_loot.rare_drop"));
        }

        Map<Long, LootEntry> head = sampleQuietly(Math.max(16, samples / 8), invoker::emiLoot$dropHead).byKey(1.0F);
        for (LootEntry e : head.values()) {
            float[] chance = new float[MAX_LOOTING + 1];
            float[] avg = new float[MAX_LOOTING + 1];
            for (int l = 0; l <= MAX_LOOTING; l++) {
                float p = (5 + l) / 200.0F;
                chance[l] = p * e.chance();
                avg[l] = p * e.average();
            }
            entries.add(new LootEntry(e.stack(), e.minCount(), e.maxCount(), chance[0], avg[0], 0, 0, true, chance, avg, "emi_loot.head_drop"));
        }

        java.util.Random original = entity.rand;
        ProbeRandom probe = new ProbeRandom();
        LootCapture.begin();
        List<ItemStack> scroll;
        try {
            entity.rand = probe;
            entity.checkForScrollDrop();
        }
        catch (Throwable ignored) {
        }
        finally {
            entity.rand = original;
            scroll = LootCapture.end();
        }
        for (ItemStack s : scroll) {
            ItemStack copy = s.copy();
            int count = copy.stackSize;
            copy.stackSize = 1;
            float p = (float) probe.probability();
            entries.add(new LootEntry(copy, count, count, p, p * count, 0, 0, true, null, null, "emi_loot.scroll_drop"));
        }
        return entries;
    }

    private static int[] experience(EntityLivingBase entity, int samples) {
        EntityLivingBaseInvoker invoker = (EntityLivingBaseInvoker) entity;
        int min = Integer.MAX_VALUE;
        int max = 0;
        long sum = 0;
        int n = Math.max(16, samples / 2);
        try {
            for (int i = 0; i < n; i++) {
                int xp = invoker.emiLoot$getExperiencePoints(null);
                min = Math.min(min, xp);
                max = Math.max(max, xp);
                sum += xp;
            }
        }
        catch (Throwable t) { return null; }
        return new int[]{min, max, (int) (sum * 1000 / n)};
    }
}
