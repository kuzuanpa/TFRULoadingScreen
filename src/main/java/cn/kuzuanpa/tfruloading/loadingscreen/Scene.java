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

/** A parsed scene file: nodes with keyframed tracks, plus loop/pacing/camera/logo settings. */
public class Scene {

    public float[] canvas;

    public float duration = 8f;

    public String background = "#000000";

    public Map<String, String> palette;

    public List<Node> nodes;

    public List<Track> tracks;

    public Loop loop;

    public Pacing pacing;

    public Camera camera;

    public Logo logo;

    private transient Map<String, List<Track>> trackIndex;

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

    public static class Loop {

        public Float duration;

        public List<Track> tracks;

        private transient List<Track> cyclic;

        private transient Map<String, List<Track>> cyclicIndex;

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

    public static class Pacing {

        public Float allowancePerProgress;

        public Float rate;

        public Float resumeSeconds;

        public Float initialAllowance;

        public Float finishRate;
    }

    public static class Node {
        public String id;
        public String type;

        public Float alpha;
        public Boolean glow;
        public float[] pivot;
        public Float rot;
        public Float scale;
        public Float sx;
        public Float sy;

        public float[] at;
        public float[] size;
        public String fill;

        public Float angle;
        public Float height;

        public List<float[]> pts;
        public Boolean closed;
        public String stroke;
        public Float width;

        public String text;
        public String color;
    }

    public static class Track {
        public String target;
        public List<Key> keys;
    }

    public static class Key {
        public float t;
        public Float dx, dy;
        public Float rot;
        public Float scale;
        public Float sx, sy;
        public Float alpha;
        public String ease;

        public String fill;
        public String stroke;
        public String color;
        public List<float[]> pts;
    }

    public static class Camera {
        public Float scale;
        public Float sx;
        public Float sy;
        public Float rot;
        public float[] pivot;
        public List<Key> keys;
    }

    public static class Logo {
        public String texture = "tfru_logo";
        public float[] at;
        public float[] size;
        public Float alpha;
        public List<Key> keys;
    }
}
