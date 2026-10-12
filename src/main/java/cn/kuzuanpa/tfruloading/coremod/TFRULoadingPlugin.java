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
 * Coremod entry point: hands the transformer to FML and turns Forge's splash off before
 * {@code SplashProgress.start()} reads the config. It is also a regular {@code @Mod}
 * ({@code getModContainerClass()} returns null).
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

    /** Runs on the launcher's working directory (the instance) before SplashProgress.start(). */
    @Override
    public void injectData(Map<String, Object> data) {
        try {
            cn.kuzuanpa.tfruloading.loadingscreen.SplashConfig.disableForgeSplash(new File("."));
        } catch (Throwable ignored) {
        }
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
