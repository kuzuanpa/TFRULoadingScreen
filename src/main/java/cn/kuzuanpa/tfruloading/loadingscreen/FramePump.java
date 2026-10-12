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

import java.lang.reflect.Field;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * A second thread that keeps the loading screen animating while the game thread loads mods.
 *
 * <p>It draws into its own shared GL context with its own font, and never touches the game thread's
 * context, texture manager or font. Two rules it must keep: it may never be the first thread to load
 * a class (see {@link LoadingScreenManager#begin()}), and it may never take the display over from the
 * game thread - hence a shared context rather than Forge's context hand-over.
 */
public final class FramePump {

    private static final int TARGET_FPS = 60;
    private static final long FRAME_NANOS = 1000000000L / TARGET_FPS;
    private static final long STOP_TIMEOUT_MS = 3000L;

    private static final Object LOCK = new Object();

    private static volatile boolean running;
    private static volatile boolean retired;
    private static Thread thread;
    private static boolean splashMutexTried;
    private static Semaphore splashMutex;
    private static long frames;
    private static long startedNanos;
    private static boolean reportedDrawFailure;
    private static boolean reportedSwapFailure;

    private FramePump() {
    }

    public static boolean isRunning() {
        return running;
    }

    /**
     * Starts the pump on the game thread; {@code -Dtfruloading.pump=false} or a failed GL context
     * leaves it off and the game thread draws instead.
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
            if (!PumpFont.isPrepared()) {
                retired = true;
                System.err.println("[tfruloading] the pump font was not prepared on the game thread,"
                        + " so starting the pump would make it the first thread to load FontRenderer"
                        + " (and with it the pack's whole transformer chain); leaving the pump off."
                        + " Frames will come from mod lifecycle events and progress-bar steps only");
                return false;
            }

            final SharedDrawable ownedDrawable;
            try {
                ownedDrawable = new SharedDrawable(Display.getDrawable());
            } catch (Throwable t) {
                System.err.println("[tfruloading] no second GL context (" + t
                        + "); frames will come from the game thread only");
                return false;
            }

            running = true;
            frames = 0L;
            startedNanos = System.nanoTime();
            thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    pump(ownedDrawable);
                }
            }, "TFRU loading screen");
            thread.setDaemon(true);
            thread.start();

            System.out.println("[tfruloading] frame pump started (own context on the pump thread, "
                    + TARGET_FPS + " fps target)");
            return true;
        }
    }

    /**
     * Stops the pump. The thread owns its drawable and releases its context by itself, so a thread
     * still stuck in a frame is only reported, never yanked.
     */
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
            if (pumpThread.isAlive()) {
                System.err.println("[tfruloading] the frame pump thread is still inside a frame after "
                        + STOP_TIMEOUT_MS + "ms; it will release its own GL context when it finishes");
            }
        }

        if (startedNanos != 0L) {
            double seconds = (System.nanoTime() - startedNanos) / 1.0e9;
            long fps = seconds > 0.001 ? Math.round(frames / seconds) : 0L;
            System.out.println("[tfruloading] frame pump stopped after " + frames + " frames / "
                    + Math.round(seconds) + "s (~" + fps + " fps)");
        }
    }

    private static void pump(SharedDrawable owned) {
        try {
            owned.makeCurrent();
        } catch (Throwable t) {
            running = false;
            System.err.println("[tfruloading] frame pump could not take its context: " + t
                    + "; frames will come from the game thread only");
            return;
        }

        try {
            while (running) {
                long began = System.nanoTime();

                // Drain the window's event queue here, then swap without processing messages again
                // (see swapBuffers): it keeps the window responsive and picks a resize up this frame.
                try {
                    Display.processMessages();
                } catch (Throwable t) {
                    reportSwapFailure(t);
                }

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
                    swapBuffers();
                } catch (Throwable t) {
                    reportSwapFailure(t);
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
                owned.releaseContext();
            } catch (Throwable ignored) {
            }
        }
    }

    private static void swapBuffers() throws InterruptedException {
        Semaphore mutex = splashMutex();
        if (mutex == null) {
            Display.update(false);
            return;
        }

        while (running && !mutex.tryAcquire(200L, TimeUnit.MILLISECONDS)) { }
        if (!running) return;
        try {
            Display.update(false);
        } finally {
            mutex.release();
        }
    }

    private static synchronized Semaphore splashMutex() {
        if (splashMutexTried) return splashMutex;
        splashMutexTried = true;
        try {
            Field field = Class.forName("cpw.mods.fml.client.SplashProgress").getDeclaredField("mutex");
            field.setAccessible(true);
            splashMutex = (Semaphore) field.get(null);
        } catch (Throwable t) {
            splashMutex = null;
            System.out.println("[tfruloading] Forge's splash display mutex is not reachable (" + t
                    + "); the frame pump will swap without it");
        }
        return splashMutex;
    }

    private static void reportSwapFailure(Throwable t) {
        if (reportedSwapFailure) return;
        reportedSwapFailure = true;
        System.err.println("[tfruloading] frame pump could not update the window: " + t);
    }
}
