package dev.lopyluna.create_lnl.content.blocks.connectors;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ConnectorBE extends SmartBlockEntity implements IConnection<ConnectorBE> {
    protected final Set<BlockPos> connections = new HashSet<>();

    public ConnectorBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    @Override
    public String lifts$getColor() {
        return connections.isEmpty() ?  IConnection.DEFAULT_COLOR : IConnection.ACTIVE_COLOR;
    }

    @Override
    public boolean lifts$isStatic() {
        return false;
    }

    @Override
    public Set<BlockPos> lifts$getConnections() {
        return connections;
    }

    @Override
    public boolean lifts$containsConnection(BlockPos pos) {
        return connections.contains(pos);
    }

    @Override
    public void lifts$addConnectionRaw(BlockPos pos, boolean update) {
        connections.add(pos);
        if (update) notifyUpdate();
    }

    @Override
    public void lifts$removeConnectionRaw(BlockPos pos, boolean update) {
        connections.remove(pos);
        if (update) notifyUpdate();
    }

    @Override
    public void lifts$clearConnectionRaw(boolean update) {
        connections.clear();
        if (update) notifyUpdate();
    }

    @Override
    public void read(CompoundTag nbt, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(nbt, provider, clientPacket);
        readI(this, level, nbt, provider, (level != null && level.isClientSide) || clientPacket);
    }

    @Override
    public void writeSafe(CompoundTag nbt, HolderLookup.Provider provider) {
        super.writeSafe(nbt, provider);
        writeI(this, level, nbt, provider, level != null && level.isClientSide);
    }

    @Override
    protected void write(CompoundTag nbt, HolderLookup.Provider provider, boolean clientPacket) {
        super.write(nbt, provider, clientPacket);
        writeI(this, level, nbt, provider, (level != null && level.isClientSide) || clientPacket);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        loadI(this, level);
    }

    @Override
    public void remove() {
        super.remove();
        removeI(this, level);
    }
}
