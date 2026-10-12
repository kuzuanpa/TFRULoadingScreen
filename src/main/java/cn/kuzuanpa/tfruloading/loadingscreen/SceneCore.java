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
import java.util.List;

/** Samples a scene node at a point in time: keyframe sampling and 2D transforms to triangle arrays. */
public final class SceneCore {

    private SceneCore() {
    }

    public static final class State {
        public float dx, dy;
        public float rot;
        public float sx = 1f;
        public float sy = 1f;
        public float alpha;

        public String fill, stroke, color;

        public List<float[]> pts;
    }

    public static final class CameraState {
        public float dx, dy;
        public float rot;
        public float sx = 1f, sy = 1f;
        public float pivotX, pivotY;
    }

    public static final class Shape {
        public float[] fill;
        public float[] stroke;
        public float pivotX, pivotY;

        public List<float[]> outline;
    }

    public static float ease(String name, float u) {
        if (u < 0f) u = 0f;
        if (u > 1f) u = 1f;
        if ("linear".equals(name)) return u;
        if ("in".equals(name)) return u * u;
        if ("out".equals(name)) return 1f - (1f - u) * (1f - u);
        return u * u * (3f - 2f * u);
    }

    private static final class Span {
        int lo = -1, hi = -1;
        float u;
    }

    private static boolean has(Scene.Key k, String ch) {
        if (k == null) return false;
        if ("dx".equals(ch)) return k.dx != null;
        if ("dy".equals(ch)) return k.dy != null;
        if ("rot".equals(ch)) return k.rot != null;
        if ("scale".equals(ch)) return k.scale != null;
        if ("sx".equals(ch)) return k.sx != null;
        if ("sy".equals(ch)) return k.sy != null;
        if ("alpha".equals(ch)) return k.alpha != null;
        if ("fill".equals(ch)) return k.fill != null;
        if ("stroke".equals(ch)) return k.stroke != null;
        if ("color".equals(ch)) return k.color != null;
        if ("pts".equals(ch)) return k.pts != null;
        return false;
    }

    private static Float numberAt(Scene.Key k, String ch) {
        if (k == null) return null;
        if ("dx".equals(ch)) return k.dx;
        if ("dy".equals(ch)) return k.dy;
        if ("rot".equals(ch)) return k.rot;
        if ("scale".equals(ch)) return k.scale;
        if ("sx".equals(ch)) return k.sx;
        if ("sy".equals(ch)) return k.sy;
        if ("alpha".equals(ch)) return k.alpha;
        return null;
    }

    private static String stringAt(Scene.Key k, String ch) {
        if (k == null) return null;
        if ("fill".equals(ch)) return k.fill;
        if ("stroke".equals(ch)) return k.stroke;
        if ("color".equals(ch)) return k.color;
        return null;
    }

    private static Span locate(List<Scene.Key> keys, String ch, float time) {
        int n = keys.size();
        float[] ts = new float[n];
        String[] es = new String[n];
        int[] idx = new int[n];
        int m = 0;
        for (int i = 0; i < n; i++) {
            if (!has(keys.get(i), ch)) continue;
            ts[m] = keys.get(i).t;
            es[m] = keys.get(i).ease;
            idx[m] = i;
            m++;
        }
        if (m == 0) return null;

        for (int i = 1; i < m; i++) {
            float tk = ts[i];
            String ek = es[i];
            int ik = idx[i];
            int j = i - 1;
            while (j >= 0 && ts[j] > tk) {
                ts[j + 1] = ts[j];
                es[j + 1] = es[j];
                idx[j + 1] = idx[j];
                j--;
            }
            ts[j + 1] = tk;
            es[j + 1] = ek;
            idx[j + 1] = ik;
        }

        Span s = new Span();
        if (time <= ts[0] || m == 1) {
            s.lo = idx[0];
            s.hi = idx[0];
            return s;
        }
        if (time >= ts[m - 1]) {
            s.lo = idx[m - 1];
            s.hi = idx[m - 1];
            return s;
        }
        for (int i = 0; i < m - 1; i++) {
            if (time <= ts[i + 1]) {
                float span = ts[i + 1] - ts[i];
                float u = span <= 0f ? 1f : (time - ts[i]) / span;
                s.lo = idx[i];
                s.hi = idx[i + 1];
                s.u = ease(es[i], u);
                return s;
            }
        }
        s.lo = idx[m - 1];
        s.hi = idx[m - 1];
        return s;
    }

