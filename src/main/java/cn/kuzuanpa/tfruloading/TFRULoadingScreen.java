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

/** Regular @Mod side: attaches progress reporting, starts the screen and watches for GUI opens. */
@Mod(modid = TFRULoadingScreen.MOD_ID, name = TFRULoadingScreen.MOD_NAME,
        version = TFRULoadingScreen.VERSION, acceptedMinecraftVersions = "1.7.10")
public final class TFRULoadingScreen {

    public static final String MOD_ID = "tfruloading";
    public static final String MOD_NAME = "TFRU Loading Screen";
    public static final String VERSION = "1.0.0";

    @Mod.EventHandler
    public void construct(FMLConstructionEvent event) {
        LoadingProgress.attach();
        LoadingScreenBridge.startScreen();
        try {
            ScreenOpenWatcher.register();
        } catch (Throwable t) {
            System.err.println("[tfruloading] could not watch for GUI opens: " + t);
        }
    }
}
