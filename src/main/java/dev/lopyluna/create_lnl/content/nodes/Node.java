package dev.lopyluna.create_lnl.content.nodes;

import dev.lopyluna.create_lnl.content.nodes.packets.NodeSyncSTC;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

@SuppressWarnings("unused")
public class Node {
    public boolean anchored;
    public int missing;
    public final Set<Integer> loose = new TreeSet<>();
    public final Map<String, Set<Key>> outputs = new HashMap<>();
    public final Map<String, Set<Key>> inputs = new HashMap<>();
    public final Map<Key, Integer> order = new HashMap<>();
    public final Map<String, Signal> published = new HashMap<>();
    public final Map<String, Signal> applied = new HashMap<>();

    public Set<Key> outputs(String port) {
        return outputs.computeIfAbsent(port, p -> new HashSet<>());
    }

    public Set<Key> inputs(String port) {
        return inputs.computeIfAbsent(port, p -> new HashSet<>());
    }

    public boolean empty() {
        if (anchored || !loose.isEmpty()) return false;
        for (var set : outputs.values()) if (!set.isEmpty()) return false;
        for (var set : inputs.values()) if (!set.isEmpty()) return false;
        return true;
    }

    public record Key(BlockPos pos, String port) {
        public static final StreamCodec<ByteBuf, Key> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Key::pos,
                ByteBufCodecs.STRING_UTF8, Key::port,
                Key::new
        );
    }

    public record Port(String id, Channel channel, Flow flow, Vec3 offset, int color, Merge merge) {
        public static Port in(String id, Channel channel, Vec3 offset) {
            return new Port(id, channel, Flow.IN, offset, channel.color, Merge.STRONGEST);
        }

        public static Port out(String id, Channel channel, Vec3 offset) {
            return new Port(id, channel, Flow.OUT, offset, channel.color, Merge.STRONGEST);
        }

        public static Port node(String id, Channel channel, Vec3 offset) {
            return new Port(id, channel, Flow.BOTH, offset, channel.color, Merge.STRONGEST);
        }

        public Port colored(int color) {
            return new Port(id, channel, flow, offset, color, merge);
        }

        public Port merging(Merge merge) {
            return new Port(id, channel, flow, offset, color, merge);
        }

        public Vec3 center(BlockPos pos) {
            return pos.getCenter().add(offset);
        }

        public Key key(BlockPos pos) {
            return new Key(pos.immutable(), id);
        }
    }

    public enum Channel {
        REDSTONE(15, 0xFF3838),
        ROTATION(256, 0x5EFE6D),
        ITEM(64, 0xF8A431),
        FLUID(1000, 0x4A9BFF);

        public final double max;
        public final int color;

        Channel(double max, int color) {
            this.max = max;
            this.color = color;
        }

        public Signal of(double value) {
            if (this == REDSTONE) return new Signal.Simple(this, Mth.clamp(Math.round(value), 0, (long) max));
            return new Signal.Simple(this, Mth.clamp(value, -max, max));
        }

        public Signal zero() {
            return of(0);
        }

        public Signal merge(@Nullable Signal a, @Nullable Signal b) {
            if (a == null) return b;
            if (b == null) return a;
            return Math.abs(b.value()) > Math.abs(a.value()) ? b : a;
        }
    }

    public enum Flow {
        IN,
        OUT,
        BOTH;

        public boolean canOutput() {
            return this != IN;
        }

        public boolean canInput() {
            return this != OUT;
        }
    }

    public interface Signal {
        Channel channel();
        double value();

        default boolean active() {
            return value() != 0;
        }

        default int strength() {
            return (int) Math.round(value());
        }

        default float speed() {
            return (float) value();
        }

        default Signal as(Channel target) {
            if (channel() == target) return this;
            return target.of(value() / channel().max * target.max);
        }

        record Simple(Channel channel, double value) implements Signal {}
    }

    public interface Merge {
        Merge STRONGEST = (channel, inputs) -> {
            Signal best = null;
            for (var signal : inputs) if (best == null || Math.abs(signal.value()) > Math.abs(best.value())) best = signal;
            return best == null ? channel.zero() : best;
        };

        Signal reduce(Channel channel, List<Signal> inputs);
    }

    public interface Host {
        List<Port> ports(Level level, BlockPos pos, BlockState state);

        default Signal read(Level level, BlockPos pos, BlockState state, Port port) {
            return port.channel().zero();
        }

        default void write(Level level, BlockPos pos, BlockState state, Port port, Signal signal) {}

        default boolean implicit() {
            return true;
        }
    }

    public static class Graphs {
        public static final NodeGraph CLIENT = new NodeGraph();

        public static NodeGraph get(Level level) {
            if (!(level instanceof ServerLevel server)) return CLIENT;
            var data = server.getDataStorage().computeIfAbsent(Data.FACTORY, "create_lnl_nodes");
            if (data.graph.listener == null) data.graph.listener = pos -> {
                data.setDirty();
                data.graph.pending.add(pos);
            };
            return data.graph;
        }

        public static void tick(ServerLevel level) {
            var graph = get(level);
            graph.tick(level);
            flush(level);
        }

        public static void flush(Level level) {
            if (!(level instanceof ServerLevel server)) return;
            var graph = get(server);
            if (graph.pending.isEmpty()) return;
            var entries = new ArrayList<NodeSyncSTC.Entry>(graph.pending.size());
            for (var pos : graph.pending) entries.add(NodeSyncSTC.of(graph, pos));
            graph.pending.clear();
            var packet = new NodeSyncSTC(false, entries);
            for (var player : server.players()) CatnipServices.NETWORK.sendToClient(player, packet);
        }

        public static void sync(ServerPlayer player) {
            var graph = get(player.serverLevel());
            var entries = new ArrayList<NodeSyncSTC.Entry>(graph.nodes.size());
            for (var pos : graph.nodes.keySet()) entries.add(NodeSyncSTC.of(graph, pos));
            CatnipServices.NETWORK.sendToClient(player, new NodeSyncSTC(true, entries));
        }
    }

    public static class Data extends SavedData {
        public static final Factory<Data> FACTORY = new Factory<>(Data::new, (tag, registries) -> {
            var data = new Data();
            data.graph.read(tag);
            return data;
        });

        public final NodeGraph graph = new NodeGraph();

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            return graph.write(tag);
        }
    }

    public static class Redstone {
        private static final Map<Level, Long2IntMap> POWER = new WeakHashMap<>();
        private static int powered;
        public static boolean any;

        public static int get(Level level, BlockPos pos) {
            var map = POWER.get(level);
            return map == null ? 0 : map.get(pos.asLong());
        }

        public static void set(Level level, BlockPos pos, int power) {
            var map = POWER.get(level);
            if (map == null && power <= 0) return;
            if (map == null) {
                map = new Long2IntOpenHashMap();
                POWER.put(level, map);
            }
            var key = pos.asLong();
            var old = map.get(key);
            if (old == power) return;
            if (power <= 0) map.remove(key);
            else map.put(key, power);
            if (old <= 0 && power > 0) powered++;
            else if (old > 0 && power <= 0) powered--;
            any = powered > 0;
            var block = level.getBlockState(pos).getBlock();
            level.neighborChanged(pos, block, pos);
            level.updateNeighborsAt(pos, block);
        }
    }

    public static class Tracker {
        public static final Set<BlockPos> TRACKED = new HashSet<>();

        public static void track(BlockEntity be) {
            if (be.getLevel() == null || !be.getLevel().isClientSide) return;
            if (be instanceof Host host && host.implicit()) {
                TRACKED.add(be.getBlockPos().immutable());
                return;
            }
            if (be.getBlockState().getBlock() instanceof Host host && host.implicit()) TRACKED.add(be.getBlockPos().immutable());
        }

        public static void untrack(BlockPos pos) {
            TRACKED.remove(pos);
        }

        public static void clear() {
            TRACKED.clear();
        }

        public static Set<BlockPos> positions() {
            var all = new HashSet<>(TRACKED);
            all.addAll(Graphs.CLIENT.nodes.keySet());
            return all;
        }
    }

    public static class Schematics {
        private static final Map<Level, Batch> PENDING = new WeakHashMap<>();
        public static boolean any;

        public static void stash(Level level, Map<BlockPos, List<Placement>> nodes) {
            if (nodes.isEmpty()) return;
            PENDING.put(level, new Batch(new HashMap<>(nodes), new ArrayList<>()));
            any = true;
        }

        public static void place(Level level, BlockPos pos) {
            if (!any) return;
            var batch = PENDING.get(level);
            if (batch == null) return;
            var group = batch.nodes().remove(pos);
            if (group != null) for (var placement : group) {
                NodePayload.load(level, placement.pos(), placement.tag(), null);
                batch.links().addAll(NodePayload.wires(placement.pos(), placement.tag(), target -> target, null));
            }
            if (!batch.links().isEmpty()) {
                var graph = Graphs.get(level);
                batch.links().removeIf(wire -> {
                    if (!graph.link(level, wire.from(), wire.to())) return false;
                    graph.order(wire.from(), wire.to(), wire.order());
                    return true;
                });
            }
            if (group != null || batch.links().isEmpty()) Graphs.flush(level);
            if (batch.nodes().isEmpty() && batch.links().isEmpty()) PENDING.remove(level);
        }

        public record Placement(BlockPos pos, CompoundTag tag) {}

        private record Batch(Map<BlockPos, List<Placement>> nodes, List<NodePayload.Wire> links) {}
    }

    public interface Template {
        Map<BlockPos, CompoundTag> lifts$nodes();
    }
}