    private static Float sampleChannel(List<Scene.Key> keys, String ch, float time) {
        Span s = locate(keys, ch, time);
        if (s == null) return null;
        Float a = numberAt(keys.get(s.lo), ch);
        Float b = numberAt(keys.get(s.hi), ch);
        if (a == null || b == null) return null;
        return a + (b - a) * s.u;
    }

    private static String sampleColor(Scene scene, List<Scene.Key> keys, String ch, float time) {
        Span s = locate(keys, ch, time);
        if (s == null) return null;
        int[] a = rgb255(scene, stringAt(keys.get(s.lo), ch));
        int[] b = rgb255(scene, stringAt(keys.get(s.hi), ch));
        StringBuilder sb = new StringBuilder("#");
        for (int i = 0; i < 3; i++) {
            int v = (int) Math.floor(a[i] + (b[i] - a[i]) * (double) s.u + 0.5d);
            if (v < 0) v = 0;
            if (v > 255) v = 255;
            sb.append(HEX[v >> 4]).append(HEX[v & 15]);
        }
        return sb.toString();
    }

    private static List<float[]> samplePts(List<Scene.Key> keys, List<float[]> base, float time) {
        if (base == null || base.size() < 2) return null;
        Span s = locate(keys, "pts", time);
        if (s == null) return null;
        List<float[]> a = keys.get(s.lo).pts;
        List<float[]> b = keys.get(s.hi).pts;
        if (a == null || b == null || a.size() != base.size() || b.size() != base.size()) return null;
        List<float[]> out = new ArrayList<float[]>(a.size());
        for (int i = 0; i < a.size(); i++) {
            float[] p = a.get(i), q = b.get(i);
            out.add(new float[] { p[0] + (q[0] - p[0]) * s.u, p[1] + (q[1] - p[1]) * s.u });
        }
        return out;
    }

    public static final float NO_LOOP = Float.NaN;

    private static final class Layer {
        float dx, dy, rot;
        float uni = 1f, sx = 1f, sy = 1f, alpha = 1f;
        String fill, stroke, color;
        List<float[]> pts;
    }

    private static Layer layer(Scene scene, Scene.Node node, List<Scene.Track> mine, float time) {
        Layer L = new Layer();
        if (mine == null || node.id == null) return L;
        for (Scene.Track track : mine) {
            if (track == null || track.keys == null || track.keys.isEmpty()) continue;
            List<Scene.Key> keys = track.keys;
            Float dx = sampleChannel(keys, "dx", time);
            Float dy = sampleChannel(keys, "dy", time);
            Float rot = sampleChannel(keys, "rot", time);
            Float scl = sampleChannel(keys, "scale", time);
            Float sxk = sampleChannel(keys, "sx", time);
            Float syk = sampleChannel(keys, "sy", time);
            Float alp = sampleChannel(keys, "alpha", time);

            if (dx != null) L.dx = dx;
            if (dy != null) L.dy = dy;
            if (rot != null) L.rot = rot;
            if (scl != null || sxk != null || syk != null) {
                L.uni = scl == null ? 1f : scl;
                L.sx = sxk == null ? 1f : sxk;
                L.sy = syk == null ? 1f : syk;
            }
            if (alp != null) L.alpha = alp;

            String fill = sampleColor(scene, keys, "fill", time);
            String stroke = sampleColor(scene, keys, "stroke", time);
            String color = sampleColor(scene, keys, "color", time);
            if (fill != null) L.fill = fill;
            if (stroke != null) L.stroke = stroke;
            if (color != null) L.color = color;

            List<float[]> pts = samplePts(keys, node.pts, time);
            if (pts != null) L.pts = pts;
        }
        return L;
    }

