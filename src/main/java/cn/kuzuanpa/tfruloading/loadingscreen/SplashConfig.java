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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.Properties;

/**
 * Turns Forge's own splash screen off so the TFRU scene owns the window.
 *
 * <p>Two independent levers, both needed:
 * <ul>
 *   <li>{@code config/splash.properties: enabled=false} — read by
 *       {@code SplashProgress.start()} during {@code Minecraft.startGame()}. Must be written before
 *       that, i.e. from the coremod's {@code injectData}.</li>
 *   <li>reflective {@code SplashProgress.enabled=false, done=true} — stops an already-running splash
 *       thread in case the properties file still said {@code enabled=true}.</li>
 * </ul>
 */
public final class SplashConfig {

    private SplashConfig() {
    }

    /**
     * Ensures {@code <gameDir>/config/splash.properties} exists and has {@code enabled=false}.
     *
     * <p>Creating the file when missing matters: Forge's {@code SplashProgress.start()} writes a
     * default file with {@code enabled=true} if it is absent, and then
     * {@code drawVanillaScreen()} would never call {@code Minecraft.loadScreen()} — so our first
     * frame would never be drawn. Must run before {@code SplashProgress.start()}.
     */
    public static boolean disableForgeSplash(File gameDir) {
        File file = new File(new File(gameDir, "config"), "splash.properties");

        Properties props = new Properties();
        if (file.isFile()) {
            InputStream in = null;
            try {
                in = new FileInputStream(file);
                props.load(in);
            } catch (IOException e) {
                return false;
            } finally {
                closeQuietly(in);
            }
        }

        if ("false".equalsIgnoreCase(props.getProperty("enabled", "true"))) {
            return false; // already off, nothing to do
        }
        props.setProperty("enabled", "false");

        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            return false;
        }

        OutputStream out = null;
        try {
            out = new FileOutputStream(file);
            props.store(out, "disabled by TFRU Loading Screen");
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            closeQuietly(out);
        }
    }

    /** Reflectively stops Forge's splash thread if it is already running. */
    public static void forceDisableForgeSplash() {
        try {
            Class<?> splash = Class.forName("cpw.mods.fml.client.SplashProgress");
            setStatic(splash, "enabled", Boolean.FALSE);
            setStatic(splash, "done", Boolean.TRUE);
        } catch (Throwable ignored) {
            // SplashProgress is optional/renamed: the scene still draws either way.
        }
    }

    private static void setStatic(Class<?> owner, String name, Object value) {
        try {
            Field f = owner.getDeclaredField(name);
            f.setAccessible(true);
            f.set(null, value);
        } catch (Throwable ignored) {
            // field missing on this Forge build
        }
    }

    private static void closeQuietly(Object closeable) {
        try {
            if (closeable instanceof InputStream) ((InputStream) closeable).close();
            else if (closeable instanceof OutputStream) ((OutputStream) closeable).close();
        } catch (IOException ignored) {
            // nothing useful to do
        }
    }
}
