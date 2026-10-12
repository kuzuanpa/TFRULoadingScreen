/*
 * This file was created by <kuzuanpa>. It is distributed as part
 * of the TFRU Loading Screen Mod. Get the Source Code in github:
 * https://github.com/kuzuanpa/TFRULoadingScreen
 *
 * TFRU Loading Screen is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * The source code of TFRU Loading Screen is Open Source and distributed
 * under the AGPLv3 License: https://www.gnu.org/licenses/agpl-3.0.txt
 * (Bundled assets are under CC BY-NC-SA 4.0; see LICENSE-ASSETS.)
 */

package cn.kuzuanpa.tfruloading.loadingscreen;

import net.minecraft.client.gui.FontRenderer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws a scene in immediate mode on purpose: the Tessellator is thread-bound in this pack (GTNHLib)
 * and the pump thread draws too. Images are uploaded by hand, never through {@code RenderEngine}.
 */
public class SceneRenderer {

    private Scene cachedScene;
    private final Map<String, SceneCore.Shape> shapes = new HashMap<String, SceneCore.Shape>();
    private final Map<String, float[][]> glowShapes = new HashMap<String, float[][]>();

    private float cameraScale = 1f;
    private int logoTexture = 0;
    private boolean logoTextureTried = false;

    public void render(Scene scene, float time, float loopTime, int guiWidth, int guiHeight,
                       FontRenderer font, HudConfig hud) {
        if (scene == null || scene.nodes == null) return;

        float cw = scene.canvas != null && scene.canvas[0] > 0 ? scene.canvas[0] : 1920f;
        float ch = scene.canvas != null && scene.canvas[1] > 0 ? scene.canvas[1] : 1080f;
        float fit = Math.min(guiWidth / cw, guiHeight / ch);
        float ox = (guiWidth - cw * fit) / 2f;
        float oy = (guiHeight - ch * fit) / 2f;

        if (scene != cachedScene) {
            cachedScene = scene;
            shapes.clear();
            glowShapes.clear();
        }

        float[] bg = SceneCore.color(scene, scene.background);
        drawTriangles(fullscreen(guiWidth, guiHeight), bg, 1f);

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        SceneCore.CameraState cam = SceneCore.sampleCamera(scene, time);
        cameraScale = (float) Math.sqrt(Math.max(1e-4, Math.abs(cam.sx) * Math.abs(cam.sy)));
        GL11.glPushMatrix();
        applyCamera(cam, ox, oy, fit);

        for (Scene.Node node : scene.nodes) {
            if (node == null || node.id == null) continue;
            SceneCore.State st = SceneCore.sample(scene, node, time, loopTime);
            if (st.alpha <= 0.002f) continue;

            if ("text".equals(node.type)) {
                drawText(scene, node, st, ox, oy, fit, font, hud);
                continue;
            }

            String fillRef = st.fill != null ? st.fill : node.fill;
            String strokeRef = st.stroke != null ? st.stroke : node.stroke;

            SceneCore.Shape sh = shapeOf(node, st);

            float[] fill = toScreen(sh.fill, sh.pivotX, sh.pivotY, st, ox, oy, fit);
            float[] stroke = toScreen(sh.stroke, sh.pivotX, sh.pivotY, st, ox, oy, fit);

            if (Boolean.TRUE.equals(node.glow)) drawGlow(scene, node, st, sh, ox, oy, fit, fillRef, strokeRef);
            if (fill != null && fillRef != null) drawTriangles(fill, SceneCore.color(scene, fillRef), st.alpha);
            if (stroke != null && strokeRef != null) drawTriangles(stroke, SceneCore.color(scene, strokeRef), st.alpha);
        }

        GL11.glPopMatrix();

        drawLogoImage(scene, time, ox, oy, fit);

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    private SceneCore.Shape shapeOf(Scene.Node node, SceneCore.State st) {

        if (st != null && st.pts != null) return SceneCore.build(node, st);
        SceneCore.Shape sh = shapes.get(node.id);
        if (sh == null) {
            sh = SceneCore.build(node, null);
            shapes.put(node.id, sh);
        }
        return sh;
    }

    private static void applyCamera(SceneCore.CameraState cam, float ox, float oy, float fit) {
        if (cam.dx == 0f && cam.dy == 0f && cam.rot == 0f && cam.sx == 1f && cam.sy == 1f) return;
        float px = ox + cam.pivotX * fit;
        float py = oy + cam.pivotY * fit;
        GL11.glTranslatef(px + cam.dx * fit, py + cam.dy * fit, 0f);
        if (cam.rot != 0f) GL11.glRotatef(cam.rot, 0f, 0f, 1f);
        GL11.glScalef(cam.sx, cam.sy, 1f);
        GL11.glTranslatef(-px, -py, 0f);
    }

    private static float[] fullscreen(int w, int h) {
        return new float[] { 0, 0, w, 0, w, h, 0, 0, w, h, 0, h };
    }

    private static float[] toScreen(float[] tris, float pivotX, float pivotY, SceneCore.State st,
                                    float ox, float oy, float fit) {
        if (tris == null || tris.length == 0) return null;
        float[] v = tris.clone();
        SceneCore.transform(v, pivotX, pivotY, st);
        for (int i = 0; i + 1 < v.length; i += 2) {
            v[i] = ox + v[i] * fit;
            v[i + 1] = oy + v[i + 1] * fit;
        }
        return v;
    }

    private static final int GLOW_BANDS = 12;
    private static final float GLOW_REACH = 29f;
    private static final float GLOW_PEAK = 0.26f;

    private void drawGlow(Scene scene, Scene.Node node, SceneCore.State st, SceneCore.Shape sh,
                          float ox, float oy, float fit, String fillRef, String strokeRef) {
        List<float[]> outline = sh.outline;
        if (outline == null || outline.size() < 3) return;
        String ref = fillRef != null ? fillRef : strokeRef;
        if (ref == null) return;
        float[] rgb = SceneCore.color(scene, ref);
        boolean closed = !"poly".equals(node.type) || Boolean.TRUE.equals(node.closed);

        float nodeScale = (float) Math.sqrt(Math.max(1e-4, Math.abs(st.sx) * Math.abs(st.sy)));
        int step = Math.max(1, Math.round(15f / (cameraScale * nodeScale)));
        float[][] layers = glowLayers(node, outline, closed, st.pts != null, step);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        for (int i = 0; i < layers.length; i++) {
            float[] screen = toScreen(layers[i], sh.pivotX, sh.pivotY, st, ox, oy, fit);
            float u = (i + 0.5f) / GLOW_BANDS;
            drawTriangles(screen, rgb, st.alpha * GLOW_PEAK * (1f - u) * (1f - u));
        }
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    private float[][] glowLayers(Scene.Node node, List<float[]> outline, boolean closed, boolean morphing,
                                 int step) {
        String key = node.id + '#' + step;
        if (!morphing) {
            float[][] cached = glowShapes.get(key);
            if (cached != null) return cached;
        }
        float scale = step / 15f;
        float reach = GLOW_REACH * scale;
        List<float[]> rounded = SceneCore.roundCorners(outline, reach, closed);
        float[][] layers = new float[GLOW_BANDS][];
        for (int i = 0; i < GLOW_BANDS; i++) {
            layers[i] = SceneCore.haloBand(rounded, closed, reach * i / GLOW_BANDS,
                    reach * (i + 1) / GLOW_BANDS);
        }
        if (!morphing) {

            if (glowShapes.size() > 512) glowShapes.clear();
            glowShapes.put(key, layers);
        }
        return layers;
    }

    private static void drawTriangles(float[] tris, float[] rgb, float alpha) {
        if (tris == null || tris.length < 6) return;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(rgb[0], rgb[1], rgb[2], clamp01(alpha));

        GL11.glBegin(GL11.GL_TRIANGLES);
        for (int i = 0; i + 1 < tris.length; i += 2) {
            GL11.glVertex2f(tris[i], tris[i + 1]);
        }
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    private static void drawText(Scene scene, Scene.Node node, SceneCore.State st,
                                 float ox, float oy, float fit, FontRenderer font, HudConfig hud) {
        if (font == null || node.at == null) return;

        String text = node.text;
        String colorRef = st.color != null ? st.color : (node.color == null ? "ink" : node.color);
        HudConfig.Label override = hud == null ? null : hud.label(node.id);
        if (override != null) {
            if (override.text != null) text = override.text;
            if (override.color != null && override.color.length() > 0) colorRef = override.color;
        }
        if (text == null) return;

        float sx = ox + (node.at[0] + st.dx) * fit;
        float sy = oy + (node.at[1] + st.dy) * fit;
        float scaleX = st.sx * fit;
        float scaleY = st.sy * fit;
        float[] rgb = SceneCore.color(scene, colorRef);
        int color = (Math.round(clamp01(st.alpha) * 255f) << 24)
                | (Math.round(rgb[0] * 255f) << 16)
                | (Math.round(rgb[1] * 255f) << 8)
                | Math.round(rgb[2] * 255f);

        GL11.glPushMatrix();
        GL11.glTranslatef(sx, sy, 0f);
        if (st.rot != 0f) GL11.glRotatef(st.rot, 0f, 0f, 1f);
        GL11.glScalef(scaleX, scaleY, 1f);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        font.drawString(text, 0, 0, color);
        GL11.glPopMatrix();
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : v > 1f ? 1f : v;
    }

    private void drawLogoImage(Scene scene, float time, float ox, float oy, float fit) {
        Scene.Logo lg = scene.logo;
        if (lg == null || lg.at == null || lg.size == null || lg.at.length < 2 || lg.size.length < 2) return;
        float alpha = SceneCore.sampleLogoAlpha(scene, time);
        if (alpha <= 0.002f) return;
        int tex = logoTexture();
        if (tex == 0) return;

        float x0 = ox + lg.at[0] * fit, y0 = oy + lg.at[1] * fit;
        float x1 = ox + (lg.at[0] + lg.size[0]) * fit;
        float y1 = oy + (lg.at[1] + lg.size[1]) * fit;

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1f, 1f, 1f, clamp01(alpha));

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0f, 0f); GL11.glVertex2f(x0, y0);
        GL11.glTexCoord2f(1f, 0f); GL11.glVertex2f(x1, y0);
        GL11.glTexCoord2f(1f, 1f); GL11.glVertex2f(x1, y1);
        GL11.glTexCoord2f(0f, 1f); GL11.glVertex2f(x0, y1);
        GL11.glEnd();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    private int logoTexture() {
        if (logoTextureTried) return logoTexture;
        logoTextureTried = true;
        InputStream in = null;
        try {
            in = SceneRenderer.class.getResourceAsStream("/assets/tfruloading/textures/tfru_logo.png");
            if (in == null) return 0;
            BufferedImage img = ImageIO.read(in);
            if (img == null) return 0;
            int w = img.getWidth(), h = img.getHeight();
            int[] px = new int[w * h];
            img.getRGB(0, 0, w, h, px, 0, w);
            ByteBuffer buf = ByteBuffer.allocateDirect(w * h * 4);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int argb = px[y * w + x];
                    buf.put((byte) ((argb >> 16) & 0xff));
                    buf.put((byte) ((argb >> 8) & 0xff));
                    buf.put((byte) (argb & 0xff));
                    buf.put((byte) ((argb >>> 24) & 0xff));
                }
            }

            ((java.nio.Buffer) buf).flip();
            int tex = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, w, h, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
            logoTexture = tex;
        } catch (Throwable t) {
            logoTexture = 0;
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) { }
        }
        return logoTexture;
    }
}
