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

package cn.kuzuanpa.tfruloading.coremod;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

import java.io.File;
import java.util.Map;

/**
 * Coremod entry point for the TFRU loading screen.
 *
 * <p>Modelled on the loading-screen replacement used by BetterLoadingScreen: the coremod exists only
 * to (a) hand the ASM transformer to FML and (b) disable Forge's own splash before
 * {@code SplashProgress.start()} reads {@code config/splash.properties}. With Forge's splash off,
 * {@code SplashProgress.drawVanillaScreen()} calls {@code Minecraft.loadScreen()}, whose body the
 * transformer replaces with our own first frame.
 *
 * <p>{@link #getModContainerClass()} returns null because this is also a regular {@code @Mod}.
 */
@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.TransformerExclusions({ "cn.kuzuanpa.tfruloading.coremod" })
@IFMLLoadingPlugin.SortingIndex(1000)
public class TFRULoadingPlugin implements IFMLLoadingPlugin {

    @Override
    public String[] getASMTransformerClass() {
        return new String[] { "cn.kuzuanpa.tfruloading.coremod.LoadingScreenTransformer" };
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        try {
            // Must happen before SplashProgress.start() reads the file. The launcher runs the game
            // with the instance directory as the working directory, which is where mcDataDir lives.
            cn.kuzuanpa.tfruloading.loadingscreen.SplashConfig.disableForgeSplash(new File("."));
        } catch (Throwable ignored) {
            // non fatal: the runtime will also try once Minecraft is up
        }
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
