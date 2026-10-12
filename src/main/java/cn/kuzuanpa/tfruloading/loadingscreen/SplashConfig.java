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
 * Turns Forge's splash screen off so the scene owns the window: {@code config/splash.properties} for
 * {@code SplashProgress.start()}, plus a reflective fallback for when the file cannot be written or is
 * read too late.
 */
public final class SplashConfig {

    private SplashConfig() {
    }

    /** Must run before {@code SplashProgress.start()}, i.e. from the coremod's injectData(). */
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
            return false;
        }
        props.setProperty("enabled", "false");

        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            System.err.println("[tfruloading] could not create " + parent
                    + "; Forge's splash will have to be forced off reflectively");
            return false;
        }

        OutputStream out = null;
        try {
            out = new FileOutputStream(file);
            props.store(out, "disabled by TFRU Loading Screen");
            return true;
        } catch (IOException e) {
            System.err.println("[tfruloading] could not write " + file + ": " + e
                    + "; Forge's splash will have to be forced off reflectively");
            return false;
        } finally {
            closeQuietly(out);
        }
    }

    public static void forceDisableForgeSplash() {
        try {
            Class<?> splash = Class.forName("cpw.mods.fml.client.SplashProgress");
            boolean enabled = setStatic(splash, "enabled", Boolean.FALSE);
            boolean done = setStatic(splash, "done", Boolean.TRUE);
            if (enabled || done) {
                System.out.println("[tfruloading] Forge's splash had to be forced off reflectively"
                        + " (enabled=" + enabled + ", done=" + done + "); check that"
                        + " config/splash.properties is writable and says enabled=false");
            }
        } catch (Throwable ignored) { }
    }

    private static boolean setStatic(Class<?> owner, String name, Object value) {
        try {
            Field f = owner.getDeclaredField(name);
            f.setAccessible(true);
            if (value.equals(f.get(null))) return false;
            f.set(null, value);
            return true;
        } catch (Throwable ignored) {

            return false;
        }
    }

    private static void closeQuietly(Object closeable) {
        try {
            if (closeable instanceof InputStream) ((InputStream) closeable).close();
            else if (closeable instanceof OutputStream) ((OutputStream) closeable).close();
        } catch (IOException ignored) { }
    }
}
