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

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * A private copy of Minecraft's font for the pump thread: own GL texture, own glyph lookup, so
 * drawing never goes through the game's {@code TextureManager} or {@code FontRenderer}.
 *
 * <p>Built on the game thread (see {@link LoadingScreenManager#begin()}): the first touch of
 * {@code FontRenderer} runs the pack's whole coremod transformer chain, which must not happen on the
 * pump thread.
 */
final class PumpFont {

    private static final ResourceLocation ASCII =
            new ResourceLocation("textures/font/ascii.png");

    private static boolean tried;
    private static int texture;
    private static FontRenderer renderer;

    private static final int MAX_PAGES = 64;
    private static final Map<String, Integer> PAGES = new HashMap<String, Integer>();

    private PumpFont() {
    }

    static synchronized FontRenderer get(Minecraft mc) {
        if (tried) return renderer;
        tried = true;
        try {
            texture = upload(mc, ASCII);
            if (texture == 0) {
                System.err.println("[tfruloading] could not read " + ASCII
                        + " from the default resource pack; the loading screen will draw no text");
                return null;
            }
            renderer = new OwnRenderer(mc, texture);
        } catch (Throwable t) {
            System.err.println("[tfruloading] could not build the loading screen font: " + t);
            renderer = null;
        }
        return renderer;
    }

    /** True once {@link #get(Minecraft)} has run; {@link FramePump} starts only after that. */
    static boolean isPrepared() {
        return tried;
    }

    private static int upload(Minecraft mc, ResourceLocation location) throws IOException {
        InputStream in = mc.mcDefaultResourcePack.getInputStream(location);
        if (in == null) return 0;
        try {
            BufferedImage img = ImageIO.read(in);
            if (img == null) return 0;
            int w = img.getWidth(), h = img.getHeight();
            int[] px = new int[w * h];
            img.getRGB(0, 0, w, h, px, 0, w);
            ByteBuffer buf = ByteBuffer.allocateDirect(w * h * 4);
            for (int i = 0; i < px.length; i++) {
                int argb = px[i];
                buf.put((byte) ((argb >> 16) & 0xff));
                buf.put((byte) ((argb >> 8) & 0xff));
                buf.put((byte) (argb & 0xff));
                buf.put((byte) ((argb >>> 24) & 0xff));
            }
            // Through a Buffer reference: Java 9+ made ByteBuffer.flip() covariant, and that signature
            // NoSuchMethodErrors on the Java 8 this game runs on.
            ((java.nio.Buffer) buf).flip();
            int tex = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, w, h, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
            return tex;
        } finally {
            try {
                in.close();
            } catch (IOException ignored) { }
        }
    }

    private static final class OwnRenderer extends FontRenderer {

        private final int texture;

        OwnRenderer(Minecraft mc, int texture) {
            super(mc.gameSettings, ASCII, null, false);
            this.texture = texture;
            super.onResourceManagerReload(null);
        }

        @Override
        protected void bindTexture(ResourceLocation location) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureFor(location));
        }

        @Override
        protected InputStream getResourceInputStream(ResourceLocation location) throws IOException {
            return Minecraft.getMinecraft().mcDefaultResourcePack.getInputStream(location);
        }
    }

    private static int textureFor(ResourceLocation location) {
        if (ASCII.equals(location)) return texture;
        String key = location.getResourcePath();
        Integer cached = PAGES.get(key);
        if (cached != null) return cached.intValue() != 0 ? cached.intValue() : texture;

        int uploaded = 0;
        try {
            uploaded = upload(Minecraft.getMinecraft(), location);
        } catch (Throwable t) {
            uploaded = 0;
        }
        if (PAGES.size() >= MAX_PAGES) {
            // All-or-nothing eviction: a page costs one 256x256 upload, so a burst is cheaper than
            // tracking usage.
            for (Integer id : PAGES.values()) {
                if (id != null && id.intValue() != 0) GL11.glDeleteTextures(id.intValue());
            }
            PAGES.clear();
        }
        PAGES.put(key, Integer.valueOf(uploaded));
        return uploaded != 0 ? uploaded : texture;
    }
}
