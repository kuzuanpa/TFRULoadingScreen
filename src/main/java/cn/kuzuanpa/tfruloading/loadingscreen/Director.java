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
 * Two timelines: {@link #actualTime()} follows what the progress bar has paid for (it gates the story),
 * {@link #loopTime()} follows wall-clock time (it drives the looping background animation).
 */
public final class Director {

    private final float duration;
    private final float loopDuration;
    private final float allowancePerProgress;
    private final float rate;
    private final float resumeSeconds;
    private final float finishRate;

    private float allowance;
    private float actualTime;
    private float loopTime;
    private float progress;
    private boolean running;
    private boolean finished;

    public Director(Scene scene) {
        Scene.Pacing p = scene == null ? null : scene.pacing;
        this.duration = scene == null || scene.duration <= 0f ? 1f : scene.duration;
        this.loopDuration = scene == null || scene.loop == null || scene.loop.duration == null
                ? 0f : scene.loop.duration;
        this.allowancePerProgress = p == null || p.allowancePerProgress == null
                ? this.duration : p.allowancePerProgress;
        this.rate = p == null || p.rate == null ? 1f : p.rate;
        this.resumeSeconds = p == null || p.resumeSeconds == null ? 1f : p.resumeSeconds;
        float initial = p == null || p.initialAllowance == null
                ? this.resumeSeconds : p.initialAllowance;
        this.finishRate = p == null || p.finishRate == null ? this.rate : p.finishRate;

        this.allowance = initial;
        this.running = initial > 0f;
    }

    public void setProgress(float p) {
        float next = p < 0f ? 0f : p > 1f ? 1f : p;
        if (next > progress) allowance += (next - progress) * allowancePerProgress;
        progress = next;
    }

    public void tick(float dt) {
        if (dt < 0f || dt != dt) dt = 0f;

        if (loopDuration > 0f) {
            loopTime = (loopTime + dt) % loopDuration;
        }

        if (actualTime >= duration) {
            actualTime = duration;
            running = false;
            return;
        }

        float speed = finished && finishRate > rate ? finishRate : rate;
        if (!running) {
            float need = finished ? 0f : resumeSeconds;
            if (allowance >= need) running = true;
        }
        if (running) {
            float want = dt * speed;
            float step = Math.min(want, allowance);
            actualTime += step;
            allowance -= step;
            if (actualTime > duration) {
                allowance += actualTime - duration;
                actualTime = duration;
            }
            if (step < want - 1e-9f || allowance <= 1e-9f) {
                running = false;
            }
        }
    }

    public void finish() {
        setProgress(1f);
        finished = true;
        float remaining = Math.max(0f, duration - actualTime);
        if (remaining > allowance) allowance = remaining;
        if (remaining > 0f) running = true;
    }

    public float actualTime() {
        return actualTime;
    }

    public float loopTime() {
        return loopTime;
    }

    public float allowance() {
        return allowance;
    }

    public boolean running() {
        return running;
    }

    public boolean finished() {
        return finished;
    }

    public boolean done() {
        return actualTime >= duration;
    }
}
