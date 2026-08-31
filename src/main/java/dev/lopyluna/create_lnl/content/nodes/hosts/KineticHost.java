package dev.lopyluna.create_lnl.content.nodes.hosts;

import dev.lopyluna.create_lnl.content.nodes.Node;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

public class KineticHost implements Node.Host {
    public static final KineticHost KINETIC = new KineticHost();
    private static final List<Node.Port> PORTS = List.of(Node.Port.out("speed", Node.Channel.ROTATION, Vec3.ZERO));

    @Nullable
    public static Node.Host of(BlockEntity be) {
        return be instanceof KineticBlockEntity ? KINETIC : null;
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
    public Node.Signal read(Level level, BlockPos pos, BlockState state, Node.Port port) {
        if (level.getBlockEntity(pos) instanceof KineticBlockEntity kinetic) return Node.Channel.ROTATION.of(kinetic.getSpeed());
        return Node.Channel.ROTATION.zero();
    }
}
