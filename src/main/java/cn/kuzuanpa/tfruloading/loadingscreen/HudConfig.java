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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Map;

/**
 * The loading screen's HUD config: the top-left labels, the bottom progress bar and the tips line.
 *
 * <p>This is deliberately <em>not</em> part of a scene. A scene is the film; the HUD is the chrome
 * that sits on top of whatever film is playing, and a modpack author wants to rebrand the loading
 * screen without re-authoring 68 seconds of vector art. So the HUD lives in its own small JSON file:
 *
 * <pre>&lt;gameDir&gt;/config/loadingscreen/loading_screen.json</pre>
 *
 * <p>The file is read <em>once</em>, when the screen initializes (see
 * {@link LoadingScreenManager#begin()}), and the parsed config is then held in memory for the rest of
 * the run: nothing re-reads it, so an edit shows up on the next launch. When the file is missing it is
 * written out from the copy bundled in the jar, so a pack author always has something to edit.
 *
 * <p>Every field is optional: a missing file, a broken file, or a missing key all fall back to the
 * defaults below and never take the loading screen down.
 *
 * <p>Pure Java (no Minecraft imports), like {@link SceneIO}, so the parsing path is the same
 * off-line and in game.
 */
public final class HudConfig {

    /** Directory under the game dir that holds the HUD config, the tips and the scenes. */
    public static final String CONFIG_DIR = "config/loadingscreen";

    /** Name of the HUD file inside {@link #CONFIG_DIR}. */
    public static final String FILE_NAME = "loading_screen.json";

    private static final String BUNDLED_CONFIG = "/assets/tfruloading/loading_screen.json";
    private static final String BUNDLED_TIPS = "/assets/tfruloading/tips.txt";

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final Gson GSON = new Gson();

    // --- defaults (the bundled loading_screen.json carries the same values) --------------------

    static final String DEFAULT_STATUS_TEXT = "{status}";
    static final String DEFAULT_PERCENT_TEXT = "{percent}%";
    static final String DEFAULT_PROGRESS_TEXT_COLOR = "#c8d8e6";
    static final String DEFAULT_PROGRESS_BORDER_COLOR = "#0f171e";
    static final String DEFAULT_PROGRESS_TRACK_COLOR = "#1a2633";
    static final String DEFAULT_PROGRESS_FILL_COLOR = "#ff993b";
    static final String DEFAULT_TIPS_FILE = "tips.txt";
    static final String DEFAULT_TIPS_COLOR = "#aab6cc";
    static final float DEFAULT_TIPS_DELAY = 3f;
    static final float DEFAULT_TIPS_CHANGE = 12f;

    /** Scene text node id -> replacement text and/or colour. Missing ids keep the scene's own text. */
    public Map<String, Label> labels;

    public Progress progress;

    public Tips tips;

    /** One top-left label override: either field may be left out. */
    public static class Label {
        public String text;
        public String color;
    }

    /** Bottom progress bar: the two text templates and the four colours. */
    public static class Progress {
        /** Template for the left-hand line. {@code {status}} -> the loading status, {@code {percent}}. */
        public String statusText;
        /** Template for the right-hand line. */
        public String percentText;
        public String textColor;
        public String borderColor;
        public String trackColor;
        public String fillColor;
    }

    /** The tips line (BetterLoadingScreen's tips.txt, ported). */
    public static class Tips {
        public Boolean enabled;
        /** Path to the tips file; relative paths resolve against {@code config/loadingscreen/}. */
        public String file;
        public String color;
        /** Seconds of loading before the first tip appears. */
        public Float delaySeconds;
        /** Seconds each tip stays on screen. */
        public Float changeSeconds;
    }

    // --- resolved values ----------------------------------------------------------------------

    public Label label(String nodeId) {
        return labels == null || nodeId == null ? null : labels.get(nodeId);
    }

    public String statusText() {
        return text(progress == null ? null : progress.statusText, DEFAULT_STATUS_TEXT);
    }

    public String percentText() {
        return text(progress == null ? null : progress.percentText, DEFAULT_PERCENT_TEXT);
    }

    public String progressTextColor() {
        return text(progress == null ? null : progress.textColor, DEFAULT_PROGRESS_TEXT_COLOR);
    }

