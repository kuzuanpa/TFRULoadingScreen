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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** The tip deck for this run: the configured tips file, shuffled once at start-up. */
final class Tips {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private static final int SEQUENCE = 4096;

    private static final long SEED = 0x7F2A9C31L;

    private static final String[] NONE = new String[0];

    private final boolean enabled;
    private final String colorRef;
    private final float delay;
    private final float change;
    private final String[] sequence;

    private Tips(boolean enabled, String colorRef, float delay, float change, String[] sequence) {
        this.enabled = enabled;
        this.colorRef = colorRef;
        this.delay = delay;
        this.change = change;
        this.sequence = sequence;
    }

    static Tips none(HudConfig hud) {
        return new Tips(false, hud.tipsColor(), hud.tipsDelay(), hud.tipsChange(), NONE);
    }

    static Tips load(File gameDir, HudConfig hud) {
        if (!hud.tipsEnabled()) return none(hud);
        String name = hud.tipsFileName();
        File file = HudConfig.tipsFileFor(gameDir, name);
        List<String> lines = read(file);
        if (lines.isEmpty() && !file.isFile()) {

            writeDefault(file);
            lines = read(file);
        }
        if (lines.isEmpty()) {
            System.err.println("[tfruloading] no usable tips in " + file + "; the tips line stays empty");
            return none(hud);
        }
        return new Tips(true, hud.tipsColor(), hud.tipsDelay(), hud.tipsChange(), shuffle(lines));
    }

    String colorRef() {
        return colorRef;
    }

    String at(float elapsedSeconds) {
        if (!enabled || sequence.length == 0) return null;
        if (elapsedSeconds < delay) return null;
        int index = (int) ((elapsedSeconds - delay) / change);
        if (index < 0) index = 0;
        return sequence[index % sequence.length];
    }

    private static List<String> read(File file) {
        List<String> lines = new ArrayList<String>();
        if (file == null || !file.isFile()) return lines;
        InputStream in = null;
        try {
            in = new FileInputStream(file);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, UTF8));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.length() == 0 || line.charAt(0) == '#') continue;
                lines.add(line);
            }
        } catch (IOException e) {
            System.err.println("[tfruloading] could not read the tips file " + file + ": " + e);
        } finally {
            closeQuietly(in);
        }
        return lines;
    }

    private static void writeDefault(File file) {
        String text = HudConfig.bundledTips();
        if (text == null) return;
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return;
        OutputStream out = null;
        try {
            out = new FileOutputStream(file);
            out.write(text.getBytes(UTF8));
            System.out.println("[tfruloading] wrote the default tips to " + file);
        } catch (IOException e) {
            System.err.println("[tfruloading] could not write " + file + ": " + e);
        } finally {
            closeQuietly(out);
        }
    }

    private static String[] shuffle(List<String> lines) {
        int n = lines.size();
        String[] source = lines.toArray(new String[n]);
        String[] out = new String[SEQUENCE];
        Random rng = new Random(SEED);
        int at = 0;
        while (at < SEQUENCE) {
            if (n > 1) {
                for (int i = n - 1; i > 0; i--) {
                    int j = rng.nextInt(i + 1);
                    String tmp = source[i];
                    source[i] = source[j];
                    source[j] = tmp;
                }
                if (at > 0 && n > 1 && out[at - 1].equals(source[0])) {
                    String tmp = source[0];
                    source[0] = source[1];
                    source[1] = tmp;
                }
            }
            for (int i = 0; i < n && at < SEQUENCE; i++) out[at++] = source[i];
        }
        return out;
    }

    private static void closeQuietly(Object closeable) {
        try {
            if (closeable instanceof InputStream) ((InputStream) closeable).close();
            else if (closeable instanceof OutputStream) ((OutputStream) closeable).close();
        } catch (IOException ignored) { }
    }
}
