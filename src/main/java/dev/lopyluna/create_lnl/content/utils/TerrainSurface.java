package dev.lopyluna.create_lnl.content.utils;

//Copied & Tweaked the Code from another project

import dev.lopyluna.create_lnl.register.LiftsTags;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.Arrays;
import java.util.List;

@SuppressWarnings("unused")
public class TerrainSurface {
    private static final int[][] EDGES = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
    private static final int[][] AXES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private final int span;
    private final int detail;
    private final int rounds;
    private final int side;
    private final int plane;
    private final int cells;
    private final double grain;
    private final byte[] kind;
    private final boolean[] tagged;
    private final boolean[] shown;
    private final boolean[] mask;
    private final boolean[] filled;
    private final boolean[] open;
    private final int[] queue;
    private final float[] density;
    private final float[] prior;
    private final float[] vertices;
    private final float[] drift;
    private final float[] gain;
    private final float[] slant;
    private final int[] tally;
    private final float[] cube = new float[8];
    private final Int2ObjectOpenHashMap<List<AABB>> shapes = new Int2ObjectOpenHashMap<>();
    private final float[][] pins;
    private final float[][] slopes;
    private final boolean[] marked = new boolean[27];
    private final float[] corners = new float[81];
    private final boolean[] octant = new boolean[8];
    private final IntArrayList quads = new IntArrayList();
    private final DoubleArrayList pokes = new DoubleArrayList();
    private final BlockPos.MutableBlockPos scan = new BlockPos.MutableBlockPos();
    private final Vector3d cursor = new Vector3d();
    private final Vector3d heading = new Vector3d();
    private final Vector3d escape = new Vector3d();
    private final int[] base = new int[3];
    private final int hedge;
    private final boolean[] half;

    public TerrainSurface(int span, int detail, int rounds) {
        this.span = span;
        this.detail = detail;
        this.rounds = rounds;
        side = span * detail;
        plane = side * side;
        cells = side - 1;
        hedge = (span + 4) * 2;
        half = new boolean[hedge * hedge * hedge];
        var reach = (span + 2) * (span + 2) * (span + 2);
        pins = new float[reach][];
        slopes = new float[reach][];
        grain = 1d / detail;
        var volume = side * side * side;
        var count = cells * cells * cells;
        kind = new byte[span * span * span];
        tagged = new boolean[volume];
        shown = new boolean[volume];
        mask = new boolean[volume];
        density = new float[volume];
        prior = new float[volume];
        filled = new boolean[count];
        open = new boolean[volume];
        queue = new int[volume];
        vertices = new float[count * 3];
        drift = new float[count * 3];
        gain = new float[count * 3];
        slant = new float[count * 3];
        tally = new int[count];
    }

    public int quads() {
        return quads.size() / 4;
    }

    public int pokes() {
        return pokes.size() / 6;
    }

    public double poke(int index, int part) {
        return pokes.getDouble(index * 6 + part);
    }

    public float vx(int quad, int corner) {
        return vertices[quads.getInt(quad * 4 + corner) * 3];
    }

    public float vy(int quad, int corner) {
        return vertices[quads.getInt(quad * 4 + corner) * 3 + 1];
    }

    public float vz(int quad, int corner) {
        return vertices[quads.getInt(quad * 4 + corner) * 3 + 2];
    }

    public Vector3d center(int quad, Vector3d out) {
        out.set(0, 0, 0);
        for (var i = 0; i < 4; i++) out.add(vx(quad, i), vy(quad, i), vz(quad, i));
        return out.div(4);
    }

    public Vector3d normal(int quad, Vector3d out) {
        out.set(vx(quad, 2) - vx(quad, 0), vy(quad, 2) - vy(quad, 0), vz(quad, 2) - vz(quad, 0))
                .cross(vx(quad, 3) - vx(quad, 1), vy(quad, 3) - vy(quad, 1), vz(quad, 3) - vz(quad, 1));
        return out.lengthSquared() <= 1.0E-12 ? out.set(0, 1, 0) : out.normalize();
    }

    public void build(BlockGetter level, Vec3 origin) {
        scan(level, origin);
        shape();
    }

    public void scan(BlockGetter level, Vec3 origin) {
        base[0] = Mth.floor(origin.x) - span / 2;
        base[1] = Mth.floor(origin.y) - span / 2;
        base[2] = Mth.floor(origin.z) - span / 2;
        carve(level);
    }