    private static List<Scene.Track> loopTracksFor(Scene scene, Scene.Node node) {
        if (scene == null || scene.loop == null || node.id == null) return null;
        return scene.loop.cyclicTracksFor(node.id);
    }

    public static State sample(Scene scene, Scene.Node node, float time) {
        return sample(scene, node, time, NO_LOOP);
    }

    public static State sample(Scene scene, Scene.Node node, float time, float loopTime) {
        float baseScale = node.scale == null ? 1f : node.scale;
        float nodeSx = node.sx == null ? 1f : node.sx;
        float nodeSy = node.sy == null ? 1f : node.sy;
        float baseRot = node.rot == null ? 0f : node.rot;
        float baseAlpha = node.alpha == null ? 1f : node.alpha;

        Layer actual = layer(scene, node, scene == null ? null : scene.tracksFor(node.id), time);
        Layer loop = (loopTime != loopTime) ? null : layer(scene, node, loopTracksFor(scene, node), loopTime);

        State st = new State();
        st.dx = actual.dx + (loop == null ? 0f : loop.dx);
        st.dy = actual.dy + (loop == null ? 0f : loop.dy);
        st.rot = baseRot + actual.rot + (loop == null ? 0f : loop.rot);
        st.sx = baseScale * nodeSx * actual.uni * actual.sx * (loop == null ? 1f : loop.uni * loop.sx);
        st.sy = baseScale * nodeSy * actual.uni * actual.sy * (loop == null ? 1f : loop.uni * loop.sy);
        st.alpha = baseAlpha * actual.alpha * (loop == null ? 1f : loop.alpha);

        String fill = loop == null ? null : loop.fill;
        String stroke = loop == null ? null : loop.stroke;
        String color = loop == null ? null : loop.color;
        List<float[]> pts = loop == null ? null : loop.pts;
        st.fill = fill != null ? fill : actual.fill;
        st.stroke = stroke != null ? stroke : actual.stroke;
        st.color = color != null ? color : actual.color;
        st.pts = pts != null ? pts : actual.pts;
        return st;
    }

    public static CameraState sampleCamera(Scene scene, float time) {
        float cw = scene != null && scene.canvas != null && scene.canvas.length > 0 && scene.canvas[0] > 0f
                ? scene.canvas[0] : 1920f;
        float ch = scene != null && scene.canvas != null && scene.canvas.length > 1 && scene.canvas[1] > 0f
                ? scene.canvas[1] : 1080f;

        CameraState cam = new CameraState();
        cam.pivotX = cw / 2f;
        cam.pivotY = ch / 2f;

        Scene.Camera c = scene == null ? null : scene.camera;
        if (c == null) return cam;

        float baseScale = c.scale == null ? 1f : c.scale;
        float camSx = c.sx == null ? 1f : c.sx;
        float camSy = c.sy == null ? 1f : c.sy;
        cam.rot = c.rot == null ? 0f : c.rot;
        cam.sx = baseScale * camSx;
        cam.sy = baseScale * camSy;
        if (c.pivot != null && c.pivot.length >= 2) {
            cam.pivotX = c.pivot[0];
            cam.pivotY = c.pivot[1];
        }
        if (c.keys == null || c.keys.isEmpty()) return cam;

        Float dx = sampleChannel(c.keys, "dx", time);
        Float dy = sampleChannel(c.keys, "dy", time);
        Float rot = sampleChannel(c.keys, "rot", time);
        Float scl = sampleChannel(c.keys, "scale", time);
        Float sxk = sampleChannel(c.keys, "sx", time);
        Float syk = sampleChannel(c.keys, "sy", time);
        if (dx != null) cam.dx = dx;
        if (dy != null) cam.dy = dy;
        if (rot != null) cam.rot = (c.rot == null ? 0f : c.rot) + rot;
        if (scl != null || sxk != null || syk != null) {
            float uni = scl == null ? 1f : scl;
            cam.sx = baseScale * camSx * uni * (sxk == null ? 1f : sxk);
            cam.sy = baseScale * camSy * uni * (syk == null ? 1f : syk);
        }
        return cam;
    }

