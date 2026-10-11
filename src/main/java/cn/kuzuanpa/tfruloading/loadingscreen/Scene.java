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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data model for the TFRU loading scene language (see LANGUAGE.md at the repository root).
 *
 * <p>This is a plain Gson POJO: every field is public and non-final so Gson can populate it.
 * Fields that are optional in the language are boxed so that "absent" is distinguishable from
 * a real 0 / false value.
 */
public class Scene {

    /** [w, h] of the virtual canvas, e.g. [1920, 1080]. */
    public float[] canvas;

    /** Length of the actual (progress-driven) timeline, in seconds. */
    public float duration = 8f;

    /** Clear colour, palette name or "#rrggbb". */
    public String background = "#000000";

    /** Colour name -> "#rrggbb". Nodes may also use raw hex. */
    public Map<String, String> palette;

    /** Draw order: later entries paint over earlier ones. */
    public List<Node> nodes;

    /** Actual-frame timeline: only advances while the director has allowance. */
    public List<Track> tracks;

    /** Loop-frame timeline: plays continuously on wall time, wrapping every cycle. */
    public Loop loop;

    /** Optional director tuning; see {@link Director}. */
    public Pacing pacing;

    /** Optional global camera. Absent means the identity (canvas drawn 1:1). */
    public Camera camera;

    /** Optional final-frame logo image. See {@link Logo}. */
    public Logo logo;

    /**
     * Tracks indexed by target id, built on first use. `SceneCore.layer` used to walk the whole
     * track list for every node, which is O(nodes x tracks) per frame - with the long scene that
     * was 98% of the sampling cost. Lists keep file order, so "the later track wins" still holds.
     */
    private transient Map<String, List<Track>> trackIndex;

    /** The tracks that target {@code id}, in file order. */
    public List<Track> tracksFor(String id) {
        if (trackIndex == null) {
            trackIndex = new HashMap<String, List<Track>>();
            if (tracks != null) {
                for (Track t : tracks) {
                    if (t == null || t.target == null) continue;
                    List<Track> list = trackIndex.get(t.target);
                    if (list == null) {
                        list = new ArrayList<Track>();
                        trackIndex.put(t.target, list);
                    }
                    list.add(t);
                }
            }
        }
        return trackIndex.get(id);
    }

    /** 循环帧: a continuously repeating timeline (anvil strike, fire flicker, ...). */
    public static class Loop {
        /** Cycle length in seconds. */
        public Float duration;

        public List<Track> tracks;

        /** Memoised tracks with the synthetic closing key; Gson leaves this alone. */
        private transient List<Track> cyclic;

        /** Same per-target index as {@link Scene#tracksFor(String)}, over the cyclic tracks. */
        private transient Map<String, List<Track>> cyclicIndex;

        /** The cyclic tracks that target {@code id}, in file order. */
        public List<Track> cyclicTracksFor(String id) {
            if (cyclicIndex == null) {
                cyclicIndex = new HashMap<String, List<Track>>();
                for (Track t : cyclicTracks()) {
                    if (t == null || t.target == null) continue;
                    List<Track> list = cyclicIndex.get(t.target);
                    if (list == null) {
                        list = new ArrayList<Track>();
                        cyclicIndex.put(t.target, list);
                    }
                    list.add(t);
                }
            }
            return cyclicIndex.get(id);
        }

        /**
         * Loop tracks with a synthetic closing key at {@code duration} carrying the earliest
         * key's channels, so the cycle lands exactly on its own start and cannot pop on the wrap.
         * Keys at or past {@code duration} are outside the cycle and are dropped, so a redundant
         * closing key left in the file cannot break the wrap.
         */
        public List<Track> cyclicTracks() {
            if (cyclic != null) return cyclic;
            cyclic = new ArrayList<Track>();
            if (tracks == null || duration == null || duration <= 0f) return cyclic;
            for (Track track : tracks) {
                if (track == null || track.keys == null || track.keys.isEmpty()) continue;
                List<Key> keys = new ArrayList<Key>(track.keys.size() + 1);
                Key first = null;
                for (Key k : track.keys) {
                    if (k == null || k.t >= duration) continue;
                    keys.add(k);
                    if (first == null || k.t < first.t) first = k;
                }
                if (first == null) continue;
                keys.add(newKeyAt(first, duration));
                Track copy = new Track();
                copy.target = track.target;
                copy.keys = keys;
                cyclic.add(copy);
            }
            return cyclic;
        }

