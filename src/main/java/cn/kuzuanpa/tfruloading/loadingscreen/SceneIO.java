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

import com.google.gson.Gson;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;

/**
 * Loads a {@link Scene} from the bundled jar resources or from a file on disk.
 *
 * <p>Pure Java (no Minecraft imports) so it can be unit tested off-line and so the exact
 * same parsing path is used by the in-game renderer.
 */
public final class SceneIO {

    private static final Gson GSON = new Gson();
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String BUNDLED_ROOT = "/assets/tfruloading/loading/scenes/";

    private SceneIO() {
    }

    /** Parses a scene straight from a reader. */
    public static Scene parse(Reader reader) {
        return GSON.fromJson(reader, Scene.class);
    }

    /**
     * Loads a scene bundled in the mod jar, e.g. {@code bundled("blacksmith")}.
     *
     * @throws IOException if the resource is missing or unreadable.
     */
    public static Scene bundled(String name) throws IOException {
        InputStream in = SceneIO.class.getResourceAsStream(BUNDLED_ROOT + name + ".json");
        if (in == null) {
            throw new IOException("Bundled scene not found: " + BUNDLED_ROOT + name + ".json");
        }
        try {
            return parse(new InputStreamReader(in, UTF8));
        } finally {
            closeQuietly(in);
        }
    }

    /** Loads a scene from disk. */
    public static Scene fromFile(File file) throws IOException {
        InputStream in = new FileInputStream(file);
        try {
            return parse(new InputStreamReader(in, UTF8));
        } finally {
            closeQuietly(in);
        }
    }

    /**
     * Reads a scene from {@code sceneDir} (a pack's {@code config/loadingscreen/scenes/}), falling
     * back to the copy bundled in the jar when the pack does not ship one. Read once, at
     * initialization - see {@link LoadingScreenManager#begin()}.
     */
    public static Scene load(File sceneDir, String name) throws IOException {
        File onDisk = new File(sceneDir, name + ".json");
        if (onDisk.isFile()) {
            return fromFile(onDisk);
        }
        return bundled(name);
    }

    private static void closeQuietly(InputStream in) {
        try {
            in.close();
        } catch (IOException ignored) {
            // nothing useful to do here
        }
    }
}
