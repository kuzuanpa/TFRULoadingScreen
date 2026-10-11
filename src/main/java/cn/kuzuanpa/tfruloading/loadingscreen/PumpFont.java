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
 * A private copy of Minecraft's ASCII font for the frame pump thread.
 *
 * <p>The pump must never touch the game's {@code FontRenderer} or {@code TextureManager}: those keep
 * their own idea of which texture is bound (MCPatcher/angelica/nechar all cache GL state in Java), and
 * a second thread binding textures through them is exactly the kind of thing that produced the
 * "half the glyphs turned into solid boxes" damage in the first pump attempt.
 *
 * <p>So this is a self-contained {@link FontRenderer} with its own GL texture, uploaded in the pump
 * thread's context - the same approach Forge's splash screen uses for its {@code SplashFontRenderer},
 * which is why it is safe to use from a thread that is not the game thread.
 *
 * <p>Forge patches {@link FontRenderer} with a {@code bindTexture(ResourceLocation)} seam precisely
 * so a private font can exist, and the unicode path (any character outside the ASCII sheet, e.g. the
 * Chinese tips) goes through that same seam: {@code loadGlyphTexture(page)} binds
 * {@code textures/font/unicode_page_XX.png}. {@link OwnRenderer#bindTexture(ResourceLocation)}
 * therefore uploads those pages on demand too, into this context, instead of letting them fall
 * through to the game's {@code TextureManager}.
 */
final class PumpFont {

    private static final ResourceLocation ASCII =
            new ResourceLocation("textures/font/ascii.png");

    private static boolean tried;
    private static int texture;
    private static FontRenderer renderer;

    /** Uploaded unicode glyph pages, keyed by resource path. Bounded; see {@link #textureFor}. */
    private static final int MAX_PAGES = 64;
    private static final Map<String, Integer> PAGES = new HashMap<String, Integer>();

    private PumpFont() {
    }

    /** The pump's font, or null when the asset could not be read (then the screen just skips text). */
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

    /** Uploads a PNG straight out of the default resource pack as a GL texture. */
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
                buf.put((byte) ((argb >> 16) & 0xff));  // red
                buf.put((byte) ((argb >> 8) & 0xff));   // green
                buf.put((byte) (argb & 0xff));          // blue
                buf.put((byte) ((argb >>> 24) & 0xff)); // alpha
            }
            // flip() through a Buffer reference on purpose: Java 9+ made ByteBuffer.flip() return
            // ByteBuffer, and a class file that names that covariant signature throws
            // NoSuchMethodError on the Java 8 the game runs on (which is exactly what happened:
            // "could not build the loading screen font: NoSuchMethodError: java.nio.ByteBuffer.flip").
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
            } catch (IOException ignored) {
                // nothing useful to do
            }
        }
    }

    /**
     * {@code FontRenderer} that reads its glyph widths from the default resource pack and binds our
     * own texture instead of going through the game's {@code TextureManager}.
     */
    private static final class OwnRenderer extends FontRenderer {

        private final int texture;

        OwnRenderer(Minecraft mc, int texture) {
            super(mc.gameSettings, ASCII, null, false);
            this.texture = texture;
            super.onResourceManagerReload(null); // reads the font image through getResourceInputStream
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

    /**
     * The texture to bind for a font resource: our ASCII atlas, or a unicode glyph page uploaded on
     * first use. A page that cannot be read falls back to the atlas (the text then reads wrong, but
     * nothing crashes and the next page still works).
     */
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
            // Bounded: a tips file can walk over dozens of CJK pages, and the ones already shown are
            // not worth keeping forever. Re-uploading a page is cheap enough (a 256x256 image).
            for (Integer id : PAGES.values()) {
                if (id != null && id.intValue() != 0) GL11.glDeleteTextures(id.intValue());
            }
            PAGES.clear();
        }
        PAGES.put(key, Integer.valueOf(uploaded));
        return uploaded != 0 ? uploaded : texture;
    }
}
