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
 * Owns the in-game loading screen: which scene is playing, the progress text, and the actual
 * frame draw.
 *
 * <p>Minecraft does not run its render loop while mods load, so the loading screen has to be pushed
 * from somewhere. There are two sources, and both are needed:
 *
 * <ul>
 *   <li>{@link FramePump}: a second thread with its own GL context at 60 fps, so the loop frames
 *       (fire, steam, anvil) keep animating even while a mod spends a minute inside one lifecycle
 *       handler. It owns everything it touches; see that class for why the game thread's context and
 *       caches are never disturbed.</li>
 *   <li>the game thread: FML mod lifecycle events ({@link LoadingProgress}) and every step of FML's
 *       progress bars ({@code ProgressManager$ProgressBar.step}, hooked by the coremod), capped at
 *       {@value #MAX_FPS} fps. This is what runs when the pump is off or unavailable.</li>
 * </ul>
 *
 * <p>The two timelines are driven by {@link Director}: the loop frames follow wall-clock time, the
 * actual frames follow the progress bar's allowance. Both are pure functions of elapsed time plus
 * progress, so a frame that had to wait still shows the correct picture when it finally happens.
 */
public final class LoadingScreenManager {

    private static final SceneRenderer RENDERER = new SceneRenderer();
    private static final String DEFAULT_SCENE = "tfru_intro";

    /**
     * Top frame rate for the game-thread fallback (frames pushed off a progress-bar step). The screen
     * then shares the thread that is loading the game, so this is also an upper bound on what it can
     * cost the load. It only applies when {@link FramePump} is not running.
     */
    private static final int MAX_FPS = 20;
    private static final long FRAME_INTERVAL_NANOS = 1000000000L / MAX_FPS;

    /** Every this many seconds the screen logs what it is showing; useful for the M7 real-client runs. */
    private static final long TELEMETRY_NANOS = 2000000000L;

    /**
     * Everything the screen draws, read once from {@code config/loadingscreen/} by {@link #loadConfig}
     * on the first {@link #begin()} and held here for the rest of the run.
     */
    private static Scene scene;
    private static Director director;
    /** HUD config: the top-left labels, the progress bar colours/text and the tips settings. */
    private static HudConfig hud = new HudConfig();
    /** The tips deck: the shuffled tips.txt lines for this run (empty when tips are off). */
    private static Tips tips;
    private static volatile String status = "Minecraft Initializing";
    private static volatile float percent;
    private static boolean started;
    private static volatile boolean finished;
    private static volatile boolean abandoned;
    private static long tickNanos = System.nanoTime();
    /** Wall clock of {@link #begin()}, used to time the tips rotation. */
    private static long screenStartNanos = System.nanoTime();
    private static long telemetryNanos = System.nanoTime();
    private static long lastFrameNanos;
    private static long telemetryFrames;

    private LoadingScreenManager() {
    }

    /**
     * Reads {@code config/loadingscreen/} and draws the first frame. Idempotent, so the earliest hook
     * to fire wins and the configuration is read exactly once; callable from the earliest hook.
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
            // not far enough into the game yet; the scene still loads from the jar
        }
        if (mc != null) {
            try {
                // Second chance, in case the coremod's early write did not find the file.
                // This has to happen before SplashProgress.start() reads it, which is why it lives
                // here now: begin() runs from the coremod hook in startGame(), not from construction.
                SplashConfig.disableForgeSplash(mc.mcDataDir);
            } catch (Throwable ignored) {
                // non fatal
            }
        }
        loadConfig(mc);

        // Show the first frame straight away on the game thread, so the window stops being black
        // before FML even starts discovering mods (and before the pump thread has spun up).
        if (!FramePump.isRunning()) renderFrame(false);
    }

    /**
     * Reads the scene, the HUD config ({@code config/loadingscreen/loading_screen.json}, written out
     * from the bundled default when it is missing) and the tips deck the HUD points at.
     *
     * <p>This is the screen's one and only read of those files: it runs once, from the first
     * {@link #begin()}, and the results stay in memory for the whole run - there is no reload path, so
     * an edit on disk shows up on the next game launch. Every part falls back to a sane default on
     * failure rather than taking the loading screen down.
     */
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

    /**
     * A frame opportunity: called from every mod lifecycle event and from every FML progress-bar
     * step. Rate limited, so a step-heavy phase cannot turn into a hundred draws per second.
     */
    public static synchronized void pumpFrame() {
        frameOpportunity();
    }

    /** Progress report from the mod lifecycle hooks. */
    public static synchronized void setProgress(String text, float fraction) {
        if (finished || abandoned) return;
        status = text == null ? "" : text;
        percent = fraction < 0f ? 0f : fraction > 1f ? 1f : fraction;
        frameOpportunity();
    }

    /** Draws, unless a frame was drawn within the last {@link #FRAME_INTERVAL_NANOS}. */
    private static void frameOpportunity() {
        if (!started || abandoned) return;
        if (FramePump.isRunning()) return; // the pump owns the window while it is up
        if (System.nanoTime() - lastFrameNanos < FRAME_INTERVAL_NANOS) return;
        renderFrame(false);
    }

    /**
     * Called once loading is complete. Banks the rest of the story so the frames that the tail (the
     * resource reload between load complete and the main menu) still provides can play it out.
     */
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
     * The game is about to show a screen (the main menu, a crash report, a mod's config screen).
     * That is the end of the loading screen: stop drawing and hand the window back, so a progress
     * bar stepped after loading (world loading, a mod's lazy setup) can never paint over the game.
     *
     * <p>Also restores the pieces of GL state the screen touches. Forge's own splash does the same
     * on the way out; it is cheap insurance for the frame the game draws next.
     */
    public static void abandon() {
        abandoned = true;
        FramePump.stop();
        // The pump was the only thing watching the window, so the game may still be sized (and its
        // off-screen framebuffer built) for the window it had at startup. Realign it here, on the game
        // thread with the game's own context: this is exactly what Minecraft does on a resize, and it
        // records the new size before the first frame the game lays out (the main menu).
        if (FramePump.windowResizedSinceStart()) {
            try {
                Minecraft mc = Minecraft.getMinecraft();
                int width = Display.getWidth();
                int height = Display.getHeight();
                if (mc != null && width > 0 && height > 0) mc.resize(width, height);
            } catch (Throwable ignored) {
                // no display / no context: the game will resize itself on its next frame anyway
            }
        }
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
            // no context (yet): nothing to restore either
        }
    }

    public static synchronized boolean isAbandoned() {
        return abandoned;
    }

    /**
     * Draws one frame.
     *
     * <p>{@code pumped} says which GL context the frame belongs to: {@code true} draws into the pump
     * thread's own context with the pump's private font, {@code false} draws into the game's context
     * from the game thread. The two never draw at the same time - a thread that does not own the
     * display is refused.
     */
    static synchronized void renderFrame(boolean pumped) {
        if (pumped != FramePump.isRunning()) return;

        Minecraft mc;
        try {
            mc = Minecraft.getMinecraft();
        } catch (Throwable t) {
            return;
        }
        if (mc == null) return;

        FontRenderer font = null;
        try {
            font = pumped ? PumpFont.get(mc) : mc.fontRenderer;
        } catch (Throwable ignored) {
            // not created yet on the very first frame
        }

        // The pump swaps through Display.update(), which also tracks the window size, and the game
        // thread's own field only follows on a resize it performs - so read the live size instead of
        // trusting the possibly stale Minecraft field while pumping.
        int fbWidth = mc.displayWidth;
        int fbHeight = mc.displayHeight;
        if (pumped && Display.getWidth() > 0 && Display.getHeight() > 0) {
            fbWidth = Display.getWidth();
            fbHeight = Display.getHeight();
            // The pump is the only thing looking at the window while it runs (the game thread is
            // loading mods, not rendering), so keep Minecraft's own size fields current too: the main
            // menu lays itself out from them the moment loading ends. A resize seen by the pump would
            // otherwise be invisible to the game.
            mc.displayWidth = fbWidth;
            mc.displayHeight = fbHeight;
        }
        if (fbWidth <= 0 || fbHeight <= 0) return;

        ScaledResolution res = new ScaledResolution(mc, fbWidth, fbHeight);
        int w = res.getScaledWidth();
        int h = res.getScaledHeight();

        // GL keeps the viewport per context and nobody resizes it for us, so a window drag would
        // otherwise leave the picture drawn into the old rectangle.
        GL11.glViewport(0, 0, fbWidth, fbHeight);

        // The director wants real wall time. dt used to be capped at 0.25s per frame "so a stall
        // plays out over several frames", which silently ate every gap longer than a quarter second
        // and cost the story 82 of the load's 100 seconds (ROADMAP §2.13). Nothing here needs the
        // cap: the story can never run past what the progress bar paid for, so a long step just
        // catches it up to the allowance ceiling, which is exactly the right frame.
        long now = System.nanoTime();
        float dt = (now - tickNanos) / 1000000000f;
        tickNanos = now;
        if (!(dt > 0f)) dt = 0f; // also swallows NaN
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

        // The chrome is config-driven (loading_screen.json) and drawn in screen space, on top of the
        // film: the progress bar and, under it, the tips line.
        String tip = tips == null ? null : tips.at((now - screenStartNanos) / 1000000000f);
        HudRenderer.draw(w, h, font, scene, hud, status, percent, tip);

        if (!pumped) {
            // The pump thread swaps with Display.update() in its own loop; the game thread has to go
            // through Minecraft so a resize is picked up.
            try {
                mc.func_147120_f(); // updateDisplay(): swaps the back buffer, and handles a resize
            } catch (Throwable ignored) {
                // if the display is not ready yet, skip this frame
            }
        }

        logTelemetry(now);
    }

    /** Prints one line every couple of seconds so a real pack can be measured without a profiler. */
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
