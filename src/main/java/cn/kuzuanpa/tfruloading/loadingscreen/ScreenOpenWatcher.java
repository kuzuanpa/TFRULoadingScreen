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

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.MinecraftForge;

/**
 * Ends the loading screen when the game shows a screen. The handler runs from inside
 * {@code Minecraft.displayGuiScreen}, before {@code currentScreen} is updated, so it must not resize
 * the game - see {@link LoadingScreenManager#handBackDisplay()}.
 */
public class ScreenOpenWatcher {

    public static void register() {
        try {
            ScreenOpenWatcher watcher = new ScreenOpenWatcher();
            MinecraftForge.EVENT_BUS.register(watcher);
            FMLCommonHandler.instance().bus().register(watcher);
        } catch (Throwable t) {
            System.err.println("[tfruloading] could not watch for GUI opens: " + t);
        }
    }

    /** Non-null gui only: a screen being closed is not the end of the loading screen. */
    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (event == null || event.gui == null) return;
        LoadingScreenBridge.onScreenOpened();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        LoadingScreenManager.applyPendingResize();
    }
}