    public void shape() {
        wedge();
        drown();
        pin();
        spread();
        contour();
        for (var i = 0; i < rounds; i++) {
            fair(.6f);
            fair(-.62f);
            settle();
            relax();
            tether();
        }
        probe();
    }

    public boolean covers(double x, double y, double z, double margin) {
        return x - margin >= base[0] + 2 && y - margin >= base[1] + 2 && z - margin >= base[2] + 2
                && x + margin <= base[0] + span - 2 && y + margin <= base[1] + span - 2 && z + margin <= base[2] + span - 2;
    }

    public double contact(double x, double y, double z, double dx, double dy, double dz, Vector3d out) {
        var shift = 0d;
        for (var pass = 0; pass < 3; pass++) {
            double px = x + dx * shift, py = y + dy * shift, pz = z + dz * shift;
            int cx = Mth.floor((px - base[0]) / grain) - 1, cy = Mth.floor((py - base[1]) / grain) - 1, cz = Mth.floor((pz - base[2]) / grain) - 1;
            double nx = 0d, ny = 0d, nz = 0d, ax = 0d, ay = 0d, az = 0d;
            var total = 0d;
            for (var i = cx - 4; i <= cx + 4; i++) for (var j = cy - 4; j <= cy + 4; j++) for (var k = cz - 4; k <= cz + 4; k++) {
                if (i < 0 || j < 0 || k < 0 || i >= cells || j >= cells || k >= cells) continue;
                var c = cell(i, j, k);
                if (!filled[c]) continue;
                var ex = px - vertices[c * 3];
                var ey = py - vertices[c * 3 + 1];
                var ez = pz - vertices[c * 3 + 2];
                var away = ex * ex + ey * ey + ez * ez;
                if (away > 3.0625) continue;
                var weight = 1 - away / 3.0625;
                weight *= weight;
                nx += slant[c * 3] * weight;
                ny += slant[c * 3 + 1] * weight;
                nz += slant[c * 3 + 2] * weight;
                ax += vertices[c * 3] * weight;
                ay += vertices[c * 3 + 1] * weight;
                az += vertices[c * 3 + 2] * weight;
                total += weight;
            }
            if (total <= 0) return Double.NaN;
            var len = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len <= 1.0E-9) return Double.NaN;
            nx /= len;
            ny /= len;
            nz /= len;
            var into = dx * nx + dy * ny + dz * nz;
            if (into > -.25) return Double.NaN;
            out.set(nx, ny, nz);
            var above = (px - ax / total) * nx + (py - ay / total) * ny + (pz - az / total) * nz;
            shift = Mth.clamp(shift - above / into, -.75, .75);
        }
        return shift;
    }

    private void probe() {
        pokes.clear();
        Arrays.fill(slant, 0f);
        for (var quad = 0; quad < quads(); quad++) {
            normal(quad, heading);
            for (var c = 0; c < 4; c++) {
                var v = quads.getInt(quad * 4 + c) * 3;
                slant[v] += (float) heading.x;
                slant[v + 1] += (float) heading.y;
                slant[v + 2] += (float) heading.z;
            }
        }
        for (var bx = 0; bx < span; bx++) for (var by = 0; by < span; by++) for (var bz = 0; bz < span; bz++) {
            var block = (bx * span + by) * span + bz;
            if (kind[block] == 0 || buried(bx, by, bz)) continue;
            if (kind[block] == 1) probe(bx, by, bz, 0, 0, 0, 1, 1, 1);
            else for (var box : shapes.get(block)) probe(bx, by, bz, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
        }
    }

    private void probe(int bx, int by, int bz, double lx, double ly, double lz, double hx, double hy, double hz) {
        double x0 = base[0] + bx + lx, y0 = base[1] + by + ly, z0 = base[2] + bz + lz, x1 = base[0] + bx + hx, y1 = base[1] + by + hy, z1 = base[2] + bz + hz;
        var edge = Math.min(.05, Math.min(hx - lx, Math.min(hy - ly, hz - lz)) * .25);
        var open = false;
        for (var i = 0; i < 8 && !open; i++) open = jutting(
                (i & 1) == 1 ? x1 - edge : x0 + edge,
                (i >> 1 & 1) == 1 ? y1 - edge : y0 + edge,
                (i >> 2 & 1) == 1 ? z1 - edge : z0 + edge);
        if (!open) return;
        pokes.add(x0);
        pokes.add(y0);
        pokes.add(z0);
        pokes.add(x1);
        pokes.add(y1);
        pokes.add(z1);
    }

    private boolean jutting(double x, double y, double z) {
        int cx = Mth.floor((x - base[0]) / grain) - 1, cy = Mth.floor((y - base[1]) / grain) - 1, cz = Mth.floor((z - base[2]) / grain) - 1;
        var best = Double.MAX_VALUE;
        var gap = 0d;
        for (var i = cx - 2; i <= cx + 2; i++) for (var j = cy - 2; j <= cy + 2; j++) for (var k = cz - 2; k <= cz + 2; k++) {
            if (i < 0 || j < 0 || k < 0 || i >= cells || j >= cells || k >= cells) continue;
            var c = cell(i, j, k);
            if (!filled[c]) continue;
            var dx = x - vertices[c * 3];
            var dy = y - vertices[c * 3 + 1];
            var dz = z - vertices[c * 3 + 2];
            var d = dx * dx + dy * dy + dz * dz;
            if (d >= best) continue;
            best = d;
            gap = dx * slant[c * 3] + dy * slant[c * 3 + 1] + dz * slant[c * 3 + 2];
        }
        return best < Double.MAX_VALUE && gap > 0;
    }

    private void carve(BlockGetter level) {
        shapes.clear();
        var cell = grain * grain * grain;
        for (var bx = 0; bx < span; bx++) for (var by = 0; by < span; by++) for (var bz = 0; bz < span; bz++) {
            scan.set(base[0] + bx, base[1] + by, base[2] + bz);
            var state = level.getBlockState(scan);
            var full = state.isCollisionShapeFullBlock(level, scan);
            var shape = full ? null : state.getCollisionShape(level, scan);
            var boxes = shape == null || shape.isEmpty() ? null : shape.toAabbs();
            var block = (bx * span + by) * span + bz;
            kind[block] = (byte) (full ? 1 : boxes == null ? 0 : 2);
            if (boxes != null) shapes.put(block, boxes);
            var smooth = state.is(LiftsTags.SMOOTH_TERRAIN);
            for (var dx = 0; dx < detail; dx++) for (var dy = 0; dy < detail; dy++) for (var dz = 0; dz < detail; dz++) {
                var value = full ? 1f : 0f;
                if (!full && boxes != null) {
                    var total = 0d;
                    for (var box : boxes) total += overlap(box.minX, box.maxX, dx * grain)
                            * overlap(box.minY, box.maxY, dy * grain)
                            * overlap(box.minZ, box.maxZ, dz * grain);
                    value = (float) Math.min(1, total / cell);
                    if (value > 0) value = Math.max(value, .5f);
                }
                var i = at(bx * detail + dx, by * detail + dy, bz * detail + dz);
                density[i] = value;
                tagged[i] = value > 0 && smooth;
            }
        }
    }

    private void wedge() {
        System.arraycopy(density, 0, prior, 0, density.length);
        for (var x = 1; x < side - 1; x++) for (var y = 1; y < side - 1; y++) for (var z = 1; z < side - 1; z++) {
            var i = at(x, y, z);
            if (prior[i] >= .5f) continue;
            var filling = false;
            for (var u = 0; u < 6 && !filling; u++) for (var v = 0; v < 6 && !filling; v++) {
                var a = AXES[u];
                var b = AXES[v];
                if (a[0] * b[0] + a[1] * b[1] + a[2] * b[2] != 0) continue;
                var pa = at(x + a[0], y + a[1], z + a[2]);
                var pb = at(x + b[0], y + b[1], z + b[2]);
                if (prior[pa] < .5f || prior[pb] < .5f) continue;
                filling = true;
                density[i] = 1f;
                if (tagged[pa] || tagged[pb]) tagged[i] = true;
            }
        }
    }

    private void drown() {
        Arrays.fill(open, false);
        var tail = 0;
        for (var x = 0; x < side; x++) for (var y = 0; y < side; y++) for (var z = 0; z < side; z++) {
            var rim = x == 0 || y == 0 || z == 0 || x == side - 1 || y == side - 1 || z == side - 1;
            if (!rim && !(x == side / 2 && y == side / 2 && z == side / 2)) continue;
            var i = at(x, y, z);
            if (density[i] >= .5f || open[i]) continue;
            open[i] = true;
            queue[tail++] = i;
        }
        for (var head = 0; head < tail; head++) {
            var i = queue[head];
            var z = i % side;
            var y = i / side % side;
            var x = i / side / side;
            for (var axis : AXES) {
                var nx = x + axis[0];
                var ny = y + axis[1];
                var nz = z + axis[2];
                if (nx < 0 || ny < 0 || nz < 0 || nx >= side || ny >= side || nz >= side) continue;
                var j = at(nx, ny, nz);
                if (open[j] || density[j] >= .5f) continue;
                open[j] = true;
                queue[tail++] = j;
            }
        }
        for (var i = 0; i < density.length; i++) if (!open[i] && density[i] < .5f) density[i] = 1f;
    }

    private double overlap(double low, double high, double from) {
        return Math.max(0, Math.min(high, from + grain) - Math.max(low, from));
    }

    private boolean blocked(double x, double y, double z) {
        int bx = Mth.floor(x), by = Mth.floor(y), bz = Mth.floor(z);
        int lx = bx - base[0], ly = by - base[1], lz = bz - base[2];
        if (lx < 0 || ly < 0 || lz < 0 || lx >= span || ly >= span || lz >= span) return false;
        var block = (lx * span + ly) * span + lz;
        if (kind[block] != 2) return kind[block] == 1;
        for (var box : shapes.get(block)) if (box.contains(x - bx, y - by, z - bz)) return true;
        return false;
    }

    private boolean buried(int bx, int by, int bz) {
        for (var axis : AXES) {
            int nx = bx + axis[0], ny = by + axis[1], nz = bz + axis[2];
            if (nx < 0 || ny < 0 || nz < 0 || nx >= span || ny >= span || nz >= span) return false;
            if (kind[(nx * span + ny) * span + nz] != 1) return false;
        }
        return true;
    }

    private void spread() {
        System.arraycopy(tagged, 0, shown, 0, tagged.length);
        for (var axis = 0; axis < 3; axis++) {
            System.arraycopy(shown, 0, mask, 0, shown.length);
            for (var x = 0; x < side; x++) for (var y = 0; y < side; y++) for (var z = 0; z < side; z++) {
                var hit = false;
                for (var o = -detail; o <= detail && !hit; o++) hit = mask[at(
                        axis == 0 ? Mth.clamp(x + o, 0, side - 1) : x,
                        axis == 1 ? Mth.clamp(y + o, 0, side - 1) : y,
                        axis == 2 ? Mth.clamp(z + o, 0, side - 1) : z)];
                shown[at(x, y, z)] = hit;
            }
        }
    }

    private void contour() {
        quads.clear();
        Arrays.fill(filled, false);
        for (var x = 0; x < cells; x++) for (var y = 0; y < cells; y++) for (var z = 0; z < cells; z++) {
            if (!shown[at(x, y, z)]) continue;
            var here = at(x, y, z);
            cube[0] = density[here];
            cube[1] = density[here + plane];
            cube[2] = density[here + side];
            cube[3] = density[here + plane + side];
            cube[4] = density[here + 1];
            cube[5] = density[here + plane + 1];
            cube[6] = density[here + side + 1];
            cube[7] = density[here + plane + side + 1];
            var bits = 0;
            for (var n = 0; n < 8; n++) if (cube[n] >= .5f) bits |= 1 << n;
            if (bits == 0 || bits == 255) continue;
            double sx = 0d, sy = 0d, sz = 0d;
            var hits = 0;
            for (var e = 0; e < 12; e++) {
                var a = EDGES[e][0];
                var b = EDGES[e][1];
                if ((bits >> a & 1) == (bits >> b & 1)) continue;
                var t = cube[a] == cube[b] ? .5f : (.5f - cube[a]) / (cube[b] - cube[a]);
                sx += (a & 1) + t * ((b & 1) - (a & 1));
                sy += (a >> 1 & 1) + t * ((b >> 1 & 1) - (a >> 1 & 1));
                sz += (a >> 2 & 1) + t * ((b >> 2 & 1) - (a >> 2 & 1));
                hits++;
            }
            if (hits == 0) continue;
            var cell = cell(x, y, z);
            vertices[cell * 3] = (float) (base[0] + (x + sx / hits + .5) * grain);
            vertices[cell * 3 + 1] = (float) (base[1] + (y + sy / hits + .5) * grain);
            vertices[cell * 3 + 2] = (float) (base[2] + (z + sz / hits + .5) * grain);
            filled[cell] = true;
        }
        for (var x = 0; x < cells; x++) for (var y = 1; y < cells; y++) for (var z = 1; z < cells; z++)
            weave(x, y, z, x + 1, y, z, cell(x, y - 1, z - 1), cell(x, y, z - 1), cell(x, y, z), cell(x, y - 1, z));
        for (var x = 1; x < cells; x++) for (var y = 0; y < cells; y++) for (var z = 1; z < cells; z++)
            weave(x, y, z, x, y + 1, z, cell(x - 1, y, z - 1), cell(x - 1, y, z), cell(x, y, z), cell(x, y, z - 1));
        for (var x = 1; x < cells; x++) for (var y = 1; y < cells; y++) for (var z = 0; z < cells; z++)
            weave(x, y, z, x, y, z + 1, cell(x - 1, y - 1, z), cell(x, y - 1, z), cell(x, y, z), cell(x - 1, y, z));
    }

    private void weave(int x, int y, int z, int nx, int ny, int nz, int a, int b, int c, int d) {
        var inside = density[at(x, y, z)] >= .5f;
        if (inside == density[at(nx, ny, nz)] >= .5f) return;
        if (!filled[a] || !filled[b] || !filled[c] || !filled[d]) return;
        if (inside) {
            quads.add(a);
            quads.add(b);
            quads.add(c);
            quads.add(d);
            return;
        }
        quads.add(d);
        quads.add(c);
        quads.add(b);
        quads.add(a);
    }

    private void fair(float amount) {
        Arrays.fill(drift, 0f);
        Arrays.fill(tally, 0);
        for (var quad = 0; quad < quads(); quad++) for (var c = 0; c < 4; c++) {
            var v = quads.getInt(quad * 4 + c);
            var w = quads.getInt(quad * 4 + (c + 1) % 4);
            for (var k = 0; k < 3; k++) {
                drift[v * 3 + k] += vertices[w * 3 + k];
                drift[w * 3 + k] += vertices[v * 3 + k];
            }
            tally[v]++;
            tally[w]++;
        }
        for (var v = 0; v < tally.length; v++) {
            if (tally[v] == 0) continue;
            for (var k = 0; k < 3; k++) vertices[v * 3 + k] += amount * (drift[v * 3 + k] / tally[v] - vertices[v * 3 + k]);
        }
    }

    private int hcell(int x, int y, int z) {
        if (x < 0) x = 0;
        else if (x >= hedge) x = hedge - 1;
        if (y < 0) y = 0;
        else if (y >= hedge) y = hedge - 1;
        if (z < 0) z = 0;
        else if (z >= hedge) z = hedge - 1;
        return (x * hedge + y) * hedge + z;
    }

    private void pin() {
        Arrays.fill(pins, null);
        Arrays.fill(slopes, null);
        Arrays.fill(half, false);
        var edge = span + 2;
        for (var bx = -1; bx <= span; bx++) for (var by = -1; by <= span; by++) for (var bz = -1; bz <= span; bz++)
            for (var i = 0; i < 8; i++) {
                if (!blocked(base[0] + bx + ((i & 1) == 1 ? .75 : .25),
                        base[1] + by + ((i >> 1 & 1) == 1 ? .75 : .25),
                        base[2] + bz + ((i >> 2 & 1) == 1 ? .75 : .25))) continue;
                half[hcell((bx + 2) * 2 + (i & 1), (by + 2) * 2 + (i >> 1 & 1), (bz + 2) * 2 + (i >> 2 & 1))] = true;
            }
        for (var bx = -1; bx <= span; bx++) for (var by = -1; by <= span; by++) for (var bz = -1; bz <= span; bz++) {
            var hits = 0;
            for (var i = 0; i < 8; i++) {
                octant[i] = half[hcell((bx + 2) * 2 + (i & 1), (by + 2) * 2 + (i >> 1 & 1), (bz + 2) * 2 + (i >> 2 & 1))];
                if (octant[i]) hits++;
            }
            if (hits == 0) continue;
            var full = hits == 8;
            Arrays.fill(marked, false);
            var n = 0;
            for (var o = 0; o < 8; o++) {
                if (full ? o > 0 : !octant[o]) continue;
                var size = full ? 1f : .5f;
                var qx = full ? 0 : (o & 1) == 1 ? .5f : 0;
                var qy = full ? 0 : (o >> 1 & 1) == 1 ? .5f : 0;
                var qz = full ? 0 : (o >> 2 & 1) == 1 ? .5f : 0;
                for (var i = 0; i < 8; i++) {
                    var ox = qx + ((i & 1) == 1 ? size : 0);
                    var oy = qy + ((i >> 1 & 1) == 1 ? size : 0);
                    var oz = qz + ((i >> 2 & 1) == 1 ? size : 0);
                    var spot = ((int) (ox * 2) * 3 + (int) (oy * 2)) * 3 + (int) (oz * 2);
                    if (marked[spot]) continue;
                    marked[spot] = true;
                    corners[n++] = bx + ox;
                    corners[n++] = by + oy;
                    corners[n++] = bz + oz;
                }
            }
            var slot = Arrays.copyOf(corners, n);
            var lean = new float[slot.length];
            for (var i = 0; i < slot.length; i += 3) {
                var ix = Math.round((slot[i] + 2) * 2);
                var iy = Math.round((slot[i + 1] + 2) * 2);
                var iz = Math.round((slot[i + 2] + 2) * 2);
                var sx = 0f;
                var sy = 0f;
                var sz = 0f;
                for (var o = 0; o < 8; o++) {
                    var dx = (o & 1) == 1 ? 1 : -1;
                    var dy = (o >> 1 & 1) == 1 ? 1 : -1;
                    var dz = (o >> 2 & 1) == 1 ? 1 : -1;
                    var solid = half[hcell(dx < 0 ? ix - 1 : ix, dy < 0 ? iy - 1 : iy, dz < 0 ? iz - 1 : iz)];
                    var w = solid ? -1 : 1;
                    sx += dx * w;
                    sy += dy * w;
                    sz += dz * w;
                }
                var len = Math.sqrt(sx * sx + sy * sy + sz * sz);
                if (len <= 1.0E-9) continue;
                lean[i] = (float) (sx / len);
                lean[i + 1] = (float) (sy / len);
                lean[i + 2] = (float) (sz / len);
            }
            int i = ((bx + 1) * edge + by + 1) * edge + bz + 1;
            pins[i] = slot;
            slopes[i] = lean;
        }
    }

    private void relax() {
        Arrays.fill(gain, 0f);
        Arrays.fill(tally, 0);
        var edge = span + 2;
        for (var quad = 0; quad < quads(); quad++) {
            heading.set(vx(quad, 2) - vx(quad, 0), vy(quad, 2) - vy(quad, 0), vz(quad, 2) - vz(quad, 0))
                    .cross(vx(quad, 3) - vx(quad, 1), vy(quad, 3) - vy(quad, 1), vz(quad, 3) - vz(quad, 1));
            if (heading.lengthSquared() <= 1.0E-12) continue;
            heading.normalize();
            var mx = 0d;
            var my = 0d;
            var mz = 0d;
            for (var c = 0; c < 4; c++) {
                var v = quads.getInt(quad * 4 + c) * 3;
                mx += vertices[v];
                my += vertices[v + 1];
                mz += vertices[v + 2];
            }
            mx = mx / 4 - base[0];
            my = my / 4 - base[1];
            mz = mz / 4 - base[2];
            var deficit = 0d;
            for (var bx = Mth.floor(mx - 1.5); bx <= Mth.floor(mx + 1.5); bx++)
                for (var by = Mth.floor(my - 1.5); by <= Mth.floor(my + 1.5); by++)
                    for (var bz = Mth.floor(mz - 1.5); bz <= Mth.floor(mz + 1.5); bz++) {
                        if (bx < -1 || by < -1 || bz < -1 || bx > span || by > span || bz > span) continue;
                        var px = bx + .5 - mx;
                        var py = by + .5 - my;
                        var pz = bz + .5 - mz;
                        if (px * heading.x + py * heading.y + pz * heading.z + .8660254037844386 <= deficit) continue;
                        var gx = Math.max(0, Math.abs(px) - .5);
                        var gy = Math.max(0, Math.abs(py) - .5);
                        var gz = Math.max(0, Math.abs(pz) - .5);
                        if (gx * gx + gy * gy + gz * gz > 2.25) continue;
                        var key = ((bx + 1) * edge + by + 1) * edge + bz + 1;
                        var slot = pins[key];
                        if (slot == null) continue;
                        var lean = slopes[key];
                        for (var i = 0; i < slot.length; i += 3) {
                            var ax = slot[i] - mx;
                            var ay = slot[i + 1] - my;
                            var az = slot[i + 2] - mz;
                            if (ax * ax + ay * ay + az * az > 2.25) continue;
                            var d = ax * heading.x + ay * heading.y + az * heading.z;
                            if (d <= deficit) continue;
                            if (lean[i] * heading.x + lean[i + 1] * heading.y + lean[i + 2] * heading.z < .9) continue;
                            deficit = d;
                        }
                    }
            if (deficit <= 0) continue;
            if (deficit > .12) deficit = .12;
            for (var c = 0; c < 4; c++) {
                var v = quads.getInt(quad * 4 + c);
                gain[v * 3] += (float) (heading.x * deficit);
                gain[v * 3 + 1] += (float) (heading.y * deficit);
                gain[v * 3 + 2] += (float) (heading.z * deficit);
                tally[v]++;
            }
        }
        for (var v = 0; v < tally.length; v++) {
            if (tally[v] == 0) continue;
            vertices[v * 3] += gain[v * 3] / tally[v];
            vertices[v * 3 + 1] += gain[v * 3 + 1] / tally[v];
            vertices[v * 3 + 2] += gain[v * 3 + 2] / tally[v];
        }
    }

    private void settle() {
        for (var quad = 0; quad < quads(); quad++) {
            normal(quad, heading);
            for (var c = 0; c < 4; c++) {
                var v = quads.getInt(quad * 4 + c) * 3;
                cursor.set(vertices[v], vertices[v + 1], vertices[v + 2]);
                if (!blocked(cursor.x, cursor.y, cursor.z)) continue;
                escape.set(heading);
                var best = reach(cursor, escape.x, escape.y, escape.z);
                if (best < 0) for (var axis : AXES) {
                    var d = reach(cursor, axis[0], axis[1], axis[2]);
                    if (d < 0 || best >= 0 && d >= best) continue;
                    best = d;
                    escape.set(axis[0], axis[1], axis[2]);
                }
                if (best <= 0) continue;
                vertices[v] += (float) (escape.x * best);
                vertices[v + 1] += (float) (escape.y * best);
                vertices[v + 2] += (float) (escape.z * best);
            }
        }
    }

    private double reach(Vector3d from, double dx, double dy, double dz) {
        var step = grain * .25;
        var out = -1d;
        for (var d = step; d <= 1.5; d += step)
            if (!blocked(from.x + dx * d, from.y + dy * d, from.z + dz * d)) {
                out = d;
                break;
            }
        if (out < 0) return -1;
        var lo = out - step;
        var hi = out;
        for (var i = 0; i < 8; i++) {
            var mid = (lo + hi) / 2;
            if (blocked(from.x + dx * mid, from.y + dy * mid, from.z + dz * mid)) lo = mid;
            else hi = mid;
        }
        return hi + 1.0E-4;
    }

    private void tether() {
        var slack = grain * .5 + .5;
        for (var x = 0; x < cells; x++) for (var y = 0; y < cells; y++) for (var z = 0; z < cells; z++) {
            var c = cell(x, y, z);
            if (!filled[c]) continue;
            var hx = base[0] + (x + 1) * grain;
            var hy = base[1] + (y + 1) * grain;
            var hz = base[2] + (z + 1) * grain;
            vertices[c * 3] = (float) Mth.clamp(vertices[c * 3], hx - slack, hx + slack);
            vertices[c * 3 + 1] = (float) Mth.clamp(vertices[c * 3 + 1], hy - slack, hy + slack);
            vertices[c * 3 + 2] = (float) Mth.clamp(vertices[c * 3 + 2], hz - slack, hz + slack);
        }
    }

    private int at(int x, int y, int z) {
        return (x * side + y) * side + z;
    }

    private int cell(int x, int y, int z) {
        return (x * cells + y) * cells + z;
    }
}
