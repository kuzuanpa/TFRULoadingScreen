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

/**
 * The loading screen chrome that is not part of any scene: the bottom progress bar and the tips
 * line under it.
 *
 * <p>Colours and both text templates come from {@link HudConfig}, so a pack can rebrand the bar
 * without touching the film. Geometry is expressed as fractions of the GUI, exactly like the old
 * hard-coded version, so the bar keeps its proportions on every resolution.
 *
 * <p>Everything here is immediate mode and only ever touches {@code GL11} plus the {@link
 * FontRenderer} it is handed: this runs on the frame-pump thread as well as on the game thread (see
 * {@link LoadingScreenManager#renderFrame(boolean)}).
 */
final class HudRenderer {

    private static final float BAR_WIDTH = 0.70f;
    private static final float BAR_HEIGHT = 0.018f;
    private static final float BAR_MIN_HEIGHT = 6f;
    private static final float BAR_BOTTOM = 0.07f;
    private static final float BAR_BORDER = 2f;
    private static final float LABEL_LIFT = 11f;
    private static final float TIP_GAP = 10f;
    private static final float TIP_BOTTOM_MARGIN = 12f;
    private static final float BORDER_ALPHA = 0.85f;
    private static final float TRACK_ALPHA = 0.95f;

    private static final int ALIGN_LEFT = 0;
    private static final int ALIGN_CENTER = 1;
    private static final int ALIGN_RIGHT = 2;

    private HudRenderer() {
    }

    /**
     * Draws the progress bar (with its label) and, when {@code tip} is non-null, the tips line.
     *
     * @param status  the loading status, e.g. {@code "Pre-initialization: GregTech"}.
     * @param percent loading progress, 0..1.
     * @param tip     the tip to show, or null / empty for none.
     */
    static void draw(int width, int height, FontRenderer font, Scene scene, HudConfig hud,
                     String status, float percent, String tip) {
        if (width <= 0 || height <= 0) return;
        float p = clamp01(percent);
        float barWidth = width * BAR_WIDTH;
        float barHeight = Math.max(BAR_MIN_HEIGHT, height * BAR_HEIGHT);
        float x = (width - barWidth) / 2f;
        float y = height - barHeight - height * BAR_BOTTOM;

        // The bar is drawn after the scene, which leaves GL_BLEND off; turn it back on so the
        // configured alphas mean something (and so both draw paths agree).
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        fill(x - BAR_BORDER, y - BAR_BORDER, barWidth + BAR_BORDER * 2f, barHeight + BAR_BORDER * 2f,
                scene, hud.progressBorderColor(), BORDER_ALPHA);
        fill(x, y, barWidth, barHeight, scene, hud.progressTrackColor(), TRACK_ALPHA);
        fill(x, y, barWidth * p, barHeight, scene, hud.progressFillColor(), 1f);

        if (font == null) return;

        int labelY = Math.round(y - LABEL_LIFT);
        int textColour = argb(scene, hud.progressTextColor(), 1f);
        String statusLine = substitute(hud.statusText(), status, p);
        String percentLine = substitute(hud.percentText(), status, p);
        text(font, statusLine, Math.round(x), labelY, textColour, ALIGN_LEFT);
        text(font, percentLine, Math.round(x + barWidth), labelY, textColour, ALIGN_RIGHT);

        if (tip != null && tip.length() > 0) {
            float tipY = Math.min(y + barHeight + TIP_GAP, height - TIP_BOTTOM_MARGIN);
            String line = font.trimStringToWidth(tip, (int) barWidth);
            text(font, line, width / 2, Math.round(tipY), argb(scene, hud.tipsColor(), 1f), ALIGN_CENTER);
        }
    }

    /**
     * Fills {@code {status}} and {@code {percent}} in a progress text template. An unknown
     * placeholder is left alone, so a template can also be plain static text.
     */
    static String substitute(String template, String status, float percent) {
        if (template == null) return "";
        String out = template.replace("{status}", status == null ? "" : status);
        return out.replace("{percent}", Integer.toString(Math.round(clamp01(percent) * 100f)));
    }

    private static void text(FontRenderer font, String s, int x, int y, int colour, int align) {
        if (s == null || s.length() == 0) return;
        int px = x;
        if (align == ALIGN_CENTER) px = x - font.getStringWidth(s) / 2;
        else if (align == ALIGN_RIGHT) px = x - font.getStringWidth(s);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        font.drawString(s, px, y, colour);
    }

    private static void fill(float x, float y, float width, float height, Scene scene,
                             String colourRef, float alpha) {
        if (width <= 0f || height <= 0f) return;
        float[] rgb = SceneCore.color(scene, colourRef);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(rgb[0], rgb[1], rgb[2], clamp01(alpha));
        // Immediate mode: this may run on the frame pump thread (see SceneRenderer.drawTriangles).
        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex2f(x, y + height);
        GL11.glVertex2f(x + width, y + height);
        GL11.glVertex2f(x + width, y);
        GL11.glVertex2f(x, y + height);
        GL11.glVertex2f(x + width, y);
        GL11.glVertex2f(x, y);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    private static int argb(Scene scene, String colourRef, float alpha) {
        float[] rgb = SceneCore.color(scene, colourRef);
        return (Math.round(clamp01(alpha) * 255f) << 24)
                | (Math.round(clamp01(rgb[0]) * 255f) << 16)
                | (Math.round(clamp01(rgb[1]) * 255f) << 8)
                | Math.round(clamp01(rgb[2]) * 255f);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : v > 1f ? 1f : v;
    }
}