    public String progressBorderColor() {
        return text(progress == null ? null : progress.borderColor, DEFAULT_PROGRESS_BORDER_COLOR);
    }

    public String progressTrackColor() {
        return text(progress == null ? null : progress.trackColor, DEFAULT_PROGRESS_TRACK_COLOR);
    }

    public String progressFillColor() {
        return text(progress == null ? null : progress.fillColor, DEFAULT_PROGRESS_FILL_COLOR);
    }

    public boolean tipsEnabled() {
        return tips == null || tips.enabled == null || tips.enabled.booleanValue();
    }

    public String tipsColor() {
        return text(tips == null ? null : tips.color, DEFAULT_TIPS_COLOR);
    }

    public String tipsFileName() {
        return text(tips == null ? null : tips.file, DEFAULT_TIPS_FILE);
    }

    public float tipsDelay() {
        return positive(tips == null ? null : tips.delaySeconds, DEFAULT_TIPS_DELAY);
    }

    public float tipsChange() {
        Float v = tips == null ? null : tips.changeSeconds;
        float f = v == null ? DEFAULT_TIPS_CHANGE : v.floatValue();
        return f > 0f ? f : DEFAULT_TIPS_CHANGE;
    }

    private static String text(String value, String fallback) {
        return value == null || value.length() == 0 ? fallback : value;
    }

    private static float positive(Float value, float fallback) {
        return value == null || value.floatValue() < 0f ? fallback : value.floatValue();
    }

    // --- loading ------------------------------------------------------------------------------

    /**
     * Reads the HUD config for a game directory. Called once, at initialization.
     *
     * <p>When {@code config/loadingscreen/loading_screen.json} is absent it is written out from the
     * bundled default, so an author can open it and start editing. Anything that goes wrong here
     * falls back to the built-in defaults; the loading screen never fails over its HUD.
     */
    public static HudConfig load(File gameDir) {
        File file = fileFor(gameDir);
        try {
            if (!file.isFile()) writeDefault(file);
            if (file.isFile()) {
                InputStream in = new FileInputStream(file);
                try {
                    Reader reader = new InputStreamReader(in, UTF8);
                    HudConfig parsed = GSON.fromJson(reader, HudConfig.class);
                    if (parsed != null) return parsed;
                } finally {
                    closeQuietly(in);
                }
            }
        } catch (Throwable t) {
            System.err.println("[tfruloading] could not read " + file + " (" + t
                    + "); using the built-in HUD defaults");
        }
        return new HudConfig();
    }

    /** {@code <gameDir>/config/loadingscreen/loading_screen.json}. */
    public static File fileFor(File gameDir) {
        return new File(new File(gameDir, CONFIG_DIR), FILE_NAME);
    }

    /** {@code <gameDir>/config/loadingscreen/scenes}: where the scene files are read from. */
    public static File scenesDirFor(File gameDir) {
        return new File(new File(gameDir, CONFIG_DIR), "scenes");
    }

    /** {@code <gameDir>/config/loadingscreen/<tips.file>}, or the path itself when absolute. */
    public static File tipsFileFor(File gameDir, String name) {
        File raw = new File(name);
        return raw.isAbsolute() ? raw : new File(new File(gameDir, CONFIG_DIR), name);
    }

    /** Reads a text resource bundled in the jar, or null. */
    static String readBundled(String path) {
        InputStream in = HudConfig.class.getResourceAsStream(path);
        if (in == null) return null;
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int read;
            while ((read = in.read(buf)) > 0) out.write(buf, 0, read);
            return new String(out.toByteArray(), UTF8);
        } catch (IOException e) {
            return null;
        } finally {
            closeQuietly(in);
        }
    }

    /** The default tips file, bundled for the first run. */
    static String bundledTips() {
        return readBundled(BUNDLED_TIPS);
    }

    private static void writeDefault(File file) {
        String text = readBundled(BUNDLED_CONFIG);
        if (text == null) return; // nothing bundled: the built-in defaults will be used
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return;
        OutputStream out = null;
        try {
            out = new FileOutputStream(file);
            out.write(text.getBytes(UTF8));
            System.out.println("[tfruloading] wrote the default HUD config to " + file);
        } catch (IOException e) {
            System.err.println("[tfruloading] could not write " + file + ": " + e);
        } finally {
            closeQuietly(out);
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
