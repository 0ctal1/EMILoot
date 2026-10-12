package btw.community.emiloot.data;

import api.world.data.DataEntry;
import net.minecraft.src.Block;
import net.minecraft.src.Entity;
import net.minecraft.src.EnumGameType;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.ILogAgent;
import net.minecraft.src.ISaveHandler;
import net.minecraft.src.Profiler;
import net.minecraft.src.TileEntity;
import net.minecraft.src.World;
import net.minecraft.src.WorldInfo;
import net.minecraft.src.WorldProvider;
import net.minecraft.src.WorldSettings;
import net.minecraft.src.WorldType;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public final class OreSandboxWorld extends World {
    public static final int MIN_Y = 1;
    public static final int MAX_Y = 127;
    private int fillerId;
    private Map<Long, Integer> placed;
    @SuppressWarnings("unused") private OreSandboxWorld() { super(null, null, null, (WorldSettings) null, null, null); }
    public static OreSandboxWorld create(int dimension) throws Exception {
        Field unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object unsafe = unsafeField.get(null);
        OreSandboxWorld world = (OreSandboxWorld) unsafe.getClass().getMethod("allocateInstance", Class.class).invoke(unsafe, OreSandboxWorld.class);
        SandboxWorld.fillNullFields(world);
        world.isRemote = false;
        world.rand = new Random();
        world.placed = new HashMap<>();
        world.worldInfo = new WorldInfo(new WorldSettings(0L, EnumGameType.SURVIVAL, true, false, WorldType.DEFAULT), "emi_loot_ores", true);
        SandboxWorld.setFieldOfType(world, WorldProvider.class, WorldProvider.getProviderForDimension(dimension));
        return world;
    }

    public void reset(int fillerId, long seed) {
        this.fillerId = fillerId;
        this.placed.clear();
        this.rand.setSeed(seed);
    }

    public Map<Long, Integer> placed() {
        return placed;
    }
    public static int yOf(long key) {
        return (int) ((key >> 26) & 0xFF);
    }
    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF)) | ((long) (y & 0xFF) << 26) | ((long) (z & 0x3FFFFFF) << 34);
    }

    private int baseId(int y) {
        if (y < MIN_Y) { return y == 0 ? Block.bedrock.blockID : 0; }
        return y <= MAX_Y ? fillerId : 0;
    }

    @Override
    public int getBlockId(int x, int y, int z) {
        if (y < 0 || y > 255) { return 0; }
        Integer p = placed.get(key(x, y, z));
        return p != null ? p >> 4 : baseId(y);
    }

    @Override
    public int getBlockMetadata(int x, int y, int z) {
        if (y < 0 || y > 255) { return 0; }
        Integer p = placed.get(key(x, y, z));
        return p != null ? p & 15 : 0;
    }

    @Override public boolean isAirBlock(int x, int y, int z) {
        return getBlockId(x, y, z) == 0;
    }
    @Override public boolean blockExists(int x, int y, int z) {
        return true;
    }
    @Override public TileEntity getBlockTileEntity(int x, int y, int z) {
        return null;
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

    @Override
    public boolean setBlock(int x, int y, int z, int id, int meta, int flags) {
        if (y < 0 || y > 255) { return false; }
        placed.put(key(x, y, z), (id << 4) | (meta & 15));
        return true;
    }

    @Override public boolean setBlock(int x, int y, int z, int id) {
        return setBlock(x, y, z, id, 0, 3);
    }
    @Override public boolean setBlockMetadataWithNotify(int x, int y, int z, int meta, int flags) { return setBlock(x, y, z, getBlockId(x, y, z), meta, flags); }
    @Override public boolean setBlockMetadataWithNotify(int x, int y, int z, int meta) { return setBlockMetadataWithNotify(x, y, z, meta, 3); }
    @Override public boolean setBlockToAir(int x, int y, int z) {
        return setBlock(x, y, z, 0, 0, 3);
    }
    @Override public boolean setBlockAndMetadataWithNotify(int x, int y, int z, int id, int meta) { return setBlock(x, y, z, id, meta, 3); }
    @Override public boolean setBlockWithNotify(int x, int y, int z, int id) {
        return setBlock(x, y, z, id, 0, 3);
    }
    @Override public void setBlockTileEntity(int x, int y, int z, TileEntity tileEntity) {}
    @Override public void removeBlockTileEntity(int x, int y, int z) {}
    @Override public void scheduleBlockUpdate(int x, int y, int z, int id, int delay) {}
    @Override public void markBlockForUpdate(int x, int y, int z) {}
    @Override public void notifyBlockChange(int x, int y, int z, int id) {}
    @Override public void notifyBlocksOfNeighborChange(int x, int y, int z, int id) {}
    @Override public void playAuxSFX(int type, int x, int y, int z, int data) {}
}
