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
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.MinecraftForge;

/**
 * Ends the loading screen as soon as the game is about to show a screen.
 *
 * <p>Forge fires {@link GuiOpenEvent} on the game thread from {@code Minecraft.displayGuiScreen},
 * which {@code Minecraft.startGame()} calls with the main menu right before the real render loop
 * starts - the same moment Forge calls {@code SplashProgress.finish()}.
 *
 * <p>Reacting to "any screen opens" (crash reports, a mod's config screen, the main menu) rather than
 * "the main menu opens" means a pack with a custom main menu - or a load that dies - still stops the
 * screen; otherwise a progress bar stepped later (world loading, a mod's lazy setup) could paint the
 * loading screen over the game.
 */
public class ScreenOpenWatcher {

    /** Registers on the Forge event bus; called once from the mod's construction event. */
    public static void register() {
        try {
            MinecraftForge.EVENT_BUS.register(new ScreenOpenWatcher());
        } catch (Throwable t) {
            System.err.println("[tfruloading] could not watch for GUI opens: " + t);
        }
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        // Only "a screen is about to be shown" ends the loading screen; displayGuiScreen(null) is
        // just a screen being closed and must not count.
        if (event == null || event.gui == null) return;
        LoadingScreenBridge.onScreenOpened();
    }
}
