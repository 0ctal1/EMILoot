package btw.community.emiloot.emi;

import emi.dev.emi.emi.EmiPort;
import emi.dev.emi.emi.api.widget.Widget;
import emi.dev.emi.emi.runtime.EmiDrawContext;
import emi.dev.emi.emi.screen.Bounds;
import emi.shims.java.net.minecraft.client.gui.DrawContext;
import net.minecraft.src.Minecraft;
import net.minecraft.src.ScaledResolution;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

final class ScrollingTextWidget extends Widget {
    private static final long PAUSE_MS = 1500L;
    private static final float PIXELS_PER_SECOND = 25.0F;
    private final String text;
    private final Bounds bounds;
    private final int color;

    ScrollingTextWidget(String text, int x, int y, int width, int color) {
        this.text = text;
        this.bounds = new Bounds(x, y, width, 10);
        this.color = color;
    }

    @Override public Bounds getBounds() {
        return bounds;
    }

    private float offset(int overflow) {
        long scroll = Math.max(1L, (long) (overflow / PIXELS_PER_SECOND * 1000.0F));
        long cycle = 2 * PAUSE_MS + 2 * scroll;
        long t = System.currentTimeMillis() % cycle;
        if (t < PAUSE_MS) { return 0; }
        if (t < PAUSE_MS + scroll) { return overflow * (float) (t - PAUSE_MS) / scroll; }
        if (t < 2 * PAUSE_MS + scroll) { return overflow; }
        return overflow * (1.0F - (float) (t - 2 * PAUSE_MS - scroll) / scroll);
    }

    @Override
    public void render(DrawContext raw, int mouseX, int mouseY, float delta) {
        EmiDrawContext draw = EmiDrawContext.wrap(raw);
        Minecraft mc = Minecraft.getMinecraft();
        int textWidth = mc.fontRenderer.getStringWidth(text);
        int overflow = textWidth - bounds.width();
        if (overflow <= 0) {
            draw.drawTextWithShadow(EmiPort.literal(text), bounds.x(), bounds.y(), color);
            return;
        }
        FloatBuffer matrix = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, matrix);
        float originX = matrix.get(12);
        float originY = matrix.get(13);
        int scale = new ScaledResolution(mc.gameSettings, mc.displayWidth, mc.displayHeight).getScaleFactor();
        int left = Math.round((originX + bounds.x()) * scale);
        int top = Math.round((originY + bounds.y()) * scale);
        int width = (bounds.width() + 1) * scale;
        int height = bounds.height() * scale;
        int bottom = mc.displayHeight - top - height;

        boolean wasScissoring = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        IntBuffer previous = BufferUtils.createIntBuffer(16);
        if (wasScissoring) {
            GL11.glGetInteger(GL11.GL_SCISSOR_BOX, previous);
            int px = previous.get(0);
            int py = previous.get(1);
            int right = Math.min(left + width, px + previous.get(2));
            int upper = Math.min(bottom + height, py + previous.get(3));
            left = Math.max(left, px);
            bottom = Math.max(bottom, py);
            width = Math.max(0, right - left);
            height = Math.max(0, upper - bottom);
        }
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(left, bottom, width, height);
        draw.push();
        draw.matrices().translate(-offset(overflow), 0.0, 0.0);
        draw.drawTextWithShadow(EmiPort.literal(text), bounds.x(), bounds.y(), color);
        draw.pop();
        if (wasScissoring) { GL11.glScissor(previous.get(0), previous.get(1), previous.get(2), previous.get(3)); }
        else { GL11.glDisable(GL11.GL_SCISSOR_TEST); }
    }
}
