package btw.community.emiloot.emi;

import btw.community.emiloot.EmiLootConfig;
import btw.community.emiloot.data.OreDistributionScanner;
import btw.community.emiloot.data.OreDistributionScanner.OreDistribution;
import emi.dev.emi.emi.EmiPort;
import emi.dev.emi.emi.api.widget.Widget;
import emi.dev.emi.emi.runtime.EmiDrawContext;
import emi.dev.emi.emi.screen.Bounds;
import emi.shims.java.net.minecraft.client.gui.DrawContext;
import emi.shims.java.net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.src.Block;
import net.minecraft.src.Icon;
import net.minecraft.src.ItemStack;
import net.minecraft.src.Minecraft;
import net.minecraft.src.RenderHelper;
import net.minecraft.src.Tessellator;
import net.minecraft.src.TextureMap;

import java.util.ArrayList;
import java.util.List;

final class OreGraphWidget extends Widget {
    private static final int LEVELS = OreDistributionScanner.HEIGHT;
    static final int WIDTH = LEVELS + 2;
    static final int HEIGHT = 52;
    private final OreDistribution data;
    private final Bounds bounds;
    private final float[][] bins;
    private final float max;
    private final int bin;

    OreGraphWidget(OreDistribution data, int x, int y) {
        this.data = data;
        this.bounds = new Bounds(x, y, WIDTH, HEIGHT);
        this.bin = EmiLootConfig.accurateOreGraphs ? 1 : 4;
        int count = LEVELS / bin;
        this.bins = new float[data.perY().size()][count];
        float m = 0;
        for (int b = 0; b < count; b++) {
            float total = 0;
            for (int v = 0; v < bins.length; v++) {
                float sum = 0;
                for (int level = b * bin; level < (b + 1) * bin; level++) {sum += data.perY().get(v)[level];}
                bins[v][b] = sum;
                total += sum;
            }
            m = Math.max(m, total);
        }
        this.max = m;
    }

    @Override public Bounds getBounds() {
        return bounds;
    }
    private int plotX() {
        return bounds.x() + 1;
    }
    private int plotY() {
        return bounds.y() + 1;
    }
    private int binAt(int mouseX, int mouseY) {
        int level = mouseX - plotX();
        if (level < 0 || level >= LEVELS || mouseY < plotY() || mouseY >= plotY() + 40) { return -1; }
        return level / bin;
    }

    private float binTotal(int b) {
        float t = 0;
        for (float[] v : bins) { t += v[b]; }
        return t;
    }

    private int heightOf(int b) {
        float total = binTotal(b);
        return total <= 0 || max <= 0 ? 0 : Math.max(1, Math.round(total / max * 40));
    }

    private void drawOutline(EmiDrawContext draw, int px, int py) {
        int bottom = py + 40;
        int count = bins[0].length;
        for (int b = 0; b <= count; b++) {
            int left = b > 0 ? heightOf(b - 1) : 0;
            int right = b < count ? heightOf(b) : 0;
            int x = px + b * bin;
            if (right > 0) { draw.fill(x, bottom - right, bin, 1, 0xFFFFFFFF); }
            if (right > left) { draw.fill(x, bottom - right, 1, right - left, 0xFFFFFFFF); }
            else if (left > right) { draw.fill(x - 1, bottom - left, 1, left - right, 0xFFFFFFFF); }
        }
    }

    @Override
    public void render(DrawContext raw, int mouseX, int mouseY, float delta) {
        EmiDrawContext draw = EmiDrawContext.wrap(raw);
        RenderHelper.disableStandardItemLighting();
        int px = plotX();
        int py = plotY();
        draw.fill(bounds.x(), bounds.y(), WIDTH - 1, 1, 0xFF373737);
        draw.fill(bounds.x(), bounds.y(), 1, 40 + 1, 0xFF373737);
        draw.fill(bounds.x() + 1, py + 40, WIDTH - 1, 1, 0xFFFFFFFF);
        draw.fill(bounds.x() + WIDTH - 1, bounds.y() + 1, 1, 40 + 1, 0xFFFFFFFF);
        draw.fill(bounds.x() + WIDTH - 1, bounds.y(), 1, 1, 0xFF8B8B8B);
        draw.fill(bounds.x(), py + 40, 1, 1, 0xFF8B8B8B);
        draw.resetColor();
        drawTextured(px, py);
        drawOutline(draw, px, py);
        int hovered = binAt(mouseX, mouseY);
        if (hovered >= 0) { draw.fill(px + hovered * bin, py, bin, 40, 0x80FFFFFF); }
        int labelY = py + 40 + 2;
        for (int level = 0; level <= LEVELS; level += 32) { draw.drawCenteredTextWithShadow(EmiPort.literal(String.valueOf(level)), px + Math.min(level, LEVELS - 4), labelY, 0xFFFFFFFF); }
        draw.resetColor();
    }