        private static Key newKeyAt(Key src, float t) {
            Key k = new Key();
            k.t = t;
            k.dx = src.dx;
            k.dy = src.dy;
            k.rot = src.rot;
            k.scale = src.scale;
            k.sx = src.sx;
            k.sy = src.sy;
            k.alpha = src.alpha;
            k.ease = src.ease;
            k.fill = src.fill;
            k.stroke = src.stroke;
            k.color = src.color;
            k.pts = src.pts;
            return k;
        }
    }

    /**
     * Director (额度/速度) tuning. Every field is optional; the defaults live in
     * {@link Director#Director(Scene)}.
     */
    public static class Pacing {
        /** Seconds of actual timeline granted per 1.0 of loading progress. Default: duration. */
        public Float allowancePerProgress;

        /** Fixed advance rate of the actual timeline, in timeline seconds per wall second. */
        public Float rate;

        /** Do not resume a stopped actual timeline until this much allowance is banked. */
        public Float resumeSeconds;

        /** Allowance the timeline starts with, so frame one is already moving. */
        public Float initialAllowance;

        /** Advance rate once loading has finished; keeps the tail snappy. */
        public Float finishRate;
    }

    public static class Node {
        public String id;
        public String type;      // "quad" | "tri" | "poly" | "text"

        // common optional transform / styling
        public Float alpha;      // default 1
        public Boolean glow;     // default false
        public float[] pivot;    // default: bbox centre (tri: the apex base)
        public Float rot;        // default 0
        public Float scale;      // default 1, uniform
        public Float sx;         // default 1, per-axis multiplier on top of scale
        public Float sy;         // default 1

        // quad
        public float[] at;       // [x, y]
        public float[] size;     // [w, h]
        public String fill;      // quad/tri/poly fill colour

        // tri
        public Float angle;      // apex angle in degrees, default 60
        public Float height;     // apex height above the base

        // poly
        public List<float[]> pts;
        public Boolean closed;   // default false
        public String stroke;    // polyline stroke colour
        public Float width;      // stroke width, default 6

        // text
        public String text;
        public String color;     // default white; text scale uses the common `scale` field
    }

    public static class Track {
        public String target;    // node id
        public List<Key> keys;
    }

    public static class Key {
        public float t;          // seconds
        public Float dx, dy;     // translation offset
        public Float rot;        // added to node.rot
        public Float scale;      // multiplied with node.scale
        public Float sx, sy;     // multiplied with the resolved x/y scale
        public Float alpha;      // multiplied with node.alpha
        public String ease;      // "linear" | "in" | "out" | "inout" (default)

        // Animatable appearance; same channel rules as the numeric channels above.
        public String fill;      // palette name or "#rrggbb", interpolated in RGB
        public String stroke;
        public String color;     // text colour
        public List<float[]> pts; // must have the node's static vertex count
    }

    /** Scene-level camera: translate + rotate + non-uniform scale about a pivot. */
    public static class Camera {
        public Float scale;      // default 1, uniform
        public Float sx;         // default 1
        public Float sy;         // default 1
        public Float rot;        // default 0, degrees
        public float[] pivot;    // default: canvas centre
        public List<Key> keys;
    }

    /**
     * Optional final-frame logo IMAGE, drawn in screen space (after the camera) so it scales with
     * the canvas. This is the one deliberate exception to "four primitives only": review asked for
     * the ending card to be the real logo texture instead of a primitive-built emblem.
     */
    public static class Logo {
        public String texture = "tfru_logo";   // logical name; the mod loads assets/tfruloading/textures/<name>.png
        public float[] at;                     // [x, y] top-left, in virtual canvas units
        public float[] size;                   // [w, h]
        public Float alpha;                    // default 1
        public List<Key> keys;                 // only the `alpha` channel is read
    }
}
