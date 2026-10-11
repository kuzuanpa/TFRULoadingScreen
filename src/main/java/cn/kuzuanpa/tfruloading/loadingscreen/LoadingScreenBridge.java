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

/**
 * Static entry point injected into {@code Minecraft.loadScreen()} by the coremod transformer.
 *
 * <p>Lives outside the coremod package on purpose: everything here must be safe to run while
 * Minecraft is still booting, so it swallows every error. If anything fails the player just gets a
 * black window until the main menu, never a crash.
 *
 * <p>{@link #startScreen()} is injected into {@code Minecraft.startGame()}, right before FML starts
 * loading mods (the anchor BetterLoadingScreen uses). That is what keeps the black window short:
 * the first frame used to wait for the whole mod list to be constructed and pre-initialized, which
 * is 13 seconds on the real TFRU pack. {@link #pumpFrame()} is injected into FML's
 * {@code ProgressManager$ProgressBar.step}, which is the only main-thread heartbeat available while
 * a mod is busy loading.
 */
public final class LoadingScreenBridge {

    private LoadingScreenBridge() {
    }

    /**
     * Starts the loading screen as early as the display exists. Called from the coremod's
     * {@code startGame} hook and again from the mod's construction event; it is idempotent.
     */
    public static void startScreen() {
        try {
            if (LoadingScreenManager.isAbandoned()) return;
            LoadingScreenManager.begin();
            FramePump.start();
        } catch (Throwable t) {
            System.err.println("[tfruloading] could not start the loading screen: " + t);
        }
    }

    /**
     * One frame opportunity from the game thread. Injected into FML's progress-bar step, which fires
     * thousands of times during a load - far more often than the mod lifecycle events.
     */
    public static void pumpFrame() {
        try {
            LoadingScreenManager.pumpFrame();
        } catch (Throwable t) {
            System.err.println("[tfruloading] could not draw a loading frame: " + t);
        }
    }

    /** Called from the replaced {@code Minecraft.loadScreen()}. */
    public static void renderFirstFrame() {
        try {
            SplashConfig.forceDisableForgeSplash();
            LoadingScreenManager.begin();
            LoadingScreenManager.renderFrame(false);
        } catch (Throwable t) {
            System.err.println("[tfruloading] first loading frame failed: " + t);
        }
    }

    /**
     * Called from {@link ScreenOpenWatcher} the moment the game is about to show a screen. That is
     * the end of the loading screen, so it stops drawing (and restores the GL state it touched).
     */
    public static void onScreenOpened() {
        try {
            LoadingScreenManager.abandon();
        } catch (Throwable t) {
            System.err.println("[tfruloading] could not hand the display back: " + t);
        }
    }
}
