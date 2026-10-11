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

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.FMLModContainer;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.event.FMLConstructionEvent;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Drives the loading screen's progress bar from FML's mod lifecycle.
 *
 * <p>FML posts each state event on the *per-mod* Guava event bus stored in
 * {@code FMLModContainer.eventBus}, so we register a listener on every mod's bus to get one progress
 * tick per mod per phase. That is the same source BetterLoadingScreen uses; it is coarse but it is
 * the only per-mod signal FML exposes without transforming FML itself.
 *
 * <p>These handlers feed the bar and hand {@link LoadingScreenManager} a frame opportunity. The
 * other frame source is FML's own progress bars, which the coremod hooks - see
 * {@code LoadingScreenBridge.pumpFrame()} and ROADMAP §2.13.
 */
public final class LoadingProgress {

    private static final AtomicBoolean ATTACHED = new AtomicBoolean();
    private static final AtomicBoolean COMPLETED = new AtomicBoolean();

    private static final String[] PHASES = {
            "Construction", "Pre-initialization", "Initialization", "Post-initialization"
    };

    /**
     * Where each phase starts, as a fraction of the bar. Construction and Pre-initialization are what
     * happens while the TFRU pack discovers and builds its 150 mods; the last slice is banked by
     * {@link #onLoadComplete} so the bar only reads 100% when the game is really done.
     */
    private static final float[] BASE = { 0.00f, 0.08f, 0.45f, 0.78f, 0.96f };

    private static int total = 1;

    /** null for the instance registered on the global FML bus (load-complete only). */
    private final ModContainer mod;
    private final int index;

    private LoadingProgress(ModContainer mod, int index) {
        this.mod = mod;
        this.index = index;
    }

    public static void attach() {
        if (!ATTACHED.compareAndSet(false, true)) return;

        try {
            List<ModContainer> mods = Loader.instance().getActiveModList();
            total = Math.max(1, mods.size());
            int i = 0;
            for (ModContainer mod : mods) {
                i++;
                if (!(mod instanceof FMLModContainer)) continue;
                try {
                    Field field = FMLModContainer.class.getDeclaredField("eventBus");
                    field.setAccessible(true);
                    EventBus bus = (EventBus) field.get(mod);
                    if (bus == null) continue;
                    bus.register(new LoadingProgress(mod, i));
                } catch (Throwable ignored) {
                    // this mod's bus is not reachable; it just will not tick the bar
                }
            }
        } catch (Throwable t) {
            System.err.println("[tfruloading] could not attach per-mod progress listeners: " + t);
        }

        try {
            FMLCommonHandler.instance().bus().register(new LoadingProgress(null, 0));
        } catch (Throwable ignored) {
            // non fatal
        }
    }

    @Subscribe
    public void onConstruct(FMLConstructionEvent event) {
        report(0);
    }

    @Subscribe
    public void onPreInit(FMLPreInitializationEvent event) {
        report(1);
    }

    @Subscribe
    public void onInit(FMLInitializationEvent event) {
        report(2);
    }

    @Subscribe
    public void onPostInit(FMLPostInitializationEvent event) {
        report(3);
    }

    @Subscribe
    public void onLoadComplete(FMLLoadCompleteEvent event) {
        if (COMPLETED.compareAndSet(false, true)) {
            LoadingScreenManager.complete();
        }
    }

    private void report(int phase) {
        if (mod == null) return; // global-bus instance: load-complete only
        float from = BASE[phase];
        float to = BASE[phase + 1];
        float within = index / (float) total;
        LoadingScreenManager.setProgress(PHASES[phase] + ": " + mod.getName(), from + (to - from) * within);
    }
}
