package dev.lopyluna.create_lnl.content.blocks.logic_byte;

import dev.lopyluna.create_lnl.content.nodes.Node;
import com.simibubi.create.api.contraption.transformable.TransformableBlockEntity;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LogicByteBE extends SmartBlockEntity implements Node.Host, TransformableBlockEntity {
    public final Direction[] facings = new Direction[LogicSlot.ALL.length];
    public final int[] outputs = new int[LogicSlot.ALL.length];
    public final int[] latched = new int[LogicSlot.ALL.length];

    public LogicByteBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        Arrays.fill(facings, Direction.NORTH);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        for (var slot : LogicSlot.ALL) behaviours.add(new LogicOpBehaviour(this, slot));
    }

    public LogicOpBehaviour mode(LogicSlot slot) {
        return getBehaviour(LogicOpBehaviour.TYPES[slot.ordinal()]);
    }

    public LogicOp op(LogicSlot slot) {
        return mode(slot).get();
    }

    public int slots() {
        return getBlockState().getValue(LogicByteBlock.SLOTS);
    }

    public boolean has(LogicSlot slot) {
        return (slots() & slot.bit) != 0;
    }

    public int count() {
        return Integer.bitCount(slots());
    }

    public void add(LogicSlot slot, Direction facing) {
        facings[slot.ordinal()] = facing;
        outputs[slot.ordinal()] = 0;
        latched[slot.ordinal()] = 0;
        mode(slot).setValue(0);
        occupy(slots() | slot.bit);
    }

    public void remove(LogicSlot slot) {
        outputs[slot.ordinal()] = 0;
        latched[slot.ordinal()] = 0;
        occupy(slots() & ~slot.bit);
    }

    private void occupy(int slots) {
        if (level != null && slots != slots()) level.setBlock(worldPosition, getBlockState().setValue(LogicByteBlock.SLOTS, slots), 3);
        notifyUpdate();
    }

    public void rotate(LogicSlot slot, boolean backwards) {
        if (!has(slot)) return;
        var facing = facings[slot.ordinal()];
        facings[slot.ordinal()] = backwards ? facing.getCounterClockWise() : facing.getClockWise();
        notifyUpdate();
    }

    @Override
    public void transform(BlockEntity be, StructureTransform transform) {
        var dirs = facings.clone();
        var values = outputs.clone();
        var held = latched.clone();
        var ops = new LogicOp[LogicSlot.ALL.length];
        for (var slot : LogicSlot.ALL) ops[slot.ordinal()] = op(slot);
        for (var slot : LogicSlot.ALL) {
            var from = slot.ordinal();
            var to = slot.transform(transform).ordinal();
            var facing = transform.rotateFacing(transform.mirrorFacing(dirs[from]));
            facings[to] = facing.getAxis().isHorizontal() ? facing : dirs[from];
            outputs[to] = values[from];
            latched[to] = held[from];
            mode(LogicSlot.ALL[to]).setValue(ops[from].ordinal());
        }
        notifyUpdate();
    }

    @Override
    public List<Node.Port> ports(Level level, BlockPos pos, BlockState state) {
        var ports = new ArrayList<Node.Port>(count());
        for (var slot : LogicSlot.ALL) {
            if (!has(slot)) continue;
            var op = op(slot);
            ports.add(Node.Port.node(slot.id, Node.Channel.REDSTONE, slot.offset).colored(0x77B6FF).merging(op));
        }
        return ports;
    }

    @Override
    public Node.Signal read(Level level, BlockPos pos, BlockState state, Node.Port port) {
        var slot = LogicSlot.of(port.id());
        return Node.Channel.REDSTONE.of(slot == null ? 0 : outputs[slot.ordinal()]);
    }

    @Override
    public void write(Level level, BlockPos pos, BlockState state, Node.Port port, Node.Signal signal) {
        var slot = LogicSlot.of(port.id());
        if (slot == null) return;
        var index = slot.ordinal();
        var value = signal.strength();
        if (op(slot).latching()) {
            if (latched[index] == value) return;
            latched[index] = value;
            if (value != 0) outputs[index] = outputs[index] == value ? 0 : value;
            notifyUpdate();
            return;
        }
        if (outputs[index] == value) return;
        outputs[index] = value;
        notifyUpdate();
    }

    @Override
    public void read(CompoundTag nbt, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(nbt, provider, clientPacket);
        var dirs = nbt.getByteArray("Facings");
        var values = nbt.getByteArray("Outputs");
        var held = nbt.getByteArray("Latched");
        for (var i = 0; i < facings.length; i++) {
            facings[i] = i < dirs.length ? Direction.from2DDataValue(dirs[i]) : Direction.NORTH;
            outputs[i] = i < values.length ? Mth.clamp(values[i], 0, 15) : 0;
            latched[i] = i < held.length ? Mth.clamp(held[i], 0, 15) : 0;
        }
    }

    @Override
    protected void write(CompoundTag nbt, HolderLookup.Provider provider, boolean clientPacket) {
        super.write(nbt, provider, clientPacket);
        var dirs = new byte[facings.length];
        var values = new byte[outputs.length];
        var held = new byte[latched.length];
        for (var i = 0; i < facings.length; i++) {
            dirs[i] = (byte) facings[i].get2DDataValue();
            values[i] = (byte) outputs[i];
            held[i] = (byte) latched[i];
        }
        nbt.putByteArray("Facings", dirs);
        nbt.putByteArray("Outputs", values);
        nbt.putByteArray("Latched", held);
    }
}
