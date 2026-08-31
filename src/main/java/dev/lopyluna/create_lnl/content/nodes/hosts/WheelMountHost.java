package dev.lopyluna.create_lnl.content.nodes.hosts;

import dev.lopyluna.create_lnl.content.nodes.Node;
import dev.ryanhcode.offroad.content.blocks.wheel_mount.WheelMountBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

//TOGO IMPLEMENT STEERING WITH NODE TO NODE CONNECTION BETWEEN STEERING WHEEL & A NEW DRIVER CONTROLS LATER

public class WheelMountHost implements Node.Host {
    public static final WheelMountHost WHEEL_MOUNT = new WheelMountHost();
    private static final List<Node.Port> PORTS = List.of(Node.Port.in("invert", Node.Channel.REDSTONE, Vec3.ZERO).colored(0x5EFE6D));

    @Nullable
    public static Node.Host of(BlockEntity be) {
        return be instanceof WheelMountBlockEntity ? WHEEL_MOUNT : null;
    }

    @Override
    public List<Node.Port> ports(Level level, BlockPos pos, BlockState state) {
        return PORTS;
    }

    @Override
    public boolean implicit() {
        return false;
    }

    @Override
    public void write(Level level, BlockPos pos, BlockState state, Node.Port port, Node.Signal signal) {
        if (level.getBlockEntity(pos) instanceof SteerInverter inverter) inverter.lifts$setInvert(signal.active());
    }
}