    public static float sampleLogoAlpha(Scene scene, float time) {
        Scene.Logo lg = scene == null ? null : scene.logo;
        if (lg == null) return -1f;
        Float a = lg.keys == null ? null : sampleChannel(lg.keys, "alpha", time);
        if (a != null) return a;
        return lg.alpha == null ? 1f : lg.alpha;
    }

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    public static int[] rgb255(Scene scene, String ref) {
        String hex = ref;
        if (hex != null && !hex.startsWith("#") && scene != null && scene.palette != null) {
            hex = scene.palette.get(hex);
        }
        if (hex == null) hex = "#ffffff";
        hex = hex.trim();
        if (hex.startsWith("#")) hex = hex.substring(1);
        int r = 255, g = 255, b = 255;
        try {
            if (hex.length() == 3) {
                r = Integer.parseInt(hex.substring(0, 1), 16) * 17;
                g = Integer.parseInt(hex.substring(1, 2), 16) * 17;
                b = Integer.parseInt(hex.substring(2, 3), 16) * 17;
            } else if (hex.length() >= 6) {
                r = Integer.parseInt(hex.substring(0, 2), 16);
                g = Integer.parseInt(hex.substring(2, 4), 16);
                b = Integer.parseInt(hex.substring(4, 6), 16);
            }
        } catch (NumberFormatException ignored) {
            r = g = b = 255;
        }
        return new int[] { r, g, b };
    }

    public static float[] color(Scene scene, String ref) {
        int[] rgb = rgb255(scene, ref);
        return new float[] { rgb[0] / 255f, rgb[1] / 255f, rgb[2] / 255f, 1f };
    }

    public static Shape build(Scene.Node n, State st) {
        Shape sh = new Shape();
        String type = n.type == null ? "quad" : n.type;

        if ("quad".equals(type)) {
            float x = n.at[0], y = n.at[1], w = n.size[0], h = n.size[1];
            sh.fill = new float[] {
                    x, y, x + w, y, x + w, y + h,
                    x, y, x + w, y + h, x, y + h
            };
            sh.outline = corners(x, y, x + w, y + h);
            sh.pivotX = x + w / 2f;
            sh.pivotY = y + h / 2f;
        } else if ("tri".equals(type)) {
            float apex = n.angle == null ? 60f : n.angle;
            float h = n.height == null ? 100f : n.height;
            float half = (float) (h * Math.tan(Math.toRadians(apex) / 2.0));
            float x = n.at[0], y = n.at[1];
            sh.fill = new float[] {
                    x, y - h,
                    x - half, y,
                    x + half, y
            };
            sh.outline = new ArrayList<float[]>(3);
            sh.outline.add(new float[] { x, y - h });
            sh.outline.add(new float[] { x - half, y });
            sh.outline.add(new float[] { x + half, y });
            sh.pivotX = x;
            sh.pivotY = y;
        } else if ("poly".equals(type)) {
            boolean closed = Boolean.TRUE.equals(n.closed);
            List<float[]> pts = st != null && st.pts != null ? st.pts : n.pts;
            if (pts != null && pts.size() >= 2) {
                sh.outline = new ArrayList<float[]>(pts.size());
                for (float[] p : pts) sh.outline.add(new float[] { p[0], p[1] });
            }
            boolean hasFill = n.fill != null || (st != null && st.fill != null);
            boolean hasStroke = n.stroke != null || (st != null && st.stroke != null);
            if (closed && hasFill && pts != null && pts.size() >= 3) {
                sh.fill = earClip(pts);
            }
            if (hasStroke && pts != null && pts.size() >= 2) {
                sh.stroke = stroke(pts, n.width == null ? 6f : n.width, closed);
            }
            if (pts != null && !pts.isEmpty()) {
                float[] bb = bounds(pts);
                sh.pivotX = (bb[0] + bb[2]) / 2f;
                sh.pivotY = (bb[1] + bb[3]) / 2f;
            }
        }

        if (n.pivot != null) {
            sh.pivotX = n.pivot[0];
            sh.pivotY = n.pivot[1];
        }
        return sh;
    }

