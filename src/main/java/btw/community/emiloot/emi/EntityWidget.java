package btw.community.emiloot.emi;

import btw.community.emiloot.data.EmiLootLog;
import emi.dev.emi.emi.EmiPort;
import emi.dev.emi.emi.api.widget.Widget;
import emi.dev.emi.emi.screen.Bounds;
import emi.shims.java.net.minecraft.client.gui.DrawContext;
import emi.shims.java.net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.src.BossStatus;
import net.minecraft.src.EntityGhast;
import net.minecraft.src.EntityLivingBase;
import net.minecraft.src.EntityBat;
import net.minecraft.src.EntityWaterMob;
import net.minecraft.src.GuiInventory;
import net.minecraft.src.Minecraft;
import net.minecraft.src.RenderHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.util.List;

public class EntityWidget extends Widget {
    private final EntityLivingBase entity;
    private final String entityName;
    private final Bounds bounds;
    private boolean failed;
    private float top;
    private float bottom;
    private float width;
    private boolean measured;
    public EntityWidget(EntityLivingBase entity, String entityName, int x, int y, int w, int h) {
        this.entity = entity;
        this.entityName = entityName;
        this.bounds = new Bounds(x, y, w, h);
        this.failed = entity == null;
        if (entity instanceof EntityGhast) {
            top = -3.9F;
            bottom = 3.1F;
            width = 4.7F;
            measured = true;
        } else if (entity instanceof EntityWaterMob) {
            top = -0.85F;
            bottom = 1.35F;
            width = 0.9F;
            measured = true;
        } else if (entity != null) {
            top = -Math.max(entity.height, 0.3F);
            bottom = 0.0F;
            width = Math.max(Math.max(entity.width, 0.3F) * 1.6F, entity.height * 0.6F);
            measured = false;
            if (entity instanceof EntityBat bat) { bat.setIsBatHanging(false); }
        } else {
            top = -1.0F;
            bottom = 0.0F;
            width = 1.0F;
        }
    }

    public static boolean canRender(EntityLivingBase entity) {
        return entity != null;
    }
    @Override public Bounds getBounds() {
        return bounds;
    }
    @Override public List<TooltipComponent> getTooltip(int mouseX, int mouseY) {
        return List.of();
    }
    @Override public void render(DrawContext draw, int mouseX, int mouseY, float delta) {
        if (failed) { return; }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) { return; }
        if (entity.worldObj != mc.theWorld) { entity.setWorld(mc.theWorld); }
        if (!measured) {
            measured = true;
            ModelBounds.Extent e = ModelBounds.measure(entity);
            if (e != null && e.scaleKnown()) {
                top = e.top();
                bottom = e.bottom();
                width = e.width();
            } else if (e != null) {
                top = Math.min(e.top(), -Math.max(entity.height, 0.3F));
                bottom = Math.max(e.bottom(), 0.0F);
                width = Math.max(e.width(), Math.max(entity.width, 0.3F));
            }
        }

        float visualHeight = bottom - top;
        float fit = Math.min(bounds.height() * 0.85F / visualHeight, bounds.width() * 0.9F / width);
        int scale = Math.max(2, Math.min(30, (int) fit));
        int centerX = bounds.x() + bounds.width() / 2;
        float centerY = bounds.y() + bounds.height() / 2.0F;
        int originY = Math.round(centerY - (top + bottom) / 2.0F * scale);
        float lookX = centerX - mouseX;
        float lookY = (originY + top * scale * 0.8F) - mouseY;
        float bossHealth = BossStatus.healthScale;
        int bossBar = BossStatus.statusBarLength;
        String bossName = BossStatus.bossName;
        boolean bossFlag = BossStatus.field_82825_d;
        int depth = GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);
        boolean depthWasOn = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthMaskWasOn = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        try {
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(true);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GuiInventory.func_110423_a(centerX, originY, scale, lookX, lookY, entity);
        } catch (Throwable t) {
            failed = true;
            EmiLootLog.warn("Disabled model rendering for " + entityName + ": " + t);
        } finally {
            while (GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH) > depth) { GL11.glPopMatrix(); }
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            GL11.glDepthFunc(depthFunc);
            GL11.glDepthMask(depthMaskWasOn);
            if (!depthWasOn) { GL11.glDisable(GL11.GL_DEPTH_TEST); }
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            BossStatus.healthScale = bossHealth;
            BossStatus.statusBarLength = bossBar;
            BossStatus.bossName = bossName;
            BossStatus.field_82825_d = bossFlag;
        }
    }
}
