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
 * The three entry points the coremod injects into Minecraft: {@code startScreen()} into
 * {@code startGame()}, {@code pumpFrame()} into FML's {@code ProgressBar.step(String)} and
 * {@code renderFirstFrame()} into the replaced {@code loadScreen()}. All are best-effort: they swallow
 * everything, because the worst acceptable outcome is the vanilla loading screen.
 */
public final class LoadingScreenBridge {

    private LoadingScreenBridge() {
    }

    /** Called from startGame() and again from the mod's construction event; idempotent. */
    public static void startScreen() {
        try {
            if (LoadingScreenManager.isHandedBack()) return;
            LoadingScreenManager.begin();
            FramePump.start();
        } catch (Throwable t) {
            System.err.println("[tfruloading] could not start the loading screen: " + t);
        }
    }

    public static void pumpFrame() {
        try {
            LoadingScreenManager.pumpFrame();
        } catch (Throwable t) {
            System.err.println("[tfruloading] could not draw a loading frame: " + t);
        }
    }

    public static void renderFirstFrame() {
        try {
            SplashConfig.forceDisableForgeSplash();
            LoadingScreenManager.begin();
            LoadingScreenManager.renderFrame(false);
        } catch (Throwable t) {
            System.err.println("[tfruloading] first loading frame failed: " + t);
        }
    }

    public static void onScreenOpened() {
        try {
            LoadingScreenManager.handBackDisplay();
        } catch (Throwable t) {
            System.err.println("[tfruloading] could not hand the display back: " + t);
        }
    }
}
