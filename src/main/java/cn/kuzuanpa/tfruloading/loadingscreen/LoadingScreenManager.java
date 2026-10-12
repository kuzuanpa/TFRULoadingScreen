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
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.Display;

import java.io.File;

/**
 * Owns the loading screen: scene and progress state, and the frame source that draws it.
 * The pump thread draws when it is up (see {@link FramePump}); otherwise the game thread does, and
 * then only from here - never from inside another mod's loop, and never with the game's own font.
 */
public final class LoadingScreenManager {

    private static final SceneRenderer RENDERER = new SceneRenderer();
    private static final String DEFAULT_SCENE = "tfru_intro";

    private static final int MAX_FPS = 20;
    private static final long FRAME_INTERVAL_NANOS = 1000000000L / MAX_FPS;

    private static final long TELEMETRY_NANOS = 2000000000L;

    private static Scene scene;
    private static Director director;
    private static HudConfig hud = new HudConfig();
    private static Tips tips;

    private static volatile String status = "Minecraft Initializing";
    private static volatile float percent;
    private static volatile boolean finished;
    /** Set once the window has been handed back; makes {@link #handBackDisplay()} a one-shot. */
    private static volatile boolean handedBack;
    /** Set by {@link #handBackDisplay()}, consumed by {@link #applyPendingResize()}. */
    private static volatile boolean needsResize;
    private static boolean started;
    /** The thread Minecraft ticks on: the only thread allowed to draw outside the pump. */
    private static volatile Thread gameThread;

    private static long tickNanos = System.nanoTime();
    private static long screenStartNanos = System.nanoTime();
    private static long telemetryNanos = System.nanoTime();
    private static long lastFrameNanos;
    private static long telemetryFrames;
    /** Window size the screen took over with, to realign the game at hand-over. */
    private static int startDisplayWidth;
    private static int startDisplayHeight;

    private LoadingScreenManager() {
    }

    /**
     * Game thread only, and the earliest hook that has one. Reads the config once, and draws a first
     * frame with the pump's font so that the pump thread is never the first to load a class.
     */
    public static synchronized void begin() {
        if (started) return;
        started = true;
        tickNanos = System.nanoTime();
        screenStartNanos = tickNanos;
        telemetryNanos = tickNanos;

        Minecraft mc = null;
        try {
            mc = Minecraft.getMinecraft();
        } catch (Throwable ignored) {
        }
        if (mc != null) {
            try {
                SplashConfig.disableForgeSplash(mc.mcDataDir);
            } catch (Throwable ignored) {
            }
        }
        loadConfig(mc);

        gameThread = Thread.currentThread();
        startDisplayWidth = Display.getWidth();
        startDisplayHeight = Display.getHeight();
        if (!FramePump.isRunning()) renderFrame(false);
    }

    private static void loadConfig(Minecraft mc) {
        File gameDir = mc != null ? mc.mcDataDir : new File(".");
        try {
            hud = HudConfig.load(gameDir);
        } catch (Throwable t) {
            System.err.println("[tfruloading] could not load the HUD config: " + t);
            hud = new HudConfig();
        }
        try {
            tips = Tips.load(gameDir, hud);
        } catch (Throwable t) {
            System.err.println("[tfruloading] could not load the tips: " + t);
            tips = Tips.none(hud);
        }
        Scene loaded = null;
        try {
            loaded = SceneIO.load(HudConfig.scenesDirFor(gameDir), DEFAULT_SCENE);
        } catch (Throwable t) {
            System.err.println("[tfruloading] could not load the scene " + DEFAULT_SCENE + ": " + t);
        }
        scene = loaded;
        director = new Director(scene);
    }

    public static synchronized void pumpFrame() {
        drawFrameIfDue();
    }

    /** Progress from the mod lifecycle hooks. Monotone: a later mod can report a lower fraction. */
    public static synchronized void setProgress(String text, float fraction) {
        if (finished || handedBack) return;
        status = text == null ? "" : text;
        float next = fraction < 0f ? 0f : fraction > 1f ? 1f : fraction;
        if (next > percent) percent = next;
        drawFrameIfDue();
    }

    /** A frame opportunity from a progress-bar step: game thread only, and rate limited. */
    private static void drawFrameIfDue() {
        if (!started || handedBack) return;
        if (FramePump.isRunning()) return;
        if (Thread.currentThread() != gameThread) return;
        if (System.nanoTime() - lastFrameNanos < FRAME_INTERVAL_NANOS) return;
        renderFrame(false);
    }

    public static synchronized void complete() {
        if (finished) return;
        percent = 1f;
        status = "Finished";
        if (director != null) director.finish();
        finished = true;
        if (!FramePump.isRunning()) renderFrame(false);
    }

    public static synchronized boolean isFinished() {
        return finished;
    }

