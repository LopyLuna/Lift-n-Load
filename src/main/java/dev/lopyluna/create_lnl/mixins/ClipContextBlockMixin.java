package dev.lopyluna.create_lnl.mixins;

import dev.lopyluna.create_lnl.Lifts;
import dev.lopyluna.create_lnl.content.nodes.NodeHosts;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Arrays;

@SuppressWarnings("SameParameterValue")
@Mixin(ClipContext.Block.class)
public class ClipContextBlockMixin {
    @Unique private static final VoxelShape lifts$EMPTY = Shapes.empty();

    @SuppressWarnings("ShadowTarget") @Final @Shadow @Mutable
    private static ClipContext.Block[] $VALUES;

    @SuppressWarnings("InvokerTarget")
    @Invoker("<init>")
    private static ClipContext.Block newCtx(String internalName, int internalId, ClipContext.ShapeGetter shapeGetter) {
        throw new AssertionError("Mixin injection failed - ClipContext.Block Mixin from " + Lifts.NAME + ".");
    }

    @Unique
    private static VoxelShape lifts$nodeShape(BlockGetter getter, BlockPos pos) {
        if (!(getter instanceof Level level)) return lifts$EMPTY;
        var ports = NodeHosts.ports(level, pos);
        if (ports.isEmpty()) return lifts$EMPTY;
        var shape = lifts$EMPTY;
        for (var port : ports) {
            var offset = port.offset();
            shape = Shapes.or(shape, Shapes.box(
                    Mth.clamp(0.5 + offset.x - 0.25, 0, 1), Mth.clamp(0.5 + offset.y - 0.25, 0, 1), Mth.clamp(0.5 + offset.z - 0.25, 0, 1),
                    Mth.clamp(0.5 + offset.x + 0.25, 0, 1), Mth.clamp(0.5 + offset.y + 0.25, 0, 1), Mth.clamp(0.5 + offset.z + 0.25, 0, 1)));
        }
        return shape;
    }

    @Inject(method = "<clinit>", at = @At(value = "FIELD", opcode = Opcodes.PUTSTATIC,
            target = "Lnet/minecraft/world/level/ClipContext$Block;$VALUES:[Lnet/minecraft/world/level/ClipContext$Block;", shift = At.Shift.AFTER)
    )
    private static void addNewClipContext(CallbackInfo info) {
        var types = new ArrayList<>(Arrays.asList($VALUES));
        types.add(newCtx("CONNECTOR", types.getLast().ordinal() + 1, (s, l, p, c) -> lifts$nodeShape(l, p)));
        $VALUES = types.toArray(new ClipContext.Block[0]);
    }
}