    private static List<float[]> corners(float x0, float y0, float x1, float y1) {
        List<float[]> out = new ArrayList<float[]>(4);
        out.add(new float[] { x0, y0 });
        out.add(new float[] { x1, y0 });
        out.add(new float[] { x1, y1 });
        out.add(new float[] { x0, y1 });
        return out;
    }

    public static List<float[]> roundCorners(List<float[]> pts, float radius, boolean closed) {
        int n = pts.size();
        if (n < 3 || radius <= 0.5f) return pts;
        List<float[]> out = new ArrayList<float[]>(n * 3);
        for (int i = 0; i < n; i++) {
            float[] b = pts.get(i);
            if (!closed && (i == 0 || i == n - 1)) {
                put(out, b[0], b[1]);
                continue;
            }
            float[] a = pts.get((i - 1 + n) % n);
            float[] c = pts.get((i + 1) % n);
            float ux = a[0] - b[0], uy = a[1] - b[1];
            float vx = c[0] - b[0], vy = c[1] - b[1];
            float lu = (float) Math.hypot(ux, uy), lv = (float) Math.hypot(vx, vy);
            if (lu < 1e-4f || lv < 1e-4f) {
                put(out, b[0], b[1]);
                continue;
            }
            ux /= lu; uy /= lu; vx /= lv; vy /= lv;
            float dot = Math.max(-1f, Math.min(1f, ux * vx + uy * vy));
            float phi = (float) (Math.acos(dot) / 2.0);
            if (phi < 1e-3f || phi > 1.5533f) {
                put(out, b[0], b[1]);
                continue;
            }

            float r = Math.min(radius, (float) (Math.min(lu, lv) / 2.0 * Math.tan(phi)));
            float t = (float) (r / Math.tan(phi));
            float p0x = b[0] + ux * t, p0y = b[1] + uy * t;
            float p1x = b[0] + vx * t, p1y = b[1] + vy * t;
            float bx = ux + vx, by = uy + vy;
            float lb = (float) Math.hypot(bx, by);
            if (lb < 1e-4f) {
                put(out, b[0], b[1]);
                continue;
            }
            float cx = b[0] + bx / lb * (float) (r / Math.sin(phi));
            float cy = b[1] + by / lb * (float) (r / Math.sin(phi));
            float a0 = (float) Math.atan2(p0y - cy, p0x - cx);
            float d = (float) Math.atan2(p1y - cy, p1x - cx) - a0;
            while (d > Math.PI) d -= 2 * Math.PI;
            while (d < -Math.PI) d += 2 * Math.PI;
            int steps = Math.max(2, Math.min(6, (int) Math.ceil(phi * 8.0)));
            for (int k = 0; k <= steps; k++) {
                float ang = a0 + d * (k / (float) steps);
                put(out, cx + (float) Math.cos(ang) * r, cy + (float) Math.sin(ang) * r);
            }
        }

        if (out.size() > 1) {
            float[] first = out.get(0);
            float[] last = out.get(out.size() - 1);
            if (Math.abs(last[0] - first[0]) < 0.01f && Math.abs(last[1] - first[1]) < 0.01f) {
                out.remove(out.size() - 1);
            }
        }
        return out;
    }

    private static void put(List<float[]> out, float x, float y) {
        if (!out.isEmpty()) {
            float[] last = out.get(out.size() - 1);
            if (Math.abs(last[0] - x) < 0.01f && Math.abs(last[1] - y) < 0.01f) return;
        }
        out.add(new float[] { x, y });
    }

