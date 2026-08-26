package dev.lopyluna.create_lnl.content.blocks.node_link;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.lopyluna.create_lnl.content.blocks.connectors.ConnectionType;
import dev.lopyluna.create_lnl.content.blocks.connectors.ConnectorBE;
import dev.lopyluna.create_lnl.content.blocks.connectors.IConnection;
import net.createmod.catnip.codecs.CatnipCodecUtils;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.data.TriState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class NodeLinkBE extends ConnectorBE {
    private static final Codec<HashMap<BlockPos, Integer>> RECEIVING_CODEC = Codec.list(
            RecordCodecBuilder.<Map.Entry<BlockPos, Integer>>create(instance -> instance.group(
                    BlockPos.CODEC.fieldOf("Pos").forGetter(Map.Entry::getKey),
                    Codec.INT.fieldOf("Strength").forGetter(Map.Entry::getValue)
            ).apply(instance, Map::entry))).xmap(list -> list.stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
            (a, b) -> b, HashMap::new)), map -> new ArrayList<>(map.entrySet()));

    public int clr = -1;
    public int strength = 0;
    public boolean invert = false;
    public HashMap<BlockPos, Integer> receivingPos = new HashMap<>();

    public Direction facing;
    public boolean receiver;

    public NodeLinkBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        update(state);
    }

    @Override
    public void read(CompoundTag nbt, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(nbt, provider, clientPacket);
        if (nbt.contains("Invert")) invert = nbt.getBoolean("Invert");
        if (nbt.contains("Color")) clr = nbt.getInt("Color");
        if ((level != null && level.isClientSide || clientPacket) && nbt.contains("Color")) updateConnection(this);
        strength = nbt.getInt("Strength");
        receivingPos.clear();
        if (nbt.contains("ReceivingPos")) CatnipCodecUtils.decode(RECEIVING_CODEC, provider, nbt.get("ReceivingPos")).ifPresent(receivingPos::putAll);
    }

    @Override
    protected void write(CompoundTag nbt, HolderLookup.Provider provider, boolean clientPacket) {
        super.write(nbt, provider, clientPacket);
        if (clr >= 0) nbt.putInt("Color", clr);
        if (receiver) nbt.putBoolean("Invert", invert);
        nbt.putInt("Strength", strength);
        if (receivingPos != null && !receivingPos.isEmpty()) nbt.put("ReceivingPos", CatnipCodecUtils.encode(RECEIVING_CODEC, provider, receivingPos).orElseThrow());
    }

    public int getStrength() {
        return Mth.clamp(invert ? 15 - strength : strength, 0, 15);
    }

    @Override
    public void lifts$onConnectionUpdate(BlockPos fromPos, IConnection<?> from, TriState tri) {
        if (isOutOfRange(fromPos, from)) return;
        if (!receiver) return;
        var old = strength;
        if (from instanceof NodeLinkBE be) {
            if (be.strength <= 0 || tri == TriState.FALSE) receivingPos.remove(fromPos);
            else receivingPos.put(fromPos, be.strength);
        }
        if (tri == TriState.FALSE || receivingPos.isEmpty()) {
            strength = 0;
            if (level != null && old != strength) level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            notifyUpdate();
            return;
        }
        strength = receivingPos.entrySet().stream().reduce((a, b) -> a.getValue() >= b.getValue() ? a : b).orElse(null).getValue();
        if (level != null && old != strength) level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        notifyUpdate();
    }

    public void checkStrength() {
        if (receiver || level == null) return;
        var power = getPower(level, worldPosition);
        if (strength != power) {
            strength = power;
            updateConnections(level, worldPosition, TriState.DEFAULT);
            notifyUpdate();
        }
    }

    private int getPower(Level level, BlockPos pos) {
        int power = 0;
        for (var dir : Iterate.directions) power = Math.max(level.getSignal(pos.relative(dir), dir), power);
        for (var dir : Iterate.directions) if (facing.getOpposite() != dir) power = Math.max(level.getSignal(pos.relative(dir), Direction.UP), power);
        return power;
    }

    @Override
    public ConnectionType getConnectType() {
        return receiver ? ConnectionType.IN : ConnectionType.OUT;
    }

    public void update(@Nullable BlockState state) {
        if (state == null) state = getBlockState();
        facing = state.getValue(NodeLinkBlock.FACING);
        var old = receiver;
        receiver = state.getValue(NodeLinkBlock.RECEIVER);
        if (!receiver) receivingPos.clear();
        if (old != receiver) strength = 0;
        checkStrength();
        checkConnections(this, level);
        if (level != null && level.isClientSide) updateConnection(this);
    }

    @Override
    public boolean canConnect(Level level, BlockPos otherPos, IConnection<?> other) {
        return (other instanceof NodeLinkBE be && receiver != be.receiver) || (!receiver && other instanceof NodeLinkReceiver);
    }

    @Override
    public void lifts$removeConnectionRaw(BlockPos pos, boolean update) {
        super.lifts$removeConnectionRaw(pos, update);
        receivingPos.remove(pos);
        if (!receiver) { checkStrength(); return; }
        var old = strength;
        if (receivingPos.isEmpty()) {
            strength = 0;
            if (level != null && old != strength) level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            notifyUpdate();
            return;
        }
        strength = receivingPos.entrySet().stream().reduce((a, b) -> a.getValue() >= b.getValue() ? a : b).orElse(null).getValue();
        if (level != null && old != strength) level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        notifyUpdate();
    }

    @Override
    public void lifts$clearConnectionRaw(boolean update) {
        super.lifts$clearConnectionRaw(update);
        receivingPos.clear();
        var old = strength;
        strength = 0;
        if (level != null && old != strength) level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        notifyUpdate();
    }

    @Override
    public String lifts$getColor() {
        return receiver ? "F8317E" : "FF3838";
    }

    @Override
    public int rgb() {
        return clr == -1 ? super.rgb() : clr;
    }

    @Override
    public boolean lifts$isStatic() {
        return true;
    }
}
