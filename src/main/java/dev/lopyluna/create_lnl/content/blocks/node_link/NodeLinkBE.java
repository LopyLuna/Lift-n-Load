package dev.lopyluna.create_lnl.content.blocks.node_link;

import dev.lopyluna.create_lnl.content.nodes.Node;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

@SuppressWarnings("deprecation")
public class NodeLinkBE extends SmartBlockEntity implements Node.Host {
    public int clr = -1;
    public int strength = 0;
    public boolean invert = false;
    public Direction facing;
    public boolean receiver;

    public NodeLinkBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        update(state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    @Override
    public List<Node.Port> ports(Level level, BlockPos pos, BlockState state) {
        var port = receiver ? Node.Port.in("in", Node.Channel.REDSTONE, Vec3.ZERO) : Node.Port.out("out", Node.Channel.REDSTONE, Vec3.ZERO);
        return List.of(port.colored(rgb()));
    }

    @Override
    public Node.Signal read(Level level, BlockPos pos, BlockState state, Node.Port port) {
        return Node.Channel.REDSTONE.of(strength);
    }

    @Override
    public void write(Level level, BlockPos pos, BlockState state, Node.Port port, Node.Signal signal) {
        setStrength(signal.strength());
    }

    public int getStrength() {
        return Mth.clamp(invert ? 15 - strength : strength, 0, 15);
    }

    public void setStrength(int value) {
        var old = strength;
        strength = Mth.clamp(value, 0, 15);
        if (old == strength) return;
        if (level != null) level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        notifyUpdate();
    }

    public void checkStrength() {
        if (receiver || level == null || level.isClientSide) return;
        var power = getPower(level, worldPosition);
        if (strength == power) return;
        strength = power;
        notifyUpdate();
    }

    private int getPower(Level level, BlockPos pos) {
        var power = 0;
        for (var dir : Iterate.directions) power = Math.max(level.getSignal(pos.relative(dir), dir), power);
        for (var dir : Iterate.directions) if (facing.getOpposite() != dir) power = Math.max(level.getSignal(pos.relative(dir), Direction.UP), power);
        return power;
    }

    @Override
    public void setBlockState(BlockState state) {
        super.setBlockState(state);
        update(state);
    }

    public void update(@Nullable BlockState state) {
        if (state == null) state = getBlockState();
        facing = state.getValue(NodeLinkBlock.FACING);
        var old = receiver;
        receiver = state.getValue(NodeLinkBlock.RECEIVER);
        if (old != receiver) strength = 0;
        checkStrength();
    }

    public int rgb() {
        return clr == -1 ? receiver ? 0xF8317E : 0xFF3838 : clr;
    }

    @Override
    public void read(CompoundTag nbt, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(nbt, provider, clientPacket);
        invert = nbt.getBoolean("Invert");
        clr = nbt.contains("Color") ? nbt.getInt("Color") : -1;
        strength = nbt.getInt("Strength");
    }

    @Override
    protected void write(CompoundTag nbt, HolderLookup.Provider provider, boolean clientPacket) {
        super.write(nbt, provider, clientPacket);
        if (clr >= 0) nbt.putInt("Color", clr);
        if (receiver) nbt.putBoolean("Invert", invert);
        nbt.putInt("Strength", strength);
    }
}
