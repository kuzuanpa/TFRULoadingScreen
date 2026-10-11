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

import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.SharedDrawable;

/**
 * Continuous frame pump: a second thread that draws the loading screen while the game thread is busy
 * loading mods, so the loop frames (fire, steam, anvil, spark) keep animating instead of stepping
 * along with the loading progress.
 *
 * <h3>Why the first attempt at this failed, and what is different now</h3>
 *
 * <p>The first pump copied Forge's {@code SplashProgress} literally: it made a shared context current
 * on the game thread and handed the <em>display</em> context to the pump thread. On this pack that
 * produced "Tried to get the Tessellator off the main thread when not capturing!" (GTNHLib redirects
 * the Tessellator to a thread-bound manager), drew zero frames in two minutes, and left the game's
 * texture state broken - the game had been uploading its textures and fonts while its GL context was
 * the shared one, so after the switch back the display context's state and the game's own caches no
 * longer agreed.
 *
 * <p>This version never touches the game thread's context at all:
 *
 * <ul>
 *   <li>the game thread keeps the display context for the whole load, exactly as vanilla;</li>
 *   <li>the pump thread makes its own shared context current and draws into it - objects (textures)
 *       are shared with the display context, state is not;</li>
 *   <li>the pump draws with immediate mode ({@code glBegin}/{@code glVertex2f}) and with its own font
 *       texture, so it never touches the Tessellator, the game's {@code TextureManager} or its
 *       {@code FontRenderer} - the three things that made the first attempt explosive;</li>
 *   <li>it swaps with {@code Display.update()}, which in LWJGL 2.9.1 swaps the <em>window</em>
 *       ({@code DrawableGL.swapBuffers()} → {@code glXSwapBuffers}) and does not care which context is
 *       current on which thread;</li>
 *   <li>stopping it is just releasing its own context - there is no handover, so nothing can be left
 *       half-switched for the game.</li>
 * </ul>
 *
 * <p>Everything degrades: no display, no {@code SharedDrawable}, or {@code -Dtfruloading.pump=false}
 * and the mod keeps rendering from the game thread on every lifecycle event and progress-bar step.
 */
public final class FramePump {

    /** Loop frames read well at 60 fps; the renderer is immediate mode and cheap. */
    private static final int TARGET_FPS = 60;
    private static final long FRAME_NANOS = 1000000000L / TARGET_FPS;
    private static final long STOP_TIMEOUT_MS = 3000L;

    private static final Object LOCK = new Object();

    private static volatile boolean running;
    private static volatile boolean retired;
    private static Thread thread;
    private static SharedDrawable drawable;
    private static long frames;
    private static long startedNanos;
    private static int startWidth;
    private static int startHeight;
    private static boolean reportedDrawFailure;
    private static boolean reportedSwapFailure;

    private FramePump() {
    }

    public static boolean isRunning() {
        return running;
    }

    /**
     * Starts the pump on the game thread. Idempotent, and safe to call before the game has drawn
     * anything: the pump only ever asks for its own context.
     */
    public static boolean start() {
        synchronized (LOCK) {
            if (running || retired) return running;

            if ("false".equalsIgnoreCase(System.getProperty("tfruloading.pump", "true"))) {
                retired = true;
                System.out.println("[tfruloading] frame pump disabled by -Dtfruloading.pump=false;"
                        + " frames will come from mod lifecycle events and progress-bar steps only");
                return false;
            }
            if (!Display.isCreated()) return false;

            try {
                // Created on the game thread (that is where the display lives) but never made current
                // here: the pump thread makes it current for itself.
                drawable = new SharedDrawable(Display.getDrawable());
            } catch (Throwable t) {
                drawable = null;
                System.err.println("[tfruloading] no second GL context (" + t
                        + "); frames will come from the game thread only");
                return false;
            }

            running = true;
            frames = 0L;
            startedNanos = System.nanoTime();
            startWidth = Display.getWidth();
            startHeight = Display.getHeight();
            thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    pump();
                }
            }, "TFRU loading screen");
            thread.setDaemon(true);
            thread.start();

            System.out.println("[tfruloading] frame pump started (own context on the pump thread, "
                    + TARGET_FPS + " fps target)");
            return true;
        }
    }

    /** Stops the pump and lets it release its own context. Call from the game thread. */
    public static void stop() {
        Thread pumpThread;
        synchronized (LOCK) {
            if (retired) return;
            retired = true;
            running = false;
            pumpThread = thread;
            thread = null;
        }

        if (pumpThread != null && pumpThread != Thread.currentThread()) {
            try {
                pumpThread.join(STOP_TIMEOUT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        synchronized (LOCK) {
            drawable = null;
        }

        if (startedNanos != 0L) {
            double seconds = (System.nanoTime() - startedNanos) / 1.0e9;
            long fps = seconds > 0.001 ? Math.round(frames / seconds) : 0L;
            System.out.println("[tfruloading] frame pump stopped after " + frames + " frames / "
                    + Math.round(seconds) + "s (~" + fps + " fps)");
        }
    }

    /**
     * True when the window is a different size than it was when the pump took over.
     *
     * <p>The game thread cannot answer this itself: while the pump runs it is the pump that consumes
     * the window's resize events (through {@code Display.update()}), so {@code Minecraft}'s own size
     * fields and its off-screen framebuffer would silently stay at the old size. The game has to be
     * realigned at handover - see {@code LoadingScreenManager.abandon()}.
     */
    public static boolean windowResizedSinceStart() {
        return startWidth > 0 && Display.isCreated()
                && (Display.getWidth() != startWidth || Display.getHeight() != startHeight);
    }

    /** The pump thread: take our own context, draw until told to stop, release it again. */
    private static void pump() {
        try {
            drawable.makeCurrent();
        } catch (Throwable t) {
            running = false;
            System.err.println("[tfruloading] frame pump could not take its context: " + t
                    + "; frames will come from the game thread only");
            return;
        }

        try {
            while (running) {
                long began = System.nanoTime();

                try {
                    LoadingScreenManager.renderFrame(true);
                    frames++;
                } catch (Throwable t) {
                    if (!reportedDrawFailure) {
                        reportedDrawFailure = true;
                        System.err.println("[tfruloading] frame pump could not draw, keeping the last"
                                + " frame: " + t);
                    }
                }

                try {
                    Display.update();
                } catch (Throwable t) {
                    if (!reportedSwapFailure) {
                        reportedSwapFailure = true;
                        System.err.println("[tfruloading] frame pump could not swap buffers: " + t);
                    }
                }

                long rest = FRAME_NANOS - (System.nanoTime() - began);
                if (rest > 0L) {
                    try {
                        Thread.sleep(rest / 1000000L, (int) (rest % 1000000L));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } finally {
            try {
                drawable.releaseContext();
            } catch (Throwable ignored) {
                // detaching our own context cannot hurt the game's context
            }
        }
    }
}
