package btw.community.emiloot.data;

import api.world.data.DataEntry;
import net.minecraft.src.Entity;
import net.minecraft.src.EnumGameType;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.ILogAgent;
import net.minecraft.src.ISaveHandler;
import net.minecraft.src.Minecraft;
import net.minecraft.src.Profiler;
import net.minecraft.src.TileEntity;
import net.minecraft.src.Vec3Pool;
import net.minecraft.src.World;
import net.minecraft.src.WorldInfo;
import net.minecraft.src.WorldProvider;
import net.minecraft.src.WorldSettings;
import net.minecraft.src.WorldType;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class SandboxWorld extends World {
    public static final int X = 0;
    public static final int Y = 64;
    public static final int Z = 0;
    private int blockId;
    private int metadata;
    @SuppressWarnings("unused") private SandboxWorld() { super(null, null, null, (WorldSettings) null, null, null); }
    public static SandboxWorld create() throws Exception {
        Field unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object unsafe = unsafeField.get(null);
        SandboxWorld world = (SandboxWorld) unsafe.getClass().getMethod("allocateInstance", Class.class).invoke(unsafe, SandboxWorld.class);
        fillNullFields(world);
        world.isRemote = false;
        world.rand = new Random();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.theWorld != null && mc.theWorld.worldInfo != null) { world.worldInfo = mc.theWorld.worldInfo; }
        else { world.worldInfo = new WorldInfo(new WorldSettings(0L, EnumGameType.SURVIVAL, true, false, WorldType.DEFAULT), "emi_loot", true); }
        setFieldOfType(world, WorldProvider.class, WorldProvider.getProviderForDimension(0));
        return world;
    }

    public void setBlockUnderTest(int blockId, int metadata, long seed) {
        this.blockId = blockId;
        this.metadata = metadata;
        this.rand.setSeed(seed);
    }

    private static boolean isOrigin(int x, int y, int z) {
        return x == X && y == Y && z == Z;
    }
    @Override public int getBlockId(int x, int y, int z) {
        return isOrigin(x, y, z) ? blockId : 0;
    }
    @Override public int getBlockMetadata(int x, int y, int z) {
        return isOrigin(x, y, z) ? metadata : 0;
    }
    @Override public boolean blockExists(int x, int y, int z) {
        return true;
    }
    @Override public TileEntity getBlockTileEntity(int x, int y, int z) {
        return null;
    }
    @Override public boolean canBlockSeeTheSky(int x, int y, int z) {
        return true;
    }
    @Override public int getFullBlockLightValue(int x, int y, int z) {
        return 15;
    }
    @Override public int getBlockLightValue(int x, int y, int z) {
        return 15;
    }
    @Override public boolean doesBlockHaveSolidTopSurface(int x, int y, int z) {
        return false;
    }
    @Override public Entity getEntityByID(int id) {
        return null;
    }
    @Override protected IChunkProvider createChunkProvider() {
        return null;
    }
    @Override public <T> T getData(DataEntry.WorldDataEntry<T> entry) {
        return worldInfo.getData(entry);
    }
    @Override public <T> void setData(DataEntry.WorldDataEntry<T> entry, T value) {}
    public void restoreBlockUnderTest(int blockId, int metadata) {
        this.blockId = blockId;
        this.metadata = metadata;
    }

    private boolean setOrigin(int x, int y, int z, int id, int meta) {
        if (!isOrigin(x, y, z)) { return false; }
        this.blockId = id;
        this.metadata = id == 0 ? 0 : meta & 15;
        return true;
    }

    @Override public boolean setBlock(int x, int y, int z, int id, int meta, int flags) {
        return setOrigin(x, y, z, id, meta);
    }
    @Override public boolean setBlock(int x, int y, int z, int id) {
        return setOrigin(x, y, z, id, 0);
    }
    @Override public boolean setBlockMetadataWithNotify(int x, int y, int z, int meta, int flags) {
        return isOrigin(x, y, z) && setOrigin(x, y, z, blockId, meta);
    }
    @Override public boolean setBlockMetadataWithNotify(int x, int y, int z, int meta) {
        return isOrigin(x, y, z) && setOrigin(x, y, z, blockId, meta);
    }
    @Override public boolean setBlockToAir(int x, int y, int z) {
        return setOrigin(x, y, z, 0, 0);
    }
    @Override public boolean setBlockAndMetadataWithNotify(int x, int y, int z, int id, int meta) {
        return setOrigin(x, y, z, id, meta);
    }
    @Override public boolean setBlockWithNotify(int x, int y, int z, int id) {
        return setOrigin(x, y, z, id, 0);
    }
    @Override public void setBlockTileEntity(int x, int y, int z, TileEntity tileEntity) {}
    @Override public void removeBlockTileEntity(int x, int y, int z) {}
    @Override public void scheduleBlockUpdate(int x, int y, int z, int id, int delay) {}
    @Override public void markBlockForUpdate(int x, int y, int z) {}
    @Override public void notifyBlockChange(int x, int y, int z, int id) {}
    @Override public void notifyBlocksOfNeighborChange(int x, int y, int z, int id) {}
    @Override public void playAuxSFX(int type, int x, int y, int z, int data) {}
    static void fillNullFields(Object obj) throws IllegalAccessException {
        for (Class<?> c = obj.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) { continue; }
                f.setAccessible(true);
                if (f.get(obj) != null) { continue; }
                Object value = defaultFor(f.getType());
                if (value != null) { f.set(obj, value); }
            }
        }
    }

    private static Object defaultFor(Class<?> type) {
        if (type == List.class || type == ArrayList.class) { return new ArrayList<>(); }
        if (type == Set.class || type == HashSet.class) { return new HashSet<>(); }
        if (type == Map.class || type == HashMap.class) { return new HashMap<>(); }
        if (type == Random.class) { return new Random(); }
        if (type == Calendar.class) { return Calendar.getInstance(); }
        if (type == Profiler.class) { return new Profiler(); }
        if (type == Vec3Pool.class) { return new Vec3Pool(300, 2000); }
        if (type == int[].class) { return new int[32768]; }
        return null;
    }

    static void setFieldOfType(Object obj, Class<?> type, Object value) throws IllegalAccessException {
        for (Class<?> c = obj.getClass(); c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) && f.getType() == type) {
                    f.setAccessible(true);
                    f.set(obj, value);
                    return;
                }
            }
        }
    }
}
