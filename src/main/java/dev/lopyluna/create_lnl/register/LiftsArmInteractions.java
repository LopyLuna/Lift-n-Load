package dev.lopyluna.create_lnl.register;

import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.kinetics.mechanicalArm.AllArmInteractionPointTypes;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;
import dev.lopyluna.create_lnl.Lifts;
import dev.lopyluna.create_lnl.content.blocks.thruster.ThrusterBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

@SuppressWarnings("SameParameterValue")
public class LiftsArmInteractions {
    private static <T extends ArmInteractionPointType> void register(final String name, final T type) {
        Registry.register(CreateBuiltInRegistries.ARM_INTERACTION_POINT_TYPE, Lifts.loc(name), type);
    }

    static {
        register("thruster", new PortableEngineType());
    }

    public static class PortableEngineType extends ArmInteractionPointType {
        @Override
        public boolean canCreatePoint(final Level level, final BlockPos pos, final BlockState state) {
            return LiftsBlocks.THRUSTER.is(state.getBlock());
        }

        @Override
        public @Nullable ArmInteractionPoint createPoint(final Level level, final BlockPos pos, final BlockState state) {
            return new PortableEngineInteractionPoint(this, level, pos, state);
        }
    }

    public static class PortableEngineInteractionPoint extends AllArmInteractionPointTypes.DepotPoint {
        public PortableEngineInteractionPoint(final ArmInteractionPointType type, final Level level, final BlockPos pos, final BlockState state) {
            super(type, level, pos, state);
        }

        @Override
        public ItemStack insert(final ArmBlockEntity armBlockEntity, final ItemStack stack, final boolean simulate) {
            if (this.cachedState.hasBlockEntity() && this.level.getBlockEntity(this.pos) instanceof final ThrusterBE be) return be.inventory.insertSlot(stack, 0, simulate);
            return super.insert(armBlockEntity, stack, simulate);
        }
    }

    public static void init() {}
}
