package btw.community.emiloot.emi;

import btw.community.emiloot.mixin.RendererLivingEntityAccessor;
import net.minecraft.src.EntityLivingBase;
import net.minecraft.src.ModelBase;
import net.minecraft.src.ModelBox;
import net.minecraft.src.ModelRenderer;
import net.minecraft.src.Render;
import net.minecraft.src.RenderManager;
import net.minecraft.src.RendererLivingEntity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ModelBounds {
    record Extent(float top, float bottom, float width, boolean scaleKnown) {}
    private float minY = Float.MAX_VALUE;
    private float maxY = -Float.MAX_VALUE;
    private float maxRadius;
    private ModelBounds() {}
    static Extent measure(EntityLivingBase entity) {
        try {
            Render render = RenderManager.instance.getEntityRenderObject(entity);
            if (!(render instanceof RendererLivingEntity)) { return null; }
            ModelBase model = ((RendererLivingEntityAccessor) render).emiLoot$getMainModel();
            if (model == null || model.boxList == null || model.boxList.isEmpty()) { return null; }
            model.setRotationAngles(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0625F, entity);
            Set<Object> children = new HashSet<>();
            for (Object o : model.boxList) { if (o instanceof ModelRenderer part && part.childModels != null) { children.addAll(part.childModels); } }
            ModelBounds b = new ModelBounds();
            for (Object o : model.boxList) { if (o instanceof ModelRenderer part && !children.contains(part)) { b.visit(part, IDENTITY, new float[3]); } }
            if (b.minY == Float.MAX_VALUE) { return null; }
            float top = -(24.0F - b.minY) / 16.0F;
            float bottom = -(24.0F - b.maxY) / 16.0F;
            float width = 2.0F * b.maxRadius / 16.0F;
            float[] scale = rendererScale((RendererLivingEntity) render, entity);
            boolean scaleKnown = scale != null;
            if (scaleKnown) {
                top *= scale[1];
                bottom *= scale[1];
                width *= scale[0];
            }
            if (bottom - top < 0.05F || width < 0.05F) { return null; }
            return new Extent(top, bottom, width, scaleKnown);
        }
        catch (Throwable t) { return null; }
    }

    private static float[] rendererScale(RendererLivingEntity render, EntityLivingBase entity) {
        java.nio.FloatBuffer buf = org.lwjgl.BufferUtils.createFloatBuffer(16);
        int depth = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_MODELVIEW_STACK_DEPTH);
        try {
            org.lwjgl.opengl.GL11.glPushMatrix();
            org.lwjgl.opengl.GL11.glLoadIdentity();
            ((RendererLivingEntityAccessor) render).emiLoot$preRenderCallback(entity, 0.0F);
            org.lwjgl.opengl.GL11.glGetFloat(org.lwjgl.opengl.GL11.GL_MODELVIEW_MATRIX, buf);
        }
        catch (Throwable t) { return null; }
        finally { while (org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_MODELVIEW_STACK_DEPTH) > depth) { org.lwjgl.opengl.GL11.glPopMatrix(); }}
        float sx = (float) Math.sqrt(buf.get(0) * buf.get(0) + buf.get(1) * buf.get(1) + buf.get(2) * buf.get(2));
        float sy = (float) Math.sqrt(buf.get(4) * buf.get(4) + buf.get(5) * buf.get(5) + buf.get(6) * buf.get(6));
        float sz = (float) Math.sqrt(buf.get(8) * buf.get(8) + buf.get(9) * buf.get(9) + buf.get(10) * buf.get(10));
        if (!(sx > 0.01F && sy > 0.01F && sx < 100.0F && sy < 100.0F)) { return null; }
        return new float[]{Math.max(sx, sz), sy};
    }

    private static final float[] IDENTITY = {1, 0, 0, 0, 1, 0, 0, 0, 1};
    @SuppressWarnings("rawtypes")
    private void visit(ModelRenderer part, float[] rot, float[] origin) {
        if (part.isHidden || !part.showModel) { return; }
        float[] pivot = {part.rotationPointX + part.offsetX * 16.0F, part.rotationPointY + part.offsetY * 16.0F, part.rotationPointZ + part.offsetZ * 16.0F};
        float[] o = add(origin, apply(rot, pivot));
        float[] r = mul(rot, mul(rotZ(part.rotateAngleZ), mul(rotY(part.rotateAngleY), rotX(part.rotateAngleX))));
        List cubes = part.cubeList;
        if (cubes != null) {
            for (Object c : cubes) {
                if (c instanceof ModelBox box) {
                    for (int i = 0; i < 8; i++) {
                        float[] corner = {(i & 1) == 0 ? box.posX1 : box.posX2, (i & 2) == 0 ? box.posY1 : box.posY2, (i & 4) == 0 ? box.posZ1 : box.posZ2};
                        float[] p = add(o, apply(r, corner));
                        minY = Math.min(minY, p[1]);
                        maxY = Math.max(maxY, p[1]);
                        maxRadius = Math.max(maxRadius, (float) Math.sqrt(p[0] * p[0] + p[2] * p[2]));
                    }
                }
            }
        }
        List kids = part.childModels;
        if (kids != null) { for (Object k : kids) { if (k instanceof ModelRenderer child) { visit(child, r, o); }}}
    }

    private static float[] rotX(float a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        return new float[]{1, 0, 0, 0, c, -s, 0, s, c};
    }

    private static float[] rotY(float a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        return new float[]{c, 0, s, 0, 1, 0, -s, 0, c};
    }

    private static float[] rotZ(float a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        return new float[]{c, -s, 0, s, c, 0, 0, 0, 1};
    }

    private static float[] mul(float[] a, float[] b) {
        float[] m = new float[9];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) { m[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j]; }
        }
        return m;
    }

    private static float[] apply(float[] m, float[] v) { return new float[]{m[0] * v[0] + m[1] * v[1] + m[2] * v[2], m[3] * v[0] + m[4] * v[1] + m[5] * v[2], m[6] * v[0] + m[7] * v[1] + m[8] * v[2]}; }
    private static float[] add(float[] a, float[] b) {
        return new float[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }
}