    private void drawTextured(int px, int py) {
        boolean started = false;
        try {
            boolean nether = data.source().equals("nether");
            Icon[] stone = new Icon[3];
            for (int s = 0; s < 3; s++) { stone[s] = nether ? Block.netherrack.getIcon(2, 0) : Block.stone.getIcon(2, s); }
            Icon[] ores = new Icon[bins.length];
            for (int v = 0; v < bins.length; v++) { ores[v] = iconOf(data.variants().get(v)); }
            for (Icon i : stone) { if (i == null) { return; } }
            for (Icon i : ores) { if (i == null) { return; } }
            Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
            Tessellator t = Tessellator.instance;
            t.startDrawingQuads();
            started = true;
            t.setColorOpaque_I(0x5A5A5A);
            for (int level = 0; level < LEVELS; level++) {
                int strata = nether ? 0 : level <= 24 ? 2 : level <= 48 ? 1 : 0;
                tile(t, stone[strata], px + level, py, 1, 40, px, py);
            }

            t.setColorOpaque_I(0xFFFFFF);
            if (max > 0) {
                int bottom = py + 40;
                for (int b = 0; b < bins[0].length; b++) {
                    float total = binTotal(b);
                    int column = heightOf(b);
                    if (column <= 0) { continue; }
                    float acc = 0;
                    int drawn = 0;
                    for (int v = 0; v < bins.length; v++) {
                        if (bins[v][b] <= 0) { continue; }
                        acc += bins[v][b];
                        int top = Math.round(acc / total * column);
                        if (top > drawn) {
                            tile(t, ores[v], px + b * bin, bottom - top, bin, top - drawn, px, py);
                            drawn = top;
                        }
                    }
                }
            }
            t.draw();
        }
        catch (Throwable e) { if (started) { try { Tessellator.instance.draw(); } catch (Throwable ignored){} } }
    }

    private static Icon iconOf(ItemStack stack) {
        int id = stack.itemID;
        if (id <= 0 || id >= Block.blocksList.length || Block.blocksList[id] == null) { return null; }
        return Block.blocksList[id].getIcon(2, stack.getItemDamage());
    }

    private static void tile(Tessellator t, Icon icon, int x, int y, int w, int h, int ox, int oy) {
        for (int yy = y; yy < y + h; ) {
            int ty = Math.floorMod(yy - oy, 16);
            int hh = Math.min(16 - ty, y + h - yy);
            for (int xx = x; xx < x + w; ) {
                int tx = Math.floorMod(xx - ox, 16);
                int ww = Math.min(16 - tx, x + w - xx);
                double u0 = icon.getInterpolatedU(tx);
                double u1 = icon.getInterpolatedU(tx + ww);
                double v0 = icon.getInterpolatedV(ty);
                double v1 = icon.getInterpolatedV(ty + hh);
                t.addVertexWithUV(xx, yy + hh, 0, u0, v1);
                t.addVertexWithUV(xx + ww, yy + hh, 0, u1, v1);
                t.addVertexWithUV(xx + ww, yy, 0, u1, v0);
                t.addVertexWithUV(xx, yy, 0, u0, v0);
                xx += ww;
            }
            yy += hh;
        }
    }

    @Override
    public List<TooltipComponent> getTooltip(int mouseX, int mouseY) {
        int b = binAt(mouseX, mouseY);
        if (b < 0) { return List.of(); }
        List<TooltipComponent> list = new ArrayList<>();
        String total = amount(binTotal(b));
        list.add(TooltipComponent.of(bin == 1 ? EmiPort.translatable("emi_loot.ore.at_level", String.valueOf(b), total) : EmiPort.translatable("emi_loot.ore.at_y", String.valueOf(b * bin), String.valueOf(b * bin + bin - 1), total)));
        if (bins.length > 1) { for (int v = 0; v < bins.length; v++) { if (bins[v][b] > 0) { list.add(TooltipComponent.of(EmiPort.literal("  " + data.variants().get(v).getDisplayName() + ": " + amount(bins[v][b])))); }}}
        list.add(TooltipComponent.of(EmiPort.translatable("emi_loot.ore.assumption." + (data.source().equals("nether") ? "nether" : "overworld"))));
        if (data.noteKey() != null) { list.add(TooltipComponent.of(EmiPort.translatable(data.noteKey()))); }
        list.add(TooltipComponent.of(EmiPort.translatable("emi_loot.ore.estimated", String.valueOf(data.samples()))));
        return list;
    }

    private static String amount(float value) {
        if (value <= 0) { return "0"; }
        if (value < 0.01F) { return "<0.01"; }
        return LootSlots.fmt(value);
    }
}