    public static void transform(float[] verts, float px, float py, State st) {
        if (verts == null || verts.length == 0) return;
        double rad = Math.toRadians(st.rot);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        for (int i = 0; i + 1 < verts.length; i += 2) {
            float x = (verts[i] - px) * st.sx;
            float y = (verts[i + 1] - py) * st.sy;
            verts[i] = px + x * cos - y * sin + st.dx;
            verts[i + 1] = py + x * sin + y * cos + st.dy;
        }
    }

    private static float[] bounds(List<float[]> pts) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (float[] p : pts) {
            if (p[0] < minX) minX = p[0];
            if (p[1] < minY) minY = p[1];
            if (p[0] > maxX) maxX = p[0];
            if (p[1] > maxY) maxY = p[1];
        }
        return new float[] { minX, minY, maxX, maxY };
    }

    private static float cross(float[] a, float[] b, float[] c) {
        return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
    }

    private static boolean pointInTriangle(float[] p, float[] a, float[] b, float[] c) {
        float eps = -1e-4f;
        return cross(a, b, p) >= eps && cross(b, c, p) >= eps && cross(c, a, p) >= eps;
    }

    public static float[] earClip(List<float[]> p) {
        int n = p.size();
        if (n < 3) return new float[0];

        float area2 = 0f;
        for (int i = 0; i < n; i++) {
            float[] a = p.get(i);
            float[] b = p.get((i + 1) % n);
            area2 += a[0] * b[1] - b[0] * a[1];
        }

        ArrayList<Integer> v = new ArrayList<Integer>();
        if (area2 > 0f) {
            for (int i = 0; i < n; i++) v.add(i);
        } else {
            for (int i = n - 1; i >= 0; i--) v.add(i);
        }

        float[] out = new float[n * 6];
        int o = 0;
        int guard = 0;
        while (v.size() > 3 && guard++ < n * n) {
            boolean earFound = false;
            int m = v.size();
            for (int i = 0; i < m; i++) {
                int ia = v.get((i - 1 + m) % m);
                int ib = v.get(i);
                int ic = v.get((i + 1) % m);
                float[] a = p.get(ia), b = p.get(ib), c = p.get(ic);
                if (cross(a, b, c) <= 0f) continue;
                boolean blocked = false;
                for (int j = 0; j < m; j++) {
                    int ij = v.get(j);
                    if (ij == ia || ij == ib || ij == ic) continue;
                    if (pointInTriangle(p.get(ij), a, b, c)) {
                        blocked = true;
                        break;
                    }
                }
                if (blocked) continue;
                out[o++] = a[0]; out[o++] = a[1];
                out[o++] = b[0]; out[o++] = b[1];
                out[o++] = c[0]; out[o++] = c[1];
                v.remove(i);
                earFound = true;
                break;
            }
            if (!earFound) break;
        }
        if (v.size() == 3) {
            float[] a = p.get(v.get(0)), b = p.get(v.get(1)), c = p.get(v.get(2));
            out[o++] = a[0]; out[o++] = a[1];
            out[o++] = b[0]; out[o++] = b[1];
            out[o++] = c[0]; out[o++] = c[1];
        }
        return java.util.Arrays.copyOf(out, o);
    }

    public static float[] stroke(List<float[]> pts, float width, boolean closed) {
        int n = pts.size();
        if (n < 2) return new float[0];
        float half = Math.max(width, 0.5f) / 2f;
        float[] left = offsetSide(pts, half, closed, 1f, 0f);
        float[] right = offsetSide(pts, half, closed, -1f, 0f);

        int segs = closed ? n : n - 1;
        float[] out = new float[segs * 12];
        int o = 0;
        for (int s = 0; s < segs; s++) {
            int i = s, j = (s + 1) % n;
            out[o++] = left[i * 2]; out[o++] = left[i * 2 + 1];
            out[o++] = left[j * 2]; out[o++] = left[j * 2 + 1];
            out[o++] = right[j * 2]; out[o++] = right[j * 2 + 1];

            out[o++] = left[i * 2]; out[o++] = left[i * 2 + 1];
            out[o++] = right[j * 2]; out[o++] = right[j * 2 + 1];
            out[o++] = right[i * 2]; out[o++] = right[i * 2 + 1];
        }
        return out;
    }

    public static float[] offsetSide(List<float[]> pts, float dist, boolean closed, float side,
                                     float limit) {
        int n = pts.size();
        float[] out = new float[n * 2];
        for (int i = 0; i < n; i++) {
            float[] p = pts.get(i);
            boolean hasPrev = closed || i > 0;
            boolean hasNext = closed || i < n - 1;

            float n0x = 0, n0y = 0, n1x = 0, n1y = 0;
            if (hasPrev) {
                float[] q = pts.get((i - 1 + n) % n);
                float dx = p[0] - q[0], dy = p[1] - q[1];
                float len = (float) Math.hypot(dx, dy);
                if (len > 1e-5f) { n0x = -dy / len; n0y = dx / len; }
            }
            if (hasNext) {
                float[] q = pts.get((i + 1) % n);
                float dx = q[0] - p[0], dy = q[1] - p[1];
                float len = (float) Math.hypot(dx, dy);
                if (len > 1e-5f) { n1x = -dy / len; n1y = dx / len; }
            }

            float mx, my;
            if (hasPrev && hasNext) {
                mx = n0x + n1x;
                my = n0y + n1y;
                float len = (float) Math.hypot(mx, my);
                if (len < 1e-5f) { mx = n0x; my = n0y; } else { mx /= len; my /= len; }
            } else if (hasNext) {
                mx = n1x; my = n1y;
            } else {
                mx = n0x; my = n0y;
            }

            float denom = mx * (hasNext ? n1x : n0x) + my * (hasNext ? n1y : n0y);
            if (denom < 0.25f) denom = 0.25f;
            float len = dist / denom;

            if (limit > 0f && len > limit) len = limit;
            len *= side;
            out[i * 2] = p[0] + mx * len;
            out[i * 2 + 1] = p[1] + my * len;
        }
        return out;
    }

    public static float outwardSide(List<float[]> pts) {
        float area2 = 0f;
        for (int i = 0; i < pts.size(); i++) {
            float[] a = pts.get(i);
            float[] b = pts.get((i + 1) % pts.size());
            area2 += a[0] * b[1] - b[0] * a[1];
        }
        return area2 > 0f ? -1f : 1f;
    }

    public static float[] haloBand(List<float[]> pts, boolean closed, float r0, float r1) {
        int n = pts.size();
        if (n < 2) return new float[0];
        float limit = r1;
        if (closed) {
            return ring(pts, n, r0, r1, outwardSide(pts), limit, true);
        }
        float[] one = ring(pts, n, r0, r1, 1f, limit, false);
        float[] two = ring(pts, n, r0, r1, -1f, limit, false);
        float[] out = new float[one.length + two.length];
        System.arraycopy(one, 0, out, 0, one.length);
        System.arraycopy(two, 0, out, one.length, two.length);
        return out;
    }

    private static float[] ring(List<float[]> pts, int n, float r0, float r1, float side, float limit,
                                boolean closed) {

        float[] a = offsetSide(pts, r0, closed, side, limit);
        float[] b = offsetSide(pts, r1, closed, side, limit);
        int segs = closed ? n : n - 1;
        float[] out = new float[segs * 12];
        int o = 0;
        for (int s = 0; s < segs; s++) {
            int i = s, j = (s + 1) % n;
            out[o++] = a[i * 2]; out[o++] = a[i * 2 + 1];
            out[o++] = a[j * 2]; out[o++] = a[j * 2 + 1];
            out[o++] = b[j * 2]; out[o++] = b[j * 2 + 1];

            out[o++] = a[i * 2]; out[o++] = a[i * 2 + 1];
            out[o++] = b[j * 2]; out[o++] = b[j * 2 + 1];
            out[o++] = b[i * 2]; out[o++] = b[i * 2 + 1];
        }
        return out;
    }
}
