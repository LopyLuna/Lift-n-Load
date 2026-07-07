package dev.lopyluna.create_lnl.mixins;

import dev.lopyluna.create_lnl.Lifts;
import dev.lopyluna.create_lnl.content.blocks.connectors.IConnection;
import net.minecraft.world.level.ClipContext;
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
    @Unique private static final VoxelShape lifts$SHAPE = net.minecraft.world.level.block.Block.box(4, 4, 4, 12, 12, 12);
    @Unique private static final VoxelShape lifts$EMPTY = Shapes.empty();

    @SuppressWarnings("ShadowTarget") @Final @Shadow @Mutable
    private static ClipContext.Block[] $VALUES;

    @SuppressWarnings("InvokerTarget")
    @Invoker("<init>")
    private static ClipContext.Block newCtx(String internalName, int internalId, ClipContext.ShapeGetter shapeGetter) {
        throw new AssertionError("Mixin injection failed - ClipContext.Block Mixin from " + Lifts.NAME + ".");
    }

    @Inject(method = "<clinit>", at = @At(value = "FIELD", opcode = Opcodes.PUTSTATIC,
            target = "Lnet/minecraft/world/level/ClipContext$Block;$VALUES:[Lnet/minecraft/world/level/ClipContext$Block;", shift = At.Shift.AFTER)
    )
    private static void addCustomBoatType(CallbackInfo info) {
        var types = new ArrayList<>(Arrays.asList($VALUES));
        types.add(newCtx("CONNECTOR", types.getLast().ordinal() + 1, (s, l, p, c) -> {
            var be = l.getBlockEntity(p);
            return be instanceof IConnection<?> ? lifts$SHAPE : lifts$EMPTY;
        }));
        $VALUES = types.toArray(new ClipContext.Block[0]);
    }
}