    /**
     * Stops drawing and gives the window back. Runs inside {@code Minecraft.displayGuiScreen}, so it
     * must call nothing that re-enters the GUI being opened - {@code Minecraft.resize} does exactly
     * that, hence {@link #applyPendingResize()}.
     */
    public static void handBackDisplay() {
        if (handedBack) return;
        handedBack = true;
        FramePump.stop();
        needsResize = startDisplayWidth > 0 && Display.isCreated()
                && (Display.getWidth() != startDisplayWidth || Display.getHeight() != startDisplayHeight);
        try {
            GL11.glClearColor(1f, 1f, 1f, 1f);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.1f);
            GL11.glColor4f(1f, 1f, 1f, 1f);
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glLoadIdentity();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glLoadIdentity();
        } catch (Throwable ignored) {
        }
    }

    /** Client tick: realigns the game's window size where no screen transition is in progress. */
    public static synchronized void applyPendingResize() {
        if (!needsResize) return;
        needsResize = false;
        try {
            Minecraft mc = Minecraft.getMinecraft();
            int width = Display.getWidth();
            int height = Display.getHeight();
            if (mc != null && width > 0 && height > 0) mc.resize(width, height);
        } catch (Throwable t) {
            System.err.println("[tfruloading] could not realign the window size: " + t);
        }
    }

    public static synchronized boolean isHandedBack() {
        return handedBack;
    }

    /**
     * Draws one frame into whichever context is current on this thread: the pump's own when the pump
     * runs, the game's otherwise. Text always goes through {@link PumpFont} - the game's
     * {@code FontRenderer} binds through its {@code TextureManager}, and Forge steps a progress bar
     * from inside that map's iteration, so binding a texture there throws ConcurrentModificationException.
     */
    static synchronized void renderFrame(boolean pumpContext) {
        if (pumpContext != FramePump.isRunning()) return;

        Minecraft mc;
        try {
            mc = Minecraft.getMinecraft();
        } catch (Throwable t) {
            return;
        }
        if (mc == null) return;

        FontRenderer font = null;
        try {
            font = PumpFont.get(mc);
        } catch (Throwable ignored) {
        }

        int fbWidth = mc.displayWidth;
        int fbHeight = mc.displayHeight;
        if (Display.getWidth() > 0 && Display.getHeight() > 0) {
            fbWidth = Display.getWidth();
            fbHeight = Display.getHeight();
            mc.displayWidth = fbWidth;
            mc.displayHeight = fbHeight;
        }
        if (fbWidth <= 0 || fbHeight <= 0) return;

        ScaledResolution res = new ScaledResolution(mc, fbWidth, fbHeight);
        int w = res.getScaledWidth();
        int h = res.getScaledHeight();

        GL11.glViewport(0, 0, fbWidth, fbHeight);

        long now = System.nanoTime();
        float dt = (now - tickNanos) / 1000000000f;
        tickNanos = now;
        if (!(dt > 0f)) dt = 0f;
        lastFrameNanos = now;

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0D, w, h, 0.0D, 1000.0D, 3000.0D);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        GL11.glTranslatef(0f, 0f, -2000f);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1f, 1f, 1f, 1f);

        GL11.glClearColor(0.03f, 0.05f, 0.07f, 1f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        if (scene != null && director != null) {
            director.setProgress(percent);
            director.tick(dt);
            RENDERER.render(scene, director.actualTime(), director.loopTime(), w, h, font, hud);
        }

        String tip = tips == null ? null : tips.at((now - screenStartNanos) / 1000000000f);
        HudRenderer.draw(w, h, font, scene, hud, status, percent, tip);

        if (!pumpContext) {
            // Not Minecraft.updateDisplay(): that calls Minecraft.resize on a resize, re-entering the
            // GUI that is being opened right now.
            try {
                Display.update();
            } catch (Throwable ignored) {
            }
        }

        logTelemetry(now);
    }

    private static void logTelemetry(long now) {
        telemetryFrames++;
        long elapsed = now - telemetryNanos;
        if (elapsed < TELEMETRY_NANOS) return;
        telemetryNanos = now;

        long fps = Math.round(telemetryFrames * 1000000000.0 / Math.max(1L, elapsed));
        telemetryFrames = 0L;

        String actual = director == null ? "-" : String.format(java.util.Locale.ROOT, "%.1f", director.actualTime());
        String allowance = director == null ? "-" : String.format(java.util.Locale.ROOT, "%.1f", director.allowance());
        String verdict = director == null ? "-" : (director.running() ? "PLAY" : "HOLD");
        System.out.println("[tfruloading] " + fps + "fps | actual " + actual + "s | progress "
                + Math.round(percent * 100f) + "% | allowance " + allowance + "s | " + verdict
                + " | " + status);
    }
}
