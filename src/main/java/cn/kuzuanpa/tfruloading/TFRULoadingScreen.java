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

package cn.kuzuanpa.tfruloading;

import cn.kuzuanpa.tfruloading.loadingscreen.LoadingProgress;
import cn.kuzuanpa.tfruloading.loadingscreen.LoadingScreenBridge;
import cn.kuzuanpa.tfruloading.loadingscreen.ScreenOpenWatcher;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLConstructionEvent;

/**
 * TFRU Loading Screen.
 *
 * <p>Scenes are tiny declarative vector files. This mod replaces the vanilla/Forge loading screen with
 * one of those scenes via a coremod transformer
 * ({@code coremod/TFRULoadingPlugin}), and drives the progress bar from FML's mod lifecycle.
 *
 * <p>The scene, the HUD config and the tips are read once from {@code config/loadingscreen/} when the
 * screen starts (see {@code LoadingScreenManager}).
 */
@Mod(modid = TFRULoadingScreen.MOD_ID, name = TFRULoadingScreen.MOD_NAME,
        version = TFRULoadingScreen.VERSION, acceptedMinecraftVersions = "1.7.10")
public final class TFRULoadingScreen {

    public static final String MOD_ID = "tfruloading";
    public static final String MOD_NAME = "TFRU Loading Screen";
    public static final String VERSION = "1.0.0";

    @Mod.EventHandler
    public void construct(FMLConstructionEvent event) {
        // Register on every mod's event bus before preInit starts firing.
        LoadingProgress.attach();
        // The coremod already started the screen from Minecraft.startGame(); this is the fallback for
        // a client where that hook could not be installed, plus the moment we start watching for the
        // main menu (which ends the loading screen).
        LoadingScreenBridge.startScreen();
        try {
            ScreenOpenWatcher.register();
        } catch (Throwable t) {
            // client-only class (GuiOpenEvent); a dedicated server must not care
            System.err.println("[tfruloading] could not watch for GUI opens: " + t);
        }
    }
}
