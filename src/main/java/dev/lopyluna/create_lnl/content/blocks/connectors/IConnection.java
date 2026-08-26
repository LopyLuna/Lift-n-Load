package dev.lopyluna.create_lnl.content.blocks.connectors;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.ryanhcode.sable.Sable;
import net.createmod.catnip.codecs.CatnipCodecUtils;
import net.createmod.catnip.codecs.CatnipCodecs;
import net.createmod.catnip.data.TriState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public interface IConnection<E extends SmartBlockEntity> {

    default ConnectionType getConnectType() {
        return ConnectionType.NONE;
    }

    HashMap<BlockPos, Connection.Self> connections = new HashMap<>();
    String DEFAULT_COLOR = "9696A0";
    String ACTIVE_COLOR = "007BFF";

    boolean lifts$isStatic();

    Set<BlockPos> lifts$getConnections();
    boolean lifts$containsConnection(BlockPos pos);
    void lifts$addConnectionRaw(BlockPos pos, boolean update);
    void lifts$removeConnectionRaw(BlockPos pos, boolean update);
    void lifts$clearConnectionRaw(boolean update);

    //TRUE = ADDED
    //DEFAULT = NORMAL UPDATE
    //FALSE = REMOVED
    default void lifts$onConnectionUpdate(BlockPos fromPos, IConnection<?> from, TriState tri) {
        isOutOfRange(fromPos, from);
    }

    default boolean isOutOfRange(BlockPos fromPos, IConnection<?> from) {
        var level = ((SmartBlockEntity)this).getLevel();
        var pos = ((SmartBlockEntity)this).getBlockPos();
        var outOfRange = Sable.HELPER.projectOutOfSubLevel(level, fromPos.getCenter()).distanceTo(Sable.HELPER.projectOutOfSubLevel(level, pos.getCenter())) >= 32;
        if (outOfRange) removeConnection((BlockEntity)this, level, fromPos, true);
        return outOfRange;
    }

    default void updateConnections(@Nullable Level level, BlockPos from) {
        if (level == null) return;
        for (var pos : lifts$getConnections()) if (level.getBlockEntity(pos) instanceof IConnection<?> connection) connection.lifts$onConnectionUpdate(from, this, TriState.DEFAULT);
    }

    default void updateConnections(@Nullable Level level, BlockPos from, TriState tri) {
        if (level == null) return;
        for (var pos : lifts$getConnections()) if (level.getBlockEntity(pos) instanceof IConnection<?> connection) connection.lifts$onConnectionUpdate(from, this, tri);
    }

    default boolean canConnect(@Nullable Level level, BlockPos otherPos, IConnection<?> other) {
        return (getConnectType() == ConnectionType.OUT) != (other.getConnectType() == ConnectionType.OUT);
    }

    default void addConnection(BlockEntity be, @Nullable Level level, BlockPos pos, boolean update, boolean updateOther) {
        if (level == null) return;
        if (!(level.getBlockEntity(pos) instanceof IConnection<?> c && ConnectorUtils.canConnect(level, be.getBlockPos(), this, pos, c))) return;
        lifts$addConnectionRaw(pos, update);
        c.lifts$addConnectionRaw(be.getBlockPos(), updateOther);
        c.lifts$onConnectionUpdate(be.getBlockPos(), this, TriState.TRUE);
    }
    default void addConnection(BlockEntity be, @Nullable Level level, BlockPos pos, boolean update) { addConnection(be, level, pos, update, update); }
    default void removeConnection(BlockEntity be, @Nullable Level level, BlockPos pos, boolean update, boolean updateOther) {
        if (level != null && level.getBlockEntity(pos) instanceof IConnection<?> c) c.lifts$onConnectionUpdate(be.getBlockPos(), this, TriState.FALSE);
        lifts$removeConnectionRaw(pos, update);
        if (level != null && level.getBlockEntity(pos) instanceof IConnection<?> connection) connection.lifts$removeConnectionRaw(be.getBlockPos(), updateOther);
    }
    default void removeConnection(BlockEntity be, @Nullable Level level, BlockPos pos, boolean update) { removeConnection(be, level, pos, update, update); }
    default void clearConnection(BlockEntity be, @Nullable Level level, boolean update, boolean updateOther) {
        updateConnections(level, be.getBlockPos(), TriState.FALSE);
        for (var c : lifts$getConnections()) if (level != null && level.getBlockEntity(c) instanceof IConnection<?> connection) connection.lifts$removeConnectionRaw(be.getBlockPos(), updateOther);
        lifts$clearConnectionRaw(update);
    }
    default void clearConnection(BlockEntity be, @Nullable Level level, boolean update) { clearConnection(be, level, update, update); }

    default void addConnectionRawNoUpdate(BlockPos pos) { lifts$addConnectionRaw(pos, false); }
    default void removeConnectionRawNoUpdate(BlockPos pos) { lifts$removeConnectionRaw(pos, false); }
    default void clearConnectionRawNoUpdate() { lifts$clearConnectionRaw(false); }
    default void addConnectionNoUpdate(BlockEntity be, @Nullable Level level, BlockPos pos) { addConnection(be, level, pos, false); }
    default void removeConnectionNoUpdate(BlockEntity be, @Nullable Level level, BlockPos pos) { removeConnection(be, level, pos, false); }
    default void clearConnectionNoUpdate(BlockEntity be, @Nullable Level level) { clearConnection(be, level, false); }

    default boolean notColorable() { return true; }

    default void setColor(String color) {}
    String lifts$getColor();

    default int rgb() {
        var clr = lifts$getColor();
        return Integer.parseInt((clr == null ? DEFAULT_COLOR : clr), 16);
    }

    default void readI(E be, @Nullable Level level, CompoundTag nbt, HolderLookup.Provider provider, boolean client) {
        clearConnectionRawNoUpdate();
        CatnipCodecUtils.decode(CatnipCodecs.set(BlockPos.CODEC), provider, nbt.get("Connections")).ifPresent(s -> s.forEach(this::addConnectionRawNoUpdate));

        if (notColorable()) return;
        if (!nbt.contains("Color")) return;
        var color = nbt.getString("Color").replace("#", "").replace("0x", "");
        if (!color.matches("[0-9A-Fa-f]{6}")) color = DEFAULT_COLOR;
        setColor(color.toUpperCase(Locale.ROOT));
        if (client) updateConnection(be);
    }

    default void writeI(E be, @Nullable Level level, CompoundTag nbt, HolderLookup.Provider provider, boolean client) {
        nbt.put("Connections", CatnipCodecUtils.encode(CatnipCodecs.set(BlockPos.CODEC), provider, lifts$getConnections()).orElseThrow());

        if (notColorable()) return;
        var color = lifts$getColor();
        var oldColor = color;
        if (!color.matches("[0-9A-Fa-f]{6}")) color = DEFAULT_COLOR;
        nbt.putString("Color", color.toUpperCase(Locale.ROOT));
        if (!client && !color.equals(oldColor)) be.notifyUpdate();
    }

    default void loadI(E be, @Nullable Level level) {
        if (level == null || level.isClientSide) return;
        if (checkConnections(be, level)) be.notifyUpdate();
    }


    default boolean checkConnections(E be, @Nullable Level level) {
        if (level == null || level.isClientSide) return false;
        var connections = lifts$getConnections();
        if (connections.isEmpty()) return false;
        var pos = be.getBlockPos();
        var newList = connections.stream().filter(p -> !level.isLoaded(p) || level.getBlockEntity(p) instanceof IConnection<?> c && ConnectorUtils.canConnect(level, pos, this, p, c)).collect(Collectors.toSet());
        if (connections.size() == newList.size()) return false;
        clearConnection(be, level, false, true);
        newList.forEach(p -> addConnection(be, level, p, false, true));
        return true;
    }

    default void removeI(E be, @Nullable Level level) {
        if (level == null) return;
        var pos = be.getBlockPos();
        for (var off : lifts$getConnections()) if (level.getBlockEntity(off) instanceof SmartBlockEntity sbe && sbe instanceof IConnection<?> connection) connection.removeConnectionNoUpdate(sbe, level, pos);
        clearConnection((BlockEntity) this, level, true);
    }

    default void updateConnection(BlockEntity be) {
        var pos = be.getBlockPos();
        IConnection.connections.remove(pos);
        IConnection.connections.put(pos, lifts$isStatic() ? Connection.of(be, rgb(), Connection.Type.getType(be)) : Connection.of(be, this::rgb, () -> Connection.Type.getType(be)));
    }
    default void updateConnection(BlockEntity be, BlockPos pos) {
        IConnection.connections.remove(pos);
        IConnection.connections.put(pos, lifts$isStatic() ? Connection.of(be, rgb(), Connection.Type.getType(be)) : Connection.of(be, this::rgb, () -> Connection.Type.getType(be)));
    }
    default void updateConnection(@Nullable Level level, BlockPos pos) {
        if (level == null) return;
        var be = level.getBlockEntity(pos);
        if (be == null) return;
        IConnection.connections.remove(pos);
        IConnection.connections.put(pos, lifts$isStatic() ? Connection.of(be, rgb(), Connection.Type.getType(be)) : Connection.of(be, this::rgb, () -> Connection.Type.getType(be)));
    }
}
